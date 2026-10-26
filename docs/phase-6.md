# Phase 6 — The user records a Hike and looks back at it

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on ADR-0001 (the schema gate, which schema 3 passes), ADR-0002 (every photo through
`MediaFiles`), ADR-0003's amendments (nothing of ours is scheduled, so no reminder and no
notification outside a recording), ADR-0005 (amended at checkpoint E for the backup this phase
ports from binky), ADR-0008 (nothing leaves the phone except by the user's own share) and ADR-0009
(the height grid a Hike's ascent is read from). [`CONTEXT.md`](../CONTEXT.md) gains **Hike**, Polish
UI *wycieczka*, and gives **Track** its row.

**What closing this phase means.** Today the app plans a Route and shows where the user is, and
forgets everything the moment the dot moves. After this phase the user presses Record at Kuźnice,
puts the phone in a pocket, pauses at Murowaniec, takes photos on the way up, presses Stop at Kasprowy
Wierch, and finds the Hike in the Hikes tab with its Track, distance, times, ascent, profile, photos
pinned where they were taken and a note, exportable as GPX, and safe in a backup. The difference a
device can be asked about: on the owner's phone, a walk of two hours or more in a pocket, screen off,
with no exemption granted, comes back as one Hike with no gap longer than two minutes.

**`PLAN.md`'s paragraph bundled two costs, and was split on 2026-10-26 with the owner** (`PLAN.md`
rule 2). This phase is the hike log and GPX export. Following a Route while walking and walking times
from the user's own pace are phase 6b, which needs this phase's recorded Hikes to learn a pace from.
Named Trails and highlighting a whole Trail are phase 6c, a map-data change that needs nothing from
either.

---

## 0. What this phase inherits

- **The app reads fixes through the platform `LocationManager`** (`data/location/PositionFixes.kt`),
  with no Play services. So Google's Activity Recognition is not available, and auto-resume uses a
  hardware sensor instead (§ 3).
- **Precise location is already asked** at the position button (phase 2), and from API 31 the user
  can grant approximate only. Recording reuses the same ask, at its own moment (§ 2).
- **Nothing in the app is scheduled, and it posts no notification of its own.** The only foreground
  service in the bundle is Play Asset Delivery's `dataSync`, whose notification and channel are the
  library's, and whose Console declaration is still open in `DOD.md`. The recording's notification
  is Kozica's first, and `POST_NOTIFICATIONS` is its first notification ask.
- **`aab-permissions.py` forbids `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** (Play restricts it). If the
  gate's verdict 2 needs a battery exemption, it is a hand-off to the system's list, never the
  direct request.
- **The height grid is on the phone** (`relief.pmtiles`, zoom 12, Terrarium in lossless WebP, about
  12 m), but phase 5 never decodes it on the phone: the Trail network carries its heights. A Hike's
  Track goes where no network edge does, so this phase is the first to read the grid on the device.
- **The backup is the template's basic version**: an agent carrying the database and every
  thumbnail, export at two scopes, and restore. The Backup screen exists, but its row left Settings in
  phase 0 until this phase. Binky (`maksforge/binky-app`, its ADR-0005) grew the design this phase
  ports.
- **No tester holds a schema-1 database**, and schema 2 is the saved Routes (phase 5 § 5). Schema 3
  is the second bump since the door, so the whole standing gate applies (`DOD.md`).
- **Nothing is being published to Play during this phase**, by the owner's choice, so the order of
  the checkpoints is not bound to a release.

## What is in, and what is deliberately not

**In:** recording a Hike from the Map tab with a `location` foreground service and its notification;
pause, and auto-resume when the phone starts moving; the Hikes tab with each Hike's Track, distance,
moving and total time, ascent and descent, profile, rename, delete and trim; photos from the camera
during a recording and from the Photo Picker afterwards, pinned to the Track; a note per Hike and a
caption per photo; binky's backup design ported, with the row back in Settings; GPX export of Hikes
and of saved Routes, through the share sheet.

**Not in, and each with a reason rather than a phase:**

- **Searching the Hikes list.** The owner wants it, but only once a list is long enough to need it;
  a new list sorted newest first does not.
- **Following a Route while walking, and a pace of the user's own.** Phase 6b: a pace needs recorded
  Hikes, which this phase creates.
- **Named Trails and highlighting a whole Trail.** Phase 6c: a change to `KozicaProfile` and the
  tiles, a rebuild and every pack delivered again, and nothing to do with recording.
- **Notes pinned to points.** One note per Hike, and a caption on a photo for "this spot was X". A
  second kind of pin is more UI for what a captioned photo already says.
- **Editing points in the middle of a Track, or splitting a Hike.** Trimming the start and the end
  covers the common accident, a forgotten Stop, and the rest is a lot of UI for a rare need.
- **Detecting a car.** A guess either way, and trim is the remedy for the case it would catch.
- **Taking a photo outside a recording.** A photo always belongs to a Hike.
- **A recurring export reminder and the "thumbnails left out" notification** binky has. Both are
  scheduled work or a notification outside a recording, which Kozica does not do (ADR-0003's
  amendments); the Hikes tab's banner says the same thing inside the app (§ 7).
- **Imperial units.** Metric only, as on every signpost the app draws.
- **GPX import.** Out of the plan (`PLAN.md`).

**The tempting one** is a sticky service: `START_STICKY`, or a boot receiver, so that a recording
killed by the ROM restarts by itself. It looks like resilience and holds no location: a foreground
service started from the background has no while-in-use access to location (a platform rule since
API 30, not ours), so it would record an empty Track while promising a full one. A recording the
process lost waits for the next launch, which offers to resume or finish it (§ 2).

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — does a recording survive a pocket on Xiaomi's ROMs? | `chore:` the debug-only recorder; the verdict is a reading | — |
| **B** | Recording: Record, Pause, auto-resume, Stop, the asks, schema 3 | `feat:` | A |
| **C** | The Hikes tab: list, details, ascent, profile, rename, delete, trim | `feat:` | B |
| **D** | Photos and the note | `feat:` | C |
| **E** | Backup, ported from binky, and its row back in Settings | `feat:` | D |
| **F** | GPX export of Hikes and saved Routes | `feat:` | C |

**A comes first** because every later checkpoint is a recording: a service MIUI or HyperOS kills in a
pocket is rethought before anything is built on it. **E comes after D** because photos are what make
backup matter: until D, Auto Backup already carries everything. **F needs only C**, so it can land
anywhere after it. **Schema 3 lands in B and is frozen there**: C, D and E add columns only by
another migration.

---

## 1. The gate: does a recording survive a pocket?

### The question, in the form that can be measured

With a `location` foreground service started from the visible app, asking for a fix every 5 s:
**with the screen off, unplugged and untouched, does it keep storing points for a whole Hike, with no
battery exemption and no autostart?** On the test phone (MIUI 12.5, API 30) for four hours forced
idle, and on the owner's phone (HyperOS, API 36) on a real walk of two hours or more. And does the
significant-motion sensor fire, screen off, when a paused phone starts walking? And what does an
hour of recording cost in battery?

### The apparatus

- **`RecorderService` in `app/src/debug/`**, its manifest entry in the debug manifest: a foreground
  service of type `location` with a plain notification and a Stop action, started by a debug
  Settings row, writing each fix to a `gate_points` table in a debug-only database file. It asks
  `GPS_PROVIDER` for a fix every 5 s with no minimum distance and stores every one with its
  accuracy, the ones worse than 30 m too: § 2's filters would leave a still phone with no points at
  all, and the gate asks whether the service lives, not what a Track keeps. Pause and resume are a
  second debug row; while paused it arms the significant-motion sensor and logs `KozicaRecord
  motion` when it fires. While recording, a `gnss` event at most once a minute, from the GNSS status
  callback, is the heartbeat: it tells a live service with no fix from a dead or frozen one.
- **`scripts/track-gaps.py`**: reads a pulled database (with its `-wal`, checkpointed on the host)
  and prints the point count, the span, every gap over 60 s between fixes of any accuracy and the
  longest; then, apart, the gaps § 2's 5 m and 30 m filters would have left. Gaps are on the clock
  each fix was stored by, and run from a start or resume to the first fix and from the last fix to a
  pause or stop. A gap spanned by heartbeats with no satellite in a fix reads "alive, no fix" and
  is listed apart; one with a satellite in a fix means a fix never stored, and excuses nothing.
  Verdict 1's bar applies to silence, neither a stored fix nor a no-fix heartbeat.
- **The test phone, simulated:** `adb shell dumpsys battery unplug`, screen off with `adb shell input
  keyevent KEYCODE_SLEEP`, `adb shell dumpsys deviceidle force-idle`, four hours, then `dumpsys
  battery reset`. It never leaves USB (the PC's internet runs through it), so a real-walk reading
  there is not possible; indoors, its fixes are weak, which makes it a test of the service and not of
  the Track. Ten minutes with the screen on must store fixes before the idle starts; a desk that gets
  none moves the phone to the window on its cable, since a reading with no fixes at all says nothing.
- **The owner's walk:** the debug build installed beside the Play build, no exemption and no
  autostart granted (read with `scripts/device-gate.py` first), battery level read before and after,
  one pause of a few minutes partway and then walking on without touching the phone.

### The verdicts, decided now rather than argued about later

1. **The walk passes** — no gap over 2 minutes between fixes outside a pause, and the service still
   in the foreground at the end (`adb shell dumpsys activity services`; not the notification, which
   the user can swipe away from API 34 while the service runs on) → no extra asks; B goes ahead as
   planned.
2. **The walk fails** → B also owns a battery-exemption hand-off, made when the user presses
   Record: the system's battery-optimisation list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`)
   and on Xiaomi the app's battery-saver page, re-read with
   `PowerManager.isIgnoringBatteryOptimizations`, and a `WarningBanner` on the recording bar while
   the answer is no. Then the walk is read again with the exemption.
