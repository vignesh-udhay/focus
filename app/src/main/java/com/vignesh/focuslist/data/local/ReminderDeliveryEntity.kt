package com.vignesh.focuslist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vignesh.focuslist.core.domain.DeliveryOutcome
import com.vignesh.focuslist.core.domain.ReminderDelivery
import java.time.Instant
import java.time.ZoneId

/**
 * The stored form of one reminder firing.
 *
 * A flat mirror of [ReminderDelivery], holding storage concerns only. It
 * carries no foreign key to `tasks` and no index on `taskId`, and both are
 * deliberate. The record has to survive its task being deleted, because a
 * reminder that arrived an hour late on a task the user then binned is still
 * evidence about the device.
 *
 * Every timestamp is a number rather than the ISO text `reminderAt` uses.
 * These are moments that happened, not wall-clock times a user chose, and
 * [scheduledElapsedAt] is not a date at all: it is milliseconds since boot,
 * from a clock with no epoch to render against.
 *
 * The two clocks are stored side by side rather than reduced to a difference
 * here, so a later reading of the same row can ask a question this version did
 * not think to.
 */
@Entity(tableName = "reminder_deliveries")
data class ReminderDeliveryEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val taskTitle: String,
    /** ISO-8601, matching how `TaskConverters` stores `reminderAt`. */
    val dueAt: String,
    val scheduledWallAt: Long,
    val scheduledElapsedAt: Long,
    val arrivedWallAt: Long,
    val arrivedElapsedAt: Long,
    val outcome: String
)

internal fun ReminderDeliveryEntity.toDomain(): ReminderDelivery {
    val scheduled = Instant.ofEpochMilli(scheduledWallAt)

    return ReminderDelivery(
        id = id,
        taskId = taskId,
        taskTitle = taskTitle,
        // The same soft parse the task converters use. A row this class cannot
        // read is a row about a reminder, not the reminder itself, so throwing
        // here would let one corrupt record take down the app that wrote it.
        // The moment the alarm was aimed at is the nearest true answer.
        dueAt = TaskConverters.textToLocalDateTime(dueAt)
            ?: scheduled.atZone(ZoneId.systemDefault()).toLocalDateTime(),
        scheduledWallAt = scheduled,
        scheduledElapsedAt = scheduledElapsedAt,
        arrivedWallAt = Instant.ofEpochMilli(arrivedWallAt),
        arrivedElapsedAt = arrivedElapsedAt,
        outcome = outcome.toOutcome()
    )
}

internal fun ReminderDelivery.toEntity(): ReminderDeliveryEntity = ReminderDeliveryEntity(
    id = id,
    taskId = taskId,
    taskTitle = taskTitle,
    dueAt = dueAt.toString(),
    scheduledWallAt = scheduledWallAt.toEpochMilli(),
    scheduledElapsedAt = scheduledElapsedAt,
    arrivedWallAt = arrivedWallAt.toEpochMilli(),
    arrivedElapsedAt = arrivedElapsedAt,
    outcome = outcome.name
)

/**
 * An unreadable outcome reads as [DeliveryOutcome.Suppressed].
 *
 * The pessimistic side. A row whose outcome cannot be parsed is a row about a
 * reminder whose fate is unknown, and treating unknown as delivered would hide
 * exactly the failure this table exists to catch.
 */
private fun String.toOutcome(): DeliveryOutcome =
    DeliveryOutcome.entries.firstOrNull { it.name == this } ?: DeliveryOutcome.Suppressed
