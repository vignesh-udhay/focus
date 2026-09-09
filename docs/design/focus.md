# Focus

The execution mode. A sheet opened from one task, not a destination and not a
task list.

`PRODUCT.md` gives Focus two sentences: it is "the execution mode for working
on one task", and it "should remove distractions and make the current task
obvious". Everything below either follows from those, or is an approved
decision recorded as one.

---

# One surface, six states

Focus is one surface with six states, opened as a sheet over the screen that
asked for it. It is not two screens, and it has not been a destination since
`docs/decisions.md` D-004 removed the queue a bar entry would have landed on.
`navigation.md` holds the current arrangement.

    Ready              sit    45:00 · 45 min focus       Complete · play
    Running            nap    44:37 · 45 min focus       Complete · pause
    Paused             sit    32:18 · 32 min left        Complete · play
    Estimate reached   nap    00:00 · Estimate reached   Complete · +5 min
    Open-ended         nap    12:43 · No time limit      Complete · pause
    Open-ended paused  sit    12:43 · No time limit      Complete · play

D-013 added the pause, the resume and the extension. D-014 settled what the
shape says. D-015 settled what leaving does. **D-046 replaced the shape with the
cat and moved the clock onto the status line**, so the middle two columns above
read differently from every earlier version of this document.

**An earlier version of this document split Focus into Ready and Session**, two
states with different navigation: Ready kept the bar, because a user who had
merely tapped Focus in the bar had asked for nothing and must not be locked in,
and Session took it, because that user had asked for the mode. Both halves are
gone. There is no Focus item in the navigation bar to arrive from, and a sheet
has no navigation bar to keep or take in any state. What that argument was
protecting still holds and is now D-015's job: leaving is always safe, because
leaving never destroys anything.

---

# What it shows

Top to bottom: the task title, the cat, one status line carrying the clock and
the budget, and one row of two controls.

**The title is the heading, and the largest thing on the screen.** It sits above
the cat, capped at four lines.

The cap is older than the cat and it outlived what it was for. D-014 put the
title outside the shape with an arithmetic argument: a cookie yields about 70%
of its box as usable area, so four lines at 200% font scale would have needed a
514dp square on a 412dp screen, and the 240dp column inside it filled at 24
characters. Nothing is inside anything now, so that argument is spent, and the
cap stays for the other half of it. A title that overruns a fixed container is
cut through the middle of a line and reads as broken rather than as shortened,
and four lines is what the column holds at the largest system font scale. The
full title is one tap away in Task Details.

**The cat is second, and it is the app's own mascot rather than a shape.**
D-046. Two poses, `cat-sit-front` when the clock is stopped and `cat-nap` while
it runs, drawn from the same board and at the same scale factor as the five
empty-state poses. What it says is what the shape said: whether the clock is
moving. It is not a gauge and it does not track progress.

**The time is third now, on the status line at body size.** It is a real readout
rather than an ornament: an estimate counts down, an open-ended session counts
up, and a paused session has to be able to say what is left or resuming it means
nothing. D-013 requires it, because three of the six states cannot be told apart
without it.

D-014 already argued that the largest object on a screen built to stop
clock-watching should not be the clock, and cut it from Display Large to
Headline Small. D-046 finished that: at Headline Small inside the shape the
countdown was still the second largest thing here, and on the status line it has
stopped being a display object. The task title is now unambiguously the largest.

The estimate is shown because Today already shows it. A screen about doing the
work should not be the one place the size of it is withheld, and the number is
the user's own answer to how long this will take.

**The status line is the clock, then whatever the controls cannot say.** The
button already reads Pause or Resume, so putting the state in text would say it
twice. What is left is the budget, and the one moment with no control to
announce it:

    Ready, Running       45:00 · 45 min focus
    Paused               32:18 · 32 min left
    Estimate reached     00:00 · Estimate reached
    Open-ended, paused   12:43 · No time limit

Ready and Running read the same budget, deliberately. What tells them apart is
the pose, the icon on the button, and the digits moving.

