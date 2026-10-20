package app.kozica.ui.regions

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.regions.Region
import app.kozica.data.regions.RegionState
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.osmDateLine

/** Every Region, to download or remove (docs/phase-2b.md § 5). */
@Composable
fun RegionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegionsViewModel = viewModel(factory = RegionsViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }

    DetailScaffold(
        title = stringResource(R.string.regions_title),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        LazyColumn(modifier = contentModifier) {
            if (state.toFetch.isNotEmpty()) {
                item {
                    FilledTonalButton(
                        onClick = viewModel::downloadAll,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.tight),
                    ) { Text(downloadAllLabel(state.toFetch.size, state.toFetchSize)) }
                }
            }
            items(state.regions.orEmpty(), key = { it.id }) { region ->
                RegionRow(
                    region = region,
                    onDownload = { viewModel.download(listOf(region.id)) },
                    onConfirm = { viewModel.confirm(region.id, confirmation) },
                    onRemove = { removing = region.id },
                )
            }
        }
    }

    state.regions?.find { it.id == removing }?.let { region ->
        RemoveDialog(
            region = region,
            onRemove = {
                viewModel.remove(region.id)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
internal fun downloadAllLabel(
    count: Int,
    bytes: Long?,
): String =
    if (bytes == null) {
        pluralStringResource(R.plurals.regions_download_all_unsized, count, count)
    } else {
        pluralStringResource(R.plurals.regions_download_all, count, count, fileSize(bytes))
    }

@Composable
private fun RegionRow(
    region: Region,
    onDownload: () -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
) {
    val line = stateLine(region)
    val action: Pair<Int, () -> Unit>? =
        when (region.state) {
            RegionState.NotDownloaded -> R.string.regions_download to onDownload
            is RegionState.Failed -> R.string.regions_retry to onDownload
            RegionState.WaitingForWifi -> R.string.regions_mobile_data to onConfirm
            RegionState.NeedsConfirmation -> R.string.regions_confirm to onConfirm
            is RegionState.Ready -> R.string.regions_remove to onRemove
            is RegionState.Downloading, is RegionState.UpdateInProgress -> null
        }
    ListItem(
        headlineContent = { Text(stringResource(region.name)) },
        supportingContent = line?.let { { Text(it) } },
        trailingContent =
            action?.let { (label, onClick) ->
                { TextButton(onClick = onClick) { Text(stringResource(label)) } }
            },
    )
}

@Composable
internal fun stateLine(region: Region): String? =
    when (val state = region.state) {
        RegionState.NotDownloaded -> region.downloadSize?.let { fileSize(it) }
        is RegionState.Downloading -> stringResource(R.string.regions_downloading, state.percent)
        is RegionState.UpdateInProgress -> stringResource(R.string.regions_updating)
        RegionState.WaitingForWifi -> stringResource(R.string.regions_waiting_for_wifi)
        RegionState.NeedsConfirmation -> stringResource(R.string.regions_needs_confirmation)
        is RegionState.Ready -> state.osmDate?.let { osmDateLine(it) }
        is RegionState.Failed -> stringResource(R.string.regions_failed)
    }

@Composable
private fun RemoveDialog(
    region: Region,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.regions_remove_title, stringResource(region.name))) },
        text = { Text(stringResource(R.string.regions_remove_body)) },
        confirmButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.regions_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
internal fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
