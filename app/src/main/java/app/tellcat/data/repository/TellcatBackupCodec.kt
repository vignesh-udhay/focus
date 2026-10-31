package app.tellcat.data.repository

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import app.tellcat.core.design.ThemePreference
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.core.domain.checklistItem
import app.tellcat.data.local.AppearancePreferences
import app.tellcat.data.local.TaskEntity
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
data class TellcatBackup(
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
 *
 * Version 2 adds a task's checklist. Restoring a version 2 file into an app
 * that ignored the key would drop every item while the restore looked
 * successful, which is exactly what the version check exists to refuse, so it
 * moved the version rather than riding in as an ignored key. Version 1 files
 * still restore, and a task in one has no checklist, which is what it had.
 */
object TellcatBackupCodec {
    // The only marker read or written, D-082. Files from before the rename say
    // `focuslist-backup` and are refused like any other foreign file: no user
    // ever held one, and the owner's own was converted by hand.
    private const val Format = "tellcat-backup"
    private const val Version = 2
    private val ReadableVersions = 1..Version

    fun write(
        output: OutputStream,
        backup: TellcatBackup,
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

    fun read(input: InputStream): TellcatBackup {
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

        require(format == Format) { "Not a Tellcat backup" }
        require(version in ReadableVersions) { "Unsupported Tellcat backup version" }
        val restoredPreferences = requireNotNull(preferences) { "Missing settings" }
        val restoredTasks = requireNotNull(tasks) { "Missing tasks" }
        require(restoredTasks.map { it.id }.toSet().size == restoredTasks.size) {
            "Duplicate task ids"
        }

        return TellcatBackup(restoredPreferences, restoredTasks)
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
        writer.name("checklist").beginArray()
        task.checklist.orEmpty().forEach { item ->
            writer.beginObject()
            writer.name("text").value(item.text)
            writer.name("done").value(item.done)
            writer.endObject()
        }
        writer.endArray()
        writer.name("createdAt").value(task.createdAt.toString())
        writer.name("scheduledDate").nullableValue(task.scheduledDate?.toString())
        writer.name("dueDate").nullableValue(task.dueDate?.toString())
        writer.name("reminderAt").nullableValue(task.reminderAt?.toString())
        writer.name("reminderDeliveredAt").nullableValue(task.reminderDeliveredAt?.toString())
        writer.name("snoozedUntil").nullableValue(task.snoozedUntil?.toString())
        writer.name("ringsLikeAlarm").value(task.ringsLikeAlarm)
        // D-115. The choice only: when it was told is about one announcement.
        writer.name("earlyReminderMinutes").nullableValue(task.earlyReminderMinutes?.toInt())
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
        // D-116. Absent from older backups, which read as on the day of the month.
        writer.name("recurrenceMonthlyOrdinal").nullableValue(task.recurrenceMonthlyOrdinal)
        writer.name("recurrenceMonthlyWeekday").nullableValue(task.recurrenceMonthlyWeekday)
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
        var checklist: List<ChecklistItem> = emptyList()
        var createdAt: Instant? = null
        var scheduledDate: LocalDate? = null
        var dueDate: LocalDate? = null
        var reminderAt: LocalDateTime? = null
        var reminderDeliveredAt: Instant? = null
        // Absent from backups written before D-078, which reads as no snooze.
        var snoozedUntil: LocalDateTime? = null
        // Absent from backups written before D-102, which reads as a plain
        // reminder. An older app skips it, so the format version stays.
        var ringsLikeAlarm = false
        // Absent before D-115, which reads as no early reminder.
        var earlyReminderMinutes: Long? = null
        var estimatedDurationMinutes: Int? = null
        var recurrence: RecurrenceUnit? = null
        var recurrenceInterval: Int? = null
        var recurrenceWeekdays: Set<DayOfWeek>? = null
        var recurrenceEndDate: LocalDate? = null
        var recurrenceEndCount: Int? = null
        var recurrenceMonthlyOrdinal: Int? = null
        var recurrenceMonthlyWeekday: String? = null
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
                "checklist" -> checklist = reader.nextChecklist()
                "createdAt" -> createdAt = Instant.parse(reader.nextString())
                "scheduledDate" -> scheduledDate = reader.nextNullableString()?.let(LocalDate::parse)
                "dueDate" -> dueDate = reader.nextNullableString()?.let(LocalDate::parse)
                "reminderAt" -> reminderAt = reader.nextNullableString()?.let(LocalDateTime::parse)
                "reminderDeliveredAt" -> reminderDeliveredAt =
                    reader.nextNullableString()?.let(Instant::parse)
                "snoozedUntil" -> snoozedUntil =
                    reader.nextNullableString()?.let(LocalDateTime::parse)
                "ringsLikeAlarm" -> ringsLikeAlarm = reader.nextBoolean()
                "earlyReminderMinutes" -> earlyReminderMinutes = reader.nextNullableInt()?.toLong()
                "estimatedDurationMinutes" -> estimatedDurationMinutes = reader.nextNullableInt()
                "recurrenceUnit" -> recurrence = reader.nextNullableString()?.let(RecurrenceUnit::valueOf)
                "recurrenceInterval" -> recurrenceInterval = reader.nextNullableInt()
                "recurrenceWeekdays" -> recurrenceWeekdays = reader.nextNullableWeekdays()
                "recurrenceEndDate" -> recurrenceEndDate =
                    reader.nextNullableString()?.let(LocalDate::parse)
                "recurrenceEndCount" -> recurrenceEndCount = reader.nextNullableInt()
                "recurrenceMonthlyOrdinal" -> recurrenceMonthlyOrdinal = reader.nextNullableInt()
                "recurrenceMonthlyWeekday" -> recurrenceMonthlyWeekday = reader.nextNullableString()
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
        // The same invariants the app holds when a task is edited, so a file
        // cannot bring in a row the app would never have written: a blank title,
        // or a duration of zero or less, which showed as "-5m".
        require(!title.isNullOrBlank()) { "Blank task title" }
        require(estimatedDurationMinutes == null || estimatedDurationMinutes > 0) {
            "Invalid estimated duration"
        }
        require(recurrenceInterval == null || recurrenceInterval > 0) { "Invalid repeat interval" }
        require(recurrenceEndCount == null || recurrenceEndCount > 0) { "Invalid repeat count" }

        return TaskEntity(
            id = requireNotNull(id) { "Missing task id" },
            title = requireNotNull(title) { "Missing task title" },
            notes = notes,
            checklist = checklist.takeIf { it.isNotEmpty() },
            createdAt = requireNotNull(createdAt) { "Missing creation time" },
            scheduledDate = scheduledDate,
            dueDate = dueDate,
            reminderAt = reminderAt,
            reminderDeliveredAt = reminderDeliveredAt,
            snoozedUntil = snoozedUntil,
            estimatedDurationMinutes = estimatedDurationMinutes,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval,
            recurrenceWeekdays = recurrenceWeekdays,
            recurrenceEndDate = recurrenceEndDate,
            recurrenceEndCount = recurrenceEndCount,
            occurrenceNumber = occurrence,
            spawnedFromId = spawnedFromId,
            completedAt = completedAt,
            deletedAt = deletedAt,
            ringsLikeAlarm = ringsLikeAlarm,
            earlyReminderMinutes = earlyReminderMinutes,
            recurrenceMonthlyOrdinal = recurrenceMonthlyOrdinal,
            recurrenceMonthlyWeekday = recurrenceMonthlyWeekday
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

    /**
     * Items go through [checklistItem], the rule the app applies when one is
     * typed, so a file cannot bring in a blank item or one spanning two lines.
     * Such an item is dropped rather than refusing the whole file: it is one
     * line of one list, not a task the app would never have written.
     */
    private fun JsonReader.nextChecklist(): List<ChecklistItem> = buildList {
        beginArray()
        while (hasNext()) {
            var text: String? = null
            var done = false
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "text" -> text = nextString()
                    "done" -> done = nextBoolean()
                    else -> skipValue()
                }
            }
            endObject()
            text?.let(::checklistItem)?.let { add(it.copy(done = done)) }
        }
        endArray()
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

