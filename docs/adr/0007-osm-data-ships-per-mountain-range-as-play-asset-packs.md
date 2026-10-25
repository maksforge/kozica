# OSM data ships per mountain range as Play asset packs, built from a Geofabrik extract kept current by diffs

## Context

The trail data comes from OpenStreetMap, built by us from Geofabrik and never from the public tile
server or Overpass (house rule in [`CLAUDE.md`](../../CLAUDE.md)). Geofabrik's Poland extract,
`poland-latest.osm.pbf`, is **2.0 GB**. That raised the question of what a data refresh costs:
re-uploading the whole thing to Play, and users downloading it again.

Neither has to happen, because the 2 GB file is only the input and the data changes slowly.
Geofabrik publishes a daily change file (`.osc.gz`) for the same extract. The last seven, read on
2026-10-18 from `download.geofabrik.de/europe/poland-updates/`, were **2.6–3.4 MB each for the whole
country**. Geofabrik also keeps dated copies of the extract: the last week's days, the first of each
recent month, and each January back to 2014. And the app needs only part of the input: the mountain
ranges, not the whole country.

Size on the phone is not what limits how much of that data the app carries. Play allows **1.5 GB per
asset pack and 30 GB across all on-demand and fast-follow packs** (Google Play's size limits, read on
2026-10-18), and hosting there is free. The limit is how useful a feature is to someone on a trail.

Geofabrik's smaller cuts are voivodeships (Małopolskie 193 MB, Podkarpackie 147 MB, Śląskie 177 MB,
Dolnośląskie 173 MB). Those don't fit either, because the ranges cross them: the Beskidy alone span
three voivodeships.

The app has no network access. It declares no `INTERNET` permission (`scripts/aab-permissions.py`
forbids it), and the privacy policy says nothing the user enters leaves the device. So whatever the
phone downloads has to arrive without the app opening a connection itself.

## Decision

**The data build runs in CI, never on a personal machine.** It is a job in `publish-play.yml` that
runs before `bundleRelease` and writes the region files into the asset-pack modules. Region files are
build output: they are gitignored and never committed.

**A committed pin says which OSM data a build uses.** A small file, `scripts/osm-data.pin`, holds the
Geofabrik replication sequence number the build targets. The build brings the extract to exactly that
sequence with `pyosmium-up-to-date --end-id` (pyosmium 4.2 or later), then checks the sequence in the
updated file's PBF header against the pin and fails if they differ. The same tag therefore always
builds the same data.

**Each release downloads a dated extract and brings it forward with the daily diffs.** The build
downloads the newest dated Geofabrik extract from before the pin, and `pyosmium-up-to-date` applies
Geofabrik's change files up to the pinned sequence. Nothing is cached between builds. The
replication state lives in the PBF header (`osmosis_replication_sequence_number`,
`osmosis_replication_timestamp`, `osmosis_replication_base_url`), which `pyosmium-up-to-date` reads and rewrites, so the file carries
its own date and there is no separate state file to keep.

**Regions follow mountain ranges, not administrative borders.** Each region is a boundary polygon
committed under `scripts/`. A region is cut from the national extract with `osmium extract`.
**Inside a region the data stays rich**: trails, huts, peaks and shelters, but also roads, villages,
water, land cover and anything else from OSM that the map or the app can use. `osmium tags-filter`
drops only what nothing in the app reads. The list of regions and the filter are fixed when the map
phase opens. Elevation (contours, hillshading) is not OSM data: it would come from a DEM through a
separate pipeline with its own licence and attribution, and is out of scope for this ADR.

**The map phase picks the file format, and the format must patch well.** Play sends an asset-pack
update as a delta against the copy already on the phone, so the format has to keep an edit local: one
changed trail must leave the bytes for the rest of the region where they were. Many small files or
tiles meet that, as does any layout whose compression restarts per area. A single block-compressed
file (a PBF, one zstd stream, an SQLite database rewritten from scratch) does not: one inserted node
shifts or recompresses everything after it, and the delta approaches the full size. This ADR covers
how the data is built and delivered; what the files contain is that phase's decision, within this
requirement.

