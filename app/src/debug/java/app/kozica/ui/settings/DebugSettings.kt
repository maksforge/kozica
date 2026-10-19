package app.kozica.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.kozica.MainApplication
import app.kozica.R
import app.kozica.data.SampleData
import app.kozica.debug.DebugMapActivity
import app.kozica.theme.Spacing
import app.kozica.ui.common.SectionHeader
import kotlinx.coroutines.launch

/**
 * The developer-only section of Settings. **This file exists only in the debug source set** — see
 * the release half for why that is a source set and not a `BuildConfig.DEBUG` branch.
 *
 * Add things here freely: a sample-data seeder, a schema-version stamp. None of it reaches a release binary, and none of its strings reach the
 * translation gate.
 */
@Composable
fun DebugSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as MainApplication

    SectionHeader(stringResource(R.string.debug_section))
    DebugRow(stringResource(R.string.debug_seed)) {
        scope.launch { SampleData.seed(app.container.items) }
    }
    DebugRow(stringResource(R.string.debug_wipe)) {
        scope.launch { SampleData.wipe(app.container.items) }
    }
    DebugRow(stringResource(R.string.debug_map)) {
        context.startActivity(Intent(context, DebugMapActivity::class.java))
    }
}

@Composable
private fun DebugRow(
    title: String,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.base, vertical = Spacing.base),
    )
}
