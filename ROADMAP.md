# Roadmap

Five phases to 1.0, sized for one person at 10 to 15 hours a week.

The reasoning behind this ordering is in `docs/decisions.md`. The product
scope it delivers is in `PRODUCT.md`.

---

## Current phase

**Phase 2: Make it trustworthy.** All four slices built, exit criteria met,
verified on a Pixel 10 emulator and on a OnePlus 8T. Phase 1 is complete.

**Read the design from `Focuslist — M3 Expressive Clean Slate` (node
`161:3405`) and nothing else.** An earlier page, `Focuslist — M3 Expressive
V1`, is still in the file and disagrees with it. Phase 1 work was started
against the old page twice before that was noticed.

Phase 1 delivered reminders end to end: a time on a task, an exact alarm that
survives a restart and a clock change, an alarm-grade channel, Done and
Snooze on the notification, the snooze arithmetic, a Set Reminder dialog, and
a permission flow covering notifications and exact alarms. All verified on a
OnePlus 8T and on an emulator.

Phase 2 has one delivered slice: **the delivery record**, `schema version 7`
and the `reminder_deliveries` table. `ReminderReceiver` writes a row every
time a reminder fires, carrying the task's title as it read then, the moment
the alarm was aimed at, the moment it arrived, and whether it was announced or
suppressed. Both clocks, as `AGENTS.md` requires. `core/domain/ReminderDelivery.kt`
holds the arithmetic and `LateThreshold`.

It is kept apart from the task because a task's own `reminderDeliveredAt` is
cleared by rescheduling, by completing, and by a recurring occurrence rolling
forward, so the evidence was being destroyed by ordinary use.

Verified on the emulator: a reminder whose alarm fired while the permission
dialog was still on screen recorded `Suppressed`, and granting the permission
produced a second row recording `Announced`.

**One limit to know before building the health screen.** The app cannot detect
that it was demoted. There is no public API to read back a scheduled alarm's
window, and D-009's evidence came from `dumpsys`. So "Exact alarms: Allowed"
will read Allowed on the OnePlus while reminders still drift. The headline
state has to be driven by the delivery record, not by the permission checks.

Second slice: **the health state**, `core/domain/ReminderHealth.kt`. It holds
the three checks and the recent delivery record together, and where they
disagree the record wins. That rule is the point: a screen built on the checks
alone reports Ready on the OnePlus in D-009.

`core/notification/ReminderHealthChecks.kt` answers the two real questions,
and guesses the third from `Build.MANUFACTURER`, because no API exposes these
features and the one that is exposed,
`isIgnoringBatteryOptimizations()`, was measured in D-009 to change an alarm's
flags and leave its window untouched. OnePlus, OPPO and realme map to sleep
standby; Xiaomi, Redmi and POCO to autostart; Samsung to sleeping apps; Huawei
and Honor to protected apps. Every other device is left alone, because a false
warning on a Pixel costs the app the attention the real warning needs.

A restrictive device that then delivers `EvidenceOfHealth` reminders on the
trot stops being warned about, and one late delivery brings the warning back.
The app cannot read the setting, so behaviour is the only evidence there is.

**Only deliveries that were exposed to idle time count as that evidence.**
`schema version 8` records how far ahead each alarm was set, and one set less
than `EvidenceHorizon` ahead proves nothing: the failure needs a phone that has
been left alone, so a reminder set for five minutes' time arriving punctually
says only that `AlarmManager` works. Without this the warning could clear on
daytime reminders and go quiet before the 6am one was missed.

Third slice: **the health screen**, `ui/health/`, with all four states, the
three rows, the settings routing, and the test reminder. Reachable from More
for now; the Clean Slate board puts it in an app-bar overflow, which arrives
with Phase 3's navigation change.

`ReminderHealthState.ActionNeeded` carries which check caused it. The first
build of the screen did not, and blamed the manufacturer's sleep feature for a
notification permission the user had refused. The headline and the button both
read `ReminderHealth.firstFailing`, so they cannot point at different problems.

The test reminder travels the same road as a real one: `AlarmManager`, a
receiver, a notification, and a row in the delivery record. Its thirty-second
futurity is under `EvidenceHorizon` by design, so it can raise a warning and
never clear one. Measured on the emulator at four milliseconds late, against
the roughly fifty seconds D-009 measured on the OnePlus.

Fourth slice: **routing to the manufacturer's own screen**,
`core/notification/DeviceSettingsRoute.kt`. Best effort, and known to be best
effort. Every candidate is resolved against the package manager before it is
launched and the launch itself is guarded, so the user always lands somewhere:
the vendor's screen where the phone permits it, the app's Android settings page
otherwise. The button is named after wherever it will actually arrive.

The packages appear twice, in that file and in `<queries>` in the manifest,
because from Android 11 an undeclared package is invisible and resolves to
nothing. That failure is silent: the deep link would degrade to the generic
page on exactly the phones that need it, and look like the feature was never
built. A unit test checks the two lists agree.

**Two things were learned from the OnePlus 8T, and D-010 records them.** The
activity names every published list gives for this vendor are stale, because
ColorOS and OxygenOS merged at ColorOS 12 and renamed everything to
`com.oplus`. And the replacements are guarded by a signature-level permission,
so they resolve and then throw. There is no ColorOS or OxygenOS entry in the
table as a result, and a test exists to stop one being helpfully added back.

The MIUI, One UI and EMUI entries are still guesses, from the same kind of list
that proved stale here, and none has been tried on that hardware.
`aRestrictedDeviceHasAtLeastOneScreenToOffer` in the instrumented suite is what
will report the next one to go stale. It passes vacuously on a Pixel and means
something on a Xiaomi, a Samsung or a Huawei.

**One caution about that test.** It passed on the OnePlus while the app itself
failed, because it only asks whether a screen resolves, and resolving is not
permission to start it. Resolution and launch are separate questions on these
skins, and only the second one matters. Trust the log line over the test:
`FocuslistReminder` records which screen was opened, or that none was.

**Phase 2's exit criteria are met.**

**Phase 3 and the design pass are the same job.** The Clean Slate board
mentions Anytime and Someday nowhere, and every screen on it carries the same
three-item bar: Today, Inbox, Upcoming, with Logbook, Reminder health and
Settings behind an app-bar overflow. `PRODUCT.md` already describes that
information architecture. Only the code disagrees, in 17 files under
`app/src/main`. Do not restyle a screen the board does not contain.

Three things the board shows are new build rather than reconciliation, and
each needs a decision before it is started: the UP NEXT hero card on Today,
Task Details as a full screen with a read-only Plan card, and Focus gaining a
timer with pause and resume. The last contradicts D-004 and needs a
superseding entry first.

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
