package app.kozica

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.kozica.data.AppPreferences
import app.kozica.data.ItemRepository
import app.kozica.data.buildAppDatabase
import app.kozica.data.location.positionFixes
import app.kozica.data.location.withPositionOverride
import app.kozica.data.regions.RegionFiles
import app.kozica.data.regions.regionStates
import app.kozica.data.regions.reliefState
import app.kozica.media.MediaFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * `internal` rather than private: [MainApplication] holds the one [AppPreferences] over this store
 * and hands it to the container, because the theme has to read a preference before the container is
 * allowed to exist (ADR-0001's gate).
 *
 * Kotlin note: `by preferencesDataStore(...)` is a property delegate on `Context` — it creates the
 * store once per process and hands the same instance to every caller. Creating a second DataStore
 * over the same file throws, so this must stay the only one.
 */
internal val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Manual dependency injection — deliberately not Hilt.
 *
 * At the size a single-developer app reaches, constructing things by hand is clearer than a
 * generated graph: the whole object graph is this file, you can read it top to bottom, and there is
 * no annotation processor round trip in every build. Migrating to Hilt later is mechanical, because
 * everything here is already constructor injection.
 *
 * The rule that makes it work: **nothing constructs its own dependencies.** A ViewModel takes a
 * repository, a repository takes a DAO and a `MediaFiles`, and only this file calls a constructor.
 * The moment a class reaches for a singleton instead, it stops being testable without a device.
 *
 * Lives at the package root with the app shell: like `Navigation.kt`, it describes how the app hangs
 * together rather than belonging to any one screen.
 */
class AppContainer(
    context: Context,
    val preferences: AppPreferences,
    val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    /**
     * Kotlin note: `by lazy` here is the ordinary decorative kind — build it on first use. It is
     * **not** the gate; the gate is the `lazy` on `MainApplication.container`, which decides whether
     * this object is ever constructed at all.
     */
    private val database by lazy { buildAppDatabase(appContext) }

    val media by lazy { MediaFiles(appContext) }

    val items by lazy { ItemRepository(database.itemDao(), media) }

    val regions by lazy { regionStates(appContext) }

    val relief by lazy { reliefState(appContext) }

    val regionFiles by lazy { RegionFiles.of(appContext) }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun positions(approximateOnly: Boolean): Flow<Location> =
        positionFixes(appContext, approximateOnly).withPositionOverride()
}
