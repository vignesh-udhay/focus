package app.tellcat.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.tellcat.core.notification.ScheduledReminder
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The copy of upcoming reminders kept for a locked phone. `docs/decisions.md`
 * D-079.
 *
 * Instrumented because it is device-protected storage, which only a device has.
 * It reads and writes the app's real file, so it clears it before and after,
 * and it must only ever run on an emulator.
 */
class LockedReminderStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun store() = LockedReminderStore(context)

    private val call = ScheduledReminder("a", "Call mom", LocalDateTime.of(2026, 10, 11, 7, 0))
    private val water = ScheduledReminder("b", "Water plants", LocalDateTime.of(2026, 10, 11, 8, 0))

    @Before
    @After
    fun clear() {
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("locked_reminders", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    /**
     * D-102. An alarm due before the first unlock after a restart can only be
     * rung as one if the copy says it is one.
     */
    @Test
    fun anAlarmStaysAnAlarmInTheCopy() {
        val alarm = ScheduledReminder("c", "Take the pills", LocalDateTime.of(2026, 10, 11, 6, 30), ringsLikeAlarm = true)

        store().replaceUpcoming(listOf(alarm, water))

        assertEquals(listOf(alarm, water), store().upcoming())
    }

    /** A copy written before D-102 has no flag, and reads as plain reminders. */
    @Test
    fun aCopyWithoutTheFlagReadsAsPlainReminders() {
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("locked_reminders", Context.MODE_PRIVATE)
            .edit()
            .putString("upcoming", """[{"taskId":"a","title":"Call mom","ringsAt":"2026-10-11T07:00"}]""")
            .commit()

        assertEquals(listOf(call), store().upcoming())
    }

    @Test
    fun upcomingRemindersSurviveANewStore() {
        store().replaceUpcoming(listOf(call, water))

        assertEquals(listOf(call, water), store().upcoming())
    }

    /** Nothing may set its alarm again before the unlock. */
    @Test
    fun aLockedDeliveryIsRecordedAndLeavesTheUpcomingList() {
        store().replaceUpcoming(listOf(call, water))
        val delivery = LockedDelivery(
            taskId = "a",
            title = "Call mom",
            ringsAt = call.ringsAt,
            scheduledWallAt = Instant.parse("2026-10-11T01:30:00Z"),
            scheduledElapsedAt = 1_000L,
            arrivedWallAt = Instant.parse("2026-10-11T01:30:01Z"),
            arrivedElapsedAt = 2_000L,
            scheduledAhead = Duration.ofHours(3)
        )

        store().recordLockedDelivery(delivery)

        assertEquals(listOf(delivery), store().lockedDeliveries())
        assertEquals(listOf(water), store().upcoming())
    }

    @Test
    fun clearingForgetsTheDeliveriesOnly() {
        store().replaceUpcoming(listOf(water))
        store().recordLockedDelivery(
            LockedDelivery(
                taskId = "a",
                title = "Call mom",
                ringsAt = call.ringsAt,
                scheduledWallAt = Instant.EPOCH,
                scheduledElapsedAt = 0L,
                arrivedWallAt = Instant.EPOCH,
                arrivedElapsedAt = 0L,
                scheduledAhead = Duration.ZERO
            )
        )

        store().clearLockedDeliveries()

        assertTrue(store().lockedDeliveries().isEmpty())
        assertEquals(listOf(water), store().upcoming())
    }
}
