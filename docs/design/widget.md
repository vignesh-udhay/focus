# Home widget

Today, on the home screen, for someone who did not ask.

`docs/decisions.md` D-031 settles what this surface is for and what it is
allowed to say. This document is the rest: the anatomy, the sizes, the platform
constraints, and the questions D-031 deliberately left open. Section 10 of the
board draws it in twelve frames.

The one sentence to carry into every decision below is D-031's: **the bar for
speaking up scales with how much the user asked to be there.** Today is
consulted. The widget is glimpsed.

---

# Design system

Less of this surface is ours than any other screen in the app.

**Ours:** the container fill, the type, the row anatomy, the words, and which
task appears. **The launcher's:** the placement, the grid, the final clipping,
and the label underneath. **The platform's:** the corner radius, the resize
handles, and the update scheduling.

Everything drawn sits on `Primary Container` rather than the `Surface` the app's
lists use. That is deliberate, and it is the one place the widget is allowed to
look unlike the app: a widget has to be identifiable at a glance over an
arbitrary wallpaper, and `PRODUCT.md` principle 5 asks for platform conventions
rather than a transplanted screen. The add button uses `Surface` inset on that
container, and since D-049 removed the lead card it is the only thing that does.

---

# The states

Four since D-049 removed the two urgent ones, and the board draws each. The
`Urgent` and `Urgent, paused` frames are the lead card and no longer describe
anything the widget can be in.

| state | frame | when |
|---|---|---|
| Loading | not drawn | the header alone, before the first read lands |
| Ordinary | `widget/Medium — Ordinary` | outstanding work, under its bands |
| Just completed | `widget/Medium — Just completed` | a row was checked and has not refreshed away |
| Everything done | `widget/Medium — Done` | today had work and it is finished |
| Nothing scheduled | `widget/Medium — Nothing scheduled` | today had none |

Ordinary is also drawn at Compact and in dark and wallpaper-warm, six frames in
all, because it is the state that has to survive every palette. The others are
drawn once. The three colour treatments were verified node-for-node identical,
so a second and third copy of each state would add frames and no information.

**Everything done and nothing scheduled are different days.** Today needs one
empty state because its Completed section sits on the same screen. The widget
has no such section, so a single state would tell someone who has just finished
six tasks that nothing was scheduled, which reads as the app forgetting their
day.

**Loading is a state and not a wait**, which is what keeps the widget off the
launcher's spinner. Until `provideContent` is called, Glance publishes nothing,
and a session that has published nothing has not started its 45-second clock
either: the only timer that can be running is the five-second idle one. A phone
that signals idle during a slow first read kills the session before it draws, and
Glance reports the worker as a success. With `updatePeriodMillis` at zero, the
widget then keeps the loading layout until the user happens to change a task.
Drawing the header immediately closes that window. D-051.

It is deliberately not the same as nothing scheduled: one is the app not knowing
yet, the other is the app knowing the day is empty, and only the second gets a
line of text.

---

# The bands

The widget draws Today's bands, labelled with Today's own strings: Overdue, No
time set, Later today. Completed is excluded. D-049.

**They are here because D-048 took away the thing that explained the list.** The
lead card named one task and gave a reason, and the rows below it read as
"everything else". With the card gone the rows are one undifferentiated run whose
order the user has to infer, and that order is the whole point: past, present,
future, with the product's defining failure at the top.

**A band is not an assertion**, which is what keeps this inside D-031. The
widget still never says which task to do and never counts anything. The rows were
already in this order; the labels name the boundaries that were always there.

**Completed stays off.** On Today it is a disclosure the user can open, and a
widget has nothing cheap to open into. Finished work is also the one thing a
surface crossed involuntarily never needs to carry. The just-completed row is a
different matter and stays, because it is evidence of what the user did rather
than a list of what they finished.

**A band with nothing in it draws no heading**, so a day with nothing overdue has
no empty label to explain. That falls out of `todaySections`, which the widget
shares with Today rather than reimplementing.

---

# The band heading

12sp Bold on `On Primary Container`, aligned to the content inset with the
"Today" title, sitting on a 28dp row with its space above the label.

**Bold and smaller, where Today is regular and quieter.** Today reaches for
`onSurfaceVariant` to set a heading back. This surface cannot: the contrast
table below rules out both accent colour and reduced alpha against a warm
`Primary Container`, leaving size and weight as the only levers. Smaller and
heavier than a 14sp Medium row title reads as a label rather than as a louder
row.

