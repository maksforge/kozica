package app.kozica.data.backup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/*
 * The remembered export folder (docs/phase-6.md § 7): a persisted tree grant, checked on every read.
 * Every failure falls back to the share sheet.
 */

private const val ZIP_MIME = "application/zip"

/** A stored Uri is not a working folder: the grant can be revoked, or restored from another phone. */
sealed interface ExportFolderState {
    data object None : ExportFolderState

    data class Remembered(
        val uri: Uri,
        val label: String,
    ) : ExportFolderState

    data object Unavailable : ExportFolderState
}

sealed interface FolderWrite {
    /** Under the name the provider gave it, which may differ from the one asked for. */
    data class Written(
        val name: String,
    ) : FolderWrite

    data object Refused : FolderWrite
}

/** Returns whether the grant was taken; one that cannot be persisted is a folder not remembered. */
fun ContentResolver.rememberExportFolder(uri: Uri): Boolean =
    try {
        takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    } catch (e: SecurityException) {
        false
    }

fun ContentResolver.forgetExportFolder(uri: Uri) {
    try {
        releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    } catch (e: SecurityException) {
        // Already released.
    }
}

/** The grant first, then the provider's name for the folder: each can outlive the other. */
fun ContentResolver.exportFolderState(stored: String?): ExportFolderState {
    if (stored.isNullOrBlank()) return ExportFolderState.None
    val uri = runCatching { stored.toUri() }.getOrNull() ?: return ExportFolderState.Unavailable
    if (persistedUriPermissions.none { it.uri == uri && it.isWritePermission }) return ExportFolderState.Unavailable
    val label = treeLabel(uri) ?: return ExportFolderState.Unavailable
    return ExportFolderState.Remembered(uri = uri, label = label)
}

/** Never throws: any provider failure is [FolderWrite.Refused], and the caller shares instead. */
suspend fun ContentResolver.writeExportToFolder(
    tree: Uri,
    archive: File,
): FolderWrite =
    withContext(Dispatchers.IO) {
        try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val target =
                DocumentsContract.createDocument(this@writeExportToFolder, parent, ZIP_MIME, archive.name)
                    ?: return@withContext FolderWrite.Refused
            val output = openOutputStream(target) ?: return@withContext FolderWrite.Refused
            output.use { archive.inputStream().use { input -> input.copyTo(output) } }
            FolderWrite.Written(displayName(target) ?: archive.name)
        } catch (e: FileNotFoundException) {
            FolderWrite.Refused
        } catch (e: IOException) {
            FolderWrite.Refused
        } catch (e: SecurityException) {
            FolderWrite.Refused
        } catch (e: IllegalArgumentException) {
            FolderWrite.Refused
        }
    }

/** A tree Uri is not a document Uri; the provider answers only for the rebuilt one. */
private fun ContentResolver.treeLabel(tree: Uri): String? =
    try {
        displayName(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
    } catch (e: IllegalArgumentException) {
        null
    }

private fun ContentResolver.displayName(document: Uri): String? {
    val cursor =
        try {
            query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
    return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
}
