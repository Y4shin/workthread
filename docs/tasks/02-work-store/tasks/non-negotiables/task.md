---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the work store
status: stable
workflow_state: ready
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
