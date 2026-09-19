#!/usr/bin/env python3
"""
Finishing pass for a recorded dashboard demo: place the narration, normalise it, and encode for text.

Beyond remux_narration.py this does the things that make a capture look produced rather than dumped:
  * EBU R128 loudness normalisation so every line sits at the same level (a TTS render drifts by a few LU
    between clips, which is audible as one sentence being quieter than the next);
  * a short fade from black at the start and to black at the end, plus an audio fade out;
  * CRF 20 with tune=stillimage — a dashboard is mostly static text, and the default CRF smears small type;
  * a verification report: where each clip landed, whether any was trimmed, and the longest silent gap.
"""
import argparse
import json
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent


def dur(ff, path):
    out = subprocess.run([ff, "-i", str(path), "-f", "null", "-"], capture_output=True, text=True)
    for line in out.stderr.splitlines():
        if "Duration:" in line:
            h, m, s = line.split("Duration:")[1].split(",")[0].strip().split(":")
            return int(h) * 3600 + int(m) * 60 + float(s)
    raise RuntimeError(f"no duration for {path}")



def median_f0(path):
    """Rough median fundamental for one clip. The TTS picks a RANDOM timbre whenever it starts cold,
    so a second render dropped into the same video can be a different speaker — a mismatch that is
    obvious to a listener and invisible in a duration report. F0 catches it."""
    import wave
    import numpy as np
    w = wave.open(str(path))
    sr, x = w.getframerate(), np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype("f4")
    if w.getnchannels() == 2:
        x = x.reshape(-1, 2).mean(1)
    x /= (abs(x).max() + 1e-9)
    win, hop, out = int(0.04 * sr), int(0.02 * sr), []
    lo, hi = int(sr / 300), int(sr / 70)
    for i in range(0, max(0, len(x) - win), hop):
        seg = x[i:i + win]
        if (seg ** 2).mean() ** 0.5 < 0.05:
            continue
        seg = seg - seg.mean()
        ac = np.correlate(seg, seg, "full")[win - 1:]
        if hi >= len(ac):
            continue
        k = lo + int(np.argmax(ac[lo:hi]))
        if ac[k] > 0.3 * ac[0]:
            out.append(sr / k)
    return float(np.median(out)) if out else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True)
    ap.add_argument("--timeline", required=True)
    ap.add_argument("--wavs", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--gap", type=float, default=0.25)
    ap.add_argument("--fade", type=float, default=0.8)
    ap.add_argument("--crf", type=int, default=20)
    ap.add_argument("--fill", default=None,
                    help="JSON of extra commentary lines [{id, after}] whose wavs live in --fill-wavs; "
                         "each is placed inside the silence that follows the phase it names, so a long "
                         "live operation is narrated instead of playing out in silence")
    ap.add_argument("--fill-wavs", default=None)
    a = ap.parse_args()

    import imageio_ffmpeg
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    tl = json.load(open(a.timeline))
    total = tl["total_s"]
    wavs = Path(a.wavs)

    clips, report, trimmed = [], [], 0
    phases = tl["phases"]
    for i, p in enumerate(phases):
        wav = wavs / f"{p['id']}.wav"
        if not wav.exists():
            report.append({"id": p["id"], "state": "missing"}); continue
        d = dur(ff, wav)
        start = p["t"]
        nxt = phases[i + 1]["t"] if i + 1 < len(phases) else total
        room = max(0.5, nxt - start - a.gap)
        trim = min(d, room)
        if trim < d - 0.05:
            trimmed += 1
        clips.append((wav, start, trim))
        report.append({"id": p["id"], "at": round(start, 1), "audio_s": round(d, 2),
                       "room_s": round(room, 2), "trimmed": trim < d - 0.05,
                       "caption": p.get("caption", "")[:60]})

    # commentary placed into the gaps after long operations
    if a.fill and a.fill_wavs:
        fills = json.load(open(a.fill))
        by_id = {p["id"]: (i, p) for i, p in enumerate(phases)}
        pending = {}
        for f in fills:
            pending.setdefault(f["after"], []).append(f)
        for anchor, items in pending.items():
            if anchor not in by_id:
                continue
            idx, ph = by_id[anchor]
            base = next((c for c in clips if c[1] == ph["t"]), None)
            after = (base[1] + base[2]) if base else ph["t"]
            end = phases[idx + 1]["t"] if idx + 1 < len(phases) else total
            # the pause before the commentary shrinks rather than losing the line: a fixed 1.5 s
            # lead-in silently dropped lines that were only a few tenths too long for their window
            spare = end - 0.5 - after - sum(dur(ff, Path(a.fill_wavs) / f"{f['id']}.wav")
                                            for f in items
                                            if (Path(a.fill_wavs) / f"{f['id']}.wav").exists())
            lead = max(0.6, min(1.5, spare / max(1, len(items))))
            cursor = after + lead
            for f in items:
                w = Path(a.fill_wavs) / f"{f['id']}.wav"
                if not w.exists():
                    continue
                d = dur(ff, w)
                if cursor + d > end - 0.5:      # only if it genuinely fits in the silence
                    report.append({"id": f["id"], "state": "skipped (no room)"})
                    continue
                clips.append((w, cursor, d))
                report.append({"id": f["id"], "at": round(cursor, 1), "audio_s": round(d, 2),
                               "fill_after": anchor, "caption": f.get("caption", "")})
                cursor += d + lead
        clips.sort(key=lambda c: c[1])

    # every line must be the same speaker: flag any clip whose pitch is far from the rest
    try:
        import statistics
        f0 = {}
        for wav, _start, _trim in clips:
            v = median_f0(wav)
            if v == v:
                f0[wav.stem] = round(v, 1)
        if len(f0) >= 3:
            med = statistics.median(f0.values())
            odd = sorted(k for k, v in f0.items() if abs(v - med) > 0.18 * med)
            voice = {"median_f0_hz": round(med, 1), "outliers": odd,
                     "per_clip_f0_hz": dict(sorted(f0.items()))}
            if odd:
                print(f"WARNING: {len(odd)} clip(s) sound like a different voice "
                      f"(median {med:.0f} Hz): {', '.join(odd)}")
        else:
            voice = {"median_f0_hz": None, "outliers": []}
    except Exception as e:                      # a pitch check must never fail the render
        voice = {"error": str(e)}

    # silence between the end of one line and the start of the next
    gaps = []
    for i, (wav, start, trim) in enumerate(clips):
        nxt_start = clips[i + 1][1] if i + 1 < len(clips) else total
        gaps.append(round(nxt_start - (start + trim), 1))

    inputs, fc = ["-i", a.video], []
    for i, (wav, start, trim) in enumerate(clips):
        inputs += ["-i", str(wav)]
        ms = int(start * 1000)
        fc.append(f"[{i+1}:a]atrim=0:{trim:.2f},asetpts=PTS-STARTPTS,adelay={ms}|{ms}[a{i}]")
    mix = "".join(f"[a{i}]" for i in range(len(clips)))
    fc.append(f"{mix}amix=inputs={len(clips)}:normalize=0:dropout_transition=0[amix]")
    # one loudness pass over the whole mix keeps relative timing but evens the level between lines
    fc.append(f"[amix]loudnorm=I=-16:TP=-1.5:LRA=11,afade=t=out:st={max(0, total - a.fade):.2f}:d={a.fade}[a]")
    fc.append(f"[0:v]fade=t=in:st=0:d={a.fade},fade=t=out:st={max(0, total - a.fade):.2f}:d={a.fade}[v]")

    cmd = [ff, "-y", "-loglevel", "error"] + inputs + [
        "-filter_complex", ";".join(fc), "-map", "[v]", "-map", "[a]",
        "-c:v", "libx264", "-preset", "slow", "-crf", str(a.crf), "-tune", "stillimage",
        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
        "-movflags", "+faststart", "-t", f"{total:.2f}", a.out]
    subprocess.run(cmd, check=True, timeout=7200)

    out = Path(a.out)
    print(f"{out} — {out.stat().st_size/1e6:.1f} MB, {total/60:.1f} min, {len(clips)} narration clips")
    print(f"clips trimmed to fit: {trimmed}")
    print(f"silence between lines: max {max(gaps):.1f}s, median {sorted(gaps)[len(gaps)//2]:.1f}s")
    long_gaps = [(report[i]['id'], g) for i, g in enumerate(gaps) if g > 6]
    if long_gaps:
        print("gaps over 6s (live operations running on screen):")
        for cid, g in long_gaps:
            print(f"  {cid}: {g:.0f}s")
    json.dump({"clips": report, "gaps": gaps, "voice": voice}, open(out.with_suffix(".report.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
