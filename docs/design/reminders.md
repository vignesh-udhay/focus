# Reminder delivery

What a reminder looks like when it arrives, and what it offers.

This is the screen the product is for. `PRODUCT.md` principle 1 makes a reminder
that does not fire the highest-severity bug in the app, higher than a crash,
and D-005 records the review that put it there: a paying user who loved the app
left because the notifications did not arrive. Everything below is downstream of
that.

---

# Design system

The notification is drawn by Android, not by this app. What the app chooses is
the text, the actions, the channel and the category; the shape, the colours and
the motion are the platform's. That is the whole reason this document is short:
most of the surface is not ours to design, and `PRODUCT.md` principle 5 says not
to fight it.

---

# The states

Seven were drawn. Five describe what the app posts, and the board keeps them in
light and dark:

- **Collapsed** — the title and one summary line.
- **Expanded** — the same, plus the notes, plus Done and Snooze.
- **Snooze options** — the second screen of the Snooze action.
- **Lock screen** — the same notification, drawn by the system.
- **Full-screen alarm** — a design held past 1.0 under D-038, not behaviour. See
  below.
- **Grouped** — several at once. Not built, and not yet decided either way. See
  below.

`notify/Opened from reminder` was the seventh and is retired. It drew a bespoke
Task screen with a card round the title and two full-width buttons, which D-018
removed from Task Details, D-020 removed the "Task" app-bar title from, and
D-022 settled the actions of. Tapping a reminder opens Task Details, which
section 05 already draws. Two drawings of one screen is how they drift, so the
frames are on the `Archive` page rather than in the delivery section.

---

# What the notification says

**The title is the task's title, and nothing is added to it.** No "Reminder:"
prefix, no app name in the text. The app name is already in the header Android
draws.

**The line beneath is the time it was set for and how often it repeats**, from
`reminderSummary`. The time takes the device's own 12 or 24 hour preference
rather than a format this app picks.

**The notes go in the expanded form and nowhere else.** `BigTextStyle` carries
them, so the collapsed notification stays the title and one line. A task with no
notes gets no expanded body, rather than an empty one.

---

# Done and Snooze

Two actions, both handled without opening the app. `PRODUCT.md` requires
completing and snoozing from the notification, and the core loop ends at
*be told → complete*, so an action that opened the app to finish would break the
loop it exists to close.

## The snooze vocabulary

Four options, and only three are ever offered.

    10m            ten minutes from now
    1h             an hour from now
    This evening   18:00 today, when the evening has not gone
    Tomorrow 9:00  09:00 tomorrow

**Android shows at most three notification actions**, so
`availableSnoozeOptions` takes the first three that apply. The board draws three
for that reason. The fourth is not lost: `This evening` drops out after 18:00,
and `Tomorrow 9:00` takes the place it leaves.

**`This evening` is the only option that can be unavailable, and that is the
point.** Offering it at 22:00 would either mean a time already gone or quietly
mean tomorrow, and both are the notification lying about what the button does.
`snoozedUntil` returns null rather than substituting, and `Snooze.kt` states the
rule for callers: do not offer an option it returns null for. A chip that says
one time and sets another is worse than a chip that is not there.

**Two are relative and two land on a named hour**, which is why the arithmetic
has a file of its own. "In ten minutes" cannot be got wrong; "this evening" can.

---

# One notification per task

Keyed on the task id, so several reminders at once read as several things to do
rather than the last one overwriting the rest. Re-posting the same task touches
the same notification.

**The app does not group them.** `notify/Grouped` draws a "Focuslist · 3
reminders" summary, and nothing calls `setGroup` or `setGroupSummary`. Android
bundles automatically at four or more, so at three the drawn summary does not
appear. The frame is a design for work not done rather than a record of
behaviour.

---

# The full-screen alarm is a design held past 1.0

`notify/Full screen alarm` draws it. No `setFullScreenIntent` exists anywhere in
the app, and none will before 1.0 ships.

**D-038 is the entry**, and it defers rather than declines. D-005 commits to
"full-screen intents where warranted" as part of the API layer, so this is scope
that was planned and dated rather than scope that was cut. It is worth naming
plainly because the board otherwise makes it look done.

The short version of the argument: per-task opt-in is the only warranting rule
that holds, per-task means schema version 11 and a backup format change on top of
a lock-screen activity and a permission flow, and that is a phase rather than a
Phase 5 bullet. Threading a second delivery path through the reminder pipeline
immediately before the first release is the sequencing mistake Phase 3 ran early
to avoid. D-038 carries the rest, including why the Play declaration is worst
spent on a first submission and why waiting costs little.

**What warrants one, when it is built:** per-task opt-in. A reminder is not
automatically an alarm, and an app that decides which of the user's tasks may
take over their device is making the claim D-031 refuses on the widget.

**The permission is obtainable**, which D-038 records from the emulator so the
next reader does not re-derive it: TickTick holds `USE_FULL_SCREEN_INTENT`
granted at `targetSdk=37` from a Play install. A non-qualifying app is not
refused the permission, only denied the pre-grant, and degrades to the heads-up
notification this app already posts.

---

# The channel

`IMPORTANCE_HIGH` and `CATEGORY_REMINDER`. `AndroidReminderAlarms.kt` carries
the argument: Material's guidance for a calm app points at `IMPORTANCE_DEFAULT`,
and it does not transfer here, because this is the app doing the one thing it
promised.

Nothing sets `setVisibility`, so the lock screen shows whatever the channel and
the user's own setting allow. The board's lock-screen frame draws the full title
and summary, which is the common case and not a guarantee.

---

# Out of scope

Not part of delivery:

- a reminder that repeats until dismissed
- custom sounds per task, which belong to the channel and to Android's settings
- anything shown after Done that is not the undo every list already raises

---

# Implementation status

**Built:** collapsed, expanded, snooze options, and the lock screen as a
consequence of them. `ReminderNotification.kt` names the frames it draws.

**Not built:** the full-screen alarm, held past 1.0 by D-038, and grouping,
which is unbuilt and still undecided. Grouping is the smaller of the two by a
wide margin: `setGroup` and `setGroupSummary`, no schema change, no permission
and no policy question. It wants an answer of its own rather than being carried
along by D-038.

**Retired:** `notify/Opened from reminder`, on the Archive page.
