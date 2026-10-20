package app.kozica.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.MapCamera
import app.kozica.data.location.overridePosition
import app.kozica.theme.LocalDarkTheme
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.WarningBanner
import app.kozica.ui.common.osmDateLine
import app.kozica.ui.common.startActivitySafely
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Home: the map, drawn by MapLibre from the Regions on the phone (docs/phase-2.md § 2). */
@Composable
fun MapScreen(
    onOpenSettings: () -> Unit,
    onOpenRegions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    LaunchedEffect(dark) { viewModel.darkTheme(dark) }
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

    // "Denied" and "not asked" read the same until a denial comes back (docs/phase-2.md § 4).
    var denied by rememberSaveable { mutableStateOf(false) }
    var preciseRefused by rememberSaveable { mutableStateOf(false) }
    var askingForPrecise by remember { mutableStateOf(false) }
    var centreWanted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.locationRead(context.readLocation())
        onPauseOrDispose {}
    }
    // The quick-settings tile flips the switch without pausing the activity.
    LifecycleStartEffect(Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = viewModel.locationRead(context.readLocation())
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val read = context.readLocation()
            viewModel.locationRead(read)
            if (read.grant == LocationGrant.NONE) denied = true
            if (askingForPrecise && read.grant == LocationGrant.APPROXIMATE) preciseRefused = true
            askingForPrecise = false
        }
    val showPosition: () -> Unit = {
        val read = state.location
        when {
            read == null -> Unit
            read.grant == LocationGrant.NONE ->
                if (!denied || activity.mayAskAgain()) {
                    centreWanted = true
                    ask.launch(LOCATION_PERMISSIONS)
                } else {
                    context.startActivitySafely(context.appSettings())
                }
            !read.on -> context.startActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> centreWanted = true
        }
    }
    val approximateOnly = state.location?.grant == LocationGrant.APPROXIMATE
    val age = rememberPositionAge(state.fix, approximateOnly)
    var dotActivated by remember { mutableStateOf(false) }
    var cameraPlaced by remember { mutableStateOf(false) }

    LaunchedEffect(map) {
        val map = map ?: return@LaunchedEffect
        map.addOnMapLongClickListener { overridePosition(it.latitude, it.longitude) }
        val start = shown?.let(MapStart::At) ?: viewModel.state.mapNotNull { it.camera }.first()
        map.moveCamera(start.toCameraUpdate())
        cameraPlaced = true
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) centreWanted = false
        }
        map.addOnCameraIdleListener {
            map.cameraPosition.toMapCamera()?.let { camera ->
                shown = camera
                viewModel.cameraIdle(camera)
            }
        }
    }

    LaunchedEffect(map, cameraPlaced, state.arrival) {
        val map = map?.takeIf { cameraPlaced } ?: return@LaunchedEffect
        val arrival = state.arrival ?: return@LaunchedEffect
        map.animateCamera(MapStart.Fit(arrival).toCameraUpdate())
        viewModel.arrivalShown(arrival)
    }

    LaunchedEffect(map, state.style) {
        val map = map ?: return@LaunchedEffect
        val style = state.style ?: return@LaunchedEffect
        map.setStyle(Style.Builder().fromJson(style)) { loaded ->
            if (!map.locationComponent.isLocationComponentActivated) {
                map.locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions
                        .Builder(context, loaded)
                        .useDefaultLocationEngine(false)
                        .locationComponentOptions(dotOptions(context, colors, stale = false))
                        .build(),
                )
                // A fix fed before the component's first frame is dropped, and approximate only
                // sends the next one ten minutes later.
                mapView.addOnDidFinishRenderingFrameListener(
                    object : MapView.OnDidFinishRenderingFrameListener {
                        override fun onDidFinishRenderingFrame(
                            fully: Boolean,
                            frameEncodingTime: Double,
                            frameRenderingTime: Double,
                        ) {
                            if (!fully) return
                            mapView.removeOnDidFinishRenderingFrameListener(this)
                            dotActivated = true
                        }
                    },
                )
            }
        }
    }

    val stale = age is PositionAge.Stale
    val hasFix = state.fix != null
    LaunchedEffect(map, dotActivated, hasFix, approximateOnly, stale, colors) {
        val component = map?.locationComponent?.takeIf { dotActivated } ?: return@LaunchedEffect
        component.show(hasFix)
        if (!hasFix) return@LaunchedEffect
        component.applyStyle(dotOptions(context, colors, stale))
        component.renderMode = if (approximateOnly) RenderMode.NORMAL else RenderMode.COMPASS
    }

    LaunchedEffect(map, dotActivated, state.fix) {
        val component = map?.locationComponent?.takeIf { dotActivated } ?: return@LaunchedEffect
        state.fix?.let(component::forceLocationUpdate)
    }

    LaunchedEffect(map, centreWanted, state.fix) {
        val map = map ?: return@LaunchedEffect
        val fix = state.fix ?: return@LaunchedEffect
        if (!centreWanted) return@LaunchedEffect
        centreWanted = false
        map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(fix.latitude, fix.longitude)))
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        state.empty?.let { EmptyLine(it, onOpenRegions, Modifier.align(Alignment.Center).padding(Spacing.base)) }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                modifier = Modifier.align(Alignment.End).padding(Spacing.tight),
            ) {
                FilledTonalIconButton(onClick = onOpenRegions) {
                    Icon(
                        painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.regions_title),
                    )
                }
                FilledTonalIconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
            LocationBanner(
                read = state.location,
                age = age,
                preciseRefused = preciseRefused,
                onAllowPrecise = {
                    askingForPrecise = true
                    ask.launch(LOCATION_PERMISSIONS)
                },
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.tight),
        ) {
            FilledTonalIconButton(onClick = showPosition) {
                Icon(
                    painterResource(R.drawable.ic_my_location),
                    contentDescription = stringResource(R.string.map_position_show),
                )
            }
            OsmCredit(state.osmDate)
        }
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
            // Leaving Home for another entry disposes it with no ON_STOP, so the stop is this.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
                currentOnStop()
            }
            mapView.onDestroy()
        }
    }
    return mapView
}