**The clock control is a round icon button.** 56dp, filled, carrying play or
pause. Holding no text, it does not grow with the font scale, and that is what
makes the row fit: at 200% two worded buttons come to roughly 223dp and 198dp
and overflow the 364dp row, while a circle and one word come to about 282dp.

This is the surviving half of an older argument that also claimed the button had
to be square, because the container transform started from it and a square is
already the circle the shape rings began at. The transform is gone. The width is
still real.

Its content description names the action per state, Start focus, Pause focus,
Resume focus, and never the glyph.

Estimate reached is the one state with no clock control, because there is no
clock left to control. It carries Complete and +5 min, both worded. Complete is
the primary there and +5 min the secondary: a timer running out is more often
the moment work is finished than the moment it needs extending, and the control
that reads as the default should be the likelier one.

**Complete leads and the clock control trails.** D-046, and the ordering had
never been decided before it: the row was built clock-first and stayed that way.

Two reasons. Complete now sits in the same place in all six states, where before
it trailed in five and led in Estimate reached, which is the one state with no
clock control for it to trail. And the clock is the control pressed repeatedly
inside a session, start then pause then resume, where Complete is pressed once
at the end; the repeated one belongs where the thumb already is.
`expressive-components.md` makes exactly that argument for the Start focus pill
on Task Details, "the pill sits where the thumb rests".

**Complete keeps its lower standing through tone.** It is the tonal button in
every state, beside a filled control, because it is the second of two actions.
Position and emphasis are separate here and only emphasis carries the ranking,
which is also the ordinary Material arrangement: the lower-emphasis action
leads, the filled one trails.

An earlier version made Complete a quiet text action below Start, on the grounds
that starting is the constructive act and completing without starting is the
exception. It was reversed for a reason that still holds: a quiet action below a
loud one reads as a footnote rather than as the other half of a pair.

Complete stays available without entering a session for the same reason it
always did. A task can turn out to be already done, or to take ten seconds, and
making the user start and stop a session to tick it off would be ceremony for
its own sake.

**There is no top app bar, and no control in one.** An earlier version of this
document ruled the bar out, a second pass brought it back to hold a dismiss
chevron, and D-032 has now removed both. The name is published as `paneTitle`,
which a screen reader announces and nothing draws.

**The way out is the drag handle.** `ModalBottomSheet` is called with no
`dragHandle` argument, so Material draws its default, and the chevron that used
to sit beneath it was a second affordance saying the same thing. D-032 has the
argument and the cost: a 48dp labelled target becomes a 32x4dp bar, and for a
screen reader the sheet's own dismiss action carries it.

**Leaving is still never destructive.** D-015 governs that and is untouched. The
handle, the drag, the scrim and the back gesture all reach `onDismissRequest`,
and all of them pause.

Still absent: capture, editing, Task Details, metadata beyond the estimate, a
floating action button.

---

# The cat

`cat-sit-front` when the clock is stopped and `cat-nap` while it runs, drawn
where the shape used to be and crossing between the two on a state change.

**What it says is whether the clock is running.** That is the whole of it. It is
not a gauge and does not track progress. The digits do that.

This is D-046, and it replaced the `Cookie4Sided` / `Cookie12Sided` morph D-014
had reduced to exactly this one sentence. Once the shape's whole job was "is the
clock moving", the job belonged to the animal the app already had: five poses
drawn on the same board, resolved against three colour roles so they follow
dynamic colour, and decorative to a screen reader by an argument already made.

Before D-014 the shape had been a progress indicator, a determinate walk from
`Circle` to `Clover8Leaf` against the estimate with a ring of six shapes for a
session that had no estimate to walk against. That design was never drawn, and
the reason it went is worth keeping: once D-013 put a readable number on the
screen, the shape and the digits measured the same quantity and the shape was
worse at it. A second channel that says what the first says, less well, is
decoration, and `expressive-motion.md` bans decoration. D-046 changes what draws
the state; it does not reopen what the state channel is for.

**Two poses cover six states.** Ready, Paused and Open-ended paused sit.
Running, Estimate reached and Open-ended nap. That is D-014's own partition, so
nothing new had to be decided about which state looks like what.

