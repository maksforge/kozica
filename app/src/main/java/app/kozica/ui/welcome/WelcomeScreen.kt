package app.kozica.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.regions.fileSize
import app.kozica.ui.regions.stateLine

/** The first launch: which Regions to download before going (docs/phase-2b.md § 5). */
@Composable
fun WelcomeScreen(
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = viewModel(factory = WelcomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.tight),
                    modifier = Modifier.padding(Spacing.base),
                ) {
                    Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.choosable.isNotEmpty()) {
                item {
                    ChoiceRow(
                        label = stringResource(R.string.welcome_all),
                        detail = null,
                        checked = state.allSelected,
                        enabled = true,
                        onToggle = viewModel::toggleAll,
                    )
                }
            }
            items(state.regions.orEmpty(), key = { it.id }) { region ->
                val choosable = region in state.choosable
                ChoiceRow(
                    label = stringResource(region.name),
                    detail = if (choosable) region.downloadSize?.let { fileSize(it) } else stateLine(region),
                    checked = !choosable || region.id in state.selected,
                    enabled = choosable,
                    onToggle = { viewModel.toggle(region.id) },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.welcome_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.base),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.tight)) {
            Button(
                onClick = {
                    viewModel.download()
                    onDownload()
                },
                enabled = state.chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(downloadLabel(state.chosen.size, state.chosenSize)) }
            TextButton(onClick = viewModel::skip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_not_now))
            }
        }
    }
}

@Composable
private fun downloadLabel(
    count: Int,
    bytes: Long?,
): String =
    when {
        count == 0 -> stringResource(R.string.regions_download)
        bytes == null -> pluralStringResource(R.plurals.welcome_download_unsized, count, count)
        else -> pluralStringResource(R.plurals.welcome_download, count, count, fileSize(bytes))
    }

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        trailingContent = detail?.let { { Text(it) } },
        modifier =
            Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = {
                onToggle()
            }),
    )
}
