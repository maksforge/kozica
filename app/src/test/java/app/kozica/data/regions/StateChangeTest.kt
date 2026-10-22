package app.kozica.data.regions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateChangeTest {
    private val known = mapOf("tatry" to RegionState.Downloading(40))

    @Test
    fun `the first read does not overwrite an update that landed before it`() {
        val first =
            StateChange(
                mapOf("tatry" to RegionState.NotDownloaded, "bieszczady" to RegionState.NotDownloaded),
                isFirstRead = true,
            )

        assertEquals(
            mapOf("tatry" to RegionState.Downloading(40), "bieszczady" to RegionState.NotDownloaded),
            known.mergedWith(first),
        )
    }

    @Test
    fun `a later update wins`() {
        val failed = StateChange(mapOf("tatry" to RegionState.Failed(-6)))

        assertEquals(mapOf("tatry" to RegionState.Failed(-6)), known.mergedWith(failed))
    }

    @Test
    fun `a Region still not downloaded once Play has been asked for it has failed`() {
        assertTrue(RegionState.NotDownloaded.afterAsking(asked = true) is RegionState.Failed)
    }

    @Test
    fun `a Region not asked for yet stays not downloaded`() {
        assertEquals(RegionState.NotDownloaded, RegionState.NotDownloaded.afterAsking(asked = false))
    }

    @Test
    fun `asking leaves a moving download alone`() {
        assertEquals(RegionState.Downloading(40), RegionState.Downloading(40).afterAsking(asked = true))
    }
}
