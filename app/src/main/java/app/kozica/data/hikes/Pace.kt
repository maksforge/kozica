package app.kozica.data.hikes

import app.kozica.data.routes.roundedUpToFiveMinutes
import app.kozica.data.routes.ruleSeconds
import app.kozica.data.routes.walk

/** docs/phase-6b.md § 2. */
internal const val PACE_FLOOR = 0.75
internal const val PACE_HIKES_NEEDED = 3
internal const val PACE_HIKES_READ = 10
private const val COUNTING_MIN_METRES = 3000.0
private const val COUNTING_MIN_MOVING_SECONDS = 3600L
private val COUNTING_RATIOS = 0.5..2.0

/** The user's walking time against the signposts' rule, from [hikes] counting Hikes; [floored] where their median was faster than [PACE_FLOOR]. */
data class Pace(
    val factor: Double,
    val hikes: Int,
    val floored: Boolean,
)

/** One recorded Hike as the Pace reads it; [ruleSeconds] null where its Track cannot be timed by the rule. */
data class PaceSample(
    val metres: Double,
    val movingSeconds: Long,
    val ruleSeconds: Double?,
) {
    val ratio: Double? get() = ruleSeconds?.takeIf { it > 0 }?.let { movingSeconds / it }

    val counts: Boolean
        get() =
            metres >= COUNTING_MIN_METRES &&
                movingSeconds >= COUNTING_MIN_MOVING_SECONDS &&
                ratio?.let { it in COUNTING_RATIOS } == true

    /** Whether reading the Track's heights could still make this Hike count. */
    val mayCount: Boolean get() = metres >= COUNTING_MIN_METRES && movingSeconds >= COUNTING_MIN_MOVING_SECONDS
}

/** [samples] newest first: the median ratio of the [PACE_HIKES_READ] newest that count, once [PACE_HIKES_NEEDED] do, never under [PACE_FLOOR]. */
fun paceOf(samples: List<PaceSample>): Pace? {
    val ratios =
        samples
            .filter { it.counts }
            .take(PACE_HIKES_READ)
            .mapNotNull { it.ratio }
            .sorted()
    if (ratios.size < PACE_HIKES_NEEDED) return null
    val middle = ratios.size / 2
    val median = if (ratios.size % 2 == 1) ratios[middle] else (ratios[middle - 1] + ratios[middle]) / 2
    return Pace(median.coerceAtLeast(PACE_FLOOR), ratios.size, floored = median < PACE_FLOOR)
}

/**
 * The rule's walking time for this Track itself, each stretch walked alone and the pauses between
 * them not timed; null unless every point's height came from the relief, since the steep-descent
 * grade cannot be read from GPS heights.
 */
fun Track.ruleSeconds(heights: List<Height?>): Double? {
    if (heights.size != sumOf { it.size }) return null
    val grid = heights.map { height -> height?.takeIf { it.source == HeightSource.GRID }?.metres ?: return null }
    val along = along()
    var at = 0
    var seconds = 0.0
    for (stretch in this.filter { it.isNotEmpty() }) {
        val metres = along.subList(at, at + stretch.size)
        val walked = walk(metres, grid.subList(at, at + stretch.size))
        at += stretch.size
        seconds += ruleSeconds(metres.last() - metres.first(), walked.ascent, walked.steepDescent)
    }
    return seconds
}

/** A walking time as the app shows it: the rule's [seconds] at the user's [Pace], or the signposts' without one. */
fun Pace?.minutes(seconds: Double): Int = roundedUpToFiveMinutes(seconds * (this?.factor ?: 1.0))
