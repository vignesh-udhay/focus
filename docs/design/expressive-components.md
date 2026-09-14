# Components

How each Catimo component looks and behaves. Read
`expressive-design-system.md` for the tokens this file spends, and
`expressive-motion.md` for anything that moves.

If you are building a new screen, assemble it from these. Do not introduce a
new component because an existing one is nearly right; say what is missing.

---

# What Catimo uses

[CD] Keep and refine, never replace:

    SegmentedListItem      Checkbox        FloatingActionButton
    NavigationBar          DropdownMenu    ModalBottomSheet
    DatePickerDialog       OutlinedTextField                Snackbar
    TopAppBar              PrimaryTabRow   Button / TextButton / OutlinedButton

[CD] Do not introduce, unless a later product decision explicitly requires one:

    SplitButton            MaterialShapes polygons         shape morphing
    navigation drawer      bottom sheet scaffold           cards

Two have since been required and have left that list rather than been smuggled
past it. `ButtonGroup` holds the Duration sheet's five options, per D-026.
`FloatingToolbar` holds Task Details' two actions, per D-037, which is also the
entry that noticed `ButtonGroup` was still listed here and said the line needed
correcting either way. The exclusion was always "Catimo has no use for them",
not "they are unsound", so a use is what removes them.

[M3] The first three are real Material 3 Expressive components. They are
excluded because Catimo has no use for them, not because they are unsound.

---

# Task row

The most repeated element in the app, and the one worth the most care.

[IMPL] `TaskRow` wraps `SegmentedListItem`. `TaskListRow` wraps `TaskRow` with
metadata derivation. Keep both, and keep the segmented
collection structure: `ListItemDefaults.segmentedShapes(index, count)`,
`segmentedColors`, and `SegmentedGap` between rows.

## Why the board builds its rows rather than instancing the kit

[CD] The five row components on the board — Task row, Plan row, Settings
navigation row, Settings toggle row, Choice radio row — are built from
primitives. Only the leaf atoms come from the M3 kit: the checkbox, the radio
button, the chevron.

[IMPL] The radio row joined them late. It had two variants and no Position, flat
0dp corners, a `Surface` fill and a 0dp group gap, while the code's `ChoiceRow`
composes `SegmentedListItem` with `segmentedShapes` and `PlanRowGroup`. It now
carries Position as the other four do, with its corners on
`Segmented/Radius outer` and `Segmented/Radius inner`, its groups on
`Segmented/Gap`, and 16dp of content inset.

[IMPL] **The two states do not share a fill.** Selected is `Secondary Container`
with an `On Secondary Container` label; unselected is `Surface Container Low`.
The board drew both alike, which was the real divergence: in code the two come
from different fields. `ListItemColors` carries `containerColor` and
`selectedContainerColor` separately, and `planRowColors()` overrides only the
first, so the selected row keeps Material's default of `secondaryContainer`.

[IMPL] **The selected row is also rounded on all four corners**, whatever its
position in the group. `ListItemShapes` carries `selectedShape` separately from
`shape`, and the segmented default resolves it to `CornerLarge`, so a selected
row detaches from its neighbours instead of staying welded to them. Only
unselected rows take the 16/4 first-middle-last treatment.

[CD] Unselected rows therefore paint the same tone as the sheet beneath them,
because a modal sheet's own container is `surfaceContainerLow` too. That is not
a defect. The selected row is the one carrying information, and it lifts; the
rest are quiet, which is what a single-choice list should look like.

[CD] Worth recording how this was nearly got wrong. Reading the board's sheet
fill and the board's row fill, finding both `Surface Container Low`, and
concluding the container never shows is circular: it tests the drawing against
itself. The emulator showed a tinted selected row immediately. Check a rendered
state before calling a colour a no-op.

[CD] The theme chooser is unaffected. Its rows strip the fill locally, which is
what a radio list in an alert dialog should do, and the board keeps that.

This is written down because "why isn't this a kit instance" is a reasonable
question with a non-obvious answer, and guessing at it once already produced a
wrong one.

[M3] **The kit does have a segmented list.** It is not a component of its own,
which is why searching the library by name finds nothing: it is
`Type = Segmented (filled)` on the kit's `List` set, a container of ten
`List item` instances at a 2dp gap. That 2dp is the same gap these rows use, so
it is a useful confirmation of the spacing.

[CD] What it does not express is the anatomy. The kit's `List item` offers
`Trailing` as None, Check Box, Icon, Radio Button or Switch. A task row's
trailing is a duration string and an icon button together, which is not among
them. The `Content` slot could hold one, but at that point the kit is supplying
a container and everything inside it is ours regardless.

[CD] The four rows also differ from one another in ways a single kit variant
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

[CD] The obvious shared base, a container component the four compose, cannot be
built here. It needs a content slot, and slots cannot be created through the
Figma plugin API. What can be shared is the values, and the values are what
drifted.

    Segmented/Radius outer   →  Corner/Large        16
    Segmented/Radius inner   →  Corner/Extra Small   4
    Segmented/Gap            →  Spacing/2            2

[IMPL] All four sets bind every corner of every variant to the two radius
variables, 240 bindings across 60 variants, and every frame that groups rows
binds its `itemSpacing` to the gap.

[CD] They are aliases onto the existing scale rather than new numbers. The scale
stays the single source of the values; these say which of them the segmented
treatment uses. That is the difference that matters: binding the rows straight
to `Corner/Large` would stop anyone typing 28, but moving the treatment to 20dp
would still mean finding all four again. One edit now.

[CD] The surface needed nothing. All four were already bound to
`surfaceContainerLow`.

[CD] **What this does not protect is variant structure.** A variable cannot make
one component carry the same states as another, and that half of the drift stays
manual: the Plan row shipped no Pressed or Focused while the Settings row had
both, which is how it also ended up at 28dp on `surfaceContainerHigh` without
anyone noticing. Enabled, Pressed and Focused is the set. Hovered, Dragged and
Disabled exist in the kit's `List item` and are deliberately unused.

## Anatomy

[CD] Checkbox leading, title, optional metadata line beneath, and the duration
at the end. Nothing else: no drag handle, no chevron, no avatar, and **no
trailing button**.

[CD] A trailing actions button was added once and D-023 removed it. It had
reversed this file's own "nothing else, no trailing icon" on the grounds that
Delete and Focus were reachable only by a gesture, which `PRODUCT.md` forbids as
the sole route. Task Details now holds both, so the gesture is no longer alone
and the rule it broke stands again.

| Element | Role | Colour |
| --- | --- | --- |
| Title | `bodyLarge` | `onSurface` |
| Metadata | `bodySmall` | `onSurfaceVariant` |
| Overdue date within metadata | `bodySmall` | `tertiary` |

[CD] Two levels of *text* hierarchy in a row. Anything that wants room in a row
is competing with the title for it, which the removed trailing button proved:
it pushed two of five seeded titles onto a second line until the screen margin
was returned to `md`.

