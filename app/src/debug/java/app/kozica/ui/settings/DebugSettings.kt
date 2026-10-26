package app.kozica.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kozica.MainApplication
import app.kozica.R
import app.kozica.data.SampleData
import app.kozica.data.location.clearPositionOverride
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionFiles
import app.kozica.data.regions.RegionState
import app.kozica.theme.Spacing
import app.kozica.ui.common.SectionHeader
import kotlinx.coroutines.launch

/**
 * The developer-only section of Settings. **This file exists only in the debug source set** — see
 * the release half for why that is a source set and not a `BuildConfig.DEBUG` branch.
 *
 * Add things here freely: a sample-data seeder, a schema-version stamp. None of it reaches a release
 * binary, and none of its strings reach the translation gate.
 */
@Composable
fun DebugSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as MainApplication
    val regions by remember { RegionFiles.of(context).states }.collectAsStateWithLifecycle(initialValue = null)

    SectionHeader(stringResource(R.string.debug_section))
    DebugRow(stringResource(R.string.debug_seed)) {
        scope.launch { SampleData.seed(app.container.routes) }
    }
    DebugRow(stringResource(R.string.debug_wipe)) {
        scope.launch { SampleData.wipe(app.container.routes) }
    }
    regions?.forEach { PackState(packState(it)) } ?: PackState(stringResource(R.string.debug_region_reading))
    DebugRow(stringResource(R.string.debug_position_real), onClick = ::clearPositionOverride)
}

@Composable
private fun packState(region: Region): String {
    val name = stringResource(region.name)
    return when (val state = region.state) {
        RegionState.NotDownloaded -> stringResource(R.string.debug_region_not_downloaded, name)
        is RegionState.Downloading -> stringResource(R.string.debug_region_downloading, name, state.percent)
        is RegionState.UpdateInProgress -> stringResource(R.string.debug_region_updating, name, state.percent)
        RegionState.WaitingForWifi -> stringResource(R.string.debug_region_waiting, name)
        RegionState.NeedsConfirmation -> stringResource(R.string.debug_region_confirm, name)
        is RegionState.Ready ->
            stringResource(
                R.string.debug_region_ready,
                name,
                state.osmDate?.toString() ?: stringResource(R.string.debug_region_no_date),
            )
        is RegionState.Failed -> stringResource(R.string.debug_region_failed, name, state.errorCode)
    }
}

@Composable
private fun PackState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.hair),
    )
}

@Composable
private fun DebugRow(
    title: String,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.base, vertical = Spacing.base),
    )
}
