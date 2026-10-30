package app.kozica.data.routes

import android.content.Context

/** The one network, and its Trails' walks, this process loads, from wherever the variant's [openTrailNetwork] finds it. */
object TrailNetworks {
    @Volatile private var loaded: TrailNetwork? = null

    /** Null when this build carries no network, which search and planning show as a placeholder. */
    fun load(context: Context): TrailNetwork? =
        loaded ?: synchronized(this) {
            loaded ?: openTrailNetwork(context)?.use { TrailNetwork.read(it.readBytes()) }?.also { loaded = it }
        }

    @Volatile private var walks: TrailWalks? = null

    /** Null when this build carries no walks, as it carries no network. */
    fun walks(context: Context): TrailWalks? =
        walks ?: synchronized(this) {
            walks ?: openTrailWalks(context)?.use { TrailWalks.read(it.readBytes()) }?.also { walks = it }
        }
}