**The cat does not wake up when the estimate runs out.** Estimate reached naps
like any other running state. Running past the estimate is ordinary: the digits
reach zero, the status line says so, and nothing completes the task by itself.
Overrunning is not failure and the screen does not say it is, which an animal
sitting up at the buzzer would.

**Both poses share a ground line and are scaled about their own feet**, so the
floor under the cat does not move while the body compresses. Nothing translates.
The poses are 192.72 x 178.87 and 202.55 x 119.13, they are drawn at those sizes
inside the union of the two rather than filling a common width, and the box is
bottom-aligned. Letting each fill the same width would shrink the wider one by
about five percent, which is the one thing that stops them reading as one
animal.

**`cat-sit-front` is a fourth sitting pose and that is deliberate.** The three
shipped sits are empty-state poses on list screens, where posture says why a
list is empty. This pair is in a sheet, is not an empty state, and is read
against its own partner. D-046 has the argument, and the ceiling that goes with
it: no prop, no third Focus pose, and nothing outside a session.
---

# The container transform, and why it is gone

An earlier version grew the play button into the session's shape, and a long
passage of this document worked out how: the colour read off the container's
travel rather than sprung separately, so the two could not come apart; the
fade-through split at thirty percent, so a label never outlived its own
background; Complete stretching from its leading edge, so that leaving was the
exact mirror of arriving; the raised elevation held for the duration of the
pass, because a shadow is what says which of two overlapping surfaces is in
front.

All of it was real and none of it survives. It existed to join two screens that
are now one surface, and the thing it grew into is now a shape that changes in
place. The sheet's own entrance is the transition into Focus, and
`expressive-motion.md` forbids a second shape morph.

It is recorded rather than deleted because the reasoning generalises past this
screen: two things animating on separate specs arrive at separate times, and the
fix is to make one a function of the other rather than to tune both until they
agree.

---

# Which task

    the task the user chose, while it is outstanding

That is the whole of Focus's behaviour. Completing it or deleting it resolves
to null, and Focus ends where the task ended.

**There used to be a queue here, and it outlived the decision that removed
it.** `docs/decisions.md` D-004 cut the Focus queue on the grounds that "the
queue multiplies the concept. Focus works on one task." What survived was a
resolution rule:

    the chosen task while it is still in the queue, otherwise the head

That fallback was the queue. Completing the task, rescheduling it out of
today, deleting it, or the day rolling over all dropped the chosen task, and
Focus moved to whatever headed today's list. It is also precisely the
behaviour that took Focus out of the navigation bar, described below: the user
landed on a task with nothing to say why that one.

The Clean Slate board settles it. No Focus frame carries a next-task preview,
and Focus — Ready reads "One task. Nothing else until you leave Focus."

The choice is one id in `TaskListViewModel`, which is app-scoped, so choosing
a task on Today and arriving at Focus finds it still chosen. Nothing about
being focused is stored on the task: there is no `focused` column and no
membership to keep in step with anything.

It is deliberately not persisted. After the process dies the choice is gone
and nothing is focused, which is the honest answer rather than a broken one.

---

# The session clock

One value: when work on the current task began.

    focusSessionStartedAt: Instant?

A moment rather than a running total, so the remaining time can be worked out
from the clock whenever anyone asks. `FocusSession` is the pure model beside
`TaskQueries`, holding the origin, the moment of any pause, and any extension;
its `elapsed(now)` and `remaining(now, estimate)` derive everything else. The
digits on screen are that derivation, formatted.

`focusProgress(startedAt, now, estimate)` used to sit here and returned a
fraction. It went with D-014: a fraction existed to advance the shape, and the
shape stopped measuring.

**Pausing moves the origin rather than starting a tally.** D-013 added a pause,
which looks like it forces a stored total: something has to remember the eleven
minutes already worked. It does not. Resuming shifts `focusSessionStartedAt`
forward by however long the pause lasted, so the single value goes on meaning
exactly what it meant before, and the rule below survives intact. What a paused
session shows is the elapsed time it held at the moment it paused, which is what
its digits already say.

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

