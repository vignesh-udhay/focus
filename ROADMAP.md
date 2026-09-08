# Roadmap

Five phases to 1.0, sized for one person at 10 to 15 hours a week.

The reasoning behind this ordering is in `docs/decisions.md`. The product
scope it delivers is in `PRODUCT.md`.

---

## Current phase

**Reminder health now has a design document**, which it had gone without while
every other screen had one. 875 lines of code, sixteen board frames, the area
`PRODUCT.md` principle 1 calls the highest severity in the product, and nothing
written down. Most of the reasoning already existed as KDoc and was lifted into
`docs/design/reminder-health.md` rather than invented.

Four board defects went with it. The header was hand-drawn in all fifteen frames
and is now `Room header`, which leaves no room in the app still drawing its own.
The check rows were three separate cards while every other list is a segmented
group, so `Check row` gained Position variants bound to the shared
`Segmented/*` variables. The eyebrows were literal capitals in the string values
and are now sentence case, on the board and in `strings.xml`. And `clone()`
silently dropped the component property references on the six new variants,
which made every check row render its default text until the references were
restored; worth knowing, because nothing warns you.

**That review produced D-021, which is the most consequential thing in this
pass.** `CheckState` had three values and the screen drew two: `Warning` and
`Blocked` shared a mark and an error tint. Since `DeviceRestriction` is inferred
from `Build.MANUFACTURER` and never measured, every OnePlus, OPPO, Realme,
Xiaomi, Redmi, POCO, Samsung, Huawei and Honor user saw a permanent red "Action
needed" from first launch, on a phone where nothing might be wrong. A
reliability screen that is always red teaches people to ignore it, which is the
one thing this screen cannot afford.

The rule now is that **the app colours what it knows.** `Blocked` keeps the
error container because the app was refused and can say so; `Warning` takes the
ordinary row surface and a question mark because it is a guess. The headline
follows: a device whose only problem is inferred reads "Worth checking" on a
neutral card, and the body says what the app cannot know, "Focuslist cannot tell
whether it is on."

**Tertiary was tried first and rejected on the render**, which surfaced a
palette fact worth keeping: `tertiaryContainer` is `#FFD7E3` and `errorContainer`
is `#FFD8D6`, one step apart in green. The Focuslist palette has three usable
container families, lavender, pink and neutral, because primary/secondary and
tertiary/error each collapse into one. That constrains any future state colour,
not just this screen's.

The board carries it, `strings.xml` carries the new label and sentence, and the
code still needs the three-way branch in `Badge()` and `detail()`.

**The check row badges went too.** Each row carried a 40dp circle behind its
glyph, and on a `Blocked` row that circle took the error container while the row
did as well, so it was invisible in the state that matters most. A container
that disappears in one of three states is not carrying anything. The glyph now
sits directly in a 40dp slot and takes the colour itself: `primary` for a tick,
`onSurfaceVariant` for a question mark, `onErrorContainer` for an exclamation,
which reads more strongly than it did behind a badge of its own colour.

**The longer Caution body overlapped the first check row by 8dp** in two frames,
because the headline grows with its copy and the rows sat at a fixed offset. All
eleven frames are now on a measured 12dp gap, and `reminder-health.md` says why
that gap is worth stating.

**D-023 removed the task row's actions menu, and the trailing button with it.**
The board's `Task row` has always been checkbox, title, metadata, duration; the
code had added a trailing button the board never drew. Checking why exposed a
premise that three later decisions had quietly removed.

`expressive-components.md` justified the button by saying Delete and Focus "live
only in the actions menu, Task Details deliberately excludes both". D-018 gave
Task Details a Start focus, D-022 gave it Delete, and its Plan rows give it
rescheduling. Nothing was excluded any more, and the button had outlived its
reason without anyone going back to check.

The menu had also become a worse duplicate: Today, Tomorrow and Pick a date
against the Scheduled sheet's No date, Today, Tomorrow, This weekend and Choose
a date. Three of its five items were rescheduling, which is administration, and
a permanent trailing button gave the most administrative action the most
prominent position on every line of every list.

The cost is a tap. Rescheduling from a list is three rather than two, and rows no
longer answer a long press. Nothing became unreachable. If it proves too slow the
answer is a bottom sheet on long press, which is where Material's compact
guidance points for a five-item menu, not the button returning.

`TaskListRow` went from 266 lines to 108. Four call sites lost their
`onDelete`, `onReschedule` and `onFocus` arguments, four instrumented tests that
asserted the menu were deleted, and two now-dead semantics fixtures went with
them. Seven design documents carried references to the menu and all seven are
updated.

**One simplification fell out.** `focus.md` had three entries and the row
long-press was the one that skipped Ready and started a session directly. With it
gone, every entry lands on Ready, so the state a user meets no longer depends on
how they arrived.

**This also deleted most of the menu pass that prompted it.** The remaining
menus are the app-bar overflow, three destinations of one kind, and Task Details'
overflow, one item. Neither needs the Expressive gap treatment, so the grouping
work is moot. What is left is componentising the More menu on the board, and a
missing row in `expressive-motion.md`, whose What moves table claims to be
complete and does not mention menus at all.

**D-022 gives Task Details a Delete, in the app-bar overflow.** The screen exists
for deciding about one task, and deciding a task is not worth doing is one of the
outcomes; deletion was reachable only from a row's long-press menu, so the screen
entirely about a task was the one place you could not throw it away.

A menu rather than an icon button for three reasons. An unlabelled trash icon is
the least legible form of the most destructive action. An icon in the bar sits
one tap from Back, in the corner a thumb reaches for when leaving, which is the
mistake `expressive-components.md` already refused for the row menu as
"constructive before destructive". And the bar's trailing slot is the overflow on
the three primary screens, so a different control there would make one slot mean
two things.

