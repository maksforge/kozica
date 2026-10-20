# Phase 2b — Every mountain range downloads from a list, across the border, delivered by Play

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds on [ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md) and owns the
second decision it leaves to "the map phase": the list of Regions, with each one's polygon. It
reopens one of ADR-0007's premises, that the data is Poland's extract alone, and amends the ADR in
this file's commit. It works inside [ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md)
and takes over phase 1's checkpoint E, whose brief is [`phase-1.md`](phase-1.md) § 5. `CONTEXT.md`
owes one change: a **Region** no longer stops at the state border.

**What closing this phase means.** Today the release map says "No Regions downloaded yet." and
nothing can change that: the asset-delivery library is in the debug build only, and only the Tatry
exists, cut at the border. After this phase nine Regions are built in CI from Poland's, Slovakia's
and Czechia's extracts, each an on-demand pack, and a Regions screen downloads any of them, or all of
them, and removes them. The difference a device can be asked about: a release build installed from
Play downloads the Tatry, draws the Slovak side's Trails beside the Polish ones, and a one-pin data
refresh arrives as a patch Play reports at a fraction of the pack.

**`PLAN.md`'s paragraph changed in one place, decided with the owner on 2026-10-20 before this file
was written, and is amended with it.** It asks "whether a polygon follows the state border exactly".
The answer is that no polygon follows it: Regions cross into Slovakia and Czechia, where the ranges
do. Half the Tatry's Trails are Slovak, and the Karkonosze's main ridge Trail walks the border. That
is pipeline work with a cost of its own, so it is a checkpoint, C, not a footnote.

---

## 0. What this phase inherits

- **A provisional strip.** Phase 1 § 3 took verdict 3: under `bundletool --local-testing` the Tatry
  pack arrived but `ExtractionWorker` never ran, so whether extraction survives `ACCESS_NETWORK_STATE`
  stripped is unknown. **JobScheduler checks that permission only from API 34** (phase 1, R8's
  reading). The test phone is API 30 and cannot answer it; the owner's phone, API 36, can.
- **A proxy, not a reading.** Phase 1's R6 put a week of churn at 0.36 % of the Tatry archive by
  bsdiff. Play's algorithm for packs is undocumented. E1, the update size Play reports, is the
  reading that counts, and it moves into this file's table.
- **The Region seam, half built.** `data/regions/Regions.kt` in `main/` holds the states and reads a
  PMTiles header. `RegionSource.kt` is the seam: the debug half wraps `RegionFiles`, which knows one
  pack, `region_tatry`, by name; the release half emits an empty list. The map already draws every
  ready Region, by layer and not by Region, and shows the oldest one's OSM date (phase 2 § 2).
- **One pack module, gated.** `region_tatry/` joins the build only with `-PmapPacks`, which no CI job
  passes yet. `app/build.gradle.kts` names it by hand.
- **One pin, one extract.** `scripts/osm-data.pin` holds one Geofabrik sequence number;
  `build-map-data.py` brings Poland's extract to it and checks its header.
- **The tile schema is final for now.** Phase 2 fixed it, and the Tatry archive it builds is 7.3 MB
  at pin 4942 (`region_tatry/src/main/assets/tatry.pmtiles`, 7,274,738 bytes).
- **A public repository** and a GitHub release per release-please tag, which the ODbL offer rides.
- **The test phone has Play and a Google account**, read 2026-10-20 with `dumpsys account`, but sits
  in airplane mode with Wi-Fi off. A reading through Play turns Wi-Fi on for its duration and off
  again.

## What is in, and what is deliberately not

**In:** the asset-delivery library in the release build, with the Console's foreground-service
declaration and the documents that say Kozica schedules nothing. Nine Regions, each a polygon and an
on-demand pack. Slovakia's and Czechia's extracts merged into the build at one moment. The data build
in `publish-play.yml`, and the archives attached to each GitHub release as the ODbL offer. The
Regions screen: every Region's state and size, Download, Remove, and "Download all". A button for it
on the map and in the empty state. The readings through Play that phase 1 could not take.

**Not in, and each with a reason rather than a phase:**

- **Ukraine's side of the Bieszczady.** Its trails are few and its extract is large against what it
  adds. The polygon stops at the border there.
- **Germany's side of the Góry Izerskie.** The same, and smaller still. Sudety Zachodnie stops at
  that border.
- **Translated Region names.** Decided with the owner: a Region's name is Polish in every language,
  `translatable="false"`, as on the signposts and the map's own labels. Most of the nine have no
  exonym, and a name that changes between the picker and the ground costs more than one that does not
  match the UI language.
