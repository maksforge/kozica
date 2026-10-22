package app.kozica.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.R
import app.kozica.data.ThemeMode
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.SectionHeader
import app.kozica.ui.common.SwitchRow
import app.kozica.ui.support.SupportRequest
import app.kozica.ui.support.hintRes
import app.kozica.ui.support.sendSupportMail
import app.kozica.ui.support.titleRes

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DetailScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        Column(modifier = contentModifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_appearance))

            Row(modifier = Modifier.padding(horizontal = Spacing.base)) {
                for (mode in ThemeMode.entries) {
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                        modifier = Modifier.padding(end = Spacing.tight),
                    )
                }
            }

            SwitchRow(
                title = stringResource(R.string.settings_material_you),
                subtitle = stringResource(R.string.settings_material_you_hint),
                checked = state.materialYou,
                onChange = viewModel::setMaterialYou,
            )

            SectionHeader(stringResource(R.string.settings_language))
            LanguageRow()
            // The channel that stands in for a native read-through (docs/translator-brief.md §8),
            // beside the picker because that is where somebody is standing the moment a translation
            // reads wrong. Its wording lives with the other two requests, in ui/support/.
            SettingsRow(
                title = stringResource(SupportRequest.Language.titleRes()),
                subtitle = stringResource(SupportRequest.Language.hintRes()),
                onClick = { context.sendSupportMail(SupportRequest.Language) },
            )

            SectionHeader(stringResource(R.string.settings_about))
            SettingsRow(stringResource(R.string.settings_support), onClick = onOpenSupport)
            SettingsRow(stringResource(R.string.settings_licences), onClick = onOpenLicences)

            // The developer-only section. In a release build this composable is a no-op that
            // renders nothing — see `src/release/…/DebugSettings.kt`. It is a source-set seam
            // rather than an `if (BuildConfig.DEBUG)` because with `isMinifyEnabled = false` a
            // statically-false branch is still compiled into the AAB, and its strings are still
            // inside the translation gate. A hide is not a strip.
            DebugSettings()
        }
    }
}

@Composable
private fun LanguageRow() {
    // Read on every composition rather than held in state: `setAppLanguage` recreates the Activity,
    // so this composable is rebuilt from scratch and the fresh read is always correct.
    val current = currentAppLanguage()
    Row(modifier = Modifier.padding(horizontal = Spacing.base)) {
        FilterChip(
            selected = current == null,
            onClick = { setAppLanguage(null) },
            label = { Text(stringResource(R.string.settings_language_system)) },
            modifier = Modifier.padding(end = Spacing.tight),
        )
        for (language in AppLanguage.entries) {
            FilterChip(
                selected = current == language,
                onClick = { setAppLanguage(language) },
                label = { Text(stringResource(language.labelRes)) },
                modifier = Modifier.padding(end = Spacing.tight),
            )
        }
    }
}

/**
 * A row that goes somewhere.
 *
 * The subtitle is optional because most rows here name a screen inside the app, where the title is
 * the whole promise. A row that **leaves** the app — opening a mail composer, a system settings
 * page — takes one, because a tap that hands the user to another app with no warning is the kind of
 * thing they back out of and never tap again.
 */
@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.base, vertical = Spacing.base),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
