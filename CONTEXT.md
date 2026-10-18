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
| **Region** | A group of neighbouring mountain ranges that the user downloads or removes as one unit, e.g. *Beskidy Zachodnie*. Its boundary follows the ranges, not administrative borders, and stops at the state border. | "area", "pack", "map" (a Region is what the map shows, not the map itself) |

## Words we deliberately avoid

- **"Trail" never names an unmarked way.** In the Tatras only marked Trails may be walked, so the
  word, the map and the copy keep a Path from passing for one.
- **"Route" and "track" never name a Trail**, because each is kept for a concept of its own: what the
  user plans (phase 5) and the line a recorded hike leaves (phase 6). Each gets a row here when its
  phase opens.

## Naming conventions in code

Some names in this codebase are deliberately generic — `AppDatabase`, `AppTheme`, `AppPreferences`,
`AppContainer`, `MainApplication`, `MainActivity`. They stay correct whatever the app is called, so
renaming the app is a package move rather than a repo-wide find-and-replace. Don't rename them to
match the product; name the *domain* types after the domain instead.
