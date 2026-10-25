package app.kozica.ui.map

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.maplibre.android.maps.MapView
import java.io.File

/**
 * Phase 4's gate (docs/phase-4.md § 1): a relief grid and its contours hand-pushed to
 * `files/relief/`, drawn by the throwaway layers in `assets/map/relief-gate-*.json`. Checkpoint C
 * replaces those with layers generated from the palette.
 */
fun reliefOverlay(
    context: Context,
    dark: Boolean,
): StyleOverlay? {
    val relief = File(context.filesDir, "relief/relief.pmtiles").takeIf(File::exists) ?: return null
    val contours = File(context.filesDir, "relief/contours.pmtiles").takeIf(File::exists) ?: return null
    val text =
        context.assets
            .open("map/relief-gate-${if (dark) "dark" else "light"}.json")
            .bufferedReader()
            .use { it.readText() }
            .replace("{relief}", relief.absolutePath)
            .replace("{contours}", contours.absolutePath)
    val overlay = Json.parseToJsonElement(text).jsonObject
    return StyleOverlay(
        sources = overlay.getValue("sources").jsonObject.mapValues { it.value.jsonObject },
        after =
            overlay.getValue("after").jsonObject.mapValues { (_, layers) ->
                layers.jsonArray.map { it.jsonObject }
            },
    )
}

/** One line per rendered frame under `KozicaFrames`, read by `scripts/map/frame-stats.py`. */
fun logFrames(mapView: MapView) {
    mapView.addOnDidFinishRenderingFrameListener { _, encoding, rendering ->
        Log.d(
            "KozicaFrames",
            "us=${SystemClock.elapsedRealtimeNanos() / 1_000} encode=$encoding render=$rendering",
        )
    }
}
