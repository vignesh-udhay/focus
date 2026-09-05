# Roadmap

Five phases to 1.0, sized for one person at 10 to 15 hours a week.

The reasoning behind this ordering is in `docs/decisions.md`. The product
scope it delivers is in `PRODUCT.md`.

---

## Current phase

**Phase 1: Make it tell you.** Started.

The design exists. The Clean Slate board in Figma covers the whole app, and
its `notify/*` frames cover the collapsed and expanded notification, the
snooze choice, the lock screen, the full-screen alarm, opening the app from a
reminder, and several at once, in light and dark. Do not invent notification
layout; it is drawn.

Done, and verified on a OnePlus 8T:

- `reminderAt` on `Task` and `TaskEntity`, schema version 5 with
  `MIGRATION_4_5`, and `core/domain/Reminders.kt` holding `pendingReminders`
  and `missedReminders`
- `ReminderScheduler`, which makes `AlarmManager` agree with storage and
  holds no memory of what it scheduled last time, run by any task write, by
  boot, by `MY_PACKAGE_REPLACED`, and by a clock or timezone change
- `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` in the manifest
- An alarm-grade channel, separate from the focus channel

The path works end to end on hardware: a reminder in storage reaches a
notification with the app closed, a reminder on a completed task is never
scheduled, and a package replacement rebuilds the alarms without the app
being opened.

Also done, and verified on the same device: Done and Snooze on the
notification, the snooze arithmetic behind them, and a Set Reminder page in
the task details sheet, drawn from the `reminder/Set Reminder` frame. A
reminder set by hand through the app reached the notification, and Done and
Snooze both did what they say.

Next, and the last of Phase 1: the permission screen. The frame is
`reminder/Precise Reminder Permission`, and it is being retargeted at
`POST_NOTIFICATIONS` rather than exact alarms. Its closing line promises a
system prompt, and `USE_EXACT_ALARM` never shows one, whereas notification
permission is refusable on every device the app supports and refusing it
produces a reminder that fires and is never seen. Routing users to
manufacturer settings for exact alarms belongs to Phase 2, which already lists
it.

**Exact alarms are being demoted on that device, and Phase 2 has to deal with
it.** `setExactAndAllowWhileIdle` produces an alarm with no `FLAG_STANDALONE`
and a window of 0.75 times its futurity, which is the inexact heuristic, even
though `canScheduleExactAlarms()` returns true and the permission is granted.
See `docs/decisions.md`, D-009. It also explains the exact-alarm spike's
result that exact and inexact arrived within 0.1 seconds of each other: they
were the same kind of alarm.

Still unverified: the Room migration test moved to version 5 but has never
run, because it needs a real SQLite runtime and an instrumented run wipes
every attached device.

The throwaway spike on branch `spike/exact-alarms` is no longer installed.
Its remaining value is measuring how far a demoted alarm drifts overnight in
Doze. While a run is in progress, do not install anything to that phone:
replacing the package ends the test.

Update this line when a phase begins and when it ends. It is the first thing
a new coding session should read, and the only place that says where the work
actually is.

Do not build ahead of the current phase. A task belonging to a later phase is
a task for later, even when it is small, even when it is adjacent to what is
being worked on now, and even when it would be quicker to do it while the file
is already open.

---

## Phase 1. Make it tell you

Give the app the feature it is named for and does not have.

Focuslist currently schedules exactly one kind of alarm, for a focus session
overrunning its estimate. There are no task reminders at all, the manifest
declares only `POST_NOTIFICATIONS`, and there is no boot receiver, so a device
restart would silently lose every alarm ever set.

Work:

- `reminderAt` on `Task`, with a Room migration and fixtures updated in both
  source sets
- Scheduling through `AlarmManager.setExactAndAllowWhileIdle`
- `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` in the manifest
- A boot receiver that reschedules every outstanding reminder
- An alarm-grade notification channel, separate from the focus channel
- Done and Snooze actions on the notification
- A permission flow that explains why the app is asking
- Setting a reminder from the task details sheet

