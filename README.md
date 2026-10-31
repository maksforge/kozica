# Kozica

Kozica is an offline hiking map of the Polish mountains, from the Karkonosze to the Bieszczady and
across the Czech and Slovak borders, with every marked trail drawn in its signposts' colour. Plan a
route over the trails, record a hike, and look back at it, all without signal.

Everything stays on the phone: the app never opens a network connection. Free, no ads, no account.

## Status

On Google Play's internal and closed testing tracks; production and 1.0 follow the closed test.

## Contributing

Kozica is source-available, not open source: read it, verify it, file issues. Pull requests are not
accepted — see [LICENSE](LICENSE).

## Build

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # build + install on the connected phone
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests — needs a device
./gradlew spotlessApply          # format (the CI gate is spotlessCheck)
python3 scripts/project.py       # what the toolchain thinks this app is called
```

Commit subjects are [Conventional Commits](https://www.conventionalcommits.org); release-please
derives the version and `CHANGELOG.md` from them. See [docs/RELEASING.md](docs/RELEASING.md).
