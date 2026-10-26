#!/usr/bin/env python3
"""Writes the map's Region outlines, their name points and the mask outside them.

    uv run --with shapely==2.1.1 python3 scripts/map/gen_regions.py && python3 scripts/map/gen_style.py

Re-run whenever a polygon in scripts/regions/ changes; the output is committed, and gen_style.py reads
its bounds. The mask is the world less the union of the Regions, which overlap by a few kilometres,
so it needs a real union rather than one hole per Region.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from shapely.geometry import Polygon, box, mapping
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parent.parent.parent
REGIONS = ROOT / "scripts/regions"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
TARGET = ROOT / "app/src/main/assets/map/regions.geojson"


def read_poly(path: Path) -> Polygon:
    lines = path.read_text().split("\n")
    points = []
    for line in lines[2:]:
        if line.strip() == "END":
            break
        lon, lat = line.split()
        points.append((float(lon), float(lat)))
    return Polygon(points)


def rounded(geometry: dict) -> dict:
    def walk(value):
        if isinstance(value, (list, tuple)):
            return [walk(v) for v in value]
        return round(value, 5) if isinstance(value, float) else value

    return {"type": geometry["type"], "coordinates": walk(geometry["coordinates"])}


def main() -> None:
    names = dict(re.findall(r'<string name="region_([a-z_]+)"[^>]*>([^<]+)</string>', STRINGS.read_text()))
    regions = {path.stem: read_poly(path) for path in sorted(REGIONS.glob("*.poly"))}
    union = unary_union(list(regions.values()))
    features = [
        {"type": "Feature", "properties": {"kind": "outside"}, "geometry": rounded(mapping(box(-180, -85, 180, 85).difference(union)))}
    ]
    for id, polygon in regions.items():
        features.append({"type": "Feature", "properties": {"kind": "region", "id": id},
                         "geometry": rounded({"type": "LineString", "coordinates": list(polygon.exterior.coords)})})
        features.append({"type": "Feature", "properties": {"kind": "label", "id": id, "name": names[id]},
                         "geometry": rounded(mapping(polygon.representative_point()))})
    collection = {"type": "FeatureCollection", "bbox": [round(v, 5) for v in union.bounds], "features": features}
    TARGET.write_text(json.dumps(collection, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(TARGET.relative_to(ROOT))


if __name__ == "__main__":
    main()
