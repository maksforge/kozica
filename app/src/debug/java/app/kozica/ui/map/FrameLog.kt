package app.kozica.ui.map

import android.os.SystemClock
import android.util.Log
import org.maplibre.android.maps.MapView

/**
 * One line per rendered frame under `KozicaFrames`, and one each time the camera starts moving, the
 * camera comes to rest and the map has nothing left to draw, read by `scripts/map/frame-stats.py`
 * (docs/phase-4.md § 1).
 */
fun logFrames(mapView: MapView) {
    mapView.addOnDidFinishRenderingFrameListener { _, encoding, rendering ->
        Log.i("KozicaFrames", "us=${now()} encode=$encoding render=$rendering")
    }
    mapView.addOnDidBecomeIdleListener { Log.i("KozicaFrames", "us=${now()} map=idle") }
    mapView.getMapAsync { map ->
        map.addOnCameraMoveStartedListener { Log.i("KozicaFrames", "us=${now()} camera=move") }
        map.addOnCameraIdleListener { Log.i("KozicaFrames", "us=${now()} camera=idle") }
    }
}

private fun now() = SystemClock.elapsedRealtimeNanos() / 1_000
