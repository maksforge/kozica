#!/usr/bin/env python3
"""Did the gate recorder live? Phase 6's gate reading (docs/phase-6.md § 1).

Reads `gate.db` from the debug build's `RecorderService`, pulled off the phone, and prints the point
count, the span, every gap over 60 s in storing fixes of any accuracy outside a pause, the gaps § 2's
5 m and 30 m filters would have left, the battery per hour and the motion events. Gaps are measured
on `stored_at`, the clock the events use, from each segment's start or resume to its pause or stop.
A gap the service's once-a-minute `gnss` heartbeat spans is "alive, no fix"; the verdict is verdict
1's bar on the rest: no silence, neither fix nor heartbeat, over 2 minutes outside a pause.

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
OPENS = ("start", "resume")
CLOSES = ("pause", "pause_no_sensor", "stop")


@dataclass
class Fix:
    stored_ms: int
    fix_ms: int
    segment: int
    latitude: float
    longitude: float
    accuracy: float | None


@dataclass
class Event:
    at: int
    kind: str
    segment: int
    battery: int | None
    satellites: int | None
    used_in_fix: int | None


@dataclass
class Gap:
    start_ms: int
    end_ms: int
    heartbeats: int
    silent_s: float

    @property
    def seconds(self) -> float:
        return (self.end_ms - self.start_ms) / 1000

    @property
    def alive(self) -> bool:
        return self.heartbeats > 0 and self.silent_s <= GAP_ALLOWED_S


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


def segment_edges(events: list[Event]) -> dict[int, tuple[int | None, int | None]]:
    opened: dict[int, int] = {}
    closed: dict[int, int] = {}
    for e in events:
        if e.kind in OPENS:
            opened.setdefault(e.segment, e.at)
        elif e.kind in CLOSES:
            closed.setdefault(e.segment, e.at)
    return {s: (opened.get(s), closed.get(s)) for s in opened.keys() | closed.keys()}


def gaps(fixes: list[Fix], events: list[Event]) -> list[Gap]:
    edges = segment_edges(events)
    heartbeats = [e.at for e in events if e.kind == "gnss"]
    found = []
    for segment in sorted({f.segment for f in fixes} | edges.keys()):
        opened, closed = edges.get(segment, (None, None))
        marks = sorted(
            m for m in [opened, *(f.stored_ms for f in fixes if f.segment == segment), closed] if m is not None
        )
        for a, b in zip(marks, marks[1:]):
            if (b - a) / 1000 <= GAP_REPORTED_S:
                continue
            inside = [h for h in heartbeats if a < h < b]
            beats = [a, *inside, b]
            silent_s = max(y - x for x, y in zip(beats, beats[1:])) / 1000
            found.append(Gap(a, b, len(inside), silent_s))
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


def print_gaps(title: str, found: list[Gap]) -> None:
    print(f"{title}: {len(found)} over {GAP_REPORTED_S} s")
    groups = (("silent", [g for g in found if not g.alive]), ("alive, no fix", [g for g in found if g.alive]))
    for name, group in groups:
        print(f"  {name}: {len(group)}")
        for g in group:
            print(f"    {stamp(g.start_ms)} → {stamp(g.end_ms)}  {g.seconds:.0f} s, "
                  f"{g.heartbeats} heartbeats, longest silence {g.silent_s:.0f} s")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("database", nargs="?", type=Path)
    parser.add_argument("--pull", action="store_true", help="pull gate.db from the debug build first")
    parser.add_argument("-s", "--serial", help="adb serial, needed with --pull")
    parser.add_argument("--since", help="ignore rows stored before this local time, e.g. 2026-10-27T09:00")
    args = parser.parse_args()
    if args.pull == (args.database is not None) or (args.pull and not args.serial):
        parser.error("give a database path, or --pull with -s <serial>")

    path = pull(args.serial) if args.pull else args.database
    since_ms = int(datetime.fromisoformat(args.since).timestamp() * 1000) if args.since else 0
    db = sqlite3.connect(path)
    fixes = [
        Fix(*row)
        for row in db.execute(
            "SELECT stored_at, fix_time, segment, latitude, longitude, accuracy FROM gate_points "
            "WHERE stored_at >= ? ORDER BY stored_at",
            (since_ms,),
        )
    ]
    columns = {row[1] for row in db.execute("PRAGMA table_info(gate_events)")}
    satellites = "satellites, used_in_fix" if "used_in_fix" in columns else "NULL, NULL"
    events = [
        Event(*row)
        for row in db.execute(
            f"SELECT at, kind, segment, battery, {satellites} FROM gate_events WHERE at >= ? ORDER BY at",
            (since_ms,),
        )
    ]
    print(f"database: {path}")
    if fixes:
        span_s = (fixes[-1].stored_ms - fixes[0].stored_ms) / 1000
        print(f"fixes: {len(fixes)} in {len({f.segment for f in fixes})} segments, "
              f"{stamp(fixes[0].stored_ms)} → {stamp(fixes[-1].stored_ms)} ({span_s / 3600:.2f} h)")
        accurate = sum(1 for f in fixes if f.accuracy is not None and f.accuracy <= RELEASE_MAX_ACCURACY_M)
        print(f"within {RELEASE_MAX_ACCURACY_M} m accuracy: {accurate}")
        late = max(fixes, key=lambda f: f.stored_ms - f.fix_ms)
        print(f"largest lag from a fix to its storing: {(late.stored_ms - late.fix_ms) / 1000:.1f} s, "
              f"stored {stamp(late.stored_ms)}")

    print("events:")
    for e in events:
        if e.kind != "gnss":
            print(f"  {stamp(e.at)}  {e.kind:<16} segment {e.segment}  battery {e.battery} %")
    heartbeats = [e for e in events if e.kind == "gnss"]
    if heartbeats:
        used = max(e.used_in_fix or 0 for e in heartbeats)
        seen = max(e.satellites or 0 for e in heartbeats)
        print(f"gnss heartbeats: {len(heartbeats)}, at most {used} satellites in a fix, {seen} seen")
    batteries = [(e.at, e.battery) for e in events if e.battery is not None]
    if len(batteries) >= 2 and batteries[-1][0] > batteries[0][0]:
        hours = (batteries[-1][0] - batteries[0][0]) / 3_600_000
        spent = batteries[0][1] - batteries[-1][1]
        print(f"battery: {spent} % over {hours:.2f} h, {spent / hours:.1f} %/h (events, not a discharge curve)")

    every = gaps(fixes, events)
    print_gaps("gaps in storing, segment edges included, outside pauses", every)
    print_gaps("gaps the release filters would leave", gaps(release_filtered(fixes), events))

    deaths = []
    previous = None
    lifecycle = [e for e in events if e.kind in OPENS + CLOSES]
    for e in lifecycle:
        if e.kind == "start" and previous not in (None, "stop"):
            deaths.append(f"a start at {stamp(e.at)} with no stop before it: the run before it died")
        previous = e.kind
    if not lifecycle or lifecycle[-1].kind != "stop":
        deaths.append("no stop event at the end: the recorder is still running, or died")
    for death in deaths:
        print(f"⚠ {death}")
    if not fixes:
        print("no fixes: the reading says nothing")
        return 1
    silence = max((g.silent_s for g in every), default=0)
    print(f"longest silence (no fix and no heartbeat): {silence:.0f} s")
    passed = silence <= GAP_ALLOWED_S and not deaths
    print(f"verdict bar (no silence over {GAP_ALLOWED_S} s outside a pause, no death): {'PASS' if passed else 'FAIL'}")
    return 0 if passed else 2


if __name__ == "__main__":
    sys.exit(main())