**The checkbox stays a checkbox**, and that was the other half of the question.
Completion is reversible state, not a one-way action: unticking is how a task is
reopened from the Logbook, and a "Mark done" button would have to become "Mark
not done" on a completed task. Three actions, three weights: Start focus is
full-width because it is the payoff, completion is a checkbox, Delete is a menu
item because it is rare and terminal.

No confirmation dialog, because deletion is a soft delete raising the same undo
offer every list raises and the screen already hosts the snackbar. Deletion also
does not navigate: the task leaving `allTasks` is what pops the screen, so there
is one exit rather than two that could disagree. The board already drew the
overflow, so only the code moved.

**A doc-versus-code divergence turned up while doing it.**
`expressive-components.md` says the row menu's Delete "is labelled in `error`"
and the code had been drawing it in the default colour since it was written. Both
Deletes are error-coloured now.

**Task Details is built, and was reviewed against the design.** It matches:
a full screen on `task-details/{taskId}` with `TaskDetailsSheet.kt` and its host
deleted, no Save, borderless title and notes committing on blur, a bar with a
back arrow and `paneTitle` instead of a drawn title, the overflow omitted, and
Repeat held to `Recurrence`'s four periods per D-019. `DatePresets.kt` improved
on the spec: both presets resolve strictly after today, so neither can silently
do nothing on the day it names. 600 unit tests pass, and the instrumented tests
assert the three regions at 100% and 200%, immediate commit, and both preset
definitions by name.

**Two things were fixed in review, and both were the same mistake.**

`PlanRow` had hand-rolled the segmented treatment with private `16.dp`, `4.dp`
and `2.dp` constants while every other collection in the app reads
`ListItemDefaults.segmentedShapes`, `segmentedColors` and `SegmentedGap` from
Material. It now wraps `SegmentedListItem` like `TaskRow` does, and the three
constants are gone. This is the code arriving at the same drift the board had,
from the other direction: a second copy of three numbers is how one group of
rows ends up disagreeing with itself about its own corners.

`TaskDetailsScreen` had bypassed `FocuslistTopAppBar` for a raw `TopAppBar`,
because the shared component required a non-null title and this screen has none.
The component's own KDoc says it exists because screens that wrote their own bar
drifted apart. `title` is nullable now, and the reason a screen may pass null is
documented on the parameter, so the next title-less screen has a supported route
rather than a precedent for bypassing.

**Reminder health moved onto the shared bar too**, so `FocuslistTopAppBar` is
now the only file in `ui/` that references `TopAppBar` at all. It also gained
the heading semantics the shared bar applies and its own bar did not, which a
screen reader uses to say where the user is.

**Task Details was the work in progress.** D-018 replaced its sheet-and-Save
design with a picker-driven full screen, and the board and the documents are
settled while the code still holds the superseded version. That is the gap being
closed. Phase 4, the Glance widget and real recurrence, comes after.

It is next rather than early: the four items below shipped, so the reason
`task-details.md` gave for deferring it no longer holds. Two boundaries stand.
**Repeat is not rebuilt** — the row picks one of `Recurrence`'s four periods,
and the board's editor is Phase 4 per D-019. **The overflow is omitted** until
its contents are decided, since deletion already lives in the row's long-press
menu.

Everything below this line describes work already done.

---

## What shipped before it

**D-021 is implemented: the app colours what it knows.** `CheckState` had three
values and the screen drew two, so a manufacturer feature inferred from
`Build.MANUFACTURER` rendered exactly like a permission the user had refused.
Every OnePlus, OPPO, Realme, Xiaomi, Redmi, POCO, Samsung, Huawei and Honor user
saw a permanent red "Action needed" from first launch.

`ReminderHealthState` gains `WorthChecking`, and `state` is now ordered by
certainty: a recorded miss, then a `Blocked` check, then a `Warning`, then
Ready. `firstFailing` split into `firstBlocked` and `firstWarning`, both keeping
the worst-cost ordering the KDoc argues for. The screen's headline gains its
fourth branch, the check rows tint per state, the three rows became one
segmented group through `ListItemDefaults`, and the badge container is gone: the
glyph sits in a 40dp slot and carries the colour itself.

**The entry said tertiary and the render refused it.** `tertiaryContainer` is
`#FFD7E3` against `errorContainer` at `#FFD8D6`, one step apart in green, so the
caution and the error were indistinguishable. `reminder-health.md` already
recorded that; D-021 did not, and now carries the correction. Warning is
neutral, and the second cue is a question mark rather than a colour.

Verified on the emulator with the manufacturer temporarily forced to OnePlus,
since a Pixel infers no restriction. The Warning-only screen reads calm: no red
anywhere, "Worth checking", and "Sleep standby can delay reminders. Focuslist
cannot tell whether it is on." The mixed state renders both at once, a red
Notifications row and a neutral Sleep standby row on the same screen, which is
the rule that certainty belongs to the check rather than to the headline. The
override was reverted.

**Task Details is rebuilt to D-018.** `TaskDetailsSheet.kt` and its host are
gone, about a thousand lines of them, and `TaskDetailsScreen.kt` replaces them:
a `task-details/{id}` route, three regions, five picker rows that commit as they
are chosen, and no Save. It is a room, so back returns to the list the row was
tapped on and the back stack does that without help.

The four lists lost their `openTaskId` state and their sheet host and take an
`onOpenTask` instead. `allTasks` is new on the view model, because a screen
reached by id from any list cannot read the list it came from: a task
rescheduled off Today while its details were open would stop being found and the
screen would close on the edit that had just been made.

**Two things are deliberately absent**, and both are recorded rather than
forgotten. The Repeat row picks one of `Recurrence`'s four periods or none; the
board's nine-frame editor with an interval, a weekday set and an end condition
is Phase 4 per D-019. And the overflow is omitted, since its contents are
undecided and deletion already lives in the row's long-press menu.

