# Components

How each Focuslist component looks and behaves. Read
`expressive-design-system.md` for the tokens this file spends, and
`expressive-motion.md` for anything that moves.

If you are building a new screen, assemble it from these. Do not introduce a
new component because an existing one is nearly right; say what is missing.

---

# What Focuslist uses

[FD] Keep and refine, never replace:

    SegmentedListItem      Checkbox        FloatingActionButton
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
metadata derivation. Keep both, and keep the segmented
collection structure: `ListItemDefaults.segmentedShapes(index, count)`,
`segmentedColors`, and `SegmentedGap` between rows.

## Why the board builds its rows rather than instancing the kit

[FD] The four row components on the board — Task row, Plan row, Settings
navigation row, Settings toggle row — are built from primitives. Only the leaf
atoms come from the M3 kit: the checkbox, the radio button, the chevron.

This is written down because "why isn't this a kit instance" is a reasonable
question with a non-obvious answer, and guessing at it once already produced a
wrong one.

[M3] **The kit does have a segmented list.** It is not a component of its own,
which is why searching the library by name finds nothing: it is
`Type = Segmented (filled)` on the kit's `List` set, a container of ten
`List item` instances at a 2dp gap. That 2dp is the same gap these rows use, so
it is a useful confirmation of the spacing.

[FD] What it does not express is the anatomy. The kit's `List item` offers
`Trailing` as None, Check Box, Icon, Radio Button or Switch. A task row's
trailing is a duration string and an icon button together, which is not among
them. The `Content` slot could hold one, but at that point the kit is supplying
a container and everything inside it is ours regardless.

[FD] The four rows also differ from one another in ways a single kit variant
would have to be overridden for: 72dp for the two-line task row, 56dp for the
one-line Plan row, 72dp for a Settings row carrying supporting text, 56dp for a
radio row.

[IMPL] The deciding reason is that the code does not use a kit approximation
either. `TaskRow.kt` calls `SegmentedListItem` with
`ListItemDefaults.segmentedShapes(index, count)`, which is real Compose M3
Expressive. The board's job is to describe what the app renders, and a
hand-built row matching it is closer to the truth than an instance of something
adjacent.

## The four rows share a base, and it is variables rather than a component

[FD] The obvious shared base, a container component the four compose, cannot be
built here. It needs a content slot, and slots cannot be created through the
Figma plugin API. What can be shared is the values, and the values are what
drifted.

    Segmented/Radius outer   →  Corner/Large        16
    Segmented/Radius inner   →  Corner/Extra Small   4
    Segmented/Gap            →  Spacing/2            2

[IMPL] All four sets bind every corner of every variant to the two radius
variables, 240 bindings across 60 variants, and every frame that groups rows
binds its `itemSpacing` to the gap.

[FD] They are aliases onto the existing scale rather than new numbers. The scale
stays the single source of the values; these say which of them the segmented
treatment uses. That is the difference that matters: binding the rows straight
to `Corner/Large` would stop anyone typing 28, but moving the treatment to 20dp
would still mean finding all four again. One edit now.

[FD] The surface needed nothing. All four were already bound to
`surfaceContainerLow`.

[FD] **What this does not protect is variant structure.** A variable cannot make
one component carry the same states as another, and that half of the drift stays
manual: the Plan row shipped no Pressed or Focused while the Settings row had
both, which is how it also ended up at 28dp on `surfaceContainerHigh` without
anyone noticing. Enabled, Pressed and Focused is the set. Hovered, Dragged and
Disabled exist in the kit's `List item` and are deliberately unused.

## Anatomy

[FD] Checkbox leading, title, optional metadata line beneath, and the duration
at the end. Nothing else: no drag handle, no chevron, no avatar, and **no
trailing button**.

[FD] A trailing actions button was added once and D-023 removed it. It had
reversed this file's own "nothing else, no trailing icon" on the grounds that
Delete and Focus were reachable only by a gesture, which `PRODUCT.md` forbids as
the sole route. Task Details now holds both, so the gesture is no longer alone
and the rule it broke stands again.

