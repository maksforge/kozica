"""docs/phase-6c.md § Tests, a regional Trail in pieces, for KozicaProfile.java:

    python3 -m unittest scripts/test_kozica_profile.py
    map-data/network-venv/bin/python scripts/test_kozica_profile.py --write    # rewrites the fixture

Runs the profile over a hand-made cut, so it needs Java and the planetiler jar build-map-data.py pins;
without them it is skipped. Writing the fixture needs pyosmium.
"""

import math
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "scripts/map/pieces-fixture.osm.pbf"
PROFILE = ROOT / "scripts/map/KozicaProfile.java"
PLANETILER = ROOT / "map-data/planetiler-0.10.2.jar"
decode = runpy.run_path(str(ROOT / "scripts/map/decode-tile.py"), run_name="test")

ZOOM = 14
LAT, LON = 49.25, 20.0
BAR, TRIANGLE = "red:white:red_bar", "red:white:red_triangle"

RELATIONS = {
    100: ("rwn", BAR, ["w1", "w2", "r101", "r102", "r103", "r106"]),
    101: ("rwn", BAR, ["w2", "w3"]),
    102: ("rwn", BAR, ["w4"]),
    103: ("rwn", BAR, ["w5"]),
    104: ("rwn", BAR, ["w6", "r103"]),
    106: ("rwn", TRIANGLE, ["w7"]),
    107: ("rwn", "blue:white:blue_bar", ["r108"]),
    108: ("rwn", "green:white:green_bar", ["w8"]),
    109: ("nwn", "blue:white:blue_bar", ["r110", "r111"]),
    110: ("nwn", "blue:white:blue_bar", ["w9"]),
    111: ("nwn", "blue:white:blue_bar", ["w10"]),
    112: ("nwn", BAR, ["w11", "r113/excursion"]),
    113: ("lwn", BAR, ["w12"]),
}
"""r100 is a bar-marked parent with ways of its own, w2 also in its piece r101, and pieces r101 to r103,
r103 also in a second parent r104; r106 is a triangle-marked spur. r107 has no ways and its only member
is marked otherwise. r109 is an `nwn` parent of the same shape as r100, so a Long-distance Trail. r112 is
KČT's main Trail, `nwn`, holding its spur as an `excursion`."""

WAYS = 12


def write_fixture() -> None:
    import osmium

    FIXTURE.unlink(missing_ok=True)
    with osmium.SimpleWriter(str(FIXTURE)) as writer:
        for way in range(1, WAYS + 1):
            for end in (0, 1):
                writer.add_node(osmium.osm.mutable.Node(
                    id=2 * way + end, version=1, location=(LON + way / 1000, LAT + end / 2000)))
        for way in range(1, WAYS + 1):
            writer.add_way(osmium.osm.mutable.Way(
                id=way, version=1, nodes=[2 * way, 2 * way + 1], tags={"highway": "path"}))
        for number, (network, symbol, members) in RELATIONS.items():
            parsed = []
            for member in members:
                ref, _, role = member.partition("/")
                parsed.append((ref[0], int(ref[1:]), role))
            writer.add_relation(osmium.osm.mutable.Relation(
                id=number, version=1, members=parsed,
                tags={"type": "route", "route": "hiking", "network": network, "osmc:symbol": symbol}))


def tile_of(lat: float, lon: float) -> tuple[int, int]:
    n = 1 << ZOOM
    y = (1 - math.log(math.tan(math.radians(lat)) + 1 / math.cos(math.radians(lat))) / math.pi) / 2 * n
    return int((lon + 180) / 360 * n), int(y)


@unittest.skipUnless(PLANETILER.is_file() and shutil.which("java"),
                     "needs Java and the planetiler jar; `python3 scripts/build-map-data.py` fetches it")
class PiecesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.directory = tempfile.TemporaryDirectory()
        out = Path(cls.directory.name)
        bounds = f"{LON},{LAT},{LON + (WAYS + 1) / 1000},{LAT + 1 / 1000}"
        subprocess.run(
            ["java", "-cp", PLANETILER, PROFILE, f"--osm_path={FIXTURE}", f"--output={out / 'fixture.pmtiles'}",
             f"--tmpdir={out / 'tmp'}", f"--trails={out / 'fixture.trails'}", f"--bounds={bounds}",
             "--tile_compression=none", f"--maxzoom={ZOOM}"],
            check=True, capture_output=True)
        cls.lines = (out / "fixture.trails").read_text().splitlines()
        archive = (out / "fixture.pmtiles").read_bytes()
        tile = decode["find_tile"](archive, decode["tile_id"](ZOOM, *tile_of(LAT, LON + WAYS / 2000)))
        cls.features = {}
        for number, _, value in decode["fields"](tile):
            if number == 3:
                name, features = decode["layer"](value)
                if name == "trails":
                    for _, feature_id, attributes in features:
                        cls.features.setdefault(feature_id // 10, []).append(attributes)

    @classmethod
    def tearDownClass(cls) -> None:
        cls.directory.cleanup()

    def test_the_parent_is_one_trail_over_its_ways_and_its_pieces(self):
        self.assertIn("trail\t100\tred\tred_bar\t1 2 3 4 5", self.lines)
        self.assertIn("trail\t104\tred\tred_bar\t6 5", self.lines)

    def test_a_piece_is_no_trail_and_a_spur_is_its_own(self):
        trails = {line.split("\t")[1] for line in self.lines if line.startswith("trail")}
        self.assertEqual(trails, {"100", "104", "106", "108", "110", "111", "112"})
        self.assertIn("trail\t106\tred\tred_triangle\t7", self.lines)

    def test_a_piece_s_ways_carry_the_parent_once(self):
        trails_on = {way: sorted(a["trail"] for a in found) for way, found in self.features.items()}
        self.assertEqual(trails_on[2], [100])
        self.assertEqual(trails_on[3], [100])
        self.assertEqual(trails_on[5], [100, 104])
        self.assertEqual(trails_on[7], [106])
        self.assertEqual([a["count"] for a in self.features[2]], [1])

    def test_a_parent_marked_otherwise_than_its_only_member_is_nothing(self):
        self.assertFalse(any("\t107\t" in line for line in self.lines))
        self.assertEqual([a["trail"] for a in self.features[8]], [108])

    def test_an_nwn_parent_is_a_long_distance_trail_and_kct_s_spur_its_trail_s(self):
        self.assertIn("long\t109\t110 111", self.lines)
        self.assertIn("trail\t112\tred\tred_bar\t11 12/excursion", self.lines)
        self.assertEqual([a["trail"] for a in self.features[12]], [112])


if __name__ == "__main__":
    if sys.argv[1:] == ["--write"]:
        write_fixture()
        print(FIXTURE)
    else:
        unittest.main()
