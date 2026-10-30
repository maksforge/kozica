#!/usr/bin/env python3
"""Builds every Region's map archive from the pinned OSM data, per docs/phase-2b.md § 3.

    python3 scripts/build-map-data.py                          # region_<id>/src/main/assets/<id>.pmtiles
    python3 scripts/build-map-data.py --region tatry           # one Region
    python3 scripts/build-map-data.py --check-determinism tatry
    python3 scripts/build-map-data.py --discard-extracts       # CI: delete each country once it is cut
    python3 scripts/build-map-data.py --region tatry --patch-from old.pin
    python3 scripts/build-map-data.py --region tatry --patch-from-extract edited.osm.pbf

The pin is one Geofabrik extract and replication sequence per line, all at one moment. Each extract
is cut once for every Region, and each Region's cuts are merged. Needs osmium-tool,
pyosmium-up-to-date (pyosmium 4.2+), JDK 21, and for the network pyosmium and Pillow importable by the
Python running this; the patch flags also need bsdiff and zstd.
Downloads are kept in map-data/ and re-verified on every run.
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
REGIONS = ROOT / "scripts/regions"
PROFILE = ROOT / "scripts/map/KozicaProfile.java"
NETWORK = ROOT / "scripts/build-network.py"
RELIEF = ROOT / "relief/src/main/assets/relief.pmtiles"
BASE_ASSETS = WORK / "base-assets"
BASE_FILES = ("trails.network", "trails.edges", "places.sqlite")

GEOFABRIK = "https://download.geofabrik.de/europe"
PLANETILER = WORK / "planetiler-0.10.2.jar"
PLANETILER_URL = "https://github.com/onthegomap/planetiler/releases/download/v0.10.2/planetiler.jar"
PLANETILER_SHA256 = "f310bd0413e2e4512b27f4046d418664e8e1d3bf31603c2a70e23de06c167e4d"

SEQUENCE = "osmosis_replication_sequence_number"
TIMESTAMP = "osmosis_replication_timestamp"
SIZE_LIMIT = 1_500_000_000
OLDEST_EXTRACT_DAYS = 400
TIMEOUT = 60

Pin = dict[str, int]


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


def read_pin(path: Path) -> Pin:
    if not path.is_file():
        fail(f"no pin file at {path}")
    pin = {}
    for line in path.read_text().splitlines():
        if not line.strip():
            continue
        if line.strip().isdigit():
            fail(f"{path} is a one-number pin, Poland's alone from before phase 2b; write it as `poland {line.strip()}`")
        if not (found := re.fullmatch(r"\s*(\S+)\s+(\d+)\s*", line)):
            fail(f"{path}: `{line}` is not `<geofabrik name> <sequence>`")
        country, sequence = found.groups()
        if country in pin:
            fail(f"{path} pins {country} twice")
        pin[country] = int(sequence)
    if not pin:
        fail(f"{path} names no extract")
    return pin


def state_timestamp(country: str, sequence: int) -> str:
    digits = f"{sequence:09d}"
    url = f"{GEOFABRIK}/{country}-updates/{digits[:3]}/{digits[3:6]}/{digits[6:]}.state.txt"
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
            state = response.read().decode()
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        fail(f"Geofabrik has no {country} replication at sequence {sequence}: is `{country}` its extract's name?")
    found = re.search(r"^timestamp=(.+)$", state, re.MULTILINE)
    if not found:
        fail(f"no timestamp in {url}")
    return found.group(1).replace("\\:", ":")


def moment(pin: Pin) -> str:
    stamps = {country: state_timestamp(country, sequence) for country, sequence in pin.items()}
    if len(set(stamps.values())) != 1:
        fail(f"the pin is not one moment: {stamps}")
    return next(iter(stamps.values()))


def header(pbf: Path, option: str) -> str:
    result = run(
        "osmium", "fileinfo", "-F", "pbf", "-g", f"header.option.{option}", pbf,
        capture_output=True, text=True,
    )
    return result.stdout.strip()


def header_sequence(pbf: Path) -> int:
    value = header(pbf, SEQUENCE)
    if not value.isdigit():
        fail(f"{pbf} has no replication sequence in its header")
    return int(value)


def dated_extract(name: str) -> Path | None:
    """None once Geofabrik has thinned the extract or its md5 away; it removes the two separately."""
    target = WORK / "extracts" / name
    try:
        with urllib.request.urlopen(f"{GEOFABRIK}/{name}.md5", timeout=TIMEOUT) as response:
            expected = response.read().decode().split()[0]
        if target.exists() and digest(target, "md5") == expected:
            return target
        download(f"{GEOFABRIK}/{name}", target)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise
    if digest(target, "md5") != expected:
        target.unlink()
        fail(f"{name} does not match Geofabrik's md5")
    return target


def newest_extract_before(country: str, sequence: int) -> Path:
    day = dt.date.fromisoformat(state_timestamp(country, sequence)[:10])
    for back in range(OLDEST_EXTRACT_DAYS):
        name = f"{country}-{day - dt.timedelta(days=back):%y%m%d}.osm.pbf"
        if (extract := dated_extract(name)) and header_sequence(extract) <= sequence:
            return extract
    fail(f"Geofabrik keeps no dated {country} extract from before sequence {sequence}")


def extract_at(country: str, sequence: int) -> Path:
    target = WORK / f"{country}-{sequence}.osm.pbf"
    if target.exists() and header_sequence(target) == sequence:
        return target
    source = newest_extract_before(country, sequence)
    if header_sequence(source) == sequence:
        return source
    partial = target.with_name(target.name + ".part")
    partial.unlink(missing_ok=True)
    run(
        "pyosmium-up-to-date", "-v", "--end-id", sequence, "--force-update-of-old-planet",
        "--format", "pbf", "-o", partial, source,
    )
    if (found := header_sequence(partial)) != sequence:
        fail(f"the {country} update reached sequence {found}, not the pin {sequence}")
    partial.rename(target)
    return target


def discard(country: str) -> None:
    for extract in [*WORK.glob(f"{country}-*.osm.pbf"), *(WORK / "extracts").glob(f"{country}-*.osm.pbf")]:
        extract.unlink()


def polygon(region: str) -> Path:
    return REGIONS / f"{region}.poly"


def bounds(region: str) -> str:
    points = [
        tuple(map(float, line.split()))
        for line in polygon(region).read_text().splitlines()
        if re.match(r"\s+-?\d", line)
    ]
    lons, lats = [lon for lon, _ in points], [lat for _, lat in points]
    return f"{min(lons)},{min(lats)},{max(lons)},{max(lats)}"


def cut(country: str, extract: Path, regions: list[str], into: Path) -> dict[str, Path]:
    """This country's part of every Region, one osmium pass each, to fit a runner (docs/phase-2b.md § 3)."""
    shutil.rmtree(into, ignore_errors=True)
    into.mkdir(parents=True)
    stamp = header(extract, TIMESTAMP)
    cuts = {}
    for region in regions:
        cuts[region] = into / f"{region}.osm.pbf"
        run(
            "osmium", "extract", "-p", polygon(region), "-s", "smart", "-O", "-o", cuts[region],
            f"--output-header={TIMESTAMP}!", extract,
        )
        if header(cuts[region], TIMESTAMP) != stamp:
            fail(f"the {region} cut lost the replication header of {extract.name}")
    return cuts


