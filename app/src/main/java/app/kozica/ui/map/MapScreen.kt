package app.kozica.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Home: the map, drawn by MapLibre from the Regions on the phone (docs/phase-2.md § 2). */
@Composable
fun MapScreen(
    onOpenSettings: () -> Unit,
    onOpenSafety: () -> Unit,
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
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
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
    var loaded by remember { mutableStateOf<Style?>(null) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    LaunchedEffect(map) {
        val map = map ?: return@LaunchedEffect
        map.addOnMapLongClickListener {
            viewModel.addPoint(it.latitude, it.longitude)
            true
        }
        map.addOnMapClickListener {
            viewModel.dismissOffer()
            focus.clearFocus()
            false
        }
        val start = shown ?: viewModel.state.mapNotNull { it.camera }.first()
        map.moveCamera(start.toCameraUpdate())
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

    LaunchedEffect(map, state.style) {
        val map = map ?: return@LaunchedEffect
        val style = state.style ?: return@LaunchedEffect
        loaded = null
        map.setStyle(Style.Builder().fromJson(style)) { ready ->
            loaded = ready
            if (!map.locationComponent.isLocationComponentActivated) {
                map.locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions
                        .Builder(context, ready)
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

    val fade = remember(state.style) { state.style?.let(::fadeOf) }
    LaunchedEffect(loaded, state.route) {
        val style = loaded ?: return@LaunchedEffect
        (
            style.getSource(
                ROUTE_SOURCE,
            ) as? GeoJsonSource
        )?.setGeoJson(routeGeoJson(state.route.points, state.route.planned))
        val opacity = if (state.route.planned != null) fade?.opacity ?: 1f else 1f
        for (id in fade?.layers.orEmpty()) {
            when (val layer = style.getLayer(id)) {
                is LineLayer -> layer.setProperties(PropertyFactory.lineOpacity(opacity))
                is SymbolLayer ->
                    layer.setProperties(
                        PropertyFactory.iconOpacity(opacity),
                        PropertyFactory.textOpacity(opacity),
                    )
            }
        }
    }

    val planned = state.route.planned
    // Clear of the search row above and the position button and credit below.
    val sidePadding = with(LocalDensity.current) { Spacing.block.roundToPx() }
    val topPadding = sidePadding * 3
    val bottomPadding = sidePadding * 5
    // Each return to the map brings a new MapLibreMap, and these two would otherwise move the camera
    // away from where the user left it; so each Route is fitted to, and each place centred on, once.
    var fitted by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(map, planned) {
        if (planned == null) fitted = null
        val map = map ?: return@LaunchedEffect
        val line = planned?.line?.takeIf { it.size >= 2 } ?: return@LaunchedEffect
        val route = planned.hashCode()
        if (route == fitted) return@LaunchedEffect
        fitted = route
        val bounds =
            LatLngBounds
                .Builder()
                .includes(line.map { LatLng(it.latitude, it.longitude) })
                .build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, sidePadding, topPadding, sidePadding, bottomPadding),
        )
    }

    val offered = state.search.offered
    var centred by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(map, offered) {
        if (offered == null) centred = null
        val map = map ?: return@LaunchedEffect
        val place = offered ?: return@LaunchedEffect
        if (place.id == centred) return@LaunchedEffect
        centred = place.id
        val zoom = maxOf(map.cameraPosition.zoom, OFFERED_ZOOM)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), zoom))
    }
    BackHandler(enabled = offered != null, onBack = viewModel::dismissOffer)

    LaunchedEffect(map, centreWanted, state.fix) {
        val map = map ?: return@LaunchedEffect
        val fix = state.fix ?: return@LaunchedEffect
        if (!centreWanted) return@LaunchedEffect
        centreWanted = false
        map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(fix.latitude, fix.longitude)))
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // A share of the height rather than a fixed one, so landscape and a large font still leave the map.
        val sheetHeight = maxHeight / 2
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                // Above the position button, which search results reach down over.
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(Spacing.tight),
                    ) {
                        SearchField(
                            text = searchText,
                            available = state.search.available,
                            onText = {
                                searchText = it
                                viewModel.search(it)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = onOpenSafety) {
                            Icon(
                                painterResource(R.drawable.ic_emergency),
                                contentDescription = stringResource(R.string.safety_title),
                            )
                        }
                        FilledTonalIconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    }
                    if (searchText.isNotBlank() && state.search.results.isNotEmpty()) {
                        SearchResults(
                            results = state.search.results,
                            onPick = {
                                searchText = ""
                                focus.clearFocus()
                                viewModel.pick(it)
                            },
                            modifier = Modifier.padding(horizontal = Spacing.tight),
                        )
                    }
                    offered?.let {
                        OfferedPlace(
                            place = it,
                            first = state.route.points.isEmpty(),
                            onAdd = viewModel::addOffered,
                            modifier = Modifier.padding(horizontal = Spacing.tight),
                        )
                    }
                    state.progress?.let {
                        ProgressLine(
                            progress = it,
                            onRetry = viewModel::retry,
                            onConfirm = { viewModel.confirm(confirmation) },
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = Spacing.base),
                        )
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
                    OsmCredit(state.osmDate, relief = state.reliefShown)
                }
            }
            if (state.route.points.isNotEmpty()) {
                RouteSheet(
                    route = state.route,
                    onRemove = viewModel::removePoint,
                    onMove = viewModel::movePoint,
                    onReverse = viewModel::reverse,
                    modifier = Modifier.heightIn(max = sheetHeight),
                )
            }
        }
    }
}

