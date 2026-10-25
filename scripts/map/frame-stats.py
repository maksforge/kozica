#!/usr/bin/env python3
"""Phase 4's gate statistic (docs/phase-4.md § 1) from a `KozicaFrames` log.

    adb logcat -d -s KozicaFrames | python3 scripts/map/frame-stats.py

Frame time is the interval between two rendered frames. A gap over --idle ms is the map at rest
between swipes, not a frame, so it splits the run rather than counting.
"""

import argparse
import re
import statistics
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--idle", type=float, default=500.0)
parser.add_argument("--slow", type=float, default=100.0)
args = parser.parse_args()

times = [int(m.group(1)) / 1000 for line in sys.stdin if (m := re.search(r"us=(\d+)", line))]
intervals = [b - a for a, b in zip(times, times[1:]) if b - a <= args.idle]
if len(intervals) < 20:
    sys.exit(f"frame-stats: only {len(intervals)} frame intervals; was the log cleared after the swipes?")
p95 = statistics.quantiles(intervals, n=20)[-1]
slow = sum(1 for i in intervals if i > args.slow)
gaps = sorted((b - a for a, b in zip(times, times[1:]) if b - a > args.idle), reverse=True)
print(f"rest gaps {len(gaps)}, longest {gaps[0] if gaps else 0:.0f} ms;", end=" ")
print(f"frames {len(intervals)}  median {statistics.median(intervals):.1f} ms  p95 {p95:.1f} ms  over {args.slow:.0f} ms: {slow}")
