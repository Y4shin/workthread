---
type: task
subtype: grilling
title: Settle deletion, exception removal, midnight intervals, Review history, and persistence
status: stable
workflow_state: done
blocked_by:
- spec-additions
- stack-research
---

## Decision to settle

Settle what can be deleted, how storage behaves, and what history is kept.

- **DEC-10.** Can tasks and projects be permanently deleted, or only cancelled
  and archived?
- **DEC-11.** How are availability exceptions removed: an explicit delete, a
  toggle, or overwriting with an empty value?
- **DEC-12.** Can a weekly working interval cross local midnight? v1 currently
  interprets each interval within one local day.
- **DEC-14.** How does the Review view obtain planned time for past days and
  weeks: a stored projection snapshot, or a recomputation from the current
  rules?
- The persistence choice the effort must settle as a non-negotiable fact:
  schema migration strategy, file location and visibility, export format, and
  how only one service owner writes at a time.

## Parent decisions

The non-negotiables task settled that data stays local and that one service
owns it. `stack-research` supplies the persistence candidate evidence and its
locking behavior.

## Known choices

- DEC-10 alternatives: no permanent deletion (cancel a task, archive a
  project), permanent deletion with confirmation, or deletion only after a
  retention window.
- DEC-11 alternatives: an explicit remove action, a toggle that reverts to the
  weekly rule, or replacing the exception with an empty interval list.
- DEC-12 alternatives: forbid crossing midnight, split the interval into two
  local-day intervals, or interpret an interval by its start day.
- DEC-14 alternatives: recompute past projections from current data, store a
  daily projection snapshot, or store only allocations that actually happened.
- Persistence candidates: an embedded relational database with a migration
  tool, or versioned local files, in the visible data directory the
  specification requires.

## Recommended starting answer

DEC-10: no permanent deletion. Cancel a task, archive a project, keep the
history, and state why in the interface.

DEC-11: an explicit remove action that reverts the date to the weekly rule.

DEC-12: forbid crossing midnight, with a validation message that tells the user
to create two intervals. It keeps capacity computation per local day and keeps
daylight-saving behavior explainable.

DEC-14: recompute past projections from current data and label them as a
projection, because the specification already treats the itinerary as derived,
not stored. Store only what actually happened: time entries and completion
timestamps.

Persistence: the research task's recommendation, provided it supports one
writer, a visible data directory, atomic upgrades, and an export that does not
depend on the storage engine.

## Downstream work

Fixes the data model's lifecycle rules, the migration path, the export format,
and the Review view's data source. Feeds `child-efforts`, the scaffolding
child, and any child that owns import and export.

## Notes

### The settled rules

**DEC-11 and DEC-12 dissolved.** The capacity model no longer has availability exceptions, since per-date `vacation` and `flex` entries replaced them, and it no longer has clock intervals, since a weekday is a minute count inside one local day. So removing a per-date entry is an explicit removal, and a working day never crosses midnight. Nothing to decide.

**DEC-10, deletion. No direct deletion, plus an optional purge.** A task is cancelled and a project is archived; nothing is deleted on request. Configuration carries a purge age in days, off by default; when it is set, a record whose cancellation or archiving is older than that many days is permanently deleted, so the data does not accumulate dead weight. Nothing is ever deleted while the setting is off.

Details that follow: the moment of cancellation or archiving is recorded as a timestamp on the record, because the age is measured from it, which adds a cancellation timestamp to a task and an archival timestamp to a project. Purging an archived project takes its tasks, notes, time entries, and dependencies with it, and the setting's own description says so, because a project is a container and a silent cascade is worse than a plain sentence. Every purge writes a log entry, and a batch raises an ephemeral notice naming how many records went and how old they were. The purge is not an operation anyone can invoke directly; it is a scheduled consequence of the setting. Rejected: permanent deletion on request with a confirmation, which takes your only record of work with the record; and deletion limited to records with no history, which cannot remove the mistyped task that has one note, and which asks the application to explain a rule nobody remembers.