`thisWeekend` and `endOfWeek` are pure functions in `core/domain`, because D-018
had to fix their meaning and a fixed meaning is worth asserting: eight tests
cover both, including that neither ever resolves to the day it was pressed on.

**One document was stale and is corrected.** `expressive-components.md`'s Task
Details section still described the two-page sheet, the typed due date, the
absent Reminder row and the disabled-while-invalid confirm, which contradicted
D-018, `task-details.md` and the board at once. Its own row-family section had
already been updated for the Plan row, so the file disagreed with itself.
`CLAUDE.md` resolves it — `docs/decisions.md` wins on scope — and the section is
rewritten rather than left for the next reader to trip over.

**Today and Quick Add are built, and that was a deliberate deviation from the
plan.** This section said the design pass had happened and Phase 4 was
next. Phase 4 is not next. The Glance widget and real recurrence wait, and the
work in progress is the screen the design pass settled: D-011, D-012 and D-013,
taken in that dependency order.

The reason is D-012. Today is the screen `PRODUCT.md` calls the centre of the
app, and it has never answered the question it exists for. The design pass
settled how it should. Building Phase 4 on top of an information architecture
that is about to change is the mistake Phase 3 was sequenced to avoid, and the
Focus now card changes Today's. Phase 4 comes after this.

Four things, in an order that was not arbitrary. **All four are done**, and
what follows describes what shipped rather than what was planned:

1. **D-013, Focus pause and resume.** A prerequisite rather than a
   nice-to-have: the Focus now card's first and strongest reason is "resume
   paused focus", and without a session that can be paused and that survives
   leaving the sheet, that reason cannot fire.
2. **The Focus now card**, with all three of D-012's reasons, since step 1
   supplies the first.
3. **Today's four bands**: Overdue, No time set, Later today, and Completed as
   a collapsed disclosure carrying a count.
4. **Quick Add per D-011**: a trailing time read alongside the trailing day,
   setting a reminder, named by one dismissible chip.

**Today and Quick Add already exist in code**, contrary to how this work was
first described. `TodayScreen.kt` carries the collapsing app bar, the segmented
collection, the FAB and the undo host; `QuickAddSheet.kt` carries the trailing
day, its marking and its supporting line. Steps 3 and 4 are changes to working
screens. What was genuinely absent is the Focus now card and any notion of a
paused session, and git history has never held either.

**One thing the design did not settle, and the code had to.** The extension
behind +5 min does not move the session's origin, although pausing does. Moving
it underflowed: elapsed time floors at zero, so shifting the origin into the
future left the five minutes silently eaten and the estimate alarm rescheduled
to the moment it already pointed at. `extendingPushesTheAnnouncementOutByFiveMinutes`
is the test that caught it, and `FocusSession`'s KDoc carries the reason.

**Verified.** 592 unit tests and 158 instrumented tests pass, the latter on the
Pixel 10 emulator with `ANDROID_SERIAL` pinned. The instrumented run covers
Focus's six states, that leaving pauses rather than stops, Quick Add's chip and
its dismissal, and Today's collapsed Completed band.

**Watched on the emulator rather than tested**, because motion cannot be
asserted: the shape morphing from `Cookie4Sided` to `Cookie12Sided` when a
session starts, the Focus now card arriving and leaving on `reveal`, and the
Completed disclosure opening. All read as intended. The card's paused reason,
the readout counting down, and the FAB clearance at the true end of the list
were confirmed by screenshot.

**Three tests were rewritten rather than repaired**, because they asserted
designs that are now retired. `FocusSessionSemanticsTest` asserted the queue: a
"Next:" footer, completing advancing to the following task, and an empty state
when the queue ran dry. `TodayScreenSemanticsTest`'s undo case asserted a
completed row that is now inside a collapsed disclosure. A view model test
asserted that completing kept the sheet open, which `focus.md` reversed.

**One bug worth naming**, because it is the exact trap `focus.md` warns about.
The sheet ended Focus when `focusedTask` was null, and every exposed flow begins
on a placeholder before storage has answered, so opening Ready closed the sheet
on the way in. The task being genuinely gone is now watched in the view model
against `repository.observeTasks()`, which only emits once it has really read.

**The header is built to D-017 and D-020, and the code no longer trails them.**
`FocuslistTopAppBar` wraps the compact 64dp `TopAppBar`, pinned, with a title
and one side slot and nothing else. The `subtitle` and `scrollBehavior`
parameters are gone from the component, so no screen can pass either.

Four screens changed with it. Today lost the planned-minutes pill, then the
date, then its `exitUntilCollapsedScrollBehavior` and the `nestedScroll` that
fed it. Inbox lost its waiting count. Upcoming and Logbook lost their scroll
behaviour. `todayPlannedMinutes` went with the pill, being its only caller, and
`AppBarTrailingTextAlignment` went with the right-aligned subtitle it was
measured for. Nothing in the app draws `displaySmall` any more.

Verified on the emulator: all four bars are 64dp with no subtitle, and Today's
Completed disclosure now sits on the opening view without scrolling, which is
the 88dp D-020 bought.

**A design review of Focus then ran against the board and produced D-014 and
D-015.** The board and the design documents changed; no Kotlin was touched in
that pass. Chapter 09 of `Focuslist — M3 Expressive` and section 07 of
`Focuslist — Components` both carry it.

**The Focus code was behind the design and has been brought in line.** D-013
was already substantially built, uncommitted, against the shape design D-014
retires: `Circle` to `Clover8Leaf` for a task with an estimate, a six-shape ring
for one without, both driven by a continuous `shapeProgress`. All of it is gone.
`FocusScreen.kt` is rewritten to the current design: title above the shape as
the heading, `Cookie4Sided` and `Cookie12Sided` morphing on a state change only,
the readout at Headline Small inside a 180dp shape, one status line, a round
icon button beside a tonal Complete, and a chevron in place of the close X.

