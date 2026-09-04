# Focuslist

## Product

Focuslist is a calm, highly polished personal task manager built specifically
for Android.

It should feel native to Android rather than like an iOS productivity app
ported to Android.

## What the app is for

Focuslist tells you about your work at the moment it matters, and it does not
miss.

That is the job. Not filing, not organising, not planning. A task app earns
trust by being the thing that reliably interrupts you, and loses it the first
time a reminder does not arrive.

Everything else in this document exists to support that.

## Core promise

If you write it down here, you will be told.

## Core loop

Capture -> Say when -> Be told -> Complete

The ideal experience is:

Open app
-> Capture the task
-> Say when it matters
-> Close the app and forget it
-> Be told at the right moment
-> Complete it

A user who never opens the app between capture and reminder is a user the
product is working for, not a user who has churned.

---

# Product Principles

## 1. Reliability is the feature

A reminder that does not fire is a bug of the highest severity in this
product. Higher than a crash: a crash is visible, a missed reminder is not.

The app is responsible for reminders arriving, including when the operating
system or the device manufacturer is working against it. "Android killed us"
is an explanation, never an excuse.

## 2. Today is the center

The primary question when opening the app is:

"What should I do now?"

Today should make that answer obvious within seconds.

## 3. Capture should be effortless

Creating a task should require almost no decisions.

Users should be able to capture first and organize later.

A task needs a title. Everything else is optional.

## 4. Tasks are for action

The task lifecycle is:

Next -> Focus -> Done

The application should encourage execution rather than task administration.

## 5. Android-native is a product requirement

The application should embrace Android conventions for:

- navigation
- back behavior
- gestures
- system bars
- Material components
- dynamic color
- motion
- notifications
- alarms
- widgets
- adaptive layouts
- accessibility

Do not reproduce iOS interaction patterns on Android.

## 6. Reduce cognitive load

The application should minimize unnecessary decisions.

Avoid exposing every possible property at once.

Every destination in the app must be explainable in one short sentence to
someone who has never read about productivity systems.

## 7. Calm over gamification

The application should not use:

- streaks
- points
- badges
- productivity scores
- unnecessary celebrations
- excessive animations
- motivational noise

The reward is getting the work done.

## 8. Free, with nothing held back

Focuslist has no subscription, no paid tier, no in-app purchases, no ads and
no account.

This is a product decision, not a temporary state. See `docs/decisions.md`,
D-001.

---

# Information Architecture

Primary destinations:

- Today
- Inbox
- Upcoming

Focus is an execution mode, not a destination and not a separate
task-management system.

Logbook is a record of completed work, reachable but not primary.

There are three lists. A user should never have to remember which one they
put something in.

---

# Core Concepts

## Task

A task represents an actionable piece of work.

A task may have:

- title
- notes
- scheduled date
- due date
- reminder
- estimated duration
- subtasks
- recurrence

## Reminder

A reminder is a promise that the app will interrupt the user at a specific
moment.

It is the product's central feature and is held to a higher standard than
anything else in this document.

A reminder must:

- fire at the time it was set for
- fire when the app is closed
- fire after the device restarts
- fire when the screen is off
- be dismissible and snoozable without opening the app
- allow completing the task from the notification

A reminder is independent of a scheduled date and of a due date. A task can
be scheduled for a day without being announced at a time.

## Recurrence

A recurring task is a task that comes back.

Completing one occurrence must produce the next one. It must never make the
task disappear.

A recurring task is not a habit. Habits are out of scope.

## Focus

Focus is the execution mode for working on one task.

Focus should remove distractions and make the current task obvious.

Focus works on one task. There is no queue.

---

# V1 Scope

Shipped in 1.0, and nothing else:

- Inbox
- Today
- Upcoming
- Tasks
- Reminders, alarm-grade and reliable
- Reminder health checking
- Recurring tasks
- Natural-language date parsing
- Task duration
- Focus mode, single task
- Logbook
- One home screen widget
- Local-first storage, no account
- Backup and restore to a file
- Dynamic color
- Light theme
- Dark theme
- Adaptive layouts

`ROADMAP.md` says which of these is being built now.

---

# Not in this product

Do not implement these. Each was considered and rejected with a reason
recorded in `docs/decisions.md`.

Removed from earlier plans:

- Anytime (D-002)
- Someday (D-002)
- Areas (D-003)
- Projects (D-003)
- Focus queue (D-004)
- Manually curated Today (D-002)

Never in scope:

- habits
- gamification
- social features
- team collaboration
- Kanban
- AI features
- analytics dashboards
- backend infrastructure
- accounts and cloud sync
- subscriptions, paid tiers, in-app purchases, ads

Post-1.0, and only once 1.0 has shipped and real users have asked:

- subtasks
- flat Lists
- tablet and foldable layouts
- Wear OS tile

Adding anything from the first two groups requires updating
`docs/decisions.md` first, with the reason the earlier decision was wrong.
Writing the reversal down is the point. If the reason cannot be written, the
change should not be made.

---

# Constraints

Do not invent product behavior.

Do not add features that were not requested.

Do not introduce dependencies without a clear reason.

Do not rewrite working code unnecessarily.

Do not build ahead of the current phase in `ROADMAP.md`.

---

# Definition of Quality

A feature is not complete merely because it compiles.

It should:

- behave correctly
- feel native to Android
- follow Material 3
- support light and dark themes
- respect accessibility
- work across relevant screen sizes
- have appropriate loading, empty and error states
- use appropriate motion
- avoid unnecessary visual complexity

Anything that schedules or delivers a reminder additionally has to survive:

- the app being force stopped
- the device restarting
- Doze and app standby
- manufacturer battery restrictions beyond stock Android

The goal is not maximum functionality.

The goal is a task app that people trust.
