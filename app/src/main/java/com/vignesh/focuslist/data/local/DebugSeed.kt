package com.vignesh.focuslist.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import java.time.DayOfWeek
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
    val scheduledDate: LocalDate? = null,
    val estimatedDurationMinutes: Int? = null,
    val recurrence: RecurrenceUnit? = null,
    val recurrenceInterval: Int? = null,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val completedAt: Instant? = null
)

/**
 * Enough to exercise every band the lists sort into: today with and without an
 * estimate, overdue, upcoming, undated inbox, and something already finished
 * for the Logbook.
 *
 * The monthly task repeats so that completing a task and watching the next one
 * appear is something the seed can show without anyone having to set a rule up
 * by hand first. It is dated the 28th so it is never a month-end date the next
 * month does not have, which is the case `Recurrence.nextOccurrence` handles
 * and the seed should not be quietly relying on.
 */
private fun seedTasks(today: LocalDate): List<SeedTask> = listOf(
    SeedTask("Review the quarterly budget", today, 45),
    SeedTask("Call the plumber about the leak", today, 15),
    SeedTask("Reply to Priya about the contract", today, 20),
    // No estimate, so Focus can be entered open-ended without editing a task
    // first. It is the only way to reach two of the six states.
    SeedTask("Book the dentist", today),
    // One minute, so Estimate reached is a state someone can sit and watch
    // arrive. Every other estimate here is fifteen minutes or more, which made
    // the slowest state to reach the one D-046 most wants looking at: the cat
    // has to stay asleep when the clock runs out.
    SeedTask("Steep the tea", today, 1),
    SeedTask("Renew the car insurance", today.minusDays(5), 30),
    SeedTask("Send the invoice to Meridian", today.minusDays(3), 10),
    SeedTask("Quarterly review with the team", today.plusDays(3), 60),
    SeedTask("Flight to Berlin", today.plusDays(7)),
    SeedTask("Read the Compose performance notes"),
    SeedTask(
        title = "Pay the rent",
        scheduledDate = today.withDayOfMonth(28),
        recurrence = RecurrenceUnit.MONTHLY
    ),
    // A weekday rule, so the seed can show the one thing D-027 added that the
    // four periods could never express. Without it the editor has to be driven
    // by hand before it can be looked at.
    SeedTask(
        title = "Team standup",
        scheduledDate = today,
        estimatedDurationMinutes = 15,
        recurrence = RecurrenceUnit.WEEKLY,
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    ),
    SeedTask(
        title = "Submit the expense report",
        scheduledDate = today.minusDays(4),
        estimatedDurationMinutes = 25,
        completedAt = Instant.now()
    )
)

private fun SeedTask.asRow(index: Int, createdAt: Long): Array<Any?> = arrayOf(
    "seed-$index",
    title,
    null,
    // Spaced so the stored order is stable and the lists have something to
    // sort by other than the order rows happened to be written in.
    createdAt - (seedTaskCount - index) * MillisPerMinute,
    scheduledDate?.toEpochDay(),
    null,
    estimatedDurationMinutes,
    recurrence?.name,
    recurrenceInterval,
    weekdays.takeIf { it.isNotEmpty() }
        ?.sortedBy(DayOfWeek::getValue)
        ?.joinToString(",", transform = DayOfWeek::name),
    completedAt?.toEpochMilli(),
    null
)

// The columns not named here take their declared defaults, which is what
// `occurrenceNumber` wants: a seeded task is the first of its series.
private const val InsertTask =
    "INSERT INTO tasks (id, title, notes, createdAt, scheduledDate, " +
        "dueDate, estimatedDurationMinutes, recurrence, recurrenceInterval, " +
        "recurrenceWeekdays, completedAt, deletedAt) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

private const val MillisPerMinute = 60_000L

private val seedTaskCount = seedTasks(LocalDate.ofEpochDay(0)).size
