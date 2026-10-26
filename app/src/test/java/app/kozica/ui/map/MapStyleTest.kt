package app.kozica.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapStyleTest {
    private val template =
        """
        {"version":8,"sources":{},"layers":[
          {"id":"land","type":"fill","source":"region","source-layer":"land"},
          {"id":"hillshade","type":"hillshade","source":"relief"},
          {"id":"contours","type":"line","source":"contours","source-layer":"contours"}
        ]}
        """.trimIndent()

    private val regions = listOf("tatry" to File("/r/tatry.pmtiles"), "bieszczady" to File("/r/bieszczady.pmtiles"))

    @Test
    fun `without the relief its layers and sources are left out`() {
        val style = Json.parseToJsonElement(expandStyle(template, regions)).jsonObject
        assertEquals(listOf("land-tatry", "land-bieszczady"), style.layerIds())
        assertFalse("relief" in style.getValue("sources").jsonObject)
    }

    @Test
    fun `with the relief its layers are drawn once, not per Region`() {
        val style = Json.parseToJsonElement(expandStyle(template, regions, File("/p/relief.pmtiles"))).jsonObject
        assertEquals(listOf("land-tatry", "land-bieszczady", "hillshade", "contours"), style.layerIds())
        val sources = style.getValue("sources").jsonObject
        assertEquals(
            "pmtiles://file:///p/contours.pmtiles",
            sources
                .getValue("contours")
                .jsonObject
                .getValue("url")
                .jsonPrimitive.content,
        )
        assertEquals(
            "terrarium",
            sources
                .getValue("relief")
                .jsonObject
                .getValue("encoding")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `the Route's source is kept and its layers drawn once`() {
        val withRoute =
            """
            {"version":8,"metadata":{"kozica:faded-opacity":0.3},
             "sources":{"route":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},
             "layers":[
              {"id":"trails","type":"line","source":"region","source-layer":"trails","metadata":{"kozica:fade":true}},
              {"id":"roads","type":"line","source":"region","source-layer":"roads"},
              {"id":"route","type":"line","source":"route"}
            ]}
            """.trimIndent()
        val expanded = expandStyle(withRoute, regions)
        val style = Json.parseToJsonElement(expanded).jsonObject
        assertEquals(
            listOf("trails-tatry", "trails-bieszczady", "roads-tatry", "roads-bieszczady", "route"),
            style.layerIds(),
        )
        assertTrue("route" in style.getValue("sources").jsonObject)
        assertEquals(Fade(listOf("trails-tatry", "trails-bieszczady"), 0.3f), fadeOf(expanded))
    }

    @Test
    fun `the Regions' outlines are drawn once, and their bounds are where the camera may go`() {
        val withRegions =
            """
            {"version":8,"metadata":{"kozica:faded-opacity":0.3,"kozica:bounds":[14.95,48.95,22.95,51.12]},
             "sources":{"regions":{"type":"geojson","data":"asset://map/regions.geojson"}},
             "layers":[
              {"id":"land","type":"fill","source":"region","source-layer":"land"},
              {"id":"outside","type":"fill","source":"regions"}
            ]}
            """.trimIndent()
        val expanded = expandStyle(withRegions, regions)

        assertEquals(
            listOf("land-tatry", "land-bieszczady", "outside"),
            Json.parseToJsonElement(expanded).jsonObject.layerIds(),
        )
        assertEquals(CameraBounds(14.95, 48.95, 22.95, 51.12), cameraBoundsOf(expanded))
    }

    private fun kotlinx.serialization.json.JsonObject.layerIds() =
        getValue("layers").jsonArray.map {
            it.jsonObject
                .getValue("id")
                .jsonPrimitive.content
        }
}
