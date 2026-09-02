# Components

How each Focuslist component looks and behaves. Read
`expressive-design-system.md` for the tokens this file spends, and
`expressive-motion.md` for anything that moves.

If you are building a new screen, assemble it from these. Do not introduce a
new component because an existing one is nearly right; say what is missing.

---

# What Focuslist uses

[FD] Keep and refine, never replace:

    SegmentedListItem      Checkbox        MediumExtendedFloatingActionButton
    NavigationBar          DropdownMenu    ModalBottomSheet
    DatePickerDialog       OutlinedTextField                Snackbar
    TopAppBar              PrimaryTabRow   Button / TextButton / OutlinedButton

[FD] Do not introduce, unless a later product decision explicitly requires one:

    FloatingToolbar        SplitButton     ButtonGroup
    MaterialShapes polygons                shape morphing
    navigation drawer      bottom sheet scaffold           cards

[M3] The first three are real Material 3 Expressive components. They are
excluded because Focuslist has no use for them, not because they are unsound.

---

# Task row

The most repeated element in the app, and the one worth the most care.

[IMPL] `TaskRow` wraps `SegmentedListItem`. `TaskListRow` wraps `TaskRow` with
metadata derivation and the long-press menu. Keep both, and keep the segmented
collection structure: `ListItemDefaults.segmentedShapes(index, count)`,
`segmentedColors`, and `SegmentedGap` between rows.

## Anatomy

[FD] Checkbox leading, title, optional metadata line beneath. Nothing else. No
trailing icon, no drag handle, no chevron, no avatar.

| Element | Role | Colour |
| --- | --- | --- |
| Title | `bodyLarge` | `onSurface` |
| Metadata | `bodySmall` | `onSurfaceVariant` |
| Overdue date within metadata | `bodySmall` | `error` |

[FD] Exactly two levels of hierarchy in a row. A third would compete with the
title, and the title is the thing the user is scanning for.

[IMPL] The row carries a minimum height from `FocuslistDimensions`. A floor,
not a height: a row with metadata or a wrapped title is already taller and
grows past it. It exists so a bare one-line row cannot come out shorter than
its neighbours and leave the collection looking ragged.

[FD] Metadata segments are joined with a middot. A row with no metadata emits
no supporting content and reserves no space for it.

## Title

[FD] Two lines maximum, then ellipsize. A long title must not be able to push
the rest of the list around, and a row that grows to six lines stops being a
row.

[FD] Metadata wraps naturally onto a second line rather than truncating. It is
short, and truncating a date is worse than wrapping it.

## States

[FD]

| State | Treatment | Non-colour cue |
| --- | --- | --- |
| Default | `onSurface` title on `surfaceContainer` | |
| Pressed | Material ripple and state layer | |
| Completed | `onSurfaceVariant` title, strikethrough | the strikethrough |
| Overdue | date rendered in `error` | the date reads as a past date |
| Scheduled today | date reads "Today" | the word itself |
| Focused | **no treatment at all** | |

**Completed** carries strikethrough as well as colour, so the state survives
both colour blindness and a greyscale screenshot.

**Overdue** is safe for the same reason without extra work: the metadata for an
overdue task shows an actual date such as "Aug 31" where a current task shows
"Today". The `error` colour is a second cue on top of a distinction that is
already textual.

**Focused rows get nothing.** Focus is a single-task execution mode, not a
selection state on a list. Marking the focused row would make Focus look like
list selection and would contradict a settled product decision. See `focus.md`.

## Interaction

[FD] Tap opens Task Details. Long press opens the actions menu. The checkbox
toggles completion and is not part of the row's click target.

[FD] The row does not change shape or size when pressed. Material's ripple and
state layer are the entire press feedback. A springing row in a list of twelve
is noise, not delight.

## Motion

[FD] Title colour and strikethrough use `stateColor`. The checkbox uses
`completion`. Rows entering, leaving and moving use `listChange`. No other
animation.

## Accessibility

[IMPL] All of this already exists and must be preserved:

- Checkbox `contentDescription`: "Mark X complete" / "Mark X not complete".
- Row click label: "Open task details". `SegmentedListItem` takes a long-press
  label but no click label, so this is applied through a `semantics` modifier
  that names the existing action rather than replacing it.
- Long-press label: "Show task actions".
- Checkbox touch target at least 48dp.

## Large font scales

[FD] The row grows vertically. The two-line title cap prevents runaway.
Metadata wraps. The checkbox stays 48dp.

---

# Task actions menu

[IMPL] A `DropdownMenu` anchored to the row, opened by long press, holding
transient state in the row itself.

[FD] Focus first where it is offered, then Delete. Constructive before
destructive, so the thumb does not land on Delete. Delete is labelled in
`error`.

