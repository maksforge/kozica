package app.kozica.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.SavedRoute
import app.kozica.data.routes.RouteTotals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * docs/phase-5.md § 5: saved Routes by name, each with its length and time, planned from the current
 * network one Route at a time as the list is shown and kept by [totals] for the next visit.
 */
class RoutesViewModel(
    saved: Flow<List<SavedRoute>>,
    private val totals: RouteTotals,
    private val renameRoute: suspend (id: String, name: String) -> Unit,
    private val deleteRoute: suspend (id: String) -> Unit,
    private val routeToShow: MutableStateFlow<String?>,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RoutesUiState> =
        saved
            .map { routes -> routes.sortedWith(compareBy(Collator.getInstance()) { it.name }) }
            .flatMapLatest { routes ->
                channelFlow {
                    launch { for (route in routes) withContext(compute) { totals.plan(route.points) } }
                    totals.planned.collect { planned ->
                        val rows =
                            routes.map { route ->
                                planned[route.points].let { RouteRow(route, it?.metres, it?.minutes) }
                            }
                        send(RoutesUiState(rows))
                    }
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

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    RoutesViewModel(
                        app.container.routes.observeAll(),
                        app.container.routeTotals,
                        app.container.routes::rename,
                        app.container.routes::delete,
                        app.container.routeToShow,
                    )
                }
            }
    }
}
