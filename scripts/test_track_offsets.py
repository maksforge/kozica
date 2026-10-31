"""docs/phase-6b.md § 1's apparatus, track-offsets.py, against one straight edge:

    python3 -m unittest scripts/test_track_offsets.py
"""

import math
import runpy
import tempfile
import unittest
from pathlib import Path

network = runpy.run_path(str(Path(__file__).with_name("build-network.py")), run_name="test")
offsets = runpy.run_path(str(Path(__file__).with_name("track-offsets.py")), run_name="test")
Fix = offsets["Fix"]
NORTH = 1 / (6_371_008.8 * math.pi / 180)


def straight_network() -> object:
    """One edge east along 49° N from 20.00° to 20.02° E."""
    points = [(49.0, 20.0), (49.0, 20.01), (49.0, 20.02)]
    lengths = [network["distance"](*a, *b) for a, b in zip(points, points[1:])]
    heights = [1000.0] * 3
    edge = network["Edge"](1, [(1, *points[0]), (2, *points[1]), (3, *points[2])], heights, sum(lengths),
                           network["climb"](lengths, heights), 0, 0)
    with tempfile.TemporaryDirectory() as folder:
        target = Path(folder) / "trails.network"
        network["write_network"]([edge], target)
        return offsets["Network"](target.read_bytes())


def walk(offsets_north: list[float], every_ms: int = 5_000, accuracy: float | None = 10.0) -> list:
    """A fix every [every_ms] along the edge, each [offsets_north] metres north of it."""
    return [Fix(i * every_ms, 1, 49.0 + north * NORTH, 20.0 + i * 0.0001, accuracy)
            for i, north in enumerate(offsets_north)]


class TrackOffsetsTest(unittest.TestCase):
    net = straight_network()

    def firings(self, fixes):
        metres = [self.net.distances(f.latitude, f.longitude)[0][0] for f in fixes]
        return offsets["firings"](fixes, metres)

    def test_distance_is_to_the_edge(self):
        self.assertAlmostEqual(self.net.distances(49.0 + 25 * NORTH, 20.005)[0][0], 25.0, delta=0.5)

    def test_thirty_seconds_beyond_fires_once_and_clears_on_the_way_back(self):
        found = self.firings(walk([0] * 4 + [60] * 8 + [0] * 4))
        self.assertEqual(1, len(found))
        self.assertEqual(20_000, found[0].since_ms)
        self.assertEqual(60_000, found[0].end_ms)

    def test_under_thirty_seconds_does_not_fire(self):
        self.assertEqual([], self.firings(walk([0] * 4 + [60] * 6 + [0] * 4)))

    def test_the_fixes_own_accuracy_widens_the_distance(self):
        self.assertEqual([], self.firings(walk([45] * 12, accuracy=48.0)))
        self.assertEqual(1, len(self.firings(walk([45] * 12, accuracy=10.0))))

    def test_a_fix_worse_than_fifty_metres_does_not_count(self):
        self.assertEqual([], self.firings(walk([60] * 12, accuracy=60.0)))

    def test_a_gap_over_thirty_seconds_restarts_the_count(self):
        fixes = [Fix(0, 1, 49.0 + 60 * NORTH, 20.0, 10.0), Fix(20_000, 1, 49.0 + 60 * NORTH, 20.001, 10.0),
                 Fix(55_000, 1, 49.0 + 60 * NORTH, 20.002, 10.0), Fix(75_000, 1, 49.0 + 60 * NORTH, 20.003, 10.0)]
        self.assertEqual([], self.firings(fixes))

    def test_a_gpx_without_accuracy_counts_every_fix_at_forty_metres(self):
        gpx = """<?xml version="1.0"?><gpx xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>""" + "".join(
            f'<trkpt lat="{49.0 + 60 * NORTH}" lon="{20.0 + i * 0.0001}"><time>2026-10-31T10:00:{i * 5:02d}Z</time></trkpt>'
            for i in range(10)
        ) + "</trkseg></trk></gpx>"
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "walk.gpx"
            path.write_text(gpx)
            fixes = offsets["read_gpx"](path)
        self.assertEqual(10, len(fixes))
        self.assertIsNone(fixes[0].accuracy)
        self.assertEqual(1, len(self.firings(fixes)))


if __name__ == "__main__":
    unittest.main()
