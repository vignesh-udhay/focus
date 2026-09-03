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
    LargeFlexibleTopAppBar PrimaryTabRow   Button / TextButton / OutlinedButton

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

[FD] Checkbox leading, title, optional metadata line beneath, and one trailing
button that opens the actions menu. Nothing else: no drag handle, no chevron,
no avatar.

[FD] The trailing button is new, and it reverses a rule this file used to state
as "nothing else, no trailing icon". The reason it changed: Delete and Focus
live only in the actions menu, Task Details deliberately excludes both, and
long press was the only way to reach them. `PRODUCT.md` says the UI must not
depend exclusively on gestures, and a gesture with nothing on screen to suggest
it was exactly that.

Long press is kept. Assistive technology reaches the menu through the row's
labelled long-press action, so the button is a second route rather than a
replacement, and it is discoverability the button adds rather than access.

[IMPL] `IconButtonDefaults.extraSmallContainerSize(IconButtonWidthOption.Narrow)`,
which draws 28 by 32. Narrow is what makes the container taller than it is wide,
and every number is a Material token. The container is only what is drawn:
`IconButton` applies `minimumInteractiveComponentSize` first, so the target is
48dp without the row carrying a 48dp square.

[FD] The menu anchors to the row's end, where the button is. A long press
anywhere on the row opens the same menu in the same place; one position is
easier to learn than a menu that appears wherever the finger landed.

| Element | Role | Colour |
| --- | --- | --- |
| Title | `bodyLarge` | `onSurface` |
| Metadata | `bodySmall` | `onSurfaceVariant` |
| Overdue date within metadata | `bodySmall` | `tertiary` |

[FD] Two levels of *text* hierarchy in a row, still. The trailing button is a
control rather than a third line of content, and it carries no label, so the
title remains the only thing being scanned.

[FD] What it costs is width, and the cost is real. Adding it pushed two of five
seeded titles onto a second line until the screen margin was returned to `md`.
Anything else that wants room in a row is competing with the title for it.

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

[FD] Tap opens Task Details. The trailing button opens the actions menu, and
long press opens the same menu in the same position. The checkbox toggles
completion and is not part of the row's click target.

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
`LargeFlexibleTopAppBar`. Callers pass a title, optionally a subtitle, and where
the screen has something to scroll under the bar, a scroll behaviour. Today
passes `exitUntilCollapsedScrollBehavior` so the large title collapses as the
list moves under it; a pinned behaviour would hold all 152dp of a two-row bar in
place. Focus passes none because there is nothing to scroll, and the placement
screen passes none because the tabs below the bar have to stay reachable.

[FD] The title carries heading semantics and no style of our own. The flexible
bar draws it at `displaySmall` expanded and shrinks it as the bar collapses;
naming a style here would fight that and freeze the collapsed state at the
expanded size.

[FD] The subtitle is a slot rather than a string, because Today spends it on two
facts at once. A screen with nothing to say there passes nothing.

[FD] A subtitle holding two facts tells them apart by position: the first at the
start, the second at the end, and neither in a container. Today reads
"Wednesday, September 2" against "2h 5m planned"; Inbox has one fact and simply
states it. The total was briefly given a tinted pill, and it read as decoration,
which `PRODUCT.md` rules out. Alignment does the same work for nothing.

[IMPL] A right-aligned subtitle needs a small end padding. Material insets the
bar's title area by 16dp at the start and 4dp at the end, because the end is
where action icons would sit and these bars have none. `xxs` takes most of the
difference back: measured on device, the text lands 1.5dp outside the edge the
rows end on, against 5.7dp with no padding. Deliberately not exact. Closing the
last 1.5dp would mean compensating for the trailing bearing of whichever glyph
the value ends with, which is a number with no meaning and no token.

[FD] A subtitle has to say something the list below it cannot, or it does not
get one. Today's date is not in the list and its planned total is a sum of it;
Inbox's count is the size of a pile the user is deciding whether to work
through now. Upcoming had one and lost it: a count of tasks that are already
grouped under their own day headings tells the reader what they can see.
Anytime and the Logbook have never had one.

[FD] So the bars are not all the same height, and that is correct rather than
drift. A bar sizes to what the screen has to say, and inventing subtitles for
the two screens with nothing to say — to make four headers agree — would be
adding noise for symmetry.

