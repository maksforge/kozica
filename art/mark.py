"""The mark: the single definition of this app's identity.

A Tatra chamois in profile on a crag, both horns hooked back into crooks, and a short tail. Both
generators import it, so the launcher icon, the notification icon and the feature graphic cannot
drift into being different marks. Edit the anchors in `draw-mark.py`, which rewrites MARK and HOLE
here, never the path data by hand.

Two subpaths: the silhouette (animal and crag as one contour) and the hole under the belly.
Coordinates are in arbitrary units around the mark's centre; callers fit them to their canvas with
`fit()`. y grows downward, as in SVG and PIL.
"""

import re
from dataclasses import dataclass

# The icon's own two colours, deliberately outside the app's scheme (the one exception to
# CLAUDE.md's colour rule besides the map's Trail colours). make-launcher-icon.py writes
# ic_launcher_background.xml from GROUND, so these are the only copy.
GROUND = (0xFF, 0xF1, 0xD6)
MARK_INK = (0x8E, 0x1B, 0x2C)


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
    "M-66,-30"
    "C-66.5,-34.67 -66.67,-38 -69,-40C-71.33,-42 -80,-42 -80,-42"
    "C-80,-42 -68,-49 -68,-49C-68,-49 -64,-50.33 -56,-52"
    "C-48,-53.67 -31.67,-56.67 -20,-59C-8.33,-61.33 6,-61.83 14,-66"
    "C22,-70.17 24.83,-79 28,-84C31.17,-89 34.5,-92 33,-96"
    "C31.5,-100 19,-108 19,-108C19,-108 29.72,-106.35 32,-106"
    "C34.28,-105.65 32.61,-105.2 32.68,-105.87C32.75,-106.55 32.53,-108.65 32.43,-110.04"
    "C32.34,-111.42 32.22,-112.83 32.11,-114.19C32,-115.54 31.91,-116.95 31.78,-118.16"
    "C31.66,-119.37 31.66,-120.48 31.35,-121.42C31.03,-122.36 30.56,-123.15 29.91,-123.81"
    "C29.26,-124.47 28.38,-125.07 27.46,-125.41C26.53,-125.74 25.41,-125.91 24.38,-125.81"
    "C23.34,-125.71 22.2,-125.36 21.26,-124.8C20.32,-124.23 19.39,-123.38 18.76,-122.43"
    "C18.12,-121.47 17.62,-120.27 17.45,-119.06C17.28,-117.85 17.75,-115.17 17.75,-115.17"
    "C17.75,-115.17 16.94,-114.31 16.57,-114.97C16.19,-115.64 15.54,-117.71 15.51,-119.16"
    "C15.47,-120.61 15.75,-122.29 16.36,-123.69C16.97,-125.09 17.96,-126.52 19.14,-127.57"
    "C20.32,-128.62 21.88,-129.53 23.45,-130C25.02,-130.48 26.87,-130.65 28.56,-130.41"
    "C30.24,-130.17 32.06,-129.52 33.53,-128.57C35,-127.62 36.41,-126.13 37.38,-124.72"
    "C38.36,-123.31 38.8,-121.57 39.36,-120.12C39.92,-118.67 40.28,-117.36 40.74,-116.02"
    "C41.21,-114.69 41.66,-113.41 42.14,-112.1C42.61,-110.8 43.07,-109.49 43.61,-108.2"
    "C44.14,-106.9 45.03,-105.07 45.36,-104.35C45.68,-103.64 45.56,-103.91 45.56,-103.91"
    "C45.56,-103.91 45.8,-104.49 46.2,-106C46.61,-107.5 47.4,-110.62 47.97,-112.93"
    "C48.54,-115.25 49.09,-117.56 49.64,-119.88C50.19,-122.2 50.72,-124.52 51.25,-126.84"
    "C51.78,-129.16 52.31,-131.52 52.83,-133.8C53.34,-136.08 53.98,-138.41 54.35,-140.54"
    "C54.72,-142.66 55.33,-144.71 55.06,-146.54C54.79,-148.37 53.93,-150.2 52.73,-151.54"
    "C51.53,-152.88 49.67,-154.09 47.85,-154.58C46.03,-155.07 43.69,-155.08 41.82,-154.47"
    "C39.94,-153.87 37.9,-152.58 36.59,-150.96C35.28,-149.34 33.97,-144.76 33.97,-144.76"
    "C33.97,-144.76 32.69,-143.91 32.84,-145.15C32.98,-146.39 33.55,-150.17 34.85,-152.2"
    "C36.16,-154.23 38.38,-156.28 40.67,-157.34C42.96,-158.41 46.02,-158.93 48.61,-158.58"
    "C51.2,-158.23 54.16,-156.97 56.22,-155.23C58.28,-153.48 60.09,-150.64 60.97,-148.1"
    "C61.86,-145.56 61.51,-142.57 61.53,-139.99C61.55,-137.42 61.22,-135.05 61.07,-132.64"
    "C60.93,-130.23 60.8,-127.89 60.67,-125.52C60.54,-123.14 60.41,-120.76 60.3,-118.38"
    "C60.19,-116 60.08,-113.63 59.99,-111.24C59.9,-108.86 59.77,-106.63 59.78,-104.09"
    "C59.78,-101.55 58.63,-99.18 60,-96C61.37,-92.82 66,-88 68,-85"
    "C70,-82 72,-78 72,-78C72,-78 69.5,-75.17 66,-73"
    "C62.5,-70.83 54.5,-68.83 51,-65C47.5,-61.17 46.17,-55.17 45,-50"
    "C43.83,-44.83 44.67,-39.33 44,-34C43.33,-28.67 42,-24 41,-18"
    "C40,-12 38.67,-4 38,2C37.33,8 37,18 37,18"
    "C37,18 47,7 47,7C47,7 57,36 57,36"
    "C57,36 51,46 51,46C51,46 64,70 64,70"
    "C64,70 62,88 62,88C62,88 -76,88 -76,88"
    "C-76,88 -70,64 -70,64C-70,64 -78,54 -78,54"
    "C-78,54 -60,44 -60,44C-60,44 -48,40 -48,40"
    "C-48,40 -52.67,27.33 -55,22C-57.33,16.67 -60.17,13.67 -62,8"
    "C-63.83,2.33 -65.33,-5.67 -66,-12C-66.67,-18.33 -65.5,-25.33 -66,-30"
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
