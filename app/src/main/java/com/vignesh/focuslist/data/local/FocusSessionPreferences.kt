package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.core.content.edit
import com.vignesh.focuslist.core.domain.FocusSession
import com.vignesh.focuslist.core.domain.FocusSessionStore
import com.vignesh.focuslist.core.domain.StoredFocusSession
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The single Focus session, persisted for process death and the home widget.
 *
 * SharedPreferences is sufficient here: this is four scalar values replaced
 * in one preference edit, not a collection or relational data. The task
 * itself remains in Room; this stores only the pointer and clock needed to
 * resume it.
 */
class FocusSessionPreferences(context: Context) : FocusSessionStore {

    private val preferences = context.getSharedPreferences(Name, Context.MODE_PRIVATE)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    override val current: StoredFocusSession?
        get() {
            val taskId = preferences.getString(TaskIdKey, null) ?: return null
            if (!preferences.contains(StartedAtKey)) return null

            return StoredFocusSession(
                taskId = taskId,
                session = FocusSession(
                    startedAt = Instant.ofEpochMilli(preferences.getLong(StartedAtKey, 0L)),
                    pausedAt = preferences.takeIf { it.contains(PausedAtKey) }
                        ?.getLong(PausedAtKey, 0L)
                        ?.let(Instant::ofEpochMilli),
                    extraMinutes = preferences.getInt(ExtraMinutesKey, 0)
                )
            )
        }

    override fun save(value: StoredFocusSession) {
        preferences.edit {
            putString(TaskIdKey, value.taskId)
            putLong(StartedAtKey, value.session.startedAt.toEpochMilli())
            putInt(ExtraMinutesKey, value.session.extraMinutes)
            val pausedAt = value.session.pausedAt
            if (pausedAt == null) remove(PausedAtKey)
            else putLong(PausedAtKey, pausedAt.toEpochMilli())
        }
        _changes.tryEmit(Unit)
    }

    override fun clear() {
        preferences.edit { clear() }
        _changes.tryEmit(Unit)
    }

    private companion object {
        const val Name = "focus_session"
        const val TaskIdKey = "task_id"
        const val StartedAtKey = "started_at"
        const val PausedAtKey = "paused_at"
        const val ExtraMinutesKey = "extra_minutes"
    }
}
