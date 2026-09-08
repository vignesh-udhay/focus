package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.core.content.edit
import com.vignesh.focuslist.core.design.ThemePreference
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two appearance choices the Settings screen owns. */
data class AppearancePreferences(
    val dynamicColor: Boolean = true,
    val theme: ThemePreference = ThemePreference.System
)

/**
 * Small process-scoped preferences store.
 *
 * Android already supplies durable key/value storage for these two scalar
 * values. Keeping a StateFlow beside it makes selection immediately visible to
 * Compose while SharedPreferences carries it across process death.
 */
class FocuslistPreferences(context: Context) {
    private val storage = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)

    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<AppearancePreferences> = mutableState.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        val updated = mutableState.value.copy(dynamicColor = enabled)
        storage.edit { putBoolean(DynamicColorKey, enabled) }
        mutableState.value = updated
    }

    fun setTheme(theme: ThemePreference) {
        val updated = mutableState.value.copy(theme = theme)
        storage.edit { putString(ThemeKey, theme.name) }
        mutableState.value = updated
    }

    /** Used by restore, where a failed disk write must fail the whole action. */
    @Suppress("UseKtx") // The extension discards commit()'s failure signal.
    @Throws(IOException::class)
    fun replace(preferences: AppearancePreferences) {
        val written = storage.edit()
            .putBoolean(DynamicColorKey, preferences.dynamicColor)
            .putString(ThemeKey, preferences.theme.name)
            .commit()

        if (!written) throw IOException("Could not store restored preferences")
        mutableState.value = preferences
    }

    private fun read(): AppearancePreferences {
        val theme = storage.getString(ThemeKey, null)
            ?.let { stored -> ThemePreference.entries.firstOrNull { it.name == stored } }
            ?: ThemePreference.System

        return AppearancePreferences(
            dynamicColor = storage.getBoolean(DynamicColorKey, true),
            theme = theme
        )
    }

    private companion object {
        const val FileName = "focuslist_preferences"
        const val DynamicColorKey = "dynamic_color"
        const val ThemeKey = "theme"
    }
}
