package app.kozica.data.regions

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.Task
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Every Region's fast-follow asset pack (docs/phase-2c.md § 1), one pack per polygon in
 * `scripts/regions/`, named `region_<id>` and holding `<id>.pmtiles`.
 *
 * One per process, so a fetch started on one screen reaches every collector through [updates].
 */
class RegionFiles private constructor(
    context: Context,
) {
    private val manager = AssetPackManagerFactory.getInstance(context.applicationContext)
    private val updates = MutableSharedFlow<Map<String, RegionState>>(extraBufferCapacity = 16)

    // Kept apart from the states because Play reports a pack's size only in some of them.
    private val downloadSizes = ConcurrentHashMap<String, Long>()

    val states: Flow<List<Region>> =
        merge(
            flow { emit(StateChange(read(REGION_NAMES.keys, logged = true), isFirstRead = true)) },
            updates.map { StateChange(it) },
            progress()
                .map { state -> StateChange(mapOf(idOf(state.name()) to fromPackState(idOf(state.name()), state))) }
                .catch { },
        ).scan(emptyMap<String, RegionState>()) { known, change -> known.mergedWith(change) }
            .filter { it.keys.containsAll(REGION_NAMES.keys) }
            .map { known -> REGION_NAMES.map { (id, name) -> Region(id, name, known.getValue(id), downloadSizes[id]) } }
            .flowOn(Dispatchers.IO)

    fun readyArchive(id: String): File? =
        manager
            .getPackLocation(pack(id))
            ?.assetsPath()
            ?.let { File(it, "$id.pmtiles") }
            ?.takeIf(File::exists)

    /** One request for every Region in [ids], rather than one per Region. */
    suspend fun download(ids: Collection<String>) {
        Log.i(TAG, "fetch ${ids.joinToString()}")
        updates.emit(
            try {
                val states = manager.fetch(ids.map(::pack)).await().packStates()
                ids.associateWith { id -> states[pack(id)]?.let { fromPackState(id, it) } ?: RegionState.NotDownloaded }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ids.associateWith { id -> readyArchive(id)?.let(::readyRegion) ?: failure(e) }
            },
        )
    }

    /**
     * Play's own dialog, for both [RegionState.WaitingForWifi] and [RegionState.NeedsConfirmation].
     * False when nothing was awaiting confirmation; the states are then read again, so a stale one
     * corrects itself.
     */
    suspend fun confirm(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean =
        manager.showConfirmationDialog(launcher).also { shown ->
            if (!shown) updates.emit(read(REGION_NAMES.keys))
        }

    // A build sideloaded by installDebug has no Play session and declares no pack, so a request
    // fails or answers without the pack; either is "not downloaded", never a crash (ADR-0007).
    // docs/phase-2c.md R1 reads the first launch's states from a Play install, so this logs in release.
    private suspend fun read(
        ids: Collection<String>,
        logged: Boolean = false,
    ): Map<String, RegionState> {
        val states =
            try {
                manager.getPackStates(ids.map(::pack)).await().packStates()
            } catch (_: Exception) {
                emptyMap()
            }
        if (logged) ids.forEach { Log.i(TAG, "$it: status ${states[pack(it)]?.status()}") }
        return ids.associateWith { id ->
            readyArchive(id)?.let(::readyRegion)
                ?: states[pack(id)]?.let { fromPackState(id, it) }
                ?: RegionState.NotDownloaded
        }
    }

    private fun progress(): Flow<AssetPackState> =
        callbackFlow {
            val listener =
                AssetPackStateUpdateListener { state ->
                    if (idOf(state.name()) in REGION_NAMES) trySend(state)
                }
            manager.registerListener(listener)
            awaitClose { manager.unregisterListener(listener) }
        }

    private fun failure(e: Exception): RegionState =
        RegionState.Failed((e as? AssetPackException)?.errorCode ?: AssetPackErrorCode.INTERNAL_ERROR)

    private fun fromPackState(
        id: String,
        state: AssetPackState,
    ): RegionState {
        state.totalBytesToDownload().takeIf { it > 0 }?.let { downloadSizes[id] = it }
        return when (state.status()) {
            AssetPackStatus.COMPLETED -> readyArchive(id)?.let(::readyRegion) ?: RegionState.NotDownloaded
            AssetPackStatus.PENDING,
            AssetPackStatus.DOWNLOADING,
            AssetPackStatus.TRANSFERRING,
            -> {
                val updating = readyArchive(id) != null
                val percent = downloadPercent(state)
                if (updating) RegionState.UpdateInProgress(percent) else RegionState.Downloading(percent)
            }
            AssetPackStatus.WAITING_FOR_WIFI -> RegionState.WaitingForWifi
            AssetPackStatus.REQUIRES_USER_CONFIRMATION -> RegionState.NeedsConfirmation
            AssetPackStatus.FAILED -> RegionState.Failed(state.errorCode())
            else -> RegionState.NotDownloaded
        }
    }

    private fun downloadPercent(state: AssetPackState): Int =
        if (state.status() == AssetPackStatus.TRANSFERRING) {
            state.transferProgressPercentage()
        } else {
            state.totalBytesToDownload().takeIf { it > 0 }?.let { (100 * state.bytesDownloaded() / it).toInt() } ?: 0
        }

    companion object {
        @Volatile private var instance: RegionFiles? = null

        fun of(context: Context): RegionFiles =
            instance ?: synchronized(this) { instance ?: RegionFiles(context).also { instance = it } }

        private const val TAG = "KozicaPack"

        private fun pack(id: String) = "region_$id"

        private fun idOf(pack: String) = pack.removePrefix("region_")
    }
}

internal class StateChange(
    val states: Map<String, RegionState>,
    val isFirstRead: Boolean = false,
)

// The first read fills only what no update has said yet: a listener event that lands while that read
// is pending is newer than the snapshot the read returns.
internal fun Map<String, RegionState>.mergedWith(change: StateChange): Map<String, RegionState> =
    if (change.isFirstRead) change.states + this else this + change.states

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
