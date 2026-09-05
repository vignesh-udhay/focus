# AGENTS.md

## Before making changes

Read `ROADMAP.md` to find the current phase. Work outside it is out of scope.

Read `PRODUCT.md` before implementing or modifying product functionality.

Read `docs/decisions.md` before adding anything listed under "Not in this
product" in `PRODUCT.md`.

Read `ARCHITECTURE.md` before adding a new type, package, or subsystem. It
says where things go and which patterns already exist.

Understand the existing code before creating new abstractions.

Do not invent product behavior, features, navigation, or UI patterns.

---

# Technology

This is a native Android application.

Use:

- Kotlin
- Jetpack Compose
- Material 3
- Android platform APIs where appropriate

Prefer official Android and Jetpack libraries.

Do not introduce a dependency when the Android SDK or existing project
dependencies already provide the required capability.

---

# Architecture

`ARCHITECTURE.md` is the map: what exists, where it lives, and which patterns
to follow. This section is the rules. Where they overlap, `ARCHITECTURE.md`
describes and this file constrains.

Keep responsibilities separated.

UI:
- Composable functions
- UI state rendering
- user interaction callbacks

Presentation:
- ViewModels
- screen state
- UI-related business orchestration

Domain:
- business rules
- use cases when they provide meaningful separation

Data:
- repositories
- local persistence
- synchronization

Do not put business logic inside composables.

Do not create abstractions merely for the sake of abstraction.

Prefer simple, understandable code.

---

# Design System

Material 3 is the foundation of the visual system.

Use the existing Focuslist theme and design tokens.

Before creating a new component:

1. Check whether an existing Material 3 component can be used.
2. Check whether an existing Focuslist component can be reused.
3. Only create a new component when there is a real design or behavioral need.

Do not hard-code colors throughout the UI.

Use Material color roles such as:

- primary
- onPrimary
- surface
- surfaceContainer
- onSurface
- onSurfaceVariant
- outline

Use the Focuslist typography definitions.

Use the Focuslist spacing definitions.

Use the Focuslist shape definitions.

Do not introduce arbitrary spacing values when an existing spacing token
is appropriate.

---

# Android-native UX

The application should feel native to Android.

Follow Android conventions for:

- navigation
- back behavior
- system bars
- edge-to-edge layouts
- gestures
- touch targets
- Material components
- dynamic color
- notifications
- widgets
- accessibility
- adaptive layouts

Do not copy iOS interaction patterns.

Do not recreate an iOS-style navigation hierarchy simply because another
product uses it.

---

# UI implementation

Every screen should consider:

- normal state
- empty state
- loading state when applicable
- error state when applicable
- dark theme
- light theme
- accessibility
- font scaling
- compact width
- expanded width

Do not design only for one fixed phone size.

Avoid unnecessary:

- cards
- borders
- shadows
- gradients
- decorative containers
- pills
- animations

Visual hierarchy should come primarily from:

- typography
- spacing
- color roles
- alignment
- component hierarchy

---

# Motion

Motion should communicate a meaningful state change.

Good uses include:

- task completion
- expanding/collapsing content
- navigation transitions
- showing or dismissing temporary UI
- focus mode transitions

Do not add animation merely because animation is possible.

Animations should be:

- purposeful
- responsive
- interruptible where appropriate
- respectful of reduced-motion/accessibility settings

---

# Interaction

Prefer direct manipulation.

For common actions:

- minimize unnecessary confirmation dialogs
- provide undo for reversible destructive actions where appropriate
- maintain predictable touch behavior
- provide accessible alternatives to gesture-only interactions

Task completion should feel immediate.

Local interactions should not wait for network operations.

---

# Product scope

`PRODUCT.md` defines the current product scope. `ROADMAP.md` defines which
part of it is being built now.

Do not implement features merely because they seem useful.

Do not add:

- AI features
- social features
- gamification
- analytics dashboards
- collaboration
- unnecessary customization
- backend infrastructure
- accounts, cloud sync, subscriptions, purchases, or ads

unless explicitly requested.

## Scope guard

This project is built across many short sessions, so scope drifts by
accumulation rather than by decision. Three rules hold it in place.

**Do not rebuild what was cut.** Anytime, Someday, Areas, Projects and the
Focus queue were removed on evidence recorded in `docs/decisions.md`. Each
will look useful again in isolation. Read the decision first.

**Do not build ahead of the current phase.** Later-phase work is later-phase
work, even when it is small, adjacent, or convenient because the file is
already open.

**Do not reverse a decision silently.** Scope changes need a superseding entry
in `docs/decisions.md`, written before the code, saying what new information
changed the answer.

When a request would do any of these three, say so before implementing, and
name the decision it touches.

---

# Reminder delivery

Reminders are the product. See `docs/decisions.md`, D-005.

Anything that schedules or delivers a reminder is held to a higher standard
than the rest of the app, and has to survive:

