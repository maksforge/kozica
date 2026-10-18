# OSM data ships per mountain range as Play asset packs, built from a Geofabrik extract kept current by diffs

## Context

The trail data comes from OpenStreetMap, built by us from Geofabrik and never from the public tile
server or Overpass (house rule in [`CLAUDE.md`](../../CLAUDE.md)). Geofabrik's Poland extract,
`poland-latest.osm.pbf`, is **2.0 GB**. That raised the question of what a data refresh costs:
re-downloading the 2 GB file for every build, re-uploading it to Play, and users downloading it
again.

None of those has to happen, because the 2 GB file is only the input and the data changes slowly.
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

**The extract is cached and brought forward with the daily diffs.** CI keeps the Poland extract in
`actions/cache`, keyed on its replication sequence. A build restores the newest entry and
`pyosmium-up-to-date` applies Geofabrik's change files up to the pin. When there is no entry, or the
restored one is already past the pin, the build downloads the newest dated Geofabrik extract from
before the pin and rolls it forward instead. The replication state lives in the PBF header
(`osmosis_replication_sequence_number`, `osmosis_replication_timestamp`,
`osmosis_replication_base_url`), which `pyosmium-up-to-date` reads and rewrites, so the file carries
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

**Download a fresh extract for every build instead of caching one.** It works, and the PBF header
still dates the data. The only cost is bandwidth and time: each build moves 2 GB to pick up a few days
of 3 MB changes.

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
- GitHub evicts a cache entry that goes unused for 7 days. Builds further apart than that start from
  a dated Geofabrik download, about 2 GB and some minutes of runner time, and lose nothing else. A
  pin whose extract or diffs Geofabrik no longer keeps can't be rebuilt, and the build fails rather
  than build different data.
- The deterministic-output rule is load-bearing and invisible: a stray timestamp in a file header
  turns every refresh into a full download for every user, and still works. The first data refresh is
  read on the phone, through one of the Play paths above. Compare the update size Play reports with
  the size of the packs. An unchanged region costing anything means the build isn't deterministic; a
  changed one costing close to its full size means the format doesn't patch well.
- The app's own data (tracks, photos, notes) stays in its own database. Region files are read-only
  inputs, replaced as a whole by Play, and never written to by the app.
