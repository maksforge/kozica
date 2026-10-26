package app.kozica.ui.map

import app.kozica.data.routes.Approach
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteSample
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLayerTest {
    @Test
    fun `the line runs solid over the Trails, dashed off them, and not at all across no way`() {
        val line =
            listOf(
                sample(0.0, Approach.OFF_TRAIL),
                sample(1.0, Approach.OFF_TRAIL),
                sample(2.0, Approach.TRAIL),
                sample(3.0, Approach.TRAIL),
                sample(4.0, Approach.NO_WAY),
                sample(5.0, Approach.TRAIL),
            )
        val points = listOf(RoutePoint(49.0, 20.0), RoutePoint(49.0, 20.005))
        val features = features(routeGeoJson(points, planned(line)))

        assertEquals(
            listOf("LineString off 2", "LineString trail 3", "LineString trail 2", "Point 1", "Point 2"),
            features,
        )
    }

    @Test
    fun `points alone are numbered before a Route is planned`() {
        assertEquals(listOf("Point 1"), features(routeGeoJson(listOf(RoutePoint(49.0, 20.0)), null)))
    }

    private fun features(geoJson: String) =
        Json.parseToJsonElement(geoJson).jsonObject.getValue("features").jsonArray.map {
            val feature = it.jsonObject
            val geometry = feature.getValue("geometry").jsonObject
            val properties = feature.getValue("properties").jsonObject
            val type = geometry.getValue("type").jsonPrimitive.content
            if (type == "Point") {
                "Point ${properties.getValue("number").jsonPrimitive.content}"
            } else {
                "$type ${properties.getValue("approach").jsonPrimitive.content} " +
                    geometry.getValue("coordinates").jsonArray.size
            }
        }

    private fun sample(
        metres: Double,
        approach: Approach,
    ) = RouteSample(49.0, 20.0 + metres / 1000, metres, 1000.0, approach)

    private fun planned(line: List<RouteSample>) =
        PlannedRoute(
            legs = emptyList(),
            line = line,
            ascent = 0.0,
            descent = 0.0,
            seconds = 0.0,
            secondsToPoint = emptyList(),
            demanding = emptyList(),
        )
}
