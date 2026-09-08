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
`ReminderPassed`. It does not lead for `NoTimeToday`.

`focusNowReasonOf` matches `NoTimeToday` for any task scheduled today carrying
no reminder, which on an ordinary day is most of them. In the app that is a weak
but real answer to a question the user asked by opening it. Permanently, in
peripheral vision, it is the app insisting all day on work whose entire claim is
that someone put it on today.

No new rule implements this. `FocusNowReason` is declared in priority order and
is `Comparable` by it. The widget reads further up the same enum than the card
does, and that is the whole difference.

The two reasons kept are the two where the widget does something no other
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
`targetCellHeight` and the launcher sizes it in grid cells. `maxResizeWidth` and
`maxResizeHeight` bound the drag.

**On API 29 and 30** none of those exist. Sizing falls back to `minWidth` and
`minHeight` in dp with `resizeMode`, and the launcher's own cell arithmetic
decides the rest. Both sets must be declared. The older ones are not optional.

**Every launcher lets the user drag a corner**, so the two drawn sizes are two
samples of a continuum rather than an enumeration. Glance's
`SizeMode.Responsive` takes a set of breakpoints and picks the largest that
fits, which matches the design: size changes how many rows fit and nothing else.
`SizeMode.Exact` would invite per-size layouts and is the wrong tool here.

**What grows is rows of today, never days.** Upcoming on the home screen would
invite the planning mindset the app is built to avoid, and the widget is called
Today.

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
the widget is worth a home-screen slot at all. **It is currently drawn quieter
than the add button, which only launches.** Whether that visual weight is right
is an open question below.

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
returns the id of the occurrence it spawned so a caller can take both back. A
widget with no undo drops that return value, which means a mis-tap on a
repeating task also creates a row. This is the strongest argument for a
widget-native undo, and it is unresolved below.

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
and the only destructive one.

Each row announces as one thing, with the duration spoken as words rather than
as `45m`, which `durationLabel` already provides through its `spoken` field.

Font scale is the untested risk. A widget cannot scroll, so at 200% the row
count has to fall rather than the text clip, and that is a thing to verify on a
device rather than assert here.

---

# Out of scope

- Upcoming, or any day other than today
- A widget configuration activity. There is nothing to configure that the app
  does not already own.
- Reordering, editing or deleting from the widget
- A count of anything, beyond disclosing rows that did not fit
- A second widget of a different shape

---

# Open product decisions

D-031 parked four questions here rather than guessing at them.

**Does the add button survive at Compact?** It is the highest-contrast element
on the widget and it only launches the app, which the row and the header also
do. At Compact it and the header consume roughly a third of the surface to leave
two rows. The case for keeping it is that capture from a home screen is
genuinely fast. The case against is that it outranks the checkbox, which is the
only control that does something here.

**Is a checked row tappable to undo?** Showing what happened is settled. A
widget-native undo is not, and recurrence raises the stakes.

**What are the responsive breakpoints?** The two drawn sizes are samples. The
real set has to come from the cell grid, not from the board.

**How does the widget behave on API 29 and 30**, where the corner dimens and the
cell-sizing attributes do not exist? The fallback is described above but has not
been seen on a device.

---

# Implementation status

**Not built.** No Glance dependency, no `appwidget` receiver in the manifest, no
`ui/widget` package. Every statement in this document describes a design, not
behaviour.

**Ready for it:** `TaskCompletion` is already the shared completion path and
names the widget as a caller. `focusNow` is a pure function over data and needs
no view model. `durationLabel` and `taskMetadata` are the row's vocabulary.

**The one thing the app has that this cannot reuse:** `CurrentDay`.
