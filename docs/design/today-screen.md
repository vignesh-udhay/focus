# Today Screen

## Purpose

Today is the default view and the first real screen in Focuslist.

It answers one question:

    What should I do now?

Opening the app should make that answer obvious within seconds.

This document specifies the first implementation only. The goal is to
validate the core task-list experience before adding features, so the screen
is deliberately small.

---

# Design principles

## Calm

The screen is a title and a list. Nothing else competes for attention.

No dashboards, no counters, no progress indicators, no motivational copy.

## Scannable

The task titles are the content. Everything else is chrome and should recede.

## Content first

The task collection occupies the dominant visual area of the screen.

Screen furniture earns its height by helping the user choose a task. A screen
title does not, which is why the header spends most of its height on the date
and on how much time is still planned, and names the screen only once.

## Fast

Tapping a checkbox completes a task immediately. No dialog, no wait, no
network.

---

# Screen structure

A single `Scaffold`:

    Scaffold
    ├── topBar                 TopAppBar, 64dp, title only, pinned
    ├── snackbarHost           SnackbarHost, carrying the undo offer
    ├── bottomBar              NavigationBar, passed in by the caller
    ├── floatingActionButton   FloatingActionButton
    └── content                LazyColumn, or the empty state

The bar is hoisted rather than built here, so Today does not decide what the
app's destinations are. `navigation.md` describes what it holds.

The public entry point is one composable:

    @Composable
    fun TodayScreen(modifier: Modifier = Modifier)

`TodayScreen` holds no task state of its own. It reads the shared
`TaskListViewModel` through `taskListViewModel()`, which builds it from the
repository and the current day that `FocuslistApplication` owns, and hands
the result to a private stateless `TodayContent` that owns the layout.

The scaffolding every task list shares is not Today's. The undo offer is
raised by `UndoSnackbarEffect` and Task Details is opened by
`TaskDetailsSheetHost`, both of which every list hosts over the same view
model. Today owns its bands, its capture behavior, and its floating action
button, and nothing else here is Today-specific. `TodayContent` is the preview seam, and
sample task lists exist only there.

Apart from `ModalBottomSheet`, which Quick Add and Task Details opt into,
none of the components named in this document require
`@OptIn(ExperimentalMaterial3Api::class)` in the Material 3 version this
project uses. Do not add opt-in annotations that the compiler does not ask
for.

---

# Top app bar

A compact 64dp M3 `TopAppBar`, start-aligned, through `FocuslistTopAppBar`.

- title: `Text("Today")`
- subtitle: none
- navigationIcon: omitted, because Today is a root destination
- actions: the overflow, as a standard icon button, opening Logbook, Reminder
  health and Settings. Reaching the other lists is still the navigation bar's
  job, not the app bar's; this is for what the navigation bar does not carry.
- colors: the Material default, named nowhere

It is pinned. It holds its 64dp in every scroll position.

## This section has reversed twice, and D-020 is the second

The bar was compact, became `LargeFlexibleTopAppBar` at 152dp, and is compact
again. Both moves turned on one question, and it is worth stating plainly so a
third change has to answer it:

**Does the header carry information the screen does not otherwise say?**

The original rule said no, and refused the tall bar:

> A tall header would spend the most valuable part of the screen restating a
> label the user already knows, and it would push the first tasks down out of
> the opening view. Task visibility wins over header prominence.

The first reversal accepted that this was right *about a title*, and overturned
it only because the header had gained a payload: a pill carrying the total time
still planned, and the date. D-017 removed the pill and recorded that the case
then rested on the date alone, "thinner than when the case was made". D-020
removed the date, which left nothing.

**The suspension ended. The argument never changed.** Anyone proposing a tall
bar a third time has to put something in it first.

## The subtitle and the height are one decision

This is the trap the second reversal walked into, and it is marked here so the
next reader does not walk into it too.

Removing the subtitle looks like tidying. The date restates the destination name
at higher precision, which is an odd thing for a subtitle to do, and a 36sp
title renders perfectly well without one. Each step is defensible alone. But the
subtitle was the entire justification for the height, so dropping only the
subtitle leaves 152dp holding a single word, which is worse than either end of
the decision.

If a subtitle ever returns, the height question reopens with it. They move
together or not at all.

## Where the date went

