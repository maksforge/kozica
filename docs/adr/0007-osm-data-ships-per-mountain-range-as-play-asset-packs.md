# OSM data ships per mountain range as Play asset packs, built from a Geofabrik extract kept current by diffs

## Context

The trail data comes from OpenStreetMap, built by us from Geofabrik and never from the public tile
server or Overpass (house rule in [`CLAUDE.md`](../../CLAUDE.md)). Geofabrik's Poland extract,
`poland-latest.osm.pbf`, is **2.0 GB**. That raised the question of what a data refresh costs:
re-downloading the 2 GB file on the build machine, re-uploading it to Play, and users downloading it
again.

None of those has to happen, because the 2 GB file is only the input and the data changes slowly.
Geofabrik publishes a daily change file (`.osc.gz`) for the same extract. The last seven, read on
2026-10-18 from `download.geofabrik.de/europe/poland-updates/`, were **2.6–3.4 MB each for the whole
country**. And the app needs only part of the input: the mountain ranges, not the whole country.

Size on the phone is not what limits how much of that data the app carries. Play allows **1.5 GB per
asset pack and 30 GB across all on-demand packs** (Google Play's size limits, read on 2026-10-18), and
hosting there is free. The limit is how useful a feature is to someone on a trail.

Geofabrik's smaller cuts are voivodeships (Małopolskie 193 MB, Podkarpackie 147 MB, Śląskie 177 MB,
Dolnośląskie 173 MB). Those don't fit either, because the ranges cross them: the Beskidy alone span
three voivodeships.

There is no server and there will not be one ([ADR-0005](0005-backup-is-automatic-for-the-database-and-manual-for-media.md)),
so whatever the phone downloads has to come from somewhere that already exists.

## Decision

**The build machine keeps one copy of the Poland extract and brings it up to date with the daily
diffs.** The 2 GB file is downloaded once. After that, `pyosmium-up-to-date` applies Geofabrik's
change files to it, and the replication state it leaves behind records the exact OSM timestamp the
data was built from.

**Regions follow mountain ranges, not administrative borders.** Each region is a boundary polygon
committed under `scripts/`. A region is cut from the national extract with `osmium extract`.
**Inside a region the data stays rich**: trails, huts, peaks and shelters, but also roads, villages,
water, land cover, contours and anything else the map or the app can use. `osmium tags-filter` drops
only what nothing in the app reads. The list of regions and the filter are fixed when the map phase
opens. The format of the output files is also that phase's
decision. This ADR covers how the data is built and delivered, not what the files contain.

**One script builds everything, deterministically.** `scripts/build-map-data.py` runs update, cut,
filter and package from start to finish. It is the script offered under ODbL from the first release.
Its output depends only on its input: features in a stable order, no build timestamps, fixed
compression settings. A region that saw no edits between two builds therefore produces a
byte-identical file. That is what keeps updates small.

**Each region is an on-demand Play asset pack.** The base install carries no map data. The user picks
the ranges they hike, at home on Wi-Fi, and Play downloads those packs. A refresh rides an ordinary
app release. Play delivers asset-pack updates as delta patches, so a refresh costs what changed, and a
byte-identical region costs nothing.

**The data date is visible.** The app shows the OSM timestamp from the build, e.g. "OSM data:
2026-10-17", next to the "© OpenStreetMap contributors" credit on every map screen. A user can see
how fresh the trails are without reading a changelog.

**A data-only refresh is a `fix:` commit**, e.g. `fix: refresh trail data to 2026-10-17`.
release-please cuts no release for `chore:`, so a refresh committed under that type would never reach
anyone.

## Alternatives

**Download a fresh 2 GB extract for every build.** It works, but each build moves 2 GB to pick up a
few days of 3 MB changes, and it loses the replication state that dates the data exactly.

**Ship the voivodeship extracts, or whole Poland.** Size alone would allow it. But the user would
pay for lowland cities they never hike in, and the ranges would still be split across files.

**Host the region files ourselves (GitHub Releases, a bucket) and download them in the app.** This
would refresh data without an app release. But it adds a downloader, integrity checks, resumption
and a failure surface. It also adds hosting that is a server in all but name, which ADR-0005 rules
out. Releases are already cheap: release-please cuts them from commits. So decoupling data from
releases buys little.

**Apply OSM diffs on the device, or ship tile-level deltas.** This would give the smallest possible
transfers, at the cost of a second data pipeline running on the phone or a delta format of our own.
Play's patching of unchanged and changed files already gets most of that saving for free.

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
  asset packs. Local testing goes through `bundletool` with `--local-testing`, and a build installed
  any other way has to show "no regions downloaded", not crash.
- **A region pack must stay under 1.5 GB compressed.** `build-map-data.py` fails the build when one
  doesn't, and the region is split in two. The 30 GB total is not a practical limit.
- During an app update Play invalidates the downloaded packs and patches them after the binary. The
  app can open in between, so it has an "update in progress" state for map data, not a crash.
- The build machine keeps a ~2 GB working file and its replication state between builds. Losing them
  costs one fresh download and nothing else.
- The deterministic-output rule is load-bearing and invisible: a stray timestamp in a file header
  turns every refresh into a full download for every user, and still works. The first data refresh is
  read on the phone. Compare the update size Play reports with the size of the packs. A refresh
  costing the full size means the build isn't deterministic.
- The app's own data (tracks, photos, notes) stays in its own database. Region files are read-only
  inputs, replaced as a whole by Play, and never written to by the app.
