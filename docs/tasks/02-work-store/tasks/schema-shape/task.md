---
type: task
subtype: grilling
title: Settle the storage shape of the eleven kinds, the change log, and the operation log
status: stable
workflow_state: done
blocked_by:
- sqlite-mechanics
---

Settle the concrete storage shape of the eleven record kinds, the change log,
and the operation log, so the specification writes columns rather than
inventing them.

### Already settled, and not to be reopened

One table per kind is expected but is part of this decision, not before it. The
identifier is a monotonic `AUTOINCREMENT` integer. The change log's sequence
position is the cursor, and its row carries the surface and whether a human or
an agent drove the change. The change log row is written in the same transaction
as the data it describes. The operation log is history kept forever, and it also
records service lifecycle events such as the stop instant. There is no user
identity column. Instants come from `ForReadingClock` in the configured zone.

### The choices to settle

1. Column typing: instants as epoch milliseconds or ISO text; dates as ISO date
   text; whether any record carries a boolean as `INTEGER 0/1`.
2. Set-valued members: whether a set is always its own kind (notes and
   dependencies already are) or a join table plus a member table, and which
   kinds need one.
3. Settings: one row per key with a typed value, one row with a typed column
   per setting, or one row per settings family. The parent carried over "one key
   per member for set-valued fields" from the Taskwarrior comparison; say what
   that means here.
4. The change log's grouping: how the rows one port call wrote are returned
   together from a cursor, and whether that grouping is a transaction id column,
   a shared sequence range, or something else.
5. The operation log's separation from the change log: what each holds, whether
   a removal's removed content lives in the operation log, and how the logs stay
   in step.
6. The cursor's dress: what the opaque token actually is, whether it carries a
   version prefix, and what a caller that passes a token from an older version
   gets.
7. Foreign keys and deletion: which relationships are enforced, what cascades,
   and how "a note is removed rather than edited" and "tasks and projects are
   never deleted in v1" are expressed.
8. The per-kind fields the parent decided in prose: the override's target and
   affected rule, the session's break reserve, the time off kind, the notice's
   persistence and dismissal, the pending operation's code, expiry, attempt
   count, and payload.

### Recommended starting answer

Typed columns per kind, `INTEGER` epoch milliseconds for instants, ISO date text
for local dates, and no generic attribute bag: the eleven kinds are known, so a
bag would trade the compiler's checks for flexibility nothing needs. Settings as
one row per setting key with a typed value column and a known key list in
`contract`, so a new setting is a row and never a migration. The change log
carries a transaction id column, so a cursor read returns one port call's rows as
one group. The cursor is a version-prefixed base64 of the sequence position, and
an unrecognised prefix is refused rather than guessed at. Removed content lives
in the operation log, which is the only place history is read from.

### Consequences the answer creates

The specification's storage section, the migration script, the record tests, and
`store-contract`, which decides how records cross the port boundary.

### Out of scope for this task

Choosing the access library or the schema's names for surfaces to use. No code
is written here.

## Notes

Four rounds, all eight items settled, plus four follow-ups the answers created.
The full record is in the map's `## Non-negotiable facts`, because the storage
shape is what the migration and the specification are written from.

### 1. Column typing

Instants are `INTEGER` epoch milliseconds in UTC. Local dates are `TEXT` in
`YYYY-MM-DD`. Durations are `INTEGER` minutes. Booleans are `INTEGER` 0 or 1 with
a CHECK. Enumerations are `TEXT` with a CHECK naming the allowed values, not
integer codes, so a row is readable and a new value is a migration that rewrites
the CHECK. JSON text appears in exactly one place, the pending operation payload.
Rejected: ISO text instants (range queries compare text, and a per-row offset
breaks ordering) and Julian day numbers (unreadable, lossy, no Java type).

### 2. Set-valued members

Every member is a row in its own table with a foreign key to its owner: a note
carries a task id, a dependency is one row with two task ids, the timetable is
gone with settings. No join tables, because nothing in these kinds is
many-to-many. Rejected: a join plus member pair (every read joins, nothing
needs sharing) and a JSON column (no foreign key, no index, nothing checked).

### 3. Settings: not in the database

The operator's answer was that storing settings in a database "is stupid": the
business settings live in a separate file behind their own port, and this task
does not settle the file. Consequences recorded and acted on:
- the kinds drop from eleven to ten, and `ForStoringWork` carries ten;
- the settings read and write confirmed in the `store-contract` operation set no
  longer apply;
