package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.core.content.edit
import com.vignesh.focuslist.core.notification.ScheduledReminder
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * The reminders that have alarms, kept where Android lets the app read them
 * before the first unlock after a restart. `docs/decisions.md` D-079.
 *
 * The database lives in credential-encrypted storage, which cannot be opened
 * until the user unlocks. Without this copy, a reminder due between a restart
 * and that unlock was held and then delivered late. Device-protected storage is
 * readable from boot, so the recovery receiver can set the alarms from here and
 * the alarm receiver can say what each one is for.
 *
 * Two lists. The upcoming reminders, replaced whole by every reconciliation
 * while the phone is unlocked. And the reminders announced while it was still
 * locked, which the database could not be told about at the time; the
 * application applies them as soon as it can and clears them.
 *
 * The titles are the cost, and D-079 says it plainly: they sit under a key the
 * device holds rather than one derived from the user's PIN.
 */
class LockedReminderStore(context: Context) {

    private val storage = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(FileName, Context.MODE_PRIVATE)

    /** Replaces the upcoming list with what the latest pass scheduled. */
    fun replaceUpcoming(reminders: List<ScheduledReminder>) {
        val encoded = encode(reminders)

        // Unchanged lists are common, since most passes follow an edit that did
        // not touch a reminder, and a write is not free.
        if (storage.getString(KeyUpcoming, null) == encoded) return
        storage.edit { putString(KeyUpcoming, encoded) }
    }

    /** The reminders the latest unlocked pass gave alarms to. */
    fun upcoming(): List<ScheduledReminder> =
        storage.getString(KeyUpcoming, null)?.let { json ->
            JSONArray(json).objects().map { item ->
                ScheduledReminder(
                    taskId = item.getString(KeyTaskId),
                    title = item.getString(KeyTitle),
                    ringsAt = LocalDateTime.parse(item.getString(KeyRingsAt))
                )
            }
        }.orEmpty()

    /**
     * Records a reminder announced while the phone was locked, and takes it off
     * the upcoming list so nothing sets its alarm again before the unlock.
     *
     * `commit` rather than `apply`: this runs in a receiver whose process may be
     * gone a moment later, and a lost record means the reminder is announced a
     * second time after the unlock.
     */
    fun recordLockedDelivery(delivery: LockedDelivery) {
        val deliveries = JSONArray(storage.getString(KeyDeliveries, null) ?: "[]").apply {
            put(
                JSONObject()
                    .put(KeyTaskId, delivery.taskId)
                    .put(KeyTitle, delivery.title)
                    .put(KeyRingsAt, delivery.ringsAt.toString())
                    .put(KeyScheduledWall, delivery.scheduledWallAt.toEpochMilli())
                    .put(KeyScheduledElapsed, delivery.scheduledElapsedAt)
                    .put(KeyArrivedWall, delivery.arrivedWallAt.toEpochMilli())
                    .put(KeyArrivedElapsed, delivery.arrivedElapsedAt)
                    .put(KeyAhead, delivery.scheduledAhead.toMillis())
            )
        }
        val remaining = upcoming().filterNot { it.taskId == delivery.taskId }

        storage.edit(commit = true) {
            putString(KeyDeliveries, deliveries.toString())
            putString(KeyUpcoming, encode(remaining))
        }
    }

    /** What was announced while locked and has not been applied yet. */
    fun lockedDeliveries(): List<LockedDelivery> =
        storage.getString(KeyDeliveries, null)?.let { json ->
            JSONArray(json).objects().map { item ->
                LockedDelivery(
                    taskId = item.getString(KeyTaskId),
                    title = item.getString(KeyTitle),
                    ringsAt = LocalDateTime.parse(item.getString(KeyRingsAt)),
                    scheduledWallAt = Instant.ofEpochMilli(item.getLong(KeyScheduledWall)),
                    scheduledElapsedAt = item.getLong(KeyScheduledElapsed),
                    arrivedWallAt = Instant.ofEpochMilli(item.getLong(KeyArrivedWall)),
                    arrivedElapsedAt = item.getLong(KeyArrivedElapsed),
                    scheduledAhead = Duration.ofMillis(item.getLong(KeyAhead))
                )
            }
        }.orEmpty()

    /** Forgets the locked deliveries once the database has been told. */
    fun clearLockedDeliveries() {
        storage.edit(commit = true) { remove(KeyDeliveries) }
    }

    private fun encode(reminders: List<ScheduledReminder>): String = JSONArray().apply {
        reminders.forEach { reminder ->
            put(
                JSONObject()
                    .put(KeyTaskId, reminder.taskId)
                    .put(KeyTitle, reminder.title)
                    .put(KeyRingsAt, reminder.ringsAt.toString())
            )
        }
    }.toString()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private companion object {
        const val FileName = "locked_reminders"
        const val KeyUpcoming = "upcoming"
        const val KeyDeliveries = "deliveries"
        const val KeyTaskId = "taskId"
        const val KeyTitle = "title"
        const val KeyRingsAt = "ringsAt"
        const val KeyScheduledWall = "scheduledWall"
        const val KeyScheduledElapsed = "scheduledElapsed"
        const val KeyArrivedWall = "arrivedWall"
        const val KeyArrivedElapsed = "arrivedElapsed"
        const val KeyAhead = "ahead"
    }
}

/**
 * A reminder announced before the first unlock, with what the delivery record
 * needs once the database can be written. Both clocks, as `AGENTS.md` requires.
 */
data class LockedDelivery(
    val taskId: String,
    val title: String,
    val ringsAt: LocalDateTime,
    val scheduledWallAt: Instant,
    val scheduledElapsedAt: Long,
    val arrivedWallAt: Instant,
    val arrivedElapsedAt: Long,
    val scheduledAhead: Duration
)
