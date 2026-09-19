#!/usr/bin/env python3
"""
Record a chaptered, narrated dashboard demo at 1080p.

Differences from record_dashboard.py, which records whatever a workload driver happens to do:
  * the phase list is known UP FRONT (a script file), and each line's narration has already been
    rendered to a wav, so the recorder holds every phase for its MEASURED audio length — a sentence
    can never be cut off, which is what the estimate-based version could not guarantee;
  * `card` phases render a full-screen title/chapter slide in the same browser (page.set_content),
    so the video gets structure without any editing step;
  * a phase may name a CSS selector to highlight, which is outlined and pulsed while it is on screen.

The driver signals progress through a small JSON state file: it writes {"phase": <index>} as it
starts each phase, and "DONE" when finished. The recorder follows that, so video and workload stay
in step no matter how long an operation actually takes.
"""
import argparse
import json
import shutil
import subprocess
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent

CARD_HTML = """<!doctype html><meta charset=utf-8>
<style>
 html,body{margin:0;height:100%%;background:#0b1120;color:#e5e7eb;
   font:400 16px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
 .wrap{height:100%%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:24px;text-align:center;padding:0 8%%}
 .chapter{font-size:15px;letter-spacing:.28em;text-transform:uppercase;color:#60a5fa;font-weight:700}
 h1{font-size:46px;line-height:1.25;margin:0;font-weight:700;max-width:22ch}
 .rule{width:120px;height:3px;background:linear-gradient(90deg,#3b82f6,#8b5cf6);border-radius:2px}
 .sub{color:#94a3b8;font-size:17px}
</style>
<div class=wrap>
  %(chapter)s
  <h1>%(title)s</h1>
  <div class=rule></div>
  <div class=sub>%(sub)s</div>
</div>"""

FOCUS_JS = """
(d) => {
  const view = document.getElementById('view');
  if (!view) return;
  const S = '_rec_style';
  if (!document.getElementById(S)) {
    const st = document.createElement('style'); st.id = S;
    st.textContent =
      '._rec_dim{filter:blur(3px) saturate(.6);opacity:.28;transition:filter .45s ease,opacity .45s ease}' +
      '._rec_focus{transition:box-shadow .45s ease;box-shadow:0 0 0 3px rgba(96,165,250,.95),0 0 44px 10px rgba(59,130,246,.30);border-radius:14px;position:relative;z-index:3}' +
      '#_rec_overlay{position:fixed;left:26px;bottom:26px;z-index:99;max-width:64%;padding:15px 20px;border-radius:13px;' +
      'background:rgba(15,23,42,.95);border:1px solid #334155;color:#e5e7eb;font:600 18px/1.45 system-ui;' +
      'box-shadow:0 10px 34px rgba(0,0,0,.6)}' +
      '#_rec_overlay .meta{color:#93c5fd;font-weight:700;font-size:13px;letter-spacing:.16em;text-transform:uppercase}';
    document.head.appendChild(st);
  }
  let el = document.getElementById('_rec_overlay');
  if (!el) { el = document.createElement('div'); el.id = '_rec_overlay'; document.body.appendChild(el); }
  el.innerHTML = d.html;

  // every focusable region of the current view, in the order the page lays them out
  const tiles = view.querySelector('.tiles');
  const cards = Array.from(view.querySelectorAll('.card'));
  const all = (tiles ? [tiles] : []).concat(cards);
  all.forEach(e => { e.classList.remove('_rec_dim'); e.classList.remove('_rec_focus'); });

  let target = null;
  if (d.panel === 'tiles') target = tiles;
  else if (d.panel !== null && d.panel !== undefined && cards.length) target = cards[Math.min(d.panel, cards.length - 1)];
  if (!target) return null;

  all.forEach(e => { if (e !== target) e.classList.add('_rec_dim'); });
  target.classList.add('_rec_focus');
  const r = target.getBoundingClientRect();
  if (r.top < 90 || r.bottom > window.innerHeight - 40) {
    window.scrollBy({ top: r.top - 130, behavior: 'smooth' });
  }
  const h2 = target.querySelector('h2');
  return h2 ? h2.textContent.trim().slice(0, 60) : (d.panel === 'tiles' ? 'stat tiles' : 'panel ' + d.panel);
}
"""

