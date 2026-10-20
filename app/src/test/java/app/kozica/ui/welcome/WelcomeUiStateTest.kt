package app.kozica.ui.welcome

import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WelcomeUiStateTest {
    private val regions =
        listOf(
            region("tatry", RegionState.Ready(File("tatry.pmtiles"), osmDate = null, bounds = null)),
            region("bieszczady", RegionState.NotDownloaded, size = 37),
            region("beskid_niski", RegionState.Failed(errorCode = -100), size = 44),
            region("gorce_pieniny", RegionState.Downloading(percent = 10)),
        )

    @Test
    fun `only a Region neither ready nor on its way can be chosen`() {
        assertEquals(listOf("bieszczady", "beskid_niski"), WelcomeUiState(regions).choosable.map { it.id })
    }

    @Test
    fun `all is every choosable Region, and a stale selection does not count`() {
        val all = WelcomeUiState(regions, selected = setOf("bieszczady", "beskid_niski", "tatry"))
        val some = WelcomeUiState(regions, selected = setOf("bieszczady"))

        assertTrue(all.allSelected)
        assertEquals(81L, all.chosenSize)
        assertFalse(some.allSelected)
    }

    @Test
    fun `nothing to choose only once the Regions are read and none is choosable`() {
        val waiting = listOf(regions[0], regions[3])

        assertFalse(WelcomeUiState().nothingToChoose)
        assertFalse(WelcomeUiState(regions).nothingToChoose)
        assertTrue(WelcomeUiState(waiting).nothingToChoose)
    }

    @Test
    fun `the size is unknown while one chosen size is`() {
        val state =
            WelcomeUiState(
                regions + region("tatry_2", RegionState.NotDownloaded, size = null),
                setOf("bieszczady", "tatry_2"),
            )

        assertNull(state.chosenSize)
    }
}

private fun region(
    id: String,
    state: RegionState,
    size: Long? = 1,
) = Region(id, name = 0, state, size)
