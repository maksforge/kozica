package app.kozica

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.kozica.ui.backup.BackupScreen
import app.kozica.ui.map.MapScreen
import app.kozica.ui.regions.RegionsScreen
import app.kozica.ui.settings.SettingsScreen
import app.kozica.ui.support.LicenceTextScreen
import app.kozica.ui.support.LicencesScreen
import app.kozica.ui.support.SupportScreen
import app.kozica.ui.welcome.WelcomeScreen

/**
 * What every back-stack entry is wrapped in — above all, **one `ViewModelStore` per entry**.
 *
 * Nav3 does not do this on its own. The ViewModel decorator ships in a separate artifact
 * (`lifecycle-viewmodel-navigation3`), which `navigation3-ui` does not depend on, so `NavDisplay`'s
 * default list cannot contain it. Without it every `viewModel()` resolves to the **Activity's**
 * store and outlives the screen that created it — an editor comes back with its `saved` flag still
 * set and bounces straight out of the second "Add" of a session, and only killing the process
 * clears it. It is a genuinely confusing bug, because the screen is correct and its state is not.
 *
 * `rememberSaveableStateHolderNavEntryDecorator` is Nav3's own default, restated because passing a list
 * replaces the defaults rather than adding to them.
 *
 * Extracted from [MainNavigation] so a test can assert the scoping directly.
 */
@Composable
internal fun appEntryDecorators(): List<NavEntryDecorator<NavKey>> =
    listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

/**
 * The app shell: a [NavDisplay] rooted at [Home], so Back from anywhere walks back there and Back
 * from there exits. The first launch shows the welcome screen in its place until it is answered. No bottom bar until phase 5 brings the tabs that fill it.
 */
@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    val activity = LocalActivity.current
    val app = LocalContext.current.applicationContext as MainApplication
    // Null only before a cold start's first read lands, which draws nothing rather than flash the map.
    val onboardingDone by app.onboardingDone.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { insets ->
        when (onboardingDone) {
            null -> Unit
            false ->
                WelcomeScreen(
                    onDownload = { backStack.add(Regions) },
                    modifier = Modifier.padding(insets).consumeWindowInsets(insets),
                )
            true ->
                NavDisplay(
                    backStack = backStack,
                    // **The keyboard is an inset like any other, and this Scaffold owns them.**
                    //
                    // `MainActivity` sets `decorFitsSystemWindows = false` for edge-to-edge, which makes the
                    // manifest's `adjustResize` inoperative from API 30 — the window manager downgrades it
                    // to a *pan*. With nothing consuming `WindowInsets.ime`, the system then pans the whole
                    // window to keep the focused field in view, which slides the top of a long form under
                    // the status bar and pushes its top bar — Save included — off the screen entirely.
                    //
                    // `consumeWindowInsets` before `imePadding` is what stops it double-counting: the
                    // keyboard's inset is measured from the bottom of the *screen*, so it already contains
                    // the navigation-bar height that `padding(insets)` just applied. Consuming says "that
                    // part is handled", leaving `imePadding` to add only the rest.
                    modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding(),
                    entryDecorators = appEntryDecorators(),
                    onBack = {
                        if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish()
                    },
                    // Kotlin note: this is a builder DSL, not a map literal — `entry<Home> { … }` registers
                    // the composable that renders that key, and the lambda receives the key itself, which is
                    // how a key carrying arguments passes them in.
                    entryProvider =
                        entryProvider {
                            entry<Home> {
                                MapScreen(
                                    onOpenSettings = { backStack.add(Settings) },
                                    onOpenRegions = { backStack.add(Regions) },
                                )
                            }
                            entry<Settings> {
                                SettingsScreen(
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenRegions = { backStack.add(Regions) },
                                    onOpenLicences = { backStack.add(Licences) },
                                    onOpenSupport = { backStack.add(Support) },
                                )
                            }
                            entry<Regions> {
                                RegionsScreen(onBack = { backStack.removeLastOrNull() })
                            }
                            entry<Backup> {
                                BackupScreen(onBack = { backStack.removeLastOrNull() })
                            }
                            entry<Support> {
                                SupportScreen(onBack = { backStack.removeLastOrNull() })
                            }
                            entry<Licences> {
                                LicencesScreen(
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenLicence = { spdxId -> backStack.add(LicenceText(spdxId)) },
                                )
                            }
                            entry<LicenceText> { key ->
                                LicenceTextScreen(
                                    spdxId = key.spdxId,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        },
                )
        }
    }
}
