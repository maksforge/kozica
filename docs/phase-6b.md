# Phase 6b — The user follows a Route on the map, and its times are their own

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
relies on ADR-0008 (nothing reaches the network: following and pace are computed on the phone from
what is already there) and ADR-0010 (the router plans the leg back to a Route, unchanged).
[`CONTEXT.md`](../CONTEXT.md) gains **Following**, **Progress** and **Pace**. No ADR: every
decision here is cheap to reverse. Planned with the owner on 2026-10-30.

**What closing this phase means.** Today a Route is planned, saved and drawn, and its walking times
are the PTTK marker's rule for everybody. After this phase the user taps "Follow" on a Route's sheet
and the map keeps them in view along it: the sheet counts down what is left to the next point and to
the end, a banner says when they have left the Route and offers a leg back, and following ends by
itself at the end. The times on every Route are the user's own once three recorded Hikes say what
their pace is, with the signposts' time beside each total. The difference a device can be asked
about: on a walk with a deliberate wrong turn, the banner shows within 30 s of the first fix beyond
the leaving distance (§ 4) and not on the stretches walked on the Route, and the time left at each of
three points is within 15 % or 10 minutes, whichever is larger, of the moving time the rest of the
walk took (R5).

`PLAN.md`'s paragraph still holds. One correction to the plan as discussed: the Trail sheet (phase
6c) shows no walking time, so the user's pace appears on the Route sheet, its points, the Routes list
and following's sheet, and nowhere else.

**Settled in review** (by the agent, 2026-10-30, answering the plan's review; each is the owner's to
reverse):

- A Hike counts towards the Pace only if every one of its points has the grid's height (§ 2).
- A fix older than 30 s when it arrives, or with an accuracy worse than 50 m, neither moves Progress
  nor counts towards leaving; a fix beyond the leaving distance does not move Progress; a gap of
  more than 30 s between fixes restarts leaving's count (§ 3, § 4).
- Where the line passes near a fix more than once, the pass least far along it ahead of Progress
  wins, for matching and "Find me on the Route" (from the start before any Progress); the leg back
  rejoins an earlier pass where it is about as near; following ends only once Progress has passed
  every via point (§ 3, § 4).
- Follow is offered only for a complete Route; the dashed off-Trail approaches are part of the line;
  editing the Route while following stops following (§ 3).
- Precise location revoked or location switched off while following, or a grant gone on reopening,
  pauses following under the map's banner rather than dropping it (§ 3).
- The gate reads the debug recorder's `gate.db` as well as GPX (§ 1). R5's margin is 15 % or 10
  minutes of moving time, whichever is larger. R2 reads the owner's Hikes from the Play build's
  export restored into the debug build, and a Track over 5 % longer than the network's line goes to
  the owner, though B's merge does not wait on R2 (*Readings*).


