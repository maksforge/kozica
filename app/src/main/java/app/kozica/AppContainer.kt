package app.kozica

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.kozica.data.AppPreferences
import app.kozica.data.HikeRepository
import app.kozica.data.RouteRepository
import app.kozica.data.buildAppDatabase
import app.kozica.data.hikes.HikeHeights
import app.kozica.data.location.positionFixes
import app.kozica.data.location.withPositionOverride
import app.kozica.data.regions.RegionFiles
import app.kozica.data.regions.RegionOutline
import app.kozica.data.regions.RegionState
import app.kozica.data.regions.regionStates
import app.kozica.data.regions.reliefState
import app.kozica.data.routes.PlaceSearch
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteTotals
import app.kozica.data.routes.TrailNetworks
import app.kozica.data.routes.openPlaces
import app.kozica.data.routes.placesFile
import app.kozica.media.MediaFiles
import app.kozica.record.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    val routes by lazy { RouteRepository(database.routeDao()) }

    val hikeHeights by lazy {
        HikeHeights(
            appContext,
            RegionOutline.of(
                appContext.assets
                    .open(REGIONS_OUTLINE)
                    .bufferedReader()
                    .use { it.readText() },
            ),
        ) { (relief.first().state as? RegionState.Ready)?.archive }
    }

    val hikes by lazy { HikeRepository(database.hikeDao(), hikeHeights::of, { media.delete(it) }, media::persistPhoto) }

    val recorder by lazy { Recorder(appContext, hikes, ::places, applicationScope) }

    /** A saved Route the Routes tab asked the map to show, until the map takes it. */
    val routeToShow = MutableStateFlow<String?>(null)

    /** A Hike the Hikes tab asked the map to show, until the map takes it. */
    val hikeToShow = MutableStateFlow<String?>(null)

    /** Photos that could not be read as an image and so were not added, until the user dismisses the count. */
    val photosLeftOut = MutableStateFlow(0)

    val regions by lazy { regionStates(appContext) }

    val relief by lazy { reliefState(appContext) }

    val regionFiles by lazy { RegionFiles.of(appContext) }

    private val placeIndex by lazy { placesFile(appContext)?.let(::openPlaces) }

    private val planner by lazy { TrailNetworks.load(appContext)?.let(::RoutePlanner) }

    private val planning = Mutex()

    val routeTotals by lazy { RouteTotals(::planRoute) }

    /** Null in a build without the name index. Blocks on its first call, which copies it out of the assets. */
    fun places(): PlaceSearch? = placeIndex

    /** Null in a build without the network. Blocks on its first call, which loads it (docs/phase-5.md § 1). */
    suspend fun planRoute(points: List<RoutePoint>): PlannedRoute? = planning.withLock { planner?.plan(points) }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun positions(approximateOnly: Boolean): Flow<Location> =
        positionFixes(appContext, approximateOnly).withPositionOverride()
}

private const val REGIONS_OUTLINE = "map/regions.geojson"
