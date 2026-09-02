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

Nothing else is consulted. Placement and scheduling are ignored entirely, so a
completed task is reachable here whatever its other fields say. That is the
whole point: there is no combination of placement and date that can hide a
finished task.

`completedAt` remains the single source of truth for completion, as it is
everywhere else. The Logbook adds no field, no flag, and no second collection.

Deleted tasks stay out, as in every other list. Deletion has its own soft-delete
and its own undo, and a task that was completed and then deleted is deleted.

---

# Ordering

Newest completion first, by `completedAt` descending.

Tasks completed at the same instant keep the order the query was given.

This is the only list ordered by `completedAt`, and the only one that ignores
both `scheduledDate` and `placement`. A task created long ago but finished this
morning belongs at the top; when it was captured is not what this list is about.

The ordering lives in `TaskQueries.completedTasks`, not in the screen.

---

# Relationship to the other lists

The Logbook and Today deliberately overlap. Today keeps completed tasks in its
bottom band, as `today-screen.md` specifies, and the Logbook keeps every
completed task. A task completed today and scheduled for today appears in both.

This is the same kind of intentional overlap as an Anytime task scheduled for
today appearing in both Anytime and Today. The lists answer different questions,
and neither hides a task from the other.

Inbox, Upcoming, Anytime, and Someday all exclude completed tasks. Those are
lists of what could be picked up. The Logbook is their counterpart.

---

# Interaction

| Interaction | Result |
| --- | --- |
| Tap checkbox | Reopens the task |
| Tap row body | Opens task details |
| Long press | Opens the task actions menu |
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

Long press and Delete behave exactly as on every other list: a soft delete with
an undo snackbar. The row keeps its `completedAt`, so undoing a deletion returns
it to the Logbook still completed.

---

# Structure

A `Scaffold` with a compact `TopAppBar`, the shared snackbar host, and the same
segmented collection every list uses, through `TaskListRow`.

There is no add-task button. Nothing is captured already finished.

The screen carries the same navigation bar every other list does. It is
reached from More, the way `PRODUCT.md` places the secondary lists, but it is
not a dead end: Today and Inbox stay one tap away.

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

# Out of scope

Not part of this screen:

- grouping by day, week, or month
- any limit on how far back the Logbook reaches
- purging, archiving, or a retention policy
- counts, streaks, statistics, or any summary of throughput
- a separate view for deleted tasks

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
destination in the navigation graph, reached from the More menu in the
navigation bar.
