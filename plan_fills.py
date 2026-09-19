#!/usr/bin/env python3
"""
Find the silences in a finished cut and size commentary lines to them.

A live demo has phases where the system is working and the narration has already finished — an agent
being declared lost, a control plane coming back. Those are the gaps a viewer reads as dead air. This
reads the placement report, lists every gap over a threshold with the phase it follows and how many
words will fit, and (with --write) emits the render script for the missing lines so they can be
rendered in the SAME voice (--ref-wav) and folded in by finish_video.py --fill.
"""
import argparse
import json
from pathlib import Path

WPS = 155 / 60.0          # measured speaking rate of the narration model


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", required=True)
    ap.add_argument("--script", required=True, help="the phase script, for captions")
    ap.add_argument("--min-gap", type=float, default=6.0)
    ap.add_argument("--lead", type=float, default=1.2, help="pause before a commentary line starts")
    ap.add_argument("--tail", type=float, default=1.0, help="pause left before the next phase speaks")
    ap.add_argument("--write", default=None, help="write a fill script here, with text: '' to fill in")
    a = ap.parse_args()

    r = json.load(open(a.report))
    phases = {p["id"]: p for p in json.load(open(a.script))}
    clips = [c for c in r["clips"] if "at" in c]
    gaps = r["gaps"]

    rows = []
    for i, g in enumerate(gaps):
        if g < a.min_gap:
            continue
        after = clips[i]["id"]
        room = g - a.lead - a.tail
        rows.append({"after": after, "gap_s": round(g, 1), "room_s": round(room, 1),
                     "words": int(room * WPS),
                     "caption": (phases.get(after, {}).get("caption") or "")[:52]})
    rows.sort(key=lambda x: -x["gap_s"])
    total = sum(g for g in gaps)
    print(f"{len(clips)} lines, {total:.0f}s of silence, max gap {max(gaps):.1f}s, "
          f"median {sorted(gaps)[len(gaps)//2]:.1f}s")
    print(f"gaps over {a.min_gap}s: {len(rows)}")
    for x in rows:
        print(f"  {x['gap_s']:5.1f}s after {x['after']}  ->  room for ~{x['words']:2d} words   {x['caption']}")

    if a.write:
        out = [{"id": f"x{i:02d}", "after": x["after"], "text": "",
                "window_s": x["room_s"], "caption": f"fill after {x['after']}",
                "_max_words": x["words"]} for i, x in enumerate(rows)]
        Path(a.write).write_text(json.dumps(out, indent=1))
        print(f"\nwrote {len(out)} empty fill slots -> {a.write}")


if __name__ == "__main__":
    main()
