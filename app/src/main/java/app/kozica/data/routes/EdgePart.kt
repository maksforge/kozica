package app.kozica.data.routes

import kotlin.math.abs

/** What walking an edge from fraction `from` of its length to `to` takes, by docs/phase-5.md § 3's rule. */
data class EdgePart(
    val seconds: Double,
    val ascent: Double,
    val descent: Double,
)

/**
 * The part's share of the edge's stored ascent, descent and steep descent is its share of the edge's raw
 * rise or fall over the point heights, so a whole edge gets exactly what the build stored.
 */
fun TrailNetwork.part(
    edge: Int,
    from: Double,
    to: Double,
): EdgePart {
    val forward = to >= from
    val climbed = (if (forward) edgeAscent else edgeDescent)[edge].toDouble()
    val dropped = (if (forward) edgeDescent else edgeAscent)[edge].toDouble()
    val stored = (if (forward) edgeSecondsForward else edgeSecondsBackward)[edge].toDouble()
    if (minOf(from, to) == 0.0 && maxOf(from, to) == 1.0) return EdgePart(stored, climbed, dropped)
    val flatSeconds = edgeLength[edge] / Router.FLAT
    val steepDropped =
        ((stored - flatSeconds - climbed * SECONDS_PER_METRE_UP) / SECONDS_PER_METRE_STEEP_DOWN).coerceAtLeast(0.0)
    val whole = riseAndFall(edge, 0.0, 1.0)
    val walked = riseAndFall(edge, minOf(from, to), maxOf(from, to))
    val (wholeUp, wholeDown) = if (forward) whole else whole.second to whole.first
    val (walkedUp, walkedDown) = if (forward) walked else walked.second to walked.first

    fun share(
        total: Double,
        part: Double,
        of: Double,
    ) = if (of == 0.0) 0.0 else total * part / of

    val ascent = share(climbed, walkedUp, wholeUp)
    val steepDescent = share(steepDropped, walkedDown, wholeDown)
    val seconds =
        abs(to - from) * flatSeconds + ascent * SECONDS_PER_METRE_UP + steepDescent * SECONDS_PER_METRE_STEEP_DOWN
    return EdgePart(seconds, ascent, share(dropped, walkedDown, wholeDown))
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

/** The raw rise and fall over the edge's point heights from fraction [low] to [high], walked forward. */
private fun TrailNetwork.riseAndFall(
    edge: Int,
    low: Double,
    high: Double,
): Pair<Double, Double> {
    val first = edgeFirstPoint[edge]
    val along = along(edge)
    val total = along.last()
    val inside = along.indices.filter { along[it] > low * total && along[it] < high * total }
    val heights =
        listOf(heightAt(first, along, low * total)) +
            inside.map { pointHeight[first + it].toDouble() } +
            listOf(heightAt(first, along, high * total))
    var rise = 0.0
    var fall = 0.0
    for ((a, b) in heights.zipWithNext()) if (b > a) rise += b - a else fall += a - b
    return rise to fall
}

private fun TrailNetwork.heightAt(
    first: Int,
    along: DoubleArray,
    metres: Double,
): Double {
    val k = (1 until along.size).firstOrNull { along[it] >= metres } ?: (along.size - 1)
    val span = along[k] - along[k - 1]
    val t = if (span == 0.0) 0.0 else ((metres - along[k - 1]) / span).coerceIn(0.0, 1.0)
    return pointHeight[first + k - 1] + (pointHeight[first + k] - pointHeight[first + k - 1]) * t
}

private const val SECONDS_PER_METRE_UP = 600.0 / 100
private const val SECONDS_PER_METRE_STEEP_DOWN = 300.0 / 100
