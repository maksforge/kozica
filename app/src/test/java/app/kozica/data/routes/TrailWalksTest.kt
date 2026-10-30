package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-6c.md § Tests: the walks' reader against the fixture `scripts/test_build_network.py` writes. */
class TrailWalksTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val walks = TrailWalks.read(javaClass.getResource("/trails-fixture.edges")!!.readBytes())

    @Test
    fun `reads each Trail's steps in order`() {
        assertEquals(3, walks.trailCount)
        val steps = walks.steps(7)!!
        assertEquals(listOf(0, 1), steps.map(TrailWalks::edge))
        assertFalse(steps.any(TrailWalks::reversed))
        assertTrue(TrailWalks.reversed(walks.steps(9)!!.single()))
        assertNull(walks.steps(10))
    }

    @Test
    fun `a reversed edge is walked from its last point`() {
        val line = network.walkLine(walks.steps(9)!!)
        assertEquals(49.2, line.first().latitude, 1e-9)
        assertEquals(20.21, line.first().longitude, 1e-9)
        assertEquals(listOf(940.0, 1000.0, 900.0), line.map { it.heightMetres })
        assertEquals(network.edgeLength[4].toDouble(), line.last().metres, 1.0)
    }

    @Test
    fun `a gap breaks the line and adds no metres`() {
        val line = network.walkLine(walks.steps(8)!!)
        val gap = line.indexOfFirst { it.breakBefore }
        assertEquals(3, gap)
        assertEquals(line[gap - 1].metres, line[gap].metres, 1e-9)
        assertEquals(listOf(700.0, 600.0, 500.0), line.take(3).map { it.heightMetres })
        assertEquals(1, line.count { it.breakBefore })
        assertEquals(network.edgeLength[2] + network.edgeLength[1].toDouble(), line.last().metres, 1.0)
    }
}
