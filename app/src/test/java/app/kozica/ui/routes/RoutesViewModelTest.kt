package app.kozica.ui.routes

import app.kozica.data.SavedRoute
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteTotals
import app.kozica.data.routes.TrailNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val network =
        TrailNetwork.read(RoutesViewModelTest::class.java.getResource("/trails-fixture.network")!!.readBytes())

    private val across = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.02))
    private val nowhere = listOf(RoutePoint(52.23, 21.01), RoutePoint(52.24, 21.02))

    @Test
    fun `saved Routes by name, each planned for its length and time, or left without where it cannot be`() =
        runTest {
            val asked = MutableStateFlow<String?>(null)
            val viewModel =
                RoutesViewModel(
                    saved = flowOf(listOf(SavedRoute("z", "Żleb", nowhere), SavedRoute("a", "Ascent", across))),
                    totals = RouteTotals { RoutePlanner(network).plan(it) },
                    renameRoute = { _, _ -> },
                    deleteRoute = {},
                    routeToShow = asked,
                    compute = Dispatchers.Main,
                )

            val rows = planned(viewModel)

            assertEquals(listOf("Ascent", "Żleb"), rows.map { it.route.name })
            assertEquals(RoutePlanner(network).plan(across).minutes, rows[0].minutes)
            assertNull("a Route with no way over the Trails has no totals", rows[1].minutes)

            viewModel.show("a")
            assertEquals("a", asked.value)
        }

    @Test
    fun `a Route planned on one visit to the tab is not planned again on the next`() =
        runTest {
            val asked = mutableListOf<List<RoutePoint>>()
            val totals =
                RouteTotals {
                    asked += it
                    RoutePlanner(network).plan(it)
                }
            val saved = flowOf(listOf(SavedRoute("a", "Ascent", across)))

            repeat(2) {
                val visit = RoutesViewModel(saved, totals, { _, _ -> }, {}, MutableStateFlow(null), Dispatchers.Main)
                val row = planned(visit).single()
                assertEquals(RoutePlanner(network).plan(across).minutes, row.minutes)
            }

            assertEquals(listOf(across), asked)
        }

    private suspend fun planned(viewModel: RoutesViewModel): List<RouteRow> =
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(5.seconds) {
                viewModel.state.first { state -> state.routes?.firstOrNull()?.minutes != null }
            }
        }!!.routes!!
}