**Removal confirmation.** Removal asks for confirmation when, and only when, it changes something already reported: a per-date entry on a past date, a closed time entry, or a note. Plan-only removals, a future date entry or a task with no history, happen at once. The operation log keeps the removed content in either case, so the confirmation is about intent rather than recoverability.

**DEC-14, the review. Recomputed, with a named limitation.** The review compares each past day's target minutes with the presence recorded for it, both recomputed from current configuration at the moment it is shown. No past itinerary is stored and no past itinerary is reconstructed. The limitation is stated in the interface: the review answers how the days went against the hours you expect of yourself now, not against the hours you expected then. Changing a weekday's minutes today therefore changes last month's review, and changing it back changes it back. The upgrade path is to reconstruct the timetable as it stood on each date from the operation log, which already holds every change; that needs no new storage, only a reader.

**The export. A zip containing a JSON document and an attachments directory.** The archive holds one JSON document carrying a header, with the format version, the application version, and the export time, followed by the stored records, and it carries the identifier values and the sequence state so the data can be read without the database. When attachments arrive, they are included as a directory inside the same archive, so the layout does not change shape later. The format is documented and engine-independent. Rejected: one CSV per record kind, which loses the nesting of dependencies, notes, and time entries or splits one dataset across files the user must keep together, and a copy of the database file as the export, which is a backup and cannot be read without the application.

**Backup is a different artifact.** A backup is the database file itself, taken with `VACUUM INTO` or by copying the data directory while no service owner runs, as the specification allows. Stated plainly: the backup restores the application, the export survives the application.

**The operation log is not part of the export.** The export carries the user's planning data for reading and moving; the log is where the running application's history lives, and the backup already carries it. This also keeps the export honest about the specification's rule that it must not claim an import flow: v1 exports, and it restores by backup, not by import.

**Retention.** A dismissed ephemeral notice is deleted immediately, a finished or expired pending operation is deleted immediately, and the dismissal record of a persistent notice is dropped together with the condition that created it. The operation log is kept forever in v1: it is the history this design leans on for removed notes and overrides, and a windowed policy would first have to decide which history is worth keeping. Rejected: keeping the junk as well, and a retention window for the log now, which would cut history to save space nobody has run out of.

**Migration safety.** Each migration runs in one transaction together with its version and checksum row, so a failed migration leaves the previous schema. The application refuses to open a database whose schema version is newer than the version it knows, rather than guessing. Before migrations run, the store writes a copy of the database beside it and keeps the most recent one, because a transaction protects against a failure and this protects against a migration that succeeded and was wrong.

**The data directory.** Per user and visible, as the specification requires and in each platform's conventional location: `$XDG_DATA_HOME/workthread`, defaulting to `~/.local/share/workthread`, on Linux; `~/Library/Application Support/workthread` on macOS; `%LOCALAPPDATA%\workthread` on Windows. The database, the confirmation code file, the most recent pre-migration copy, and the export default destination all live there. The ownership lock stays in the runtime directory, which is what the architecture task settled.

### Consequences for dependent tasks

- `task-model-comparison`: the task record gains a cancellation timestamp and the project record an archival timestamp, both of which the purge age reads.
- `session-and-timetable`: removing a past per-date entry asks for confirmation, and removing a future one does not.
- `decisions-agent-interface`: the purge is not an agent-callable operation, and the export is.
- `architecture`: the data directory and the pre-migration copy join the storage decisions; the confirmation code file lives there too.
- `child-efforts`: the child that owns export and backup owns the zip layout, the format version, and the documented schema; the scaffolding child owns the data directory and the migration guard.

### New work and remaining fog

No new planning task. One thing is deliberately left to a later version rather than fogged: an import that reads the documented export, which the specification excludes from v1 and which the format now makes possible. Nothing remains foggy.
