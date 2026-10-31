package app.kozica.data.hikes

import android.util.Log
import java.util.Locale

/**
 * One line per Hike each time the Pace is read again, and one with the Pace, under `KozicaPace`, for
 * phase 6b's R2 (docs/phase-6b.md, Readings). A Hike after the tenth that counts is "not read".
 */
fun logPace(
    hikes: List<Pair<String, PaceSample?>>,
    pace: Pace?,
) {
    val lines =
        hikes.map { (id, sample) ->
            val why =
                when {
                    sample == null -> "not read"
                    sample.counts -> "counted"
                    !sample.mayCount -> "under 3 km or an hour"
                    sample.ruleSeconds == null -> "no grid heights"
                    else -> "ratio outside 0.5..2"
                }
            String.format(
                Locale.ROOT,
                "hike=%s metres=%.0f moving_s=%s rule_s=%s ratio=%s %s",
                id,
                sample?.metres ?: Double.NaN,
                sample?.movingSeconds ?: "-",
                sample?.ruleSeconds?.let { String.format(Locale.ROOT, "%.0f", it) } ?: "-",
                sample?.ratio?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "-",
                why,
            )
        } +
            (
                pace?.let {
                    String.format(
                        Locale.ROOT,
                        "pace=%.3f hikes=%d floored=%b",
                        it.factor,
                        it.hikes,
                        it.floored,
                    )
                }
                    ?: "pace=- (fewer than $PACE_HIKES_NEEDED counting hikes)"
            )
    // JVM unit tests run the debug source set, where android.util.Log is a stub that throws.
    runCatching { lines.forEach { Log.i("KozicaPace", it) } }
}
