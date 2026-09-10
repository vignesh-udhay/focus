# Logbook

## Purpose

The Logbook holds every task that has been completed.

It answers one question:

    What did I finish?

It exists for a second reason, less visible but more important: it is what
makes completing a task safe. Every other list drops a task the moment it is
done. Without somewhere for those tasks to land, ticking a checkbox outside
Today put a task permanently beyond reach once the undo snackbar lapsed.

Completion is not deletion. The Logbook is where that is true.

---

# Semantics

A task appears in the Logbook when it is completed and not deleted:

    !isDeleted && isCompleted

Nothing else is consulted. Scheduling is ignored entirely, so a completed
task is reachable here whatever its other fields say. That is the whole point:
there is no date, and no absence of one, that can hide a finished task.

`completedAt` remains the single source of truth for completion, as it is
everywhere else. The Logbook adds no field, no flag, and no second collection.

Deleted tasks stay out, as in every other list. Deletion has its own soft-delete
and its own undo, and a task that was completed and then deleted is deleted.

---

# Ordering

Newest completion first, by `completedAt` descending.

Tasks completed at the same instant keep the order the query was given.

This is the only list ordered by `completedAt`, and the only one that ignores
`scheduledDate` entirely. A task created long ago but finished this morning
belongs at the top; when it was captured is not what this list is about.

The ordering lives in `TaskQueries.completedTasks`, not in the screen.

---

# Grouping

Completed tasks sit under a heading naming the day they were finished, newest
day first. Inside a day the order is `completedAt` descending, as above.

D-016 is the entry. The argument is that the ordering was otherwise invisible: a
list sorted by completion time with nothing marking the days gives a reader no
way to tell whether the twelfth row is from yesterday or from March. The
headings add no data, because `completedAt` is already the sort key, and no
field, query or state.

A heading says when, and only when. There is no count beside it. That is the
line D-016 draws, and it is the same line the Out of scope list draws further
down.

---

# Relationship to the other lists

The Logbook and Today deliberately overlap. Today keeps completed tasks in its
bottom band, as `today-screen.md` specifies, and the Logbook keeps every
completed task. A task completed today and scheduled for today appears in both.

The lists answer different questions, and neither hides a task from the
other.

Inbox, Today and Upcoming all exclude completed tasks, except for Today's
bottom band. Those are lists of what could be picked up. The Logbook is their
counterpart, and it is what makes dropping a completed task safe: without it,
finishing something would put it beyond reach once the undo snackbar lapsed.

---

# Interaction

| Interaction | Result |
| --- | --- |
| Tap checkbox | Reopens the task |
| Tap row body | Opens task details |
| Long press | Nothing, since D-023 |
| Swipe | Nothing |

## Reopening

Unchecking a row clears `completedAt` through the ordinary `toggleComplete`
path. The task leaves the Logbook and returns to whichever active lists it
belongs to, decided by the same queries as always.

No undo is offered, because reopening is itself the reversal of completing.
That matches the rule everywhere else in the app, and it strands nothing: a
reopened task is by definition outstanding, so an active list holds it.

## Editing

Completed tasks stay editable. The row opens the same `TaskDetailsSheet`, and
saving goes through the same `editTask`, which copies onto the stored task and
therefore cannot touch `completedAt`.

Editing never removes a task from the Logbook. Rescheduling a completed task
changes where it will appear once reopened, and nothing about where it sits
here.

## Deleting

Deletion behaves exactly as on every other list: a soft delete with an undo
snackbar. The row keeps its `completedAt`, so undoing a deletion returns it to
the Logbook still completed.

It is reached from Task Details rather than from the row. D-023 removed the row's
actions menu, so tapping through is the route from every list including this
one.

---

# Structure

A `Scaffold` with a compact top app bar, the shared snackbar host, and the same
segmented collection every list uses, through `TaskListRow`.

