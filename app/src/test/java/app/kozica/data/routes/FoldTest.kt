package app.kozica.data.routes

import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/phase-5.md § Tests: the same table as `FoldTest` in `scripts/test_build_network.py`. */
class FoldTest {
    @Test
    fun `the names a user types without accents`() {
        assertEquals("sniezka", fold("Śnieżka"))
        assertEquals("lomnica", fold("Łomnica"))
        assertEquals("gerlachovsky stit", fold("Gerlachovský štít"))
        assertEquals("snezka", fold("Sněžka"))
        assertEquals("hala gasienicowa", fold("Hala Gąsienicowa"))
        assertEquals("schronisko pttk na hali", fold("Schronisko PTTK „Na Hali”"))
    }
}
