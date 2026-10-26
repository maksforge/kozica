#!/usr/bin/env python3
"""Did the gate recorder live? Phase 6's gate reading (docs/phase-6.md § 1).

Reads `gate.db` from the debug build's `RecorderService`, pulled off the phone, and prints the point
count, the span, every gap over 60 s between fixes of any accuracy outside a pause, the longest, the
gaps § 2's 5 m and 30 m filters would have left, the battery per hour and the motion events. The
verdict is verdict 1's bar: no gap over 2 minutes outside a pause.

Usage:

    python3 scripts/track-gaps.py --pull -s 132bc856         # pull the debug build's gate.db, then read it
    python3 scripts/track-gaps.py path/to/gate.db            # read a copy already pulled
    python3 scripts/track-gaps.py --pull -s <serial> --since 2026-10-27T09:00
"""

from __future__ import annotations

import argparse
import math
import sqlite3
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import project  # noqa: E402

GAP_REPORTED_S = 60
GAP_ALLOWED_S = 120
RELEASE_MIN_METRES = 5
RELEASE_MAX_ACCURACY_M = 30


@dataclass
class Fix:
    time_ms: int
    segment: int
    latitude: float
    longitude: float
    accuracy: float | None


def pull(serial: str) -> Path:
    out = Path(tempfile.mkdtemp(prefix="gate-")) / "gate.db"
    with out.open("wb") as f:
        subprocess.run(
            ["adb", "-s", serial, "exec-out", "run-as", project.DEBUG_APPLICATION_ID, "cat", "databases/gate.db"],
            stdout=f,
            check=True,
        )
    return out


def metres(a: Fix, b: Fix) -> float:
    lat = math.radians((a.latitude + b.latitude) / 2)
    dy = (b.latitude - a.latitude) * 111_320
    dx = (b.longitude - a.longitude) * 111_320 * math.cos(lat)
    return math.hypot(dx, dy)


def stamp(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M:%S")


def gaps(fixes: list[Fix]) -> list[tuple[Fix, Fix, float]]:
    found = []
    for a, b in zip(fixes, fixes[1:]):
        if a.segment != b.segment:
            continue
        seconds = (b.time_ms - a.time_ms) / 1000
        if seconds > GAP_REPORTED_S:
            found.append((a, b, seconds))
    return found


def release_filtered(fixes: list[Fix]) -> list[Fix]:
    kept: list[Fix] = []
    for fix in fixes:
        if fix.accuracy is None or fix.accuracy > RELEASE_MAX_ACCURACY_M:
            continue
        if kept and kept[-1].segment == fix.segment and metres(kept[-1], fix) < RELEASE_MIN_METRES:
            continue
        kept.append(fix)
    return kept


def print_gaps(title: str, found: list[tuple[Fix, Fix, float]]) -> None:
    print(f"{title}: {len(found)} over {GAP_REPORTED_S} s")
    for a, b, seconds in found:
        print(f"  {stamp(a.time_ms)} → {stamp(b.time_ms)}  {seconds:.0f} s")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("database", nargs="?", type=Path)
    parser.add_argument("--pull", action="store_true", help="pull gate.db from the debug build first")
    parser.add_argument("-s", "--serial", help="adb serial, needed with --pull")
    parser.add_argument("--since", help="ignore rows before this local time, e.g. 2026-10-27T09:00")
    args = parser.parse_args()
    if args.pull == (args.database is not None) or (args.pull and not args.serial):
        parser.error("give a database path, or --pull with -s <serial>")

    path = pull(args.serial) if args.pull else args.database
    since_ms = int(datetime.fromisoformat(args.since).timestamp() * 1000) if args.since else 0
    db = sqlite3.connect(path)
    fixes = [
        Fix(*row)
        for row in db.execute(
            "SELECT fix_time, segment, latitude, longitude, accuracy FROM gate_points "
            "WHERE fix_time >= ? ORDER BY fix_time",
            (since_ms,),
        )
    ]
    events = db.execute(
        "SELECT at, kind, segment, battery FROM gate_events WHERE at >= ? ORDER BY at", (since_ms,)
    ).fetchall()
    print(f"database: {path}")
    if not fixes:
        print("no fixes: the reading says nothing")
        return 1

    span_s = (fixes[-1].time_ms - fixes[0].time_ms) / 1000
    print(f"fixes: {len(fixes)} in {len({f.segment for f in fixes})} segments, "
          f"{stamp(fixes[0].time_ms)} → {stamp(fixes[-1].time_ms)} ({span_s / 3600:.2f} h)")
    accurate = sum(1 for f in fixes if f.accuracy is not None and f.accuracy <= RELEASE_MAX_ACCURACY_M)
    print(f"within {RELEASE_MAX_ACCURACY_M} m accuracy: {accurate}")

    print("events:")
    for at, kind, segment, battery in events:
        print(f"  {stamp(at)}  {kind:<16} segment {segment}  battery {battery} %")
    batteries = [(at, battery) for at, _, _, battery in events if battery is not None]
    if len(batteries) >= 2 and batteries[-1][0] > batteries[0][0]:
        hours = (batteries[-1][0] - batteries[0][0]) / 3_600_000
        used = batteries[0][1] - batteries[-1][1]
        print(f"battery: {used} % over {hours:.2f} h, {used / hours:.1f} %/h (events, not a discharge curve)")

    every = gaps(fixes)
    print_gaps("gaps between fixes of any accuracy, outside pauses", every)
    print_gaps("gaps the release filters would leave", gaps(release_filtered(fixes)))

    longest = max((s for _, _, s in every), default=0)
    deaths = []
    previous = None
    for at, kind, _, _ in events:
        if kind == "start" and previous not in (None, "stop"):
            deaths.append(f"a start at {stamp(at)} with no stop before it: the run before it died")
        if kind == "stop":
            before = [f for f in fixes if f.time_ms <= at]
            silent_s = (at - before[-1].time_ms) / 1000 if before else math.inf
            if silent_s > GAP_ALLOWED_S and previous not in ("pause", "pause_no_sensor"):
                deaths.append(f"the stop at {stamp(at)} came {silent_s:.0f} s after the last fix")
        previous = kind
    if not events or events[-1][1] != "stop":
        deaths.append("no stop event at the end: the recorder is still running, or died")
    for death in deaths:
        print(f"⚠ {death}")
    print(f"longest gap between fixes: {longest:.0f} s")
    passed = longest <= GAP_ALLOWED_S and not deaths
    print(f"verdict bar (no gap over {GAP_ALLOWED_S} s outside a pause, no death): {'PASS' if passed else 'FAIL'}")
    return 0 if passed else 2


if __name__ == "__main__":
    sys.exit(main())
