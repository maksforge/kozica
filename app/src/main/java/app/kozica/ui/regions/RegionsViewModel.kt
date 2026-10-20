package app.kozica.ui.regions

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionFiles
import app.kozica.data.regions.RegionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** @param regions null until the first read of the phone's Regions. */
data class RegionsUiState(
    val regions: List<Region>? = null,
) {
    /** What "Download all" fetches: every Region neither ready nor already on its way. */
    val toFetch: List<Region>
        get() = regions.orEmpty().filter { it.state == RegionState.NotDownloaded || it.state is RegionState.Failed }

    /** Null while any of [toFetch]'s sizes is unknown, so a partial sum never passes for the whole. */
    val toFetchSize: Long?
        get() = toFetch.sumOf { it.downloadSize ?: return null }
}

class RegionsViewModel(
    regions: Flow<List<Region>>,
    private val files: RegionFiles,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<RegionsUiState> =
        regions
            .map(::RegionsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegionsUiState())

    // The application's scope: Back mid-request would otherwise drop the state Play answers with.
    fun download(ids: Collection<String>) {
        applicationScope.launch { files.download(ids) }
    }

    fun downloadAll() = download(state.value.toFetch.map { it.id })

    fun remove(id: String) {
        applicationScope.launch { files.remove(id) }
    }

    /** Nothing awaiting confirmation means the state shown was stale, so the Region is asked for again. */
    fun confirm(
        id: String,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        viewModelScope.launch { if (!files.confirm(launcher)) files.download(listOf(id)) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    RegionsViewModel(app.container.regions, app.container.regionFiles, app.container.applicationScope)
                }
            }
    }
}
