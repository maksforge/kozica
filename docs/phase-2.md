# Phase 2 — The map draws the mountains as the signposts mark them, and shows where you are

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds on [ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md) and owns one
of the two decisions that ADR leaves to "the map phase": the filter. It works inside
[ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md). `CONTEXT.md` is owed nothing:
**Trail**, **Path**, **Hut**, **Shelter** and **Region** already name everything the map draws.

**What closing this phase means.** Today the release build opens on a placeholder, and only a debug
screen draws the Tatry, as plain lines. After this phase the app opens on the map, in the release
build too. It draws every Trail in its signpost colour and symbol, with two Trails on one way drawn
side by side, Paths apart from them, and Huts, Shelters, peaks, passes, springs, viewpoints, the way
in and out, the land, national-park boundaries and the state border, in light and dark. One tap
shows where the user is, with accuracy and heading. The difference a device can be asked about: a
debug build with the Tatry on the phone shows a yellow and a black Trail sharing a way, both drawn,
and the position dot over them, with no network permission in the merged manifest.

**`PLAN.md`'s paragraph is split, and amended with this file.** It bundled the map with every Region
and the picker that downloads them. The two halves are blocked by different things: the map can be
built and read against the Tatry archive already on the developer's machine, but a picker that
reaches users needs the asset-delivery library in the release build, which is phase 1's checkpoint E
and waits on Play work. By `PLAN.md` rule 2 they are two phases. This one draws; **phase 2b** builds
every Region, owns the Region list, and brings the picker and "Download all".

---

## 0. What this phase inherits

- **A gate nobody has read.** Phase 1's R1, whether MapLibre draws with every network permission
  stripped, was deferred on 2026-10-19 with R8, R9 and R10's screen. `DOD.md` makes them due before
  this phase builds on MapLibre, and phase 1's § 1 holds their verdicts. This phase's A is those
  readings.
- **A thin tile schema.** `scripts/map/KozicaProfile.java` emits water, ways with a sorted list of
  Trail colours, and named peaks: what phase 1's gate and readings needed and nothing else. Phase 1
  left the full schema to this phase, under three rules that stand: no compression inside the file,
  deterministic output, one archive per Region.
- **Trail colours read the way the profile reads them.** A Trail's colour is the first field of its
  relation's `osmc:symbol`, and `colour=` only when there is no symbol. 140 ways in the Tatry
  archive carry two or more colours (phase 1, R10).
- **A debug map, not a map.** `DebugMapActivity`, the gate style and Noto Sans glyphs live under
  `app/src/debug/`. `RegionFiles` there reads the Tatry pack's state, falling back to a hand-pushed
  archive in `files/regions/`.
- **The palette is waiting for a map.** Phase 0 § 3 fixed four seeds on the condition that nothing
  the app paints over the map reads as a Trail colour, and left the reading to this phase.
- **Rule 4's location row is this phase's.** Precise location, asked on the first tap on "show me
  where I am", never at launch; fine and coarse together; and from API 31 a third answer,
  approximate only.
- **MapLibre merges the fine and coarse location pair** with its three network permissions
  (ADR-0008). The network three stay stripped; the location pair becomes the ask.

## What is in, and what is deliberately not

**In:** the map screen in `main/`, replacing Home's placeholder, drawn by MapLibre with every
network permission stripped from the release build. The full tile schema, which is the filter, and
the style that draws it, in light and dark. The position: the ask, the dot, accuracy, heading, and
what the user sees for each answer. The OSM credit and data date on the map. ODbL and the font's OFL
on the licences screen. The palette reading.

**Not in, and each with a reason rather than a phase:**

- **Any Region in the release build.** The release map draws whatever Region is on the phone, and in
  this phase nothing can put one there: the asset-delivery library stays debug-only until phase 2b
  or E. The release map therefore shows its empty state, "no Regions downloaded", which ADR-0007
  requires anyway. No OSM-derived data is conveyed, so the ODbL offer is not yet owed.
- **The Region list, the polygons beyond the Tatry, and the picker.** Phase 2b's. One Region is
  enough to draw and read every layer; building nine would only multiply the readings.
- **Search, tapping a feature for its details, and route planning.** Phase 5 brings search and
  routes, and a details sheet needs the copy and layout decisions that come with them.
- **Contours and hillshading.** Phase 4: a DEM pipeline with its own licence.
- **Remembering the camera across launches.** It is a stored key, and the door freezes stored keys
  (`PLAN.md` rule 1). The camera survives process death through saved state, and a fresh launch
  opens on the downloaded Regions' bounds.
- **Following the user as they move.** One tap centres the map on the position once; the map does
  not then chase the dot. A camera that moves itself under a finger is a fault report in waiting,
  and recording a hike is phase 6.

