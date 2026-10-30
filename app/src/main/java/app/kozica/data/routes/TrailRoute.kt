package app.kozica.data.routes

/**
 * docs/phase-6c.md § 5: the points that hold an ordinary Route on a Trail's walk. The walk's two ends,
 * a point on each side of a gap, and between them a via point at the farthest junction to which a
 * leg, planned either way, keeps to the walk's own edges; found by doubling the span along the walk,
 * then bisecting back, since a leg that keeps to the Trail as far as one junction keeps to it as far
 * as every junction before.
 */
fun TrailNetwork.pointsAlong(
    steps: IntArray,
    router: Router,
): List<RoutePoint> {
    val own = steps.map(TrailWalks::edge).toSet()
    val stretches = mutableListOf<MutableList<Int>>()
    val stretchEdges = mutableListOf<MutableList<Int>>()
    for (step in steps) {
        val edge = TrailWalks.edge(step)
        val (from, to) =
            if (TrailWalks.reversed(step)) {
                edgeTo[edge] to edgeFrom[edge]
            } else {
                edgeFrom[edge] to
                    edgeTo[edge]
            }
        if (stretches.isEmpty() || TrailWalks.gapBefore(step)) {
            stretches += mutableListOf(from)
            stretchEdges += mutableListOf<Int>()
        }
        stretches.last() += to
        stretchEdges.last() += edge
    }
    val loop = stretches.size == 1 && stretches[0].first() == stretches[0].last()

    fun alongTrail(
        from: Int,
        to: Int,
    ): Leg? =
        router.leg(lat(from), lon(from), lat(to), lon(to))?.takeIf { leg ->
            leg.steps.all {
                it.edge in own ||
                    it.from == it.to
            }
        }

    // A one-way stretch is walked only its own way, so a leg over one is not asked to hold reversed.
    fun holds(
        from: Int,
        to: Int,
    ): Boolean {
        val leg = alongTrail(from, to) ?: return false
        return leg.steps.any { edgeOneway[it.edge].toInt() != 0 } || alongTrail(to, from) != null
    }

    val points = mutableListOf<RoutePoint>()
    for ((nodes, edges) in stretches.zip(stretchEdges)) {
        val last = nodes.lastIndex
        var at = 0
        // Neighbouring points are about as far apart, so each search starts at the span the last one found.
        var start = 1
        points += RoutePoint(lat(nodes[0]), lon(nodes[0]))
        while (at < last) {
            // A loop's first leg may not close it, or the Route would have nowhere to go.
            val reach = if (loop && at == 0) last - 1 else last
            var good = at
            var span = minOf(start, reach - at).coerceAtLeast(1)
            while (at + span <= reach && holds(nodes[at], nodes[at + span])) {
                good = at + span
                span *= 2
            }
            var bad = minOf(at + span, reach + 1)
            while (bad - good > 1) {
                val middle = (good + bad) / 2
                if (holds(nodes[at], nodes[middle])) good = middle else bad = middle
            }
            if (good == at) {
                // Another way joins these two junctions more cheaply, so a point on the Trail's own edge holds it.
                points += middleOf(edges[at])
                at++
            } else {
                start = good - at
                at = good
            }
            points += RoutePoint(lat(nodes[at]), lon(nodes[at]))
        }
    }
    return points
}

/** Halfway along the edge's middle segment, so even an edge of two points gets a point inside it. */
private fun TrailNetwork.middleOf(edge: Int): RoutePoint {
    val point = edgeFirstPoint[edge] + (edgePointCount[edge] - 1) / 2
    return RoutePoint(
        (pointLat[point] + pointLat[point + 1]) * 0.5e-7,
        (pointLon[point] + pointLon[point + 1]) * 0.5e-7,
    )
}

private fun TrailNetwork.lat(node: Int) = nodeLat[node] * 1e-7

private fun TrailNetwork.lon(node: Int) = nodeLon[node] * 1e-7
