# Phase 7a — The map shows the Bike routes, and one switch turns it to Cycling

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on [ADR-0011](adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md)
(one Mode switch, Bike routes only), ADR-0007 (every Region's pack is rebuilt and delivered again),
ADR-0008 (the Bike routes' index ships in the app like the Trails') and ADR-0010 (the network gains
the Bike routes' ways as a second set of edges, with their direction; its router is untouched until
7b). [`CONTEXT.md`](../CONTEXT.md) already holds **Mode**, **Bike route** and **Bike**; it owes
nothing new. Planned with the owner on 2026-11-01, the design advised by a design review of the
style and the mark.

**What closing this phase means.** Today the map draws Trails and nothing else that is marked; the
Regions' 1,450 signed cycling routes are dropped by the profile. After this phase a developer build
has a walker | bike switch under the search row: in Cycling the Bike routes are drawn in their
marking colours with R-1 plates along them, the Trails faded beneath; a Bike route tapped is seen
whole with its title, length, ascent, profile and MTB difficulty, and a one-way descent shows only
the way it is ridden; search finds Bike routes and offers the other Mode's matches. A release build
shows Hiking alone, with the Bike routes faded beneath the Trails, until phase 7c opens the switch
to everyone. The difference a device can be asked about: on the test phone, in Cycling, Bielsko's
Twister draws its chevrons downhill and its sheet gives one direction only, and the map at zoom 13
with every Bike route drawn stays within phase 4's frame gate.

**`PLAN.md`'s paragraphs move one thing.** The Bike routes' edges, their membership and their
direction were 7b's; they are built here, because the sheet's length, ascent and profile need them
and a one-way line must never be offered the wrong way on a sheet. 7b keeps the router, the riding
time and planning.

---

## 0. What this phase inherits

- **Phase 6c built everything a Bike route needs once it is data**: a relation id on every Trail
  feature, membership from the profile, edges cut where the routes on a way change, `trails.edges`,
  the walk that draws a whole Trail below zoom 10, the tap with its chooser, the highlight and fade,
  the sheet with its profile, Trails in the name index, Long-distance Trails with Sections. 7a feeds
  Bike routes through the same paths rather than building a second set.
- **`KozicaProfile` keeps hiking relations and drops the rest.** Its `trails()` and `reversed()`
  compute each way's `index`/`count` and its drawn direction over Trails only (phase 2 § 3). A way a
  Bike route shares with a Trail needs one order over both, or a red Bike route lies on a red Trail
  and the X-crossing phase 2 fixed comes back.
- **`build-network.py`'s `oneway()` reads foot tags only.** A walker has no direction; a rider on a
  trail-centre line has one, put in four places by OSM (`cycling-research.md` § 3).
- **The style's fade is one key.** `faded()` in `gen_style.py` asserts no `-opacity` of its own in a
  layer's paint, and `MapStyle.kt` applies `kozica:fade` while something is selected. A second fade
  for the other Mode is a second key.
- **The launcher icon is one in both variants.** `art/mark.py` is the identity, `draw-mark.py`
  rewrites `MARK` and `HOLE` only, and the generators iterate `PARTS`.
- **Developer-only surface lives in `app/src/debug/`**, with its other half in `app/src/release/`
  (`CLAUDE.md`). The switch and the new mark follow that rule until 7c.

## What is in, and what is deliberately not

**In:** `route=bicycle` and `route=mtb` relations in every Region's tiles, in a `bikes` layer counted
with the Trails; their edges, membership and direction in the network; Bike routes in the name index;
the style (lines, plates, chevrons, violet for no colour, numbers, zoom thresholds, the Mode fade);
the Mode switch and the All · Touring · MTB chips, developer-only; the tap, highlight and sheet in
Cycling; search in Cycling with the hint row; the mark with wheels in its crag, developer-only.

**Not in, and each with a reason rather than a phase:**

- **Planning a Route in Cycling, and a riding time.** The router's second set of edges and the
  timing rule are 7b's: the rule is checked against published riding times, which is its own work.
  The sheet in 7a shows no time and no "Plan a Route along it".
- **GPX of a Bike route.** It is the Route along it written out, so it comes with that in 7b.
- **The switch in release builds.** A release user who could switch to Cycling could not plan or
  record there, and a long press would plan a walk. The switch opens in 7c, when both work.
- **The new mark in release builds.** An icon that promises bikes in an app that cannot switch to
  them is the one thing in 7a a release user would see. It ships with the switch.
- **`mtb:scale` on the map.** On 14 % of MTB-route ways it would draw a patchwork that looks like
  data where there is none; the sheet says it with its share (§ 4).

**The tempting one: letting a tap reach the other Mode's routes.** Where a Bike route shares a way
with a Trail, a chooser listing both looks like a free convenience. It is not: the tap would switch
the Mode by a side door, and the chooser 6c kept short doubles. A tap answers the current Mode only;
search's hint row is the one way across (§ 5).

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **Readings, no gate**: the archives' growth and the title tally | `chore:` the tally | — |
| **B** | The data: the `bikes` layer counted with the Trails, direction, edges and membership, the index, a fresh pin | `feat:` (the map data refreshes) | A |
| **C** | The style, the switch and the chips (developer-only) | `feat:` | B |
| **D** | The tap, the highlight and the sheet in Cycling | `feat:` | C |
| **E** | Search in Cycling and the hint row | `feat:` | D |
| **F** | The mark with wheels in its crag (developer-only) | `feat:` | — |

**There is no gate.** Bike routes ship whatever they cost: the owner decided on 2026-11-01 that size
blocks nothing, so A's figures are recorded, not judged. **B is one checkpoint** for the reason 6c's
was: the profile, the network and the index move the same pin, and each alone ships nothing. **F
depends on nothing** and can land at any point; it only waits for the owner's approval of the
renders.

---

## 1. Readings before the build

- **The archives' growth**: each Region's `.pmtiles` and the three base files, built with the
  `bikes` layer from the pin B will ship, against 0.14.4's (355 MB of Regions, `trails.network`
  9.7 MB, `trails.edges` 128 KB, `places.sqlite` 7.2 MB). Recorded per Region and in total.
