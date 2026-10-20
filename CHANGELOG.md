# Changelog

## [0.3.0](https://github.com/srednimax/kozica/compare/v0.2.0...v0.3.0) (2026-10-20)


### Features

* add the Regions screen, to download and remove each Region or all of them ([9c7803c](https://github.com/srednimax/kozica/commit/9c7803c2051d5e96f6555b80a144d9d7a0a0cf71))
* build every Region in the Play release and offer its map data under ODbL on the GitHub release ([e6025a1](https://github.com/srednimax/kozica/commit/e6025a1263758e8d40015b456f01c1ace5fa86d2))


### Bug Fixes

* attach an ODbL notice beside the map archives on the GitHub release ([b0b91fa](https://github.com/srednimax/kozica/commit/b0b91fa55199d44e3b062e97ad4e4e5651889cfb))
* fail a branch dispatch or a missing upload key before the map data build ([ef979cc](https://github.com/srednimax/kozica/commit/ef979ccec0f353cbb4020a7c1c5a58106285b607))
* move the map to one arrived Region, only when it shows no downloaded data, and remember it once shown ([f30ecee](https://github.com/srednimax/kozica/commit/f30ecee692db1ca43d7d0c1d42d5e1169689b86f))
* send Confirm's fallback download from the application scope ([0d21221](https://github.com/srednimax/kozica/commit/0d2122119b82e4eef8eb64f7e13f9f0c11cdba93))

## [0.2.0](https://github.com/srednimax/kozica/compare/v0.1.0...v0.2.0) (2026-10-19)


### Features

* draw Trails side by side with their symbols, over the full map schema, light and dark ([07ffa86](https://github.com/srednimax/kozica/commit/07ffa86c948c3ad96b75591e0b7fc2627f180694))
* give Kozica its chamois-ochre palette ([5d5e222](https://github.com/srednimax/kozica/commit/5d5e22266494ad11901ae1211bece5e83446bdb4))
* open on a home screen that holds the map's place ([048830d](https://github.com/srednimax/kozica/commit/048830d03b3241ef2798e9568cb8c321f9a331ec))
* open the app on the offline map ([133e3bd](https://github.com/srednimax/kozica/commit/133e3bd9e86712a3135986ac8ae830f2b438b422))
* show where you are on the map, with the ask, its answers and a stale fix ([76c1420](https://github.com/srednimax/kozica/commit/76c1420f1164cbcbc2900000d9c53711ac1e3ae7))


### Bug Fixes

* address support mail to the developer's Gmail ([abbf8b8](https://github.com/srednimax/kozica/commit/abbf8b8a2f5fe9d61e465c7017d1f5580d8a1a8b))
* draw the map before the first position fix arrives ([0019336](https://github.com/srednimax/kozica/commit/00193366502036ed67ea4b985fc77291543ded40))
* feed the dot only after its first frame, so a cold start's single approximate fix is drawn ([11b44b0](https://github.com/srednimax/kozica/commit/11b44b0b09288764bde3665a06623278537f21b9))
* give the feature graphic Kozica's own tagline ([f7cadfd](https://github.com/srednimax/kozica/commit/f7cadfda36a2139a09056e3052ac5f0261798de5))
* hide the schema-mismatch continue button where the open would throw ([6369e6d](https://github.com/srednimax/kozica/commit/6369e6d8ffc3b5584beac6320a9fa3d6d21d3c16))
* keep the map style as state so a return from the background never sets it again ([9152545](https://github.com/srednimax/kozica/commit/91525459f0a576d5db89e0d2d928651b6bf28cc4))
* let aab-locale.py pass a string the release shrinks away ([a28c8ea](https://github.com/srednimax/kozica/commit/a28c8eaf1494dc12d26c961cfe1a44ac0874242f))
* name the ODbL row's subject OpenStreetMap so the map's credit stays Home's alone ([c9e7154](https://github.com/srednimax/kozica/commit/c9e71548610f1673c0f9bda46507eb12c56b97f6))
* put temu after the Polish stale age so its abbreviation's full stop does not double ([c81d1fe](https://github.com/srednimax/kozica/commit/c81d1fe059da5fec9048c3a0e63b71ab9631cfdc))
* re-read the location switch while Home is open, and let a pan cancel a pending centre ([e95b37e](https://github.com/srednimax/kozica/commit/e95b37e0c205a0bfaa59dd7bda5c9e1d8580da3d))
* read a Trail's colour= only when its relation has no osmc:symbol ([2b43748](https://github.com/srednimax/kozica/commit/2b43748436797fbf1b05351ab651a4bf77dad720))
* store the map camera when Home is left for another screen and when Back finishes the app ([73e03b8](https://github.com/srednimax/kozica/commit/73e03b87035c782effae31cbadf737e04a20da11))
* survive an osmc:symbol made only of separators ([baf26e7](https://github.com/srednimax/kozica/commit/baf26e7cbc8528886756dd2fb6eb657ea8e5ff86))


### Reverts

* let aab-locale.py check every translated string again ([d2759af](https://github.com/srednimax/kozica/commit/d2759af380f28f9d224a751efef41e08772b2b47))

## Changelog

Nothing released yet.
