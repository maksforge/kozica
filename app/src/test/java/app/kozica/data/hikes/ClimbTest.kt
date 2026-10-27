package app.kozica.data.hikes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbTest {
    private fun grid(vararg metres: Double) = metres.map { Height(it, HeightSource.GRID) }

    private fun gps(vararg metres: Double) = metres.map { Height(it, HeightSource.GPS) }

    @Test
    fun `the grid's wobble inside the 5 m dead band adds no climb, as a Route's does not`() {
        val sawtooth = grid(*DoubleArray(201) { 1_000.0 + if (it % 2 == 0) 2.0 else -2.0 })

        val climb = climb(sawtooth)

        assertEquals(0.0, climb.ascent, 1e-9)
        assertEquals(0.0, climb.descent, 1e-9)
        assertFalse(climb.fromGps)
    }

    @Test
    fun `a steady climb on the grid counts every metre, the last few inside the band included`() {
        val climb = climb(grid(*DoubleArray(101) { 1_000.0 + it * 1.03 }))

        assertEquals(103.0, climb.ascent, 1e-9)
        assertEquals(0.0, climb.descent, 1e-9)
    }

    @Test
    fun `GPS jitter under 10 m adds nothing, and a real climb through it counts`() {
        val jitter = gps(*DoubleArray(200) { 600.0 + if (it % 2 == 0) 4.5 else -4.5 })
        assertEquals(0.0, climb(jitter).ascent, 1e-9)

        val climbing = gps(600.0, 607.0, 603.0, 612.0, 608.0, 621.0, 640.0)
        val climb = climb(climbing)
        assertEquals(40.0, climb.ascent, 1e-9)
        assertEquals(0.0, climb.descent, 1e-9)
        assertTrue(climb.fromGps)
    }

    @Test
    fun `GPS counts each leg from the turn, where the grid's dead band counts from its last reference`() {
        val rolling = doubleArrayOf(600.0, 606.0, 612.0, 615.0, 609.0, 603.0, 609.0, 615.0, 618.0)

        val fromGps = climb(gps(*rolling))
        assertEquals(30.0, fromGps.ascent, 1e-9)
        assertEquals(12.0, fromGps.descent, 1e-9)

        val fromGrid = climb(grid(*rolling))
        assertEquals(27.0, fromGrid.ascent, 1e-9)
        assertEquals(9.0, fromGrid.descent, 1e-9)
    }

    @Test
    fun `at the last GPS point the open leg and what lies past its turn count, as the grid's last point does`() {
        val climb = climb(gps(600.0, 615.0, 608.0))

        assertEquals(15.0, climb.ascent, 1e-9)
        assertEquals(7.0, climb.descent, 1e-9)
        assertEquals(6.0, climb(gps(600.0, 606.0)).ascent, 1e-9)
    }

    @Test
    fun `no climb is counted across a change of source, GPS being metres above the grid`() {
        val heights = grid(1_000.0, 1_050.0, 1_100.0) + gps(1_140.0, 1_141.0, 1_140.0) + grid(1_100.0, 1_120.0)

        val climb = climb(heights)

        assertEquals(120.0, climb.ascent, 1e-9)
        assertEquals(0.0, climb.descent, 1e-9)
        assertTrue(climb.fromGps)
    }

    @Test
    fun `a point with no height neither breaks a run nor counts`() {
        val climb = climb(listOf(Height(1_000.0, HeightSource.GRID), null, null, Height(1_030.0, HeightSource.GRID)))

        assertEquals(30.0, climb.ascent, 1e-9)
    }

    @Test
    fun `the profile runs along the Track and breaks at a pause, leaving out points with no height`() {
        val track: Track =
            listOf(
                listOf(point(0, 49.0000), point(5, 49.0009)),
                listOf(point(600, 49.0018), point(605, 49.0027)),
            )
        val heights = grid(1_000.0, 1_010.0) + listOf(null) + grid(1_030.0)

        val profile = profileOf(track, heights)

        assertEquals(listOf(1_000.0, 1_010.0, 1_030.0), profile.map { it.heightMetres })
        assertEquals(listOf(false, false, true), profile.map { it.breakBefore })
        assertEquals(track.along()[3], profile.last().metres, 1e-9)
    }

    private fun point(
        seconds: Long,
        latitude: Double,
    ) = TrackPoint(seconds * 1_000, latitude, 20.0, null)
}
