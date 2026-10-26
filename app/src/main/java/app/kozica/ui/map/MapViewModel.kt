package app.kozica.ui.map

import android.annotation.SuppressLint
import android.location.Location
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.AppPreferences
import app.kozica.data.MapCamera
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import app.kozica.data.routes.DemandingStretch
import app.kozica.data.routes.Place
import app.kozica.data.routes.PlaceSearch
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * @param regions null until the first read of the phone's Regions.
 * @param relief the relief pack, null until its first read or in a build without one.
 * @param style the expanded style JSON, null until the template is read.
 * @param camera where a new map view opens, null until the stored camera is read.
 * @param location null until the first resume reads it.
 * @param fix the newest position, null until the first arrives or while location cannot be read.
 */
data class MapUiState(
    val regions: List<Region>? = null,
    val relief: Region? = null,
    val style: String? = null,
    val camera: MapCamera? = null,
    val location: LocationRead? = null,
    val fix: Location? = null,
    val search: SearchUiState = SearchUiState(),
    val route: RouteUiState = RouteUiState(),
) {
    private val ready: List<RegionState.Ready> get() = regions.orEmpty().mapNotNull { it.state as? RegionState.Ready }

    /** The Regions and the relief, which arrive the same way and are counted together. */
    val packs: List<Region>? get() = regions?.let { it + listOfNotNull(relief) }

    val progress: MapProgress? get() = packs?.let(::mapProgress)

    val reliefShown: Boolean get() = relief?.state is RegionState.Ready

    /** The stalest ready Region's date, because the map is no fresher than its oldest part. */
    val osmDate: Instant? get() = ready.mapNotNull { it.osmDate }.minOrNull()
}

/**
 * @param available null until the name index has been looked for, false in a build without one.
 * @param offered the result picked, centred on the map and offered as a point.
 */
data class SearchUiState(
    val available: Boolean? = null,
    val results: List<Place> = emptyList(),
    val offered: Place? = null,
)

/**
 * @param planned the Route through [points], null under two points, while it is planned, or in a
 *   build with no network ([unavailable]).
 */
data class RouteUiState(
    val points: List<RoutePoint> = emptyList(),
    val planned: PlannedRoute? = null,
    val stretches: List<NamedStretch> = emptyList(),
    val unavailable: Boolean = false,
)

/** A demanding stretch with the places that name its two ends, either null where nothing is near. */
data class NamedStretch(
    val stretch: DemandingStretch,
    val from: String?,
    val to: String?,
)