[IMPL] The row carries a minimum height from `FocuslistDimensions`. A floor,
not a height: a row with metadata or a wrapped title is already taller and
grows past it. It exists so a bare one-line row cannot come out shorter than
its neighbours and leave the collection looking ragged.

[CD] Metadata segments are joined with a middot. A row with no metadata emits
no supporting content and reserves no space for it.

## Title

[CD] Two lines maximum, then ellipsize. A long title must not be able to push
the rest of the list around, and a row that grows to six lines stops being a
row.

[CD] Metadata wraps naturally onto a second line rather than truncating. It is
short, and truncating a date is worse than wrapping it.

## States

[CD]

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

[CD] Tap opens Task Details. The checkbox toggles completion and is not part of
the row's click target. There is nothing else: D-023 removed the actions menu
and the long press that opened it.

[CD] The row does not change shape or size when pressed. Material's ripple and
state layer are the entire press feedback. A springing row in a list of twelve
is noise, not delight.

## Motion

[CD] Title colour and strikethrough use `stateColor`. The checkbox uses
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

[CD] The row grows vertically. The two-line title cap prevents runaway.
Metadata wraps. The checkbox stays 48dp.

---

# The row has no actions menu

[CD] `docs/decisions.md` D-023 removed the trailing button and the long-press
menu. Tapping a row opens Task Details, and everything the menu carried lives
there: rescheduling through the Plan rows, Start focus as the primary action,
Delete in the overflow.

[CD] **The menu existed for a reason that three later decisions removed.** This
section used to justify the trailing button by saying Delete and Focus "live
only in the actions menu, Task Details deliberately excludes both". D-018 gave
Task Details a Start focus, D-022 gave it Delete, and its Plan rows give it
rescheduling. Nothing was excluded any more, and the button had outlived its
premise without anyone going back to check.

[CD] It had also become a worse duplicate. The menu offered Today, Tomorrow and
Pick a date; the Scheduled sheet offers No date, Today, Tomorrow, This weekend
and Choose a date, and cannot be beaten by a subset that has no way to clear a
date.

[CD] Three of the menu's five items were rescheduling, which is administration,
and a permanent trailing button gave the most administrative action the most
prominent position on every line of every list. `PRODUCT.md` principle 4 asks
for the opposite. The width was real too: adding the button pushed two of five
seeded titles onto a second line until the screen margin was returned to `md`.

[CD] What it costs is a tap. Rescheduling from a list is three rather than two,
and rows no longer answer a long press. Nothing became unreachable. D-023
records the trade and names what would reverse it: if this proves too slow, the
answer is a bottom sheet on long press, which is where Material's compact
guidance points for a five-item menu, not the button returning.

[CD] Two rules from the old menu survive it, because they are about the product
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

[CD] Its state change is the strongest expressive moment in the app and uses
the `completion` token. This is the only component with that privilege.

---

# Top app bar

[IMPL] `FocuslistTopAppBar`, one component for every list screen, wrapping the
compact M3 `TopAppBar` at 64dp. Callers pass a title and, on the three primary
destinations, the overflow. It is pinned: no scroll behaviour, no collapse.

[CD] The title carries heading semantics and no style of our own. The component
supplies `titleLarge`.

## There is no subtitle

[CD] D-020 removed it, and with it the 152dp `LargeFlexibleTopAppBar` the
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

[CD] One line survives, because it is the rule that would govern a subtitle
coming back: **a subtitle has to say something the list below it cannot, or it
does not get one.** Upcoming already failed that test, its count being of tasks
already grouped under their own day headings.

## The overflow is the only action

[CD] On Today, Inbox and Upcoming, and nowhere else. A standard icon button
rather than a filled one, opening Logbook and Settings.
`navigation.md` holds the rule: reaching the other lists is the navigation bar's
job, and the overflow carries what the navigation bar does not.

An earlier version of this section read "No actions, no navigation icon: every
destination is reachable from the navigation bar, so there is nothing for an app
bar action to do that the bar does not already do." That was written while More
was a bar item. `navigation.md` removed More and moved what sat behind it into
this overflow, so the premise is gone.

[CD] Rooms take a back arrow and no overflow instead, through
`Focuslist / Room header`. A screen wears the bar and an overflow, or a back
arrow and no bar, never both.

## Colour and sharing

[CD] The bar names no colour. Material's default is `surface`, and the page is
`surface`, so bar and page are one ground and the collection is the only thing
on it. An earlier override existed only because the page had been moved onto a
container role; it went when the page came back.

[IMPL] Shared rather than repeated because these properties are always applied
together and six copies drifted apart on all of them. Do not build a bar by
hand; if a screen needs something this cannot express, say so.
---

# Navigation bar

[IMPL] `NavigationBar` on every screen, with Today, Inbox, Focus and More.

[CD] On every screen rather than only the primary ones, so no list is a dead
end.

[CD] More is a menu, not a destination. It shows as current while the user is
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

[CD] **Do not move this to the primary family.** It was tried and reverted, and
the reasoning is recorded here so it is not retried:

The apparent problem was that the selected pill looked grey rather than brand
coloured next to the floating action button. Measured in CIELAB against a
Google Play reference, that was wrong. Play's pill is L\* 89.8, C\* 16.8, hue
247; Play's primary button is L\* 40.2, C\* 72.5, hue 292. Forty-four degrees
of hue apart and four times the chroma: they are different palettes, and the
pill's chroma of about 16 is exactly what Material's tonal scheme generates for
secondary. Catimo's own default pill measured C\* 12.1, already close to the
reference. Switching it to `primaryContainer` raised chroma to 28 and dropped
the tone from 90 to 82, moving it *away* from the reference on both counts.

The earlier measurement that suggested otherwise used `max(RGB) - min(RGB)` as
a saturation proxy, which overstates chroma badly for light colours. Use
CIELAB C\*.

[CD] A selected navigation item is a **marker** and the floating action button
is an **action**, and they are deliberately drawn from different palettes. The
marker recedes into the bar; the button is meant to be the most prominent thing
on the screen. See the floating action button section.

[CD] Where an icon has a filled and an outlined variant, selected uses filled
and unselected uses outlined, so selection is not conveyed by the container
alone.

[IMPL] Today, Inbox and Focus each have both variants and switch between them.
More keeps one icon in both states: three dots have no filled counterpart, and
drawing one would be inventing a symbol rather than using a pair Material
already defines. The rule is conditional for exactly this reason.

[CD] Each destination in the More menu carries a leading icon, in the same
outline style and at the same weight as the bar's. The menu is where the four
secondary lists are chosen between, and four lines of bare text are harder to
pick from than four that each look like something.

---

# Floating action button

[IMPL] `AddTaskFab`, one component wrapping `FloatingActionButton`, on Today and
Inbox only. Both screens previously wrote the same button out in full; they now
call this.

[CD] The container is the **Material default**, `primaryContainer` with
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

