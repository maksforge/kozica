package app.kozica.data.location

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** In memory only, so process death or the debug row returns the map to the real reader. */
private val chosen = MutableStateFlow<Location?>(null)

/** The debug half of the position seam: a chosen fix, while one is set, replaces the real ones. */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<Location>.withPositionOverride(): Flow<Location> = chosen.flatMapLatest { it?.let(::flowOf) ?: this }

/**
 * Stamped now, so it goes stale like a real fix. It carries no bearing: the heading cone follows
 * the phone's real compass.
 */
fun overridePosition(
    latitude: Double,
    longitude: Double,
    accuracy: Float = DEFAULT_ACCURACY,
) {
    chosen.value =
        Location("debug").apply {
            this.latitude = latitude
            this.longitude = longitude
            this.accuracy = accuracy
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
}

fun clearPositionOverride() {
    chosen.value = null
}

private const val DEFAULT_ACCURACY = 10f
