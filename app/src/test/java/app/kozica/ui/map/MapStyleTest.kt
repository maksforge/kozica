package app.kozica.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun kotlinx.serialization.json.JsonObject.layerIds() =
        getValue("layers").jsonArray.map {
            it.jsonObject
                .getValue("id")
                .jsonPrimitive.content
        }
}