**The tempting one** is drawing Trails from a ready-made outdoor style, such as OpenTopoMap's look
or an OpenMapTiles schema with a hiking overlay. It looks like a week saved. But those schemas carry
hiking routes as separate relation geometry or not at all, and none of them offsets two Trails on
one way, which is the thing a Polish trail map has to get right: a junction where a red and a blue
Trail share a way for a kilometre is where people take the wrong one. The schema stays ours, small,
and shaped around that.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — phase 1's deferred R1, R8, R9 and R10's screen | readings only | — |
| **B** | **The map in `main/`** — MapLibre in the release build with its network permissions stripped, Home becomes the map, its empty state, credit and licences | `feat:` | A |
| **C** | **The schema and the style** — the filter, Trails side by side with their symbols, everything else `PLAN.md` lists, light and dark | `feat:` | B |
| **D** | **You are here** — the ask, its three answers and location off, the dot, accuracy, heading, a stale fix | `feat:` | B |
| **E** | **The palette** — phase 0's reading over the finished style | readings, or `fix:` if a seed moves | C, D |

**A goes first because B is a bet otherwise.** Phase 1 built B–D on the assumption that MapLibre
draws offline. This phase would put that assumption into the release build. If R1 takes phase 1's
verdict 2, the renderer changes, and B–D here are rewritten for Mapsforge before any of them is
built.

**B is small on purpose.** It moves the debug map into `main/` and makes it the app's first screen,
with the style phase 1 already has. Everything the release artifact gains in this phase — MapLibre's
native code, the stripped permissions, the licences — lands in that one commit, so its
`aab-permissions.py` run reads one change.

**C and D are independent.** C changes the profile, the archive and the style; D changes the screen
and the manifest. Either can merge first. E reads both, so it comes last.

---

## 1. The gate

Phase 1 § 1, unchanged: the question, the apparatus and the four verdicts are written there and are
not re-argued here. R8 and R9 are phase 1's too, with their verdicts in phase 1 § 3. The readings
are recorded in phase 1's table, where their rows are; this phase's R1 points to them.

If R1 takes verdict 1, this phase goes ahead as written. If it takes verdict 2, this file stops at A
and is rewritten for Mapsforge/VTM before B.

## 2. The map in `main/`

- **MapLibre becomes `implementation`**, still `android-sdk-opengl` (phase 1 § 1: the default
  `android-sdk` is Vulkan, and its `uses-feature` would be a Play device filter). The release
  artifact gains about 13 MB of native code per ABI, which phase 1 named as a known gap for this
  phase to decide. It is decided: the map is the app.
- **The main manifest strips the three network permissions** with `tools:node="remove"`, moved from
  the debug manifest. `aab-permissions.py` already forbids them, so its run on the release bundle is
  the proof. The location pair is also stripped in B and returns in D, with its ask.
