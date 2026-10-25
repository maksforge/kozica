#!/usr/bin/env python3
"""Reuses an earlier release's Region archives when nothing that builds them has changed.

    python3 scripts/map-archives.py reuse --tag v0.8.0   # exit 0: installed; otherwise build them
    python3 scripts/map-archives.py record               # writes map-data/MAP-DATA-INPUTS.txt

The record names every input of build-map-data.py by its sha256, the tools by their versions, and
every installed archive by its sha256. It is attached to each version's release beside the archives,
so the next release whose inputs read the same downloads those archives instead of building them
(ADR-0007's 2026-10-25 amendment).
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RECORD = ROOT / "map-data" / "MAP-DATA-INPUTS.txt"
DOWNLOADS = ROOT / "map-data" / "reused"
INPUTS = [
    "scripts/build-map-data.py",
    "scripts/osm-data.pin",
    "scripts/map/KozicaProfile.java",
    *sorted(str(path.relative_to(ROOT)) for path in (ROOT / "scripts/regions").glob("*.poly")),
]


def log(message: str) -> None:
    print(f"map-archives: {message}", file=sys.stderr, flush=True)


def sha256(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as file:
        while chunk := file.read(1 << 20):
            hasher.update(chunk)
    return hasher.hexdigest()


def first_line(*command: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    return (result.stdout or result.stderr).splitlines()[0].strip()


def pyosmium() -> str:
    try:
        return importlib.metadata.version("osmium")
    except importlib.metadata.PackageNotFoundError:
        return "absent"


def inputs() -> list[str]:
    return [
        *(f"input {sha256(ROOT / path)} {path}" for path in INPUTS),
        f"tool osmium-tool {first_line('osmium', '--version')}",
        f"tool pyosmium {pyosmium()}",
        f"tool java {first_line('java', '-version')}",
    ]


def regions() -> list[str]:
    return sorted(path.stem for path in (ROOT / "scripts/regions").glob("*.poly"))


def archive(region: str) -> Path:
    return ROOT / f"region_{region}/src/main/assets/{region}.pmtiles"


def gh(*args: str) -> str:
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=True).stdout


def latest_record(tag: str) -> tuple[str, list[str]] | None:
    """The newest other version's release that carries a record, and that record's lines."""
    releases = json.loads(gh("release", "list", "--limit", "20", "--json", "tagName,isDraft"))
    for release in releases:
        other = release["tagName"]
        if other == tag or release["isDraft"] or not other.startswith("v"):
            continue
        assets = json.loads(gh("release", "view", other, "--json", "assets"))["assets"]
        if any(asset["name"] == RECORD.name for asset in assets):
            DOWNLOADS.mkdir(parents=True, exist_ok=True)
            gh("release", "download", other, "--pattern", RECORD.name, "--dir", str(DOWNLOADS), "--clobber")
            return other, (DOWNLOADS / RECORD.name).read_text().splitlines()
    return None


def reuse(tag: str) -> bool:
    found = latest_record(tag)
    if not found:
        log("no earlier release carries a record; building")
        return False
    other, lines = found
    current = inputs()
    recorded = [line for line in lines if not line.startswith("archive ")]
    if recorded != current:
        changed = sorted(set(current) ^ set(recorded))
        log(f"inputs differ from {other}'s; building. Lines in only one of the two:\n  " + "\n  ".join(changed))
        return False
    expected = {name: digest for _, digest, name in (line.split() for line in lines if line.startswith("archive "))}
    if sorted(expected) != [f"{region}.pmtiles" for region in regions()]:
        log(f"{other}'s record names other archives than the Regions; building")
        return False
    gh("release", "download", other, "--pattern", "*.pmtiles", "--dir", str(DOWNLOADS), "--clobber")
    for name, digest in expected.items():
        if sha256(DOWNLOADS / name) != digest:
            log(f"{other}'s {name} does not match its record; building")
            return False
    for region in regions():
        target = archive(region)
        target.parent.mkdir(parents=True, exist_ok=True)
        (DOWNLOADS / f"{region}.pmtiles").replace(target)
    log(f"inputs match {other}'s; installed its {len(expected)} archives")
    return True


def record() -> None:
    missing = [str(archive(region)) for region in regions() if not archive(region).is_file()]
    if missing:
        sys.exit(f"map-archives: no archive at {', '.join(missing)}")
    RECORD.parent.mkdir(parents=True, exist_ok=True)
    archives = [f"archive {sha256(archive(region))} {region}.pmtiles" for region in regions()]
    RECORD.write_text("\n".join([*inputs(), *archives]) + "\n")
    log(f"wrote {RECORD}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("reuse").add_argument("--tag", required=True, help="this run's own release tag")
    commands.add_parser("record")
    args = parser.parse_args()
    if args.command == "record":
        record()
        return
    try:
        reused = reuse(args.tag)
    except subprocess.CalledProcessError as error:
        log(f"{' '.join(error.cmd)} failed; building\n{error.stderr}")
        reused = False
    sys.exit(0 if reused else 1)


if __name__ == "__main__":
    main()