`FocusProgress.kt` and `FocusProgressTest` were deleted rather than adapted,
because D-014 leaves nothing needing a fraction. The three cases worth keeping
from that test — overrun, a clock that has gone backwards, and a missing
estimate — are asserted against `FocusSession` instead. **`focus.md`'s
Verification section still names `FocusProgressTest` and the function**; that is
the one place the documents now trail the code.

D-015 cost a second change worth naming: the sheet's visibility is no longer the
same fact as the session existing. Leaving pauses, so a paused session outlives
the sheet, and a sheet driven by the session would reopen itself on the next
frame. `isFocusSessionActive` became `isFocusSheetOpen`, and the two are written
separately.

D-014 settles what the session shape says. `Cookie4Sided` at rest,
`Cookie12Sided` while running, morphing on a state change only. It no longer
carries progress, because D-013's readout carries that better and the shape was
the worse of the two at it. The task title moved out of the shape to become the
heading, capped at four lines, and the digits dropped to Headline Small inside a
180dp shape. The board had already frozen the old morph in all six states
without recording it, so this writes down what was in front of us.

D-015 settles what leaving does. Close and back both pause rather than stop, so
nothing is discarded in any state. It supersedes D-013's clause that a running
session does not survive leaving, and it removes `focus.md`'s hand-written back
handler.

**The play button is back**, which qualifies the paragraph above. It returns as
a plain 56dp round filled icon button carrying play or pause, not as the origin
of a container transform. The transform stays gone. What brought the button back
is the half of its original argument that never depended on the transform:
holding no text it does not grow with the font scale, and two worded buttons
overflow the 364dp action row at 200%.

**The Logbook's completion-summary card was removed.** It totalled the estimates
of completed tasks and called the result "of finished work", which claims a
measurement the app never makes. `logbook.md` already ruled out "counts,
streaks, statistics, or any summary of throughput", so the board had drifted
from a document that was already right.

**The Logbook panel was then rebuilt from components, and produced D-016.** It
had been the least componentised screen on the board: hand-drawn rows holding a
raw checkbox and two loose text nodes, a hand-drawn header, and day labels at
Title Large, the same size as the screen name. Its rows were 364dp wide at x=24
and 66dp tall against 380 at x=16 and 72 everywhere else, and nothing dimmed a
completed title, so only the checkbox said these tasks were done. It now uses
`Focuslist / Task row` at `Status=Completed` in segmented positions, and the
Section header component the Today bands use.

A `Focuslist / Room header` component was added for this, since the Logbook and
Reminder health both hand-draw a back arrow and a title. Reminder health has not
been moved onto it yet.

D-016 keeps the day grouping, which `logbook.md` had listed as out of scope. The
argument is that the list is ordered by `completedAt` and nothing said so, which
made its one ordering decision invisible. The entry draws the line at a heading
naming a day and nothing more: a count beside it is the step that turns a record
into the scoreboard the original entry was written to prevent.

Empty, loading and read-failure frames were added, which the Logbook had never
had.

**Today's error frame was then rebuilt from the component too, and a copy bug
came out with it.** A sweep found Today's was the only hand-drawn empty or error
block left on the board; Inbox and Upcoming already used `Tone=Error` instances.
Today now matches them, carrying its own calendar icon the way they carry
theirs.

The copy bug is the more useful find. Inbox and Upcoming both read **"Check your
connection and try again."** Focuslist has no accounts, no cloud sync and no
backend, all three permanently out of scope in `PRODUCT.md`, so every read is
local and no connection is ever involved. The line sent users to fix something
that was not the problem and implied a server the app does not have. All four
error states now say what did not happen instead, and
`expressive-components.md` carries the rule and the reason.

The copy existed only on the board. It is not in `strings.xml`, which holds no
error strings at all, so nothing shipped with it.

**The two empty-state treatments were then merged into one.** The plain variants
drew a 28dp card on `surfaceContainerLow`; the illustrated ones sat bare on the
background. What decided between them was whether a mascot had been drawn for
that screen yet, which is invisible to a user and made the Logbook's empty state
look unlike every other empty state in the app. The card went rather than
spreading: a card contains related content, and an empty state is the absence of
content. All five states were re-centred after losing 48dp of card padding.

Two icon defects came out of that. Upcoming's error glyph was bound to
`onSurface` rather than `onErrorContainer` and was the filled `schedule` rather
than the outlined one, so it drew a near-black disc on a pink container while
Today and Inbox drew dark red outlines. Both fixed, and
`expressive-components.md` now states the rule.

**`focus.md` and `expressive-motion.md` no longer trail the code.** Both named
`focusProgress` and `FocusProgressTest`, which the Focus rewrite deleted. They
describe `FocusSession` and `FocusSessionTest` now, including the extension not
moving the origin and why.

**A design review of Task setup then ran, and produced D-018 and D-019.** It
found the largest divergence in the project: `task-details.md`, the code and the
board described three different screens. The doc and `TaskDetailsSheet.kt` had a
bottom sheet with typed date fields, a draft and a Save; the board had a full
screen of picker rows committing immediately. The doc also contradicted itself,
naming reminder among the six fields it edits and again under Out of scope
beside recurrence, which `PRODUCT.md` lists as a task property.

D-018 settles it in the board's favour: capture by typing, edit by picking.
`task-details.md` is rewritten to the board. **The code is now deliberately
behind the design**, because rebuilding that screen is not this phase.

The board was refined before the entry was written rather than after:

