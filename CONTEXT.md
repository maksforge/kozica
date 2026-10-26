# Vocabulary

The words Kozica uses, and the ones it does not.

**The rule this file exists to enforce: one concept, one word, everywhere.** The entity, the DAO, the
route key, the string resource and the sentence in the UI all use it. When the domain word and the
user-facing word genuinely differ, both are written here with the difference stated.

## The domain

| Term | Means | Not |
| --- | --- | --- |
| **Trail** | A marked hiking route with a colour and a symbol, as on the signposts, running over any number of paths and roads. Polish UI: *szlak*. | "route", "track", "path" |
| **Path** | Any footway on the map, marked or not. A Path is never drawn or named as if it were a Trail. Polish UI: *ścieżka*. | "trail" for an unmarked way |
| **Hut** | A staffed mountain hut. Polish UI: *schronisko*. | "shelter", "refuge", "hostel" |
| **Shelter** | An unstaffed roof on or near a Trail. Polish UI: *wiata*. A staffed *chatka* is a Hut. | "hut", *chatka* |
| **Route** | What the user plans: a start, an end and any via points, in an order the user can change, each leg joined over Trails only. It can be saved under a name. Polish UI: *trasa*. | "trail" for a route, "track" (kept for a recorded hike) |
| **Region** | A group of neighbouring mountain ranges that the map data is built and delivered in, e.g. *Beskidy Zachodnie*. Its boundary follows the ranges, not administrative borders, and crosses into Slovakia and Czechia where the ranges do; it stops at Ukraine's and Germany's. The user never picks one: every Region arrives with the app. | "area", "pack", "map" (a Region is what the map shows, not the map itself) |
| **Hike** | One recorded outing, from Record to Stop: its track, its photos, its note. Saved in the Hikes tab. Polish UI: *wycieczka*. | "activity", "walk", "trip", "recording" (the act, not the thing kept) |
| **Track** | The line a Hike leaves: the phone's fixes in order, broken into stretches at each pause. Polish UI: *ślad*. | "route", "trail", "path" |

## Words we deliberately avoid

- **"Trail" never names an unmarked way.** In the Tatras only marked Trails may be walked, so the
  word, the map and the copy keep a Path from passing for one.
- **"Route" and "track" never name a Trail**, because each is kept for a concept of its own: what the
  user plans (a Route, above) and the line a Hike leaves (a Track, above).

## Naming conventions in code

Some names in this codebase are deliberately generic — `AppDatabase`, `AppTheme`, `AppPreferences`,
`AppContainer`, `MainApplication`, `MainActivity`. They stay correct whatever the app is called, so
renaming the app is a package move rather than a repo-wide find-and-replace. Don't rename them to
match the product; name the *domain* types after the domain instead.
