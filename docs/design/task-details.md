# Task Details

The screen that edits one task. Reached by tapping a row on any list.

**A page, not a sheet.** Every picker on this screen is a bottom sheet, so a
sheet container would mean sheets over a sheet. The superseded code refused that
and worked around it with page navigation inside the one sheet plus a carve-out
making the reminder a dialog, three mechanisms doing one job. Its own comment
gave the other half away: "The page is also nearly the height of the screen, so
a stacked sheet would cover the one beneath it anyway and the context it was
meant to preserve would not be visible." A full-height sheet is a page wearing a
drag handle. As a page it is the third room, alongside the Logbook and Reminder
health, and needs no new concept.

**D-018 rewrote this document.** It was a bottom sheet holding a draft, with
typed date fields and a Save button. It is now a full screen whose controls
commit as they are chosen. What follows describes the current design; the entry
carries the argument and what it cost.

---

# Design system

Row, sheet, button and motion behaviour are specified in
`expressive-components.md`. This document covers what this particular screen
edits and why.

---

# What it edits

Six fields, and only these six:

- title
- notes
- scheduled date
- due date
- reminder
- estimated duration

Plus repeat, which is drawn and deferred. See D-019.

`TaskListViewModel.editTask` names the same fields and copies them onto the
stored task, so `id`, `createdAt`, `completedAt` and `deletedAt` are carried
through untouched.

An earlier version of this list said recurrence and reminders were out of scope
while also naming reminder among the six fields it edits. Both halves cannot be
true. `PRODUCT.md` names recurrence and reminder among the properties a task may
have, and the view model holds both, so the out-of-scope line was the wrong one.

---

# Three regions

The screen reads as three, top to bottom.

**Identity.** A checkbox, the title, the notes. The title is the screen's
heading and its primary input at once, at Headline Small on `onSurface`. The
notes sit under it at Body Large on `onSurfaceVariant`.

Both are editable in place, borderless, committing on blur.

**There is no card around them, and that is the fix rather than the omission.**
A tinted container read as a summary, which is exactly why the two most-edited
fields on the screen looked read-only. A Material text field brings its own
container, so putting real fields inside a card nests one container in another.
Every comparable app puts the title and notes as plain editable text at the top
and the properties in rows beneath, and they agree because the title is the
heading and the input at the same time.

The duration used to appear here as a chip as well as in the Plan rows. It is
one field and it is shown once.

**Plan.** A section label, then five grouped rows: Scheduled, Due date,
Reminder, Duration, Repeat. Each shows its current value and opens a sheet.

**Action.** One full-width `Start focus`. It is the screen's only accent, and it
is a third way into Focus alongside the Focus now card and a Today row's
long-press. `focus.md` carries the entry rules.

---

# The app bar has no title

A back arrow at the start and an overflow at the end, holding one item.

The screen used to be titled "Task", centred, directly above the task's own
title. Two centred headings stacked, the upper one naming the app rather than
the work. The screen name is published as `paneTitle`, which a screen reader
announces and nothing draws. This is the same correction the Focus header took,
for the same reason.

---

# Setting a date

Both date rows open the same shape of sheet: the clear option first, then three
day presets in a 2x2 grid, then a full-width Choose a date that opens the
Material date picker.

    Scheduled     No date      Today      Tomorrow   This weekend
    Due date      No due date  Today      Tomorrow   End of week

**Scheduled had no clear option**, which meant a task could not be returned to
the Inbox from this screen. A task with no scheduled date is an Inbox task, so
the absence was a dead end rather than a missing convenience.

Two presets are not in the parser's vocabulary and are not self-evident, so
D-018 fixes their meaning: **This weekend** is the coming Saturday and **End of
week** is the coming Friday.

## Presets may offer what typing may not

`date-parsing.md` excludes `next week` because English does not agree on which
week it means. That is an argument about ambiguous text, not about the concept,
and a button does not inherit it: a button carries one defined meaning.

So the two ways of setting a date are allowed different vocabularies, and the
reason is that a button resolves the ambiguity that made the phrase unparseable.
`Next week` was on the board and was dropped anyway, because its cell was needed
for the clear option.

---

# Notes

