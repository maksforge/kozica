#!/usr/bin/env python3
"""Reuses a release's Region archives, Trail network and name index when nothing that builds them changed.

    python3 scripts/map-archives.py reuse    # exit 0: installed; otherwise build them
    python3 scripts/map-archives.py record   # writes map-data/MAP-DATA-INPUTS.txt

The record names every input of build-map-data.py by its sha256, the tools by their versions, and
every installed archive and base file by its sha256. It is attached to each version's release beside the archives,
so a later run whose inputs read the same downloads those archives instead of building them
(ADR-0007's 2026-10-25 amendment).
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RECORD = ROOT / "map-data" / "MAP-DATA-INPUTS.txt"
DOWNLOADS = ROOT / "map-data" / "reused"
BASE_ASSETS = ROOT / "map-data" / "base-assets"
BASE_FILES = ("trails.network", "trails.edges", "places.sqlite")
INPUTS = [
    "scripts/build-map-data.py",
    "scripts/build-network.py",
    "scripts/osm-data.pin",
    "scripts/relief.pin",
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


def version(package: str) -> str:
    try:
        return importlib.metadata.version(package)
    except importlib.metadata.PackageNotFoundError:
        return "absent"


def webp() -> str:
    try:
        from PIL import features
    except ImportError:
        return "absent"
    return features.version("webp") or "absent"


def inputs() -> list[str]:
    return [
        *(f"input {sha256(ROOT / path)} {path}" for path in INPUTS),
        f"tool osmium-tool {first_line('osmium', '--version')}",
        f"tool pyosmium {version('osmium')}",
        f"tool java {first_line('java', '-version')}",
        f"tool sqlite {sqlite3.sqlite_version}",
        f"tool pillow {version('pillow')} libwebp {webp()}",
    ]


def regions() -> list[str]:
    return sorted(path.stem for path in (ROOT / "scripts/regions").glob("*.poly"))


def outputs() -> dict[str, Path]:
    """Each file the build installs, by the name it is published under."""
    archives = {f"{region}.pmtiles": ROOT / f"region_{region}/src/main/assets/{region}.pmtiles" for region in regions()}
    return archives | {name: BASE_ASSETS / name for name in BASE_FILES}


def gh(*args: str) -> str:
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=True).stdout


def latest_record() -> tuple[str, list[str]] | None:
    """The newest version's release that carries a record, this run's own included, and its lines."""
    releases = json.loads(gh("release", "list", "--limit", "20", "--json", "tagName,isDraft"))
    for release in releases:
        tag = release["tagName"]
        if release["isDraft"] or not tag.startswith("v"):
            continue
        assets = json.loads(gh("release", "view", tag, "--json", "assets"))["assets"]
        if any(asset["name"] == RECORD.name for asset in assets):
            DOWNLOADS.mkdir(parents=True, exist_ok=True)
            gh("release", "download", tag, "--pattern", RECORD.name, "--dir", str(DOWNLOADS), "--clobber")
            return tag, (DOWNLOADS / RECORD.name).read_text().splitlines()
    return None


def reuse() -> bool:
    found = latest_record()
    if not found:
        log("no release carries a record; building")
        return False
    source, lines = found
    current = inputs()
    recorded = [line for line in lines if not line.startswith("archive ")]
    if recorded != current:
        changed = sorted(set(current) ^ set(recorded))
        log(f"inputs differ from {source}'s; building. Lines in only one of the two:\n  " + "\n  ".join(changed))
        return False
    expected = {name: digest for _, digest, name in (line.split() for line in lines if line.startswith("archive "))}
    targets = outputs()
    if sorted(expected) != sorted(targets):
        log(f"{source}'s record names other files than this build installs; building")
        return False
    for name in expected:
        gh("release", "download", source, "--pattern", name, "--dir", str(DOWNLOADS), "--clobber")
    for name, digest in expected.items():
        if sha256(DOWNLOADS / name) != digest:
            log(f"{source}'s {name} does not match its record; building")
            return False
    for name, target in targets.items():
        target.parent.mkdir(parents=True, exist_ok=True)
        (DOWNLOADS / name).replace(target)
    log(f"inputs match {source}'s; installed its {len(expected)} files")
    return True


def record() -> None:
    missing = [str(path) for path in outputs().values() if not path.is_file()]
    if missing:
        sys.exit(f"map-archives: nothing at {', '.join(missing)}")
    RECORD.parent.mkdir(parents=True, exist_ok=True)
    archives = [f"archive {sha256(path)} {name}" for name, path in sorted(outputs().items())]
    RECORD.write_text("\n".join([*inputs(), *archives]) + "\n")
    log(f"wrote {RECORD}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("reuse")
    commands.add_parser("record")
    args = parser.parse_args()
    if args.command == "record":
        record()
        return
    try:
        reused = reuse()
    except subprocess.CalledProcessError as error:
        log(f"{' '.join(error.cmd)} failed; building\n{error.stderr}")
        reused = False
    sys.exit(0 if reused else 1)


if __name__ == "__main__":
    main()
