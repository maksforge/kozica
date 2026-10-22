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
four hand-offs only; phase 2b's Regions put half the Tatry's Trails and the Slovak Pieniny and
Beskidy on the map, and on the Czech side the Karkonosze, Góry Izerskie, Broumovsko and Góry
Orlickie, the Jesioniki with Śnieżnik, and Beskid Śląsko-Morawski (point-in-polygon on
`scripts/regions/*.poly`), where 985 and Ratunek are the wrong first call. Decided with the owner on
2026-10-22: **a section per country, in a fixed order, chosen by nobody's position.** And its other
items are already done: the listing, the privacy policy and the App content answers in phase P, the
support address on 2026-10-20 (`DOD.md`).

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
visibility for the three apps, with the manifest's `<queries>` comment amended to say what every
entry is for, since it now claims they exist only for mail, Play and the browser; the screen's
`scripts/edge-to-edge.py` scenes, `safety` and `safety-bottom` (scrolled to the end, since three
sections and 112 scroll); one sentence in the store listing's full description, in both languages.
ADR-0008's amendment.

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
link, and that is checked against the source (§ 2) and read on the phone (R1), not gated.

**A cuts a release on its own.** A stranger is not yet installing, so A reaches the internal track as
an ordinary `feat:` release.

**B closes when its sentence is merged into `docs/store-listing.md`.** No release carries it to Play:
`publish-play.yml` sends the bundle and its mapping only, and the description changes only on a run
of `publish-play-production.yml` with `update_listing: true`, which needs *Manage store presence*
([`RELEASING.md`](RELEASING.md)). That push is the owner's, and one of the door's items below.

## The door to closed testing

`PLAN.md` opens closed testing when this phase closes and the items below, from `DOD.md`'s *Before
the first upload*, are done. Those still open on 2026-10-22:

- **The developer's:** checkpoint E's refresh reading ([`phase-2b.md`](phase-2b.md) § 6), which can
  only be taken on the closed track itself. Done on 2026-10-22: nothing more to delete (phase 0
  kept Room, photos and backup for phase 6), and the four `aab-*.py` gates pass on every release
  bundle in `publish-play.yml` (`DOD.md`).
- **The owner's:** the mark and its provenance (`art/`); the licence in `LICENSE` and `README.md`;
  the closed test and its testers; and pushing the listing with B's sentence.
- **Not a gate:** the dormant `items` table, which phase 5's `MIGRATION_1_2` replaces under the
  standing schema gate.

**What the door freezes** is what a tester's install keeps. From the first one, deleting Room, photos
or backup loses a tester's data instead of costing nothing, which is why the deletion comes first. The
`applicationId` and the upload key were fixed when Play took the first bundle. And a build on a tester's
phone keeps its compiled-in support address and Safety targets, so a target that moves is put right
for them only by an update.

---

## 1. The Safety screen

A detail screen, `ui/safety/`, pushed from the map's top row: **Regions, Safety, Settings**, with
Material's `emergency` glyph as one vector drawable, `ic_emergency.xml`, beside `ic_download.xml` and
`ic_my_location.xml`: the app ships the core icon set only, and `material-icons-extended` is not
added for one glyph. No `ViewModel`: the only state is whether each app is installed, read on every
resume (the user installs it and comes back), and which tap last found nothing, as on Support.

From the top:

1. One line: Kozica works without signal; these rows open other apps and pages, and a call or a page
   needs signal.
2. **112**, a dial row for all three countries and any network: for a hiker who is not sure which
   country they are in, and for when no other number connects.
3. A section per country, **Poland, Slovakia, Czechia**, each with the same four kinds of row, in
   this order: the rescue number, the rescue app, the avalanche bulletin, the mountain weather.
   Poland's second number row is the fallback for when 985 does not connect.

| Kind | Title | Opens | Hint shows |
| --- | --- | --- | --- |
| Rescue number | "Call mountain rescue", or the fallback's "if 985 does not connect" | `ACTION_DIAL` with `tel:<number>` | the number as published, and that the phone app opens with it typed and the user places the call |
| Rescue app | "Rescue app: " and the app's name | the app if installed; otherwise its Play page, pinned to Play, then the browser | whether it is installed, and that it must be set up before the user sets out |
| Avalanche bulletin | "Avalanche bulletin, " and the issuer | `ACTION_VIEW` on the page | the page's host |
| Mountain weather | "Mountain weather, " and the issuer; ČHMÚ's "Weather model charts for the mountains" | `ACTION_VIEW` on the page | the page's host |

