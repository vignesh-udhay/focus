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
rather than a transplanted screen. The lead card uses `Surface` inset on that
container, which is the same relationship the add button already had.

---

# The states

Six, and the board draws each.

| state | frame | when |
|---|---|---|
| Ordinary | `widget/Medium — Ordinary` | outstanding work, nothing urgent |
| Urgent, reminder passed | `widget/Medium — Urgent` | a reminder fired and the task is still open |
| Urgent, paused | `widget/Medium — Urgent, paused` | a focus session is paused |
| Just completed | `widget/Medium — Just completed` | a row was checked and has not refreshed away |
| Everything done | `widget/Medium — Done` | today had work and it is finished |
| Nothing scheduled | `widget/Medium — Nothing scheduled` | today had none |

Ordinary is also drawn at Compact and in dark and wallpaper-warm, six frames in
all, because it is the state that has to survive every palette. The others are
drawn once. The three colour treatments were verified node-for-node identical,
so a second and third copy of each state would add frames and no information.

Compact draws the urgent state too, and it draws only the lead card. That is the
point of the size rather than a compromise of it.

**Everything done and nothing scheduled are different days.** Today needs one
empty state because its Completed section sits on the same screen. The widget
has no such section, so a single state would tell someone who has just finished
six tasks that nothing was scheduled, which reads as the app forgetting their
day.

---

# The threshold

The widget leads with a task and its reason for `ResumePaused` and
`ReminderPassed`, which since D-035 is every reason there is.

**This used to be the widget's own threshold, and is now the rule.** D-031 gave
the widget a filter because `focusNowReasonOf` also matched `NoTimeToday`, for
any task scheduled today carrying no reminder, which on an ordinary day is most
of them. Permanently, in peripheral vision, that is the app insisting all day on
work whose entire claim is that someone put it on today.

D-035 accepted the same argument one surface inward and removed the reason
outright, on the further ground that in-app the card was then the first row of
the band directly below it, drawn larger and explained by that band's own label.
So the filter is gone: `focusNow` returns what the widget can lead with, and the
widget and Today decline together.

The two reasons left are the two where the widget does something no other
surface can. A notification is transient and a widget is permanent, so
`ReminderPassed` on the home screen is the durable backstop for a reminder that
was dismissed unread, which D-005 makes the highest-severity concern in the
product. `ResumePaused` turns three taps into one.

---

# The lead card

`Surface` on `Primary Container`, large corner, holding four things: a checkbox,
the reason in words, the title, and either the duration or a Resume action.

**The reason is not optional.** D-012 requires the card to say why a task is the
one, and a card that asserted without saying so would be the Focus queue D-004
removed. On a surface nobody asked to look at, an assertion with no reason is
worse than nothing.

The reason takes the app's own strings rather than new ones:
`today_focus_now_was_due_at` for a passed reminder, and
`today_focus_now_paused_remaining` for a paused session.

**The card carries a checkbox.** The most urgent task must be completable
without opening anything. A lead that could only be read would make the widget
worse than the notification it is backing up.

Only the paused state shows Resume, because only it has a session to return to.

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

**A count discloses only what did not fit.** `CompletedDisclosure.kt` sets the
app's default as "no count badge" and makes its own band a narrow exception
because it counts what is collapsed out of view. "+2 more" is that same
exception. "3 tasks left" above three visible rows is not, and it is gone.

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
widget has no band headings, so the words carry all of it.

**Nothing about this failure is widget-specific.** Any frame anywhere on the
board putting an accent on `Primary Container` has it, and sections 16 and 18
are where to look.

---

# Size

`minSdk = 29`, and that is the constraint shaping this section.

**On API 31 and above** the widget declares `targetCellWidth` and
`targetCellHeight` and the launcher sizes it in grid cells.

**On API 29 and 30** none of those exist. Sizing falls back to `minWidth` and
`minHeight` in dp with `resizeMode`, and the launcher's own cell arithmetic
decides the rest. Both sets must be declared. The older ones are not optional.

**Every launcher lets the user drag a corner**, so the drawn sizes are samples
of a continuum rather than an enumeration. `SizeMode.Exact` reports the size the
widget actually has, and `rowCapacity` divides it. D-041.

