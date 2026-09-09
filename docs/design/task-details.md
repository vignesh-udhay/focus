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

Plus repeat, which is a rule rather than a value: a period, an interval, a
weekday set and an end condition. See D-027.

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

**The checkbox centres on the title's first line, not on the title's box.**
Top-aligning the two lines up their containers rather than the things inside
them: the glyph sits at the middle of its 48dp target, while the title's first
line sits below the text field's own vertical inset. That put the mark 8dp off
from the line it refers to.

The offset is measured from the line height rather than set to a constant,
because which of the two sits lower changes with the font scale. A fixed 8dp is
right at 100% and wrong above it.

**The notes indent to the title, not to the screen edge.** They belong to the
task the title names, and starting them further left made the two read as
separate blocks with the checkbox pointing at neither.

The gap after the checkbox is 16dp because that is the text field's own start
inset, which this `TextField` overload gives no way to override. The board
states that number rather than a tidier one nobody can produce.

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

**Actions.** A floating toolbar, pinned bottom centre, holding Delete and Start
focus. D-037. Start focus is the toolbar's attached FAB and one of two ways into
Focus alongside the Focus now card, the row long-press having gone with D-023.
`focus.md` carries the entry rules.

This replaces a full-width `Start focus` at the foot of the scroll and an
app-bar overflow whose only item was Delete. A menu of one promises options it
does not have, and the two actions belong together: both act on the one task the
screen is about, which is what M3 means by a floating toolbar's contextual
actions.

The full-width button carried a play glyph because without it the button wore
the treatment every commit in this app wears, on a screen D-018 left with
nothing to commit, and it was pressed by people meaning to close the page. That
trap is gone with the pill: a small play icon in a floating bar is not open to
being read as Done. What replaces it is lower discoverability, which D-037
records as the trade.

---

# The app bar has no title

A back arrow at the start and nothing at the end. The overflow that held Delete
is gone with D-037, which moved it to the floating toolbar; the trailing slot now
matches Today, Inbox and Upcoming by being empty here rather than by holding a
different control.

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

## The sheet always says what is set

Exactly one control in a date sheet is ever lit. Nothing set lights the clear
preset; a preset date lights that preset; **any other date lights Choose a
date**, which then reads the date instead of its own name and takes the filled
treatment a chosen preset takes.

Without that last case the sheet could not show a custom date at all. A task
scheduled three weeks out lit no preset and left the button reading "Choose a
date", so the sheet looked identical to one with nothing set, on a screen whose
whole job is showing what a task holds. The date is worded by
`scheduledDateLabel`, the helper the Plan row behind the sheet already reads, so
the two cannot describe one date differently.

This is the same defect D-026 fixed in the Duration sheet, and it was still
standing here. Fixing one sheet of a matched pair without checking the other is
how two sheets built from one design become two designs.

**The board does not draw this state.** Its frames hold a task scheduled for
Today, so the button is in its unlit form. Same undrawn case as the Duration
sheet's Custom row carrying a value.

## Presets may offer what typing may not

`date-parsing.md` excludes `next week` because English does not agree on which
week it means. That is an argument about ambiguous text, not about the concept,
and a button does not inherit it: a button carries one defined meaning.

So the two ways of setting a date are allowed different vocabularies, and the
reason is that a button resolves the ambiguity that made the phrase unparseable.
`Next week` was on the board and was dropped anyway, because its cell was needed
for the clear option.

---

# Setting a duration

Five choices as one connected group, `None` and the four presets, then a Custom
row beneath them. D-026 is the entry.

**The group is `ButtonGroup`, which is the board's component and the one that
does not truncate.** Five hand-rolled buttons in a row left about 54dp for a
label, and `None` at 200% font scale wants roughly 66dp. What does not fit moves
into a menu behind an indicator instead of being clipped.

