package app.kozica.ui.map

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.MapCamera
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.startActivitySafely
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Home: the map, drawn by MapLibre from the Regions on the phone (docs/phase-2.md § 2). */
@Composable
fun MapScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Saved state rather than the ViewModel's, so the camera also survives process death.
    var shown by rememberSaveable(stateSaver = CameraSaver) { mutableStateOf<MapCamera?>(null) }
    val mapView = rememberMapView(onStop = viewModel::storeCamera)
    val map by produceState<MapLibreMap?>(null, mapView) {
        mapView.getMapAsync { map ->
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
            value = map
        }
    }

    LaunchedEffect(map) {
        val map = map ?: return@LaunchedEffect
        val start = shown?.let(MapStart::At) ?: viewModel.state.mapNotNull { it.camera }.first()
        map.moveCamera(start.toCameraUpdate())
        map.addOnCameraIdleListener {
            map.cameraPosition.toMapCamera()?.let { camera ->
                shown = camera
                viewModel.cameraIdle(camera)
            }
        }
    }

    LaunchedEffect(map, state.style) {
        val map = map ?: return@LaunchedEffect
        val style = state.style ?: return@LaunchedEffect
        map.setStyle(Style.Builder().fromJson(style))
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        state.empty?.let { EmptyLine(it, Modifier.align(Alignment.Center).padding(Spacing.base)) }
        FilledTonalIconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.tight),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
        }
        OsmCredit(state.osmDate, Modifier.align(Alignment.BottomEnd).padding(Spacing.tight))
    }
}

@Composable
private fun rememberMapView(onStop: () -> Unit): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnStop by rememberUpdatedState(onStop)
    DisposableEffect(lifecycle, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> {
                        mapView.onStop()
                        currentOnStop()
                    }
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

@Composable
private fun EmptyLine(
    empty: MapEmpty,
    modifier: Modifier = Modifier,
) {
    MapLabel(modifier) {
        Text(
            text =
                stringResource(
                    when (empty) {
                        MapEmpty.NO_REGIONS -> R.string.map_no_regions
                        MapEmpty.UPDATING -> R.string.map_updating
                    },
                ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
        )
    }
}

@Composable
private fun OsmCredit(
    osmDate: Instant?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val credit = stringResource(R.string.map_osm_credit)
    val locale = LocalConfiguration.current.locales[0]
    MapLabel(modifier) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = Spacing.tight, vertical = Spacing.hair),
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = OSM_COPYRIGHT,
                                styles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)),
                            ) { context.startActivitySafely(Intent(Intent.ACTION_VIEW, OSM_COPYRIGHT.toUri())) },
                        ) { append(credit) }
                    },
                style = MaterialTheme.typography.labelSmall,
            )
            if (osmDate != null) {
                val date =
                    DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(locale)
                        .withZone(ZoneId.systemDefault())
                        .format(osmDate)
                Text(stringResource(R.string.map_osm_date, date), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** The style's colours are not the theme's until phase 2's C, so text sits on a surface of its own. */
@Composable
private fun MapLabel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        content = content,
    )
}

private val CameraSaver =
    Saver<MapCamera?, DoubleArray>(
        save = { camera -> camera?.let { doubleArrayOf(it.latitude, it.longitude, it.zoom) } },
        restore = { (latitude, longitude, zoom) -> MapCamera(latitude, longitude, zoom) },
    )

private const val OSM_COPYRIGHT = "https://www.openstreetmap.org/copyright"

private fun MapStart.toCameraUpdate(): CameraUpdate =
    when (this) {
        is MapStart.At ->
            CameraUpdateFactory.newCameraPosition(
                CameraPosition
                    .Builder()
                    .target(LatLng(camera.latitude, camera.longitude))
                    .zoom(camera.zoom)
                    .build(),
            )
        is MapStart.Fit ->
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                0,
            )
    }

private fun CameraPosition.toMapCamera(): MapCamera? = target?.let { MapCamera(it.latitude, it.longitude, zoom) }
