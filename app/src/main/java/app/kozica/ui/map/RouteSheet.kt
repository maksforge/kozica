package app.kozica.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.data.routes.Approach
import app.kozica.data.routes.PlannedRoute
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.RouteSample
import app.kozica.theme.Spacing
import java.util.Locale

/** docs/phase-5.md § 4: the totals and warnings always, the points, the profile and Reverse on a tap. */
@Composable
internal fun RouteSheet(
    route: RouteUiState,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onReverse: () -> Unit,
    onShowSaveDialog: (Boolean) -> Unit,
    onSave: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Set as the dialog opens, so a draft restored after the Route itself was lost is never shown.
    var draft by rememberSaveable { mutableStateOf("") }
    val planned = route.planned.takeUnless { route.planning }
    val open = expanded || route.points.size < 2
    Surface(
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base).padding(bottom = Spacing.base)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = route.points.size >= 2,
                            onClickLabel =
                                stringResource(
                                    if (expanded) R.string.route_details_hide else R.string.route_details_show,
                                ),
                            role = Role.Button,
                        ) { expanded = !expanded }
                        .padding(vertical = Spacing.base),
            ) {
                Box(
                    Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
            route.name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            when {
                route.unavailable -> Text(stringResource(R.string.map_planning_missing))
                route.points.size < 2 -> Unit
                planned == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> Totals(planned, locale)
            }
            if (route.points.size >= 2) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onReverse) { Text(stringResource(R.string.route_reverse)) }
                    TextButton(onClick = {
                        draft = route.name
                            ?: listOf(route.points.first(), route.points.last()).joinToString(" – ") { it.name() }
                        onShowSaveDialog(true)
                    }) { Text(stringResource(R.string.route_save)) }
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (planned != null) Warnings(planned, route, locale)
                if (open) {
                    route.points.forEachIndexed { i, point ->
                        PointRow(
                            index = i,
                            point = point,
                            count = route.points.size,
                            minutes = planned?.minutesTo(i)?.takeIf { i > 0 },
                            offTrailMetres = planned?.offTrailMetres(i),
                            locale = locale,
                            onRemove = { onRemove(i) },
                            onMove = { to -> onMove(i, to) },
                        )
                    }
                    if (planned != null && planned.metres > 0) {
                        Profile(planned.line, locale, Modifier.padding(top = Spacing.base))
                    }
                }
            }
        }
    }
    if (route.naming) {
        RouteNameDialog(
            title = stringResource(R.string.route_save),
            name = draft,
            onNameChange = { draft = it },
            confirm = stringResource(R.string.route_save),
            onConfirm = onSave,
            onDismiss = { onShowSaveDialog(false) },
        )
    }
}

/** Asks for a Route's name, for Save and for Rename; a blank name cannot be confirmed. */
@Composable
internal fun RouteNameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.route_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Totals(
    planned: PlannedRoute,
    locale: Locale,
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Total(stringResource(R.string.route_length), length(planned.metres, locale))
        Total(stringResource(R.string.route_ascent), wholeMetres(planned.ascent, locale))
        Total(stringResource(R.string.route_descent), wholeMetres(planned.descent, locale))
        Total(stringResource(R.string.route_walking_time), walkingTime(planned.minutes, locale))
    }
}

@Composable
private fun Total(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Warnings(
    planned: PlannedRoute,
    route: RouteUiState,
    locale: Locale,
) {
    val lines =
        planned.legs.mapIndexedNotNull { i, leg ->
            if (leg != null) {
                null
            } else {
                stringResource(
                    R.string.route_no_way,
                    route.points[i].name(),
                    route.points[i + 1].name(),
                )
            }
        } +
            route.stretches.map { named ->
                val name =
                    listOfNotNull(named.from, named.to).distinct().joinToString(" – ").ifEmpty {
                        "${length(named.stretch.fromMetres, locale)} – ${length(named.stretch.toMetres, locale)}"
                    }
                stringResource(R.string.route_demanding, name)
            }
    for (line in lines) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.tight))
        }
    }
}

@Composable
private fun PointRow(
    index: Int,
    point: RoutePoint,
    count: Int,
    minutes: Int?,
    offTrailMetres: Double?,
    locale: Locale,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Text(
                "${index + 1}",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.tight)) {
            val name = point.name()
            when (index) {
                0 -> PointRole(stringResource(R.string.route_start))
                count - 1 -> PointRole(stringResource(R.string.route_end))
            }
            Text(
                if (index == 0 || index == count - 1) name else stringResource(R.string.route_via, name),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (minutes != null) Text(walkingTime(minutes, locale), style = MaterialTheme.typography.bodyMedium)
            if (offTrailMetres != null && offTrailMetres > OFF_TRAIL_SHOWN_METRES) {
                Text(
                    stringResource(R.string.route_off_trail, distance(offTrailMetres, locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onMove(index - 1) }, enabled = index > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.route_move_up))
        }
        IconButton(onClick = { onMove(index + 1) }, enabled = index < count - 1) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.route_move_down))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.route_remove))
        }
    }
}

@Composable
private fun PointRole(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Height against distance, broken across a leg with no way. */
@Composable
private fun Profile(
    line: List<RouteSample>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val low = line.minOf { it.heightMetres }
    val high = line.maxOf { it.heightMetres }
    val stroke = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(wholeMetres(high, locale), style = MaterialTheme.typography.labelSmall)
            Text(length(line.last().metres, locale), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val total = line.last().metres.coerceAtLeast(1.0)
            val span = (high - low).coerceAtLeast(PROFILE_MIN_SPAN_METRES)

            fun at(sample: RouteSample) =
                Offset(
                    (sample.metres / total * size.width).toFloat(),
                    (size.height - (sample.heightMetres - low) / span * size.height).toFloat(),
                )
            val outline = Path()
            val area = Path()
            for ((i, sample) in line.withIndex()) {
                val point = at(sample)
                if (i == 0 || sample.approach == Approach.NO_WAY) {
                    if (i > 0) area.lineTo(at(line[i - 1]).x, size.height)
                    outline.moveTo(point.x, point.y)
                    area.moveTo(point.x, size.height)
                    area.lineTo(point.x, point.y)
                } else {
                    outline.lineTo(point.x, point.y)
                    area.lineTo(point.x, point.y)
                }
            }
            area.lineTo(at(line.last()).x, size.height)
            area.close()
            drawPath(area, fill)
            drawPath(outline, stroke, style = Stroke(width = 2.dp.toPx()))
        }
        Text(wholeMetres(low, locale), style = MaterialTheme.typography.labelSmall)
    }
}

/** A point's place name, or its position where no place is near it. */
private fun RoutePoint.name(): String = label ?: String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude)

/** How far point [i] is from where the Route joins the Trails, by the leg leaving it or else the one reaching it. */
private fun PlannedRoute.offTrailMetres(i: Int): Double? =
    legs.getOrNull(i)?.start?.metresAway ?: legs.getOrNull(i - 1)?.end?.metresAway

private const val OFF_TRAIL_SHOWN_METRES = 50.0
private const val PROFILE_MIN_SPAN_METRES = 50.0