3. **The recording dies even with the exemption** → stop. The phase is rethought with the owner
   before B: nothing is built on a recorder the ROM kills.

**The walk decides; the test phone informs.** A failed R1 beside a passing walk adds no ask: R1 is
read again with the exemption, and *Known gaps* records whether MIUI 12.5 needs one. A failed R1
beside a failed walk is verdict 2.

**The motion sensor's reading is separate, and cannot veto the phase.** It fires → auto-resume as in
§ 3. It is absent, or does not fire with the screen off → the phone falls back to resuming by hand,
and the pause screen says so.

### The verdict, taken *(date)*

*(Which one, the reading behind it.)*

## 2. Recording

**Starting.** A Record button on the Map tab. Pressing it walks through the asks in order, each only
if needed, and starts the service from the visible app:

| State at Record | What happens |
| --- | --- |
| Precise location granted | The recording starts |
| Location never asked, or denied once | The system's ask (fine and coarse together, as phase 2 asks) |
| Approximate only (API 31+) | The system's precise-upgrade ask, as phase 2's banner makes it (phase 2 § 4) |
| Approximate kept at that ask, or location denied for good | Nothing starts; a screen says why, with a button into the app's settings |
| Notifications not granted (API 33+) | The system's ask, once, at Record and never at launch (ADR-0003); a no still records, and the recording bar says the shade has no Stop button |
| The phone's location switched off | The recording starts, stores nothing, and the bar and the notification say so; the gap is a break in the Track |