@Composable
private fun rememberMapView(onStop: () -> Unit): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) }.also(::logFrames) }
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

@Composable
private fun ProgressLine(
    progress: MapProgress,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapLabel(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
        ) {
            Text(
                text =
                    when (progress) {
                        is MapProgress.Downloading ->
                            stringResource(
                                R.string.map_downloading,
                                progress.ready,
                                progress.total,
                            )
                        MapProgress.WaitingForWifi -> stringResource(R.string.map_waiting_for_wifi)
                        MapProgress.NeedsConfirmation -> stringResource(R.string.map_needs_confirmation)
                        is MapProgress.Failed -> stringResource(R.string.map_download_failed)
                        MapProgress.Updating -> stringResource(R.string.map_updating)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
            when (progress) {
                MapProgress.WaitingForWifi ->
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.map_mobile_data)) }
                MapProgress.NeedsConfirmation ->
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.map_confirm)) }
                is MapProgress.Failed ->
                    TextButton(onClick = onRetry) {
                        Text(
                            progress.size?.let {
                                stringResource(
                                    R.string.map_retry_sized,
                                    Formatter.formatShortFileSize(LocalContext.current, it),
                                )
                            }
                                ?: stringResource(R.string.map_retry),
                        )
                    }
                is MapProgress.Downloading, MapProgress.Updating -> Unit
            }
        }
    }
}

@Composable
private fun OsmCredit(
    osmDate: Instant?,
    relief: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val credit = stringResource(R.string.map_osm_credit)
    val reliefCredit = stringResource(R.string.map_relief_credit, RELIEF_SOURCES)
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
                        if (relief) append(" · $reliefCredit")
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

/** Close enough that the Trails around a picked place are drawn. */
private const val OFFERED_ZOOM = 13.0

private const val OSM_COPYRIGHT = "https://www.openstreetmap.org/copyright"

/** The relief's three agencies, by the names their licences ask for (docs/phase-4.md § 3). */
private const val RELIEF_SOURCES = "GUGiK, ÚGKK SR, ČÚZK"

private fun MapCamera.toCameraUpdate(): CameraUpdate =
    CameraUpdateFactory.newCameraPosition(
        CameraPosition
            .Builder()
            .target(LatLng(latitude, longitude))
            .zoom(zoom)
            .build(),
    )

private fun CameraPosition.toMapCamera(): MapCamera? = target?.let { MapCamera(it.latitude, it.longitude, zoom) }
