# Reminder health

The screen that answers one question:

    Can this app be relied on to interrupt me?

`PRODUCT.md` principle 1 says a reminder that does not fire is the highest
severity bug in the product, higher than a crash, because a crash is visible and
a miss is not. This screen is the app admitting that in public.

**This document was written late.** The screen has 875 lines of code across
three files, sixteen board frames across two chapters, and had no design
document while every other screen had one. Most of the reasoning below already
existed as KDoc and is lifted here rather than invented.

---

# Why it exists at all

`docs/decisions.md` D-009 is the entry. Three permissions can all report success
on a device that delivers reminders a minute late, so a screen built on
permissions alone reports green on a phone that is failing.

So the model holds both: what the platform says the app is allowed to do, and
what actually happened when it tried. **Where they disagree, what happened
wins.** That rule is why `ReminderHealth` is a type rather than the screen
reading three booleans.

---

# The three checks

    Notifications     without this the alarm fires and nothing appears
    Exact alarms      without this the notification appears, late
    Background work   without this it might not appear at all

They are ordered by what a failure costs, not by how the screen lays them out.
`firstFailing` walks them in that order, so the screen's headline names the
worst thing wrong rather than the first thing listed.

**Background work is the vague one, by necessity.** Android's own battery
allowlist is one part of it. The manufacturer features that actually delay
alarms are not visible to any API and have to be inferred from the
manufacturer.

---

# What the screen leads with

    Missed reminder  →  a delivery actually went wrong
    Action needed    →  a check is failing
    Ready            →  all three pass
    Checking         →  nothing has been read yet

**A missed reminder outranks a failing check**, even though the failing check is
usually its cause. The user experienced the late reminder; the permission is the
explanation, and it is still on screen underneath. Leading with the explanation
would be the app talking about itself.

`ActionNeeded` carries which check caused it, because the three fail differently
and one sentence for all of them would be wrong two times out of three. That is
not hypothetical: the first build of this screen blamed the manufacturer for a
notification permission the user had refused.

---

# Naming the restriction

Three pieces, each doing a different job:

    title    OnePlus may put Focuslist to sleep      who is doing it
    body     Sleep standby can delay reminders.      what it is called
    button   Open Sleep standby settings             where to go

The title takes `Build.MANUFACTURER`, normalised for casing. The body and the
button take the feature name.

**The feature name is the one that matters**, and it is why `DeviceRestriction`
is carried at all: "Sleep standby" is findable in the user's settings app where
"background restrictions" is not. Several vendors ship the same idea under the
same word, so the enum is named for the feature rather than the vendor.

The manufacturer is in the title anyway because it says who to be annoyed with,
and the actionable name appears twice below it.

---

# What the app will not claim

None of the manufacturer features is visible to any API.
`isIgnoringBatteryOptimizations()` reports Android's own allowlist and nothing
else, and on the device D-009 was measured against, joining that allowlist
changed the alarm's flags and left its delivery window untouched.

So the app infers the feature from the manufacturer and says what it cannot
know, rather than claiming a state it has not measured. `CheckState.Warning`
exists for exactly this: known to be a risk, not known to be failing.

---

# Layout

A room: back arrow, no navigation bar, reached from the app-bar overflow.

    Room header
    Headline card          the state, its eyebrow, and one sentence
    Three check rows       one segmented group, 2dp gap
    Primary action         Test reminder, or Open <the right settings>
    Last checked           Ready only

The check rows are a segmented group like every other list in the app, First,
Middle and Last, with their radii bound to `Segmented/Radius outer` and
`inner`. They are not interactive and carry no pressed or focused state: they
report, they do not navigate.

**The status glyph has no container.** It sits directly in a 40dp leading slot,
which keeps the tick, the question mark and the exclamation on one axis without
drawing a circle behind them.

Each row used to carry a 40dp badge, and it was never doing consistent work: on
a `Blocked` row the badge took the error container and so did the row, which
made the circle invisible in the one state that matters most. Three stacked
circles also compete with the text they are annotating, and a container inside a
container is the thing this design system keeps removing.