- **Showing a Region's outline on the map before it is downloaded.** It would need the polygons in the
  app as geometry, and a picker that draws. A row with a name and a size is enough to choose by; the
  names are the ones on every guidebook's cover.
- **A refresh outside a release.** ADR-0007 ties data to releases, and nothing here reopens that.

**The tempting one** is drawing the polygons along the state border "properly", or along each
range's watershed. It looks like care. But a Region's edge is never seen: the map draws a Region's
tiles, not its outline, and two ready Regions that overlap draw as one (phase 2 § 2). What the edge
decides is only whether a Trail near it is in the pack. So polygons are generous, overlap their
neighbours by a few kilometres, and are drawn coarsely, in tens of points, not hundreds.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — Play delivers the Tatry pack to a release-shaped build with `ACCESS_NETWORK_STATE` stripped, on API 36, and patches it for a one-pin refresh | readings only, from B's branch | — |
| **B** | **The library in `main/`** — `RegionFiles` for any pack, the release half of the seam, the declaration, the four permission rows | `chore:` | A |
| **C** | **Across the border** — Slovakia and Czechia merged into the build at one moment, a pin per extract | `chore:` | — |
| **D** | **Nine Regions** — the polygons, one pack module per polygon, built and measured | `chore:` | C |
| **E** | **The Regions screen** — the list, Download, Remove, "Download all", the map's button and the empty state's | `feat:` | B |
| **F** | **Play delivers it** — the data build in `publish-play.yml`, `-PmapPacks`, the archives on the GitHub release, the licences screen's offer | `feat:` | B, D, E |

**A reads B's branch before B merges.** The question is whether the release build may carry the
library at all, and the only apparatus that answers it is that build, delivered by Play. So B is
written on a branch, bundled locally with the Tatry pack, and given to Play through internal app
sharing. A local bundle is allowed here: ADR-0007 forbids local data only in a *release*, and an
internal-sharing link reaches the owner alone. If A takes verdict 2, B does not merge.

**C needs no gate and runs beside A.** It is local pipeline work and puts nothing in front of a user.
It goes before D so the nine polygons are drawn once, already across the border, rather than drawn
to the border and redrawn.

**B, C and D cut no release; E and F do.** B puts the library into the release while the release
still carries no pack, so a user sees nothing. E is the first thing a user sees, and on its own it
shows nine Regions none of which can arrive. That is honest for the few hours between E and F on the
internal track, and E and F may merge in one release-please release if they land together. F is the
first release that conveys OSM-derived data, so the ODbL offer ships in F's commit, not after it.

---

## 1. The gate: does Play deliver a pack to a build that cannot read network state?

### The question, in the form that can be measured

On the owner's phone (`132bc856`, API 36, asked first), a release-variant bundle from B's branch,
with `asset-delivery` as `implementation`, `ACCESS_NETWORK_STATE` removed from the merged manifest
by `tools:node="remove"` and the Tatry pack included, is uploaded to internal app sharing and
installed from its link. Asked for the Tatry, does the app reach Ready with the OSM date, draw the
map from the pack's path, and log no `SecurityException`? And did `ExtractionWorker` run, or did the
Play Store hand the pack over already extracted?

Then a second bundle, one pin newer, is uploaded to internal app sharing and installed over the
first from its link. What download size does Play report, and does the Tatry reach Ready again
without being asked? An internal-track install does not update in place (`RELEASING.md`), so it
cannot stand in.

### The apparatus

- B's branch, built with `./gradlew bundleRelease -PmapPacks` and the upload key, twice: at pin 4942
  and at the newest pin Geofabrik serves on the day, each with the Tatry archive built by
  `build-map-data.py` at that pin. Both are Poland-only; C does not wait for A, and A does not wait
  for C. The second carries a higher `versionCode` by hand on the branch, never committed.
- Internal app sharing, enabled in the Console with the owner as an uploader. Each bundle goes up on
  the Console's page through Claude in Chrome; two uploads do not earn a script.
- The owner's phone on Wi-Fi, because internal app sharing installs through the Play Store app. The
  test phone stands in for nothing here except R2, the second API level.
- Logcat filtered for `WM-WorkerWrapper`, `ExtractionWorker`, `AssetPack` and `SecurityException`;
  `dumpsys jobscheduler` for the app's jobs; Play's download size from the Play Store app's update
  sheet, and the pack's bytes from `AssetPackState.bytesDownloaded()`, logged by the debug section's
  state row, which B moves to read any pack.

### The verdicts, decided now rather than argued about later

