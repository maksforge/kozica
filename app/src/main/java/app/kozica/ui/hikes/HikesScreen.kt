package app.kozica.ui.hikes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.HikeSummary
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.map.length
import app.kozica.ui.map.short
import java.time.Duration
import kotlin.time.toKotlinDuration

/** The Hikes tab (docs/phase-6.md § 4): newest first; one opens on the map. */
@Composable
fun HikesScreen(
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HikesViewModel = viewModel(factory = HikesViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = state.hikes

    DetailScaffold(title = stringResource(R.string.hikes_title), onBack = null, modifier = modifier) { content ->
        when {
            rows == null -> Unit
            rows.isEmpty() ->
                Box(content.fillMaxSize().padding(Spacing.base), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.hikes_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else ->
                LazyColumn(content.fillMaxSize()) {
                    items(rows, key = { it.id }) { hike ->
                        HikeRow(
                            hike = hike,
                            onShow = {
                                viewModel.show(hike.id)
                                onShow()
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun HikeRow(
    hike: HikeSummary,
    onShow: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val facts =
        listOfNotNull(
            hikeDate(hike.startedAt, locale),
            length(hike.metres, locale),
            hike.endedAt?.let { Duration.between(hike.startedAt, it).toKotlinDuration().short(locale) },
            hike.ascentMetres?.let { "↑ ${climbMetres(it, hike.ascentFromGps, locale)}" },
        )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onShow)
            .padding(horizontal = Spacing.base, vertical = Spacing.tight),
    ) {
        Text(hike.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            facts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
