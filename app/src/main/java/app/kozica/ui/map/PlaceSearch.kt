package app.kozica.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.kozica.R
import app.kozica.data.regions.REGION_NAMES
import app.kozica.data.routes.Place
import app.kozica.data.routes.PlaceKind
import app.kozica.theme.Spacing

/** docs/phase-5.md § 4's search field, disabled with a reason in a build without the name index. */
@Composable
internal fun SearchField(
    text: String,
    available: Boolean?,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onText,
        enabled = available == true,
        singleLine = true,
        placeholder = {
            Text(stringResource(if (available == false) R.string.map_planning_missing else R.string.map_search))
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onText("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.map_search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results follow the text as it is typed, so the key's work is only to clear the keyboard off them.
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        modifier = modifier,
    )
}

@Composable
internal fun SearchResults(
    results: List<Place>,
    onPick: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, modifier = modifier) {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(results, key = { it.id }) { place ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(place) }
                        .padding(horizontal = Spacing.base, vertical = Spacing.tight),
                ) {
                    PlaceLines(place)
                }
            }
        }
    }
}

/** The place offered as a point: added as the start of an empty Route, or else as its new end. */
@Composable
internal fun OfferedPlace(
    place: Place,
    first: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
        ) {
            Column(Modifier.weight(1f)) { PlaceLines(place) }
            FilledTonalButton(onClick = onAdd) {
                Text(stringResource(if (first) R.string.route_start else R.string.route_end))
            }
        }
    }
}

/** Its local name, the name that matched where that differs, then its kind and Region. */
@Composable
private fun PlaceLines(place: Place) {
    Text(place.name, style = MaterialTheme.typography.bodyLarge)
    if (place.matched != place.name) {
        Text(place.matched, style = MaterialTheme.typography.bodyMedium)
    }
    val region = REGION_NAMES[place.region]?.let { stringResource(it) }
    Text(
        listOfNotNull(stringResource(place.kind.label), region).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@get:StringRes
private val PlaceKind.label: Int
    get() =
        when (this) {
            PlaceKind.PEAK -> R.string.place_peak
            PlaceKind.PASS -> R.string.place_pass
            PlaceKind.HUT -> R.string.place_hut
            PlaceKind.SHELTER -> R.string.place_shelter
            PlaceKind.VIEWPOINT -> R.string.place_viewpoint
            PlaceKind.TOWN -> R.string.place_town
            PlaceKind.VILLAGE -> R.string.place_village
            PlaceKind.HAMLET -> R.string.place_hamlet
            PlaceKind.LAKE -> R.string.place_lake
            PlaceKind.CLEARING -> R.string.place_clearing
            PlaceKind.PLACE -> R.string.place_place
        }
