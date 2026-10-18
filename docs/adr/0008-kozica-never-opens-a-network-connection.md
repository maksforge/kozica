# Kozica never opens a network connection; live information is a hand-off to another app

## Context

A hiking app is tempted by the network from every side: online map tiles, mountain weather, the TOPR
avalanche bulletin, trail closures, hut opening hours, sharing your position with someone at home,
cloud sync of hikes, crash reports. Each would be useful somewhere, and most of them are useful exactly
where a phone has no signal: on a ridge, in a valley, in the Tatras' dead zones.

The map data does not need the network: Play delivers it as asset packs
([ADR-0007](0007-osm-data-ships-per-mountain-range-as-play-asset-packs.md)). Nor does the user's
position: GPS is a receiver, and the assistance that speeds up a first fix (A-GPS, Wi-Fi and cell
positioning) is fetched by Android and Google Play services, not by the app, and needs no permission
of ours.

## Decision

**The merged manifest of every build declares no `INTERNET` permission.** `scripts/aab-permissions.py`
forbids it. A dependency that merges it — MapLibre, the Play Billing Library for a later tip — has it
removed with `tools:node="remove"` and must work without it; a library that cannot is not used.
The same goes for `ACCESS_NETWORK_STATE`.

**Live information is a hand-off.** Where the user needs something only the network has, the app
opens another app with an intent and claims nothing about the result:

- mountain rescue: the dialler with 985;
- the official **Ratunek** app of GOPR and TOPR, for calling for help with a position;
- the avalanche bulletin: TOPR's page in the browser;
- mountain weather: IMGW's page in the browser.

They arrive together, before the first stranger installs the app (`PLAN.md`, phase 3).

## Alternatives

**Fetch a narrow set of live data — the avalanche bulletin, the weather.** It is the most useful
online feature for safety. But neither source has a public API, so the app would scrape pages that
change without notice, and a bulletin cached yesterday and shown as today's is worse than none. The
browser shows the page as its authors keep it, with its own date on it.

**Share the hiker's position with someone at home.** It is a real safety feature, and the most
sensitive data an app can hold. It would need a server, an account and a privacy story of its own.
Ratunek already lets rescuers locate a caller.

**Crash reporting.** It is the honest loss: bugs on testers' phones arrive only when someone mails
them, through `ui/support/`. At this size, the mail hand-off and readings from a real phone carry it.

**Cloud backup and sync.** Backup stays on the device and in Android's own backup
([ADR-0005](0005-backup-is-automatic-for-the-database-and-manual-for-media.md)), which needs nothing
from the app's manifest.

**Host the map data ourselves.** Covered and rejected in ADR-0007.

## Consequences

- The privacy policy and Play's Data safety answers stay one sentence: nothing the user enters leaves
  the phone. Adding any network feature reopens both, and this ADR.
- MapLibre needs `MapLibre.setConnected(false)` before the first map is created, or it asks the
  system for network state it no longer has permission to read and throws.
- A hand-off's target can move: a URL changes, Ratunek is renamed. It is fixed in an ordinary release,
  and the browser or the Play Store shows the user that something is wrong rather than the app.
- No crash reports, ever. A fault on a tester's phone is found by them, or by a reading.
