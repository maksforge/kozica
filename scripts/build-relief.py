#!/usr/bin/env python3
"""Builds the relief archives from the three national lidar terrain models, per docs/phase-4.md § 2.

    uv venv map-data/relief/venv && uv pip install -p map-data/relief/venv -r scripts/relief-requirements.txt
    map-data/relief/venv/bin/python scripts/build-relief.py                 # every Region
    map-data/relief/venv/bin/python scripts/build-relief.py --region tatry  # one Region, to try it
    map-data/relief/venv/bin/python scripts/build-relief.py --sample 49.232 19.982

Run once, by hand, on a machine that can download about 220 GB; the output is published to its own
GitHub release and pinned in scripts/relief.pin (ADR-0009). Each sheet is resampled as it arrives,
kept as a small part in map-data/relief/parts/ and deleted, so a stopped run resumes where it was.
Needs osmium-tool and pyosmium-up-to-date for the state borders, as build-map-data.py does.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import gzip
import hashlib
import http.client
import importlib.util
import io
import json
import math
import os
import re
import socket
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import warnings
import zipfile
from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree

import laspy
import mapbox_vector_tile
import numpy as np
import rasterio
from PIL import Image
from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer
from pyproj import Transformer
from rasterio.features import rasterize
from rasterio.transform import Affine
from rasterio.warp import Resampling, reproject
from scipy.ndimage import distance_transform_edt, gaussian_filter, map_coordinates
import shapely
from shapely import affinity
from shapely.geometry import LineString, MultiPolygon, Polygon, box, shape
from shapely.ops import unary_union
from skimage.measure import find_contours

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / "map-data" / "relief"
PARTS = WORK / "parts"
REGIONS = ROOT / "scripts" / "regions"
PIN = ROOT / "scripts" / "osm-data.pin"

Z = 12
TILE = 512
WORLD = TILE << Z
EARTH = 2 * math.pi * 6378137
RES = EARTH / WORLD
MERCATOR = rasterio.crs.CRS.from_epsg(3857)
TIMEOUT = 120
CONTOUR_STEP = 20
INDEX_STEP = 100

BORDERS = {"poland": 49715, "slovakia": 14296, "czech-republic": 51684}

GUGIK = "https://mapy.geoportal.gov.pl/wss/service/PZGIK/{service}/WFS/Skorowidze"
GUGIK_SERVICES = ("NumerycznyModelTerenuEVRF2007", "NumerycznyModelTerenuKRON86")
CUZK_FEED = "https://atom.cuzk.gov.cz/DMR4G-SJTSK/DMR4G-SJTSK.xml"
CUZK_ZIP = "https://openzu.cuzk.gov.cz/opendata/DMR4G/epsg-5514/{code}.zip"
UGKK_WCS = (
    "https://inspirews.skgeodesy.sk/geoserver/el/ows?service=WCS&version=2.0.1&request=GetCoverage"
    "&coverageId=el__EL.GridCoverage&format=image/tiff&subset=E({x0},{x1})&subset=N({y0},{y1})&SCALEFACTOR=0.2"
)
UGKK_TILE = 5000
GEOID_SK = "https://cdn.proj.org/sk_gku_Slovakia_ETRS89h_to_Baltic1957.tif"
GEOID_SK_SHA256 = "d280e6f7f6c84714d4c060519cf2213a43c0f9064f2cd3de23265a2671c7e865"


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def fail(message: str) -> None:
    sys.exit(f"build-relief: {message}")


class DeviceHTTPSHandler(urllib.request.HTTPSHandler):
    """Binds downloads to one network interface, as `curl --interface` does."""

    def https_open(self, request):
        return self.do_open(self.connection, request)

    def connection(self, host, **kwargs):
        connection = http.client.HTTPSConnection(host, **kwargs)
        device = os.environ["KOZICA_RELIEF_DEVICE"].encode()

        def create(address, timeout=socket._GLOBAL_DEFAULT_TIMEOUT, source_address=None):
            host, port = address
            family, kind, proto, _, target = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)[0]
            sock = socket.socket(family, kind, proto)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BINDTODEVICE, device)
            sock.settimeout(timeout)
            sock.connect(target)
            return sock

        connection._create_connection = create
        return connection


def opener():
    if os.environ.get("KOZICA_RELIEF_DEVICE"):
        return urllib.request.build_opener(DeviceHTTPSHandler())
    return urllib.request.build_opener()


def fetch(url: str, attempts: int = 5) -> bytes:
    for attempt in range(attempts):
        try:
            with opener().open(url, timeout=TIMEOUT) as response:
                return response.read()
        except OSError as error:
            if attempt == attempts - 1:
                raise
            log(f"retrying {url}: {error}")
            time.sleep(2**attempt)


def reshape(func, geometry):
    return shapely.transform(geometry, lambda xy: np.column_stack(func(xy[:, 0], xy[:, 1])))


def map_build():
    spec = importlib.util.spec_from_file_location("build_map_data", ROOT / "scripts" / "build-map-data.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


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


def border(country: str, sequence: int, build) -> Polygon | MultiPolygon:
    cached = WORK / f"border-{country}-{sequence}.geojson"
    if not cached.exists():
        extract = build.extract_at(country, sequence)
        with tempfile.TemporaryDirectory() as scratch:
            relation = Path(scratch) / "relation.osm.pbf"
            subprocess.run(["osmium", "getid", "-r", "-o", relation, extract, f"r{BORDERS[country]}"], check=True)
            subprocess.run(
                ["osmium", "export", "-f", "geojson", "--geometry-types", "polygon", "-o", cached, relation],
                check=True,
            )
    features = json.loads(cached.read_text())["features"]
    return unary_union([shape(f["geometry"]) for f in features])




def to_pixel(lon, lat):
    lat = np.radians(np.asarray(lat, dtype=np.float64))
    x = (np.asarray(lon, dtype=np.float64) + 180) / 360 * WORLD
    y = (1 - np.log(np.tan(lat) + 1 / np.cos(lat)) / math.pi) / 2 * WORLD
    return x, y


def to_lonlat(x: float, y: float, z: int = Z) -> tuple[float, float]:
    world = TILE << z
    return x / world * 360 - 180, math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / world))))


def pixel_transform(x0: int, y0: int, z: int = Z) -> Affine:
    res = EARTH / (TILE << z)
    return Affine(res, 0, x0 * res - EARTH / 2, 0, -res, EARTH / 2 - y0 * res)


@dataclass(frozen=True)
class Grid:
    x0: int
    y0: int
    width: int
    height: int

    @staticmethod
    def around(area) -> Grid:
        west, south, east, north = area.bounds
        left, top = to_pixel(west, north)
        right, bottom = to_pixel(east, south)
        tx0, ty0 = int(left) // TILE, int(top) // TILE
        tx1, ty1 = int(right) // TILE + 1, int(bottom) // TILE + 1
        return Grid(tx0 * TILE, ty0 * TILE, (tx1 - tx0) * TILE, (ty1 - ty0) * TILE)

    def window(self, west: float, south: float, east: float, north: float, pad: int = 2):
        left, top = to_pixel(west, north)
        right, bottom = to_pixel(east, south)
        c0 = max(int(left) - self.x0 - pad, 0)
        r0 = max(int(top) - self.y0 - pad, 0)
        c1 = min(int(right) - self.x0 + pad + 1, self.width)
        r1 = min(int(bottom) - self.y0 + pad + 1, self.height)
        return (r0, c0, r1, c1) if r1 > r0 and c1 > c0 else None




@dataclass(frozen=True)
class Sheet:
    id: str
    source: str
    url: str
    bounds: tuple[float, float, float, float]
    kind: str


def gugik_index(area) -> list[Sheet]:
    """Per 1:5000 sheet, the newest lidar grid, else the newest of any kind (docs/phase-4.md § 2)."""
    cache = WORK / f"index-pl-{hashlib.sha256(area.wkb).hexdigest()[:12]}.json"
    if cache.exists():
        return [Sheet(**s) for s in json.loads(cache.read_text())]
    west, south, east, north = area.bounds
    to_wgs = Transformer.from_crs(2180, 4326).transform
    found: dict[str, list] = {}
    for service in GUGIK_SERVICES:
        capabilities = fetch(f"{GUGIK.format(service=service)}?service=WFS&request=GetCapabilities").decode()
        for year in sorted({int(y) for y in re.findall(r"<Name>gugik:SkorowidzNMT(\d{4})</Name>", capabilities)}):
            start = 0
            while True:
                query = urllib.parse.urlencode(
                    {
                        "service": "WFS", "version": "2.0.0", "request": "GetFeature",
                        "typeNames": f"gugik:SkorowidzNMT{year}", "count": 5000, "startIndex": start,
                        "BBOX": f"{south},{west},{north},{east},urn:ogc:def:crs:EPSG::4326",
                    }
                )
                root = ElementTree.fromstring(fetch(f"{GUGIK.format(service=service)}?{query}"))
                members = root.findall("{http://www.opengis.net/wfs/2.0}member")
                for member in members:
                    feature = member[0]
                    get = lambda name: (feature.findtext(f"{{http://www.gugik.gov.pl}}{name}") or "").strip()
                    if get("czy_ark_wypelniony") not in ("TAK", ""):
                        continue
                    coords = list(map(float, feature.find(".//{http://www.opengis.net/gml/3.2}posList").text.split()))
                    ring = [to_wgs(n, e)[::-1] for n, e in zip(coords[0::2], coords[1::2])]
                    outline = Polygon(ring)
                    if not outline.intersects(area):
                        continue
                    if get("format") != "ARC/INFO ASCII GRID" or not get("url_do_pobrania"):
                        continue
                    lidar = get("zrodlo_danych") == "Skaning laserowy"
                    spacing = re.match(r"[\d.]+", get("char_przestrz"))
                    resolution = float(spacing.group()) if spacing else 99.0
                    rank = (lidar, service.endswith("EVRF2007"), year, -resolution)
                    found.setdefault(get("godlo"), []).append(
                        (rank, Sheet(get("godlo"), "pl", get("url_do_pobrania"), outline.bounds,
                                     f"{'lidar' if lidar else 'photo'} {resolution:g} m {year} {service[-7:]}"))
                    )
                if len(members) < 5000:
                    break
                start += 5000
            log(f"index pl {service[-7:]} {year}: {len(found)} sheets so far")
    sheets = [max(entries, key=lambda e: (e[0], e[1].url))[1] for _, entries in sorted(found.items())]
    cache.write_text(json.dumps([s.__dict__ for s in sheets]))
    return sheets


def cuzk_index(area) -> list[Sheet]:
    cache = WORK / f"index-cz-{hashlib.sha256(area.wkb).hexdigest()[:12]}.json"
    if cache.exists():
        return [Sheet(**s) for s in json.loads(cache.read_text())]
    atom = "{http://www.w3.org/2005/Atom}"
    root = ElementTree.fromstring(fetch(CUZK_FEED))
    sheets = []
    for entry in root.findall(f"{atom}entry"):
        polygon = entry.findtext("{http://www.georss.org/georss}polygon")
        link = next((l.get("href") for l in entry.findall(f"{atom}link") if "datasetFeeds" in (l.get("href") or "")), None)
        if not polygon or not link:
            continue
        values = list(map(float, polygon.split()))
        outline = Polygon(zip(values[1::2], values[0::2]))
        if not outline.intersects(area):
            continue
        code = re.search(r"_([A-Z0-9]+)\.xml$", link).group(1)
        sheets.append(Sheet(code, "cz", CUZK_ZIP.format(code=code), outline.bounds, "lidar 5 m DMR 4G"))
    sheets.sort(key=lambda s: s.id)
    cache.write_text(json.dumps([s.__dict__ for s in sheets]))
    return sheets


def ugkk_index(area) -> list[Sheet]:
    to_tm = Transformer.from_crs(4326, 3046, always_xy=True).transform
    to_wgs = Transformer.from_crs(3046, 4326, always_xy=True).transform
    projected = reshape(to_tm, area)
    west, south, east, north = projected.bounds
    sheets = []
    for x in range(int(west // UGKK_TILE) * UGKK_TILE, int(east) + 1, UGKK_TILE):
        for y in range(int(south // UGKK_TILE) * UGKK_TILE, int(north) + 1, UGKK_TILE):
            cell = box(x, y, x + UGKK_TILE, y + UGKK_TILE)
            if cell.intersects(projected):
                url = UGKK_WCS.format(x0=x, x1=x + UGKK_TILE, y0=y, y1=y + UGKK_TILE)
                bounds = reshape(to_wgs, cell).bounds
                sheets.append(Sheet(f"sk-{x}-{y}", "sk", url, bounds, "lidar 5 m DMR 5.0 (WCS)"))
    return sheets




def read_sheet(sheet: Sheet, geoid: Path) -> tuple[np.ndarray, Affine, int]:
    """The sheet's heights in metres above its national datum, with its transform and CRS."""
    data = fetch(sheet.url)
    if sheet.source == "pl":
        with rasterio.open(io.BytesIO(data)) as source:
            heights = source.read(1, masked=True).astype(np.float32).filled(np.nan)
            return heights, source.transform, 2180
    if sheet.source == "cz":
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            name = next(n for n in archive.namelist() if n.lower().endswith(".laz"))
            points = laspy.read(io.BytesIO(archive.read(name)))
        x, y, z = np.asarray(points.x), np.asarray(points.y), np.asarray(points.z, dtype=np.float32)
        step = 5.0
        left, top = x.min(), y.max()
        cols = np.rint((x - left) / step).astype(np.int64)
        rows = np.rint((top - y) / step).astype(np.int64)
        heights = np.full((rows.max() + 1, cols.max() + 1), np.nan, dtype=np.float32)
        heights[rows, cols] = z
        return heights, Affine(step, 0, left - step / 2, 0, -step, top + step / 2), 5514
    with rasterio.open(io.BytesIO(data)) as source:
        heights = source.read(1, masked=True).astype(np.float32).filled(np.nan)
        heights[heights > 1e30] = np.nan
        transform = source.transform
    rows, cols = np.indices(heights.shape)
    xs, ys = transform * (cols + 0.5, rows + 0.5)
    lon, lat = Transformer.from_crs(3046, 4937, always_xy=True).transform(xs, ys)
    with rasterio.open(geoid) as grid:
        undulation = grid.read(1).astype(np.float64)
        gcols, grows = ~grid.transform * (lon, lat)
    # Ellipsoidal heights to Slovakia's Baltic heights: H = h - N, checked at Kriváň (docs/phase-4.md § 2).
    n = map_coordinates(undulation, [grows - 0.5, gcols - 0.5], order=1, mode="nearest")
    return heights - n.astype(np.float32), transform, 3046