**The space sits above the label, not below it**, so a heading groups downward
with the rows it introduces rather than floating between two bands.

**It carries its own tap target.** The root's "anywhere else opens Today" cannot
reach it, because a heading is an item inside the collection and `AbsListView`
consumes every touch in its bounds before the root sees it. Rows never noticed
this, having targets of their own. A heading had none and was inert until it was
given one.

---

# The row

Same anatomy as `TaskListRow`, and for the same reasons.

    [ ] Title                         45m
        2:00 PM

**The trailing column is the duration and nothing else.** Date and repeat answer
*when*; a duration answers *how much*. Mixed into one column they stop being
scannable, and scanning is the only thing this surface is for. The board used to
put "Overdue" in that column. It does not now.

**The metadata line appears only when there is something to say**, which is what
`taskMetadata` already does: reminder time first, then the date, then the
repeat. A row with none of them is one line tall, and on a widget that is a row
of someone else's content bought back.

**A title that does not fit is truncated, not wrapped.** A wrapped title costs a
whole row at Compact width, and rows are the scarcest thing here.

**There is no count, and there is nothing left for one to disclose.** D-043 made
the rows a scrolling collection, so no task is withheld and `+N more` is gone
with the state it described. `CompletedDisclosure.kt` still sets the app's
default as "no count badge", and the widget now takes that default rather than
the narrow exception D-031 carved for it. "3 tasks left" above three visible rows
was never the exception and went long before.

---

# Colour carries nothing

Measured against the board's own variables:

| pairing | Light | Dark | Wallpaper warm |
|---|---|---|---|
| `Tertiary` on `Primary Container` | 4.99:1 | 5.51:1 | **3.53:1** |
| `Error` on `Primary Container` | 4.90:1 | | **3.61:1** |
| `On Primary Container` at 85% | 5.07:1 | | **3.88:1** |

Warm `Primary Container` is a mid-luminance orange. No accent clears AA against
it for a 12sp line, and reducing opacity moves away from the threshold rather
than toward it. M3 offers no `onPrimaryContainerVariant` to retreat to.

So the widget spends no colour on meaning and no alpha on hierarchy. Overdue is
signalled by the date itself, and row hierarchy is 12sp Regular against 14sp
Medium.

This is the position the app already holds rather than a concession.
`TaskRow.kt` treats the overdue colour as "the second cue" on top of the words,
and Today keeps overdue readable without it because a band heading names it. The
widget has that heading too since D-049, so the two surfaces now carry overdue
the same way: a label above, a date in the row, and no colour in either.

**Nothing about this failure is widget-specific.** Any frame anywhere on the
board putting an accent on `Primary Container` has it, and sections 16 and 18
are where to look.

**Colour on this surface has to be deferred, not resolved.** Glance emits a
day/night pair for each colour and the launcher picks at draw time, which is what
lets the widget follow the system without being rebuilt. Anything resolved to a
single value in the app process instead is frozen at whatever mode that process
was in when the Glance session last ran, and a phone on scheduled dark mode
breaks the match every sunrise. The checkbox was the one thing doing that, which
is why it was invisible in whichever mode the widget was not built in. D-050.

---

# Size

`minSdk = 29`, and that is the constraint shaping this section.

**On API 31 and above** the widget declares `targetCellWidth` and
`targetCellHeight` and the launcher sizes it in grid cells.

**On API 29 and 30** none of those exist. Sizing falls back to `minWidth` and
`minHeight` in dp with `resizeMode`, and the launcher's own cell arithmetic
decides the rest. Both sets must be declared. The older ones are not optional.

**The widget does not read its own size at all.** D-043 made the rows a
`LazyColumn`, which is a `ListView`, so the platform fills the height and scrolls
the remainder. `sizeMode` is `SizeMode.Single`: one layout, built once, stretched
to whatever the launcher gives it.

**Three entries argued about this and the last one deleted the question.** The
short history, because it is the most instructive thing in this document.
`SizeMode.Responsive` was chosen first, on the reasoning that the drawn sizes are
"samples of a continuum rather than an enumeration" followed by the API that
enumerates. D-036 then replaced a breakpoint table with arithmetic over the
reported height. D-041 found that the arithmetic had been fed one of two
constants for its whole life, because `Responsive` also decides what `LocalSize`
reports, and switched to `Exact`. D-043 removed the arithmetic instead.