Exit criteria:

- A reminder set for a time fires at that time with the app closed
- The same reminder fires after a device restart between setting and firing
- The same reminder fires with the screen off and the device idle
- Completing from the notification completes the task, and the app agrees
- Snoozing from the notification reschedules, and survives a restart
- Tests cover scheduling, rescheduling on boot, and the snooze arithmetic

Estimate: 5 to 7 weeks.

---

## Phase 2. Make it trustworthy

The differentiator. Reminders that fire on a Pixel are table stakes.
Reminders that fire on a Xiaomi are the product.

Work:

- Detect manufacturer battery restrictions that
  `isIgnoringBatteryOptimizations()` does not report: Samsung sleeping apps,
  Xiaomi autostart, OnePlus sleep standby, and others
- Route the user to the correct settings screen for their device
- Explain the problem in language a person who does not know what Doze is can
  act on
- Record the time each alarm was scheduled for and the time it actually
  fired, and notice the gap
- A reminder health screen that reports whether the app can currently be
  relied on
- A test reminder the user can fire in 30 seconds to check

Exit criteria:

- On a restricted device, the app says it is restricted before a reminder is
  missed, not after
- Every warning the health screen shows leads somewhere the user can act
- A missed reminder is detected and surfaced rather than passing silently
- The health screen is honest when everything is fine, and does not nag

Estimate: 4 to 5 weeks.

---

## Phase 3. Subtract

Bring the code to the scope `PRODUCT.md` now describes.

Do this before Phase 4, so the widget is not built against an information
architecture that is about to change.

Work:

- Remove Anytime and Someday, and the placements behind them
- Collapse navigation to Inbox, Today, Upcoming, with Focus as a mode and
  Logbook reachable but not primary
- Simplify triage to match
- Remove the Focus queue
- Update the design documents under `docs/design/` that still describe the old
  structure, and remove the superseded banners once they are correct
- Room migration for removed placements, preserving user data

Around 16 source files reference the removed concepts. Check with:

    grep -rlniE "anytime|someday" app/src

Exit criteria:

- No reference to Anytime, Someday or the Focus queue remains in `app/src`
- No document under `docs/` describes a destination the app does not have
- Existing tasks in those placements land somewhere sensible after migration,
  and the migration is tested
- The app builds and all tests pass

Estimate: 2 to 3 weeks.

---

## Phase 4. Reach the home screen

Work:

- A Jetpack Glance widget: today's tasks, tap to complete, tap to add
- Dynamic color on the widget, because that is what makes an Android app look
  native from the home screen
- Recurrence past the current four fixed periods: intervals, weekday sets, and
  an end condition
- Completing a recurring task produces the next occurrence and never makes the
  task vanish

Exit criteria:

- The widget updates when the task list changes, without opening the app
- Completing from the widget completes the task
- The widget is legible in light and dark, and at small and large sizes
- Recurrence rules round-trip through storage and are covered by tests

Estimate: 5 to 6 weeks.

---

## Phase 5. Ship it

Work:

- Settings screen
- Backup and restore to a JSON file the user controls
- Full accessibility pass with TalkBack
- Dark theme audit
- Play listing: screenshots, the one-line pitch, privacy policy
- Publish free, with no in-app purchases configured at all

Store listing, one line:

> A calm task app for Android whose reminders actually go off. Free, no
> account, no subscription.

Exit criteria:

- A backup taken on one install restores completely onto a clean install
- Every screen is navigable and comprehensible with TalkBack
- The Play listing makes no claim the app does not deliver
- 1.0 is live

Estimate: 3 to 4 weeks.

---

## After 1.0

In rough evidence order, and only once real users ask:

- Subtasks
- Flat Lists, replacing the deferred Projects idea (see `docs/decisions.md`,
  D-003)
- Tablet and foldable layouts
- Wear OS tile

Let the app's own reviews set this order rather than this document.

---

## Totals

Roughly 19 to 25 weeks to 1.0. Longer than it looks, because Phases 1 and 2
are systems work rather than screens.
