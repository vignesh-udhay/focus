# Storage

How tasks are stored, and how the stored shape changes over time.

---

# The table

One table, `tasks`, one row per task, written through `TaskDao` and read only
by `TaskRepository`. `TaskEntity` never leaves the data layer, and `TaskQueries`
derives every view from the domain model rather than from SQL.

---

# Versions

    1  the original nine columns
    2  adds notes
    3  adds recurrence
    4  adds spawnedFromId

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

Versions 3 and 4 are the same statement against `recurrence` and
`spawnedFromId`, and null means the same kind of thing in each: a task written
before the column existed does not recur, and was made by a person rather than
spawned by finishing a recurring one. Nothing to backfill in either case.

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
as the old app would have, runs the real migration, validates the result
against the exported version-2 schema, and reopens the file through
`FocuslistDatabase`. `TaskDaoTest` covers the round trip and the ordering
contract. `TaskMappersTest` covers the translation in both directions.

The migration has also been run on a device: a version-1 build seeded with
seven tasks, upgraded in place, with every row and every version-1 column
compared before and after.
