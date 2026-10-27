package app.kozica.ui.backup

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.PreservedCopy
import app.kozica.data.PreservedKind
import app.kozica.data.backup.AutoBackupStatus
import app.kozica.data.backup.BackupScope
import app.kozica.data.backup.ExportFolderState
import app.kozica.data.backup.RestoreOutcome
import app.kozica.data.backup.RestoreRefusal
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.SectionHeader
import app.kozica.ui.common.openSystemBackupSettings
import app.kozica.ui.common.shareBackupArchive
import app.kozica.ui.common.sharePreservedCopy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.system.exitProcess

/** Backup and restore (ADR-0005, docs/phase-6.md § 7). */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel =
        viewModel(factory = BackupViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Any MIME type: mail and messengers relabel a zip, and the manifest is the real check.
    val pickArchive =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.inspect(RestoreSource.Picked(uri))
        }
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.rememberFolder(uri)
        }

    LaunchedEffect(state.exported) {
        state.exported?.let { archive ->
            context.shareBackupArchive(archive)
            viewModel.clearExported()
        }
    }

    val restoring = state.working == BackupWork.Restoring
    // Leaving would clear the ViewModel mid-swap, and the terminal screen would never come.
    BackHandler(enabled = restoring) {}

    DetailScaffold(
        title = stringResource(R.string.settings_backup),
        onBack = onBack.takeIf { state.restored == null && !restoring },
        modifier = modifier,
    ) { contentModifier ->
        val restored = state.restored
        if (restored != null) {
            RestoreFinished(restored, contentModifier)
            return@DetailScaffold
        }
        Column(contentModifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.section)) {
            AutomaticBackup(state.autoBackup, onOpenSettings = { context.openSystemBackupSettings() })
            Export(
                scope = state.scope,
                folder = state.folder,
                working = state.working,
                onScope = viewModel::setScope,
                onExport = viewModel::export,
            )
            ExportFolder(
                folder = state.folder,
                notice = state.notice,
                onChoose = {
                    try {
                        pickFolder.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        viewModel.folderPickerUnavailable()
                    }
                },
                onForget = viewModel::forgetFolder,
                onDismissNotice = viewModel::dismissNotice,
            )
            SectionHeader(stringResource(R.string.backup_restore_title))
            Paragraph(stringResource(R.string.backup_restore_help))
            OutlinedButton(
                onClick = { pickArchive.launch(arrayOf("*/*")) },
                enabled = state.working == null,
                modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
            ) {
                Text(stringResource(R.string.backup_restore))
            }
            if (restoring) Working(R.string.backup_restoring)
            SavedCopies(
                copies = state.preserved,
                enabled = state.working == null,
                onRestore = { viewModel.inspect(RestoreSource.Snapshot(it.file)) },
                onShare = { context.sharePreservedCopy(it) },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    state.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_confirm_body,
                        stringResource(pending.manifest.scope.labelRes),
                        dateTime(pending.manifest.createdAt),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRestore,
                ) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    state.refusal?.let { refusal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRefusal,
            title = { Text(stringResource(R.string.backup_refused_title)) },
            text = { Text(refusal.message()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
    state.pendingDelete?.let { copy ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.preserved_delete_title)) },
            text = { Text(stringResource(R.string.preserved_delete_body, copy.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.preserved_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Never blank: no marker is said as such, because a blank line reads as a working backup. */
@Composable
private fun AutomaticBackup(
    status: AutoBackupStatus,
    onOpenSettings: () -> Unit,
) {
    SectionHeader(stringResource(R.string.backup_auto_title))
    val line =
        when (status) {
            AutoBackupStatus.NeverRecorded -> stringResource(R.string.backup_auto_never)
            AutoBackupStatus.OverQuota -> stringResource(R.string.backup_auto_over_quota)
            is AutoBackupStatus.Recorded ->
                stringResource(
                    if (status.stale) R.string.backup_auto_stale else R.string.backup_auto_recorded,
                    dateTime(status.at),
                )
        }
    Text(
        line,
        style = MaterialTheme.typography.titleMedium,
        color = if (status == AutoBackupStatus.OverQuota) MaterialTheme.colorScheme.error else Color.Unspecified,
        modifier = Modifier.padding(horizontal = Spacing.base),
    )
    val excluded = (status as? AutoBackupStatus.Recorded)?.excludedThumbnails ?: 0
    if (excluded > 0) {
        Text(
            pluralStringResource(R.plurals.backup_auto_excluded, excluded, excluded),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = Spacing.base, end = Spacing.base, top = Spacing.hair),
        )
    }
    Paragraph(stringResource(R.string.backup_auto_photos))
    if (status !is AutoBackupStatus.Recorded || status.stale) {
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = Spacing.base)) {
            Text(stringResource(R.string.backup_auto_settings))
        }
    }
}

@Composable
private fun Export(
    scope: BackupScope,
    folder: ExportFolderState,
    working: BackupWork?,
    onScope: (BackupScope) -> Unit,
    onExport: (toFolder: Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.backup_export_title))
    Paragraph(stringResource(R.string.backup_explainer))
    Row(Modifier.padding(horizontal = Spacing.base)) {
        for (option in BackupScope.entries) {
            FilterChip(
                selected = scope == option,
                onClick = { onScope(option) },
                label = { Text(stringResource(option.labelRes)) },
                modifier = Modifier.padding(end = Spacing.tight),
            )
        }
    }
    Paragraph(
        stringResource(
            when (scope) {
                BackupScope.Records -> R.string.backup_scope_records_help
                BackupScope.Everything -> R.string.backup_scope_everything_help
            },
        ),
    )
    Button(
        onClick = { onExport(false) },
        enabled = working == null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.tight),
    ) {
        Text(stringResource(R.string.backup_export_share))
    }
    if (folder is ExportFolderState.Remembered) {
        OutlinedButton(
            onClick = { onExport(true) },
            enabled = working == null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base),
        ) {
            Text(stringResource(R.string.backup_export_folder, folder.label))
        }
    }
    if (working == BackupWork.Exporting) Working(R.string.backup_exporting)
}

