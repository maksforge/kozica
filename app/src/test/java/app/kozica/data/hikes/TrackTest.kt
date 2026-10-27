package app.kozica.data.hikes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTest {
    private val start = 1_761_480_000_000L

    /** Kuźnice towards Kasprowy, a fix every 5 s, a pause, then on again with a fix that had no height. */
    private val track: Track =
        listOf(
            List(500) { i ->
                TrackPoint(start + i * 5_000L, 49.2702 - i * 0.00004, 19.9808 + i * 0.00003, 1012.4 + i * 0.7)
            },
            listOf(
                TrackPoint(start + 3_000_000, 49.2321, 20.0047, null),
                TrackPoint(start + 3_005_000, 49.23205, 20.00475, 1890.0),
                TrackPoint(start + 3_010_000, -0.0000001, -179.9999999, -12.3),
            ),
        )

    @Test
    fun `a Track comes back from its encoding with every point, stretch and break`() {
        val decoded = decodeTrack(encodeTrack(track))
        assertEquals(track.map { it.size }, decoded.map { it.size })
        for ((stretch, back) in track.zip(decoded)) {
            for ((a, b) in stretch.zip(back)) {
                assertEquals(a.timeMillis, b.timeMillis)
                assertEquals(a.latitude, b.latitude, 1e-7)
                assertEquals(a.longitude, b.longitude, 1e-7)
                if (a.altitude == null) assertNull(b.altitude) else assertEquals(a.altitude!!, b.altitude!!, 0.05)
            }
        }
    }

    @Test
    fun `an empty Track and an empty stretch survive too`() {
        assertEquals(emptyList<List<TrackPoint>>(), decodeTrack(encodeTrack(emptyList())))
        assertEquals(listOf(emptyList<TrackPoint>()), decodeTrack(encodeTrack(listOf(emptyList()))))
    }

    @Test
    fun `a day's Track stays small`() {
        val bytes = encodeTrack(track).size
        assertTrue("$bytes bytes for 503 points", bytes < 503 * 10)
    }

    @Test
    fun `distance never joins two stretches across a pause`() {
        val apart =
            listOf(
                listOf(TrackPoint(0, 49.0, 20.0, null), TrackPoint(5_000, 49.0001, 20.0, null)),
                listOf(TrackPoint(600_000, 49.1, 20.0, null), TrackPoint(605_000, 49.1001, 20.0, null)),
            )
        assertEquals(2 * 11.1, apart.metres(), 0.2)
    }

    @Test
    fun `moving time leaves out a rest, which shows as one slow step`() {
        val walk =
            listOf(
                listOf(
                    TrackPoint(0, 49.0, 20.0, null),
                    TrackPoint(5_000, 49.00005, 20.0, null),
                    // Twenty minutes later, six metres on: a rest the 5 m filter held back.
                    TrackPoint(1_205_000, 49.0001, 20.0, null),
                    TrackPoint(1_210_000, 49.00015, 20.0, null),
                ),
            )
        assertEquals(10, walk.movingSeconds())
    }

    @Test
    fun `a trim keeps the points between its two, in the stretches they were in, and drops a stretch left empty`() {
        val three =
            listOf(
                List(3) { TrackPoint(it * 5_000L, 49.0 + it * 0.0001, 20.0, null) },
                List(2) { TrackPoint(600_000 + it * 5_000L, 49.1 + it * 0.0001, 20.0, null) },
                List(3) { TrackPoint(1_200_000 + it * 5_000L, 49.2 + it * 0.0001, 20.0, null) },
            )

        val kept = three.trim(1, 5)

        assertEquals(listOf(three[0].drop(1), three[1], three[2].take(1)), kept)
        assertEquals(listOf(three[2].take(2)), three.trim(5, 6))
    }

    @Test
    fun `along adds up each stretch, standing still across a pause`() {
        val apart =
            listOf(
                listOf(TrackPoint(0, 49.0, 20.0, null), TrackPoint(5_000, 49.0001, 20.0, null)),
                listOf(TrackPoint(600_000, 49.1, 20.0, null), TrackPoint(605_000, 49.1001, 20.0, null)),
            )

        val along = apart.along()

        assertEquals(4, along.size)
        assertEquals(along[1], along[2], 0.0)
        assertEquals(apart.metres(), along.last(), 1e-9)
    }

    private val walk: Track =
        listOf(
            listOf(TrackPoint(0, 49.0, 20.0, null), TrackPoint(10_000, 49.001, 20.002, null)),
            listOf(TrackPoint(600_000, 49.1, 20.1, null), TrackPoint(605_000, 49.1001, 20.1, null)),
        )

    @Test
    fun `a photo taken inside a stretch is pinned where the Track was at that moment`() {
        assertEquals(Pin(49.00025, 20.0005), walk.pinAt(2_500))
        assertEquals(Pin(49.001, 20.002), walk.pinAt(10_000))
        assertEquals(Pin(49.1, 20.1), walk.pinAt(600_000))
    }

    @Test
    fun `a photo taken in a pause is pinned at the last point before it, not across the break`() {
        assertEquals(Pin(49.001, 20.002), walk.pinAt(300_000))
        assertEquals(Pin(49.001, 20.002), walk.pinAt(599_999))
    }

    @Test
    fun `a photo taken before the Track or after it has no pin`() {
        assertNull(walk.pinAt(-1))
        assertNull(walk.pinAt(605_001))
        assertNull(emptyList<List<TrackPoint>>().pinAt(0))
    }

    @Test
    fun `the camera pins at the last fix when its button was pressed`() {
        assertEquals(Pin(49.0, 20.0), walk.lastFixAt(9_999))
        assertEquals(Pin(49.001, 20.002), walk.lastFixAt(300_000))
        assertEquals(Pin(49.1001, 20.1), walk.lastFixAt(700_000))
        assertNull(walk.lastFixAt(-1))
    }
}
