package com.vignesh.focuslist.ui.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.nextRecurringInstance
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskCompletion
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.time.CurrentDay
import com.vignesh.focuslist.core.time.SystemCurrentDay
import com.vignesh.focuslist.core.domain.anytimeTasks as queryAnytimeTasks
import com.vignesh.focuslist.core.domain.completedTasks as queryCompletedTasks
import com.vignesh.focuslist.core.domain.focusQueue as queryFocusQueue
import com.vignesh.focuslist.core.domain.inboxTasks as queryInboxTasks
import com.vignesh.focuslist.core.domain.somedayTasks as querySomedayTasks
import com.vignesh.focuslist.core.domain.todayTasks as queryTodayTasks
import com.vignesh.focuslist.core.domain.upcomingTasks as queryUpcomingTasks
import com.vignesh.focuslist.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.vignesh.focuslist.core.notification.FocusAlarms
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * An action the user can still take back.
 *
 * Carries the task id rather than the task, because the row the action came
 * from may no longer be on screen, and the task's current state has to be read
 * fresh when the undo is actually pressed.
 */
sealed interface PendingUndo {

    val taskId: String

    /**
     * A task that was just completed, and can be reopened.
     *
     * [spawnedTaskId] is the next instance a recurring task started when it
     * was completed, or null for a task that happens once. Undo has to take
     * that back too: reopening the task without removing the copy would leave
     * the user holding two, and only one of them was ever theirs.
     */
    data class Completion(
        override val taskId: String,
        val spawnedTaskId: String? = null
    ) : PendingUndo

    /** A task that was just deleted, and can be restored. */
    data class Deletion(override val taskId: String) : PendingUndo

    /**
     * A task that was just triaged into another bucket, and can be put back.
     *
     * [previousPlacement] is carried for the same reason [Reschedule] carries
     * the day it left: the write destroys it, and undo has to know where the
     * task came from rather than guess.
     */
    data class Move(
        override val taskId: String,
        val previousPlacement: TaskPlacement
    ) : PendingUndo

    /**
     * A task that was just moved to another day, and can be moved back.
     *
     * [previousDate] is carried because it is not recoverable from the task
     * once the write has happened, unlike a completion or a deletion, which
     * are undone by clearing a timestamp. Null is a real value here: it means
     * the task had no day before, and undo returns it to having none.
     */
    data class Reschedule(
        override val taskId: String,
        val previousDate: LocalDate?
    ) : PendingUndo
}

