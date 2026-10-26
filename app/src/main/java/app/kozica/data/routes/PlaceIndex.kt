package app.kozica.data.routes

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.cos

/** The kinds `scripts/build-network.py` writes, by the name it stores (docs/phase-5.md § 2). */
enum class PlaceKind(
    val stored: String,
) {
    PEAK("peak"),
    PASS("pass"),
    HUT("hut"),
    SHELTER("shelter"),
    VIEWPOINT("viewpoint"),
    TOWN("town"),
    VILLAGE("village"),
    HAMLET("hamlet"),
    LAKE("water"),
    CLEARING("clearing"),
    PLACE("place"),
    ;

    companion object {
        fun of(stored: String): PlaceKind = entries.firstOrNull { it.stored == stored } ?: PLACE
    }
}

/** @param matched the name the search matched, which may be another language's or an older one. */
data class Place(
    val id: Long,
    val kind: PlaceKind,
    val latitude: Double,
    val longitude: Double,
    val region: String,
    val name: String,
    val matched: String = name,
)

interface PlaceSearch {
    fun search(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int = 20,
    ): List<Place>

    fun nearest(
        latitude: Double,
        longitude: Double,
    ): Place?
}

/** `places.sqlite`, opened read-only. */
class PlaceIndex(
    file: File,
) : PlaceSearch {
    private val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    /**
     * Names starting with [text] before names holding it from a word's start on, each group nearest to
     * the centre first; one row per place, by its best-matched name.
     */
    override fun search(
        text: String,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ): List<Place> {
        val folded = fold(text)
        if (folded.isEmpty()) return emptyList()
        val squeeze = cos(Math.toRadians(latitude)).let { it * it }
        val found = LinkedHashMap<Long, Place>()
        db
            .rawQuery(
                """
                SELECT p.id, p.kind, p.lat, p.lon, p.region, p.name, n.name
                FROM place_names n JOIN places p ON p.id = n.place
                WHERE n.folded MATCH ?
                ORDER BY substr(n.folded, 1, ?) = ? DESC,
                    (p.lat - ?) * (p.lat - ?) + (p.lon - ?) * (p.lon - ?) * ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(
                    "\"$folded*\"",
                    folded.length.toString(),
                    folded,
                    latitude.toString(),
                    latitude.toString(),
                    longitude.toString(),
                    longitude.toString(),
                    squeeze.toString(),
                    (limit * 4).toString(),
                ),
            ).use { rows ->
                while (rows.moveToNext() && found.size < limit) {
                    val id = rows.getLong(0)
                    if (id in found) continue
                    found[id] =
                        Place(
                            id = id,
                            kind = PlaceKind.of(rows.getString(1)),
                            latitude = rows.getDouble(2),
                            longitude = rows.getDouble(3),
                            region = rows.getString(4),
                            name = rows.getString(5),
                            matched = rows.getString(6),
                        )
                }
            }
        return found.values.toList()
    }

    /** The place that best names a spot: within [metres], a peak, pass or Hut before anything else, then the nearest. */
    override fun nearest(
        latitude: Double,
        longitude: Double,
    ): Place? {
        val metres = NAMING_METRES
        val dLat = metres / METRES_PER_DEGREE
        val dLon = dLat / cos(Math.toRadians(latitude))
        val near = mutableListOf<Place>()
        db
            .rawQuery(
                "SELECT id, kind, lat, lon, region, name FROM places WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                arrayOf(
                    latitude - dLat,
                    latitude + dLat,
                    longitude - dLon,
                    longitude + dLon,
                ).map { it.toString() }.toTypedArray(),
            ).use { rows ->
                while (rows.moveToNext()) {
                    near +=
                        Place(
                            rows.getLong(0),
                            PlaceKind.of(rows.getString(1)),
                            rows.getDouble(2),
                            rows.getDouble(3),
                            rows.getString(4),
                            rows.getString(5),
                        )
                }
            }
        return near
            .map { it to Router.metres(latitude, longitude, it.latitude, it.longitude) }
            .filter { (_, away) -> away <= metres }
            .minWithOrNull(compareBy({ (place, _) -> place.kind !in LANDMARKS }, { (_, away) -> away }))
            ?.first
    }

    private companion object {
        const val NAMING_METRES = 500.0
        const val METRES_PER_DEGREE = 111_320.0
        val LANDMARKS = setOf(PlaceKind.PEAK, PlaceKind.PASS, PlaceKind.HUT)
    }
}

/**
 * The assets' `places.sqlite`, copied out once per install of an APK, since SQLite cannot open a file
 * inside one; null when this build carries none. The copy is in `noBackupFilesDir`, which the next
 * install rewrites rather than restores.
 */
fun copiedPlaces(context: Context): File? {
    val copy = File(context.noBackupFilesDir, PLACES)
    val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    if (copy.exists() && copy.lastModified() >= installed) return copy
    val asset =
        try {
            context.assets.open(PLACES)
        } catch (_: FileNotFoundException) {
            return null
        }
    val partial = File(context.noBackupFilesDir, "$PLACES.part")
    asset.use { input -> partial.outputStream().use { input.copyTo(it) } }
    check(partial.renameTo(copy)) { "could not move $partial into place" }
    return copy
}

const val PLACES = "places.sqlite"