class MapViewModel(
    regions: Flow<List<Region>>,
    private val preferences: AppPreferences,
    private val readAsset: (path: String) -> String,
    private val applicationScope: CoroutineScope,
    private val positions: (approximateOnly: Boolean) -> Flow<Location>,
    private val fetch: suspend (ids: List<String>) -> Unit,
    private val confirmFetch: suspend (ActivityResultLauncher<IntentSenderRequest>) -> Boolean,
    relief: Flow<Region?> = flowOf(null),
    private val places: suspend () -> PlaceSearch? = { null },
    private val planRoute: suspend (List<RoutePoint>) -> PlannedRoute? = { null },
) : ViewModel() {
    // Paired before stateIn, so the style is never built from Regions whose relief has not been read yet.
    private val packs: StateFlow<Pair<List<Region>, Region?>?> =
        combine(regions, relief, ::Pair).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val camera = MutableStateFlow<MapCamera?>(null)

    private val dark = MutableStateFlow<Boolean?>(null)

    private val style: StateFlow<String?> =
        combine(
            dark.filterNotNull().map { readAsset(styleTemplate(it)) },
            packs
                .filterNotNull()
                .map { (regions, relief) -> regions.readyArchives() to (relief?.state as? RegionState.Ready)?.archive }
                .distinctUntilChanged(),
        ) { template, (archives, relief) -> expandStyle(template, archives, relief) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val location = MutableStateFlow<LocationRead?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private val fix: Flow<Location?> =
        location
            .flatMapLatest { read ->
                when {
                    read == null || !read.on -> flowOf(null)
                    read.grant == LocationGrant.NONE -> flowOf(null)
                    else -> positions(read.grant == LocationGrant.APPROXIMATE)
                }
            }.onStart { emit(null) }

    private val search = MutableStateFlow(SearchUiState())

    private val route = MutableStateFlow(RouteUiState())

    private var searching: Job? = null

    private var planning: Job? = null

    val state: StateFlow<MapUiState> =
        combine(
            combine(packs, style, camera, location, fix) { packs, style, camera, location, fix ->
                MapUiState(packs?.first, packs?.second, style, camera, location, fix)
            },
            search,
            route,
        ) { map, search, route -> map.copy(search = search, route = route) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch { camera.compareAndSet(null, preferences.mapCamera.first() ?: TATRY) }
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { places() } != null
            search.update { it.copy(available = found) }
        }
        // Play fetches fast-follow packs by itself; this covers one it has not started and a Region a
        // later build adds. A failed or cancelled one waits for Retry (docs/phase-2c.md § 1).
        viewModelScope.launch {
            val missing = toFetch(packs.filterNotNull().first().flattened())
            if (missing.isNotEmpty()) applicationScope.launch { fetch(missing) }
        }
    }

    fun retry() {
        val failed =
            packs.value
                ?.flattened()
                .orEmpty()
                .filter { it.state is RegionState.Failed }
                .map { it.id }
        applicationScope.launch { fetch(failed) }
    }

    /** Nothing awaiting confirmation means the state shown was stale, so what showed it is asked for again. */
    fun confirm(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val waiting =
            packs.value
                ?.flattened()
                .orEmpty()
                .filter { it.state == RegionState.WaitingForWifi || it.state == RegionState.NeedsConfirmation }
                .map { it.id }
        viewModelScope.launch { if (!confirmFetch(launcher)) applicationScope.launch { fetch(waiting) } }
    }

    fun cameraIdle(at: MapCamera) {
        camera.value = at
    }

    fun darkTheme(on: Boolean) {
        dark.value = on
    }

    fun locationRead(read: LocationRead) {
        location.value = read
    }

    fun storeCamera() {
        val at = camera.value ?: return
        // Back finishes the activity, and that cancels viewModelScope before this write would run.
        applicationScope.launch { preferences.setMapCamera(at) }
    }

    /** Results nearest the map's centre first, so the newest text wins over a slower older one. */
    fun search(text: String) {
        searching?.cancel()
        val centre = camera.value ?: TATRY
        searching =
            viewModelScope.launch {
                val results =
                    if (text.isBlank()) {
                        emptyList()
                    } else {
                        withContext(
                            Dispatchers.IO,
                        ) { places()?.search(text, centre.latitude, centre.longitude) }.orEmpty()
                    }
                search.update { it.copy(results = results) }
            }
    }

    fun pick(place: Place) {
        searching?.cancel()
        search.update { it.copy(results = emptyList(), offered = place) }
    }

    fun dismissOffer() {
        search.update { it.copy(offered = null) }
    }

    fun addOffered() {
        val place = search.value.offered ?: return
        dismissOffer()
        changePoints { it + RoutePoint(place.latitude, place.longitude, place.name) }
    }

    /** A long-press: the point is named after the place nearest it, if any is near. */
    fun addPoint(
        latitude: Double,
        longitude: Double,
    ) {
        viewModelScope.launch {
            val label = withContext(Dispatchers.IO) { places()?.nearest(latitude, longitude)?.name }
            changePoints { it + RoutePoint(latitude, longitude, label) }
        }
    }

    fun removePoint(index: Int) {
        changePoints { points -> points.filterIndexed { i, _ -> i != index } }
    }

    fun movePoint(
        from: Int,
        to: Int,
    ) {
        changePoints { points ->
            if (to !in points.indices) return@changePoints points
            points.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun reverse() {
        changePoints { it.reversed() }
    }

    private fun changePoints(change: (List<RoutePoint>) -> List<RoutePoint>) {
        val points =
            route
                .updateAndGet {
                    it.copy(
                        points = change(it.points),
                        planned = null,
                        stretches = emptyList(),
                    )
                }.points
        planning?.cancel()
        if (points.size < 2) return
        planning =
            viewModelScope.launch {
                val planned = withContext(Dispatchers.Default) { planRoute(points) }
                if (planned == null) {
                    route.update { it.copy(unavailable = true) }
                    return@launch
                }
                val stretches = withContext(Dispatchers.IO) { name(planned) }
                route.update { it.copy(planned = planned, stretches = stretches, unavailable = false) }
            }
    }

    private suspend fun name(planned: PlannedRoute): List<NamedStretch> {
        val index = places()
        return planned.demanding.map { stretch ->
            fun at(metres: Double) =
                planned.line.firstOrNull { it.metres >= metres }?.let {
                    index
                        ?.nearest(
                            it.latitude,
                            it.longitude,
                        )?.name
                }
            NamedStretch(stretch, at(stretch.fromMetres), at(stretch.toMetres))
        }
    }

    companion object {
        /** The first launch's camera, before any is stored: the Tatry at a zoom where Trails draw. */
        private val TATRY = MapCamera(latitude = 49.23, longitude = 20.0, zoom = 11.0)

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    MapViewModel(
                        app.container.regions,
                        app.preferences,
                        { path ->
                            app.assets
                                .open(path)
                                .bufferedReader()
                                .use { it.readText() }
                        },
                        app.container.applicationScope,
                        app.container::positions,
                        app.container.regionFiles::download,
                        app.container.regionFiles::confirm,
                        app.container.relief,
                        app.container::places,
                        app.container::planRoute,
                    )
                }
            }
    }
}

private fun Pair<List<Region>, Region?>.flattened(): List<Region> = first + listOfNotNull(second)

private fun List<Region>.readyArchives(): List<Pair<String, File>> =
    mapNotNull { region -> (region.state as? RegionState.Ready)?.let { region.id to it.archive } }
