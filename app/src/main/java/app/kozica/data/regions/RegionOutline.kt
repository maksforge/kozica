package app.kozica.data.regions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Whether a position is inside a Region, from the mask `scripts/map/gen_regions.py` writes into
 * `map/regions.geojson`: the world with every Region cut out of it, so a point is in a Region when
 * it is not in the mask.
 */
class RegionOutline(
    private val rings: List<DoubleArray>,
) {
    fun contains(
        latitude: Double,
        longitude: Double,
    ): Boolean = rings.count { it.crossedBy(latitude, longitude) } % 2 == 0

    /** Even-odd: whether a ray east from the point crosses this ring an odd number of times. */
    private fun DoubleArray.crossedBy(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        var inside = false
        var j = size - 2
        for (i in 0 until size step 2) {
            val (lon1, lat1) = this[i] to this[i + 1]
            val (lon2, lat2) = this[j] to this[j + 1]
            if ((lat1 > latitude) != (lat2 > latitude) &&
                longitude < (lon2 - lon1) * (latitude - lat1) / (lat2 - lat1) + lon1
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    companion object {
        fun of(geoJson: String): RegionOutline {
            val mask =
                Json
                    .parseToJsonElement(geoJson)
                    .jsonObject
                    .getValue("features")
                    .jsonArray
                    .map { it.jsonObject }
                    .first {
                        it["properties"]
                            ?.jsonObject
                            ?.get("kind")
                            ?.jsonPrimitive
                            ?.content == "outside"
                    }.getValue("geometry")
                    .jsonObject
                    .getValue("coordinates")
                    .jsonArray
            val rings =
                mask.flatMap { polygon ->
                    polygon.jsonArray.map { ring ->
                        (ring as JsonArray)
                            .flatMap { position -> position.jsonArray.take(2).map { it.jsonPrimitive.double } }
                            .toDoubleArray()
                    }
                }
            return RegionOutline(rings)
        }
    }
}