[CD] Regular rather than extended. The button was
`MediumExtendedFloatingActionButton`, which spent 80dp of the content column
carrying the words "Add task" and repeated, in text, what a plus on a task
screen already says. At 56dp it clears more of the list. The label survives as
the icon's content description, so nothing is lost to a screen reader, and the
semantics test asserts it there rather than as visible text.

[CD] The glyph is a bare plus. The design's own is `add_circle`, which draws a
circle inside the button's own circle; Material pairs this container with a
plain glyph, because the container is already the circle.

[CD] Keep the current behaviour exactly. It does not collapse or expand on
scroll. Lists must reserve clearance beneath their last row so the button never
covers a task; that clearance is `FocuslistDimensions.FabClearance`, a dimension
token composed from the spacing scale, not a number written into a screen.

---

# Empty states

[IMPL] `TaskListEmptyState`. Material 3 has no empty-state component, so this is
Catimo's own: a 24dp inset, an 80dp expressive icon container, a 12dp gap,
then a centred headline and supporting line.

[CD] Headline in `titleMediumEmphasized`, supporting line in `bodyMedium` and
`onSurfaceVariant`.

[CD] An earlier version of this section ended "No illustration, no icon, no
action button". All three have since arrived and the section did not keep up.
The component carries an icon container; the board has an illustrated variant per
screen carrying the mascot; and the error tone is paired with an action.

## The illustrated variant

[IMPL] `TaskListEmptyState` takes an optional `illustration` slot, drawn above
the headline with `lg` beneath it, which is the gap all three board variants
draw. The three lists pass their mascot; the Logbook passes nothing and is the
same component without one.

[CD] One mascot per screen, and each says why its own screen is empty rather
than decorating the absence. It is the same cat in all three; what changes is
its posture.

| Screen | Pose | Board node | Drawn at |
| --- | --- | --- | --- |
| Today | curled asleep, for a day with nothing on it | `1076:5756` | 211x130 |
| Inbox | sitting upright, waiting to be filled | `1076:5773` | 190x173 |
| Upcoming | lying down but awake, for days still clear | `1076:5781` | 250x134 |
| Today, finished | sitting, content, eyes closed | `1068:5746` | 195x176 |
| Error | sitting, with a question mark | `1076:5764` | 173x170 |

[CD] **The board redraws these, and the node ids move when it does.** The
sleeping, sitting, lying and question poses were replaced wholesale rather than
edited, so every path changed and each pose took a new node. Nothing else had to: the part
names, the paint order and the three colour bindings survived the redraw
untouched, which is the property `MascotImage` exists to protect. The drawn sizes
moved by less than half a dp. Ids in this table are therefore a record of where a
pose came from, not a stable handle.

[CD] **The three share one scale factor, not a common width or height.** A cat
sitting is genuinely taller than the same cat lying down. Matching heights would
shrink the sitting one and matching widths would swell it, and either reads as
two differently sized animals. The board already drew the three at a consistent
scale, within about 9% by area, so carrying that scale through is the rule.

[CD] **The error pose is the exception to the no-props rule, and has to be.**
The other four say why a screen is empty, and a posture can carry that. "The app
could not read your tasks" is not a state a cat can sit in, so `cat-question`
takes the question mark. It is the only prop in the set and the only pose that
is not about an empty collection.

[CD] **The finished-day pose is a sitting cat, like the Inbox one, and the eyes
are the difference.** Closed and curved where the Inbox cat's are open and
waiting. Both are sitting because both are states where nothing is in motion;
what separates a day that is done from an inbox that is waiting is whether the
animal is still watching for something. Both also draw the nose in `mid` rather
than `dark`, which is the sitting-pose exception the tone table already carried
for one pose and now carries for two.

[CD] **The props are gone.** An earlier set gave the cat something to sit beside
on each screen, a bowl for Inbox, a calendar for Upcoming, a checked card for
Today, and the object carried the meaning. The posture carries it now. That is
the stronger version: it says the same three things with the animal alone, and
it drops three objects that had to be drawn, scaled and kept consistent.

[CD] Before the cat there was a dachshund, and before these poses there were the
props. Each swap cost three generated files and one KDoc, because the poses are
data and `MascotImage` owns the roles and the sizing. That is the property worth
protecting: a screen asks for a mascot without knowing what is inside it.

[CD] **The Logbook still has no mascot.** The three lists have one and it does
not. That is the same shape of inconsistency this section once recorded as a
defect, and it is decided by the same invisible fact: which poses have been
drawn. It should get one. What it must not get is a pose that congratulates,
because `logbook.md` says its empty state "does not congratulate, and it does
not treat an empty Logbook as a problem or an achievement".

[CD] **The mascot is decorative to a screen reader.** It draws the fact the
headline states, and the headline is the heading TalkBack lands on, so
describing the drawing as well would put that fact in the path twice.
`illustration_isNotAnnounced` holds it.

[IMPL] Each mascot is an `ImageVector` built in Kotlin, not a vector drawable.
Their three fills are colour roles rather than ink, and a drawable can neither
read the Compose colour scheme nor take three colours from one tint. Every pose
uses the same three, which is what keeps them from drifting apart:

| Tone | Role | What it draws |
| --- | --- | --- |
| light | `primaryFixed` | the coat, and the soft contact shade it sits on |
| mid | `primaryFixedDim` | the shading that gives the coat its folds and its tail |
| dark | `onPrimaryFixedVariant` | eyes and nose |

[CD] The tones bind by the part's **name**, not by its hex. The greys drift
between poses, and the sitting cat's nose is deliberately drawn lighter than its
eyes where the other two draw it dark, so matching on colour would have produced
three slightly different mappings and one wrong nose.

[CD] The ordering is the part that has to hold. Binding the cat a step darker for
more contrast against the page was tried and rejected on an earlier set: it put
the shade lighter than the animal, which describes light that cannot happen.

[CD] Fixed roles, which is what `Color.kt` set them for. They hold one value in
light and dark, so there is one artwork rather than a light one and a dark one
to keep in step, and a device palette replaces all three together under dynamic
colour.

[IMPL] `MascotImage` owns all of that, plus the sizing. Each pose file is its
frame, its builder, and its exported path data, and nothing else.

[CD] **The light tone is not symmetric across themes.** At `primaryFixed` it
sits 8.0 L* below the page in light and 83.8 above it in dark. That was a
problem when it drew the dachshund's ground shadow, since the faintest part of
the drawing in light became the brightest in dark; binding the shadow to
`surfaceContainerHighest` was tried and reverted, and the measurements are worth
keeping because they are the ones to start from if it recurs. It matters less
now: the light tone draws the cat and its objects, which are meant to be the
brightest things in the drawing, and the shade beneath them is the separate
`mid` tone.

[CD] **The mascot gives way before the copy does.** It is 102dp tall in a column
that does not scroll, so it is capped at its drawn width and shrinks on a window
narrower than that. At 200% font scale the two lines stay on screen, which
`EmptyStateSemanticsTest` asserts at both scales.

