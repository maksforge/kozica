# Phase 0 — Kozica opens on its own screen, in its own colours, with nothing of the template left

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
amends [ADR-0003](adr/0003-two-scheduling-mechanisms.md) (nothing schedules any more) and relies on
[ADR-0006](adr/0006-the-app-owns-its-palette-material-you-is-opt-in.md). `CONTEXT.md` is owed
nothing more: Region, Trail, Path, Hut and Shelter were settled before this phase opened, and no term
of its own appears on screen.

**What closing this phase means.** Today the app is the template: a tab bar around an Items list,
reminders with their alarms and permissions, a slate-blue scheme. After this phase it opens on one
screen that holds the map's place, with Settings, Licences and Support behind an icon, in the
chamois-ochre palette, in English and Polish. The difference a device can be asked about: the
installed build declares none of the reminders' permissions, and nothing on screen says "Item".

---

## 0. What this phase inherits

- Version 0.1.0 on Play's internal track, built from the template unchanged. Nothing has reached a
  stranger, so nothing is frozen yet.
- `minSdk` 26, deliberately kept: the only phone in the loop is API 36, so branches below it are
  exercised only by CI's lowest-API legs until testers' phones arrive.
- `scripts/aab-permissions.py`'s EXPECTED table still describes the template.

## What is in, and what is deliberately not

**In:** deleting reminders, WorkManager and the rest of `work/`; deleting the Items screens, routes,
strings and tab bar, and the sample data that feeds them; a single home screen with an empty-map
placeholder and an icon to Settings; the ochre palette; `aab-permissions.py` and
`scripts/edge-to-edge.py` rewritten against what is left; the privacy policy and
`play-app-content.md` amended to match.

**Not in, and each with a reason rather than a phase:**

- **The map.** It is phases 1 and 2. This phase leaves its place empty rather than drawing anything
  temporary that a later phase would have to remove.
- **The mark.** Its concept is the owner's to draw or generate, and it must not resemble the Tatra
  National Park's chamois logo. The placeholder ships until then; the DOD item stays open.
- **The support address.** It waits for the dedicated account; the DOD item stays open.
- **`ItemEntity` as a table.** It stays in the schema, dormant, with no screen reading it. Room
  cannot run with no entity, and the first real one — a planned route — arrives in phase 5. Only a
  debug build may drop the database (`destructiveMigrationAllowed()`), and Play installs already run
  0.1.0, so phase 5 ships a real `MIGRATION_1_2` that drops `items` and creates its own table, with
  all five items of `DOD.md`'s standing schema gate. Deleting Room now and re-adding it then would
  cost more than a table nobody reads.
- **Backup in Settings.** The code and the screen stay; the row leaves Settings until phase 6 gives
  it something of the user's to keep, for the same reason a tab waits for the phase that fills it.

**The tempting one** is to put the three final tabs in now — Map, Routes, Hikes — so the shell never
changes. Two of them would be empty through the whole closed test. A tab arrives with the phase that
fills it (phase 5).

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **Reminders gone** — the code, the manifest entries, WorkManager, Settings' section, the allowlist rows | `refactor:` | — |
| **B** | **Items gone, one screen** — the Items UI and tab bar out, the empty-map home in, the debug seeding seam kept but seeding nothing | `feat:` | — |
| **C** | **The palette** — four seeds in `scripts/gen_scheme.py`, regenerated, read on the phone in light and dark | `feat:` | — |

A and B are independent; both edit `SettingsScreen.kt`, so whichever merges second rebases. C goes
last so the palette is judged on the screen that will actually carry it rather than on the Items
list.

There is no gate. Nothing in this phase is unmeasured platform behaviour; the risk is a permission
or a string left behind, which the readings below catch.

---

## 1. Reminders gone

`docs/optional-modules.md` § *Reminders and WorkManager* is the list, file by file. What it leaves
easy to miss:

- Six permissions leave the artifact: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` and
  `SCHEDULE_EXACT_ALARM`, which the manifest declares, and `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and
  `FOREGROUND_SERVICE`, which WorkManager merges. `aab-permissions.py`'s EXPECTED loses all six in the
  same commit, and `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` join `INTERNET` in FORBIDDEN, with
  [ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md) as the reason.