- **`MapLibre.setConnected(false)` runs in `MainApplication.onCreate`**, before any `MapView` can
  exist (ADR-0008's consequence).
- **`ui/map/MapScreen.kt`** replaces Home's placeholder. It is an `AndroidView` around `MapView`, as
  in the gate: maplibre-compose is still out for the Kotlin pin (phase 1, *the tempting one*).
  `MapViewModel` holds one immutable UI state: the ready Regions, the camera and the position.
- **Which Regions are on the phone is a seam with two halves.** `data/regions/Regions.kt` in `main/`
  defines a `Flow` of ready Regions, each a path and an OSM date. The debug half is `RegionFiles`,
  pack or hand-pushed. The release half, in `src/release/`, emits an empty list until phase 2b gives
  it the library. Same shape as `DebugSettings.kt`'s seam.
- **The style moves to `src/main/assets/map/`**, with its glyphs. Sources point at
  `pmtiles://file://<path>` for each ready Region, one source per Region, added when the Flow emits.
- **The empty state** is the map's own background with one line and no button, because in this phase
  there is nothing to press: "No Regions downloaded yet." Phase 2b adds the button.
- **The credit and the date.** "© OpenStreetMap contributors" sits on the map, linked to the
  copyright page through `ui/common/Intents.kt`, and beside it "OSM data: <date>" for the newest
  ready Region. MapLibre's own attribution control and logo are turned off, because the credit we
  owe is OSM's and the link would open the browser to a page that names neither.
- **Licences.** `app.cash.licensee` picks up MapLibre's BSD-2-Clause by itself. ODbL and Noto Sans's
  OFL are not dependencies, so they become two hand-written entries beside the generated list, and
  their texts join `src/main/assets/licences/`.
- **The debug map goes.** `DebugMapActivity` and its Settings row are removed; the debug section
  keeps "Download Tatry", "Remove Tatry" and the pack state.

Copy: "No Regions downloaded yet.", "© OpenStreetMap contributors", "OSM data: %1$s".

## 3. The schema and the style

**The filter is the profile.** ADR-0007 has `osmium tags-filter` drop what nothing in the app reads.
But planetiler emits only what the profile names, so a second list in a filter step would only have
to be kept in step with the first. The cut goes straight to planetiler, and the layer table below is
the filter. ADR-0007's amendment records that.

| Layer | From OSM | Min zoom | Attributes |
| --- | --- | --- | --- |
| `land` | `natural=wood\|scrub\|grassland\|heath\|scree\|bare_rock\|glacier`, `landuse=forest\|meadow\|residential` | 8 | `kind` |
| `water` | `natural=water`, `waterway=river\|stream` | 8 (rivers), 12 (streams) | `kind` |
| `boundaries` | `boundary=national_park`, `boundary=protected_area` with `protect_class=2`, `admin_level=2` | 6 | `kind` |
| `roads` | `highway=` motorway to `unclassified`, `residential`, `service`, `track` | 8–13 by class | `kind` |
| `paths` | `highway=path\|footway\|steps\|bridleway`, `sac_scale` | 12 | `kind`, `sac_scale` |
| `trails` | each `route=hiking` relation's member ways | 10 | `colour`, `symbol`, `index`, `count`, `ref` |
| `pois` | Huts `tourism=alpine_hut`; Shelters `tourism=wilderness_hut`, `amenity=shelter`; `natural=peak\|saddle\|spring`, `mountain_pass=yes`, `tourism=viewpoint`; the way in and out: `amenity=parking`, `highway=bus_stop`, `railway=station\|halt` | 11–14 by kind | `kind`, `name`, `ele` |
| `places` | `place=town\|village\|hamlet` | 8–12 by kind | `kind`, `name` |

- **A Trail is one feature per Trail per way, not a list on the way.** R10's sorted list cannot be
  drawn: a style cannot loop over it. So the profile emits a way that `count` Trails share `count`
  times, each with its own `colour`, `symbol` and `index` in that sorted order. The style offsets
  each by `(index - (count - 1) / 2) × width`, so two Trails sit side by side and three keep the
  middle one on the way. Sorting by colour, then by relation id, keeps the order stable between
  builds and the same on both sides of a junction.
- **The symbol is the `osmc:symbol` foreground**, such as `red_bar`, `red_dot`, `red_cross`,
  `red_triangle`, `red_corner`. It is drawn as an icon repeated along the line from zoom 14. A
  foreground the sprite sheet does not hold falls back to the bar in the Trail's colour, never to
  nothing. The sprites are generated by `scripts/map/sprites.py` from the shapes × the five Trail
  colours, and committed with the style, so the build needs no network.
- **Paths are drawn so they can never pass for a Trail** (`CONTEXT.md`): thin, dashed, in a neutral,
  never in a Trail colour, and drawn under Trails.
- **Trail colours are the signposts' red, blue, green, yellow and black, the same in light and
  dark.** This is the exception to "colours come from `MaterialTheme`" that phase 0 § 3 promised to
  record, and `CLAUDE.md` records it with C. The base colours of the style (land, water, roads,
  labels) are chosen against the palette seeds, darker in the dark style. Black Trails get a light
  casing in the dark style so they stay visible.
- **Labels are Polish names**, the `name` tag, in Noto Sans with glyph ranges covering Latin
  Extended-A, so *ł*, *ś*, *ż* and *ą* render from local glyphs. A missing range draws nothing,
  silently, so R6 reads a label with each.
- **The style is two files**, `style-light.json` and `style-dark.json`, picked by the app's own
  night mode (`theme/NightMode.kt`), not by the system's alone.
- **Determinism is re-read.** The schema change rebuilds the archive, so phase 1's R4 is taken
  again, and the archive's size and the one-Trail patch are re-read against R5 and R7.

## 4. You are here

The ask, and every answer to it, in one table (`PLAN.md` rule 4):

| State | When | What the user sees |
| --- | --- | --- |
| Not asked | Until the first tap on the position button | The button, nothing else. No ask at launch |
| Precise | Fine granted | The dot, the accuracy circle, the heading cone; the map centres once |
| Approximate only | API 31+, coarse granted, fine not | The large accuracy circle, no heading cone, and a banner: "Approximate location only. Allow precise location to see where you are on a Trail." with "Settings" |
| Denied | The ask refused | The button stays; a tap asks again while Android allows it, then opens the app's settings |
| Location off | Granted, but the system switch is off | A banner: "Location is turned off." with "Settings" |
| Stale | No fix for 30 s | The dot greys, and the banner says "Last position %1$s ago." |

- **Fine and coarse are asked together**, with `RequestMultiplePermissions`, as Android requires
  from API 31 for the approximate option to be offered.
- **Every state but "not asked" is re-read** on each `ON_RESUME`: `checkSelfPermission` and
  `LocationManager.isLocationEnabled` can both be read back reliably, so the banners use
  `WarningBanner` (`ui/common/Surfaces.kt`).
- **The dot is MapLibre's `LocationComponent`**, in `RenderMode.COMPASS`, with its stale state on at
  30 s. Which location engine its default uses, and that it opens no connection and needs no Play
  services, is read from the AAR when D starts and confirmed by R8 on the phone. The assistance that
  speeds a first fix is Android's, not ours (ADR-0008).
- **The position button centres the map once.** It does not follow, and a pan does not need to be
  undone.
- **A position outside every ready Region is still drawn**, over the empty background, because "you
  are outside the map" is the information.
- **The stale line's age is a safety value.** A position shown as current when it is minutes old
  puts a hiker on the wrong side of a ridge, so its age and its threshold are a pure function with a
  test (below), and MapLibre's grey dot is configured to the same 30 s.

Copy: "Show my position", "Approximate location only. Allow precise location to see where you are on
a Trail.", "Location is turned off.", "Last position %1$s ago.", "Settings".

## 5. The palette

Phase 0 § 3's reading, now that there is a map: the position dot, the accuracy circle, the heading
cone, the position button and the banners, over a yellow and a black Trail, in light and dark. If
any of them reads as a Trail colour, or a Trail disappears under one, a seed changes in
`scripts/gen_scheme.py`, the scheme is regenerated, and the reading is taken again.

---

## Tests

One, for D's stale position: `PositionAge`, a pure function from a fix's time and now to fresh or
stale and the age shown. It fails in the direction the app exists to prevent: a fix that is old
reported as fresh. The test covers the 30 s boundary on both sides, a fix time in the future (a
clock change), and an age that crosses an hour.

Nothing in C is a safety value. A Trail drawn in the wrong colour is a real fault, but it is the
profile reading OSM, and a reading on the phone and in the tile catches it, not a unit test.

## Documents this phase amends

- **`PLAN.md`**: phase 2's paragraph and status line, split into 2 and 2b, with this file.
- **ADR-0007**: a dated amendment with C, saying the profile is the filter and no `tags-filter` step
  runs.
- **`CLAUDE.md`**: with B, the stack table's MapLibre row loses "debug only"; with C, the colour
  house rule gains the map style's Trail colours as its one exception.
- **`scripts/aab-permissions.py`**: with D, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` move
  into EXPECTED, each naming the position button.
- **`docs/privacy-policy.md`** and **`docs/play-app-content.md`**, in the same commit as D: the app
  reads the phone's location to draw it on the map, on the phone only, and never stores or sends it.
  Data safety stays "no data collected", because collection means leaving the device. The line
  "Would change any of it: location of any kind" is rewritten to say what does: storing it, which
  phase 6 does.
- **`DOD.md`**: phase 1's phone readings item closes with A.

## Known gaps, deliberately

- **The release map is empty until phase 2b.** A release from this phase reaches only the owner on
  internal testing, and its map says "No Regions downloaded yet." That is honest, and it keeps OSM
  data out of the release until the ODbL offer ships with it.
- **No feature can be tapped.** A Hut's name is on the map; its opening times, phone number and
  everything else in its tags are not reachable until a details sheet exists.
- **The compass is the phone's.** A heading cone from an uncalibrated magnetometer points wrong, and
  the app cannot tell. Android's own calibration prompt is the remedy.

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device or the build, with the command that
read it. A reading that drives the phone waits until the owner says it is free.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate: phase 1's R1, R8, R9 and R10's screen | phase 1's Readings table | |
| R2 | The release bundle declares none of the network three after B, and after D exactly the location pair beside AndroidX's own | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | |
| R3 | The release map opens on its empty state and the credit, no crash, in English and Polish | `./gradlew installRelease` or the internal-testing build, `adb exec-out screencap -p` | |
| R4 | Two builds of the new schema are byte-identical; its size and the one-Trail patch against phase 1's R5 and R7 | `python3 scripts/build-map-data.py --check-determinism`, then `--patch-from-extract` | |
| R5 | A way shared by two Trails draws both, side by side, with their symbols; Paths read as Paths | debug build with the Tatry, `adb exec-out screencap -p` at zoom 15 over w22773921 | |
| R6 | Labels with *ł*, *ś*, *ż*, *ą* render, in light and dark | the same, at two places named with them | |
| R7 | Each row of § 4's table, on the phone, with the API level | `adb shell pm revoke`/`grant`, the system location switch, a screencap per state | |
| R8 | Which engine `LocationComponent` uses; no `SecurityException`, no Play services | `adb logcat -d \| grep -E 'SecurityException\|LocationComponent\|LocationEngine'` | |
| R9 | The palette over a yellow and a black Trail, light and dark | `adb shell cmd uimode night yes` / `no`, screencaps | |