- **The title tally**: `scripts/map/trail-tally.py` gains Bike routes, counting how many land at
  each step of § 4's title rule, per kind and per Region.

## 2. The data

- **Kept relations**: `route=bicycle` (Touring) and `route=mtb` (MTB), with `name`, `name:pl`, `ref`,
  `colour`, `network`, `from`/`to`, `mtb:scale`, `mtb:scale:uphill`, `signed_direction`. A Bike route
  whose members are Bike routes is one with Sections, by 6c's Long-distance rule.
- **The `bikes` layer**, from tile zoom 10: one feature per Bike route per way, with `bike` (the
  relation id), `kind` (`touring` | `mtb`), `colour` (the five or `other`), `network`, `ref`,
  `index`, `count`, and `dir` (`forward` | `backward`) on a one-way way only. **`index`/`count` and
  the way's drawn direction are computed over the union** of Trails and Bike routes on the way:
  Trails by colour rank then id, then Touring, then MTB, each by colour rank then id; the reference
  relation is the union's first. `trails()` and `reversed()` change to read the union.
- **Direction**, per relation, from every place OSM puts it: `oneway:bicycle`, else `oneway`, on the
  way; the member's `forward`/`backward` role; and `signed_direction=yes`, whose sense is the
  relation's own member order. Each one-way relation's sense is computed against its own order, then
  written as `dir` against the way as drawn. Two-way ways carry no `dir`.
- **The network**: the Bike routes' ways join `build-network.py` as a second set of edges, with their
  direction, membership and cuts where the Bike routes on a way change, by the mechanism 6c built
  for Trails. A Bike route's walk, length, ascent each way and profile come from them. The router
  does not read them until 7b.
- **The name index**: Bike routes join `places.sqlite` as a kind of their own, folded by the one rule.

