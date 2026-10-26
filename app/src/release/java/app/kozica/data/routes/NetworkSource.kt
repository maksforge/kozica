package app.kozica.data.routes

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** The network in the base module's assets, which only a `-PmapPacks` build carries. */
fun openTrailNetwork(context: Context): InputStream? =
    try {
        context.assets.open(TRAIL_NETWORK)
    } catch (_: FileNotFoundException) {
        null
    }

/** The name index beside it. */
fun placesFile(context: Context): File? = copiedPlaces(context)

const val TRAIL_NETWORK = "trails.network"
