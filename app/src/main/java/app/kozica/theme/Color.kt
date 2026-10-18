package app.kozica.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// GENERATED — do not hand-edit individual roles.
//
// Both schemes are derived from four brand seeds by Material's tonal palette
// construction (CAM16/HCT): the seeds are the judgement, the ~36 roles are
// arithmetic. Hand-picking a role breaks the fixed tonal relationship it holds
// with the rest of its family, and the failure shows up as a contrast bug on one
// screen out of thirty rather than as a visible mistake here.
//
//   primary   #A86B32  — brand: filled buttons, FAB, selected tab
//   secondary #7D7064  — quiet containers: chips, tonal buttons, selected segment
//   tertiary  #56646E  — accents and caution markers, used sparingly
//   neutral   #8A847C  — seeds every surface, so it decides warm or cool overall
//
// Error stays M3 baseline (hue 25, chroma 84): a safety signal, not a brand choice.
//
// To change the palette, edit the seeds in scripts/gen_scheme.py and re-run it.
// Colours come from MaterialTheme, never literals, and dynamic colour defaults
// off (ADR-0006) so this file is what a user actually sees.

private val AppLightColors =
    lightColorScheme(
        primary = Color(0xFF88511A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDCBF),
        onPrimaryContainer = Color(0xFF2F1500),
        inversePrimary = Color(0xFFFFB777),
        secondary = Color(0xFF685C51),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF0DFD1),
        onSecondaryContainer = Color(0xFF231A11),
        tertiary = Color(0xFF52606A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD6E5F1),
        onTertiaryContainer = Color(0xFF0F1D25),
        error = Color(0xFFBA1B1B),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD4),
        onErrorContainer = Color(0xFF410001),
        background = Color(0xFFFFF8EF),
        onBackground = Color(0xFF1E1B16),
        surface = Color(0xFFFFF8EF),
        onSurface = Color(0xFF1E1B16),
        surfaceVariant = Color(0xFFEDE1CF),
        onSurfaceVariant = Color(0xFF4D4639),
        surfaceDim = Color(0xFFE1D9CF),
        surfaceBright = Color(0xFFFFF8EF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFBF2E9),
        surfaceContainer = Color(0xFFF5EDE3),
        surfaceContainerHigh = Color(0xFFEFE7DD),
        surfaceContainerHighest = Color(0xFFE9E1D8),
        inverseSurface = Color(0xFF343029),
        inverseOnSurface = Color(0xFFF7EFE5),
        outline = Color(0xFF7F7667),
        outlineVariant = Color(0xFFD0C5B4),
        scrim = Color(0xFF000000),
    )

private val AppDarkColors =
    darkColorScheme(
        primary = Color(0xFFFFB777),
        onPrimary = Color(0xFF4D2700),
        primaryContainer = Color(0xFF6C3A02),
        onPrimaryContainer = Color(0xFFFFDCBF),
        inversePrimary = Color(0xFF88511A),
        secondary = Color(0xFFD4C4B6),
        onSecondary = Color(0xFF392F25),
        secondaryContainer = Color(0xFF50453A),
        onSecondaryContainer = Color(0xFFF0DFD1),
        tertiary = Color(0xFFBAC9D4),
        onTertiary = Color(0xFF24323B),
        tertiaryContainer = Color(0xFF3B4952),
        onTertiaryContainer = Color(0xFFD6E5F1),
        error = Color(0xFFFFB4A9),
        onError = Color(0xFF680003),
        errorContainer = Color(0xFF930006),
        onErrorContainer = Color(0xFFFFDAD4),
        background = Color(0xFF16130E),
        onBackground = Color(0xFFE9E1D8),
        surface = Color(0xFF16130E),
        onSurface = Color(0xFFE9E1D8),
        surfaceVariant = Color(0xFF4D4639),
        onSurfaceVariant = Color(0xFFD0C5B4),
        surfaceDim = Color(0xFF16130E),
        surfaceBright = Color(0xFF3D3932),
        surfaceContainerLowest = Color(0xFF110E09),
        surfaceContainerLow = Color(0xFF1E1B16),
        surfaceContainer = Color(0xFF221F19),
        surfaceContainerHigh = Color(0xFF2D2923),
        surfaceContainerHighest = Color(0xFF38342E),
        inverseSurface = Color(0xFFE9E1D8),
        inverseOnSurface = Color(0xFF343029),
        outline = Color(0xFF998F7F),
        outlineVariant = Color(0xFF4D4639),
        scrim = Color(0xFF000000),
    )

internal val LightColors = AppLightColors
internal val DarkColors = AppDarkColors