[CD] The headline stays `titleMediumEmphasized` in the illustrated variant. The
board draws it at Title Large Emphasized, which is the size and weight the app
bar uses, and on an otherwise empty screen that puts two identical headings
240dp apart with neither leading. The mascot is already what draws the eye. If
the board is ever right about this, the type table in
`expressive-design-system.md` is what has to change first, since it names the
three places emphasis is allowed.

[CD] Two tones, and they are a parameter now rather than a description:
`EmptyStateTone.Neutral` explains an empty collection, `EmptyStateTone.Error`
explains a read that failed. `Error` is the only one that takes an action, and
`TaskListErrorState` is the assembled form the four screens call.

[CD] **The icon container is gone, and the headline carries the colour.** This
section used to describe an 80dp `errorContainer` icon with the glyph on
`onErrorContainer`, and called that plus the button "the signal". No such
container was ever built; when the error state was finally implemented the
illustrated variant already existed, so the error state took the mascot like
every other state. A mascot draws in the fixed primary tones, so it says nothing
about severity. The headline takes `MaterialTheme.colorScheme.error` instead.
That is the whole colour signal, and without it a failed read would look like an
ordinary empty list worded oddly.

## No container, in any state

[CD] Empty and error states sit directly on the background. There is no card, no
surface, and no elevation.

[CD] The plain variants used to draw one: a 28dp radius on `surfaceContainerLow`
with 24dp of padding. The illustrated variants never did, so the app had two
empty-state treatments and the thing that decided between them was whether a
mascot had been drawn for that screen yet. That is invisible to a user and it
made the Logbook's empty state look unlike every other empty state in the app.

[CD] The card went rather than spreading. A card is a container for related
content, and an empty state is the absence of content, so a surface drawn around
nothing is decoration around nothing. Removing it also cost 48dp of padding that
was only ever inseting content from a card edge, and it lets the copy use the
full 380dp column the illustrated variants already use.

[CD] **An error still reads as an error without one.** Its icon container takes
`errorContainer` with the glyph on `onErrorContainer`, and it is the only state
carrying an action button. Those two are the signal; the card was adding nothing
the colour was not already saying.

[IMPL] The icon is the screen's own, outlined, in `onErrorContainer`: `today`,
`inbox`, `schedule`. Upcoming had drifted on both counts, bound to `onSurface`
and swapped to a filled `schedule`, which rendered a near-black disc on a pink
container while the other two were dark red outlines.

[CD] The copy is plain. An empty list is not an achievement, and nothing here
congratulates the user. That holds for the finished-day state too, which is the
closest this app comes to a celebration and is kept on the right side of
`PRODUCT.md` principle 7 by the same rule as everything else: the line states a
fact and points somewhere.

[CD] **A supporting line states a fact, and never instructs.** The headline names
the state; the line under it says something true about the app. It does not tell
the user what to do next, and it does not comment on how they feel about it.
Today's read "Add a task when you are ready", which was the only one aimed at the
user rather than at the screen, and the mildest form of the motivational noise
`PRODUCT.md` principle 7 rules out. It also sat under a cat drawn asleep, so the
picture and the words wanted different things.

[CD] Today has two of these, and D-033 turns on the difference. `today_empty_*`
is a day that never had anything on it. `today_all_done_*` is a day that had work
and finished it:

    All done for today
    Tomorrow's tasks are in Upcoming.

[CD] The finished-day one is not an empty state and does not draw like the
others. It heads the list through `TaskListDoneHeader`, with the Completed
disclosure directly beneath it, because a finished day is full rather than empty
and those rows are how a task completed today is reopened. Both forms share one
body composable. Its supporting line points at Upcoming rather than at the rows
below it, since those are already on screen and do not need announcing.

[CD] Today is the screen where the pattern is hardest, because it is the only one
that explains itself. `logbook.md` gives the supporting line its job, teaching a
user what lands on a screen they have no other way to learn about, and nobody
needs to be told what Today is for. So the useful fact is the one the headline
cannot carry: a blank Today does not mean a blank app.

    Nothing scheduled for today
    Tasks without a day wait in your Inbox.

[CD] It mirrors `inbox_empty_supporting`, "Anything you capture without a day
waits here", with the same vocabulary pointing the other way. It stays true when
the Inbox is empty too, because it says where such tasks live rather than
claiming any exist.

[CD] **"Nothing overdue, either" was tried first and is wrong.** Overdue is one of
Today's own bands, and the empty state only renders on `tasks.isEmpty()`, so the
line restates what the blank screen already proves. It also enumerates a category
of absence, which is an anxious thing to do on a clear day. Whether the app is
silently missing work is a real question, and the reminder health screen is where
it is answered.

## Error copy says what did not happen

[CD] The headline names the read that failed. The supporting line says the data
is intact, because that is the question the user actually has:

    Couldn't load your tasks
    Your tasks are safe. This is a read that failed.

    Couldn't load your Logbook
    The record is safe. This is a read that failed.

[CD] **It must not mention a connection.** Inbox and Upcoming both read "Check
your connection and try again", and that was wrong in a way worth recording.
Catimo has no accounts, no cloud sync and no backend, and `PRODUCT.md` puts
all three permanently out of scope. Every read is local. Telling users to check
their connection sends them to fix something that was never involved, and it
implies the app has a server it does not have. This is the same failure as the
Logbook's removed summary card: interface text asserting something untrue about
the system.

[CD] The Logbook's wording differs on purpose. It is the screen that exists to
make completing a task safe, so a user who cannot see their finished work has a
specific and reasonable fear. "The record is safe" answers that fear rather than
the generic one.

---

# Section labels

[IMPL] One `SectionLabel`, shared. `labelLarge` in `onSurfaceVariant`, above the
group it names, with `md` beneath it and `lg` above it. Today names bands,
Upcoming names days, and both are the same kind of heading over the same kind of
collection, so they cannot be allowed to drift.

[CD] Sentence case, everywhere. The design draws Today's bands in capitals and
Upcoming's dates in sentence case; one of the two had to give, and shouting a
word the user is not reading is the one worth losing.

[CD] A label is a label and nothing more: no divider, no background, no
container, no chevron, no count badge, not collapsible.

[CD] Where a heading names a day, the rows beneath it do not. Upcoming groups by
date, so its rows drop the date from their metadata and show the duration and
the recurrence instead; repeating the day on every row would say the same thing
twice and spend width the rest of the line needs. `TaskListRow` takes
`showDate` for this, defaulting to true, because every other list wants it.

---

# Sheets

[IMPL] `ModalBottomSheet` for Quick Add and for each of the sheets Task
Details' Plan rows open, with only the Hidden and Expanded states enabled: none
of them has a half-height state worth stopping at.

