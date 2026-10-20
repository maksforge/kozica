package app.kozica.ui.welcome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.AppPreferences
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @param regions null until the first read of the phone's Regions.
 * @param selected the Regions ticked, of those [choosable].
 */
data class WelcomeUiState(
    val regions: List<Region>? = null,
    val selected: Set<String> = emptySet(),
) {
    /** A Region already ready or on its way, as after an update from 0.3.0, is shown ticked and fixed. */
    val choosable: List<Region>
        get() = regions.orEmpty().filter { it.state == RegionState.NotDownloaded || it.state is RegionState.Failed }

    val chosen: List<Region> get() = choosable.filter { it.id in selected }

    val allSelected: Boolean get() = choosable.isNotEmpty() && chosen.size == choosable.size

    /** Every Region already ready or on its way, so there is nothing left to ask. */
    val nothingToChoose: Boolean get() = regions != null && choosable.isEmpty()

    /** Null while any chosen size is unknown, as "Download all" does on the Regions screen. */
    val chosenSize: Long? get() = chosen.sumOf { it.downloadSize ?: return null }
}

class WelcomeViewModel(
    regions: Flow<List<Region>>,
    private val downloadRegions: suspend (List<String>) -> Unit,
    private val preferences: AppPreferences,
    private val applicationScope: CoroutineScope,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // A list, because a Set is not Bundle-able; kept here so the ticks outlive process death.
    private val selected = savedState.getStateFlow(SELECTED, emptyList<String>())

    val state: StateFlow<WelcomeUiState> =
        combine(regions, selected.map { it.toSet() }, ::WelcomeUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeUiState())

    init {
        viewModelScope.launch {
            if (state.first { it.regions != null }.nothingToChoose) preferences.setOnboardingDone(true)
        }
    }

    fun toggle(id: String) {
        val ticked = selected.value
        savedState[SELECTED] = if (id in ticked) ticked - id else ticked + id
    }

    fun toggleAll() {
        savedState[SELECTED] = if (state.value.allSelected) emptyList() else state.value.choosable.map { it.id }
    }

    fun download() {
        val ids = state.value.chosen.map { it.id }
        applicationScope.launch { downloadRegions(ids) }
        applicationScope.launch { preferences.setOnboardingDone(true) }
    }

    fun skip() {
        applicationScope.launch { preferences.setOnboardingDone(true) }
    }

    companion object {
        private const val SELECTED = "selected"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    WelcomeViewModel(
                        app.container.regions,
                        app.container.regionFiles::download,
                        app.preferences,
                        app.container.applicationScope,
                        createSavedStateHandle(),
                    )
                }
            }
    }
}
