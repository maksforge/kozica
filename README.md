# Kozica

<One or two sentences: what the app does, and for whom.>

<The constraint that shapes everything — for example: "Every feature is free. No ads, no server, no
account.">

## Status

Just bootstrapped from [android-starter](https://github.com/srednimax/android-starter). The release
pipeline and quality gates are real; the screens are still the template's placeholder app. Nothing
is on Play yet.

## Contributing

<Say whether pull requests are accepted — and make it agree with [LICENSE](LICENSE).>

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
