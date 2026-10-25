"""docs/phase-4.md § Tests, for build-relief.py:

    map-data/relief/venv/bin/python -m unittest scripts/test_build_relief.py
"""

import io
import runpy
import unittest
from pathlib import Path

import numpy as np
from PIL import Image
from shapely.geometry import box

relief = runpy.run_path(str(Path(__file__).with_name("build-relief.py")), run_name="test")


def decode(webp: bytes) -> np.ndarray:
    """MapLibre's Terrarium decode, which the phone and phase 5's ascent both rely on."""
    rgb = np.asarray(Image.open(io.BytesIO(webp)).convert("RGB"), dtype=np.float64)
    return rgb[..., 0] * 256 + rgb[..., 1] + rgb[..., 2] / 256 - 32768


class TerrariumTest(unittest.TestCase):
    def test_heights_survive_the_round_trip_to_a_tenth_of_a_metre(self):
        heights = np.array([-12.3, 0.0, 2499.0, 1234.56], dtype=np.float32)
        block = np.resize(heights, (relief["TILE"], relief["TILE"])).astype(np.float32)
        decoded = decode(relief["terrarium"](block))
        np.testing.assert_allclose(decoded[0, :4], [-12.3, 0.0, 2499.0, 1234.6], atol=0.005)


class GapCheckTest(unittest.TestCase):
    def setUp(self):
        self.inside = box(20.001, 49.201, 20.009, 49.209)
        self.grid = relief["Grid"].around(self.inside)
        self.countries = [box(19.0, 48.0, 21.0, 50.0)]
        self.heights = np.full((self.grid.height, self.grid.width), 1000.0, dtype=np.float32)

    def cell(self, lon, lat):
        x, y = relief["to_pixel"](lon, lat)
        return int(y) - self.grid.y0, int(x) - self.grid.x0

    def check(self):
        relief["check_gaps"](self.heights, self.grid, self.inside, self.countries, {"test": self.inside})

    def test_an_empty_cell_inside_the_union_fails_the_build(self):
        self.heights[self.cell(20.005, 49.205)] = np.nan
        with self.assertRaises(SystemExit):
            self.check()

    def test_an_empty_cell_outside_the_union_passes(self):
        self.heights[self.cell(20.0005, 49.2095)] = np.nan
        self.check()


if __name__ == "__main__":
    unittest.main()
