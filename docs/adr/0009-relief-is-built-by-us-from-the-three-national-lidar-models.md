# Relief is built by us from the three national lidar terrain models, once, and pinned

## Context

Phase 4 draws shading and contours, and phase 5 reads a route's ascent, from one height grid on the
phone ([`phase-4.md`](../phase-4.md)). The grid has to cover the nine Regions, which cross from
Poland into Slovakia and Czechia. Two things decide its quality more than its resolution: whether
it measures the ground or whatever stands on it, and whether it steps at the two state borders.

Most of the Beskidy, Bieszczady and Sudety are forest. A model that measures the canopy draws
contours that bulge 20–30 m at every forest edge, and an ascent summed over it climbs at each.

Each of the three countries publishes a lidar terrain model, free to reuse: Poland's GUGiK NMT
(1 m, and 5 m), Slovakia's ÚGKK DMR 5.0 (1 m) and Czechia's ČÚZK DMR 4G (5 m). None of their
licences is share-alike: GUGiK asks that the source is stated, the other two are CC BY 4.0. Read on
2026-10-22:

- **GUGiK NMT**: [geoportal.gov.pl's NMT page](https://www.geoportal.gov.pl/pl/dane/numeryczny-model-terenu-nmt/).
- **ČÚZK DMR 4G**: [ČÚZK's open data terms](https://cuzk.gov.cz/Uvod/Produkty-a-sluzby/Otevrena-data/Otevrena-data-zakladni-informace.aspx).
- **ÚGKK DMR 5.0**: CC BY 4.0 confirmed only second-hand, through
  [Mapterhorn's attribution catalogue](https://download.mapterhorn.com/attribution.json). Phase 4's
  checkpoint B confirms it on ÚGKK's own pages in its first commit and links them here.

## Decision

**The grid is built by `scripts/build-relief.py` from the three national terrain models**, each
fetched sheet by sheet through its agency's own index, resampled onto Web Mercator at zoom 12 (about
12 m) as it arrives, and merged into one grid across both borders. The build fails on any empty cell
inside the Regions, clipped to the three countries.

**It is built once, not per release.** A workflow started by hand attaches the output to a GitHub
release of its own, and `scripts/relief.pin` holds its tag and hashes; `publish-play.yml` downloads
and verifies it. A new build is a decision, taken when an agency publishes a new survey.

**It ships as one fast-follow `relief` pack for all Regions together**, not inside each Region's.

## Alternatives

**Copernicus GLO-30.** One worldwide 30 m grid in a public bucket, one licence, no seams. It lost
because it is a surface model: the canopy problem above, across most of the area the app covers.

**Mapterhorn's tiles.** An open project that already merges these three national models, with
GLO-30 filling its gaps, published as Terrarium tiles MapLibre reads directly; a Region's cut is one
`pmtiles extract`. It would have saved the downloaders and the seam. The owner chose to own the
build end to end, as the OSM data is built from Geofabrik's extracts rather than taken ready-made:
a third party's build can change or vanish, and pinning a copy of it still leaves the build itself
outside the repository.

**The national models with GLO-30 filling gaps in advance.** It cannot fail on a gap, which is the
point against it: a fourth source and its long fixed notice added for gaps no one has seen. If one
appears, it is filled for that gap, in an amendment here.

**Built on every release, like the OSM data.** The ground does not change between two OSM pins, so
each release would re-download several GB from three agencies to rebuild identical bytes, and would
fail whenever one of them is down.

**Relief inside each Region's pack.** It was the first plan, so relief came and went with its Region.
With every Region always on the phone ([`phase-2c.md`](../phase-2c.md)) there is no coming and
going, and the Regions' overlaps would stack two shading layers into a dark band at every seam.

## Consequences

- Three downloaders to keep working, each on an agency's own index. They run only when the relief
  is rebuilt, so a broken one is found then, not at a release.
- The build is reproducible from the repository, but only as long as the agencies keep serving the
  same sheets. The pinned release is the copy that every release actually packs.
- Three credits on the licences screen, a short line on the map, and no database to offer: unlike
  ODbL, none of the licences reaches the derived grid.
- Poland's heights mix PL-EVRF2007-NH with PL-KRON86-NH, sheet by sheet, because many of the newer
  PL-EVRF2007-NH 1 m grids are photogrammetric (the amendment below). The KRON86 sheets are
  Kronstadt heights, like the Baltic heights Slovakia and Czechia use, and all of them differ by
  well under a metre. Invisible against 20 m contours; a later use that needs better is
  a new decision.

## Amendment — 2026-10-25: what the agencies serve, and a build on the owner's PC

Reading each agency's service before writing the build changed three of the Decision's
assumptions; the decision itself, the three national models built by us, stands.

- **Poland's 5 m NMT is photogrammetric, not lidar.** GUGiK's sheet index marks each 5 m grid's
  source as aerial photographs, which in forest is the canopy problem this ADR exists to avoid.
  The build takes the newest 1 m lidar grid for each 1:5000 sheet, which covers all but about 220
  of the roughly 5,300 sheets the Regions touch, and falls back to the newest grid of any kind for
  those, logging how many of each it used. GUGiK marks many of its newer PL-EVRF2007-NH 1 m grids
  as aerial photographs too, so where those are the newest the lidar is its PL-KRON86-NH survey of
  about 2011–15: in the Tatry, 128 sheets of 2023–25 EVRF2007 lidar and 45 of 2012 KRON86. That
  makes Poland about 215 GB to download.
- **Czechia's DMR 4G ships as LAZ point files**, a regular 5 m grid of points rather than a raster,
  under CC BY 4.0 ("Data poskytována pod licencí: Creative Commons CC-BY 4.0", atom.cuzk.gov.cz).
- **Slovakia's DMR 5.0 has no sheets**: one 197 GB archive holding a single 157 GB GeoTIFF. The
  build reads the same model through ÚGKK's INSPIRE WCS at 5 m, whose heights are ellipsoidal, and
  corrects them with ÚGKK's own height grid (`sk_gku_Slovakia_ETRS89h_to_Baltic1957.tif`, CC BY
  4.0), pinned by hash. ÚGKK's CC BY 4.0 is now confirmed first-hand: its ALS provision page says
  "Author ÚGKK SR, License CC-BY 4.0" and asks that the source be given as "ÚGKK SR"
  ([provision page](https://www.skgeodesy.sk/geoportal-en/zbgis/als/provision-als-products/),
  [terms](https://www.skgeodesy.sk/files/geoportal/licencne-podmienky/terms_and_conditions_of_provision_and_use_als_products.pdf)).

**The build runs once on the owner's PC, not in a hand-started workflow.** Decided with the owner:
215 GB in one go would pass a hosted runner's six-hour limit, and splitting it across jobs buys
nothing for a build that runs once. This is not the local build ADR-0007 rejected. That one would
have put a developer's machine between a release's tag and its bundle on every release; this one
produces a pinned input, published to its own release with its hashes in `scripts/relief.pin`,
which every release then downloads and checks, and which anyone can rebuild from the committed,
deterministic script and the sheet lists published beside the archives: the agencies' indexes gain
a survey year at a time, so the live ones would pick other sheets.
