# Cycling: what the signs, the routers, OSM and the parks say

Research of 2026-11-01 for
[ADR-0011](adr/0011-cycling-follows-marked-bike-routes-and-its-times-are-our-own-estimate.md).

Sources are primary where they could be had: statute texts, official park pages, the PTTK
instruction, and router source code. Anything secondary or unconfirmed is marked **[unconfirmed]**.
The OSM figures come from the Geofabrik extracts in `map-data/extracts/`: Poland's and Czechia's
dated 2026-10-28, Slovakia's 2026-10-01: the counts were run on that Slovak file, and the
2026-10-28 one beside it was not counted again. All three predate the current pin, OpenStreetMap as
of 2026-10-30 (`scripts/osm-data.pin`). Unless a figure says otherwise, a relation is counted when a
sampled member way falls inside a `scripts/regions/*.poly` polygon.

## 1. Signposts: times or kilometres?

**Poland (PTTK).** PTTK's *Instrukcja znakowania szlaków turystycznych* (2014, uchwała 33A/18/2014
ZG PTTK) covers walking, mountain, cycling, skiing and horse trails
([PDF](https://bip.umww.pl/artykuly/2831449/pliki/20240819111222_zaczniknr3instrukcjaznakowaniaszlakowpttk.pdf)).
- Walking signposts give "czas przejścia (góry np. 3h 30') lub odległości w kilometrach (niziny np.
  2,6km)" (§ VIII.A).
- The cycling signpost R-3 gives "nazwy miejscowości … z odległościami w kilometrach", at most two
  places (§ VIII.D.1d).
- The cycling signposts on public roads (R-4c, R-4d) are road signs under the 2003 road-signs
  regulation. R-4d is the "drogowskaz … podający odległość".
- Under an R-4, a plate may give a climb or descent gradient to 1% wherever it exceeds 3% (§
  VIII.D.10).
- **The instruction has no cycling time and no rule for computing one.** The only non-walking time
  it mentions is on horse trails: "w górach należy podawać odległość w godzinach marszu stępem". It
  gives no formula for that either.
- No regional or municipal Polish cycling time convention was found **[unconfirmed: absence of
  evidence only]**.

**Czechia (KČT).**
- KČT's system page says cycle signs (IS 19 boards, IS 21 tables) carry "kromě čísla trasy také
  názvy cílů a vzdálenosti k nim", and long-distance routes name a goal "ve vzdálenosti kolem 25-35
  km" ([kct.cz](https://kct.cz/turisticke-znaceni/system-turistickeho-znaceni/)).
- Czech walking signs are also in km: "směrovky informují o kilometrových vzdálenostech" (same
  page).
- No time appears anywhere in KČT's system.

**Slovakia (SCK/KST).**
- Cycle routes are marked by the Slovenský cykloklub under STN 01 8028 ([OSM
  wiki](https://wiki.openstreetmap.org/wiki/WikiProject_Slovakia/Cycle_routes)). The Prešov region's
  guide says "the direction and the kilometre distance to the destination is determined by a cycle
  marker and a cycle sign"
  ([presovregion.travel](https://presovregion.travel/en/clanok/how-to-find-your-way-around-the-signposting-of-cycling-routes/)).
- STN 01 8028 is a paid standard and was not read
  ([normoff](https://normy.normoff.gov.sk/norma/120217/nahlad)). That signs show km only and never
  times is therefore **[unconfirmed at the standard level]**.

**Conclusion:** in all three countries, cycling signposts show kilometres. None of them has an
official cycling equivalent of PTTK's walking-time rule.

## 2. How planners compute cycling time

| Engine | Flat speed | Uphill | Downhill | Surface / MTB grade |
| --- | --- | --- | --- | --- |
| **BRouter** ([trekking.brf](https://raw.githubusercontent.com/abrensch/brouter/master/misc/profiles2/trekking.brf), [StdPath.java](https://github.com/abrensch/brouter/blob/master/brouter-core/src/main/java/btools/router/StdPath.java)) | A physics model. It solves `S_C_x·v³ + m·g·(C_r + incline)·v = P` with defaults of 90 kg, 100 W, C_r 0.01 and S_C_x 0.225 m². That gives about 21.4 km/h on the flat. | Falls out of the same equation. That gives 3% ≈ 9.7, 5% ≈ 6.7, 10% ≈ 3.7 and 15% ≈ 2.5 km/h. | Capped at `maxSpeed` 45 km/h, or at the way's `maxspeed` if lower. The cap is reached by about −5%. | The time model ignores surface; surface only changes routing cost. The `fastbike` profile uses the same defaults. |
| **GraphHopper** ([BikeCommonAverageSpeedParser](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/util/parsers/BikeCommonAverageSpeedParser.java), [MountainBikeAverageSpeedParser](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/util/parsers/MountainBikeAverageSpeedParser.java), [bike_elevation.json](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/resources/com/graphhopper/custom_models/bike_elevation.json)) | Bike: 18 km/h on asphalt, cycleway and roads; track 12; path or footway 6; pushing sections 4. MTB: 20 km/h paved, 14–16 on dirt and gravel, 12 on grass; track grades run 20, 16, 12, 8, 4. | Slope ≥4% ×0.9; ≥8% ×0.6; ≥12% capped at 6 km/h; ≥15% capped at 3 km/h. | Slope ≤ −4% gives only ×1.10, so a descent is barely faster than the flat. | `mtb_rating` is `mtb:scale` + 1 ([OSMMtbRatingParser](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/util/parsers/OSMMtbRatingParser.java)). The `bike` profile excludes `mtb:scale` ≥ 2. The `mtb` profile caps `mtb:scale` ≥ 3 at 4 km/h with half priority, and excludes 6 ([mtb.json](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/resources/com/graphhopper/custom_models/mtb.json)). |
| **OSRM** ([bicycle.lua](https://github.com/Project-OSRM/osrm-backend/blob/master/profiles/bicycle.lua)) | 15 km/h default; track 12; path 13; pushing 4. Surface table: gravel 6, ground 10, dirt 8, mud and sand 3. | **None.** The stock profile has no elevation. Elevation is possible only through a raster `segment_function`, as in the example [rasterbot.lua](https://github.com/Project-OSRM/osrm-backend/blob/master/profiles/rasterbot.lua): `duration / (1 − 5·slope)`. | Same raster hook, no cap. | Speed is the minimum of the highway, surface, tracktype and smoothness values. It respects `maxspeed`. |
| **Valhalla** ([bicyclecost.cc](https://github.com/valhalla/valhalla/blob/master/src/sif/bicyclecost.cc)) | Road 25, cross 20, hybrid 18, mountain 16 km/h. | Grade factor: +3% ×0.85; +5% ×0.75; +8% ×0.55; +10% ×0.5; +15% ×0.3. | −3% ×1.4; −5% ×1.7; −8% ×2.0; −10% ×2.2 (the table's maximum). | A surface factor per bike type: a mountain bike on "path" gets ×0.55, a road bike there gets 0. Dismount 5.1 km/h. |
| **Komoot** | Not published. A fitness slider from Untrained to Pro changes the time but not the route ([komoot running-app page](https://www.komoot.com/running-app/running-route-app)). The support article on Pace says the duration follows the chosen pace ([support](https://support.komoot.com/hc/en-us/articles/4402920434458-Planning-Tours-on-Android)). | **[unconfirmed]** | **[unconfirmed]** | Komoot exposes sport types (road, gravel, MTB, touring), but not their speeds. |
| **Mapy.com (mapy.cz)** | Not published. A blog says the estimate uses only distance and climb, ignoring surface ([bezpodpory.cz](https://bezpodpory.cz/jak-planovat-bikepacking-nebo-cyklovylet-pres-mapy-cz/)). | **[unconfirmed]** | **[unconfirmed]** | **[unconfirmed]** |

What they share: a flat speed of 15–25 km/h depending on bike type and surface, and a steep falloff
uphill. On the downhill they differ by a factor of 2 or more: GraphHopper adds +10%, Valhalla
multiplies by up to 2.2, and BRouter runs to a 45 km/h cap. For mountain terrain, only GraphHopper
ties speed to `mtb:scale` (4 km/h at scale 3 and above).

## 3. OSM tagging

**Route types and networks.**
- `route=mtb` is for "named, numbered or otherwise signed routes, designated for mountain biking".
  `route=bicycle` is the general case ([wiki](https://wiki.openstreetmap.org/wiki/Tag:route%3Dmtb)).
- Both use `network=icn/ncn/rcn/lcn`.
- Slovakia maps SCK routes to `ncn` (three-digit refs, red), `rcn` (four-digit refs; blue starts
  with 2, green with 5, yellow with 8) and `lcn`
  ([wiki](https://wiki.openstreetmap.org/wiki/WikiProject_Slovakia/Cycle_routes)).
- Czech cyclo routes form one KČT national network, and the number of digits gives the class
  ([wiki](https://wiki.openstreetmap.org/wiki/Cs:%C4%8Cesko/Zna%C4%8Den%C3%AD_cyklotras_v_%C4%8Cesku)).
- Our Regions hold about 1,450 cycling route relations, by two counts:
  - **Per Region**, with `osmium extract -s smart -p scripts/regions/<id>.poly` on each country's
    extract, every relation in the nine outputs counted once by its id, since the Regions overlap
    and cross borders: 1,054 `route=bicycle`, 411 `route=mtb` and, for scale, 2,754 `route=hiking`.
    Per Region below.
  - **By a sampled member way**, the default above: 1024 `route=bicycle` (463 `rcn`, 429 `lcn`, 47
    `ncn`, 16 `icn`) and 410 `route=mtb`. Of the MTB relations, 232 have no `network` and 115 are
    `lcn`. It misses a relation whose sampled way lies outside every polygon while other members lie
    inside, which a smart extract keeps.

| Region | `route=bicycle` | `route=mtb` | `route=hiking` |
| --- | --- | --- | --- |
| `beskid_niski` | 47 | 15 | 179 |
| `beskidy_zachodnie` | 206 | 95 | 571 |
| `bieszczady` | 43 | 4 | 136 |
| `gorce_pieniny` | 120 | 56 | 291 |
| `gory_swietokrzyskie` | 76 | 0 | 67 |
| `sudety_srodkowe` | 229 | 113 | 651 |
| `sudety_wschodnie` | 112 | 68 | 234 |
| `sudety_zachodnie` | 222 | 77 | 519 |
| `tatry` | 95 | 0 | 340 |

A relation in two Regions counts in both rows, so the rows add up to more than the totals.

**Direction.**
- Member roles `forward` and `backward` mean that the route uses a way only in, or only against, its
  drawn direction. `signed_direction=yes` means the route "is to be cycled in only one direction"
  ([wiki](https://wiki.openstreetmap.org/wiki/Tag:route%3Dmtb)).
- `oneway:bicycle=yes/-1/no` overrides `oneway` for cyclists. When it is absent, the cyclist follows
  `oneway` ([wiki](https://wiki.openstreetmap.org/wiki/Key:oneway:bicycle)).
- In our Regions, 929 of the 6866 `route=mtb` member ways (13.5%) carry `oneway=yes/-1`, and 650
  carry a forward or backward role. For `route=bicycle` the figures are 2743 of 71,594 ways (3.8%)
  and 1893.

**Difficulty.**
- `mtb:scale` runs from 0 to 6, for flat and downhill riding. 0 means "no special skills", 3 means
  "very good skills", and 6 means "not rideable".
- `mtb:scale:uphill` runs from 0 to 5. Level 5 means "too steep and/or difficult to ride uphill"
  ([wiki](https://wiki.openstreetmap.org/wiki/Key:mtb:scale)).
- `mtb:type` is "in use" on both ways and routes
  ([wiki](https://wiki.openstreetmap.org/wiki/Key:mtb:type)). Worldwide it is mostly `crosscountry`
  (12.5k), `allmountain` (4.2k) and `downhill` (3.4k)
  ([taginfo](https://taginfo.openstreetmap.org/keys/mtb:type#values)).
- No downhill/flow trail uses `route=piste` in our data. That tag is for winter fatbike pistes only.
- Coverage in our Regions is thin. `mtb:scale` is on 14% of MTB-route ways and 1.5% of bicycle-route
  ways, mostly at 0–1. `surface` is on 61% and 76%.

**Signed trail centres: yes, mapped as one-way.**
- **Singltrek pod Smrkem** ([r611323](https://www.openstreetmap.org/relation/611323),
  `operator=Singltrek, s.r.o.`) is a super-relation of loops. Loops such as Rapický okruh
  ([r1636172](https://www.openstreetmap.org/relation/1636172)) and Hřebenáč
  ([r1636174](https://www.openstreetmap.org/relation/1636174)) are tagged `oneway=yes` with
  `forward` members. Their ways are `highway=path, oneway=yes, bicycle=designated, foot=no`. The
  access line "Nástupní" ([r1636173](https://www.openstreetmap.org/relation/1636173)) has no roles,
  so it is ridden both ways.
- **Enduro Trails Bielsko-Biała**: each line is its own `route=mtb` with `colour`, `ref`,
  `mtb:type=allmountain` and a negative `incline`. Examples are Twister
  ([r5738733](https://www.openstreetmap.org/relation/5738733), −6.4%) and Stefanka
  ([r5738736](https://www.openstreetmap.org/relation/5738736), −4.8%). Most of them have no
  `network` tag. Their ways are mostly `highway=cycleway, oneway=yes, foot=no, bicycle=designated`,
  and their member roles are empty. Climbing is done on a separate uphill line, Daglezjowy
  ([r6674939](https://www.openstreetmap.org/relation/6674939), `incline=6.5%`,
  `mtb:scale:uphill=1`).
- **Rychlebské stezky** ([r377944](https://www.openstreetmap.org/relation/377944),
  `mtb:type=allmountain / enduro`) has 29 of its 61 members with forward or backward roles, but only
  one `oneway` way in the extract. Its sub-trails, such as Trail dr. Wiessnera and SuperFlow, are
  fully one-way. Some Czech centres also use `signed_direction=yes`, for example the
  Mamutí/Lesní/Mlýnský trails (r14302065–7).
- **Inconsistency**: direction sits sometimes on the way (`oneway`), sometimes in the relation role,
  and sometimes in both. No single tag can be relied on.

**Bends: OSM marks almost none off the roads.**
- Counted over all of Poland, on `poland-261028.osm.pbf`: `osmium tags-filter -R` for `hazard` and
  the `traffic_sign` keys, then the bend signs A-1 to A-4 (`PL:A-1` … `PL:A-4`, four of them written
  without the hyphen) read out of every `traffic_sign*` key's sign list (`traffic_sign`, `:forward`,
  `:backward`, and `:2`, which holds one of them), and `osmium getparents` for the ways a node sits
  on.
- 62 objects carry `hazard=curve` (40) or `hazard=curves` (22): 46 ways, all roads, and 16 nodes,
  one of them on a cycleway.
- 138 carry a bend sign: 136 nodes and 2 ways. Most of the nodes stand beside a road rather than on
  it. Of those on a way, one is on a track and the rest on roads; none is on a path, footway or
  cycleway.

**Access.**
- `bicycle=no/yes/designated/dismount` on the way decides access, and GraphHopper, OSRM and Valhalla
  all honour it (see their profiles above).
- In a box over the Polish Tatras, 503 of 1444 path, track and footway ways carry `bicycle=no`, 29
  carry `bicycle=yes` (for example Droga Brata Alberta and Kuźnice), and none carry `designated`.
  Most of TPN's six legal routes (see § 4) are therefore not tagged `bicycle=yes` on the ways
  **[unconfirmed per route: each route was not checked]**.

## 4. Bikes in the national parks

**The statutes forbid bikes off designated routes in all three countries.**
- **PL**: Ustawa o ochronie przyrody, art. 15 ust. 1 pkt 15, forbids "ruchu pieszego, rowerowego,
  narciarskiego i jazdy konnej wierzchem, z wyjątkiem szlaków i tras narciarskich wyznaczonych przez
  dyrektora parku narodowego" (consolidated text [Dz.U. 2024 poz.
  1478](https://api.sejm.gov.pl/eli/acts/DU/2024/1478/text.html)).
- **CZ**: zákon 114/1992 § 16(1)(m) forbids riding "na kole nebo na koni mimo silnice, místní
  komunikace a místa vyhrazená orgánem ochrany přírody"
  ([zakonyprolidi](https://www.zakonyprolidi.cz/cs/1992-114)).
- **SK**: zákon 543/2002 § 14(1)(b), the third degree of protection and the default in national
  parks under § 19(2), forbids "vchádzať alebo stáť s bicyklom … mimo diaľnice, cesty, miestnej
  komunikácie a vyznačenej cyklotrasy" ([2014
  consolidation](https://static.slov-lex.sk/static/SK/ZZ/2002/543/20141201.html)). This text was
  amended in 2022 and 2024, and **the current wording was not verified**.

| Park | What the park's own page or order says |
| --- | --- |
| **TPN** (Tatry PL) | "tylko na udostępnionych do tego celu szlakach lub drogach": six routes, for example Droga pod Reglami 10.7 km, Dolina Chochołowska 6.5 km and Dolina Suchej Wody 6.4 km. There are no bikes on the Morskie Oko road or in Kościeliska, pedestrians have absolute priority, and the legal basis is Zarządzenie 6/2026 ([tpn.gov.pl](https://tpn.gov.pl/turystyka-rowerowa)). A season of 1 May–30 Nov and hours of 8–18 appear only on a retailer's page **[unconfirmed]**. |
| **TANAP** (Tatry SK) | Bikes are allowed on roads, on routes marked with the standard cycle sign, and on designated places in daytime. The basis is § 5 of the Okresný úrad Prešov decree on the Návštevný poriadok ([tanap.sk](https://www.tanap.sk/cykloturistika/)). |
| **Babiogórski** | The official page has no text ([bgpn.gov.pl](https://bgpn.gov.pl/turystyka-rowerowa)). A blog and a rental listing say bikes are not allowed in the park at all ([bezprawnik](https://bezprawnik.pl/rowerem-w-parku-narodowym/)) **[unconfirmed]**. |
| **Gorczański** | The visitor rules say bikes "wyłącznie po odpowiednio oznakowanych szlakach … dopuszczonych do ruchu rowerowego", in groups of at most 5, giving way to walkers, skiers and riders ([regulamin](https://gpn.gov.pl/regulamin-dla-odwiedzajacych-gpn)). There are about nine routes, for example Hucisko–Turbacz and Borek–Schronisko PTTK pod Turbaczem ([gpn.gov.pl](https://gpn.gov.pl/szlaki-rowerowe)). |
| **Pieniński** | Two routes are open: the red Droga Pienińska (Szczawnica–Červený Kláštor, 10.5 km) and the green Krościenko–Kras (2.5 km). "Trasy rowerowe pokrywają się ze szlakami pieszymi" ([piepn.gov.pl](https://piepn.gov.pl/turystyka-rowerowa)). |
| **Bieszczadzki** | Bikes are allowed on public roads and, with a ticket, on marked bike trails (about 75 km in total). "poruszanie się rowerem po pieszych szlakach turystycznych nie jest dozwolone" ([bdpn.gov.pl](https://bdpn.gov.pl/turystyka-rowerowa)). |
| **Magurski** | "turystyka rowerowa tylko po drogach publicznych, wyznaczonych szlakach rowerowych, i drogach zakładowych wskazanych przez Dyrektora", from sunrise to sunset. Five named bike trails, with visitor caps (Zarządzenie 12/2026, annexes 1 and 6, [mpn.gov.pl](https://mpn.gov.pl/zasady-udostepniania-mpn)). |
| **Karkonoski / KRNAP** | **KRNAP**: cycling is banned except on roads and local roads (not on forest roads) and on routes the Správa designates. There are more than 460 km of such routes, under Opatření obecné povahy 1/2026 ([krnap.cz](https://www.krnap.cz/navstevnici/letni-aktivity/cykloturistika/)). **KPN (PL)**: no official page was found. A National Geographic piece says bikes are allowed only on the route segments that cross the park ([natgeo.pl](https://www.national-geographic.pl/traveler/kierunki/polska-dla-aktywnych-unosze-sie-w-karkonosze/)) **[unconfirmed]**. |
| **Gór Stołowych** | Lists designated routes: the "Góry Stołowe" loop Karłów–Pasterka–Droga nad Urwiskiem–Skalne Grzyby–Batorów (24 km), the Masaryk route (about 18 km on the PL side), yellow connectors and ER-2. Walkers have absolute priority ([pngs.gov.pl](https://pngs.gov.pl/turystyka-rowerowa)). |
| **Świętokrzyski** | Lists two: the green walking-and-cycling path Nowa Słupia–Święta Katarzyna, and the "Barabasz" path Bukowa Góra–Wilków ([swpn.gov.pl](https://swpn.gov.pl/turystyka-rowerowa)). It does not state the restriction itself; art. 15 applies. |

Short answer: **yes. In every one of these parks, bikes may go only on designated routes (plus
public roads), and Babia Góra may allow none.** Many of the designated routes are shared with
walking trails, with walkers having priority.

## What this means for Kozica

- **There is no official cycling-time rule to adopt.** Signposts in PL, CZ and SK show kilometres
  only, so km should be the primary figure. Any time Kozica shows is our own estimate and should be
  labelled as one, unlike the PTTK walking time.
- **The time model should be our choice, and we should defend it.** The flat speed, the uphill
  falloff and above all the downhill cap are where the engines disagree, from GraphHopper's +10% to
  BRouter's 45 km/h. On MTB terrain, `surface` and `mtb:scale` should cap speed, as GraphHopper's 4
  km/h at scale 3 and above does. Coverage of those tags is thin (14% / 1.5%), so the defaults will
  carry most of the network.
- **Direction is real for bikes and absent for walkers.** `build-network.py`'s `oneway()` reads only
  foot tags. A cycling network needs `oneway`, `oneway:bicycle`, the relation roles
  `forward`/`backward` and `signed_direction`. Trail-centre descents (Smrk, Bielsko, Rychleby) must
  not be offered uphill, and their separate climbing lines carry the return.
- **The national parks reduce to one rule.** Off public roads, a bike may use only a way on a
  designated route. Inside the park polygons, the router should trust route membership
  (`route=bicycle/mtb`) and `bicycle=no` over plain path access. OSM does not tag TPN's legal routes
  `bicycle=yes` consistently.
- **`route=mtb` mixes two kinds of thing.** It holds long signed touring routes (the `rcn` "Pętla …"
  loops, Babia Góra Trails) and short one-way trail-centre lines with no `network`. The two likely
  need different treatment in display and timing.
