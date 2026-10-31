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

    /**
     * D-108. A follow-up due before the first unlock is posted from the copy,
     * so the copy has to say it is one, and which reminder it follows up.
     */
    @Test
    fun aFollowUpStaysAFollowUpInTheCopy() {
        val followUp = ScheduledReminder(
            "d",
            "Call the dentist",
            LocalDateTime.of(2026, 10, 11, 9, 40),
            followUpOf = LocalDateTime.of(2026, 10, 11, 9, 30)
        )

        store().replaceUpcoming(listOf(followUp, water))

        assertEquals(listOf(followUp, water), store().upcoming())
    }

    /**
     * D-108. The unlock decides from these whether to set a follow-up, and
     * whether one was already posted while locked.
     */
    @Test
    fun aLockedDeliveryKeepsWhetherItWasAFollowUpAndWhetherItRemindsAgain() {
        val delivery = LockedDelivery(
            taskId = "a",
            title = "Call mom",
            ringsAt = call.ringsAt,
            scheduledWallAt = Instant.EPOCH,
            scheduledElapsedAt = 0L,
            arrivedWallAt = Instant.EPOCH,
            arrivedElapsedAt = 0L,
            scheduledAhead = Duration.ZERO,
            followUp = true,
            remindsAgain = true
        )

        store().recordLockedDelivery(delivery)

        assertEquals(listOf(delivery), store().lockedDeliveries())
    }

    /** D-115. An early reminder in the copy keeps the reminder it warns about. */
    @Test
    fun anEarlyReminderStaysEarlyInTheCopy() {
        val early = ScheduledReminder(
            "d",
            "Call the dentist",
            LocalDateTime.of(2026, 10, 11, 9, 0),
            earlyFor = LocalDateTime.of(2026, 10, 11, 10, 0)
        )

        store().replaceUpcoming(listOf(early, water))

        assertEquals(listOf(early, water), store().upcoming())
    }

    @Test
    fun aLockedDeliveryKeepsWhetherItWasEarly() {
        val delivery = LockedDelivery(
            taskId = "a",
            title = "Call mom",
            ringsAt = call.ringsAt,
            scheduledWallAt = Instant.EPOCH,
            scheduledElapsedAt = 0L,
            arrivedWallAt = Instant.EPOCH,
            arrivedElapsedAt = 0L,
            scheduledAhead = Duration.ZERO,
            early = true
        )

        store().recordLockedDelivery(delivery)

        assertEquals(listOf(delivery), store().lockedDeliveries())
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
