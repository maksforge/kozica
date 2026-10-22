package app.kozica.ui.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rescue numbers are a safety value edited by hand (`docs/phase-3.md` § Tests): this fails when
 * one is changed by accident, which no build and no reading on a phone without a SIM would notice.
 */
class SafetyTest {
    @Test
    fun `every rescue number dials exactly what its service publishes`() {
        val dialled = Country.entries.associateWith { country -> country.rescue.map { it.dialled } }
        assertEquals(
            mapOf(
                Country.Poland to listOf("985", "+48601100300"),
                Country.Slovakia to listOf("18300"),
                Country.Czechia to listOf("+4201210"),
            ),
            dialled,
        )
        assertEquals("112", EMERGENCY.dialled)
    }

    @Test
    fun `a dialled number is digits after an optional plus`() {
        val all = Country.entries.flatMap { it.rescue } + EMERGENCY
        for (number in all) assertTrue(number.shown, Regex("""\+?\d+""").matches(number.dialled))
    }

    @Test
    fun `every country leads with a number that answers a local phone`() {
        for (country in Country.entries) assertTrue(country.name, !country.rescue.first().fallback)
    }

    @Test
    fun `the screen lists Poland, then Slovakia, then Czechia`() {
        assertEquals(listOf(Country.Poland, Country.Slovakia, Country.Czechia), Country.entries)
    }

    @Test
    fun `every page is https and shows a bare host`() {
        val pages = Country.entries.flatMap { it.avalanche + it.weather }
        for (page in pages) assertTrue(page.url, page.url.startsWith("https://"))
        assertEquals(
            "laviny.sk",
            Country.Slovakia.avalanche
                .single()
                .host,
        )
        assertEquals("gory.imgw.pl", Country.Poland.weather.host)
    }

    @Test
    fun `Poland and Czechia each list two avalanche bulletins, Slovakia one`() {
        assertEquals(
            mapOf(Country.Poland to 2, Country.Slovakia to 1, Country.Czechia to 2),
            Country.entries.associateWith { it.avalanche.size },
        )
    }

    @Test
    fun `only ČHMÚ's weather row is model charts`() {
        assertEquals(listOf(Country.Czechia), Country.entries.filter { it.weather.modelCharts })
    }
}
