#!/usr/bin/env python3
"""
Replace a recorded dashboard video's narration track without re-recording it.

Takes the silent capture (dashboard.webm), the phase timeline the recorder wrote (timeline.json)
and a directory of per-line wavs (narr_NN.wav, e.g. rendered by Fish Audio S2 Pro on the DGX), and
muxes each clip in at the second its phase appeared on screen. A clip longer than its phase window
is placed anyway and only trimmed if it would overrun the next line, so nothing is cut mid-sentence
unless two lines would otherwise overlap.

  ./venv/bin/python dashboard/remux_narration.py --wavs dashboard/video/fish --out dashboard/video/dashboard_fish.mp4
"""
import argparse
import json
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent


def duration(ff, path):
    out = subprocess.run([ff, "-i", str(path), "-f", "null", "-"], capture_output=True, text=True)
    for line in out.stderr.splitlines():
        if "Duration:" in line:
            h, m, s = line.split("Duration:")[1].split(",")[0].strip().split(":")
            return int(h) * 3600 + int(m) * 60 + float(s)
    raise RuntimeError(f"no duration for {path}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", default=str(HERE / "video" / "dashboard.webm"))
    ap.add_argument("--timeline", default=str(HERE / "video" / "timeline.json"))
    ap.add_argument("--wavs", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--gap", type=float, default=0.3, help="minimum silence between consecutive lines")
    a = ap.parse_args()

    import imageio_ffmpeg
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    tl = json.load(open(a.timeline))
    phases = [p for p in tl["phases"] if p.get("say")]
    total = tl["total_s"]
    wavs = Path(a.wavs)

    clips, report = [], []
    for i, p in enumerate(phases):
        wav = wavs / f"narr_{i:02d}.wav"
        if not wav.exists():
            report.append((f"narr_{i:02d}", "MISSING", 0, 0)); continue
        dur = duration(ff, wav)
        start = p["t"]
        nxt = phases[i + 1]["t"] if i + 1 < len(phases) else total
        room = max(0.5, nxt - start - a.gap)
        trim = min(dur, room)
        clips.append((wav, start, trim))
        report.append((f"narr_{i:02d}", "trimmed" if trim < dur - 0.05 else "full", round(dur, 2), round(room, 2)))

    inputs, fc = ["-i", a.video], []
    for i, (wav, start, trim) in enumerate(clips):
        inputs += ["-i", str(wav)]
        ms = int(start * 1000)
        fc.append(f"[{i+1}:a]atrim=0:{trim:.2f},asetpts=PTS-STARTPTS,adelay={ms}|{ms}[a{i}]")
    fc.append("".join(f"[a{i}]" for i in range(len(clips))) + f"amix=inputs={len(clips)}:normalize=0:dropout_transition=0[a]")
    cmd = [ff, "-y", "-loglevel", "error"] + inputs + [
        "-filter_complex", ";".join(fc), "-map", "0:v", "-map", "[a]",
        "-c:v", "libx264", "-preset", "medium", "-crf", "23", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-t", f"{total:.2f}", a.out]
    subprocess.run(cmd, check=True, timeout=3600)

    print(f"{len(clips)} clips muxed onto {Path(a.video).name} -> {a.out} ({Path(a.out).stat().st_size/1e6:.1f} MB)")
    for cid, state, dur, room in report:
        flag = "" if state == "full" else f"  <-- {state}"
        print(f"  {cid}: {dur:6.2f}s audio, {room:6.2f}s room{flag}")


if __name__ == "__main__":
    main()