def country_cuts(
    pin: Pin, regions: list[str], label: str, discarding: bool, poland: Path | None = None
) -> tuple[str, dict[str, list[Path]]]:
    """The pin's one timestamp, and each Region's cut of every extract in the pin."""
    stamp = moment(pin)
    cuts: dict[str, list[Path]] = {region: [] for region in regions}
    for country, sequence in pin.items():
        extract = poland if country == "poland" and poland else extract_at(country, sequence)
        if (found := header(extract, TIMESTAMP)) != stamp:
            fail(f"{extract.name} is at {found}, not the pin's moment {stamp}")
        for region, path in cut(country, extract, regions, WORK / "cuts" / label / country).items():
            cuts[region].append(path)
        log(f"cut {country}; {shutil.disk_usage(WORK).free / 1e9:.1f} GB free")
        if discarding:
            discard(country)
    return stamp, cuts


def merge(stamp: str, cuts: list[Path], target: Path) -> Path:
    """The merged file carries the pin's timestamp alone: the extracts' sequences differ."""
    target.parent.mkdir(parents=True, exist_ok=True)
    run("osmium", "merge", "-O", "-o", target, f"--output-header={TIMESTAMP}={stamp}", *cuts)
    return target


def archive_path(region: str) -> Path:
    return ROOT / f"region_{region}/src/main/assets/{region}.pmtiles"


def install(built: dict[str, Path]) -> None:
    """Only once every Region has built, so a failed run leaves no pack at a different pin from the rest."""
    for region, archive in built.items():
        target = archive_path(region)
        target.parent.mkdir(parents=True, exist_ok=True)
        archive.replace(target)
        log(f"installed {target}")