1. **The Tatry reaches Ready, the map draws from the pack, and no `SecurityException` is logged on
   API 36, whether or not `ExtractionWorker` ran.** → The strip stands, and B merges. Which of the
   two paths delivered the pack is recorded, because it says whether this question can return with
   a later library version.
2. **Extraction fails for want of `ACCESS_NETWORK_STATE`.** → Phase 1 § 3's verdict 2, unchanged:
   no workaround in code. The owner chooses between a narrow ADR-0008 amendment that allows
   `ACCESS_NETWORK_STATE` alone, since it opens no connection and changes no Data safety answer, and
   a different delivery for map data. B does not merge until then; C and D go on.
And for the refresh, R3:

3. **The pack arrives, but the refresh costs close to the whole pack.** → The format question goes
   back to the owner before F, as phase 1 § 4's verdict 2 says: determinism first (`--check-determinism`
   at both pins), then the layout. A refresh that costs the whole pack every release is not a reason
   to stop the phase, but it is a reason to know before the first user pays it.

"Close to the whole pack" is above 25 % of the Tatry pack's compressed size. R6's bsdiff said 0.6 %,
and Play is free to do worse than bsdiff; it is not free to do forty times worse without that being
the finding.

### The verdict, taken *(date)*

## 2. The library in `main/`

Phase 1 § 5's first two bullets are this checkpoint, and stand as written there. What this phase adds:

- **`RegionFiles` stops knowing the Tatry.** It takes the list of pack names and exposes one `Flow`
  of every Region's state, from one `AssetPackStateUpdateListener` and one `getPackStates` call over
  the whole list. `fetch` and `removePack` take a Region. It moves to `main/`'s `data/regions/`.
  `RegionSource.kt` stays a seam with two halves, as the position override's is: the release half
  passes `RegionFiles` through, and the debug half wraps it with the fallback to a hand-pushed
  `files/regions/<id>.pmtiles`.
- **The Region list is the polygons.** `scripts/regions/<id>.poly` names every Region. Gradle reads
  that directory: `settings.gradle.kts` includes one pack module per polygon, `region_<id>`, from one
  shared build script rather than nine copies, and `app/build.gradle.kts` adds them all to
  `assetPacks` under `-PmapPacks`. The app's list is generated from the same directory into a Kotlin
  source, in the order a hiker reads the mountains, west to east, with Góry Świętokrzyskie last,
  each entry naming `R.string.region_<id>`. One list, so a Region cannot exist in the build and not in
  the app, and one without a name does not compile. The order is a line in the generator, not a
  field in the polygon.
- **`WaitingForConfirmation` gets its handler.** Over mobile data Play holds a large download for the
  user's yes. `AssetPackManager.showConfirmationDialog` needs an `ActivityResultLauncher`, so the
  Regions screen registers one; the map's state row only names the state.
- **The Console declaration** for `FOREGROUND_SERVICE_DATA_SYNC`: the library's extraction service,
  started by the Play Store's delivery, user-initiated by a download. It is the owner's account, so
  it is filled through Claude in Chrome in the sitting that merges B, and recorded in
  `play-app-content.md`.

## 3. Across the border

- **Three extracts, one moment.** `scripts/osm-data.pin` becomes one line per extract,
  `<geofabrik name> <sequence>`, for `poland`, `slovakia` and `czech-republic`. Geofabrik numbers
  each extract's replication on its own: on 2026-10-18 at 20:25:06Z Poland and Slovakia were at 4942
  and Czechia at 4943. So a pin is three numbers, and the build **fails unless all three updated
  headers carry the same `osmosis_replication_timestamp`**. That timestamp is the data's date, and
  the one the map shows.
- **Each extract is brought to its sequence as Poland's is today**, from the newest dated extract
  before it, by `pyosmium-up-to-date --end-id`, then checked against its line of the pin. Sizes read
  2026-10-20: Poland 2.11 GB, Czechia 0.95 GB, Slovakia 0.35 GB, so a release build downloads about
  3.4 GB, not 2.1.
- **`osmium merge` joins them before the cut.** Geofabrik's extracts overlap a little at the borders,
  and `merge` keeps one copy of an object present in two. The merged file carries the shared
  replication header, written with `--output-header`, as phase 1 § 4's edit does, or planetiler
  would read no date.
- **Determinism is re-read.** `--check-determinism` builds twice from the merged file. A merge whose
  output order depended on input order would be the stray timestamp ADR-0007 warns of.
- **A data refresh is still one `fix:` commit** that moves the pin: now three numbers, one moment.
- **The profile is unchanged.** Slovakia's and Czechia's Trails use the same four colours and the
  same `osmc:symbol` grammar; R5 reads the Tatry's Slovak side off the screen to prove it.