def wav_seconds(ff, path):
    out = subprocess.run([ff, "-i", str(path), "-f", "null", "-"], capture_output=True, text=True)
    for line in out.stderr.splitlines():
        if "Duration:" in line:
            h, m, s = line.split("Duration:")[1].split(",")[0].strip().split(":")
            return int(h) * 3600 + int(m) * 60 + float(s)
    return 0.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8900/")
    ap.add_argument("--script", required=True)
    ap.add_argument("--state", required=True, help="JSON file the driver writes its current phase index into")
    ap.add_argument("--wavs", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", default="1920x1080")
    ap.add_argument("--max", type=int, default=2400)
    ap.add_argument("--pad", type=float, default=0.9, help="extra seconds held after a line finishes speaking")
    a = ap.parse_args()

    import imageio_ffmpeg
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    w, h = (int(x) for x in a.size.split("x"))
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    for f in out.glob("*.webm"):
        f.unlink()
    script = json.load(open(a.script))
    # Hold each phase for its MEASURED narration length when the wav exists. When it does not (the
    # render is still queued on the cluster), fall back to a deliberately slow estimate — 130 wpm is
    # below the model's measured ~140 wpm — so the audio still fits when it is muxed in later.
    audio, estimated = {}, 0
    for ph in script:
        wav = Path(a.wavs) / f"{ph['id']}.wav"
        if wav.exists():
            audio[ph["id"]] = wav_seconds(ff, wav)
        else:
            audio[ph["id"]] = len(ph.get("say", "").split()) / (130 / 60.0)
            estimated += 1
    if estimated:
        print(f"note: {estimated}/{len(script)} lines have no wav yet — holding on a 130 wpm estimate")

    def driver_phase():
        try:
            raw = Path(a.state).read_text().strip()
            return "DONE" if raw == "DONE" else json.loads(raw).get("phase", -1)
        except Exception:
            return -1

    timeline, t0, spotlit = [], None, {}
    with sync_playwright() as p:
        b = p.chromium.launch(args=["--no-sandbox"])
        ctx = b.new_context(viewport={"width": w, "height": h},
                            record_video_dir=str(out), record_video_size={"width": w, "height": h})
        pg = ctx.new_page()
        pg.goto(a.url, wait_until="networkidle", timeout=45000)
        t0 = time.time()
        pg.wait_for_timeout(1200)

        seen = -1
        while time.time() - t0 < a.max:
            cur = driver_phase()
            if cur == "DONE":
                break
            if cur < 0 or cur == seen:
                pg.wait_for_timeout(400); continue
            seen = cur
            if cur >= len(script):
                break
            ph = script[cur]
            hold = max(3.0, audio.get(ph["id"], 4.0) + a.pad)
            start = round(time.time() - t0, 2)
            timeline.append({"i": cur, "id": ph["id"], "t": start, "caption": ph["caption"],
                             "audio_s": round(audio.get(ph["id"], 0), 2), "hold_s": round(hold, 2)})
            print(f"  [{start:7.1f}s] {ph['id']} hold {hold:5.1f}s  {ph['caption'][:58]}", flush=True)

            if ph["act"] == "card":
                pg.set_content(CARD_HTML % {
                    "chapter": f'<div class=chapter>{ph["chapter"]}</div>' if ph.get("chapter") else "",
                    "title": ph["caption"], "sub": "Cresco · recorded live"})
                pg.wait_for_timeout(int(hold * 1000))
                pg.goto(a.url, wait_until="domcontentloaded", timeout=45000)
                pg.wait_for_timeout(1000)
                continue

            tab = ph.get("tab")
            if tab and pg.locator("#tabs .tab", has_text=tab).count():
                pg.locator("#tabs .tab", has_text=tab).first.click()
                pg.wait_for_timeout(500)
                pg.evaluate("window.scrollTo({top:0})")
            # A spotlight teaches where a thing is; teaching it twice is noise. Each panel is
            # highlighted once, and later visits show the whole tab so the viewer watches it change.
            panel = ph.get("panel")
            key = (tab, panel)
            if panel is not None and key in spotlit:
                print(f"           (already spotlit in {spotlit[key]} — showing the whole tab)", flush=True)
                panel = None
            elif panel is not None:
                spotlit[key] = ph["id"]

            end = time.time() + hold
            focused = None
            while time.time() < end:
                el = int(time.time() - t0)
                meta = f"{el//60:02d}:{el%60:02d}" + (f" · {tab} view" if tab else "")
                got = pg.evaluate(FOCUS_JS, {
                    "html": f"<div class=meta>{meta}</div>{ph['caption']}",
                    "panel": panel})
                focused = focused or got
                if driver_phase() not in (cur, -1):
                    break
                pg.wait_for_timeout(700)
            if focused:
                timeline[-1]["focused"] = focused
                print(f"           focused: {focused}", flush=True)
            # a phase may ask to end on a different tab, so the next act is already in frame
            if ph.get("tab_after") and pg.locator("#tabs .tab", has_text=ph["tab_after"]).count():
                pg.locator("#tabs .tab", has_text=ph["tab_after"]).first.click()

        pg.wait_for_timeout(1200)
        total = round(time.time() - t0, 2)
        video = pg.video
        ctx.close()
        webm = Path(video.path())
        b.close()

    final = out / "advanced.webm"
    shutil.move(str(webm), final)
    (out / "advanced_timeline.json").write_text(json.dumps({"total_s": total, "phases": timeline}, indent=1))
    print(f"\nrecorded {final} ({final.stat().st_size/1e6:.1f} MB, {total:.0f}s, {len(timeline)} phases)")


if __name__ == "__main__":
    main()
