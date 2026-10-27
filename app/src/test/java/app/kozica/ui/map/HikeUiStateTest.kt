package app.kozica.ui.map

import app.kozica.data.hikes.Climb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class HikeUiStateTest {
    private val along = listOf(0.0, 1_500.0, 3_000.0, 4_500.0, 6_000.0001)

    private fun keeping(keep: ClosedFloatingPointRange<Double>) =
        HikeUiState(
            id = "hike",
            name = "",
            startedAt = Instant.EPOCH,
            endedAt = Instant.EPOCH,
            metres = along.last(),
            movingSeconds = 0,
            track = emptyList(),
            along = along,
            climb = Climb(0.0, 0.0, fromGps = false),
            keep = keep,
        )

    private fun slider(metres: Double) = metres.toFloat().toDouble()

    @Test
    fun `a handle left at the end keeps the last point though the slider's float rounds it short`() {
        val end = slider(along.last())
        check(end < along.last())

        val state = keeping(slider(1_600.0)..end)

        assertEquals(2..along.lastIndex, state.kept)
    }

    @Test
    fun `both handles left at the ends keep everything and cut nothing`() {
        val state = keeping(slider(0.0)..slider(along.last()))

        assertEquals(along.indices, state.kept)
        assertFalse(state.cuts)
    }

    @Test
    fun `a handle moved off an end still cuts at the point before it`() {
        assertEquals(0..2, keeping(0.0..slider(4_000.0)).kept)
    }
}
