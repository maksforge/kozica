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

**`PLAN.md`'s paragraph changed in two places, both decided before this file was written, and is
amended with it.**

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
MapLibre, with the network permissions stripped. An on-demand asset pack for the Tatry, and the Play
Asset Delivery library with the code that asks for the pack and reads its state, both in the debug
build only. Local readings of determinism, patch size and pack delivery through
`bundletool --local-testing`.

**Not in, and each with a reason rather than a phase:**

- **A map in the release build.** Phase 2 draws the map for users. Here MapLibre is a
  `debugImplementation` dependency: the release artifact, its allowlist and its licences stay as they
  are until phase 2 decides what the user sees.
- **The asset-delivery library in the release build.** It brings WorkManager and a `dataSync`
  foreground service, and both need the Console's foreground-service declaration in the commit that
  puts them in the release (ADR-0007, `aab-permissions.py`'s own rule). That commit is E's, or phase
  2's if its map reaches a release first. Until then the release build gains nothing from this phase.
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
| **C** | **The pack** — the Tatry as an on-demand asset pack; the debug build asks for it and reads its state, with WorkManager's network permission stripped | `chore:` (debug only) | B |
| **D** | **The proxy** — patch size between two pins, measured locally | readings only | B |
| **E** | **Play delivers it** — the library in `main/` with its declaration, the data build in CI, an update through Play, its real size | `fix:` or `feat:`, later | C, D, Play work resuming |

**A goes first, and builds its own archive by hand.** The gate's archive comes from one planetiler
command run by hand, not from `build-map-data.py`. A gate taken after B would answer whether to build
B a week after B was written.

**None of A–D cuts a release.** Each is debug-only or tooling, and the release build gains nothing
from any of them. That is deliberate: a release that carries a pack, or the library that asks for
one, is E's to make.

**E is a checkpoint of this phase that closes later.** Phase 1 closes on A–D. E gets a `DOD.md` item
so it cannot be lost, due before the first release that carries a pack or the library.

---

## 1. The gate: does MapLibre draw offline with every network permission stripped?

### The question, in the form that can be measured

Does a debug build that removes `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` from the merged manifest with
`tools:node="remove"`, and calls `MapLibre.setConnected(false)` before any `MapView` exists, draw a
Tatry PMTiles archive from the app's own files directory? The style, glyphs and sprites come from
`asset://`. The test is no crash, no `SecurityException` in logcat, and a screencap showing lines
drawn from the archive and one text label drawn from local glyphs. Trail colours are not part of it:
a Trail's colour lives on a route relation, which only B's profile reads, and a styling miss must
never be what switches the renderer.

The question is not settled by reading MapLibre's source. That reading says `MapView` calls
`MapLibre.isConnected()` during setup, which reaches `ConnectivityManager` and throws without
`ACCESS_NETWORK_STATE` unless `setConnected(false)` short-circuits it. The phone decides.

### The apparatus

All of it under `app/src/debug/`, per the house rule:

- `debugImplementation` of MapLibre Native Android, pinned in `gradle/libs.versions.toml`. The
  renderer is OpenGL ES (`android-sdk-opengl`): the default `android-sdk` is the Vulkan build, and
  its `uses-feature android.hardware.vulkan.version required="true"` would become a Play device filter.
- The debug manifest removes the five permissions above.
- `DebugMapScreen`, reached from Settings' debug section, is an `AndroidView` around `MapView`. It
  calls `setConnected(false)` first and loads `pmtiles://file://<filesDir>/regions/tatry.pmtiles`
  with a minimal style from debug assets: water and ways as plain lines, and peak names. "© OpenStreetMap contributors" sits on it, linked as the house rule requires, even in
  debug.
- The archive is built once by hand with planetiler `--tile_compression=none` from a Tatry cut. It
  reaches the app with `adb shell run-as <debug id> sh -c 'cat > files/regions/tatry.pmtiles' <
  tatry.pmtiles`, and is never committed.

### The verdicts, decided now rather than argued about later

1. **The screencap shows lines and the label, nothing is thrown, and the merged manifest declares
   none of the five.** → MapLibre and PMTiles stand. B builds PMTiles.
2. **It crashes or throws for want of a permission, and no public API avoids it.** → MapLibre is out.
   The phase switches to Mapsforge/VTM and `.map`, rewrites this file's B–D for that format, and
   records why in ADR-0007's amendment. A fork or patch of MapLibre is not a fallback: it would be a
   renderer to maintain.
3. **It draws, but only with one permission kept.** → Not accepted as a pass. ADR-0008 allows no
   network permission. This is verdict 2 unless the permission is one of the two location ones, which
   phase 2 owns anyway; then it is verdict 1 with that permission noted for phase 2.
4. **Nothing is thrown, but nothing is drawn.** → Not a pass, and not yet any verdict. Find why first:
   the style, the archive's path, the `pmtiles://file://` scheme. Only a cause that needs a network
   permission makes it verdict 2.

### The verdict, taken 2026-10-19

**Verdict 1.** On the phone (Xiaomi 24115RA8EG, API 36), the debug build drew the Tatry archive offline
with all five permissions stripped (R1). It drew lines, water, Trails in their colours and peak
names with Polish characters. MapLibre logged `connected - false`, and nothing threw. MapLibre and
PMTiles stand.

The readings found one fault no verdict anticipated: R9 crashed. With no pack, a failed state
request reached `asset-delivery-ktx` 2.3.0's failure callback, which threw `NoSuchMethodError`
on the main thread. That library is built against coroutines 1.5. The debug build now uses the
plain `asset-delivery` artifact, with its own `Task.await()` and `callbackFlow`, and R9 passes on
that build.

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
- **The debug style gains one rule**: Trail lines coloured from the profile's colour attribute, the
  first of a shared way's list, so R10 can be read off the screen. Drawing a shared way's other
  colours stays phase 2's. R10 reads them from the tile instead, with `scripts/map/decode-tile.py`: a
  few lines that print one tile's features and attributes, cheap because the tiles are uncompressed.
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
  pack name. **The pack joins the build only when `-PmapPacks` (or `=true`) is passed**, never
  because its archive is on disk. `assetPacks` is set on the app extension, not per build type (AGP 9.0.1's
  DSL), so a rule keyed on the file would put OSM-derived data into any local `bundleRelease` made
  on a machine that holds the archive, with no ODbL offer. In this phase only R8's local-testing
  debug bundle passes it. Without it `assembleDebug`, CI and a checkout without data keep building.
  E makes `publish-play.yml` pass it, and fail when the archive is missing.
- **The Play Asset Delivery library** (`com.google.android.play:asset-delivery` 2.3.0, not its
  `-ktx`
  wrapper, which crashes under this app's coroutines; § 1's verdict) as a `debugImplementation`. **It brings WorkManager back, in the debug build only.** `asset-delivery`
  depends on `androidx.work:work-runtime` 2.9.1 for its `ExtractionWorker`, and WorkManager merges
  `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` and `ACCESS_NETWORK_STATE`, which is
  FORBIDDEN. The library itself adds `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`. Read
  from the AARs' manifests on 2026-10-19. Nothing of ours schedules anything, and the release artifact
  carries none of it, so the documents that say so stay true until E.
- **`ACCESS_NETWORK_STATE` stays stripped.** A's debug manifest already removes it with
  `tools:node="remove"`, and WorkManager's copy merges into the same removal. WorkManager reads
  network state only to enforce a network constraint, and whether `ExtractionWorker` sets one is not
  visible from its bytecode. The verdicts, decided now:
  1. **A pack downloads and extracts through `--local-testing`, R8 shows `ExtractionWorker` ran, and
     logcat shows no `SecurityException`.** → The strip stands; E re-checks it through Play, where
     extraction runs for real.
  2. **Extraction fails for want of the permission.** → No workaround in code. ADR-0008 and ADR-0007
     conflict, and the owner chooses between a narrow ADR-0008 amendment that allows
     `ACCESS_NETWORK_STATE` alone, since it opens no connection and changes no Data safety answer, and
     a different delivery for map data. The phase stops at C until then.
  3. **The pack arrives, but `ExtractionWorker` never ran under `--local-testing`.** → Undecided, and
     the strip is provisional. E decides it through Play.

  **Taken 2026-10-19: verdict 3.** The pack arrived and the app reported it ready (R8), but
  `ExtractionWorker` never ran under `--local-testing`, and nothing threw. The strip stays
  provisional until E.
- **The four other rows wait for E.** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` belong in EXPECTED, each naming the library or WorkManager
  as its source. But `aab-permissions.py` reads the release artifact, and in C that artifact carries
  none of them, so rows added now would fail it as missing. Phase 0 removed the last three with
  WorkManager; they return because the only way to receive a pack without `INTERNET` needs them.
- **`data/regions/RegionFiles.kt`**, under `app/src/debug/` beside the debug map, asks
  `AssetPackManager` for a Region's state and path, and exposes it as a `Flow` of: not downloaded,
  downloading (with progress), update in progress, ready (path and OSM date), failed. The OSM date is
  read from the archive's metadata, which the profile writes from the PBF header's replication
  timestamp, so the file carries its own date.
- **The debug section** shows the Tatry's state and offers "Download Tatry" and "Remove Tatry". Debug
  strings, `translatable="false"`. `DebugMapScreen` reads the path from `RegionFiles` once C lands,
  and falls back to the hand-pushed file, so R1 can still be taken without R8.
- **A build installed by `installDebug` has no pack** and shows "not downloaded", never a crash
  (ADR-0007). `bundletool build-apks --local-testing` then `install-apks` is the local path for the
  first download.

## 4. The proxy

Two builds from two pins a week apart, the same profile, then `bsdiff` from the older archive to the
newer. The patch size is read against the newer archive, raw and zstd-compressed. A one-feature edit is
measured the same way, from a pin and a copy of its extract with one Trail's colour changed. Both are
readings, R6 and R7. The commands are flags on `build-map-data.py` so they can be re-run at any pin:

- **`--patch-from <older pin>`** builds both pins and measures the patch from the older to the newer.
- **`--patch-from-extract <pbf>`** takes a local PBF as the older side in place of a pin, skipping
  the update and the header check for that side; the newer side is the current pin.

**The edited copy**, for R7: a Trail's colour lives on its route relation, so the edit is to one
relation. `osmium getid -f opl <pinned extract> r<id> > edit.opl`, change its colour and bump its
`v` by one. Where the relation has an `osmc:symbol`, the profile reads only that tag's first field
and never `colour=`, so the first field is the one that must change; `colour=` changes with it so
the two agree (for r1020533, `black:white:black_bar` to `blue:white:blue_bar`). Then
`osmium cat edit.opl -o edit.osc` and `osmium apply-changes <pinned extract> edit.osc -o
edited.osm.pbf --output-header=<option>=<value>`, with one `--output-header` for each of
`osmosis_replication_sequence_number`, `_timestamp` and `_base_url`, each value read by
`osmium fileinfo -g header.option.<option> <pinned extract>`. Without them `apply-changes` drops the
replication header, and the older archive's metadata would then differ by more than the edit. The
relation and its id go into the reading.

### The verdicts, decided now

1. **R6's patch, one week of churn, is at most 5 % of the newer archive zstd-compressed.** → The
   format stands. ADR-0007's amendment is written with the measured number.
2. **It is above 5 %.** → First prove determinism (R4), and look in the archive for anything per-build:
   a timestamp in the metadata, an order that varies. If the archive is clean and the patch is still
   above 5 %, the layout question goes back to the owner before E.

**What the proxy cannot say.** Play's algorithm for packs is not documented. A small bsdiff is
necessary, not sufficient. E is the reading that counts.

## 5. Play delivers it (later)

What E does when Play work resumes, written now so it is not re-derived:

- **The asset-delivery library moves to `main/`**, unless phase 2 has already moved it: the
  dependency becomes `implementation`, `RegionFiles` moves to `main/`'s `data/regions/`, and the
  `ACCESS_NETWORK_STATE` strip moves to `main/`'s manifest. In the same commit, per ADR-0007 and
  `aab-permissions.py`'s rule: the Console's foreground-service declaration for `dataSync`,
  `play-app-content.md` § *Foreground service permissions* rewritten from "None declared", and only
  then the four EXPECTED rows. E2 reads the result. ADR-0007's "in the commit that adds the library"
  is read as the commit that puts it in the release.
- **The documents that say Kozica schedules nothing change with that commit**, because the release
  then carries WorkManager: `CLAUDE.md`'s "Nothing scheduled" stack row, ADR-0003's amendment ("the
  artifact no longer carries…"), and `PLAN.md`'s "Reminders and scheduled work" under *Not in this
  plan*. Each says that WorkManager is the asset-delivery library's, and that nothing of ours
  schedules anything.
- **The data build moves into `publish-play.yml`**, before `bundleRelease`, as ADR-0007 decides.
  The workflow passes `-PmapPacks`, and a release without the Tatry archive fails there.
- **Two builds go through internal app sharing**, differing by one pin. The update size Play reports
  is read against R6. An internal-track install does not update in place, so it cannot stand in
  (`RELEASING.md`).
- **The ODbL offer ships with it**: the script and profile, linked from the licences screen.
- **`aab-locale.py`'s open failure on `schema_mismatch_continue`** is still standing then; it blocks
  any upload, pack or not.

---

## Tests

None. This phase computes no safety value (`PLAN.md` rule 3). Determinism and the size limit are
build-time assertions inside `build-map-data.py`, not unit tests.

## Documents this phase amends

- **`PLAN.md`**: phase 1's paragraph and status line, amended with this file to match it.
- **ADR-0007**: a dated amendment, written when R6 takes § 4's verdict 1, with the measured number.
  Files store their tiles uncompressed, because compression inside a file defeats the delta. The
  format is PMTiles built by planetiler, or `.map` if the gate goes the other way. The CI data build
  joins with E.
- **`scripts/aab-permissions.py`**: unchanged in this phase. The four rows in § 3 land with E.
- **`CLAUDE.md`**: the stack table gains MapLibre and Play Asset Delivery, both debug only for now;
  the layout's `app/src/debug/` gains the debug map and `RegionFiles`, and `scripts/` gains
  `scripts/map/` and `scripts/regions/`.
- **`.gitignore`**: Region archives, the downloaded extract and the planetiler jar.

The documents that change when the library reaches the release are listed in § 5, with E.

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
| R1 | The gate: the debug build draws the Tatry archive with all five permissions stripped, nothing thrown; lines and the label visible | `./gradlew installDebug`, push the archive with `run-as`, open the debug map; `adb exec-out screencap -p > r1.png`; `adb logcat -d \| grep -E 'SecurityException\|FATAL\|KozicaGate\|Mbgl'` | 2026-10-19, API 36, archive `7a5e28c6…9171` pushed with `run-as`: lines, water, Trails in five colours and peak names with Polish letters (ł, ń, ó, ś) drawn, OSM credit shown; logcat `Mbgl-ConnectivityReceiver: connected - false`, no `SecurityException`, `FATAL`, `KozicaGate` error or `Mbgl` load error. **Verdict 1** |
| R2 | The merged debug manifest declares none of the five, and no required hardware feature | `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk`; `aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk \| grep -A2 uses-feature` | 2026-10-19, debug APK with `android-sdk-opengl` 13.7.1 (`libmaplibre.so` in all four ABIs): one permission, AndroidX's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; none of the five. One `uses-feature`, `android.hardware.wifi` with `required=false`; no `android.hardware.vulkan.version` |
| R3 | After C, the merged debug manifest still declares none of the five with the asset-delivery library and WorkManager in. The release bundle's reading moved to E2 | `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` | 2026-10-19, debug APK with `asset-delivery-ktx` 2.3.0 and `work-runtime` 2.9.1: none of the five. Four new, exactly § 3's: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, beside AndroidX's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. WorkManager's `InitializationProvider` and `SystemJobService` are merged. The release bundle is unchanged: `aab-permissions.py` passes |
| R4 | Two builds at one pin are byte-identical | `python3 scripts/build-map-data.py --check-determinism` | 2026-10-19, pin 4942: byte-identical, 1,051,229 bytes, sha256 `7a5e28c6…9171`. The metadata carries sequence 4942 and its time, 2026-10-18T20:25:06Z, once the cut copies the replication header: `osmium extract` drops it by default |
| R5 | The Tatry archive's size, raw and zstd | `ls -l`, `zstd -19 -c … \| wc -c` | 2026-10-19, pin 4942: 1,051,229 bytes raw, 622,694 zstd -19. Geofabrik's Poland extract stops at the state border, so `tatry.poly`'s Slovak half draws nothing; the Region's shape is phase 2's |
| R6 | The patch between two pins a week apart, against the newer archive | `python3 scripts/build-map-data.py --patch-from <older pin>` | 2026-10-19, 4935 (2026-10-11, from `poland-261001` and ten diffs) to 4942 (2026-10-18, `poland-261018`): bsdiff patch 3,824 bytes, 0.36 % of the newer archive raw, 0.61 % of it zstd. § 4's verdict 1 |
| R7 | The patch for a one-Trail colour edit, with the relation's id | the edit in § 4, then `python3 scripts/build-map-data.py --patch-from-extract edited.osm.pbf` | 2026-10-19, pin 4942 against a copy of `poland-261018` with r1020533 ("Stacja IMGW - Świnicka Przełęcz", v13 to v14) recoloured black to blue, `osmc:symbol` `black:white:black_bar` to `blue:white:blue_bar` and `colour=` with it, its replication header kept: bsdiff patch 1,600 bytes, 0.15 % of the newer archive raw, 0.26 % of it zstd. The older archive is 1,051,173 bytes, sha256 `3d27f05b…f4ba`, 56 smaller than the newer, which is still `7a5e28c6…9171` |
| R8 | The first download through `bundletool --local-testing`: the app reports the Tatry ready with its OSM date, the debug map draws from the pack's path, and whether `ExtractionWorker` ran, with the phone's API level (JobScheduler checks the network-state permission only from API 34) | `./gradlew bundleDebug -PmapPacks`, then `bundletool build-apks --local-testing --bundle app/build/outputs/bundle/debug/app-debug.aab … && bundletool install-apks …`; `adb shell getprop ro.build.version.sdk`; `adb logcat -d \| grep -E 'WM-WorkerWrapper\|ExtractionWorker\|SecurityException'`; `adb shell dumpsys jobscheduler \| grep <debug id>` | 2026-10-19, API 36: "Tatry: not downloaded" → Download → "Tatry: ready, OSM data of 2026-10-18T20:25:06Z"; extracted to `files/assetpacks/region_tatry/44/44/assets/tatry.pmtiles`, and the gate drew from it with the pushed file removed. No `WM-WorkerWrapper` or `ExtractionWorker` line, no `SecurityException`, and `dumpsys jobscheduler` lists no job for the app. **§ 3 verdict 3**: undecided until E |
| R9 | `installDebug` with no pack reports "not downloaded" and does not crash | `./gradlew installDebug`, open Settings' debug section | 2026-10-19, API 36. First run: **crash**, `NoSuchMethodError` in `asset-delivery-ktx` 2.3.0's `onFailure`. After the switch to plain `asset-delivery`: "Tatry: not downloaded", and the gate shows its no-archive message; the process stays alive |
| R10 | The debug map draws Trails in their colours from the profile's attribute; the tile holding a way shared by two Trails carries both colours on that way, sorted. The decode is local and can be taken any time; the screen waits for the phone | `build-map-data.py`, push, open the debug map, `adb exec-out screencap -p > r10.png`; `python3 scripts/map/decode-tile.py <archive> <z> <x> <y>`; the count, over the archive's z14 tile range: `for x in $(seq 9083 9118); do for y in $(seq 5605 5620); do python3 scripts/map/decode-tile.py <archive> 14 $x $y; done; done 2>/dev/null \| grep "colours': '[^']*;" \| awk '{print $2}' \| sort -u \| wc -l` | 2026-10-19, decode only, pin 4942 (`7a5e28c6…9171`): w22773921, a path two Trails share, in tile 14/9090/5612, carries `colour=blue`, `colours=blue;green`. w22787884 in 14/9090/5611 carries `green;red`, and w26552164 in 14/9096/5607 `blue;green`. 140 ways carry two or more colours, in 163 features, because some ways are split across tiles. Screen, 2026-10-19: Trails drawn in red, blue, green, yellow and black from `colour` (offsets for shared ways are phase 2's) |
| E1 | *(checkpoint E)* The update size Play reports for a one-pin data refresh, against R6 | internal app sharing, two builds | |
| E2 | *(checkpoint E)* The release bundle declares none of the FORBIDDEN permissions with the asset-delivery library and WorkManager in, and exactly the four new EXPECTED rows | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | |
