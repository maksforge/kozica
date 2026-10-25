package app.kozica.ui.support

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kozica.BuildConfig
import app.kozica.R
import app.kozica.theme.Spacing
import app.kozica.ui.appViewModelExtras
import app.kozica.ui.common.DetailScaffold
import app.kozica.ui.common.SectionHeader
import app.kozica.ui.common.startActivitySafely

/**
 * Attribution for every dependency in *this* binary.
 *
 * The list is generated at build time by `app.cash.licensee` over the resolved runtime classpath —
 * not typed from `libs.versions.toml`, which would name a dozen artifacts where the build actually
 * ships two hundred. Apache-2.0 §4 travels with each of them, and a hand-written list cannot stay
 * true across a dependency bump.
 */
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    onOpenLicence: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LicencesViewModel =
        viewModel(factory = LicencesViewModel.Factory, extras = appViewModelExtras()),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DetailScaffold(
        title = stringResource(R.string.settings_licences),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        LazyColumn(modifier = contentModifier.fillMaxSize()) {
            item(key = ODBL) {
                LicenceSection("Open Database License 1.0", "OpenStreetMap") {
                    onOpenLicence(ODBL)
                }
            }
            item(key = MAP_DATA_RELEASE) {
                MapDataOffer {
                    context.startActivitySafely(
                        Intent(Intent.ACTION_VIEW, "$MAP_DATA_RELEASE${BuildConfig.VERSION_NAME}".toUri()),
                    )
                }
            }
            // The relief's three sources (docs/phase-4.md § 3): GUGiK named, as Polish law asks; the
            // other two under CC BY 4.0, which also asks that the change be said.
            item(key = CC_BY) {
                LicenceSection("Creative Commons Attribution 4.0", "ÚGKK SR: DMR 5.0\nČÚZK: DMR 4G") {
                    onOpenLicence(CC_BY)
                }
            }
            item(key = "GUGiK") {
                LicenceSection("Numeryczny Model Terenu", "GUGiK", onClick = null)
            }
            item(key = "relief-modified") {
                Text(
                    text = stringResource(R.string.licences_relief_modified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
                )
            }
            item(key = OFL) {
                LicenceSection("SIL Open Font License 1.1", "Noto Sans") { onOpenLicence(OFL) }
            }
            for (group in groups) {
                item(key = group.title) {
                    // Only an SPDX licence has text bundled with the binary; a vendor's own terms can
                    // only be linked, so that row is not tappable here.
                    LicenceSection(
                        title = group.title,
                        subjects = group.artifacts.joinToString("\n") { it.coordinates },
                        onClick = group.spdxId?.let { id -> { onOpenLicence(id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapDataOffer(onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.base, vertical = Spacing.tight),
    ) {
        Text(text = stringResource(R.string.licences_map_data), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.licences_map_data_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicenceSection(
    title: String,
    subjects: String,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        SectionHeader(title)
        Text(
            text = subjects,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.base),
        )
    }
}
