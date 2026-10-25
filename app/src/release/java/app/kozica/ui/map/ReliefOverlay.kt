package app.kozica.ui.map

import android.content.Context
import org.maplibre.android.maps.MapView

/** The release half of phase 4's gate apparatus (docs/phase-4.md § 1): no relief until C, no frame log. */
fun reliefOverlay(
    context: Context,
    dark: Boolean,
): StyleOverlay? = null

fun logFrames(mapView: MapView) = Unit