## Ending with nothing to work on

A session whose task is finished or gone has ended, whether or not it was
stopped. Leaving it running would leave a sheet counting down against a task
that no longer exists, which is the one case D-015 does not cover: pausing is
safe because there is something to come back to, and here there is not.

[IMPL] Watched in `TaskListViewModel` against `repository.observeTasks()`, not
from the screen and not against `focusedTask`. Every exposed `StateFlow` begins
on a placeholder before storage has answered, and a screen reading that
placeholder cannot tell "nothing to do" from "not loaded yet". Reading it as
empty ends the session on the way in, which breaks entry from a task row — the
one entry that has to work. The repository only emits once it has really read.

---

# Entry

Three ways in:

- **the paused session card on Today**, which opens the sheet on the session it
  names, **already running**
- **Start focus on Task Details**, which opens the sheet on the task being
  edited, with the clock started
- **Focus on a Today row**, the same, on the task the row is for

**None of them lands on Ready, and this paragraph used to say two of them did.**
D-048 removed the card's other behaviour: it spoke for a paused session or a
reminder that had passed, and only the second had no session yet, so only the
second needed a state where a task is chosen and no clock is running. With that
reason gone the card always resumes.

The Task Details claim was wrong before D-048 and is worth naming separately:
`TaskDetailsScreen` calls `beginFocus`, which starts the clock, not `openFocus`,
which is the one that lands on Ready. The document said Ready and the code has
gone straight into the session for as long as that call has been there.

**So Ready is currently unreachable**, and `openFocus` has no caller outside an
instrumented test. This document argues two sections below that "a drawn state
nothing can arrive at is a state that should not exist", which makes this a
question for a decision rather than something to quietly patch here: either an
entry point lands on Ready again, or D-013's six states become five. It is
recorded in `docs/decisions.md` D-048 as a consequence that entry did not
foresee.

**A third way used to exist**, Focus in a Today row's long-press menu, and it was
the one that skipped Ready and started the session directly, "because picking one
task out of a list and choosing Focus on it is the deciding already done". D-023
removed the row menu, and the special case went with it.

That is a simplification rather than a loss. Ready was previously reachable by
some routes and not others, so the state a user met depended on how they had
arrived. Now every entry lands on the same place and the user presses play.

The first does not skip it, and the difference is who chose. The card names a
task the app picked and gives its reason; Ready is where the user agrees with
that pick before the clock runs. This is also the only thing that makes Ready
reachable, which is worth stating plainly, because a drawn state nothing can
arrive at is a state that should not exist.

**There used to be a third way in**, the Focus item in the navigation bar,
opening on whatever a queue resolved to. D-004 removed the queue and
`navigation.md` removed the bar item. Landing a user on whichever task happened
to head a list, with nothing to say why that one, is precisely what both
decisions were getting rid of. The paused session card is its replacement and
differs in the one way that mattered: it names a session the user started and
paused themselves, so there is no choice being made on their behalf at all. Under
D-012 the card made one and stated its reason; D-048 left it with nothing to
choose between.

Only Today rows offer it. A row on Upcoming or the Logbook would have to
either do nothing or silently
schedule the task for today, and neither is specified behaviour. `TaskListRow`
takes `onFocus` as a nullable callback and omits the menu item when it is not
given, so those four screens are unchanged.

---

# Completion

The same write every list makes, through `toggleComplete`, raising the same
single undo offer. Finishing a task in Focus is exactly as undoable as
finishing it anywhere else, and the offer follows the user to another screen.

Completing closes the sheet and returns to Today. Undoing puts the task back on
the list; it does not reopen Focus.

**An earlier version had the session continue onto the next task**, calling that
`PRODUCT.md`'s "continue to the next task" taken literally, with the shape
springing back as the new title arrived so the restarted clock had something
saying so. All of that needs a queue, and D-004 removed the queue. It survived
here for the same reason the old resolution rule under "Which task" survived,
and it goes for the same reason: there is no next task to continue to, because
Focus works on the one task the user chose.

