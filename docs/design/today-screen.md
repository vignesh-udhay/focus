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
    ├── topBar                 LargeFlexibleTopAppBar, title and subtitle
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

Use `LargeFlexibleTopAppBar`, start-aligned, through `FocuslistTopAppBar`.

- title: `Text("Today")`
- subtitle: the date, and a pill carrying the total time still planned
- navigationIcon: omitted, because Today is a root destination
- actions: none. Reaching the other lists is the navigation bar's job, not the
  app bar's.
- expandedHeight: the default, 152dp with a subtitle
- colors: the Material default, named nowhere

## This reverses an earlier rule, deliberately

This section previously said the opposite. It named
`LargeFlexibleTopAppBar` among the components to avoid, and argued:

> A tall header would spend the most valuable part of the screen restating a
> label the user already knows, and it would push the first tasks down out of
> the opening view. Task visibility wins over header prominence.

That argument is right **about a title**, and it is kept for one: "Today" is a
label the user already knows, because they tapped Today to get here. If the
header carried only a title, the old rule would still stand.

Two things it assumed are no longer true.

**The header now carries information rather than a label.** The date is not
available anywhere else on the screen, and the planned total answers the
question `PRODUCT.md` puts at the centre of Today: knowing that two hours of
work remain is part of deciding what to do next. Neither restates anything.

**The old bar was pinned.** It held its 64dp in every scroll position and never
gave it back. This one collapses to 64dp as soon as the list moves under it, so
the extra height is spent only on the opening view and returned immediately.

The cost is real and worth stating: at rest the header is 152dp against the old
64dp, which is roughly one task row below the fold. That was accepted knowingly.

`MediumFlexibleTopAppBar` was considered as a middle option and rejected. With a
subtitle it is 136dp, so it saves 16dp, and it drops the subtitle to
`labelLarge` at 14sp, which makes the date and the pill harder to read. It costs
fidelity to the design and buys almost nothing. If the opening view ever has to
be protected, go back to the compact bar and find another home for the date and
the pill; do not take the middle.

## Still true

Do not use `CenterAlignedTopAppBar`. A start-aligned title is the Android
convention for a root list destination, and a centered one reads as an iOS
navigation bar.

Do not override the app bar's title typography. The component supplies it, and
shrinks it as the bar collapses; naming a style freezes the collapsed state at
the expanded size.

---

# Scrolling behavior

The `LazyColumn` is the only scroll container on the screen.

Let the app bar collapse:

- create the behavior with `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`
- apply `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` to the
  `Scaffold`
- pass the same `scrollBehavior` to the app bar

The bar gives back its extra height as the list moves under it and settles at
64dp, changing its container color on the way. A pinned behaviour would hold all
152dp of a two-row bar in every scroll position, which is the thing the old
compact rule was right to object to.
`TopAppBarDefaults.enterAlwaysScrollBehavior()` is the alternative worth
trying if the bar starts to feel like it is in the way: it scrolls the bar
off and returns it on the first upward scroll, handing those few dp back to
the list. Compare both on a device before switching.

The activity already runs edge to edge. Apply the Scaffold's `innerPadding`
to the list's `contentPadding`, not as a `padding` modifier wrapped around
the list. Content must scroll under the system bars rather than stopping
short of them.

---

# Spacing

Use `FocuslistSpacing` for spacing the components do not own.

- horizontal list padding: `FocuslistSpacing.md`
- vertical padding at the top and bottom of the collection:
  `FocuslistSpacing.xs`
- between segments: `ListItemDefaults.SegmentedGap`, never a Focuslist token

The bottom content padding must clear the floating action button, or the last
task sits underneath it and cannot be tapped. Combine the Scaffold's bottom
inset with the height of the medium extended FAB, the spacing the Scaffold
leaves beneath it, and a gap above it, composed from `FocuslistSpacing`
tokens rather than written as one hard-coded figure.

Do not re-derive the internal padding of `TaskRow` or the app bar. Those
components own their own spacing.

---

# Typography hierarchy

From most to least prominent:

1. screen title, supplied by `TopAppBar`
2. task title, `bodyLarge`, supplied by `TaskRow`
3. task metadata, `bodySmall`, supplied by `TaskRow`

The empty state sits outside that hierarchy and uses `titleMedium` with a
`bodyMedium` supporting line.

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
| Long press | Opens the task actions menu |
| Swipe | Nothing |

Completing and opening stay separate, as specified in `task-row.md`.

The row's `onClick` opens `TaskDetailsSheet`, a `ModalBottomSheet` editing the
five fields a task carries about itself: title, placement, scheduled date, due
date, and estimated duration. It must not navigate, and there is no details
screen or back stack.

The sheet holds a draft, so dismissing writes nothing. Save goes through
`TaskListViewModel.editTask`, which reads the stored task fresh and copies only
those five fields, leaving `id`, `createdAt`, `completedAt`, and `deletedAt`
untouched: editing can never complete, reopen, delete, or restore a task. A
task edited out of Today leaves the list through the ordinary query, with no
snackbar and nothing to undo.

`TodayScreen` tracks the open task by id and reads the task back out of the
list, so a task deleted from under an open sheet simply stops being found and
the sheet closes with it.

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
- the row's long press carries an `onLongClickLabel`, so the actions menu is
  announced and offered as an action rather than left as an undiscoverable
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

Today's ordering has always had three bands: scheduled for today, then overdue,
then completed. They are now labelled.

The first band carries no label. At the top of the Today screen, today's work
needs no announcement. "Overdue" and "Completed" each get a line of label text
above their group, and nothing else: no divider, no container, no count, not
collapsible. Each band rounds its own corners, so it reads as one collection
rather than a slice of a longer one.

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
- subtasks, projects, areas, recurrence, reminders
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

- the compact pinned app bar, the segmented collection, the empty state, and
  the extended FAB
- Quick Add, creating a task scheduled for today
- completion, stored as `completedAt` and persisted through the repository,
  with undo offered in a snackbar
- the three-band ordering described above
- soft deletion through the long-press actions menu, with undo offered in a
  snackbar
- starting Focus on a task from the long-press actions menu, described in
  `focus.md`
- Task Details, editing title, notes, placement, scheduled date, due date, and
  estimated duration, described in `task-details.md`

Tasks are stored in a Room database owned by `FocuslistApplication`, read
through `TaskRepository`, and derived into Today by `TaskQueries.todayTasks`.
The schema, its versions, and its migrations are described in `storage.md`.

Not built: Areas, Projects, and Settings.

`TaskRowPlayground` remains a temporary harness, separate from this screen.