## 3. On the map

- **The line**: the Trails' widths (`TRAIL_WIDTHS`: 2.0 at 10, 3.0 at 13, 4.5 at 15, 7.0 at 17),
  solid, round joins; yellow and black take the Trails' casing rule, nothing else is cased. Strands
  sit side by side in the union's order.
- **Zoom thresholds**, in the style, the same in both Modes: `icn` and `ncn` from 10, `rcn` from 11,
  `lcn`, no `network` and every MTB route from 12. Judged on the phone (R3) and moved there if the
  map reads badly.
- **The plates**: 20×12 px at 1×, white with the Trail plates' edge, a bar in the marking colour across
  the bottom and a black bicycle over it, after the Polish R-1 sign. Touring: thin wheels, drop bar.
  MTB: heavy wheels, flat bar, standing on a zigzag of rough ground. Upright in the viewport, stacked
  by slot where routes share a way, from zoom 13, size 0.85 at 13 to 1.3 at 17, spacing 260 px at
  13 to 180 at 16. Sprites `bike_touring_<colour>` and `bike_mtb_<colour>`, `other` included.
- **No marking colour**: violet `#7f3fbf`, the same in both themes, a literal beside the five Trail
  colours in `CLAUDE.md`'s exceptions. The `ref` runs along the line, size 10, from zoom 13 on violet
  routes and from 15 on coloured ones; hidden, not faded, in the other Mode.
- **One-way**: open chevrons on the line, 8×8 px, in the strand's casing tone (`route_casing` on red,
  blue, green and violet, the yellow and black casings on theirs), rotated by `dir`, from zoom 13,
  spacing 120 px at 13 to 80 at 16. The slot offset's sign flips with a backward chevron, since the
  offset turns with the icon. A selected route's chevrons are 0.2 larger and never faded, and its
  walk below zoom 10 carries them too.
- **The Mode fade**: the other Mode's lines, plates and Trail symbols keep their colour at opacity
  0.4 in light and 0.5 in dark, by a second style key `kozica:mode` (`hiking` | `cycling`) that
  `MapStyle.kt` applies as the lower of it and the selection fade. Under a selection the rest stays
  at 0.3 and the other Mode drops to 0.2.
- **The switch**: a two-segment button, walker and bike, under the search row at the left; in
  Cycling the chips All · Touring · MTB beside it, filtering the map as the sub-filter will filter
  planning. The Mode is stored and the app opens in the last one, in Hiking on a first launch. In
  `app/src/debug/`; `app/src/release/`'s half passes Hiking.

