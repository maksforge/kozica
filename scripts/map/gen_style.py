#!/usr/bin/env python3
"""Generates the map's light and dark style templates, per docs/phase-2.md § 3.

    python3 scripts/map/gen_style.py && python3 scripts/map/sprites.py

The five Trail colours are the only literals. Every other colour is a tone of a palette that
scripts/gen_scheme.py derives from its seeds, so a seed change reaches the map when both re-run.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from gen_scheme import N, NV, P, S, T  # noqa: E402

ASSETS = ROOT / "app/src/main/assets/map"
FONT = ["Noto Sans Regular"]

TRAIL = {
    "red": "#d62828",
    "blue": "#1d4ed8",
    "green": "#2e8b3a",
    "yellow": "#e6b800",
    "black": "#1a1a1a",
}
SHAPES = ("bar", "dot", "cross", "triangle", "corner")
POIS = ("hut", "shelter", "peak", "pass", "spring", "viewpoint", "parking", "stop", "station")


def tone(palette, value: int) -> str:
    return "#" + palette.hexes(value).lower()


def palette(dark: bool) -> dict[str, str]:
    def pick(light_tone: int, dark_tone: int) -> int:
        return dark_tone if dark else light_tone

    return {
        "background": tone(N, pick(96, 8)),
        "meadow": tone(N, pick(93, 11)),
        "wood": tone(S, pick(87, 15)),
        "scrub": tone(S, pick(83, 18)),
        "rock": tone(NV, pick(89, 21)),
        "glacier": tone(N, pick(99, 26)),
        "residential": tone(NV, pick(91, 14)),
        "water": tone(T, pick(84, 22)),
        "waterway": tone(T, pick(66, 45)),
        "park": tone(S, pick(50, 60)),
        "country": tone(NV, pick(30, 75)),
        "road": tone(N, pick(100, 30)),
        "road_casing": tone(NV, pick(70, 18)),
        "track": tone(NV, pick(50, 55)),
        "path": tone(NV, pick(40, 70)),
        "trail_other": tone(NV, 60),
        "yellow_casing": tone(NV, 30),
        "black_casing": tone(N, 90),
        "text": tone(N, pick(10, 90)),
        "text_halo": tone(N, pick(98, 10)),
        "hut_text": tone(P, pick(35, 80)),
        "badge": tone(N, pick(99, 20)),
        "badge_edge": tone(NV, pick(55, 60)),
        "ink": tone(NV, pick(25, 90)),
        "hut_ink": tone(P, pick(40, 80)),
        "spring_ink": tone(T, pick(45, 75)),
        "symbol_white": tone(N, 100),
        "symbol_edge": tone(NV, 50),
    }


def by_zoom(*stops: tuple[float, object]) -> list:
    return ["interpolate", ["linear"], ["zoom"], *(value for stop in stops for value in stop)]


TRAIL_WIDTHS = ((10, 2.0), (13, 3.0), (15, 4.5), (17, 7.0))
SYMBOL_STEP = 18
SLOT = ["-", ["coalesce", ["get", "index"], 0], ["/", ["-", ["coalesce", ["get", "count"], 1], 1], 2]]


def trail_width(extra: float = 0) -> list:
    return by_zoom(*((zoom, width + extra) for zoom, width in TRAIL_WIDTHS))


def trail_offset() -> list:
    return by_zoom(*((zoom, ["*", SLOT, width]) for zoom, width in TRAIL_WIDTHS))


def symbol_offset() -> list:
    # An expression cannot build an array from data, so each slot gets its own literal offset;
    # twice the slot is an integer, which match needs. The step along the line keeps two Trails'
    # symbols beside each other rather than stacked.
    twice_slot = ["-", ["*", 2, ["coalesce", ["get", "index"], 0]], ["-", ["coalesce", ["get", "count"], 1], 1]]

    def at(width: float) -> list:
        slots = (value for twice in range(-8, 9)
                 for value in (twice, ["literal", [twice / 2 * SYMBOL_STEP, twice / 2 * width]]))
        return ["match", twice_slot, *slots, ["literal", [0, 0]]]

    return by_zoom(*((zoom, at(width)) for zoom, width in TRAIL_WIDTHS if zoom >= 13))


def layer(id: str, type: str, source_layer: str | None = None, **parts) -> dict:
    result = {"id": id, "type": type}
    if source_layer is not None:
        result |= {"source": "region", "source-layer": source_layer}
    return result | {key.replace("_", "-"): value for key, value in parts.items()}


def style(dark: bool) -> dict:
    c = palette(dark)
    casing = "black" if dark else "yellow"
    trail_colour = ["match", ["get", "colour"], *(v for pair in TRAIL.items() for v in pair), c["trail_other"]]

    def text_layout(size, **more):
        return {"text-font": FONT, "text-size": size, **more}

    def text_paint(colour=None):
        return {"text-color": colour or c["text"], "text-halo-color": c["text_halo"], "text-halo-width": 1.2}

    layers = [
        layer("background", "background", paint={"background-color": c["background"]}),
        layer(
            "land", "fill", "land",
            paint={"fill-color": ["match", ["get", "kind"],
                                  ["wood", "forest"], c["wood"],
                                  "scrub", c["scrub"],
                                  ["grassland", "meadow", "heath"], c["meadow"],
                                  ["scree", "bare_rock"], c["rock"],
                                  "glacier", c["glacier"],
                                  "residential", c["residential"],
                                  c["background"]]},
        ),
        layer("water-area", "fill", "water", filter=["==", ["geometry-type"], "Polygon"],
              paint={"fill-color": c["water"]}),
        layer(
            "water-line", "line", "water", filter=["==", ["geometry-type"], "LineString"],
            paint={"line-color": c["waterway"],
                   "line-width": by_zoom(*((zoom, ["match", ["get", "kind"], "river", river, stream])
                                           for zoom, river, stream in ((8, 1.0, 0.3), (12, 2.0, 0.6), (16, 4.0, 1.6))))},
        ),
        layer("boundary-park", "line", "boundaries", filter=["==", ["get", "kind"], "national_park"],
              paint={"line-color": c["park"], "line-width": by_zoom((6, 1.0), (14, 2.5)),
                     "line-dasharray": ["literal", [4, 2]], "line-opacity": 0.8}),
        layer("boundary-country", "line", "boundaries", filter=["==", ["get", "kind"], "country"],
              paint={"line-color": c["country"], "line-width": by_zoom((6, 1.2), (14, 3.0)),
                     "line-dasharray": ["literal", [6, 2, 1, 2]]}),
        layer("road-casing", "line", "roads", filter=["!=", ["get", "kind"], "track"],
              layout={"line-join": "round", "line-cap": "round"},
              paint={"line-color": c["road_casing"], "line-width": by_zoom((8, 1.2), (12, 3.0), (16, 9.0))}),
        layer("roads", "line", "roads", filter=["!=", ["get", "kind"], "track"],
              layout={"line-join": "round", "line-cap": "round"},
              paint={"line-color": c["road"], "line-width": by_zoom((8, 0.6), (12, 1.8), (16, 7.0))}),
        layer("tracks", "line", "roads", filter=["==", ["get", "kind"], "track"],
              paint={"line-color": c["track"], "line-width": by_zoom((12, 0.8), (16, 2.0)),
                     "line-dasharray": ["literal", [5, 2]]}),
        layer("paths", "line", "paths",
              paint={"line-color": c["path"], "line-width": by_zoom((12, 0.6), (16, 1.4)),
                     "line-dasharray": ["literal", [2, 2]]}),
        layer("trail-casings", "line", "trails", filter=["==", ["get", "colour"], casing],
              layout={"line-join": "round"},
              paint={"line-color": c[f"{casing}_casing"], "line-width": trail_width(2.0),
                     "line-offset": trail_offset()}),
        layer("trails", "line", "trails",
              layout={"line-join": "round"},
              paint={"line-color": trail_colour, "line-width": trail_width(), "line-offset": trail_offset()}),
        layer(
            "trail-symbols", "symbol", "trails", minzoom=14,
            layout={
                "symbol-placement": "line",
                "symbol-spacing": 160,
                "icon-image": ["coalesce", ["image", ["coalesce", ["get", "symbol"], ""]],
                               ["image", ["concat", ["get", "colour"], "_bar"]], ["image", "other_bar"]],
                "icon-rotation-alignment": "map",
                "icon-keep-upright": False,
                "icon-allow-overlap": True,
                "icon-ignore-placement": True,
                "icon-offset": symbol_offset(),
            },
        ),
        layer(
            "pois", "symbol", "pois",
            layout=text_layout(
                12,
                **{
                    "icon-image": ["concat", "poi_", ["get", "kind"]],
                    "symbol-sort-key": ["match", ["get", "kind"], "hut", 0, "shelter", 1, "peak", 2, "pass", 3,
                                        "station", 4, 5],
                    "text-field": ["case", ["has", "ele"],
                                   ["format", ["coalesce", ["get", "name"], ""], {}, "\n", {},
                                    ["concat", ["get", "ele"], " m"], {"font-scale": 0.85}],
                                   ["coalesce", ["get", "name"], ""]],
                    "text-anchor": "top",
                    "text-offset": ["literal", [0, 0.9]],
                    "text-optional": True,
                    "text-max-width": 8,
                },
            ),
            paint=text_paint(["match", ["get", "kind"], ["hut", "shelter"], c["hut_text"], c["text"]]),
        ),
        layer(
            "places", "symbol", "places",
            layout=text_layout(["match", ["get", "kind"], "town", 15, "village", 13, 11],
                               **{"text-field": ["get", "name"], "text-max-width": 8}),
            paint=text_paint(),
        ),
    ]
    return {
        "version": 8,
        "glyphs": "asset://map/glyphs/{fontstack}/{range}.pbf",
        "sprite": f"asset://map/sprite-{'dark' if dark else 'light'}",
        "layers": layers,
    }


def main() -> None:
    for name, dark in (("light", False), ("dark", True)):
        target = ASSETS / f"style-{name}.json"
        target.write_text(json.dumps(style(dark), indent=1, ensure_ascii=False) + "\n")
        print(target.relative_to(ROOT))


if __name__ == "__main__":
    main()
