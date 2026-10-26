package app.kozica.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.SavedRoute
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** A saved Route with its totals, null until it is planned or where it cannot be. */
data class RouteRow(
    val route: SavedRoute,
    val metres: Double? = null,
    val minutes: Int? = null,
)

/** @param routes null until the first read, then by name. */
data class RoutesUiState(
    val routes: List<RouteRow>? = null,
)

/**
 * docs/phase-5.md § 5: saved Routes by name, each with its length and time, planned again from the
 * current network one Route at a time as the list is shown.
 */
class RoutesViewModel(
    saved: Flow<List<SavedRoute>>,
    private val planRoute: suspend (List<RoutePoint>) -> PlannedRoute?,
    private val renameRoute: suspend (id: String, name: String) -> Unit,
    private val deleteRoute: suspend (id: String) -> Unit,
    private val routeToShow: MutableStateFlow<String?>,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val totals = mutableMapOf<List<RoutePoint>, Pair<Double, Int>?>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RoutesUiState> =
        saved
            .map { routes -> routes.sortedWith(compareBy(Collator.getInstance()) { it.name }) }
            .transformLatest { routes ->
                val rows = routes.map { it.withTotals() }.toMutableList()
                emit(RoutesUiState(rows.toList()))
                for ((i, row) in rows.withIndex()) {
                    val points = row.route.points
                    if (points in totals || points.size < 2) continue
                    totals[points] =
                        withContext(compute) { planRoute(points) }
                            ?.takeIf { it.complete }
                            ?.let { it.metres to it.minutes }
                    rows[i] = row.route.withTotals()
                    emit(RoutesUiState(rows.toList()))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutesUiState())

    fun show(id: String) {
        routeToShow.value = id
    }

    fun rename(
        id: String,
        name: String,
    ) {
        viewModelScope.launch { renameRoute(id, name) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteRoute(id) }
    }

    private fun SavedRoute.withTotals(): RouteRow =
        totals[points].let { found -> RouteRow(this, found?.first, found?.second) }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    RoutesViewModel(
                        app.container.routes.observeAll(),
                        app.container::planRoute,
                        app.container.routes::rename,
                        app.container.routes::delete,
                        app.container.routeToShow,
                    )
                }
            }
    }
}