- The hero lost its tinted card. It was why the title and notes looked
  read-only, and it was showing duration a second time when Duration already has
  a row. Title and notes are borderless fields now, at Headline Small on
  `onSurface` and Body Large on `onSurfaceVariant`.
- The app bar lost its "Task" title, the same correction the Focus header took:
  two centred headings stacked with the upper one naming the app.
- The Plan row went from 4 variants to 12, gaining Pressed and Focused. Settings
  rows already had all three and these open sheets the same way. Its radius went
  28 to 16 and its surface Container High to Low, matching the Task row and the
  Settings row; a 28dp radius on a 56dp row against 16 on a 72dp row had the
  shape scale backwards.
- Row values moved from Primary to `onSurfaceVariant`. Five accented values
  competed with the one button that should carry the accent, and `None` in
  accent colour read as though something were set.
- `Plan` became a Section header instance instead of a 22sp text node as loud as
  the app bar.
- Both date sheets were rebuilt from `Button - tonal` and `Button - outline`
  instances. They had been frames named after the Compose modifiers they wanted.
  **Scheduled had no clear option**, so a task could not be returned to the Inbox
  from this screen; `Next week` gave up its cell, being the one preset
  `date-parsing.md` excludes by name.

**Two defects surfaced on the way.** The Settings row's Pressed state was broken,
rendering white instead of a subtle darkening because its fills were bound in the
wrong order and `On Surface` sat as the base at full opacity; twelve variants
across the navigation and toggle rows are repaired. And 23 duration strings on
the board read `45 min`, `1 hour` and `1 h 30 min` while `DurationLabel.kt`
formats `45m`, `1h` and `1h 30m` and its KDoc says it is shared so nothing "can
never word it differently". All normalised.

D-019 keeps the nine Repeat frames as a Phase 4 design and out of the code.
`Recurrence.kt` excludes an interval, a weekday set, an end date and an
occurrence count in writing, and the board designs all four. Designing ahead is
fine; building ahead is not.

**D-020 took the header back to a compact 64dp bar and removed the date.** The
board had been drawn as an 80dp header with the overflow beside a 36sp title,
which is the middle option `today-screen.md` rejects by name, and the code was
`LargeFlexibleTopAppBar` at 152dp. Correcting the board to the component was the
plan until the subtitle came out.

That is what settled it. The tall bar was never justified by the title:
`today-screen.md` says the rule it overturned was "right about a title" and
overturned it only because the header had gained a payload. D-017 removed the
planned-minutes pill and narrowed the case to the date alone, "thinner than when
the case was made". Removing the date left nothing, so the original rule applies
again and the suspension simply ended.

Today gets 88dp back on its opening view, roughly one and a bit task rows, on
the screen `PRODUCT.md` calls the centre of the app.

**31 frames were reflowed** and `Focuslist / Screen header — Collapsed` is
retired, since a pinned 64dp bar has nothing to collapse to. Four documents
followed: `today-screen.md`'s app-bar and scrolling sections,
`expressive-components.md`'s app-bar section, `expressive-motion.md`'s What
moves table and its collapse note, and `expressive-design-system.md`'s
typography table. Nothing in the app draws `displaySmall` any more.

**The trap is recorded** in `today-screen.md`, because it nearly caught us:
removing the subtitle looks like tidying, and doing it alone leaves 152dp
holding one word, which is worse than either end of the decision. The subtitle
and the height are one decision.

**The four segmented row components now share a base.** Task row, Plan row,
Settings navigation row and Settings toggle row had agreed on radius, surface
and gap only by hand, and had already drifted: the Plan row sat at 28dp on
`surfaceContainerHigh` with no interaction states while the Settings row shipped
three.

The base is variables, not a component. Figma cannot express the obvious version
here, a container the four compose, because that needs a content slot and slots
cannot be created through the plugin API. So three semantic aliases were added
onto the existing scale, `Segmented/Radius outer` to `Corner/Large`,
`Segmented/Radius inner` to `Corner/Extra Small`, and `Segmented/Gap` to
`Spacing/2`. Every corner of every variant binds to them, 240 bindings across 60
variants, and the 24 frames that group rows bind their `itemSpacing` to the gap.
Both sets render identically afterwards, which is the point.

Aliases rather than binding straight to the scale, because binding to
`Corner/Large` would stop anyone typing 28 but would still mean finding all four
again to move the treatment to 20dp.

**Variant structure is still unprotected.** A variable cannot make one component
carry the same states as another, so that half of the drift stays manual.
`expressive-components.md` names the set: Enabled, Pressed, Focused.

**Also answered there:** why these rows are custom rather than kit instances.
The kit does ship a segmented list, as `Type = Segmented (filled)` on its `List`
set rather than a component of its own, which is why searching by name misses
it. It uses the same 2dp gap. What it cannot express is the anatomy: its
`List item` offers Trailing as None, Check Box, Icon, Radio Button or Switch,
and a task row's trailing is a duration string and an icon button together.

**Four design documents were rewritten** against these entries: `focus.md`,
`expressive-motion.md`, `expressive-components.md` and
`expressive-design-system.md`. Two live contradictions surfaced while doing it
and are fixed. `focus.md`'s Completion section still described the session
continuing onto the next task, which needs the queue D-004 removed and which its
own "Which task" section already contradicted. And `expressive-design-system.md`
still said Focuslist used seven Material shapes, recording a worry about that
accumulation; it uses two now, and the worry is answered.

---

**A design review of Today ran against the board, and the board changed rather
than the app.** No Kotlin was touched, none of it is reflected in code yet, and
the work is in Figma on the `Focuslist — M3 Expressive` page.

- **Colour.** Every M3 library component on the board was still bound to the
  library's own `Schemes/*` variables, which carry no Focuslist modes, so the
  Start focus button and the FAB stayed baseline purple in the wallpaper
  frames. 929 bindings moved onto the `Focuslist Brand` collection. The Light
  value of `On Primary Container` was corrected from `#001356`, a blue that did
  not belong to the indigo primary, to `#403d89`, the tone M3 already uses for
  the Dark mode's primary container.