| Element | Role | Colour |
| --- | --- | --- |
| Title | `bodyLarge` | `onSurface` |
| Metadata | `bodySmall` | `onSurfaceVariant` |
| Overdue date within metadata | `bodySmall` | `tertiary` |

[FD] Two levels of *text* hierarchy in a row. Anything that wants room in a row
is competing with the title for it, which the removed trailing button proved:
it pushed two of five seeded titles onto a second line until the screen margin
was returned to `md`.

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
| Overdue | date rendered in `tertiary` | the date reads as a past date |
| Scheduled today | date reads "Today" | the word itself |
| Focused | **no treatment at all** | |

**Completed** carries strikethrough as well as colour, so the state survives
both colour blindness and a greyscale screenshot.

**Overdue** is safe for the same reason without extra work: the metadata for an
overdue task shows an actual date such as "Aug 31" where a current task shows
"Today". The colour is a second cue on top of a distinction that is already
textual, which is what makes `tertiary` affordable where `error` used to sit.
See `expressive-design-system.md` for why it moved.

**Focused rows get nothing.** Focus is a single-task execution mode, not a
selection state on a list. Marking the focused row would make Focus look like
list selection and would contradict a settled product decision. See `focus.md`.

## Interaction

[FD] Tap opens Task Details. The checkbox toggles completion and is not part of
the row's click target. There is nothing else: D-023 removed the actions menu
and the long press that opened it.

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
- Row click label: "Open task details". `SegmentedListItem` takes no click
  label, so this is applied through a `semantics` modifier that names the
  existing action rather than replacing it.
- Checkbox touch target at least 48dp.

## Large font scales

[FD] The row grows vertically. The two-line title cap prevents runaway.
Metadata wraps. The checkbox stays 48dp.

---

# The row has no actions menu

[FD] `docs/decisions.md` D-023 removed the trailing button and the long-press
menu. Tapping a row opens Task Details, and everything the menu carried lives
there: rescheduling through the Plan rows, Start focus as the primary action,
Delete in the overflow.

[FD] **The menu existed for a reason that three later decisions removed.** This
section used to justify the trailing button by saying Delete and Focus "live
only in the actions menu, Task Details deliberately excludes both". D-018 gave
Task Details a Start focus, D-022 gave it Delete, and its Plan rows give it
rescheduling. Nothing was excluded any more, and the button had outlived its
premise without anyone going back to check.

[FD] It had also become a worse duplicate. The menu offered Today, Tomorrow and
Pick a date; the Scheduled sheet offers No date, Today, Tomorrow, This weekend
and Choose a date, and cannot be beaten by a subset that has no way to clear a
date.

[FD] Three of the menu's five items were rescheduling, which is administration,
and a permanent trailing button gave the most administrative action the most
prominent position on every line of every list. `PRODUCT.md` principle 4 asks
for the opposite. The width was real too: adding the button pushed two of five
seeded titles onto a second line until the screen margin was returned to `md`.

[FD] What it costs is a tap. Rescheduling from a list is three rather than two,
and rows no longer answer a long press. Nothing became unreachable. D-023
records the trade and names what would reverse it: if this proves too slow, the
answer is a bottom sheet on long press, which is where Material's compact
guidance points for a five-item menu, not the button returning.

[FD] Two rules from the old menu survive it, because they are about the product
rather than the control.

**There are no triage actions.** The row menu once carried Move to Anytime and
Move to Someday, filing a task into one of two undated lists. Both lists were
removed on evidence, D-002, and the axis behind them went with schema version 9.
What is left is the decision those actions were working around: give the task a
day, or do not.

**Never a move back to Inbox.** Inbox means undated, so removing a task's day is
what puts it there, and a separate control saying the same thing twice would be
one more way to express one decision.

---

# Checkbox

[IMPL] Material `Checkbox`, unchanged.

[FD] Its state change is the strongest expressive moment in the app and uses
the `completion` token. This is the only component with that privilege.

---

# Top app bar

