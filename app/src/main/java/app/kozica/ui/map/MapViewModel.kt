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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

/**
 * @param regions null until the first read of the phone's Regions.
 * @param style the expanded style JSON, null until the template is read.
 * @param camera where a new map view opens, null until the stored camera is read.
 * @param location null until the first resume reads it.
 * @param fix the newest position, null until the first arrives or while location cannot be read.
 */
data class MapUiState(
    val regions: List<Region>? = null,
    val style: String? = null,
    val camera: MapCamera? = null,
    val location: LocationRead? = null,
    val fix: Location? = null,
) {
    private val ready: List<RegionState.Ready> get() = regions.orEmpty().mapNotNull { it.state as? RegionState.Ready }

    val progress: MapProgress? get() = regions?.let(::mapProgress)

    /** The stalest ready Region's date, because the map is no fresher than its oldest part. */
    val osmDate: Instant? get() = ready.mapNotNull { it.osmDate }.minOrNull()
}

class MapViewModel(
    regions: Flow<List<Region>>,
    private val preferences: AppPreferences,
    private val readAsset: (path: String) -> String,
    private val applicationScope: CoroutineScope,
    private val positions: (approximateOnly: Boolean) -> Flow<Location>,
    private val fetch: suspend (ids: List<String>) -> Unit,
    private val confirmFetch: suspend (ActivityResultLauncher<IntentSenderRequest>) -> Boolean,
) : ViewModel() {
    private val regions = regions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val camera = MutableStateFlow<MapCamera?>(null)

    private val dark = MutableStateFlow<Boolean?>(null)

    private val style: StateFlow<String?> =
        combine(
            dark.filterNotNull().map { readAsset(styleTemplate(it)) },
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
        location
            .flatMapLatest { read ->
                when {
                    read == null || !read.on -> flowOf(null)
                    read.grant == LocationGrant.NONE -> flowOf(null)
                    else -> positions(read.grant == LocationGrant.APPROXIMATE)
                }
            }.onStart { emit(null) }

    val state: StateFlow<MapUiState> =
        combine(this.regions, style, camera, location, fix) { regions, style, camera, location, fix ->
            MapUiState(regions, style, camera, location, fix)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch { camera.compareAndSet(null, preferences.mapCamera.first() ?: TATRY) }
        // Play fetches fast-follow packs by itself; this covers one it has not started and a Region a
        // later build adds. A failed or cancelled one waits for Retry (docs/phase-2c.md § 1).
        viewModelScope.launch {
            val missing = toFetch(this@MapViewModel.regions.filterNotNull().first())
            if (missing.isNotEmpty()) applicationScope.launch { fetch(missing) }
        }
    }

    fun retry() {
        val failed =
            regions.value
                .orEmpty()
                .filter { it.state is RegionState.Failed }
                .map { it.id }
        applicationScope.launch { fetch(failed) }
    }

    /** Nothing awaiting confirmation means the state shown was stale, so what showed it is asked for again. */
    fun confirm(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val waiting =
            regions.value
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
                    )
                }
            }
    }
}

private fun List<Region>.readyArchives(): List<Pair<String, File>> =
    mapNotNull { region -> (region.state as? RegionState.Ready)?.let { region.id to it.archive } }
