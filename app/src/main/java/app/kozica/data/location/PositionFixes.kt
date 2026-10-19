package app.kozica.data.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone's fixes, read by the app itself rather than by Play services or MapLibre's engine
 * (docs/phase-2.md § 4). The last known fix comes first, however old: `PositionAge` greys it.
 */
@RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
fun positionFixes(
    context: Context,
    approximateOnly: Boolean,
): Flow<Location> =
    callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = manager.provider(approximateOnly)
        Log.i(TAG, "provider $provider, approximate only $approximateOnly")
        if (provider == null) {
            awaitClose()
            return@callbackFlow
        }
        manager.getLastKnownLocation(provider)?.let { trySend(it) }
        val listener =
            LocationListenerCompat { fix ->
                Log.d(TAG, "fix from ${fix.provider}, accuracy ${fix.accuracy} m")
                trySend(fix)
            }
        LocationManagerCompat.requestLocationUpdates(
            manager,
            provider,
            LocationRequestCompat.Builder(INTERVAL_MS).setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build(),
            ContextCompat.getMainExecutor(context),
            listener,
        )
        awaitClose { LocationManagerCompat.removeUpdates(manager, listener) }
    }

// GPS needs the fine permission, so with approximate only the network provider stands in for it.
private fun LocationManager.provider(approximateOnly: Boolean): String? {
    val candidates =
        if (approximateOnly) {
            listOf(FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
    return candidates.firstOrNull { LocationManagerCompat.hasProvider(this, it) }
}

// LocationManager.FUSED_PROVIDER is API 31, but many older phones carry the provider under this name.
private const val FUSED_PROVIDER = "fused"

private const val INTERVAL_MS = 1_000L

private const val TAG = "KozicaLocation"
