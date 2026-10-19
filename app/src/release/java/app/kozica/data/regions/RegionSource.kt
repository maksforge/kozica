package app.kozica.data.regions

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Nothing can put a Region on the phone until phase 2b brings the asset-delivery library to release. */
@Suppress("UNUSED_PARAMETER")
fun regionStates(context: Context): Flow<List<Region>> = flowOf(emptyList())