**The host in the hint** tells the user where a tap goes before they make it, and gives them
something to type on another device when this one has no browser.

**The numbers, URLs, app names and issuers are code, not strings.** They are not prose, a translator
must never be able to change them, and `SafetyTest` (§ Tests) reads the numbers from one table. The
rows' words are strings.

**The issuer names the range** where a page covers only some: TOPR (Tatry), GOPR (Karkonosze, Babia
Góra, Bieszczady), SHMÚ (Tatry), Horská služba (Karkonosze) and Horská služba (Jesioniki). The ranges
are in the Polish the Regions' names use (`region_*`, `translatable="false"`), so the qualifier lives
in code with its URL and needs no string.

**Not installed is a state, not an error.** Each rescue app's hint says whether it is installed,
read with `PackageManager.getLaunchIntentForPackage`, which needs the three `<package>` entries in
`<queries>` (from API 30, package visibility). A missing phone app or browser is an error, and says
so inline, as on Support; a missing Play and browser reuses Support's `support_no_play`.

**Installed is not ready.** HZS's app registers the user's phone number with an SMS code, and
registration needs signal, so an app installed and never opened is no use on the ridge. Neither hint
promises readiness: both say to set the app up before setting out. What each app asks at first run is
read in R2.

## 2. The targets

Checked against each body's own site or Play page on 2026-10-22. ADR-0008's consequence holds: a
target that moves is fixed in an ordinary release.

| Country | Rescue number | Rescue app | Avalanche bulletin | Mountain weather |
| --- | --- | --- | --- | --- |
| Poland | **985**, and **+48 601 100 300**, the same service on an ordinary mobile number, for any phone 985 does not reach: a foreign SIM, or a Polish one roamed onto a Slovak or Czech network on the ridge (gopr.pl, topr.pl) | Ratunek, `com.pagasolutions.emergencycall_pl` | TOPR (Tatry), `https://lawiny.topr.pl`; GOPR (Karkonosze, Babia Góra, Bieszczady), `https://lawiny.gopr.pl`, which also carries the simplified bulletins | IMGW, `https://gory.imgw.pl` |
| Slovakia | **18 300** (hzs.sk) | Horská záchranná služba, `hzs.sk.hzs` | HZS, `https://www.laviny.sk` | SHMÚ (Tatry), `https://www.shmu.sk/sk/?page=107` |
| Czechia | **+420 1210** (horskasluzba.cz, published in this form) | Záchranka, `com.medicalit.zachranka`, which calls Horská služba | Horská služba (Karkonosze), `https://www.horskasluzba.cz/cz/pocasi-na-horach/lavinova-predpoved/krkonose`; Horská služba (Jesioniki), `https://www.horskasluzba.cz/cz/pocasi-na-horach/lavinova-predpoved/jeseniky` | ČHMÚ, `https://www.chmi.cz/predpoved-pocasi/meteogramy-aladin/hory-a-lyzarska-strediska` |

Poland therefore has two number rows and two bulletin rows, Czechia two bulletin rows, Slovakia one
of each. **SHMÚ's forecast covers the Tatras alone**, and no Slovak forecast for the Pieniny or
Beskidy was found; the row says "Tatry" rather than promising more. **Horská služba publishes two
avalanche bulletins, the Krkonoše's and the Jeseníky's**, and a Region reaches each
(`sudety_zachodnie`, `sudety_wschodnie`); the other Czech ranges on the map have none. **ČHMÚ's row is
model output**: meteograms of its ALADIN model for each mountain range, not a written forecast like
IMGW's or SHMÚ's, so its title says so. The URL is the one the old
`…/predpoved-pocasi/lyzarska-strediska-a-hory/` redirects to.

## 3. The copy

English, then Polish per [`translator-brief.md`](translator-brief.md). Country names are translated;
the services', apps' and ranges' names are not. The user-facing word is *phone app*
(*aplikacja Telefon*), never *dialler*, and no string needs the user's gender (§ 6.2). The map's
button reads `safety_title` as its content description.

