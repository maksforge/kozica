#!/usr/bin/env python3
"""Builds the Trail network and the name index of docs/phase-5.md § 2 from the Regions' merged cuts.

    python3 scripts/build-network.py --relief map-data/relief/relief.pmtiles --out map-data/base-assets \\
        --region tatry map-data/merged/pin/tatry.osm.pbf map-data/archives/tatry.ways ...

The Trail ways are the ones KozicaProfile.java listed for each Region; their nodes and tags, and the
named places, are read from the merged cuts. Needs pyosmium 4.2+ and Pillow with WebP, imported only
where they are used, so `scripts/test_build_network.py` runs on a plain Python.
"""

from __future__ import annotations

import argparse
import functools
import gzip
import io
import math
import mmap
import sqlite3
import struct
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path

FORMAT_VERSION = 1
MAGIC = b"KZTN"
DENSIFY_METRES = 30.0
DEAD_BAND_METRES = 5.0
FLAT_METRES_PER_SECOND = 4000 / 3600
SECONDS_PER_METRE_UP = 600 / 100
SECONDS_PER_METRE_STEEP_DOWN = 300 / 100
STEEP_GRADE = 0.10
RELIEF_ZOOM = 12
RELIEF_TILE = 512
EARTH_RADIUS = 6_371_008.8

ONEWAY_HIGHWAYS = {"path", "footway", "steps", "via_ferrata"}
SAC_GRADES = {
    "hiking": 1,
    "mountain_hiking": 2,
    "demanding_mountain_hiking": 3,
    "alpine_hiking": 4,
    "demanding_alpine_hiking": 5,
    "difficult_alpine_hiking": 6,
}
FORWARD_ONLY = 1
BACKWARD_ONLY = 2
NAME_KEYS = ("name", "name:pl", "name:sk", "name:cs", "alt_name", "old_name")
UNDECOMPOSED = str.maketrans({"ł": "l", "đ": "d", "ð": "d", "ø": "o", "ß": "ss", "æ": "ae", "œ": "oe", "ı": "i"})


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def fold(text: str) -> str:
    """The search key: lower case, accents off, letters no decomposition reaches mapped by hand."""
    lowered = text.lower().translate(UNDECOMPOSED)
    bare = "".join(c for c in unicodedata.normalize("NFKD", lowered) if not unicodedata.combining(c))
    return " ".join("".join(c if c.isascii() and c.isalnum() else " " for c in bare).split())


def distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_RADIUS * math.asin(math.sqrt(a))


@dataclass(frozen=True)
class Climb:
    ascent: float
    descent: float
    steep_ascent: float
    steep_descent: float


def climb(lengths: list[float], heights: list[float]) -> Climb:
    """Ascent and descent past a dead band, and the parts of each steeper than STEEP_GRADE.

    `lengths[i]` is the horizontal distance from point i to point i + 1. What is left inside the band at
    the last point counts too, so the edges of a Route climb exactly what its ends differ by."""
    ascent = descent = steep_ascent = steep_descent = 0.0
    reference = heights[0]
    run = 0.0
    for i, (length, height) in enumerate(zip(lengths, heights[1:])):
        run += length
        change = height - reference
        if abs(change) < DEAD_BAND_METRES and i < len(lengths) - 1:
            continue
        steep = run > 0 and abs(change) / run > STEEP_GRADE
        if change > 0:
            ascent += change
            steep_ascent += change if steep else 0.0
        else:
            descent -= change
            steep_descent -= change if steep else 0.0
        reference, run = height, 0.0
    return Climb(ascent, descent, steep_ascent, steep_descent)


def walking_seconds(length: float, up: float, steep_down: float) -> float:
    """The PTTK marker's rule, docs/phase-5.md § 3, unrounded."""
    return length / FLAT_METRES_PER_SECOND + up * SECONDS_PER_METRE_UP + steep_down * SECONDS_PER_METRE_STEEP_DOWN


