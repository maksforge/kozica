package app.kozica.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kozica.MainApplication
import app.kozica.data.AppPreferences
import app.kozica.data.HikeEntity
import app.kozica.record.Recorder
import app.kozica.record.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * @param unfinished a Hike whose recording was lost with its process, offered to resume or finish
 *   while nothing records.
 */
data class RecordingUiState(
    val recording: Recording? = null,
    val unfinished: HikeEntity? = null,
    val autoResume: Boolean = true,
    val notificationsAsked: Boolean = false,
)

/** The recording bar's and its dialogs', which every tab shares (docs/phase-6.md § 2). */
class RecordingViewModel(
    private val recorder: Recorder,
    unfinished: Flow<HikeEntity?>,
    private val preferences: AppPreferences,
    private val finish: suspend (hikeId: String, now: Instant) -> Unit,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<RecordingUiState> =
        combine(
            recorder.recording,
            unfinished,
            preferences.autoResume,
            preferences.notificationsAsked,
        ) { recording, unfinished, autoResume, asked ->
            RecordingUiState(recording, unfinished.takeIf { recording == null }, autoResume, asked)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingUiState())

    fun start(hikeId: String?) = recorder.start(hikeId)

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    fun stop() = recorder.stop()

    /** On the application's scope, so leaving the screen cannot cut it short. */
    fun finishUnfinished(hikeId: String) {
        applicationScope.launch { finish(hikeId, Instant.now()) }
    }

    fun notificationsAsked() {
        viewModelScope.launch { preferences.setNotificationsAsked() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    val container = app.container
                    RecordingViewModel(
                        container.recorder,
                        container.hikes.observeUnfinished(),
                        app.preferences,
                        container.recorder::finish,
                        container.applicationScope,
                    )
                }
            }
    }
}
