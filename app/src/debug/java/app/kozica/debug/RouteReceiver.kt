package app.kozica.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import app.kozica.MainApplication
import app.kozica.data.routes.RoutePlanner
import app.kozica.data.routes.RoutePoint
import app.kozica.data.routes.TrailNetworks
import kotlinx.coroutines.runBlocking
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
 *
 * With `--el trail <id>` (and `--ez reversed true` for the other way) it makes the Route along that
 * Trail by "Plan a Route along it"'s code, logs the Trail's row beside it, and plans the points
 * again as a saved Route is on open, for phase 6c's R4.
 */
class RouteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.hasExtra("trail")) {
            val trail = intent.getLongExtra("trail", 0)
            val reversed = intent.getBooleanExtra("reversed", false)
            val pending = goAsync()
            val app = context.applicationContext as MainApplication
            thread(name = "RouteReceiver") {
                try {
                    along(app, trail, reversed)
                } catch (e: Exception) {
                    Log.e(TAG, "planning along a Trail failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
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

    private fun along(
        app: MainApplication,
        id: Long,
        reversed: Boolean,
    ) {
        val trail = app.container.trail(id)
        if (trail == null) {
            Log.i(TAG, "no Trail $id in this build")
            return
        }
        val facts = trail.facts
        Log.i(
            TAG,
            "trail $id: %.1f m, ascent %.1f m, descent %.1f m, %d gaps".format(
                facts.metres,
                facts.ascent,
                facts.descent,
                facts.gaps,
            ),
        )
        val started = SystemClock.elapsedRealtime()
        val points =
            runBlocking {
                app.container.routeAlong(
                    trail,
                )
            }.orEmpty().let { if (reversed) it.reversed() else it }
        Log.i(TAG, "along: ${points.size} points in ${SystemClock.elapsedRealtime() - started} ms")
        val route = runBlocking { app.container.planRoute(points) } ?: return
        Log.i(
            TAG,
            "route: %.1f m, ascent %.1f m, descent %.1f m, %d legs".format(
                route.metres,
                route.ascent,
                route.descent,
                route.legs.size,
            ),
        )
        val network = TrailNetworks.load(app) ?: return
        val own =
            TrailNetworks
                .walks(app)
                ?.steps(id)
                ?.map { it ushr 2 }
                ?.toSet()
                .orEmpty()
        for ((i, leg) in route.legs.withIndex()) {
            val off = leg?.steps?.filter { it.edge !in own && it.from != it.to }.orEmpty()
            if (off.isNotEmpty()) {
                val metres = off.sumOf { kotlin.math.abs(it.to - it.from) * network.edgeLength[it.edge] }
                Log.i(
                    TAG,
                    "leg ${i + 1} leaves the Trail over ${off.size} edges, %.1f m, from %s".format(metres, points[i]),
                )
            }
        }
        val again =
            runBlocking { app.container.planRoute(points.map { RoutePoint(it.latitude, it.longitude) }) } ?: return
        Log.i(TAG, "again: %.1f m, ascent %.1f m, descent %.1f m".format(again.metres, again.ascent, again.descent))
    }

    private fun position(text: String): RoutePoint? {
        val parts = text.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        return if (parts.size == 2) RoutePoint(parts[0], parts[1]) else null
    }

    private companion object {
        const val TAG = "KozicaRoute"
    }
}
