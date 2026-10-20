package app.kozica.ui.welcome

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import app.kozica.data.AppPreferences
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val store = MemoryPreferences()
    private val preferences = AppPreferences(store)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with every Region ready or on its way, the welcome is answered on its own`() =
        runTest {
            welcome(listOf(region("tatry", RegionState.Downloading(percent = 10))))

            assertTrue(preferences.onboardingDone.first())
        }

    @Test
    fun `with a Region to choose, the welcome waits for an answer`() =
        runTest {
            welcome(listOf(region("tatry", RegionState.NotDownloaded)))

            assertFalse(preferences.onboardingDone.first())
        }

    @Test
    fun `the ticks outlive the ViewModel through its saved state`() =
        runTest {
            val regions =
                listOf(region("tatry", RegionState.NotDownloaded), region("bieszczady", RegionState.NotDownloaded))
            val savedState = SavedStateHandle()
            welcome(regions, savedState).toggle("tatry")

            val restored = welcome(regions, savedState)

            assertEquals(setOf("tatry"), restored.state.first { it.regions != null }.selected)
        }

    private fun welcome(
        regions: List<Region>,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = WelcomeViewModel(
        regions = flowOf(regions),
        downloadRegions = {},
        preferences = preferences,
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
        savedState = savedState,
    )
}

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}

private fun region(
    id: String,
    state: RegionState,
) = Region(id, name = 0, state, downloadSize = 1)