**The rule the old argument was protecting outlived it and is now free.** The
worry was that an accurate size "would invite per-size layouts", which is the
drift D-031 forbids, and the guard was that `rowCapacity` be the only reader.
There is no reader and no `rowCapacity`. Size affects nothing, which is the
strongest available form of "size changes how many rows fit and nothing else".

**There is no maximum size.** `maxResizeWidth` and `maxResizeHeight` are absent
on purpose: the widget grows as far as it is dragged and fills what it is given.
They previously held 364x266, which is the Medium frame reused as a limit rather
than a decision, and with `minResize` equal to `minWidth` and `minHeight` the
resizable band was 76dp in each axis, so the launcher drew handles that could
not move.

**The minimums are sized to the layout.** `minResizeHeight` is 140dp, which was
derived when the model still reserved 60 of header, 20 of disclosure and 8 of
bottom inset and needed 48 left for a row. D-043 removed the disclosure and the
reservation, so 140 now holds a header and rather more than one row, and it is
kept because a widget shorter than that is a scroll view with nothing visible in
it. `minResizeWidth` is 250dp and is conservative, since a row spends 48 on its
checkbox and 70 on its duration column before the title is given anything.
TickTick's task list reaches 110dp with simpler rows. Taking either lower wants a
render on a device rather than an argument.

**What grows is rows of today, never days.** Upcoming on the home screen would
invite the planning mindset the app is built to avoid, and the widget is called
Today.

---

# The content column

Two insets, named in `FocuslistWidget.kt` rather than spelled out per element.

    ContentInset   24dp   where text begins and ends, both edges
    SurfaceInset    8dp   where a filled surface's edge and a 48dp target begin

A target that starts at `SurfaceInset` carries its glyph 15dp inside itself, so
the glyph lands at 23, one dp inside the text column. That is below perception,
and it is what lets the checkbox keep all 48dp of itself while still reading as
one column with the text above it.

The lead card takes `SurfaceInset` too, so its edge and the rows' targets start
together, the way `today-screen.md` has the Focus now card and the bands share a
left edge. Its own trailing control lands back on `ContentInset`, measured from
the widget rather than from the card.

`+N more` used to be the exception, sitting on the row titles at 58 because it
stood in for rows rather than heading a group. It is gone with D-043, and so is
the only element in the widget that was not on one of the two insets.
`today-screen.md` keeps the same distinction for a band label, which does head a
group and so sits at its edge.

**This section exists because none of it was written down.** The insets had been
18 in the header, 18 in the empty states, 8 on a row, 24 at the end of one, and
58 on the disclosure, so each new element picked whichever number was nearest in
the file and nothing lined up with anything.

**A Glance `padding` is the view's own padding, not a margin.** The lead card
carried `.padding(horizontal = 8.dp).background(surface)` and drew its surface
edge to edge with the 8dp inside it, which on a launcher reads as the card having
escaped the widget that is supposed to contain it. A margin in Glance is a parent
carrying the padding, which is how the card is built now.

---

# Corners and background

`android.R.dimen.system_app_widget_background_radius` is API 31 and capped at
28dp, which is where the board's 28 comes from. `system_app_widget_inner_radius`
governs anything nested inside it.

**Read them, do not hardcode them.** The launcher clips to its own shape, and a
hardcoded corner that disagrees shows as a seam. On API 29 and 30 neither dimen
exists and the widget draws its own background at the same value.

---

# Interaction

Four targets, and only one of them stays on the home screen. Resume left with the
lead card in D-049.

| target | does |
|---|---|
| checkbox | completes, in place, without leaving |
| row | opens Task Details for that task |
| add button | opens Quick Add |
| anywhere else | opens Today |

**"Anywhere else" is harder to keep true than it looks**, because most of the
widget is inside a `ListView` and `AbsListView` consumes every touch in its
bounds whether or not anything in it is clickable. The root's target never sees
them. Two things have already been lost to this and both are now fixed: the space
below a short list, where D-043's weighted list stretched over pixels it had no
rows for and left about a quarter of a three-task widget inert (D-047), and the
band headings, which are items in the collection with no target of their own
until D-049 gave them one.

Anything new drawn inside that list needs its own action, or it is dead.

The checkbox is the only one that does not launch the app, and it is the reason
the widget is worth a home-screen slot at all. **It is drawn quieter than the
add button, which only launches.** The add button remains at Compact after
consultation: capture from the home screen is useful enough to keep the direct
target. Its visible circle is 32dp, aligned to the visual height of the Today
title, while its Android touch area remains 48dp. The checkbox also keeps its
full 48dp touch area.

---

# Completing from the home screen

