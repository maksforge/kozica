package app.kozica.data.hikes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

class ReliefTest {
    @Test
    fun `tile ids follow the Hilbert curve as scripts build-network py numbers them`() {
        // From `_tile_id()` in scripts/build-network.py; the zoom-12 pair are tiles of the Tatry.
        val expected =
            mapOf(
                Triple(0, 0, 0) to 0L,
                Triple(1, 0, 0) to 1L,
                Triple(1, 0, 1) to 2L,
                Triple(1, 1, 1) to 3L,
                Triple(1, 1, 0) to 4L,
                Triple(2, 1, 3) to 11L,
                Triple(12, 2275, 1420) to 19_819_594L,
                Triple(12, 2279, 1425) to 19_819_667L,
            )
        for ((zxy, id) in expected) assertEquals("$zxy", id, tileId(zxy.first, zxy.second, zxy.third))
    }

    @Test
    fun `a directory reads back its entries, an offset of 0 following on from the entry before`() {
        val bytes =
            varints(
                3,
                5,
                1,
                4, // tile ids as deltas: 5, 6, 10
                1,
                2,
                0, // run lengths, the last a leaf directory
                100,
                200,
                50, // lengths
                1,
                0,
                1_001, // offsets + 1, where 0 means just after the entry before
            )

        assertEquals(
            listOf(
                DirectoryEntry(5, 1, 100, 0),
                DirectoryEntry(6, 2, 200, 100),
                DirectoryEntry(10, 0, 50, 1_000),
            ),
            readDirectory(bytes),
        )
    }

    @Test
    fun `a Terrarium pixel is red times 256, plus green, plus blue over 256, less 32768`() {
        assertEquals(1_011.5, terrarium(argb(height = 1_011.5)), 1e-9)
        assertEquals(-12.25, terrarium(argb(height = -12.25)), 1e-9)
    }

    @Test
    fun `heights are bilinear between pixel centres, and missing where a corner's tile is`() {
        // A slope rising a metre per pixel eastward and two per pixel southward.
        val heights = ReliefHeights { x, y -> IntArray(RELIEF_TILE * RELIEF_TILE) { argb(slope(x, y, it)) } }
        val (lat, lon) = coordinates(pixelX = 1_165_000.75, pixelY = 727_000.25)

        val height = heights.height(lat, lon)!!

        assertEquals(1_165_000.75 + 2 * 727_000.25, height + OFFSET, 1e-3)
        assertNull(ReliefHeights { _, _ -> null }.height(lat, lon))
    }

    /** Heights relative to [OFFSET], so the slope stays inside Terrarium's range. */
    private fun slope(
        tileX: Int,
        tileY: Int,
        index: Int,
    ): Double {
        val px = tileX * RELIEF_TILE + index % RELIEF_TILE
        val py = tileY * RELIEF_TILE + index / RELIEF_TILE
        return px + 2.0 * py - OFFSET
    }

    /** The position at the centre-relative pixel, inverting the reader's Web Mercator. */
    private fun coordinates(
        pixelX: Double,
        pixelY: Double,
    ): Pair<Double, Double> {
        val world = RELIEF_TILE.toDouble() * (1 shl RELIEF_ZOOM)
        val lon = (pixelX + 0.5) / world * 360 - 180
        val n = PI * (1 - 2 * (pixelY + 0.5) / world)
        val lat = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n)))
        val s = sin(Math.toRadians(lat))
        assertEquals(pixelY, (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * world - 0.5, 1e-6)
        return lat to lon
    }

    private fun argb(height: Double): Int {
        val q = Math.round((height + 32_768) * 256).toInt()
        return (0xFF shl 24) or (q shr 16 and 0xFF shl 16) or (q shr 8 and 0xFF shl 8) or (q and 0xFF)
    }

    private fun varints(vararg values: Int): ByteArray {
        val out = ByteArrayOutputStream()
        for (value in values) {
            var rest = value
            while (rest >= 0x80) {
                out.write(((rest and 0x7F) or 0x80).toInt())
                rest = rest ushr 7
            }
            out.write(rest.toInt())
        }
        return out.toByteArray()
    }

    private companion object {
        const val OFFSET = 2_600_000.0
    }
}
