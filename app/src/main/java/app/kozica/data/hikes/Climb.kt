package app.kozica.data.hikes

import app.kozica.data.routes.DEAD_BAND_METRES
import kotlin.math.abs

/** Where a point's height came from (docs/phase-6.md § 5). */
enum class HeightSource { GRID, GPS }

data class Height(
    val metres: Double,
    val source: HeightSource,
)

/** @param fromGps whether any height came from GPS, so the numbers are shown as "≈". */
data class Climb(
    val ascent: Double,
    val descent: Double,
    val fromGps: Boolean,
)

/** One point of a Hike's profile, [metres] along its Track; [breakBefore] where a pause or a break lies behind it. */
data class ProfilePoint(
    val metres: Double,
    val heightMetres: Double,
    val breakBefore: Boolean,
)

/** GPS height jitters by metres from one fix to the next; this much in one direction is a climb (§ 5). */
internal const val GPS_BAND_METRES = 10.0

/**
 * Ascent and descent of [heights], one per point in walking order and null where nothing gave one.
 * Each run of one source is summed alone, the grid's by phase 5's dead band and GPS's by its wider
 * band, and never across a change of source: GPS is above the ellipsoid, the grid above the sea.
 */
fun climb(heights: List<Height?>): Climb {
    var ascent = 0.0
    var descent = 0.0
    for (run in runsOf(heights)) {
        val band = if (run.first().source == HeightSource.GRID) DEAD_BAND_METRES else GPS_BAND_METRES
        val (up, down) = climb(run.map { it.metres }, band)
        ascent += up
        descent += down
    }
    return Climb(ascent, descent, fromGps = heights.any { it?.source == HeightSource.GPS })
}

/** What is left inside the band at the last point counts too, as `climb()` in `scripts/build-network.py` does. */
private fun climb(
    heights: List<Double>,
    band: Double,
): Pair<Double, Double> {
    var ascent = 0.0
    var descent = 0.0
    var reference = heights.first()
    for (i in 1 until heights.size) {
        val change = heights[i] - reference
        if (abs(change) < band && i < heights.size - 1) continue
        if (change > 0) ascent += change else descent -= change
        reference = heights[i]
    }
    return ascent to descent
}

private fun runsOf(heights: List<Height?>): List<List<Height>> {
    val runs = mutableListOf<MutableList<Height>>()
    for (height in heights.filterNotNull()) {
        val run = runs.lastOrNull()
        if (run != null && run.last().source == height.source) run += height else runs += mutableListOf(height)
    }
    return runs
}

/** Height against distance, [heights] being [track]'s points in order; a point with no height is left out. */
fun profileOf(
    track: Track,
    heights: List<Height?>,
): List<ProfilePoint> {
    val along = track.along()
    val starts = track.stretchStarts()
    val profile = mutableListOf<ProfilePoint>()
    var broken = false
    for (i in along.indices) {
        if (i in starts) broken = true
        val height = heights[i] ?: continue
        profile += ProfilePoint(along[i], height.metres, breakBefore = broken && profile.isNotEmpty())
        broken = false
    }
    return profile
}

/** Where each stretch begins among the Track's points in order. */
private fun Track.stretchStarts(): Set<Int> = runningFold(0) { at, stretch -> at + stretch.size }.dropLast(1).toSet()
