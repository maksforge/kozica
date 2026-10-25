# Phase 5 — The user plans a Route over the Trails and sees how long it takes

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
adds [ADR-0010](adr/0010-routes-are-planned-over-our-own-trail-network.md) and relies on ADR-0001 (the
schema gate, which the Routes table is the first to pass), ADR-0005 (Auto Backup carries the
database, so saved Routes are backed up without new code), ADR-0007 (the pinned OSM build the network
comes from), ADR-0008 (everything on the phone) and ADR-0009 (the height grid the ascent is read
from). [`CONTEXT.md`](../CONTEXT.md) gains **Route**, Polish UI *trasa*.

**What closing this phase means.** Today the app is one map: it shows where the Trails go, and the
user works out the rest from the signposts. After this phase the user searches for "Morskie Oko",
adds Kasprowy Wierch, and the map draws a Route between them over the Trails, with its length, ascent,
descent and walking time, the time to each point, and an elevation profile; saved under a name, it is
in the Routes tab tomorrow, and after an update. The difference a device can be asked about: on the
test phone, a Route from Kuźnice to Morskie Oko through Zawrat draws in under half a second, never
takes Orla Perć against its one-way direction, and comes back from the Routes tab after `adb install
-r` of the next build.

**`PLAN.md`'s paragraph is right in what it promises and silent on what was decided on 2026-10-25,**
while this file was planned with the owner: a Route runs over Trails only; search finds named places
but not Trails; the network and the name index are our own, built with the map data and shipped in
the app; and the owner's highlighting idea, named Trails, GPX export, following a Route and a pace of
the user's own all move to phase 6.

---

## 0. What this phase inherits

- **The Trails are already built from the pinned OSM data** (phase 2's `trails` layer: every
  `route=hiking` relation's member ways, those with neither `colour` nor `osmc:symbol` drawn in the
  fallback colour). The network is the same ways, taken from the profile rather than filtered a
  second time (§ 2), so a Route can only follow what the map draws as a Trail.
- **Regions overlap by a few kilometres** (phase 2b § 4), and Trails cross from one Region into the
  next. A network per Region would cut every such Trail at a boundary; one network for all nine has
  no boundary to cut.
- **The height grid** (phase 4): `relief.pmtiles`, zoom 12, Terrarium in lossless WebP, about 12 m,
  pinned by `scripts/relief.pin` and fetched by `scripts/fetch-relief.py`. The contours are traced
  from it, so a Route's ascent and the lines under it agree.