[IMPL] `FocuslistTopAppBar`, one component for every list screen, wrapping the
compact M3 `TopAppBar` at 64dp. Callers pass a title and, on the three primary
destinations, the overflow. It is pinned: no scroll behaviour, no collapse.

[FD] The title carries heading semantics and no style of our own. The component
supplies `titleLarge`.

## There is no subtitle

[FD] D-020 removed it, and with it the 152dp `LargeFlexibleTopAppBar` the
subtitle was the only justification for. `today-screen.md` carries the reasoning,
and the rule that matters if anyone reopens it: the subtitle and the height are
one decision and move together or not at all.

Most of this section used to be about subtitles. That Today spent one on two
facts at once, the date and a planned total, told apart by alignment rather than
by a container. That a right-aligned subtitle needed `xxs` end padding, because
Material insets the title area 16dp at the start and 4dp at the end, landing the
text 1.5dp outside the edge the rows end on. That Inbox's count earned one while
Upcoming's did not. That the bars were therefore different heights, and that this
was correct rather than drift.

D-017 removed the total, D-020 removed the date, and Inbox's count went with
them. All four bars are 64dp now and none has a subtitle.

[FD] One line survives, because it is the rule that would govern a subtitle
coming back: **a subtitle has to say something the list below it cannot, or it
does not get one.** Upcoming already failed that test, its count being of tasks
already grouped under their own day headings.

## The overflow is the only action

[FD] On Today, Inbox and Upcoming, and nowhere else. A standard icon button
rather than a filled one, opening Logbook, Reminder health and Settings.
`navigation.md` holds the rule: reaching the other lists is the navigation bar's
job, and the overflow carries what the navigation bar does not.

An earlier version of this section read "No actions, no navigation icon: every
destination is reachable from the navigation bar, so there is nothing for an app
bar action to do that the bar does not already do." That was written while More
was a bar item. `navigation.md` removed More and moved what sat behind it into
this overflow, so the premise is gone.

[FD] Rooms take a back arrow and no overflow instead, through
`Focuslist / Room header`. A screen wears the bar and an overflow, or a back
arrow and no bar, never both.

## Colour and sharing

[FD] The bar names no colour. Material's default is `surface`, and the page is
`surface`, so bar and page are one ground and the collection is the only thing
on it. An earlier override existed only because the page had been moved onto a
container role; it went when the page came back.

[IMPL] Shared rather than repeated because these properties are always applied
together and six copies drifted apart on all of them. Do not build a bar by
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

[IMPL] `AddTaskFab`, one component wrapping `FloatingActionButton`, on Today and
Inbox only. Both screens previously wrote the same button out in full; they now
call this.

[FD] The container is the **Material default**, `primaryContainer` with
`onPrimaryContainer` content. `AddTaskFab` names no colour.

[M3] Material documents no base-role floating action button. The regular button
has Primary, Secondary, Tertiary and Surface styles and every one of them is a
*container* role; the one named Primary is `colorPrimaryContainer` with
`colorOnPrimaryContainer`. Nothing in the specification maps a floating action
button to `colorPrimary`.

[IMPL] It was briefly overridden to `primary`, to make it strong in light and
pale in dark the way a base role inverts. That was reverted, and the consequence
is worth stating rather than rediscovering: the button is pale on a light screen
and dark on a dark one, and it is therefore never the highest-contrast element
on the page. That is what Material intends. If the product ever decides it must
dominate, that is a deliberate departure from the specification and should be
recorded as one.

[FD] Regular rather than extended. The button was
`MediumExtendedFloatingActionButton`, which spent 80dp of the content column
carrying the words "Add task" and repeated, in text, what a plus on a task
screen already says. At 56dp it clears more of the list. The label survives as
the icon's content description, so nothing is lost to a screen reader, and the
semantics test asserts it there rather than as visible text.

[FD] The glyph is a bare plus. The design's own is `add_circle`, which draws a
circle inside the button's own circle; Material pairs this container with a
plain glyph, because the container is already the circle.

