# Focus

The execution mode. A primary navigation destination whose content is a single
task, not a task list.

`PRODUCT.md` gives Focus two sentences: it is "the execution mode for working
on one task", and it "should remove distractions and make the current task
obvious". Everything below either follows from those, or is an approved
decision recorded as one.

---

# Two states

Focus is a destination and a mode, and the difference between them is the whole
design.

**Ready** is a place. The task that is next, how long it was estimated at, and
a control to begin. The navigation stays.

**Session** is a mode. The task grows into the screen, the navigation goes, and
what is left is the task, the action that finishes it, and a quiet line saying
what follows.

The split is not decoration. It is what makes hiding the navigation honest.

An earlier version of this document said the navigation bar always stays,
because "a destination that hides the control used to open it would be a trap
rather than a calm screen". That reasoning is still correct and is exactly why
the split exists. A user who merely tapped Focus in the bar has asked for
nothing and must not be locked in; a user who tapped Start has asked for the
mode and can leave it by an on-screen control or by back. Ready keeps the bar.
Only Session takes it.

---

# What Ready shows

The visual treatment is specified in `expressive-components.md` under "Focus
screen". What follows is the product behaviour it renders.

The task title, the estimated duration if the task carries one, Start, and
Complete.

The estimate is shown because Today already shows it. A screen about doing the
work should not be the one place the size of it is withheld, and the number is
the user's own answer to how long this will take.

Complete stays available without entering a session. A task can turn out to be
already done, or to take ten seconds, and making the user start and stop a
session to tick it off would be ceremony for its own sake.

Still absent: capture, editing, Task Details, metadata beyond the estimate, a
floating action button.

---

# What Session shows

The task title, the estimate, the shape, Complete, a way out, and one line
naming what comes next.

## The shape

A `Morph` between `MaterialShapes.Circle` and `MaterialShapes.Clover4Leaf`,
drawn behind the title, advanced by how far the session has run against the
task's estimate.

This is the one place Focuslist takes Material 3 Expressive's shape morphing,
and it is taken on one condition: **the morph carries information or it does
not happen.** A shape that merely animated would be the decorative motion
`expressive-motion.md` forbids. This one is a progress indicator with no number
to read.

That imprecision is the point. A silhouette cannot be read to a percentage, and
should not be: a gauge invites clock-watching, which is the opposite of what
this screen is for. The morph says time is moving. It does not invite you to
check how much.

A task with no estimate gets the starting shape and no movement at all. There
is nothing to be a fraction of, and a shape drifting to a rhythm of its own
would look like information and be none.

Not a timer. There is no countdown, no elapsed clock, no digits, and running
past the estimate is ordinary: the shape settles at its final form and stays
there. Overrunning is not failure and the screen does not say it is.

## What comes next

One line, dimmed, at the bottom: the task after this one in the queue, or
nothing when this is the last.

An earlier version of this document ruled a preview of the next task out of
scope, as "one more thing to look at instead of the task". The reversal is
deliberate. `PRODUCT.md` lists a focus queue as a V1 feature, and a queue no
one can see is not a queue; the core promise is knowing what to do next, and
the moment the user is most entitled to that answer is while finishing the
thing before it.

It is a peek, not a picker. It cannot be tapped, scrolled, or chosen from.
Deciding belongs to Today; Session is for execution, and a control that let the
user swap tasks here would import the deciding back into the mode.

Dimmed with colour, not blur. `Modifier.blur` needs API 31 and the app supports
29, so the effect that works everywhere is the one that carries the meaning.

## The way out

An on-screen close control, and back.

Back alone is not enough. Gesture navigation draws no visible back affordance,
and this screen has hidden the one control the user knows about. `PRODUCT.md`
requires that the UI not depend exclusively on gestures, and here that
requirement is what keeps the mode from being the trap it would otherwise be.

Back leaves the session before it leaves the screen: Session, then Ready, then
wherever Focus was opened from.

---

# The queue

Focus draws from `TaskQueries.focusQueue`, which is Today's outstanding work:

    focusQueue(tasks, today) = todayTasks(tasks, today) minus the completed

Defined over `todayTasks` rather than beside it, so Focus follows Today's plan
by construction. Two separate filters would agree until one of them was edited.

Completion is the only thing dropped. Today keeps a finished task in its bottom
band as a record of the session; Focus is for working on one task, and a
finished task cannot be worked on. The ordering is Today's, unchanged: today's
work first, then what has slipped.

Nothing about being *in* the queue is stored. There is no `focused` column, no
ordering column, and no membership to keep in step with anything.

Because Focus resolves to the head of this queue, the order the queue arrives
in is user-visible. `TaskDao.observeTasks` guarantees a total order for exactly
that reason; see `storage.md`.

---

# Which task

    the chosen task while it is still in the queue, otherwise the head

That one line is the whole of Focus's behaviour, and it is worth being precise
about why it is written that way. Completing the task, rescheduling it out of
today, deleting it, and the day rolling over all take it out of the queue. In
every one of those cases the chosen id stops matching anything and the head
appears instead.

