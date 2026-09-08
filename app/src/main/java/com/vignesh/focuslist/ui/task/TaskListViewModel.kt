package com.vignesh.focuslist.ui.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.vignesh.focuslist.core.domain.FocusNow
import com.vignesh.focuslist.core.domain.FocusNowReason
import com.vignesh.focuslist.core.domain.FocusSession
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.nextRecurringInstance
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskCompletion
import com.vignesh.focuslist.core.time.CurrentDay
import com.vignesh.focuslist.core.time.SystemCurrentDay
import com.vignesh.focuslist.core.domain.completedTasks as queryCompletedTasks
import com.vignesh.focuslist.core.domain.focusNow as queryFocusNow
import com.vignesh.focuslist.core.domain.inboxTasks as queryInboxTasks
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
import java.time.Duration
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
 * one task, chosen by the user.
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
     * Every task the repository holds, deleted ones excluded by the DAO.
     *
     * The one flow that is not a view. Task Details is reached by id from any
     * list, so it cannot read the list it was opened from: a task rescheduled
     * off Today while its details are open would stop being found and the
     * screen would close on an edit the user just made.
     *
     * No query wraps it, because there is no filtering to do. Adding one that
     * merely copied the list would be a view in the `TaskQueries.kt` sense
     * without being a view of anything.
     */
    val allTasks: StateFlow<List<Task>> =
        repository.observeTasks()
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
     * Every outstanding task without a scheduled date, derived from the same
     * stored stream. The query owns the filtering and the ordering.
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
     * The task the user chose to focus on, or null while they have chosen
     * none.
     *
     * Not stored on the task, and not persisted across a relaunch. Focus is
     * entered by picking the task it is for, so a session that did not survive
     * the process has no task to resume and null is the honest answer.
     */
    private val _focusedTaskId = MutableStateFlow<String?>(null)

    /**
     * The one task Focus is on, and only ever that one.
     *
     * There used to be a fallback here: when the chosen task left, Focus
     * showed the head of a queue instead. That was the queue `docs/decisions.md`
     * D-004 removed, surviving as a resolution rule, and it contradicted the
     * answer Focus gives to "why this task", which is: because you said so.
     * The Clean Slate board says the same thing in one line on the Focus
     * screen, "One task. Nothing else until you leave Focus."
     *
     * So completing the task, deleting it, or never choosing one all resolve
     * to null, and the session ends where the task did rather than moving the
     * user somewhere they did not ask to be.
     */
    val focusedTask: StateFlow<Task?> =
        combine(repository.observeTasks(), _focusedTaskId) { tasks, id ->
            tasks.firstOrNull { task ->
                task.id == id && !task.isDeleted && !task.isCompleted
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )

    /**
     * Points Focus at [id].
     *
     * Recorded as given, without checking anything. An id that matches no
     * outstanding task resolves to null, which is the same state as a task
     * that was completed or deleted while Focus was open.
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
        _isFocusSheetOpen.value = true
    }

    private val _focusSession = MutableStateFlow(restoreFocusSession())

    /**
     * The session being worked, or null while none has been started.
     *
     * A moment and a banked duration rather than a running total, so progress
     * can be worked out from the clock whenever anyone asks. Kept in
     * [SavedStateHandle] so a session survives the process being killed while
     * the user was away in another app, which over a forty-five minute estimate
     * is a normal thing to happen rather than an edge case.
     *
     * `docs/decisions.md` D-013 replaced the bare start instant this used to
     * be. A session that can be paused needs three more things than a start:
     * what earlier legs added up to, whether the clock is moving, and how far
     * the estimate has been extended. [FocusSession] holds all four and does
     * the arithmetic.
     */
    val focusSession: StateFlow<FocusSession?> = _focusSession.asStateFlow()

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
    private val _isFocusSheetOpen = MutableStateFlow(false)

    /**
     * Whether the Focus sheet is on screen.
     *
     * **Separate from whether a session exists, and D-015 is why.** Leaving now
     * pauses rather than stops, so a paused session outlives the sheet by
     * design. If the sheet were still driven by the session, closing it would
     * reopen it on the next frame.
     *
     * Not persisted. A process that died was not showing anything, and the
     * paused session it left behind is reachable from the Focus now card, which
     * is the whole point of D-015.
     */
    val isFocusSheetOpen: StateFlow<Boolean> = _isFocusSheetOpen.asStateFlow()

    /**
     * Starts working on whatever [focusedTask] currently is.
     *
     * Takes no task: choosing and starting are separate acts, and the caller
     * that wants both does both. This is what Ready's Start focus does.
     */
    fun startFocusSession() = restartFocusClock()

    /**
     * Stops the clock, keeping everything worked so far.
     *
     * The session stays and so does the task pointer, because a paused session
     * is one the user can come back to, and the Focus now card is what points
     * at it.
     */
    fun pauseFocusSession() {
        writeFocusSession(_focusSession.value?.paused(Instant.now()))
    }

    /** Starts the clock again, without counting the time spent paused. */
    fun resumeFocusSession() {
        writeFocusSession(_focusSession.value?.resumed(Instant.now()))
    }

    /**
     * Gives the session another five minutes, at the estimate.
     *
     * On the session, never on the task. A task's estimate is the user's answer
     * to how long the work takes, and one session running long is not a
     * correction to it: extending must not quietly rewrite the number Today
     * adds up and Task Details shows.
     */
    fun extendFocusSession() {
        writeFocusSession(_focusSession.value?.extended())
    }

    /**
     * Marks now as the moment work on the current task began.
     *
     * A fresh session rather than a nudged one: nothing is carried from a
     * previous task, because the clock measures *this task* against *its*
     * estimate. Without the reset, finishing a forty-five minute task in ten
     * and moving to a fifteen minute one would show the new task as already
     * overrun before a second of it had been worked.
     */
    private fun restartFocusClock() {
        writeFocusSession(FocusSession(startedAt = Instant.now()))
    }

    /**
     * Leaving the Focus sheet, by the chevron, the scrim, the drag or back.
     *
     * **It pauses. It never stops.** That is `docs/decisions.md` D-015, and it
     * supersedes D-013's clause that a running session did not survive leaving.
     *
     * The control cannot tell which of two intentions a tap carries. "I am
     * finished with this" and "I need to look at something else for a minute"
     * are both ordinary and arrive through the same button, so the question is
     * not which is likelier but which mistake is cheaper to be wrong about.
     * Stopping when the user meant to pause loses the elapsed time silently,
     * with nothing that puts it back. Pausing when they meant to stop leaves one
     * card on Today that they can ignore, and that completing the task clears.
     * One failure is unrecoverable and invisible; the other costs a glance.
     *
     * D-013's principle is unchanged, not reversed: a session running with
     * nothing on screen pointing at it is state the user cannot reach, and
     * pausing on the way out means that situation never occurs.
     *
     * The alarm goes, because a paused clock has no moment for the estimate to
     * be reached at and an alarm left pointing at one would fire while the user
     * was deliberately not working. The `init` watcher below does that: pausing
     * changes the session, which re-runs the announcement.
     */
    fun leaveFocusSheet() {
        pauseFocusSession()
        _isFocusSheetOpen.value = false
    }

    /**
     * Completing the task from inside Focus.
     *
     * The same write every list makes, so finishing here is exactly as undoable
     * as finishing anywhere else and the offer follows the user to Today.
     * Completing closes the sheet, because the screen has nothing left to be
     * about; undoing puts the task back on the list and does not reopen Focus.
     */
    fun completeFromFocus(id: String) {
        toggleComplete(id)
        endFocus()
    }

    /**
     * Ends Focus outright: no session, no chosen task, no sheet.
     *
     * Not a control the user has. D-015 removed the one that stopped a session,
     * and stopping was only ever pausing and not resuming. This is for the two
     * cases where there is nothing to come back to: the task was completed, or
     * it was deleted from somewhere else while the sheet was open. Pausing is
     * safe because something is waiting; here nothing is.
     */
    fun endFocus() {
        writeFocusSession(null)
        alarms.cancel()
        _focusedTaskId.value = null
        _isFocusSheetOpen.value = false
    }

    /** One place that writes the session, so the flow and the saved state agree. */
    private fun writeFocusSession(session: FocusSession?) {
        _focusSession.value = session

        if (session == null) {
            savedState.remove<Long>(FocusSessionStartedAtKey)
            savedState.remove<Long>(FocusSessionPausedAtKey)
            savedState.remove<Int>(FocusSessionExtraKey)
        } else {
            savedState[FocusSessionStartedAtKey] = session.startedAt.toEpochMilli()
            savedState[FocusSessionPausedAtKey] = session.pausedAt?.toEpochMilli()
            savedState[FocusSessionExtraKey] = session.extraMinutes
        }
    }

    /**
     * The session as it was before the process died, or null if there was none.
     *
     * The origin is what says a session existed at all; the pause is absent for
     * a running one, which is the same shape the value has in memory.
     */
    private fun restoreFocusSession(): FocusSession? {
        val startedAt = savedState.get<Long>(FocusSessionStartedAtKey) ?: return null

        return FocusSession(
            startedAt = Instant.ofEpochMilli(startedAt),
            pausedAt = savedState.get<Long>(FocusSessionPausedAtKey)?.let(Instant::ofEpochMilli),
            extraMinutes = savedState.get<Int>(FocusSessionExtraKey) ?: 0
        )
    }

    /**
     * The task the Focus now card holds, or null when nothing qualifies.
     *
     * `docs/decisions.md` D-012. The rule is a pure function in `core/domain`;
     * this only supplies it with the three things it cannot read for itself:
     * the stored tasks, the current day, and which task a paused session is on.
     *
     * **It reads every task, not Today's list.** A paused session's task need
     * not be scheduled for today: a task focused from Inbox and paused is still
     * the thing the user was doing, and sending them back to find it would be
     * the app losing their place.
     *
     * The clock is read at collection time rather than injected, unlike
     * [today]. Two of the three reasons turn on the time of day, and `CurrentDay`
     * is a day: it emits on a date change and nothing finer. What that costs is
     * that a reminder passing does not re-run the rule on its own; the card
     * appears the next time anything else emits, which in practice is the next
     * write or the next time the screen is opened. Making it exact would mean a
     * timer per reminder on the screen the app opens to, and the reminder itself
     * is what is responsible for interrupting the user. The card is where the
     * task goes afterwards.
     */
    val focusNow: StateFlow<FocusNow?> =
        combine(
            repository.observeTasks(),
            currentDay.today,
            _focusSession,
            _focusedTaskId
        ) { tasks, day, session, focusedId ->
            queryFocusNow(
                tasks = tasks,
                today = day,
                now = LocalDateTime.now(),
                // Only a *paused* session earns the card's strongest reason. A
                // running one is already on screen in the sheet, and a card
                // pointing at it would be the app telling the user to go where
                // they already are.
                pausedTaskId = focusedId?.takeIf { session?.isPaused == true }
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )

    /**
     * Opening Focus from the card.
     *
     * Two behaviours behind one action, and the card's label says which is
     * which. A paused session resumes, because the user pressed Resume and
     * making them press it again inside the sheet would be a confirmation of a
     * confirmation. Anything else chooses the task without starting a clock,
     * which lands the sheet on Ready.
     *
     * **Ready is why this is not `beginFocus`.** `focus.md` has a task row skip
     * Ready on the grounds that picking one task out of a list is the deciding
     * already done. The card is the opposite case: the *app* picked, and Ready
     * is where the user agrees with the choice before the clock runs. That is
     * also the entry Ready lost when Focus left the navigation bar in Phase 3,
     * which is what left D-013's sixth state unreachable.
     */
    fun openFocusFromCard() {
        val card = focusNow.value ?: return

        if (card.reason == FocusNowReason.ResumePaused) {
            // The card's button already read Resume, so the user has pressed it.
            // Making them press play inside the sheet would be a confirmation of
            // a confirmation.
            focusTask(card.task.id)
            _isFocusSheetOpen.value = true
            resumeFocusSession()
            return
        }

        openFocus(card.task.id)
    }

    /**
     * Opens Focus on [id] in Ready: chosen, with no clock running.
     *
     * Any session left over from another task goes, because the clock measures
     * this task against *its* estimate. Without that, picking up a fifteen
     * minute task after forty minutes on another would open already overrun.
     */
    fun openFocus(id: String) {
        focusTask(id)
        writeFocusSession(null)
        _isFocusSheetOpen.value = true
    }

    init {
        // The sheet is open on a task that no longer exists.
        //
        // `focus.md`: a session whose task is finished or gone has ended,
        // whether or not it was stopped, and it is the one case D-015's pausing
        // does not cover — pausing is safe because there is something to come
        // back to, and here there is not.
        //
        // Watched against the stored stream rather than against `focusedTask`,
        // because that flow starts on a placeholder and reading the placeholder
        // as "gone" would close the sheet on the way in. The repository only
        // emits once it has really read.
        viewModelScope.launch {
            combine(
                _isFocusSheetOpen,
                _focusedTaskId,
                repository.observeTasks()
            ) { isOpen, chosen, tasks ->
                isOpen && chosen != null && tasks.none { task ->
                    task.id == chosen && !task.isDeleted && !task.isCompleted
                }
            }
                .distinctUntilChanged()
                .collect { isGone -> if (isGone) endFocus() }
        }

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
            _focusSession
                .map { session -> session != null }
                // On the boolean, so restarting the clock for a new task does
                // not tear down and rebuild the very collection that noticed.
                .distinctUntilChanged()
                .flatMapLatest { isRunning ->
                    // Nothing to watch while no session is running.
                    if (!isRunning) {
                        emptyFlow()
                    } else {
                        // The same flow the screen draws, rather than a
                        // second copy of the resolution. Two rules for "which
                        // task is Focus on" is how the alarm ends up announcing
                        // a task the user is not looking at.
                        //
                        // Paired with the session, because since D-013 the
                        // moment the estimate is reached is no longer fixed
                        // when the session starts: pausing removes it, resuming
                        // pushes it out by however long the user was away, and
                        // +5 min moves it deliberately. An alarm placed once at
                        // the start would fire in the middle of a pause.
                        combine(focusedTask, _focusSession) { task, session -> task to session }
                    }
                }
                .collect { (task, session) ->
                    if (task == null) {
                        // The task finishing no longer ends the session. It
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
                        return@collect
                    }

                    // No clock restart here. Choosing another task is the only
                    // way this task changes now, and `beginFocus` already
                    // restarts the clock as part of choosing.
                    //
                    // It used to restart here as well, because the queue could
                    // hand Focus a new task with no user action behind it. With
                    // the queue gone that branch fired a second restart a few
                    // hundred microseconds after the first, so the estimate was
                    // scheduled against a start time that nothing else held.
                    announce(task, session)
                }
        }
    }

    /**
     * Asks for the estimate being reached to be announced, or for nothing.
     *
     * A task with no estimate has no moment to announce, and a task already
     * past its estimate has had it: scheduling in the past would fire at once
     * and tell the user something they worked out by looking at the clock.
     *
     * A paused session has no moment either, and [FocusSession.estimateReachedAt]
     * is where all three of those answers live, so the arithmetic stays testable
     * without a device and this function only obeys.
     */
    private fun announce(task: Task, session: FocusSession?) {
        val reachedAt = session?.estimateReachedAt(Instant.now(), task.estimatedDurationMinutes)

        if (reachedAt == null) {
            alarms.cancel()
            return
        }

        alarms.scheduleEstimateReached(task.title, reachedAt)
    }

    /**
     * Captures a new task, and reports whether anything was captured.
     *
     * The caller supplies [scheduledDate]: Today captures for today, Inbox
     * captures without a day, and deciding when is the decision Inbox exists
     * to defer.
     *
     * A blank title creates nothing and returns false, since the title is the
     * one thing a task cannot do without. The screen keeps its sheet open on a
     * false so the user can finish typing, and closes it on a true rather than
     * waiting on the write, which is local and fast.
     */
    fun createTask(
        title: String,
        scheduledDate: LocalDate?,
        // A moment to be interrupted at, or null for a task that never speaks
        // up. Quick Add supplies one when the title ended in a time, per
        // `docs/decisions.md` D-011; everything else passes null.
        reminderAt: LocalDateTime? = null
    ): Boolean {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return false

        viewModelScope.launch {
            repository.insert(
                Task(
                    id = UUID.randomUUID().toString(),
                    title = trimmed,
                    createdAt = Instant.now(),
                    scheduledDate = scheduledDate,
                    reminderAt = reminderAt
                )
            )
        }

        // A promise has been made and stored, and nothing here knows whether
        // the app is allowed to keep it. The same flag every other reminder
        // write raises, so a capture that set one is checked exactly as a
        // reminder set from Task Details is.
        if (reminderAt != null) _reminderJustSet.value = true

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

        /**
         * Where a session is kept across process death.
         *
         * Three keys, one per value. Pausing moves the origin rather than
         * starting a tally, so it needs none of its own beyond the moment the
         * clock stopped, which is what a stopped clock reads from instead of
         * the current time. The extension is explicit; the field's own KDoc has
         * the bug that says why it cannot move the origin too.
         */
        const val FocusSessionStartedAtKey = "focus.session.startedAt"
        const val FocusSessionPausedAtKey = "focus.session.pausedAt"
        const val FocusSessionExtraKey = "focus.session.extraMinutes"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
