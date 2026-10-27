# Phase 6c — The user taps a Trail and sees all of it, by name

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on ADR-0007 (every Region's pack is rebuilt and delivered again), ADR-0008 (nothing new reaches
the network: the Trail index ships in the app like the name index) and ADR-0010 (the router, which
gains one constraint: stay on this Trail). [`CONTEXT.md`](../CONTEXT.md) gains **Section**, and
**Trail** gains the rule for its name. Planned with the owner on 2026-10-27.

**What closing this phase means.** Today a Trail on the map is a coloured line that does nothing when
tapped, and the search knows places but not Trails. After this phase the user taps the red line above
Kuźnice, sees that whole Trail picked out from Zakopane to Kasprowy Wierch with the rest of the map
faded, reads its name, length, climb each way and profile on a sheet, and with one button turns it
into a Route or a GPX file; typing "Główny Szlak Beskidzki" into search finds it too. The difference
a device can be asked about: on the test phone, a tap on each of the four strands where four Trails
share a path selects the Trail it was on, by way of a chooser, and the highlight draws within the
frame budget phase 4 measured.

`PLAN.md`'s paragraph still holds; it named the change to the data and nothing more.

---

## 0. What this phase inherits

- **The tiles know a Trail's colour, symbol, `ref`, its place among the Trails sharing a way
  (`index`, `count`), and nothing that names it** (`scripts/map/KozicaProfile.java`, `trails`
  layer). One feature is written per Trail per way. Two pieces of the same Trail are therefore
  indistinguishable from two Trails of the same colour: the map cannot pick out "all of this one"
  until a relation id is in every feature. That one attribute is the whole of the map-data change.
- **Fading the rest of the map already exists.** Layers the style marks `kozica:fade`
  (`scripts/map/gen_style.py`) are faded by `MapStyle.kt` while a Route is shown (phase 5 § 4). A
  selected Trail reuses that, so there is one way the map steps back, not two.
- **The network keeps each edge's first way id, and the build already lists every Trail's ways**
  (`map-data/archives/<id>.ways`, phase 5 § 2). What it does not keep is which Trails an edge
  belongs to. The Route along a Trail and the Trail's length, climb and profile all need that
  mapping, so it is written at build time, once, beside the network.
- **The name index is SQLite with FTS4 over folded names** (phase 5 § 2, `PlaceIndex.kt`). Trails
  join it as a kind of their own rather than as a second index, so search stays one query and one
  fold rule.
- **A tap on the map today only opens a Hike's photo**; a long press plans a Route. The Trail tap
  takes the plain tap where it lands on no photo, and the long press is untouched.
- **Phase 6 exports a saved Route as GPX** through the share sheet, with the share-grant fix MIUI
  needed. A Trail's GPX is that writer with the Trail's line.

## What is in, and what is deliberately not

**In:** a relation id on every Trail feature and a Trail index built from a freshly pinned extract;
tapping a Trail to select it, with a chooser where several share the path; the selected Trail
highlighted whole with the rest faded; a sheet with the Trail's title, colour and symbol, length,
ascent and descent each way, elevation profile, "Plan a Route along it" (the whole Trail, either
direction) and "Export GPX"; Trails found by name in the existing search; a long-distance Trail's
sections selectable one at a time, with the long Trail named and shown whole on request.

**Not in, and each with a reason rather than a phase:**

- **A stretch of a Trail as a Route.** The owner chose the whole Trail, end to end. A stretch needs
  a picking mode on the highlighted line, and a Route made from the whole Trail is already editable
  in the Route sheet, where its ends can be moved like any Route's.
- **Trail names drawn on the map as labels.** Names along four side-by-side strands would collide
  with each other and with the symbols already drawn every few hundred metres; the sheet carries the
  name instead.
- **Storing a Trail as anything of the user's.** A selection is not saved; the Route it makes is, as a
  Route. No schema change.
- **Unmarked Paths in the tap.** A Path is never named or selected as if it were a Trail
  (`CONTEXT.md`).

**The tempting one: names in the tiles.** Writing each Trail's name into every feature looks free,
since the sheet needs the name. It is not: a name is repeated on every way of the Trail in every tile
at every zoom, and four Trails on one way carry four names. The tiles get one integer, the relation
id; the names, `from`/`to`, parent and length live once each in the Trail index, which the sheet and
the search both read.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — do the Regions' Trail relations carry enough to title them? | `chore:` the tally script; the verdict is a reading | — |
| **B** | The data: relation ids in the tiles, the Trail index, edge-to-Trail mapping, a fresh extract | `feat:` (the map data refreshes) | A |
| **C** | Tap, chooser, highlight, the sheet's facts and profile | `feat:` | B |
| **D** | Trails in search | `feat:` | C |
| **E** | Plan a Route along it, and Export GPX | `feat:` | C |

**A comes first** because the sheet's title rule (§ 2) is only sound if the data has the tags it
reads; if most relations have none, the title falls back to places, and the place lookup is built in
B rather than discovered in C. **B is one checkpoint** although it touches the profile, the network
build and the index, because each half alone ships nothing and they move the same pin. **D and E
need only C** and can land in either order.

---

## 1. The gate: do the Trails carry what titles them?

### The question, in the form that can be measured

Of the `route=hiking` relations whose ways fall in the nine Regions, from the freshly pinned extract:
what share has a `name` (or `name:pl`), what share has `from` and `to` but no name, and what share
has neither? And how many are members of a parent (`type=superroute`, or a `route=hiking` relation
whose members are relations)?

### The apparatus

`scripts/map/trail-tally.py`, reading the merged cut the build already makes, printing the four
counts per Region and twenty sample titles from each rule, so the reading is the printout.