So "complete the task, show the next task" is emergent. There is no advance
step, and therefore no way for one of those four routes to be handled and
another quietly missed.

The choice is a pointer into a derived list, not an attribute of a task. It
lives in `TaskListViewModel`, which is app-scoped, so choosing a task on Today
and arriving at Focus finds it still chosen.

It is deliberately not persisted. After the process dies the choice is gone and
Focus opens on the head of the queue, which is a correct state rather than a
broken one.

---

# The session clock

One value: when work on the current task began.

    focusSessionStartedAt: Instant?

A moment rather than a running total, so progress can be worked out from the
clock whenever anyone asks. `focusProgress(startedAt, now, estimate)` derives
the fraction and is a pure function beside `TaskQueries`.

**It measures the task, not the session.** It is restarted when the session
moves on to the next task, because the fraction it feeds is against *that
task's* estimate. Without the restart, finishing a forty-five minute task in
ten and picking up a fifteen minute one would show the new task as already
overrun before a second of it had been worked, and would announce it
immediately.

Restarted only from one real task to another. The first task of a session must
not reset a clock that has just been restored from a killed process, and the
placeholder every flow emits before storage answers is not a task.

**Derived, never accumulated.** A session outlives being backgrounded, and a
process frozen for ten minutes has to come back knowing ten minutes went by. A
counter that only advanced while someone was watching would come back wrong.

Kept in `SavedStateHandle`, so a session survives the process being killed
while the user was away in another app. Over a forty-five minute estimate that
is a normal thing to happen rather than an edge case. It does not survive the
task being swiped away, which is correct: that is a user saying they have
stopped.

This is the one stored notion of being focused, and it is deliberately the
smallest one. It records that a session is running and when it began. It does
not record which task, which stays a pointer as above; it does not touch the
database; and it adds no column to a task.

## Ending on an empty queue

A session with nothing left to work on has ended, whether or not it was
stopped. Leaving it running would hide the navigation behind an empty screen,
which is the trap the mode exists to avoid.

[IMPL] Watched in `TaskListViewModel` against `repository.observeTasks()`, not
from the screen and not against `focusedTask`. Every exposed `StateFlow` begins
on a placeholder before storage has answered, and a screen reading that
placeholder cannot tell "nothing to do" from "not loaded yet". Reading it as
empty ends the session on the way in, which breaks entry from a task row — the
one entry that has to work. The repository only emits once it has really read.

---

# Entry

Two ways in:

- the Focus item in the navigation bar, which opens **Ready** on whatever the
  rule above resolves to
- Focus in a Today row's long-press actions menu, which chooses that task and
  starts the **session** directly

The second is `PRODUCT.md`'s "choose task, tap Focus". It skips Ready on
purpose: picking one task out of a list and choosing Focus on it is the
deciding already done, and a second confirming tap would be friction with
nothing behind it.

Only Today rows offer it. The queue is derived from Today, so a row on Anytime,
Someday, Upcoming, or the Logbook would have to either do nothing or silently
schedule the task for today, and neither is specified behaviour. `TaskListRow`
takes `onFocus` as a nullable callback and omits the menu item when it is not
given, so those four screens are unchanged.

---

# Completion

The same write every list makes, through `toggleComplete`, raising the same
single undo offer. Finishing a task in Focus is exactly as undoable as
finishing it anywhere else, and the offer follows the user to another screen.

Undoing puts the task back in the queue. If it was the chosen one, Focus shows
it again.

Completing inside a session does not leave it. The next task appears in place
and the session continues, which is `PRODUCT.md`'s "continue to the next task"
taken literally. The session ends only when the user stops it or the queue runs
out.

---

# Empty state

One state, for both of the ways the queue empties: nothing scheduled for today,
and everything scheduled already done.

Deliberately one rather than two. A separate "all done" state would be a
celebration, and `PRODUCT.md` rules those out.

---

# Accessibility

The task title is the heading a screen reader lands on, in both states.

The shape carries meaning, so it carries `progressSemantics`. Colour and form
are never the only channel.

Reduced motion is respected, and the line it draws is the same one the shape is
built on. The enter transition is decoration and is skipped entirely. The morph
is **not**, because it is information: freezing it would answer a request for
stillness by withholding the answer. See `expressive-motion.md`.

In Session the title is capped at four lines with an ellipsis, which is what
the square holds at the largest system font scale. It is the one piece of text
in the app with a hard ceiling, and it exists because a title that overruns a
fixed shape is cut through the middle of a line and reads as broken rather than
as shortened. The full title is one tap away in Task Details. Ready has no
shape and wraps freely.

---

# Reachability

Focus is a strict subset of Today, which is what keeps
`task-states.md`'s invariant safe. It is never the only place a task can be
found, so nothing is reachable from Focus and nowhere else, and removing the
screen entirely would strand no task.

It holds no completed tasks. The Logbook and Today's bottom band continue to
hold every one of them.

