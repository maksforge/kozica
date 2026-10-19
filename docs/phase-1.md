# Phase 1 — The Tatry reach the phone as a file the app can find, read and draw

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds on [ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md) and owns the
one decision that ADR leaves to "the map phase" for the file format. It works inside
[ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md), which is the hardest constraint here.
`CONTEXT.md` is owed nothing: **Region** already names what a pack holds, and no new term reaches the
screen.

**What closing this phase means.** Today the app holds the map's place with a placeholder and carries
no map data. After this phase `scripts/build-map-data.py` turns a pinned Geofabrik extract into one
Region, the Tatry, as a PMTiles archive. A debug build draws that file with MapLibre on the phone while
declaring none of the network permissions. The app asks for the Tatry as an on-demand asset pack, and
reads the pack once it is on the phone. The difference a device can be asked about: a debug build
shows the Tatry's state — not downloaded, downloading, ready with its OSM date — and draws the Region
with `INTERNET`, `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` absent from its merged manifest.

**`PLAN.md`'s paragraph is wrong in two places, and both were decided before this file was written.**

- **The gate it names is split off.** `PLAN.md` puts the gate on what an update costs "measured through
  a build Play delivers". Play-side work is deferred for now: only the local build has to work. So, by
  `PLAN.md` rule 2, the phase splits along that line. This phase measures what it can locally — a
  binary diff between two builds as a proxy for Play's patch — and the Play-delivered reading becomes
  checkpoint **E**, taken when Play work resumes and before any release carries a pack.
- **"Nothing has to be drawn yet" no longer holds.** The format cannot be judged apart from the
  renderer that reads it. The chosen pair, MapLibre Native with PMTiles, is only sound if MapLibre runs
  with every network permission stripped. That is checked by drawing, so the gate draws.

---

## 0. What this phase inherits

- **ADR-0007's pipeline, unbuilt.** The pin, the dated-extract-plus-diffs update, the per-Region cut,
  the deterministic-output rule and the on-demand pack are all decided. None of it exists as code.
- **ADR-0008 and its allowlist.** `scripts/aab-permissions.py` FORBIDDEN holds `INTERNET`,
  `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE`. MapLibre Native's AAR declares all three, plus coarse
  and fine location.
- **A finding that amends ADR-0007.** ADR-0007 says a layout "whose compression restarts per area"
  patches well. A research spike on 2026-10-18 measured otherwise, using bsdiff as the proxy for Play's
  patch on a Tatry-sized area. With each tile gzipped, one week of real OSM edits cost a patch of about
  44 % of the file. With the same tiles stored uncompressed, it cost under 1 %. Gzip inside a file is
  opaque to a binary diff. Those numbers came from a scratch run and are re-taken here as readings
  (R6, R7). They are not cited as settled.
- **Play does not document how it patches asset packs.** APKs are patched file by file with bsdiff on
  uncompressed bytes. For packs the documentation says only "delta patching". That is why checkpoint E
  exists at all.
- **One phone, reached over wireless adb** through Tailscale. The owner often uses it to talk to the
  session, so a reading that drives the screen waits until the phone is free.

## What is in, and what is deliberately not

**In:** `scripts/build-map-data.py`, from pin to PMTiles, for the Tatry only and run locally. The
planetiler profile it runs, deliberately thin. A debug-only map screen that draws the archive with
MapLibre, with the network permissions stripped. An on-demand asset pack for the Tatry, the Play Asset
Delivery library, and the code in `main/` that asks for the pack and reads its state. Local readings of
determinism, patch size and pack delivery through `bundletool --local-testing`.

**Not in, and each with a reason rather than a phase:**

- **A map in the release build.** Phase 2 draws the map for users. Here MapLibre is a
  `debugImplementation` dependency: the release artifact, its allowlist and its licences stay as they
  are until phase 2 decides what the user sees.
- **Every Region, and the filter.** ADR-0007 gives the list of Regions and the filter to phase 2.
  Building one Region is enough to prove the pipeline; building nine would only multiply the download.
- **The full tile schema.** Phase 2 owns the layers and attributes the map needs. This phase owns the
  rules every schema must obey: no compression inside the file, deterministic output, one archive per
  Region. Its profile carries only what the gate draws and the patch readings exercise.
- **The data build in CI, and anything Play delivers.** ADR-0007 puts the release data build in
  `publish-play.yml`. That belongs with checkpoint E, the first time a pack is meant to reach Play.
  Until then the script runs on the developer's machine, which ADR-0007 forbids only for *release*
  data.
