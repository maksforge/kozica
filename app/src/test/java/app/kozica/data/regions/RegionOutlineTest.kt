package app.kozica.data.regions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RegionOutlineTest {
    private val outline = RegionOutline.of(File("src/main/assets/map/regions.geojson").readText())

    @Test
    fun `a Hike in the Tatry is inside a Region, one in Warsaw or Kraków is not`() {
        assertTrue("Kasprowy Wierch", outline.contains(49.2318, 19.9818))
        assertTrue("Śnieżka", outline.contains(50.7360, 15.7397))
        assertFalse("Warsaw", outline.contains(52.2297, 21.0122))
        assertFalse("Kraków's market square", outline.contains(50.0617, 19.9373))
    }
}