- The rest of `work/` goes too. `Autostart.kt` and `BatteryExemption.kt` serve only background work,
  and `PLAN.md` rule 4 brings them back only on a reading. The `com.miui.securitycenter` entry in
  `<queries>` goes with `Autostart.kt`. Two helpers move out first: `startActivitySafely` to
  `ui/common/`, because `ui/support/SupportHandoff.kt` uses it and phase 3's hand-offs will; and
  `schemaBlocksBackgroundWork()` to `data/`, beside the gate, because ADR-0001 makes it the check
  every background entry point runs and phase 6's recording service is the next one.
  `SchemaGuard.kt`'s `rebuildInBackground` serves only the reminders' receivers and goes with them.
- Settings' *Reminders* section is the template's worked example of `PLAN.md` rule 4, and phase 2's
  location ask copies its shape. After this checkpoint read it at `10bf995`:
  `ui/settings/SettingsScreen.kt` and `work/NotificationPermission.kt`.

ADR-0003 gets a dated amendment: Kozica schedules nothing, and both mechanisms are gone.

## 2. Items gone, one screen

`docs/optional-modules.md` § *The placeholder domain* lists the files. The home screen is an empty
map placeholder — a neutral surface with the app name and one line of copy — and a top-bar icon to
Settings. `scripts/edge-to-edge.py`'s SCENES walk this screen and Settings, nothing else.

Debug seeding keeps its seam: `SeedReceiver`, Settings' debug rows and the seed machinery in
`edge-to-edge.py` and `screenshots.py` stay, and `SampleData.kt` seeds nothing until phase 5 or 6
has data to put in it. Deleting the seam would mean rewriting both scripts now and again then. The
wipe row's `debug_wipe`, "Delete all items", becomes "Delete all data", or R4 finds it.

Copy, English:

- Placeholder: "Your map will appear here."
- Settings icon's content description: "Settings"

## 3. The palette

Four seeds in `scripts/gen_scheme.py`:

| Seed | Hue |
| --- | --- |
| Primary | Chamois ochre, the summer coat |
| Secondary | Warm grey-brown, rock |
| Tertiary | Muted slate, dark enough to read as neither trail blue nor black |
| Neutral | Warm stone grey |

**The constraint is the map, not taste.** Red, blue, green, yellow and black are Trails. No role the
app paints over the map — the position dot, a selected control, a floating button — may read as one
of them, and each Trail colour must stay clearly visible on the map in light and dark. The Trail
colours themselves belong to the map style, not to `MaterialTheme`; phase 2 records that exception
to the house rule.

This phase cannot check the constraint, because there is no map yet. Phase 2 reads it: the position
dot and the map controls over a yellow and a black Trail, in light and dark, and a seed changes if
one fails.

---

## Tests

None. This phase introduces no value that computes or bounds something safety-relevant (`PLAN.md`
rule 3).

## Documents this phase amends

- ADR-0003: the amendment above.
- `CLAUDE.md`: the WorkManager stack row, `work/` in the layout, and the environment note's
  scheduled notifications.
- `docs/privacy-policy.md`: the notifications line goes with reminders, and point 2 of *The three
  things that happen outside the app*, the backups you export yourself, with the heading's count,
  until phase 6 brings Backup back.
- `docs/play-app-content.md`: the reminder permissions' rows, § *Foreground service permissions*
  and the reminders in the header comment, in the same commit as the policy.
- `scripts/aab-permissions.py`: EXPECTED, to what is left; FORBIDDEN, as in § 1, and the battery
  exemption row's reason, which names `work/BatteryExemption.kt`.

## Known gaps, deliberately

- **The placeholder mark ships to internal testing.** Nobody but the owner sees it before the door.
- **`support@example.com` stays compiled in** until the dedicated account exists. No stranger has a
  build before phase 3, which owns the address.

## Readings

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The release bundle declares none of the six permissions in § 1 | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | 2026-10-18, versionCode 13: one permission, AndroidX's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; none of the 8 forbidden ones |
| R2 | The app opens on the placeholder screen, in English and in Polish | `./gradlew installDebug`, switch language in Settings | |
| R3 | The palette in light and dark, on the phone | `./gradlew installDebug`, system dark mode on and off | |
| R4 | Nothing says "Item" anywhere | `grep -rniE '>[^<]*\bitems?\b' app/src/*/res/values*/strings.xml`, and a walk through every screen | |
