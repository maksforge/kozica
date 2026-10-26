package app.kozica.data.routes

import kotlin.math.abs

// docs/phase-5.md § 3's rule; scripts/build-network.py's constants of the same names are the source.
internal const val DEAD_BAND_METRES = 5.0
internal const val FLAT_METRES_PER_SECOND = 4000.0 / 3600
internal const val SECONDS_PER_METRE_UP = 600.0 / 100
internal const val SECONDS_PER_METRE_STEEP_DOWN = 300.0 / 100
internal const val STEEP_GRADE = 0.10

/** What walking an edge from fraction `from` of its length to `to` takes, by docs/phase-5.md § 3's rule. */
data class EdgePart(
    val seconds: Double,
    val ascent: Double,
    val descent: Double,
)

/**
 * A whole edge gets exactly what the build stored; a part of one gets `climb()`'s rule run over the
 * heights it walks, in walking order, its cut ends interpolated.
 */
fun TrailNetwork.part(
    edge: Int,
    from: Double,
    to: Double,
): EdgePart {
    val forward = to >= from
    if (minOf(from, to) == 0.0 && maxOf(from, to) == 1.0) {
        return EdgePart(
            (if (forward) edgeSecondsForward else edgeSecondsBackward)[edge].toDouble(),
            (if (forward) edgeAscent else edgeDescent)[edge].toDouble(),
            (if (forward) edgeDescent else edgeAscent)[edge].toDouble(),
        )
    }
    val first = edgeFirstPoint[edge]
    val along = along(edge)
    val total = along.last()
    val (low, high) = minOf(from, to) * total to maxOf(from, to) * total
    val inside = along.indices.filter { along[it] > low && along[it] < high }
    val metres = listOf(from * total) + (if (forward) inside else inside.reversed()).map { along[it] } + (to * total)
    val heights = metres.map { heightAt(first, along, it) }
    var ascent = 0.0
    var descent = 0.0
    var steepDescent = 0.0
    var reference = heights.first()
    var run = 0.0
    for (i in 1 until heights.size) {
        run += abs(metres[i] - metres[i - 1])
        val change = heights[i] - reference
        if (abs(change) < DEAD_BAND_METRES && i < heights.size - 1) continue
        val steep = run > 0 && abs(change) / run > STEEP_GRADE
        if (change > 0) ascent += change else descent -= change
        if (change < 0 && steep) steepDescent -= change
        reference = heights[i]
        run = 0.0
    }
    val seconds =
        abs(to - from) * edgeLength[edge] / FLAT_METRES_PER_SECOND + ascent * SECONDS_PER_METRE_UP +
            steepDescent * SECONDS_PER_METRE_STEEP_DOWN
    return EdgePart(seconds, ascent, descent)
}

/** Metres from the edge's first point to each of its points. */
internal fun TrailNetwork.along(edge: Int): DoubleArray {
    val first = edgeFirstPoint[edge]
    val along = DoubleArray(edgePointCount[edge])
    for (k in 1 until along.size) {
        along[k] = along[k - 1] +
            Router.metres(
                pointLat[first + k - 1] * 1e-7,
                pointLon[first + k - 1] * 1e-7,
                pointLat[first + k] * 1e-7,
                pointLon[first + k] * 1e-7,
            )
    }
    return along
}

/** In metres, interpolated between the edge's points. */
private fun TrailNetwork.heightAt(
    first: Int,
    along: DoubleArray,
    metres: Double,
): Double {
    val k = (1 until along.size).firstOrNull { along[it] >= metres } ?: (along.size - 1)
    val span = along[k] - along[k - 1]
    val t = if (span == 0.0) 0.0 else ((metres - along[k - 1]) / span).coerceIn(0.0, 1.0)
    return (pointHeight[first + k - 1] + (pointHeight[first + k] - pointHeight[first + k - 1]) * t) / 10.0
}
