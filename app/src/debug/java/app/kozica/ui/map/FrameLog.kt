package app.kozica.ui.map

import android.os.SystemClock
import android.util.Log
import org.maplibre.android.maps.MapView

/** One line per rendered frame under `KozicaFrames`, read by `scripts/map/frame-stats.py` (docs/phase-4.md § 1). */
fun logFrames(mapView: MapView) {
    mapView.addOnDidFinishRenderingFrameListener { _, encoding, rendering ->
        Log.i("KozicaFrames", "us=${SystemClock.elapsedRealtimeNanos() / 1_000} encode=$encoding render=$rendering")
    }
}
