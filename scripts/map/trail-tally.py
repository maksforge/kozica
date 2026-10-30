#!/usr/bin/env python3
"""Phase 6c's gate (docs/phase-6c.md § 1): what the Regions' Trail relations carry to title them.

    python3 scripts/map/trail-tally.py

Reads every Region's merged cut that build-map-data.py leaves in map-data/merged/pin/. A Trail is a
`route=hiking` relation, as KozicaProfile.java decides, with a way in a cut; each is counted once
across the union, since the cuts overlap. A Long-distance Trail is a relation with a Trail among its
members, which are then its Sections. The rules are § 2's title rule: a name, then `from` and `to`,
then the ends a mapper wrote as "A - B" in one of ENDS_KEYS, the Slovak and Czech conventions.
Needs pyosmium 4.2+.
"""

import argparse
import re
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
REGIONS = ROOT / "scripts/regions"
CUTS = ROOT / "map-data/merged/pin"
ENDS_KEYS = ("description", "osmc:name", "note:cs", "note")
RULES = ("name", "from – to", *ENDS_KEYS, "neither")
COLOUR_CODE = re.compile(r"^\[[^\]]{1,3}\]\s*")
DASH = re.compile(r"\s+[-–—]\s+")
LONGEST_END = 40


@dataclass
class Relation:
    tags: dict[str, str]
    ways: list[int]
    relations: list[int]
    regions: set[str] = field(default_factory=set)


def name(tags: dict[str, str]) -> str | None:
    """KČT's names lead with the colour's letter, `[Ž] A - B`, which the sheet shows already."""
    found = tags.get("name:pl") or tags.get("name")
    return COLOUR_CODE.sub("", found, count=1).strip() or None if found else None


def written_ends(value: str) -> tuple[str, str] | None:
    """Place names between spaced dashes, the first and last the ends. A mapper's prose in a note
    fails the length, so it is not read as ends."""
    places = [place.strip() for place in DASH.split(COLOUR_CODE.sub("", value.strip(), count=1))]
    if len(places) >= 2 and all(0 < len(place) <= LONGEST_END and ";" not in place for place in places):
        return places[0], places[-1]
    return None


def rule(tags: dict[str, str]) -> str:
    if name(tags):
        return "name"
    if tags.get("from") and tags.get("to"):
        return "from – to"
    return next((key for key in ENDS_KEYS if written_ends(tags.get(key, ""))), "neither")


def sample_title(rid: int, tags: dict[str, str]) -> str:
    match rule(tags):
        case "name":
            return name(tags)
        case "from – to":
            return f"{tags['from']} – {tags['to']}"
        case "neither":
            pass
        case key:
            return " – ".join(written_ends(tags[key]))
    marks = " ".join(f"{key}={tags[key]}" for key in ("ref", "osmc:symbol", "colour") if key in tags)
    return f"r{rid} {marks}".rstrip()


def read(regions: list[str], cuts: Path) -> dict[int, Relation]:
    import osmium

    relations: dict[int, Relation] = {}
    for region in regions:
        present: set[int] = set()
        candidates: dict[int, Relation] = {}
        for obj in osmium.FileProcessor(str(cuts / f"{region}.osm.pbf"), osmium.osm.WAY | osmium.osm.RELATION):
            if obj.is_way():
                present.add(obj.id)
            elif "route" in obj.tags or obj.tags.get("type") == "superroute":
                candidates[obj.id] = Relation(
                    dict(obj.tags),
                    [m.ref for m in obj.members if m.type == "w"],
                    [m.ref for m in obj.members if m.type == "r"],
                )
        for rid, relation in candidates.items():
            if relation.ways and not any(way in present for way in relation.ways):
                continue
            relations.setdefault(rid, relation).regions.add(region)
    return relations


def trails_of(relations: dict[int, Relation]) -> dict[int, Relation]:
    return {rid: r for rid, r in relations.items()
            if r.tags.get("type") == "route" and r.tags.get("route") == "hiking" and r.regions and r.ways}


def long_distance_of(relations: dict[int, Relation], trails: dict[int, Relation]) -> dict[int, Relation]:
    return {rid: r for rid, r in relations.items() if any(member in trails for member in r.relations)}


def share(count: int, total: int) -> str:
    return f"{count:6,} {100 * count / total:5.1f} %" if total else f"{count:6,}     – "


def print_counts(label: str, trails: dict[int, Relation]) -> None:
    counts = Counter(rule(r.tags) for r in trails.values())
    titled = len(trails) - counts["neither"]
    cells = "".join(f"{counts[key]:>12,}" for key in RULES)
    print(f"{label:<20}{len(trails):>7,}{cells}   {share(titled, len(trails))}")


def evenly(items: list, count: int) -> list:
    if len(items) <= count:
        return items
    return [items[i * len(items) // count] for i in range(count)]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cuts", type=Path, default=CUTS, help="the directory holding <region>.osm.pbf")
    parser.add_argument("--samples", type=int, default=20)
    args = parser.parse_args()

    regions = sorted(path.stem for path in REGIONS.glob("*.poly"))
    relations = read(regions, args.cuts)
    trails = trails_of(relations)
    long_distance = long_distance_of(relations, trails)

    print("Trails by what titles them\n")
    print(f"{'':<20}{'Trails':>7}{''.join(f'{key:>12}' for key in RULES)}   titled by tags")
    print_counts("union", trails)
    for region in regions:
        print_counts(region, {rid: r for rid, r in trails.items() if region in r.regions})

    for key in RULES:
        found = sorted(rid for rid, r in trails.items() if rule(r.tags) == key)
        print(f"\n{key}: {len(found):,}, {min(args.samples, len(found))} evenly by id")
        for rid in evenly(found, args.samples):
            print(f"  {sample_title(rid, trails[rid].tags)}")

    sections = {member for r in long_distance.values() for member in r.relations if member in trails}
    named = sorted(rid for rid, r in long_distance.items() if name(r.tags))
    kinds = Counter(r.tags.get("type", "?") for r in long_distance.values())
    print(f"\nLong-distance Trails with Sections in the Regions: {len(long_distance):,}"
          f" ({', '.join(f'type={k} {n}' for k, n in sorted(kinds.items()))}),"
          f" {len(named):,} named; {len(sections):,} Sections")
    for rid in named:
        r = long_distance[rid]
        count = sum(member in trails for member in r.relations)
        print(f"  r{rid} {name(r.tags)}: {count} of {len(r.relations)} member relations are Sections here")
    for rid in sorted(set(long_distance) - set(named)):
        print(f"  {sample_title(rid, long_distance[rid].tags)} (no name)")

    untitled = {rid for rid, r in trails.items() if rule(r.tags) == "neither"}
    under_named = {member for rid in named for member in long_distance[rid].relations} & untitled
    print(f"\nOf the {len(untitled):,} Trails with neither, {len(under_named):,} are Sections of a named"
          " Long-distance Trail, whose name the sheet shows above their title")


if __name__ == "__main__":
    main()
