package com.vignesh.focuslist.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.design.ThemePreference
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import com.vignesh.focuslist.data.local.AppearancePreferences
import com.vignesh.focuslist.data.local.TaskEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocuslistBackupCodecTest {
    @Test
    fun everyTaskFieldAndAppearanceSettingRoundTrips() {
        val backup = FocuslistBackup(
            preferences = AppearancePreferences(
                dynamicColor = false,
                theme = ThemePreference.Dark
            ),
            tasks = listOf(completeTask())
        )
        val bytes = ByteArrayOutputStream().also { output ->
            FocuslistBackupCodec.write(
                output = output,
                backup = backup,
                createdAt = Instant.parse("2026-09-08T00:00:00Z")
            )
        }.toByteArray()

        val restored = FocuslistBackupCodec.read(ByteArrayInputStream(bytes))

        assertEquals(backup, restored)
    }

    @Test
    fun foreignJsonIsRejected() {
        val bytes = """{"format":"someone-elses-file","version":1,"settings":{},"tasks":[]}"""
            .toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            FocuslistBackupCodec.read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun futureVersionIsRejectedInsteadOfSilentlyDroppingData() {
        val bytes = """
            {
              "format":"focuslist-backup",
              "version":2,
              "settings":{"dynamicColor":true,"theme":"System"},
              "tasks":[]
            }
        """.trimIndent().toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            FocuslistBackupCodec.read(ByteArrayInputStream(bytes))
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