[CD] Standard Material scrim, drag handle and corner treatment. Sheet motion is
the Material default, with one exception: Quick Add opens at `Expanded` and
lets the keyboard carry it up, D-073. It is the only sheet that opens a
keyboard, and a slide running under a rising keyboard lands the sheet in the
wrong place and then corrects it. Dismissal still slides.

[CD] **A sheet used to hold a draft**, and Task Details was the reason that rule
existed: nothing was written until Save, so dismissing left the task exactly as
it was. D-018 removed the draft along with the Save, and records it as the price
rather than pretending it is free. Quick Add still holds one, because a capture
that has not been confirmed is not a task yet.

[CD] Content scrolls. Six fields do not fit at large font scales, and an action
that cannot be reached is a broken screen.

## Quick Add

**Extended by D-011.** A trailing time is now read as well as a day, and when
one is read the sheet shows a single dismissible Reminder chip. The rule below
against a second way to set a date is kept, and is why the day still has no
chip. The reminder chip is not a second setter: nothing else in the sheet sets
a reminder, and dismissing it unmarks the same run in the field, so there is
one mechanism rather than two.

**The sheet's heading came back in D-052.** This section used to end "The sheet
also lost its heading; the FAB that opens it already says Add task." That was
right about the FAB and wrong about where the name went: removing the heading
did not remove the name, it moved it into the field's floating label, where it
was redrawn on every keystroke and cost a row of the input to say what the sheet
was for. "New task" is a `titleLarge` heading above the field now, and the field
carries no label. A filled field is 56dp with or without one, so the container
did not move; the top row went back to the text.

[CD] One field and one action. Do not add a second field or a date picker.
Capture should require almost no decisions.

[CD] The field reads a day off the end of the title and marks it: the matched
words take `primary`, and a supporting line under the field names the resolved
date. That is one field still, not two — the marking and the line are feedback
on what was typed, not somewhere else to type.

**When no day was typed, that line names the destination instead, and the sheet
has to be told what it is.** D-053. The two hosts differ: Today saves
`parsed.date ?: today` and the capture lands in Today, Inbox saves `parsed.date`
and the capture stays undated in Inbox. The line worked it out from
`parsed.date` alone, which is null in both cases, and so said "Saved to Today"
over a task going to Inbox. `fallbackDate` is now a parameter, so a host cannot
open this sheet without answering the question.

**A dated capture still names the date, not the list**, even though a future day
means the task appears in Upcoming. The date is what the user typed and wants
confirmed, and the list follows from it. The line names a destination only when
there is no date to name, which is also the only time the destination is the
surprising part.

**And it says nothing until there is a title.** It used to read "Saved to Today"
over an empty field, asserting a destination for a task that did not exist. It
is tied to the same test the Add button uses, so the line appears exactly when
there is something for it to describe.

[CD] The placeholder is an example of exactly that, and it is there to teach
it. Nothing else on the sheet says a date can be typed, so without one the
field looks like a plain text box and the feature is found by accident or never.
The design this screen was drawn from solved the same problem with a row of
Today / Tomorrow / No date chips; those were declined, because they would be a
second way to set a date alongside the one the field already has, and the two
would need a precedence rule the user cannot see — a title ending in "tomorrow"
with **Today** selected has to resolve somehow, and neither answer is
defensible. An example costs nothing and adds no second mechanism.

[CD] The colour is never the only signal. A screen reader cannot announce it
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

[CD] A full screen, not a sheet. Three regions on one scrolling column:
identity, plan, action.

| Element | Treatment |
| --- | --- |
| App bar | back arrow, no title, no overflow |
| Title | Headline Small, `onSurface`, borderless, four-line cap, heading |
| Notes | Body Large, `onSurfaceVariant`, borderless, several lines |
| Section label | the shared `SectionLabel`, reading "Plan" |
| Plan row | 56dp, `surfaceContainerLow`, 16dp outer and 4dp inner, 2dp gap |
| Start focus | full width, 56dp, the screen's only accent |

[CD] **No card around the identity region**, and that is the fix rather than the
omission. A tinted container read as a summary, which is exactly why the two
most-edited fields on the screen looked read-only. A Material text field brings
its own container, so putting real fields inside a card nests one in another.

[IMPL] The fields are borderless: every `TextFieldDefaults` colour is
transparent except the cursor, which is what says the text can be typed into.
They commit on blur rather than per keystroke, and the field is the draft until
focus leaves it.

[CD] The rows are the row family's Plan variant, which the section above
specifies and binds to the shared radius and gap variables. Each shows its value
rather than naming the sheet it opens: `Due date  None` says what can be set,
and an empty field does not.

[CD] **Unset values are not styled differently.** `None` and `Doesn't repeat`
render exactly like `Today` and `45m`. `task-details.md` carries the argument in
full, including the reason there is no token for it: the next step down from
`onSurfaceVariant` is `outline`, which on `surfaceContainerLow` is about 3.8:1
and fails AA at 14sp.

[CD] Durations read through `DurationLabel`: `45m`, `1h`, `1h 30m`. Never
"45 min", which nothing else in the app says.

## The sheets the rows open

[CD] Both date sheets are one shape: a title, the clear option full width, three
day presets in a 2x2 grid, then a full-width Choose a date opening the Material
date picker. Only the due date carries a supporting line, because it is the one
that has to explain what it is for.

[CD] A preset is filled when it is the value the task holds and outlined
otherwise. That is the one place a value is styled differently on this screen,
and it is about the control rather than about whether the field is set.

[IMPL] Custom duration is an `AlertDialog` rather than a second sheet. A modal
sheet on Android is a dialog with its own window, so stacking means two of them:
the scrim darkens twice and back has to be dispatched across the pair. That
lesson is inherited from the screen this replaced, where it was learned the hard
way, and it is the one thing from the two-page design worth carrying forward.

[CD] The reminder is a dialog over the screen, unchanged from the design that
preceded this one. D-018 rewrote how a reminder is reached, not what setting one
is, and it is the one control the board still draws exactly as it was.

[IMPL] A Plan row is one `Row` inside `SegmentedListItem`'s content slot, not a
headline with a trailing slot. `ListItem` measures trailing content first and
gives the headline the remainder, so a long value ate the row: with every weekday
selected, the Repeat label measured 139px at 100% and was not displayed at all at
200%, leaving a row with a value and no name. The label is measured first now and
the value takes what is left, which is the right way round because the label is
one of five fixed strings and the value is the unbounded one.

[CD] `PlanRowSemanticsTest` guards it by rendering the same label beside a short
value and a long one and asserting the two widths match. It reads the *unmerged*
tree, and that is the whole probe: `SegmentedListItem` merges its descendants, so
the default tree answers both lookups with the same row node. The first version
measured the row twice and passed against the layout it was written to catch,
which is D-026's warning arriving a second time.

[IMPL] The Repeat sheet is the board's editor, built under D-027: three panes in
one `ModalBottomSheet`, with Every and Ends behind a back arrow. This said it
offered four periods and nothing else, which was true while D-019 held the editor
back.