Rows take `Status=Completed`: the title dims to `onSurfaceVariant`, the checkbox
is checked, and the duration stays on the right. The supporting line is hidden
here. The only thing it would carry is the date, and the day heading above the
row has already said it.

There is no add-task button. Nothing is captured already finished.

**The screen is a room, not a place.** It draws a back arrow and no navigation
bar, and it is reached from the app-bar overflow on Today, Inbox and Upcoming.
`navigation.md` holds the rule: a screen wears either the bar and an overflow, or
a back arrow and no bar, never both.

An earlier version of this section said the Logbook "carries the same navigation
bar every other list does" and is "reached from More". Both halves are stale.
More was a bar item standing in for a screen `PRODUCT.md` never defined, and it
is gone; what sat behind it moved to the overflow. The bar went with it, because
a bar showing three destinations with none of them current tells the user they
are nowhere.

---

# Empty state

Uses the shared `TaskListEmptyState`:

    Nothing completed yet

    Tasks you finish are kept here.

The supporting line explains what the screen is for, because a user arriving at
an empty Logbook has no other way to learn what lands here.

It does not congratulate, and it does not treat an empty Logbook as a problem or
an achievement. `PRODUCT.md` is explicit that the reward is getting the work
done, not a score for having done it.

---

# Loading and read failure

Two more states, drawn on the board in chapter 09 beside the Logbook itself.

**First load** shows nothing, and the chrome. D-065 replaced the loading
indicator this line used to specify, on the grounds that it was never built and
that building it would give one of four lists a spinner for a read all four
share. The back arrow and the bar are drawn before the list arrives, so a slow
read is never a trap; the empty state is what waits, because "Nothing completed
yet" is a claim about the user's work and the app has not looked yet.

**A read that failed** shows the error-toned empty state and a Try again button.
The wording matters more here than on other screens, because this is the list
that exists to make completing a task safe:

    Couldn't load your Logbook

    The record is safe. This is a read that failed.

The supporting line says what did not happen. A user who cannot see their
finished work has a reasonable fear that it is gone, and the screen answers it
directly rather than leaving them to infer it from a retry button.

---

# Out of scope

Not part of this screen:

- any limit on how far back the Logbook reaches
- purging, archiving, or a retention policy
- counts, streaks, statistics, or any summary of throughput
- a separate view for deleted tasks

**Grouping by day was on this list, and D-016 took it off.** Day headings stay.
Week and month grouping do not, and neither does a count beside a heading, which
is the step that turns a record into the scoreboard the rest of this list exists
to prevent.

The last two matter most. `PRODUCT.md` rules out streaks, points, productivity
scores, and complex analytics, and a list of finished work is exactly where
those would try to creep in. The Logbook is a record, not a scoreboard.

---

# Open product decisions

**The Logbook is not in `PRODUCT.md`.** It appears in none of the three
structural lists: Information Architecture, V1 Features, or the contents of
More. It was commissioned directly rather than derived from the product
document, and those lists have not been updated to include it.

**Nothing purges completed tasks.** The Logbook grows without bound, and there
is no retention policy, no archive, and no way to clear it. That is acceptable
at present data sizes and will not stay acceptable forever.

**Deleted tasks still have no surface.** `restore()` exists on the DAO and the
repository and is reachable only through the deletion undo snackbar. The Logbook
deliberately does not show deleted tasks, so a task deleted after the snackbar
lapses remains unreachable. This is the same class of problem the Logbook solves
for completion, and it is still open for deletion.

---

# Implementation status

Implemented. `LogbookScreen` reads `TaskListViewModel.completedTasks`, derived
from `TaskQueries.completedTasks` over the shared repository stream, and is a
destination in the navigation graph, reached from the app-bar overflow.

**Not yet built:** the day grouping D-016 settles, and the loading and
read-failure states above. The screen currently renders one flat list. Grouping
is a presentation change over the existing query, since `completedAt` is already
the sort key, so it needs no schema or repository work.
