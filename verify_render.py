#!/usr/bin/env python3
"""Headless render check: load the dashboard, click every tab it offers, fail on any console/page error,
and screenshot each tab. Storage is expected when the backend reports a GFS deployment.

  ./venv/bin/python dashboard/verify_render.py [http://localhost:8900/] [--out DIR] [--expect-storage]
"""
import argparse
import json
import sys
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("url", nargs="?", default="http://localhost:8900/")
    ap.add_argument("--out", default=str(Path(__file__).resolve().parent / "screenshots"))
    ap.add_argument("--expect-storage", action="store_true", help="fail unless the Storage tab is present and populated")
    a = ap.parse_args()
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)

    snap = json.load(urllib.request.urlopen(a.url.rstrip("/") + "/api/snapshot", timeout=30))
    storage = snap.get("storage")
    print(f"backend ok={snap.get('ok')} nodes={len(snap.get('nodes') or [])} gfs_enabled={snap.get('gfs_enabled')}"
          + (f" storage: source={storage.get('source')} index={storage.get('index')} nodes={len(storage.get('nodes') or [])} "
             f"at_risk={storage.get('at_risk_total')} error={storage.get('error')}" if storage else ""))

    errors, seen = [], []
    with sync_playwright() as p:
        b = p.chromium.launch(args=["--no-sandbox"])
        pg = b.new_page(viewport={"width": 1400, "height": 1200})
        pg.on("console", lambda m: errors.append(f"console.{m.type}: {m.text}") if m.type == "error" else None)
        pg.on("pageerror", lambda e: errors.append(f"PAGEERROR: {e}"))
        pg.goto(a.url, wait_until="networkidle", timeout=30000)
        pg.wait_for_timeout(2500)   # first poll + paint
        tabs = pg.locator("#tabs .tab").all_inner_texts()
        print("tabs:", tabs)
        for t in tabs:
            pg.locator("#tabs .tab", has_text=t).first.click()
            pg.wait_for_timeout(600)
            view = pg.inner_text("#view")
            if "render error" in view:
                errors.append(f"{t}: {view[:200]}")
            seen.append((t, len(view)))
            pg.screenshot(path=str(out / f"{t.lower()}.png"), full_page=True)
        b.close()

    for t, n in seen:
        print(f"  {t:<10} {n:>6} chars")
    if a.expect_storage:
        if "Storage" not in [t for t, _ in seen]:
            errors.append("Storage tab missing (expected a GFS deployment)")
        elif storage and storage.get("error"):
            errors.append(f"Storage backend error: {storage['error']}")
        elif storage and not storage.get("nodes"):
            errors.append("Storage tab rendered but the roster is empty")
    if errors:
        print("ERRORS:"); [print("  " + e) for e in errors]
        sys.exit(1)
    print(f"render OK: {len(seen)} tabs, 0 errors, screenshots in {out}")


if __name__ == "__main__":
    main()
