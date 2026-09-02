package com.vignesh.focuslist

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.room.Room
import com.vignesh.focuslist.data.local.FocuslistDatabase
import com.vignesh.focuslist.data.local.FocuslistMigrations
import com.vignesh.focuslist.data.local.debugSeedCallback
import com.vignesh.focuslist.core.notification.AndroidFocusAlarms
import com.vignesh.focuslist.core.notification.FocusAlarms
import com.vignesh.focuslist.core.time.SystemCurrentDay
import com.vignesh.focuslist.data.repository.TaskRepository

/**
 * The application-level composition root.
 *
 * It owns one database and one repository for the life of the process, which
 * is what keeps a single connection pool alive across configuration changes.
 * Both are created lazily, so nothing touches disk until something asks for a
 * task.
 *
 * The current day is owned here too, for the same reason: one answer for the
 * whole process, alive across configuration changes, and updating while the
 * app runs rather than being fixed when a screen was built.
 *
 * Deliberately not a service locator: it holds these three objects and nothing
 * else.
 */
class FocuslistApplication : Application() {

    val database: FocuslistDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            FocuslistDatabase::class.java,
            DATABASE_NAME
        )
            // No destructive fallback. A missing migration should fail loudly
            // rather than quietly empty someone's task list.
            .addMigrations(*FocuslistMigrations)
            // Fires only when the database is first created, which on a debug
            // build is every reinstall the instrumented tests cause. A release
            // install reaches the same line and is handed nothing.
            .addCallback(debugSeedCallback(isDebuggable))
            .build()
    }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    /**
     * Whether this install is a debug build.
     *
     * Read from the manifest flag rather than `BuildConfig`, which this module
     * does not generate, and which would mean turning on a build feature for
     * one boolean.
     */
    private val isDebuggable: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Eager, so the date broadcasts are being listened for from the start. */
    val currentDay: SystemCurrentDay by lazy { SystemCurrentDay(this) }

    /**
     * How a focus session reaches the user once they have left the app.
     *
     * Owned here for the same reason as the day: it is process-scoped, holds no
     * screen state, and one owner means one outstanding alarm.
     */
    val focusAlarms: FocusAlarms by lazy { AndroidFocusAlarms(this) }

    companion object {
        const val DATABASE_NAME = "focuslist.db"
    }
}
