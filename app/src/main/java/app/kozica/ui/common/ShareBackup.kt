package app.kozica.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.kozica.R
import app.kozica.data.PreservedCopy
import app.kozica.data.PreservedKind
import java.io.File

private const val ZIP_MIME = "application/zip"

fun Context.shareBackupArchive(archive: File) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME
            putExtra(Intent.EXTRA_STREAM, backupUri(archive))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(send, getString(R.string.backup_share)))
}

/** A wipe copy travels with its sidecars, where its latest writes may still be. */
fun Context.sharePreservedCopy(copy: PreservedCopy) {
    val send =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (copy.kind == PreservedKind.WipeCopy) "application/octet-stream" else ZIP_MIME
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(copy.files.map(::backupUri)))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(send, getString(R.string.backup_share)))
}

private fun Context.backupUri(file: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
