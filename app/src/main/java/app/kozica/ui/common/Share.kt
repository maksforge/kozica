package app.kozica.ui.common

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.PendingIntentCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import app.kozica.R
import app.kozica.data.PreservedCopy
import app.kozica.data.PreservedKind
import java.io.File
import java.util.UUID

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
 * finds no provider. Granting every app that takes the share up front, as ourselves, survives that;
 * [ShareChosenReceiver] takes it back from all but the pick, [revokeUnpickedShare] from all of them
 * after a cancel, and [revokeShares] from the pick at the next share or export from its directory.
 * A pick reported only after that cancel gets its grant back.
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
    val takers =
        packageManager
            .queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .distinct()
    uris.map { it.pathSegments.first() }.distinct().forEach { revokeShares(it) }
    for (taker in takers) {
        for (uri in uris) grantUriPermission(taker, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val open = OpenShare(UUID.randomUUID().toString(), takers, uris)
    shares.opened(open)
    val onChosen =
        Intent(this, ShareChosenReceiver::class.java)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(EXTRA_SHARE, open.id)
            .putStringArrayListExtra(EXTRA_TAKERS, ArrayList(takers))
            .putParcelableArrayListExtra(EXTRA_URIS, ArrayList<Uri>(uris))
    val chosen =
        PendingIntentCompat.getBroadcast(this, 0, onChosen, PendingIntent.FLAG_UPDATE_CURRENT, true)
    startActivity(Intent.createChooser(send, getString(title), chosen?.intentSender))
}

private class OpenShare(
    val id: String,
    val takers: List<String>,
    val uris: List<Uri>,
)

private val shares = ShareBook<OpenShare> { it.id }

/**
 * For the screen that opened the sheet, once it is back: a sheet with no pick heard is taken as closed
 * without one. A trampoline target that hands off to a service and finishes can beat its own pick here.
 */
fun Context.revokeUnpickedShare() {
    val open = shares.returned() ?: return
    revoke(open.takers, open.uris)
}

/** Every app's read access to what was shared from one of file_paths.xml's directories, by its [root] name. */
fun Context.revokeShares(root: String) =
    revokeUriPermission(
        Uri
            .Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(fileProviderAuthority)
            .appendPath(root)
            .build(),
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )

/** The share sheet's word on which app was picked, filled into the broadcast [share] hands it. */
class ShareChosenReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val picked =
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
                ?: return
        val uris = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_URIS, Uri::class.java).orEmpty()
        if (shares.picked(intent.getStringExtra(EXTRA_SHARE))) {
            for (uri in uris) context.grantUriPermission(picked.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.revoke(intent.getStringArrayListExtra(EXTRA_TAKERS).orEmpty().filter { it != picked.packageName }, uris)
    }
}

private fun Context.revoke(
    takers: List<String>,
    uris: List<Uri>,
) {
    for (taker in takers) {
        for (uri in uris) revokeUriPermission(taker, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private const val EXTRA_SHARE = "app.kozica.extra.SHARE_ID"
private const val EXTRA_TAKERS = "app.kozica.extra.SHARE_TAKERS"
private const val EXTRA_URIS = "app.kozica.extra.SHARE_URIS"

private val Context.fileProviderAuthority get() = "$packageName.fileprovider"

private fun Context.sharedUri(file: File) = FileProvider.getUriForFile(this, fileProviderAuthority, file)