[IMPL] It carries a summary line under its title, in the Due date sheet's
supporting-text treatment: `bodyMedium` on `onSurfaceVariant`, directly under the
headline. That slot is this app's supporting-text slot and the summary has taken
it, which leaves the footnote above Save as a note about what committing does
rather than as supporting text. The two are not interchangeable: the summary
reflows as the rule changes, so it cannot sit above the primary action without
moving it under the user's thumb.

[CD] It is the one sheet on Task Details with a Save, and the exception is
argued rather than assumed. Every other row sets one field from one choice, so
writing on the tap is the whole interaction; a rule is four fields that only mean
something together, and writing each tap would put half-built rules on the task.

[CD] The weekday chips are `FilterChip`s at
`FocuslistDimensions.WeekdayChipSize`, sized rather than left to their labels.
Seven at their natural width came to 1002px inside a 992px column on a 1080px
screen, so the last day wrapped to a line of its own at the default font scale.
48dp is the board's own number and `SpaceBetween` spends the remainder, so the
row keeps its seven columns; the height is a floor, so a letter at 200% makes its
chip taller rather than being cut.

[CD] A chip given a fixed width must be told to centre its label.
`ChipArrangement` places the first child at x = 0 and leaves the slack on the
right, which is correct for a chip sized by its content and wrong for one sized
by us. Pass `horizontalArrangement = Arrangement.Center`. Measured before the
fix, the letters sat 5px left of centre for M and W and 10px for T, F and S: half
the leftover space, so the narrower the glyph the worse it looked.

[CD] The last selected day does not come off, which the board asks for in the
name of the frame that holds them. Nothing happens when it is tapped, rather than
an error or a disabled chip: a greyed-out chip would say the day was unavailable
when it is the one that is chosen.

[CD] **`FilterChip` is the classic Material component here, not the expressive
one, and that is deliberate.** `Chip.kt` contains no reference to Expressive. The
expressive control for picking several from a short set is the connected button
group below, and the board does not draw one: its weekday node is seven separate
48dp instances, while the modal sheet, the app bar and Save repeat in the same
frame are all named as `M3` instances. `ButtonGroup` would also overflow a day
into a dropdown when it ran out of room, which is insurance for durations and a
missing Saturday here.

---

# Inputs

[IMPL] `TextField` throughout: the filled variant, per D-025. This said
`OutlinedTextField`, which was a note about which component was in use rather
than an argument for it.

[IMPL] **No active indicator, and the large corner on all four sides.**
Material's filled field ships a 1dp rule under it and a top-rounded,
bottom-square container. Both are gone, through one shared
`focuslistFieldColors()` and `FocuslistFieldShape` in
`ui/component/FieldStyle.kt`, read by all four fields that draw a container:
Quick Add, the repeat interval and occurrences, and the custom-duration Hours
and Minutes.

[IMPL] The shape is `MaterialTheme.shapes.large` rather than a written 16dp, so
the field cannot drift from the rows: `ListItemDefaults.segmentedShapes`
resolves `CornerLarge`, which is the same value, and D-008 leaves the corner
scale to the theme rather than restating it per component.

[CD] The error indicator is deliberately kept. The rule is removed as
decoration, and in the error state it stops being decoration: it is the cue
Material pairs with the error colour and the supporting text. A line that
appears only when something is wrong earns its place; one that is always there
does not.

[CD] This sits inside D-025 rather than against it. That entry argued for a
*tinted container*, and the argument holds: every other surface in the app is
one. The underline and the flat bottom edge came along with the component and
were never argued for by anyone. Nothing else in Catimo has a rule line, and
nothing else is rounded on two corners only, so the field was the one piece
still speaking Material 2.

[CD] 16dp rather than fully round, and the reason is D-025's own reversal
condition: "a filled container reading as a chip or a button somewhere it sits
beside real ones. The place to watch is Quick Add, where the field sits above a
filled Add task button." A pill-shaped field walks into exactly that. 16dp is
`Segmented/Radius outer`, so the field matches the row families and stays
clearly not-a-button.

[CD] Task Details' title and notes are unaffected. They already paint the
container out entirely, so there is no corner or indicator to correct.

[CD] The rule is narrower than "filled everywhere", and worth stating precisely:
**a field that draws a container draws a filled one.** Task Details' title and
notes draw none. They are the same component with its container painted out,
because D-018 makes the title the screen's heading and its primary input at
once, and a container around it makes the top of that screen read as a summary
card.

[CD] A field's own controls go in its trailing slot, not beside it. The
component centres trailing content on the input line, so it stays aligned at
every font scale and does not move when supporting text appears. A clear button
in a Row next to the field had to be aligned against a height that changes with
both, and sat about 10dp high at 100% with no correction that survived 200%.

[CD] Clear is an icon there rather than the word. Its content description names
the field it clears, which three buttons all reading "Clear" never did.

[CD] Every field carries a label. A field with a constrained format carries a
placeholder showing the format. Errors use the Material error treatment plus
supporting text; never colour alone.

[CD] A single-line field's placeholder is capped to one line, so an empty field
is never taller than a filled one at large font scales.

[CD] A date field is the value: an existing date is written into it as text,
and an empty field means no date. Typing and the calendar picker both write the
same field.

---

# Snackbar

[IMPL] One undo offer for the whole app, shown with `SnackbarDuration.Short`,
through `UndoSnackbarHost`. Every screen hosts that rather than a bare
`SnackbarHost`, so no screen can be the one that forgets.

[CD] Short, which is four seconds against the long form's ten. It used to be
long, on the grounds that undo is the only way back, and that reasoning left
out how often the offer appears: completing a task is the most frequent thing
anyone does here, and ten seconds of a bar across the bottom of the list after
every tick is the same tax `expressive-motion.md` refuses to put on the
interaction itself. A user who ticks the wrong task knows at once.

[IMPL] Naming the shorter value costs nothing in reach. Material passes either
through `calculateRecommendedTimeoutMillis` with `containsControls` set, so a
user who has asked the system for more time to act is given it regardless of
which is named here.

[CD] Material default appearance and motion. Undo is the only action.

[CD] It exposes a polite live region, so the offer is announced when it arrives
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
| Cat | `cat-sit-front` or `cat-nap`, mascot colour roles, bottom-aligned |
| Status | Body Medium, `onSurfaceVariant`, centred: clock, then budget |
| Complete | 56dp tonal button, leading |
| Clock control | 56dp round filled icon button, play or pause, trailing |

[CD] Gaps are 20dp throughout. The column is centred in the content area between
the app bar and the bottom inset rather than pinned under the app bar: this is a
single-purpose mode screen with one column on it, and hanging that column from
the top left the lower half of the screen empty for no reason.

[CD] The second row is the cat, not a shape. D-046. `cat-sit-front` at rest and
`cat-nap` while running, board nodes `1119:5624` and `1119:5634`, drawn at
192.72 x 178.87 and 202.55 x 119.13 inside the union of the two and aligned to
the bottom so the ground line does not move between them. It takes the same
0.3643 scale factor as the five empty-state poses.

