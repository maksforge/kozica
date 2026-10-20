package app.kozica.data.regions

import org.junit.Assert.assertEquals
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
}