**One script builds everything, deterministically.** `scripts/build-map-data.py` runs update, cut,
filter and package from start to finish. It is the script offered under ODbL from the first release.
Its output depends only on its input: features in a stable order, no build timestamps, fixed
compression settings. A region that saw no edits between two pins therefore produces a
byte-identical file.

**Each region is an on-demand Play asset pack.** The base install carries no map data. The user picks
the ranges they hike, at home on Wi-Fi, and Play downloads those packs. The Play Store app does the
download; the app only asks for a pack and reads it once it is on the device, and the Play Asset
Delivery library declares no `INTERNET` permission. A refresh rides an ordinary app release. With a
format that meets the requirement above, a refresh costs roughly what changed and an unchanged region
costs nothing. Busy ranges such as the Tatry and the Beskidy see edits between almost any two pins,
so those packs change in every refresh.

**The data date is visible.** The app shows the OSM timestamp from the build, e.g. "OSM data:
2026-10-17", next to the "© OpenStreetMap contributors" credit on every map screen. A user can see
how fresh the trails are without reading a changelog.

**A data-only refresh is a `fix:` commit that moves the pin**, e.g. `fix: refresh trail data to
2026-10-17`. It changes `scripts/osm-data.pin` and nothing else. release-please cuts no release for
`chore:`, so a refresh committed under that type would never reach anyone.

## Alternatives

**Cache the extract between builds.** It would save the 2 GB download. But `publish-play.yml` runs
on a release tag, and a tag ref can't restore a cache saved under a different tag, so `actions/cache`
would never hit. Keeping one warm would need a scheduled job that pulls Geofabrik's diffs every day
for a build that happens a few times a year.

**Build the data on a developer's machine and upload the bundle from there.** It keeps a warm 2 GB
file between builds for free. But the release bundle would no longer come from the tagged checkout
that `publish-play.yml` builds and checks, and the data in it would depend on whatever that machine
last ran.

**Ship the voivodeship extracts, or whole Poland.** Size alone would allow it. But the user would
pay for lowland cities they never hike in, and the ranges would still be split across files.

**Host the region files ourselves (GitHub Releases, a bucket) and download them in the app.** This
would refresh data without an app release. But the app would need the `INTERNET` permission it does
not have today, every download would send the user's IP address to a third-party host, and the
privacy policy and the Play Data safety answers would both have to change. Play Asset Delivery
downloads through the Play Store and needs none of that. It would also add a downloader, integrity
checks, resumption and a failure surface. Releases are already cheap: release-please cuts them from
commits. So decoupling data from releases buys little.

**Apply OSM diffs on the device, or ship tile-level deltas.** This would give the smallest possible
transfers, at the cost of a second data pipeline running on the phone or a delta format of our own.
With a format that meets the patching requirement, Play's deltas get most of that saving for free.

**One pack for all ranges.** Simpler to build. But every user downloads the Sudety to hike the
Bieszczady, every refresh touches everyone's one file, and rich data for every range together can
pass the 1.5 GB per-pack limit.

**Install-time asset packs.** The data would be there on first launch. But every install carries
every range, and the store download grows with each range added.

## Consequences

- Data refreshes are tied to releases. A trail closed in OSM today reaches users with the next
  release, not the next day. For a hiking app, a refresh per release plus one before each season is
  enough. If it turns out not to be, that is a new ADR superseding this one.
- **The data-update path exists only through Play.** `installDebug` and plain `adb install` deliver no
  asset packs, and a build installed that way has to show "no regions downloaded", not crash.
  `bundletool --local-testing` covers the first download only: it does not support updates or
  wait-for-Wi-Fi. A refresh, the "update in progress" state and the delta size are proven only by a
  build Play delivers, through internal app sharing or a closed track. An internal-track install
  doesn't update in place ([`RELEASING.md`](../RELEASING.md)), so it can't stand in for a refresh.
