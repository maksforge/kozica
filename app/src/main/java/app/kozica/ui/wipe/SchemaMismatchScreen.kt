package app.kozica.ui.wipe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kozica.R
import app.kozica.SchemaMismatch
import app.kozica.theme.Spacing

/**
 * The screen in front of a database this build cannot open (ADR-0001).
 *
 * **It is two screens wearing one name**, and the build variant decides which:
 *
 * - In a **debug** build, continuing wipes and lets the app through. The copy is already taken, so
 *   the user is consenting to lose the *live* file, not the data.
 * - In a **release** build there is nothing to consent to — the open would throw — so there is no
 *   continue button at all. It is a dead end that offers the copy, not a button that destroys
 *   someone's history on a path where nothing was going to destroy it.
 *
 * The button is [SchemaMismatchContinue], one file per variant, so a release binary does not carry
 * it or its string.
 *
 * ⚠️ This screen is the one nobody sees in testing, because every migration test opens the database
 * directly and walks straight past the gate that shows it. Verify an upgrade on a phone.
 */
@Composable
fun SchemaMismatchScreen(
    mismatch: SchemaMismatch,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.block),
    ) {
        Text(
            text = stringResource(R.string.schema_mismatch_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text =
                stringResource(
                    R.string.schema_mismatch_body,
                    mismatch.fromVersion,
                    mismatch.toVersion,
                ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.base),
        )
        Text(
            text = stringResource(R.string.schema_mismatch_copy, mismatch.preservedCopy.name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.base),
        )
        SchemaMismatchContinue(onContinue)
    }
}