**`None` is a preset, not a separate control.** The board offers no way to clear
a duration, the same gap the Scheduled sheet had. "No estimate" is a real value:
`focus.md` gives an unestimated task its own open-ended session.

**Custom is a second state of the same sheet**, reached by the row and left by a
back arrow, with two fields and Done. Not a dialog over the sheet, which would
be two windows and two scrims for one question.

**The Custom row carries the estimate when no preset holds it.** A task set to
1h 20m lights nothing in the group, and without this the sheet showed five
unselected buttons and no sign of the value the task actually had. It stays
blank when a preset does hold the value, because `Custom  45m` under a lit `45m`
would say the number was typed rather than pressed.

---

# Setting a reminder

Two rows and a Save, per D-030:

    Reminder
    Day                                          Tomorrow  >
    Time                                         9:00 AM   >
    [ Save reminder ]

**A sheet, and that closes the carve-out this document opens with.** The
superseded design was criticised at the top of this page for "making the
reminder a dialog", a third mechanism doing the job of the other two. It stayed
a dialog after D-018 because it held one value. It holds two now, so it is a
sheet like everything else the Plan rows open, and the exception is gone.

**Why not day presets inside the time dialog.** That was built first. The day
took the top third of a window that exists to pick a time, the calendar button
was the heaviest control on it despite being the least-used option, and asking
for the full width made the dialog span the screen and lose its own inset.
Reusing the date sheet's grid was defended as consistency. The components were
consistent and the density was not.

**A Save, on `RepeatSheet`'s terms.** D-018 commits as you go because every
other row is one field set by one choice. A day and a time only mean something
together, and writing each as it is tapped would push the task through saved
states nobody asked for, each rescheduling an alarm.

**No summary line above the rows.** Two rows reading Tomorrow and 9:00 AM say
the whole thing. Repeat has one because four fields compose into something its
controls do not show separately; two do not, and a line reading "Tomorrow, 9:00
AM" over them is the restatement D-026 refused for Duration's hero readout.

## The day follows the time until you choose one

Until the Day row is opened, the day is whichever one puts that time of day next
in the future. Pick nine in the morning at six in the evening and the row reads
Tomorrow. The correction is shown, in the row it is about, and undone by opening
the row and tapping Today.

Once a day has been chosen it is honoured exactly, and a moment already gone
disables Save under a line saying so. Choosing a past moment deliberately is a
request the app cannot keep, and a reminder is a promise to interrupt: storing
one the app has already judged unkeepable is worse than refusing it.

The day pane offers **Today and Tomorrow** rather than the date sheets' four
cells. The far preset that suits a scheduled date does not follow here, because
"this weekend at 3pm" is a vague thing to ask of an interruption in a way
"tomorrow at 3pm" is not. Clearing lives on the pane behind, which frees both
cells for days.

---

# Repeat says its rule in words

The Repeat sheet states the whole rule as a sentence, in the sheet header's
supporting slot, directly under the title:

    Repeat
    Every week on Mon, Wed and Fri

**Because a rule assembled from three controls cannot be read back from them.**
The sheet offers an interval, a weekday set and an end condition, each legible
on its own. Nothing on it said what the three amounted to, so understanding your
own rule meant holding "1 week", "M W F" and "Never" in your head and composing
them. The sentence is the only place the rule exists as one thing.

**The same sentence is the Plan row's value.** The row read "Mon, Wed, Fri",
which names the days and drops the interval and the end, so a weekly rule and a
fortnightly one looked identical from the screen that is meant to show what a
task holds. One string serves both, which is also why this costs little: the row
needs it regardless of whether the sheet shows it.

Grammar, in order: `Every {interval}` then optionally `on {days}` then
optionally the end clause. `Every day`. `Every 2 weeks on Tue, 10 times`.
`Every week on Mon, Wed and Fri, until Aug 17`. No end condition adds no clause.

**Not "Repeats every…"**, because the sheet is already titled Repeat and the row
is already labelled Repeat. The verb would echo the label immediately above it.