| Key | English | Polish |
| --- | --- | --- |
| `safety_title` | Safety | Bezpieczeństwo |
| `safety_intro` | Kozica works without signal. These rows open other apps and pages; a call or a page needs signal. | Kozica działa bez zasięgu. Poniższe opcje otwierają inne aplikacje i strony; połączenie i strona wymagają zasięgu. |
| `safety_emergency` | Emergency number, in all three countries | Numer alarmowy we wszystkich trzech krajach |
| `safety_emergency_hint` | %1$s, on any network: when you are not sure which country you are in, or no other number connects. Opens the phone app with the number typed; you place the call. | %1$s, w każdej sieci: gdy nie wiesz, w którym kraju jesteś, lub inny numer się nie łączy. Otwiera aplikację Telefon z wpisanym numerem; połączenie nawiązujesz samodzielnie. |
| `safety_poland` / `safety_slovakia` / `safety_czechia` | Poland / Slovakia / Czechia | Polska / Słowacja / Czechy |
| `safety_rescue` | Call mountain rescue | Wezwij ratowników górskich |
| `safety_dial_hint` | %1$s. Opens the phone app with the number typed; you place the call. | %1$s. Otwiera aplikację Telefon z wpisanym numerem; połączenie nawiązujesz samodzielnie. |
| `safety_rescue_fallback` | Call mountain rescue if %1$s does not connect | Wezwij ratowników górskich, gdy %1$s się nie łączy |
| `safety_rescue_fallback_hint` | %1$s, from a Polish or a foreign SIM, roaming included. Opens the phone app with the number typed; you place the call. | %1$s, z telefonu z polską lub zagraniczną kartą SIM, także w roamingu. Otwiera aplikację Telefon z wpisanym numerem; połączenie nawiązujesz samodzielnie. |
| `safety_rescue_app` | Rescue app: %1$s | Aplikacja ratunkowa: %1$s |
| `safety_app_installed` | Installed. Open it before you set out to check it is set up. | Zainstalowana. Otwórz ją przed wyjściem w góry i sprawdź, czy jest skonfigurowana. |
| `safety_app_missing` | Not installed. Install it from Google Play and set it up before you set out. | Niezainstalowana. Zainstaluj ją z Google Play i skonfiguruj przed wyjściem w góry. |
| `safety_avalanche` | Avalanche bulletin, %1$s | Komunikat lawinowy, %1$s |
| `safety_weather` | Mountain weather, %1$s | Pogoda w górach, %1$s |
| `safety_weather_model` | Weather model charts for the mountains, %1$s | Wykresy modelu pogody dla gór, %1$s |
| `safety_opens_browser` | %1$s, in your browser | %1$s, w przeglądarce |
| `safety_no_dialler` | No phone app on this phone to open. | Na tym telefonie nie ma aplikacji Telefon do otwarcia. |
| `safety_no_browser` | No web browser on this phone to open. | Na tym telefonie nie ma przeglądarki do otwarcia. |

`safety_rescue_fallback`'s argument is the country's first number as shown (985), and every
`%1$s` in a hint is a number or a host.

---

## Tests

**`SafetyTest`, for a safety value** (`PLAN.md` rule 3). The numbers are not computed, but they are
the one thing on the screen whose error is the failure the app exists to prevent, and they are
edited by hand. It holds every dialled number to the table in § 2 — 112; Poland exactly 985, then
+48 601 100 300; Slovakia 18 300; Czechia +420 1210 — as digits after an optional `+`, and every
country to at least one number, led by the one a local phone dials rather than the fallback. It
fails in the direction of a number edited by accident, which no build and no reading on a phone
without a SIM would notice.

## Documents this phase amends

- **`PLAN.md`**: phase 3's paragraph, with this file: three countries, what is already done, and
  that the door is `DOD.md`'s list.
- **ADR-0008**: a dated amendment with this file: the hand-offs per country, and the reasons above.
- **`docs/store-listing.md`**: one sentence in each full description (B).
- **`DOD.md`**: "Write the listing" is ticked; it was written in phase P and has had release notes
  since 0.1.0.

## Known gaps, deliberately

- **Whether 985 connects on a foreign SIM in Poland is not verified**, which is why the Polish section
  also has +48 601 100 300. The same question for 18 300 has no published answer; 112 is above it.
- **Ratunek's Play listing was last updated in April 2025.** It is still the app GOPR's site links.
- **The test phone has no SIM**, so no row is ever dialled in a reading. The phone app opening with
  the number typed is the whole claim, and no rescue app's SMS registration is completed there.