[FD] Keep the current behaviour exactly. It does not collapse or expand on
scroll. Lists must reserve clearance beneath their last row so the button never
covers a task; that clearance is `FocuslistDimensions.FabClearance`, a dimension
token composed from the spacing scale, not a number written into a screen.

---

# Empty states

[IMPL] `TaskListEmptyState`. Material 3 has no empty-state component, so this is
Focuslist's own: a 24dp inset, an 80dp expressive icon container, a 12dp gap,
then a centred headline and supporting line.

[FD] Headline in `titleMediumEmphasized`, supporting line in `bodyMedium` and
`onSurfaceVariant`.

[FD] An earlier version of this section ended "No illustration, no icon, no
action button". All three have since arrived and the section did not keep up.
The component carries an icon container; the board has an illustrated variant per
screen carrying the mascot, drawn but not yet exported; and the error tone is
paired with an action.

[FD] Two tones. `Neutral` explains an empty collection. `Error` explains a read
that failed, and is the only one that takes a button beside it: a separate M3
medium 56dp Try again, sitting below the component rather than inside it.

## No container, in any state

[FD] Empty and error states sit directly on the background. There is no card, no
surface, and no elevation.

[FD] The plain variants used to draw one: a 28dp radius on `surfaceContainerLow`
with 24dp of padding. The illustrated variants never did, so the app had two
empty-state treatments and the thing that decided between them was whether a
mascot had been drawn for that screen yet. That is invisible to a user and it
made the Logbook's empty state look unlike every other empty state in the app.

[FD] The card went rather than spreading. A card is a container for related
content, and an empty state is the absence of content, so a surface drawn around
nothing is decoration around nothing. Removing it also cost 48dp of padding that
was only ever inseting content from a card edge, and it lets the copy use the
full 380dp column the illustrated variants already use.

[FD] **An error still reads as an error without one.** Its icon container takes
`errorContainer` with the glyph on `onErrorContainer`, and it is the only state
carrying an action button. Those two are the signal; the card was adding nothing
the colour was not already saying.

[IMPL] The icon is the screen's own, outlined, in `onErrorContainer`: `today`,
`inbox`, `schedule`. Upcoming had drifted on both counts, bound to `onSurface`
and swapped to a filled `schedule`, which rendered a near-black disc on a pink
container while the other two were dark red outlines.

[FD] The copy is plain. An empty list is not an achievement, and nothing here
congratulates the user or decorates the absence.

## Error copy says what did not happen

[FD] The headline names the read that failed. The supporting line says the data
is intact, because that is the question the user actually has:

    Couldn't load your tasks
    Your tasks are safe. This is a read that failed.

    Couldn't load your Logbook
    The record is safe. This is a read that failed.

[FD] **It must not mention a connection.** Inbox and Upcoming both read "Check
your connection and try again", and that was wrong in a way worth recording.
Focuslist has no accounts, no cloud sync and no backend, and `PRODUCT.md` puts
all three permanently out of scope. Every read is local. Telling users to check
their connection sends them to fix something that was never involved, and it
implies the app has a server it does not have. This is the same failure as the
Logbook's removed summary card: interface text asserting something untrue about
the system.

[FD] The Logbook's wording differs on purpose. It is the screen that exists to
make completing a task safe, so a user who cannot see their finished work has a
specific and reasonable fear. "The record is safe" answers that fear rather than
the generic one.

---

# Section labels

[IMPL] One `SectionLabel`, shared. `labelLarge` in `onSurfaceVariant`, above the
group it names, with `md` beneath it and `lg` above it. Today names bands,
Upcoming names days, and both are the same kind of heading over the same kind of
collection, so they cannot be allowed to drift.

[FD] Sentence case, everywhere. The design draws Today's bands in capitals and
Upcoming's dates in sentence case; one of the two had to give, and shouting a
word the user is not reading is the one worth losing.

[FD] A label is a label and nothing more: no divider, no background, no
container, no chevron, no count badge, not collapsible.

