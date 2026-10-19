package app.kozica.ui.map

import android.location.Location
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.kozica.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
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
}

private object NoPreferences : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(emptyPreferences())
}