@Composable
private fun ExportFolder(
    folder: ExportFolderState,
    notice: BackupNotice?,
    onChoose: () -> Unit,
    onForget: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    SectionHeader(stringResource(R.string.backup_folder_title))
    Paragraph(
        when (folder) {
            ExportFolderState.None -> stringResource(R.string.backup_folder_none)
            is ExportFolderState.Remembered -> stringResource(R.string.backup_folder_chosen, folder.label)
            ExportFolderState.Unavailable -> stringResource(R.string.backup_folder_unavailable)
        },
    )
    Row(
        Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        OutlinedButton(onClick = onChoose) {
            val none = folder is ExportFolderState.None
            Text(stringResource(if (none) R.string.backup_folder_choose else R.string.backup_folder_change))
        }
        if (folder !is ExportFolderState.None) {
            TextButton(onClick = onForget) { Text(stringResource(R.string.backup_folder_forget)) }
        }
    }
    notice?.let {
        Row(Modifier.padding(horizontal = Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Text(it.text(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

@Composable
private fun SavedCopies(
    copies: List<PreservedCopy>,
    enabled: Boolean,
    onRestore: (PreservedCopy) -> Unit,
    onShare: (PreservedCopy) -> Unit,
    onDelete: (PreservedCopy) -> Unit,
) {
    val context = LocalContext.current
    SectionHeader(stringResource(R.string.preserved_title))
    if (copies.isEmpty()) Paragraph(stringResource(R.string.preserved_empty))
    for (copy in copies) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.tight)) {
            Text(stringResource(copy.kind.labelRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    copy.savedAt?.let { dateTime(it) },
                    Formatter.formatShortFileSize(context, copy.totalBytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!copy.kind.restorable) {
                Text(
                    stringResource(R.string.preserved_not_restorable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                if (copy.kind.restorable) {
                    TextButton(onClick = { onRestore(copy) }, enabled = enabled) {
                        Text(stringResource(R.string.preserved_restore))
                    }
                }
                TextButton(onClick = { onShare(copy) }) { Text(stringResource(R.string.preserved_share)) }
                TextButton(onClick = { onDelete(copy) }, enabled = enabled) {
                    Text(stringResource(R.string.preserved_delete))
                }
            }
        }
    }
    Paragraph(stringResource(R.string.preserved_help))
}

/**
 * Half the app holds `Flow`s over the file just replaced, so the process ends here. One tap rather
 * than a scheduled relaunch, which would be a background activity start the platform restricts.
 */
@Composable
private fun RestoreFinished(
    restored: RestoreOutcome.Restored,
    modifier: Modifier,
) {
    val activity = LocalActivity.current
    val close = {
        activity?.finishAffinity()
        exitProcess(0)
    }
    BackHandler(onBack = close)
    Column(modifier.verticalScroll(rememberScrollState()).padding(Spacing.base)) {
        Text(stringResource(R.string.backup_restored_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(
                R.string.backup_restored_scope,
                stringResource(restored.manifest.scope.labelRes),
                dateTime(restored.manifest.createdAt),
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.base),
        )
        val overlaid = restored.imagesWritten
        Text(
            pluralStringResource(R.plurals.backup_restored_images, overlaid, overlaid),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.tight),
        )
        if (!restored.mediaComplete) {
            Text(
                stringResource(R.string.backup_restored_media_incomplete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.tight),
            )
        }
        val kept = restored.merge.kept.size
        if (kept > 0) {
            Text(
                pluralStringResource(R.plurals.backup_restored_kept, kept, kept),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.tight),
            )
        }
        Text(
            stringResource(R.string.backup_restored_snapshot),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.tight),
        )
        Text(
            stringResource(R.string.backup_restored_close_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.base),
        )
        Button(onClick = close, modifier = Modifier.fillMaxWidth().padding(top = Spacing.base)) {
            Text(stringResource(R.string.backup_restored_close))
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
    )
}

@Composable
private fun Working(label: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.tight)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.hair))
    }
}

@Composable
private fun dateTime(at: Instant): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(LocalConfiguration.current.locales[0])
        .format(at.atZone(ZoneId.systemDefault()))

private val BackupScope.labelRes: Int
    get() =
        when (this) {
            BackupScope.Records -> R.string.backup_scope_records
            BackupScope.Everything -> R.string.backup_scope_everything
        }

private val PreservedKind.labelRes: Int
    get() =
        when (this) {
            PreservedKind.WipeCopy -> R.string.preserved_kind_wipe
            PreservedKind.RestoreSnapshot -> R.string.preserved_kind_snapshot
        }

@Composable
internal fun BackupNotice.text(): String =
    when (this) {
        is BackupNotice.Saved -> stringResource(R.string.backup_folder_saved, name)
        BackupNotice.FolderRefused -> stringResource(R.string.backup_folder_refused)
        BackupNotice.FolderUnavailable -> stringResource(R.string.backup_folder_unavailable)
        BackupNotice.NotRemembered -> stringResource(R.string.backup_folder_not_remembered)
        BackupNotice.NoPicker -> stringResource(R.string.backup_folder_no_picker)
        BackupNotice.ExportFailed -> stringResource(R.string.backup_export_failed)
    }

@Composable
private fun RestoreRefusal.message(): String =
    when (this) {
        RestoreRefusal.NotABackup -> stringResource(R.string.backup_refused_not_a_backup)
        is RestoreRefusal.MadeByANewerKozica ->
            stringResource(
                R.string.backup_refused_newer,
                fileVersion,
                readableVersion,
            )
        RestoreRefusal.TooLarge -> stringResource(R.string.backup_refused_too_large)
        RestoreRefusal.Unreadable -> stringResource(R.string.backup_refused_unreadable)
        RestoreRefusal.RecordingUnderway -> stringResource(R.string.backup_refused_recording)
    }
