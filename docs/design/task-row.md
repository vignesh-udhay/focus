# Task Row

## Purpose

The Task Row is the primary interaction surface in Focuslist.

Its visual and motion specification now lives in `expressive-components.md`,
under "Task row", with the tokens it spends in `expressive-design-system.md`.
Where this document and those disagree, those win.

It represents one actionable task and should make the following immediately
clear:

1. What is the task?
2. When is it relevant?
3. How long might it take?
4. How do I complete it?

The task row should be fast to scan and fast to interact with.

---

# Design Principles

## Calm

A task row is not an independent card floating above the screen.

Structure comes from the collection. A list of tasks is rendered as one
segmented surface, and each row is a segment of that surface rather than a
container in its own right.

Avoid, at the level of the individual row:

- cards
- borders
- shadows
- gradients
- decorative backgrounds

## Scannable

The task title is the dominant element.

Supporting information is visually subordinate.

## Direct

Completing a task should require one obvious interaction.

## Native

Use Material 3 components and interaction patterns where appropriate.

Do not imitate iOS task-management UI.

---

# Anatomy

A standard task row contains:

- completion control
- task title
- optional metadata

Conceptually:

    ○  Finish landing page
       Today · 45 min

The completion control and task title form the primary interaction.

Metadata is optional.

A task without metadata should not reserve unnecessary vertical space.

The row is built on Material 3's `SegmentedListItem` and maps onto its slots:

- `leadingContent`: the completion control
- `content`: the task title
- `supportingContent`: the metadata, omitted entirely when there is none

Do not use `overlineContent` or `trailingContent` by default. A task row
should not display every property a task has.

---

# Layout

The task row should use a horizontal layout:

    [completion] [task content]

The completion control should have an accessible touch target of at least
48dp.

The title should occupy the remaining available width.

Metadata appears below the title when present.

`SegmentedListItem` provides this layout, its content padding, and the
vertical alignment of its slots. Prefer `ListItemDefaults.ContentPadding`
over re-deriving the row's internal padding from spacing tokens.

Use FocuslistSpacing tokens for the spacing the component does not own, such
as the padding around a collection.

Do not introduce arbitrary spacing values unless necessary.

---

# Task collections

A list of tasks is a single segmented surface, not a stack of separate
containers.

Each row is a segment, and its shape depends on where it sits in the
collection:

- the first item rounds its top corners
- the last item rounds its bottom corners
- items in between stay square
- a lone item rounds all four corners

Resolve this with `ListItemDefaults.segmentedShapes(index, count)`. Do not
hand-roll the corner logic.

Separate segments with `ListItemDefaults.SegmentedGap`. Do not use dividers,
borders, or per-row margins to separate tasks.

Take the container color from `ListItemDefaults.segmentedColors()`, or from a
color-scheme role such as `surfaceContainer`, and keep it the same for every
row in the collection. A row must not change its container color to
communicate ordinary state such as completion.

When a screen groups tasks under headers, each group is its own segmented
collection with its own index and count, so each group reads as one surface.

---

# Typography

Task title:

- Material bodyLarge or an equivalent product-defined text style
- normal weight by default
- strong enough contrast against the background

Metadata:

- Material bodySmall
- visually subordinate to the title

Completed task title:

- reduced visual emphasis
- strikethrough may be used
- completion should primarily be communicated through the completion control

Do not make completed tasks visually disappear immediately.

---

# Completion Control

The completion control represents the task's completion state.

States:

- unchecked
- checked
- pressed
- disabled when applicable

The control must have:

- minimum 48dp accessible touch target
- clear checked/unchecked states
- appropriate content description

The checkbox is the leading content of the segment. It owns the completion
toggle. The rest of the row does not.

`SegmentedListItem` also has a toggleable overload that applies
`Role.Checkbox` to the whole row. Do not use it here. It would turn the
entire row into one toggle and erase the distinction between completing a
task and opening it. Use the `onClick` overload and keep a `Checkbox` in
`leadingContent`.

Do not create a custom checkbox unless Material's component cannot provide
the required behavior.

---

# Interaction

## Tap completion control

Tapping the completion control immediately completes the task.

Do not show a confirmation dialog.

The UI should respond immediately.

If the task is accidentally completed, the application should provide an
appropriate undo mechanism.

## Tap task

Tapping the task opens the task details/editing interface.

This is the segment's own `onClick`. Tapping the completion control must not
open details.

