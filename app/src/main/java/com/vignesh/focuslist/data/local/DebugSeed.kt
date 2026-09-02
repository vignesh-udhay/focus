package com.vignesh.focuslist.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.TaskPlacement
import java.time.Instant
import java.time.LocalDate

/**
 * Fixture tasks, written once when a debug build creates its database.
 *
 * Running the instrumented tests reinstalls the app, and reinstalling clears
 * its data. Without this, every test run costs whoever is developing the
 * afternoon's worth of tasks they had been trying things against, and the next
 * hour is spent typing them back in. The seed makes that free.
 *
 * Guarded on the build being debuggable rather than on `BuildConfig`, which
 * this module does not generate. A release install creates its database the
 * same way and gets nothing.
 *
 * Written through raw SQL in [RoomDatabase.Callback.onCreate], which is the
 * only point where the database exists but nothing has read it yet. The DAO is
 * not usable here: the database is still being opened, and asking Room for it
 * would deadlock.
 *
 * Deliberately only `onCreate`. Seeding on open would refill the list every
 * launch and make deleting a task impossible to test.
 */
internal fun debugSeedCallback(isDebuggable: Boolean): RoomDatabase.Callback =
    object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            if (!isDebuggable) return

            val today = LocalDate.now()
            val createdAt = Instant.now().toEpochMilli()

            seedTasks(today).forEachIndexed { index, task ->
                db.execSQL(InsertTask, task.asRow(index, createdAt))
            }
        }
    }

/**
 * A task as the fixture describes it, before it is a row.
 *
 * Dates are offsets from today rather than fixed days, so the seed still means
 * something in a month: "overdue" has to still be overdue for the Today screen
 * to have anything to sort into its overdue band.
 */
private data class SeedTask(
    val title: String,
    val placement: TaskPlacement,
    val scheduledDate: LocalDate? = null,
    val estimatedDurationMinutes: Int? = null,
    val recurrence: Recurrence? = null,
    val completedAt: Instant? = null
)

/**
 * Enough to exercise every band the lists sort into: today with and without an
 * estimate, overdue, upcoming, undated inbox, the two triage placements, and
 * something already finished for the Logbook.
 *
 * Two of them repeat, on a daily and a monthly rule, so that completing a task
 * and watching the next one appear is something the seed can show without
 * anyone having to set a rule up by hand first. The monthly one is dated the
 * 28th so it is never a month-end date the next month does not have, which is
 * the case `Recurrence.nextOccurrence` handles and the seed should not be
 * quietly relying on.
 */
private fun seedTasks(today: LocalDate): List<SeedTask> = listOf(
    SeedTask("Review the quarterly budget", TaskPlacement.INBOX, today, 45),
    SeedTask("Call the plumber about the leak", TaskPlacement.INBOX, today, 15),
    SeedTask("Reply to Priya about the contract", TaskPlacement.INBOX, today, 20),
    SeedTask("Book the dentist", TaskPlacement.INBOX, today),
    SeedTask("Renew the car insurance", TaskPlacement.INBOX, today.minusDays(5), 30),
    SeedTask("Send the invoice to Meridian", TaskPlacement.INBOX, today.minusDays(3), 10),
    SeedTask("Quarterly review with the team", TaskPlacement.INBOX, today.plusDays(3), 60),
    SeedTask("Flight to Berlin", TaskPlacement.INBOX, today.plusDays(7)),
    SeedTask("Read the Compose performance notes", TaskPlacement.INBOX),
    SeedTask(
        title = "Water the plants",
        placement = TaskPlacement.ANYTIME,
        scheduledDate = today,
        estimatedDurationMinutes = 5,
        recurrence = Recurrence.DAILY
    ),
    SeedTask(
        title = "Pay the rent",
        placement = TaskPlacement.INBOX,
        scheduledDate = today.withDayOfMonth(28),
        recurrence = Recurrence.MONTHLY
    ),
    SeedTask("Learn to make sourdough", TaskPlacement.SOMEDAY),
    SeedTask(
        title = "Submit the expense report",
        placement = TaskPlacement.INBOX,
        scheduledDate = today.minusDays(4),
        estimatedDurationMinutes = 25,
        completedAt = Instant.now()
    )
)

private fun SeedTask.asRow(index: Int, createdAt: Long): Array<Any?> = arrayOf(
    "seed-$index",
    title,
    null,
    placement.name,
    // Spaced so the stored order is stable and the lists have something to
    // sort by other than the order rows happened to be written in.
    createdAt - (seedTaskCount - index) * MillisPerMinute,
    scheduledDate?.toEpochDay(),
    null,
    estimatedDurationMinutes,
    recurrence?.name,
    completedAt?.toEpochMilli(),
    null
)

private const val InsertTask =
    "INSERT INTO tasks (id, title, notes, placement, createdAt, scheduledDate, " +
        "dueDate, estimatedDurationMinutes, recurrence, completedAt, deletedAt) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

private const val MillisPerMinute = 60_000L

private val seedTaskCount = seedTasks(LocalDate.EPOCH).size
