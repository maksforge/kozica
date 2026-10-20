package app.kozica.data.regions

import android.content.Context
import kotlinx.coroutines.flow.Flow

fun regionStates(context: Context): Flow<List<Region>> = RegionFiles.of(context).states
