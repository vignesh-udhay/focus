package com.vignesh.focuslist.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vignesh.focuslist.core.design.FocuslistDarkColorScheme
import com.vignesh.focuslist.core.design.FocuslistLightColorScheme

/**
 * The Focuslist theme.
 *
 * Dynamic colour is the default, because a system-coloured task list feels
 * like part of the device, which `PRODUCT.md` asks for. Where the device
 * cannot supply a scheme, or where one is deliberately not wanted, the app
 * falls back to its own identity rather than to Material's baseline purple.
 * Both fallbacks come from one seed, so light and dark are the same product.
 *
 * The motion scheme is deliberately not passed. `MaterialExpressiveTheme`
 * defaults it to the expressive scheme, which is the one the design wants, and
 * naming it here would only be a second place for it to be wrong.
 */
@Composable
fun FocuslistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicAvailable = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicAvailable && darkTheme -> dynamicDarkColorScheme(context)
        dynamicAvailable && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> FocuslistDarkColorScheme
        else -> FocuslistLightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = FocuslistTypography,
        shapes = FocuslistShapes,
        content = content
    )
}