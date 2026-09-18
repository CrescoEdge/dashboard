#!/usr/bin/env python3
"""
Record the dashboard as a narrated video while something happens to the mesh.

Playwright drives a headless Chromium with video capture and clicks around: it rotates through the tabs the
current phase cares about (with a look at the others), scrolls the long tabs, and paints a caption overlay. The
workload driver updates a phase file with either a plain caption line or a JSON object
    {"caption": "...", "say": "narration text", "tabs": ["Transfers", "Links", "Overview"]}
and writes DONE at the end. The recorder logs a timeline (when each phase appeared on screen), renders each
phase's narration with macOS `say`, places the clips at their phase start (trimmed so they never overlap) and muxes
video + narration into an H.264/AAC mp4 with the bundled imageio-ffmpeg. Without `say`/ffmpeg it still leaves the webm.

  ./venv/bin/python dashboard/record_dashboard.py --phase-file /tmp/phase.txt --out dashboard/video --max 1500
"""
import argparse
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

ALL_TABS = ["Overview", "Globals", "Tunnels", "Routing", "Nodes", "Links", "Metrics", "Storage", "Transfers"]
SCROLL_TABS = {"Storage", "Transfers", "Nodes", "Metrics", "Links", "Tunnels"}
OVERLAY_JS = """
(txt) => {
  let d = document.getElementById('_rec_overlay');
  if (!d) { d = document.createElement('div'); d.id = '_rec_overlay';
    d.style.cssText = 'position:fixed;left:16px;bottom:16px;z-index:99;max-width:72%;padding:10px 14px;border-radius:10px;'
      + 'background:rgba(15,23,42,.93);border:1px solid #334155;color:#e5e7eb;font:600 15px/1.35 system-ui;box-shadow:0 4px 18px rgba(0,0,0,.5)';
    document.body.appendChild(d); }
  d.innerHTML = txt;
}
"""


def tts(voice, rate, text, path, ff):
    """Render narration with macOS `say`; returns the clip duration in seconds. PCM wav first (duration from the
    header), else the default AIFF with the duration measured by ffmpeg."""
    try:
        subprocess.run(["say", "-v", voice, "-r", rate, "--data-format=LEI16@22050", "-o", str(path), text], check=True, timeout=120, capture_output=True)
        import wave
        with wave.open(str(path)) as f:
            return f.getnframes() / f.getframerate()
    except Exception:
        aiff = path.with_suffix(".aiff")
        subprocess.run(["say", "-v", voice, "-r", rate, "-o", str(aiff), text], check=True, timeout=120, capture_output=True)
        subprocess.run([ff, "-y", "-loglevel", "error", "-i", str(aiff), str(path)], check=True, timeout=120)
        import wave
        with wave.open(str(path)) as f:
            return f.getnframes() / f.getframerate()


def read_phase(path):
    try:
        raw = Path(path).read_text().strip()
    except OSError:
        return {"caption": "", "say": None, "tabs": None, "done": False}
    if raw == "DONE":
        return {"caption": "", "say": None, "tabs": None, "done": True}
    if raw.startswith("{"):
        try:
            d = json.loads(raw)
            return {"caption": d.get("caption", ""), "say": d.get("say"), "tabs": d.get("tabs"), "done": False}
        except Exception:
            pass
    return {"caption": raw, "say": None, "tabs": None, "done": False}


