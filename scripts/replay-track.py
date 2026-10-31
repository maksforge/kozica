#!/usr/bin/env python3
"""Replays a walk through the debug build's position override, for phase 6b's R3 and R4.

Sends each point to `PositionReceiver` in order, with its own time rebased to start at `--t0` (now
unless given) and sent `--speed` times faster than it was walked, so Following's window, which
reads the fix's own time, sees walking pace (docs/phase-6b.md, Readings). The points are a GPX
track's, each with its time, or a Route's line from `RouteReceiver`'s `cache/route.txt`, walked at
`--kmh` with a point every `--every` metres.

`--first` and `--last` pick a run of the points, by the index each line prints, and `--t0` keeps the
timeline across runs, so a replay paused for R4's force-stop goes on from where it stopped.

Usage:

    python3 scripts/replay-track.py walk.gpx --speed 10 -s mnbm7hw8ivdqzx9l
    python3 scripts/replay-track.py --route --speed 20 -s <serial>          # pulls cache/route.txt
    python3 scripts/replay-track.py --route route.txt --last 40 --t0 <ms> -s <serial>
"""

from __future__ import annotations

import argparse
import math
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from project import DEBUG_APPLICATION_ID  # noqa: E402

EARTH_METRES = 6_371_008.8


def metres(a: tuple[float, float], b: tuple[float, float]) -> float:
    la1, lo1, la2, lo2 = map(math.radians, (*a, *b))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * EARTH_METRES * math.asin(math.sqrt(h))


def gpx_points(path: Path) -> list[tuple[float, float, float]]:
    """(lat, lon, seconds from the first point) for every trkpt with a time."""
    points = []
    for element in ET.parse(path).iter():
        if not element.tag.endswith("trkpt"):
            continue
        stamp = next((c.text for c in element if c.tag.endswith("time")), None)
        if stamp is None:
            continue
        at = datetime.fromisoformat(stamp.replace("Z", "+00:00")).timestamp()
        points.append((float(element.get("lat")), float(element.get("lon")), at))
    if not points:
        sys.exit(f"{path}: no track points with a time")
    start = points[0][2]
    return [(la, lo, at - start) for la, lo, at in points]


def route_points(path: Path, kmh: float, every: float) -> list[tuple[float, float, float]]:
    """The line resampled every `every` metres, timed at `kmh`."""
    line = [tuple(map(float, row.split()[:2])) for row in path.read_text().splitlines() if row.strip()]
    if len(line) < 2:
        sys.exit(f"{path}: fewer than two points")
    speed = kmh * 1000 / 3600
    points = [(line[0][0], line[0][1], 0.0)]
    walked = 0.0
    next_at = every
    for a, b in zip(line, line[1:]):
        span = metres(a, b)
        while span > 0 and next_at <= walked + span:
            t = (next_at - walked) / span
            points.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, next_at / speed))
            next_at += every
        walked += span
    if points[-1][:2] != line[-1]:
        points.append((line[-1][0], line[-1][1], walked / speed))
    return points


def pull_route(serial: str | None) -> Path:
    out = Path(tempfile.gettempdir()) / f"kozica-route-{serial or 'default'}.txt"
    adb = ["adb"] + (["-s", serial] if serial else [])
    text = subprocess.run(
        adb + ["exec-out", "run-as", DEBUG_APPLICATION_ID, "cat", "cache/route.txt"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    out.write_text(text)
    return out


def send(serial: str | None, lat: float, lon: float, accuracy: float, at_ms: int) -> None:
    adb = ["adb"] + (["-s", serial] if serial else [])
    subprocess.run(
        adb
        + [
            "shell",
            "am",
            "broadcast",
            "-n",
            f"{DEBUG_APPLICATION_ID}/app.kozica.debug.PositionReceiver",
            "-f",
            "0x00000020",
            "--ef",
            "lat",
            f"{lat:.7f}",
            "--ef",
            "lon",
            f"{lon:.7f}",
            "--ef",
            "accuracy",
            f"{accuracy:g}",
            "--el",
            "time",
            str(at_ms),
        ],
        check=True,
        capture_output=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("gpx", nargs="?", type=Path, help="a GPX file whose track points carry times")
    parser.add_argument("--route", nargs="?", const="pull", help="a route.txt, or pull the phone's when bare")
    parser.add_argument("--kmh", type=float, default=4.0, help="walking speed along a Route's line")
    parser.add_argument("--every", type=float, default=10.0, help="metres between a Route's points")
    parser.add_argument("--speed", type=float, default=1.0, help="how many times faster than walked")
    parser.add_argument("--accuracy", type=float, default=10.0, help="each fix's accuracy in metres")
    parser.add_argument("--first", type=int, default=0, help="index of the first point to send")
    parser.add_argument("--last", type=int, help="index of the last point to send")
    parser.add_argument("--t0", type=int, help="epoch ms the first point is rebased to, now by default")
    parser.add_argument("-s", "--serial", help="the device, as adb -s takes it")
    args = parser.parse_args()

    if (args.gpx is None) == (args.route is None):
        parser.error("give a GPX file or --route, not both")
    if args.gpx is not None:
        points = gpx_points(args.gpx)
    else:
        path = pull_route(args.serial) if args.route == "pull" else Path(args.route)
        points = route_points(path, args.kmh, args.every)

    t0 = args.t0 if args.t0 is not None else int(time.time() * 1000)
    last = len(points) - 1 if args.last is None else min(args.last, len(points) - 1)
    print(f"t0={t0} points={len(points)} sending {args.first}..{last} at {args.speed:g}x", flush=True)
    started = time.monotonic()
    origin = points[args.first][2]
    for index in range(args.first, last + 1):
        lat, lon, seconds = points[index]
        wait = (seconds - origin) / args.speed - (time.monotonic() - started)
        if wait > 0:
            time.sleep(wait)
        at_ms = t0 + int(seconds * 1000)
        send(args.serial, lat, lon, args.accuracy, at_ms)
        print(f"{index} time={at_ms} lat={lat:.6f} lon={lon:.6f}", flush=True)


if __name__ == "__main__":
    main()
