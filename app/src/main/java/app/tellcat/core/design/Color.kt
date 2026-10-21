package app.tellcat.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Tellcat colour identity.
 *
 * What a fresh install draws in since `docs/decisions.md` D-077, below API 31,
 * and in every preview. Dynamic colour replaces it only when the user turns that
 * on in Settings.
 *
 * Both schemes are generated from one seed, so light and dark are recognisably
 * the same product rather than two palettes that happen to share a layout.
 *
 * Do not read these directly. Screens and components read
 * [androidx.compose.material3.MaterialTheme.colorScheme], which resolves to
 * either these or the dynamic scheme; a component naming a colour here would
 * ignore the user's device.
 */

/** The Tellcat brand seed. Every tone below is derived from this one colour. */
val TellcatSeed: Color = Color(0xFF4F5DFF)

/**
 * The light scheme.
 *
 * **The primary and secondary palettes are generated in HCT**, Material's own
 * colour space, at [TellcatSeed]'s hue of 282, with tone stepped as Material
 * steps it. `docs/decisions.md` D-085. They used to be generated in CIELAB at a
 * primary chroma of 48, and the two choices together cost the app its colour:
 * CIELAB bends blues toward purple as their chroma falls, which carried the hue
 * to 290, and 48 is Material's own stock chroma, so every role landed within
 * ΔE 6 of Material's baseline purple scheme. The seed is an indigo; the app was
 * drawing Material's default.
 *
 * Light primary is chroma 70, so the accent reads as the seed's indigo rather
 * than a muted violet. The containers stay calm at 16 to 20, which is the
 * restraint the old cap was buying, and the dark primary container is chroma
 * 52, rich rather than neon. Every text pairing is at least 6.2:1.
 *
 * The tertiary, error and neutral palettes, and the three fixed roles below,
 * are unchanged by D-085: overdue keeps its colour, and the cat keeps its coat.
 *
 * They are written out rather than generated at run time because generating
 * them needs `material-color-utilities`, which this project does not depend
 * on, and because a fallback palette is a fixed design decision with no
 * business being recomputed on every launch.
 *
 * Light `onPrimaryContainer` is tone 30, not tone 10. It was `#001356`, which
 * is tone 10 and reads blue rather than indigo — a hue the primary palette does
 * not otherwise contain. Current M3 puts light on-primary-container at tone 30;
 * Material's own baseline uses `#4F378A`. Tone 30 here is `#403D89`, which is
 * also the dark scheme's `primaryContainer`, and M3 expects those two to match.
 * This is what the Focus now card's text resolves through.
 *
 * Both schemes set the three fixed roles the mascot illustrations are drawn
 * against: `primaryFixed` (P90), `primaryFixedDim` (P80) and
 * `onPrimaryFixedVariant` (P30). Fixed roles hold the same value in light and
 * dark by definition, which is why they carry identical hex in both schemes
 * below; that is the point of them, not a copy-paste slip. Dynamic colour
 * supplies its own, so these only apply to the fallback.
 */
val TellcatLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3D4BDC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF313AA1),
    inversePrimary = Color(0xFFBEC2FF),
    secondary = Color(0xFF5C5D71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF191A2B),
    tertiary = Color(0xFF834F60),
    onTertiary = Color(0xFFFFFEFF),
    tertiaryContainer = Color(0xFFFFD7E3),
    onTertiaryContainer = Color(0xFF370A1D),
    error = Color(0xFFC10030),
    onError = Color(0xFFFFFEFD),
    errorContainer = Color(0xFFFFD8D6),
    onErrorContainer = Color(0xFF430002),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE4E0F0),
    onSurfaceVariant = Color(0xFF484551),
    surfaceTint = Color(0xFF3D4BDC),
    inverseSurface = Color(0xFF313035),
    inverseOnSurface = Color(0xFFF1F0F7),
    outline = Color(0xFF787583),
    outlineVariant = Color(0xFFC8C5D3),
    scrim = Color(0xFF010009),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    primaryFixed = Color(0xFFE5DEFF),
    primaryFixedDim = Color(0xFFCABEFF),
    onPrimaryFixedVariant = Color(0xFF403D89),
)

/** The dark fallback, from the same seed and the same palettes. */
val TellcatDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF1E2678),
    primaryContainer = Color(0xFF343C98),
    onPrimaryContainer = Color(0xFFE0E0FF),
    inversePrimary = Color(0xFF3D4BDC),
    secondary = Color(0xFFC5C4DC),
    onSecondary = Color(0xFF2E2F41),
    secondaryContainer = Color(0xFF444558),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFF2B7C8),
    onTertiary = Color(0xFF502132),
    tertiaryContainer = Color(0xFF693848),
    onTertiaryContainer = Color(0xFFFFD7E3),
    error = Color(0xFFFFB2AF),
    onError = Color(0xFF6A0015),
    errorContainer = Color(0xFF950022),
    onErrorContainer = Color(0xFFFFD8D6),
    background = Color(0xFF141318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF141318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF484551),
    onSurfaceVariant = Color(0xFFC8C5D3),
    surfaceTint = Color(0xFFBEC2FF),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF313035),
    outline = Color(0xFF928F9D),
    outlineVariant = Color(0xFF484551),
    scrim = Color(0xFF010009),
    surfaceBright = Color(0xFF3A383E),
    surfaceDim = Color(0xFF141318),
    surfaceContainerLowest = Color(0xFF0F0D14),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F24),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
    primaryFixed = Color(0xFFE5DEFF),
    primaryFixedDim = Color(0xFFCABEFF),
    onPrimaryFixedVariant = Color(0xFF403D89),
)
