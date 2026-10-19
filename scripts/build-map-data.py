#!/usr/bin/env python3
"""Builds the Tatry Region's map archive from the pinned OSM data, per docs/phase-1.md § 2 and § 4.

    python3 scripts/build-map-data.py                          # region_tatry/src/main/assets/tatry.pmtiles
    python3 scripts/build-map-data.py --check-determinism      # R4
    python3 scripts/build-map-data.py --patch-from 4935        # R6
    python3 scripts/build-map-data.py --patch-from-extract edited.osm.pbf   # R7

Needs osmium-tool, pyosmium-up-to-date (pyosmium 4.2+) and JDK 21; the patch flags also need bsdiff
and zstd. Downloads are kept in map-data/ and re-verified on every run.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / "map-data"
PIN = ROOT / "scripts/osm-data.pin"
POLY = ROOT / "scripts/regions/tatry.poly"
PROFILE = ROOT / "scripts/map/KozicaProfile.java"
OUTPUT = ROOT / "region_tatry/src/main/assets/tatry.pmtiles"

GEOFABRIK = "https://download.geofabrik.de/europe"
UPDATES = f"{GEOFABRIK}/poland-updates"
PLANETILER = WORK / "planetiler-0.10.2.jar"
PLANETILER_URL = "https://github.com/onthegomap/planetiler/releases/download/v0.10.2/planetiler.jar"
PLANETILER_SHA256 = "f310bd0413e2e4512b27f4046d418664e8e1d3bf31603c2a70e23de06c167e4d"

REPLICATION_HEADER = (
    "osmosis_replication_sequence_number",
    "osmosis_replication_timestamp",
)
SIZE_LIMIT = 1_500_000_000
OLDEST_EXTRACT_DAYS = 400
TIMEOUT = 60


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def fail(message: str) -> None:
    sys.exit(f"build-map-data: {message}")


def run(*command: object, **kwargs) -> subprocess.CompletedProcess:
    log("$ " + " ".join(str(part) for part in command))
    return subprocess.run([str(part) for part in command], **{"check": True, **kwargs})


def require(*tools: str) -> None:
    missing = [tool for tool in tools if shutil.which(tool) is None]
    if missing:
        fail(f"missing on PATH: {', '.join(missing)}")


def digest(path: Path, algorithm: str) -> str:
    hasher = hashlib.new(algorithm)
    with path.open("rb") as file:
        while chunk := file.read(1 << 20):
            hasher.update(chunk)
    return hasher.hexdigest()


def download(url: str, target: Path) -> None:
    log(f"downloading {url}")
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + ".part")
    with urllib.request.urlopen(url, timeout=TIMEOUT) as response, partial.open("wb") as file:
        shutil.copyfileobj(response, file, 1 << 20)
    partial.rename(target)


def planetiler() -> Path:
    if not PLANETILER.exists():
        download(PLANETILER_URL, PLANETILER)
    if digest(PLANETILER, "sha256") != PLANETILER_SHA256:
        fail(f"{PLANETILER} does not match its pinned sha256; delete it and re-run")
    return PLANETILER


def read_pin() -> int:
    return int(PIN.read_text().strip())


def pin_date(sequence: int) -> dt.date:
    digits = f"{sequence:09d}"
    url = f"{UPDATES}/{digits[:3]}/{digits[3:6]}/{digits[6:]}.state.txt"
    with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
        state = response.read().decode()
    found = re.search(r"^timestamp=(\d{4}-\d{2}-\d{2})", state, re.MULTILINE)
    if not found:
        fail(f"no timestamp in {url}")
    return dt.date.fromisoformat(found.group(1))


def exists(url: str) -> bool:
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=TIMEOUT):
            return True
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return False
        raise


def header_sequence(pbf: Path) -> int:
    result = run(
        "osmium", "fileinfo", "-F", "pbf", "-g", "header.option.osmosis_replication_sequence_number", pbf,
        capture_output=True, text=True,
    )
    value = result.stdout.strip()
    if not value.isdigit():
        fail(f"{pbf} has no replication sequence in its header")
    return int(value)


def dated_extract(name: str) -> Path:
    target = WORK / "extracts" / name
    with urllib.request.urlopen(f"{GEOFABRIK}/{name}.md5", timeout=TIMEOUT) as response:
        expected = response.read().decode().split()[0]
    if target.exists() and digest(target, "md5") == expected:
        return target
    download(f"{GEOFABRIK}/{name}", target)
    if digest(target, "md5") != expected:
        target.unlink()
        fail(f"{name} does not match Geofabrik's md5")
    return target


def newest_extract_before(sequence: int) -> Path:
    day = pin_date(sequence)
    for back in range(OLDEST_EXTRACT_DAYS):
        name = f"poland-{day - dt.timedelta(days=back):%y%m%d}.osm.pbf"
        if not exists(f"{GEOFABRIK}/{name}"):
            continue
        extract = dated_extract(name)
        if header_sequence(extract) <= sequence:
            return extract
    fail(f"Geofabrik keeps no dated extract from before sequence {sequence}")


def extract_at(sequence: int) -> Path:
    target = WORK / f"poland-{sequence}.osm.pbf"
    if target.exists() and header_sequence(target) == sequence:
        return target
    source = newest_extract_before(sequence)
    if header_sequence(source) == sequence:
        return source
    partial = target.with_name(target.name + ".part")
    partial.unlink(missing_ok=True)
    run(
        "pyosmium-up-to-date", "-v", "--end-id", sequence, "--force-update-of-old-planet",
        "--format", "pbf", "-o", partial, source,
    )
    if (found := header_sequence(partial)) != sequence:
        fail(f"the update reached sequence {found}, not the pin {sequence}")
    partial.rename(target)
    return target


def tile(extract: Path, archive: Path, replicated: bool = True) -> Path:
    scratch = WORK / "tmp" / archive.stem
    shutil.rmtree(scratch, ignore_errors=True)
    scratch.mkdir(parents=True)
    cut = scratch / "region.osm.pbf"
    run(
        "osmium", "extract", "-p", POLY, "-s", "smart", "--set-bounds", "-O", "-o", cut,
        *(f"--output-header={option}!" for option in REPLICATION_HEADER), extract,
    )
    if replicated and header_sequence(cut) != header_sequence(extract):
        fail(f"the cut lost the replication header of {extract.name}")
    archive.parent.mkdir(parents=True, exist_ok=True)
    result = run(
        "java", "-Xmx3g", "-cp", planetiler(), PROFILE,
        f"--osm_path={cut}", f"--output={archive}", f"--tmpdir={scratch / 'planetiler'}",
        "--tile_compression=none", "--maxzoom=14",
        capture_output=True, text=True, check=False,
    )
    output = result.stdout + result.stderr
    planetiler_log = scratch.parent / f"{archive.stem}.log"
    planetiler_log.write_text(output)
    if result.returncode != 0 or re.search(r"\bERR\b", output):
        fail(f"planetiler logged errors; see {planetiler_log}")
    shutil.rmtree(scratch)
    size = archive.stat().st_size
    if size > SIZE_LIMIT:
        fail(f"{archive.name} is {size:,} bytes, over the {SIZE_LIMIT:,} limit of a Play asset pack")
    log(f"{archive}: {size:,} bytes, sha256 {digest(archive, 'sha256')}")
    return archive


def check_determinism(sequence: int) -> None:
    extract = extract_at(sequence)
    first = tile(extract, WORK / "determinism/first.pmtiles")
    second = tile(extract, WORK / "determinism/second.pmtiles")
    if first.read_bytes() != second.read_bytes():
        fail(f"two builds at sequence {sequence} differ: {first} {second}")
    print(f"deterministic: two builds at sequence {sequence} are byte-identical")


def zstd_size(path: Path) -> int:
    result = run("zstd", "-19", "-q", "-c", path, capture_output=True)
    return len(result.stdout)


def measure_patch(older: Path, newer: Path) -> None:
    patch = WORK / "patch/older-to-newer.bsdiff"
    run("bsdiff", older, newer, patch)
    patch_size = patch.stat().st_size
    raw = newer.stat().st_size
    compressed = zstd_size(newer)
    print(f"patch {patch_size:,} bytes")
    print(f"newer archive {raw:,} bytes raw: {patch_size / raw:.2%}")
    print(f"newer archive {compressed:,} bytes zstd -19: {patch_size / compressed:.2%}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check-determinism", action="store_true", help="build twice and compare bytes")
    mode.add_argument("--patch-from", type=int, metavar="PIN", help="measure the patch from an older pin")
    mode.add_argument("--patch-from-extract", type=Path, metavar="PBF", help="measure the patch from a local PBF")
    args = parser.parse_args()

    require("osmium", "pyosmium-up-to-date", "java")
    if args.patch_from is not None or args.patch_from_extract is not None:
        require("bsdiff", "zstd")
    sequence = read_pin()

    if args.check_determinism:
        check_determinism(sequence)
    elif args.patch_from is not None:
        older = tile(extract_at(args.patch_from), WORK / "patch/older.pmtiles")
        measure_patch(older, tile(extract_at(sequence), OUTPUT))
    elif args.patch_from_extract is not None:
        older = tile(args.patch_from_extract.resolve(), WORK / "patch/older.pmtiles", replicated=False)
        measure_patch(older, tile(extract_at(sequence), OUTPUT))
    else:
        tile(extract_at(sequence), OUTPUT)


if __name__ == "__main__":
    main()
