package app.kozica.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * `startActivity`, reporting failure rather than taking the app down with it.
 *
 * Every hand-off launches something it cannot be sure exists — a mail app, Play, and from phase 3
 * the live-information hand-offs. An OEM screen that is present but not exported to us throws
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