def oneway(tags: dict[str, str]) -> int:
    """docs/phase-5.md § 3: the walker's direction, never a vehicle's."""
    foot = tags.get("oneway:foot")
    if foot is not None:
        return {"yes": FORWARD_ONLY, "true": FORWARD_ONLY, "1": FORWARD_ONLY, "-1": BACKWARD_ONLY}.get(foot, 0)
    if tags.get("foot:backward") == "no":
        return FORWARD_ONLY
    if tags.get("foot:forward") == "no":
        return BACKWARD_ONLY
    if tags.get("highway") in ONEWAY_HIGHWAYS:
        return {"yes": FORWARD_ONLY, "true": FORWARD_ONLY, "1": FORWARD_ONLY, "-1": BACKWARD_ONLY}.get(
            tags.get("oneway", ""), 0)
    return 0


class Relief:
    """Heights from the relief's Terrarium tiles, bilinear between pixel centres."""

    def __init__(self, path: Path) -> None:
        self.file = path.open("rb")
        self.data = mmap.mmap(self.file.fileno(), 0, access=mmap.ACCESS_READ)
        header = self.data[:127]
        if header[:7] != b"PMTiles" or header[7] != 3:
            sys.exit(f"build-network: {path} is not a PMTiles v3 archive")
        self.root = struct.unpack_from("<QQ", header, 8)
        self.leaves = struct.unpack_from("<Q", header, 40)[0]
        self.tiles = struct.unpack_from("<Q", header, 56)[0]
        self.compression = header[97]
        self.missing = 0

    def _directory(self, offset: int, length: int) -> list[tuple[int, int, int, int]]:
        raw = self.data[offset:offset + length]
        data = gzip.decompress(raw) if self.compression == 2 else raw
        count, pos = _varint(data, 0)
        ids, runs, lengths, offsets = [], [], [], []
        last = 0
        for _ in range(count):
            delta, pos = _varint(data, pos)
            last += delta
            ids.append(last)
        for target in (runs, lengths):
            for _ in range(count):
                value, pos = _varint(data, pos)
                target.append(value)
        for i in range(count):
            value, pos = _varint(data, pos)
            offsets.append(offsets[i - 1] + lengths[i - 1] if value == 0 and i > 0 else value - 1)
        return list(zip(ids, runs, lengths, offsets))

    def _tile_bytes(self, wanted: int) -> bytes | None:
        offset, length = self.root
        for _ in range(4):
            entries = self._directory(offset, length)
            found = None
            for entry in entries:
                if entry[0] <= wanted:
                    found = entry
                else:
                    break
            if found is None:
                return None
            tile_id, run, size, at = found
            if run == 0:
                offset, length = self.leaves + at, size
                continue
            if wanted >= tile_id + run:
                return None
            return self.data[self.tiles + at:self.tiles + at + size]
        return None

    @functools.lru_cache(maxsize=256)
    def _tile(self, x: int, y: int) -> bytes | None:
        from PIL import Image

        webp = self._tile_bytes(_tile_id(RELIEF_ZOOM, x, y))
        if webp is None:
            return None
        image = Image.open(io.BytesIO(webp)).convert("RGB")
        if image.size != (RELIEF_TILE, RELIEF_TILE):
            sys.exit(f"build-network: relief tile {x}/{y} is {image.size}, not {RELIEF_TILE} px")
        return image.tobytes()

    def _pixel(self, px: int, py: int) -> float | None:
        tile = self._tile(px // RELIEF_TILE, py // RELIEF_TILE)
        if tile is None:
            return None
        i = 3 * ((py % RELIEF_TILE) * RELIEF_TILE + px % RELIEF_TILE)
        return tile[i] * 256 + tile[i + 1] + tile[i + 2] / 256 - 32768

    def height(self, lat: float, lon: float) -> float | None:
        world = RELIEF_TILE * (1 << RELIEF_ZOOM)
        x = (lon + 180) / 360 * world - 0.5
        sin = math.sin(math.radians(lat))
        y = (0.5 - math.log((1 + sin) / (1 - sin)) / (4 * math.pi)) * world - 0.5
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        corners = [self._pixel(x0, y0), self._pixel(x0 + 1, y0), self._pixel(x0, y0 + 1), self._pixel(x0 + 1, y0 + 1)]
        if any(c is None for c in corners):
            self.missing += 1
            return None
        top = corners[0] * (1 - fx) + corners[1] * fx
        bottom = corners[2] * (1 - fx) + corners[3] * fx
        return top * (1 - fy) + bottom * fy


def _varint(data: bytes, pos: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, pos
        shift += 7


def _tile_id(z: int, x: int, y: int) -> int:
    n = 1 << z
    d = 0
    s = n // 2
    while s > 0:
        rx = 1 if x & s else 0
        ry = 1 if y & s else 0
        d += s * s * ((3 * rx) ^ ry)
        if ry == 0:
            if rx == 1:
                x, y = n - 1 - x, n - 1 - y
            x, y = y, x
        s //= 2
    return (n * n - 1) // 3 + d


def place_kind(tags) -> str | None:
    """docs/phase-5.md § 2, in the `pois` layer's order where the two overlap."""
    if tags.get("tourism") == "alpine_hut":
        return "hut"
    if tags.get("tourism") == "wilderness_hut" or (
        tags.get("amenity") == "shelter" and tags.get("shelter_type") != "public_transport"
    ):
        return "shelter"
    if tags.get("natural") == "peak":
        return "peak"
    if tags.get("natural") == "saddle" or tags.get("mountain_pass") == "yes":
        return "pass"
    if tags.get("tourism") == "viewpoint":
        return "viewpoint"
    place = tags.get("place")
    if place in ("town", "village", "hamlet"):
        return place
    if place == "locality":
        return "place"
    if tags.get("natural") == "water":
        return "water"
    if tags.get("natural") == "grassland" or tags.get("landuse") == "meadow":
        return "clearing"
    return None


def names(tags) -> list[str]:
    found = []
    for key in NAME_KEYS:
        for value in (tags.get(key) or "").split(";"):
            value = value.strip()
            if value and value not in found:
                found.append(value)
    return found


def point_on_surface(rings: list[list[tuple[float, float]]]) -> tuple[float, float]:
    """The middle of the widest stretch inside the area along the line through its middle latitude."""
    lats = [lat for ring in rings for lat, _ in ring]
    middle = (min(lats) + max(lats)) / 2
    crossings = []
    for ring in rings:
        for (lat1, lon1), (lat2, lon2) in zip(ring, ring[1:]):
            if (lat1 <= middle < lat2) or (lat2 <= middle < lat1):
                crossings.append(lon1 + (middle - lat1) * (lon2 - lon1) / (lat2 - lat1))
    crossings.sort()
    best = max(zip(crossings[::2], crossings[1::2]), key=lambda pair: pair[1] - pair[0], default=None)
    if best is None:
        return rings[0][0]
    return middle, (best[0] + best[1]) / 2


@dataclass
class Place:
    kind: str
    lat: float
    lon: float
    region: str
    names: list[str]


def read_cuts(regions: list[tuple[str, str, str]]) -> tuple[dict, dict]:
    """Every listed Trail way, with its nodes and the tags the network reads, and every named place."""
    import osmium

    class Reader(osmium.SimpleHandler):
        def __init__(self, region: str, wanted_ways: set[int], ways: dict, places: dict) -> None:
            super().__init__()
            self.region, self.wanted_ways, self.ways, self.places = region, wanted_ways, ways, places

        def _place(self, key, tags, lat: float, lon: float) -> None:
            if key in self.places:
                return
            kind = place_kind(tags)
            found = names(tags)
            if kind and found:
                self.places[key] = Place(kind, lat, lon, self.region, found)

        def node(self, n) -> None:
            if "name" in n.tags and n.location.valid():
                self._place(("n", n.id), n.tags, n.location.lat, n.location.lon)

        def way(self, w) -> None:
            if w.id in self.wanted_ways and w.id not in self.ways:
                nodes = [(nd.ref, nd.location.lat, nd.location.lon) for nd in w.nodes if nd.location.valid()]
                if len(nodes) >= 2:
                    tags = {k: w.tags.get(k) for k in ("highway", "oneway", "oneway:foot", "foot:backward",
                                                        "foot:forward", "sac_scale") if k in w.tags}
                    self.ways[w.id] = (nodes, tags)

        def area(self, a) -> None:
            if "name" not in a.tags or place_kind(a.tags) is None:
                return
            rings = [[(nd.location.lat, nd.location.lon) for nd in ring] for ring in a.outer_rings()]
            rings += [[(nd.location.lat, nd.location.lon) for nd in inner]
                      for outer in a.outer_rings() for inner in a.inner_rings(outer)]
            rings = [ring for ring in rings if len(ring) >= 4]
            if rings:
                lat, lon = point_on_surface(rings)
                self._place(("w" if a.from_way() else "r", a.orig_id()), a.tags, lat, lon)

    ways: dict = {}
    places: dict = {}
    for region, pbf, listed in sorted(regions):
        wanted = {int(line) for line in Path(listed).read_text().split()}
        Reader(region, wanted, ways, places).apply_file(pbf, locations=True)
        log(f"read {region}: {len(ways)} Trail ways, {len(places)} places so far")
    return ways, places


@dataclass
class Edge:
    way: int
    points: list[tuple[int, float, float]]
    heights: list[float]
    length: float
    climb: Climb
    flags: int
    sac: int


def densify(points: list[tuple[int, float, float]]) -> list[tuple[int, float, float]]:
    """Every OSM node, and points between them no more than DENSIFY_METRES apart, for the climb alone:
    the added points have ref 0 and are not stored."""
    out = [points[0]]
    for (_, lat1, lon1), point in zip(points, points[1:]):
        _, lat2, lon2 = point
        steps = math.ceil(distance(lat1, lon1, lat2, lon2) / DENSIFY_METRES)
        for step in range(1, steps):
            f = step / steps
            out.append((0, lat1 + (lat2 - lat1) * f, lon1 + (lon2 - lon1) * f))
        out.append(point)
    return out


def fill_heights(heights: list[float | None]) -> list[float] | None:
    """A point the relief does not reach takes its neighbours' heights, interpolated along the edge."""
    known = [i for i, h in enumerate(heights) if h is not None]
    if not known:
        return None
    out = list(heights)
    for i, h in enumerate(heights):
        if h is not None:
            continue
        before = max((k for k in known if k < i), default=None)
        after = min((k for k in known if k > i), default=None)
        if before is None:
            out[i] = heights[after]
        elif after is None:
            out[i] = heights[before]
        else:
            out[i] = heights[before] + (heights[after] - heights[before]) * (i - before) / (after - before)
    return out


@dataclass(frozen=True)
class Piece:
    way: int
    points: list[tuple[int, float, float]]
    flags: int
    sac: int

    def turned(self) -> Piece:
        flags = {FORWARD_ONLY: BACKWARD_ONLY, BACKWARD_ONLY: FORWARD_ONLY}.get(self.flags, self.flags)
        return Piece(self.way, self.points[::-1], flags, self.sac)


def pieces_of(ways: dict) -> list[Piece]:
    """Every way split at each of its inner nodes another Trail way, or the same one, passes through, and
    a piece that closes on itself split again in its middle, so a loop is kept without an edge from a
    node to itself."""
    uses: dict[int, int] = {}
    for nodes, _ in ways.values():
        for ref, _, _ in nodes:
            uses[ref] = uses.get(ref, 0) + 1
    pieces = []
    for way_id in sorted(ways):
        nodes, tags = ways[way_id]
        cut = [0] + [i for i in range(1, len(nodes) - 1) if uses[nodes[i][0]] > 1] + [len(nodes) - 1]
        for start, end in zip(cut, cut[1:]):
            part = nodes[start:end + 1]
            closed = part[0][0] == part[-1][0]
            if closed and len(part) < 3:
                continue
            middle = len(part) // 2
            for half in (part[:middle + 1], part[middle:]) if closed else (part,):
                pieces.append(Piece(way_id, half, oneway(tags), SAC_GRADES.get(tags.get("sac_scale", ""), 0)))
    return pieces


def chains(pieces: list[Piece]) -> list[Piece]:
    """docs/phase-5.md § 2's edges: pieces joined through every node where exactly two of them meet and
    both walk alike, so a climb is measured across a way's end and only a real junction cuts it."""
    at: dict[int, list[int]] = {}
    for i, piece in enumerate(pieces):
        at.setdefault(piece.points[0][0], []).append(i)
        at.setdefault(piece.points[-1][0], []).append(i)
    taken = [False] * len(pieces)

    def beyond(node: int, current: int) -> int | None:
        meeting = at[node]
        if len(meeting) != 2:
            return None
        other = meeting[0] if meeting[1] == current else meeting[1]
        return None if taken[other] else other

    out = []
    for first in range(len(pieces)):
        if taken[first]:
            continue
        taken[first] = True
        behind, ahead = [], [pieces[first]]
        current = first
        while (other := beyond((head := (behind[-1] if behind else ahead[0])).points[0][0], current)) is not None:
            piece = pieces[other] if pieces[other].points[-1][0] == head.points[0][0] else pieces[other].turned()
            if (piece.flags, piece.sac) != (head.flags, head.sac):
                break
            taken[other] = True
            behind.append(piece)
            current = other
        current = first
        while (other := beyond(ahead[-1].points[-1][0], current)) is not None:
            tail = ahead[-1]
            piece = pieces[other] if pieces[other].points[0][0] == tail.points[-1][0] else pieces[other].turned()
            if (piece.flags, piece.sac) != (tail.flags, tail.sac):
                break
            taken[other] = True
            ahead.append(piece)
            current = other
        chain = behind[::-1] + ahead
        closed = len(chain) > 1 and chain[0].points[0][0] == chain[-1].points[-1][0]
        for run in (chain[:-1], chain[-1:]) if closed else (chain,):
            points = run[0].points + [point for piece in run[1:] for point in piece.points[1:]]
            out.append(Piece(run[0].way, points, run[0].flags, run[0].sac))
    return out


def edges_of(ways: dict, relief: Relief) -> tuple[list[Edge], int]:
    edges, flat = [], 0
    for chain in chains(pieces_of(ways)):
        dense = densify(chain.points)
        heights = fill_heights([relief.height(lat, lon) for _, lat, lon in dense])
        if heights is None:
            heights = [0.0] * len(dense)
            flat += 1
        lengths = [distance(a[1], a[2], b[1], b[2]) for a, b in zip(dense, dense[1:])]
        kept = [i for i, point in enumerate(dense) if point[0] != 0]
        edges.append(Edge(chain.way, [dense[i] for i in kept], [heights[i] for i in kept], sum(lengths),
                          climb(lengths, heights), chain.flags, chain.sac))
    return edges, flat


def write_network(edges: list[Edge], target: Path) -> tuple[int, int, int]:
    """docs/phase-5.md § 2's binary, little-endian, its arrays one after another, widest first."""
    vertices = sorted({ref for edge in edges for ref in (edge.points[0][0], edge.points[-1][0])})
    index = {ref: i for i, ref in enumerate(vertices)}
    where = {}
    for edge in edges:
        for (ref, lat, lon), height in ((edge.points[0], edge.heights[0]), (edge.points[-1], edge.heights[-1])):
            where[ref] = (lat, lon, height)
    point_count = sum(len(edge.points) for edge in edges)

    def e7(value: float) -> int:
        return round(value * 1e7)

    def dm(value: float) -> int:
        return round(value * 10)

    out = io.BytesIO()
    out.write(MAGIC + struct.pack("<HHIII", FORMAT_VERSION, 0, len(vertices), len(edges), point_count))
    out.write(struct.pack(f"<{len(edges)}q", *(edge.way for edge in edges)))
    out.write(struct.pack(f"<{len(vertices)}i", *(e7(where[ref][0]) for ref in vertices)))
    out.write(struct.pack(f"<{len(vertices)}i", *(e7(where[ref][1]) for ref in vertices)))
    out.write(struct.pack(f"<{len(edges)}I", *(index[edge.points[0][0]] for edge in edges)))
    out.write(struct.pack(f"<{len(edges)}I", *(index[edge.points[-1][0]] for edge in edges)))
    firsts, first = [], 0
    for edge in edges:
        firsts.append(first)
        first += len(edge.points)
    out.write(struct.pack(f"<{len(edges)}I", *firsts))
    out.write(struct.pack(f"<{len(edges)}I", *(len(edge.points) for edge in edges)))
    for values in (
        [edge.length for edge in edges],
        [walking_seconds(edge.length, edge.climb.ascent, edge.climb.steep_descent) for edge in edges],
        [walking_seconds(edge.length, edge.climb.descent, edge.climb.steep_ascent) for edge in edges],
        [edge.climb.ascent for edge in edges],
        [edge.climb.descent for edge in edges],
    ):
        out.write(struct.pack(f"<{len(edges)}f", *values))
    out.write(struct.pack(f"<{point_count}i", *(e7(lat) for edge in edges for _, lat, _ in edge.points)))
    out.write(struct.pack(f"<{point_count}i", *(e7(lon) for edge in edges for _, _, lon in edge.points)))
    out.write(struct.pack(f"<{len(vertices)}h", *(dm(where[ref][2]) for ref in vertices)))
    out.write(struct.pack(f"<{point_count}h", *(dm(h) for edge in edges for h in edge.heights)))
    out.write(bytes(edge.flags for edge in edges))
    out.write(bytes(edge.sac for edge in edges))
    target.write_bytes(out.getvalue())
    return len(vertices), len(edges), point_count


def write_places(places: dict, target: Path) -> int:
    target.unlink(missing_ok=True)
    db = sqlite3.connect(target)
    db.executescript(
        """
        PRAGMA page_size = 4096;
        PRAGMA journal_mode = DELETE;
        CREATE TABLE places (
            id INTEGER PRIMARY KEY, kind TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
            region TEXT NOT NULL, name TEXT NOT NULL);
        CREATE VIRTUAL TABLE place_names USING fts4(place, name, folded, notindexed=place, notindexed=name);
        """
    )
    order = sorted(places, key=lambda key: ({"n": 0, "w": 1, "r": 2}[key[0]], key[1]))
    with db:
        for number, key in enumerate(order, start=1):
            place = places[key]
            db.execute("INSERT INTO places VALUES (?, ?, ?, ?, ?, ?)",
                       (number, place.kind, round(place.lat, 7), round(place.lon, 7), place.region, place.names[0]))
            for name in place.names:
                db.execute("INSERT INTO place_names (place, name, folded) VALUES (?, ?, ?)", (number, name, fold(name)))
    db.execute("INSERT INTO place_names (place_names) VALUES ('optimize')")
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(order)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--relief", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--region", nargs=3, action="append", required=True, metavar=("ID", "PBF", "WAYS"))
    args = parser.parse_args()

    relief = Relief(args.relief)
    ways, places = read_cuts(args.region)
    edges, flat = edges_of(ways, relief)
    args.out.mkdir(parents=True, exist_ok=True)
    nodes, edge_count, points = write_network(edges, args.out / "trails.network")
    log(f"trails.network: {nodes} nodes, {edge_count} edges, {points} points, "
        f"{(args.out / 'trails.network').stat().st_size:,} bytes; {relief.missing} points beyond the relief, "
        f"{flat} edges with no height at all")
    count = write_places(places, args.out / "places.sqlite")
    log(f"places.sqlite: {count} places, {(args.out / 'places.sqlite').stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
