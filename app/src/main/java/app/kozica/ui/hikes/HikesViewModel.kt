package app.kozica.ui.hikes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.HikeSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** @param hikes null until the first read, then newest first. */
data class HikesUiState(
    val hikes: List<HikeSummary>? = null,
)

/**
 * docs/phase-6.md § 4: ended Hikes newest first. Opening the tab reads the height grid for any
 * Hike still without its ascent, which a Stop the process did not outlive leaves behind.
 */
class HikesViewModel(
    ended: Flow<List<HikeSummary>>,
    private val fillClimbs: suspend () -> Unit,
    private val hikeToShow: MutableStateFlow<String?>,
    io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val state: StateFlow<HikesUiState> =
        ended.map { HikesUiState(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HikesUiState())

    init {
        viewModelScope.launch(io) { fillClimbs() }
    }

    fun show(id: String) {
        hikeToShow.value = id
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    HikesViewModel(
                        app.container.hikes.observeEnded(),
                        app.container.hikes::fillClimbs,
                        app.container.hikeToShow,
                    )
                }
            }
    }
}