[FD] Focus appears only on Today rows. The Focus queue is derived from Today,
so anywhere else the action would either do nothing or have to schedule the
task for today, and neither is specified behaviour.

---

# Checkbox

[IMPL] Material `Checkbox`, unchanged.

[FD] Its state change is the strongest expressive moment in the app and uses
the `completion` token. This is the only component with that privilege.

---

# Top app bar

[IMPL] `FocuslistTopAppBar`, one component for every screen, wrapping
`TopAppBar`. Callers pass a title and, where the screen has something to scroll
under the bar, a `pinnedScrollBehavior`. Focus passes none because there is
nothing to scroll, and the placement screen passes none because the tabs below
the bar have to stay reachable.

[FD] Title in `titleLargeEmphasized`, carrying heading semantics. No actions,
no navigation icon: every destination is reachable from the navigation bar, so
there is nothing for an app bar action to do that the bar does not already do.

[IMPL] Shared rather than repeated because those two properties are always
applied together and six copies drifted apart on both. Do not build a bar by
hand; if a screen needs something this cannot express, say so.

---

# Navigation bar

[IMPL] `NavigationBar` on every screen, with Today, Inbox, Focus and More.

[FD] On every screen rather than only the primary ones, so no list is a dead
end.

[FD] More is a menu, not a destination. It shows as current while the user is
on one of its destinations.

[IMPL] Selection uses the `NavigationBarItem` default and **must not be
overridden**: `secondaryContainer` for the indicator, `onSecondaryContainer`
for the icon, `secondary` for the label, `onSurfaceVariant` for both parts of
an unselected item.

[M3] This is the documented Material 3 role set. The Material Components for
Android navigation bar guidance specifies `colorSecondaryContainer` for the
active indicator and `colorOnSecondaryContainer` for the active icon. Compose's
`NavigationBarTokens` agrees, and colours the active label `secondary` where
the Views implementation uses `onSurface`; Compose's choice is the closer match
to shipping Material products.

[FD] **Do not move this to the primary family.** It was tried and reverted, and
the reasoning is recorded here so it is not retried:

The apparent problem was that the selected pill looked grey rather than brand
coloured next to the floating action button. Measured in CIELAB against a
Google Play reference, that was wrong. Play's pill is L\* 89.8, C\* 16.8, hue
247; Play's primary button is L\* 40.2, C\* 72.5, hue 292. Forty-four degrees
of hue apart and four times the chroma: they are different palettes, and the
pill's chroma of about 16 is exactly what Material's tonal scheme generates for
secondary. Focuslist's own default pill measured C\* 12.1, already close to the
reference. Switching it to `primaryContainer` raised chroma to 28 and dropped
the tone from 90 to 82, moving it *away* from the reference on both counts.

The earlier measurement that suggested otherwise used `max(RGB) - min(RGB)` as
a saturation proxy, which overstates chroma badly for light colours. Use
CIELAB C\*.

[FD] A selected navigation item is a **marker** and the floating action button
is an **action**, and they are deliberately drawn from different palettes. The
marker recedes into the bar; the button is meant to be the most prominent thing
on the screen. See the floating action button section.

[FD] Where an icon has a filled and an outlined variant, selected uses filled
and unselected uses outlined, so selection is not conveyed by the container
alone.

[IMPL] Today, Inbox and Focus each have both variants and switch between them.
More keeps one icon in both states: three dots have no filled counterpart, and
drawing one would be inventing a symbol rather than using a pair Material
already defines. The rule is conditional for exactly this reason.

[FD] Each destination in the More menu carries a leading icon, in the same
outline style and at the same weight as the bar's. The menu is where the four
secondary lists are chosen between, and four lines of bare text are harder to
pick from than four that each look like something.

---

# Floating action button

[IMPL] `AddTaskFab`, one component wrapping `MediumExtendedFloatingActionButton`,
on Today and Inbox only. Both screens previously wrote the same button out in
full; they now call this.

[FD] The container is the **Material default**, `primaryContainer` with
`onPrimaryContainer` content. `AddTaskFab` names no colour.

[IMPL] It was briefly overridden to `primary`, to make the button strong in
light and pale in dark the way a base role inverts. That has been reverted.

[M3] Material documents no base-role floating action button. The regular button
has Primary, Secondary, Tertiary and Surface styles and the extended button has
the same set; the one *named* Primary is `colorPrimaryContainer` with
`colorOnPrimaryContainer`, and every other variant is a container role too.
Nothing in the specification maps a floating action button to `colorPrimary`.

[FD] The consequence is worth stating plainly rather than discovering again:
the button is pale on a light screen and dark on a dark one, and it is
therefore never the highest-contrast element on the page. That is what Material
intends for it. If the product ever decides it must dominate, that is a
deliberate departure from the specification and should be recorded as one.

