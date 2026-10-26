package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-5.md § Tests: a Route's totals over the fixture `TrailNetworkTest` reads. */
class RoutePlannerTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val planner = RoutePlanner(network)

    private val a = RoutePoint(49.0, 20.0)
    private val b = RoutePoint(49.0, 20.01)
    private val c = RoutePoint(49.0, 20.02)
    private val e = RoutePoint(49.2, 20.2)
    private val f = RoutePoint(49.2, 20.21)

    @Test
    fun `a signpost's rounding, up to 5 minutes`() {
        assertEquals(0, roundedUpToFiveMinutes(0.0))
        assertEquals(5, roundedUpToFiveMinutes(1.0))
        assertEquals(5, roundedUpToFiveMinutes(300.0))
        assertEquals(10, roundedUpToFiveMinutes(301.0))
    }

    @Test
    fun `the total is the sum rounded, not the rounded parts summed`() {
        val route = planner.plan(listOf(a, b, c))
        val leg = route.legs[0]!!.seconds
        assertEquals(15, route.minutesTo(1))
        assertEquals(roundedUpToFiveMinutes(2 * leg), route.minutes)
        assertTrue(route.minutes < 2 * route.minutesTo(1))
    }

    @Test
    fun `against the one-way Trail the Route climbs over D and names the demanding stretch`() {
        val route = planner.plan(listOf(c, a))
        assertEquals(200.0, route.ascent, 1.0)
        assertEquals(200.0, route.descent, 1.0)
        assertEquals(route.metres, network.edgeLength[2] + network.edgeLength[3].toDouble(), 5.0)
        val stretch = route.demanding.single()
        assertEquals(3, stretch.sac)
        assertEquals(network.edgeLength[3].toDouble(), stretch.fromMetres, 5.0)
        assertEquals(route.metres, stretch.toMetres, 1.0)
        assertEquals(700.0, route.line.maxOf { it.heightMetres }, 0.1)
    }

    @Test
    fun `a leg from partway along an edge climbs only its part`() {
        val route = planner.plan(listOf(RoutePoint(49.005, 20.005), a))
        assertEquals(0.0, route.ascent, 0.1)
        assertEquals(100.0, route.descent, 10.0)
        assertEquals(600.0, route.line.first().heightMetres, 1.0)
        assertEquals(500.0, route.line.last().heightMetres, 0.1)
    }

    @Test
    fun `a leg with no way leaves a gap the length does not cross`() {
        val route = planner.plan(listOf(a, b, e, f))
        assertFalse(route.complete)
        assertNull(route.legs[1])
        val across = route.line.single { it.approach == Approach.NO_WAY }
        val before = route.line[route.line.indexOf(across) - 1]
        assertEquals(before.metres, across.metres, 0.0)
        assertEquals(network.edgeLength[0] + network.edgeLength[4].toDouble(), route.metres, 5.0)
    }
}
