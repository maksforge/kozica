# Definition of done — what is still open

The **live checklist**. Keep it short: when an item closes, tick it, write the *result* into whatever
long-form record you keep, and delete the detail from here. A session should be able to pick up the
work by reading this file alone.

## The standing schema gate — never ticked, checked at every bump

**An update must migrate an existing install without losing anything** (ADR-0001). This item does not
close. Whenever `APP_SCHEMA_VERSION` changes, all five hold before the release goes out:

1. `MIGRATION_x_y` written **and registered in** `APP_MIGRATIONS` for every step. A migration Room
   never runs is not a migration, and nothing about the build complains.
2. The exported `app/schemas/*/N.json` committed — every later migration is transcribed from it,
   never from the entity classes.
3. `SchemaGateTest` extended, so the **launch gate** is proven to let the upgrade through. Every
   migration test opens the database directly and walks past that gate; this assertion is the only
   thing standing in front of it.
4. A migration test proving the **rows survive** — read values back, do not merely assert nothing
   threw. `runMigrationsAndValidate` compares *shapes* and is blind to a table emptied by a cascading
   `DROP`.
5. **An actual upgrade watched on a phone**: install the previous build, put real data in it, install
   the new one over the top, confirm the app opens and the data is there.
   `./gradlew assembleDebug -PreleaseShapedDebug` is how to do this without touching a Play install —
   it is minified *and* `BuildConfig.DEBUG == false`, so migrations are registered rather than the
   destructive fallback. ⚠️ **Both halves are load-bearing**: a plain debug build takes the fallback
   and proves the opposite of what you wanted.

`scripts/schema-gate.py` enforces 1–3 mechanically in CI. Items 4 and 5 are yours.

## Before the first upload

- [x] **Delete what the app does not need.** Phase 0 deleted the reminders with their WorkManager jobs, and
      the placeholder domain ([`optional-modules.md`](optional-modules.md)). Room, photos and backup
      stay on purpose: Room for phase 5's planned routes ([`phase-0.md`](phase-0.md) § *Not in*),
      photos and backup for phase 6's hike log ([`PLAN.md`](PLAN.md) § *Phase 0*). That is decided
      before the door because a tester's install keeps them: from phase 5 on, deleting one loses a
      tester's data ([`phase-3.md`](phase-3.md) § *The door to closed testing*).
- [x] **Decide `minSdk` deliberately.** Kept at 26 ([`phase-0.md`](phase-0.md) § 0).
- [ ] **Replace the placeholder mark.** `art/mark.py`, then `python3 art/make-launcher-icon.py` and
      `make-feature-graphic.py`. Commit the concept image and record its provenance in
      `art/README.md` — where the art came from is the thing most likely to block a first upload, and
      it is discovered late.
- [x] **Drop the dormant `items` table.** Phase 5's `MIGRATION_1_2` replaced it with saved Routes
      ([`phase-5.md`](phase-5.md) § 5).
- [x] **Rewrite `scripts/edge-to-edge.py`'s SCENES** against your screens. Home and Settings; each
      new screen brings its own scenes.
- [x] **Run the artifact gates before the first tag.** Overtaken: all four never ran locally before a
      tag. Phase 0's R1 ran `aab-permissions.py` on a local `bundleRelease` (versionCode 13,
      2026-10-18), and v0.2.0, the first tag, passed all four in `publish-play.yml` run 39339727698
      on its first try: versionCode 88 matching, 3 permissions all accounted for and none of the 8
      forbidden, all 47 `pl` strings present, 3 reflection classes intact. The failure this item
      guarded against, a stale table found after the tag with the version spent, never happened.
      Every release since runs all four before Play sees the bundle, `publish-play-closed.yml`
      promotes those same bytes, and the standing *every release* check below keeps it.
- [x] **Choose the palette.** Chamois ochre, rock, slate and stone in `scripts/gen_scheme.py`; every
      contrast pair passes. Phase 2's R9 read it against the Trail colours on the map: no seed moved.
- [x] **Choose a dedicated support address.** `kozica.support@maksforge.com` since 2026-10-20, on
      the owner's own domain: Cloudflare Email Routing forwards it to the developer's Gmail, which
      sends as it. `ui/support/SupportHandoff.kt` compiles it in, and the Play listing's contact
      field and the privacy policy's *Contact* give it too. The item once ruled out an alias on
      personal mail, and this is one (replies still carry the Gmail in `Return-Path`); it was relaxed
      because what a build freezes is the address, not the mailbox. Moving the mailbox is now only a
      change to the Cloudflare route; all three change only if the address does. v0.2.0 and v0.3.0
      compile in the Gmail itself, so that inbox stays read for as long as either build survives.
