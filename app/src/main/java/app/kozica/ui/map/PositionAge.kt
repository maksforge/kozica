package app.kozica.ui.map

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

sealed interface PositionAge {
    data object Fresh : PositionAge

    /** @param age null when the fix's stamp cannot be trusted, so its age is unknown. */
    data class Stale(
        val age: Duration?,
    ) : PositionAge
}

/**
 * @param fixNanos the fix's `elapsedRealtimeNanos`; `Location` returns 0 when a provider never set it.
 * @param nowNanos `SystemClock.elapsedRealtimeNanos()`.
 */
fun positionAge(
    fixNanos: Long,
    nowNanos: Long,
    approximateOnly: Boolean,
): PositionAge {
    if (fixNanos <= 0 || fixNanos > nowNanos) return PositionAge.Stale(null)
    val age = (nowNanos - fixNanos).nanoseconds
    val threshold = if (approximateOnly) APPROXIMATE_STALE else PRECISE_STALE
    return if (age < threshold) PositionAge.Fresh else PositionAge.Stale(age.shown())
}

private fun Duration.shown(): Duration = if (this < 1.minutes) inWholeSeconds.seconds else inWholeMinutes.minutes

private val PRECISE_STALE = 30.seconds

// Android throttles a coarse-only client to fixes minutes apart (docs/phase-2.md § 4, R7).
private val APPROXIMATE_STALE = 15.minutes