- **Whether a rescue app asks for a phone number before it works is not read.** Registration needs
  an SMS and the test phone has no SIM; only the owner's phone could read it. § 1's "registers the
  user's phone number with an SMS code" for HZS is not confirmed on a phone.
- **ČHMÚ has no written mountain forecast to link.** Its range pages hold a slot for one that read
  "not available" on 2026-10-22; the row opens the meteograms and says so.

## Readings

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | Every row on the test phone opens what its hint says: the phone app with the number typed, Play on each app's page, the browser on each page **loaded**, not a "no connection" error that a dead link would also show. Wi-Fi on for the reading and off after; the phone stays in airplane mode | `adb -s mnbm7hw8ivdqzx9l shell svc wifi enable`; before every tap `adb -s mnbm7hw8ivdqzx9l shell dumpsys activity activities \| grep -m1 mResumedActivity` must name Kozica, else abort, and `adb -s mnbm7hw8ivdqzx9l shell monkey -p io.github.srednimax.kozica.debug -c android.intent.category.LAUNCHER 1` brings it back; `adb -s mnbm7hw8ivdqzx9l exec-out screencap -p` per row; per dial row `adb -s mnbm7hw8ivdqzx9l shell uiautomator dump /sdcard/u.xml` then `adb -s mnbm7hw8ivdqzx9l shell cat /sdcard/u.xml \| grep -o 'text="[^"]*" resource-id="com.google.android.dialer:id/digits"'`, and after it `adb -s mnbm7hw8ivdqzx9l shell am force-stop com.google.android.dialer`; `adb -s mnbm7hw8ivdqzx9l shell svc wifi disable` | 2026-10-22, test phone, API 30, debug build of `main` at 7bb2c31, Polish, Wi-Fi on in airplane mode. All 16 rows: the phone app opened with `112`, `985`, `+48 601 100 300`, `18 300` and `+420 1210` typed (read from the dialler's `digits` field), nothing dialled; Play opened Ratunek (listed under Polkomtel), Horská záchranná služba and Záchranka (as Play's sheet); the browser loaded lawiny.topr.pl, lawiny.gopr.pl, gory.imgw.pl (landing on `/zakopane`), laviny.sk (`/bulletin/latest`), shmu.sk's Tatras forecast, both horskasluzba.cz pages and ČHMÚ's ALADIN meteograms. Out of season, TOPR's last bulletin is 2026-09-28 and laviny.sk and both Horská služba pages say none is available: the sources' own pages, not errors. **One hazard of the apparatus, not the app:** BACK from the dialler leaves it on its suggested contacts, and a blind tap meant for the next row landed on one; the dialler refused it with "Wyłącz tryb samolotowy, by zadzwonić". Airplane mode is no guard: it does not stop emergency numbers, so the same mis-tap on the call button with `112` typed would have placed a real emergency call, and on the owner's phone, with a SIM and real contacts, it calls a person. Hence the force-stop after each dial row and the abort unless Kozica is in front. Wi-Fi off again after |
| R2 | An installed rescue app opens, and its hint reads "Installed" after a return from Play; each app's first run | Ratunek installed over Wi-Fi from Kozica's row through Play, then opened from the row; the other two from their Play pages opened directly with `adb -s mnbm7hw8ivdqzx9l shell am start -a android.intent.action.VIEW -d "https://play.google.com/store/apps/details?id=<package>" -p com.android.vending`, launched once with `adb -s mnbm7hw8ivdqzx9l shell monkey -p <package> -c android.intent.category.LAUNCHER 1`, `adb -s mnbm7hw8ivdqzx9l exec-out screencap -p`, uninstalled | 2026-10-22, test phone. Ratunek: on return from Play its row read "Zainstalowana. Otwórz ją przed wyjściem w góry…" without a restart, and the row then opened Ratunek, whose first screen is a location permission prompt, left unanswered. Horská záchranná služba and Záchranka were not installed through their rows, so their "installed" hint is not read. Horská záchranná služba's first screen: "Ustawić aplikację" above a red "Natychmiast przywołaj pomoc". Záchranka's: "Początek" above a red "Muszę zadzwonić po pomoc", with the note "Wysyła SMS alarmowy bez danych zdrowotnych i innych informacji". Whether any asks for a phone number: *Known gaps* |