/**
 * State for every task surface.
 *
 * One view model behind all of them, scoped to the Activity. Each surface is
 * the same stored tasks read through a different domain query, and every write
 * goes through here, so they agree with each other by construction and a
 * single undo offer stands for the whole app rather than per screen.
 *
 * That covers Focus too, which is not a list: it reads the same stream through
 * [focusQueue] and shows one task from it.
 *
 * It holds no view rules of its own: filtering and ordering live in
 * `TaskQueries`, and it knows nothing about Room, entities, or the DAO.
 *
 * @param currentDay the day the dated views are derived against, injected
 * rather than read from the clock so the derivation stays deterministic and
 * testable. It is a stream, not a value: an app left open across midnight has
 * to re-derive rather than keep working against the day it was built on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModel(
    private val repository: TaskRepository,
    private val currentDay: CurrentDay,
    private val savedState: SavedStateHandle,
    private val alarms: FocusAlarms
) : ViewModel() {

    /**
     * What completing a task actually does.
     *
     * Built here rather than taken as a parameter, because it needs exactly
     * the two things this view model already holds. A notification action
     * builds its own from the same two, which is the point of it having moved
     * out of here: one implementation of what finishing a task means.
     */
    private val completion = TaskCompletion(repository, currentDay)

    /**
     * The day the dated views are derived against.
     *
     * Exposed for the row metadata that phrases a date as "Today" or
     * "Tomorrow", and read at the moment of an action so a task captured after
     * midnight gets the new day.
     */
    val today: StateFlow<LocalDate> = currentDay.today

    val todayTasks: StateFlow<List<Task>> =
        combine(repository.observeTasks(), currentDay.today) { tasks, day ->
            queryTodayTasks(tasks, day)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    /**
     * Tasks scheduled beyond [today], derived from the same stored stream.
     *
     * The query owns the filtering and the ordering. Nothing here re-decides
     * either, and a task that stops being upcoming simply stops being emitted.
     */
    val upcomingTasks: StateFlow<List<Task>> =
        combine(repository.observeTasks(), currentDay.today) { tasks, day ->
            queryUpcomingTasks(tasks, day)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    /**
     * Tasks captured but not yet decided about, derived from the same stored
     * stream. The query owns the filtering and the ordering.
     */
    val inboxTasks: StateFlow<List<Task>> =
        repository.observeTasks()
            .map { tasks -> queryInboxTasks(tasks) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    /**
     * Triaged and actionable, derived from the same stored stream.
     *
     * Overlaps Today and Upcoming on purpose: a task can be both actionable and
     * scheduled, and neither view hides it from the other.
     */
    val anytimeTasks: StateFlow<List<Task>> =
        repository.observeTasks()
            .map { tasks -> queryAnytimeTasks(tasks) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    /** Triaged and deliberately deferred, on the same terms as [anytimeTasks]. */
    val somedayTasks: StateFlow<List<Task>> =
        repository.observeTasks()
            .map { tasks -> querySomedayTasks(tasks) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    /**
     * Everything finished, derived from the same stored stream.
     *
     * The active lists all drop a task once it is complete, so this is where a
     * completed task stays reachable, and why completing one is never
     * destructive.
     */
    val completedTasks: StateFlow<List<Task>> =
        repository.observeTasks()
            .map { tasks -> queryCompletedTasks(tasks) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    /**
     * Today's work, still outstanding: what Focus draws from.
     *
     * Derived from the same stored stream as every other list, through the
     * same query layer. Nothing about being focused is stored on a task, so
     * there is no membership to keep in step with anything.
     */
    val focusQueue: StateFlow<List<Task>> =
        combine(repository.observeTasks(), currentDay.today) { tasks, day ->
            queryFocusQueue(tasks, day)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    /**
     * The task the user chose to focus on, or null while they have chosen
     * none.
     *
     * A pointer into [focusQueue], not an attribute of a task. It is
     * deliberately not persisted: on relaunch it is null and Focus falls back
     * to the head of the queue, which is a correct state rather than a broken
     * one.
     */
    private val _focusedTaskId = MutableStateFlow<String?>(null)

    /**
     * The one task Focus is on: the chosen one while it is still available,
     * otherwise the head of the queue.
     *
     * This fallback is the whole of Focus's behaviour. Completing the task,
     * rescheduling it out of today, deleting it, and the day rolling over all
     * take it out of [focusQueue], and the next task appears because the
     * chosen id no longer matches anything. There is no advance step, and so
     * no way for one of those four routes to be handled and another missed.
     */
    val focusedTask: StateFlow<Task?> =
        combine(focusQueue, _focusedTaskId) { queue, id ->
            queue.firstOrNull { task -> task.id == id } ?: queue.firstOrNull()
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )

    /**
     * Points Focus at [id].
     *
     * The id is recorded as given, without checking the queue. A task that is
     * not in it simply never matches, and [focusedTask] shows the head
     * instead, which is the same thing that happens when the chosen task later
     * leaves.
     */
    fun focusTask(id: String) {
        _focusedTaskId.value = id
    }

    /**
     * Choose a task and start working on it, which are now one act.
     *
     * Focus is entered by picking the task it is for, so there is no moment
     * between choosing and starting for anything to happen in. That is what
     * removed the Ready state, and it is also what answers "why this task":
     * because you said so.
     */
    fun beginFocus(id: String) {
        focusTask(id)
        restartFocusClock()
    }

    private val _focusSessionStartedAt = MutableStateFlow(
        savedState.get<Long>(FocusSessionStartedAtKey)?.let(Instant::ofEpochMilli)
    )

    /**
     * When the running session began, or null while none is running.
     *
     * A moment rather than a running total, so progress can be worked out from
     * the clock whenever anyone asks. Kept in [SavedStateHandle] so a session
     * survives the process being killed while the user was away in another
     * app, which over a forty-five minute estimate is a normal thing to happen
     * rather than an edge case.
     */
    val focusSessionStartedAt: StateFlow<Instant?> = _focusSessionStartedAt.asStateFlow()

    /**
     * Whether the user is working, as opposed to looking at what to work on.
     *
     * Focus is two things wearing one name: a destination you can wander into
     * from the bar, and the mode you are in while actually doing the task.
     * Only the second may take the navigation away, because only the second
     * was asked for. A destination that hid the control used to reach it would
     * be a trap; a mode the user started, and can stop, is not.
     *
     * Held here rather than in the screen because the navigation lives above
     * the graph: the bar and the rail are both outside the destination, and
     * neither can be told to stand down by a composable inside it.
     *
     * Persisted in [SavedStateHandle], so a session survives the process being
     * killed while the user was away in another app, which over a
     * forty-five-minute estimate is ordinary rather than an edge case.
     */
    val isFocusSessionActive: StateFlow<Boolean> =
        _focusSessionStartedAt
            .map { startedAt -> startedAt != null }
            .stateIn(
                scope = viewModelScope,
                // Eagerly, not while subscribed: the navigation above the graph
                // reads this to decide whether to draw itself, and a bar that
                // reappeared whenever the flow went cold would flicker back
                // into a running session.
                started = SharingStarted.Eagerly,
                initialValue = _focusSessionStartedAt.value != null
            )

    /**
     * Starts working on whatever [focusedTask] currently is.
     *
     * Takes no task: choosing and starting are separate acts, and the caller
     * that wants both does both.
     */
    fun startFocusSession() = restartFocusClock()

    /**
     * Marks now as the moment work on the current task began.
     *
     * Also called when the session moves on to the next task, because the
     * timestamp measures *this task* against *its* estimate, not the session
     * against the first task's. Without the reset, finishing a forty-five
     * minute task in ten and moving to a fifteen minute one would show the new
     * task as already overrun before a second of it had been worked.
     */
    private fun restartFocusClock() {
        val startedAt = Instant.now()
        _focusSessionStartedAt.value = startedAt
        savedState[FocusSessionStartedAtKey] = startedAt.toEpochMilli()
    }

    /**
     * Stops working, returning Focus to a destination.
     *
     * Called on the way out by hand, and by the screen when the queue empties:
     * a session with nothing left to do has ended whether or not it was
     * stopped, and leaving the flag set would hide the navigation behind an
     * empty state.
     */
    fun stopFocusSession() {
        _focusSessionStartedAt.value = null
        savedState.remove<Long>(FocusSessionStartedAtKey)
        alarms.cancel()
        // The pointer goes too. It used to survive, harmlessly, because Focus
        // was a destination the user had to navigate to and the stale choice
        // was simply what they found there next time. Now the running flag is
        // what puts the sheet on screen, so a pointer left behind would be a
        // choice nobody made, waiting to be reopened.
        _focusedTaskId.value = null
    }

    init {
        // A session with nothing left to work on has ended, whether or not it
        // was stopped. Leaving it running would hide the navigation behind an
        // empty screen, which is the trap the mode is meant to avoid.
        //
        // Watched here rather than from the screen, and against the stored
        // stream rather than against the focused task. The exposed flows all
        // start on a placeholder before storage has answered, and a screen
        // reading that placeholder cannot tell "nothing to do" from "not
        // loaded yet": entering Focus would stop the session it was entered
        // for. The repository only emits once it has really read.
        viewModelScope.launch {
            var workingOn: String? = null

            _focusSessionStartedAt
                .map { startedAt -> startedAt != null }
                // On the boolean, so restarting the clock for a new task does
                // not tear down and rebuild the very collection that noticed.
                .distinctUntilChanged()
                .flatMapLatest { isRunning ->
                    // Nothing to watch while no session is running.
                    if (!isRunning) {
                        workingOn = null
                        emptyFlow()
                    } else {
                        combine(
                            repository.observeTasks(),
                            currentDay.today,
                            _focusedTaskId
                        ) { tasks, day, chosenId ->
                            val queue = queryFocusQueue(tasks, day)
                            queue.firstOrNull { task -> task.id == chosenId }
                                ?: queue.firstOrNull()
                        }
                    }
                }
                .collect { task ->
                    if (task == null) {
                        // The queue running dry no longer ends the session. It
                        // used to, because a session with nothing in it hid the
                        // navigation behind an empty screen, and that was the
                        // trap the mode existed to avoid. Focus is a sheet now:
                        // the navigation is behind it, not gone, so there is no
                        // trap and no reason to close on the user at the one
                        // moment they have most earned being told they are
                        // done. The sheet shows the empty state and waits to be
                        // dismissed.
                        //
                        // The alarm still goes, because there is nothing left
                        // whose estimate could be reached.
                        alarms.cancel()
                        workingOn = null
                        return@collect
                    }

                    // A different task than a moment ago means the session
                    // moved on, and the new one gets its own clock. Only from
                    // one real task to another: the first task of a session
                    // must not reset a clock that was just restored from a
                    // killed process.
                    if (workingOn != null && workingOn != task.id) {
                        restartFocusClock()
                    }
                    workingOn = task.id

                    announce(task)
                }
        }
    }

    /**
     * Asks for the estimate being reached to be announced, or for nothing.
     *
     * A task with no estimate has no moment to announce, and a task already
     * past its estimate has had it: scheduling in the past would fire at once
     * and tell the user something they worked out by looking at the clock.
     */
    private fun announce(task: Task) {
        val startedAt = _focusSessionStartedAt.value
        val minutes = task.estimatedDurationMinutes

        if (startedAt == null || minutes == null || minutes <= 0) {
            alarms.cancel()
            return
        }

        val reachedAt = startedAt.plusSeconds(minutes.toLong() * SecondsPerMinute)
        if (!reachedAt.isAfter(Instant.now())) {
            alarms.cancel()
            return
        }

        alarms.scheduleEstimateReached(task.title, reachedAt)
    }

    /**
     * Captures a new task, and reports whether anything was captured.
     *
     * Always lands in [TaskPlacement.INBOX], because capture is not triage. The
     * caller supplies [scheduledDate]: Today captures for today, Inbox captures
     * without a day, and deciding when is the decision Inbox exists to defer.
     *
     * A blank title creates nothing and returns false, since the title is the
     * one thing a task cannot do without. The screen keeps its sheet open on a
     * false so the user can finish typing, and closes it on a true rather than
     * waiting on the write, which is local and fast.
     */
    fun createTask(title: String, scheduledDate: LocalDate?): Boolean {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return false

        viewModelScope.launch {
            repository.insert(
                Task(
                    id = UUID.randomUUID().toString(),
                    title = trimmed,
                    createdAt = Instant.now(),
                    placement = TaskPlacement.INBOX,
                    scheduledDate = scheduledDate
                )
            )
        }

        return true
    }

    private val _reminderJustSet = MutableStateFlow(false)

    /**
     * Whether a reminder has just been set that the app has not yet checked it
     * can actually deliver.
     *
     * A promise has been made and stored, and nothing here knows whether the
     * app is allowed to keep it: permissions are an Android question and this
     * class holds none of that. It raises the flag, the UI decides whether
     * there is anything to ask, and [acknowledgeReminder] lowers it.
     *
     * Set only when the time moves. Re-saving a task whose reminder did not
     * change is not a new promise, and asking again would be the app finding
     * an excuse rather than a reason.
     */
    val reminderJustSet: StateFlow<Boolean> = _reminderJustSet.asStateFlow()

    /** Called once the reminder has been dealt with, whatever the answer. */
    fun acknowledgeReminder() {
        _reminderJustSet.value = false
    }

    private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)

    /**
     * The one action a user can still take back, or null when there is nothing
     * to undo.
     *
     * Only the most recent action is held. A newer one replaces the offer
     * rather than queueing behind it, and an undo for a superseded action does
     * nothing.
     */
    val pendingUndo: StateFlow<PendingUndo?> = _pendingUndo.asStateFlow()

    /**
     * Completes an outstanding task, or reopens a completed one.
     *
     * The task is located by id, never by position, so the Today view's
     * ordering has no bearing on which task changes.
     *
     * Only completing offers an undo. Reopening a task is already the reversal
     * of completing it, so it raises nothing, and it withdraws any offer still
     * standing for that same task rather than leaving a snackbar that no longer
     * describes the task's state.
     */
    fun toggleComplete(id: String) {
        viewModelScope.launch {
            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch

            if (task.isCompleted) {
                completion.reopen(id)

                // Withdraw whatever offer is standing for this task, rather
                // than a reconstructed one. Building a `Completion` here with
                // a null `spawnedTaskId` never equalled the real offer, which
                // carries the id it spawned, so the compare-and-set silently
                // matched nothing and the snackbar was left offering an undo
                // for something already undone.
                dismissUndo(task.id)
                return@launch
            }

            _pendingUndo.value =
                PendingUndo.Completion(task.id, spawnedTaskId = completion.complete(id))
        }
    }

    /**
     * Reopens the task [id] was completed on, leaving everything else alone.
     *
     * The task is read fresh rather than restored from a copy taken when the
     * offer was raised, so any other change made in the meantime survives.
     * Only the completion is undone.
     *
     * A task the user has already reopened by hand has nothing left to take
     * back, so the undo does nothing rather than writing over newer state.
     */
    fun undoComplete(id: String) {
        val offer = _pendingUndo.value as? PendingUndo.Completion ?: return
        if (offer.taskId != id) return
        if (!_pendingUndo.compareAndSet(expect = offer, update = null)) return

        viewModelScope.launch {
            // The spawned instance goes first, and unconditionally. It exists
            // only because of the completion being undone, so it goes back
            // whether or not the original is still there to reopen, and it is
            // erased rather than soft-deleted: a row the user never created
            // should not be left for them to find.
            offer.spawnedTaskId?.let { repository.delete(it) }

            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch
            if (!task.isCompleted) return@launch

            repository.update(task.copy(completedAt = null))
        }
    }

    /**
     * Applies an edit to the task [id] names.
     *
     * Only the seven fields a task carries about itself can change. The stored
     * task is read fresh and copied, so `id`, `createdAt`, `completedAt`, and
     * `deletedAt` are carried through untouched: editing cannot complete,
     * reopen, delete, or restore a task, and a sheet left open against stale
     * values cannot write them back.
     *
     * [notes] has no default, deliberately. A default would let a caller that
     * edits some other field silently erase a note it never asked about, and
     * the compiler would not say a word. Required, every caller has to decide,
     * and the one caller there is passes the note it is already holding, so an
     * edit to any other field carries the existing note through.
     *
     * [reminderAt] has no default for the same reason, and a stronger one. A
     * note erased by an unrelated edit is an annoyance; a reminder erased that
     * way is the app quietly breaking the promise it exists to keep.
     *
     * It is one write rather than a separate notes operation for the same
     * reason it reads the task fresh: two writes launched from one save would
     * each read before the other had written, and one of the two edits would
     * be lost.
     *
     * A blank title or a non-positive duration is rejected rather than stored,
     * mirroring the guard Quick Add already applies. A blank note is not
     * rejected but is stored as null, so "no notes" has one representation
     * rather than two that read the same on screen.
     */
    fun editTask(
        id: String,
        title: String,
        notes: String?,
        placement: TaskPlacement,
        scheduledDate: LocalDate?,
        dueDate: LocalDate?,
        estimatedDurationMinutes: Int?,
        recurrence: Recurrence?,
        reminderAt: LocalDateTime?
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        if (estimatedDurationMinutes != null && estimatedDurationMinutes <= 0) return

        val trimmedNotes = notes?.trim()?.takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch

            repository.update(
                task.copy(
                    title = trimmed,
                    notes = trimmedNotes,
                    placement = placement,
                    scheduledDate = scheduledDate,
                    dueDate = dueDate,
                    estimatedDurationMinutes = estimatedDurationMinutes,
                    recurrence = recurrence,
                    reminderAt = reminderAt,
                    // Moving a reminder makes it owed again. Leaving it alone
                    // does not: saving the sheet an hour after a reminder
                    // arrived must not announce it a second time. The rule
                    // belongs here because this is a place that writes
                    // reminderAt, and Task's own documentation says whoever
                    // writes it owns the record of it having been delivered.
                    reminderDeliveredAt = if (reminderAt == task.reminderAt) {
                        task.reminderDeliveredAt
                    } else {
                        null
                    }
                )
            )

            // After the write, so the promise is stored whatever the user
            // says next. Refusing to be notified must not also lose the
            // reminder that prompted the question.
            if (reminderAt != null && reminderAt != task.reminderAt) {
                _reminderJustSet.value = true
            }
        }
    }

    /**
     * Moves [id] to [date], offering a way back.
     *
     * Rescheduling is the most repeated decision a task list asks for, and it
     * is the one that makes a task disappear from the list it was taken on: a
     * task moved to tomorrow is gone from Today the moment it is chosen. That
     * is the same disappearance completing causes, so it answers the same way,
     * with an undo rather than a confirmation.
     *
     * The day before the move is captured here rather than derived later,
     * because the write destroys it. Null is a real value on both sides: a
     * task can be moved off a day onto none, and back onto none again.
     *
     * A move to the day the task already sits on changes nothing and offers
     * nothing, so tapping Today on a task already scheduled for today does not
     * raise a snackbar about a move that did not happen.
     */
    fun rescheduleTask(id: String, date: LocalDate?) {
        viewModelScope.launch {
            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch
            if (task.scheduledDate == date) return@launch

            repository.update(task.copy(scheduledDate = date))
            _pendingUndo.value = PendingUndo.Reschedule(task.id, previousDate = task.scheduledDate)
        }
    }

    /**
     * Puts [id] back on the day it was on before it was rescheduled.
     *
     * The task is read fresh and only its scheduled date is written, so an
     * edit made in the meantime survives, exactly as undoing a completion
     * leaves everything but the completion alone.
     */
    /**
     * Triages [id] into [placement], offering a way back.
     *
     * The same shape as [rescheduleTask], and for the same reason: this is the
     * other decision that makes a task vanish from the list it was taken on. A
     * task moved out of the Inbox is gone from it the moment it is chosen, so
     * it answers with an undo rather than a confirmation.
     *
     * A move to the bucket the task is already in changes nothing and offers
     * nothing, so it raises no snackbar about a move that did not happen.
     */
    fun moveTask(id: String, placement: TaskPlacement) {
        viewModelScope.launch {
            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch
            if (task.placement == placement) return@launch

            repository.update(task.copy(placement = placement))
            _pendingUndo.value = PendingUndo.Move(task.id, previousPlacement = task.placement)
        }
    }

    /** Puts a triaged task back where it was. */
    fun undoMove(id: String) {
        val offer = _pendingUndo.value as? PendingUndo.Move ?: return
        if (offer.taskId != id) return
        if (!_pendingUndo.compareAndSet(expect = offer, update = null)) return

        viewModelScope.launch {
            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch

            repository.update(task.copy(placement = offer.previousPlacement))
        }
    }

    fun undoReschedule(id: String) {
        val offer = _pendingUndo.value as? PendingUndo.Reschedule ?: return
        if (offer.taskId != id) return
        if (!_pendingUndo.compareAndSet(expect = offer, update = null)) return

        viewModelScope.launch {
            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch

            repository.update(task.copy(scheduledDate = offer.previousDate))
        }
    }

    /**
     * Removes a task from view without destroying it.
     *
     * The row is marked deleted rather than dropped, so the deletion can be
     * taken back. Today stops showing the task because the stored stream
     * excludes deleted rows.
     *
     * The undo offer is raised only once the write has happened, so an id that
     * matches no task offers nothing.
     */
    fun deleteTask(id: String) {
        viewModelScope.launch {
            val task = repository.observeTasks().first().firstOrNull { it.id == id } ?: return@launch

            repository.softDelete(id = task.id, deletedAt = Instant.now())
            _pendingUndo.value = PendingUndo.Deletion(task.id)
        }
    }

    /**
     * Takes back the deletion of [id], returning the task to Today.
     *
     * Clearing `deletedAt` is the whole operation: nothing else about the task
     * changed when it was deleted, and the restored task finds its own place
     * again through the Today query rather than being put back by hand.
     *
     * The id must still be the one on offer. An undo for a deletion that has
     * already been superseded does nothing.
     */
    fun undoDelete(id: String) {
        if (!_pendingUndo.compareAndSet(expect = PendingUndo.Deletion(id), update = null)) return

        viewModelScope.launch {
            repository.restore(id)
        }
    }

    /**
     * Withdraws the offer standing for [id], whichever action it was, without
     * undoing anything.
     *
     * An action the user let pass simply stands. Nothing is written.
     */
    fun dismissUndo(id: String) {
        val offer = _pendingUndo.value ?: return
        if (offer.taskId == id) withdrawOffer(offer)
    }

    /** Clears [offer], but only if it is still the one standing. */
    private fun withdrawOffer(offer: PendingUndo) {
        _pendingUndo.compareAndSet(expect = offer, update = null)
    }

    /**
     * Builds a [TaskListViewModel] for production.
     *
     * This is where the real clock enters, as a [SystemCurrentDay] owned by the
     * application. The view model itself takes any [CurrentDay], so tests
     * supply one they control.
     */
    class Factory(
        private val repository: TaskRepository,
        private val currentDay: SystemCurrentDay,
        private val alarms: FocusAlarms
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(TaskListViewModel::class.java)) {
                "Unexpected ViewModel class: ${modelClass.name}"
            }

            @Suppress("UNCHECKED_CAST")
            return TaskListViewModel(
                repository = repository,
                currentDay = currentDay,
                // From the extras rather than held by the factory: the handle
                // belongs to the owner being created for, and a factory that
                // kept one would hand the same saved state to every owner.
                savedState = extras.createSavedStateHandle(),
                alarms = alarms
            ) as T
        }
    }

    private companion object {

        /** Where a running session's start is kept across process death. */
        const val FocusSessionStartedAtKey = "focus.session.startedAt"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SecondsPerMinute = 60L
    }
}