[CD] It does not grow with the window, for the reason the shape it replaced did
not: the row beside it holds a fixed-size readout, and scaling the drawing while
the digits stay put would only make them look lost. An earlier version capped
the shape at 320dp "so a wide window gets a shape, not a wall", which was solving
a problem this size does not have.

[CD] **What used to be here** was `MaterialShapes.Cookie4Sided` at rest and
`Cookie12Sided` while running, taken on the board from the M3 Design Kit Shape
Set variants "4-sided cookie" and "12-sided cookie", at a fixed 180dp holding the
readout inside it. D-014 had reduced its job to saying whether the clock was
running; D-046 gave that job to the mascot and moved the readout onto the status
line.

[CD] The title sat outside the shape for an arithmetic reason rather than a taste
one, and the arithmetic is worth keeping even though the shape is gone. A cookie
yields about 70% of its box as usable area, so four lines at 200% font scale
would have needed a 514dp square on a 412dp screen, and three lines 411dp with
nothing left for margins. D-014 has the working. The four-line cap survives it.

[CD] The shape is drawn, not clipped to. A `Shape` would have to be a new object
every tick to change, which puts the work in layout; drawing reads the state in
the draw phase, where a changed value costs one redraw of one node. This
survives from the old design and is cheaper now, because the value changes on a
state change rather than on every tick.

[CD] The clock control is an icon button everywhere it appears, because holding
no text it does not grow with the font scale. Two worded buttons come to roughly
223dp and 198dp at 200% and overflow the 364dp row; a circle and one word come
to about 282dp.

[CD] Estimate reached is the only state with no clock control, since there is no
clock left to control. It carries two worded buttons, Complete and +5 min, with
Complete as the primary.

[CD] Complete is tonal in every state, beside a filled control, because it is
the second of two actions.

[CD] The app bar holds one control and no title: a chevron down at the start,
keeping its 48dp target. Per D-015 it pauses rather than stops, so it discards
nothing in any state, and back does the same. A chevron rather than an X because
the session is being put away rather than closed, and because a bottom sheet is
dismissed by dragging down, so the control should not mean something different
from the gesture.

[CD] The screen name is published as `paneTitle` rather than drawn. While the
bar carried a centred "Focus", the screen had two centred headings stacked and
the upper one named the app instead of the work.

[CD] The task title takes `onSurface`, not `onPrimaryContainer`. It sits on the
background now that it is outside the shape. Only the digits, which are inside
the shape, take `onPrimaryContainer`. The two happen to be close in the fallback
palette, so getting this wrong is invisible until a dynamic scheme pulls them
apart.

[CD] Interaction states are not drawn at the Focus level. The controls are
instances of the M3 Icon button and Button sets, which already ship Enabled,
Hovered, Focused, Pressed and Disabled. Restating them would give the two copies
somewhere to disagree.

---

# Menus

[IMPL] Two, both `DropdownMenu`: the app-bar overflow on Today, Inbox and
Upcoming, and Task Details' overflow holding Delete. `MenuDefaults.containerColor`
is `surfaceContainer` and both take it, because both are the same component and
cannot have two container colours.

[IMPL] **The corner is overridden.** `MenuDefaults.shape` resolves
`CornerExtraSmall`, 4dp, and both menus pass `FocuslistMenuShape` instead, which
is the theme's large corner. Everything the app draws on a surface is on that
corner already: rows, cards, fields. A 4dp menu was the last square thing left
and read as a component borrowed from another app. The board had drawn 16 all
along; the code was the side that was wrong.

[IMPL] **Every item carries a glyph, and it sits after the label.** Logbook and
Settings take their own symbols; Delete takes a bin. Sized by
`MenuDefaults.TrailingIconSize`.

[CD] That is where Material puts it. The spec's own example runs Revert, Delete,
Settings, Help & feedback, each with its symbol right-aligned against the label,
and this is the one component in the app whose icon goes on the right rather
than the left.

[CD] **These icons were briefly deleted outright, and that was wrong.** The
argument was that a *leading* icon here echoed a navigation bar these
destinations are not in, which `FocuslistNavigation.kt` says plainly by calling
them rooms you go into and come back from. The premise held; the conclusion did
not. The icons belonged in the other slot, not in the bin. Worth recording,
because the reasoning read as sound and produced the wrong screen.

[CD] Delete's glyph is `error`, like its word. Colour is the second cue on both
halves and the word still says it first.

[IMPL] Item anatomy: 48dp tall, label inset 12dp, glyph inset 12dp from the
trailing edge, 8dp of padding above the first item and below the last.

[IMPL] The board draws these glyphs from the same path data as the app's
drawables rather than from kit instances, because the kit in this file exposes
no delete, settings, notification or logbook symbol. Identical geometry by
construction beats two drawings that agree today.

[CD] **Not the segmented menu, though Material now offers one.**
`MenuDefaults` exposes leading, middle, trailing and standalone item shapes and
a Standard/Vibrant pair of group colours, and `DropdownMenuItem` takes a shape,
so the expressive grouped treatment is buildable. It is for menus long enough
to need grouping. Ours hold two items and one; segmenting them would give a
transient popup more structure than the persistent lists behind it, and the
same guidance caps groups at one or two and warns against menus that scroll.

[CD] Revisit if a menu reaches five or six items, or genuinely needs two groups.
Settings has since joined the overflow, making three, which is still not that.

---

# Dialogs

[IMPL] Three, and each is a Material picker or an acknowledgement rather than a
confirmation: `DatePickerDialog` behind both date sheets, `TimePickerDialog` for
the reminder, and one `AlertDialog` on the health screen saying a test reminder
has been scheduled.

[CD] No dialog asks whether the user meant it. `PRODUCT.md` says to avoid
confirmation dialogs for low-risk reversible actions, and undo covers those
instead. The custom-duration dialog was the exception and is gone: it raised a
second window over an open sheet and put a Done/Cancel confirm on a screen D-018
had made commit-as-you-go. It is a state of that sheet now. See D-026.

[CD] A dialog over a `ModalBottomSheet` is two windows, two scrims, and two
things back could mean. Where a sheet needs a second step, the sheet changes
what it shows and takes a back arrow.

---

# Worded action buttons

[IMPL] One height, `FocuslistDimensions.ActionHeight`, 56dp, read by Start
focus on Task Details, Focus's worded button, both date sheets' Choose a date,
the date presets, and Done in the custom-duration state.

[CD] A floor rather than a fixed height. Pinned exactly, a label at 200% font
scale is cut through the middle of its letters, so the button grows to hold its
own text.

[CD] **56dp because 48dp is not a size in this system.** The expressive button
scale runs 32, 40, 56, 96, 136, with nothing between 40 and 56. Three sheet
buttons used to take their height from `TouchTargetMin`, which put them at
48dp: an accessibility floor standing in for a size. Start focus reached
instead for `FocusControlSize` and got the right number through a token named
for another screen. Neither was a decision anyone made.

