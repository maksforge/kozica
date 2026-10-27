package app.kozica.ui.hikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.map.HikeUiState
import app.kozica.ui.map.Profile
import app.kozica.ui.map.RouteNameDialog
import app.kozica.ui.map.length
import app.kozica.ui.map.short
import java.time.Duration
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/** docs/phase-6.md § 4: a Hike's totals and profile under its Track on the map, and what can be done to it. */
@Composable
internal fun HikeSheet(
    hike: HikeUiState,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
    onStartTrim: () -> Unit,
    onTrimChange: (ClosedFloatingPointRange<Double>) -> Unit,
    onCancelTrim: () -> Unit,
    onTrim: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var renaming by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var confirmingTrim by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val keep = hike.keep
    Surface(
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.base)
                .padding(bottom = Spacing.base),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
                Column(Modifier.weight(1f)) {
                    Text(hike.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        hikeDate(hike.startedAt, locale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.hike_close))
                }
            }
            if (keep == null) {
                Totals(hike, locale)
            } else {
                Text(
                    stringResource(R.string.hike_trim_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Spacing.tight),
                )
            }
            if (hike.profile.size >= 2) {
                Profile(hike.profile, locale, Modifier.padding(top = Spacing.base), kept = keep)
            }
            val total = hike.along.lastOrNull() ?: 0.0
            if (keep != null && total > 0) {
                RangeSlider(
                    value = keep.start.toFloat()..keep.endInclusive.toFloat(),
                    onValueChange = { onTrimChange(it.start.toDouble()..it.endInclusive.toDouble()) },
                    valueRange = 0f..total.toFloat(),
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (keep == null) {
                    TextButton(onClick = {
                        draft = hike.name
                        renaming = true
                    }) { Text(stringResource(R.string.routes_rename)) }
                    TextButton(onClick = onStartTrim) { Text(stringResource(R.string.hike_trim)) }
                    TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.hike_delete)) }
                } else {
                    TextButton(onClick = onCancelTrim) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { confirmingTrim = true }, enabled = hike.cuts) {
                        Text(stringResource(R.string.hike_trim))
                    }
                }
            }
        }
    }

    if (renaming) {
        RouteNameDialog(
            title = stringResource(R.string.routes_rename),
            name = draft,
            onNameChange = { draft = it },
            confirm = stringResource(R.string.routes_rename),
            onConfirm = { name ->
                onRename(name)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmingTrim) {
        AlertDialog(
            onDismissRequest = { confirmingTrim = false },
            title = { Text(stringResource(R.string.hike_trim_title)) },
            text = { Text(stringResource(R.string.hike_trim_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onTrim()
                    confirmingTrim = false
                }) { Text(stringResource(R.string.hike_trim)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingTrim = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.hike_delete_title)) },
            text = {
                Text(
                    if (hike.photoCount == 0) {
                        stringResource(R.string.hike_delete_body)
                    } else {
                        pluralStringResource(R.plurals.hike_delete_body_photos, hike.photoCount, hike.photoCount)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onDelete()
                }) { Text(stringResource(R.string.hike_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Totals(
    hike: HikeUiState,
    locale: Locale,
) {
    val climb = hike.climb
    val fromGps = climb.fromGps
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        modifier = Modifier.padding(top = Spacing.tight),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Total(stringResource(R.string.hike_distance), length(hike.metres, locale))
            Total(stringResource(R.string.route_ascent), climbMetres(climb.ascent, fromGps, locale))
            Total(stringResource(R.string.route_descent), climbMetres(climb.descent, fromGps, locale))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.block), modifier = Modifier.fillMaxWidth()) {
            Total(stringResource(R.string.hike_moving_time), hike.movingSeconds.seconds.short(locale))
            Total(
                stringResource(R.string.hike_total_time),
                Duration.between(hike.startedAt, hike.endedAt).toKotlinDuration().short(locale),
            )
        }
        if (fromGps) {
            Text(
                stringResource(R.string.hike_from_gps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
