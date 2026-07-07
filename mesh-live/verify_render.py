#!/usr/bin/env python3
"""Headless render check: load the dashboard, capture console errors, screenshot tabs."""
import sys
from playwright.sync_api import sync_playwright

OUT = "/tmp/claude-1000/-scratch-llm-crawl/2be5e9ca-1c5b-4245-ab7a-3785f175d415/scratchpad"
URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8900/"
TABS = ["Overview", "Topology", "Links", "Nodes", "Metrics"]

errors, logs = [], []
with sync_playwright() as p:
    b = p.chromium.launch(args=["--no-sandbox"])
    pg = b.new_page(viewport={"width": 1400, "height": 1600})
    pg.on("console", lambda m: (logs.append(f"{m.type}: {m.text}"),
                                errors.append(m.text) if m.type == "error" else None))
    pg.on("pageerror", lambda e: errors.append(f"PAGEERROR: {e}"))
    pg.goto(URL, wait_until="networkidle", timeout=30000)
    pg.wait_for_timeout(3500)  # let React mount + first poll
    # confirm React actually mounted
    root_txt = pg.inner_text("#root")[:200]
    print("ROOT TEXT:", repr(root_txt))
    for t in TABS:
        try:
            pg.click(f"button:has-text('{t}')", timeout=4000)
            pg.wait_for_timeout(1800)
            pg.screenshot(path=f"{OUT}/dash_{t.lower()}.png", full_page=True)
            print(f"  shot {t}")
        except Exception as e:
            print(f"  FAIL tab {t}: {e}")
    b.close()

print("\n=== CONSOLE ERRORS ===")
for e in errors[:30]:
    print(" ", e)
print(f"total errors: {len(errors)}")