- **A download picker.** Phase 2 owns the picker and "Download all". Here a debug row asks for the
  Tatry; nothing in the release build asks for a pack.

**The tempting one** is maplibre-compose, a native Compose API for MapLibre. It needs Kotlin 2.4.20's
standard library against the 2.3.20 this repo pins, and its last two releases each broke the API. The
gate uses `AndroidView` around `MapView`. Phase 2 may revisit it on a Kotlin bump of its own.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — MapLibre draws a local Tatry PMTiles with every network permission stripped | `chore:` (debug only) | — |
| **B** | **The build** — `build-map-data.py` from pin to a deterministic, uncompressed PMTiles | `chore:` | A |
| **C** | **The pack** — the Tatry as an on-demand asset pack; `main/` asks for it and reads its state, with WorkManager's network permission stripped | `chore:` | B |
| **D** | **The proxy** — patch size between two pins, measured locally | readings only | B |
| **E** | **Play delivers it** — the data build in CI, an update through Play, its real size | `fix:` or `feat:`, later | C, D, Play work resuming |

**A goes first, and builds its own archive by hand.** The gate's archive comes from one planetiler
command run by hand, not from `build-map-data.py`. A gate taken after B would answer whether to build
B a week after B was written.

**None of A–D cuts a release.** Each is debug-only or tooling. The release build gains the Play Asset
Delivery library in C, but nothing a user can reach. That is deliberate: a release that carries a
pack is E's to make.

**E is a checkpoint of this phase that closes later.** Phase 1 closes on A–D. E gets a `DOD.md` item
so it cannot be lost, due before the first release that carries a pack.

---

## 1. The gate: does MapLibre draw offline with every network permission stripped?

### The question, in the form that can be measured

Does a debug build that removes `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` from the merged manifest with
`tools:node="remove"`, and calls `MapLibre.setConnected(false)` before any `MapView` exists, draw a
Tatry PMTiles archive from the app's own files directory? The style, glyphs and sprites come from
`asset://`. The test is no crash, no `SecurityException` in logcat, lines drawn from the archive, and
one text label drawn from local glyphs. Trail colours are not part of it: a Trail's colour lives on a
route relation, which only B's profile reads, and a styling miss must never be what switches the
renderer.

The question is not settled by reading MapLibre's source. That reading says `MapView` calls
`MapLibre.isConnected()` during setup, which reaches `ConnectivityManager` and throws without
`ACCESS_NETWORK_STATE` unless `setConnected(false)` short-circuits it. The phone decides.

### The apparatus

All of it under `app/src/debug/`, per the house rule:

- `debugImplementation` of MapLibre Native Android, pinned in `gradle/libs.versions.toml`.
- The debug manifest removes the five permissions above.
- `DebugMapScreen`, reached from Settings' debug section, is an `AndroidView` around `MapView`. It
  calls `setConnected(false)` first and loads `pmtiles://file://<filesDir>/regions/tatry.pmtiles`
  with a minimal style from debug assets: water and ways as plain lines, and peak names. "© OpenStreetMap contributors" sits on it, linked as the house rule requires, even in
  debug.
- The archive is built once by hand with planetiler `--tile_compression=none` from a Tatry cut. It
  reaches the app with `adb shell run-as <debug id> sh -c 'cat > files/regions/tatry.pmtiles' <
  tatry.pmtiles`, and is never committed.

### The verdicts, decided now rather than argued about later

1. **It draws, nothing is thrown, and the merged manifest declares none of the five.** → MapLibre and
   PMTiles stand. B builds PMTiles.
2. **It crashes or throws for want of a permission, and no public API avoids it.** → MapLibre is out.
   The phase switches to Mapsforge/VTM and `.map`, rewrites this file's B–D for that format, and
   records why in ADR-0007's amendment. A fork or patch of MapLibre is not a fallback: it would be a
   renderer to maintain.
3. **It draws, but only with one permission kept.** → Not accepted as a pass. ADR-0008 allows no
   network permission. This is verdict 2 unless the permission is one of the two location ones, which
   phase 2 owns anyway; then it is verdict 1 with that permission noted for phase 2.

### The verdict, taken *(date)*

*(Filled in when the phone is free.)*

## 2. The build: `scripts/build-map-data.py`

