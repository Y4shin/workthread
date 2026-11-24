---
type: task
subtype: grilling
title: Settle the operation set of ForStoringWork and the shape of snapshotFor
status: stable
workflow_state: done
blocked_by: []
---

Settle the operation set of `ForStoringWork` and the shape of `snapshotFor`, so
every later effort calls the store the same way.

### Already settled, and not to be reopened

The transaction boundary is one port call. The ports are kept few. The change
log row for a change is written inside that call's transaction. `ForStoringWork`
carries all eleven record kinds, the transaction, the change log append, and a
read of changes since a cursor. Every read of a changing thing returns the
complete current state plus changes since the caller's cursor plus a fresh
cursor. Cockburn's `For` prefix stays. The port lives in `contract`, which no
other module may be replaced for; `core` and `adapters` both depend on it.

### The choices to settle

1. Per-kind operations against a small verb set. Per-kind means
   `storeTask`, `storeProject`, `removeNote`, one read per kind, and so on, which
   is roughly fifty methods across eleven kinds. A verb set means one `store`,
   one `remove`, one `find`, one `list`, each taking a record envelope.
2. How the port stays type-checked if the verb set wins: a sealed record
   interface the compiler can narrow, or a kind tag plus a cast at one place in
   the adapter.
3. What a write returns: nothing, the stored record with its allocated
   identifier, the fresh cursor, or a result carrying all three.
4. `snapshotFor`: whether it is one method taking a query and a cursor, a per-
   query family, or a read that every query method already provides by taking a
   cursor. Its name is settled by the map; its signature is not.
5. Compound changes: a caller sometimes changes two things at once (a task and
   its note). Whether the port accepts a change set in one call or forces two
   calls, and what that costs against the one-call-one-transaction rule.
6. Errors: exceptions or a result type, and how not-found, conflict, refused, and
   a schema-newer-than-the-code refusal are each expressed.
7. Identifier allocation: the store allocates, so no operation takes an
   identifier on create. Whether an update takes the record or the identifier and
   fields.
8. Reads and the cursor: whether reads that return a cursor sit on the same port
   as writes, and whether a read can ask for changes only, with no state.

### Recommended starting answer

One port with a small verb set: `store(record)` returning the stored record with
its identifier and the fresh cursor, `remove(record reference)` for the kinds
that may be removed, `find(kind, id)`, `list(kind, filter)`, `changesSince(cursor)`,
and `snapshotFor(query, cursor)` returning state, changes, and the fresh cursor
in one result. A sealed record interface keeps the parameter type-checked. One
call stays one transaction, and a caller that needs two changes makes two calls
inside one coarse use case on the core side, never a transaction spanning the
port. Errors are a sealed result, not exceptions, because the core must tell
"not found" from "the schema is newer" without catching framework types.

### Consequences the answer creates

Every later effort's call sites, the core's use cases, the specification's port
section, and the fake the core's own tests will use in place of the real store.

### Depends on

Nothing to start. Item 2 needs the transaction facts from `sqlite-mechanics`,
which is why that task comes first in the queue.

## Notes

Rounds 1 to 3 of the grilling, answered by the operator. Two items are
deferred to research tasks and return as one more round when their findings
land: a compound change that spans records (`transaction-modeling`), and how an
edit travels through the port (`edit-shape`). The task stays open until those
two are settled and the operator confirms the whole record.

### 1. Port shape: per-kind methods, not one verb set

The port carries one method per kind and operation. The operator chose this
over a six-method verb set because he wants the port to keep the data valid as
it goes, and per-kind operations are where that intent can be stated. Generic
verb helpers are still wanted "wherever it can be done without exposing too
many `use_foot_gun()` to the coder", so a shared generic path sits behind the
per-kind methods rather than a second public shape.

Rejected: the six-method verb set over a record envelope, because the change
log and cursor allocation living in one place is not worth an untyped call
site; verb set plus typed overloads, because it is two public paths into one
adapter method.

Consequences: the port grows over several efforts as use cases land, and the
specification must name the initial operation set precisely.

### 2. `snapshotFor`: one query value, and the type coupling is possible

The operator accepted `snapshotFor(Query, Cursor)` on the condition that the
query and its result type are coupled in the type system, so a mismatched pair
is a compile error. It is: a sealed `SnapshotQuery<R>` with one permitted case
per projection, each fixing `R`, and `snapshotFor(SnapshotQuery<R> query,
Cursor cursor)` returning a result holding `R`. The fallback, one method per
query family, is therefore not needed.