- the app being closed or force stopped
- the device restarting
- Doze and app standby
- manufacturer battery restrictions beyond stock Android

Rules:

- Schedule with `AlarmManager.setExactAndAllowWhileIdle`. Do not use
  `WorkManager` for a user-visible reminder time. It is not exact and it is
  not for this.
- Every scheduled reminder must be recoverable after a restart. If it is not
  in storage and rescheduled by the boot receiver, it does not exist.
- Reschedule on `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED` too. A
  phone corrects its own clock as a matter of routine, from carrier NITZ and
  from an NTP poll every 18 hours, and an alarm placed against the old clock
  then fires at the wrong moment. Same failure as the reboot case: still
  scheduled, no longer pointing at the time the user asked for. Both actions
  are on Android's implicit-broadcast exception list, so a manifest receiver
  still gets them with the app closed.
- Reminder notifications use their own alarm-grade channel, separate from the
  focus session channel.
- `isIgnoringBatteryOptimizations()` reports on stock Android only. It is not
  evidence that a reminder will arrive on a Samsung, Xiaomi or OnePlus
  device.
- `canScheduleExactAlarms()` returning true is not evidence that an alarm will
  be exact. Measured on a OnePlus 8T on 5 September 2026: with
  `USE_EXACT_ALARM` granted and that call returning true,
  `setExactAndAllowWhileIdle` still produced an alarm carrying no
  `FLAG_STANDALONE` and a window of 0.75 times its futurity, which is AOSP's
  heuristic for an inexact alarm. Permission is what the system agrees to be
  asked for, not what it agrees to do. Verify by observing the scheduled
  alarm in `dumpsys alarm`, never by asking permission.
- A missed reminder must be detectable after the fact. Record the time each
  alarm was scheduled for alongside the time it actually fired, on both
  `System.currentTimeMillis()` and `SystemClock.elapsedRealtime()`. The wall
  clock alone cannot tell a late alarm apart from a clock that moved under it.
  Measuring only the wall clock is how the exact-alarm spike produced a
  reminder that appeared to arrive five minutes early, which `AlarmManager`
  has no mechanism to do.
- Never silently swallow a scheduling failure. If the app cannot promise a
  reminder, it has to say so.

Test scheduling arithmetic, boot rescheduling, clock-change rescheduling, and
snooze as JVM tests. They are domain logic and do not need a device.

---

# Dependencies

Before adding a dependency:

1. Check whether an existing dependency provides the capability.
2. Check whether the Android SDK provides the capability.
3. Consider maintenance and APK size.
4. Explain why the dependency is necessary.

Do not add libraries simply because they are popular.

---

# Code quality

Prefer:

- readable code
- small composables
- meaningful names
- immutable UI state
- unidirectional data flow
- Kotlin idioms
- minimal duplication

Avoid:

- giant composables
- deeply nested conditional logic
- duplicated UI implementations
- premature abstractions
- unexplained magic numbers
- dead code

---

# Testing

When changing behavior:

- build the project
- run relevant tests
- verify the affected UI manually when appropriate

Do not consider a feature complete merely because it compiles.

For important user interactions, add appropriate tests.

## Which source set

Default to `src/test`. A test belongs in `src/androidTest` only when it needs a
real Android runtime: Room against SQLite, or Compose UI and semantics.

View models are JVM tests. Their only tie to a device was `viewModelScope`
dispatching on `Dispatchers.Main`, and `MainDispatcherRule` supplies one. A
misfiled test is not a small mistake: an instrumented run builds two APKs,
installs them, and uninstalls them again afterwards, which wipes the app and its
database off every attached device.

Adding a column to `TaskEntity` means updating the fixtures in both source sets.
Give the field no default, so the compiler names every site that has to think
about it, and assert the new field somewhere. A round trip that never sets it
passes whether the value is carried or dropped.

---

# Agent workflow

For every requested feature:

1. Read the "Current phase" section of `ROADMAP.md`.
2. Read `PRODUCT.md`.
3. Read this file.
4. Confirm the feature belongs to the current phase, and is not something
   `docs/decisions.md` removed. If it is either, say so before continuing.
5. Inspect the existing implementation.
6. Identify reusable components and design tokens.
7. Implement the smallest coherent change.
8. Build the project.
9. Run relevant tests.
10. Review the implementation against the product and design requirements.
11. Report what changed and any remaining issues.
12. If the work moved the project forward, update "Current phase" in
    `ROADMAP.md`.

Do not modify unrelated code.

Do not refactor unrelated areas while implementing a feature.

If a requirement is ambiguous, identify the ambiguity rather than inventing
a major product decision.

---

# Definition of done

A feature is complete when:

- it implements the requested behavior
- it follows PRODUCT.md
- it follows the existing design system
- it builds successfully
- relevant tests pass
- it works with light and dark themes
- accessibility has been considered
- adaptive layouts have been considered
- the implementation does not introduce unnecessary complexity