One script, from pin to archive, deterministic. ADR-0007 decides the steps; this section decides the
tools and the format.

- **The pin** is `scripts/osm-data.pin`, the Geofabrik replication sequence number.
- **The extract** is the newest dated `poland-YYMMDD.osm.pbf` from before the pin, brought to the pin
  by `pyosmium-up-to-date --end-id`. The script checks the sequence in the PBF header against the pin
  and fails on a mismatch.
- **The cut** is `osmium extract` with `scripts/regions/tatry.poly`, a polygon drawn along the range.
  Whether a Region's polygon follows the state border exactly is phase 2's decision with the rest of
  the Region list.
- **Route relations become way attributes before tiling.** Trails are `route=hiking` relations, and a
  Trail's colour lives in the relation's `osmc:symbol` or `colour`. planetiler's YAML profiles cannot
  read relation membership, so the profile is Java: `scripts/map/KozicaProfile.java`, run with JDK 21's
  single-file launcher against a pinned, checksummed planetiler jar
  (`java -cp planetiler.jar scripts/map/KozicaProfile.java …`). No Gradle project for it. A way shared
  by several Trails carries the list of their colours, sorted, so phase 2 can offset them.
- **The archive** is PMTiles v3, written by planetiler with `--tile_compression=none`. Not
  `pmtiles convert`: it always gzips vector tiles.
- **Determinism is checked, not assumed.** `--check-determinism` builds twice and compares the two
  archives byte for byte; CI will run it in E.
- **The size limit is enforced.** The script fails when an archive passes 1.5 GB, per ADR-0007.

The script and the profile are what ODbL §4.6 asks to be offered from the first release that conveys
the data. That release is E's, so the offer is too.

## 3. The pack

- **An on-demand asset pack module, `region_tatry`**, whose assets directory `build-map-data.py`
  writes into. Pack names allow letters, digits and underscores only, and the directory name is the
  pack name. The pack joins the build only when its archive exists, so `assembleDebug`, CI and a
  checkout without data keep building. E makes its absence a failure in `publish-play.yml`.
- **The Play Asset Delivery library** (`com.google.android.play:asset-delivery-ktx` 2.3.0) in
  `main/`. **It brings WorkManager back.** `asset-delivery` depends on `androidx.work:work-runtime`
  2.9.1 for its `ExtractionWorker`, and WorkManager merges `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
  `FOREGROUND_SERVICE` and `ACCESS_NETWORK_STATE`, which is FORBIDDEN. The library itself adds
  `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`. Read from the AARs' manifests on
  2026-10-19.
- **`ACCESS_NETWORK_STATE` is stripped with `tools:node="remove"` in `main/`'s manifest.** WorkManager
  reads network state only to enforce a network constraint, and whether `ExtractionWorker` sets one is
  not visible from its bytecode. The verdicts, decided now:
  1. **A pack downloads and extracts through `--local-testing`, R8 holds, and logcat shows no
     `SecurityException`.** → The strip stands; E re-checks it through Play, where extraction runs
     for real.
  2. **Extraction fails for want of the permission.** → No workaround in code. ADR-0008 and ADR-0007
     conflict, and the owner chooses between a narrow ADR-0008 amendment that allows
     `ACCESS_NETWORK_STATE` alone, since it opens no connection and changes no Data safety answer, and
     a different delivery for map data. The phase stops at C until then.
- **The other four rows go into EXPECTED**, in C's commit, each naming the asset-delivery library or
  WorkManager as its source: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`. Phase 0 removed the last three with WorkManager. They return because the
  only way to receive a pack without `INTERNET` needs them.
- **`data/regions/RegionFiles.kt`** asks `AssetPackManager` for a Region's state and path, and exposes
  it as a `Flow` of: not downloaded, downloading (with progress), update in progress, ready (path and
  OSM date), failed. The OSM date is read from the archive's metadata, which the profile writes from
  the PBF header's replication timestamp, so the file carries its own date.
- **The debug section** shows the Tatry's state and offers "Download Tatry" and "Remove Tatry". Debug
  strings, `translatable="false"`. `DebugMapScreen` reads the path from `RegionFiles` once C lands,
  in place of the hand-pushed file.
- **A build installed by `installDebug` has no pack** and shows "not downloaded", never a crash
  (ADR-0007). `bundletool build-apks --local-testing` then `install-apks` is the local path for the
  first download.

## 4. The proxy

