package app.kozica.ui.map

import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Short units, so the platform's abbreviation sidesteps the case the host sentence would need. */
internal fun Duration.short(locale: Locale): String =
    toComponents { hours, minutes, seconds, _ ->
        val measures =
            buildList {
                if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
                if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
                if (isEmpty()) add(Measure(seconds, MeasureUnit.SECOND))
            }
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures.toTypedArray())
    }

internal fun walkingTime(
    minutes: Int,
    locale: Locale,
): String = minutes.minutes.short(locale)

/** Kilometres to one decimal, as a signpost's distances are. */
internal fun length(
    metres: Double,
    locale: Locale,
): String {
    val number = NumberFormat.getInstance(locale).apply { maximumFractionDigits = 1 }
    return MeasureFormat
        .getInstance(locale, MeasureFormat.FormatWidth.SHORT, number)
        .format(Measure(metres / 1000, MeasureUnit.KILOMETER))
}

internal fun wholeMetres(
    metres: Double,
    locale: Locale,
): String {
    val number = NumberFormat.getIntegerInstance(locale)
    return MeasureFormat
        .getInstance(locale, MeasureFormat.FormatWidth.SHORT, number)
        .format(Measure(Math.round(metres), MeasureUnit.METER))
}

/** Metres under a kilometre, kilometres from there on. */
internal fun distance(
    metres: Double,
    locale: Locale,
): String = if (metres < 1000) wholeMetres(metres, locale) else length(metres, locale)
