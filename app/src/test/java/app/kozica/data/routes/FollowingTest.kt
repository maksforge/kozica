package app.kozica.data.routes

import app.kozica.data.hikes.Pace
import app.kozica.data.hikes.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FollowingTest {
    private val degreesPerMetre = 1 / (6_371_008.8 * PI / 180)

    /** Straight north from (49, 20) by [metres] along the way, and back down where the line turns at [turn]. */
    private fun at(
        metres: Double,
        turn: Double = Double.MAX_VALUE,
    ) = 49.0 + (if (metres <= turn) metres else 2 * turn - metres) * degreesPerMetre

    private val snap = Snap(0, 0.0, 49.0, 20.0, 0.0)

    private fun leg(seconds: Double) = Leg(snap, snap, emptyList(), seconds, 0)

    /** Up a 1 km valley and back down it, the turn being a via point; 600 m climbed and descended at a steady grade. */
    private val outAndBackRoute =
        PlannedRoute(
            legs = listOf(leg(1800.0), leg(1500.0)),
            line =
                (0..20).map { i ->
                    val metres = i * 100.0
                    val height = 1000.0 + 600 * (if (metres <= 1000) metres else 2000 - metres) / 1000
                    RouteSample(at(metres, turn = 1000.0), 20.0, metres, height, Approach.TRAIL)
                },
            ascent = 600.0,
            descent = 600.0,
            seconds = 3300.0,
            secondsToPoint = listOf(0.0, 1800.0, 3300.0),
            demanding = emptyList(),
            metresToPoint = listOf(0.0, 1000.0, 2000.0),
        )

    private val outAndBack = FollowedLine(outAndBackRoute)

    private fun fix(
        alongTheWay: Double,
        atMillis: Long,
        turn: Double = 1000.0,
        accuracy: Double = 10.0,
        age: Long = 0,
        east: Double = 0.0,
    ) = FollowFix(at(alongTheWay, turn), 20.0 + east * degreesPerMetre / 0.656, accuracy, age, atMillis)

    @Test
    fun `on the way up a valley Progress is on the way up, not the way back down`() {
        val progress = outAndBack.follow(Progress(200.0, 0), fix(300.0, 30_000))

        assertEquals(300.0, progress!!.metres, 1.0)
    }

    @Test
    fun `past the turn the same place along the valley is the way down`() {
        val atTurn = Progress(1000.0, 0)

        val progress = outAndBack.follow(atTurn, fix(1300.0, 180_000))

        assertEquals(1300.0, progress!!.metres, 1.0)
    }

    @Test
    fun `walking back the way they came leaves Progress where it was`() {
        val progress = outAndBack.follow(Progress(600.0, 0), fix(400.0, 60_000, turn = Double.MAX_VALUE))

        assertEquals(600.0, progress!!.metres, 0.0)
    }

    @Test
    fun `a stale, a vague or a distant fix moves nothing`() {
        val progress = Progress(200.0, 0)

        assertEquals(progress, outAndBack.follow(progress, fix(300.0, 30_000, age = 31_000)))
        assertEquals(progress, outAndBack.follow(progress, fix(300.0, 30_000, accuracy = 60.0)))
        assertEquals(progress, outAndBack.follow(progress, fix(300.0, 30_000, east = 60.0)))
    }

    @Test
    fun `the window grows with the time since the last fix`() {
        val start = Progress(0.0, 0)

        assertEquals(start, outAndBack.follow(start, fix(800.0, 10_000)))
        assertEquals(800.0, outAndBack.follow(start, fix(800.0, 600_000))!!.metres, 1.0)
    }

    @Test
    fun `find me searches the whole line ahead but still takes the first pass`() {
        val found = outAndBack.findMe(null, fix(800.0, 0))

        assertEquals(800.0, found!!.metres, 1.0)
    }

    @Test
    fun `from the start what is left is the sheet's own total, and at the turn exactly the legs'`() {
        val fromStart = outAndBack.left(Progress(0.0, 0))
        val fromTurn = outAndBack.left(Progress(1000.0, 0))

        assertEquals(3300.0, fromStart.toEnd.seconds, 1e-6)
        assertEquals(1800.0, fromStart.toNext.seconds, 1e-6)
        assertEquals(1, fromStart.nextPoint)
        assertEquals(1500.0, fromTurn.toEnd.seconds, 1e-6)
        assertEquals(2, fromTurn.nextPoint)
    }

    @Test
    fun `partway along the time left only falls, and at a pace it is rounded up`() {
        val lefts = (0..20).map { outAndBack.left(Progress(it * 100.0 + 37, 0)).toEnd.seconds }
        val pace = Pace(1.2, hikes = 3, floored = false)
        val partway = outAndBack.left(Progress(437.0, 0)).toEnd

        assertTrue(lefts.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(pace.minutes(partway.seconds) * 60 >= partway.seconds * 1.2)
        assertEquals(600.0 * (1000 - 437) / 1000, partway.ascent, 6.0)
    }

    @Test
    fun `from the start the climb left is the sheet's Ascent, though the line's own differs`() {
        val sheet = 610.0
        val line = FollowedLine(outAndBackRoute.copy(ascent = sheet))

        assertEquals(sheet, line.left(Progress(0.0, 0)).toEnd.ascent, 1e-9)
    }

    @Test
    fun `over bumps inside the dead band and up a climb neither the time nor the climb left rises`() {
        val bumpy =
            FollowedLine(
                PlannedRoute(
                    legs = listOf(leg(700.0), leg(600.0)),
                    line =
                        (0..40).map { i ->
                            val metres = i * 25.0
                            val height =
                                1000.0 + 0.6 * (metres - 500).coerceAtLeast(0.0) +
                                    if (i % 2 == 1 && i < 20) 4.9 else 0.0
                            RouteSample(at(metres), 20.0, metres, height, Approach.TRAIL)
                        },
                    ascent = 300.0,
                    descent = 0.0,
                    seconds = 1300.0,
                    secondsToPoint = listOf(0.0, 700.0, 1300.0),
                    demanding = emptyList(),
                    metresToPoint = listOf(0.0, 500.0, 1000.0),
                ),
            )

        val lefts = (0..1000).map { bumpy.left(Progress(it.toDouble(), 0)).toEnd }

        assertTrue(lefts.zipWithNext().all { (a, b) -> b.seconds <= a.seconds && b.ascent <= a.ascent })
        assertEquals(300.0, lefts.first().ascent, 1e-9)
        assertEquals(1300.0, lefts.first().seconds, 1e-9)
    }

    @Test
    fun `the end is reached at the last point, not on passing near it`() {
        val nearEnd = Progress(1990.0, 0)

        assertTrue(outAndBack.ended(nearEnd, fix(1995.0, 0)))
        assertFalse(outAndBack.ended(Progress(1000.0, 0), fix(1995.0, 0)))
    }
}
