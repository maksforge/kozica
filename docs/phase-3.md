# Phase 3 — A stranger can call for help, read the avalanche bulletin and the weather from the map

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds the hand-offs [ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md) promised "before
the first stranger installs the app", and amends that ADR: its four hand-offs are Poland's, and since
phase 2b the Regions cross into Slovakia and Czechia. `CONTEXT.md` is owed nothing: the Safety screen
is a screen, not a domain term.

**What closing this phase means.** Today a hiker who needs mountain rescue has to know the number,
and Kozica says nothing about the avalanche danger or the weather it cannot fetch. After this phase a
button on the map opens the Safety screen: for Poland, Slovakia and Czechia in turn, a row that opens
the dialler with that country's mountain-rescue number already typed, a row that opens its rescue app
(or the app's Play page, when it is not installed), and rows that open the avalanche bulletin and the
mountain weather in the browser. 112 sits above them. The difference a device can be asked about:
every row on the test phone opens what its hint says it opens, and nothing is dialled until the user
presses the dialler's own call button.

**`PLAN.md`'s paragraph is wrong in two places, and is amended with this file.** It lists Poland's
four hand-offs only; phase 2b's Regions put half the Tatry's Trails, the Slovak Pieniny and Beskidy
and the Czech Karkonosze on the map, where 985 and Ratunek are the wrong first call. Decided with the
owner on 2026-10-22: **a section per country, in a fixed order, chosen by nobody's position.** And
three of its other items are already done or are the owner's alone: the privacy policy and the App
content answers closed in phase P, and the support address is a `DOD.md` item that waits on the
owner's choice of account, not on code.

---

## 0. What this phase inherits

- **The hand-off idiom.** `ui/common/Intents.kt`'s `startActivitySafely` reports a launch that found
  nothing rather than crashing, and `ui/support/SupportScreen.kt` turns that into an inline line
  naming what is missing. Its `rateOnPlay` is the Play-pinned `https://` link with a browser fallback,
  a HyperOS device finding that applies to every Play page this phase opens.
- **`<queries>` for `https`.** The manifest already declares it, so a browser answers; package
  visibility for the three rescue apps is new (§ 2).
- **A map with two buttons in its top row**, Regions and Settings, as `FilledTonalIconButton`s.
- **No `CALL_PHONE`, and none wanted.** `ACTION_DIAL` needs no permission; `PLAN.md` rule 4's table
  gains no row.

## What is in, and what is deliberately not

**In:** the Safety screen with its three country sections and 112; the map's button for it; package
visibility for the three apps; one sentence in the store listing's full description, in both
languages. ADR-0008's amendment.

**Not in, and each with a reason rather than a phase:**

- **Choosing the country by position.** It would compute a safety value from a fix that may be
  missing, stale, or a few hundred metres from the border, and a wrong answer hands a hiker the wrong
  number. Three fixed sections cost a scroll. Decided with the owner.
- **Calling directly (`ACTION_CALL`).** It needs `CALL_PHONE`, an ask with no moment that is not
  already an emergency, and a pocket can press a row. The dialler with the number typed costs one tap.
- **National parks' closure pages.** TPN, KPN, BdPN, PPN, KRNAP and TANAP each keep one, the Slovak
  ones on deep links that move. A dozen rows that only some Regions need would bury the four that
  every Region needs. The avalanche bulletin and the weather are the two a hiker cannot do without.
- **Showing the bulletin's level or the weather in the app.** ADR-0008's first alternative, rejected
  there.
- **Ukraine's and Germany's services.** No Region crosses those borders (phase 2b).

**The tempting one** is putting 985 on the map itself, one tap from anywhere, a red button. It looks
like care. But it adds a control to every map screenshot to save one tap on a screen that opens
instantly, and a red button that is pressed by accident teaches people to ignore it. The Safety
button is in the top row with the other two.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The Safety screen** — the three sections and 112, the map's button, package visibility, the copy in English and Polish | `feat:` | — |
| **B** | **The listing says so** — one sentence in each full description | `docs:` | A |

No gate: every target is an ordinary intent that the platform resolves or refuses, and
`startActivitySafely` already handles the refusal. What could go wrong is a wrong number or a dead
link, and that is checked against the source (§ 3) and read on the phone (R1), not gated.

**A cuts a release on its own.** A stranger is not yet installing, so A reaches the internal track as
an ordinary `feat:` release, and B rides the release after it or the same one.

---

## 1. The Safety screen

A detail screen, `ui/safety/`, pushed from the map's top row: **Regions, Safety, Settings**, with
Material's `emergency` icon. No `ViewModel`: the only state is whether each app is installed, read on
every resume (the user installs it and comes back), and which tap last found nothing, as on Support.

From the top:

1. One line: Kozica works offline; these rows open other apps, and everything but the dialler needs
   signal.
2. **112**, a dial row, "if no other number connects".
3. A section per country, **Poland, Slovakia, Czechia**, each with the same four kinds of row, in
   this order: the rescue number, the rescue app, the avalanche bulletin, the mountain weather.

| Kind | Opens | Hint shows |
| --- | --- | --- |
| Rescue number | `ACTION_DIAL` with `tel:<number>` | the number, and that the call is placed by the user |
| Rescue app | the app if installed; otherwise its Play page, pinned to Play, then the browser | "installed", or "not installed — install it before you set out" |
| Avalanche bulletin | `ACTION_VIEW` on the page | the page's host |
| Mountain weather | `ACTION_VIEW` on the page | the page's host |

**The host in the hint** tells the user where a tap goes before they make it, and gives them
something to type on another device when this one has no browser.

**The numbers and URLs are code, not strings.** They are not prose, a translator must never be able
to change them, and `SafetyTest` (§ Tests) reads them from one table. The rows' words are strings.

**Not installed is a state, not an error.** Each rescue app's hint says whether it is installed,
read with `PackageManager.getLaunchIntentForPackage`, which needs the three `<package>` entries in
`<queries>` (from API 30, package visibility). A missing dialler or browser is an error, and says so
inline, as on Support.

## 2. The targets

Checked against each body's own site or Play page on 2026-10-22. ADR-0008's consequence holds: a
target that moves is fixed in an ordinary release.

| Country | Rescue number | Rescue app | Avalanche bulletin | Mountain weather |
| --- | --- | --- | --- | --- |
| Poland | **985**, and **+48 601 100 300**, the same service on an ordinary mobile number, which also answers a foreign SIM (gopr.pl, topr.pl) | Ratunek, `com.pagasolutions.emergencycall_pl` | TOPR, `https://lawiny.topr.pl` for the Tatry; GOPR, `https://lawiny.gopr.pl` for the Karkonosze, Babia Góra, the Bieszczady and the simplified bulletins | IMGW, `https://gory.imgw.pl` |
| Slovakia | **18 300** (hzs.sk) | Horská záchranná služba, `hzs.sk.hzs` | `https://www.laviny.sk` | SHMÚ's Tatras forecast, `https://www.shmu.sk/sk/?page=107` |
| Czechia | **+420 1210** (horskasluzba.cz, published in this form) | Záchranka, `com.medicalit.zachranka`, which calls Horská služba | Horská služba, `https://www.horskasluzba.cz/cz/pocasi-na-horach/lavinova-predpoved/krkonose` | ČHMÚ, `https://www.chmi.cz/predpoved-pocasi/lyzarska-strediska-a-hory/` |

Poland therefore has two number rows and two bulletin rows; the other two countries one of each.
**SHMÚ's forecast covers the Tatras alone**, and no Slovak forecast for the Pieniny or Beskidy was
found; the row says "Tatras" rather than promising more. **The Czech bulletin row opens the
Krkonoše**, the one Czech range a Region reaches that has a bulletin of its own; Jeseníky is outside
every Region.

## 3. The copy

English, then Polish per [`translator-brief.md`](translator-brief.md). Country names are translated;
the services' and apps' names are not.

| Key | English |
| --- | --- |
| `safety_title` | Safety |
| `safety_intro` | Kozica works without signal. These rows open other apps and pages, and all but the dialler need signal. |
| `safety_112` | Emergency number 112 |
| `safety_112_hint` | If no other number connects. Opens the dialler; you place the call. |
| `safety_rescue` | Call mountain rescue |
| `safety_rescue_hint` | %1$s. Opens the dialler; you place the call. |
| `safety_rescue_foreign` | Call mountain rescue from a foreign phone |
| `safety_app_installed` | Installed |
| `safety_app_missing` | Not installed. Install it before you set out. |
| `safety_avalanche` | Avalanche bulletin |
| `safety_weather` | Mountain weather |
| `safety_opens_browser` | %1$s, in your browser |
| `safety_no_dialler` / `safety_no_browser` | No dialler on this phone. / No browser on this phone. |

---

## Tests

**`SafetyTest`, for a safety value** (`PLAN.md` rule 3). The numbers are not computed, but they are
the one thing on the screen whose error is the failure the app exists to prevent, and they are
edited by hand. It holds every rescue number's `tel:` URI to the table in § 2, digits only after an
optional `+`, and every country to exactly one rescue row and one app. It fails in the direction of a
number edited by accident, which no build and no reading on a phone without a SIM would notice.

## Documents this phase amends

- **`PLAN.md`**: phase 3's paragraph, with this file: three countries, and what is already done.
- **ADR-0008**: a dated amendment with this file: the hand-offs per country, and the reasons above.
- **`docs/store-listing.md`**: one sentence in each full description (B).
- **`DOD.md`**: "Write the listing" is ticked; it was written in phase P and has had release notes
  since 0.1.0.

## Known gaps, deliberately

- **Whether 985 connects on a foreign SIM in Poland is not verified**, which is why the Polish section
  also has +48 601 100 300. The same question for 18 300 has no published answer; 112 is above it.
- **Ratunek's Play listing was last updated in April 2025.** It is still the app GOPR's site links.
- **The test phone has no SIM**, so no row is ever dialled in a reading. The dialler opening with the
  number typed is the whole claim.

## Readings

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | Every row on the test phone opens what its hint says: the dialler with the number typed, Play on each app's page, the browser on each page | `adb -s mnbm7hw8ivdqzx9l exec-out screencap -p` per row; `adb shell dumpsys activity activities \| grep -m1 mResumedActivity` | |
| R2 | An installed rescue app opens, and its hint reads "Installed" after a return from Play | install one app over Wi-Fi from its Play page, back to Kozica | |