Rejected, as a consequence: the per-query family (`snapshotTasks(cursor)`,
`snapshotPlan(cursor)`), and reads that carry their own cursor without a
`snapshotFor` at all.

### 3. Compound changes: deferred

Deferred to the `transaction-modeling` research task, created by this pass. The
operator's reason is that the question is a transaction question, and it should
be answered from what Java actually does, not from a preference.

### 4. Errors: a monadic result type we own

Every operation that can fail returns a hand-written monadic result type with
`map` and `flatMap`. No dependency is added for it. `@CheckReturnValue` from
`com.google.errorprone` marks the operations, and errorprone annotations are to
be used widely rather than only here. A lint rule forbids literal `throw` and
literal `catch`, and one abstraction wraps code that can throw, turns it into a
result, and panics on the spot when the wrapped code throws something
unexpected. The operator rejected try/catch as a concept: "a terribly designed
concept".

Rejected: a checked exception family in the signatures; the unchecked exception
family that Spring Data, JPA, jOOQ, and Jdbi use. Neither lets the caller match
on outcomes without either catch blocks or an import of an adapter type.

### 5. Operations per kind: only the ones a named use case needs

Each kind gets only the operations a named use case already requires. A later
effort adds one when its own use case lands. Removals count only where removal
is a real operation (a note is removed rather than edited).

Rejected: the uniform five operations per kind (about forty-five methods, most
uncalled); create and read only (no edit or removal, which the command line
effort needs at once).

### 6. Integrity: constraints plus adapter checks, with deferred checking

SQLite carries CHECK constraints, NOT NULL, and foreign keys turned on for the
facts a single row can state. The adapter checks the same facts so a refusal
carries a reason. Rules spanning records stay in the core. The operator added a
constraint on the checking: an atomic operation may defer validation until the
combined execution ends, so two writes that are each invalid alone and valid
together can be expressed. That deferral needs the transaction shape, which is
why it is carried by `transaction-modeling`.

Rejected: adapter checks with no constraints (a migration mistake can leave an
invalid row); constraints only (a refusal reads as a driver error, and
cross-record rules cannot be expressed).

### 7. What a write returns

A write returns the monadic result type. On success it holds the stored record,
with its allocated identifier. The operator's rule: return the record when the
implementation already holds it or the caller will use it, which he expects to
be often, so in practice always. The cursor is returned only where the caller
expects one, so the cursor belongs to the reads (`snapshotFor`,
`changesSince`), not to every write.

### 8. How an edit travels: deferred

The operator proposed a diff record per kind. For a note that is a `NoteDiff`,
built from a note or from nothing, with `withTitle(String)` and
`withoutTitle()`, and an update method taking a sealed input that accepts either
`Note` or `NoteDiff`. He asked for the Java convention to be researched first,
with pros and cons, as its own task. That task is `edit-shape`, created by this
pass, and the item returns as a later round.

### 9. Reading changes only

One port holds both directions. `changesSince(cursor)` returns only the changed
entries with no state, and `snapshotFor` returns state plus the same entries.

Rejected: changes only through `snapshotFor` (the store builds a snapshot
nobody reads); a separate change-feed port (a second port, a second adapter, and
two objects to wire into every surface and every fake).

### 10. Scope of the error-handling tooling

All of it lands in this effort: the result type, the errorprone dependency in
the version catalogue, the ban on literal `throw` and `catch`, and the wrapper.
The operator's reason: so it does not fall under the rug.

Consequences the specification must carry: `com.google.errorprone:error_prone_annotations`
appears in `gradle/libs.versions.toml` and is declared by every module that
annotates; the build gains a check that refuses a literal `throw` or `catch`
outside the wrapper; and the existing build checks are the precedent for it.

### What this pass does not settle

- Compound writes spanning two records, and how an atomic operation defers its
  integrity check: `transaction-modeling`.
- The shape of an edit: `edit-shape`.
- Which concrete operations exist per kind. This follows from "only named use
  cases" once the two research results are in, because the named use cases are
  what the specification must enumerate.

### 11. Rounds 4 and 5: the two deferred items, answered by research

