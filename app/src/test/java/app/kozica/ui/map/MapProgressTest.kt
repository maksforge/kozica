package app.kozica.ui.map

import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MapProgressTest {
    @Test
    fun `every Region ready shows no line`() {
        assertNull(mapProgress(listOf(region(READY), region(READY))))
    }

    @Test
    fun `a Region downloading or not yet asked for counts the ready ones`() {
        assertEquals(
            MapProgress.Downloading(ready = 1, total = 3),
            mapProgress(listOf(region(READY), region(DOWNLOADING), region(RegionState.NotDownloaded))),
        )
    }

    @Test
    fun `a download still moving is named before a failure`() {
        assertEquals(
            MapProgress.Downloading(ready = 0, total = 2),
            mapProgress(listOf(region(FAILED), region(DOWNLOADING))),
        )
    }

    @Test
    fun `waiting for Wi-Fi is named before a confirmation and a failure`() {
        assertEquals(
            MapProgress.WaitingForWifi,
            mapProgress(
                listOf(region(FAILED), region(RegionState.NeedsConfirmation), region(RegionState.WaitingForWifi)),
            ),
        )
    }

    @Test
    fun `a confirmation is named before a failure`() {
        assertEquals(
            MapProgress.NeedsConfirmation,
            mapProgress(listOf(region(FAILED), region(RegionState.NeedsConfirmation))),
        )
    }

    @Test
    fun `a failure with nothing moving offers Retry`() {
        assertEquals(
            MapProgress.Failed(size = 31_000_000),
            mapProgress(listOf(region(READY), Region("tatry", name = 0, FAILED, downloadSize = 31_000_000))),
        )
    }

    @Test
    fun `Retry drops the size while any failed Region's is unknown`() {
        assertEquals(
            MapProgress.Failed(size = null),
            mapProgress(listOf(Region("a", name = 0, FAILED, downloadSize = 31_000_000), region(FAILED))),
        )
    }

    @Test
    fun `an update in progress is named when nothing else is`() {
        assertEquals(
            MapProgress.Updating,
            mapProgress(listOf(region(READY), region(RegionState.UpdateInProgress(percent = 40)))),
        )
    }

    @Test
    fun `only Regions never asked for are fetched unasked`() {
        val regions =
            listOf(
                Region("a", name = 0, RegionState.NotDownloaded),
                Region("b", name = 0, FAILED),
                Region("c", name = 0, READY),
                Region("d", name = 0, DOWNLOADING),
                Region("e", name = 0, RegionState.UpdateInProgress(percent = 10)),
                Region("f", name = 0, RegionState.WaitingForWifi),
            )
        assertEquals(listOf("a"), toFetch(regions))
    }
}

private fun region(state: RegionState) = Region("tatry", name = 0, state)

private val READY = RegionState.Ready(File("tatry.pmtiles"), osmDate = null, bounds = null)

private val DOWNLOADING = RegionState.Downloading(percent = 20)

private val FAILED = RegionState.Failed(errorCode = -6)
