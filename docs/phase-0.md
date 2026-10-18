# Phase 0 — Kozica opens on its own screen, in its own colours, with nothing of the template left

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
amends [ADR-0003](adr/0003-two-scheduling-mechanisms.md) (nothing schedules any more) and relies on
[ADR-0001](adr/0001-the-database-stops-being-disposable-at-1-0.md) (the database is disposable until
1.0) and [ADR-0006](adr/0006-the-app-owns-its-palette-material-you-is-opt-in.md). `CONTEXT.md` is owed
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

**In:** deleting reminders and WorkManager; deleting the Items screens, routes, strings and tab bar,
and the debug seeding that feeds them; a single home screen with an empty-map placeholder and an icon
to Settings; the ochre palette; `aab-permissions.py` and `scripts/edge-to-edge.py` rewritten against
what is left; the privacy policy and `play-app-content.md` amended to match.

**Not in, and each with a reason rather than a phase:**

- **The map.** It is phases 1 and 2. This phase leaves its place empty rather than drawing anything
  temporary that a later phase would have to remove.
- **The mark.** Its concept is the owner's to draw or generate, and it must not resemble the Tatra
  National Park's chamois logo. The placeholder ships until then; the DOD item stays open.
- **The support address.** It waits for the dedicated account; the DOD item stays open.
- **`ItemEntity` as a table.** It stays in the schema, dormant, with no screen reading it. Room
  cannot run with no entity, and the first real one — a planned route — arrives in phase 5, which
  replaces this table while the database is still disposable (ADR-0001). Deleting Room now and
  re-adding it then would cost more than a table nobody reads.

**The tempting one** is to put the three final tabs in now — Map, Routes, Hikes — so the shell never
changes. Two of them would be empty through the whole closed test. A tab arrives with the phase that
fills it (phase 5).

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **Reminders gone** — the code, the manifest entries, WorkManager, Settings' section, the allowlist rows | `refactor:` | — |
| **B** | **Items gone, one screen** — the Items UI and tab bar out, the empty-map home in, debug seeding pointed at nothing | `feat:` | — |
| **C** | **The palette** — four seeds in `scripts/gen_scheme.py`, regenerated, read on the phone in light and dark | `feat:` | — |

A and B are independent and touch different files. C goes last so the palette is judged on the
screen that will actually carry it rather than on the Items list.

There is no gate. Nothing in this phase is unmeasured platform behaviour; the risk is a permission
or a string left behind, which the readings below catch.

---

## 1. Reminders gone

`docs/optional-modules.md` § *Reminders and WorkManager* is the list, file by file. Two parts of it
are easy to miss:

- WorkManager merges `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` and
  `RECEIVE_BOOT_COMPLETED` without the source declaring them. Removing it removes all four, and
  `aab-permissions.py`'s EXPECTED table loses them in the same commit.
  [ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md) wants `ACCESS_NETWORK_STATE` gone
  for good; this is where it goes.
- Settings' *Reminders* section is the template's worked example of `PLAN.md` rule 4. Phase 2's
  location ask copies its shape, so read it before deleting it.

ADR-0003 gets a dated amendment: Kozica schedules nothing, and both mechanisms are gone.

## 2. Items gone, one screen

`docs/optional-modules.md` § *The placeholder domain* lists the files. The home screen is an empty
map placeholder — a neutral surface with the app name and one line of copy — and a top-bar icon to
Settings. `scripts/edge-to-edge.py`'s SCENES walk this screen and Settings, nothing else.

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

---

## Tests

None. This phase introduces no value that computes or bounds something safety-relevant (`PLAN.md`
rule 3).

## Documents this phase amends

- ADR-0003: the amendment above.
- `docs/privacy-policy.md`: the notifications line goes with reminders.
- `docs/play-app-content.md`: the reminder permissions' rows, in the same commit as the policy.
- `scripts/aab-permissions.py`: EXPECTED, to what is left.

## Known gaps, deliberately

- **The placeholder mark ships to internal testing.** Nobody but the owner sees it before the door.
- **`support@example.com` stays compiled in** until the dedicated account exists. No stranger has a
  build before phase 3, which owns the address.

## Readings

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The release bundle declares none of the reminders' permissions | `./gradlew bundleRelease && python3 scripts/aab-permissions.py` | |
| R2 | The app opens on the placeholder screen, in English and in Polish | `./gradlew installDebug`, switch language in Settings | |
| R3 | The palette in light and dark, on the phone | `./gradlew installDebug`, system dark mode on and off | |
| R4 | Nothing says "Item" anywhere | `grep -rli 'item' app/src/*/res/values*/strings.xml`, and a walk through every screen | |