**While recording.** The Track draws live on the map. A recording bar sits at the bottom of every tab,
with distance and time and the Pause, camera and Stop buttons. The notification carries the same
time and distance, with Pause (or Resume) and Stop. No live ascent: ascent is read once the Hike
ends (§ 5).

**The notification's channel**, "Hike recording", is created at `IMPORTANCE_LOW`: no sound and no
heads-up, for a notification that is up for hours. A channel's importance can only be lowered
afterwards, and only by the user (ADR-0003), so this is decided once. It is Kozica's only channel of its
own (Play Asset Delivery creates one for its `dataSync` service), and every change to the notification, auto-resume's included (§ 3), is a silent update of the same
one.

**Fixes.** `GPS_PROVIDER`, at least 5 s and 5 m apart (Android requires both before it sends the
next one). Fixes worse than 30 m accuracy are dropped, which removes the zig-zags under a cliff or
in a forest. That is 2,000 to 4,000 points for a day's walk. The gate's battery reading may change
these numbers; nothing before it does.

**Storage.** Each fix is a row in `track_points` as it arrives, so nothing recorded is lost to the
process dying. At Stop, the Hike's points are encoded into one compact Track on its `hikes` row (a
segment per stretch between pauses, delta-encoded, about 25 KB for a day) and the rows are deleted.
Rows would cost about ten times that, and Auto Backup's 25 MB holds the database whole or not at
all (§ 7). The encoding is B's to choose, under a round-trip test.

