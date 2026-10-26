package app.kozica.data

import android.content.Context

/**
 * **The wipe guard, asked from a background entry point** — what every worker, receiver and
 * service must ask before it touches anything (ADR-0001). `RecordingService` asks it, since a
 * notification's action reaches it with no screen in front.
 *
 * The hazard is specific: the OS can start this process with no UI and no user present, and anything
 * that forces the container over a schema this build cannot open destroys the database in the
 * background on a phone nobody is looking at. So the question is asked out of four bytes of the file
 * header rather than by opening anything, and the answer is `true` exactly when the right move is to
 * do nothing.
 *
 * **It asks the launch gate's question, not "do the versions differ".** A debug build still blocks
 * on any mismatch — it has no migrations registered, so opening means wiping — and a release build
 * still blocks on a file no migration covers, where opening would throw in a process with nobody to
 * show it to. What it does *not* block is the ordinary upgrade: a mismatch a registered migration
 * can walk is one the next database open performs anyway, so background work that skipped it would
 * be sitting out a migration happening around it.
 */
internal fun Context.schemaBlocksBackgroundWork(): Boolean =
    schemaGateDecision(
        readUserVersion(getDatabasePath(APP_DATABASE_FILE)),
        APP_SCHEMA_VERSION,
    ) != SchemaGate.Open
