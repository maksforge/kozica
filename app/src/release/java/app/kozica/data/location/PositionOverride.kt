package app.kozica.data.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

/** The release half of the position seam: the real fixes, untouched (docs/phase-2.md § 4). */
fun Flow<Location>.withPositionOverride(): Flow<Location> = this
