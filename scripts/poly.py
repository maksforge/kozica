"""Reads an osmium polygon file, as `osmium extract -p` does: every ring, `!` rings as holes."""

from __future__ import annotations

from pathlib import Path

from shapely.geometry import MultiPolygon, Polygon
from shapely.ops import unary_union


def read_poly(path: Path) -> Polygon | MultiPolygon:
    outer, holes, ring, hole = [], [], None, False
    for line in path.read_text().splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        if line == "END":
            if ring is not None:
                (holes if hole else outer).append(Polygon(ring))
                ring = None
            continue
        if ring is None:
            ring, hole = [], line.startswith("!")
            continue
        lon, lat = map(float, line.split()[:2])
        ring.append((lon, lat))
    area = unary_union(outer)
    for h in holes:
        area = area.difference(h)
    return area
