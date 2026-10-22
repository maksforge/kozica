# Phase 2c — Every Region arrives with the app, and nobody has to pick one

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
amends [ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md)'s delivery
decision, each Region an on-demand pack, in this file's commit, and undoes the user-facing half of
[`phase-2b.md`](phase-2b.md) § 5. [`CONTEXT.md`](../CONTEXT.md)'s **Region** was redefined with it:
the unit the map data is built and delivered in, which the user never picks.

**What closing this phase means.** Today a fresh install opens on a welcome screen asking which of
nine Regions to download, and the map draws nothing until the user has chosen and Play has fetched
them; a Regions screen downloads and removes them one by one or all at once. After this phase every
Region is a **fast-follow** pack: Play fetches all nine by itself right after the install, without
the app being opened, and the app has no screen for choosing. The difference a device can be asked
about: a build installed from Play, never opened, holds all nine Regions a few minutes later, and
the first launch opens on the Tatry's Trails.

Decided with the owner on 2026-10-22, while phase 4 was being planned: relief for every Region
would grow the downloads by about 200 MB, and the owner chose to ship everything to everyone rather
than add relief to a per-Region choice. Kozica is on the internal track only, so nothing a stranger
installed loses a screen.

---

## 0. What this phase inherits

- **Phase 2b's packs and library stay as they are.** Nine `region_<id>` modules built by
  `gradle/region-pack.gradle.kts`, the asset-delivery library in `main/`, its four permissions and
  the `dataSync` extraction service. Only the pack's `deliveryType` changes, in one line.
- **`RegionFiles` already reads every state a fast-follow pack passes through.** Play reports a
  fast-follow pack with the same `AssetPackStatus` values as an on-demand one, and `fetch` accepts
  one that has not arrived, so the Retry and Play's confirmation dialog keep their code.
- **What a device has proven and what it has not.** R7 proved Play delivers all nine on-demand
  packs to a Play install. Nothing has yet read a fast-follow pack, on either phone, or what an
  install that holds on-demand packs does when the update turns them fast-follow.
- **The map's camera is stored** (phase 2), and phase 2b § 5 moves it to a Region on its first
  download, remembered in `AppPreferences.readyRegions`.

## What is in, and what is deliberately not

**In:** `deliveryType` `fast-follow` for every Region pack. The map's progress line while any Region
has not arrived, with Retry and Play's confirmation. The first launch's camera on the Tatry. Deleting
the Regions screen, the welcome screen, the map's download button, Settings' *Map* section, the
camera's arrival rule and both stored keys that served them. The listing, the privacy policy and the
foreground-service answers that described a Region the user chose.

**Not in, and each with a reason rather than a phase:**

- **Removing a Region to free space.** About 560 MB on the phone is real on a small one, but a
  Remove brings back the per-Region choice this phase removes, and a map with holes in it. If
  support mail asks for it, it returns as its own change.
- **Install-time packs.** The data would be there before the first launch, but the store listing
  would show about 485 MB, Android wants about twice that free to install or update, and the
  difference over fast-follow is the minute after install.
- **A read-only list of Regions and their states.** It answers a question few users will ask, and
  the debug build's Settings already lists every pack's state.

**The tempting one** is keeping the Regions screen "for later", behind the progress line's Retry or
in Settings. It costs nothing to leave in, it seems. But every string on it stays inside the
translation gate, its states have to stay true to a delivery mode that no longer asks, and a later
phase adding Remove back would start from a screen built for choosing rather than freeing space.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **Every Region arrives by itself** — fast-follow, the progress line, the Tatry on first launch, the screens and keys deleted | `feat:` | — |
| **B** | **What the documents say** — the listing, its 0.6.0 notes, the privacy policy, the foreground-service answers | `docs:` | A |

**No gate.** Nothing here can veto the phase: if Play fetched nothing by itself, the app's own
`fetch` on the first launch (§ 1) still brings every Region, as phase 2b's did. R1 reads which of
the two did it, and R2 the update from 0.5.0's on-demand packs.

**A and B ship in one release.** B's listing describes A's behaviour, so its notes are written on A's
branch, as phase 3's were, and the release PR waits for B.

---

## 1. Every Region arrives by itself

- **`deliveryType.set("fast-follow")`** in `gradle/region-pack.gradle.kts`. Nothing else in the
  build changes: `-PmapPacks`, the data build and the archives on the GitHub release are as phase
  2b left them.
