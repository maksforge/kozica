#!/usr/bin/env python3
"""Prints one tile of an uncompressed PMTiles v3 archive: its layers, features and attributes.

    python3 scripts/map/decode-tile.py map-data/regions/tatry.pmtiles 14 9101 5612

Reads R10 of docs/phase-1.md, a shared way's sorted colours, without a renderer.
"""

from __future__ import annotations

import gzip
import struct
import sys
from pathlib import Path

GEOMETRY = {0: "unknown", 1: "point", 2: "line", 3: "polygon"}


def varint(data: bytes, pos: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, pos
        shift += 7


def decompress(data: bytes, compression: int) -> bytes:
    if compression in (0, 1):
        return data
    if compression == 2:
        return gzip.decompress(data)
    sys.exit(f"compression {compression} is not supported")


def tile_id(z: int, x: int, y: int) -> int:
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


def directory(data: bytes) -> list[tuple[int, int, int, int]]:
    count, pos = varint(data, 0)
    ids, runs, lengths, offsets = [], [], [], []
    last = 0
    for _ in range(count):
        delta, pos = varint(data, pos)
        last += delta
        ids.append(last)
    for _ in range(count):
        value, pos = varint(data, pos)
        runs.append(value)
    for _ in range(count):
        value, pos = varint(data, pos)
        lengths.append(value)
    for i in range(count):
        value, pos = varint(data, pos)
        offsets.append(offsets[i - 1] + lengths[i - 1] if value == 0 and i > 0 else value - 1)
    return list(zip(ids, runs, lengths, offsets))


def find_tile(archive: bytes, wanted: int) -> bytes | None:
    header = archive[:127]
    if header[:7] != b"PMTiles" or header[7] != 3:
        sys.exit("not a PMTiles v3 archive")
    root_offset, root_length = struct.unpack_from("<QQ", header, 8)
    leaf_offset = struct.unpack_from("<Q", header, 40)[0]
    data_offset = struct.unpack_from("<Q", header, 56)[0]
    internal, tiles = header[97], header[98]

    offset, length = root_offset, root_length
    for _ in range(4):
        entries = directory(decompress(archive[offset : offset + length], internal))
        match = None
        for entry in entries:
            if entry[0] > wanted:
                break
            match = entry
        if match is None:
            return None
        entry_id, run, entry_length, entry_offset = match
        if run == 0:
            offset, length = leaf_offset + entry_offset, entry_length
            continue
        if wanted >= entry_id + run:
            return None
        start = data_offset + entry_offset
        return decompress(archive[start : start + entry_length], tiles)
    return None


def fields(data: bytes):
    pos = 0
    while pos < len(data):
        key, pos = varint(data, pos)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, pos = varint(data, pos)
        elif wire == 1:
            value, pos = data[pos : pos + 8], pos + 8
        elif wire == 2:
            size, pos = varint(data, pos)
            value, pos = data[pos : pos + size], pos + size
        elif wire == 5:
            value, pos = data[pos : pos + 4], pos + 4
        else:
            sys.exit(f"wire type {wire} is not supported")
        yield number, wire, value


def packed(data: bytes) -> list[int]:
    values, pos = [], 0
    while pos < len(data):
        value, pos = varint(data, pos)
        values.append(value)
    return values


def value_of(data: bytes):
    for number, _, value in fields(data):
        if number == 1:
            return value.decode()
        if number == 2:
            return struct.unpack("<f", value)[0]
        if number == 3:
            return struct.unpack("<d", value)[0]
        if number in (4, 5):
            return value
        if number == 6:
            return (value >> 1) ^ -(value & 1)
        if number == 7:
            return bool(value)
    return None


def print_layer(data: bytes) -> None:
    name, keys, values, features = "", [], [], []
    for number, _, value in fields(data):
        if number == 1:
            name = value.decode()
        elif number == 2:
            features.append(value)
        elif number == 3:
            keys.append(value.decode())
        elif number == 4:
            values.append(value_of(value))
    print(f"{name}: {len(features)} features")
    for feature in features:
        feature_id, tags, geometry = None, [], 0
        for number, wire, value in fields(feature):
            if number == 1:
                feature_id = value
            elif number == 2:
                tags = packed(value) if wire == 2 else [value]
            elif number == 3:
                geometry = value
        attributes = {keys[tags[i]]: values[tags[i + 1]] for i in range(0, len(tags), 2)}
        print(f"  {GEOMETRY.get(geometry, geometry)} {feature_id} {attributes}")


def main() -> None:
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    archive = Path(sys.argv[1]).read_bytes()
    z, x, y = (int(arg) for arg in sys.argv[2:])
    tile = find_tile(archive, tile_id(z, x, y))
    if tile is None:
        sys.exit(f"no tile {z}/{x}/{y} in {sys.argv[1]}")
    for number, _, value in fields(tile):
        if number == 3:
            print_layer(value)


if __name__ == "__main__":
    main()
