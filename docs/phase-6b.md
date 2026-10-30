# Phase 6b — The user follows a Route on the map, and its times are their own

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on ADR-0008 (nothing reaches the network: following and pace are computed on the phone from
what is already there) and ADR-0010 (the router plans the leg back to a Route, unchanged).
[`CONTEXT.md`](../CONTEXT.md) gains **Following**, **Progress** and **Pace**. No ADR: every
decision here is cheap to reverse. Planned with the owner on 2026-10-30.

**What closing this phase means.** Today a Route is planned, saved and drawn, and its walking times
are the PTTK marker's rule for everybody. After this phase the user taps "Follow" on a Route's sheet
and the map tracks them along it: the sheet counts down what is left to the next point and to the
end, a banner says when they have left the Route and offers a leg back, and following ends by itself
at the end. The times on every Route are the user's own once three recorded Hikes say what their pace
is, with the signposts' time beside each total. The difference a device can be asked about: on a
walk with a deliberate wrong turn, the banner shows within 30 s of leaving the Route and not on the
stretches walked on it, and the time left at each point is within the reading's margin of the time
the rest actually took (R5).

`PLAN.md`'s paragraph still holds. One correction to the plan as discussed: the Trail sheet (phase
6c) shows no walking time, so the user's pace appears on the Route sheet, its points, the Routes list
and following's sheet, and nowhere else.

---

## 0. What this phase inherits

- **The walking-time rule is four constants** in `data/routes/EdgePart.kt` (4 km/h, 10 min per
  100 m up, 5 min per 100 m down where steeper than 10 %), applied per edge by the router and summed
  unrounded, then rounded up to 5 minutes (phase 5 § 3). A pace multiplies the sum, so the router
  and its tests do not change.
- **A recorded Hike keeps every fix with its time and its stretch**, the moving seconds, and heights
  from the relief (`data/hikes/`, `HikeHeights.kt`, `Climb.kt`). The rule's time for a Hike's own
  Track can therefore be computed from the Track alone, with no Trail network: its length, and its
  climbs and steep descents from the relief's heights. Nothing needs to have been walked on a Route.
- **The map already asks for precise location, re-reads it, and says when it is off**: the position
  button's ask (phase 2), `LocationRead`, `PositionAge`'s stale fix, and the banners. Following reuses
  them rather than growing its own.
- **Reading positions without walking is already possible.** The debug `PositionReceiver` puts "you
  are here" anywhere (phase 2 § 4), and `RouteReceiver` plans and logs a Route (phase 5). Following's
  readings before the walk replay fixes through the first, against a Route from the second.
