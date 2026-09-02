package com.vignesh.focuslist.core.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * What day it is, as far as the application is concerned.
 *
 * Today and Upcoming are derived from this rather than from the clock directly,
 * so the date the app is working against is one value with one owner. Reading
 * `LocalDate.now()` at each call site would be the same thing said in many
 * places, and would drift.
 *
 * It is a [StateFlow] rather than a plain [LocalDate] because the answer
 * changes while the app is running. An app left open across midnight has to
 * notice.
 */
interface CurrentDay {

    /** The current calendar day, re-emitted whenever it changes. */
    val today: StateFlow<LocalDate>
}

/**
 * The real day, from the device.
 *
 * Updates from two directions, because neither alone is enough:
 *
 * - the system's date, time, and time-zone broadcasts, which cover an app left
 *   open across midnight, a manually changed clock, and travel across a
 *   time-zone boundary;
 * - [refresh], which the Activity calls when it resumes, covering the case
 *   where the app was backgrounded across midnight and the process was frozen
 *   or throttled while the broadcast went out.
 *
 * There is no polling. Nothing wakes up on a timer to ask what day it is.
 *
 * The receiver is registered for the life of the process, alongside the
 * database, and is deliberately never unregistered: the day is a
 * process-scoped fact, not a screen-scoped one.
 */
class SystemCurrentDay(context: Context) : CurrentDay {

    private val _today = MutableStateFlow(LocalDate.now())

    override val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val dateChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        ContextCompat.registerReceiver(
            context.applicationContext,
            dateChanged,
            filter,
            // Only the system sends these, so nothing else may reach us.
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Re-reads the clock.
     *
     * Assigning the same date is a no-op for a [StateFlow], so calling this on
     * every resume costs nothing on the days when nothing changed.
     */
    fun refresh() {
        _today.value = LocalDate.now()
    }
}
