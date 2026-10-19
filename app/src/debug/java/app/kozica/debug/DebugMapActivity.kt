package app.kozica.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.viewinterop.AndroidView
import app.kozica.R
import app.kozica.data.regions.RegionFiles
import app.kozica.theme.AppTheme
import app.kozica.theme.Spacing
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File

/**
 * Phase 1's gate (docs/phase-1.md § 1): draws the Tatry archive with no network permission, from the
 * pack once it is downloaded and from the hand-pushed file until then.
 */
class DebugMapActivity : ComponentActivity() {
    private var mapView: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val archive = RegionFiles(this).readyArchive() ?: File(filesDir, "regions/tatry.pmtiles")
        if (!archive.exists()) {
            Log.w(TAG, "No pack and no archive at ${archive.absolutePath}")
            setContent { AppTheme { MissingArchive() } }
            return
        }

        MapLibre.getInstance(this)
        // Before any MapView: its setup asks ConnectivityManager otherwise, which throws without
        // ACCESS_NETWORK_STATE.
        MapLibre.setConnected(false)

        val mapView = MapView(this).also { mapView = it }
        mapView.onCreate(savedInstanceState)
        mapView.addOnDidFailLoadingMapListener { Log.e(TAG, "Map failed to load: $it") }
        val style =
            assets
                .open("map/gate-style.json")
                .bufferedReader()
                .use { it.readText() }
                .replace("{archive}", archive.absolutePath)
        mapView.getMapAsync { map ->
            map.cameraPosition =
                CameraPosition
                    .Builder()
                    .target(LatLng(49.232, 19.982))
                    .zoom(12.0)
                    .build()
            map.setStyle(Style.Builder().fromJson(style))
        }

        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    OsmCredit(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Spacing.tight))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        mapView?.onDestroy()
        super.onDestroy()
    }
}

private const val TAG = "KozicaGate"

@Composable
private fun MissingArchive() {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(Spacing.base), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.debug_map_no_archive), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun OsmCredit(modifier: Modifier = Modifier) {
    val credit = stringResource(R.string.debug_osm_credit)
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), modifier = modifier) {
        Text(
            text =
                buildAnnotatedString {
                    withLink(
                        LinkAnnotation.Url(
                            "https://www.openstreetmap.org/copyright",
                            TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)),
                        ),
                    ) { append(credit) }
                },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.tight, vertical = Spacing.hair),
        )
    }
}