def build_part(sheet: Sheet, grid: Grid, geoid: Path) -> str:
    part = PARTS / f"{sheet.source}-{sheet.id}.npz"
    if part.exists():
        return "kept"
    window = grid.window(*sheet.bounds)
    if window is None:
        return "outside"
    heights, transform, crs = read_sheet(sheet, geoid)
    r0, c0, r1, c1 = window
    target = np.full((r1 - r0, c1 - c0), np.nan, dtype=np.float32)
    reproject(
        heights, target,
        src_transform=transform, src_crs=rasterio.crs.CRS.from_epsg(crs), src_nodata=np.nan,
        dst_transform=pixel_transform(grid.x0 + c0, grid.y0 + r0), dst_crs=MERCATOR, dst_nodata=np.nan,
        resampling=Resampling.average,
    )
    valid = ~np.isnan(target)
    centimetres = np.where(valid, np.rint(target * 100), 0).astype(np.int32)
    scratch = part.with_suffix(".part.npz")
    np.savez_compressed(scratch, window=np.array(window), cm=centimetres, valid=valid)
    scratch.rename(part)
    return "built"




def merge(grid: Grid, sheets: list[Sheet]) -> np.ndarray:
    total = np.zeros((grid.height, grid.width), dtype=np.int64)
    count = np.zeros((grid.height, grid.width), dtype=np.uint8)
    for sheet in sheets:
        part = PARTS / f"{sheet.source}-{sheet.id}.npz"
        if not part.exists():
            continue
        with np.load(part) as loaded:
            r0, c0, r1, c1 = loaded["window"]
            total[r0:r1, c0:c1] += loaded["cm"]
            count[r0:r1, c0:c1] += loaded["valid"]
    heights = np.full((grid.height, grid.width), np.nan, dtype=np.float32)
    covered = count > 0
    heights[covered] = (total[covered] / count[covered] / 100).astype(np.float32)
    return heights