[FD] Where a heading names a day, the rows beneath it do not. Upcoming groups by
date, so its rows drop the date from their metadata and show the duration and
the recurrence instead; repeating the day on every row would say the same thing
twice and spend width the rest of the line needs. `TaskListRow` takes
`showDate` for this, defaulting to true, because every other list wants it.

---

# Sheets

[IMPL] `ModalBottomSheet` for Quick Add and for each of the sheets Task
Details' Plan rows open, with only the Hidden and Expanded states enabled: none
of them has a half-height state worth stopping at.

[FD] Standard Material scrim, drag handle and corner treatment. Sheet motion is
the Material default.

[FD] **A sheet used to hold a draft**, and Task Details was the reason that rule
existed: nothing was written until Save, so dismissing left the task exactly as
it was. D-018 removed the draft along with the Save, and records it as the price
rather than pretending it is free. Quick Add still holds one, because a capture
that has not been confirmed is not a task yet.

[FD] Content scrolls. Six fields do not fit at large font scales, and an action
that cannot be reached is a broken screen.

## Quick Add

**Extended by D-011.** A trailing time is now read as well as a day, and when
one is read the sheet shows a single dismissible Reminder chip. The rule below
against a second way to set a date is kept, and is why the day still has no
chip. The reminder chip is not a second setter: nothing else in the sheet sets
a reminder, and dismissing it unmarks the same run in the field, so there is
one mechanism rather than two. The sheet also lost its heading; the FAB that
opens it already says "Add task".

[FD] One field and one action. Do not add a second field or a date picker.
Capture should require almost no decisions.

[FD] The field reads a day off the end of the title and marks it: the matched
words take `primary`, and a supporting line under the field names the resolved
date. That is one field still, not two — the marking and the line are feedback
on what was typed, not somewhere else to type.

[FD] The placeholder is an example of exactly that, and it is there to teach
it. Nothing else on the sheet says a date can be typed, so without one the
field looks like a plain text box and the feature is found by accident or never.
The design this screen was drawn from solved the same problem with a row of
Today / Tomorrow / No date chips; those were declined, because they would be a
second way to set a date alongside the one the field already has, and the two
would need a precedence rule the user cannot see — a title ending in "tomorrow"
with **Today** selected has to resolve somehow, and neither answer is
defensible. An example costs nothing and adds no second mechanism.

[FD] The colour is never the only signal. A screen reader cannot announce it
and not everyone sees it, so the supporting line carries the same fact in text.
A rewrite the user cannot see is one they cannot correct. See
`date-parsing.md`.

## Task Details

**Superseded by D-018.** This section described a two-page bottom sheet holding
a draft: a Details page and a Schedule page swapping inside one
`ModalBottomSheet`, a summary row reading "Today · 45 min · Daily", a typed due
date, a `BackHandler` on the second page, no Reminder row, completion and
deletion deliberately absent, and a confirming action disabled while a field was
invalid. None of it survives. `task-details.md` holds the current design.

What follows is the visual treatment only.

[FD] A full screen, not a sheet. Three regions on one scrolling column:
identity, plan, action.

| Element | Treatment |
| --- | --- |
| App bar | back arrow, no title, no overflow |
| Title | Headline Small, `onSurface`, borderless, four-line cap, heading |
| Notes | Body Large, `onSurfaceVariant`, borderless, several lines |
| Section label | the shared `SectionLabel`, reading "Plan" |
| Plan row | 56dp, `surfaceContainerLow`, 16dp outer and 4dp inner, 2dp gap |
| Start focus | full width, 56dp, the screen's only accent |

[FD] **No card around the identity region**, and that is the fix rather than the
omission. A tinted container read as a summary, which is exactly why the two
most-edited fields on the screen looked read-only. A Material text field brings
its own container, so putting real fields inside a card nests one in another.

[IMPL] The fields are borderless: every `TextFieldDefaults` colour is
transparent except the cursor, which is what says the text can be typed into.
They commit on blur rather than per keystroke, and the field is the draft until
focus leaves it.

