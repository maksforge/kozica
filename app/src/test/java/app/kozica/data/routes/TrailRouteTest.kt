package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/phase-6c.md § Tests, the via points, over the fixture `scripts/test_build_network.py` writes. */
class TrailRouteTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val router = Router(network)
    private val a = 49.0 to 20.0
    private val b = 49.0 to 20.01
    private val c = 49.0 to 20.02
    private val d = 49.01 to 20.01

    private fun step(
        edge: Int,
        reversed: Boolean = false,
        gapBefore: Boolean = false,
    ) = (edge shl 2) or (if (gapBefore) 2 else 0) or (if (reversed) 1 else 0)

    private fun along(vararg steps: Int) =
        network.pointsAlong(steps, router).map {
            Math.round(it.latitude * 1e4) / 1e4 to
                Math.round(it.longitude * 1e4) / 1e4
        }

    @Test
    fun `a Trail the router follows by itself needs only its ends`() {
        assertEquals(listOf(a, c), along(step(0), step(1)))
    }

    @Test
    fun `a via point holds the Route where another Trail is a shortcut`() {
        assertEquals(listOf(a, d, c), along(step(2), step(3)))
        val planned =
            RoutePlanner(
                network,
                router,
            ).plan(along(step(2), step(3)).map { RoutePoint(it.first, it.second) })
        assertEquals(network.edgeLength[2] + network.edgeLength[3].toDouble(), planned.metres, 1.0)
    }

    @Test
    fun `a gap gets a point on each side`() {
        assertEquals(listOf(d, a, b, c), along(step(2, reversed = true), step(1, gapBefore = true)))
    }
}
