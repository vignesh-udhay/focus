# Task states

Where a task can be found, for every combination of the four things a task
records. This document exists to keep one promise checkable:

> Every non-deleted task is reachable from at least one intentional surface,
> and completing a task never makes it permanently unreachable.

Filtering and ordering live in `TaskQueries`. This is a reading of those
queries, not a second definition of them.

---

# The dimensions

- placement: Inbox, Anytime, Someday
- scheduled date: past, today, future, none
- completed: yes, no
- deleted: yes, no

Three by four by two by two is 48 combinations. Twenty-four are deleted and
intentionally unreachable. The other 24 are below.

---

# Outstanding tasks

| placement | scheduled | reachable from |
| --- | --- | --- |
| Inbox | none | Inbox |
| Inbox | past | Today, overdue band |
| Inbox | today | Today |
| Inbox | future | Upcoming |
| Anytime | none | Anytime |
| Anytime | past | Today, overdue band |
| Anytime | today | Today |
| Anytime | future | Upcoming |
| Someday | none | Someday |
| Someday | past | Today, overdue band |
| Someday | today | Today |
| Someday | future | Upcoming |

One thing is worth reading off this table: **every outstanding task is in
exactly one list.** A day decides it. Without one, placement decides between
Inbox, Anytime and Someday; with one, the day sends it to Today or Upcoming and
placement stops mattering until the day is taken away again.

This is a reversal. Anytime and Someday used to read placement alone, so a
scheduled task appeared in two lists at once, and the table above had "Anytime,
and Today" in it four times. The reasoning was that placement and scheduling
are independent axes and neither should hide the other, which is true of the
data and wrong for the lists: it meant Anytime showed work already planned for
today, and it meant a Someday task could be scheduled for this afternoon, the
list calling something deliberately deferred while the calendar called it due.

Giving a task a day is the decision the three undated lists are waiting for.
Inbox already worked this way; the other two now match it.

---

# Completed tasks

All twelve completed combinations are reachable from the Logbook, whatever the
placement and whatever the date. The Logbook filters on completion alone.

Today additionally keeps a completed task in its bottom band when it was
scheduled for today or earlier, so finishing something does not make it vanish
from under the user mid-session.

Focus adds no dimension to the table above. Being focused is not stored on a
task: the Focus queue is Today's outstanding work, so Focus is a strict subset
of Today and is never the only place a task can be found. It holds no completed
tasks, and the Logbook covers those as it does everywhere else. See `focus.md`.

Every other list drops a completed task. The Logbook is what makes that safe:
without it, completing a task would put it permanently beyond reach once the
undo snackbar lapsed.

---

# Deleted tasks

Deleted tasks are intentionally not surfaced anywhere after the undo window.

Deletion is soft, so the row survives in the database with `deletedAt` set,
and every query excludes it. The undo snackbar is the only way back, and once
it lapses the task is gone as far as the app is concerned.

This is a deliberate gap, not an oversight. A Deleted or Trash view, and any
purge policy for rows that accumulate, are open product decisions. Neither is
implemented and neither should be invented.

---

# Keeping this true

A new list surface should be checked against the outstanding table above, and
a new task field that affects filtering should add a dimension to it. Notes,
added in schema version 2, adds none: nothing filters, orders, or groups by it,
so it cannot strand a task anywhere. The
invariant is easy to break quietly: a query that excludes one more thing is a
one-line change, and the state it strands has no test that fails.
