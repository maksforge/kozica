#!/usr/bin/env python3
"""Phase 4's gate statistic (docs/phase-4.md § 1) from a `KozicaFrames` log.

    adb logcat -d -s KozicaFrames | python3 scripts/map/frame-stats.py

Frame time is the interval between two rendered frames while the camera moves: from its
`camera=move` through its `camera=idle`, which comes as the finger lifts, to the next `map=idle`,
when the fling and the tiles it brought in are drawn. A moving camera's first frame is timed from
the move. Frames drawn with the camera at rest are counted apart, so a long gap there is a pause and
a long one in motion is a stall. A log without camera lines, from a build before them, falls back to --idle: a gap over
it is a rest, anything shorter a frame.
"""

import argparse
import re
import statistics
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--idle", type=float, default=500.0)
parser.add_argument("--slow", type=float, default=100.0)
args = parser.parse_args()

events = sorted(
    (int(m.group(1)) / 1000, m.group(2) or "frame")
    for line in sys.stdin
    if (m := re.search(r"us=(\d+)(?: (camera=move|camera=idle|map=idle))?", line))
)
frames = [t for t, kind in events if kind == "frame"]


def report(intervals, prefix):
    if len(intervals) < 20:
        sys.exit(f"frame-stats: only {len(intervals)} frame intervals; was the log cleared after the swipes?")
    p95 = statistics.quantiles(intervals, n=20)[-1]
    slow = sum(1 for i in intervals if i > args.slow)
    print(prefix, end=" ")
    print(f"frames {len(intervals)}  median {statistics.median(intervals):.1f} ms  p95 {p95:.1f} ms  over {args.slow:.0f} ms: {slow}", end="")


if not any(kind.startswith("camera") for _, kind in events):
    intervals = [b - a for a, b in zip(frames, frames[1:]) if b - a <= args.idle]
    gaps = sorted((b - a for a, b in zip(frames, frames[1:]) if b - a > args.idle), reverse=True)
    report(intervals, f"rest gaps {len(gaps)}, longest {gaps[0] if gaps else 0:.0f} ms;")
    print()
    sys.exit()

moves = 0
moving = settling = in_motion = False
last = None
intervals = []
rest_frames = 0
for t, kind in events:
    if kind == "camera=move":
        if not moving:
            moves += 1
            last = t
        moving = in_motion = True
        settling = False
    elif kind == "camera=idle":
        settling = moving
    elif kind == "map=idle":
        if settling:
            moving = settling = in_motion = False
    else:
        if in_motion and last is not None:
            intervals.append(t - last)
        elif last is not None:
            rest_frames += 1
        last = t
        in_motion = moving

report(intervals, f"camera moves {moves};")
print(f"  longest {max(intervals):.0f} ms; at rest {rest_frames} frames")
