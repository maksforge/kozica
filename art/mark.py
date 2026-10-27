"""The mark: the single definition of this app's identity.

A Tatra chamois in profile on a crag, its one horn hooked into a crook that stays open at launcher
size. Both generators import it, so the launcher icon, the notification icon and the feature graphic
cannot drift into being different marks. Edit the anchors in `draw-mark.py`, which rewrites MARK and
HOLE here, never the path data by hand.

Two subpaths: the silhouette (animal and crag as one contour) and the hole under the belly.
Coordinates are in arbitrary units around the mark's centre; callers fit them to their canvas with
`fit()`. y grows downward, as in SVG and PIL.
"""

import re
from dataclasses import dataclass

# The icon's two colours — take both from the app's generated scheme (`scripts/gen_scheme.py`) rather
# than picking them for the icon, so the identity and the app agree. They are the *only* copy:
# make-launcher-icon.py writes ic_launcher_background.xml from GROUND, so recolouring the identity is
# an edit here and a re-run, never a second value somewhere else to keep in step. (It used to be
# two — this file and a hand-written background drawable — and an app built from the template found
# them disagreeing the first time its palette moved.)
#
# ⚠ **A dark tone of a warm hue is brown.** The first app built from this template tried its
# `primary` darkened for contrast as the ground and it read as mud; then a gradient, whose middle is
# the same mud at 44dp. What survived was a flat mid-tone role (`surfaceVariant`) under a light-ish
# ink (`primary`). Judge the ground at launcher size on a real home screen, light and dark.
GROUND = (0x4D, 0x46, 0x39)  # the icon's flat field — the dark scheme's `surfaceVariant`
MARK_INK = (0xFF, 0xB7, 0x77)  # the mark on that field — the dark scheme's `primary`


@dataclass(frozen=True)
class Subpath:
    """One closed contour, as cubic path data.

    `d` is always wound CLOCKWISE on screen (positive shoelace area with y downward). A hole is
    stored the same way and reversed at read time by `nodes()`, so it is subtracted by the non-zero
    fill rule that both SVG and VectorDrawable use by default. That is what makes the hole a real
    hole rather than a shape painted in the background colour — and it is why it survives the
    monochrome layer, where Android tints everything one flat colour and a painted hole would vanish.
    """

    d: str
    hole: bool = False


MARK = (
    "M-65,-32"
        "C-65,-32 -80,-38 -80,-38C-80,-38 -67,-50 -67,-50"
        "C-67,-50 -63.83,-50.5 -56,-52C-48.17,-53.5 -31.67,-56.67 -20,-59"
        "C-8.33,-61.33 6,-61.83 14,-66C22,-70.17 25,-79.17 28,-84"
        "C31,-88.83 33.33,-90.83 32,-95C30.67,-99.17 20,-109 20,-109"
        "C20,-109 41,-107 41,-107C41,-107 43.28,-109.17 43.82,-112"
        "C44.37,-114.83 44.11,-120.09 44.27,-124C44.43,-127.91 44.79,-132.77 44.8,-135.43"
        "C44.81,-138.1 44.81,-138.64 44.31,-139.98C43.82,-141.33 42.94,-142.57 41.84,-143.49"
        "C40.74,-144.41 39.22,-145.21 37.71,-145.51C36.21,-145.81 34.37,-145.77 32.81,-145.28"
        "C31.25,-144.79 29.55,-143.81 28.35,-142.55C27.15,-141.29 26.05,-139.58 25.59,-137.72"
        "C25.13,-135.86 25.59,-131.39 25.59,-131.39C25.59,-131.39 22.91,-129.86 22.1,-131.04"
        "C21.29,-132.22 20.52,-135.9 20.72,-138.45C20.93,-141.01 21.84,-144.06 23.33,-146.35"
        "C24.82,-148.65 27.16,-150.9 29.67,-152.24C32.18,-153.58 35.43,-154.45 38.38,-154.41"
        "C41.33,-154.38 44.7,-153.53 47.35,-152.03C50,-150.53 52.63,-147.99 54.28,-145.41"
        "C55.92,-142.83 56.62,-140.13 57.2,-136.57C57.77,-133 57.57,-128.09 57.73,-124"
        "C57.89,-119.91 57.97,-116.67 58.18,-112C58.39,-107.33 57.53,-100.5 59,-96"
        "C60.47,-91.5 64.83,-88 67,-85C69.17,-82 72,-78 72,-78"
        "C72,-78 69.5,-75.17 66,-73C62.5,-70.83 54.5,-68.83 51,-65"
        "C47.5,-61.17 46.17,-55.17 45,-50C43.83,-44.83 44.67,-39.33 44,-34"
        "C43.33,-28.67 42,-24 41,-18C40,-12 38.67,-4 38,2"
        "C37.33,8 37,18 37,18C37,18 47,7 47,7"
        "C47,7 57,36 57,36C57,36 51,46 51,46"
        "C51,46 64,70 64,70C64,70 62,88 62,88"
        "C62,88 -76,88 -76,88C-76,88 -70,64 -70,64"
        "C-70,64 -78,54 -78,54C-78,54 -60,44 -60,44"
        "C-60,44 -48,40 -48,40C-48,40 -52.67,27.33 -55,22"
        "C-57.33,16.67 -60.17,13.67 -62,8C-63.83,2.33 -65.5,-5.33 -66,-12"
        "C-66.5,-18.67 -65,-32 -65,-32"
        "Z"
)