Two builds from two pins a week apart, the same profile, then `bsdiff` from the older archive to the
newer. The patch size is read against the newer archive, raw and zstd-compressed. A one-feature edit is
measured the same way, from a pin and a copy of its extract with one Trail's colour changed. Both are
readings, R6 and R7. The command is a flag on `build-map-data.py` so it can be re-run at any pin.

**What the proxy cannot say.** Play's algorithm for packs is not documented. A small bsdiff is
necessary, not sufficient. E is the reading that counts.

## 5. Play delivers it (later)

What E does when Play work resumes, written now so it is not re-derived:

- **The data build moves into `publish-play.yml`**, before `bundleRelease`, as ADR-0007 decides. A
  release without the Tatry archive fails there.
- **Two builds go through internal app sharing**, differing by one pin. The update size Play reports
  is read against R6. An internal-track install does not update in place, so it cannot stand in
  (`RELEASING.md`).
- **The ODbL offer ships with it**: the script and profile, linked from the licences screen.
- **`aab-locale.py`'s open failure on `schema_mismatch_continue`** is still standing then; it blocks
  any upload, pack or not.
- **C's permissions reach the next release even without a pack.** C lands in `main/` as a `chore:`, so
  any release cut for another reason ships the asset-delivery library with its `dataSync` foreground
  service. That release needs the Console's foreground-service declaration and a
  `play-app-content.md` § *Foreground service permissions*, which phase 0 deleted.

---

## Tests

None. This phase computes no safety value (`PLAN.md` rule 3). Determinism and the size limit are
build-time assertions inside `build-map-data.py`, not unit tests.

## Documents this phase amends

- **ADR-0007**: a dated amendment, written when R6 is taken. Files store their tiles uncompressed,
  because compression inside a file defeats the delta. The format is PMTiles built by planetiler, or
  `.map` if the gate goes the other way. The CI data build joins with E.
- **`scripts/aab-permissions.py`**: EXPECTED gains the four rows in § 3, in C's commit; FORBIDDEN is
  unchanged.
- **`CLAUDE.md`**: the stack table gains MapLibre (debug only, for now) and Play Asset Delivery; the
  layout gains `data/regions/`, `scripts/map/` and `scripts/regions/`.
- **`.gitignore`**: Region archives, the downloaded extract and the planetiler jar.

## Known gaps, deliberately

- **No user sees any of it.** Everything a user could reach waits for phase 2's map and E's release.
- **The proxy is a proxy.** Until E, the format's patch behaviour through Play is a prediction backed
  by bsdiff, not a reading.
- **The debug build carries ~13 MB of native code per ABI** from MapLibre. Phase 2 decides whether
  the release build carries it. If the gate passes, it will.
- **ODbL's offer waits for E.** Nothing built here is conveyed to anyone until a release carries a
  pack.

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device or the build, with the command that
read it. A reading that drives the phone waits until the owner says it is free.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate: the debug build draws the Tatry archive with all five permissions stripped, nothing thrown | `./gradlew installDebug`, push the archive with `run-as`, open the debug map; `adb logcat -d \| grep -E 'SecurityException\|FATAL'` | |
| R2 | The merged debug manifest declares none of the five | `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` | |
| R3 | The release bundle declares none of the FORBIDDEN permissions with the asset-delivery library and WorkManager in, and exactly the four new EXPECTED rows | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | |
| R4 | Two builds at one pin are byte-identical | `python3 scripts/build-map-data.py --check-determinism` | |
| R5 | The Tatry archive's size, raw and zstd | `ls -l`, `zstd -19 -c … \| wc -c` | |
| R6 | The patch between two pins a week apart, against the newer archive | `python3 scripts/build-map-data.py --patch-from <older pin>` | |
| R7 | The patch for a one-Trail colour edit | the same, against the edited extract | |
| R8 | The first download through `bundletool --local-testing`: the app reports the Tatry ready with its OSM date, and the debug map draws from the pack's path | `bundletool build-apks --local-testing … && bundletool install-apks …` | |
| R9 | `installDebug` with no pack reports "not downloaded" and does not crash | `./gradlew installDebug`, open Settings' debug section | |
| R10 | The debug map draws Trails in their colours from the profile's attribute, including a way shared by two Trails | `build-map-data.py`, push, open the debug map | |
| E1 | *(checkpoint E)* The update size Play reports for a one-pin data refresh, against R6 | internal app sharing, two builds | |