- **The publish run reuses the last version's map archives when nothing that builds them changed**
  (ADR-0007's 2026-10-25 amendment, `scripts/map-archives.py`). Anything this phase adds to the build
  joins that record, or a reused release would ship a network from one pin beside archives from
  another.
- **Room is kept for this phase** (phase 3 § *The door*): the dormant `items` table is what
  `MIGRATION_1_2` replaces, and every tester's database is carried forward by it. This is the first
  schema bump since the door, so the whole standing gate applies (`DOD.md`).
- **The debug build's long-press** sets a fake position (`app/src/debug/`, `PositionOverride.kt`).
  Planning takes the long-press in `main/`, so the override keeps only `PositionReceiver` (§ 4).

## What is in, and what is deliberately not

**In:** the Trail network and the name index, built by `build-map-data.py` with the map data and
shipped in the app; A* over the network with one-way Trails obeyed; walking time by the rule in § 3;
ascent and descent from the height grid; search across all nine Regions; the Route on the map with
the rest of the map faded; the bottom sheet with totals, the time to each point, the profile, a
reverse button and difficulty warnings; saved Routes in Room; the tab bar with Map and Routes.

**Not in, and each with a reason rather than a phase:**

- **Unmarked Paths in a Route.** Decided with the owner: a Route runs over Trails only. In the Tatry
  only marked Trails may be walked, and `CONTEXT.md` keeps a Path from passing for a Trail; a planner
  that routed over Paths would do exactly that.
- **"Avoid difficult Trails".** A setting that changes Routes is one more thing to test, and the
  warning names the hard stretch already. Not in this plan; reopened if testers ask.
- **Seasonal closures.** TPN's winter closures are not reliably in OSM. The Safety screen's links stay
  the place for them.
- **GPX import.** An imported track can leave the Trails, which a Route cannot. Not in this plan.
- **Named Trails, highlighting a whole Trail, GPX export, following a Route while walking, and walking
  times from the user's own pace.** Moved to phase 6 with the owner: following needs phase 6's
  location service, a pace needs its recorded hikes, and the rest were kept with them rather than
  made a phase 5b.

**The tempting one** is letting the planner use a Path for the last few hundred metres to a
viewpoint or a car park off the Trails. It looks like a kindness and is the rule this phase exists
to keep: once a Route can take one unmarked metre, nothing stops it taking a Tatra gully. A point off
the network snaps to the nearest Trail, and the sheet says how far away the point is.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — the network and the name index built, and A* fast enough on the test phone | `feat:` the build's output and the debug apparatus; the verdict is a reading | — |
| **B** | The router: one-way Trails, walking time, ascent | `feat:` | A |
| **C** | The map: search, points, the drawn Route, the sheet | `feat:` | B |
| **D** | Saved Routes and the tab bar | `feat:` | C |

**A comes first** because every later checkpoint stands on the network's format: a format that
cannot be searched in half a second on a 2019 phone is rethought before any UI is written against
it. **D comes last** because it is the one checkpoint that freezes something: its table reaches
every tester's database, and a column changed after D ships is a second migration.

---

## 1. The gate: is planning instant on the test phone?

### The question, in the form that can be measured

On the test phone (`mnbm7hw8ivdqzx9l`, Redmi Note 8 Pro, API 30), with the network loaded: how long
from the request to the finished Route for the longest Route a person plans in one go, Kuźnice to
Łysa Polana across the Polish Tatry by the Trails, and for one across a Region boundary, Zakopane to
Turbacz? And how big are the network and the name index, and how long does loading the network take
at the first planning after a cold start?

### The apparatus

- `build-map-data.py` writes `trails.network` and `places.sqlite` beside the archives (§ 2).
- A debug receiver, `RouteReceiver` in `app/src/debug/`, takes two positions by `am broadcast`,
  plans between them on a background thread and logs `KozicaRoute` lines: load time, search time,
  nodes visited, the Route's length. No UI is needed to take the reading.

### The verdicts, decided now rather than argued about later

**No timing counts until both Routes are found and plausible**: each one's logged edges follow the
Trails the map draws between its ends, with no gap jumped and no detour a signpost would not send a
walker on. A Route not found or plainly wrong means the network is fixed first (a junction not
split, the Regions' overlap joined twice or not at all) and A is read again: an A* that finds
nothing has explored the whole component, so its time measures something else.

1. **Both Routes in under 500 ms, the network loaded in under 2 s** → B goes ahead on this format.
2. **Over 500 ms, or a load over 2 s** → the format is rethought before B (contracting the network
   to junctions only, with the geometry read per edge on demand), and A is read again.
3. **The files together over 30 MB** → the network keeps junctions and simplified geometry only, and
   the gate is read again.

Verdict 3 stands beside either of the others: a format that is fast but too big is still rethought,
and when 2 and 3 both hold, one rethink answers both.

### The verdict, taken *(date)*

*(Which one, the reading behind it.)*

## 2. The network and the name index

- **`trails.network`**: every way that the map draws as a Trail, from the union of the nine
  Regions, each way once by its OSM id, split at every junction and every point where another Trail
  joins. The ways are the ones `KozicaProfile.java` lists for its `route=hiking` relations, written
  beside each Region's archive, so the rule stays written once; their tags and nodes are read from
  the merged cut. Nodes carry a position and a height; edges carry their geometry, length, the
  walking time each way (§ 3), the ascent and descent each way, the `sac_scale` grade, and their
  one-way direction, if any, by § 3's rule. A compact little-endian binary with a format version in
  its header, loaded whole into memory; A's verdict confirms that or replaces it.
- **Heights are sampled at build time** from the pinned `relief.pmtiles`, which `fetch-relief.py`
  already puts in the build before the map data. The phone never decodes a relief tile to plan: the
  network carries the heights, so the profile and the ascent are the grid's numbers, read once,
  under a test.
- **`places.sqlite`**: named places from the same build, one row each, with its kind, its position,
  its Region, its local `name` and its `name:pl`, `name:sk`, `name:cs`, `alt_name` and `old_name`.
  An FTS4 table over the names folded to plain ASCII (FTS5 is not in every Android's SQLite at API
  26). Folding is done in the build and again on the phone, by one rule written twice and tested
  twice: lower case, accents off, and the letters no decomposition reaches (`ł`, `đ`, `ß`) mapped by
  hand.
- **Kinds searched, and the tags behind each:** peaks (`natural=peak`), passes (`natural=saddle`,
  `mountain_pass=yes`), Huts (`tourism=alpine_hut`), Shelters (`tourism=wilderness_hut`, or
  `amenity=shelter` that is not a bus shelter) and viewpoints (`tourism=viewpoint`), as the `pois`
  layer reads them; towns, villages and hamlets (`place=town|village|hamlet`), as the `places` layer
  does; and lakes and other named water (`natural=water` with a `name`, at a point on its surface),
  which no layer reads, since the `water` layer carries no name. Clearings (*polana*, *hala*:
  `place=locality`, a named `natural=grassland` or `landuse=meadow`) are not a kind searched.
- **Shipped in the app**, in the base module's assets, not as a pack: both are small, both are
  needed the moment the map opens, and both change only when the pin does, which is a release
  anyway. ADR-0010.
- **Build output, never committed**, like the archives. `build-map-data.py` writes both to
  `map-data/base-assets/`, which is gitignored, and the base module takes that directory as an
  assets source only under `-PmapPacks`, which fails at configuration when either file is missing,
  as it does for an archive. Without it (`ci.yml`'s `assembleDebug`, a local debug build) the debug
  half reads a copy hand-pushed to `files/network/`, as `RegionSource` does for an archive; with
  neither, search and planning say they are not in this build and the map works as before: a
  placeholder, never a crash.
- **They join the map record.** `scripts/map-archives.py` adds `scripts/relief.pin` to its inputs
  (the heights now come from it), the versions of SQLite and of the WebP decoder to its tool lines
  (SQLite writes its own version into bytes 96–99 of every file it writes), and both files to its
  outputs, so a reused release installs the last version's network and index with its archives, or
  builds all of them. `--check-determinism` writes both a second time and compares the bytes, as it
  does the Tatry's archive; they are small, so it costs seconds.
- **They join the published data.** `publish-play.yml` attaches both to the GitHub release beside
  the `.pmtiles`, and `scripts/map/MAP-DATA-LICENCE.txt` names them under ODbL. The network's heights
  are the relief's (ADR-0009's sources, CC BY 4.0 and GUGiK's credit), so
  `scripts/map/RELIEF-DATA-LICENCE.txt` is attached with them and names the network beside the two
  relief archives.

## 3. The router

- **A Route** is a start, an end and via points in the user's order. Each leg is the shortest
  walking time between its two points over the network; A* with the straight-line distance at 4 km/h
  as its heuristic, which never overestimates, since no walking time is faster than the flat.
- **Snapping.** A point is joined to the nearest point on any Trail, on the edge itself rather than
  only at a node. The sheet shows the distance from the point to where the Route starts when it is
  over 50 m.
- **One-way Trails** are never walked against their direction, read as the OSM wiki's *Key:oneway*
  reads them for walkers: `oneway:foot` (or `foot:backward`/`foot:forward=no`) wherever it is set,
  and it wins; otherwise `oneway=yes` or `-1` only on `highway=path`, `footway`, `steps` and
  `via_ferrata`, and never on a road or a track, where it binds vehicles only. `-1` is against the
  way's drawn direction. A leg that has no other way says so rather than drawing one.
- **Walking time**, per edge and each way, by the PTTK marker's rule (decided with the owner on
  2026-10-25, the best-sourced match for Polish signposts; no trail body publishes its own): **4 km/h
  on the flat, plus 10 minutes per 100 m of ascent, plus 5 minutes per 100 m of descent where the
  grade is steeper than 20 %**, added. The 20 % is a starting value; B's reading against real
  signposts may move it, and may give the Tatry constants of their own if they come out clearly
  faster than TPN's signposts. Totals and the time to each point are summed unrounded and then
  rounded up to 5 minutes, as signposts are.
- **Ascent and descent** are summed from the node heights with a 5 m dead band, so a 12 m grid's
  noise over a flat ridge does not add a climb at every cell.
- **Difficulty.** Every edge graded `demanding_mountain_hiking` or harder is collected per Route,
  and the sheet names each stretch once.

## 4. On the map

- **Search** is a field at the top of the Map tab. Results: names starting with what was typed before
  names containing it; within each, the nearest to the map's centre first. Each shows its local name,
  the name that matched underneath when it differs ("Gerlachovský štít", "Gerlach"), its kind and its
  Region. Picking one centres the map there and offers it as a point.
- **Points** are added from a search result or a long-press on the map: the first is the start, the
  last the end, the rest via points. In the sheet they can be reordered, removed, and the whole Route
  reversed. A round trip is the start picked again as the end.
- **The drawn Route** is a thick line in a colour of the scheme's palette, never one of the five
  Trail colours, with numbered markers at its points, generated by `scripts/map/gen_style.py` like
  every other map colour. While a Route is shown, every other Trail, Path and label fades to one
  opacity in the style; nothing is hidden, so the colours at a junction still match the signpost.
- **The bottom sheet**: length, ascent, descent, walking time; each point with the time to it; the
  elevation profile against distance; the difficulty warnings; Reverse and Save.
- **The debug position override** gives the long-press to planning. `PositionReceiver` becomes the
  only way to set a position, as every scripted reading already sets it; the Settings row still
  returns the map to the real one.

Copy, in English (Polish and the other shipped languages by the translation gate): "Search places",
"Start", "End", "Via %1$s", "Reverse", "Save route", "%1$s from the nearest Trail", "No way over the
Trails between %1$s and %2$s", "Includes a demanding stretch: %1$s", "Ascent", "Descent", "Walking
time", and the kind names (peak, pass, hut, shelter, viewpoint, town, village, hamlet, lake).

## 5. Saved Routes and the tab bar

- **A saved Route stores its name and its points only**: each point's position and label, in order.
  Decided with the owner: the legs, totals and profile are planned again from the current network
  every time it opens, so a Route always follows today's Trails. A point with no Trail near it any
  more opens flagged, not as an error.
- **The `routes` table** replaces `items` in `MIGRATION_1_2`, under all five items of the standing
  schema gate (`DOD.md`): the migration registered, `2.json` committed, `SchemaGateTest` extended,
  a migration test that reads a Route back, and an upgrade from 0.7.0 watched on the phone. The
  points are a child table, not a JSON column, so a point can gain a column later without rewriting
  every Route. `ItemEntity`, its DAO and repository go with the table.
- **The tab bar**: Map and Routes. Routes lists saved Routes by name with their length and time;
  opening one shows it on the map in the sheet, where it can be changed and saved again. Settings
  stays behind its icon on the map. Phase 6 adds the third tab.
- **Backup.** `AppBackupAgent` already carries the checkpointed database (ADR-0005), so saved Routes
  are in Android's backup with no new code. The manual archive in Settings stays phase 6's.

Copy: "Map", "Routes", "No saved routes yet", "Rename", "Delete route", "Delete %1$s?".

---

## Tests

- **The walking-time rule** for a flat edge, a climb, a gentle descent and a steep one, and the
  rounding up to 5 minutes of a sum, not of each part.
- **The dead band**: a sawtooth of ±3 m over a flat kilometre adds no ascent; a steady climb of 100
  m adds 100.
- **A\*** on a small hand-built network: the shortest-time leg, a one-way edge refused against its
  direction and taken with it, and a leg with no way.
- **Folding**, the same table in Python and Kotlin: "Śnieżka", "Łomnica", "Gerlachovský štít",
  "Sněžka", "Hala Gąsienicowa" to their folded forms, both sides asserting the same strings.
- **The network reader** against a fixture written by the build's writer, so the two ends of the
  format cannot drift.
- **`MIGRATION_1_2`**: a Route with three points written and read back after the migration, and
  `SchemaGateTest`'s assertion for 1 → 2.

## Documents this phase amends

- **`PLAN.md`**: this phase's paragraph and phase 6's, and the status line when it closes.
- **`CONTEXT.md`**: **Route** (added while this file was planned).
- **ADR-0010**, new.
- **ADR-0007**: an amendment, since its 2026-10-25 record gains the relief pin, SQLite's and the
  WebP decoder's versions and the two files, and its ODbL offer is no longer the archives alone.
- **ADR-0009**: its Context's "from one height grid on the phone" points to ADR-0010, which samples
  the grid in the build.
- **`phase-2b.md` § 6**: the offer attaches the network and the index too.
- **`DOD.md`**: "Drop the dormant `items` table" is ticked when D merges.
- **`CLAUDE.md`**: the ODbL house rule's list of what the release attaches; the stack table's
  routing line; the layout's `data/routes/`; the `app/src/debug/` paragraph, which loses the long
  press and gains `RouteReceiver` and the hand-pushed `files/network/`.
- **`scripts/map/MAP-DATA-LICENCE.txt`**: the network and the name index.
- **`scripts/map/RELIEF-DATA-LICENCE.txt`**: the network, whose heights it covers too.
- **`docs/privacy-policy.md`**: what the app keeps now includes saved Routes, on the phone and in
  Android's backup.
- **`docs/play-app-content.md`**: the Data safety reasoning gains the Routes table; still no data
  collected, as with the preferences file.
- **`docs/RELEASING.md`**: what the publish run attaches; and § *Why internal*, whose "an
  internal-track install demands an uninstall" phase 2c's R2 disproved: 0.3.0 from the internal
  track updated in place to 0.6.0.

## Known gaps, deliberately

- **Walking times are a rule's, not the signposts'.** No trail body publishes its formula; B's
  reading says how far ours is from theirs, and the sheet's times are an estimate like any
  signpost's.
- **The network changes with every pin**, so a saved Route can change shape after an update. That is
  the price of always following today's Trails, chosen over storing yesterday's line.
- **A Route cannot reach a place off the Trails.** It ends at the nearest Trail and says how far the
  place is.
- **Seasonal closures are not known to the planner.**

## Readings

`PLAN.md` rule 3. The test phone (`mnbm7hw8ivdqzx9l`) unless named; its keyguard cleared with `wm
dismiss-keyguard` before any screencap. A cold-started package is stopped, so every `RouteReceiver`
broadcast passes `-f 0x00000020`, as `PositionReceiver`'s does, and autostart, which lapses on its
own on both Xiaomi phones, is read with `scripts/device-gate.py` first (CLAUDE.md § Environment
notes).

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate: both Routes found and plausible (§ 1), then load time, plan time and nodes visited for Kuźnice → Łysa Polana and Zakopane → Turbacz; the two files' sizes | debug build, both files hand-pushed to `files/network/`; cold start; `adb shell am broadcast -n io.github.srednimax.kozica.debug/app.kozica.debug.RouteReceiver -f 0x00000020 --es from <lat,lon> --es to <lat,lon>`; `adb logcat -d -s KozicaRoute`; `ls -l` of both files in the build | *(date)* |
| R2 | Our walking times against the signposts: five Trail sections in the Beskidy and five in the Tatry, each signpost's time beside ours | photographed or published signpost times; `RouteReceiver` for each section | *(date)* |
| R3 | Orla Perć: a Route from Zawrat to Kozi Wierch takes it; one from Kozi Wierch to Zawrat does not | `RouteReceiver` both ways; the logged edges | *(date)* |
| R4 | Search: "gerlach", "snieznik", "morskie oko", "polana" near Turbacz; the first results and their order. Clearings are not a kind (§ 2), so "polana" expects only places of the searched kinds whose names hold the word, those starting with it first, each group nearest Turbacz first | the release-shaped debug build, typed on the phone; screencap | *(date)* |
| R5 | The Route on the map: Kuźnice → Morskie Oko via Zawrat drawn with the rest faded, the sheet's numbers and profile, light and dark | screencap of each | *(date)* |
| R6 | The upgrade, both ways it reaches a phone: `main` before D as `assembleDebug -PreleaseShapedDebug`, opened; D's the same way over it, opened past the launch gate; a Route saved, the app killed and reopened; then D's release from the internal track over 0.7.0 from Play, updated in place as phase 2c's R2 was, opened | `adb install -r` of each APK, `am force-stop`, launch, screencap of the Routes tab; Play's install for the last step | *(date)* |
