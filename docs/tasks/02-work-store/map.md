---
type: map
title: "Work store: the record model, the SQLite store, and the change log"
status: stable
origin_effort: planner-v1
---
# Work store: the record model, the SQLite store, and the change log

## Destination

A store every later surface reaches the work through: the record model for every
kind, the SQLite store behind `ForStoringWork` where one call is one
transaction, forward-only migrations carrying a version and a checksum per
script with a copy taken before they run, the change log whose sequence position
is the cursor, the operation log as history, the ownership lock that refuses a
second owner, the data and runtime locations, and `snapshotFor`. It ships no
surface: the command line, the API, the MCP interface, and the client build on
it in later efforts.

## Constraints

- SQLite through `org.xerial:sqlite-jdbc`, pinned in the version catalogue and
  resolved only in `adapters`; `contract` and `core` hold no driver type.
- WAL mode, a `busy_timeout`, and exactly one writer thread. Never on a network
  filesystem.
- One port call is one transaction, and the change log row for a change is
  written in that same transaction.
- Migrations are ordered SQL scripts shipped inside the core, applied at startup
  inside a transaction, each with its version and checksum recorded there. A
  database whose schema version is newer than the code is refused. The most
  recent copy taken before a migration is kept.
- The identifier is a monotonic integer allocated only by the data owner,
  declared `AUTOINCREMENT` so a value is never reused, and opaque by rule: no
  surface interprets one, compares two, or assumes adjacency.
- The data directory holds the database with its `-wal` and `-shm` siblings, the
  confirmation log, and the pre-migration copy. The runtime directory holds
  `owner.lock`, `owner.json`, and the transport credential, and the store never
  learns how that directory is resolved.
- Ownership is an exclusive `FileChannel.tryLock` on `owner.lock`, taken before
  the database opens. `owner.json` is a hint and the lock is the truth, and the
  file the lock is held on is never replaced.
- The core depends on no framework and on no module but the one holding the port
  interfaces.
- The build has eight modules and the effort adds none.

## Non-goals

- No surface of any kind: no command, no MCP tool, no API endpoint, no window,
  no tray.
- No planner, no ordering, no forecast, and no recalculation.
- No export, no import, no backup restore command, and no purge.
- No attachments: `ForStoringAttachments` stays reserved and unimplemented.
- No daemon, no API listener, and no `Authority`.
- No reader or viewer for the operation log.
- No rule enforcement beyond identity, status, and the timestamps. Blocked stays
  derived and is never stored, and product rules stay in the core.

## Non-negotiable facts

**Success test:** Every record kind this effort defines reads back unchanged through `ForStoringWork` after the process is killed, every change since a cursor is returned exactly once, and a second process is refused the data directory.

- The kinds are the task, project, dependency, note, time entry, session, time
  off, settings, notice, pending operation, and override. `ForStoringWork`
  carries all eleven, and a kind the port does not carry is not stored.
- The port has one adapter-implemented write method taking an ordered change
  set, and every per-kind operation is a default method that builds a one-element
  change set and delegates to it. One call opens one transaction, writes one
  change-log row per change in that transaction, and runs the integrity check
  once at the end of the set.
- The change set is pure data. It runs no caller logic between its writes, and
  the interface stays open to a function-valued change case if a named use case
  ever needs a decision taken on post-write state.
- A write returns the stored record inside the monadic result and no cursor. A
  cursor belongs to the reads: `changesSince`, and `snapshotFor` with a sealed
  generic query that fixes its own result type.
- An edit is a per-kind patch record with explicit presence, `Change` for a field
  that cannot be cleared and `NullableChange` for one that can. A patch that
  changes nothing writes no column, no change-log row, and returns the current
  record.
- A failure the caller must distinguish is a case of the monadic result, never a
  framework or driver exception. Literal `throw` and `catch` are refused by a
  build check outside the one wrapper.
- Integrity is checked at the port on the merged record, with SQLite CHECK
  constraints, NOT NULL, and foreign keys as the backstop, and rules spanning
  records stay in the core.
- Nothing framework-shaped or driver-shaped enters `contract`, so the core's own
  tests replace the port with plain collections.