**A completed row stays in place, checked, until the next refresh.** It does not
disappear on tap.

The failure this prevents is specific. Three 48dp targets, read while walking,
and a mis-tap completes the wrong task. If the row vanishes immediately, the
mistake erases its own evidence: nothing on screen says which task moved, and
recovering means opening the app and expanding a Completed disclosure that is
collapsed by default. `TaskListViewModel.kt` states the app's view of that trade
plainly, that one failure is unrecoverable and invisible while the other costs a
glance.

Keeping the row is also honest about the platform, where the update is
asynchronous and the row would linger anyway.

**Completion goes through `TaskCompletion`**, not a second implementation. Its
own documentation anticipated this surface: one operation needed identically by
a view model, a notification action and later the widget.

**Recurrence makes the mis-tap more expensive.** `TaskCompletion.complete`
returns the id of the occurrence it spawned so a caller can take both back. The
checked row is tappable to undo after consultation. It goes through
`TaskCompletion.reopen`, so a still-untouched recurring occurrence is taken back
with the completion rather than being left as work nobody asked for.

---

# Staying current

**The widget observes its data rather than reading it, because a Glance session
outlives an update.** Everything above `provideContent` runs once per session,
not once per update, and a session lives 45 seconds past its first composition.
A value read up there was therefore the only data the widget had for that whole
window: `updateAll` posts an event that forces a recomposition, and recomposing
re-rendered the captured value instead of re-reading it. Tasks, the day, the
stored Focus session and the completion evidence are one `Flow` collected inside
the composition, so a change during a session recomposes the widget. D-045.

**`updatePeriodMillis` is not a refresh strategy.** Its floor is 30 minutes and
it wakes the device to fire. A task list that changes when the data changes
should update when the data changes.

Four triggers, none of them a timer:

- the task data changing, observed from Room;
- the day rolling over, which reaches a live session through `CurrentDay` and a
  dead one through the date, time and time-zone broadcasts a manifest receiver
  listens for;
- the stored Focus session being saved or cleared;
- a reminder firing or being acted on, which is when the lead state flips.

**Something still has to start a session.** While none is running nothing is
collecting, so the application's own observer is what wakes the widget. It calls
`updateAll` on every task and Focus write: that starts a session when there is
none, and is a cheap event when there already is one.

**`SystemCurrentDay` is process-scoped, which is why it is not the whole answer
to midnight.** Its receiver is deliberately never unregistered, which is right
for the app and says nothing about a launcher hosting RemoteViews with the app
process dead. The manifest receiver covers that case. `CurrentDay` covers the
other one, where a session is alive as midnight passes.

---

# Theme

**The widget follows the system, not the Settings theme choice.**

It sits among other widgets on the launcher's surface, where looking wrong
beside its neighbours costs more than differing from an in-app setting the user
is not currently looking at. The tempting escape hatch is a widget-theme row in
Settings, and D-024 closed that list at four rows.

Dynamic colour comes from `GlanceTheme` and is API 31 and above. Below that the
widget takes the app's own light and dark palettes, which is what sections 16
and 18 of the board draw.

---

# Accessibility

The checkbox is 48dp and stays 48dp. It is the smallest target on the surface
and the only destructive one. The native RemoteViews checkbox is narrower than
Material's 48dp target and otherwise sits at its leading edge, so its glyph is
inset 15dp horizontally inside that target. A one-line row centres the native
glyph inside the target with a 7dp top inset. A row with metadata takes no top
inset, aligning the glyph with the title instead of centring it against the
combined title-and-metadata block. The tappable bounds do not move.

Each row announces as one thing, with the duration spoken as words rather than
as `45m`, which `durationLabel` already provides through its `spoken` field.

At 150% and above the model reduces the row count rather than asking a widget
that cannot scroll to clip its text. That rule has JVM coverage; 200% still
needs visual verification on a launcher.

---

# Out of scope

- Upcoming, or any day other than today
- A widget configuration activity. There is nothing to configure that the app
  does not already own.
- Reordering, editing or deleting from the widget
- A count of anything, beyond disclosing rows that did not fit
- A second widget of a different shape

---

# Resolved decisions and platform verification

**The add button survives at Compact.** The direct capture path is worth its
space and the user chose to keep it after reviewing the tradeoff.

**A checked row is tappable to undo.** Both its checkbox and its body reopen the
task through `TaskCompletion`, including safe recurrence cleanup.

**The board's 288x190dp and 364x266dp are drawings, not declarations.** They are
the smallest complete Compact and Medium layouts and they remain what the frames
show. They are no longer given to Glance as anything.

