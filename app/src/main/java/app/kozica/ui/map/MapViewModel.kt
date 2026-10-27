package app.kozica.ui.map

import android.annotation.SuppressLint
import android.location.Location
import android.net.Uri
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
import app.kozica.data.HikeEntity
import app.kozica.data.HikePhotoEntity
import app.kozica.data.MapCamera
import app.kozica.data.SavedRoute
import app.kozica.data.gpx.Gpx
import app.kozica.data.gpx.hikeGpx
import app.kozica.data.hikes.Climb
import app.kozica.data.hikes.Height
import app.kozica.data.hikes.Pin
import app.kozica.data.hikes.ProfilePoint
import app.kozica.data.hikes.Track
import app.kozica.data.hikes.along
import app.kozica.data.hikes.climb
import app.kozica.data.hikes.decodeTrack
import app.kozica.data.hikes.profileOf
import app.kozica.data.hikes.trim
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import app.kozica.data.routes.DemandingStretch
import app.kozica.data.routes.Place
import app.kozica.data.routes.PlaceSearch
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.record.Recording
import app.kozica.ui.common.GpxExport
import app.kozica.ui.common.GpxExports
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * @param regions null until the first read of the phone's Regions.
 * @param relief the relief pack, null until its first read or in a build without one.
 * @param style the expanded style JSON, null until the template is read.
 * @param camera where a new map view opens, null until the stored camera is read.
 * @param location null until the first resume reads it.
 * @param fix the newest position, null until the first arrives or while location cannot be read.
 * @param recording the Hike being recorded, null when none is.
 * @param track the recording Hike's Track so far, drawn live (docs/phase-6.md § 2).
 * @param hike the Hike the Hikes tab asked the map to show, null when none is (docs/phase-6.md § 4).
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
    val recording: Recording? = null,
    val track: Track = emptyList(),
    val hike: HikeUiState? = null,
) {
    private val ready: List<RegionState.Ready> get() = regions.orEmpty().mapNotNull { it.state as? RegionState.Ready }

    /** The Regions and the relief, which arrive the same way and are counted together. */
    val packs: List<Region>? get() = regions?.let { it + listOfNotNull(relief) }

    val progress: MapProgress? get() = packs?.let(::mapProgress)

    val reliefShown: Boolean get() = relief?.state is RegionState.Ready

    /** The stalest ready Region's date, because the map is no fresher than its oldest part. */
    val osmDate: Instant? get() = ready.mapNotNull { it.osmDate }.minOrNull()

    /** The recording's Track and the shown Hike's, each stretch its own line. */
    val drawnTrack: Track get() = track + hike?.drawn.orEmpty()

    /** A planned Route or a shown Hike fades the rest of the map (docs/phase-5.md § 4). */
    val faded: Boolean get() = route.planned != null || hike != null
}

/**
 * A Hike shown on the map, its sheet's whole content.
 *
 * @param along metres along the Track to each of its points, in order.
 * @param climb null when no ascent is stored and no height could be read, so none is shown.
 * @param keep the metres Trim keeps, null while not trimming.
 * @param photos in the order they were taken (docs/phase-6.md § 6).
 */
data class HikeUiState(
    val id: String,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val metres: Double,
    val movingSeconds: Long,
    val track: Track,
    val along: List<Double>,
    val climb: Climb?,
    val heights: List<Height?> = emptyList(),
    val profile: List<ProfilePoint> = emptyList(),
    val note: String? = null,
    val photos: List<HikePhoto> = emptyList(),
    val keep: ClosedFloatingPointRange<Double>? = null,
) {
    val photoCount: Int get() = photos.size

    /** The points [keep] holds, by their index among the Track's points in order; null when fewer than two. */
    val kept: IntRange?
        get() {
            val keep = keep ?: return null
            val total = along.lastOrNull() ?: return null
            // The slider hands back floats, so a handle left at an end can land a float step inside it.
            val slack = Math.ulp(total.toFloat()).toDouble()
            val atEnd = keep.endInclusive >= total - slack
            val first = if (keep.start <= slack) 0 else along.indexOfFirst { it >= keep.start }
            val last = if (atEnd) along.lastIndex else along.indexOfLast { it <= keep.endInclusive }
            return (first..last).takeIf { first >= 0 && last > first }
        }

    /** Whether Trim would cut anything. */
    val cuts: Boolean get() = kept?.let { it.first > 0 || it.last < along.lastIndex } == true

    /** The Track, or while trimming the part that stays. */
    val drawn: Track get() = if (keep == null) track else kept?.let { track.trim(it.first, it.last) }.orEmpty()
}

/** A photo of a Hike, its paths resolved; [pin] is null for one with no place on the Track. */
data class HikePhoto(
    val id: String,
    val photo: File,
    val thumbnail: File,
    val caption: String?,
    val pin: Pin?,
)