HOLE = (
    "M-32,-16"
        "C-27.67,-18.67 -16,-17.5 -8,-20C0,-22.5 10.83,-32.67 16,-31"
        "C21.17,-29.33 21.5,-18.17 23,-10C24.5,-1.83 25,18 25,18"
        "C25,18 12,22 12,22C12,22 2,29 2,29"
        "C2,29 -14,27 -14,27C-14,27 -26,35 -26,35"
        "C-26,35 -37,40 -37,40C-37,40 -41.5,21.33 -41,14"
        "C-40.5,6.67 -35.5,1 -34,-4C-32.5,-9 -36.33,-13.33 -32,-16"
        "Z"
)

PARTS = (
    Subpath(MARK),
    Subpath(HOLE, hole=True),
)

_NUM = re.compile(r"-?\d*\.?\d+(?:e-?\d+)?")


def _points(d):
    """Path data -> the flat control-point list: 1 start point plus 3 per cubic segment."""
    pts = []
    for chunk in re.finditer(r"[MC]([^MCZz]*)", d):
        nums = [float(v) for v in _NUM.findall(chunk.group(1))]
        pts += list(zip(nums[0::2], nums[1::2]))
    return pts


def nodes(e, tf=lambda p: p):
    """Control points, caller-transformed, wound for fill or hole."""
    pts = _points(e.d)
    if e.hole:
        pts.reverse()
    return [tf(p) for p in pts]


def path_data(e, tf=lambda p: p, precision=2):
    """SVG/VectorDrawable path data for one subpath."""
    n = nodes(e, tf)

    def f(p):
        return f"{round(p[0], precision):g},{round(p[1], precision):g}"

    out = [f"M{f(n[0])}"]
    for i in range(1, len(n), 3):
        out.append("C" + " ".join(f(n[i + j]) for j in range(3)))
    return "".join(out) + "Z"


def outline(e, tf=lambda p: p, steps=32):
    """The same subpath flattened to a polygon, for renderers that can't draw curves."""
    n = nodes(e, tf)
    pts = []
    for i in range(1, len(n), 3):
        (x0, y0), (x1, y1), (x2, y2), (x3, y3) = n[i - 1], n[i], n[i + 1], n[i + 2]
        for s in range(steps):
            t = s / steps
            u = 1 - t
            pts.append((
                u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3,
            ))
    return pts


def bounds(parts=PARTS):
    """Bounding box of the silhouette. Holes are ignored — a hole cannot extend the mark."""
    xs, ys = [], []
    for e in parts:
        if e.hole:
            continue
        for x, y in outline(e):
            xs.append(x)
            ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def fit(canvas, art_size, parts=PARTS):
    """A transform placing the mark centred on a square canvas at a given longest-side size.

    Returns a function mapping mark coordinates to canvas coordinates.
    """
    x0, y0, x1, y1 = bounds(parts)
    scale = art_size / max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return lambda p: (canvas / 2 + (p[0] - cx) * scale, canvas / 2 + (p[1] - cy) * scale)
