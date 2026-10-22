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
}

private fun mapViewModel(
    regions: Flow<List<Region>>,
    preferences: AppPreferences = AppPreferences(MemoryPreferences()),
    fetch: suspend (List<String>) -> Unit = {},
) = MapViewModel(
    regions = regions,
    preferences = preferences,
    readAsset = { "" },
    applicationScope = CoroutineScope(Dispatchers.Unconfined),
    positions = { MutableSharedFlow<Location>() },
    fetch = fetch,
    confirmFetch = { false },
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