def tile(source: Path, region: str, archive: Path) -> Path:
    scratch = WORK / "tmp" / archive.stem
    shutil.rmtree(scratch, ignore_errors=True)
    scratch.mkdir(parents=True)
    archive.parent.mkdir(parents=True, exist_ok=True)
    result = run(
        "java", "-Xmx3g", "-cp", planetiler(), PROFILE,
        f"--osm_path={source}", f"--output={archive}", f"--tmpdir={scratch / 'planetiler'}",
        f"--trails={archive.with_suffix('.trails')}",
        f"--bounds={bounds(region)}", "--tile_compression=none", "--maxzoom=14",
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


def check_determinism(stamp: str, cuts: dict[str, list[Path]], sources: dict[str, Path], built: dict[str, Path]) -> None:
    """Merges and tiles each Region a second time from the same cuts and compares bytes."""
    for region, region_cuts in cuts.items():
        again = merge(stamp, region_cuts, WORK / "determinism" / f"{region}.osm.pbf")
        if again.read_bytes() != sources[region].read_bytes():
            fail(f"two merges of {region} differ")
        second = tile(again, region, WORK / "determinism" / f"{region}.pmtiles")
        if second.read_bytes() != built[region].read_bytes():
            fail(f"two builds of {region} differ: {built[region]} {second}")
        print(f"deterministic: two builds of {region} are byte-identical")


def network(sources: dict[str, Path], target: Path) -> Path:
    """The Trail network and the name index of docs/phase-5.md § 2, from every Region's merged cut."""
    if not RELIEF.is_file():
        fail(f"no relief at {RELIEF}: the network's heights come from it; run `python3 scripts/fetch-relief.py`")
    shutil.rmtree(target, ignore_errors=True)
    regions = [part for region, source in sources.items()
               for part in ("--region", region, source, WORK / f"archives/{region}.trails")]
    run(sys.executable, NETWORK, "--relief", RELIEF, "--out", target, *regions)
    return target


def check_network_determinism(sources: dict[str, Path], built: Path) -> None:
    again = network(sources, WORK / "determinism/network")
    for name in BASE_FILES:
        if (again / name).read_bytes() != (built / name).read_bytes():
            fail(f"two builds of {name} differ: {built / name} {again / name}")
        print(f"deterministic: two builds of {name} are byte-identical")


def zstd_size(path: Path) -> int:
    result = run("zstd", "-19", "-q", "-c", path, capture_output=True)
    return len(result.stdout)


def measure_patch(region: str, older: Path, newer: Path) -> None:
    patch = WORK / f"patch/{region}.bsdiff"
    run("bsdiff", older, newer, patch)
    patch_size = patch.stat().st_size
    raw = newer.stat().st_size
    compressed = zstd_size(newer)
    print(f"{region}: patch {patch_size:,} bytes")
    print(f"{region}: newer archive {raw:,} bytes raw: {patch_size / raw:.2%}")
    print(f"{region}: newer archive {compressed:,} bytes zstd -19: {patch_size / compressed:.2%}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--region", action="append", metavar="ID", help="build only this Region; repeatable")
    parser.add_argument("--discard-extracts", action="store_true", help="delete each country's extracts once cut")
    parser.add_argument(
        "--check-determinism", nargs="*", metavar="ID",
        help="merge and tile these Regions, or every one built, a second time and compare bytes",
    )
    older = parser.add_mutually_exclusive_group()
    older.add_argument("--patch-from", type=Path, metavar="PIN", help="measure the patch from an older pin file")
    older.add_argument(
        "--patch-from-extract", type=Path, metavar="PBF",
        help="measure the patch from the pin with this local PBF in place of Poland's extract",
    )
    args = parser.parse_args()

    known = sorted(path.stem for path in REGIONS.glob("*.poly"))
    regions = list(dict.fromkeys(args.region or known))
    if unknown := set(regions) - set(known):
        fail(f"no polygon for {', '.join(sorted(unknown))} in {REGIONS}")
    checked = (args.check_determinism or regions) if args.check_determinism is not None else []
    if unbuilt := set(checked) - set(regions):
        fail(f"--check-determinism names Regions this run does not build: {', '.join(sorted(unbuilt))}")
    patching = args.patch_from or args.patch_from_extract
    if args.discard_extracts and patching:
        fail("--discard-extracts leaves no extract for the older side of a patch")

    require("osmium", "pyosmium-up-to-date", "java")
    if patching:
        require("bsdiff", "zstd")
    pin = read_pin(PIN)

    if patching:
        older_pin = read_pin(args.patch_from) if args.patch_from else pin
        poland = args.patch_from_extract.resolve() if args.patch_from_extract else None
        stamp, cuts = country_cuts(older_pin, regions, "older", False, poland)
        olders = {
            region: tile(merge(stamp, cuts[region], WORK / f"merged/older/{region}.osm.pbf"), region,
                         WORK / f"patch/{region}-older.pmtiles")
            for region in regions
        }
    stamp, cuts = country_cuts(pin, regions, "pin", args.discard_extracts)
    sources = {region: merge(stamp, cuts[region], WORK / f"merged/pin/{region}.osm.pbf") for region in regions}
    built = {region: tile(source, region, WORK / f"archives/{region}.pmtiles") for region, source in sources.items()}
    if checked:
        check_determinism(stamp, {region: cuts[region] for region in checked}, sources, built)
    networked = None
    if set(regions) == set(known) and not patching:
        networked = network(sources, WORK / "network")
        if checked:
            check_network_determinism(sources, networked)
    else:
        log("the network and the name index need every Region; not built")
    install(built)
    if networked:
        BASE_ASSETS.mkdir(parents=True, exist_ok=True)
        for name in BASE_FILES:
            (networked / name).replace(BASE_ASSETS / name)
            log(f"installed {BASE_ASSETS / name}")
    if patching:
        for region in regions:
            measure_patch(region, olders[region], archive_path(region))
    shutil.rmtree(WORK / "cuts", ignore_errors=True)

if __name__ == "__main__":
    main()
