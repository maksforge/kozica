package app.kozica.data.regions

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.ktx.requestFetch
import com.google.android.play.core.ktx.requestPackStates
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.ktx.requestRemovePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.zip.GZIPInputStream

sealed interface RegionState {
    data object NotDownloaded : RegionState

    data class Downloading(
        val percent: Int,
    ) : RegionState

    data class UpdateInProgress(
        val percent: Int,
    ) : RegionState

    data class Ready(
        val archive: File,
        val osmDate: Instant?,
    ) : RegionState

    data class Failed(
        val errorCode: Int,
    ) : RegionState
}

/** The Tatry's on-demand asset pack (docs/phase-1.md § 3). Debug only until checkpoint E. */
class RegionFiles(
    context: Context,
) {
    private val manager = AssetPackManagerFactory.getInstance(context.applicationContext)
    private val updates = MutableSharedFlow<RegionState>(extraBufferCapacity = 8)

    val tatry: Flow<RegionState> =
        merge(
            flow { emit(read()) },
            updates,
            manager
                .requestProgressFlow(listOf(PACK))
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
                manager.requestFetch(listOf(PACK)).packStates()[PACK]?.let(::fromPackState) ?: RegionState.NotDownloaded
            } catch (e: AssetPackException) {
                RegionState.Failed(e.errorCode)
            },
        )
    }

    suspend fun remove() {
        try {
            manager.requestRemovePack(PACK)
        } catch (e: AssetPackException) {
            updates.emit(RegionState.Failed(e.errorCode))
            return
        }
        updates.emit(read())
    }

    // A build sideloaded by installDebug has no Play session and declares no pack, so a request
    // fails or answers without the Tatry; either is "not downloaded", never a crash (ADR-0007).
    private suspend fun read(): RegionState {
        readyArchive()?.let { return RegionState.Ready(it, osmDate(it)) }
        return try {
            manager.requestPackStates(listOf(PACK)).packStates()[PACK]?.let(::fromPackState)
                ?: RegionState.NotDownloaded
        } catch (_: Exception) {
            RegionState.NotDownloaded
        }
    }

    private fun fromPackState(state: AssetPackState): RegionState =
        when (state.status()) {
            AssetPackStatus.COMPLETED ->
                readyArchive()?.let { RegionState.Ready(it, osmDate(it)) } ?: RegionState.NotDownloaded
            AssetPackStatus.PENDING,
            AssetPackStatus.DOWNLOADING,
            AssetPackStatus.TRANSFERRING,
            AssetPackStatus.WAITING_FOR_WIFI,
            AssetPackStatus.REQUIRES_USER_CONFIRMATION,
            -> {
                val percent = downloadPercent(state)
                if (readyArchive() != null) RegionState.UpdateInProgress(percent) else RegionState.Downloading(percent)
            }
            AssetPackStatus.FAILED -> RegionState.Failed(state.errorCode())
            else -> RegionState.NotDownloaded
        }

    private fun downloadPercent(state: AssetPackState): Int =
        if (state.status() == AssetPackStatus.TRANSFERRING) {
            state.transferProgressPercentage()
        } else {
            state.totalBytesToDownload().takeIf { it > 0 }?.let { (100 * state.bytesDownloaded() / it).toInt() } ?: 0
        }

    private companion object {
        const val PACK = "region_tatry"
        const val ARCHIVE = "tatry.pmtiles"
    }
}

/** The replication time planetiler copies from the extract's header into the PMTiles v3 metadata. */
internal fun osmDate(archive: File): Instant? =
    runCatching {
        RandomAccessFile(archive, "r").use { file ->
            val header = ByteArray(PMTILES_HEADER).also(file::readFully)
            if (String(header, 0, 7, Charsets.US_ASCII) != "PMTiles" || header[7] != 3.toByte()) return null
            val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val metadata = ByteArray(fields.getLong(32).toInt())
            file.seek(fields.getLong(24))
            file.readFully(metadata)
            val text =
                when (header[97].toInt()) {
                    1 -> metadata.decodeToString()
                    2 -> GZIPInputStream(metadata.inputStream()).use { it.readBytes().decodeToString() }
                    else -> return null
                }
            Json
                .parseToJsonElement(text)
                .jsonObject["planetiler:osm:osmosisreplicationtime"]
                ?.jsonPrimitive
                ?.content
                ?.let(Instant::parse)
        }
    }.getOrNull()

private const val PMTILES_HEADER = 127