def plan_for(tabs_pref, present, base_dwell):
    """Rotation for a phase: its preferred tabs (longer dwell), then one of the other tabs, round-robin."""
    pref = [t for t in (tabs_pref or ["Overview", "Storage", "Transfers", "Links"]) if t in present]
    others = [t for t in present if t not in pref]
    plan = [(t, base_dwell + (3 if t in ("Storage", "Overview") else 0)) for t in pref]
    if others:
        plan.append(("__other__", 4))
    return plan or [("Overview", base_dwell)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8900/")
    ap.add_argument("--phase-file", required=True)
    ap.add_argument("--out", default=str(Path(__file__).resolve().parent / "video"))
    ap.add_argument("--max", type=int, default=1500, help="max seconds")
    ap.add_argument("--dwell", type=float, default=7.0, help="seconds per preferred tab")
    ap.add_argument("--size", default="1600x1000")
    ap.add_argument("--voice", default="Samantha")
    ap.add_argument("--rate", default="178", help="words per minute for `say`")
    ap.add_argument("--no-audio", action="store_true")
    a = ap.parse_args()
    w, h = (int(x) for x in a.size.split("x"))
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    for f in out.glob("*.webm"):
        f.unlink()
    timeline = []          # [{"t": s, "caption": ..., "say": ...}]
    other_idx = 0

    with sync_playwright() as p:
        b = p.chromium.launch(args=["--no-sandbox"])
        ctx = b.new_context(viewport={"width": w, "height": h}, record_video_dir=str(out), record_video_size={"width": w, "height": h})
        t0 = time.time()
        pg = ctx.new_page()
        pg.goto(a.url, wait_until="networkidle", timeout=30000)
        pg.wait_for_timeout(1500)
        last_caption = None
        cur = read_phase(a.phase_file)
        while time.time() - t0 < a.max and not cur["done"]:
            present = [t for t in ALL_TABS if pg.locator("#tabs .tab", has_text=t).count() > 0]
            plan = plan_for(cur["tabs"], present, a.dwell)
            phase_changed = False
            for tab, secs in plan:
                if tab == "__other__":
                    others = [t for t in present if t not in (cur["tabs"] or [])]
                    if not others:
                        continue
                    tab = others[other_idx % len(others)]; other_idx += 1
                loc = pg.locator("#tabs .tab", has_text=tab)
                if loc.count() == 0:
                    continue
                loc.first.click()
                pg.evaluate("window.scrollTo(0,0)")
                end = time.time() + secs
                scroll = tab in SCROLL_TABS
                while time.time() < end:
                    ph = read_phase(a.phase_file)
                    if ph["done"]:
                        cur = ph; break
                    if ph["caption"] != last_caption and ph["caption"]:
                        last_caption = ph["caption"]
                        timeline.append({"t": round(time.time() - t0, 2), "caption": ph["caption"], "say": ph["say"]})
                        cur = ph; phase_changed = True
                        end = min(end, time.time() + 2.5)   # finish this tab quickly, then rotate for the new phase
                    el = int(time.time() - t0)
                    pg.evaluate(OVERLAY_JS, f"<span style='color:#94a3b8'>{el // 60:02d}:{el % 60:02d} · {tab}</span><br>{last_caption or '…'}")
                    if scroll:
                        pg.evaluate("window.scrollBy(0, 260)")
                    pg.wait_for_timeout(1000)
                if cur["done"] or phase_changed:
                    break
            if cur["done"]:
                break
        pg.wait_for_timeout(1500)
        total_s = round(time.time() - t0, 2)
        video = pg.video
        ctx.close()
        webm = Path(video.path())
        b.close()
    final_webm = out / "dashboard.webm"
    shutil.move(str(webm), final_webm)
    (out / "timeline.json").write_text(json.dumps({"total_s": total_s, "phases": timeline}, indent=1))
    print(f"recorded {final_webm} ({final_webm.stat().st_size / 1e6:.1f} MB, {total_s:.0f} s, {len(timeline)} phases)")

    # ---- narration + mp4 ----
    try:
        import imageio_ffmpeg
        ff = imageio_ffmpeg.get_ffmpeg_exe()
    except Exception as e:  # noqa: BLE001
        print(f"mp4 skipped (no ffmpeg): {e}"); return
    mp4 = out / "dashboard.mp4"
    clips = []
    if not a.no_audio and shutil.which("say"):
        spoken = [e for e in timeline if e.get("say")]
        for i, e in enumerate(spoken):
            path = out / f"narr_{i:02d}.wav"
            try:
                dur = tts(a.voice, a.rate, e["say"], path, ff)
            except Exception as ex:  # noqa: BLE001
                print(f"narration {i} skipped: {ex}"); continue
            nxt = next((x["t"] for x in timeline if x["t"] > e["t"]), total_s)
            limit = max(1.0, nxt - e["t"] - 0.4)
            clips.append((path, e["t"], min(dur, limit), dur))
    if clips:
        inputs = ["-i", str(final_webm)]
        fc = []
        for i, (path, start, trim, dur) in enumerate(clips):
            inputs += ["-i", str(path)]
            ms = int(start * 1000)
            fc.append(f"[{i + 1}:a]atrim=0:{trim:.2f},asetpts=PTS-STARTPTS,adelay={ms}|{ms}[a{i}]")
        fc.append("".join(f"[a{i}]" for i in range(len(clips))) + f"amix=inputs={len(clips)}:normalize=0:dropout_transition=0[a]")
        cmd = [ff, "-y", "-loglevel", "error"] + inputs + ["-filter_complex", ";".join(fc), "-map", "0:v", "-map", "[a]",
               "-c:v", "libx264", "-preset", "medium", "-crf", "23", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
               "-movflags", "+faststart", "-t", f"{total_s:.2f}", str(mp4)]
        subprocess.run(cmd, check=True, timeout=3600)
        cut = sum(1 for c in clips if c[2] < c[3] - 0.05)
        print(f"narrated {mp4} ({mp4.stat().st_size / 1e6:.1f} MB, {len(clips)} clips, {cut} trimmed to fit their phase)")
    else:
        subprocess.run([ff, "-y", "-loglevel", "error", "-i", str(final_webm), "-c:v", "libx264", "-preset", "medium", "-crf", "23",
                        "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(mp4)], check=True, timeout=3600)
        print(f"converted {mp4} ({mp4.stat().st_size / 1e6:.1f} MB, no narration)")


if __name__ == "__main__":
    main()
