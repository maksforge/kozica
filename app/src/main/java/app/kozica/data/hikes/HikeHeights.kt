package app.kozica.data.hikes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.location.altitude.AltitudeConverter
import android.os.Build
import androidx.annotation.RequiresApi
import app.kozica.data.regions.RegionOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Each point's height by docs/phase-6.md § 5's rule: the grid's inside the Regions while the relief
 * is on the phone, else the fix's own, converted to sea level where the platform can (API 34+).
 */
class HikeHeights(
    private val context: Context,
    private val outline: RegionOutline,
    private val relief: suspend () -> File?,
) {
    private var opened: Pair<File, ReliefHeights>? = null

    /** One per point of [track] in order, read on the IO dispatcher. */
    suspend fun of(track: Track): List<Height?> =
        withContext(Dispatchers.IO) {
            val grid = grid()
            val seaLevel = seaLevel(context)
            track.flatten().map { point ->
                val fromGrid =
                    grid
                        ?.takeIf { outline.contains(point.latitude, point.longitude) }
                        ?.height(point.latitude, point.longitude)
                fromGrid?.let { Height(it, HeightSource.GRID) }
                    ?: point.altitude?.let { Height(seaLevel(point, it), HeightSource.GPS) }
            }
        }

    @Synchronized
    private fun cached(file: File): ReliefHeights =
        opened?.takeIf { it.first == file }?.second
            ?: ReliefHeights(tilesOf(PmTilesArchive(file))).also { opened = file to it }

    private suspend fun grid(): ReliefHeights? = relief()?.let { runCatching { cached(it) }.getOrNull() }
}

/** Below API 34 there is no geoid on the phone, so a GPS height stays above the ellipsoid (*Known gaps*). */
private fun seaLevel(context: Context): (TrackPoint, Double) -> Double =
    if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    ) {
        seaLevelConverter(context)
    } else {
        { _, altitude -> altitude }
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun seaLevelConverter(context: Context): (TrackPoint, Double) -> Double {
    val converter = AltitudeConverter()
    return { point, altitude ->
        val location =
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = point.latitude
                longitude = point.longitude
                this.altitude = altitude
            }
        try {
            converter.addMslAltitudeToLocation(context, location)
            location.mslAltitudeMeters
        } catch (_: IOException) {
            altitude
        } catch (_: IllegalArgumentException) {
            altitude
        }
    }
}

private fun tilesOf(archive: PmTilesArchive): (Int, Int) -> IntArray? =
    { x, y ->
        archive.tile(RELIEF_ZOOM, x, y)?.let { webp ->
            val options =
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPremultiplied = false
                }
            BitmapFactory.decodeByteArray(webp, 0, webp.size, options)?.let { bitmap ->
                val pixels =
                    if (bitmap.width == RELIEF_TILE && bitmap.height == RELIEF_TILE) {
                        IntArray(RELIEF_TILE * RELIEF_TILE).also {
                            bitmap.getPixels(it, 0, RELIEF_TILE, 0, 0, RELIEF_TILE, RELIEF_TILE)
                        }
                    } else {
                        null
                    }
                bitmap.recycle()
                pixels
            }
        }
    }
