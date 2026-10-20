package app.kozica.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.AppPreferences
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionFiles
import app.kozica.data.regions.RegionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    /** Null while any chosen size is unknown, as "Download all" does on the Regions screen. */
    val chosenSize: Long? get() = chosen.sumOf { it.downloadSize ?: return null }
}

class WelcomeViewModel(
    regions: Flow<List<Region>>,
    private val files: RegionFiles,
    private val preferences: AppPreferences,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val selected = MutableStateFlow(emptySet<String>())

    val state: StateFlow<WelcomeUiState> =
        combine(regions, selected, ::WelcomeUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeUiState())

    fun toggle(id: String) {
        selected.update { if (id in it) it - id else it + id }
    }

    fun toggleAll() {
        val all =
            state.value.choosable
                .map { it.id }
                .toSet()
        selected.value = if (state.value.allSelected) emptySet() else all
    }

    fun download() {
        val ids = state.value.chosen.map { it.id }
        applicationScope.launch { files.download(ids) }
        applicationScope.launch { preferences.setOnboardingDone(true) }
    }

    fun skip() {
        applicationScope.launch { preferences.setOnboardingDone(true) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    WelcomeViewModel(
                        app.container.regions,
                        app.container.regionFiles,
                        app.preferences,
                        app.container.applicationScope,
                    )
                }
            }
    }
}