/** Adds each of [sources] in turn, going on past one that fails; returns how many failed. */
internal suspend fun <T> addEach(
    sources: List<T>,
    add: suspend (T) -> Unit,
): Int =
    sources.count { source ->
        try {
            add(source)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            true
        }
    }

/** The stored ascent, else one read from [heights] when at least one height came back. */
internal fun shownClimb(
    stored: Climb?,
    heights: List<Height?>,
): Climb? = stored ?: heights.takeIf { read -> read.any { it != null } }?.let(::climb)

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
 * @param planned the Route last planned, null under two points or in a build with no network
 *   ([unavailable]). While [planning] it is the one before the latest change, kept so the map does not
 *   flash back to unfaded between the two, but it no longer matches [points].
 * @param naming whether the Save dialog is open. Held here rather than in the sheet's saved state, so
 *   it goes with the Route when a dead process comes back without one.
 */
data class RouteUiState(
    val points: List<RoutePoint> = emptyList(),
    val planned: PlannedRoute? = null,
    val stretches: List<NamedStretch> = emptyList(),
    val unavailable: Boolean = false,
    val planning: Boolean = false,
    val savedId: String? = null,
    val name: String? = null,
    val naming: Boolean = false,
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
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val routeToShow: MutableStateFlow<String?> = MutableStateFlow(null),
    private val loadRoute: suspend (id: String) -> SavedRoute? = { null },
    private val saveRoute: suspend (id: String?, name: String, points: List<RoutePoint>) -> String = { _, _, _ -> "" },
    private val observeName: (id: String) -> Flow<String?> = { emptyFlow() },
    private val recording: Flow<Recording?> = flowOf(null),
    private val observeTrack: (hikeId: String) -> Flow<Track> = { flowOf(emptyList()) },
    private val hikeToShow: MutableStateFlow<String?> = MutableStateFlow(null),
    private val observeHike: (id: String) -> Flow<HikeEntity?> = { flowOf(null) },
    private val observePhotos: (id: String) -> Flow<List<HikePhotoEntity>> = { flowOf(emptyList()) },
    private val resolveMedia: (relativePath: String) -> File = ::File,
    private val heights: suspend (Track) -> List<Height?> = { track -> track.flatten().map { null } },
    private val renameHike: suspend (id: String, name: String) -> Unit = { _, _ -> },
    private val trimHike: suspend (id: String, first: Int, last: Int) -> Unit = { _, _, _ -> },
    private val deleteHike: suspend (id: String) -> Unit = {},
    private val saveNote: suspend (id: String, note: String) -> Unit = { _, _ -> },
    private val savePhoto: suspend (hikeId: String, source: Uri) -> Unit = { _, _ -> },
    private val saveCaption: suspend (photoId: String, caption: String) -> Unit = { _, _ -> },
    private val savePinRemoved: suspend (photoId: String) -> Unit = {},
    private val deleteStoredPhoto: suspend (photoId: String) -> Unit = {},
    private val photosLeftOut: MutableStateFlow<Int> = MutableStateFlow(0),
    private val writeGpx: suspend (Gpx, LocalDate) -> File = { _, _ -> error("No GPX in this view model") },
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
            .flowOn(io)
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

    // Changes whenever the map's Route is replaced, so a Save that returns late lands only on its own.
    private var routeGeneration = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    private val track: Flow<Track> =
        recording
            .map { it?.hikeId }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let(observeTrack) ?: flowOf(emptyList()) }

    private val shownHike = MutableStateFlow<String?>(null)

    private val hikeKeep = MutableStateFlow<ClosedFloatingPointRange<Double>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hike: Flow<HikeUiState?> =
        combine(
            shownHike.flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    combine(observeHike(id), observePhotos(id), ::Pair).mapLatest { (hike, photos) ->
                        hike?.let { hikeState(it, photos) }
                    }
                }
            },
            hikeKeep,
        ) { hike, keep -> hike?.copy(keep = keep) }

    val state: StateFlow<MapUiState> =
        combine(
            combine(packs, style, camera, location, fix) { packs, style, camera, location, fix ->
                MapUiState(packs?.first, packs?.second, style, camera, location, fix)
            },
            search,
            combine(route, hike.onStart { emit(null) }, ::Pair),
            recording,
            track.onStart { emit(emptyList()) },
        ) { map, search, (route, hike), recording, track ->
            map.copy(search = search, route = route, recording = recording, track = track, hike = hike)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch { camera.compareAndSet(null, preferences.mapCamera.first() ?: TATRY) }
        viewModelScope.launch {
            val found = withContext(io) { places() } != null
            search.update { it.copy(available = found) }
        }
        viewModelScope.launch {
            routeToShow.filterNotNull().collect { id ->
                loadRoute(id)?.let(::show)
                routeToShow.compareAndSet(id, null)
            }
        }
        viewModelScope.launch {
            hikeToShow.filterNotNull().collect { id ->
                showHike(id)
                hikeToShow.compareAndSet(id, null)
            }
        }
        viewModelScope.launch { followSavedName() }
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
                            io,
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
        closeHike()
        changePoints { it + RoutePoint(place.latitude, place.longitude, place.name) }
    }

    /** A long-press: the point joins at once, in press order, then takes the name of the place nearest it, if any is near. */
    fun addPoint(
        latitude: Double,
        longitude: Double,
    ) {
        val point = RoutePoint(latitude, longitude)
        closeHike()
        changePoints { it + point }
        viewModelScope.launch {
            val label = withContext(io) { places()?.nearest(latitude, longitude)?.name } ?: return@launch
            route.update { state ->
                state.copy(points = state.points.map { if (it === point) it.copy(label = label) else it })
            }
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

    /** Replaces whatever is on the map with a saved Route, planned again from today's network. */
    private fun show(saved: SavedRoute) {
        dismissOffer()
        closeHike()
        routeGeneration++
        route.update { RouteUiState(savedId = saved.id, name = saved.name) }
        changePoints { saved.points }
    }

    /** A rename in the Routes tab renames the Route on the map; a delete leaves its points unsaved. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun followSavedName() {
        route
            .map { it.savedId }
            .distinctUntilChanged()
            .flatMapLatest { id -> id?.let { observeName(it).map { name -> id to name } } ?: emptyFlow() }
            .collect { (id, name) ->
                route.update {
                    when {
                        it.savedId != id -> it
                        name == null -> it.copy(savedId = null, name = null)
                        else -> it.copy(name = name)
                    }
                }
            }
    }

    /** Replaces whatever is on the map with a Hike from the Hikes tab. */
    private fun showHike(id: String) {
        dismissOffer()
        planning?.cancel()
        routeGeneration++
        route.value = RouteUiState()
        hikeKeep.value = null
        readHeights = null
        shownHike.value = id
    }

    fun closeHike() {
        shownHike.value = null
        hikeKeep.value = null
    }

    // The last Track's heights, so a note or a photo does not read the relief again.
    private var readHeights: Pair<ByteArray, List<Height?>>? = null

    private suspend fun hikeState(
        hike: HikeEntity,
        photos: List<HikePhotoEntity>,
    ): HikeUiState? {
        val endedAt = hike.endedAt ?: return null
        val bytes = hike.track ?: return null
        val track = decodeTrack(bytes)
        val heights =
            readHeights?.takeIf { it.first.contentEquals(bytes) }?.second
                ?: heightsOf(track).also { readHeights = bytes to it }
        val stored =
            hike.ascentMetres?.let { ascent -> Climb(ascent, hike.descentMetres ?: 0.0, hike.ascentFromGps) }
        return HikeUiState(
            id = hike.id,
            name = hike.name,
            startedAt = hike.startedAt,
            endedAt = endedAt,
            metres = hike.metres,
            movingSeconds = hike.movingSeconds,
            track = track,
            along = track.along(),
            climb = shownClimb(stored, heights),
            heights = heights,
            profile = profileOf(track, heights),
            note = hike.note,
            photos =
                photos.map {
                    HikePhoto(
                        id = it.id,
                        photo = resolveMedia(it.photoPath),
                        thumbnail = resolveMedia(it.thumbnailPath),
                        caption = it.caption,
                        pin = if (it.latitude != null && it.longitude != null) Pin(it.latitude, it.longitude) else null,
                    )
                },
        )
    }

    // A failed read draws no profile rather than ending the map's state with it.
    private suspend fun heightsOf(track: Track): List<Height?> =
        try {
            withContext(io) { heights(track) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            track.flatten().map { null }
        }

    /** Trim's handles start at the two ends, keeping everything. */
    fun startTrim() {
        val along = state.value.hike?.along ?: return
        hikeKeep.value = 0.0..(along.lastOrNull() ?: 0.0)
    }

    fun trimTo(keep: ClosedFloatingPointRange<Double>) {
        if (hikeKeep.value != null) hikeKeep.value = keep
    }

    fun cancelTrim() {
        hikeKeep.value = null
    }

    fun trim() {
        val hike = state.value.hike ?: return
        val kept = hike.kept ?: return
        hikeKeep.value = null
        applicationScope.launch { trimHike(hike.id, kept.first, kept.last) }
    }

    fun renameHike(name: String) {
        val id = shownHike.value ?: return
        viewModelScope.launch { renameHike(id, name) }
    }

    fun setNote(note: String) {
        val id = shownHike.value ?: return
        applicationScope.launch { saveNote(id, note) }
    }

    /** One at a time, past the screen's life, each appearing in the strip as it is written. */
    fun addPhotos(
        hikeId: String,
        sources: List<Uri>,
    ) {
        if (sources.isEmpty()) return
        applicationScope.launch {
            val failed = addEach(sources) { savePhoto(hikeId, it) }
            if (failed > 0) photosLeftOut.update { it + failed }
        }
    }

    fun setCaption(
        photoId: String,
        caption: String,
    ) {
        applicationScope.launch { saveCaption(photoId, caption) }
    }

    fun unpinPhoto(photoId: String) {
        applicationScope.launch { savePinRemoved(photoId) }
    }

    fun deletePhoto(photoId: String) {
        applicationScope.launch { deleteStoredPhoto(photoId) }
    }

    private val gpxExports = GpxExports(viewModelScope, writeGpx)
    val gpx: StateFlow<GpxExport> = gpxExports.state

    /** The shown Hike as a GPX file for the share sheet (docs/phase-6.md § 8). */
    fun exportHikeGpx() {
        val hike = state.value.hike ?: return
        val pinned = hike.photos.mapNotNull { photo -> photo.pin?.let { it to photo.caption } }
        gpxExports.start(hike.startedAt.atZone(ZoneId.systemDefault()).toLocalDate()) {
            hikeGpx(hike.name, hike.note, hike.track, hike.heights, pinned)
        }
    }

    fun gpxShared() = gpxExports.shared()

    fun dismissGpxFailure() = gpxExports.dismiss()

    fun deleteHike() {
        val id = shownHike.value ?: return
        closeHike()
        // Past the screen's life: Back right after Delete must not leave the Hike behind.
        applicationScope.launch { deleteHike(id) }
    }

    fun showSaveDialog(shown: Boolean) {
        route.update { it.copy(naming = shown) }
    }

    /** Saves the points under [name]: over the Route they were opened from, or as a new one. */
    fun save(name: String) {
        val current = route.updateAndGet { it.copy(naming = false) }
        val generation = routeGeneration
        viewModelScope.launch {
            val id = saveRoute(current.savedId, name, current.points)
            if (generation == routeGeneration) route.update { it.copy(savedId = id, name = name) }
        }
    }

    private fun changePoints(change: (List<RoutePoint>) -> List<RoutePoint>) {
        val points =
            route
                .updateAndGet {
                    val points = change(it.points)
                    if (points.isEmpty()) {
                        RouteUiState()
                    } else if (points.size < 2) {
                        it.copy(points = points, planned = null, stretches = emptyList(), planning = false)
                    } else {
                        it.copy(points = points, planning = true)
                    }
                }.points
        if (points.isEmpty()) routeGeneration++
        planning?.cancel()
        if (points.size < 2) return
        planning =
            viewModelScope.launch {
                val planned = withContext(compute) { planRoute(points) }
                if (planned == null) {
                    route.update {
                        it.copy(
                            planned = null,
                            stretches = emptyList(),
                            unavailable = true,
                            planning = false,
                        )
                    }
                    return@launch
                }
                val stretches = withContext(io) { name(planned) }
                route.update {
                    it.copy(planned = planned, stretches = stretches, unavailable = false, planning = false)
                }
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
                        routeToShow = app.container.routeToShow,
                        loadRoute = app.container.routes::get,
                        saveRoute = app.container.routes::save,
                        observeName = app.container.routes::observeName,
                        recording = app.container.recorder.recording,
                        observeTrack = app.container.hikes::observeTrack,
                        hikeToShow = app.container.hikeToShow,
                        observeHike = app.container.hikes::observe,
                        observePhotos = app.container.hikes::observePhotos,
                        resolveMedia = app.container.media::resolve,
                        heights = app.container.hikeHeights::of,
                        renameHike = app.container.hikes::rename,
                        trimHike = app.container.hikes::trim,
                        deleteHike = app.container.hikes::delete,
                        saveNote = app.container.hikes::setNote,
                        savePhoto = { id, source -> app.container.hikes.addPhoto(id, source) },
                        saveCaption = app.container.hikes::setCaption,
                        savePinRemoved = app.container.hikes::unpin,
                        deleteStoredPhoto = app.container.hikes::deletePhoto,
                        photosLeftOut = app.container.photosLeftOut,
                        writeGpx = app.container.gpxFiles::write,
                    )
                }
            }
    }
}

private fun Pair<List<Region>, Region?>.flattened(): List<Region> = first + listOfNotNull(second)

private fun List<Region>.readyArchives(): List<Pair<String, File>> =
    mapNotNull { region -> (region.state as? RegionState.Ready)?.let { region.id to it.archive } }
