package app.kozica.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kozica.MainApplication
import app.kozica.data.MapCamera
import kotlinx.coroutines.runBlocking

/**
 * Stores the map's camera for scripted readings (docs/phase-4.md § 1); the map opens on it at the
 * next cold start. Delivery needs what [SeedReceiver] needs: `-f 0x00000020`.
 *
 * ```bash
 * adb shell am force-stop <pkg>.debug
 * adb shell am broadcast -n <pkg>.debug/app.kozica.debug.CameraReceiver -f 0x00000020 \
 *   --ef lat 49.23 --ef lon 20.0 --ef zoom 13
 * ```
 */
class CameraReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!intent.hasExtra("lat") || !intent.hasExtra("lon") || !intent.hasExtra("zoom")) {
            resultCode = 1
            resultData = "lat, lon and zoom are required"
            return
        }
        val camera =
            MapCamera(
                latitude = intent.getFloatExtra("lat", 0f).toDouble(),
                longitude = intent.getFloatExtra("lon", 0f).toDouble(),
                zoom = intent.getFloatExtra("zoom", 0f).toDouble(),
            )
        // A small write the next launch must see, so it finishes before the receiver returns.
        runBlocking { (context.applicationContext as MainApplication).preferences.setMapCamera(camera) }
        resultCode = 0
        resultData = "camera set"
    }
}