**There is no capacity.** The rows are a `LazyColumn`, so every outstanding task
for today is handed to the platform and the overflow scrolls. D-043.

**What that replaced is worth recording, because it took three entries.** D-036
turned a breakpoint table into arithmetic over the reported height, since a
dragged widget is rarely a drawn size. D-041 found the arithmetic had always been
fed one of two constants: the `DpSize`s survived D-036 on the grounds that
"Glance needs them to choose which RemoteViews it builds", which was true and
carried an untraced consequence, that `SizeMode.Responsive` also decides what
`LocalSize` reports. A widget with room for five rows drew three, which is the
bug D-036 was written to prevent, surviving D-036.

D-043's reading is that measuring was the liability rather than any of the three
implementations of it. A `ListView` works out how many rows fit without being
told the height, and the obligation the model carried, that a composable changing
height without changing `RowHeight` silently breaks the calculation, is gone with
the calculation.

**`WidgetScrollTest` is what stops this regressing quietly.** It composes the
widget to `RemoteViews`, inflates them, and asserts an `AdapterView` is in the
tree. A `Column` produces a `LinearLayout` that clips, and the test was checked
against exactly that before being trusted: it fails on the old shape and passes
on the new. Confirmed against a real launcher as well: the Pixel launcher's view
hierarchy reports an `android.widget.ListView` inside the hosted widget.

**The widget's data is a stream, not a snapshot.** `provideGlance` runs once per
Glance session rather than once per update, so a value read there was frozen for
the session's whole life. Tasks added in the app did not arrive, and unchecking
a row the user had just checked did nothing, because the check had opened the
session the uncheck was then trapped inside. Collected inside `provideContent`
instead. D-045.

**`WidgetSnapshotsTest` is what stops that one regressing.** Six assertions that
a change to a source reaches a collector already listening. Four of them fail if
the flow is reduced to a single read, which was checked before they were
trusted.

**The lead card's item id is `Long.MAX_VALUE`.** `Long.MIN_VALUE` reads like the
obvious pick for "no task could be this" and is in fact
`LazyListScope.UnspecifiedItemId`, so passing it asked Glance to invent an id.
It was spelled that way from D-043 until D-045.

**API 29 has now been looked at, on an emulator installed for the purpose.** The
widget renders, the collection is populated by the bound `RemoteViewsService`
rather than carried inside the RemoteViews, rows scroll, checkboxes complete
without leaving the launcher, and a content-height list measures correctly there
too, which is what D-047 needed to know before shipping.

**What that render also showed: there is no fallback corner.** Glance's
`cornerRadius` is documented "Only works on Android S+" and is a no-op below it
for both the `Dp` and the resource form, so `values/widget_dimensions.xml`'s 28dp
is never applied. On API 29 and 30 the widget is a hard-edged rectangle and the
add button is a square rather than a circle. The `values-v31` override is
correct and does its job from 31 up. Fixing the lower band means a shape drawable
behind the surface instead of a colour plus a radius, which is a change to how
every filled surface in the widget is drawn and has not been made.

---

# Implementation status

**Built with Jetpack Glance.** One provider renders the six states,
uses dynamic colour on API 31 and later with Focuslist light/dark fallbacks,
keeps add at both sizes, and keeps a just-completed row in place until the next
data or day change. Its checked row is the undo target the consultation chose.

`TaskCompletion` remains the shared completion path. `focusNow` supplies the
candidate before the widget applies its stricter threshold, and the widget's
model is a pure function covered by JVM tests.

Room changes and persisted Focus-session changes update every instance. A
manifest receiver covers date, time and timezone changes while the app is
closed; `updatePeriodMillis` remains zero. The day comes from the application's
`CurrentDay` while a session is running and from that receiver when none is,
which are the two halves of the same answer rather than a duplicate of it.
Storage refreshes are conflated but never cancelled once started,
so a second Room emission cannot withdraw the Glance update already being
enqueued and leave the launcher showing stale tasks. Task and Focus signals
share that one serialized path. The observer skips Room's initial snapshot:
starting a Glance worker creates the Application, and enqueuing again from that
initial value cancels the worker that is already rendering. Provider and clock
broadcasts already own initial/closed-process refreshes. Checkbox roles are
resolved to concrete colours before building `CheckBoxColors`: Glance's
dynamic roles are resource-backed providers, and its custom checked/unchecked
API rejects those providers at runtime even though the code compiles.