Copy: "Hiking", "Cycling" (the switch's descriptions), "All", "Touring", "MTB".

## 4. The tap and the sheet

- **Only the current Mode answers a tap**: Trails in Hiking, Bike routes in Cycling. The chooser
  lists the current Mode's routes only.
- **The title**: its `name` (`name:pl` over it in Polish), else its number and kind, else "*colour*
  Bike route: *from* – *to*" from the ends a mapper wrote or the places nearest them, else the colour
  and kind alone. A route with both a name and a number shows the number under the title.
- **The facts**: kind, colour and plate, length, ascent and descent each way, the profile. A one-way
  route gives the ridden direction only. No time.
- **The MTB difficulty**: the route's own `mtb:scale` if it carries one, else the hardest grade on its
  ways with the share of its length graded; `mtb:scale:uphill` the same way; nothing at all when
  nothing is graded. The graded stretches are tinted on the profile, darker for harder.

Copy: "Bike route *n*", "MTB route *n*", "*colour* Bike route: *from* – *to*", "*colour* Bike route",
"*colour* MTB route", "S*n* at its hardest · graded over *p* % of its length", "Uphill S*n* at its
hardest", "Ridden one way".

## 5. Search

Search finds places in both Modes, Trails in Hiking and Bike routes in Cycling. When the other Mode
has matches, one row closes the results: a tap switches the Mode and keeps the text, so the results
are now those routes.

Copy: "*n* Trails match: show them in Hiking", "*n* Bike routes match: show them in Cycling", each
with its plural forms.

## 6. The mark

The crag becomes the bike the chamois stands on: the slab deepened by 12 units, with two round holes
of radius 17 at (−30, 76) and (18, 76) as wheels, `Subpath(hole=True)` in `PARTS`; the chamois
untouched. A third hole, the frame diamond (−12, 58) (6, 58) (−3, 78), stays only if it reads at
44 dp. `draw-mark.py` learns to write the wheels, or its next run loses them. Rendered at 48 dp,
24 dp and 512 px for the owner's approval before it merges; the launcher resources go under
`app/src/debug/` until 7c.

---

## Tests

- **Direction**, in the build, one fixture per place OSM puts it: `oneway:bicycle` over `oneway`
  (both senses and `no`), a `forward` and a `backward` role, `signed_direction=yes` with no roles in
  a member order opposite to a way's drawing, and a way drawn reversed by the union's reference
  relation. Fails towards a descent offered uphill.
- **One order over both kinds**: a way with two Trails and two Bike routes, one of each red, gets
  four distinct slots, in the union's order, and the drawn direction of the union's first. Fails
  towards a strand drawn over another.
- **The title rule**, each branch, in English and Polish, as 6c's test does for Trails.
- **The difficulty line**: the route's own tag over its ways', the hardest grade and its share, and
  no line when nothing is graded. Fails towards an easy grade claimed for an ungraded route.

## Documents this phase amends

- **ADR-0010**: an amendment: the Bike routes' ways are a second set of edges with direction, built
  in 7a, read by the router from 7b.
- **ADR-0011**: its Consequences note that the edges and direction are built in 7a.
- **`PLAN.md`**: 7a's and 7b's paragraphs, for the edges and direction moved here.
- **`CLAUDE.md`**: the colour exceptions gain the Bike routes' five colours, violet and the chevron
  tones; the Layout gains the switch's seam.
- **`docs/phase-2.md` § 3**: the `bikes` layer and the union's order.
- **`docs/store-listing.md`** and the release notes: not until 7c, when release users can switch.

## Known gaps, deliberately

- **A Bike route's direction is OSM's.** A trail-centre line mapped with none of the four tags is
  drawn and given as two-way. Fixing it is an OSM edit, reaching the app with the next pin.
- **Difficulty is thin.** 14 % of MTB-route ways and 1.5 % of Touring ones carry `mtb:scale`; most
  sheets will have no difficulty line, and that is said by its absence rather than guessed.
- **Release users see faded Bike routes they cannot select** from B until 7c. Harmless, and the
  alternative, a second set of archives, is not worth it.

## Readings

`PLAN.md` rule 3. The test phone (`mnbm7hw8ivdqzx9l`) unless named, its keyguard cleared, a debug
build with the Regions hand-pushed.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The archives' and base files' growth, per Region and in total | `build-map-data.py` over B's pin; `ls -l map-data/archives/` against the v0.14.4 release's attachments | |
| R2 | The title tally for Bike routes | `python3 scripts/map/trail-tally.py` | |
| R3 | The plates, the fade and the zoom thresholds at real size, in both themes | test phone, screencaps at zoom 10–17 over Beskidy Zachodnie and the Sudety | |
| R4 | Direction: Twister (r5738733), Rapický okruh (r1636172) and a Rychleby sub-trail draw their chevrons the way they are ridden, and their sheets give one direction | test phone, `CameraReceiver` to each, screencaps | |
| R5 | A tap in Cycling where Trails and Bike routes share a way lists the Bike routes only | test phone, screencap of the chooser | |
| R6 | The frame time at zoom 11 and 13 in Cycling with every Bike route drawn, against phase 4's gate | `FrameLog`, `frame-stats.py` | |
| R7 | The hint row switches the Mode and keeps the text | test phone, screencaps | |
| R8 | The mark at 48 dp, 24 dp and 512 px | renders, the owner's verdict | |
