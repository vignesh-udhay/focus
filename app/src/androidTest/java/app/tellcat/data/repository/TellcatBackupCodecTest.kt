package app.tellcat.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.core.design.ThemePreference
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

    @Test
    fun foreignJsonIsRejected() {
        val bytes = """{"format":"someone-elses-file","version":1,"settings":{},"tasks":[]}"""
            .toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            TellcatBackupCodec.read(ByteArrayInputStream(bytes))
        }
    }

    /**
     * D-081. Every backup written before the rename says `focuslist-backup`,
     * Catimo's included, and the move from a Catimo install is a restore of one.
     */
    @Test
    fun aBackupFromBeforeTheRenameStillOpens() {
        val bytes = """
            {
              "format":"focuslist-backup",
              "version":1,
              "settings":{"dynamicColor":false,"theme":"Dark"},
              "tasks":[{"id":"a","title":"Kept","createdAt":"2026-10-10T07:00:00Z","occurrenceNumber":1}]
            }
        """.trimIndent().toByteArray()

        val backup = TellcatBackupCodec.read(ByteArrayInputStream(bytes))

        assertEquals(listOf("Kept"), backup.tasks.map { it.title })
        assertEquals(ThemePreference.Dark, backup.preferences.theme)
    }

    /** New files carry the new name; the old one is only ever read. */
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
              "version":2,
              "settings":{"dynamicColor":true,"theme":"System"},
              "tasks":[]
            }
        """.trimIndent().toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            TellcatBackupCodec.read(ByteArrayInputStream(bytes))
        }
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
        deletedAt = Instant.parse("2026-09-09T05:05:00Z")
    )
}