- **The Focus now card.** Sentence-case label instead of letterspaced caps, the
  estimate moved onto the reason line, and the empty spacer column removed so
  the action sits on the card's own inset. 200dp down to 176dp.
- **The screen header.** Full bleed with its own 16dp padding, matching
  `LargeFlexibleTopAppBar`, and the overflow demoted from a filled primary
  circle to a standard icon button. A separate Collapsed component covers the
  scrolled state, which the board had never drawn.
- **The navigation bar.** M3 colour roles applied. Unselected icons sat on
  `onSurface` while their labels sat on `onSurfaceVariant`, which is what made
  the Upcoming clock the heaviest thing on the bar.
- **Bands.** Task rows in one band now use the `Position=First/Middle/Last`
  variants that already existed, so a band reads as a single connected group.

**The board itself was reorganised.** Scattered sections became one numbered
column with a read-me, a core loop flow map, and a caption on every frame. Dark
coverage was cut from 46 frames to 23, on the rule that dark is kept only where
it proves something light cannot: system surfaces, reminder health contrast,
one frame per surface archetype, widgets, and the theme chooser. Retired frames
live on an `Archive — retired dark frames` page rather than being deleted.

**One thing is blocked and needs a decision before it is built.** The board's
Quick Add sheet shows the parsed day and time as removable chips. That
contradicts two recorded designs. `docs/design/expressive-components.md`
declined chips on Quick Add, because they would be a second way to set a date
alongside the field and would need a precedence rule the user cannot see.
`docs/design/date-parsing.md` puts times of day deliberately outside the
vocabulary and names "tomorrow at 3pm" as the string that must be refused
rather than filed. The board frame already disagreed with both before this
session. Do not build that sheet from the board until it is settled.

---

**Phase 3: Subtract. Complete**, all four exit criteria met. Phases 1 and 2
are complete. **Phase 4 is next**: the Glance widget and real recurrence
rules, which the plan deliberately sequenced after the information
architecture settled.

**D-009 now rests on three manufacturers, and the OnePlus is the outlier.** A
Galaxy S24 Ultra on Android 16 and a Xiaomi on HyperOS both honour
`setExactAndAllowWhileIdle` exactly, `window=0`, delivering 19ms and 280ms
late. The OnePlus 8T demotes the alarm and delivers about fifty seconds late.
The demotion is a minority behaviour, not how Android works.

The decision stands and is stronger for it. All three reported the permission
granted and all three said "Exact alarms: Allowed", while the behaviour
differed by three orders of magnitude, so only measuring delivery tells them
apart. The Xiaomi also showed the health screen earning its place: Focuslist
was absent from MIUI's Background autostart list, so on that phone it could
not have rebuilt its alarms after a restart at all.

**Read the design from `Focuslist — M3 Expressive Clean Slate` (node
`161:3405`) and nothing else.** An earlier page, `Focuslist — M3 Expressive
V1`, is still in the file and disagrees with it. Phase 1 work was started
against the old page twice before that was noticed.

Phase 1 delivered reminders end to end: a time on a task, an exact alarm that
survives a restart and a clock change, an alarm-grade channel, Done and
Snooze on the notification, the snooze arithmetic, a Set Reminder dialog, and
a permission flow covering notifications and exact alarms. All verified on a
OnePlus 8T and on an emulator.

Phase 2 has one delivered slice: **the delivery record**, `schema version 7`
and the `reminder_deliveries` table. `ReminderReceiver` writes a row every
time a reminder fires, carrying the task's title as it read then, the moment
the alarm was aimed at, the moment it arrived, and whether it was announced or
suppressed. Both clocks, as `AGENTS.md` requires. `core/domain/ReminderDelivery.kt`
holds the arithmetic and `LateThreshold`.

It is kept apart from the task because a task's own `reminderDeliveredAt` is
cleared by rescheduling, by completing, and by a recurring occurrence rolling
forward, so the evidence was being destroyed by ordinary use.

Verified on the emulator: a reminder whose alarm fired while the permission
dialog was still on screen recorded `Suppressed`, and granting the permission
produced a second row recording `Announced`.

**One limit to know before building the health screen.** The app cannot detect
that it was demoted. There is no public API to read back a scheduled alarm's
window, and D-009's evidence came from `dumpsys`. So "Exact alarms: Allowed"
will read Allowed on the OnePlus while reminders still drift. The headline
state has to be driven by the delivery record, not by the permission checks.

Second slice: **the health state**, `core/domain/ReminderHealth.kt`. It holds
the three checks and the recent delivery record together, and where they
disagree the record wins. That rule is the point: a screen built on the checks
alone reports Ready on the OnePlus in D-009.

`core/notification/ReminderHealthChecks.kt` answers the two real questions,
and guesses the third from `Build.MANUFACTURER`, because no API exposes these
features and the one that is exposed,
`isIgnoringBatteryOptimizations()`, was measured in D-009 to change an alarm's
flags and leave its window untouched. OnePlus, OPPO and realme map to sleep
standby; Xiaomi, Redmi and POCO to autostart; Samsung to sleeping apps; Huawei
and Honor to protected apps. Every other device is left alone, because a false
warning on a Pixel costs the app the attention the real warning needs.

A restrictive device that then delivers `EvidenceOfHealth` reminders on the
trot stops being warned about, and one late delivery brings the warning back.
The app cannot read the setting, so behaviour is the only evidence there is.

**Only deliveries that were exposed to idle time count as that evidence.**
`schema version 8` records how far ahead each alarm was set, and one set less
than `EvidenceHorizon` ahead proves nothing: the failure needs a phone that has
been left alone, so a reminder set for five minutes' time arriving punctually
says only that `AlarmManager` works. Without this the warning could clear on
daytime reminders and go quiet before the 6am one was missed.

