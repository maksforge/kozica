"""The mark's drawing tool: anchor points -> cubic path data, written over MARK and HOLE in
`mark.py` beside this file. (x, y, kind): kind 'c' = sharp corner, 's' = smooth (Catmull-Rom
tangent). Move an anchor, run this, then the two generators.
"""
import math
import re
import sys
from pathlib import Path

HERE = Path(__file__).parent


def stroke(center, widths):
    """A thick stroke along a centreline -> (left side base->tip, right side tip->base)."""
    left, right = [], []
    n = len(center)
    for i, (x, y) in enumerate(center):
        x0, y0 = center[max(i - 1, 0)]
        x1, y1 = center[min(i + 1, n - 1)]
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        nx, ny = -dy / L, dx / L
        w = widths[i] / 2
        left.append((x - nx * w, y - ny * w))
        right.append((x + nx * w, y + ny * w))
    return left, right[::-1]


def horn(base, rise, radius, sweep_deg, w_base, w_tip, n_rise=3, n_arc=7):
    """Centreline: straight up from `base` for `rise`, then an arc of `radius` hooking backward
    (toward -x) and down through `sweep_deg`. Width eases from w_base to w_tip."""
    bx, by = base
    pts = [(bx, by - rise * i / n_rise) for i in range(n_rise + 1)]
    top = (bx, by - rise)
    cx, cy = bx - radius, top[1]
    for i in range(1, n_arc + 1):
        a = math.radians(sweep_deg) * i / n_arc
        pts.append((cx + radius * math.cos(a), cy - radius * math.sin(a)))
    total = len(pts)
    widths = []
    for i in range(total):
        t = i / (total - 1)
        widths.append(w_base + (w_tip - w_base) * (t ** 1.25))
    return pts, widths


HORN_C, HORN_W = horn(base=(51, -100), rise=36, radius=14, sweep_deg=200, w_base=15, w_tip=3.5)
HL, HR = stroke(HORN_C, HORN_W)

OUTER = [
    (-65, -32, 'c'),   # tail root, underside
    (-80, -38, 'c'),   # tail tip
    (-67, -50, 'c'),   # tail root, top
    (-56, -52, 's'),   # rump top
    (-20, -59, 's'),   # back
    (14, -66, 's'),    # withers
    (28, -84, 's'),    # neck
    (32, -95, "s"),    # poll
    (20, -109, "c"),   # ear tip
    (41, -107, 'c'),   # notch between ear and horn
] + [(x, y, 's') for x, y in HL[1:-1]] + [
    (HL[-1][0], HL[-1][1], 'c'),
] + [(x, y, 's') for x, y in HR[:-1]] + [
    (59, -96, 's'),    # forehead
    (67, -85, 's'),    # nose bridge
    (72, -78, "c"),    # nose tip
    (66, -73, "s"),    # chin
    (51, -65, 's'),    # throat
    (45, -50, 's'),
    (44, -34, 's'),    # chest
    (41, -18, "s"),    # front leg front
    (38, 2, "s"),
    (37, 18, 'c'),     # front hoof front
    (47, 7, 'c'),      # summit tip
    (57, 36, 'c'),
    (51, 46, 'c'),
    (64, 70, 'c'),
    (62, 88, 'c'),     # bottom right
    (-76, 88, 'c'),    # bottom left
    (-70, 64, 'c'),
    (-78, 54, 'c'),
    (-60, 44, 'c'),    # ledge
    (-48, 40, 'c'),    # hind hoof back
    (-55, 22, 's'),    # cannon
    (-62, 8, 's'),     # hock
    (-66, -12, 's'),   # thigh
]

HOLE = [
    (-32, -16, 's'),   # belly rear
    (-8, -20, 's'),    # belly
    (16, -31, 's'),    # belly front
    (23, -10, 's'),    # knee
    (25, 18, 'c'),     # front hoof back
    (12, 22, 'c'),     # rock top
    (2, 29, 'c'),
    (-14, 27, 'c'),
    (-26, 35, 'c'),
    (-37, 40, 'c'),    # hind hoof front
    (-41, 14, 's'),    # hind leg front edge
    (-34, -4, 's'),    # stifle
]


def cubic(points, k=1.0):
    n = len(points)
    out = []
    for i in range(n):
        x1, y1, k1 = points[i]
        x2, y2, k2 = points[(i + 1) % n]
        x0, y0, _ = points[(i - 1) % n]
        x3, y3, _ = points[(i + 2) % n]
        if k1 == 'c':
            c1 = (x1, y1)
        else:
            c1 = (x1 + (x2 - x0) / 6 * k, y1 + (y2 - y0) / 6 * k)
        if k2 == 'c':
            c2 = (x2, y2)
        else:
            c2 = (x2 - (x3 - x1) / 6 * k, y2 - (y3 - y1) / 6 * k)
        out.append((c1, c2, (x2, y2)))
    return out


def fmt(p):
    return f"{round(p[0], 2):g},{round(p[1], 2):g}"


def path_data(points):
    segs = cubic(points)
    d = f"M{fmt(points[0][:2])}"
    for c1, c2, p in segs:
        d += f"C{fmt(c1)} {fmt(c2)} {fmt(p)}"
    return d + "Z"


def area(points):
    s = 0
    for i in range(len(points)):
        x1, y1 = points[i][:2]
        x2, y2 = points[(i + 1) % len(points)][:2]
        s += x1 * y2 - x2 * y1
    return s / 2


def as_literal(d, indent="    "):
    """Path data as the wrapped Python string literal mark.py carries: one 'C' pair per line."""
    m = re.match(r"(M[^C]*)(.*)Z$", d)
    head, body = m.group(1), m.group(2)
    segs = re.findall(r"C[^C]*", body)
    lines = [f'{indent}"{head}"']
    for i in range(0, len(segs), 2):
        lines.append(f'{indent}    "{"".join(segs[i:i + 2])}"')
    lines.append(f'{indent}    "Z"')
    return "\n".join(lines)


def write_mark(out, template):
    src = template.read_text()
    src = re.sub(r"MARK = \(\n.*?\n\)", lambda _: "MARK = (\n" + as_literal(path_data(OUTER)) + "\n)", src, count=1, flags=re.S)
    src = re.sub(r"HOLE = \(\n.*?\n\)", lambda _: "HOLE = (\n" + as_literal(path_data(HOLE)) + "\n)", src, count=1, flags=re.S)
    out.write_text(src)


if __name__ == "__main__":
    print("outer area", round(area(OUTER)), "hole area", round(area(HOLE)))
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "mark.py"
    template = Path(sys.argv[2]) if len(sys.argv) > 2 else out
    write_mark(out, template)
    print("wrote", out)
