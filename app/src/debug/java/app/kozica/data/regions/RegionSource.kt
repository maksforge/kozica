package app.kozica.data.regions

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File

/** The Tatry pack, or the archive hand-pushed to `files/regions/` as docs/phase-1.md § 1 describes. */
fun regionStates(context: Context): Flow<List<Region>> {
    val handPushed = File(context.filesDir, "regions/tatry.pmtiles")
    return RegionFiles
        .of(context)
        .tatry
        .map { state ->
            val tatry = if (state !is RegionState.Ready && handPushed.exists()) readyRegion(handPushed) else state
            listOf(Region("tatry", tatry))
        }.flowOn(Dispatchers.IO)
}
