# Storage

How tasks are stored, and how the stored shape changes over time.

---

# The tables

`tasks`, one row per task, written through `TaskDao` and read only by
`TaskRepository`. `TaskEntity` never leaves the data layer, and `TaskQueries`
derives every view from the domain model rather than from SQL.

`reminder_deliveries`, one row per reminder that fired, added at version 7 and
written by `ReminderReceiver`. Deliberately not a column on the task: a task's
own `reminderDeliveredAt` is cleared by rescheduling, by completing, and by a
recurring occurrence rolling forward, so ordinary use was destroying the
evidence. See `docs/decisions.md`, D-009.

---

# Versions

    1  the original nine columns
    2  adds notes
    3  adds recurrence
    4  adds spawnedFromId
    5  adds reminderAt
    6  adds reminderDeliveredAt
    7  adds the reminder_deliveries table
    8  adds scheduledAheadMs to reminder_deliveries
    9  removes the task placement column

Schemas are exported to `app/schemas` and checked in. That export is what
`MigrationTestHelper` builds an old database from, and what it validates a
migrated one against, so it is not a build artefact to be regenerated and
forgotten.

---

# Migrations

`Migrations.kt` holds one `Migration` per version step and a
`FocuslistMigrations` array. `FocuslistApplication` registers the array on the
builder.

The builder deliberately does **not** call `fallbackToDestructiveMigration`.
Losing someone's tasks is not an acceptable outcome for a schema change, and a
destructive fallback turns a missing migration into silent data loss instead of
a loud failure in development. A version bump without a migration should fail.

Version 2 is one statement:

    ALTER TABLE tasks ADD COLUMN notes TEXT

No `NOT NULL`, no default, so SQLite appends the column and every existing row
reads it as null, which is what a task written before notes existed should say
about its notes. Nothing else is read, rewritten, or moved.

Versions 3 to 8 are the same statement against another column, and null means
the same kind of thing in each: a task written before the column existed does
not recur, was made by a person rather than spawned by finishing a recurring
one, has no reminder, and cannot have had one announced. Nothing to backfill in
any of them.

## Version 9 is the exception, and the shape to copy next time

It removes a column, and `ALTER TABLE ... DROP COLUMN` is not available here.
It needs SQLite 3.35, which arrives with Android 14, and `minSdk` is 29. A
migration written that way works on a modern test device and breaks the upgrade
for everyone below it, which is the worst failure a migration can have: invisible
to the person who wrote it.

So the table is recreated. Create `tasks_new` with the surviving columns, copy,
drop the old table, rename the new one:

    CREATE TABLE IF NOT EXISTS tasks_new (...)
    INSERT INTO tasks_new (id, title, ...) SELECT id, title, ... FROM tasks
    DROP TABLE tasks
    ALTER TABLE tasks_new RENAME TO tasks

**Name the columns on both sides of that copy.** `SELECT *` passes every test
and silently reorders people's data when the two column lists differ. `tasks`
carries no indices, so there are none to recreate afterwards; a future table
that has them must recreate them here, because dropping the table drops them.

---

# Ordering

`TaskDao.observeTasks` orders by `createdAt, id`.

The view ordering still belongs to `TaskQueries`. What the DAO guarantees is a
*deterministic* starting order, because the sorts downstream are stable and so
their tie-breaks are decided by the order they are handed. Without an
`ORDER BY`, SQLite may return rows in whatever order it finds them.

This became worth pinning down when Focus arrived: Focus resolves to the head
of its queue, so an unspecified row order would decide which single task the
user is shown. `createdAt` alone is not a total order, since two tasks can be
captured in the same millisecond, so the primary key is added to make it one.

---

# Write-ahead logging

Room uses WAL, so a live database is a `focuslist.db` alongside a
`focuslist.db-wal`. On a running install the main file can hold almost nothing:
observed on the development emulator, a database with seven tasks was a 4 KB
main file and a 144 KB WAL.

That is normal, and it matters in two places. Reading the main file alone, by
copying it off a device, shows stale or empty data unless the WAL is copied
with it. And the app is configured for Android's Auto Backup with the default
template rules, which back up all three files without coordinating them. There
is no install base yet, so nothing is at risk today, but what the app should
back up is an open product decision rather than a setting to change quietly.

---

# Verification

`MigrationTest` builds a genuine version-1 database, writes rows with raw SQL
as the old app would have, runs the real migrations, validates the result
against the exported schema for the current version, and reopens the file
through `FocuslistDatabase`. It covers the three starting points that exist: a
version-1 install, a version-2 one, and a fully populated version-8 row
crossing the recreated table at version 9.

Those tests still contain the strings `INBOX`, `ANYTIME` and `SOMEDAY` after
placement was removed from the app. That is the point of them. Old databases
hold those values and have to survive. `TaskDaoTest` covers the round trip and the ordering
contract. `TaskMappersTest` covers the translation in both directions.

The migration has also been run on a device: a version-1 build seeded with
seven tasks, upgraded in place, with every row and every version-1 column
compared before and after.