[FD] A count says nothing when there is nothing to count. Inbox drops its
subtitle entirely when the list is empty, rather than reading "0 items waiting
to process" above an empty state that already says so.

[FD] No actions, no navigation icon: every destination is reachable from the
navigation bar, so there is nothing for an app bar action to do that the bar
does not already do.

[FD] The bar names no colour. Material's default is `surface` at rest lifting to
`surfaceContainer`, and the page is `surface`, so bar and page are one ground
and the collection is the only thing on it. An earlier override existed only
because the page had been moved onto a container role; it went when the page
came back.

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

[IMPL] `TaskListEmptyState`: a centred column of two lines. Material 3 has no
empty-state component.

[FD] Headline in `titleMediumEmphasized`, supporting line in `bodyMedium` and
`onSurfaceVariant`. No illustration, no icon, no action button.

[FD] The copy is plain. An empty list is not an achievement, and nothing here
congratulates the user or decorates the absence.

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

[FD] Two pages in one sheet. Details carries what the task is: title, notes and
placement, plus one row summarising when it happens. Schedule carries when and
how big: the day, the due date, the estimated duration and the recurrence.

[FD] Split because seven controls at once is what `PRODUCT.md` means by
"avoid exposing every possible property at once".

[FD] The summary row states what is set rather than naming the page it opens.
A row reading only "Schedule" would hide its own contents: someone looking for
the duration would have no reason to think it lives behind a date. It reads
"Today · 45 min · Daily", in the order and with the separator a task row uses.

[IMPL] One `ModalBottomSheet` whose content swaps, not two stacked. A modal
sheet on Android is a dialog with its own `Window`, so stacking means two of
them: the scrim darkens twice and back has to be dispatched across the pair.
The Schedule page is also nearly the height of the screen, so a stacked sheet
would cover the one beneath it and the context it was meant to preserve would
not be visible anyway.

[FD] The Schedule page carries a back arrow and a `BackHandler`. Without the
handler the system would close the whole sheet from the second page, throwing
away the draft rather than returning to the details it came from.

[FD] The due date is hidden behind an offer to add one, and shown already open
for a task that has one. Most tasks have no deadline, and a field that is nearly
always blank is a decision asked of everyone to serve a few.

[FD] Not tied to Repeats, though that was considered. The recurrence
roll-forward is the only place the app touches a due date, but that is where the
code happens to use it rather than where a user needs it: a deadline is most
natural on a one-off, and "every Monday" needs none at all. Tying the field to
recurrence would hide it in the case it is most useful and show it in the case
it is least.

[FD] Worth knowing about `dueDate` before spending more on it: nothing reads it.
No list filters on it, nothing sorts by it, no row shows it, and overdue is
`scheduledDate.isBefore(today)`. `nextRecurringInstance` shifts it forward by
the same number of days as the scheduled date, keeping whatever gap the two had,
and that is the whole of its behaviour. It is a well-kept record with no reader.
If it is ever to mean something, the plumbing that keeps it meaningful across a
repeating series already exists and is tested.

[FD] The day is picked from a calendar; the due date is still typed. Natural
language stays where capture happens, in Quick Add, which is where speed is the
point. This is the organise-later step, where a specific day is usually wanted.
A deadline is more often described than located, so "next friday" still works
there.

[FD] No Time and no Reminder row. The design draws both. A task carries no time
of day, and reminders are named in `PRODUCT.md` but not built, so either would
be new functionality rather than a redesign.

[FD] Completion and deletion are deliberately absent; they have their own
interactions, and editing must not quietly finish or remove a task.

[FD] Validation shows in the field, not on the button: an invalid field is
marked with the Material error treatment and carries supporting text saying
what is wrong. The confirming action is disabled while any field is invalid.

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

[FD] A primary destination whose content is one task, not a list. Two states;
the product behaviour is in `focus.md`.

[FD] Both states are one layout, not two cross-faded against each other. The
container, the title and the action slot are each a single element that
changes. That is what lets the title stay still while the session forms around
it; see "Becoming the session" in `focus.md`.

## Ready

| Element | Treatment |
| --- | --- |
| Task title | `headlineMediumEmphasized`, `onSurface`, centred, heading semantics |
| Estimate | `bodyLarge`, `onSurface`, centred, under the title, omitted when absent |
| Start | the action slot: `primary` container, `onPrimary` label |
| Complete | `TextButton`, directly under the action slot |
| Top app bar | absent |
| Everything else | absent |

