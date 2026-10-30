package app.kozica.data.routes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A Route's length and its walking time by the signposts' rule, unrounded. */
data class Totals(
    val metres: Double,
    val seconds: Double,
)

/**
 * Saved Routes' totals by their points, each planned once for the life of the process, since the
 * network cannot change under a running one. A Route with no complete way over the Trails is known
 * as null.
 */
class RouteTotals(
    private val planRoute: suspend (List<RoutePoint>) -> PlannedRoute?,
) {
    private val known = MutableStateFlow<Map<List<RoutePoint>, Totals?>>(emptyMap())

    val planned: StateFlow<Map<List<RoutePoint>, Totals?>> = known.asStateFlow()

    suspend fun plan(points: List<RoutePoint>) {
        if (points.size < 2 || points in known.value) return
        val totals = planRoute(points)?.takeIf { it.complete }?.let { Totals(it.metres, it.seconds) }
        known.update { it + (points to totals) }
    }
}