def pixel_geometry(geometry, grid: Grid):
    return affinity.affine_transform(reshape(lambda lon, lat: to_pixel(lon, lat), geometry), [1, 0, 0, 1, -grid.x0, -grid.y0])


def check_gaps(heights: np.ndarray, grid: Grid, inside, regions: dict) -> None:
    mask = rasterize([(pixel_geometry(inside, grid), 1)], out_shape=heights.shape, dtype=np.uint8).astype(bool)
    gaps = mask & np.isnan(heights)
    if not gaps.any():
        log(f"no gaps among {mask.sum():,} cells")
        return
    rows, cols = np.nonzero(gaps)
    for r, c in list(zip(rows, cols))[:: max(len(rows) // 20, 1)][:20]:
        lon, lat = to_lonlat(grid.x0 + c + 0.5, grid.y0 + r + 0.5)
        where = next((name for name, area in regions.items() if area.contains(box(lon, lat, lon, lat))), "?")
        log(f"  gap at {lat:.5f} N {lon:.5f} E ({where})")
    fail(f"{len(rows):,} cells inside the Regions have no height (docs/phase-4.md § 2)")




def halve(heights: np.ndarray, x0: int, y0: int) -> tuple[np.ndarray, int, int]:
    if x0 % 2:
        heights, x0 = np.pad(heights, ((0, 0), (1, 0)), constant_values=np.nan), x0 - 1
    if y0 % 2:
        heights, y0 = np.pad(heights, ((1, 0), (0, 0)), constant_values=np.nan), y0 - 1
    h, w = heights.shape
    heights = np.pad(heights, ((0, h % 2), (0, w % 2)), constant_values=np.nan)
    blocks = heights.reshape(heights.shape[0] // 2, 2, heights.shape[1] // 2, 2)
    with np.errstate(invalid="ignore"), warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        return np.nanmean(blocks, axis=(1, 3)).astype(np.float32), x0 // 2, y0 // 2


def tile_window(heights: np.ndarray, x0: int, y0: int, tx: int, ty: int, pad: int = 0) -> np.ndarray:
    out = np.full((TILE + 2 * pad, TILE + 2 * pad), np.nan, dtype=np.float32)
    left, top = tx * TILE - pad - x0, ty * TILE - pad - y0
    r0, c0 = max(top, 0), max(left, 0)
    r1, c1 = min(top + TILE + 2 * pad, heights.shape[0]), min(left + TILE + 2 * pad, heights.shape[1])
    if r1 > r0 and c1 > c0:
        out[r0 - top : r1 - top, c0 - left : c1 - left] = heights[r0:r1, c0:c1]
    return out


def terrarium(block: np.ndarray) -> bytes:
    """Lossless WebP: a lossy encoder rewrites the low bits, which are the heights."""
    missing = np.isnan(block)
    if missing.any():
        nearest = distance_transform_edt(missing, return_distances=False, return_indices=True)
        block = block[tuple(nearest)]
    # Tenths of a metre: finer noise is the 1 m sources averaged, and doubles the archive.
    tenths = np.rint(block.astype(np.float64) * 10) / 10
    q = np.rint((tenths + 32768) * 256).astype(np.int64)
    rgb = np.stack([(q >> 16) & 255, (q >> 8) & 255, q & 255], axis=-1).astype(np.uint8)
    out = io.BytesIO()
    Image.fromarray(rgb, "RGB").save(out, format="WEBP", lossless=True, quality=100, method=4, exact=True)
    return out.getvalue()


def contour_tile(block: np.ndarray, pad: int, index_only: bool) -> bytes | None:
    """Lines traced from the same grid as the shading, so the two and phase 5's ascent agree."""
    valid = ~np.isnan(block)
    if not valid.any():
        return None
    filled = np.where(valid, block, np.nanmean(block))
    smooth = gaussian_filter(filled, sigma=1.0)
    step = INDEX_STEP if index_only else CONTOUR_STEP
    low, high = math.floor(np.nanmin(block[valid]) / step + 1) * step, np.nanmax(block[valid])
    scale = 4096 / TILE
    clip = box(-8, -8, TILE + 8, TILE + 8)
    features = []
    for level in range(low, int(high) + 1, step):
        for line in find_contours(smooth, level, mask=valid):
            if len(line) < 2:
                continue
            geometry = LineString(line[:, ::-1] - pad).intersection(clip).simplify(0.5)
            if geometry.is_empty:
                continue
            geometry = affinity.scale(geometry, scale, scale, origin=(0, 0))
            features.append({"geometry": geometry.wkt, "properties": {"ele": level, "index": level % INDEX_STEP == 0}})
    if not features:
        return None
    features.sort(key=lambda f: (f["properties"]["ele"], f["geometry"]))
    mvt = mapbox_vector_tile.encode(
        [{"name": "contours", "features": features}], default_options={"y_coord_down": True, "extents": 4096}
    )
    return gzip.compress(mvt, compresslevel=9, mtime=0)


def tiles_of(heights: np.ndarray, x0: int, y0: int):
    for ty in range(y0 // TILE, (y0 + heights.shape[0] - 1) // TILE + 1):
        for tx in range(x0 // TILE, (x0 + heights.shape[1] - 1) // TILE + 1):
            yield tx, ty


def write_archive(path: Path, tiles: dict[int, bytes], tile_type, compression, zooms, bounds, metadata) -> None:
    west, south, east, north = bounds
    with path.open("wb") as f:
        writer = Writer(f)
        for tile_id in sorted(tiles):
            writer.write_tile(tile_id, tiles[tile_id])
        writer.finalize(
            {
                "tile_type": tile_type, "tile_compression": compression,
                "min_zoom": zooms[0], "max_zoom": zooms[1],
                "min_lon_e7": int(west * 1e7), "min_lat_e7": int(south * 1e7),
                "max_lon_e7": int(east * 1e7), "max_lat_e7": int(north * 1e7),
                "center_zoom": zooms[1],
                "center_lon_e7": int((west + east) / 2 * 1e7), "center_lat_e7": int((south + north) / 2 * 1e7),
            },
            metadata,
        )


def build_archives(heights: np.ndarray, grid: Grid, inside, attribution: str) -> None:
    mask = rasterize([(pixel_geometry(inside.buffer(0.01), grid), 1)], out_shape=heights.shape, dtype=np.uint8)
    heights = np.where(mask.astype(bool), heights, np.nan).astype(np.float32)
    relief: dict[int, bytes] = {}
    contours: dict[int, bytes] = {}
    level, x0, y0 = heights, grid.x0, grid.y0
    for z in range(Z, -1, -1):
        for tx, ty in tiles_of(level, x0, y0):
            block = tile_window(level, x0, y0, tx, ty)
            if np.isnan(block).all():
                continue
            relief[zxy_to_tileid(z, tx, ty)] = terrarium(block)
            if z >= Z - 1:
                pad = 8
                mvt = contour_tile(tile_window(level, x0, y0, tx, ty, pad), pad, index_only=z < Z)
                if mvt:
                    contours[zxy_to_tileid(z, tx, ty)] = mvt
        log(f"zoom {z}: {sum(1 for t in relief if zxy_to_tileid(z, 0, 0) <= t < zxy_to_tileid(z + 1, 0, 0))} relief tiles")
        if z:
            level, x0, y0 = halve(level, x0, y0)
    bounds = inside.bounds
    write_archive(
        WORK / "relief.pmtiles", relief, TileType.WEBP, Compression.NONE, (0, Z), bounds,
        {"name": "Kozica relief", "encoding": "terrarium", "attribution": attribution},
    )
    write_archive(
        WORK / "contours.pmtiles", contours, TileType.MVT, Compression.GZIP, (Z - 1, Z), bounds,
        {
            "name": "Kozica contours", "attribution": attribution,
            "vector_layers": [{"id": "contours", "fields": {"ele": "Number", "index": "Boolean"}, "minzoom": Z - 1, "maxzoom": Z}],
        },
    )


def sample(latitude: float, longitude: float) -> None:
    from pmtiles.reader import MmapSource, Reader

    x, y = to_pixel(longitude, latitude)
    with (WORK / "relief.pmtiles").open("rb") as f:
        data = Reader(MmapSource(f)).get(Z, int(x) // TILE, int(y) // TILE)
    if data is None:
        fail("no relief tile there")
    rgb = np.asarray(Image.open(io.BytesIO(data)).convert("RGB"), dtype=np.float64)[int(y) % TILE, int(x) % TILE]
    print(f"{rgb[0] * 256 + rgb[1] + rgb[2] / 256 - 32768:.2f} m")


def sha256(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(1 << 20):
            hasher.update(chunk)
    return hasher.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--region", action="append", help="build only these Regions")
    parser.add_argument("--jobs", type=int, default=8, help="sheets fetched at once")
    parser.add_argument("--device", help="network interface to download through, e.g. enp6s0")
    parser.add_argument("--sample", nargs=2, type=float, metavar=("LAT", "LON"))
    args = parser.parse_args()
    if args.device:
        os.environ["KOZICA_RELIEF_DEVICE"] = args.device
    if args.sample:
        sample(*args.sample)
        return

    WORK.mkdir(parents=True, exist_ok=True)
    PARTS.mkdir(exist_ok=True)
    regions = {p.stem: read_poly(p) for p in sorted(REGIONS.glob("*.poly"))}
    chosen = {name: regions[name] for name in args.region} if args.region else regions
    build = map_build()
    pin = build.read_pin(PIN)
    countries = {country: border(country, pin[country], build) for country in BORDERS}
    union = unary_union(list(chosen.values()))
    inside = union.intersection(unary_union(list(countries.values())))
    grid = Grid.around(inside)
    log(f"grid {grid.width} x {grid.height} cells")

    geoid = WORK / "geoid-sk.tif"
    if not geoid.exists() or sha256(geoid) != GEOID_SK_SHA256:
        geoid.write_bytes(fetch(GEOID_SK))
        if sha256(geoid) != GEOID_SK_SHA256:
            fail("Slovakia's height grid does not match its pinned hash")

    sheets = [
        *gugik_index(union.intersection(countries["poland"])),
        *cuzk_index(union.intersection(countries["czech-republic"])),
        *ugkk_index(union.intersection(countries["slovakia"])),
    ]
    kinds: dict[str, int] = {}
    for sheet in sheets:
        kind = f"{sheet.source} {sheet.kind.split(' 20')[0] if sheet.source == 'pl' else sheet.kind}"
        kinds[kind] = kinds.get(kind, 0) + 1
    log(f"{len(sheets)} sheets: " + ", ".join(f"{n} {k}" for k, n in sorted(kinds.items())))

    started = time.monotonic()
    with concurrent.futures.ProcessPoolExecutor(max_workers=args.jobs) as pool:
        futures = {pool.submit(build_part, sheet, grid, geoid): sheet for sheet in sheets}
        for done, future in enumerate(concurrent.futures.as_completed(futures), 1):
            sheet = futures[future]
            try:
                future.result()
            except Exception as error:
                fail(f"{sheet.source} sheet {sheet.id} ({sheet.url}): {error}")
            if done % 50 == 0 or done == len(sheets):
                log(f"{done}/{len(sheets)} sheets, {time.monotonic() - started:.0f} s")

    heights = merge(grid, sheets)
    check_gaps(heights, grid, inside, chosen)
    attribution = "Relief: GUGiK, ÚGKK SR, ČÚZK"
    build_archives(heights, grid, inside, attribution)
    for name in ("relief.pmtiles", "contours.pmtiles"):
        path = WORK / name
        log(f"{name}: {path.stat().st_size:,} bytes, sha256 {sha256(path)}")


if __name__ == "__main__":
    main()
