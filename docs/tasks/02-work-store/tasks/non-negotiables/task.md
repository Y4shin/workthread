---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the work store
status: stable
workflow_state: done
blocked_by: []
---

Settle this effort's non-goals and non-negotiable facts, including its
effort-level success test. The scope, the requirement list, and the position in
the build order are already recorded below and come from the parent effort.

### The raw request, verbatim

The human asked for all twelve child efforts in one pass of intake. This child's
scope, quoted from the parent map's `## Child efforts` section:

> The task and project record model, including notes, statuses, and identifiers;
> the SQLite store behind `ForStoringWork`; ordered migrations with a version and
> checksums; the operation log; the change log with cursors; the ownership lock;
> the data location; and `snapshotFor`.

### Destination

A store that round-trips a task and a project, answers "what changed since this
cursor", refuses a second owner, migrates forward only, and is the single
transaction boundary every surface goes through.

### Actor

The developer building every later capability on it, and the person whose record
has to survive an upgrade, a crash, and a disk failure.

### Classification

Feature.

### Stories

1. As a person capturing work, I want a task and a project to survive a restart
   unchanged, so that the record is real rather than in memory.
2. As an agent or a window, I want to ask what changed since a cursor, so that I
   can stay current without refetching everything.
3. As a person running two copies by accident, I want the second one refused
   with a clear message, so that my data cannot be written by two owners.
4. As a person upgrading the application, I want migrations to run inside a
   transaction and a copy kept first, so that a failed upgrade leaves my data as
   it was.
5. As a developer, I want identifiers that are never reused, so that a reference
   in a log or an export can never point at the wrong record.

### Constraints

- SQLite through `org.xerial:sqlite-jdbc` 3.54.1.0, WAL mode, `busy_timeout`,
  one writer thread, never on a network filesystem.
- One port call is one transaction, and the change log is written inside the
  same transaction as the data it describes.
- The identifier is a monotonic integer allocated only by the data owner and
  opaque by rule; no surface may interpret or compare one.
- Nine stored record kinds: task, project, dependency, note, time entry,
  session, time off, notice, pending operation, plus settings.
- Data directory and runtime directory as settled, including `owner.lock`,
  `owner.json`, and the confirmation log file.
- This effort implements no surface, no planner, no export, and no purge.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-RULE-ID-1, SRC-RULE-STATE-1, SRC-RULE-RECALC-4, SRC-RULE-DATA-3,
    T04-006, T04-014, T05-051..T05-054, T11-010..T11-012, T11-050..T11-055,
    T12-001, T12-002, T12-005, T12-007, T12-009..T12-014, T12-029,
    T12-031..T12-033, T12-038, T021, T022, T023, T027, T028, T032, T033,
    T17-008, T17-010, T17-015, T17-028..T17-034, T17-037, T17-038, T17-041,
    T18-025, T20-023, MAP-031..MAP-033, MAP-059, CTX-note

That is 61 rows.

### Position and release, already settled

Second in the build order, blocked by `01-scaffolding`, release 0.1 Capture. Its
successor is `03-cli-basics`. The full order is the table under `## Effort order`
in the parent's architecture document.

## Notes

Settled in one grilling session, two rounds. The operator's answers are recorded in their terms.

### The record model is built here, not deferred

The effort defines every stored record kind in `contract` and creates every table in the first migration. The operator's reason: where the parent already decided a record's shape, there is nothing left to design, so deferring it only moves decided work to the tail of the effort chain. If a shape changes later, a migration is the normal path, not a problem.

The kinds are the task, project, dependency, note, time entry, session, time off, settings, notice, pending operation, and **override**. The override is a tenth kind the parent's list omitted, settled as its own record with a target, an affected rule (dependency order, capacity for the current day, or ordering), the surface and whether a human or an agent drove it, an instant, and an optional reason.

Rejected: the narrow reading, which would have built only the task, project, dependency, note, and settings records and added the rest through later migrations. The operator rejected it because it defers decided work rather than settling it.

### Documentation moves with the code

The operator's condition on the override: the repository must never hold two contradictory counts. So the tenth kind is recorded in this effort's architecture document and in the requirements register in the same write as the schema. The architecture document of the parent effort counts nine bullets because it bundles task and project together and omits the override; the new document states the eleven kinds explicitly. A register row that still says nine is superseded by a new row, never edited, because the register records what was settled and when.

Approved to support this: `docs/v1/requirements-lookup.py` gains a source-file lookup, since the two lookups it offers (by effort, by id) cannot list the rows that cite a given file.

### The one display formatter lives here

`contract` gains the single identifier formatter, rendering `TSK-0001` and `PRJ-0001`. Reason: register row MAP-031 is this effort's obligation, and a formatter written later by the CLI effort invites each surface to spell identifiers its own way. Rejected: leaving it to `03-cli-basics`.

### The store test seam

One test-only database builder owns the mode. A test asks for a real database file in a temporary directory or an in-process database. A build property overrides the request for the whole suite onto real files, and the override wins over what the test asked for, so the resolution lives in the builder and never in the test. One test always runs on a real file, so durability across an unclean close and the owner lock keep a safety net. A build check refuses a test that constructs the store or its database directly, so every store test goes through the builder.

Rejected: real files for every test, which makes the inner loop slow; and in-process by default with the committed pipeline opting into real files, which edits a file that `verifyCiWorkflow` checks and leaves the less faithful mode as the default.

### The floor, and why it was amended

The operator caught that the drafted success test could be satisfied by a store that round-trips only tasks and projects. The floor now states the breadth explicitly: the success test says every kind reads back unchanged, and the non-negotiable facts name all eleven kinds and say that a kind `ForStoringWork` does not carry is not stored. Non-goals exclude every surface: no command, MCP tool, API endpoint, window, or tray; no planner, ordering, forecast, or recalculation; no export, import, backup restore, or purge; no attachments; no daemon or API listener; no operation-log reader.

**Success test.** Every record kind this effort defines reads back unchanged through `ForStoringWork` after the process is killed, every change since a cursor is returned exactly once, and a second process is refused the data directory.

### Consequences for dependent efforts

- `03-cli-basics` finds the time entry and session tables already present and adds only its use cases.
- `08-mcp-agent-interface` finds the pending operation table already present.
- A later effort that needs a new field asks for a migration; it does not add a column by hand.
- Every later effort's store tests go through the same builder, so a change to the store's test mode is one change.

### Left open, and promoted to tasks

No fog remains. Three questions were precise enough to become planning tasks: the facts the SQLite driver and the engine give for the mechanisms this effort must prove (`sqlite-mechanics`), the concrete storage shape per kind and for the two logs (`schema-shape`), and the operation set of `ForStoringWork` including `snapshotFor` (`store-contract`).
