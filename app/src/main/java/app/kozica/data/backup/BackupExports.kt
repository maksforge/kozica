package app.kozica.data.backup

import android.content.ContentResolver
import app.kozica.data.AppPreferences
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.time.Instant

sealed interface ExportResult {
    data class Saved(
        val name: String,
    ) : ExportResult

    /** For the share sheet; [fallback] says why the remembered folder did not take it. */
    data class Share(
        val archive: File,
        val fallback: FolderFallback? = null,
    ) : ExportResult

    data object Failed : ExportResult
}

enum class FolderFallback { Refused, Unavailable }

/**
 * One export, shared by the Backup screen and the Hikes tab's banner (docs/phase-6.md § 7). An
 * Everything export records its start, which the banner counts newer photos from: the share sheet
 * never says whether the file went anywhere, so the file existing is what counts. Recorded before
 * the archive is written, so the preferences inside it say so too and a restore of it raises no banner.
 */
class BackupExports(
    private val exporter: BackupExporter,
    private val exportsDir: File,
    private val preferences: AppPreferences,
    private val contentResolver: ContentResolver,
) {
    suspend fun export(
        scope: BackupScope,
        toFolder: Boolean,
    ): ExportResult {
        val now = Instant.now()
        val previous = preferences.lastEverythingExport.first()
        if (scope == BackupScope.Everything) preferences.setLastEverythingExport(now)
        val archive =
            try {
                exporter.exportTo(File(exportsDir, exportFileName(scope, now)), scope, now)
            } catch (e: IOException) {
                if (scope == BackupScope.Everything) preferences.setLastEverythingExport(previous)
                return ExportResult.Failed
            }
        if (!toFolder) return ExportResult.Share(archive)
        return when (val folder = contentResolver.exportFolderState(preferences.backupFolderUri.first())) {
            ExportFolderState.None -> ExportResult.Share(archive)
            ExportFolderState.Unavailable -> ExportResult.Share(archive, FolderFallback.Unavailable)
            is ExportFolderState.Remembered ->
                when (val written = contentResolver.writeExportToFolder(folder.uri, archive)) {
                    is FolderWrite.Written -> ExportResult.Saved(written.name)
                    FolderWrite.Refused -> ExportResult.Share(archive, FolderFallback.Refused)
                }
        }
    }
}