/** Every row of docs/phase-2.md § 4's table but "not asked" and "denied", which show only the button. */
@Composable
private fun LocationBanner(
    read: LocationRead?,
    age: PositionAge?,
    preciseRefused: Boolean,
    onAllowPrecise: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val staleLine =
        (age as? PositionAge.Stale)?.age?.let {
            stringResource(
                R.string.map_position_stale,
                it.short(locale),
            )
        }
    val settings = stringResource(R.string.map_location_settings)
    when {
        read == null || read.grant == LocationGrant.NONE -> Unit
        !read.on ->
            WarningBanner(
                title = stringResource(R.string.map_location_off),
                actionLabel = settings,
                onAct = { context.startActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )
        read.grant == LocationGrant.APPROXIMATE ->
            WarningBanner(
                title = stringResource(R.string.map_location_approximate),
                body = listOfNotNull(stringResource(R.string.map_location_approximate_body), staleLine),
                actionLabel = if (preciseRefused) settings else stringResource(R.string.map_location_allow),
                onAct = {
                    if (preciseRefused) {
                        context.startActivitySafely(
                            context.appSettings(),
                        )
                    } else {
                        onAllowPrecise()
                    }
                },
            )
        staleLine != null -> WarningBanner(title = staleLine)
    }
}

/** Re-read every second, so a fix that stops arriving goes stale on screen. */
@Composable
private fun rememberPositionAge(
    fix: Location?,
    approximateOnly: Boolean,
): PositionAge? {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(fix) {
        while (fix != null) {
            delay(1.seconds)
            tick++
        }
    }
    return remember(fix, approximateOnly, tick) {
        fix?.let { positionAge(it.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos(), approximateOnly) }
    }
}

// Its own stale state is off: it never reads the fix's age, so PositionAge greys the dot instead.
private fun dotOptions(
    context: Context,
    colors: ColorScheme,
    stale: Boolean,
): LocationComponentOptions {
    val tint = (if (stale) colors.outline else colors.primary).toArgb()
    return LocationComponentOptions
        .builder(context)
        .enableStaleState(false)
        .foregroundTintColor(tint)
        .bearingTintColor(tint)
        .accuracyColor(tint)
        .backgroundTintColor(colors.surface.toArgb())
        .build()
}

@SuppressLint("MissingPermission")
private fun LocationComponent.show(shown: Boolean) {
    isLocationComponentEnabled = shown
}

private fun android.app.Activity?.mayAskAgain(): Boolean =
    this != null && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)

private fun Context.appSettings() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Short units, so the platform's abbreviation sidesteps the case the host sentence would need. */
private fun Duration.short(locale: Locale): String =
    toComponents { hours, minutes, seconds, _ ->
        val measures =
            buildList {
                if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
                if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
                if (isEmpty()) add(Measure(seconds, MeasureUnit.SECOND))
            }
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures.toTypedArray())
    }

@Composable
private fun EmptyLine(
    empty: MapEmpty,
    onOpenRegions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapLabel(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
        ) {
            Text(
                text =
                    stringResource(
                        when (empty) {
                            MapEmpty.NO_REGIONS -> R.string.map_no_regions
                            MapEmpty.UPDATING -> R.string.map_updating
                        },
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (empty == MapEmpty.NO_REGIONS) {
                TextButton(onClick = onOpenRegions) { Text(stringResource(R.string.map_choose_regions)) }
            }
        }
    }
}

@Composable
private fun OsmCredit(
    osmDate: Instant?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val credit = stringResource(R.string.map_osm_credit)
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
                Text(osmDateLine(osmDate), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Text laid straight over Trails and map labels is unreadable, so it sits on a surface of its own. */
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
