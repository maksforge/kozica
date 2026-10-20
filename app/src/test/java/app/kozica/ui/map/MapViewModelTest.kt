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
    fun `a Region that arrives is shown once`() =
        runTest {
            val regions = MutableStateFlow(listOf(tatry(RegionState.NotDownloaded)))
            val viewModel = mapViewModel(regions)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            regions.value = listOf(tatry(READY))
            assertEquals(TATRY_BOUNDS, viewModel.state.value.arrival)

            viewModel.arrivalShown(TATRY_BOUNDS)
            assertNull(viewModel.state.value.arrival)
        }

    // Play invalidates every pack in an app update, which brings each Region back to Ready.
    @Test
    fun `a Region updated in place is no arrival`() =
        runTest {
            val regions = MutableStateFlow(listOf(tatry(READY)))
            val viewModel = mapViewModel(regions)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.arrivalShown(TATRY_BOUNDS)

            regions.value = listOf(tatry(RegionState.UpdateInProgress(percent = 40)))
            regions.value = listOf(tatry(READY))

            assertNull(viewModel.state.value.arrival)
        }

    @Test
    fun `a Region removed and downloaded again is an arrival`() =
        runTest {
            val regions = MutableStateFlow(listOf(tatry(RegionState.NotDownloaded)))
            val viewModel = mapViewModel(regions)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            regions.value = listOf(tatry(READY))
            viewModel.arrivalShown(TATRY_BOUNDS)

            regions.value = listOf(tatry(RegionState.NotDownloaded))
            regions.value = listOf(tatry(READY))

            assertEquals(TATRY_BOUNDS, viewModel.state.value.arrival)
        }

    @Test
    fun `a Region shown before a restart is no arrival after it`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val regions = MutableStateFlow(listOf(tatry(RegionState.NotDownloaded)))
            val before = mapViewModel(regions, preferences)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { before.state.collect {} }
            regions.value = listOf(tatry(READY))
            before.arrivalShown(TATRY_BOUNDS)

            val after = mapViewModel(regions, preferences)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { after.state.collect {} }

            assertNull(after.state.value.arrival)
        }

    // Process death between the download and the map's return.
    @Test
    fun `an arrival never shown survives a restart`() =
        runTest {
            val preferences = AppPreferences(MemoryPreferences())
            val regions = MutableStateFlow(listOf(tatry(RegionState.NotDownloaded)))
            val before = mapViewModel(regions, preferences)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { before.state.collect {} }
            regions.value = listOf(tatry(READY))

            val after = mapViewModel(regions, preferences)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { after.state.collect {} }

            assertEquals(TATRY_BOUNDS, after.state.value.arrival)
        }

    @Test
    fun `Regions that arrive together fit the first, not their union`() =
        runTest {
            val regions =
                MutableStateFlow(listOf(tatry(RegionState.NotDownloaded), bieszczady(RegionState.NotDownloaded)))
            val viewModel = mapViewModel(regions)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            regions.value = listOf(tatry(READY), bieszczady(BIESZCZADY_READY))

            assertEquals(TATRY_BOUNDS, viewModel.state.value.arrival)
        }

    @Test
    fun `a Region that arrives while the map shows a ready one leaves the camera`() =
        runTest {
            val regions =
                MutableStateFlow(listOf(tatry(RegionState.NotDownloaded), bieszczady(RegionState.NotDownloaded)))
            val viewModel = mapViewModel(regions)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            regions.value = listOf(tatry(READY), bieszczady(RegionState.NotDownloaded))
            viewModel.arrivalShown(TATRY_BOUNDS)

            regions.value = listOf(tatry(READY), bieszczady(BIESZCZADY_READY))

            assertNull(viewModel.state.value.arrival)
        }

    @Test
    fun `a Region that arrives while the map looks elsewhere is an arrival`() =
        runTest {
            val regions = MutableStateFlow(listOf(tatry(READY), bieszczady(RegionState.NotDownloaded)))
            val viewModel = mapViewModel(regions)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.arrivalShown(TATRY_BOUNDS)
            viewModel.cameraIdle(MapCamera(latitude = 50.06, longitude = 19.94, zoom = 10.0))

            regions.value = listOf(tatry(READY), bieszczady(BIESZCZADY_READY))

            assertEquals(BIESZCZADY_BOUNDS, viewModel.state.value.arrival)
        }
}

private fun mapViewModel(
    regions: Flow<List<Region>>,
    preferences: AppPreferences = AppPreferences(MemoryPreferences()),
) = MapViewModel(
    regions = regions,
    preferences = preferences,
    readAsset = { "" },
    applicationScope = CoroutineScope(Dispatchers.Unconfined),
    positions = { MutableSharedFlow<Location>() },
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