`MediumExtendedFloatingActionButton` defaults to `primaryContainer`, a container
role: tone 90 in light and tone 30 in dark. That makes the button pale on a pale
screen and dark on a dark one, so it never becomes the most prominent thing in
either theme. `primary` is a base role and inverts the way a prominent action
should, tone 40 in light and tone 80 in dark. Capture is the primary action on
both screens it appears on, so it takes the primary role.

[IMPL] Pass only `containerColor`. `contentColorFor` maps `primary` to
`onPrimary` by itself, and naming the content colour would be a second place for
it to be wrong.

[FD] Keep the current behaviour exactly. It does not collapse or expand on
scroll for now. Lists must reserve clearance beneath their last row so the
button never covers a task; that clearance is a dimension token, not a number
written into a screen.

---

# Empty states

[IMPL] `TaskListEmptyState`: a centred column of two lines. Material 3 has no
empty-state component.

[FD] Headline in `titleMediumEmphasized`, supporting line in `bodyMedium` and
`onSurfaceVariant`. No illustration, no icon, no action button.

[FD] The copy is plain. An empty list is not an achievement, and nothing here
congratulates the user or decorates the absence.

---

# Section labels

[FD] New with the Today redesign. `labelLarge` in `onSurfaceVariant`, above the
group it names, with `md` beneath it and `lg` above it.

[FD] A label is a label and nothing more: no divider, no background, no
container, no chevron, no count badge, not collapsible.

---

# Sheets

[IMPL] `ModalBottomSheet` for both Quick Add and Task Details, with only the
Hidden and Expanded states enabled: neither has a half-height state worth
stopping at.

[FD] Standard Material scrim, drag handle and corner treatment. Sheet motion is
the Material default.

[FD] A sheet holds a draft. Nothing is written until the user confirms, so
dismissing leaves the task exactly as it was.

[FD] Sheet content scrolls. Six fields do not fit at large font scales, and a
confirming action that cannot be reached is a broken screen.

## Quick Add

[FD] One field and one action. Do not add a second field, a date picker, or a
placement control. Capture should require almost no decisions.

[FD] The field reads a day off the end of the title and marks it: the matched
words take `primary`, and a supporting line under the field names the resolved
date. That is one field still, not two — the marking and the line are feedback
on what was typed, not somewhere else to type.

[FD] The colour is never the only signal. A screen reader cannot announce it
and not everyone sees it, so the supporting line carries the same fact in text.
A rewrite the user cannot see is one they cannot correct. See
`date-parsing.md`.

## Task Details

[FD] Six fields: title, notes, placement, scheduled date, due date, estimated
duration. Completion and deletion are deliberately absent; they have their own
interactions, and editing must not quietly finish or remove a task.

[FD] Validation shows in the field, not on the button: an invalid field is
marked with the Material error treatment and carries supporting text saying
what is wrong. The confirming action is disabled while any field is invalid.

---

# Inputs

[IMPL] `OutlinedTextField` throughout.

[FD] Every field carries a label. A field with a constrained format carries a
placeholder showing the format. Errors use the Material error treatment plus
supporting text; never colour alone.

[FD] A single-line field's placeholder is capped to one line, so an empty field
is never taller than a filled one at large font scales.

[FD] A date field is the value: an existing date is written into it as text,
and an empty field means no date. Typing and the calendar picker both write the
same field.

---

# Snackbar

[IMPL] One undo offer for the whole app, shown with `SnackbarDuration.Long`,
through `UndoSnackbarHost`. Every screen hosts that rather than a bare
`SnackbarHost`, so no screen can be the one that forgets.

[FD] Material default appearance and motion. Undo is the only action.

[FD] It exposes a polite live region, so the offer is announced when it arrives
instead of waiting to be found. Undo is time limited, and an offer nobody hears
is not an offer. Polite rather than assertive: completing a task is the user's
own doing, and interrupting a screen reader over it would be rude about
something that is not urgent.

---

# Focus screen

[FD] A primary destination whose content is one task, not a list. Two states;
the product behaviour is in `focus.md`.

## Ready

| Element | Treatment |
| --- | --- |
| Task title | `headlineMediumEmphasized`, `onSurface`, centred, heading semantics |
| Estimate | `bodyLarge`, `onSurface`, centred, under the title, omitted when absent |
| Start | one filled `Button` |
| Complete | one `TextButton` under it |
| Everything else | absent |

[FD] Two actions rather than one, and the weights say which is which. Starting
is the constructive act and is filled; completing without a session is the
shortcut and is text.

## Session