Nowhere. D-020 records why: nothing on Today needs it. The bands are relative,
Overdue and No time set and Later today, and so is the Focus now card, so no
row, band or card is harder to read for its absence. It was pleasant rather than
load-bearing.

If that turns out to be wrong, the answer is the date as the title in the
compact bar, not the tall bar returning. The height was never what made the date
useful.

## Still true

Do not use `CenterAlignedTopAppBar`. A start-aligned title is the Android
convention for a root list destination, and a centered one reads as an iOS
navigation bar.

Do not override the app bar's title typography. The component supplies it. The
original reason was that naming a style freezes the collapsed state at the
expanded size, which a pinned bar cannot suffer from; the rule survives its
reason because a screen naming its own type is how a design system stops being
one.
---

# Scrolling behavior

The `LazyColumn` is the only scroll container on the screen.

The app bar is pinned and takes no scroll behaviour. It holds its 64dp in every
scroll position.

This section used to say the opposite at length: create
`TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`, apply
`Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` to the `Scaffold`,
and pass the same behaviour to the bar, so that 152dp gave its height back as the
list moved under it. D-020 removed the tall bar, and 64dp has nothing to collapse
to.

`TopAppBarDefaults.enterAlwaysScrollBehavior()` is still worth knowing about if
the bar ever starts to feel like it is in the way: it scrolls the bar off and
returns it on the first upward scroll, handing those 64dp to the list. That is a
separate question from the height and can be answered on its own.

The activity already runs edge to edge. Apply the Scaffold's `innerPadding`
to the list's `contentPadding`, not as a `padding` modifier wrapped around
the list. Content must scroll under the system bars rather than stopping
short of them.

## The FAB does not hide on scroll

Material allows it and this screen declines it. Hiding the capture control
while someone is reading their list removes it at the moment they are most
likely to think of something new, and `PRODUCT.md` asks that capture require
almost no decisions. Today is short enough that a persistent FAB rarely covers
anything worth seeing.

That holds only if the list can scroll clear of it. See Spacing.

---

# Spacing

Use `FocuslistSpacing` for spacing the components do not own.

- horizontal list padding: `FocuslistSpacing.md`
- vertical padding at the top and bottom of the collection:
  `FocuslistSpacing.xs`
- between segments: `ListItemDefaults.SegmentedGap`, never a Focuslist token

The bottom content padding must clear the floating action button, or the last
task sits underneath it and cannot be tapped. Combine the Scaffold's bottom
inset with the height of the FAB, the spacing the Scaffold leaves beneath it,
and a gap above it, composed from `FocuslistSpacing` tokens rather than written
as one hard-coded figure. The FAB is the 56dp default size, so that is roughly
72dp of clearance before the navigation bar is counted.

The board briefly drew the 80dp medium size, which M3 Expressive offers. It was
changed back to match the code. 56dp is the standard FAB, and the 24dp the
larger one costs is permanent, because this FAB does not hide on scroll.

The failure is quiet, which is why it is worth stating. The last row is still
drawn, so nothing looks broken; its checkbox and its whole 72dp target are
simply covered, and the task cannot be completed from the list. No composed
frame on the board shows this, because every one of them happens to hold a
short enough list that the content ends above the FAB. Do not read their
spacing as the specification.

## Section labels align with the group, not the text inside it

A band label starts where the group it heads starts. The task cards sit at
`FocuslistSpacing.md` from the screen edge, so the label starts there too, and
the two form one column.

An earlier version indented Today's labels a further 16dp, which aligned them
to nothing: not the card edge, and not the row title, which begins after the
checkbox. Upcoming's date headers were already correct and Today now matches
them. Both screens are the same list with different grouping rules, and they
should not look like two systems.

There is one section header component. If two appear with the same name, the
second is a stale library duplicate; do not pick it.

Do not re-derive the internal padding of `TaskRow` or the app bar. Those
components own their own spacing.

---

# Typography hierarchy

From most to least prominent:

1. screen title, supplied by `TopAppBar`
2. task title, `bodyLarge`, supplied by `TaskRow`
3. task metadata, `bodySmall`, supplied by `TaskRow`

The empty state sits outside that hierarchy and uses `titleMedium` with a
`bodyMedium` supporting line, above them the cat curled asleep, which says why
the screen is empty. `expressive-components.md` owns the variant, its colour
roles, and why the headline did not follow the board up to Title Large.

