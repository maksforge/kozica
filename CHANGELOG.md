# Changelog

## [0.10.0](https://github.com/maksforge/kozica/compare/v0.9.0...v0.10.0) (2026-10-27)


### Features

* replace the placeholder mark with a Tatra chamois on its crag ([1194c37](https://github.com/maksforge/kozica/commit/1194c377359252b9360cccdd37fec0a6622e13fb))


### Bug Fixes

* leave the context alone on Android 13+, where the platform already follows the app's language ([38b2290](https://github.com/maksforge/kozica/commit/38b2290e641689df1b9807292738bb13a6ee85d4))
* show the recording notification and a new hike's name in the app's language, not the phone's, below Android 13 ([e8d8819](https://github.com/maksforge/kozica/commit/e8d881988a17d4fcfa99e68d398b936962303a34))

## [0.9.0](https://github.com/maksforge/kozica/compare/v0.8.1...v0.9.0) (2026-10-27)


### Features

* add photos and a note to a Hike, from the camera while recording or picked afterwards, pinned to its Track ([6580447](https://github.com/maksforge/kozica/commit/65804472a4f2f14349c8caa80b4a9460fce93917))
* add the Hikes tab, with each Hike's Track, totals, ascent, profile, rename, trim and delete ([c41f6c2](https://github.com/maksforge/kozica/commit/c41f6c2bf25c0ddbb1202acb50ec37ce3eea7e11))
* draw the map's compass in the app's colours, with an N at its north tip ([a6cfd2d](https://github.com/maksforge/kozica/commit/a6cfd2da97c6b308ed53efcb1f945f54ca656eae))
* export a Hike or a saved Route as GPX through the share sheet ([007cc64](https://github.com/maksforge/kozica/commit/007cc644f84e917fe029c3ee647c3c5e1e66221c))
* port binky's backup, with the Backup row back in Settings and a banner for photos no export has covered ([0e00ae4](https://github.com/maksforge/kozica/commit/0e00ae452dff06eb721f25e6c2a0942177330fd3))
* record a hike: a location foreground service, pause and auto-resume, schema 3 ([b4845d4](https://github.com/maksforge/kozica/commit/b4845d402143ad949498c3f93c0d3ecb7eb8ed4f))
* say how many photos couldn't be added, and word unpinning a photo as Remove from map ([b4a67d8](https://github.com/maksforge/kozica/commit/b4a67d876bd2b3b653acfb05bda5791ad56c7898))
* translate the Hikes tab and a Hike's sheet into Polish ([3bc2303](https://github.com/maksforge/kozica/commit/3bc2303d66062e319d15fc4e701469cb816c1152))


### Bug Fixes

* centre the compass needle on its disc and line the disc up under Settings ([85be826](https://github.com/maksforge/kozica/commit/85be826eb59c655b99d673f322a2d3c7f476ebd9))
* count GPS ascent from the last turn, as phase 6 § 5 says ([7a80c6d](https://github.com/maksforge/kozica/commit/7a80c6d0e686a19ff122c7411d6f5476a5b9a3d7))
* delete a Hike and its photos' rows in one transaction, so a photo written meanwhile leaves no files behind ([48a3bf1](https://github.com/maksforge/kozica/commit/48a3bf145b7447bcc61f4f9c703704272ab68896))
* derive the compass rim from its size and set the N from its measured cap ([90a19a6](https://github.com/maksforge/kozica/commit/90a19a602853e1d180416c37bfb9b3740a3ee957))
* discard a hike that recorded no point ([735eaaf](https://github.com/maksforge/kozica/commit/735eaaf707b24a42f58f795fc57f5245497471d8))
* don't ask to stop again when the app returns from recents ([999779d](https://github.com/maksforge/kozica/commit/999779d77b1b4f174b65e177cb8d3644709359a4))
* draw a Hike's sheet without its profile when the heights can't be read ([5df4c9e](https://github.com/maksforge/kozica/commit/5df4c9e844e023c1056e74b5d9ffc021c4ae5caa))
* draw the compass as a bezel compass, the owner's pick of three designs ([a1445e0](https://github.com/maksforge/kozica/commit/a1445e083205f85c72847d528c3b2c42aae9ad8e))
* finish a Hike once, reading its points in the transaction that deletes them ([4257630](https://github.com/maksforge/kozica/commit/4257630ba7f2b3ae73c1ffffea93a4b9de8650bb))
* give a late-reported pick its grant back after its share was taken as cancelled ([63a80d6](https://github.com/maksforge/kozica/commit/63a80d65acab60c56886006c1d63eefff5569cd4))
* give the compass needle a centre hub ([d520502](https://github.com/maksforge/kozica/commit/d520502b4786282dfba6b7868ec01b5620187789))
* grant every app that takes a share read access first, since MIUI's share sheet drops the intent's grant ([2816d13](https://github.com/maksforge/kozica/commit/2816d13f97f692091b30261b5414eb81603244ae))
* harden backup and restore against interruption, quota, concurrency and a running recording ([e3d329e](https://github.com/maksforge/kozica/commit/e3d329ec15ed98c597df78835e677b0add6db34d))
* hide a Hike's ascent and descent when none is stored and no height could be read ([b9908d9](https://github.com/maksforge/kozica/commit/b9908d9e56911be4d0355ac0c217bb5e28cfb2de))
* keep a Hike's end when Trim's handle is left there, whatever the slider's float rounding ([b2729e0](https://github.com/maksforge/kozica/commit/b2729e0c03f23f6144c6cb1c40c7cb2c342505ac))
* keep an emoji whole when a GPX file name is cut to length ([fa1fbb5](https://github.com/maksforge/kozica/commit/fa1fbb5ffdd0238cdc0803a51d98909cb03fff4d))
* keep picked photos across a process death, and keep the viewer open on the next photo after a delete ([fe7eff4](https://github.com/maksforge/kozica/commit/fe7eff4366d0e036c0d839dcba873cf61c077c3a))
* keep the map's compass below the banners under the search row ([1c09169](https://github.com/maksforge/kozica/commit/1c091699147f40890efd39d867b426f432d29dbf))
* keep the map's compass below the Safety and Settings buttons ([82d27e4](https://github.com/maksforge/kozica/commit/82d27e4a649845d454ea8e70a64afa08809fbcde))
* keep the notification's Stop confirmation across a recreated activity ([c6232d2](https://github.com/maksforge/kozica/commit/c6232d286e8f5b0c317b4d68a626fc1a01e9826d))
* keep the notification's time counting while paused, as the bar's does ([3d054b4](https://github.com/maksforge/kozica/commit/3d054b47458330e8a9054c84798b0ed76c038bd9))
* let a restore repair images already on the phone, count what it wrote, and hold the backup lock through the folder hand-off ([54eb1da](https://github.com/maksforge/kozica/commit/54eb1daa7308374da5aefc5ce2ba047f2f6bfe05))
* name finishing a lost recording without pointing at a place in Polish ([4f2e7e1](https://github.com/maksforge/kozica/commit/4f2e7e1bacdda92b4e0e0eb25c6416c2e6d4b538))
* read a Hike's ascent off Stop's and Trim's paths, and leave it unread when the read fails ([e47a2e3](https://github.com/maksforge/kozica/commit/e47a2e334f43fb0a7f567b6b003552e98b7faae2))
* revoke a cancelled share's grants when the app is back, instead of sweeping at launch ([799bfce](https://github.com/maksforge/kozica/commit/799bfce702598eb4ce373040fb47591542046c75))
* run a GPX export in its view model, one at a time, and say when it fails ([f0ab70f](https://github.com/maksforge/kozica/commit/f0ab70ffa288860ae4ad0c400902b2f678120fa6))
* run each recording handler whole under one lock, and resume only an open Hike ([c571f29](https://github.com/maksforge/kozica/commit/c571f299159dadd5bcd444117226612017728b41))
* span the compass disc with the needle and set the N inside its north half ([4fe14e8](https://github.com/maksforge/kozica/commit/4fe14e86958cf0afb6b56c0e40ce8cfc3f6d079e))
* sweep share grants only on the user's own launch, not when a share target restarts the process ([24bd01b](https://github.com/maksforge/kozica/commit/24bd01b3b45cc6fa42a6cec31a78fa828517f143))
* take back a share's read grants from every app but the pick, and from all before the files go ([3f2b779](https://github.com/maksforge/kozica/commit/3f2b779e91ef85cd571d78ebdb150feded5fcad7))

## [0.8.1](https://github.com/maksforge/kozica/compare/v0.8.0...v0.8.1) (2026-10-26)


### Bug Fixes

* clear the outside-position banner on any camera move or a fix inside the Regions ([e265861](https://github.com/maksforge/kozica/commit/e265861c7d937a4604db53dfa0652bcb4226c28d))
* fall back to an older Geofabrik extract when a dated one has lost its md5 ([6eec81a](https://github.com/maksforge/kozica/commit/6eec81aa4be3f886c74c787c6a3c6e29e8833fb3))
* keep fading Region names from hiding the towns and peaks beneath them ([36ebf9a](https://github.com/maksforge/kozica/commit/36ebf9aeef003aadc41734b48df5bde164e1d131))
* keep the map over the Regions, shade what lies outside them, and name them when zoomed out ([5a421fb](https://github.com/maksforge/kozica/commit/5a421fb91cddc62ddf6feae2d5625da3aa508ecc))
* let a Route's fit finish while the sheet grows, and say so when the position lies outside the Regions ([c54f6b4](https://github.com/maksforge/kozica/commit/c54f6b4d70ba4df1e41265c29d05447e66fcd4c2))
* pass over a dated extract whose md5 or file answers 404 on the download itself ([6c93822](https://github.com/maksforge/kozica/commit/6c93822906fe3be31bcc0ff72b513a428cc6008b))

## [0.8.0](https://github.com/maksforge/kozica/compare/v0.7.0...v0.8.0) (2026-10-26)


### Features

* build the Trail network and the name index, and plan a leg over them in the debug build ([056c5ef](https://github.com/maksforge/kozica/commit/056c5ef17273ebd06ee405423f019e7a6e24614e))
* count a descent as steep from a 10 % grade, as R2's signposts read it ([1cde8ff](https://github.com/maksforge/kozica/commit/1cde8ff4a6a45fe38445b88ee499d227198cfb0b))
* plan a Route through its points, with its totals, the time to each point, its profile and its demanding stretches ([bfac0c0](https://github.com/maksforge/kozica/commit/bfac0c03291e4c45e19127b80f4d6cd297590ae7))
* save Routes under a name and list them in a Routes tab beside the map ([097276c](https://github.com/maksforge/kozica/commit/097276c9d7f5aceee6aaab1d51b1e9bb150464b4))
* search places and plan a Route over the Trails on the map ([b9b8b2a](https://github.com/maksforge/kozica/commit/b9b8b2aba11d23c49c4a6882075ba3e93f60a2da))
* translate saved Routes and the tab bar into Polish ([891c198](https://github.com/maksforge/kozica/commit/891c1987eb3e947f0cee0aa3417121818d1eef48))
* translate search and the Route into Polish ([3438779](https://github.com/maksforge/kozica/commit/3438779cbf88628f3b2cc457f1e6a7897e2d20ee))


### Bug Fixes

* add long-pressed points in press order and keep the last Route shown while the next is planned ([968e45b](https://github.com/maksforge/kozica/commit/968e45bcce7efc2f818c33ae0442b577c1c2a394))
* cap the Route sheet at half the height, warnings in its scroll, and label its handle ([dee7720](https://github.com/maksforge/kozica/commit/dee77208f77f40e6fe0aa86d7ae409329a5967f1))
* copy the name index once per install and show the placeholder when the copy fails ([1e03d89](https://github.com/maksforge/kozica/commit/1e03d89c220d4abe84f1b8c81b46bb6bb76e470d))
* fit the camera to each Route and centre each offered place only once ([52c2298](https://github.com/maksforge/kozica/commit/52c2298f53b10021a612f42df6cea9087fdbd0e8))
* give part of an edge its own climb and time, and give no time after a missing leg ([0bd2eef](https://github.com/maksforge/kozica/commit/0bd2eef3e0a1d0d285ad9473bb3827126f301567))
* hide the keyboard on its Search key ([3ce1d77](https://github.com/maksforge/kozica/commit/3ce1d77839fb2506395a9f9d9df71069d569e340))
* keep a Trail way that closes on itself in the network ([d84bad9](https://github.com/maksforge/kozica/commit/d84bad93fbc8bcaa94acb80784cba464806da838))
* keep the map's Route in step with the saved one, and Save over it without a cascade ([7cc6556](https://github.com/maksforge/kozica/commit/7cc6556bea75fbe7345e62bccde991163aeb768b))
* keep the Save dialog's text in Compose state so typing does not lag behind the field ([990cac4](https://github.com/maksforge/kozica/commit/990cac4ee44f0bfed9f32b165e5ff941a85ae70c))
* lose no climb at a way's end, and let a join on a one-way edge's node stand on it ([90b230a](https://github.com/maksforge/kozica/commit/90b230a4308955d567a7f7e5ae0b71d191f34909))
* run the dead band and the steep rule over the part of an edge walked, not a share of its raw rise ([55ee94b](https://github.com/maksforge/kozica/commit/55ee94b1a0010b290be473997ffaffdc06638a46))
* run the push hook's notes gate in the checkout the push comes from, and only on a push ([a703cfe](https://github.com/maksforge/kozica/commit/a703cfea6edaf9ad6db915bc5eea58464d64e121))


### Performance Improvements

* plan each saved Route's totals once per process, not on every visit to the tab ([1cc01d4](https://github.com/maksforge/kozica/commit/1cc01d41bb3c48b6a6eafc7142babb55a7d93a1a))

## [0.7.0](https://github.com/maksforge/kozica/compare/v0.6.0...v0.7.0) (2026-10-25)


### Features

* show the shape of the mountains: shading and contours from the relief pack ([db44601](https://github.com/maksforge/kozica/commit/db4460164540c80821ad37774a5b1d47a9600e2d))


### Bug Fixes

* build the map style once, from the Regions and the relief read together, and read the packs afresh for a new collector ([887f1a1](https://github.com/maksforge/kozica/commit/887f1a113b0b090e6657d43ac0a692e7e4189434))

## [0.6.0](https://github.com/srednimax/kozica/compare/v0.5.0...v0.6.0) (2026-10-22)


### Features

* every Region arrives with the app, and the Regions and welcome screens are gone ([5898cfd](https://github.com/srednimax/kozica/commit/5898cfddda055014e717a9f4f3f989e94b81da65))
* leave a failed or cancelled Region to Retry, which shows its size ([872edbb](https://github.com/srednimax/kozica/commit/872edbb62a8856d33eb622c8cbb6e72bd7642864))


### Bug Fixes

* fail a Region Play still calls not downloaded once asked, and log each fetch's outcome ([c77590a](https://github.com/srednimax/kozica/commit/c77590a9540553281cc281905e6c2311366de90d))
* say in the map's first fetch that a cancelled Region waits for Retry ([34b83fa](https://github.com/srednimax/kozica/commit/34b83fa0154eb1f674128a431aac8266b6d7a957))

## [0.5.0](https://github.com/srednimax/kozica/compare/v0.4.1...v0.5.0) (2026-10-22)


### Features

* add the Safety screen, with each country's mountain rescue, rescue app, avalanche bulletin and weather ([7f41a89](https://github.com/srednimax/kozica/commit/7f41a89a4e285ca0957b4cc76329ef6fb7a0a1b8))


### Bug Fixes

* bring the Safety screen into line with phase 3's targets and copy ([61df62d](https://github.com/srednimax/kozica/commit/61df62d58e71e87a2824b593c883110b26b566fb))
* say the Polish fallback number needs a SIM, unlike 112 ([f7cb85a](https://github.com/srednimax/kozica/commit/f7cb85ad930400ea46f0af03dcefa25fa00a5351))

## [0.4.1](https://github.com/srednimax/kozica/compare/v0.4.0...v0.4.1) (2026-10-20)


### Bug Fixes

* address support mail to kozica.support@maksforge.com ([3c98266](https://github.com/srednimax/kozica/commit/3c98266ecc4a9423e4305cb6335e6291c9dc947d))

## [0.4.0](https://github.com/srednimax/kozica/compare/v0.3.0...v0.4.0) (2026-10-20)


### Features

* ask on the first launch which Regions to download, and say where to change them ([3ec89df](https://github.com/srednimax/kozica/commit/3ec89dffa998a8a0848abc2749c2f7261430b3f4))


### Bug Fixes

* hold the first launch's answer for the process, so a recreated Activity draws on its first frame ([ff598b9](https://github.com/srednimax/kozica/commit/ff598b99ddc322b0745a914077331328dcd84a54))
* keep an unset prompt dismissal out of the capture driver's needles so locale runs start ([7a70e80](https://github.com/srednimax/kozica/commit/7a70e80fa177e4de0e2036da8fd39c8b156633d1))
* keep the welcome screen's ticks through process death, and open the map when no Region is left to choose ([a590bb6](https://github.com/srednimax/kozica/commit/a590bb69b1c81167813942e5088c71b85bcc9a37))
* move the map to an arrived Region at no less than the Trails' zoom, and record R7 to R9 ([c21894d](https://github.com/srednimax/kozica/commit/c21894d3cbc323752566aa1b6a10dec59ebea1ee))
* show All Regions as partly checked, and a fixed Region's state under its name, on the welcome screen ([20be3a6](https://github.com/srednimax/kozica/commit/20be3a6317856270da810b92417ef5a578615ce1))

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