- The first migration creates the table for every kind in one write, because
  migrations are forward-only.
- One transaction is one port call, and the change log row for a change is
  written in that same transaction.
- The override is an explicit, confirmed, recorded exception carrying a target,
  an affected rule of dependency order, capacity for the current day, or
  ordering, the surface it came from and whether a human or an agent drove it,
  an instant, and an optional reason.
- A task's stored status is one of inbox, ready, in progress, completed, or
  cancelled. Blocked is derived and never stored, leaving completed clears the
  completion time, and a cancelled or completed task is excluded from
  scheduling.
- An identifier is never reused and never renumbered, proved by a test that
  deletes a record and asserts the next identifier is not the deleted one.
- The one formatter in `contract` renders an identifier as `TSK-0001` or
  `PRJ-0001`, and no surface formats one its own way.
- Every store test opens its database through the one test-only builder. A real
  database file in a temporary directory is the default, a build property
  overrides the whole suite onto real files, one test always runs on a real
  file, and a build check refuses a test that constructs the store or its
  database directly.
- The eleven kinds are recorded in this effort's architecture document and in
  the requirements register in the same write as the schema, and a register row
  that states nine kinds is superseded rather than edited.
- The writer connection is opened once, before any statement runs, with the
  pragmas issued as explicit statements in one fixed order outside any
  transaction: `busy_timeout`, `journal_mode = WAL`, `synchronous = FULL`,
  `foreign_keys = ON`. Every write transaction opens with `BEGIN IMMEDIATE`,
  and the driver's autocommit flag is never read as evidence that a transaction
  is open. The facts behind this are cited in
  `docs/research/sqlite-mechanics.md`.

## Requirements

This effort owes the requirement ranges stated in its contract under `## Contracts per child effort` in `docs/tasks/planner-v1/architecture.md`. The requirements register holds 61 kept rows for `02-work-store`.

Read the register only through its lookup script: `python3 docs/v1/requirements-lookup.py effort 02-work-store` prints every row for this effort with the register's header, and `python3 docs/v1/requirements-lookup.py id <id>` prints one row with the same header. Any requirements lookup must use the script: do not open, grep, or filter `docs/v1/requirements-directory.csv` by hand. If a lookup the script does not offer is needed, extend the script first, with the operator's approval.

Every row is verified one by one before this effort may close, and the closing ticket records one verdict per row id with a pointer to the evidence in the repository. A group verdict does not cover a row.

## Decisions so far

- The `non-negotiables` grilling settled the effort's breadth: every stored
  record kind is defined in `contract` and every table is created by the first
  migration, rather than deferring kinds to the efforts whose use cases need
  them.
- The kinds are the task, project, dependency, note, time entry, session, time
  off, settings, notice, pending operation, and override. The override is the
  tenth kind the parent's list omitted, and it is stored as a first-class record
  with a target, an affected rule, the surface and actor, an instant, and an
  optional reason.
- The one identifier display formatter is built in `contract` here, not in
  `03-cli-basics`, because register row MAP-031 is this effort's obligation.
- Store tests go through one test-only database builder that owns the mode, with
  a build property that overrides every test onto a real database file, and a
  build check that refuses a test which constructs the store or its database
  directly.
- Documentation moves with the code: the eleven kinds are recorded in this
  effort's architecture document and in the requirements register in the same
  write as the schema, and a register row that states nine kinds is superseded
  rather than edited.
- The `sqlite-mechanics` research settled the driver and engine facts the
  specification quotes, recorded at `docs/research/sqlite-mechanics.md`:
  `journal_mode = WAL` is the only setting that persists in the file, the other
  three pragmas are per connection and must be applied on every open in a fixed
  order; the driver applies `SQLiteConfig` properties in no defined order, so
  the pragmas are issued as explicit statements; a write transaction opens with
  `BEGIN IMMEDIATE` and the driver's autocommit flag is not evidence that a
  transaction is open; `AUTOINCREMENT` is never reused across delete, rollback,
  or a `VACUUM INTO` restore, but a bare `.db` copy taken before a checkpoint
  can regress the counter; `VACUUM INTO` is available, refuses to run inside a
  transaction, and the most recent copy is kept beside the database; migrations,
  including their version and checksum rows, commit as one transaction; an
  unclean close loses nothing committed when `synchronous = FULL`, and the
  ownership lock survives the engine's own file locks.