## 4. Nine Regions

Decided with the owner, 2026-10-20. Ids are the pack names, ASCII and underscores; names are
Polish, `translatable="false"`.

| Id | Name | Ranges | Crosses into |
| --- | --- | --- | --- |
| `sudety_zachodnie` | Sudety Zachodnie | Karkonosze, Góry Izerskie, Rudawy Janowickie, Góry Kaczawskie | CZ |
| `sudety_srodkowe` | Sudety Środkowe | Góry Stołowe, Sowie, Wałbrzyskie, Kamienne, Bystrzyckie, Orlickie | CZ |
| `sudety_wschodnie` | Sudety Wschodnie | Masyw Śnieżnika, Góry Bialskie, Złote, Opawskie | CZ |
| `beskidy_zachodnie` | Beskidy Zachodnie | Beskid Śląski, Mały, Żywiecki, Makowski | CZ, SK |
| `gorce_pieniny` | Gorce i Pieniny | Gorce, Pieniny, Beskid Sądecki, Beskid Wyspowy | SK |
| `tatry` | Tatry | the Tatry, with Podhale's edge to Zakopane | SK |
| `beskid_niski` | Beskid Niski | | SK |
| `bieszczady` | Bieszczady | | SK; not UA |
| `gory_swietokrzyskie` | Góry Świętokrzyskie | | — |

- **Polygons are coarse and generous**: tens of points, a few kilometres past each range's last
  Trail, overlapping their neighbours by about as much. Each is drawn on the range's own map, not
  derived from OSM boundaries, and committed as `scripts/regions/<id>.poly`.
- **`tatry.poly` is redrawn**, across the border. Phase 1's R5 found its Slovak half empty because the
  extract stopped there; after C it is not.
- **Each Region's archive is measured** (R6) and must stay under ADR-0007's 1.5 GB; at the Tatry's
  7.3 MB none is expected near it, and the build's check stays the guard.
- **The debug map row "Download Tatry"** becomes the Regions screen, in E. The debug section keeps
  the pack states as text, for readings.

## 5. The Regions screen

- **A `Regions` NavKey** and `ui/regions/`, one `ViewModel`, one immutable UI state: every Region with
  its state and its download size. Reached from a download button on the map, beside Settings, and
  from the empty state, which gains the button phase 2 left for this phase ("Choose Regions").
- **A row** is the Region's name, then its state: the size to download; downloading with a percent;
  waiting for Wi-Fi, with the button that opens Play's confirmation; update in progress; ready, with
  its OSM date, phase 2's "OSM data: %1$s"; failed, with Retry. Download or Remove sits at the row's end. The size is
  `AssetPackState.totalBytesToDownload()`, which Play reports before anything is fetched.
- **"Download all"** fetches every Region not ready, in one `fetch` call, and says how many and how
  large: "Download all — %1$d Regions, %2$s". It is absent when nothing is left to fetch.
- **Remove asks first.** A Region removed on a ridge with no signal does not come back until there is
  signal. The dialog says that, in one line, and Remove is its confirming button. Download asks
  nothing: Play's own confirmation covers mobile data.
- **The map's button and the empty state** read the same `RegionSource`, so the empty state's line
  is still phase 2's: "Map data is being updated." during an update, "No Regions downloaded yet."
  otherwise, now with the button under it.

Copy: "Regions", "Choose Regions", "Download", "Remove", "Retry", "Download all — %1$d Regions,
%2$s" (a plural), "Downloading — %1$d%%", "Waiting for Wi-Fi", "Download over mobile data",
"Updating", "Remove %1$s?", "You won't see this Region's map until you download it
again, which needs a connection.", "Download failed". The Region names are not copy.

## 6. Play delivers it

Phase 1 § 5's last three bullets are this checkpoint, with C's three extracts and D's nine packs:

- **The data build joins `publish-play.yml`** before `bundleRelease`: `build-map-data.py` for every
  Region at the pin, `--check-determinism` on one of them (the Tatry, which changes in every
  refresh), then `bundleRelease -PmapPacks`, which fails when an archive is missing.
- **The ODbL offer is the database itself.** Decided with the owner, 2026-10-20: the workflow
  attaches the nine `.pmtiles` to the release-please GitHub release, under ODbL, and the licences
  screen's ODbL entry links to the release page for the app's own version. That offers the
  derivative database (ODbL §4.6) without licensing the code, whose `LICENSE` stays all rights
  reserved. The workflow fails if the upload fails: a release on Play without its offer on GitHub
  is the one outcome the house rule exists to prevent.
