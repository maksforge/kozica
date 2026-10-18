# Roadmap

Sequence and status only. The reasoning behind a decision lives in [`docs/adr/`](adr/); the words
live in [`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in
[`CLAUDE.md`](../CLAUDE.md).

**This file is the record, not the worklist.** What is still open — the boxes to tick, with the
device state and the commands to read it — lives in [`DOD.md`](DOD.md), which stays short enough to
open every session. Read the phase you are in here; read `DOD.md` to know what to do next.

**A phase being planned or built gets its own file** — `phase-N.md` beside this one — so working on
it costs the phase rather than the whole history. This file stays deliberately thin: one paragraph
per phase, no task lists. The detail is written when the phase opens, not now. The app this template
was extracted from and the first app built from it settled on exactly this split independently.
[`phase-template.md`](phase-template.md) is the shape a phase file settled into; copy it on the day.

## Status

- [x] **Phase 0** — Skeleton: the template's reminders and Items gone, the ochre palette, one map screen
- [ ] **Phase P** — The pipeline: everything set up; the first *automatic* upload still to be watched
- [ ] **Phase 1** — The data reaches the phone: one Region built in CI and delivered by Play
- [ ] **Phase 2** — The map: every Region drawn, Trails in their colours, "you are here"
- [ ] **Phase 3** — Safe for a stranger ← **the door: closed testing opens here**
- [ ] **Phase 4** — Elevation: contours and hillshading
- [ ] **Phase 5** — Route planning, and search by name
- [ ] **Phase 6** — The hike log
- [ ] **Phase N** — Ship shape: screenshots, production access, **1.0**

---

## Rules that cut across every phase

### 1. Decide where the door is, early

**The door** is the first build a stranger installs — closed testing, for a new personal Play
account. Everything that reaches a tester's phone is harder to change afterwards: a stored key, a
permission, a `minSdk`, the `applicationId`. So name the phase the door sits after, and write down what
it freezes. The closed-test window is calendar time and other people's replies, so later phases can
run while it waits.

**Do not plan a decision on the testers' answers.** The first app built from this template left two
phases' open questions — defaults, whether a behaviour was too clever — to be asked of its closed-test
testers. The survey that did go out asked none of them, and both closed *unanswered, on the values
that shipped*. What carried
the decisions was the device readings. Decide on a reading, ship a default, and let the mail hand-off
(`ui/support/`, live from the door) tell you if it is wrong.

### 2. When the two halves of a phase cost wildly different amounts, they are two phases

The first app built from this template split every one of its middle phases along cost or along
kind, because in each case a bundle of a cheap item with an expensive one — or a feature with the
safety work it depends on — hid which half was blocking. However neatly one sentence describes both, two costs are two phases.

### 3. A phase proves itself on the phone, not in a test

Every phase ends with readings taken off a real device and written into its `phase-N.md`, with the
command that produced them. A green test run says the code does what the test asked; the phone says
what the ROM allowed. Vendor ROMs are where those two part company.

**But the rule has a second clause, and leaving it out is how a roadmap arrives at no tests at all.**
Most of the risk in an app this size is device behaviour, and none of that is reachable by a unit
test — but the pure functions that *compute or bound a safety value* are the exception, and each of
them fails in exactly the direction the app exists to prevent. So: device behaviour is proven by
measurement; a safety value is proven by test, and **the phase that introduces one introduces its
test**. For the first app built from this template that came to five tests across the whole roadmap
— a window that crosses midnight, two deadlines resolved against a clock, a ramp whose bottom end
turns the backlight off over a live touchscreen, the width of a window that swallows touches, and a
restored intent that had to be refused. Not a testing culture. Five tests standing in front of the
properties `CLAUDE.md` calls load-bearing.

⚠ **Reconcile the list when the roadmap closes, and expect the count to be wrong.** That app planned
four and shipped five: the fifth arrived because a device reading found a fault no test had been
written for. That is the rule working, not an exception to it — the top of a ramp is not visible on a
device until it strands somebody.

### 4. Ask for nothing before the feature that needs it

An app of this shape ends up asking the user for three or four things, and most of them are hand-offs
to settings screens whose result cannot be read back reliably — `appops` has been caught lying about
`SYSTEM_ALERT_WINDOW` on a real phone. So the sequence is a rule rather than a phase:

**Every ask waits for the moment the feature needing it is switched on. The phase that introduces an
ask owns its hand-off, its re-read, and what the user sees when the answer is no.**

Kozica's asks — the moment each is made and the phase that owns it — are this table, so a later
phase cannot quietly move one forward to "get it out of the way".

| Ask | Moment it is made | Phase that owns it |
| --- | --- | --- |
| Precise location | The first tap on "show me where I am" — never at launch. Fine and coarse are asked together, and from API 31 the user may grant approximate only: a third answer the map has to handle | 2 |
| Notifications | Starting a hike recording: its notification is the stop button | 6 |
| Background location | Never: a `location` foreground service started from the visible app keeps while-in-use access | 6 |
| Battery exemption, Xiaomi autostart | Never, unless a reading shows a recording dying without them | 6 |
| *(none for photos)* | The Photo Picker and the system camera's `TakePicture` intent need no permission | 6 |


**The third clause is the one that gets left out, and it is the one that generates support mail.**
These denials nearly all fail *silently*: a denied notification permission leaves a foreground
service running with no way to stop it, a denied autostart leaves reboot restore never firing, a
denied battery exemption leaves a schedule never firing. In each case the app knows and the user does
not. A re-read tells the app; **nothing tells the person holding the phone** unless a screen does.
`ui/common/Surfaces.kt`'s `WarningBanner` is the idiom for a state the app can re-read; a permanent
row that makes no claim is the idiom for one it cannot.

**Autostart is the case where even the re-read is out of reach.** It is not an appop and there is no
API for it, so no code of yours can tell a granted state from a denied one — only
`scripts/device-gate.py`, over `adb`, run by you. The clause survives it: the phase still owns the
hand-off and still owns saying what a denial costs, without claiming to know the answer. What changes
is only who can check.

**The same logic applies to side effects, not only to permissions.** A feature that makes one of the
phone's own controls go inert is a system control that appears broken, caused by you. The phase that
introduces the behaviour owns saying so.

---
---

## Phase 0 — Skeleton

Everything the template shipped for an app that is not this one goes: reminders and WorkManager, the
Items screens and the tab bar built around them. Room, photos and backup stay, because the hike log
needs all three. The app opens on one screen, the map's place, with Settings behind an icon, in
the chamois-ochre palette, in English and Polish. The mark stays a placeholder until its concept is
drawn. [`phase-0.md`](phase-0.md).

## Phase P — The pipeline

Done but for one reading: the repository, the upload key, the Play entry, the secrets and the service
account are in place, and version 0.1.0 reached internal testing by hand. What is left is watching
`publish-play.yml` upload a release-please release by itself
([`RELEASING.md`](RELEASING.md)).

## Phase 1 — The data reaches the phone

`scripts/build-map-data.py` builds one Region, the Tatry, in CI from the pinned extract
([ADR-0007](adr/0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md)), and Play delivers it
to the phone as an on-demand asset pack. The gate is the file format: what an update costs after a
one-Trail edit, measured through a build Play delivers, because Play does not document how it patches
asset packs. Nothing has to be drawn yet; the app only proves it can find and read the files. Of
ADR-0007's decisions for "the map phase", this one owns the file format.

## Phase 2 — The map

Every Region built and downloadable from a picker, with "Download all". The map draws Trails in their
marking colours and symbols, Paths apart from them, Huts, Shelters, peaks, passes, springs,
viewpoints, the way in and out, the land, national-park boundaries and the state border, and the
user's position, accuracy and heading. The OSM credit and the data date sit on every map screen.
It owns ADR-0007's other two decisions, fixed when it opens: the list of Regions and the filter. It
also takes the palette reading phase 0 could not ([`phase-0.md`](phase-0.md) § 3).

## Phase 3 — Safe for a stranger

What a stranger needs before installing: the listing, the privacy policy, a real support address, the
App content answers, and the hand-offs for live information — mountain rescue (985), Ratunek, the
avalanche bulletin and the weather ([ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md)).
Closed testing opens when this phase closes; the next three phases are built while it runs.

## Phase 4 — Elevation

Contours and hillshading from a DEM, through a second pipeline with its own source, licence and
attribution. Route planning's walking times come from the same data.

## Phase 5 — Route planning

The user plans a route over Trails and sees its length, ascent and walking time, entirely on the
phone. Search by name arrives here, because picking a start and an end needs it. A second area of
the app arrives with it, so this is the phase that brings a tab bar.

## Phase 6 — The hike log

Record a hike — the track, photos and notes — and look back at it. It brings the `location`
foreground service, with `FOREGROUND_SERVICE_LOCATION` from API 34 and the Console's
foreground-service declaration; the notifications ask; and the first of the user's own data that a
backup has to keep, so Backup returns to Settings.

## Phase N — Ship shape

Screenshots, the production-access application and **1.0**: everything above, in the public's hands.

## Not in this plan

- **A network connection**, ever: no online tiles, weather, sync, accounts or crash reporting of our
  own ([ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md)). Live information is a
  hand-off.
- **Ads, paid features and sign-in.** Kozica is free. An optional tip that unlocks nothing may come
  after 1.0, through Play Billing, only once a purchase works on a phone with Billing's telemetry
  stripped as ADR-0008 requires.
- **Data beyond Poland's border** in 1.0. A Region stops at the state border; cross-border ranges
  would be a new ADR once the file format is settled.
- **Reminders and scheduled work.** Nothing in the plan schedules anything.
