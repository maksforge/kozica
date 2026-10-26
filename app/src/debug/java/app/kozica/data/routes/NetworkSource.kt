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
        handPushed(context, TRAIL_NETWORK)?.inputStream()
    }

/** The assets' name index, or one hand-pushed beside the network. */
fun placesFile(context: Context): File? = copiedPlaces(context) ?: handPushed(context, PLACES)

private fun handPushed(
    context: Context,
    name: String,
): File? = File(context.filesDir, "network/$name").takeIf { it.exists() }

const val TRAIL_NETWORK = "trails.network"