- register row `T11-012`, whose parenthetical lists settings and timetable inside
  `ForStoringWork`, is superseded rather than edited;
- register row `SRC-RULE-DATA-3` still holds, because it requires settings to
  survive upgrades and restarts and a file satisfies it;
- a new grilling task, `settings-storage`, owns the file's format, location,
  port, atomic write, and invalid-file behaviour.

### 4. Identity: a central record table, a shared internal id, per-kind display numbers

One central `record` table holds `(id, type)`, with `type` under a CHECK over the
ten kinds and `id` from one shared AUTOINCREMENT sequence. Each kind table
carries the same id, a composite foreign key on `(id, type)` into the central
table, a CHECK that its own type is its kind, and its own display number. The
operator's reasoning: the change log then foreign-keys into exactly one table
while the discriminator stays enforced on both sides.

The operator then split the identifiers: the shared sequence holds the internal
id, and each kind has its own counter for the number a person sees. So the port
takes and returns the per-kind display identifier, the internal id never crosses
the port, and a project can be PRJ-0001 while its internal id is 12. Both are
never reused. Rejected: the display identifier from the shared sequence
(interleaved numbers a person reads) and a private surrogate with the visible
number having no foreign key (two ids and a join for every feed reader).

### 5. Removal: soft everywhere

`removed_at` lives on each kind table, and the central record row never changes.
Every read filters on it. Per-date uniqueness is a partial unique index over the
rows that are not removed, which SQLite allows because the column is on the
indexed table. Nothing is hard-deleted in this effort, since purge is out of
scope. This is what lets the change log's foreign key hold while the log still
records a removal. Rejected: `removed_at` on the central table (a partial index
cannot see a parent column, so one session per date could not be enforced while
a removed session exists) and both places (two columns that can disagree).

### 6. The change log's grouping and content

A transaction column holds an integer allocated once per port call from a
counter row, incremented inside the same write transaction, so a cursor read
returns one call's rows as one group and a replaying client can apply a group
atomically. Rejected: a flat feed (no way to tell an atomic group) and a shared
sequence range (grouping inferred from writer discipline).

The row is minimal: kind, the record reference, create or update or remove, the
transaction, the surface, the actor, and the instant. A consumer that needs
values reads the snapshot the same call returns. Two derived parts the operator
confirmed at the gate: the row carries the kind and the display number beside the
internal key, so a feed reader never joins; and the internal key is a composite
foreign key into the central record table. Rejected: the changed field names
(the port must produce and maintain the list) and a before-image (large rows that
duplicate the operation log).

### 7. The two logs

The change log is the feed: one row per changed record, ordered by sequence, what
the cursor reads. The operation log is history: one row per operation with the
operation name, surface, actor, instant, outcome, the removed content as a
before-image, and lifecycle events such as the stop instant. Both are written in
the same transaction, and the history row names the changes it caused. Rejected:
one table with a type discriminator (the feed scans rows it must skip and
retention cannot differ) and a change log alone (a lifecycle event has no home).

### 8. The cursor

A version prefix and a base64url encoding of the 8-byte sequence position, for
example `v1:AAAAAAAAAGQ`. An unknown prefix, a wrong length, or a bad encoding is
refused with a result case and never guessed at. Rejected: the bare number (a
caller can construct and compare one) and a random token in a table (every read
writes, and a crash strands rows).

### 9. Foreign keys and deletion

Foreign keys are declared and enforced, with the central record table as the one
target for a record reference. Soft deletion replaces hard deletion, so no
relation ever dangles and no cascade is needed. A task and a project are never
removed in v1 because the port carries no operation for them; notes and
dependencies are removed by setting `removed_at`, with the before-image in the
operation log. A cascade waits for purge in a later effort, and purge is out of
scope here.

### 10. The per-kind fields

Adopted as listed in the grilling, and treated as preliminary: a later effort may
change a shape through a migration, which this effort already treats as the
normal path. Notable parts the parent left in prose and this task fixed: source
metadata is three columns (type defaulting to `manual`, optional reference,
optional URL); project status is active, paused, completed, or archived; a task
in `ready` requires a positive estimate, enforced by a row CHECK; the override's
target is two typed nullable columns with a CHECK that exactly one is set; the
notice carries type, level, lifetime, message, an optional condition key, and a
dismissal instant; the pending operation carries its operation name, code,
expiry, attempt count, and payload.

### What this task does not settle

The settings file, which is `settings-storage`. The migration script's exact DDL
and the change records per kind, which are the specification's. The JSON side of
the patch carrier, which the API effort owns.
