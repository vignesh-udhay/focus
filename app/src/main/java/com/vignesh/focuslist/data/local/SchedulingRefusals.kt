package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the latest reconciliation had an alarm refused by the system.
 *
 * `docs/decisions.md` D-079. D-078 made the scheduler carry on past a refusal
 * and log it; `AGENTS.md` says a scheduling failure must not be swallowed, and a
 * log is not something the user reads. This is what the exact-alarm health
 * check reads, so a refusal reaches the Today banner and the health screen.
 *
 * Replaced by every pass, so it clears itself the first time every alarm is
 * accepted again.
 */
class SchedulingRefusals(context: Context) {

    private val storage = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)

    /** Whether the latest pass had any alarm refused. */
    val refusedRecently: Boolean
        get() = storage.getBoolean(KeyRefused, false)

    /** Records the outcome of one pass. Written only when it changes. */
    fun record(failures: Int) {
        val refused = failures > 0
        if (refused != refusedRecently) storage.edit { putBoolean(KeyRefused, refused) }
    }

    private companion object {
        const val FileName = "reminder_scheduling"
        const val KeyRefused = "refused"
    }
}
