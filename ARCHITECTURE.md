# Architecture

How the app is technically built. A map, not a rulebook.

The rules live in `AGENTS.md`. The reasoning behind individual types lives in
their KDoc, which is thorough and is the better source for detail. This file
exists so a session can orient in a minute instead of reading the source tree.

---

## Shape

One Gradle module, `:app`. No feature modules.

No dependency injection framework. No Hilt, no Dagger, no Koin. Wiring is
manual and lives in one place.

The dependency list is deliberately short: Compose, Material 3, Navigation
Compose, Lifecycle, Room. Adding to it needs a reason, per `AGENTS.md`.

## Packages

    core/design       design tokens: color, spacing, dimensions, motion, layout
    core/domain       task model and pure business rules
    core/notification alarm seam and its Android implementation
    core/time         current-day seam and its Android implementation
    data/local        Room: entity, DAO, converters, migrations, mappers
    data/repository   TaskRepository
    ui/component      shared composables
    ui/<screen>       one package per screen
    ui/navigation     routes and the NavHost
    ui/theme          Material theme, type, shape

`core` knows nothing about Android UI. `ui` knows nothing about Room.
`data` knows nothing about composables.

## Composition root

`FocuslistApplication` owns four things for the life of the process:

- `database`, a Room database, created lazily so nothing touches disk until a
  task is asked for
- `taskRepository`, wrapping the DAO
- `currentDay`, a `SystemCurrentDay` that listens for date-change broadcasts
- `focusAlarms`, an `AndroidFocusAlarms`

It is deliberately not a service locator. It holds these and nothing else. If
something new needs process scope, adding it here is correct; adding a
general registry is not.

## The central pattern: one list, views as pure functions

This is the most important thing to understand, and the easiest to violate.

The app does not run a separate database query per screen. `TaskRepository`
exposes a single `observeTasks(): Flow<List<Task>>`, and every view is derived
from that list in memory by a pure function in `core/domain/TaskQueries.kt`:

    todayTasks(tasks, today)
    upcomingTasks(tasks, today)
    todaySections(tasks, today)
    todayPlannedMinutes(tasks, today)

These take data and return data. No Android types, no coroutines, no
repository. That is what makes the app's actual behavior testable as plain JVM
tests, and it is why `TaskQueries.kt` carries so much of the product logic.

`TaskListViewModel` is the one view model. It observes the repository once and
exposes a `StateFlow` per view, each `stateIn(viewModelScope)`. Screens
collect the flow they need.

When adding a view: add a pure function to `TaskQueries.kt` and a `StateFlow`
that calls it. Do not add a DAO query. Do not filter in a composable.

## Seams: how Android is kept out of testable code

Two interfaces exist purely so that decisions stay testable without a device:

- `CurrentDay` in `core/time`, implemented by `SystemCurrentDay`. Today is a
  value the app is told, never a value it reads from the clock inline.
- `FocusAlarms` in `core/notification`, implemented by `AndroidFocusAlarms`.
  The view model decides when something should be announced and does not know
  that `AlarmManager` or notifications exist.

Both are process-scoped, owned by the application, and injected into the view
model. This is the established pattern. Follow it for anything that touches
the platform.

## Persistence

Room, one entity, `TaskEntity`, currently at schema version 4.

Migrations are explicit in `FocuslistMigrations`. There is no destructive
fallback: a missing migration fails loudly rather than quietly emptying
someone's task list. Schema JSON is exported and committed, and
`MigrationTest` runs each version forward.

`TaskMappers` converts between `TaskEntity` and the domain `Task`. Domain
types never carry Room annotations.

Adding a column means updating fixtures in both `src/test` and
`src/androidTest`. Give the field no default so the compiler names every site
that has to think about it. See `AGENTS.md`.

Deletion is soft. `deletedAt` is set and cleared, which is what makes undo
work.

## Navigation

Compose Navigation. Routes are string constants in `FocuslistRoutes`, named
rather than positional, so a route survives screens being reordered.

Destinations are split into primary, which appear in the navigation bar, and
secondary, reachable through More.

Note: an uncommitted change converts Focus from a destination into a sheet,
which matches `PRODUCT.md` treating Focus as a mode rather than a place. The
route list still reflects the older arrangement, and Phase 3 removes the
Anytime and Someday routes entirely.

## Testing

`src/test` is the default. JVM tests cover the domain functions and the view
model, using `MainDispatcherRule` for `viewModelScope`.

`src/androidTest` is only for things needing a real runtime: Room against
SQLite, and Compose semantics. A misfiled test is expensive, because an
instrumented run installs and uninstalls the app and wipes its database off
every attached device.

---

## Where the Phase 1 reminder subsystem goes

Read this before writing reminder code. There is a real tension here, and the
obvious approach is wrong.

**The existing alarm is view-model driven.** `FocusAlarms` is scheduled by
`TaskListViewModel` because a focus session only exists while someone is
using the app. That is correct for focus, and it is the wrong model for task
reminders.

**A task reminder has to work when nothing is alive.** It is set once, then
has to survive the app closing, the process dying, and the device restarting.
No view model exists at the moment it needs to fire, and none exists at boot
when it needs rescheduling. So reminder scheduling cannot hang off the view
model the way focus alarms do.

The shape that follows:

- A `Reminders` interface in `core/notification`, following the `FocusAlarms`
  pattern, with an `AndroidReminders` implementation owned by
  `FocuslistApplication`.
- Scheduling driven from the write path, not the UI. When a task with a
  `reminderAt` is saved, the alarm is scheduled. When it is completed,
  cleared, or rescheduled, the alarm follows. `TaskRepository` is the point
  every write already passes through.
- A boot receiver that reads outstanding reminders from Room directly and
  reschedules them. It has no view model and must not need one.
- The decision of *when* a reminder should fire stays a pure function in
  `core/domain`, so snooze arithmetic and recurrence interaction are JVM
  tested. The Android side only obeys.

The rule that falls out: **storage is the source of truth for what is
scheduled, and the alarm is a cache of it.** If a reminder is not in Room and
recoverable by the boot receiver, it does not exist, whatever `AlarmManager`
was told.

Delivery constraints and standards for this subsystem are in `AGENTS.md`
under "Reminder delivery". The reasoning for why reminders carry this weight
is in `docs/decisions.md`, D-005.
