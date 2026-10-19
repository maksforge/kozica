package app.kozica

import android.app.Application
import app.kozica.data.APP_DATABASE_FILE
import app.kozica.data.APP_SCHEMA_VERSION
import app.kozica.data.AppPreferences
import app.kozica.data.PRESERVED_DIRECTORY
import app.kozica.data.SchemaGate
import app.kozica.data.ThemeMode
import app.kozica.data.preserveBeforeWipe
import app.kozica.data.readUserVersion
import app.kozica.data.schemaGateDecision
import app.kozica.theme.applyThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.maplibre.android.MapLibre
import java.io.File

/**
 * A database this build cannot open as it stands. The copy is already taken and the original is
 * still on disk untouched; what happens next depends on which build found it (ADR-0001).
 *
 * @param preservedCopy where the copy landed, under `filesDir`.
 * @param fromVersion the schema version the file on disk was written at.
 * @param toVersion the version this build expects.
 */
data class SchemaMismatch(
    val preservedCopy: File,
    val fromVersion: Int,
    val toVersion: Int,
)

/**
 * Holds the one [AppContainer] for the process, and **the wipe guard that stands in front of it**.
 *
 * ADR-0001's guard is *structural*, and this class is the structure. The pre-Room version check and
 * the copy-aside run in [onCreate] — four bytes out of a file header, no Room and no container
 * involved — and [container] sits behind a `lazy` that is forced only once any pending wipe has been
 * consented to. No Room object exists, so no collection of one can exist, and the property stays
 * true however the container grows later.
 *
 * The tempting alternative — leave the container constructed and merely stop it from *collecting* —
 * works today and is one eager `stateIn` away from silently breaking, in an app that will go on to
 * add background work. A guard by absence-of-subscription is unwritten and
 * unenforceable, and it would be load-bearing for the only copy of data the user cannot retype.
 *
 * Kotlin note: `by lazy` computes on first read and caches — like a memoised getter, but here the
 * *first read* is the event that matters, which is why nothing may touch [container] before consent.
 */
class MainApplication : Application() {
    /**
     * The scope for work that outlives any screen. `SupervisorJob` so one failed child does not
     * cancel its siblings — the closest JS analogue is `Promise.allSettled` semantics rather than
     * `Promise.all`.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _schemaMismatch = MutableStateFlow<SchemaMismatch?>(null)

    /** Non-null while a blocking schema screen should be shown instead of the app. */
    val schemaMismatch: StateFlow<SchemaMismatch?> = _schemaMismatch.asStateFlow()

    /**
     * The preference store, and the one thing outside the gate that may be read before it.
     *
     * It has to be: the theme is chosen before the first window exists, and DataStore is not the
     * database the guard is protecting.
     */
    lateinit var preferences: AppPreferences
        private set

    /**
     * The theme mode as it was on disk at process start, so the very first composition paints in the
     * right palette rather than flashing light and repainting.
     */
    var startupThemeMode: ThemeMode = ThemeMode.SYSTEM
        private set

    /**
     * **Nothing may read this until [schemaMismatch] is null.** Forcing the lazy constructs Room.
     */
    val container: AppContainer by lazy { AppContainer(this, preferences, applicationScope) }

    override fun onCreate() {
        super.onCreate()

        preferences = AppPreferences(preferencesStore)

        // A blocking read, deliberately and exactly once. Everything else about DataStore is a Flow,
        // but this value has to be known before the first window is created — a Flow collected in a
        // composition arrives a frame later, which is a visible light flash for a user who chose
        // dark. One small file read at process start is the cheaper of the two costs.
        startupThemeMode = runBlocking { preferences.themeModeNow() }
        applyThemeMode(startupThemeMode)

        // Before any MapView exists: its setup asks ConnectivityManager otherwise, which throws
        // without ACCESS_NETWORK_STATE (ADR-0008).
        MapLibre.getInstance(this)
        MapLibre.setConnected(false)

        val databaseFile = getDatabasePath(APP_DATABASE_FILE)
        val onDisk = readUserVersion(databaseFile)

        when (schemaGateDecision(onDisk)) {
            SchemaGate.Open -> Unit

            SchemaGate.Consent, SchemaGate.Refuse -> {
                // The copy is taken *before* anything decides what to do about it, so the file
                // exists whichever branch the user then takes — including "force-stop the app".
                val preserved =
                    preserveBeforeWipe(databaseFile, File(filesDir, PRESERVED_DIRECTORY))
                // Null when nothing was there to preserve after all, so there is nothing to refuse over.
                if (preserved != null) {
                    _schemaMismatch.value =
                        SchemaMismatch(
                            preservedCopy = preserved,
                            fromVersion = onDisk,
                            toVersion = APP_SCHEMA_VERSION,
                        )
                }
            }
        }
    }

    /**
     * The user has read the screen and continued. Only reachable in a build where continuing is
     * survivable — a release build's screen is a dead end by design.
     */
    fun consentToWipe() {
        _schemaMismatch.value = null
    }
}
