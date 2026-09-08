package com.vignesh.focuslist.data.repository

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.vignesh.focuslist.core.design.ThemePreference
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import com.vignesh.focuslist.data.local.AppearancePreferences
import com.vignesh.focuslist.data.local.TaskEntity
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** A fully parsed backup. Parsing finishes before restore changes local data. */
data class FocuslistBackup(
    val preferences: AppearancePreferences,
    val tasks: List<TaskEntity>
)

/**
 * Versioned JSON for files the user owns.
 *
 * The codec mirrors the stored task rather than a screen projection, so notes,
 * completion, deletion, reminder delivery state, and the complete recurrence
 * rule all survive a round trip. Unknown keys are ignored for forward-safe
 * additions; an unknown format version is rejected because silently dropping a
 * future field would make a restore look successful when it was not.
 */
object FocuslistBackupCodec {
    private const val Format = "focuslist-backup"
    private const val Version = 1

    fun write(
        output: OutputStream,
        backup: FocuslistBackup,
        createdAt: Instant = Instant.now()
    ) {
        JsonWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.beginObject()
            writer.name("format").value(Format)
            writer.name("version").value(Version.toLong())
            writer.name("createdAt").value(createdAt.toString())
            writer.name("settings")
            writePreferences(writer, backup.preferences)
            writer.name("tasks").beginArray()
            backup.tasks.forEach { task -> writeTask(writer, task) }
            writer.endArray()
            writer.endObject()
        }
    }

    fun read(input: InputStream): FocuslistBackup {
        var format: String? = null
        var version: Int? = null
        var preferences: AppearancePreferences? = null
        var tasks: List<TaskEntity>? = null

        JsonReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "format" -> format = reader.nextString()
                    "version" -> version = reader.nextInt()
                    "settings" -> preferences = readPreferences(reader)
                    "tasks" -> tasks = readTasks(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }

        require(format == Format) { "Not a Focuslist backup" }
        require(version == Version) { "Unsupported Focuslist backup version" }
        val restoredPreferences = requireNotNull(preferences) { "Missing settings" }
        val restoredTasks = requireNotNull(tasks) { "Missing tasks" }
        require(restoredTasks.map { it.id }.toSet().size == restoredTasks.size) {
            "Duplicate task ids"
        }

        return FocuslistBackup(restoredPreferences, restoredTasks)
    }

    private fun writePreferences(writer: JsonWriter, preferences: AppearancePreferences) {
        writer.beginObject()
        writer.name("dynamicColor").value(preferences.dynamicColor)
        writer.name("theme").value(preferences.theme.name)
        writer.endObject()
    }

    private fun readPreferences(reader: JsonReader): AppearancePreferences {
        var dynamicColor: Boolean? = null
        var theme: ThemePreference? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "dynamicColor" -> dynamicColor = reader.nextBoolean()
                "theme" -> theme = ThemePreference.valueOf(reader.nextString())
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return AppearancePreferences(
            dynamicColor = requireNotNull(dynamicColor) { "Missing dynamic color setting" },
            theme = requireNotNull(theme) { "Missing theme setting" }
        )
    }

    private fun readTasks(reader: JsonReader): List<TaskEntity> = buildList {
        reader.beginArray()
        while (reader.hasNext()) add(readTask(reader))
        reader.endArray()
    }

    private fun writeTask(writer: JsonWriter, task: TaskEntity) {
        writer.beginObject()
        writer.name("id").value(task.id)
        writer.name("title").value(task.title)
        writer.name("notes").nullableValue(task.notes)
        writer.name("createdAt").value(task.createdAt.toString())
        writer.name("scheduledDate").nullableValue(task.scheduledDate?.toString())
        writer.name("dueDate").nullableValue(task.dueDate?.toString())
        writer.name("reminderAt").nullableValue(task.reminderAt?.toString())
        writer.name("reminderDeliveredAt").nullableValue(task.reminderDeliveredAt?.toString())
        writer.name("estimatedDurationMinutes").nullableValue(task.estimatedDurationMinutes)
        writer.name("recurrenceUnit").nullableValue(task.recurrence?.name)
        writer.name("recurrenceInterval").nullableValue(task.recurrenceInterval)
        writer.name("recurrenceWeekdays")
        task.recurrenceWeekdays?.let { weekdays ->
            writer.beginArray()
            weekdays.sortedBy(DayOfWeek::getValue).forEach { writer.value(it.name) }
            writer.endArray()
        } ?: writer.nullValue()
        writer.name("recurrenceEndDate").nullableValue(task.recurrenceEndDate?.toString())
        writer.name("recurrenceEndCount").nullableValue(task.recurrenceEndCount)
        writer.name("occurrenceNumber").value(task.occurrenceNumber.toLong())
        writer.name("spawnedFromId").nullableValue(task.spawnedFromId)
        writer.name("completedAt").nullableValue(task.completedAt?.toString())
        writer.name("deletedAt").nullableValue(task.deletedAt?.toString())
        writer.endObject()
    }

    @Suppress("LongMethod")
    private fun readTask(reader: JsonReader): TaskEntity {
        var id: String? = null
        var title: String? = null
        var notes: String? = null
        var createdAt: Instant? = null
        var scheduledDate: LocalDate? = null
        var dueDate: LocalDate? = null
        var reminderAt: LocalDateTime? = null
        var reminderDeliveredAt: Instant? = null
        var estimatedDurationMinutes: Int? = null
        var recurrence: RecurrenceUnit? = null
        var recurrenceInterval: Int? = null
        var recurrenceWeekdays: Set<DayOfWeek>? = null
        var recurrenceEndDate: LocalDate? = null
        var recurrenceEndCount: Int? = null
        var occurrenceNumber: Int? = null
        var spawnedFromId: String? = null
        var completedAt: Instant? = null
        var deletedAt: Instant? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "title" -> title = reader.nextString()
                "notes" -> notes = reader.nextNullableString()
                "createdAt" -> createdAt = Instant.parse(reader.nextString())
                "scheduledDate" -> scheduledDate = reader.nextNullableString()?.let(LocalDate::parse)
                "dueDate" -> dueDate = reader.nextNullableString()?.let(LocalDate::parse)
                "reminderAt" -> reminderAt = reader.nextNullableString()?.let(LocalDateTime::parse)
                "reminderDeliveredAt" -> reminderDeliveredAt =
                    reader.nextNullableString()?.let(Instant::parse)
                "estimatedDurationMinutes" -> estimatedDurationMinutes = reader.nextNullableInt()
                "recurrenceUnit" -> recurrence = reader.nextNullableString()?.let(RecurrenceUnit::valueOf)
                "recurrenceInterval" -> recurrenceInterval = reader.nextNullableInt()
                "recurrenceWeekdays" -> recurrenceWeekdays = reader.nextNullableWeekdays()
                "recurrenceEndDate" -> recurrenceEndDate =
                    reader.nextNullableString()?.let(LocalDate::parse)
                "recurrenceEndCount" -> recurrenceEndCount = reader.nextNullableInt()
                "occurrenceNumber" -> occurrenceNumber = reader.nextInt()
                "spawnedFromId" -> spawnedFromId = reader.nextNullableString()
                "completedAt" -> completedAt = reader.nextNullableString()?.let(Instant::parse)
                "deletedAt" -> deletedAt = reader.nextNullableString()?.let(Instant::parse)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val occurrence = requireNotNull(occurrenceNumber) { "Missing occurrence number" }
        require(occurrence > 0) { "Invalid occurrence number" }

        return TaskEntity(
            id = requireNotNull(id) { "Missing task id" },
            title = requireNotNull(title) { "Missing task title" },
            notes = notes,
            createdAt = requireNotNull(createdAt) { "Missing creation time" },
            scheduledDate = scheduledDate,
            dueDate = dueDate,
            reminderAt = reminderAt,
            reminderDeliveredAt = reminderDeliveredAt,
            estimatedDurationMinutes = estimatedDurationMinutes,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval,
            recurrenceWeekdays = recurrenceWeekdays,
            recurrenceEndDate = recurrenceEndDate,
            recurrenceEndCount = recurrenceEndCount,
            occurrenceNumber = occurrence,
            spawnedFromId = spawnedFromId,
            completedAt = completedAt,
            deletedAt = deletedAt
        )
    }

    private fun JsonWriter.nullableValue(value: String?) {
        if (value == null) nullValue() else value(value)
    }

    private fun JsonWriter.nullableValue(value: Int?) {
        if (value == null) nullValue() else value(value.toLong())
    }

    private fun JsonReader.nextNullableString(): String? =
        if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextString()
        }

    private fun JsonReader.nextNullableInt(): Int? =
        if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextInt()
        }

    private fun JsonReader.nextNullableWeekdays(): Set<DayOfWeek>? {
        if (peek() == JsonToken.NULL) {
            nextNull()
            return null
        }

        return buildSet {
            beginArray()
            while (hasNext()) add(DayOfWeek.valueOf(nextString()))
            endArray()
        }
    }
}

