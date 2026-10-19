package app.kozica.data.regions

import android.app.Activity
import android.content.Context
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Tatry's on-demand asset pack (docs/phase-1.md § 3). Debug only until phase 2b.
 *
 * One per process, so a download or a removal started from Settings reaches the map's collector
 * through [updates].
 */
class RegionFiles private constructor(
    context: Context,
) {
    private val manager = AssetPackManagerFactory.getInstance(context.applicationContext)
    private val updates = MutableSharedFlow<RegionState>(extraBufferCapacity = 8)

    val tatry: Flow<RegionState> =
        merge(
            flow { emit(read()) },
            updates,
            progress()
                .map(::fromPackState)
                .catch { },
        ).flowOn(Dispatchers.IO)

    fun readyArchive(): File? =
        manager
            .getPackLocation(PACK)
            ?.assetsPath()
            ?.let { File(it, ARCHIVE) }
            ?.takeIf(File::exists)

    suspend fun download() {
        updates.emit(
            try {
                manager
                    .fetch(listOf(PACK))
                    .await()
                    .packStates()[PACK]
                    ?.let(::fromPackState)
                    ?: RegionState.NotDownloaded
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure(e)
            },
        )
    }

    fun confirm(activity: Activity) {
        manager.showConfirmationDialog(activity).addOnFailureListener { updates.tryEmit(failure(it)) }
    }

    suspend fun remove() {
        try {
            manager.removePack(PACK).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updates.emit(failure(e))
            return
        }
        updates.emit(read())
    }

    // A build sideloaded by installDebug has no Play session and declares no pack, so a request
    // fails or answers without the Tatry; either is "not downloaded", never a crash (ADR-0007).
    private suspend fun read(): RegionState {
        readyArchive()?.let { return readyRegion(it) }
        return try {
            manager
                .getPackStates(listOf(PACK))
                .await()
                .packStates()[PACK]
                ?.let(::fromPackState)
                ?: RegionState.NotDownloaded
        } catch (_: Exception) {
            RegionState.NotDownloaded
        }
    }

    private fun progress(): Flow<AssetPackState> =
        callbackFlow {
            val listener = AssetPackStateUpdateListener { state -> if (state.name() == PACK) trySend(state) }
            manager.registerListener(listener)
            awaitClose { manager.unregisterListener(listener) }
        }

    private fun failure(e: Exception): RegionState =
        RegionState.Failed((e as? AssetPackException)?.errorCode ?: AssetPackErrorCode.INTERNAL_ERROR)

    private fun fromPackState(state: AssetPackState): RegionState =
        when (state.status()) {
            AssetPackStatus.COMPLETED -> readyArchive()?.let(::readyRegion) ?: RegionState.NotDownloaded
            AssetPackStatus.PENDING,
            AssetPackStatus.DOWNLOADING,
            AssetPackStatus.TRANSFERRING,
            -> {
                val percent = downloadPercent(state)
                if (readyArchive() != null) RegionState.UpdateInProgress(percent) else RegionState.Downloading(percent)
            }
            AssetPackStatus.WAITING_FOR_WIFI,
            AssetPackStatus.REQUIRES_USER_CONFIRMATION,
            -> RegionState.WaitingForConfirmation
            AssetPackStatus.FAILED -> RegionState.Failed(state.errorCode())
            else -> RegionState.NotDownloaded
        }

    private fun downloadPercent(state: AssetPackState): Int =
        if (state.status() == AssetPackStatus.TRANSFERRING) {
            state.transferProgressPercentage()
        } else {
            state.totalBytesToDownload().takeIf { it > 0 }?.let { (100 * state.bytesDownloaded() / it).toInt() } ?: 0
        }

    companion object {
        private const val PACK = "region_tatry"
        private const val ARCHIVE = "tatry.pmtiles"

        @Volatile private var instance: RegionFiles? = null

        fun of(context: Context): RegionFiles =
            instance ?: synchronized(this) { instance ?: RegionFiles(context).also { instance = it } }
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
