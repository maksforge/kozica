package app.kozica.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Android's backup settings, best effort: no public action is guaranteed, so this walks a list and
 * ends at Settings itself. `startActivity` in a loop, because package visibility can make a
 * `resolveActivity` lookup miss a screen that would open.
 */
fun Context.openSystemBackupSettings() {
    val actions =
        buildList {
            add("android.settings.BACKUP_AND_RESET_SETTINGS")
            add("android.settings.BACKUP_SETTINGS")
            // From API 31 this is the Privacy dashboard, with no backup switch on it.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) add(Settings.ACTION_PRIVACY_SETTINGS)
            add(Settings.ACTION_SETTINGS)
        }
    for (action in actions) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            continue
        } catch (e: SecurityException) {
            continue
        }
    }
}
