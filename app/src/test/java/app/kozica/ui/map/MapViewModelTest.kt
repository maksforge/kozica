package app.kozica.ui.map

import android.location.Location
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.kozica.data.AppPreferences
import app.kozica.data.MapCamera
import app.kozica.data.regions.GeoBounds
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import app.kozica.data.routes.Place
import app.kozica.data.routes.PlaceKind
import app.kozica.data.routes.PlaceSearch
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.TrailNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // On a cold start the resume effect reads location before the screen subscribes to the state.
    @Test
    fun `the style arrives while the first fix is still pending`() =
        runTest {
            val viewModel =
                MapViewModel(
                    regions = flowOf(emptyList()),
                    preferences = AppPreferences(NoPreferences),
                    readAsset = { """{"version":8,"sources":{},"layers":[]}""" },
                    applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    positions = { MutableSharedFlow<Location>() },
                    fetch = {},
                    confirmFetch = { false },
                )
            viewModel.darkTheme(false)
            viewModel.locationRead(LocationRead(LocationGrant.PRECISE, on = true))

            val drawn =
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(5.seconds) { viewModel.state.first { it.style != null } }
                }

            assertNotNull("no style while waiting for a fix", drawn)
        }

    @Test
    fun `the first launch opens on the Tatry`() =
        runTest {
            val viewModel = mapViewModel(flowOf(listOf(tatry(READY))))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(
                49.23,
                viewModel.state.value.camera
                    ?.latitude,
            )
            assertEquals(
                11.0,
                viewModel.state.value.camera
                    ?.zoom,
            )
        }

    @Test
    fun `a stored camera wins over the Tatry`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val stored = MapCamera(latitude = 50.74, longitude = 15.74, zoom = 13.0)
            preferences.setMapCamera(stored)
            val viewModel = mapViewModel(flowOf(listOf(tatry(READY))), preferences)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(stored, viewModel.state.value.camera)
        }

    @Test
    fun `the map asks once for every Region never asked for`() =
        runTest {
            val fetched = mutableListOf<List<String>>()
            val regions =
                MutableStateFlow(
                    listOf(
                        tatry(RegionState.NotDownloaded),
                        bieszczady(RegionState.Downloading(percent = 30)),
                        Region("beskid_niski", name = 0, RegionState.Failed(errorCode = -6)),
                    ),
                )
            val viewModel = mapViewModel(regions, fetch = { fetched += it })
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            regions.value = listOf(tatry(RegionState.Downloading(percent = 0)), bieszczady(BIESZCZADY_READY))

            assertEquals(listOf(listOf("tatry")), fetched)
        }

    @Test
    fun `the relief is fetched and counted with the Regions`() =
        runTest {
            val fetched = mutableListOf<List<String>>()
            val relief = Region("relief", name = 0, RegionState.NotDownloaded)
            val viewModel =
                mapViewModel(flowOf(listOf(tatry(READY))), fetch = { fetched += it }, relief = flowOf(relief))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(listOf(listOf("relief")), fetched)
            assertEquals(MapProgress.Downloading(ready = 1, total = 2), viewModel.state.value.progress)
        }

    @Test
    fun `long-pressed points are named after the place near them and planned from two on`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)

            val route = settled(viewModel) { it.route.planned != null }.route

            assertEquals(listOf("Near 49.0", "Near 49.0"), route.points.map { it.label })
            assertNotNull(route.planned?.legs?.single())
        }

    @Test
    fun `reverse plans the same points the other way`() =
        runTest {
            val viewModel = routingViewModel()
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)
            settled(viewModel) { it.route.planned != null }

            viewModel.reverse()

            val route =
                settled(
                    viewModel,
                ) {
                    it.route.planned != null &&
                        it.route.points
                            .first()
                            .longitude == 20.02
                }.route
            assertEquals(listOf(20.02, 20.0), route.points.map { it.longitude })
        }

    @Test
    fun `an offered place joins as the next point, under its name`() =
        runTest {
            val viewModel = routingViewModel()
            val place = Place(1, PlaceKind.PEAK, 49.0, 20.01, "tatry", "Kopa")
            viewModel.pick(place)
            viewModel.addOffered()

            val state = settled(viewModel) { it.route.points.isNotEmpty() }
            assertEquals(listOf("Kopa"), state.route.points.map { it.label })
            assertNull(state.search.offered)
        }

    @Test
    fun `a build without the network says planning is not in it`() =
        runTest {
            val viewModel = routingViewModel(planRoute = { null })
            viewModel.addPoint(49.0, 20.0)
            viewModel.addPoint(49.0, 20.02)

            assertTrue(settled(viewModel) { it.route.unavailable }.route.unavailable)
        }

    private suspend fun settled(
        viewModel: MapViewModel,
        until: (MapUiState) -> Boolean,
    ): MapUiState =
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(5.seconds) { viewModel.state.first(until) }
        } ?: error("the state never settled")
}

private val fixture =
    TrailNetwork.read(
        MapViewModelTest::class.java.getResource("/trails-fixture.network")!!.readBytes(),
    )

private object NearbyNames : PlaceSearch {
    override fun search(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ) = emptyList<Place>()

    override fun nearest(
        latitude: Double,
        longitude: Double,
    ) = Place(0, PlaceKind.PLACE, latitude, longitude, "tatry", "Near $latitude")
}

private fun routingViewModel(
    planRoute: suspend (List<RoutePoint>) -> PlannedRoute? = {
        RoutePlanner(fixture).plan(it)
    },
) = MapViewModel(
    regions = flowOf(emptyList()),
    preferences = AppPreferences(MemoryPreferences()),
    readAsset = { "" },
    applicationScope = CoroutineScope(Dispatchers.Unconfined),
    positions = { MutableSharedFlow<Location>() },
    fetch = {},
    confirmFetch = { false },
    places = { NearbyNames },
    planRoute = planRoute,
)

private fun mapViewModel(
    regions: Flow<List<Region>>,
    preferences: AppPreferences = AppPreferences(MemoryPreferences()),
    fetch: suspend (List<String>) -> Unit = {},
    relief: Flow<Region?> = flowOf(null),
) = MapViewModel(
    regions = regions,
    preferences = preferences,
    readAsset = { "" },
    applicationScope = CoroutineScope(Dispatchers.Unconfined),
    positions = { MutableSharedFlow<Location>() },
    fetch = fetch,
    confirmFetch = { false },
    relief = relief,
)

private fun tatry(state: RegionState) = Region("tatry", name = 0, state)

private val TATRY_BOUNDS = GeoBounds(south = 49.1, west = 19.6, north = 49.4, east = 20.3)

private val READY = RegionState.Ready(File("tatry.pmtiles"), osmDate = null, bounds = TATRY_BOUNDS)

private fun bieszczady(state: RegionState) = Region("bieszczady", name = 0, state)

private val BIESZCZADY_BOUNDS = GeoBounds(south = 49.0, west = 22.0, north = 49.5, east = 22.9)

private val BIESZCZADY_READY = RegionState.Ready(File("bieszczady.pmtiles"), osmDate = null, bounds = BIESZCZADY_BOUNDS)

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}

private object NoPreferences : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(emptyPreferences())
}