Without it the glyph carries the colour: `primary` for a tick, `onSurfaceVariant`
for a question mark, `onErrorContainer` for an exclamation. That last one now
reads more strongly than it did behind a badge of the same colour as its row.

**A 12dp gap sits between the headline and the group.** It is worth stating
because the headline grows with its copy while the rows do not, and a longer body
has already eaten the gap once and overlapped the first row by 8dp.

The eyebrow is sentence case. It was `READY`, `ACTION NEEDED` and
`MISSED REMINDER` in literal capitals in the string values, which the Focus now
card had already moved away from, and which hands a screen reader an
initialism and a translator English casing rules.

**The content column is 24dp rather than the 16dp the lists use**, matching
Settings. That is not currently justified anywhere and is worth settling the
next time either screen is touched.

---

# Testing a reminder

Ready offers "Test reminder in 30 seconds", which schedules a real reminder
through the real path and tells the user they can close the app.

Thirty seconds is long enough to leave the app, which is the point: a test that
only fires while you are watching tests the half that was never in doubt.

---

# Certainty, not just severity

`CheckState` has three values and the screen draws three. D-021 is the entry.

    Ok        nothing wrong that the app can see
    Warning   known to be a risk, not known to be failing
    Blocked   known to be failing, the app has been refused

**The app colours what it knows.** `Blocked` takes the error container, because
the app was refused and can say so. `Warning` takes the ordinary row surface and
a question mark, because the app inferred it from `Build.MANUFACTURER` and has
measured nothing. A guess gets words and a mark, not a tint.

The headline follows the same rule. A device whose only problem is an inferred
restriction reads **Worth checking** on a neutral card, not **Action needed** in
error colours, and the body says what the app cannot know:

    Sleep standby can delay reminders.
    Focuslist cannot tell whether it is on.

**The screen used to render Warning and Blocked identically.** `Badge()`
branched on `Ok` against not-`Ok`, so a feature the app was guessing at looked
exactly like a permission the user had refused. Because the inference is by
manufacturer, that meant every OnePlus, OPPO, Realme, Xiaomi, Redmi, POCO,
Samsung, Huawei and Honor user saw a permanent red "Action needed" from first
launch. A reliability screen that is always red teaches people to ignore it,
which is the one thing this screen cannot afford.

**Tertiary was tried first and rejected on the render.** The obvious answer was
a third colour, and the app already uses `tertiary` for an overdue date. It does
not work here: this palette puts `tertiaryContainer` at `#FFD7E3` and
`errorContainer` at `#FFD8D6`, one step apart in green, so the caution and the
error were indistinguishable. That is worth knowing beyond this screen. The
Focuslist palette has three usable container families, lavender, pink and
neutral, and primary/secondary and tertiary/error each collapse into one.

**What stays loud.** A refused permission is an error, because the app was told.
A late delivery is an error, because it happened. So a user who ignores the
caution and then misses a reminder still gets the red screen.

**Why not stay silent until something actually goes wrong.** Considered and
rejected. It is the more honest position and it accepts one missed reminder as
the price of learning, which principle 1 forbids. The warning is pre-emptive; it
just has to be accurate about its own certainty.

**The same line decides what leaves this screen.** Since D-040 Today carries a
banner, and it draws for `ActionNeeded` and `Missed` only. `WorthChecking` stays
here. The difference is who asked: a user who opens Reminder health wants the
caution and can weigh it, and a user who opens Today did not ask anything. A
permanent, unclearable notice on the default screen of four vendors' phones is
the "always red" failure above, moved somewhere it would be seen far more often.
`today-screen.md` has the banner's own section.

---

# Out of scope

- a history of past deliveries; `latestConcern` is the one recent delivery worth
  mentioning and nothing accumulates
- any claim about whether a manufacturer feature is switched on
- scores, percentages or a reliability rating

---

# Implementation status

Implemented. `ReminderHealthScreen` reads `ReminderHealthViewModel`, which is
built from the application's own repositories rather than the shared
`TaskListViewModel`, because it reads a different table and asks a different
question. `ReminderHealth` and its checks are pure and live beside
`TaskQueries`.