- The `sqlite-mechanics` research leaves three things unproved, named in the
  findings and carried by tickets rather than assumed: nothing was executed
  against the pinned jar, the `journal_mode` of a `VACUUM INTO` copy is inferred,
  and the Windows lock release on forced termination is assumed. A spike test
  answers the first two, and the never-reuse rule gets its own test.
- The `transaction-modeling` research settled how an atomic multi-record write
  travels through the port, recorded at `docs/research/transaction-modeling.md`:
  the adapter implements one write method taking an ordered change set, and
  every per-kind operation is a default method that builds a one-element change
  set and delegates to it. So one call is one transaction, the deferred
  integrity check has an end to run at, and no framework type enters `contract`.
  Rejected on evidence: a callback handing the caller a framework handle, a
  caller-opened JDBC transaction in `contract`, a unit-of-work object, and a
  lambda taking the port itself, each of which either names a framework type in
  `contract` or makes the core's fake model nesting or a database.
- The `edit-shape` research settled how a partial edit travels, recorded at
  `docs/research/edit-shape.md`: a per-kind patch record carrying an explicit
  presence per field, with `Change<T>` for a value that may not be cleared and
  `NullableChange<T>` for one that may, so "leave alone", "set", and "clear" are
  three distinguishable states the compiler can check. A caller holding a whole
  record calls `replacing(record)`. The sealed either-input the operator
  proposed is dropped, because `Replace` still needs the read and costs a sealed
  interface plus two case records per kind. No Java data layer in the trusted set
  takes a hand-written per-kind diff as its update parameter, so the shape is a
  composition of recognised pieces rather than a convention; RFC 7396 and the
  update-mask pattern are its nearest relatives.
- The `edit-shape` research leaves two choices to the port and one risk to a
  later effort: whether a patch that changes nothing writes nothing or is
  refused, whether the identifier travels beside the patch or inside it, and that
  a JSON request body cannot distinguish an absent member from an explicit null
  without a custom deserializer, which the API effort must face before it designs
  a patch body.
- The `store-contract` grilling settled the port itself, on four rounds of
  answers and three research notes. It carries per-kind operations, not one
  verb set, and only the operations a named use case needs: the initial set is
  task create, edit, read, list; project create, edit, read, list; note append,
  remove, list; dependency add, remove, list; time entry append, remove, and list
  by task and by session; session store, read, list by month; time off store,
  remove, list by month; settings read and write; notice raise, dismiss, list;
  pending operation record, read, list, delete, and count a failed attempt;
  override record, read, list; operation log append; and the change feed. Reads
  are `changesSince(Cursor)` and `snapshotFor(SnapshotQuery<R>, Cursor)`, with
  the query's result type fixed by its sealed case, and a write returns the
  stored record with no cursor. A change set is pure data and runs no logic
  between its writes; the deferred integrity check at the end of the set is what
  lets two changes that are each invalid alone be applied together, and the
  interface stays open to a function-valued change case if a use case ever needs
  a decision on post-write state. Errors are a hand-written monadic result with
  `map` and `flatMap`, `@CheckReturnValue`, a lint ban on literal `throw` and
  `catch`, and one wrapper that turns error-prone calls into a result.
- The `store-contract` grilling settled the tooling scope too: the monadic
  result type, the errorprone annotations, the lint ban, and the wrapper all land
  in this effort rather than a later one, so the first code that uses the error
  channel already obeys the rule.
- The `change-set-logic` research, written for the mid-set question, recorded at
  `docs/research/change-set-logic.md`, is what the strict-set answer rests on:
  the domain literature keeps decisions inside the transaction, a decider
  function decides from state read before it, and a decision on post-write state
  needs either a callback or a function carried in the set. The function-valued
  case stays documented as the designed extension, with its costs named.

## Fog


