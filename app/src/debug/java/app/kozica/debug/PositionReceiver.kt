package app.kozica.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kozica.data.location.overridePosition

/**
 * Puts "you are here" at a chosen place for scripted readings (docs/phase-2.md § 4). Delivery
 * needs what [SeedReceiver] needs: `-f 0x00000020`, and autostart on HyperOS.
 *
 * ```bash
 * adb shell am broadcast -n <pkg>.debug/app.kozica.debug.PositionReceiver -f 0x00000020 \
 *   --ef lat <lat> --ef lon <lon> --ef accuracy 10 [--el time <epoch ms>]
 * ```
 *
 * `time` is the fix's own time, now unless given; `scripts/replay-track.py` sets it.
 */
class PositionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!intent.hasExtra("lat") || !intent.hasExtra("lon")) {
            resultCode = 1
            resultData = "lat and lon are required"
            return
        }
        overridePosition(
            latitude = intent.getFloatExtra("lat", 0f).toDouble(),
            longitude = intent.getFloatExtra("lon", 0f).toDouble(),
            accuracy = intent.getFloatExtra("accuracy", 10f),
            time = intent.getLongExtra("time", System.currentTimeMillis()),
        )
        resultCode = 0
        resultData = "position set"
    }
}