- **The listing and the policy say where the data comes from.** The privacy policy gains one
  sentence: map data is downloaded by the Play Store app, under Google's terms; Kozica opens no
  connection. The listing's description names both sides of the border.

---

## Tests

None for a safety value: nothing in this phase computes one (`PLAN.md` rule 3). Two guards that are
not unit tests stand where tests might be expected:

- **The pin's one moment** is `build-map-data.py`'s check that three headers carry one timestamp. A
  build at three different moments would draw a border where Trails end on one side a day early.
- **The Region list's one source** is Gradle reading `scripts/regions/`. A Region with a polygon and
  no name string fails to compile, not at runtime.

## Documents this phase amends

- **`PLAN.md`**: phase 2b's paragraph, with this file: the polygons cross the border, and C exists.
- **ADR-0007**: a dated amendment with this file: the nine Regions, the three extracts at one moment
  and their pin, and the archives on the GitHub release as the ODbL offer.
- **`CONTEXT.md`**: Region's definition, with this file: it crosses the state border where the ranges
  do, and stops at Ukraine's and Germany's.
- **`DOD.md`**: checkpoint E's item points here, with this file.
- **With B**, as phase 1 § 5 lists: `CLAUDE.md`'s stack rows for Play Asset Delivery and "Nothing
  scheduled", ADR-0003's amendment, `PLAN.md`'s "Reminders and scheduled work",
  `play-app-content.md` § *Foreground service permissions*, and `aab-permissions.py`'s four EXPECTED
  rows. `CLAUDE.md`'s layout: `region_tatry/` becomes one `region_<id>/` per polygon, and
  `data/regions/` loses "each variant supplies the states".
- **With F**: `CLAUDE.md`'s OSM house rule names the offer this phase chose; `docs/privacy-policy.md`
  and `docs/store-listing.md` per § 6; `RELEASING.md` gains the data build and the release assets.

## Known gaps, deliberately

- **The test phone cannot answer the gate.** API 30 is below the level where JobScheduler checks
  `ACCESS_NETWORK_STATE`, so A is the owner's phone's, and waits until it is free.
- **Every release downloads 3.4 GB from Geofabrik.** ADR-0007's alternative of a cache still fails
  for the same reason, a tag ref. Minutes of runner time per release are the price of a build anyone
  can repeat.
- **Ukraine's and Germany's sides are missing.** Named above, with their reasons.
- **E alone shows a list nothing arrives from.** Between E's release and F's, on the internal track
  only.

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device or the build, with the command that
read it. A reading on the owner's phone waits until the owner says it is free. A reading through
Play needs Wi-Fi on; the test phone's goes off again after.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate: the Tatry through internal app sharing on API 36 reaches Ready and draws, no `SecurityException`, and whether `ExtractionWorker` ran | `adb -s 132bc856 logcat -d \| grep -E 'WM-WorkerWrapper\|ExtractionWorker\|AssetPack\|SecurityException'`; `adb -s 132bc856 shell dumpsys jobscheduler \| grep io.github.srednimax.kozica`; `adb -s 132bc856 exec-out screencap -p > r1.png` | |
| R2 | The same install on the test phone, API 30: the second API level, below JobScheduler's check | as R1, with `-s mnbm7hw8ivdqzx9l` | |
| R3 | *(phase 1's E1)* The update size Play reports for a one-pin refresh of the Tatry, against R6 of phase 1, and the pack back to Ready unasked | internal app sharing, the second link over the first; Play Store's update sheet; the debug section's logged `bytesDownloaded` | |
| R4 | *(phase 1's E2)* The release bundle declares none of the FORBIDDEN permissions with the library and WorkManager in, and exactly the four new EXPECTED rows | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | |
| R5 | Across the border: the three extracts at one moment, the merged build deterministic, and the Tatry's Slovak Trails drawn | `python3 scripts/build-map-data.py --check-determinism`; push the archive; `adb exec-out screencap -p > r5.png` over Štrbské Pleso | |
| R6 | Every Region's archive size, raw and zstd, at one pin | `ls -l`, `zstd -19 -c … \| wc -c` per archive | |
| R7 | The Regions screen on the phone: one Region downloaded, removed with its dialog, then "Download all" to every Region ready, from a build Play delivers | F's release on the internal track, uninstalled and reinstalled from Play; `adb exec-out screencap -p` per state | |
| R8 | The ODbL offer: the release page holds nine archives whose hashes match the bundle's packs | `gh release view v<x.y.z> --json assets`; `sha256sum` against `bundletool`'s extracted packs | |