---

# Out of scope

Not part of Focus:

- a countdown, an elapsed clock, or any digits counting anything
- a pause, a resume, or a session history
- capture, quick add, or a floating action button
- editing, and the task details sheet
- a curated or reorderable queue, and any stored notion of *which* task is
  focused
- pulling work from Anytime when Today is empty
- a picker, a scroller, or any way to change task from inside a session
- white noise and screen dimming
- do not disturb, and anything else that changes the state of the device
- a foreground service, and any claim on the process while a session runs
- reminders, due-date alerts, and any notification not about the running
  session

---

# The estimate being reached

One notification, when a session's estimate runs out and the user is not
looking at it.

An earlier version of this document ruled notifications out of Focus
altogether. The reversal is deliberate and narrow. `PRODUCT.md` lists
notifications as a V1 feature and an Android requirement, and the shape only
answers someone who is looking at it: an estimate expiring while the user is in
another app said nothing at all, which made setting an estimate worth less the
moment you put the phone down.

Narrow means what it says. One notification, about the task being worked on,
at a moment the user asked for by giving the task an estimate. No reminders, no
due dates, no daily summary, nothing recurring, and nothing at all for a task
with no estimate.

## Not while it is on screen

A session the user is looking at is not notified. The shape has been saying
where the estimate stands the whole time, and interrupting the screen whose job
is to protect attention would be the app working against itself.

[IMPL] `FocusSessionVisibility` is read by the receiver at the moment it fires.
It is driven by the **lifecycle**, not by composition: pressing home stops the
activity but leaves the composition standing, so a flag cleared on leaving
composition stays set for the whole time the user is in another app, which is
exactly when the notification is meant to arrive. Started rather than resumed,
so a session behind a permission dialog still counts as visible.

## Inexact, on purpose

Scheduled with `AlarmManager.setAndAllowWhileIdle`.

Exact alarms need `SCHEDULE_EXACT_ALARM`, which is user-grantable, increasingly
restricted, and meant for alarm clocks and calendars. Asking for it here would
be hard to justify, and a few minutes of drift on a forty-five minute estimate
is honest: an estimate is a guess, and announcing it to the second would claim
a precision the number never had. It is the audible counterpart to a shape that
is deliberately unreadable as a gauge.

`setAndAllowWhileIdle` rather than `set`, so Doze cannot hold the announcement
until the user next picks the phone up, which is when they no longer need it.

## Default importance

The channel alerts rather than sitting silent.

Silent would be calmer and `PRODUCT.md` does ask for calm, but it would also be
invisible to the only person it exists for: someone who put the phone down. A
notification nobody notices costs the permission and delivers nothing. Calm
here means rarity — one notification per session, never repeated, and none at
all unless the user set an estimate.

Not ongoing, and auto-cancelling. The estimate being reached is a moment, not a
state, and a notification that could not be dismissed would be the app refusing
to leave.

## The permission

`POST_NOTIFICATIONS` is asked for the first time a session starts on a task
that has an estimate, and never at launch. That is the first moment the app has
anything to notify about, which is the only context in which the question can
be answered well.

Refusal costs nothing on screen. The shape still shows progress; the user is
simply not told when they are elsewhere, which is what they said.

## Where it lives

`core/notification/`, beside `core/time/`, and built the same way: an interface
the view model depends on, and an Android implementation the application owns.

    FocusAlarms.scheduleEstimateReached(taskTitle, at)
    FocusAlarms.cancel()

The view model decides *when* a session should be announced and knows nothing
about alarms, notifications, or channels. Tests substitute a recording
implementation, so the decision is checked without a device scheduling a real
alarm against the machine running the suite.

---

# Verification

`focusQueue` is covered by `TaskQueriesTest`, and the resolution rule by
`TaskListViewModelTest`, including the four routes that take a task out of the
queue. `focusProgress` is covered by `FocusProgressTest`, including overrun, a
clock that has gone backwards, and a missing estimate.

`TaskListViewModelTest` also covers the announcement: that starting a session
schedules the focused task's estimate, that a task without one schedules
nothing, that stopping cancels, and that moving to the next task restarts the
clock and reschedules against the new estimate.

`FocusSessionSemanticsTest` covers the contract that makes hiding the
navigation safe: Ready keeps the bar and shows the estimate, Session hides the
bar and offers a visible way out, completing advances without leaving, an
emptied queue returns the navigation, and a session started before the screen
opens survives the screen opening. Ready and Session are both checked at 100%
and 200% font scale.

The morph cannot be tested. Spring physics and a shape advancing over
forty-five minutes are not meaningfully assertable, so the shape is checked by
watching it on the emulator at several points across a short estimate. State it
that way in reports: the morph was observed, not tested. Both themes and the
session's session-survives-process-death behaviour are also checked by hand.

The notification itself is checked by hand on the emulator against a
one-minute estimate, in both directions: backgrounded, it arrives; left on
screen, it does not.
