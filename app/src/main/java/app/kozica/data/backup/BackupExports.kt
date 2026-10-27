package app.kozica.data.backup

import android.content.ContentResolver
import app.kozica.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * Everything export records its start once the archive is whole, which the banner counts newer
 * photos from: the share sheet never says whether the file went anywhere, so the file existing is
 * what counts. The archive's own preferences predate that, so a restore of it raises the time
 * itself ([adoptRestoredEverythingExport]).
 */
class BackupExports(
    private val exporter: BackupExporter,
    private val exportsDir: File,
    private val preferences: AppPreferences,
    private val contentResolver: ContentResolver,
    private val lock: Mutex,
    private val revokeShares: () -> Unit,
) {
    suspend fun export(
        scope: BackupScope,
        toFolder: Boolean,
    ): ExportResult {
        val now = Instant.now()
        return lock.withLock {
            val archive =
                try {
                    withContext(Dispatchers.IO) {
                        revokeShares()
                        exportsDir.deleteRecursively()
                    }
                    exporter.exportTo(File(exportsDir, exportFileName(scope, now)), scope, now)
                } catch (e: IOException) {
                    return ExportResult.Failed
                }
            if (scope == BackupScope.Everything) preferences.setLastEverythingExport(now)
            handOver(archive, toFolder)
        }
    }

    private suspend fun handOver(
        archive: File,
        toFolder: Boolean,
    ): ExportResult {
        if (!toFolder) return ExportResult.Share(archive)
        return when (val folder = contentResolver.exportFolderState(preferences.backupFolderUri.first())) {
            ExportFolderState.None -> ExportResult.Share(archive)
            ExportFolderState.Unavailable -> ExportResult.Share(archive, FolderFallback.Unavailable)
            is ExportFolderState.Remembered ->
                when (val written = contentResolver.writeExportToFolder(folder.uri, archive)) {
                    is FolderWrite.Written -> {
                        withContext(Dispatchers.IO) { archive.delete() }
                        ExportResult.Saved(written.name)
                    }
                    FolderWrite.Refused -> ExportResult.Share(archive, FolderFallback.Refused)
                }
        }
    }
}
