"""docs/phase-5.md § Tests, for build-network.py:

    python3 -m unittest scripts/test_build_network.py
    python3 scripts/test_build_network.py --write    # rewrites the fixture TrailNetworkTest reads
"""

import math
import runpy
import sys
import tempfile
import unittest
from pathlib import Path

network = runpy.run_path(str(Path(__file__).with_name("build-network.py")), run_name="test")
FIXTURE = Path(__file__).resolve().parent.parent / "app/src/test/resources/trails-fixture.network"
Edge = network["Edge"]


def edge(way: int, refs: list[int], points: list[tuple[float, float]], heights: list[float], flags: int = 0, sac: int = 0):
    lengths = [network["distance"](*a, *b) for a, b in zip(points, points[1:])]
    located = [(ref, lat, lon) for ref, (lat, lon) in zip(refs, points)]
    return Edge(way, located, heights, sum(lengths), network["climb"](lengths, heights), flags, sac)


def fixture_edges() -> list:
    """A–B–C along a flat line, B→C one-way; A–D up and D–C down; E–F an island far from the rest.

    Node refs are OSM-like ids; only the ends of an edge are nodes of the network."""
    a, b, c, d = (49.0, 20.0), (49.0, 20.01), (49.0, 20.02), (49.01, 20.01)
    e, f = (49.2, 20.2), (49.2, 20.21)
    return [
        edge(100, [1, 2], [a, b], [500.0, 500.0]),
        edge(101, [2, 0, 3], [b, (49.0, 20.015), c], [500.0, 500.0, 500.0], flags=network["FORWARD_ONLY"]),
        edge(102, [1, 0, 4], [a, (49.005, 20.005), d], [500.0, 600.0, 700.0], sac=3),
        edge(103, [4, 3], [d, c], [700.0, 500.0]),
        edge(104, [5, 6], [e, f], [900.0, 900.0]),
    ]


class FoldTest(unittest.TestCase):
    def test_the_names_a_user_types_without_accents(self):
        fold = network["fold"]
        self.assertEqual(fold("Śnieżka"), "sniezka")
        self.assertEqual(fold("Łomnica"), "lomnica")
        self.assertEqual(fold("Gerlachovský štít"), "gerlachovsky stit")
        self.assertEqual(fold("Sněžka"), "snezka")
        self.assertEqual(fold("Hala Gąsienicowa"), "hala gasienicowa")
        self.assertEqual(fold("Schronisko PTTK „Na Hali”"), "schronisko pttk na hali")


class ClimbTest(unittest.TestCase):
    def test_noise_inside_the_dead_band_adds_no_climb(self):
        heights = [500 + (2 if i % 2 else -2) for i in range(41)]
        climb = network["climb"]([25.0] * 40, heights)
        self.assertEqual((climb.ascent, climb.descent), (0, 0))

    def test_a_steady_climb_counts_in_full(self):
        climb = network["climb"]([25.0] * 50, [500 + 2 * i for i in range(51)])
        self.assertAlmostEqual(climb.ascent, 100)
        self.assertEqual(climb.steep_ascent, 0)

    def test_a_steep_descent_is_counted_as_steep(self):
        climb = network["climb"]([25.0] * 10, [600 - 10 * i for i in range(11)])
        self.assertAlmostEqual(climb.descent, 100)
        self.assertAlmostEqual(climb.steep_descent, 100)

    def test_a_gentle_descent_costs_no_time(self):
        climb = network["climb"]([25.0] * 50, [600 - 2 * i for i in range(51)])
        self.assertAlmostEqual(climb.descent, 100)
        self.assertEqual(climb.steep_descent, 0)
        self.assertAlmostEqual(network["walking_seconds"](1250, climb.ascent, climb.steep_descent), 1125)


class WalkingTimeTest(unittest.TestCase):
    def test_the_pttk_marker_rule(self):
        seconds = network["walking_seconds"]
        self.assertAlmostEqual(seconds(4000, 0, 0), 3600)
        self.assertAlmostEqual(seconds(4000, 100, 0), 3600 + 600)
        self.assertAlmostEqual(seconds(4000, 0, 100), 3600 + 300)