**This paragraph used to argue the opposite**, and the correction is worth
keeping visible. It said `SizeMode.Responsive` "picks the largest that fits,
which matches the design", and that `SizeMode.Exact` "would invite per-size
layouts and is the wrong tool here". The first half named the continuum and then
chose the API that quantises it: under `Responsive`, `LocalSize` reports the
matched member of the declared set, so the composable was told 190 or 266 and
nothing else however large the widget was. "Picks the largest that fits" is true
of what the launcher draws, not of what the code is told.

**The second half was a real worry and it is kept as a rule.** An accurate size
does tempt code to branch on it and grow a second layout, which is the drift
D-031 forbids. The guard is not an inaccurate number, it is that **`rowCapacity`
is the only reader of the size.** A second reader is the thing to refuse.

**There is no maximum size.** `maxResizeWidth` and `maxResizeHeight` are absent
on purpose: the widget grows as far as it is dragged and fills what it is given.
They previously held 364x266, which is the Medium frame reused as a limit rather
than a decision, and with `minResize` equal to `minWidth` and `minHeight` the
resizable band was 76dp in each axis, so the launcher drew handles that could
not move.

**The minimums are sized to the layout.** `minResizeHeight` is 140dp: 60 of
header, 20 of disclosure and 8 of bottom inset leave 52, which is one 48dp row,
and the hard floor is 136. `minResizeWidth` is 250dp and is conservative, since
a row spends 48 on its checkbox and 70 on its duration column before the title
is given anything. TickTick's task list reaches 110dp with simpler rows. Taking
ours lower wants a render on a device rather than an argument.

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

`+N more` is the exception, and deliberately: it sits on the row titles at 58,
because it stands in for rows that did not fit rather than heading a group.
`today-screen.md` draws the same distinction the other way round for a band
label, which does head a group and so sits at its edge.

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

Five targets, and only one of them stays on the home screen.

| target | does |
|---|---|
| checkbox | completes, in place, without leaving |
| row | opens Task Details for that task |
| lead card Resume | resumes the paused session |
| add button | opens Quick Add |
| anywhere else | opens Today |

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

**`CurrentDay` does not reach here.** `SystemCurrentDay` registers a
process-scoped receiver and is deliberately never unregistered, which is right
for the app and irrelevant to a widget: the RemoteViews are hosted by the
launcher and the app process may not be running at all. The widget needs its own
answer to midnight, or it shows yesterday's Today until something else wakes it.

**`updatePeriodMillis` is not that answer.** Its floor is 30 minutes and it
wakes the device to fire. A task list that changes when the data changes should
update when the data changes.

Three triggers, none of them a timer:

- the task data changing, observed from Room;
- the day rolling over, from the same date, time and time-zone broadcasts
  `SystemCurrentDay` already listens for;
- a reminder firing or being acted on, which is when the lead state flips.

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

**Capacity is the reported height, and since D-041 that height is real.** D-036
replaced a breakpoint table with arithmetic, because a widget is resized by
dragging and most are neither drawn size: one taller than Compact and a little
narrower than Medium used to fall to Compact, and Compact with a lead card is no
rows, so a large widget drew one card over an empty container.

**The arithmetic then spent two versions being fed a constant.** The two
`DpSize`s survived D-036 on the grounds that "Glance needs them to choose which
RemoteViews it builds", which was true and carried a consequence nobody traced:
`SizeMode.Responsive` also decides what `LocalSize` reports, so the height was
always 190 or 266. A widget with room for five rows drew three and left the rest
blank, which is the bug D-036 was written to prevent, surviving D-036. The
`DpSize`s are gone with `SizeMode.Exact`.

**API 29 and 30 use the declared dp minimums and the 28dp fallback corner.** The
resource and provider fallbacks are implemented. They still need visual
verification on an API 29 or 30 launcher; the available emulator is API 37.

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
closed; `updatePeriodMillis` remains zero. The widget reads its own current day
because the launcher's `RemoteViews` cannot share the screen's `CurrentDay`
collector. Storage refreshes are conflated but never cancelled once started,
so a second Room emission cannot withdraw the Glance update already being
enqueued and leave the launcher showing stale tasks. Task and Focus signals
share that one serialized path. The observer skips Room's initial snapshot:
starting a Glance worker creates the Application, and enqueuing again from that
initial value cancels the worker that is already rendering. Provider and clock
broadcasts already own initial/closed-process refreshes. Checkbox roles are
resolved to concrete colours before building `CheckBoxColors`: Glance's
dynamic roles are resource-backed providers, and its custom checked/unchecked
API rejects those providers at runtime even though the code compiles.
