package app.kozica.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import app.kozica.data.regions.RegionFiles
import app.kozica.data.regions.RegionState
import app.kozica.theme.Spacing
import app.kozica.ui.common.SectionHeader
import kotlinx.coroutines.flow.map
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
    val regions = remember { RegionFiles.of(context) }
    val tatry by remember { regions.states.map { all -> all.first { it.id == TATRY }.state } }
        .collectAsStateWithLifecycle(initialValue = null)
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    SectionHeader(stringResource(R.string.debug_section))
    DebugRow(stringResource(R.string.debug_seed)) {
        scope.launch { SampleData.seed(app.container.items) }
    }
    DebugRow(stringResource(R.string.debug_wipe)) {
        scope.launch { SampleData.wipe(app.container.items) }
    }
    Text(
        text = tatryState(tatry),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.tight),
    )
    DebugRow(stringResource(R.string.debug_tatry_download)) {
        if (tatry == RegionState.WaitingForWifi || tatry == RegionState.NeedsConfirmation) {
            regions.confirm(confirmation)
        } else {
            scope.launch { regions.download(listOf(TATRY)) }
        }
    }
    DebugRow(stringResource(R.string.debug_tatry_remove)) {
        scope.launch { regions.remove(TATRY) }
    }
    DebugRow(stringResource(R.string.debug_position_real), onClick = ::clearPositionOverride)
}

@Composable
private fun tatryState(state: RegionState?): String =
    when (state) {
        null -> stringResource(R.string.debug_tatry_reading)
        RegionState.NotDownloaded -> stringResource(R.string.debug_tatry_not_downloaded)
        is RegionState.Downloading -> stringResource(R.string.debug_tatry_downloading, state.percent)
        is RegionState.UpdateInProgress -> stringResource(R.string.debug_tatry_updating, state.percent)
        RegionState.WaitingForWifi -> stringResource(R.string.debug_tatry_waiting)
        RegionState.NeedsConfirmation -> stringResource(R.string.debug_tatry_confirm)
        is RegionState.Ready ->
            stringResource(
                R.string.debug_tatry_ready,
                state.osmDate?.toString() ?: stringResource(R.string.debug_tatry_no_date),
            )
        is RegionState.Failed -> stringResource(R.string.debug_tatry_failed, state.errorCode)
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

private const val TATRY = "tatry"
