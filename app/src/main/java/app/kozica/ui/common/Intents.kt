package app.kozica.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * `startActivity`, reporting failure rather than taking the app down with it.
 *
 * Every hand-off launches something it cannot be sure exists — a mail app, Play, and from phase 3
 * the Safety screen's hand-offs. An OEM screen that is present but not exported to us throws
 * `SecurityException`, which is the same outcome from here as it not being there at all.
 */
internal fun Context.startActivitySafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

/** The Play app itself, which is the only store this app is listed in. */
private const val PLAY_STORE_APP = "com.android.vending"

/**
 * Open an app's Play page: in the Play app if the phone has it, in a browser if not.
 *
 * **An `https://` link pinned to the Play app, not `market://`.** That is a device finding: on
 * HyperOS the `market` scheme is also claimed by Xiaomi's own store, so an unpinned `market://` tap
 * opens the system chooser with that store listed first — and the app may not be in it. `setPackage`
 * is the platform's way of saying *this app and no other*; with it, the launch either opens Play or
 * throws `ActivityNotFoundException` because Play is not installed. The `https` link is the same one
 * either way, so the fallback is that link unpinned, which a browser answers.
 */
internal fun Context.openOnPlay(packageId: String): Boolean {
    val listing = "https://play.google.com/store/apps/details?id=$packageId".toUri()
    val inPlay = Intent(Intent.ACTION_VIEW, listing).setPackage(PLAY_STORE_APP)
    return startActivitySafely(inPlay) || startActivitySafely(Intent(Intent.ACTION_VIEW, listing))
}