---

# There is no empty state

Focus is always opened on a task, completing that task returns to Today, and
leaving pauses rather than ends. So the only way to arrive at an empty Focus is
for the task to be deleted from somewhere else while the sheet is open. When
that happens the sheet closes and returns to Today, which is where the user
would have to go anyway.

**An earlier version specified one**, covering "the task finished, and
everything scheduled already done", arrived at rather than cut to: the last
shape unwound to the circle and shrank back into the button it came out of
before the empty state faded in, so that the ending was felt without being
celebrated. It needed the queue to have a notion of everything being done, it
needed the container transform to arrive the way it described, and it was never
drawn. Closing is the honest answer for a screen that has nothing left to be
about.

What that version was protecting is worth keeping in view: `PRODUCT.md` rules
out celebrations, so whatever Focus does at the end must not become one. Closing
quietly clears that bar by not saying anything at all.

---

# Accessibility

The task title is the heading a screen reader lands on, in every state.

The cat publishes nothing, and it is decorative for the same reason every other
mascot is. What it says is whether the clock is running, and that is already
said twice in text: the button reads Pause or Resume, and the status line names
the state. It is never the only channel, so it needs no semantics of its own.
The shape it replaced was silent on the same grounds, under D-014.

An earlier version of this document said the shape carried `progressSemantics`.
It never did, and it should not have: announcing a deliberately unreadable gauge
to the decimal would have handed a screen reader exactly the clock-watching this
screen exists to prevent. That whole problem is now gone, because the number is
on the screen as text for everyone.

Reduced motion is respected, and D-046 keeps it cheap. The crossing is a brief
state change rather than a running animation, so under reduced motion the pose
simply swaps and the scale does not happen. Nothing is withheld by that: the
pose is information and it still changes, the settle is decoration and it does
not. Nothing else on this screen moves while a session is running. See
`expressive-motion.md`.

The title is capped at four lines with an ellipsis. It is the one piece of text
in the app with a hard ceiling. The cap exists because a title that overruns a
fixed container is cut through the middle of a line and reads as broken rather
than as shortened, and four lines is what the column holds at the largest system
font scale. The full title is one tap away in Task Details.

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

**Superseded in part by D-013.** The pause and the resume come back, and so does
the readout. What follows is the list as it now stands; the two struck items and
the reasoning are below it.

Not part of Focus:

- a session history: no log of past sessions, no totals, no streaks
- capture, quick add, or a floating action button
- editing, and the task details sheet
- a curated or reorderable queue, and any stored notion of *which* task is
  focused
- a picker, a scroller, or any way to change task from inside a session
- white noise and screen dimming
- do not disturb, and anything else that changes the state of the device
- a foreground service, and any claim on the process while a session runs
- reminders, due-date alerts, and any notification not about the running
  session

## What D-013 took off this list, and why

**A pause and a resume.** D-013 names this one and gives the argument: the Focus
now card's first and strongest reason is "resume paused focus", and building the
card without a session that can be paused ships the rest of its reasons and
leaves the most useful one as a comment. That argument has since got sharper
rather than weaker: D-035 cut the card to two reasons, so a Focus without pause
would now leave it with one. `PRODUCT.md` principle 7 still rules out the session
history, and nothing here asks for one.

**A countdown and an elapsed clock.** D-013 does not name this one, and it takes
it all the same: its six-state table gives every state a readout, and three of
those states cannot be told apart without one. That gap was found while building
this and settled rather than guessed at.

The reason the digits now earn their place is that a pause created a state the
old rule could not describe. "Paused" with nothing after it does not tell the
user whether they have five minutes left or forty, which is exactly the question
someone deciding whether to resume is asking. The original rule was written for a
screen where the only two states were running and not running, and where the
shape carried everything a running session had to say.

**The shape's own rule did not survive**, and this paragraph used to say it had.
It read: the shape is still deliberately unreadable as a gauge, still derived
from the clock, and still says only that the session is running and how far along
it is. D-014 took the second half of that away. Once the digits were on screen,
the shape and the number were measuring the same thing and the shape was worse at
it, so the shape stopped measuring and now says only whether the clock is
running. See "The shape" above.

