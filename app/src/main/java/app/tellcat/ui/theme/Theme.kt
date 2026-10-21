package app.tellcat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.tellcat.core.design.TellcatDarkColorScheme
import app.tellcat.core.design.TellcatLightColorScheme

/**
 * The Tellcat theme.
 *
 * The Tellcat scheme is the default since D-077, so the app has the same
 * colour on every phone, in the store and in onboarding. Dynamic colour is one
 * switch away for anyone who wants the wallpaper's colours instead. Light and
 * dark come from one seed, generated in HCT since D-085, so they are the same
 * product.
 *
 * Colour is the only thing this theme supplies. The type ramp, the corner
 * scale and the motion scheme are all left to `MaterialExpressiveTheme`,
 * which already defaults them to the expressive values the design is drawn
 * against. Restating any of them here would only be a second place for them
 * to be wrong, and an app that redraws the system's corners and weights
 * stops reading as an Android app and starts reading as a skin. See D-008.
 *
 * Emphasis is a type role, not a weight override: reach for
 * `titleLargeEmphasized` rather than copying `titleLarge` with a heavier
 * weight. That is what the emphasized roles are for.
 */
@Composable
fun TellcatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off unless asked for, as the app itself is since D-077.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicAvailable = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicAvailable && darkTheme -> dynamicDarkColorScheme(context)
        dynamicAvailable && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> TellcatDarkColorScheme
        else -> TellcatLightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        content = content
    )
}
