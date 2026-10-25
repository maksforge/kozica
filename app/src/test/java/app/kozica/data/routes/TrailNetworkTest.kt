package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/phase-5.md § Tests: the reader against the fixture `scripts/test_build_network.py` writes. */
class TrailNetworkTest {
    private val network = TrailNetwork.read(javaClass.getResource("/trails-fixture.network")!!.readBytes())
    private val router = Router(network)

    private fun ways(leg: Leg) = leg.steps.map { network.edgeWay[it.edge] }

    @Test
    fun `reads every array the writer wrote`() {
        assertEquals(6, network.nodeCount)
        assertEquals(5, network.edgeCount)
        assertEquals(listOf(100L, 101L, 102L, 103L, 104L), network.edgeWay.toList())
        assertEquals(TrailNetwork.FORWARD_ONLY, network.edgeOneway[1].toInt())
        assertEquals(3, network.edgeSac[2].toInt())
        assertEquals(490_000_000, network.nodeLat[0])
        assertEquals(7000.toShort(), network.nodeHeight[3])
        assertEquals(3, network.edgePointCount[2])
        assertTrue(network.edgeAscent[2] > 150)
    }

    @Test
    fun `the flat way is the shortest`() {
        val leg = router.leg(49.0, 20.0, 49.0, 20.02)!!
        assertEquals(listOf(100L, 101L), ways(leg))
        assertEquals((network.edgeLength[0] + network.edgeLength[1]) / (4000.0 / 3600), leg.seconds, 1.0)
    }

    @Test
    fun `a one-way Trail is never walked against its direction`() {
        val leg = router.leg(49.0, 20.02, 49.0, 20.0)!!
        assertFalse(101L in ways(leg))
        assertEquals(listOf(103L, 102L), ways(leg))
    }

    @Test
    fun `no way to an island says so`() {
        assertNull(router.leg(49.0, 20.0, 49.2, 20.205))
    }
}
