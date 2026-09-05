package com.vignesh.focuslist.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * An actionable piece of work.
 *
 * This is domain data. It holds what is true about a task, not how a task is
 * presented: no formatted strings, no display metadata, and nothing that
 * depends on the UI layer.
 *
 * [scheduledDate] says which day the task is meant to be worked on, and
 * [dueDate] says when it is actually due. Today, Inbox and Upcoming are views
 * derived from [scheduledDate], not states stored here.
 *
 * @param id stable identity, assigned at creation and suitable for use as a
 * list key. A UUID string in production; the value is opaque to this model.
 * @param title what the task is.
 * @param createdAt when the task was captured. Required, with no default, so
 * that creating a task always establishes its creation time explicitly.
 * @param notes anything the task needs said about it beyond its title, or null
 * when there is nothing. It belongs with [title], and sits here only because
 * every parameter with a default has to follow the ones without. Free text:
 * nothing reads it, parses it, or filters on it.
 * @param scheduledDate the day the task is meant to be worked on, or null when
 * it is not scheduled. Drives the Today and Upcoming views.
 * @param dueDate the day the task is actually due, or null when it has no
 * deadline. Independent of [scheduledDate].
 * @param reminderAt when to interrupt the user about this task, or null when
 * the task never speaks up. A wall-clock time rather than an [Instant],
 * deliberately: a reminder set for 9am means 9am where the user is, so it
 * follows [scheduledDate] onto the local calendar rather than [createdAt] onto
 * the absolute timeline. The consequence is that changing timezone changes
 * when this fires, which is why `AGENTS.md` requires a reschedule on
 * `ACTION_TIMEZONE_CHANGED`. Independent of [scheduledDate] and [dueDate]: a
 * task can be scheduled for a day and say nothing, or be unscheduled and still
 * ring.
 * @param reminderDeliveredAt when the reminder was actually announced, or null
 * while it is still owed. This is what stops a reminder being delivered twice.
 * A reminder whose moment has passed stays owed until something records that
 * it was told, and without this the scheduler re-announces it on every pass:
 * one per task edit, restart and clock change.
 *
 * Its invariant belongs to whoever writes [reminderAt]. Giving a task a new
 * reminder time, including a snooze, must clear this, or the new time is born
 * already delivered and never fires.
 * @param estimatedDurationMinutes how long the task is expected to take, or
 * null when it has no estimate.
 * @param recurrence how often the task comes back, or null when it happens
 * once. Completing a recurring task finishes that occurrence and starts the
 * next; the rule itself says nothing about which dates those are, and
 * `Recurrence.kt` derives them.
 * @param completedAt when the task was completed, or null while it is
 * outstanding. Completion is recorded by setting this and undone by clearing
 * it.
 * @param deletedAt when the task was deleted, or null while it is live.
 * Deletion is soft so that it can be undone.
 */
data class Task(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val notes: String? = null,
    val scheduledDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val reminderAt: LocalDateTime? = null,
    val reminderDeliveredAt: Instant? = null,
    val estimatedDurationMinutes: Int? = null,
    val recurrence: Recurrence? = null,
    /**
     * The occurrence this one was created by finishing, for a recurring task.
     *
     * Null for everything a person made themselves, which is almost every
     * task. It exists so that reopening a completed occurrence can find the
     * copy that completion produced: without it the only way back is the undo
     * offer, which lasts four seconds, and unticking the box afterwards left
     * the user holding two.
     */
    val spawnedFromId: String? = null,
    val completedAt: Instant? = null,
    val deletedAt: Instant? = null
) {

    /**
     * Whether the task is complete.
     *
     * Derived from [completedAt] so the two can never disagree.
     */
    val isCompleted: Boolean
        get() = completedAt != null

    /**
     * Whether the task has been deleted and is awaiting undo or purge.
     *
     * Derived from [deletedAt] so the two can never disagree.
     */
    val isDeleted: Boolean
        get() = deletedAt != null
}