Third slice: **the health screen**, `ui/health/`, with all four states, the
three rows, the settings routing, and the test reminder. Reachable from More
for now; the Clean Slate board puts it in an app-bar overflow, which arrives
with Phase 3's navigation change.

`ReminderHealthState.ActionNeeded` carries which check caused it. The first
build of the screen did not, and blamed the manufacturer's sleep feature for a
notification permission the user had refused. The headline and the button both
read `ReminderHealth.firstFailing`, so they cannot point at different problems.

The test reminder travels the same road as a real one: `AlarmManager`, a
receiver, a notification, and a row in the delivery record. Its thirty-second
futurity is under `EvidenceHorizon` by design, so it can raise a warning and
never clear one. Measured on the emulator at four milliseconds late, against
the roughly fifty seconds D-009 measured on the OnePlus.

Fourth slice: **routing to the manufacturer's own screen**,
`core/notification/DeviceSettingsRoute.kt`. Best effort, and known to be best
effort. Every candidate is resolved against the package manager before it is
launched and the launch itself is guarded, so the user always lands somewhere:
the vendor's screen where the phone permits it, the app's Android settings page
otherwise. The button is named after wherever it will actually arrive.

The packages appear twice, in that file and in `<queries>` in the manifest,
because from Android 11 an undeclared package is invisible and resolves to
nothing. That failure is silent: the deep link would degrade to the generic
page on exactly the phones that need it, and look like the feature was never
built. A unit test checks the two lists agree.

**Two things were learned from the OnePlus 8T, and D-010 records them.** The
activity names every published list gives for this vendor are stale, because
ColorOS and OxygenOS merged at ColorOS 12 and renamed everything to
`com.oplus`. And the replacements are guarded by a signature-level permission,
so they resolve and then throw. There is no ColorOS or OxygenOS entry in the
table as a result, and a test exists to stop one being helpfully added back.

The MIUI, One UI and EMUI entries are still guesses, from the same kind of list
that proved stale here, and none has been tried on that hardware.
`aRestrictedDeviceHasAtLeastOneScreenToOffer` in the instrumented suite is what
will report the next one to go stale. It passes vacuously on a Pixel and means
something on a Xiaomi, a Samsung or a Huawei.

**One caution about that test.** It passed on the OnePlus while the app itself
failed, because it only asks whether a screen resolves, and resolving is not
permission to start it. Resolution and launch are separate questions on these
skins, and only the second one matters. Trust the log line over the test:
`FocuslistReminder` records which screen was opened, or that none was.

**Phase 2's exit criteria are met.**

**Phase 3 and the design pass are the same job.** The Clean Slate board
mentions Anytime and Someday nowhere, and every screen on it carries the same
three-item bar: Today, Inbox, Upcoming, with Logbook, Reminder health and
Settings behind an app-bar overflow. `PRODUCT.md` already described that
information architecture. The code disagreed in two ways. The first is now
fixed. The second was missed, and is described below.

The subtraction went in three commits, in an order that mattered:

1. **Inbox stopped reading placement.** It is now every task that is
   outstanding and undated. This came first because Inbox previously required
   `placement == INBOX`, so an undated Anytime or Someday task appeared in no
   primary list at all. Deleting the screens before this would have left real
   work in the database with nothing able to reach it.
2. **The interface went.** Two routes, two More entries, `PlacementScreen`,
   the triage action on every row, a picker in Task Details, and the undo
   machinery behind the move.
3. **The axis went**, `schema version 9`. `TaskPlacement`, the column, the
   converter, and the `anytimeTasks` and `somedayTasks` helpers.

**`MIGRATION_8_9` is the first migration here that is not an `ADD COLUMN`.**
`minSdk` is 29 and `ALTER TABLE ... DROP COLUMN` needs SQLite 3.35, which
arrives with Android 14, so it would work on a modern test device and break
the upgrade for everyone else. The table is recreated instead: create, copy
naming all thirteen surviving columns on both sides, drop, rename. Never
`SELECT *` in that copy. It passes every test and shuffles people's data.

Verified on the Pixel 10 emulator: 145 instrumented tests including a
version-1 database walked to 9, a version-2 one, and a fully populated
version-8 row crossing the recreated table with only `placement` missing.
524 unit tests.

4. **The navigation bar was reconciled last.** Upcoming moved into the bar,
   the More tab went, and Logbook and Reminder health moved to an app-bar
   overflow. Logbook lost its bottom bar and gained a back arrow, because with
   More gone there was nothing in the bar for it to select and it would have
   drawn three items and told the user they were in none of them.

This last one was nearly missed. Removing Anytime and Someday from More left a
plausible looking menu, and every navigation test asserted the arrangement
that existed rather than the requirement in `PRODUCT.md`, so nothing could
fail. It was found by writing a sentence in `ARCHITECTURE.md` and checking it.
`NavigationSemanticsTest` now asserts the bar holds exactly the three names
`PRODUCT.md` gives.

Two things finished it after the navigation.

**The Focus queue, which had outlived the decision that removed it.** D-004 cut
it; what survived was a resolution rule, `the chosen task while it is still in
the queue, otherwise the head`. That fallback was the queue: completing,
deleting or rescheduling the chosen task moved Focus to whatever headed
today's list. It was visible to the user as a "Next:" footer. The board is
explicit, no Focus frame has a next-task preview and Focus — Ready reads "One
task. Nothing else until you leave Focus."

**The design documents.** Ten under `docs/design/` still described Anytime,
Someday or placement. All seven Superseded banners are gone, which is what
they promised: "this banner comes off in Phase 3, when the document is
rewritten." `navigation.md` and `task-states.md` were rewritten; the rest were
corrected in place. Every remaining mention of those words records that they
were removed rather than describing them as present.