Free text, several lines, for anything that needs saying about a task beyond its
title. `PRODUCT.md` lists notes among the things a task may have.

Nothing reads it. Notes do not appear on a task row, are not part of Quick Add,
and no query filters, orders or groups by them. Capture stays one field and a
list stays scannable.

Null and blank both mean no notes. The field holds the blank, because a text
field has to hold something, and the view model maps a blank or whitespace-only
value back to null, so "no notes" has one representation in storage rather than
two that look identical on screen.

An existing note survives an edit to any other field. `editTask` takes notes as
a required parameter with no default: a default would let a caller editing some
other field erase a note it never asked about, with the compiler saying nothing.
Required, every caller has to decide.

---

# Committing

Every control writes when it is chosen. There is no Save.

**This replaced a draft, and the draft was worth something.** Nothing was
written until Save, so dismissing left the task exactly as it was. Immediate
commit has no equivalent, and D-018 records that as the price rather than
pretending it is free. What stands in for it is that each write is one field,
made by one deliberate choice, and reversible by reopening the same row.

Completion is the exception and is not a field. The checkbox goes through the
ordinary `toggleComplete` and raises the same single undo offer every list
raises.

An earlier version of this document said completion was "deliberately absent"
from this screen. The board has carried a checkbox in the hero throughout, and
completing a task you are looking at is not a trap, so the absence was the
thing that was wrong.

---

# Unset values are not styled differently

`None` and `Doesn't repeat` render exactly like `Today` and `45m`.

The question comes up every time someone reads this screen, so the answer
belongs here. Those are values the user chose or accepted, not gaps. Dimming
them would make the screen read as a form with blanks to fill, which principle 3
contradicts by saying a task needs only a title, and which principle 4 calls
task administration rather than execution.

There is also no token for it. The next step down from `onSurfaceVariant` is
`outline`, which on the row's `surfaceContainerLow` is about 3.8:1 and fails AA
for 14sp text. Unset values would become the hardest thing on the screen to
read.

---

# Scrolling

The content column scrolls. Six fields at large font scales do not fit, and
without scrolling `Start focus` sits off the bottom with no way to reach it.

---

# Deleting

The overflow holds Delete and nothing else. D-022 is the entry.

**A menu rather than an icon button.** An unlabelled trash icon is the least
legible form of the most destructive action, and an icon in the bar would sit
one tap from Back, in the corner a thumb reaches for when leaving.
`expressive-components.md` decided that principle for the row menu already:
constructive before destructive, so the thumb does not land on Delete. The bar's
trailing slot is also the overflow on the three primary screens, and a different
control there would make one slot mean two things.

**A menu rather than a button beside Start focus**, for the same thumb reason,
and because it would weight a rare one-way action like the screen's primary.

Three actions, three weights. Start focus is full-width because it is the
payoff. Completion is a checkbox because it is reversible state. Delete is a
menu item because it is rare and terminal.

**No confirmation.** Deletion is a soft delete raising the same single undo
offer every list raises, and this screen already hosts the snackbar. It also
does not navigate: the task leaving `allTasks` is what pops the screen, so
deletion has one exit rather than two that could disagree.

---

# Known gaps

Repeat is designed and not built. See D-019.

---

# Out of scope

Not part of this screen:

- subtasks, projects and areas
- rich text or formatting in notes
- deletion as anything other than an overflow item
- a typed date field, which is Quick Add's job

---

# Implementation status

**Built.** `TaskDetailsSheet.kt` and its host are gone, and `TaskDetailsScreen.kt`
replaces them: a route, three regions, five picker rows, and no Save.

The screen reads `TaskListViewModel.allTasks` rather than the list it was opened
from. It is reached by id from any list, so reading one list would mean a task
rescheduled off Today while its details were open stopped being found and the
screen closed on an edit the user had just made.

Leaving on a missing task is guarded on the repository having answered at all.
Every exposed flow begins on a placeholder, and reading that placeholder as
"gone" would pop the screen on the way in, which is the trap `focus.md` records
Focus falling into.

**Repeat stays as it is**: the row picks one of `Recurrence`'s four periods or
none, and the board's editor with an interval, a weekday set and an end
condition is Phase 4 per D-019.
