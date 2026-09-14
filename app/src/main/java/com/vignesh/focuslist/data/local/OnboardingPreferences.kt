package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app has passed the one first-run screen.
 *
 * This is app state, not user data. It intentionally lives outside
 * [FocuslistPreferences], whose appearance choices are included in backup and
 * restore. Restoring a Catimo JSON backup must not decide whether the app has
 * seen its own introduction.
 */
class OnboardingPreferences(
    context: Context,
    fileName: String = FileName
) {
    private val storage = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    private val mutableCompleted = MutableStateFlow(storage.getBoolean(CompletedKey, false))
    val completed: StateFlow<Boolean> = mutableCompleted.asStateFlow()

    fun complete() {
        storage.edit { putBoolean(CompletedKey, true) }
        mutableCompleted.value = true
    }

    private companion object {
        const val FileName = "onboarding_preferences"
        const val CompletedKey = "completed"
    }
}