**Not in Phase 3, and not started:** the UP NEXT hero card, Task Details as a
full screen, and the Focus timer. All three come from the board rather than
from this plan, and each needs a decision before it is built. The timer
contradicts D-004 and needs a superseding entry first. The board is the source
of truth for how the app should feel; it is not a scope document, and taking
work from it directly is how a phase quietly grows: the UP NEXT hero card on Today,
Task Details as a full screen with a read-only Plan card, and Focus gaining a
timer with pause and resume. The last contradicts D-004 and needs a
superseding entry first. Do not restyle a screen the board does not contain.

Update this line when a phase begins and when it ends. It is the first thing
a new coding session should read, and the only place that says where the work
actually is.

Do not build ahead of the current phase. A task belonging to a later phase is
a task for later, even when it is small, even when it is adjacent to what is
being worked on now, and even when it would be quicker to do it while the file
is already open.

---

## Phase 1. Make it tell you

Give the app the feature it is named for and does not have.

Focuslist currently schedules exactly one kind of alarm, for a focus session
overrunning its estimate. There are no task reminders at all, the manifest
declares only `POST_NOTIFICATIONS`, and there is no boot receiver, so a device
restart would silently lose every alarm ever set.

Work:

- `reminderAt` on `Task`, with a Room migration and fixtures updated in both
  source sets
- Scheduling through `AlarmManager.setExactAndAllowWhileIdle`
- `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` in the manifest
- A boot receiver that reschedules every outstanding reminder
- An alarm-grade notification channel, separate from the focus channel
- Done and Snooze actions on the notification
- A permission flow that explains why the app is asking
- Setting a reminder from the task details sheet

Exit criteria:

- A reminder set for a time fires at that time with the app closed
- The same reminder fires after a device restart between setting and firing
- The same reminder fires with the screen off and the device idle
- Completing from the notification completes the task, and the app agrees
- Snoozing from the notification reschedules, and survives a restart
- Tests cover scheduling, rescheduling on boot, and the snooze arithmetic

Estimate: 5 to 7 weeks.

---

## Phase 2. Make it trustworthy

The differentiator. Reminders that fire on a Pixel are table stakes.
Reminders that fire on a Xiaomi are the product.

Work:

- Detect manufacturer battery restrictions that
  `isIgnoringBatteryOptimizations()` does not report: Samsung sleeping apps,
  Xiaomi autostart, OnePlus sleep standby, and others
- Route the user to the correct settings screen for their device
- Explain the problem in language a person who does not know what Doze is can
  act on
- Record the time each alarm was scheduled for and the time it actually
  fired, and notice the gap
- A reminder health screen that reports whether the app can currently be
  relied on
- A test reminder the user can fire in 30 seconds to check

Exit criteria:

- On a restricted device, the app says it is restricted before a reminder is
  missed, not after
- Every warning the health screen shows leads somewhere the user can act
- A missed reminder is detected and surfaced rather than passing silently
- The health screen is honest when everything is fine, and does not nag

Estimate: 4 to 5 weeks.

---

## Phase 3. Subtract

Bring the code to the scope `PRODUCT.md` now describes.

Do this before Phase 4, so the widget is not built against an information
architecture that is about to change.

Work:

- Remove Anytime and Someday, and the placements behind them
- Collapse navigation to Inbox, Today, Upcoming, with Focus as a mode and
  Logbook reachable but not primary
- Simplify triage to match
- Remove the Focus queue
- Update the design documents under `docs/design/` that still describe the old
  structure, and remove the superseded banners once they are correct
- Room migration for removed placements, preserving user data

Around 16 source files reference the removed concepts. Check with:

    grep -rlniE "anytime|someday" app/src

Exit criteria:

- No reference to Anytime, Someday or the Focus queue remains in `app/src`
- No document under `docs/` describes a destination the app does not have
- Existing tasks in those placements land somewhere sensible after migration,
  and the migration is tested
- The app builds and all tests pass

Estimate: 2 to 3 weeks.

---

## Phase 4. Reach the home screen

Work:

- A Jetpack Glance widget: today's tasks, tap to complete, tap to add
- Dynamic color on the widget, because that is what makes an Android app look
  native from the home screen
- Recurrence past the current four fixed periods: intervals, weekday sets, and
  an end condition
- Completing a recurring task produces the next occurrence and never makes the
  task vanish

Exit criteria:

- The widget updates when the task list changes, without opening the app
- Completing from the widget completes the task
- The widget is legible in light and dark, and at small and large sizes
- Recurrence rules round-trip through storage and are covered by tests

Estimate: 5 to 6 weeks.

---

## Phase 5. Ship it

Work:

- Settings screen
- Backup and restore to a JSON file the user controls
- Full accessibility pass with TalkBack
- Dark theme audit
- Play listing: screenshots, the one-line pitch, privacy policy
- Publish free, with no in-app purchases configured at all

Store listing, one line:

> A calm task app for Android whose reminders actually go off. Free, no
> account, no subscription.

Exit criteria:

- A backup taken on one install restores completely onto a clean install
- Every screen is navigable and comprehensible with TalkBack
- The Play listing makes no claim the app does not deliver
- 1.0 is live

Estimate: 3 to 4 weeks.

---

## After 1.0

In rough evidence order, and only once real users ask:

- Subtasks
- Flat Lists, replacing the deferred Projects idea (see `docs/decisions.md`,
  D-003)
- Tablet and foldable layouts
- Wear OS tile

Let the app's own reviews set this order rather than this document.

---

## Totals

Roughly 19 to 25 weeks to 1.0. Longer than it looks, because Phases 1 and 2
are systems work rather than screens.
