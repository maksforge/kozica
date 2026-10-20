package app.kozica.data.regions

import androidx.annotation.StringRes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.zip.GZIPInputStream

/**
 * Every Region the build knows of, with its state, in [REGION_NAMES]' order. Each variant supplies
 * `regionStates(context)` in `RegionSource.kt` from [RegionFiles]; the debug half adds archives
 * pushed by hand (docs/phase-2b.md § 2).
 *
 * @param downloadSize the bytes Play reports a fetch would cost, null until Play has said.
 */
data class Region(
    val id: String,
    @StringRes val name: Int,
    val state: RegionState,
    val downloadSize: Long? = null,
)

sealed interface RegionState {
    data object NotDownloaded : RegionState

    data class Downloading(
        val percent: Int,
    ) : RegionState

    data class UpdateInProgress(
        val percent: Int,
    ) : RegionState

    data object WaitingForWifi : RegionState

    data object NeedsConfirmation : RegionState

    data class Ready(
        val archive: File,
        val osmDate: Instant?,
        val bounds: GeoBounds?,
    ) : RegionState

    data class Failed(
        val errorCode: Int,
    ) : RegionState
}

data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

/** A Region whose archive is on disk, with what the archive's PMTiles v3 header says about it. */
fun readyRegion(archive: File): RegionState.Ready =
    runCatching {
        RandomAccessFile(archive, "r").use { file ->
            val header = ByteArray(PMTILES_HEADER).also(file::readFully)
            require(String(header, 0, 7, Charsets.US_ASCII) == "PMTiles" && header[7] == 3.toByte())
            val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            RegionState.Ready(archive, osmDate(file, fields), bounds(fields))
        }
    }.getOrElse { RegionState.Ready(archive, osmDate = null, bounds = null) }

/** The replication time planetiler copies from the extract's header into the archive's metadata. */
private fun osmDate(
    file: RandomAccessFile,
    fields: ByteBuffer,
): Instant? =
    runCatching {
        val metadata = ByteArray(fields.getLong(32).toInt())
        file.seek(fields.getLong(24))
        file.readFully(metadata)
        val text =
            when (fields.get(97).toInt()) {
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
    }.getOrNull()

private fun bounds(fields: ByteBuffer): GeoBounds =
    GeoBounds(
        south = fields.getInt(106) / E7,
        west = fields.getInt(102) / E7,
        north = fields.getInt(114) / E7,
        east = fields.getInt(110) / E7,
    )

private const val PMTILES_HEADER = 127
private const val E7 = 10_000_000.0