**Stopping.** From the notification or the bar, with a confirmation. The Hike is saved straight
away, named with the date and the nearest named place to where it started, by phase 5's rule for
demanding stretches (peaks, passes and Huts first, within 500 m); the date alone when nothing is that
close. Renaming is in the Hike's details. A Hike that stored no point, a Record and Stop with no
fix between them, is discarded rather than saved as an empty one (decided with the owner on
2026-10-26).

**One recording at a time**, never tied to a saved Route in this phase.

**When the process dies.** The service is not sticky and there is no boot receiver (the tempting
one, above). The next launch finds a Hike with no end, and offers to **resume** it, which starts a
new stretch, or to **finish** it at its last point. It never throws a recorded point away.

**The schema guard.** Record starts the service from the visible app, past the launch gate, but a
notification's Pause, Resume or Stop reaches the service with no screen in front. So the service
asks `BackgroundSchemaGuard` (`schemaBlocksBackgroundWork()`) on every start before it touches the
database, as ADR-0003's 2026-10-18 amendment promised; a blocked start stops the service and writes
nothing.

**Schema 3**: `hikes` (name, start and end, the encoded Track, the note, the totals computed at Stop
and after a trim) and `track_points` (while recording), with the photos' table in D's shape decided
now, since the schema freezes at B: `hike_photos` (the media path by kind, the time taken, a nullable
position and the caption). `MIGRATION_2_3`, its schema JSON, the `SchemaGateTest` assertion, a
migration test reading back a saved Route, and an upgrade watched on the test phone, per the
standing gate in `DOD.md`.

**Copy:** "Record hike", "Hike recording", "Recording", "Paused", "Resume", "Stop", "Stop
recording? The hike is saved and you can't add to it afterwards.", "Recording a hike needs precise
location", "Allow precise location in the app's settings to record a hike. Approximate location can
be off by more than a kilometre.", "Open settings", "Notifications are off, so there's no Stop
button outside the app", "Location is off — nothing is being recorded", "A hike didn't finish
recording", "Resume recording", "Finish it here".

## 3. Pause, and resuming by itself

**Pause stops GPS**, which is the battery reason to pause, and keeps the foreground service and its
notification, now "Paused" with Resume and Stop. Stopping the service instead would need a restart
from the background to resume, which Android 12+ refuses and which would hold no location anyway.
**The Track breaks at a pause**: the next fix starts a new stretch, and nothing joins the two with a
straight line.

