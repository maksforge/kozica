package app.kozica.ui.safety

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.SectionHeader
import app.kozica.ui.common.openOnPlay
import app.kozica.ui.common.startActivitySafely

/**
 * Each country's rescue number, rescue app, avalanche bulletin and mountain weather, as hand-offs
 * (`docs/phase-3.md` § 1, ADR-0008).
 *
 * No `ViewModel`, as on Support: the state is which rescue apps are installed, which belongs to the
 * phone rather than the app, and which tap last found nothing.
 */
@Composable
fun SafetyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var installed by remember { mutableStateOf(context.installedRescueApps()) }
    // Read again on every return: the usual way back here is from Play, having just installed one.
    LifecycleResumeEffect(Unit) {
        installed = context.installedRescueApps()
        onPauseOrDispose {}
    }
    // Keyed by the row's target, so the line appears under the row that was tapped rather than at
    // the foot of a screen taller than the phone.
    var failed by remember { mutableStateOf<Pair<String, Int>?>(null) }

    fun handOff(
        target: String,
        @StringRes missing: Int,
        open: () -> Boolean,
    ) {
        failed = if (open()) null else target to missing
    }

    @Composable
    fun DialRow(
        title: String,
        @StringRes hint: Int,
        number: RescueNumber,
    ) = SafetyRow(
        title = title,
        hint = stringResource(hint, number.shown),
        error = failed?.takeIf { it.first == number.dialled }?.second,
        onClick = { handOff(number.dialled, R.string.safety_no_dialler) { context.dial(number) } },
    )

    @Composable
    fun PageRow(
        @StringRes title: Int,
        page: Page,
    ) = SafetyRow(
        title = stringResource(title, page.issuer),
        hint = stringResource(R.string.safety_opens_browser, page.host),
        error = failed?.takeIf { it.first == page.url }?.second,
        onClick = { handOff(page.url, R.string.safety_no_browser) { context.openPage(page) } },
    )

    DetailScaffold(
        title = stringResource(R.string.safety_title),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        Column(
            modifier =
                contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.safety_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.snug),
            )
            DialRow(stringResource(R.string.safety_emergency), R.string.safety_emergency_hint, EMERGENCY)

            for (country in Country.entries) {
                SectionHeader(stringResource(country.labelRes()))
                for (number in country.rescue) {
                    if (number.fallback) {
                        DialRow(
                            stringResource(R.string.safety_rescue_fallback, country.rescue.first().shown),
                            R.string.safety_rescue_fallback_hint,
                            number,
                        )
                    } else {
                        DialRow(stringResource(R.string.safety_rescue), R.string.safety_dial_hint, number)
                    }
                }
                val app = country.app
                val isInstalled = app.packageId in installed
                SafetyRow(
                    title = stringResource(R.string.safety_rescue_app, app.name),
                    hint =
                        stringResource(
                            if (isInstalled) R.string.safety_app_installed else R.string.safety_app_missing,
                        ),
                    error = failed?.takeIf { it.first == app.packageId }?.second,
                    onClick = { handOff(app.packageId, R.string.support_no_play) { context.openRescueApp(app) } },
                )
                for (page in country.avalanche) PageRow(R.string.safety_avalanche, page)
                val weather = country.weather
                PageRow(if (weather.modelCharts) R.string.safety_weather_model else R.string.safety_weather, weather)
            }
        }
    }
}

@Composable
private fun SafetyRow(
    title: String,
    hint: String,
    @StringRes error: Int?,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.base, vertical = Spacing.snug),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** `ACTION_DIAL` types the number and leaves the call to the user, so no `CALL_PHONE` is needed. */
private fun Context.dial(number: RescueNumber): Boolean =
    startActivitySafely(Intent(Intent.ACTION_DIAL, "tel:${number.dialled}".toUri()))

private fun Context.openPage(page: Page): Boolean = startActivitySafely(Intent(Intent.ACTION_VIEW, page.url.toUri()))

/** The app if it is installed, its Play page if not. */
private fun Context.openRescueApp(app: RescueApp): Boolean {
    val launch = packageManager.getLaunchIntentForPackage(app.packageId)
    return if (launch != null) startActivitySafely(launch) else openOnPlay(app.packageId)
}

/** Visible only because the manifest's `<queries>` names each package (package visibility, API 30). */
private fun Context.installedRescueApps(): Set<String> =
    Country.entries
        .map { it.app.packageId }
        .filter { packageManager.getLaunchIntentForPackage(it) != null }
        .toSet()

private fun Country.labelRes(): Int =
    when (this) {
        Country.Poland -> R.string.safety_poland
        Country.Slovakia -> R.string.safety_slovakia
        Country.Czechia -> R.string.safety_czechia
    }
