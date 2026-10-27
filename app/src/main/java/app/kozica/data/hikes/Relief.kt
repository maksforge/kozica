package app.kozica.data.hikes

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/** The relief's zoom and tile size (ADR-0009), as `scripts/build-network.py` reads them. */
internal const val RELIEF_ZOOM = 12
internal const val RELIEF_TILE = 512

/**
 * Heights from the relief's Terrarium tiles, bilinear between pixel centres: `Relief` in
 * `scripts/build-network.py`, which gave the Trail network its heights, so a Hike over a planned
 * Route reads the same grid the same way. [tile] is a zoom-12 tile's pixels as ARGB, row by row,
 * or null where the relief has none.
 */
class ReliefHeights(
    private val tile: (x: Int, y: Int) -> IntArray?,
) {
    private val tiles =
        object : LinkedHashMap<Long, IntArray?>(TILES_KEPT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, IntArray?>) = size > TILES_KEPT
        }

    @Synchronized
    fun height(
        latitude: Double,
        longitude: Double,
    ): Double? {
        val world = RELIEF_TILE.toDouble() * (1 shl RELIEF_ZOOM)
        val x = (longitude + 180) / 360 * world - 0.5
        val s = sin(Math.toRadians(latitude))
        val y = (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * world - 0.5
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val a = pixel(x0, y0) ?: return null
        val b = pixel(x0 + 1, y0) ?: return null
        val c = pixel(x0, y0 + 1) ?: return null
        val d = pixel(x0 + 1, y0 + 1) ?: return null
        val top = a * (1 - fx) + b * fx
        val bottom = c * (1 - fx) + d * fx
        return top * (1 - fy) + bottom * fy
    }

    private fun pixel(
        px: Int,
        py: Int,
    ): Double? {
        val tx = px / RELIEF_TILE
        val ty = py / RELIEF_TILE
        val pixels = tiles.getOrPut(tx.toLong() shl 32 or ty.toLong()) { tile(tx, ty) } ?: return null
        return terrarium(pixels[(py % RELIEF_TILE) * RELIEF_TILE + px % RELIEF_TILE])
    }

    private companion object {
        /** About 1 MiB each; a day's Hike crosses a handful. */
        const val TILES_KEPT = 16
    }
}

/** A Terrarium pixel's height in metres. */
internal fun terrarium(argb: Int): Double {
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    return r * 256 + g + b / 256.0 - 32768
}

/** A PMTiles v3 archive's tiles by z/x/y, read straight from the file, as the map's own reader does. */
class PmTilesArchive(
    file: File,
) : Closeable {
    private val file = RandomAccessFile(file, "r")
    private val header = ByteBuffer.wrap(read(0, HEADER_BYTES)).order(ByteOrder.LITTLE_ENDIAN)

    init {
        require(String(header.array(), 0, 7, Charsets.US_ASCII) == "PMTiles" && header.get(7) == 3.toByte()) {
            "not a PMTiles v3 archive"
        }
    }

    private val gzip = header.get(INTERNAL_COMPRESSION).toInt() == GZIP

    fun tile(
        z: Int,
        x: Int,
        y: Int,
    ): ByteArray? {
        val wanted = tileId(z, x, y)
        var offset = header.getLong(ROOT_OFFSET)
        var length = header.getLong(ROOT_LENGTH)
        repeat(MAX_DEPTH) {
            val entry = directory(read(offset, length.toInt())).lastOrNull { it.tileId <= wanted } ?: return null
            if (entry.runLength == 0L) {
                offset = header.getLong(LEAVES_OFFSET) + entry.offset
                length = entry.length
            } else {
                if (wanted >= entry.tileId + entry.runLength) return null
                return read(header.getLong(TILES_OFFSET) + entry.offset, entry.length.toInt())
            }
        }
        return null
    }

    private fun directory(bytes: ByteArray): List<DirectoryEntry> =
        readDirectory(if (gzip) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes)

    @Synchronized
    private fun read(
        offset: Long,
        length: Int,
    ): ByteArray {
        file.seek(offset)
        return ByteArray(length).also(file::readFully)
    }

    override fun close() = file.close()

    private companion object {
        const val HEADER_BYTES = 127
        const val ROOT_OFFSET = 8
        const val ROOT_LENGTH = 16
        const val LEAVES_OFFSET = 40
        const val TILES_OFFSET = 56
        const val INTERNAL_COMPRESSION = 97
        const val GZIP = 2
        const val MAX_DEPTH = 4
    }
}

internal data class DirectoryEntry(
    val tileId: Long,
    val runLength: Long,
    val length: Long,
    val offset: Long,
)

/** The PMTiles v3 directory layout: a count, then each column of the entries in turn, all varints. */
internal fun readDirectory(bytes: ByteArray): List<DirectoryEntry> {
    var at = 0

    fun varint(): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val b = bytes[at++].toInt() and 0xFF
            value = value or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
        }
    }
    val count = varint().toInt()
    var id = 0L
    val ids = LongArray(count) { varint().also { id += it }.let { id } }
    val runs = LongArray(count) { varint() }
    val lengths = LongArray(count) { varint() }
    val offsets = LongArray(count)
    for (i in 0 until count) {
        val value = varint()
        offsets[i] = if (value == 0L && i > 0) offsets[i - 1] + lengths[i - 1] else value - 1
    }
    return List(count) { DirectoryEntry(ids[it], runs[it], lengths[it], offsets[it]) }
}

/** The tile's place on the PMTiles Hilbert curve, as `_tile_id()` in `scripts/build-network.py`. */
internal fun tileId(
    z: Int,
    x: Int,
    y: Int,
): Long {
    val n = 1L shl z
    var tx = x.toLong()
    var ty = y.toLong()
    var d = 0L
    var s = n / 2
    while (s > 0) {
        val rx = if (tx and s > 0) 1L else 0L
        val ry = if (ty and s > 0) 1L else 0L
        d += s * s * ((3 * rx) xor ry)
        if (ry == 0L) {
            if (rx == 1L) {
                tx = n - 1 - tx
                ty = n - 1 - ty
            }
            tx = ty.also { ty = tx }
        }
        s /= 2
    }
    return (n * n - 1) / 3 + d
}