[FD] Two actions rather than one, and the weights say which is which. Starting
is the constructive act and takes the slot; completing without a session is the
shortcut and is text beneath it. An earlier version put both in the slot, where
the second competed with the one the screen is for; a later one put the
shortcut at the foot of the screen, where it was orphaned against the
navigation bar and a long way from the task it applied to.

[IMPL] The shortcut is always composed and faded rather than swapped in and
out, so the height it occupies is identical at every font scale and nothing
above it shifts when the session takes it away. Its semantics are cleared while
it is invisible, so a screen reader is not offered a control that is not there.

[IMPL] It sits outside the box that draws the container, not inside it. Inside,
it displaced the action slot upward while the container's rectangle went on
being computed as the bottom of that box; the two came apart and every label
ended up drawn on the wrong background.

[IMPL] Start's own container is transparent. The fill behind it is the drawn
container, because that is the thing that grows away when the session begins,
and a second container painted on top of it would stay behind and give the
trick away. The label colour is named for the same reason.

## Session

| Element | Treatment |
| --- | --- |
| Shape | a ring of `MaterialShapes`, `surfaceContainerHigh`, square, capped at 320dp |
| Task title | `headlineMediumEmphasized`, `primary`, centred, max 4 lines |
| Estimate | `bodyLarge`, `primary`, under the title |
| Complete | the action slot: one filled `Button` |
| Stop | `IconButton` with `ic_close`, top start |
| Next task | `bodyMedium`, `onSurfaceVariant`, at the foot of the screen |
| Top app bar | absent |
| Navigation bar | absent |

## The action slot

[FD] One slot, in both states, at `ButtonDefaults.MediumContainerHeight`. Ready
puts Start in it and Session puts Complete, so the control the session is for
appears exactly where the control that began it was.

[FD] The slot travels between the two states, because Ready reserves only the
title's height above it rather than the whole square. `focus.md` has the
reasoning under "Becoming the session".

[FD] Medium rather than large. The design draws its buttons at 84dp, which is
not one of Material's five button heights; large is a 96dp box a label has to
fit inside at every scale.

[IMPL] The medium height is a floor, not a fixed size. Pinned at exactly 56dp
the label was cut through the middle of its letters at 200% font scale, so the
button is allowed to grow to hold its own text, and the slot reports the size
it actually took. The container has to start from the rectangle the button
occupies rather than the one it was specified at, or the drawn pill and the
real button come apart.

## The foot of the screen

[FD] The peek at what follows, and nothing else. It is Session's alone; Ready
leaves it empty. It keeps its space when nothing follows, so the last task of a
session does not move the screen.

## Appearing and disappearing

[FD] Everything that comes and goes across the transform is driven off the
container's travel rather than given an animation of its own, and the swap is a
fade-through rather than a cross-fade: the outgoing label is gone before the
incoming one appears. `focus.md` records why both, under "Becoming the
session". The short version is that a cross-fade on an effects spec settles far
faster than the container moves, which left labels drawn on backgrounds that
had already gone.

## Both

[FD] Which shapes depends on whether the task carries an estimate: two walked
once from circle to clover when it does, a ring of six walked forever when it
does not. Both begin at the circle, which is where the container transform
leaves off. `focus.md` has the reasoning under
"Determinate and indeterminate"; the short version is that this is the
distinction Material's own loading indicator draws, and it is carried by the
kind of motion rather than by what any one shape means.

[FD] The shape is drawn, not clipped to. A `Shape` would have to be a new
object every tick to change, which puts the work in layout; drawing reads the
progress in the draw phase, where a changed value costs one redraw of one node.

[FD] Capped at 320dp so a wide window gets a shape, not a wall.

[FD] The title is the one piece of text in the app with a hard line cap, and it
is capped in both states. A fixed square cannot grow to fit, and a title that
overruns it is cut through the middle of a line, which reads as broken rather
than as shortened. Four lines is what the square holds at 200% font scale. The
cap applies in Ready too, where there is no shape yet, because the square is
reserved in both states and a title that overran it would collide with Start
and then be cut anyway the moment the session began.

[FD] The shape is `surfaceContainerHigh` with a `primary` title, not
`primaryContainer` with `onPrimaryContainer`. It is the ground the task sits
on, and a 320dp block of the brand colour is not ground. `primary` on a surface
container is the pairing a text button already uses, so it holds up under
dynamic colour.

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