Every style comes from `FocuslistTypography`. Do not define new text styles
for this screen.

---

# Surfaces and colors

The screen is two surfaces: the background, and the segmented collection
sitting on it.

- Scaffold `containerColor`: `surface`
- segments: `ListItemDefaults.segmentedColors(containerColor = surfaceContainer)`
- app bar: the Material default, named nowhere

Both overrides matter. The default segment container color resolves to
`surface`, and the Scaffold's default `containerColor` is `background`, which
carries the same tone. Leaving both at their defaults makes the collection
invisible against the screen behind it. The collection sits one tonal step
above the screen behind it, and the color is applied at the collection's call
site rather than inside `TaskRow`.

Keep the contrast slight. The list should read as one coherent surface with
soft edges, not as six separate tiles.

All colors come from `MaterialTheme.colorScheme`, through `FocuslistTheme`,
so dynamic color and both themes work without further work. Do not hard-code
a color anywhere on this screen.

## The card appears for an event, and most days there is no card

Two reasons, per D-035: a focus session was paused, or a reminder fired and the
work is still outstanding. Nothing else promotes a task, and there is no
fallback to "the first task".

A third reason used to match any task scheduled for today carrying no time,
which is most of them. Under it the card was the first row of the "No time set"
band lifted out and drawn larger, with that band's own label as its explanation,
on grounds equally true of every other row in the band. D-035 has the argument
and what would reverse it.

**Draw and review this screen without a card.** That is now its ordinary state:
work scheduled, nothing timed, nothing started, so the list begins at its first
band under the app bar. A screen that only looks right with a card on it is
mis-specified, and every preview but the card's own should be checked in both
forms.

## The card rounds to the same corner as the bands

`FocusNowCard` names `shape = MaterialTheme.shapes.large`, which is the corner
`ListItemDefaults.segmentedShapes` gives a band's outer edges.

It had to be named, because the defaults disagree. `CardDefaults.shape` is
`medium`, so the card sat at 12dp directly above rows at 16dp, on the same left
edge at the same inset. Measured on a Pixel emulator at 420dpi from the corner's
cut-out area: 31.4px against 41.4px, now 42.0px against 41.4px.

`expressive-design-system.md` says shape "communicates component identity" rather
than hierarchy, and by that rule a card and a list item may legitimately differ.
They may not differ here. D-012 removes the promoted task from the bands below so
it appears on Today exactly once, which makes the card a promoted task rather
than a different kind of object, and the eye compares the two edges directly.

Named as the token rather than a number so the two move together. Neither had
chosen a corner, which is precisely how they drifted apart: each inherited a
different Material default and nothing recorded that they were meant to match.

---

# Completed-task behavior

Completed and incomplete tasks coexist in the same list.

When a task is completed:

- the checkbox changes state
- the title takes the completed treatment defined in `task-row.md`
- the row moves to the bottom of the list

Today reads in three bands: outstanding tasks scheduled for today, then
outstanding tasks that are overdue, then completed tasks. Within each band the
order the query was handed is preserved. This ordering lives in
`TaskQueries.todayTasks`, not in SQL and not in the screen.

Completion is checked before scheduling, so a completed task sinks to the
bottom whether it was scheduled for today or is overdue. Reopening it lifts it
back into its scheduling band.

The task does not leave the list and nothing is destroyed. This is the
screen's answer to the "eventually leave the active task list" clause in
`task-row.md`: it sinks rather than leaves, and completion stays reversible by
tapping the checkbox again.

Completing also raises a snackbar reading "Task completed" with an "Undo"
action, on the same surface and for the same duration as deletion. Undo reads
the task fresh and clears only `completedAt`, so anything else that changed in
the meantime survives, and the Today query decides where the reopened task
lands. Reopening a task by tapping the checkbox raises no new offer, because it
is already the reversal; it withdraws the standing offer instead, so no
snackbar outlives the state it describes.

Completed tasks are not grouped under a header, dimmed as a block, or hidden.
The move is not animated: the row simply appears in its new position.

---

# Interaction states

| Interaction | Result |
| --- | --- |
| Tap checkbox | Toggles completion immediately |
| Tap row body | Opens task details |
| Tap Focus now card body | Opens task details |
| Tap the card's own button | Opens Focus |
| Long press | Nothing, since D-023 |
| Swipe | Nothing |