- **The position dot redraws the map at rest** (`DOD.md`, phase 6c's R3): with the screen on,
  following costs those frames on top of the camera's. Following keeps the screen's own timeout
  rather than adding to it (§ 3).
- **A Track holds only the good fixes.** The recorder drops any fix worse than 30 m, and keeps no
  accuracy for the rest. Following reads the live fixes, bad ones included, so a Track understates
  what following will see (§ 1).
- **Nothing of ours runs with the screen off except a recording** (phase 6's `location` foreground
  service). Following does not start one, so with the screen off following knows nothing until the
  user looks.

## What is in, and what is deliberately not

**In:** a pace learned from the user's recorded Hikes, floored at 0.75× the signposts' rule and
shown beside the signposts' time; "Follow" on a Route's sheet, with the map tracking the user, the
sheet counting down what is left, progress matched forward along the Route, "Find me on the Route",
a banner when the user has left it with "Back to the Route", following surviving a closed app, and
following ending by itself at the end.

**Not in, and each with a reason rather than a phase:**

- **A warning with the screen off.** It needs a running location service, so following would ride on
  a recording; the owner chose on-screen only. The closing walk (R5) says whether a wrong turn missed
  in the pocket matters enough to reopen it.
- **Turn-by-turn announcements.** A junction model and spoken copy in every shipped language, for a
  user who checks the map at junctions anyway.
- **Heading-up.** The compass near rock and steel is off by tens of degrees, and the map stays north up
  like the paper one and the signposts' sketches.
- **The time taken since Follow.** It would be confused with a Hike's moving time; the Hike is the
  measurement, and it is what feeds the pace.
- **A pace for climbs and one for descents.** Three factors need many more Hikes before they are
  stable and take more than a line to explain. One factor until real use shows it misleads on long
  climbs.
- **A pace setting.** The signposts' time stays beside each total, which covers walking with somebody
  slower.
- **Linking a recorded Hike to the Route it followed.** Following and recording stay independent: one
  can follow without making a Hike and record without following.

**The tempting one: keeping the screen on while following.** It looks like what a navigation app
does, and it makes following ready at a glance. It is also the costliest thing on a long day, and a
hiker looks at the map at junctions rather than all the way. The forward window (§ 3) already picks
up where the user is after the phone has been in a pocket.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — how often a real Track on a Trail sits beyond 40 m for 30 s | `chore:` the script; the verdict is a reading | — |
| **B** | The user's pace on every walking time | `feat:` | — |
| **C** | Follow, Progress and what is left | `feat:` | A |
| **D** | The banner and the leg back | `feat:` | C |
| **E** | Following survives a closed app; the closing walk | `feat:` | D |

**B does not wait for A.** The pace reads the Hikes and nothing else, so it is built and released
while A waits for Tracks; its own reading (R2) needs only the Hikes already recorded. C waits for A
because the banner's thresholds are A's to confirm, and C draws the line the banner is measured
against.

---

## 1. The gate: is 40 m for 30 s a wrong turn, or just the GPS?

### The question, in the form that can be measured

Over real Tracks recorded on marked Trails, for every fix: its distance from the nearest edge of
the Trail network. Then the number of times "farther than 40 m for 30 s" would have fired, per hour
walked, with each firing's place and length, checked against the map to tell a wrong turn from
drift. A Track keeps no accuracy, so the rule's accuracy term (§ 4) cannot be read here; it is read
live on the walk (R5). And a Track is already filtered: the recorder drops every fix worse than 30 m
(`MAX_ACCURACY_METRES`, `RecordingService.kt`), so the worst drift is missing from it and shows as a
gap in time instead. The script lists every gap over 30 s within a stretch, with its place, since
following sees the fixes the recorder threw away.

### The apparatus

`scripts/routes/track-offsets.py`: reads GPX files (a Hike's export, with each point's time) and
`trails.network`, and prints per file the hours, the fixes, the
distance percentiles, and every firing with its start time, length and coordinates. The Tracks come
from the owner's phone: a few Hikes walked on marked Trails exported from the Play build's share
sheet, or, if there are none, one recorded walk on marked Trails, ideally with forest or a gorge in
it. The same files are R2's Hikes.

### The verdicts, decided now rather than argued about later

1. **No firing on a stretch walked on the Route, or at most one per three hours, each under a minute**
   → C and D as planned, with 40 m and 30 s.
2. **More than that, but the firings sit where the sky is poor** (a gorge, forest, under a face) →
   the accuracy term is meant for them, and build as planned; R5 walks such a stretch and reads the
   live accuracy there. If R5's banner still fires on it, the distance goes up in 10 m steps until it
   does not, and the step taken is written there.
3. **Drift beyond 40 m with a good accuracy, along open ground** → the network's line, not the GPS, is
   off (a Trail drawn away from where it is walked); list the edges, and the banner waits for a
   decision with the owner on whether to report them or widen the rule.

### The verdict, taken *(date)*

*(Which one, the reading behind it, and anything it found that neither verdict anticipated.)*

---

## 2. Pace

**The rule's time for a Hike** is the walking-time rule applied to the Hike's own Track: each
stretch's length at 4 km/h, plus 10 minutes per 100 m of climb and 5 minutes per 100 m of descent
where the grade between two heights is steeper than 10 %, from the relief's heights with phase 5's
5 m dead band. Pauses between stretches are not walked, so they are not timed. The ratio is the
Hike's moving seconds over that time.

**A Hike counts** when it is at least 3 km long, has at least 1 hour of moving time, and its ratio
lies between 0.5 and 2 — a bus ride or a recording left running in a car falls outside. Only the 10
most recent counting Hikes are read, so last year's pace does not outvote this summer's.

**The Pace** is the median ratio over those Hikes, **used once three count**, and **never below
0.75**: no recording can promise a Route in under three quarters of the signposts' time, whatever
the Hikes say, because a pace learned on half-day walks is applied to long days where people slow
late. Above 1 there is no cap beyond the counting rule's 2.

**Where it shows.** Every walking time the app gives is the rule's time multiplied by the Pace, then
rounded up to 5 minutes as now: the Route sheet's total and each point's time, the Routes list, and
following's sheet. Each total also shows the signposts' time, smaller, and one line under the Route
sheet's totals says where the user's time comes from. With fewer than three counting Hikes nothing
changes, and the line says the times are the signposts'.

The Pace is computed from the Hikes table when it changes, as a `Flow`, and is nothing stored:
deleting or trimming a Hike changes it, and a restore brings it back with the Hikes.

Copy:

- Total: "4 h 10 min · signposts 4 h 50 min"
- The line, with a pace: "Your pace: 0.85× the signposts, from 7 Hikes"
- The line, without: "Signposts' times. Your own pace starts after 3 recorded Hikes of 3 km or more."
- Floored: "Your pace: 0.75× the signposts, the fastest Kozica shows, from 5 Hikes"

## 3. Following

**Follow** is a button on a Route's sheet, for a saved Route and for one just planned. It needs
precise location, and asks for it there, by the position button's ask, the first time it is tapped
without it (`PLAN.md` rule 4):

| State at the tap | What happens |
| --- | --- |
| Precise location granted, location on | Following starts |
| No location permission | The map's ask; following starts if precise is granted |
| Approximate only | "Following needs precise location", with a button to the app's location settings; following does not start |
| Location off in the system | The map's "Location is off" banner; following starts once a fix arrives |

**While following** the camera centres on the user at each fix, north up, until the user pans or
zooms; the position button resumes it. The screen keeps its own timeout. The sheet shows, from the
user's Progress: distance, climb and time to the next point by name, and to the end. "Stop" ends
following and leaves the Route drawn; closing the Route ends it too.

**Progress** is the user's place along the Route's line, and it only moves forward. Each fix is
matched to the nearest point of the line within a window ahead of the last Progress, so on a Route
that walks a stretch twice (up a valley and back down it, a loop that touches itself) it is the
pass the user is on. The window reaches as far ahead as 6 km/h would carry the user since the last
matched fix, and never less than 200 m, so a long stretch with the phone in the pocket still finds
them. Walking back the way they came leaves Progress where it was rather than jumping. **"Find me on
the Route"** matches over the whole line once, for joining a Route halfway or skipping a part of it.

**The end.** Once Progress reaches the last point and the user is within the leaving distance of it
(§ 4), the sheet says so and following stops, the Route still drawn.

**Surviving a closed app.** The followed Route's points, its name if it has one, and the Progress are
written to `AppPreferences` as they change. Opening the app again returns to following that Route,
unless it was stopped, finished, or last touched over 24 hours ago, when the value is dropped. The
points are kept rather than the Route's id so a Route followed before it was saved survives too. No
schema change.

Copy:

- Button: "Follow", "Stop"
- Sheet: "Next: Hala Gąsienicowa · 1.8 km · ↑ 240 m · 1 h 05 min", "To the end: 6.4 km · ↑ 610 m · 3 h 20 min"
- "Find me on the Route"
- "Following needs precise location", button "Location settings"
- "You've reached the end of the Route"

## 4. Leaving the Route, and the leg back

**Leaving.** The banner shows once every fix for 30 s has been farther from the Route's line than
40 m, or than the fix's own accuracy where that is larger, and clears at the first fix back within
it. Both numbers are A's to confirm and R5's to check, and are changed only by a reading.

**"Back to the Route"** plans a leg over the Trails from the user's position to the nearest point of
the line ahead of Progress, by the router as it is, and draws it apart from the Route, timed by the
Pace. The saved Route is not changed. The leg goes when the user is back on the Route, or when it is
planned again. Off the Trails the router starts from the nearest Trail, as phase 5's ends do, and the
leg says how far that is. Replanning the rest of the Route instead is out: it could drop a via point,
a Hut the user meant to reach.

Copy:

- Banner: "You've left the Route · 120 m from it", action "Back to the Route"
- Leg: "Back to the Route: 650 m · 15 min"
- Off the Trails: "Back to the Route from the nearest Trail, 80 m away"

---

## Tests

- **The Pace's floor.** A set of Hikes whose median ratio is under 0.75 gives 0.75; one under 0.5
  is not counted at all. Fails towards a time too short, the direction that leaves people on a ridge
  at dusk.
- **Which Hikes count**, and that fewer than three counting Hikes give no Pace: a short Hike, a slow
  car ride and an eleventh-oldest Hike each change nothing. Fails towards a pace from the wrong data.
- **The rule's time for a Track** against the router's for the same line over a test network, to
  the minute: the Pace is only honest if both sides use one rule.
- **Progress on an out-and-back Route** stays on the pass being walked and does not move backwards.
  Fails towards a time left that jumps by half the Route.
- **Leaving** fires after 30 s beyond the distance and not before, and the accuracy term widens it.
  Fails towards a silent wrong turn, or a banner that cries wolf.

## Documents this phase amends

- **`CONTEXT.md`**: **Following**, **Progress**, **Pace**.
- **`PLAN.md`**: this file, linked from phase 6b's paragraph.
- **The store listing** (`docs/store-listing.md`): following a Route and times from your own pace,
  once E closes.
- **No permission, no stored key of a new kind, no network**: the privacy policy and
  `play-app-content.md` are unchanged. Following uses the location the map already asks for, while
  the app is on screen.

## Known gaps, deliberately

- **With the screen off, following knows nothing.** A wrong turn is found when the user looks. The
  closing walk says whether that is enough.
- **The Pace is one number.** A walker fast on the flat and slow downhill gets a pace that fits
  neither. It is the median of whole Hikes, and the signposts' time stays beside it.
- **The Pace learns from where the user has been.** Hikes in the Beskidy say little about a Tatry day
  of chains and scree; the floor keeps that from promising too much.
- **The leg back follows Trails only**, like every Route. Someone off the Trails is led from the
  nearest one, and told how far it is.

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device, with the command that read it.

R1, R3 and R4 run on the test phone with `PositionReceiver`'s fixes replayed by a script from a
Track, and the Route from `RouteReceiver`; autostart for the debug build is re-read with
`scripts/device-gate.py` first, since the broadcasts need it. R5 is the owner's walk.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate (§ 1): firings of 40 m / 30 s per hour over real Tracks on Trails | `python3 scripts/routes/track-offsets.py <gpx>…` | *(owed)* |
| R2 | The Pace from the owner's Hikes: each Hike's ratio, which counted, the median, and three Routes' times before and after | the owner's Hikes; the Pace's log line; `RouteReceiver` | *(owed)* |
| R3 | Progress on an out-and-back Route and a loop: Progress never moves back, and the time left falls with every fix | replayed fixes; the following log line per fix | *(owed)* |
| R4 | Following survives a killed process: force-stop mid-Route, relaunch, and the sheet is back with the same Progress | `am force-stop`, relaunch, screencap | *(owed)* |
| R5 | The closing walk: a Route followed end to end with a deliberate wrong turn and a stretch under poor sky; when the banner showed, how far the leg back was, and the time left at three points against the time the rest took | the owner's walk, recorded as a Hike, the following log | *(owed)* |
