package app.kozica.data.hikes

import app.kozica.data.routes.TrailNetwork
import app.kozica.data.routes.part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToLong

class PaceTest {
    private fun hike(
        ratio: Double,
        metres: Double = 10_000.0,
        movingSeconds: Long = 4 * 3600,
    ) = PaceSample(metres, movingSeconds, movingSeconds / ratio)

    @Test
    fun `a median faster than three quarters of the signposts shows three quarters`() {
        val pace = paceOf(listOf(hike(0.6), hike(0.65), hike(0.7)))!!

        assertEquals(PACE_FLOOR, pace.factor, 0.0)
        assertTrue(pace.floored)
        assertTrue(pace.minutes(4 * 3600.0) >= 180)
    }

    @Test
    fun `a ratio under one half is not a walk, so it neither counts nor pulls the median down`() {
        val pace = paceOf(listOf(hike(0.3), hike(0.9), hike(1.0), hike(1.1)))!!

        assertEquals(1.0, pace.factor, 1e-9)
        assertEquals(3, pace.hikes)
    }

    @Test
    fun `fewer than three counting Hikes give no pace`() {
        assertNull(paceOf(listOf(hike(0.9), hike(1.0))))
        assertNull(paceOf(listOf(hike(0.9), hike(1.0), hike(1.1, metres = 2_000.0))))
        assertNull(paceOf(listOf(hike(0.9), hike(1.0), hike(1.1, movingSeconds = 3_000))))
        assertNull(paceOf(listOf(hike(0.9), hike(1.0), PaceSample(10_000.0, 4 * 3600, ruleSeconds = null))))
    }

    @Test
    fun `a car ride and a short stroll leave the pace where it was`() {
        val walks = listOf(hike(0.9), hike(1.0), hike(1.1))
        val withOthers = listOf(hike(0.2), hike(1.9, metres = 800.0)) + walks

        assertEquals(paceOf(walks), paceOf(withOthers))
    }

    @Test
    fun `only the ten newest counting Hikes are read`() {
        val newest = List(10) { hike(1.2) }
        val older = List(10) { hike(0.8) }

        assertEquals(1.2, paceOf(newest + older)!!.factor, 1e-9)
    }

    @Test
    fun `a slower pace lengthens a time, rounded up as a signpost is`() {
        val pace = Pace(1.3, hikes = 4, floored = false)

        assertEquals(65, pace.minutes(50 * 60.0))
        assertEquals(50, null.minutes(50 * 60.0))
    }

    private val network = TrailNetwork.read(PaceTest::class.java.getResource("/trails-fixture.network")!!.readBytes())
    private val climbing = (0 until network.edgeLength.size).maxBy { network.edgeAscent[it] + network.edgeDescent[it] }

    private fun ruleSecondsAlong(points: List<Int>): Double {
        val track =
            listOf(
                points.map { TrackPoint(0, network.pointLat[it] * 1e-7, network.pointLon[it] * 1e-7, altitude = null) },
            )
        return track.ruleSeconds(points.map { Height(network.pointHeight[it] / 10.0, HeightSource.GRID) })!!
    }

    private val climbingPoints: List<Int>
        get() = network.edgeFirstPoint[climbing].let { (it until it + network.edgePointCount[climbing]).toList() }

    @Test
    fun `a Track is timed by the router's own rule`() {
        val ours = ruleSecondsAlong(climbingPoints)
        val router = network.part(climbing, 0.0, 1.0).seconds

        assertTrue(network.edgeAscent[climbing] + network.edgeDescent[climbing] > 50)
        assertEquals((router / 60).roundToLong(), (ours / 60).roundToLong())
    }

    @Test
    fun `a Track walked down the edge is timed by the router's steep-descent rule`() {
        val ours = ruleSecondsAlong(climbingPoints.reversed())
        val router = network.part(climbing, 1.0, 0.0).seconds

        assertTrue(network.edgeAscent[climbing] > 50)
        assertEquals((router / 60).roundToLong(), (ours / 60).roundToLong())
    }

    @Test
    fun `heights wobbling under five metres climb nothing until they move five`() {
        val degreesPerHundredMetres = 100 / (6_371_008.8 * PI / 180)
        val metres = listOf(1000, 1003, 998, 1002, 995, 998, 992, 997, 1001, 1004, 1003)
        val track =
            listOf(metres.indices.map { TrackPoint(0, 49.0 + it * degreesPerHundredMetres, 20.0, altitude = null) })
        val heights = metres.map { Height(it.toDouble(), HeightSource.GRID) }

        assertEquals(1000.0, track.along().last(), 1e-6)
        assertEquals(900.0 + 8 * 6.0, track.ruleSeconds(heights)!!, 1e-6)
    }

    @Test
    fun `a Track with a GPS height is not timed`() {
        val track = listOf(listOf(TrackPoint(0, 49.0, 20.0, 1000.0), TrackPoint(1, 49.001, 20.0, 1010.0)))
        val heights = listOf(Height(1000.0, HeightSource.GRID), Height(1010.0, HeightSource.GPS))

        assertNull(track.ruleSeconds(heights))
    }
}
