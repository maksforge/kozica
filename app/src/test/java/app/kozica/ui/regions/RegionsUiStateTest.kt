package app.kozica.ui.regions

import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RegionsUiStateTest {
    @Test
    fun `download all fetches only what is neither ready nor on its way`() {
        val state =
            RegionsUiState(
                listOf(
                    region("tatry", RegionState.Ready(File("tatry.pmtiles"), osmDate = null, bounds = null)),
                    region("bieszczady", RegionState.NotDownloaded),
                    region("beskid_niski", RegionState.Failed(errorCode = -100)),
                    region("gorce_pieniny", RegionState.Downloading(percent = 10)),
                    region("sudety_zachodnie", RegionState.WaitingForWifi),
                ),
            )

        assertEquals(listOf("bieszczady", "beskid_niski"), state.toFetch.map { it.id })
    }

    @Test
    fun `the size is the sum, or nothing while one is unknown`() {
        val known = RegionsUiState(listOf(region("tatry", size = 18), region("bieszczady", size = 37)))
        val partly = RegionsUiState(listOf(region("tatry", size = 18), region("bieszczady", size = null)))

        assertEquals(55L, known.toFetchSize)
        assertNull(partly.toFetchSize)
    }
}

private fun region(
    id: String,
    state: RegionState = RegionState.NotDownloaded,
    size: Long? = 1,
) = Region(id, name = 0, state, size)