**Settled in C** (by the agent, 2026-10-30, building it; each is the owner's to reverse):

- **C was built before A**, on the owner's word: the numbers A can move are constants in one place,
  `data/routes/Following.kt` (`LEAVING_METRES`, `FIX_WORST_ACCURACY_METRES`, `FIX_OLDEST_MILLIS`,
  `WINDOW_METRES_PER_SECOND`, `WINDOW_LEAST_METRES`), so a verdict changes them and nothing else.
- The screen hands each fix to the ViewModel while following, so with the app off screen nothing is
  matched, as § 3 has it. A fix's age on arrival is read against `elapsedRealtimeNanos`; the
  window's clock is the fix's own `time`, so a replayed fix's time and a Progress kept across a
  reboot both read right.
- Before the first fix that counts, the sheet shows what is left from the start. The time left is
  the router's at each point and between two points the leg's time spread by the rule over the line
  walked; the climb left is the line's own, scaled to the sheet's Ascent. Both are counted once
  along the whole line with the dead band committed in walking order, so standing at the start
  they are the sheet's own totals and neither rises as Progress moves on.
- A replay faster than walking stalls Progress by design: the window is 6 km/h since the last match,
  so R3's fixes keep their own times, and a jump ahead is "Find me on the Route"'s.

**Settled in D** (by the agent, 2026-10-30, building it; each is the owner's to reverse):

- `LEAVING_MILLIS` (30 s) is both the count and the gap that restarts it, beside C's constants. A
  gap restarts the count, not a banner already shown: only a counting fix back within the distance
  clears it.
- Leaving counts from Follow, before any Progress, so Follow tapped away from the Route raises the
  banner after 30 s. Its distance is to the nearest point of the whole line, at the latest counting fix.
- The leg back's window is C's, from the last match to the fix it is planned from, so the longer the
  user has been off, the farther ahead it may rejoin. Before any Progress it is the whole line.
- "Back to the Route" stays on the banner and plans the leg again. A leg still planning when the user
  is back is dropped. Where the router finds no way, the sheet says "No way back to the Route over
  the Trails".
- "From the nearest Trail" shows past 50 m, the Route sheet's threshold for its points.
- The leg is dashed in `onSurface`'s tones (the style's `route_back`), as is its sheet line, and
  drawn above the Track it often retraces: the Route has the primary palette and the Track the
  tertiary, so it reads as neither; a selected Trail hides it with the Route.
- Polish: "Jesteś poza trasą" needs no gender, and the action is the noun "Powrót na trasę" (the app
  observes rather than advises).

**Settled in E** (by the agent, 2026-10-31, building it; each is the owner's to reverse):

- The saved Route's id is kept beside the points, so a resumed Route is still the saved one: Save
  writes over it, and a rename or a delete in the Routes tab reaches it as before.
- The value is written whenever the points, the name or Progress change, each write stamped with the
  time, and "last touched" is that stamp. Leaving's count and the leg back are not kept: a reopen is
  a gap of more than 30 s anyway, which restarts the count (§ 4).
- On reopening, the Route is planned again from today's network and followed only if it is still
  complete; otherwise it stays drawn, unfollowed, and the value goes. A Route or a Hike the map was
  asked to show before the read wins, and the value goes with the next write.
- A resumed Route keeps the user in view, as a Follow tap does.
---

## 0. What this phase inherits

- **The walking-time rule is four constants** in `data/routes/EdgePart.kt` (4 km/h, 10 min per
  100 m up, 5 min per 100 m down where steeper than 10 %), applied per edge by the router and summed
  unrounded, then rounded up to 5 minutes (phase 5 § 3). A pace multiplies the sum, so the router
  and its tests do not change.
- **A recorded Hike keeps every fix with its time and its stretch, and the moving seconds**
  (`data/hikes/`). It keeps no per-point heights: `HikeHeights.of` works them out at read time, from
  the relief's grid inside the Regions while the relief is on the phone, and from the fix's own GPS
  height everywhere else (`HikeHeights.kt`, `Climb.kt`). The stored ascent and descent are totals,
  not the rule's steep descent, so the Pace does not reuse them. The rule's time for a Hike's own
  Track can therefore be computed from the Track alone, with no Trail network: its length, and its
  climbs and steep descents from the grid's heights. Nothing needs to have been walked on a Route.
- **The map already asks for precise location, re-reads it, and says when it is off**: the position
  button's ask (phase 2), `LocationRead`, `PositionAge`'s stale fix, and the banners. Following reuses
  them rather than growing its own.
- **Reading positions without walking is already possible.** The debug `PositionReceiver` puts "you
  are here" anywhere (phase 2 § 4), and `RouteReceiver` plans and logs a Route (phase 5). Following's
  readings before the walk replay fixes through the first, against a Route from the second; each
  gains what the replay needs (*Readings*).
- **The position dot redraws the map at rest** (`DOD.md`, phase 6c's R3): with the screen on,
  following costs those frames on top of the camera's. Following keeps the screen's own timeout
  rather than adding to it (§ 3).
- **A Track holds only the good fixes.** The recorder drops any fix worse than 30 m, and keeps no
  accuracy for the rest. Following reads the live fixes up to 50 m (§ 3), so a Track understates
  what following will see. Phase 6's debug gate recorder keeps every fix with its accuracy (§ 1).
- **Nothing of ours runs with the screen off except a recording** (phase 6's `location` foreground
  service). Following does not start one, so with the screen off following knows nothing until the
  user looks.

## What is in, and what is deliberately not

**In:** a pace learned from the user's recorded Hikes, floored at 0.75× the signposts' rule and
shown beside the signposts' time; "Follow" on a Route's sheet, with the map keeping the user in
view, the sheet counting down what is left, progress matched forward along the Route, "Find me on
the Route", a banner when the user has left it with "Back to the Route", following surviving a
closed app, and
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
drift. Two sources, read the same way:

- **The debug gate recorder's `gate.db`** (phase 6's `RecorderService`, read today by
  `scripts/track-gaps.py`) keeps every fix with its accuracy, those worse than 30 m included, so it
  reads the rule's accuracy term (§ 4) and the 50 m bound on which fixes count (§ 3) as following
  will apply them. Phase 6's R2 walk, if it is on marked Trails, serves here too.
- **A Hike's GPX export** keeps no accuracy, and is already filtered: the recorder drops every fix
  worse than 30 m (`MAX_ACCURACY_METRES`, `RecordingService.kt`), so the worst drift is missing from
  it and shows as a gap in time instead. For a GPX the script lists every gap over 30 s within a
  stretch, with its place, and reads the distance alone.

**A bias, named now.** The distance to the nearest edge understates the offset wherever another
Trail runs within 40 m, at junctions and on parallel paths, so it pushes the count towards verdict
1. The verdict says how many of the firings' minutes lie within 40 m of a second Trail.

### The apparatus

`scripts/track-offsets.py`, beside `track-gaps.py`: reads `gate.db` (each fix's time, position and
accuracy) and GPX files (a Hike's export, with each point's time) against `trails.network`, and
prints per file the hours, the fixes, the distance percentiles, and every firing with its start
time, length, coordinates and, from `gate.db`, its accuracy. A firing is counted by § 4's rule as
the banner will apply it: from `gate.db`, only the fixes that count (§ 3: none worse than 50 m), with
the accuracy term; from both, 30 s across the fixes received, a gap of more than 30 s between two of
them restarting the count rather than satisfying it. The Tracks come from the owner's phone:
a `gate.db` from a walk on marked Trails with the gate recorder running, and a few Hikes walked on
marked Trails exported from the Play build's share sheet; if there are neither, one recorded walk on
marked Trails with the gate recorder beside it, ideally with forest or a gorge in it. The same Hikes
are R2's.

**Settled in building it** (by the agent, 2026-10-31; the owner's to reverse): a firing is at least
40 m from every edge, so none of its minutes can lie within 40 m of a second Trail. The script
reports the bias where it can hide a wrong turn instead: the minutes walked within 40 m of a second
edge not joined to the nearest. A `gate.db` is pulled with its `-wal`, whose rows are the newest.

### The verdicts, decided now rather than argued about later

1. **No firing on a stretch walked on a Trail, or at most one per three hours, each under a minute**
   → C and D as planned, with 40 m and 30 s.
2. **More than that, but the firings sit where the sky is poor** (a gorge, forest, under a face) →
   the accuracy term is meant for them, and build as planned; R5 walks such a stretch and reads the
   live accuracy there. If R5's banner still fires on it, the distance goes up in 10 m steps until it
   does not, and the step taken is written there.
3. **Drift beyond 40 m in `gate.db` from fixes with an accuracy under 40 m, along open ground** (so
   the accuracy term would not have widened it) → the network's line, not the GPS, is off (a Trail
   drawn away from where it is walked); list the edges, and the banner waits for a decision with the
   owner on whether to report them or widen the rule. A GPX cannot give this verdict.

### The verdict, taken *(date)*

*(Which one, the reading behind it, and anything it found that neither verdict anticipated.)*

---

## 2. Pace

**The rule's time for a Hike** is the walking-time rule applied to the Hike's own Track: each
stretch's length at 4 km/h, plus 10 minutes per 100 m of climb and 5 minutes per 100 m of descent
where the grade between two heights is steeper than 10 %, from the grid's heights by
`HikeHeights.of` with phase 5's 5 m dead band. Pauses between stretches are not walked, so they are
not timed. The ratio is the Hike's moving seconds over that time.

**A Hike counts** when it is at least 3 km long, has at least 1 hour of moving time, every one of its
points has the grid's height, and its ratio lies between 0.5 and 2 — a bus ride or a recording left
running in a car falls outside. GPS heights jitter and cannot give the steep-descent grade, so a
Hike outside the Regions, or partly outside them, or read while the relief is not on the phone, does
not count. Only the 10 most recent counting Hikes are read, so last year's pace does not outvote
this summer's.

**The Pace** is the median ratio over those Hikes, **used once three count**, and **never below
0.75**: no recording can promise a Route in under three quarters of the signposts' time, whatever
the Hikes say, because a pace learned on half-day walks is applied to long days where people slow
late. Above 1 there is no cap beyond the counting rule's 2.

**Where it shows.** Every walking time the app gives is the rule's time multiplied by the Pace, then
rounded up to 5 minutes as now: the Route sheet's total and each point's time, the Routes list, and
following's sheet. Each total also shows the signposts' time, smaller, and one line under the Route
sheet's totals says where the user's time comes from. With fewer than three counting Hikes nothing
changes, and the line says the times are the signposts'.

The Pace is recomputed when the ended Hikes change, as a `Flow`, and is nothing stored: deleting or
trimming a Hike changes it, and a restore brings it back with the Hikes. Heights are read only for
Hikes whose length and moving time could count, and each Hike's ratio is cached in memory by its id
and length, so a rename or a note edit reads no Track again; the cache goes when the relief arrives
or leaves, since that changes which heights are the grid's.

Copy:

- Total: "4 h 10 min · signposts 4 h 50 min"
- The line, with a pace: "Your pace: 0.85× the signposts, from 7 hikes"
- The line, without: "Signposts' times. Your own pace starts after 3 recorded hikes of 3 km and an
  hour or more."
- Floored: "Your pace: 0.75× the signposts, the fastest Kozica shows, from 5 hikes"

## 3. Following

**Follow** is a button on a Route's sheet, for a saved Route and for one just planned, and only for
a complete one: a Route with a leg that has no way has a gap in its line that Progress cannot cross,
so its sheet has no Follow. The dashed off-Trail approaches at each point are part of the line, for
Progress and for leaving alike. Follow needs precise location, and asks for it there by the same
asks as phase 6's Record (phase 6 § 2), the first time it is tapped without it (`PLAN.md` rule 4):

| State at the tap | What happens |
| --- | --- |
| Precise location granted, location on | Following starts |
| Location never asked, or denied once | The system's ask (fine and coarse together, as phase 2 asks); following starts if precise is granted |
| Approximate only (API 31+) | The system's precise-upgrade ask, as phase 2's banner makes it (phase 2 § 4); following starts if it is granted |
| Approximate kept at that ask, or location denied for good | "Following needs precise location", with a button to the app's location settings; following does not start |
| Location off in the system | The map's "Location is off" banner; following starts once a fix arrives |

**While following** the camera centres on the user at each fix, north up, until the user pans or
zooms; the position button resumes it. The screen keeps its own timeout. The sheet shows, from the
user's Progress: distance, climb and time to the next point by name, and to the end. "Stop" ends
following and leaves the Route drawn; closing the Route ends it too. Editing the Route while
following, by adding, moving or removing a point or reversing it, stops following, and the sheet
says so: the line under Progress has changed.

**Losing location while following** (rule 4's third clause). Precise location revoked, location
switched off, or a grant gone when the app reopens to a followed Route each show the map's existing
banner for that state, and following pauses rather than stopping: Progress and leaving's count stand
still, and the sheet keeps the last figures. The first fix that counts once location is back
resumes it.

**Which fixes count.** A fix older than 30 s when it arrives (the last known fix that
`positionFixes()` emits first, after a pocket or a relaunch) or with an accuracy worse than 50 m
moves the dot and nothing else: it neither moves Progress nor counts towards leaving (§ 4). A fix
beyond the leaving distance counts towards leaving but does not move Progress, so a parallel wrong
path does not carry the user along the Route.

**Progress** is the user's place along the Route's line, and it only moves forward. Each fix that
counts is matched within a window ahead of Progress. The window reaches as far ahead as 6 km/h would
carry the user since the last matched fix, and never less than 200 m, so a long stretch with the
phone in the pocket still finds them. Where the line comes within the leaving distance of the fix
more than once inside the window (up a valley and back down it, a loop that touches itself), each
pass gives one candidate, its nearest point to the fix, and the candidate least far along the line
ahead of Progress wins: it is the pass the user is on, never the later one. Walking back the way
they came leaves Progress where it was rather than jumping. **"Find me on the Route"** matches once
by the same rule with no window, over the whole line ahead of Progress, or from the start before
there is any, for joining a Route halfway or skipping a part of it.

**The end.** Once Progress reaches the last point, having passed every via point on the way, and the
user is within the leaving distance of it (§ 4), the sheet says so and following stops, the Route
still drawn.

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
- "Following stopped: the Route changed"

## 4. Leaving the Route, and the leg back

**Leaving.** The banner shows once every fix that counts (§ 3) for 30 s has been farther from the
Route's line than 40 m, or than the fix's own accuracy where that is larger, and clears at the first
fix back within it. The 30 s is counted across fixes actually received, from the first beyond the
distance: a gap of more than 30 s between two of them (the screen off, a pocket) restarts the count
at the fix after it rather than satisfying it. Both numbers are A's to confirm and R5's to check, and
are changed only by a reading.

**"Back to the Route"** plans a leg over the Trails from the user's position to a point of the line
chosen by its own rule, since a user who has left has no pass within the leaving distance: the
nearest point of the line within the forward window ahead of Progress, or an earlier pass in the
window where that is no more than the leaving distance farther, so on an out-and-back the leg
rejoins the way up rather than skipping the summit on the way down. It is planned by the router as it is,
and drawn apart from the Route, timed by the Pace. The saved Route is not changed. The leg goes when
the user is back on the Route, or when it is planned again. Off the Trails the router starts from
the nearest Trail, as phase 5's ends do, and the leg says how far that is. Replanning the rest of
the Route instead is out: it could drop a via point, a Hut the user meant to reach.

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
- **The time left from inside an edge.** From a Progress partway along an edge, at a Pace, the time
  to the next point and to the end is the rest of that edge (split by `part()`) and the edges after
  it, by the rule, times the Pace, rounded up to 5 minutes; from the start it equals the Route's
  total. Fails towards a time too short, the one weighed against dusk.
- **Leaving** fires after 30 s beyond the distance and not before, and the accuracy term widens it.
  Fails towards a silent wrong turn, or a banner that cries wolf.

## Documents this phase amends

- **`CONTEXT.md`**: **Following**, **Progress**, **Pace**.
- **`PLAN.md`**: this file, linked from phase 6b's paragraph.
- **The store listing** (`docs/store-listing.md`): following a Route and times from your own pace,
  once E closes.
- **The privacy policy** (`docs/privacy-policy.md`), decided with the owner on 2026-10-30: following
  keeps where the user is on a Route, so the policy says so, in E, the checkpoint that starts keeping
  it, and not before. A bullet after the recording's, under *What the app can access, and why*:

  > **Your location while you follow a Route**, only once you tap Follow and allow precise location.
  > While the app is on screen it reads your location to show how far along the Route you are. So
  > that it can pick up where you were if Android closes it, the app keeps the Route you follow and
  > how far along it you are in its settings on the phone, until following ends, or the next time you
  > open the app once 24 hours have passed. It is never sent anywhere by the app, and goes into
  > Android's backup below with the rest of the app's data.

- **`play-app-content.md`**: `ACCESS_FINE_LOCATION`'s row gains following a Route, whose Route and
  Progress are kept in the app's settings on the phone until following ends, or the next open once
  24 hours have passed, in E with the policy. Its numbered "no data collected" reasons gain a sixth
  beside the saved Route's and the recorded Hike's:

  > 6. **Following's Route and Progress are location, stored and not collected.** While following,
  >    the Route's points and how far along it the user is are kept in the app's settings on the
  >    device, and leave it only by point 2's backup, which is the user's own, or by a backup file the
  >    user exports. Since phase 6b's checkpoint E; still no data collected.

  Data safety stays "no data collected": nothing leaves the device. No new permission, no network.

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

R3 and R4 run on the test phone, with `adb shell svc power stayon usb` so its own screen timeout
does not end a long replay, and autostart for the debug build re-read with `scripts/device-gate.py`
first, since the broadcasts need it. R2 and R5 are the owner's Hikes and walk. The debug apparatus
they need, built with B and C, all in `app/src/debug/` with a no-op in `src/release/` where `main/`
calls it:

- **Fixes with their own times.** `overridePosition()` stamps every fix now, so a Track replayed
  faster than real time would look like walking faster than 6 km/h and the window would lose the
  user. `PositionReceiver` gains `--el time <epoch ms>`, set as the fix's `time`, which the window
  and leaving's 30 s are measured by; its `elapsedRealtimeNanos` stays the arrival's, which is what
  freshness (§ 3) reads. `scripts/replay-track.py <gpx> --speed <n> -s <serial>` sends a GPX's
  points through it in order, their times rebased to start now and sent `n` times faster, each with
  an accuracy (10 m unless `--accuracy` says otherwise).
- **The Route on the sheet.** `RouteReceiver` gains `--ez show true`, which opens the Route it has
  just planned on the map's sheet as a Route just planned is, so Follow is one `adb shell input tap`
  (its place read from `uiautomator dump`).
- **What force-stop clears.** The position override is in memory only, so `am force-stop` drops it
  and the relaunched app would read the test phone's real fix at the desk, kilometres off the Route,
  and raise the banner. R4 therefore sends the next replayed fix after the force-stop and before the
  relaunch: the broadcast starts the process, so the app reads the override from its first fix. The
  followed Route and its Progress are in `AppPreferences` and survive.
- **`KozicaFollow`**, one line per fix while following: the fix's time, its accuracy, its distance
  from the line, whether it counted and why not (stale, inaccurate, beyond the distance), Progress in
  metres along the line, the window's reach, leaving's count in seconds, whether the banner shows,
  and the time left to the next point and to the end. **`KozicaPace`**, one line per Hike each time
  the Pace is recomputed (its id, length, moving time, rule's time, ratio, counted or why not) and
  one with the Pace and how many Hikes it is from. `RouteReceiver` logs the time × the Pace beside
  the rule's.

**Where the owner's Hikes meet the debug build.** They are in the Play build, and the debug build has
its own `applicationId`. R2 restores the Play build's manual export (Backup, everything) into the
debug build on the owner's phone: the restore checks the archive's format and schema version and
nothing about the package (`BackupRestorer`), so a debug build at the same schema or a newer one
takes it. Before the restore, the debug build needs by hand what Play's packs and base assets bring,
since installed over adb it has none (*Layout* in `CLAUDE.md`): the relief hand-pushed to `files/relief/relief.pmtiles`
(`RegionSource.kt`), without which `HikeHeights.of` falls back to GPS heights and no Hike counts;
and for R5, the Regions to `files/regions/<id>.pmtiles` and the network and name index to
`files/network/` (`NetworkSource.kt`). R5 runs on that same debug build, so its times are the
owner's Pace rather than the signposts'.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate (§ 1): firings of 40 m / 30 s per hour over real Tracks on Trails | `python3 scripts/track-offsets.py <gate.db or gpx>…` | *(owed)* |
| R2 | The Pace from the owner's Hikes: each Hike's ratio, which counted, the median, and three Routes' times before and after. And whether a GPS Track's length is inflated against the network's line: for a Hike walked on Trails, its length against a Route planned along it; a Track longer than the line lowers the ratio, so a Pace that promises too little time, and a gap over 5 % goes to the owner; B's merge does not wait on R2 | the Play build's export restored into the debug build on the owner's phone; `adb logcat -d -s KozicaPace`; `RouteReceiver` | *(owed)* |
| R3 | Progress on an out-and-back Route and a loop: Progress never moves back, and the time left falls with every fix | `RouteReceiver --ez show true`, Follow tapped, `scripts/replay-track.py`; `adb logcat -d -s KozicaFollow` | *(owed)* |
| R4 | Following survives a killed process: force-stop mid-Route, relaunch, and the sheet is back with the same Progress | R3's replay paused; `am force-stop`, the next fix sent, relaunch, screencap; `KozicaFollow` before and after | **Pass**, 2026-10-31, test phone, by hand since R3's apparatus is not built: a 0.4 km Route from Kuźnice long-pressed, Follow, one `PositionReceiver` fix midway; the sheet read "Do końca: 0,2 km · ↑ 26 m · 10 min" before `am force-stop` and the same after the fix re-sent and a relaunch, the map back on the dot. Stop then left no `followed_route` in the settings (`run-as … cat files/datastore/app_preferences.preferences_pb`) |
| R5 | The closing walk: a Route followed end to end with a deliberate wrong turn and a stretch under poor sky; when the banner showed against the first fix beyond the distance, how far the leg back was, and the time left at three points against the Hike's moving time from each to the end, within 15 % or 10 minutes, whichever is larger | the owner's walk on the debug build, recorded as a Hike; `KozicaFollow` | *(owed)* |
