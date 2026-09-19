#!/usr/bin/env python3
"""
Check a finished demo video before it goes out: streams, loudness, the placement report, and frames
pulled from the file itself (a report can say a spotlight was applied; only a frame proves it).
"""
import argparse
import json
import subprocess
from pathlib import Path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True)
    ap.add_argument("--frames", default="", help="comma-separated seconds to extract as PNGs")
    ap.add_argument("--frame-dir", default=None)
    a = ap.parse_args()

    import imageio_ffmpeg
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    v = Path(a.video)
    probe = subprocess.run([ff, "-i", str(v), "-f", "null", "-"], capture_output=True, text=True).stderr
    print(f"{v.name} — {v.stat().st_size/1e6:.1f} MB")
    for line in probe.splitlines():
        if "Duration:" in line or "Stream #" in line:
            print("  " + line.strip())

    vol = subprocess.run([ff, "-i", str(v), "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    for line in vol.splitlines():
        if "mean_volume" in line or "max_volume" in line:
            print("  " + line.split("]")[-1].strip())

    rep = v.with_suffix(".report.json")
    if rep.exists():
        r = json.load(open(rep))
        clips, gaps = r["clips"], r["gaps"]
        placed = [c for c in clips if "at" in c]
        trimmed = [c for c in placed if c.get("trimmed")]
        missing = [c for c in clips if c.get("state")]
        print(f"  clips placed {len(placed)}  trimmed {len(trimmed)}  not placed {len(missing)}"
              + (f" ({', '.join(c['id'] for c in missing)})" if missing else ""))
        print(f"  gaps: max {max(gaps):.1f}s  median {sorted(gaps)[len(gaps)//2]:.1f}s  "
              f"silence {sum(gaps):.0f}s of {placed[-1]['at']+placed[-1]['audio_s']:.0f}s")
        voice = r.get("voice") or {}
        if voice.get("outliers"):
            print(f"  !! VOICE MISMATCH: {voice['outliers']} against median {voice.get('median_f0_hz')} Hz")
        elif voice.get("median_f0_hz"):
            print(f"  voice: one speaker, median {voice['median_f0_hz']} Hz, no outliers")

    if a.frames:
        d = Path(a.frame_dir or v.parent / "frames"); d.mkdir(parents=True, exist_ok=True)
        for s in a.frames.split(","):
            out = d / f"{v.stem}_{s}s.png"
            subprocess.run([ff, "-y", "-loglevel", "error", "-ss", s, "-i", str(v),
                            "-frames:v", "1", str(out)], check=True)
            print(f"  frame {s}s -> {out}")


if __name__ == "__main__":
    main()
