package app.kozica.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.kozica.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Phase 2's "OSM data: %1$s", on the map and on each ready Region alike. */
@Composable
fun osmDateLine(osmDate: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val date =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(osmDate)
    return stringResource(R.string.map_osm_date, date)
}
