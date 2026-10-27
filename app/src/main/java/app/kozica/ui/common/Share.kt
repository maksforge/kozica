package app.kozica.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.kozica.R
import app.kozica.data.PreservedCopy
import app.kozica.data.PreservedKind
import java.io.File

private const val ZIP_MIME = "application/zip"

fun Context.shareBackupArchive(archive: File) = share(listOf(archive), ZIP_MIME, R.string.backup_share)

/** A wipe copy travels with its sidecars, where its latest writes may still be. */
fun Context.sharePreservedCopy(copy: PreservedCopy) =
    share(
        copy.files,
        if (copy.kind == PreservedKind.WipeCopy) "application/octet-stream" else ZIP_MIME,
        R.string.backup_share,
    )

fun Context.shareGpx(file: File) = share(listOf(file), "application/gpx+xml", R.string.gpx_export)

/**
 * MIUI 12.5's share sheet starts the picked app as the system rather than as us, and the platform
 * refuses a grant made that way ("use startActivityAsCaller() instead"), so the app opens the file and
 * finds no provider. Granting every app that takes the share up front, as ourselves, survives that.
 */
private fun Context.share(
    files: List<File>,
    mime: String,
    title: Int,
) {
    val uris = files.map(::sharedUri)
    val send =
        Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    for (target in packageManager.queryIntentActivities(send, 0)) {
        for (uri in uris) {
            grantUriPermission(
                target.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    startActivity(Intent.createChooser(send, getString(title)))
}

private fun Context.sharedUri(file: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