[FD] The rows are the row family's Plan variant, which the section above
specifies and binds to the shared radius and gap variables. Each shows its value
rather than naming the sheet it opens: `Due date  None` says what can be set,
and an empty field does not.

[FD] **Unset values are not styled differently.** `None` and `Doesn't repeat`
render exactly like `Today` and `45m`. `task-details.md` carries the argument in
full, including the reason there is no token for it: the next step down from
`onSurfaceVariant` is `outline`, which on `surfaceContainerLow` is about 3.8:1
and fails AA at 14sp.

[FD] Durations read through `DurationLabel`: `45m`, `1h`, `1h 30m`. Never
"45 min", which nothing else in the app says.

## The sheets the rows open

[FD] Both date sheets are one shape: a title, the clear option full width, three
day presets in a 2x2 grid, then a full-width Choose a date opening the Material
date picker. Only the due date carries a supporting line, because it is the one
that has to explain what it is for.

[FD] A preset is filled when it is the value the task holds and outlined
otherwise. That is the one place a value is styled differently on this screen,
and it is about the control rather than about whether the field is set.

[IMPL] Custom duration is an `AlertDialog` rather than a second sheet. A modal
sheet on Android is a dialog with its own window, so stacking means two of them:
the scrim darkens twice and back has to be dispatched across the pair. That
lesson is inherited from the screen this replaced, where it was learned the hard
way, and it is the one thing from the two-page design worth carrying forward.

[FD] The reminder is a dialog over the screen, unchanged from the design that
preceded this one. D-018 rewrote how a reminder is reached, not what setting one
is, and it is the one control the board still draws exactly as it was.

[FD] The Repeat sheet offers `Recurrence`'s four periods and none, and nothing
else. The board's editor with an interval, a weekday set and an end condition is
Phase 4 per D-019.

---

# Inputs

[IMPL] `OutlinedTextField` throughout.

[FD] A field's own controls go in its trailing slot, not beside it. The
component centres trailing content on the input line, so it stays aligned at
every font scale and does not move when supporting text appears. A clear button
in a Row next to the field had to be aligned against a height that changes with
both, and sat about 10dp high at 100% with no correction that survived 200%.

[FD] Clear is an icon there rather than the word. Its content description names
the field it clears, which three buttons all reading "Clear" never did.

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

[IMPL] One undo offer for the whole app, shown with `SnackbarDuration.Short`,
through `UndoSnackbarHost`. Every screen hosts that rather than a bare
`SnackbarHost`, so no screen can be the one that forgets.

[FD] Short, which is four seconds against the long form's ten. It used to be
long, on the grounds that undo is the only way back, and that reasoning left
out how often the offer appears: completing a task is the most frequent thing
anyone does here, and ten seconds of a bar across the bottom of the list after
every tick is the same tax `expressive-motion.md` refuses to put on the
interaction itself. A user who ticks the wrong task knows at once.

[IMPL] Naming the shorter value costs nothing in reach. Material passes either
through `calculateRecommendedTimeoutMillis` with `containsControls` set, so a
user who has asked the system for more time to act is given it regardless of
which is named here.

[FD] Material default appearance and motion. Undo is the only action.

[FD] It exposes a polite live region, so the offer is announced when it arrives
instead of waiting to be found. Undo is time limited, and an offer nobody hears
is not an offer. Polite rather than assertive: completing a task is the user's
own doing, and interrupting a screen reader over it would be rude about
something that is not urgent.

---

# Focus screen

**Superseded by D-014 and D-015.** This section used to specify a two-state
screen, Ready and Session, joined by a container transform that grew the play
button into a shape carrying progress, with an action slot that changed height
between the two and a foot that held the way out. None of that survives.
`focus.md` holds the current design and all of the reasoning; what follows is
the visual treatment only.

One surface, six states, in a sheet. Centred on a 364dp column, top to bottom:

| Element | Treatment |
| --- | --- |
| Title | Headline Medium Emphasized, `onSurface`, centred, four-line cap |
| Shape | 180dp, `primaryContainer` |
| Time | Headline Small, `onPrimaryContainer`, centred in the shape |
| Status | Body Medium, `onSurfaceVariant`, centred |
| Clock control | 56dp round filled icon button, play or pause |
| Complete | 56dp tonal button |

[FD] Gaps are 20dp throughout. The column is centred in the content area between
the app bar and the bottom inset rather than pinned under the app bar: this is a
single-purpose mode screen with one column on it, and hanging that column from
the top left the lower half of the screen empty for no reason.

[FD] The shape is `MaterialShapes.Cookie4Sided` at rest and `Cookie12Sided`
while running. On the board the geometry comes from the M3 Design Kit Shape Set,
variants "4-sided cookie" and "12-sided cookie", which are the same shapes.

[FD] It is a fixed 180dp and does not grow with the window. It holds a
fixed-size readout rather than content, so scaling it would only make the
digits look lost. An earlier version capped it at 320dp "so a wide window gets a
shape, not a wall", which was solving a problem this size does not have.

[FD] The title sits outside the shape rather than inside it, and that change has
an arithmetic reason rather than a taste one. A cookie yields about 70% of its
box as usable area, so four lines at 200% font scale would need a 514dp square
on a 412dp screen, and three lines would need 411dp with nothing left for
margins. D-014 has the working.

[FD] The shape is drawn, not clipped to. A `Shape` would have to be a new object
every tick to change, which puts the work in layout; drawing reads the state in
the draw phase, where a changed value costs one redraw of one node. This
survives from the old design and is cheaper now, because the value changes on a
state change rather than on every tick.

[FD] The clock control is an icon button everywhere it appears, because holding
no text it does not grow with the font scale. Two worded buttons come to roughly
223dp and 198dp at 200% and overflow the 364dp row; a circle and one word come
to about 282dp.

[FD] Estimate reached is the only state with no clock control, since there is no
clock left to control. It carries two worded buttons, Complete and +5 min, with
Complete as the primary.

[FD] Complete is tonal in every state, beside a filled control, because it is
the second of two actions.

[FD] The app bar holds one control and no title: a chevron down at the start,
keeping its 48dp target. Per D-015 it pauses rather than stops, so it discards
nothing in any state, and back does the same. A chevron rather than an X because
the session is being put away rather than closed, and because a bottom sheet is
dismissed by dragging down, so the control should not mean something different
from the gesture.

[FD] The screen name is published as `paneTitle` rather than drawn. While the
bar carried a centred "Focus", the screen had two centred headings stacked and
the upper one named the app instead of the work.

[FD] The task title takes `onSurface`, not `onPrimaryContainer`. It sits on the
background now that it is outside the shape. Only the digits, which are inside
the shape, take `onPrimaryContainer`. The two happen to be close in the fallback
palette, so getting this wrong is invisible until a dynamic scheme pulls them
apart.

[FD] Interaction states are not drawn at the Focus level. The controls are
instances of the M3 Icon button and Button sets, which already ship Enabled,
Hovered, Focused, Pressed and Disabled. Restating them would give the two copies
somewhere to disagree.

---

# Dialogs

[IMPL] `DatePickerDialog` is the only dialog in the app.

[FD] Keep it that way. `PRODUCT.md` says to avoid confirmation dialogs for
low-risk reversible actions, and undo covers those instead.

---

# Segmented controls

[IMPL] `SingleChoiceSegmentedButtonRow` for recurrence. There are no tab rows
left: the pair that carried Anytime and Someday went with those lists.

[FD] Segmented controls are for small, mutually exclusive, equally weighted
choices. Three options is the practical limit.

[FD] A segmented row must not overflow at large font scales, and must not
truncate a label to avoid doing so.

[IMPL] A segmented row scrolls sideways when it cannot fit. Its minimum width
is the width of the field, so at ordinary font scales the three buttons divide
that exactly as before and there is nothing to scroll. At 200% three labels no
longer fit across a phone, and the row grows to the width its content needs
rather than clipping an option out of reach.

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