| Element | Treatment |
| --- | --- |
| Shape | `Morph(Circle, Clover4Leaf)`, `primaryContainer`, square, capped at 320dp |
| Task title | `headlineMediumEmphasized`, `onPrimaryContainer`, centred, max 4 lines |
| Estimate | `bodyLarge`, `onPrimaryContainer`, under the title |
| Complete | one filled `Button` under the shape |
| Stop | `IconButton` with `ic_close`, top start |
| Next task | `bodyMedium`, `onSurfaceVariant`, bottom, one line of text |
| Top app bar | absent |
| Navigation bar | absent |

[FD] The shape is drawn, not clipped to. A `Shape` would have to be a new
object every tick to change, which puts the work in layout; drawing reads the
progress in the draw phase, where a changed value costs one redraw of one node.

[FD] Capped at 320dp so a wide window gets a shape, not a wall.

[FD] The title is the one piece of text in the app with a hard line cap. A
fixed square cannot grow to fit, and a title that overruns it is cut through
the middle of a line, which reads as broken rather than as shortened. Four
lines is what the square holds at 200% font scale.

## Both

[FD] Absent on purpose in both states: metadata beyond the estimate, editing,
Task Details, a countdown or elapsed clock, capture, a floating action button.
`PRODUCT.md` asks Focus to remove distractions, and every affordance left out is
one that would work against that.

[FD] The navigation bar stays in Ready and goes in Session. Focus is reached
from the bar, and a destination that hides the control used to open it is a
trap; a mode the user started, and can stop, is not. Session therefore has to
carry a visible exit, because gesture navigation draws no back affordance.

[FD] When the focused task changes, the title changes with `stateColor` and the
button does not move. No celebration. The reward is the next task appearing.

[FD] The shape morph is the one place Focuslist takes Material 3 Expressive's
shape morphing, and it is conditional: it advances against the task's estimate
or it does not move. See `expressive-motion.md`.

---

# Dialogs

[IMPL] `DatePickerDialog` is the only dialog in the app.

[FD] Keep it that way. `PRODUCT.md` says to avoid confirmation dialogs for
low-risk reversible actions, and undo covers those instead.

---

# Segmented controls

[IMPL] `SingleChoiceSegmentedButtonRow` for placement, `PrimaryTabRow` for the
Anytime and Someday tabs.

[FD] Segmented controls are for small, mutually exclusive, equally weighted
choices. Three options is the practical limit.

[FD] A segmented row must not overflow at large font scales, and must not
truncate a label to avoid doing so.

[IMPL] The placement row scrolls sideways when it cannot fit. Its minimum width
is the width of the field, so at ordinary font scales the three buttons divide
that exactly as before and there is nothing to scroll. At 200% three labels no
longer fit across a phone, and the row grows to the width its content needs
rather than clipping Someday out of reach.

[FD] Scrolling in preference to wrapping. Segmented buttons are joined, and
their start, middle and end shapes only read as one control on one line;
wrapping would break the shape into pieces that no longer look joined.

[FD] This is ours, not Material's. The Material Components documentation for
toggle button groups says nothing about what to do when labels do not fit, and
the tabs documentation describes scrollable tabs without giving any rule for
when to prefer them. Scrolling is the pattern Material *offers* for a row of
choices that overflows; choosing it here is a Focuslist decision and should not
be quoted as guidance.

Nothing about the interaction changes: all three options stay selectable, the
selected one keeps its check, and no label is ever truncated.

---

# Icons

[IMPL] Hand-written 24dp vector drawables, one per navigation destination.

[FD] Material Symbols outline style, 24dp, tinted from the colour scheme and
never given a hardcoded fill.

[FD] A navigation icon is decorative when its label sits beside it: the label
names the destination and a content description would only repeat it. An icon
that is the whole control, such as a picker trigger, always carries a content
description.

[FD] No icon containers, no coloured icon backgrounds, no expressive icon
treatments.

---

# Component states

[FD] Where each state comes from. "Material" means the component's own
treatment, unmodified.

| Component | Default | Pressed | Selected | Disabled | Error | Completed |
| --- | --- | --- | --- | --- | --- | --- |
| Task row | `surfaceContainer` | Material ripple | n/a | n/a | n/a | strikethrough + `onSurfaceVariant` |
| Checkbox | Material | Material | Material checked | Material | n/a | checked, with `completion` motion |
| Navigation item | Material | Material | Material + filled icon | n/a | n/a | n/a |
| Button | Material | Material | n/a | Material | n/a | n/a |
| Text field | Material | Material | Material focused | Material | Material + supporting text | n/a |
| Menu item | Material | Material | n/a | Material | `error` label for Delete | n/a |
| FAB | Material | Material | n/a | n/a | n/a | n/a |

[FD] There is no loading state anywhere. Tasks come from a local database that
emits quickly enough that none has been designed. If one is ever needed it is a
design decision, not something to improvise.