class OnewayTest(unittest.TestCase):
    def test_walkers_follow_their_own_tags_and_never_a_road_s(self):
        oneway, forward, backward = network["oneway"], network["FORWARD_ONLY"], network["BACKWARD_ONLY"]
        self.assertEqual(oneway({"highway": "path", "oneway": "yes"}), forward)
        self.assertEqual(oneway({"highway": "steps", "oneway": "-1"}), backward)
        self.assertEqual(oneway({"highway": "track", "oneway": "yes"}), 0)
        self.assertEqual(oneway({"highway": "unclassified", "oneway": "yes"}), 0)
        self.assertEqual(oneway({"highway": "track", "oneway:foot": "yes"}), forward)
        self.assertEqual(oneway({"highway": "path", "oneway": "yes", "oneway:foot": "no"}), 0)
        self.assertEqual(oneway({"highway": "path", "foot:backward": "no"}), forward)


class PointOnSurfaceTest(unittest.TestCase):
    def test_a_point_inside_a_crescent(self):
        ring = [(0, 0), (0, 10), (10, 10), (10, 0), (8, 0), (8, 8), (2, 8), (2, 0), (0, 0)]
        lat, lon = network["point_on_surface"]([[(lon, lat) for lat, lon in ring]])
        self.assertTrue(0 < lat < 10 and (0 < lon < 2 or 8 < lon < 10 or 8 < lat))


class FixtureTest(unittest.TestCase):
    def test_the_committed_fixture_is_what_the_writer_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            written = Path(directory) / "fixture.network"
            network["write_network"](fixture_edges(), written)
            self.assertEqual(written.read_bytes(), FIXTURE.read_bytes(),
                             "run `python3 scripts/test_build_network.py --write` and commit the fixture")

    def test_the_lengths_in_it(self):
        edges = fixture_edges()
        self.assertTrue(math.isclose(edges[0].length, 730, rel_tol=0.01))


class Rising:
    """4 m up every 0.01° east, under the dead band for any one way below."""

    def height(self, lat: float, lon: float) -> float:
        return 500 + 400 * (lon - 20)


def way(refs: list[int], tags: dict | None = None) -> tuple:
    return [(ref, 49.0, 20 + ref / 100) for ref in refs], tags or {}


def edges_of(ways: dict) -> list:
    return network["edges_of"](ways, Rising())[0]


class EdgesTest(unittest.TestCase):
    def test_ways_meeting_end_to_end_are_one_edge_so_their_climb_counts(self):
        edges = edges_of({1: way([1, 2]), 2: way([3, 2]), 3: way([3, 4])})
        self.assertEqual([[ref for ref, _, _ in edge.points] for edge in edges], [[1, 2, 3, 4]])
        self.assertAlmostEqual(edges[0].climb.ascent, 12)

    def test_a_loop_is_kept_whole(self):
        def walked(ways: dict) -> list:
            return sorted(sorted(tuple(sorted(pair)) for pair in zip(refs, refs[1:])) for edge in edges_of(ways)
                          for refs in [[ref for ref, _, _ in edge.points]])

        self.assertEqual(walked({1: way([5, 1]), 2: way([1, 2, 3, 1])}), [[(1, 2), (2, 3)], [(1, 3)], [(1, 5)]])
        self.assertEqual(walked({1: way([1, 2, 3, 4, 2, 5])}), [[(1, 2)], [(2, 3), (3, 4)], [(2, 4)], [(2, 5)]])

    def test_a_junction_or_a_change_of_walking_cuts_an_edge(self):
        self.assertEqual(len(edges_of({1: way([1, 2]), 2: way([2, 3]), 3: way([2, 5])})), 3)
        self.assertEqual(len(edges_of({1: way([1, 2]), 2: way([2, 3], {"highway": "path", "oneway": "yes"})})), 2)


if __name__ == "__main__":
    if sys.argv[1:] == ["--write"]:
        FIXTURE.parent.mkdir(parents=True, exist_ok=True)
        print(network["write_network"](fixture_edges(), FIXTURE))
    else:
        unittest.main()
