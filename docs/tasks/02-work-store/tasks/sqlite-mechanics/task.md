---
type: task
subtype: research
title: What the pinned SQLite driver and engine give for WAL, transactions, migrations, and durability
status: stable
workflow_state: done
blocked_by: []
---

What do `org.xerial:sqlite-jdbc` 3.54.1.0 and the SQLite version it bundles
actually give for each mechanism this effort must prove? One answer per
mechanism, each with a citation:

1. Connection setup: the exact statements for WAL mode, `busy_timeout`,
   `synchronous`, and `foreign_keys`, whether each is per connection or
   persistent in the file, and the order they must run in.
2. Transaction control through JDBC: how to make one port call one transaction
   that includes the change log row (autocommit off, `BEGIN IMMEDIATE`, rollback
   on failure), and what a connection in a bad state does to the next call.
3. `AUTOINCREMENT` and `sqlite_sequence`: what happens after a delete, after a
   rollback, and after a restore by copying the file, so the never-reuse rule is
   a fact rather than an assumption.
4. `VACUUM INTO` for the pre-migration copy: availability in this version, the
   destination rules, and whether it can run inside the migration transaction.
5. Migrations inside a transaction: which statements refuse to run in one, how a
   failed script leaves the previous schema, and whether the version and checksum
   rows can be written in the same transaction as the script.
6. Reproducing an unclean close from a test: what killing a child JVM, or closing
   without a checkpoint, does to a committed record in WAL mode, and what
   `synchronous` setting makes the answer stable across the three platforms.
7. Where an in-process database differs: WAL support, file locks, and anything
   else that would make an in-memory test prove less than the real one.
8. The ownership lock: opening the database while a `FileChannel` lock is held on
   a sibling file, and how a second opener fails.

### It unblocks

`schema-shape`, and the durability and migration sections of the specification.
The success test in the map needs item 6 answered before a ticket can claim it.

### Trusted sources

`sqlite.org` documentation for pragmas, transactions, ALTER TABLE limits, and
`VACUUM INTO`; the `xerial/sqlite-jdbc` repository and its issue tracker for
driver behaviour; the pinned versions in `gradle/libs.versions.toml` and
`adapters/build.gradle.kts`. Anything from a blog or a forum is a lead, not
evidence.

### Evidence required

A findings note at
`docs/tasks/02-work-store/tasks/sqlite-mechanics/findings.md`, answering all
eight items, one citation per material claim, a confidence per item, and the
concrete connection-setup statements the store will use. Unresolved questions
are named, not smoothed over.

### Likely dependents

`schema-shape` is blocked by this task. `store-contract` reads item 2: the port
shape follows from how a transaction is actually opened.

## Notes

All eight items answered with primary-source citations and a confidence per item.
The findings are at `docs/research/sqlite-mechanics.md`, following the existing
`docs/research/` convention (`type: findings`, `source_task: sqlite-mechanics`).

The concrete open sequence the store will use is quoted in that file and copied
into the map's `## Non-negotiable facts`: lock `owner.lock` first, then one
driver-configured connection, then the four pragmas as explicit statements in a
fixed order outside any transaction, then the version check, the `VACUUM INTO`
copy, and the migrations.

What changed against the assumptions this effort started with:
- The driver applies `SQLiteConfig` properties in no defined order, so pragmas
  are issued as statements rather than set as config properties.
- `getAutoCommit()` reports the driver's own flag, not the engine's state, so it
  is never used as evidence that a transaction is open.
- `VACUUM INTO` refuses to run inside a transaction, so the pre-migration copy
  is taken before the migration transaction opens.

What is not proved and must be carried by tickets rather than assumed: nothing
was executed against the pinned jar, the `journal_mode` of a `VACUUM INTO` copy
is inferred, the Windows lock release on forced termination is assumed, and the
`sqlite_sequence` behaviour after copying only the `.db` is derived rather than
observed. The file's "Next steps" names the two spike tests that close the
first three.