- **The Play Asset Delivery library adds `FOREGROUND_SERVICE_DATA_SYNC`** for its extraction service.
  It gets a row in the `aab-permissions.py` allowlist and an answer in the Play Console's
  foreground-service declaration, in the commit that adds the library.
- **A region pack must stay under 1.5 GB compressed.** `build-map-data.py` fails the build when one
  doesn't, and the region is split in two. The 30 GB total is not a practical limit.
- During an app update Play invalidates the downloaded packs and patches them after the binary. The
  app can open in between, so it has an "update in progress" state for map data, not a crash.
- Every release costs one ~2 GB download from Geofabrik plus the diffs up to the pin, and some
  minutes of runner time. A pin whose extract or diffs Geofabrik no longer keeps can't be rebuilt,
  and the build fails rather than build different data.
- The deterministic-output rule is load-bearing and invisible: a stray timestamp in a file header
  turns every refresh into a full download for every user, and still works. The first data refresh is
  read on the phone, through one of the Play paths above. Compare the update size Play reports with
  the size of the packs. An unchanged region costing anything means the build isn't deterministic; a
  changed one costing close to its full size means the format doesn't patch well.
- The app's own data (tracks, photos, notes) stays in its own database. Region files are read-only
  inputs, replaced as a whole by Play, and never written to by the app.

## Amendment — 2026-10-19: PMTiles, its tiles uncompressed

Phase 1 picks the format left above to the map phase ([`phase-1.md`](../phase-1.md) § 2 and § 4):
one PMTiles archive per Region, built by planetiler from the Region's cut, **with its tiles stored
uncompressed** (`--tile_compression=none`). The requirement above stands; this narrows what meets
it, and strikes the Decision's "as does any layout whose compression restarts per area". Tiles
compressed one by one do not patch well, because a binary diff cannot see through compressed bytes.
The basis is the 2026-10-18 research spike that `phase-1.md` § 0 describes, a scratch run: a week of
edits cost a patch of about 44 % of the file with each tile gzipped, and under 1 % with the same
tiles uncompressed. No reading here rebuilds the gzipped variant; R6 and R7 measure only the
uncompressed build.

The proxy for Play's delta is bsdiff between two builds, read against the newer archive. A week of
churn, pins 4935 to 4942, patches in 3,824 bytes: 0.36 % of the 1,051,229-byte Tatry archive raw,
0.61 % of it zstd-compressed (R6). One Trail recoloured patches in 1,600 bytes: 0.15 % and 0.26 %
(R7). R6 is far under the 5 % that `phase-1.md` § 4 set, so the format stands; R7, which has no
bar, is smaller still. The proxy is necessary, not sufficient: Play's algorithm for packs is not
documented, and the reading that counts is the phase's checkpoint E.

**The format still waits on the gate.** Whether MapLibre draws the archive with every network
permission stripped (R1, § 1) has not yet been read on the phone; the reading is a
[`DOD.md`](../DOD.md) item, now due before phase 2 builds on MapLibre. If it does not draw, the
format becomes Mapsforge's `.map`, and R6 and R7 are read again for it.

**The CI data build arrives with E.** Until then `build-map-data.py` runs locally and writes the
Region's file into its pack module, where it stays git-ignored. The decision above, a job in
`publish-play.yml` before `bundleRelease`, holds from the first release that carries a pack.

## Amendment — 2026-10-19: the map phase is split in two

The Decision's "the list of regions and the filter are fixed when the map phase opens" now reads per
half. The map phase was split ([`PLAN.md`](../PLAN.md), [`phase-2.md`](../phase-2.md)): phase 2
draws the map against the Tatry and fixes the filter when it opens; phase 2b builds every Region and
fixes the list of Regions, with each Region's polygon, when it opens. Phase 2b also takes over phase
1's checkpoint E, so the CI data build above arrives with 2b.

## Amendment — 2026-10-19: the profile is the filter