**Auto-resume** uses the significant-motion sensor (`TYPE_SIGNIFICANT_MOTION`): a one-shot hardware
trigger that fires when the user starts walking or travelling and ignores the phone shaken in place.
It needs no permission, costs next to nothing, and wakes the phone from Doze. Armed at Pause,
cancelled at Resume and Stop. When it fires, the recording resumes and the notification says so,
silently (§ 2's channel). A wrong resume records a few extra metres; a missed one loses a stretch
nobody can get back, so it resumes rather than asks.

**A Settings switch turns it off**, on by default, because the owner knows it can fire while sitting
still. On a phone without the sensor the switch is not shown, and the pause screen says resuming is
by hand.

**A forgotten pause** stays paused: nothing resumes on a timer and nothing reminds, since nothing is
scheduled. The recording bar is the reminder, and the notification where it shows: from API 34 the
user can swipe it away while the service runs on, and with notifications denied it is only in the
system's list of active apps.

**Copy:** "Pause", "Resumed — you're moving", "Resume when I start moving", "Resumes by itself when
the phone senses you walking. It can sometimes resume while you're sitting still.", "Resume by hand
on this phone".

## 4. The Hikes tab

A third tab beside Map and Routes, **Hikes** (*Wycieczki*). The list is newest first: name, date,
distance, total time and ascent. A Hike's details: the Track on the map with the rest of the map
faded as a Route's is, distance, moving time and total time, ascent and descent, the profile, the
photos, the note, and Rename, Export (F), Trim and Delete.

**Moving time** leaves out pauses and stretches with no movement; **total time** runs from Record to
Stop. Saved Routes and Hikes stay separate lists: one is a plan, the other what happened.

**Trim**: two handles on the profile, and the Track between them is what stays. The cut points are
deleted for good after a confirmation; the totals are computed again, and a photo whose time falls
outside the kept span keeps its place in the Hike but loses its pin.

**Delete**: a confirmation that names how many photos go with the Hike; the photos' files are
deleted with their rows.

**Copy:** "Hikes", "No hikes yet. Press Record on the map to record one.", "Distance", "Moving
time", "Total time", "Ascent", "Descent", "Rename", "Trim", "Trim the start and end of the track",
"The cut part of the track is deleted. This can't be undone.", "Delete hike?", "Delete this hike and
its %d photos? This can't be undone." (plural).

## 5. Ascent and descent

**Inside the Regions, from the height grid**, so a Hike walked along a planned Route shows the same
numbers the plan did. Read once, at Stop and after a trim: the zoom-12 relief tiles under the Track
are decoded on the phone (lossless WebP, which every supported API decodes) and each point takes the
grid's height at its position. Those heights are summed with phase 5's 5 m dead band, the rule a
Route's ascent is summed by, so a Track wobbling sideways across a slope's 12 m cells does not add a
climb at every point, and the two numbers compare.

**Outside the grid, from GPS altitude, smoothed**: a climb or a drop counts only once the height has
moved 10 m in one direction since the last turn, which removes most of the jitter. **Ascent is summed
within each run of one source and never across a change of source**: GPS gives height above the
ellipsoid, about 35–40 m above sea level in southern Poland, and a sum across the boundary would
count that offset as a climb.

When any part came from GPS, the number shows "≈" and one line under it. Each point's height, by the
same rule, is what the profile draws and what GPX writes; from API 34, GPS heights are converted to
sea level with the platform's `AltitudeConverter`, offline (the known gap below).

**Copy:** "Partly from GPS height, less precise".

## 6. Photos and the note

**During a recording**, the camera button opens the system camera (`TakePicture`, no permission).
The photo is pinned at the last fix when the button was pressed, because `MediaFiles` strips GPS from
the file itself, as the house rules require.

**Afterwards**, "Add photos" on a Hike opens the Photo Picker (no permission). The time taken
(`DateTimeOriginal`, with `OffsetTimeOriginal` where the camera wrote it, else the phone's zone) is
read before `MediaFiles` strips the metadata. A time within a stretch pins the photo at the Track's
position at that moment. A time between two stretches, in a pause or a break, pins it at the last
point before them, where the user stopped, as the camera button would have: nothing is drawn across
a break (§ 3), so nothing is placed on one. Any other time, or none, adds it unpinned.

Each photo is written by `MediaFiles` as `photos/<uuid>.jpg` with its thumbnail. Pins on the Hike's
map open the photo, and the details show a strip; each photo can be captioned, removed from the Hike
or deleted. A missing file is a placeholder.

**One note per Hike**, free text in the details, editable any time.

**Copy:** "Take photo", "Add photos", "Caption", "Note", "Add a note", "Remove from hike", "Delete
photo".

## 7. Backup, ported from binky

Binky's ADR-0005 design, and its code where it fits (`data/backup/` and `ui/backup/` there), with two
changes. ADR-0005 is amended at E to record it.

- **The agent carries the database and the preferences always**, then thumbnails **newest first up
  to 20 MiB** in all (binky's `AUTO_BACKUP_BUDGET_BYTES`), never the full photos. The budget is a
  pure function over files, under a test. At about 50 KB per 512² thumbnail, a few hundred fit
  beside the database.
- **The full photos travel on a phone-to-phone transfer** (`FLAG_DEVICE_TO_DEVICE_TRANSFER`, API
  28+; binky's ADR-0005 says 30, wrongly), where there is no quota and no cloud.
- **A marker file outside the database** records the last automatic backup and how many thumbnails
  were left out. It is never in the backup set and is cleared at restore. Backup settings shows three
  states: when the last one ran, that it is stale past 14 days, or that none has been recorded on this
  phone, with a button into the system's backup settings.
- **Backup settings says in words** that automatic backup keeps Hikes, notes and Routes but not the
  photos.
- **Export through the share sheet, with a remembered folder** (a persisted tree grant, checked on
  every read), falling back to the share sheet whenever the folder fails.
- **Restore**: a confirmation naming the scope and the date, an automatic Records export of the
  current state first, then stage, migrate, swap; a newer schema refused; the manifest is the
  authority, and only entries of a known shape are extracted.
- **The Backup row returns to Settings.**

**Changed for Kozica:** two export scopes, **Records** (database, preferences, thumbnails) and
**Everything** (and the photos); binky's third scope separates avatars and documents, which Kozica
does not have.

**Left out:** binky's recurring export reminder and its exclusion notification (*Not in*, above).
Instead **the Hikes tab shows a banner** while photos exist that no Everything export has covered, with
an Export button. The app knows this: it records when it last exported Everything.

**The preferences joining the backup carry the map's last centre**, which the privacy policy says
stays on the phone. With Hikes in the database, the backup already carries far more location than
that, from B; the sentence goes at E, with the preferences, since `docs/` is published on merge.

**Copy:** binky's, translated into Kozica's words, plus "%d photos aren't in any backup" (plural),
"Export", "Automatic backup keeps your hikes, notes and Routes, not your photos. Export everything
to keep the photos."

## 8. GPX export

**From a Hike**: one `<trk>` with a `<trkseg>` per stretch between pauses; each point with its time
and its height by § 5's rule; the Hike's name and note as `<name>` and `<desc>`; each pinned photo as
a `<wpt>` with its caption, without the image.

**From a saved Route**: the planned line as a `<trk>`, which watches and other apps import most
reliably, with its heights from the network and its points as `<wpt>`s. Its line and points come
from OSM, so its `<metadata>` carries the attribution: a `<copyright>` by "OpenStreetMap
contributors" with the ODbL as its `<license>`, and a `<link>` to
https://www.openstreetmap.org/copyright. A Hike's own Track is the user's data and carries none.

**Delivery**: the file is written to the cache and handed to the share sheet, named after the Hike or
Route and its date. Nothing leaves the phone unless the user picks where it goes; ADR-0008 stands,
because the share is the user's.

**Copy:** "Export GPX".

---

## Tests

- **Track encoding round trip** (B): points in, the same points out, segment breaks included. Fails
  towards losing a recorded Hike.
- **Ascent** (C): the grid run's 5 m dead band, the GPS hysteresis, and no climb counted across a
  change of source. Fails towards a wrong ascent beside a planned Route's.
- **Moving time and trim** (C): a pause and a stationary stretch left out; a trim recomputes the
  totals and unpins the photos outside it.
- **Photo time to Track position** (D): inside, outside, in a pause (the last point before it), no
  time at all.
- **Auto Backup admission** (E): the database and preferences always in, thumbnails newest first up
  to the budget and never over. Fails towards the whole backup being silently dropped.
- **The marker** (E): never in the backup set, cleared at restore, stale past 14 days.
- **GPX writer** (F): well-formed with names that need escaping, a `<trkseg>` per stretch, the OSM
  attribution in a Route's and none in a Hike's.

## Documents this phase amends

- **`CONTEXT.md`**: Hike and Track, with this file.
- **`PLAN.md`**: phases 6b and 6c, with this file.
- **The privacy policy**, each sentence with the checkpoint that makes it true, since `docs/` is
  published on merge: at B, the app stores location, on the phone only, in Hikes, and Auto Backup
  carries them to the user's own Google account; at D, photos have their location stripped; at E,
  the preferences join the backup (the "stays on the phone" sentence goes) and a backup leaves only by
  the user's own share; at F, so does a GPX.
- **`play-app-content.md`** (B): the `location` foreground service's declaration and its answers
  (the owner's, with a short video, in the same sitting as `dataSync`'s), recording a Hike added to
  the `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` rows, and Data safety's reasoning for stored
  location. It stays "no data collected": nothing is transmitted off the device by the app.
- **`scripts/aab-permissions.py`** (B): `FOREGROUND_SERVICE_LOCATION` and `POST_NOTIFICATIONS` join
  the allowlist, and `FOREGROUND_SERVICE`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` gain
  the recording as a reason, in step with `play-app-content.md`'s rows.
- **ADR-0003** (B): an amendment saying `POST_NOTIFICATIONS` returns with the recording's
  notification, asked at Record, on § 2's one channel, and that the recording service asks
  `BackgroundSchemaGuard`, as its 2026-10-18 amendment promised.
- **ADR-0005** (E): amended for binky's design.
- **`docs/store-listing.md`**: the hike log in the description, before the release that carries it.
- **`scripts/edge-to-edge.py`**: scenes for the Hikes tab, a Hike's details and the recording bar.

## Known gaps, deliberately

- **Below API 34, GPS heights outside the grid are above the ellipsoid**, about 35–40 m above sea
  level in southern Poland, in the profile and in GPX. Ascent is unaffected (§ 5). Closing it means
  bundling a geoid model, for Hikes outside every Region on older phones.
- **The significant-motion sensor can fire while sitting still**, and some phones lack it. The
  Settings switch and the by-hand fallback cover both.
- **A recording the ROM kills stops until the next launch.** The sticky service that would hide that
  records nothing (the tempting one).
- **Photos picked afterwards with no time taken stay unpinned.** Reading the photo's own location
  instead would need `ACCESS_MEDIA_LOCATION`, a new ask for a rare case.

## Readings

Device state the readings depend on: the test phone unplugged in software (`dumpsys battery
unplug`) and reset afterwards, its keyguard dismissed for screenshots; the owner's phone with no
battery exemption and no autostart, read with `scripts/device-gate.py` before the walk, since the
autostart grant lapses on its own, and notifications allowed for the debug build, as a user who
answered yes at Record has them. Every database is pulled with its `-wal` and checkpointed on the
host.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate, test phone: four hours forced idle, screen off, unplugged in software | § 1's commands; `scripts/track-gaps.py` on the pulled database | |
| R2 | The gate, owner's phone: a real walk of two hours or more in a pocket, one pause, battery per hour | `scripts/track-gaps.py`; battery level before and after | |
| R3 | The significant-motion sensor: present on both phones, and firing on the walk after the pause | `adb shell dumpsys sensorservice`; `adb logcat -s KozicaRecord` | |
| R4 | Process death mid-recording: the next launch offers resume or finish, and no point is lost | `adb shell am kill` during a recording; the pulled database | |
| R5 | Schema 2 to 3 on the test phone: the previous release-shaped build with a saved Route, this one over the top | `assembleDebug -PreleaseShapedDebug` both times; `dumpsys dbinfo` | |
| R6 | A Hike's ascent beside the planned Route over the same Trails | the Hikes tab and the Routes tab, same Trails | |
| R7 | Auto Backup with photos past the budget: the database still in the backup | `adb shell bmgr backupnow`; the marker file | |
| R8 | A GPX export opened in another app, with its photos as waypoints | the share sheet to Mapy.cz or Files | |
| R9 | Each row of § 2's table, with the API level: on the owner's phone (API 36) approximate only, its upgrade kept, denied for good, notifications denied (does it record, what the bar says) and the notification swiped away (the service runs on); on the test phone (API 30) denied and location off; and on both, the `location` service starting with no `SecurityException` | `adb shell pm revoke`/`grant`, `cmd location set-location-enabled`, a screencap per state; `adb logcat -d \| grep SecurityException`; `adb shell dumpsys activity services` | |