### The verdicts, decided now rather than argued about later

1. **Name or `from`/`to` on 80 % or more** → § 2's rule as written; the place fallback covers the
   rest.
2. **Under 80 %** → the place fallback becomes the main path, and B builds the ends' lookup
   (nearest named peak, pass or Hut within 500 m of each end, by phase 5's rule) before C.
3. **Parents found are fewer than a handful, or none carry a name** → § 2's section rule ships, and
   the "Show the whole long Trail" action is dropped rather than built for no data.

### The verdict, taken *(date)*

*(Which one, and the tally behind it.)*

## 2. The data

- **`KozicaProfile.java` writes `trail` (the relation id) on every `trails` feature**, alongside
  colour, symbol and `index`. A section of a parent writes its own id; the parent lives only in the
  index.
- **The extract is re-pinned** (`scripts/osm-data.pin`) when B's data is built, so the one
  re-delivery of every pack also brings the newest OSM. The ODbL release attachment follows by
  itself (`publish-play.yml`).
- **`trails` table in `places.sqlite`**: one row per Trail with its id, title, `name`, `name:pl`,
  `ref`, colour, symbol, `from`, `to`, parent id and parent name, length, ascent and descent each way,
  and its bounding box; its folded names go in the same FTS table as places, with kind `trail`.
- **`trails.edges`** beside `trails.network`: for each Trail, its edges in walking order. Length,
  climb and the profile are read from the network's heights along those edges, as a Route's are, so
  a Trail and the Route made from it agree to the metre.
- **The title rule:** `name:pl` or `name` if present; else "*colour* Trail: *from* – *to*" from the
  tags; else the same with the nearest named place at each end (§ 1, verdict 2). A section of a
  parent shows its own title, and the parent's name above it.

## 3. On the map

- **A tap** queries the `trails` layer in a finger-sized box around the point. One Trail id →
  selected. Several → a chooser listing each with its colour chip, symbol and title. None → the tap
  does what it does today.
- **The selected Trail** is drawn by a highlight layer filtered on its id, over the faded map
  (`kozica:fade`), across every Region whose tiles it crosses. A long Trail's "Show the whole *name*"
  highlights every section with that parent.
- **The camera** stays where it is after a tap; "Show whole Trail" on the sheet fits its bounding box.
  A Trail picked from search fits it straight away.
- **Leaving:** closing the sheet, back, or a tap on empty map clears the selection and the fade. A
  Route being shown and a Trail selected never coexist: selecting one clears the other.
- **The sheet:** title (parent name above it for a section), colour and symbol, length, ascent and
  descent each way, the elevation profile (the Route sheet's chart), and two buttons: "Plan a Route
  along it" and "Export GPX".

**Copy:** "Trails here", "Show whole Trail", "Show the whole %1$s", "Plan a Route along it", "Export
GPX", "%1$s Trail: %2$s – %3$s" (colour name, ends), and the five colour names as adjectives for a
Trail.

## 4. Search

Trails appear in the existing search results with their colour chip and symbol, ranked with places by
the same folded match; a long Trail matches by its own name and its sections by theirs. Picking one
selects it and fits the camera (§ 3).

## 5. A Route along it, and GPX

- **"Plan a Route along it"** asks the router (ADR-0010) for a Route from the Trail's first edge to
  its last, **restricted to the Trail's own edges** (`trails.edges`), so it cannot take a shortcut
  over another Trail. The result opens in the ordinary Route sheet, where it can be reversed, edited
  and saved under the Trail's title by default.
- **"Export GPX"** writes the Trail's line with the network's heights through phase 6's writer and
  share path, named after its title, with the OSM `<copyright>` and `<link>` the Route export
  carries.

---

## Tests

- **The title rule**, each branch: name, `name:pl` over `name`, `from`/`to`, place fallback, section
  with parent. Fails towards a blank or a wrong-language title.
- **`trails.edges` order**: a Trail's edges join end to end, and its length equals the sum of their
  lengths. Fails towards a profile that jumps.
- **The restricted router**: on a fixture where another Trail is a shortcut, the Route along the
  Trail stays on the Trail. Fails towards a Route that leaves it.
- **Folding**: Trail names fold by the one rule places use (`ł`, accents), in the build and on the
  phone.

## Documents this phase amends

- **ADR-0010**: an amendment for the restricted search (a Route along one Trail).
- **`CONTEXT.md`**: **Section** (one relation of a long-distance Trail), and the title rule under
  **Trail**.
- **`docs/store-listing.md`**: the full description gains tapping and finding Trails by name, in
  both languages, and the release notes say so.
- **`docs/phase-2.md` § 3**: the `trails` layer's attributes gain `trail`.

## Known gaps, deliberately

- **A Trail's title is OSM's, not the signposts'.** Where a relation is untagged and no place is
  near an end, the title is the colour alone. Fixing it is an OSM edit, which reaches the app with
  the next data pin.
- **A Trail crossing out of every Region is cut where the data stops.** Its length and Route are
  those of the part Kozica has.

## Readings

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate's tally per Region | `python3 scripts/map/trail-tally.py` | *(owed)* |
| R2 | Four strands at one point: each tap's chooser lists all four, and each pick highlights the right one | test phone, debug build with the Regions hand-pushed; screencaps | *(owed)* |
| R3 | The highlight and fade draw within phase 4's frame budget | `ui/map/FrameLog.kt`, `scripts/map/frame-stats.py` | *(owed)* |
| R4 | A Route along a Trail matches the Trail's length and climb | the sheet against the Route sheet | *(owed)* |
| R5 | The update size Play reports for the re-delivered packs | Play Console, the release's size | *(owed)* |