The Decision's "`osmium tags-filter` drops only what nothing in the app reads" is not built as a
step. planetiler emits only what the profile names, so a `tags-filter` list would be a second copy of
the profile's, kept in step by hand. The cut goes straight to planetiler, and the layer table in
[`phase-2.md`](../phase-2.md) § 3, which `scripts/map/KozicaProfile.java` implements, is the filter.
What reaches a Region is still what the Decision names: Trails, Huts, peaks and Shelters, roads,
villages, water and land cover. A thing the app starts to read joins the profile and the table, not
a filter.

## Amendment — 2026-10-20: nine Regions, across the border

Phase 2b fixes the list of Regions ([`phase-2b.md`](../phase-2b.md) § 4): Sudety Zachodnie,
Środkowe and Wschodnie, Beskidy Zachodnie, Gorce i Pieniny, Tatry, Beskid Niski, Bieszczady and Góry
Świętokrzyskie, each a coarse polygon that overlaps its neighbours by a few kilometres.

**The Context's "the app needs only part of the input" no longer means part of Poland.** The ranges
cross the state border, and half the Tatry's Trails are Slovak, so Regions cross it too. The build
takes Geofabrik's Slovakia and Czechia extracts beside Poland's, brings each to its own replication
sequence, and joins them with `osmium merge` before the cut. Geofabrik numbers each extract on its
own, so the pin becomes one sequence per extract, and the build fails unless the three updated
headers carry one replication timestamp: a pin is still one moment, and that moment is the data's
date. A release build downloads about 3.4 GB rather than 2.1. Ukraine's side of the Bieszczady and
Germany's of the Góry Izerskie stay out, for what they would add against their size.

**The ODbL offer is the database.** `publish-play.yml` attaches every Region's archive to the
release-please GitHub release, under ODbL, and the licences screen links to it. The Decision's "it
is the script offered under ODbL" is struck: `build-map-data.py` stays readable in the public
repository, but the offer is the derivative database itself, which leaves the code's licence alone.

## Amendment — 2026-10-22: every Region is a fast-follow pack

The Decision's "each region is an on-demand Play asset pack … the user picks the ranges they hike"
is struck. **Every Region is a fast-follow pack** ([`phase-2c.md`](../phase-2c.md)): Play fetches
all of them right after the install, without the app being opened, and the app fetches any that
has not arrived when the map first opens. The user picks nothing, and the Regions screen and the
first launch's choice are gone. Decided with the owner while phase 4 was planned, when relief for
every Region would have added about 200 MB to that choice: shipping everything to everyone was
preferred to a choice of ranges.

The Alternatives' **"One pack for all ranges"** still loses: the packs stay one per Region, so a
refresh still costs only the Regions it changed. **"Install-time asset packs"** still loses too,
for a reason other than its own: the store listing would show every byte, and Android wants twice
that free to install or update. Fast-follow keeps the listing at the app's size.

What this costs: every install downloads every Region, about 275 MB before phase 4's relief, and
every refresh reaches every user's changed packs, not only the Regions they had chosen. The
Consequences' "update in progress" state, the data path existing only through Play, and the 1.5 GB
per-pack limit stand unchanged.

## Amendment — 2026-10-25: a release whose inputs are unchanged reuses the last one's archives

"Nothing is cached between builds" is narrowed. Three releases in a row (0.5.0 to 0.7.0) rebuilt
byte-identical archives, each spending about 40 of its 44 minutes on the data. `publish-play.yml`
now attaches `MAP-DATA-INPUTS.txt` beside the archives: the sha256 of `build-map-data.py`, the pin,
the profile and every Region's polygon, the osmium, pyosmium and JDK versions, and each archive's
sha256. A release whose inputs read the same as the newest version's record downloads that
release's archives, checks them against it, and skips the build (`scripts/map-archives.py`). Any
difference, a missing record or a failed download falls through to the full build. Decided with
the owner.

The Alternatives' reason against a cache does not apply: a published release's assets are readable
from any ref. What a reused release gives up is its own determinism check; the build its archives
came from proved the Tatry's bytes repeat, from the same inputs.
