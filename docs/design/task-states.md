# Task states

Where a task can be found, for every combination of the three things that
decide it. This document exists to keep one promise checkable:

> Every non-deleted task is reachable from at least one intentional surface,
> and completing a task never makes it permanently unreachable.

Filtering and ordering live in `TaskQueries`. This is a reading of those
queries, not a second definition of them.

---

# The dimensions

- scheduled date: past, today, future, none
- completed: yes, no
- deleted: yes, no

Four by two by two is 16 combinations. Eight are deleted and intentionally
unreachable. The other eight are below.

**There used to be a fourth dimension.** Placement, one of Inbox, Anytime or
Someday, which tripled the table to 48. It was removed at schema version 9,
and the table did not merely shrink: the question "where is this task" stopped
having two answers that could disagree. `docs/decisions.md` D-002 has the
reasoning.

---

# Outstanding tasks

| scheduled | reachable from |
| --- | --- |
| none | Inbox |
| past | Today, overdue band |
| today | Today |
| future | Upcoming |

**The day decides, and nothing else does.** A task with no day is in Inbox; a
task with one is in Today or Upcoming. There is no second axis for the two to
argue over, so every outstanding task is in exactly one list by construction
rather than by the queries happening to agree.

That is what the placement removal bought. Under the old model Inbox required
both `placement == INBOX` and no scheduled date, which left an undated Anytime
or Someday task in no list at all: present in the database, reachable from
nowhere, and invisible because the Anytime and Someday screens existed to
cover for it. Removing those screens without first widening Inbox would have
stranded real work. See `ROADMAP.md` for the order that avoided it.

---

# Completed tasks

All four completed combinations are reachable from the Logbook, whatever the
date. The Logbook filters on completion alone.

Today additionally keeps a completed task in its bottom band when it was
scheduled for today or earlier, so finishing something does not make it vanish
from under the user mid-session.

Reopening a recurring occurrence takes back the copy that finishing it
produced, whether that is done through the undo offer or by unticking the box
later. The two used to disagree: the offer removed the copy and the checkbox
did not, so a daily task ticked and unticked a moment later left the user
holding two, and the checkbox is the path that lasts. `spawnedFromId` records
which occurrence a copy came from so the later path can find it.

Only while the copy is untouched. A spawn that has been completed has its own
record and its own successor, and a deleted one has already been dealt with;
either way it has stopped being a row nobody asked for, and removing it would
destroy work rather than tidy up.

Focus adds no dimension to this table. Being focused is not stored on a task:
it is one id held in memory for as long as the sheet is open, and the task it
names is reachable from whichever list its date puts it in. Focus shows only
the task the user picked, so it can never be the only place something is
found. See `focus.md`.

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
added in schema version 2, adds none: nothing filters, orders, or groups by
it, so it cannot strand a task anywhere.

The invariant is easy to break quietly. A query that excludes one more thing
is a one-line change, and the state it strands has no test that fails. That is
not hypothetical: `inboxTasks` carried exactly such a clause for months, and
what it stranded was invisible only because two screens existed to hide it.
