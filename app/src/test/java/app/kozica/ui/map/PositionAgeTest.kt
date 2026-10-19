package app.kozica.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class PositionAgeTest {
    private val now = 5_000.seconds.inWholeNanoseconds

    @Test
    fun `a precise fix is fresh until 30 s and stale from 30 s`() {
        assertEquals(PositionAge.Fresh, positionAge(now - (30.seconds - 1.nanoseconds).inWholeNanoseconds, now, false))
        assertEquals(PositionAge.Stale(30.seconds), positionAge(now - 30.seconds.inWholeNanoseconds, now, false))
    }

    @Test
    fun `an approximate fix is fresh until 15 min and stale from 15 min`() {
        assertEquals(PositionAge.Fresh, positionAge(now - (15.minutes - 1.nanoseconds).inWholeNanoseconds, now, true))
        assertEquals(PositionAge.Stale(15.minutes), positionAge(now - 15.minutes.inWholeNanoseconds, now, true))
    }

    @Test
    fun `the age shown is whole seconds under a minute and whole minutes across the hour`() {
        assertEquals(
            PositionAge.Stale(45.seconds),
            positionAge(now - 45_700.milliseconds.inWholeNanoseconds, now, false),
        )
        assertEquals(
            PositionAge.Stale(59.minutes),
            positionAge(now - (59.minutes + 59.seconds).inWholeNanoseconds, now, false),
        )
        assertEquals(
            PositionAge.Stale(65.minutes),
            positionAge(
                now - (1.hours + 5.minutes + 59.seconds).inWholeNanoseconds,
                now,
                true,
            ),
        )
    }

    @Test
    fun `a fix with no stamp is stale with no age, even seconds after boot`() {
        val justBooted = 10.seconds.inWholeNanoseconds
        assertEquals(PositionAge.Stale(null), positionAge(0, justBooted, false))
        assertEquals(PositionAge.Stale(null), positionAge(0, justBooted, true))
    }

    @Test
    fun `a fix stamped after now is stale with no age`() {
        assertEquals(PositionAge.Stale(null), positionAge(now + 1, now, false))
    }
}
