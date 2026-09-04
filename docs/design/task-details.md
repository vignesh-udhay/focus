# Task Details

> **Superseded in part.** This document still describes the pre-Phase-3
> information architecture, which included Anytime, Someday, or the Focus
> queue. Those were removed on evidence: see `docs/decisions.md`, D-002 and
> D-004. Where this document and `PRODUCT.md` disagree, `PRODUCT.md` is
> right. This banner comes off in Phase 3, when the document is rewritten.

The sheet that edits one task. Reached by tapping a row on any list.

---

# Design system

Sheet, field, validation and motion behaviour are specified in
`expressive-components.md`. This document covers what this particular sheet
edits and why.

---

# What it edits

Six fields, and only these six:

- title
- notes
- placement
- scheduled date, typed or picked
- due date, typed or picked
- estimated duration

Completion and deletion are deliberately absent. They have their own
interactions, and editing a task must not quietly finish or remove it.

`TaskListViewModel.editTask` names the same six and copies them onto the stored
task, so `id`, `createdAt`, `completedAt`, and `deletedAt` are carried through
untouched. A sheet left open against stale values cannot write them back.

---

# Dates

Each date field is a text field. An existing date is written into it, and an
empty field means the task has no such date.

It is typed or picked, and both write the same field: typing is the quick path
and the calendar behind the trailing icon is the certain one, so neither is a
mode the user has to be in. What the text was understood to mean is spelled out
underneath, with the weekday, because "next friday" is only useful if the day it
landed on can be checked before saving.

Text the parser cannot read is an error that blocks Save, exactly as an unusable
duration is, rather than being quietly dropped. `date-parsing.md` describes what
is understood and why.

The text written into the field is a fixed English form, because it has to be
text the parser reads back; the confirmation underneath is localised.

---

# The draft

The sheet holds a draft. Nothing is written until Save, so dismissing leaves
the task exactly as it was. The draft is keyed on the task id, so opening a
different task starts a different draft.

Save is disabled while the title is blank, the duration is not a positive
number, or either date field holds text that is not a date. The title is the one thing a task cannot do without, and the duration
error is shown in the field rather than on Save, so the reason is where the
problem is.

---

# Notes

Free text, several lines tall, for anything that needs saying about a task
beyond its title. `PRODUCT.md` lists notes among the things a task may have.

Nothing reads it. Notes do not appear on a task row, are not part of Quick Add,
and no query filters, orders, or groups by them. Capture stays one field, and a
list stays scannable.

Null and blank both mean no notes. The draft holds the blank, because a text
field has to hold something, and the view model maps a blank or whitespace-only
draft back to null on save, so "no notes" has one representation in storage
rather than two that look identical on screen.

An existing note survives an edit to any other field. `editTask` takes notes as
a required parameter with no default: a default would let a caller editing some
other field erase a note it never asked about, with the compiler saying
nothing. Required, every caller has to decide, and the sheet passes the note it
is already holding.

Notes is a single write with the rest of the fields rather than an operation of
its own, because two writes launched from one Save would each read the stored
task before the other had written, and one of the two edits would be lost.

---

# Scrolling

The content column scrolls. Six fields, one of them several lines tall and two
carrying supporting text, do not fit at large font scales, and without scrolling the Save button sits off the
bottom of the sheet with no way to reach it. The sheet's own nested scrolling
still takes over at the top, so dragging it closed keeps working.

---

# Known gaps

The placement segmented button row overflows at very large font scales: the
Someday label spills outside its segment. `SingleChoiceSegmentedButtonRow`
divides the width evenly and does not wrap. This predates notes and is
unrelated to the height of the sheet.

A date field is one line, so at large font scales a long value is scrolled
within it rather than wrapped. The resolved date underneath is not truncated,
so the whole day is always readable.

---

# Out of scope

Not part of this sheet:

- completion and deletion
- subtasks, projects, areas, recurrence, and reminders
- rich text or formatting in notes
- a full-screen task detail view, as opposed to this sheet

---

# Verification

`TaskListViewModelTest` covers the editing rules, including that an edit to any
other field keeps an existing note, that clearing a note stores null rather
than a blank, and that a note survives being read back from storage.
`DateParserTest` covers everything the date fields accept and refuse.

`TaskDetailsSemanticsTest` covers the sheet: every field is labelled, the three
identical "Clear" buttons are told apart by description, the placement control
publishes which option is current, a blank title and an unreadable date each
refuse Save visibly, and Save is reachable by scrolling at 200% font scale,
which is what the sheet's `verticalScroll` exists for.

Both themes are still verified by hand on the emulator.
