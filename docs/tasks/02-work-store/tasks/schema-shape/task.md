---
type: task
subtype: grilling
title: Settle the storage shape of the eleven kinds, the change log, and the operation log
status: stable
workflow_state: ready
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