- [x] **Write the listing.** `docs/store-listing.md`, since phase P; phase 3 adds the Safety screen
      to it.
- [x] **Write the privacy policy** and confirm GitHub Pages is serving `docs/`. Play requires a
      *hosted* URL, and an offline app has no server of its own. Served at
      `https://srednimax.github.io/kozica/privacy-policy.html` since 2026-10-20.
- [x] **Answer the Console's App content pages and write [`play-app-content.md`](play-app-content.md)
      in the same sitting.** All ten answered 2026-10-20.
- [ ] **`LICENSE` and `README.md`.** Bootstrap wrote an all-rights-reserved notice that grants
      nothing; choose the licence you mean, and make the README's *Contributing* agree with it.
- [x] **Create the upload keystore, outside the repo.** Recorded in [`RELEASING.md`](RELEASING.md)
      § *The upload key*.
- [x] **Set up the GitHub repository.** The `main: require CI` ruleset, rebase merges, Pages and
      `RELEASE_PLEASE_TOKEN`.
- [x] **Set the five Play secrets and create the service account.** All in place; 0.2.0 reached
      internal testing by itself on 2026-10-20.
- [ ] **Plan the closed test.** A new personal developer account reaches production only after a
      closed test — at the time of writing 12 testers opted in for 14 continuous days, then an access
      application. Recruit the testers before the build is ready, not after; `publish-play-closed.yml`
      moves each build to them.
- [x] **Decide the `applicationId` deliberately.** `io.github.srednimax.kozica`, fixed by the Play
      entry.
- [ ] **Take checkpoint E, phase 2b's since phase 2 split.** Its deadline, before any release
      carries a map pack or the asset-delivery library, passed with 0.3.0, which carried both, and
      what was read then stands: the library in `main/` declares exactly its four permissions (R4),
      the data build runs in `publish-play.yml` (R9, run 39462967609), Play delivered all nine packs
      from the internal track (R7), and the release page's archives match them (R8). **Still open:**
      the Console's foreground-service declaration for `dataSync`, which
      [`play-app-content.md`](play-app-content.md) still records as not made; this item closes when
      that section gets its date. The update size Play reports for a one-pin refresh is not part of
      this gate: only the closed track can read it, on the first pin-only release after the door
      ([`phase-2b.md`](phase-2b.md) § 6).
- [x] **Take phase 1's phone readings before phase 2 builds on MapLibre.** Taken 2026-10-19: R1
      verdict 1, R8 verdict 3 (undecided until E), R9 after fixing a crash, R10's screen
      ([`phase-1.md`](phase-1.md) § 1).

## Phase 5's last reading

- [ ] **R6's Play step** ([`phase-5.md`](phase-5.md) § *Readings*): the first release carrying
      schema 2, from the internal track, over 0.7.0 from Play, updated in place and opened past the
      launch gate with a Route saved before and found after. Taken once that release is out; phase 5
      closes in `PLAN.md` with it.

## After the closed test

- [ ] **The production-access application is in, with its answers recorded verbatim** in
      [`play-app-content.md`](play-app-content.md). Until it is granted the production track does not
      exist. The app this template was extracted from waited one day; that is one datapoint, not a
      promise. **If it comes back rejected, read the reason before changing any artifact.** Changing
      the build first leaves you arguing the reason against a different build.
- [ ] **Decide the first production number.** release-please never jumps to `1.0.0` by itself (with
      the major at 0 even `feat!:` bumps the minor), so it takes a `Release-As: 1.0.0` footer
      ([`RELEASING.md`](RELEASING.md)), or a deliberate decision to stay on 0.x. In order: production
      access granted, *then* that version's release notes in every locale on an ordinary branch (never
      on release-please's own, which it force-pushes), *then* the AAB and the listing go up together.

## Standing checks that never close

- [ ] **Every release: run the artifact checks on the built AAB**, not on the source.
      `aab-permissions.py` is the one that finds what a *dependency* merged into your manifest —
      a permission you never declared, or a `uses-feature` that quietly filters the app off devices.
- [ ] **Every release: read the release notes gate's output** rather than trusting it passed.
- [ ] **Every dependency bump: re-run `licensee`.** The build fails on an unallowed licence, which is
      the point — but the fix is two things, the allowlist *and* the bundled text.
- [ ] **Test the release-shaped build on a device.** A missing R8 keep rule does not crash; it makes
      a feature silently stop working.