The clock-watching objection is real and was weighed. It is answered by what the
readout is attached to: a control. Every state that shows digits shows them above
the one decision that state offers, so the number is being read to make a choice
rather than watched to pass the time.

**Ready comes back too**, as a state. D-013 lists it among the six, and the code
had removed it when Focus left the navigation bar and the only entry left was a
task row, which went straight into the session on purpose. Ready was then reached
from the Focus now card, which was the replacement for the navigation-bar entry
this document originally gave it: the card named a task the app had chosen, and
Ready was where the user agreed with the choice before the clock ran.

**That route is gone, and Ready is unreachable again.** D-048 left the card
speaking only for a session the user started themselves, which is a choice already
made, so the card resumes rather than offering Ready. See "Entry" above: nothing
in the app now calls `openFocus`. The state is still drawn and still tested; what
it lacks is a way in.

This paragraph used to end by saying a row long-press still skipped Ready. D-023
removed the row menu, so nothing skips it any more and every entry lands there.
See Entry above.

Ready comes back without the container transform `expressive-components.md`
describes for it. The sheet's own entrance already does that job, and rebuilding
the transform would be a second shape morph, which `expressive-motion.md`
forbids.

**The play button itself did come back**, which an earlier version of this note
said it had not. It is a plain 56dp round icon button rather than the origin of a
transform, and it is there for the half of the original argument that never
depended on the transform: holding no text, it does not grow with the font scale,
and two worded buttons overflow the row at 200%. See "What it shows".

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

It waits for the shape to arrive before it asks. The dialog is a system window
drawn over everything, so requesting it as the session composed put it on top
of the container transform every single time: the first Start a user ever
pressed was the one run of the animation they were guaranteed not to see.
Waiting costs nothing, because the moment being announced is minutes away.

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

The resolution rule is covered by `TaskListViewModelTest`: nothing is focused
until a task is chosen, choosing one focuses that task whatever day it is
scheduled for, and completing or deleting it ends Focus rather than moving on.
`FocusSession` is covered by `FocusSessionTest`, including overrun, a clock that
has gone backwards, and a missing estimate. Pause and resume belong there too:
resuming shifts the origin, so a session paused for ten minutes and resumed
reports the same elapsed time it held when it paused. So does the extension,
which does *not* move the origin, because moving it underflowed against a
zero-floored elapsed and was silently eaten.

`FocusProgressTest` covered the same three edge cases against the fraction and
was deleted with it.

`TaskListViewModelTest` also covers the announcement: that starting a session
schedules the focused task's estimate, that a task without one schedules
nothing, that pausing cancels, and that resuming reschedules against the time
that is actually left rather than the whole estimate.

`FocusSessionSemanticsTest` covers the six states. Each publishes its task title
as a heading, names its clock control by the action rather than the glyph,
carries its status line as text, and offers a visible way out. Completing ends
the task and returns to Today. **Leaving pauses rather than stops**, which is
D-015 and is the assertion that matters most here, because the failure it guards
against is a silent one. Every state is checked at 100% and 200% font scale,
including a four-line title.

**The shape is testable now, and it was not before.** D-014 made it a function of
one boolean: 4-sided at rest, 12-sided while running. That is an assertion rather
than an observation, which the old design could never manage, because a shape
advancing over forty-five minutes on spring physics is not meaningfully
assertable. What still has to be watched rather than tested is whether the change
reads at a glance, which is D-014's own reversal condition.

**The screen is idle while a session runs**, which is worth stating because it
used to not be. The old unestimated session walked a ring forever, so the screen
never went idle and anything waiting on idleness had to be told not to. Nothing
here animates between state changes any more, so that workaround goes.

Both themes and the session-survives-process-death behaviour are checked by hand.

The notification itself is checked by hand on the emulator against a
one-minute estimate, in both directions: backgrounded, it arrives; left on
screen, it does not.