**Only on the main state.** The Every and Ends substates are titled for the part
they edit and carry a back arrow; a whole-rule summary there would describe
something other than the screen it sits on.

**Build it from complete parameterised strings, not by gluing fragments.**
Word order is not English's everywhere, and a sentence concatenated from
"Every 2 weeks" + " on " + "Mon" + ", " + "10 times" cannot be translated.
`task_repeat_interval_on_days`, `%1$s on %2$s`, is the shape to follow, with the
day list built by a list formatter rather than by joining on commas.

**One case is undrawn.** No frame shows an `after N times` rule on the main
state, so `Every 2 weeks on Tue, 10 times` exists in this document and nowhere on
the board.

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

Delete is the leading action in the floating toolbar. D-037 is the entry;
D-022 is the one it supersedes.

**Why not the overflow any more.** It held Delete and nothing else, and three
dots promising options that turn out to be one option is a control lying about
itself. D-022 argued each alternative down and never argued for the menu.

**What D-022 settled and still holds.** Delete does not sit one tap from Back in
the corner a thumb reaches for when leaving, and it does not carry the weight of
the screen's payoff. In the toolbar it leads and Start focus trails as the
attached FAB, so the thumb's natural landing is the constructive action. That is
the row menu's rule, "constructive before destructive", read for a horizontal
bar instead of a vertical list.

**What it gives up.** The word. "An unlabelled trash icon is the least legible
form of the most destructive action" was D-022's first objection and it is still
true; the icon keeps `error` so colour is the second cue, and the word survives
as the content description. The delete is soft and undoable, which is what makes
that affordable.

Three actions, three weights. Start focus is a FAB because it is the payoff.
Completion is a checkbox because it is reversible state. Delete is a plain icon
button because it is rare and terminal.

**No confirmation.** Deletion is a soft delete raising the same single undo
offer every list raises, and this screen already hosts the snackbar. It also
does not navigate directly: once the task has been shown, its leaving
`allTasks` is what pops the screen, even when it was the final live task and
the resulting list is empty. Deletion therefore has one exit rather than two
that could disagree.

---

# Known gaps

None on this screen. Repeat was the last one, and D-027 closed it.

---

# Out of scope

Not part of this screen:

- subtasks, projects and areas
- rich text or formatting in notes
- a confirmation dialog before deleting
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

**Repeat is the editor now.** D-027 ends D-019's deferral, so `RepeatSheet.kt`
is three panes in one `ModalBottomSheet`, on the Duration sheet's shape: the
rule, then Every and Ends behind a back arrow, all named "SAME ModalBottomSheet"
on the board.

It is also the one sheet on this screen with a Save, and that is not D-018 being
quietly dropped. Every other row sets one field from one choice, so writing on
the tap is the whole interaction. A rule is four fields that only mean something
together, and writing each tap would make "every 2 weeks on Monday" pass through
"every 1 week" as a real saved state, rescheduling the series on the way.

**The board draws no way to stop a task repeating.** The main pane has Every,
Days, Ends and Save repeat, and none of them clears the rule. The sheet adds a
"Doesn't repeat" action beside Save, shown only while the task repeats, on the
reasoning the date sheets already use: their grid spends its first cell on "No
date", and a control offered when it can do nothing is one the user cannot tell
worked.

**The weekday chips are sized rather than laid out by their labels.** At their
natural width the seven came to 1002px inside a 992px column on a 1080px screen,
so the last day wrapped to a line of its own at the default font scale.
`FocuslistDimensions.WeekdayChipSize` is the board's own 48dp, from a node called
"Weekday selector / 7 equal 48dp targets", and the row spreads them with
`SpaceBetween` so the gaps are the remainder rather than a number chosen here.
The width is fixed and the height is a floor, so a letter at 200% makes its chip
taller rather than being cut.