Completing and opening stay separate, as specified in `task-row.md`.

**The card had no entry here at all until it needed one**, and that is how a
behaviour decided in a code comment turns into a defect report: the card took no
click, on the argument that a clickable container behind two controls is a
target whose edges the user cannot see. D-012 removes the promoted task from the
bands, so that left its details unreachable from Today. The card's body opens the
task now, with the same `Open task details` label the rows carry.

The row's `onClick` opens Task Details, the full screen editing the fields a
task carries about itself: title, notes, scheduled date, due date, estimated
duration, and how often it repeats. It is a destination on
`task-details/{taskId}`, so opening one is a navigation and back returns here.

**This paragraph used to say the opposite**, that Task Details "must not
navigate, and there is no details screen or back stack", and described a sheet
holding a draft that Save committed. D-018 replaced all of that: the screen
commits as you go and there is no Save. `task-details.md` carries the current
description.

Editing goes through `TaskListViewModel.editTask`, which reads the stored task
fresh and copies only the editable fields, leaving `id`, `createdAt`,
`completedAt`, and `deletedAt` untouched: editing can never complete, reopen,
delete, or restore a task. A task edited out of Today leaves the list through
the ordinary query, with no snackbar and nothing to undo.

Task Details reads the task back out of the list by id, so a task deleted from
under it simply stops being found and the screen pops.

Long press opens a `DropdownMenu` anchored to the row, holding one item:
Delete. Whether that menu is open is transient state of one row and stays in
that row's own `remember`, not in the view model.

A task row has no swipe behavior. See "Deleting a task" for why.

Pressed feedback belongs to `SegmentedListItem` and its shape morph. Do not
add a pressed background, a selection state, or a ripple of your own.

---

# Deleting a task

Deletion is a soft delete. `TaskListViewModel.deleteTask(id)` stamps `deletedAt`
through `TaskRepository.softDelete`, and the row stays in the database. The
task leaves Today because the DAO's `observeTasks()` excludes every row that
has a `deletedAt`.

Completion and deletion are independent axes. Deleting a completed task keeps
its `completedAt` in storage.

The interaction is deliberately two steps: long press, then choose Delete.
There is no confirmation dialog; undo is the recovery mechanism. A task row
still has no swipe behavior, so no single gesture can clear a task off the
screen.

Deleting raises a snackbar reading "Task deleted" with an "Undo" action, shown
for `SnackbarDuration.Long`. Undo calls `restore()` through the repository, the
task returns through the same Room flow, and the Today query decides where it
lands rather than the UI putting it back by hand. Everything else about the
task, including `completedAt`, is untouched, because clearing `deletedAt` is
the whole operation.

The view model holds the id of the action on offer, not the row, because the
row may be gone from the list by the time the snackbar appears. One offer
stands at a time across both completion and deletion: a newer action replaces
the offer rather than queueing behind it, and an undo for a superseded action
does nothing. Completing a task and then deleting another leaves the deletion
on offer, and the completion stands.

Letting the snackbar time out or dismissing it leaves the task deleted. There
is no automatic restore. No screen shows deleted tasks, and nothing purges
them.

---

# Accessibility

Task titles wrap to as many lines as they need. Never set `maxLines`,
`TextOverflow.Ellipsis`, or a fixed height on a task row. A title that cannot
be read in full is a broken task list.

Requirements:

- verify the screen at the largest system font size, with a long title and
  metadata present
- no fixed row heights anywhere on the screen
- the checkbox keeps its 48dp touch target and its content description
- each row exposes two nodes to TalkBack: the checkbox, and the row itself
- the row's tap carries a click label, so it is announced as "open task
  details" rather than as an undiscoverable
  gesture
- completion is legible without color, through the checkbox state and the
  strikethrough
- the empty state text is reachable by TalkBack
- the add-task button is reachable and announces its label

The first implementation targets compact width. It should not break on a
wider window, but tuning for expanded layouts is out of scope.

---

# Preview requirements

Previews use fake in-memory data only, and pass `dynamicColor = false` so
they do not depend on the host wallpaper.

Required previews:

1. populated list, light and dark
2. empty state, light and dark
3. populated list at a large font scale, using `fontScale` on `@Preview`

The populated preview must include:

- an outstanding task scheduled for today
- an outstanding task that is overdue
- an outstanding task carrying a reminder that has passed, which is what puts a
  card on the screen at all since D-035, and which leaves the Overdue band, so
  the overdue task above is what keeps that band populated
- a completed task
- a title long enough to wrap onto multiple lines

so that all three ordering bands are visible. Every task in Today has a
scheduled date and therefore carries at least one metadata segment, so a row
with no metadata cannot occur on this screen. `TaskRowPlayground` covers that
case instead.

`TaskRowPlayground` already covers the row in isolation. These previews cover
the screen: the app bar, the collection, the spacing, and the FAB together.

---

# Sections

Four bands, in this order, per D-012:

    Overdue          past, and needs a decision
    No time set      today, do it whenever
    Later today      today, it will announce itself
    Completed · N    a disclosure, collapsed by default

Every band carries a label. Each is a line of text above its group, and nothing
else: no divider, no container. Each band rounds its own corners, so it reads
as one collection rather than a slice of a longer one. Rows inside "No time
set" do not repeat the band's own words in their supporting line.

Completed is the exception to "nothing else": it carries a count and it
collapses, because a plain completed list grows through the day and pushes live
work down the screen.

**This replaces an earlier rule.** This section previously described three
bands — scheduled for today, overdue, completed — with the first carrying no
label, on the argument that "at the top of the Today screen, today's work needs
no announcement", and with Completed neither counted nor collapsible. The band
order changed, so the first band is no longer the one that needs no
announcement, and the argument retires with the position. D-012 has the
reasoning, including why the label is "No time set" rather than "Anytime
today".

The grouping is read from `TaskQueries.todaySections`, which cuts the list
`todayTasks` already ordered at the points where the band changes. Concatenating
the sections returns exactly what `todayTasks` returned; nothing is re-sorted
and no filtering moved into the screen.

---

# Design system

Colours, type, shape, spacing and motion come from
`expressive-design-system.md`, the components from `expressive-components.md`,
and anything that moves from `expressive-motion.md`. Those three files are the
contract; this one describes only what is particular to Today.

Today is the reference screen for that system: it is the first to adopt it and
the pattern the other lists follow.

---

# Out of scope

Still not part of this screen:

- a nav host and a navigation graph
- a task details screen, as opposed to the editing sheet described in
  `task-details.md`
- a navigation rail, and adaptive navigation generally
- section headers, grouping, filtering
- manual reordering and drag and drop
- swipe gestures
- natural-language date parsing in a task title, which Quick Add now does at
  the end of a title only; see `date-parsing.md`
- subtasks and recurrence
- projects and areas, which are cut; see `docs/decisions.md`, D-003
- reminders, which Phase 1 adds
- the Focus screen itself, which is its own destination and its own document
- widgets and app shortcuts
- notifications, which exist only for a running focus session reaching its
  estimate; nothing on Today notifies. See `focus.md`
- expanded-width and tablet layout tuning
- search
- AI, agents, analytics, and gamification of any kind

Loading and error states are still absent. Tasks come from a local Room
database that emits quickly enough that no loading state has been introduced,
and no error state has been designed. Both remain open questions rather than
settled decisions.

---

# Implementation status

This screen is implemented. It is the start destination of the navigation
graph, and it carries the app's navigation bar like every other list. How that
graph is arranged is described in `navigation.md`, not here.

Built and working:

- the compact pinned app bar, the segmented collection, the illustrated empty
  state, and the extended FAB
- Quick Add, creating a task scheduled for today
- completion, stored as `completedAt` and persisted through the repository,
  with undo offered in a snackbar
- the three-band ordering described above
- soft deletion from Task Details' overflow, with undo offered in a
  snackbar
- starting Focus from the Focus now card or from Task Details, described in
  `focus.md`
- Task Details, editing title, notes, scheduled date, due date, and
  estimated duration, described in `task-details.md`

Tasks are stored in a Room database owned by `FocuslistApplication`, read
through `TaskRepository`, and derived into Today by `TaskQueries.todayTasks`.
The schema, its versions, and its migrations are described in `storage.md`.

Cut, not pending: Areas and Projects. See `docs/decisions.md`, D-003.

Not built yet: Settings, which Phase 5 adds.

`TaskRowPlayground` remains a temporary harness, separate from this screen.
