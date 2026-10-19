package app.kozica.ui.map

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.AppPreferences
import app.kozica.data.MapCamera
import app.kozica.data.regions.GeoBounds
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

sealed interface MapStart {
    data class At(
        val camera: MapCamera,
    ) : MapStart

    data class Fit(
        val bounds: GeoBounds,
    ) : MapStart
}

enum class MapEmpty {
    NO_REGIONS,
    UPDATING,
}

/**
 * @param regions null until the first read of the phone's Regions.
 * @param style the expanded style JSON, null until the template is read.
 * @param camera where a new map view opens, null until the stored camera is read.
 * @param location null until the first resume reads it.
 * @param fix the newest position, null while location cannot be read.
 */
data class MapUiState(
    val regions: List<Region>? = null,
    val style: String? = null,
    val camera: MapStart? = null,
    val location: LocationRead? = null,
    val fix: Location? = null,
) {
    private val ready: List<RegionState.Ready> get() = regions.orEmpty().mapNotNull { it.state as? RegionState.Ready }

    // Play invalidates every pack in one app update, so a Region updating beside a ready one is not
    // named here (docs/phase-2.md § 2).
    val empty: MapEmpty?
        get() =
            when {
                regions == null || ready.isNotEmpty() -> null
                regions.any { it.state is RegionState.UpdateInProgress } -> MapEmpty.UPDATING
                else -> MapEmpty.NO_REGIONS
            }

    /** The stalest ready Region's date, because the map is no fresher than its oldest part. */
    val osmDate: Instant? get() = ready.mapNotNull { it.osmDate }.minOrNull()
}

class MapViewModel(
    regions: Flow<List<Region>>,
    private val preferences: AppPreferences,
    private val assets: AssetManager,
    private val applicationScope: CoroutineScope,
    private val positions: (approximateOnly: Boolean) -> Flow<Location>,
) : ViewModel() {
    private val regions = regions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val camera = MutableStateFlow<MapStart?>(null)

    private val style: StateFlow<String?> =
        combine(
            flow { emit(assets.open(STYLE_TEMPLATE).bufferedReader().use { it.readText() }) },
            this.regions
                .filterNotNull()
                .map { it.readyArchives() }
                .distinctUntilChanged(),
        ) { template, archives -> expandStyle(template, archives) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val location = MutableStateFlow<LocationRead?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private val fix: Flow<Location?> =
        location.flatMapLatest { read ->
            when {
                read == null || !read.on -> flowOf(null)
                read.grant == LocationGrant.NONE -> flowOf(null)
                else -> positions(read.grant == LocationGrant.APPROXIMATE)
            }
        }

    val state: StateFlow<MapUiState> =
        combine(this.regions, style, camera, location, fix, ::MapUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch {
            val start =
                preferences.mapCamera.first()?.let(MapStart::At)
                    ?: MapStart.Fit(
                        this@MapViewModel
                            .regions
                            .filterNotNull()
                            .first()
                            .bounds() ?: SOUTHERN_POLAND,
                    )
            camera.compareAndSet(null, start)
        }
    }

    fun cameraIdle(at: MapCamera) {
        camera.value = MapStart.At(at)
    }

    fun locationRead(read: LocationRead) {
        location.value = read
    }

    fun storeCamera() {
        val at = (camera.value as? MapStart.At)?.camera ?: return
        // Back finishes the activity, and that cancels viewModelScope before this write would run.
        applicationScope.launch { preferences.setMapCamera(at) }
    }

    companion object {
        /** From the Karkonosze to the Bieszczady, for a phone with no Region and no stored camera. */
        private val SOUTHERN_POLAND = GeoBounds(south = 49.0, west = 14.8, north = 50.9, east = 23.0)

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    MapViewModel(
                        app.container.regions,
                        app.preferences,
                        app.assets,
                        app.container.applicationScope,
                        app.container::positions,
                    )
                }
            }
    }
}

private fun List<Region>.readyArchives(): List<Pair<String, File>> =
    mapNotNull { region -> (region.state as? RegionState.Ready)?.let { region.id to it.archive } }

private fun List<Region>.bounds(): GeoBounds? =
    mapNotNull { (it.state as? RegionState.Ready)?.bounds }.reduceOrNull { a, b ->
        GeoBounds(
            south = minOf(a.south, b.south),
            west = minOf(a.west, b.west),
            north = maxOf(a.north, b.north),
            east = maxOf(a.east, b.east),
        )
    }
