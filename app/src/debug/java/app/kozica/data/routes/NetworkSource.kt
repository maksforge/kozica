package app.kozica.data.routes

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** The assets' network, or one hand-pushed to `files/network/`, as docs/phase-5.md § 2 describes. */
fun openTrailNetwork(context: Context): InputStream? =
    try {
        context.assets.open(TRAIL_NETWORK)
    } catch (_: FileNotFoundException) {
        File(context.filesDir, "network/$TRAIL_NETWORK").takeIf { it.exists() }?.inputStream()
    }

const val TRAIL_NETWORK = "trails.network"