## Long press

Long press opens the actions a task supports beyond completing and opening it.
On Today that is a menu with a single item, Delete.

Normal task management must not require it. Completing a task stays one tap on
the checkbox, and opening it stays one tap on the row. Long press carries the
less common and more consequential actions, and the caller decides what those
are: `TaskRow` takes an optional `onLongClick`, and a row with nothing extra to
offer leaves it null.

Whenever `onLongClick` is set, set `onLongClickLabel` with it. The label is
what exposes the action to accessibility services, so TalkBack announces it and
offers it rather than leaving it a gesture a user cannot discover. An
unlabelled long press is not an acceptable way to reach an action.

---

# Completed state

When a task is completed:

1. The completion control changes state.
2. The title transitions to the completed visual state.
3. The row may animate toward its final position/state.
4. The task should eventually leave the active task list according to the
   current screen's behavior.

The transition should feel satisfying but restrained.

Avoid celebratory animations.

Do not use confetti, bursts, or gamification.

---

# Motion

Motion should communicate the state change.

Completion should use a short, responsive transition.

Potential motion:

- checkbox state transition
- title emphasis transition
- subtle row movement/removal

Motion must not delay the user's ability to continue working.

If reduced-motion accessibility preferences are enabled, unnecessary
animation should be minimized.

---

# Pressed state

The entire task row should provide appropriate touch feedback when it is
interactive.

Pressed feedback should use Material interaction/state conventions.

`SegmentedListItem` handles this through its `ListItemShapes`, morphing to
the pressed shape on touch. Let the component do it rather than adding a
separate pressed background.

The segmented container is the collection's surface, not a per-row
affordance. No row should gain a background of its own merely to communicate
that it is interactive.

---

# Metadata

Possible metadata includes:

- scheduled date
- due date
- duration
- project
- reminder

Metadata should only be displayed when it provides useful information.

Do not display every property of a task in the row.

Examples:

    Today · 45 min

    Tomorrow · Work

    Friday · 30 min · Website

Metadata should remain compact.

---

# Density

The default task list should feel comfortable rather than dense.

The task row should have enough vertical space for easy touch interaction and
quick scanning.

Do not optimize for maximum tasks visible on screen.

Optimize for fast comprehension and reliable interaction.

---

# Dark theme

The task row must work with Material 3 dark color roles.

Do not define separate arbitrary dark-mode colors.

Use MaterialTheme.colorScheme, including for the segmented container color.

Avoid excessive contrast between individual task rows.

The list should continue to feel like one coherent surface.

---

# Accessibility

The task row must support:

- TalkBack
- font scaling
- touch target requirements
- semantic state for completion
- meaningful content descriptions

Do not communicate important information through color alone.

Completed state must remain understandable without relying on color.

---

# Adaptive layouts

The task row must work across:

- compact phones
- large phones
- tablets
- expanded window sizes

On wider layouts, use available space without artificially stretching
individual controls.

The task content should remain readable.

---

# Component boundary

Create a reusable TaskRow composable.

The component should receive state and callbacks rather than owning
application-level business logic.

Conceptually:

    TaskRow(
        task = task,
        shapes = ...,
        onToggleComplete = ...,
        onClick = ...
    )

TaskRow remains a reusable task component, used wherever a single task
appears. The collection around it decides the segmentation.

The row does not know how many tasks exist or where it sits among them. The
caller resolves the segment shape with
`ListItemDefaults.segmentedShapes(index, count)` and passes the result in.

The TaskRow should not directly access repositories, databases, or
ViewModels.

---

# Implementation priority

Implement in this order:

1. Basic unchecked row
2. Metadata
3. Checked/completed state
4. Segmented shape within a collection
5. Pressed state
6. Accessibility
7. Motion
8. Adaptive behavior

Do not implement advanced gestures until the basic interaction is excellent.

---

# Implementation status

`TaskRow` is built on `SegmentedListItem` and takes its shapes from the caller,
as this document describes.

It puts the checkbox in `leadingContent` with its own touch target and content
description, the title in the content slot, and the metadata in
`supportingContent`, omitted entirely when there is none. It uses the `onClick`
overload rather than the toggleable one, so completing and opening stay
separate. Callers may override `colors`, and may pass `onLongClick` with an
`onLongClickLabel`.

Motion is limited to the title color transition and the pressed shape morph
that `SegmentedListItem` provides. Adaptive tuning for expanded windows has not
been done.
