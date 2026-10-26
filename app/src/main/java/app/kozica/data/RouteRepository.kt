package app.kozica.data

import app.kozica.data.routes.RoutePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** A saved Route as the screens see it: its points in the user's order. */
data class SavedRoute(
    val id: String,
    val name: String,
    val points: List<RoutePoint>,
)

class RouteRepository(
    private val dao: RouteDao,
) {
    fun observeAll(): Flow<List<SavedRoute>> = dao.observeAll().map { routes -> routes.map { it.toSaved() } }

    suspend fun get(id: String): SavedRoute? = dao.get(id)?.toSaved()

    /** The Route's name as it changes, null once it is deleted. */
    fun observeName(id: String): Flow<String?> = dao.observeName(id)

    /** Saves [points] under [name], as a new Route when [id] is null; returns the Route's id. */
    suspend fun save(
        id: String?,
        name: String,
        points: List<RoutePoint>,
    ): String {
        val now = Instant.now()
        val existing = id?.let { dao.get(it)?.route }
        val route = existing?.copy(name = name, updatedAt = now) ?: RouteEntity(name = name, createdAt = now)
        dao.save(
            route,
            points.mapIndexed {
                i,
                point,
                ->
                RoutePointEntity(route.id, i, point.latitude, point.longitude, point.label)
            },
        )
        return route.id
    }

    suspend fun rename(
        id: String,
        name: String,
    ) = dao.rename(id, name, Instant.now())

    suspend fun delete(id: String) = dao.delete(id)

    private fun RouteWithPoints.toSaved() =
        SavedRoute(
            route.id,
            route.name,
            points.sortedBy { it.position }.map { RoutePoint(it.latitude, it.longitude, it.label) },
        )
}