[CD] It also fixes a proportion. A date preset is 176dp wide; at 48dp tall that
is 3.7:1 and reads as a bar, at 56dp it is 3.1:1 and reads as a button.

[CD] **The sheet's commit is the same size as the screen's.** Done and Start
focus are never on screen together, so there is no hierarchy to protect between
them, and both are the full-width filled commit of the surface they sit on.
Giving the same role the same treatment is what lets "full-width filled pill at
the bottom" mean one thing. The cost is about 24dp of sheet height, which is
less of the task visible behind it.

[CD] **A glyph says the button is not a commit.** Text alone at the foot of a
surface means "this commits what you are looking at and leaves": Done in the
custom-duration state, Save repeat, Doesn't repeat. An icon beside the label
means the button does something else: Choose a date opens a picker, Start focus
enters a mode.

[CD] Start focus was on the wrong side of that line and was pressed by people
meaning to close the screen. D-018 sharpens the trap by removing Save, so a lone
filled pill at the bottom of Task Details has no other reading available, and
the back arrow is a small target in the far corner while the pill sits where the
thumb rests. It carries the play glyph now, Focus's own, so the control that
starts a session looks the same in both places.

[CD] **No Done button on Task Details, and the reason is worth keeping.** The
screen commits as it goes, so a Done that only navigates would imply a commit
boundary that does not exist, and worse imply that leaving by back might lose
work. The exit that was wanted already exists as the system back gesture, which
is in the thumb zone and needs no pixels. What was wrong was a button
impersonating an exit, not a missing one.

[CD] Not this: the duration segments, which are Material's `Size=Small` inside
a connected group and sized by `toggleableItem`. A member of a group is not a
worded action button.

[IMPL] The Repeat sheets' eight footer buttons, Save repeat and the six Done
buttons across the substates, were centred pills between 86dp and 134dp wide.
They are full width now, like Done in the custom-duration state and Choose a
date in the date sheets. A sheet's commit is the full-width thing at its foot.

[CD] Making one full width takes three changes, not one. The footer has to stop
hugging and centring, the instance has to fill, and the instance's own Content
and State-layer have to fill as well: stretch only the outer box and the visible
pill stays exactly as small as it was, which reads as the change having silently
failed.

---

# Weekday chips

[IMPL] Seven `FilterChip`s, 48dp wide with 48dp as a height floor, `CircleShape`,
spread across the sheet by `SpaceBetween` so the six gaps take the remainder.
Monday first. Selected is `secondaryContainer` with an `onSecondaryContainer`
letter; unselected is transparent with a 1dp `outlineVariant` ring and an
`onSurfaceVariant` letter.

[CD] The board drew something else and has been corrected to the code: a 37x32
kit toggle, a 100dp pill when unselected and a 12dp squircle when selected,
filled `Primary` with an `On Primary` letter. Three differences at once, and the
selected one was the loudest thing in the sheet.

[IMPL] Built from primitives, like the five row families and for the same
reason. The kit toggle would not resize: its inner content hugs its own label, so
a 48dp square could not be imposed from outside without the frame snapping back.
A frame, a radius and a centred letter answer it exactly.

[CD] Every fill was bound to the kit's `Schemes/*` variables, which carry no
Catimo modes, so the chips would not have followed dark or wallpaper themes.
The same leak found on two duration segments. Worth a sweep rather than another
one-by-one fix.

---

# Clearing a repeat

[IMPL] A full-width `TextButton` reading "Doesn't repeat", beneath Save repeat in
the Repeat sheet's footer, `ActionHeight` tall like the button above it.

[CD] Text rather than filled or outlined, because the footer holds two actions
and only one of them is the commit. Weight says which: Save repeat is filled,
clearing is a word.

[CD] Offered only when there is a rule to clear. The code guards it on
`canClear`, on the rule `DatePresets.kt` argues from: a control that can do
nothing is one the user cannot tell worked.

[CD] **No frame draws the state where it is absent.** All three Repeat main
frames hold a rule, so the button shows on all three and its conditional half is
undrawn.

---

# Connected button groups

[IMPL] One, in the Duration sheet: `ButtonGroup` holding five `toggleableItem`s,
None and the four presets. There are no tab rows left, the pair that carried
Anytime and Someday went with those lists, and
`SingleChoiceSegmentedButtonRow` went with a Repeat design that no longer
exists. Repeat's Every and Ends substates are radio rows through
`SegmentedListItem`'s selectable overload, because their labels run as long as
"After occurrences" and a connected group wants short ones.

[CD] A connected group is for a small set of mutually exclusive, equally
weighted choices whose labels are short. Durations qualify: `None`, `15m`,
`30m`, `45m`, `1h`.

[CD] A row of choices must not overflow at large font scales, and must not
truncate a label to avoid doing so. This is the rule; what follows is how it is
kept.

[IMPL] `ButtonGroup` moves what does not fit into a menu behind
`ButtonGroupDefaults.OverflowIndicator`. Every option stays selectable, the
selected one keeps its state, and no label is ever cut.

[IMPL] Measured once, it never has to: all five duration presets stay laid out
inside 380dp from 100% to 200% font scale. Nothing guards that number, so
re-measure before relying on it. And measure *bounds*, not presence, because
overflowed options stay in the semantics tree with only their bounds changed,
so counting them reports success at any width. D-026 records this.

[CD] **This replaced a hand-rolled row that had no answer for running out of
room.** Five equal-weight `Button`s with `maxLines = 1` and no overflow set
could only clip. The exact scale at which it started was calculated rather than
observed, and the calculation was wrong in its details; the structural point did
not depend on it.

[CD] An earlier version of this section reached for sideways scrolling instead,
and said so as a Catimo decision rather than Material guidance, because
Material's toggle-button-group documentation gave no rule for labels that do not
fit. Material has since answered it in the component. Overflow to a menu is
better than scrolling for the same reason a menu beats a scroll anywhere: the
indicator is visible, where off-screen content is not.

[CD] The shapes come from `toggleableItem`'s own default content, so the
leading, middle and trailing corners that make five buttons read as one control
are Material's rather than a `when` on the index. Do not hand-roll them back.

---

# Icons

[IMPL] Hand-written 24dp vector drawables, one per navigation destination.

[CD] Material Symbols outline style, 24dp, tinted from the colour scheme and
never given a hardcoded fill.

[CD] A navigation icon is decorative when its label sits beside it: the label
names the destination and a content description would only repeat it. An icon
that is the whole control, such as a picker trigger, always carries a content
description.

[CD] No icon containers, no coloured icon backgrounds, no expressive icon
treatments.

---

# Component states

[CD] Where each state comes from. "Material" means the component's own
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

[CD] There is no loading state anywhere. Tasks come from a local database that
emits quickly enough that none has been designed. If one is ever needed it is a
design decision, not something to improvise.
