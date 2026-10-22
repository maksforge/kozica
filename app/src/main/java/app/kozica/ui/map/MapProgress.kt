package app.kozica.ui.map

import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState

/** The map's one line while any Region has not arrived, docs/phase-2c.md § 1's table. */
sealed interface MapProgress {
    data class Downloading(
        val ready: Int,
        val total: Int,
    ) : MapProgress

    data object WaitingForWifi : MapProgress

    data object NeedsConfirmation : MapProgress

    /** @param size what Retry would fetch, null while Play has not said for every failed Region. */
    data class Failed(
        val size: Long?,
    ) : MapProgress

    data object Updating : MapProgress
}

// The first that applies wins: a download still moving is what the user is waiting on, and Play's
// dialog comes before Retry because a failure may be waiting on it.
fun mapProgress(regions: List<Region>): MapProgress? {
    val states = regions.map { it.state }
    val ready = states.count { it is RegionState.Ready }
    return when {
        ready == states.size -> null
        states.any { it is RegionState.Downloading || it == RegionState.NotDownloaded } ->
            MapProgress.Downloading(ready, states.size)
        RegionState.WaitingForWifi in states -> MapProgress.WaitingForWifi
        RegionState.NeedsConfirmation in states -> MapProgress.NeedsConfirmation
        states.any { it is RegionState.Failed } ->
            MapProgress.Failed(
                regions.filter { it.state is RegionState.Failed }.sumOf {
                    it.downloadSize
                        ?: return MapProgress.Failed(null)
                },
            )
        else -> MapProgress.Updating
    }
}

/**
 * What the app asks Play for unasked: only a Region never asked for. A failed or cancelled one waits
 * for Retry, which shows its size, so a download the user stopped is not started again behind them.
 */
fun toFetch(regions: List<Region>): List<String> =
    regions.filter { it.state == RegionState.NotDownloaded }.map { it.id }
