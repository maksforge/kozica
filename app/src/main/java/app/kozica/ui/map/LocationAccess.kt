package app.kozica.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

enum class LocationGrant {
    NONE,
    APPROXIMATE,
    PRECISE,
}

/** Both read back reliably, so they are re-read on every resume rather than remembered. */
data class LocationRead(
    val grant: LocationGrant,
    val on: Boolean,
)

/** Asked together, as Android requires from API 31 for the approximate option to be offered. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.readLocation(): LocationRead =
    LocationRead(
        grant =
            when {
                granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationGrant.PRECISE
                granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationGrant.APPROXIMATE
                else -> LocationGrant.NONE
            },
        on = LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java)),
    )

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
