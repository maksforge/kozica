package app.kozica.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.TrailNetworks
import java.io.File
import kotlin.concurrent.thread

/**
 * Plans a Route and logs its timings and totals, for phase 5's readings (docs/phase-5.md § Readings);
 * its line goes to `cache/route.txt`, one `lat lon height approach` per line. `via` is optional, its
 * points separated by `;`. Delivery needs `-f 0x00000020`.
 *
 * ```bash
 * adb shell am broadcast -n <pkg>.debug/app.kozica.debug.RouteReceiver -f 0x00000020 \
 *   --es from 49.2697,19.9813 --es to 49.2010,20.0710 --es via '49.2420,20.0080'
 * adb logcat -d -s KozicaRoute
 * ```
 */
class RouteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val from = intent.getStringExtra("from")?.let(::position)
        val to = intent.getStringExtra("to")?.let(::position)
        val via =
            intent
                .getStringExtra("via")
                ?.split(";")
                ?.map(::position)
                .orEmpty()
        if (from == null || to == null || via.any { it == null }) {
            resultCode = 1
            resultData = "from and to are required and via is optional, each point as lat,lon, via's joined by ;"
            return
        }
        val pending = goAsync()
        val app = context.applicationContext
        thread(name = "RouteReceiver") {
            try {
                plan(app, listOf(from) + via.filterNotNull() + listOf(to))
            } catch (e: Exception) {
                Log.e(TAG, "planning failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun plan(
        context: Context,
        points: List<RoutePoint>,
    ) {
        var started = SystemClock.elapsedRealtime()
        val network = TrailNetworks.load(context)
        if (network == null) {
            Log.i(TAG, "no network in this build")
            return
        }
        Log.i(
            TAG,
            "load ${SystemClock.elapsedRealtime() - started} ms: ${network.nodeCount} nodes, ${network.edgeCount} edges",
        )
        started = SystemClock.elapsedRealtime()
        val planner = RoutePlanner(network)
        Log.i(TAG, "index ${SystemClock.elapsedRealtime() - started} ms")
        started = SystemClock.elapsedRealtime()
        val route = planner.plan(points)
        Log.i(TAG, "plan ${SystemClock.elapsedRealtime() - started} ms")
        for ((i, leg) in route.legs.withIndex()) {
            if (leg == null) {
                Log.i(TAG, "leg ${i + 1}: no way")
                continue
            }
            Log.i(
                TAG,
                "leg ${i + 1}: joined ${leg.start.metresAway.toInt()} m and ${leg.end.metresAway.toInt()} m " +
                    "from its points, ${leg.steps.size} edges, ${leg.nodesVisited} nodes visited, " +
                    "${route.minutesTo(i + 1)} min to its end",
            )
        }
        Log.i(
            TAG,
            "route: ${route.metres.toInt()} m, ascent ${route.ascent.toInt()} m, descent ${route.descent.toInt()} m, " +
                "${route.minutes} min (${(route.seconds / 60).toInt()} unrounded)",
        )
        for (stretch in route.demanding) {
            Log.i(
                TAG,
                "demanding: sac ${stretch.sac} from ${stretch.fromMetres.toInt()} m to ${stretch.toMetres.toInt()} m",
            )
        }
        File(context.cacheDir, "route.txt").printWriter().use { out ->
            for (sample in route.line) {
                out.println("${sample.latitude} ${sample.longitude} ${sample.heightMetres} ${sample.approach}")
            }
        }
    }

    private fun position(text: String): RoutePoint? {
        val parts = text.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        return if (parts.size == 2) RoutePoint(parts[0], parts[1]) else null
    }

    private companion object {
        const val TAG = "KozicaRoute"
    }
}