**The label is centred by an argument, not by the component.** Material's
`ChipArrangement` places the first child at x = 0 and spends every spare pixel on
the right, because a chip is normally as wide as its content and has no slack to
place. Fixing the width creates slack, and the letters sat left of the circles
drawn around them: measured at 5px for M and W and 10px for T, F and S, which is
the signature of the fault rather than a coincidence, since the offset is half
what is left over. `horizontalArrangement = Arrangement.Center` brings all seven
inside half a pixel.

**The last selected day does not come off**, which the board asks for in the same
frame's name. Nothing happens when it is tapped: there is no error to report,
only a state the rule cannot be in, and a disabled chip would say the day was
unavailable when it is the one that is chosen. `Recurrence` still reads an empty
set as the anchor's weekday, because every weekly rule written before D-027 has
one.

**They are `FilterChip`s, which is the classic Material component and not the
expressive one.** `Chip.kt` carries no reference to Expressive at all. The
expressive control for picking several from a short set is the connected button
group, `ButtonGroup` with `toggleableItem`, and it is not what the board draws:
its weekday node holds seven separate 48dp instances, while the sheet, the app
bar and Save repeat in the same frame are all named as `M3` instances. A
connected group would also put a day behind an overflow menu when it ran out of
room, which is worse here than wrapping.

**The sheet carries a summary line under its title, and it is the only place the
whole rule is stated.** "Every week on Mon, Wed and Fri, until Aug 17", read off
the draft rather than the task, because this is the one sheet on the screen that
does not commit as you go: the line previews what Save will write.

It is not a restatement of the controls, which is the objection D-026 upheld
against Duration's hero readout. That was one number at display size, said again
one gesture after it was set. This composes four values the three rows only show
separately, and the composition is where the ambiguity is: "Every 2 weeks" with
three days lit does not say, from the controls alone, whether it means every
other Monday or Mon, Wed and Fri in alternating weeks.

Shown on every rule, including the simple ones it adds nothing to. A summary that
appears only when things get complicated is one nobody reads at the moment it
appears, which is the moment it matters. `bodyMedium` on `onSurfaceVariant`,
matching the Due date sheet's supporting line, which is the same register in the
same position; the footnote above Save is `bodySmall`, so the line about this
rule outranks the line that says the same thing on every rule forever.

**The Ends value is not in the Plan row.** 471:6537 writes the subtitle with the
horizon and 471:6668 writes the applied row without one. The row answers how
often; when it stops is what opening the sheet is for.

**Sets with names of their own do not get listed.** Every day selected at an
interval of one reads "Daily", which is what it is. The weekend and the working
week read "Weekends" and "Weekdays", and which days those hold is ICU's answer
rather than Monday to Friday. Naming describes and never rewrites: the stored
rule keeps its seven chips, so reopening the sheet shows what was actually
chosen. D-027 carries the reasoning.

**The row cannot be starved by its own value.** `PlanRow` used `ListItem`'s
trailing slot, which is measured before the headline, so a long enough value took
the whole row and the Repeat label was not displayed at all at 200%. It is one
`Row` now with the label measured first. That was a defect in every row of the
group, not only this one.

**And the row stays compact, which is a deliberate divergence.** The board draws
it reading "Every week on Mon, Wed and Fri"; the code writes "Mon, Wed and Fri".
The value sits beside a label and a chevron, next to siblings reading "Today",
"None" and "45m", and the long phrase makes that row taller than the four above
it in a longer language or at 200%. It also puts a verb back that `PlanRow`'s own
rule keeps out. `RecurrenceStyle` names the three amounts: Compact for the rows,
Sentence for the notification, Full for the subtitle.

**The Duration sheet and the board agree.** Board frames 2, 4, 7 and 8 were
brought to the code under D-026: the hero readout removed, the fifth segment
added, Done made full width, and the current value marked in both date sheets.
The one thing no frame shows is the Custom row carrying a value, because the
frames' task holds 45m and the row stays blank when a preset holds the
estimate.
