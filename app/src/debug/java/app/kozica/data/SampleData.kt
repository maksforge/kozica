package app.kozica.data

import app.kozica.data.routes.RoutePoint
import kotlinx.coroutines.flow.first

/** The debug fixture: two saved Routes in the Tatry, the same every time (docs/phase-0.md § 2). */
object SampleData {
    suspend fun seed(repository: RouteRepository) {
        wipe(repository)
        repository.save(
            null,
            "Kuźnice – Morskie Oko",
            listOf(
                RoutePoint(49.2702510, 19.9808508, "Kuźnice"),
                RoutePoint(49.2190881, 20.0163920, "Zawrat"),
                RoutePoint(49.1987000, 20.0712000, "Morskie Oko"),
            ),
        )
        repository.save(
            null,
            "Kasprowy Wierch",
            listOf(
                RoutePoint(49.2702510, 19.9808508, "Kuźnice"),
                RoutePoint(49.2318000, 19.9818000, "Kasprowy Wierch"),
            ),
        )
    }

    suspend fun wipe(repository: RouteRepository) {
        for (route in repository.observeAll().first()) repository.delete(route.id)
    }
}
