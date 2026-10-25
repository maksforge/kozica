package app.kozica.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import app.kozica.data.routes.Router
import app.kozica.data.routes.TrailNetworks
import java.io.File
import kotlin.concurrent.thread

/**
 * Plans one leg between two positions and logs its timings, for phase 5's gate (docs/phase-5.md § 1);
 * the leg's line goes to `cache/route.txt`, one `lat lon` per line. Delivery needs `-f 0x00000020`.
 *
 * ```bash
 * adb shell am broadcast -n <pkg>.debug/app.kozica.debug.RouteReceiver -f 0x00000020 \
 *   --es from 49.2697,19.9813 --es to 49.2010,20.0710
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
        if (from == null || to == null) {
            resultCode = 1
            resultData = "from and to are required, each as lat,lon"
            return
        }
        val pending = goAsync()
        val app = context.applicationContext
        thread(name = "RouteReceiver") {
            try {
                plan(app, from, to)
            } catch (e: Exception) {
                Log.e(TAG, "planning failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun plan(
        context: Context,
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
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
        val router = Router(network)
        Log.i(TAG, "index ${SystemClock.elapsedRealtime() - started} ms")
        started = SystemClock.elapsedRealtime()
        val starts = router.joins(from.first, from.second)
        val ends = router.joins(to.first, to.second)
        Log.i(
            TAG,
            "joins ${SystemClock.elapsedRealtime() - started} ms: ${starts.size} and ${ends.size} Trails within reach",
        )
        started = SystemClock.elapsedRealtime()
        val leg = router.leg(starts, ends, to.first, to.second)
        val searched = SystemClock.elapsedRealtime() - started
        if (leg == null) {
            Log.i(TAG, "search $searched ms: no way")
            return
        }
        Log.i(TAG, "joined ${leg.start.metresAway.toInt()} m and ${leg.end.metresAway.toInt()} m from the points")
        val metres = leg.steps.sumOf { network.edgeLength[it.edge].toDouble() }
        Log.i(
            TAG,
            "search $searched ms: ${leg.nodesVisited} nodes visited, ${leg.steps.size} edges, about ${metres.toInt()} m, ${(leg.seconds / 60).toInt()} min",
        )
        File(context.cacheDir, "route.txt").printWriter().use { out ->
            for (step in leg.steps) {
                val first = network.edgeFirstPoint[step.edge]
                val points = first until first + network.edgePointCount[step.edge]
                for (point in if (step.forward) points else points.reversed()) {
                    out.println(
                        "${network.pointLat[point] * 1e-7} ${network.pointLon[point] * 1e-7} ${network.edgeWay[step.edge]}",
                    )
                }
            }
        }
    }

    private fun position(text: String): Pair<Double, Double>? {
        val parts = text.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    private companion object {
        const val TAG = "KozicaRoute"
    }
}
