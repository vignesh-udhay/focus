package app.tellcat.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.core.design.ThemePreference
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.data.local.AppearancePreferences
import app.tellcat.data.local.TaskEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TellcatBackupCodecTest {
    @Test
    fun everyTaskFieldAndAppearanceSettingRoundTrips() {
        val backup = TellcatBackup(
            preferences = AppearancePreferences(
                dynamicColor = false,
                theme = ThemePreference.Dark
            ),
            tasks = listOf(completeTask())
        )
        val bytes = ByteArrayOutputStream().also { output ->
            TellcatBackupCodec.write(
                output = output,
                backup = backup,
                createdAt = Instant.parse("2026-09-08T00:00:00Z")
            )
        }.toByteArray()

        val restored = TellcatBackupCodec.read(ByteArrayInputStream(bytes))

        assertEquals(backup, restored)
    }

    /**
     * D-115. The early reminder is carried; a backup written before it, which
     * has no such key, reads as none.
     */
    @Test
    fun theEarlyReminderIsCarriedAndAnOlderBackupReadsNone() {
        val backup = TellcatBackup(
            preferences = AppearancePreferences(),
            tasks = listOf(completeTask().copy(earlyReminderMinutes = 60))
        )
        val written = ByteArrayOutputStream().also { output ->
            TellcatBackupCodec.write(output = output, backup = backup, createdAt = Instant.parse("2026-09-08T00:00:00Z"))
        }.toString(Charsets.UTF_8.name())

        val restored = TellcatBackupCodec.read(ByteArrayInputStream(written.toByteArray()))
        assertEquals(60L, restored.tasks.single().earlyReminderMinutes)

        val older = Regex(""""earlyReminderMinutes":\\s*60,?""").replace(written, "")
        val fromOlder = TellcatBackupCodec.read(ByteArrayInputStream(older.toByteArray()))
        assertNull(fromOlder.tasks.single().earlyReminderMinutes)
    }

    @Test
    fun foreignJsonIsRejected() {
        val bytes = """{"format":"someone-elses-file","version":1,"settings":{},"tasks":[]}"""
            .toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            TellcatBackupCodec.read(ByteArrayInputStream(bytes))
        }
    }

    /**
     * D-082. A file from before the rename is someone else's file as far as
     * restore is concerned. Otherwise valid, so the marker is all it fails on.
     */
    @Test
    fun aBackupFromBeforeTheRenameIsRefused() {
        val bytes = """
            {
              "format":"focuslist-backup",
              "version":1,
              "settings":{"dynamicColor":false,"theme":"Dark"},
              "tasks":[{"id":"a","title":"Kept","createdAt":"2026-10-10T07:00:00Z","occurrenceNumber":1}]
            }
        """.trimIndent().toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            TellcatBackupCodec.read(ByteArrayInputStream(bytes))
        }
    }

    /** New files carry the one marker restore accepts. */
    @Test
    fun aNewBackupSaysTellcat() {
        val output = ByteArrayOutputStream()
        TellcatBackupCodec.write(output, TellcatBackup(AppearancePreferences(), emptyList()))

        assertTrue(output.toString(Charsets.UTF_8.name()).contains("\"format\": \"tellcat-backup\""))
    }

    @Test
    fun futureVersionIsRejectedInsteadOfSilentlyDroppingData() {
        val bytes = """
            {
              "format":"tellcat-backup",
              "version":3,
              "settings":{"dynamicColor":true,"theme":"System"},
              "tasks":[]
            }
        """.trimIndent().toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            TellcatBackupCodec.read(ByteArrayInputStream(bytes))
        }
    }

    /**
     * Version 1 files were written before a task could have a checklist.
     * They still restore, each task without one.
     */
    @Test
    fun aVersionOneBackupRestoresWithoutChecklists() {
        val bytes = """
            {
              "format":"tellcat-backup",
              "version":1,
              "settings":{"dynamicColor":false,"theme":"Dark"},
              "tasks":[{"id":"a","title":"Kept","createdAt":"2026-10-10T07:00:00Z",
                "reminderAt":"2026-10-11T09:00:00","occurrenceNumber":1}]
            }
        """.trimIndent().toByteArray()

        val restored = TellcatBackupCodec.read(ByteArrayInputStream(bytes))

        assertEquals(1, restored.tasks.size)
        assertNull(restored.tasks.single().checklist)
        // Written before D-102: rings as a notification.
        assertFalse(restored.tasks.single().ringsLikeAlarm)
    }

    /** A file cannot bring in a row the app would never have written. */
    @Test
    fun aTaskTheAppWouldRefuseIsRejected() {
        listOf(
            completeTask().copy(title = "   "),
            completeTask().copy(estimatedDurationMinutes = -5),
            completeTask().copy(recurrenceInterval = 0),
            completeTask().copy(recurrenceEndDate = null, recurrenceEndCount = 0)
        ).forEach { invalid ->
            val bytes = ByteArrayOutputStream().also { output ->
                TellcatBackupCodec.write(
                    output = output,
                    backup = TellcatBackup(
                        preferences = AppearancePreferences(),
                        tasks = listOf(invalid)
                    ),
                    createdAt = Instant.parse("2026-09-08T00:00:00Z")
                )
            }.toByteArray()

            assertThrows(IllegalArgumentException::class.java) {
                TellcatBackupCodec.read(ByteArrayInputStream(bytes))
            }
        }
    }

    private fun completeTask() = TaskEntity(
        id = "task-1",
        title = "Call the plumber",
        notes = "Ask about the kitchen tap",
        // Two items, one ticked, so dropping either the list or a tick fails.
        checklist = listOf(ChecklistItem("Washer"), ChecklistItem("Tape", done = true)),
        createdAt = Instant.parse("2026-09-01T08:00:00Z"),
        scheduledDate = LocalDate.parse("2026-09-09"),
        dueDate = LocalDate.parse("2026-09-10"),
        reminderAt = LocalDateTime.parse("2026-09-09T09:30:00"),
        reminderDeliveredAt = Instant.parse("2026-09-09T04:00:03Z"),
        // A real value, so a codec that dropped the field fails. D-078.
        snoozedUntil = LocalDateTime.parse("2026-09-09T10:30:00"),
        estimatedDurationMinutes = 45,
        recurrence = RecurrenceUnit.WEEKLY,
        recurrenceInterval = 2,
        recurrenceWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        recurrenceEndDate = LocalDate.parse("2026-12-31"),
        recurrenceEndCount = null,
        occurrenceNumber = 3,
        spawnedFromId = "task-0",
        completedAt = Instant.parse("2026-09-09T05:00:00Z"),
        deletedAt = Instant.parse("2026-09-09T05:05:00Z"),
        // True, the value that is not the default, so a codec that dropped the
        // field fails the round trip. D-102.
        ringsLikeAlarm = true
    )
}

