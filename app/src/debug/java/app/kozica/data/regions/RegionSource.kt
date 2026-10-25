package app.kozica.data.regions

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File

/** Every pack, or an archive hand-pushed to `files/regions/<id>.pmtiles` as docs/phase-1.md § 1 describes. */
fun regionStates(context: Context): Flow<List<Region>> =
    RegionFiles
        .of(context)
        .states
        .map { regions ->
            regions.map { region ->
                val handPushed = File(context.filesDir, "regions/${region.id}.pmtiles")
                if (region.state !is RegionState.Ready && handPushed.exists()) {
                    region.copy(state = readyRegion(handPushed))
                } else {
                    region
                }
            }
        }.flowOn(Dispatchers.IO)

/** The relief pack, or a relief hand-pushed to `files/relief/`, as phase 4's gate pushed one. */
fun reliefState(context: Context): Flow<Region> =
    RegionFiles
        .of(context)
        .relief
        .map { relief ->
            val handPushed = File(context.filesDir, "relief/relief.pmtiles")
            if (relief.state !is RegionState.Ready &&
                handPushed.exists()
            ) {
                relief.copy(state = readyRegion(handPushed))
            } else {
                relief
            }
        }.flowOn(Dispatchers.IO)
