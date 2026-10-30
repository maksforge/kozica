package app.kozica.data.hikes

import app.kozica.data.HikeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The user's [Pace], read again whenever the ended Hikes change; nothing of it is stored, so a trim,
 * a delete or a restore changes it (docs/phase-6b.md § 2).
 *
 * @param hikes newest first.
 * @param ruleSecondsOf the rule's time for a Hike's Track, which reads its heights; null where it cannot be timed.
 */
class Paces(
    hikes: Flow<List<HikeSummary>>,
    private val ruleSecondsOf: suspend (id: String) -> Double?,
) {
    // By id and length, since a trim changes the length; a Hike the relief could not time yet is read again next time.
    private val timed = mutableMapOf<Pair<String, Double>, Double>()

    val pace: Flow<Pace?> = hikes.map { paceOf(samples(it)) }.distinctUntilChanged()

    private suspend fun samples(hikes: List<HikeSummary>): List<PaceSample> {
        val samples = mutableListOf<PaceSample>()
        for (hike in hikes) {
            if (samples.count { it.counts } == PACE_HIKES_READ) break
            val sample = PaceSample(hike.metres, hike.movingSeconds, ruleSeconds = null)
            samples += if (sample.mayCount) sample.copy(ruleSeconds = ruleSeconds(hike)) else sample
        }
        return samples
    }

    private suspend fun ruleSeconds(hike: HikeSummary): Double? {
        val key = hike.id to hike.metres
        return timed[key] ?: ruleSecondsOf(hike.id)?.also { timed[key] = it }
    }
}