The two items deferred after round 3 are settled. Both were answered from
`docs/research/transaction-modeling.md`, `docs/research/edit-shape.md`, and
`docs/research/change-set-logic.md`, all cited to primary sources.

**How an atomic multi-record write travels (`transaction-modeling`).** Adopted:
the adapter implements one write method taking an ordered change set, and every
per-kind operation is a default method that builds a one-element change set and
delegates to it. The per-kind methods you chose in round 1 survive; the shared
generic path you asked for is that one method. Rejected on evidence: a callback
that hands the caller a framework handle (jOOQ, Jdbi, Spring's transaction
template, JPA `runInTransaction`), a caller-opened JDBC transaction (it names
`java.sql.Connection` in `contract` and turns the core's fake into a database),
a unit-of-work object, and a lambda taking the port itself.

**What a change set does about logic between its writes.** Settled: the set is
pure data and runs no caller logic between writes. The chicken-and-egg case you
raised is covered by the deferred integrity check at the end of the set, so two
writes that are each invalid alone and valid together pass. A conditional change
carries an expected-state precondition; a decision that needs more is a rule the
core evaluates from a snapshot read. Recorded deliberately, in your words: this
choice is for simplicity for now, and the interface stays open to new approaches
if a use case needs one later. The research's function-valued change case is
recorded as the designed extension, with its costs: the change-log rows stop
being knowable before the call, the fake can no longer be a single map swap, and
`contract` would carry a function. The other shapes the research names, and that
were rejected, are a second read-modify-write method and a broader decider
shape.

**How a partial edit travels (`edit-shape`).** Adopted: a per-kind patch record
with one presence value per field. `Change<T>` has `Keep` and `Set` for a field
that cannot be cleared, `NullableChange<T>` adds `Clear` for a field that can,
and the compiler refuses a merge that forgets `Clear`. A caller holding a whole
record calls `replacing(record)`. The merge is a pure function in `contract`, so
the fake and the adapter cannot disagree about what a patch means. Your sealed
either-input is dropped: the research shows the `Replace` case still needs the
same read the patch needs, and it costs a sealed interface plus two case records
per kind. No version column and no expected-state parameter until a named use
case needs a conditional transition.

**What the store does with a patch that changes nothing.** It writes no column
and no change-log row, and returns the current record.

### 12. The initial operation set, confirmed

The port carries exactly these operations in this effort, one default method per
line, all delegating to the one write method or reading through the change feed:

- task: create, edit, read one, list
- project: create, edit, read one, list
- note: append, remove, list for a task
- dependency: add, remove, list for a task
- time entry: append, remove, list for a task, list for a session
- session: store, read one, list for a month
- time off: store, remove, list for a month
- settings: read, write
- notice: raise, dismiss, list
- pending operation: record, read one, list, delete, count a failed attempt
- override: record, read one, list
- operation log: append a lifecycle event
- change feed: changes since a cursor, snapshot for a query

Later efforts add an operation when their own use case lands. Rejected: a
narrower set holding only the kinds the first two consuming efforts use, because
the success test requires every kind to read back; and the uniform four
operations per kind, which is the frontloading you ruled out.

### Record of what the port is, at the close of this grilling

- One write method taking an ordered change set; one transaction per call; one
  change-log row per change, written in that transaction; the integrity check
  once at the end of the set.
- Per-kind default methods over it, the initial set named above, and no
  operation without a named use case.
- Reads: `changesSince(Cursor)` and `snapshotFor(SnapshotQuery<R>, Cursor)`,
  both returning the monadic result and a fresh cursor.
- Writes return the stored record inside the monadic result, with no cursor.
- Edits are per-kind patch records with explicit presence; a no-op patch writes
  nothing.
- Integrity is checked at the port, on the merged record, with SQLite
  constraints as the backstop, and rules spanning records stay in the core.
- The error channel is a hand-written monadic result type, with
  `@CheckReturnValue` from errorprone, a lint ban on literal `throw` and
  `catch`, and one wrapper that turns error-prone calls into a result and panics
  on an unexpected throw. All of that lands in this effort.
- Nothing framework-shaped or driver-shaped enters `contract`, so the core's
  fake replaces the port with plain collections.

Still to be decided by later phases, not by this grilling: the concrete change
records and patch records per kind (the specification), the rule for a change
set that names one record twice, the schema's column shapes (`schema-shape`), and
the JSON side of the three-state patch carrier, which the API effort must face
before it designs a request body.
