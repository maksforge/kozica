package app.kozica.data.regions

import android.content.Context
import kotlinx.coroutines.flow.Flow

fun regionStates(context: Context): Flow<List<Region>> = RegionFiles.of(context).states

/** The relief pack, as Play delivers it. */
fun reliefState(context: Context): Flow<Region> = RegionFiles.of(context).relief
