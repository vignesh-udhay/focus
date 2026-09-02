package com.vignesh.focuslist.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Focuslist colour identity, for when the device does not supply one.
 *
 * Dynamic colour is the default and is what most users see. These schemes are
 * the fallback: below API 31, wherever dynamic colour is switched off, and in
 * every preview. Without them the app falls back to the Material baseline
 * purple, which is nobody's brand.
 *
 * Both schemes are generated from one seed, so light and dark are recognisably
 * the same product rather than two palettes that happen to share a layout.
 *
 * Do not read these directly. Screens and components read
 * [androidx.compose.material3.MaterialTheme.colorScheme], which resolves to
 * either these or the dynamic scheme; a component naming a colour here would
 * ignore the user's device.
 */

/** The Focuslist brand seed. Every tone below is derived from this one colour. */
val FocuslistSeed: Color = Color(0xFF4F5DFF)

/**
 * The light fallback.
 *
 * Tones were derived from [FocuslistSeed] by holding its hue and chroma and
 * varying CIE L*, the quantity Material calls tone, then clamping each result
 * into sRGB. The secondary, tertiary, neutral, neutral variant and error
 * palettes use Material's usual chroma relationships to that hue.
 *
 * The seed itself is unusually saturated, at a chroma near 94. The primary
 * palette is generated at 48 instead, which keeps the indigo recognisable
 * while dropping the neon: carried through at full chroma, the dark scheme's
 * primary container came out a vivid blue that read as loud rather than calm.
 *
 * They are written out rather than generated at run time because generating
 * them needs `material-color-utilities`, which this project does not depend
 * on, and because a fallback palette is a fixed design decision with no
 * business being recomputed on every launch.
 */
val FocuslistLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF5B54A3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF001356),
    inversePrimary = Color(0xFFCABEFF),
    secondary = Color(0xFF605B75),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5DFFD),
    onSecondaryContainer = Color(0xFF1C192E),
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
    surfaceTint = Color(0xFF5B54A3),
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
)

/** The dark fallback, from the same seed and the same palettes. */
val FocuslistDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFCABEFF),
    onPrimary = Color(0xFF25276F),
    primaryContainer = Color(0xFF403D89),
    onPrimaryContainer = Color(0xFFE5DEFF),
    inversePrimary = Color(0xFF5B54A3),
    secondary = Color(0xFFC9C3E0),
    onSecondary = Color(0xFF312D45),
    secondaryContainer = Color(0xFF48445C),
    onSecondaryContainer = Color(0xFFE5DFFD),
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
    surfaceTint = Color(0xFFCABEFF),
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
)
