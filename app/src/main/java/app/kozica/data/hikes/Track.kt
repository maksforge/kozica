package app.kozica.data.hikes

import app.kozica.data.routes.Router
import java.io.ByteArrayOutputStream
import kotlin.math.roundToLong

/** One fix kept on a Track. [altitude] is the GPS height above the ellipsoid, null when the fix had none. */
data class TrackPoint(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
)

/** A Hike's Track: its stretches between pauses, each in time order, never joined to the next. */
typealias Track = List<List<TrackPoint>>

private const val FORMAT_VERSION = 1
private const val DEGREE_UNITS = 1e7
private const val ALTITUDE_UNITS = 10.0

/**
 * The compact form a Track is stored in once its Hike ends (docs/phase-6.md § 2): a version byte,
 * then each stretch as its point count and every value as a zigzag varint delta from the point
 * before, positions in 1e-7 degrees and heights in decimetres. About 8 bytes a point.
 */
fun encodeTrack(track: Track): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(FORMAT_VERSION)
    out.varint(track.size.toLong())
    var time = 0L
    var latitude = 0L
    var longitude = 0L
    var altitude = 0L
    for (stretch in track) {
        out.varint(stretch.size.toLong())
        for (point in stretch) {
            val lat = (point.latitude * DEGREE_UNITS).roundToLong()
            val lon = (point.longitude * DEGREE_UNITS).roundToLong()
            out.varint(zigzag(point.timeMillis - time))
            out.varint(zigzag(lat - latitude))
            out.varint(zigzag(lon - longitude))
            time = point.timeMillis
            latitude = lat
            longitude = lon
            // The lowest bit says whether a height follows, so a fix with none costs one byte.
            val height = point.altitude?.let { (it * ALTITUDE_UNITS).roundToLong() }
            if (height == null) {
                out.varint(0)
            } else {
                out.varint(zigzag(height - altitude) shl 1 or 1)
                altitude = height
            }
        }
    }
    return out.toByteArray()
}

fun decodeTrack(bytes: ByteArray): Track {
    val input = VarintReader(bytes)
    val version = input.byte()
    require(version == FORMAT_VERSION) { "Track format $version is newer than this build" }
    var time = 0L
    var latitude = 0L
    var longitude = 0L
    var altitude = 0L
    return List(input.varint().toInt()) {
        List(input.varint().toInt()) {
            time += unzigzag(input.varint())
            latitude += unzigzag(input.varint())
            longitude += unzigzag(input.varint())
            val height = input.varint()
            val hasHeight = height and 1L == 1L
            if (hasHeight) altitude += unzigzag(height ushr 1)
            TrackPoint(
                time,
                latitude / DEGREE_UNITS,
                longitude / DEGREE_UNITS,
                if (hasHeight) altitude / ALTITUDE_UNITS else null,
            )
        }
    }
}

/** Along each stretch, never across a pause. */
fun Track.metres(): Double = sumOf { stretch -> stretch.zipWithNext { a, b -> a.metresTo(b) }.sum() }

/**
 * The time spent moving: each step between two fixes at walking pace or faster. A rest leaves no
 * fixes, because the 5 m filter holds them back, so it shows up as one slow step and is left out.
 */
fun Track.movingSeconds(): Long =
    sumOf { stretch ->
        stretch.zipWithNext().sumOf { (a, b) ->
            val seconds = (b.timeMillis - a.timeMillis) / 1000.0
            if (seconds > 0 && a.metresTo(b) / seconds >= MOVING_METRES_PER_SECOND) seconds else 0.0
        }
    }.roundToLong()

/** Metres from the first point to each point in order, along each stretch and never across a pause. */
fun Track.along(): List<Double> {
    val along = mutableListOf<Double>()
    var metres = 0.0
    for (stretch in this) {
        stretch.forEachIndexed { i, point ->
            if (i > 0) metres += stretch[i - 1].metresTo(point)
            along += metres
        }
    }
    return along
}

/** The points [first] to [last] of the Track in order, both kept, in the stretches they were in (docs/phase-6.md § 4). */
fun Track.trim(
    first: Int,
    last: Int,
): Track {
    var at = 0
    return mapNotNull { stretch ->
        val from = at
        at += stretch.size
        val kept = stretch.filterIndexed { i, _ -> from + i in first..last }
        kept.takeIf { it.isNotEmpty() }
    }
}

/** About 0.7 km/h, below the slowest climb on a steep Trail. */
const val MOVING_METRES_PER_SECOND = 0.2

private fun TrackPoint.metresTo(other: TrackPoint) = Router.metres(latitude, longitude, other.latitude, other.longitude)

private fun zigzag(value: Long): Long = (value shl 1) xor (value shr 63)

private fun unzigzag(value: Long): Long = (value ushr 1) xor -(value and 1)

private fun ByteArrayOutputStream.varint(value: Long) {
    var rest = value
    while (rest and 0x7FL.inv() != 0L) {
        write(((rest and 0x7F) or 0x80).toInt())
        rest = rest ushr 7
    }
    write(rest.toInt())
}

private class VarintReader(
    private val bytes: ByteArray,
) {
    private var at = 0

    fun byte(): Int = bytes[at++].toInt() and 0xFF

    fun varint(): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val b = byte()
            value = value or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
        }
    }
}