- **The app asks for whatever has not arrived.** On the map's first collection of the Regions, a
  Region that is neither ready nor on its way (Play's `NOT_INSTALLED`, or `FAILED`) is fetched, all
  of them in one `fetch` call, as "Download all" did. That covers a fast-follow download Play has
  not started, one the user cancelled in the Play Store, and a Region added to a later build.
  `installDebug` has no Play session, so the fetch fails and the map shows its empty state, as now.
- **The progress line** replaces the empty state's "No Regions downloaded yet." and its button. It
  shows while any Region is not ready, over the map rather than instead of it, since the Regions
  already ready draw beneath it:

  | State of the Regions not ready | Line | Action |
  | --- | --- | --- |
  | Any downloading or pending | "Downloading maps — %1$d of %2$d" (ready of all) | — |
  | Any waiting for Wi-Fi | "Waiting for Wi-Fi" | "Download over mobile data", Play's dialog |
  | Any needing confirmation | "Play needs your confirmation" | "Confirm", Play's dialog |
  | Any failed, none of the above | "Download failed" | "Retry", one `fetch` for every failed Region |
  | Any updating | "Map data is being updated." (phase 2's) | — |

  The first matching row wins, in that order: a download still moving is reported before one that
  failed, and Play's dialog before a Retry, because the dialog may be what the failure waits on.
  With every Region ready the line is gone.
- **The first launch opens on the Tatry.** With no stored camera, the map centres on the Tatry at
  about zoom 11, where Trails, Huts and peaks draw, instead of phase 2's overview from the
  Karkonosze to the Bieszczady, which draws borders and park outlines only. After that the stored
  camera applies, as it does now.
- **The camera never moves by itself.** Phase 2b § 5's arrival rule goes, with
  `AppPreferences.readyRegions`: nine Regions landing within a minute of the install would
  otherwise move the map under a finger that is already panning it.
- **Deleted:** `ui/regions/` with its screen, `ViewModel` and test; the `Regions` NavKey; the map's
  download button; Settings' *Map* section and its row; `ui/welcome/`, its NavKey and the
  `onboarding_done` key; `RegionFiles.remove`; the `regions_*`, `welcome_*` and `map_choose_regions`
  strings in every locale; the Regions and welcome scenes in `scripts/edge-to-edge.py`. A stored key
  nothing reads any more is harmless on the internal track's installs, so neither key gets a
  removal step. `REGION_NAMES` and the Region name strings stay: the debug build's pack list and the
  progress line's count read them.
- **The map's buttons** are Safety and Settings.

Copy: "Downloading maps — %1$d of %2$d". The other lines are phase 2b's, kept under new names:
"Waiting for Wi-Fi", "Download over mobile data", "Play needs your confirmation", "Confirm",
"Download failed", "Retry". "Map data is being updated." is phase 2's.

## 2. What the documents say

- **The listing's description** loses "Download the ranges you need before you go": every range
  arrives with the app. The 0.6.0 notes say the map now arrives with the app, all nine Regions,
  with nothing to choose, and that the Regions screen is gone.
- **The website's front page**, `docs/index.md`, loses "once you have downloaded the ranges you
  need" from its opening line.
- **The privacy policy** says the map data is downloaded by the Play Store app after Kozica is
  installed, rather than "when you choose a Region".
- **[`play-app-content.md`](play-app-content.md)'s foreground-service answers**: "What the user
  sees" becomes the maps that arrive with the app being made ready to use offline. The declaration
  is still checkpoint E's, owed in the Console.

---

## Tests

- **The progress line's state** is a pure function from the Regions' states to one line and its
  action, with the order in § 1's table. A test per row, and one with a downloading and a failed
  Region proving downloading wins: a Retry offered while a download still runs would start a second
  one.
- **What the first launch fetches**: not-installed and failed Regions, never one ready, downloading
  or updating. A Region updating fetched again would restart Play's patch.

## Documents this phase amends

- **ADR-0007**, with an amendment dated 2026-10-22: every Region a fast-follow pack.
- **`phase-2b.md`**, at its head: § 5's screens and the camera's arrival rule are undone here.
- **`CONTEXT.md`**: Region, already amended.
- **`docs/store-listing.md`**, **`docs/index.md`**, **`docs/privacy-policy.md`**,
  **`docs/play-app-content.md`**: § 2.
- **`PLAN.md`**: this phase's line and paragraph.
- **`CLAUDE.md`**: the Stack's Play Asset Delivery row and the Layout's `data/regions/` line, with A.

## Known gaps, deliberately

- **Every install carries every Region, about 275 MB from Play and 351 MB on the phone**, before
  phase 4's relief. Someone who hikes only the Tatry pays for the Sudety. Accepted for an app with
  nothing to choose.
- **A data refresh reaches everyone's nine packs.** Before, it reached only the Regions a user had
  chosen. What a refresh costs through the closed track is still phase 2b § 6's reading to take.
- **Opening the app in the first minute shows a part-drawn map.** The progress line says why.

## Readings

`PLAN.md` rule 3. A reading through Play needs Wi-Fi on the test phone; it goes off again after.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | A fresh install from Play holds all nine Regions without being opened, and which of Play and the app's own `fetch` brought them | install A's release from the internal track with the app never opened; after five minutes `adb -s mnbm7hw8ivdqzx9l logcat -d \| grep -E 'AssetPack\|KozicaPack\|Finsky.*region_'`; then the first launch's screen, `adb -s mnbm7hw8ivdqzx9l exec-out screencap -p > r1.png` | *(date)* |
| R2 | The update from 0.5.0: an install holding some on-demand Regions and missing others ends with all nine, the ones it held kept rather than fetched again | 0.5.0 from the internal track with three Regions downloaded, then A's release over it; R1's logcat filter, and Finsky's byte counts per pack | *(date)* |
| R3 | The first launch mid-download: the Tatry, the progress line counting up, Regions drawing in place, the camera unmoved | open the app within a minute of R1's install; a screencap per state | *(date)* |
| R4 | The same fresh install on the owner's phone, API 36, HyperOS | R1 with `-s 132bc856`, after saying so in chat | *(date)* |
