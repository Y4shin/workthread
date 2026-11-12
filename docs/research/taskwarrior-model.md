---
type: findings
title: "Taskwarrior task data model and semantics"
status: stable
source_task: taskwarrior-model-research
---

# Research: Taskwarrior's task data model and its semantics

Research date: 2026-02-14 (sources below are the live `develop` branch and the
current documentation site).

## Question

What is Taskwarrior's task data model, field by field, and what are its
semantics? Specifically: every stored attribute (name, type, default,
nullability, meaning), the identifier, the lifecycle statuses and legal
transitions, the on-disk storage format with atomicity and history behavior,
dependency semantics, the difference between annotations, tags, and UDAs, how
urgency is computed and whether it is stored, what `recur`/`wait`/`scheduled`
mean and do not mean, and what the model deliberately does not have.

Source boundary observed: taskwarrior.org documentation and man pages, plus the
`GothenburgBitFactory/taskwarrior` and `GothenburgBitFactory/taskchampion`
source repositories. No blogs or third-party summaries were used as load
bearing evidence.

## Sources

| # | Source | URL | Passage relied on |
|---|--------|-----|-------------------|
| S1 | Taskwarrior docs, "Task Representation" (surfaces the TaskChampion model) | https://taskwarrior.org/docs/task/ | "Tasks are stored internally as a key/value map with string keys and values. All fields are optional: the `Create` operation creates an empty task." Keys defined: `status`, `description`, `modified`, `start`, `end`, `tag_<tag>`, `wait`, `entry`, `annotation_<timestamp>`, `dep_<uuid>`. "Any unrecognized keys are treated as 'user-defined attributes' (UDAs)." |
| S2 | TaskChampion docs, "Task Model" (authoritative per S1) | https://github.com/GothenburgBitFactory/taskchampion/blob/main/docs/src/tasks.md | "`status` - one of `pending` for a pending task (the default), `completed`, `deleted`, `recurring`, or `iterative`". "`start` - the most recent time at which this task was started (a task with no `start` key is not active)". "`end` ... note that this key may not agree with `status`". "`annotation_<timestamp>` - value is an annotation created at the given time". "`dep_<uuid>` - indicates this task depends on another task identified by `<uuid>`; the value is ignored". |
| S3 | Taskwarrior JSON Format (RFC in the source repo; the documented interchange format) | https://github.com/GothenburgBitFactory/taskwarrior/blob/develop/doc/devel/rfcs/task.md | Full attribute/type table: `status` String, `uuid` UUID, `entry`/`start`/`end`/`due`/`until`/`wait`/`modified`/`scheduled` Date, `recur` String, `mask` String, `imask` Integer, `parent` UUID, `project` String, `priority` String, `depends` String, `tags` String (list), `annotation` String (list). "All tasks have four required fields ... At a minimum, a valid task contains: uuid, status, entry, description." "`status` ... may ONLY be one of these literal strings: pending, deleted, completed, waiting, recurring." |
| S4 | `src/Task.h` (develop) | https://github.com/GothenburgBitFactory/taskwarrior/blob/develop/src/Task.h | `enum status { pending, completed, deleted, recurring, waiting };` and public members `float urgency_value{0.0}; bool recalc_urgency{true}; bool is_blocked; bool is_blocking; int annotation_count;` — urgency, blocked-ness and annotation count are runtime fields on the object, not stored keys. Helpers `tag2Attr`, `dep2Attr`, `isAnnotationAttr` encode the key naming. |
| S5 | `src/Task.cpp` (develop) | https://github.com/GothenburgBitFactory/taskwarrior/blob/develop/src/Task.cpp | `getStatus()`: "Implement the 'virtual' Task::waiting status, which is not stored on-disk but is defined as a pending task with a `wait` attribute in the future." `setStatus()`: "the 'waiting' status is a virtual version of 'pending', so translate that back to 'pending' here". `run_urgency`/`urgency()`: "if (recalc_urgency) { urgency_value = urgency_c(); ... }" — computed and cached in memory. `parseJSON` comment: "// Urgency, if present, is ignored." `composeJSON`: "// Include urgency. if (decorate) out << ... urgency()". `validate_add()`: "A task must have a description." "A recurring task must also have a 'due' date." `validate()`: sets uuid, entry, end, modified; "Tasks with a wait: date get a special status"; applies `default.project`, `default.due`, `default.scheduled`, `uda.<name>.default`; `validate_before` warnings for wait/due, entry/start, wait/scheduled, scheduled/due. `addDependency`: "A task cannot be dependent on itself." / "Circular dependency detected and disallowed." `fixTagsAttribute()`: "Fix up the old `tags` attribute to match the `tag_..` attributes". `addAnnotation()`: "the time is incremented (one second) in order to find a unique timestamp." |
| S6 | Taskwarrior docs, "User Defined Attributes" | https://taskwarrior.org/docs/udas/ | "A UDA has a type, which may be one `string`, `numeric`, `date`, `duration` or `uuid`." "A very common request is for an `estimate` attribute ... Until now, the answer to most of these requests is to use tags or annotations to approximate the storage of the metadata." "Other attributes, such as `priority` do not have much associated functionality ... This is why `priority` is not really a core attribute, but a built-in UDA." "taskwarrior will preserve all orphan UDA data, but will not let you manipulate it." |
| S7 | Taskwarrior docs, "Terminology" | https://taskwarrior.org/docs/terminology/ | `UUID`: "A UUID never changes, and is not modifiable." `ID` is "simply a line number in the 'working set'". `start`: "Note that this does not imply there is time tracking in Taskwarrior, this is simply a log of start/stop times ... Many users confuse this log with legitimate time tracking capabilities." `wait`: "the task is hidden from reports until the 30th of the month, whereupon the wait date is removed". `scheduled`: "represents the first opportunity to begin work on a task". `until`: "the date that a task is automatically deleted. It is an expiration date." `ready`: "pending, is not blocked, and either has no scheduled date, or has one that is before now". `Rebuilding the Working Set (GC)`: "Tasks that are deleted and completed are moved out of the working set ... keeps the ID numbers as low as possible." |
| S8 | Taskwarrior docs, "Urgency" | https://taskwarrior.org/docs/urgency/ | "Taskwarrior uses a polynomial expression to calculate urgency. This is a simple sum of terms." Default coefficients listed: `urgency.user.tag.next.coefficient 15.0`, `urgency.due.coefficient 12.0`, `urgency.blocking.coefficient 8.0`, `urgency.uda.priority.H 6.0`, `urgency.scheduled 5.0`, `urgency.active 4.0`, `urgency.age 2.0`, `urgency.annotations 1.0`, `urgency.tags 1.0`, `urgency.project 1.0`, `urgency.waiting -3.0`, `urgency.blocked -5.0`. "The urgency caused by tags and annotations is further modified by the number of tags/annotations present: The factor is 0.8 for one, 0.9 for two and 1.0 for more." |
| S9 | Taskwarrior docs, "Columns" (`task columns` output) | https://taskwarrior.org/docs/commands/columns/ | Type column for every reportable attribute: `depends` string, `description` string, `due`/`end`/`entry`/`modified`/`scheduled`/`start`/`until`/`wait` date, `id` numeric (Read Only), `imask` numeric (Read Only), `mask` string (Read Only), `parent` string (Read Only), `priority` string, `project` string, `recur` duration, `status` string, `tags` string, `urgency` numeric, `uuid` string (Read Only). |
| S10 | Taskwarrior docs, "How Recurrence Works" | https://taskwarrior.org/docs/recurrence/ | "A recurring task is given a status of `recurring` ... the recurring task you create is called the template task, from which recurring tasks instances are created." "That `mask` is a string of task statuses ... `-` means that instance is still pending ... `+` (completed) or `X` (deleted) or `W` (waiting)." "`recurrence.limit` is set to 1, the default." |
| S11 | Taskwarrior man page `task(1)` 3.5.0 | https://taskwarrior.org/docs/man/task.1/ | `ID`: "Tasks can be specified uniquely by IDs, which are the indexes of the 'working set' ... The ID of a task may therefore change." `depends:<id1,id2 ...>`: "Declares this task to be dependent on id1 and id2 ... It accepts a comma-separated list of ID numbers, UUID numbers and ID ranges. When prefixing any element of this list by '-', the specified tasks are removed from the dependency list." `until:<expiration date of task>`: "after which it will be deleted." `wait:`: "Note that, for compatibilty, such tasks are shown as having status 'waiting', but this will change in a future release." `purge`: "Only tasks that are already deleted can be purged ... causes permanent, non-revertible loss of data." `undo`: "Reverts the most recent action." Tags: "Tags cannot contain whitespace, and cannot start with any of the special characters: `+-*/()<>^!%=~`". |
| S12 | `src/Context.cpp` (develop), embedded default configuration | https://github.com/GothenburgBitFactory/taskwarrior/blob/develop/src/Context.cpp | `data.location=~/.task`, `gc=1`, `dependency.reminder=1`, `dependency.confirmation=1`, `urgency.inherit=0`, `urgency.age.max=365`, `recurrence.limit=1`, `journal.info=1`, `journal.time=0`, `dateformat=Y-M-D`, `due=7`, plus the whole urgency coefficient block and `uda.priority.type=string`, `uda.priority.values=H,M,L,`. |
| S13 | TaskChampion docs, "Replica Storage" | https://github.com/GothenburgBitFactory/taskchampion/blob/main/docs/src/storage.md | "The storage is transaction-protected, with the expectation of a serializable isolation level." Contents: `tasks`, `base_version`, `operations`, `working_set`. "Every change to the task database is captured as an operation." Operation forms: `Create(uuid)`, `Delete(uuid, oldTask)`, `Update(uuid, property, oldValue, newValue, timestamp)`, `UndoPoint()`. "An 'undo' command reverses operations ... until it reaches an `UndoPoint` operation." "After operations are synchronized to the server, they can no longer be undone." |
| S14 | Taskwarrior docs, "Upgrading to Taskwarrior 3" | https://taskwarrior.org/docs/upgrade-3/ | "Taskwarrior 2.x stored tasks in files with the `.data` suffix. Taskwarrior 3.x stores tasks in `taskchampion.sqlite3` in your task directory." |
| S15 | `src/commands/CmdUndo.cpp` (develop) | https://github.com/GothenburgBitFactory/taskwarrior/blob/develop/src/commands/CmdUndo.cpp | `replica->get_undo_operations()` then `commit_reversed_operations(...)`; on failure prints "Could not undo: other operations have occurred." Confirm prompt: "The undo command is not reversible." |
| S16 | `src/dependency.cpp` (develop) | https://github.com/GothenburgBitFactory/taskwarrior/blob/develop/src/dependency.cpp | `dependencyIsCircular()` rejects cycles: "Returns true if the supplied task adds a cycle to the dependency chain." `dependencyChainOnComplete()`: prints "Task {1} is blocked by:", then offers "Would you like the dependency chain fixed?", and "Repair the chain - everything in blocked should now depend on everything in blocking, instead of task.id." `dependencyChainOnStart()`: nag only, no refusal. |

## Field table

Two layers must be kept apart. TaskChampion (the storage engine) defines a
small set of recognized keys and treats everything else as a UDA. Taskwarrior
(the CLI product) knows about a larger set of attributes, most of which are,
strictly speaking, TaskChampion UDAs with product semantics layered on top
(S1, S2). Nullability is uniform: any field may be absent (S1, S2).

Types below are the Taskwarrior-facing types from S3 and S9.

| Name | Type | Default | Nullable | Meaning and notes | Confidence |
|---|---|---|---|---|---|
| `uuid` | UUID string, read-only | generated on create | no (added by `validate()`) | Permanent identity, "never changes, and is not modifiable" (S7). Source comment notes "`uuid` is not a property in the TaskChampion model" (S5), i.e. it is the storage key, not a stored field. | high |
| `status` | fixed enum string, read-only via validation | `pending` | no in practice | Stored values are only `pending`, `completed`, `deleted`, `recurring` (S5 `setStatus`). `waiting` is virtual (see Lifecycle). TaskChampion docs additionally list `iterative` (S2). | high |
| `description` | string, no newlines | none | no on `add` (rejected), yes in raw storage | The one-line summary. `validate_add()` throws "A task must have a description." (S5, S3). | high |
| `entry` | date (epoch in storage, ISO-8601 in JSON) | now, set by `validate()` | effectively no | Creation time. Used for age and for `urgency.age` (S5, S8). Modifiable to backdate an old task (S7). | high |
| `modified` | date | now, set by `validate()` | generated | Most recent modification; "used as a reference when merging tasks" (S3). | high |
| `start` | date | absent | yes | "the most recent time at which this task was started (a task with no `start` key is not active)" (S2). Presence defines "active" (S7). A single timestamp, not an interval, not a total. | high |
| `end` | date | absent; set on completion/deletion | yes | Completion or deletion time. Removed when a task returns to `pending` (S3, S5). May disagree with `status` and consumers must cope (S2). | high |
| `due` | date | absent (config `default.due` can inject one) | yes | Deadline. "considered due" within 7 days, configurable via `rc.due` (S7). Feeds `urgency.due` (S8). | high |
| `scheduled` | date | absent (config `default.scheduled`) | yes | "the first opportunity to begin work on a task" (S7). Drives the `READY` virtual tag and the `ready` report. Does not block work, only labels readiness. | high |
| `wait` | date | absent | yes | "the time before which this task should be hidden, as it is not actionable" (S2). Reaching the date removes the wait date and the task reappears as pending (S7). | high |
| `until` | date | absent | yes | Expiry. Man page: "expiration date of a task, after which it will be deleted" (S11). On a recurrence template it instead ends the series (S3, S10). See Contradictions. | medium |
| `recur` | duration string, kept raw | absent | yes | Period between instances. "The value is kept in the raw state (such as '3wks') as a string, so that it may be evaluated each time it is needed" (S3). Requires `due` (S5). | high |
| `mask` | string of status chars | created with the first instance | template only | Per-instance status ledger on the template: `-` pending, `+` completed, `X` deleted, `W` waiting (S3, S10). Read-only column (S9). | high |
| `imask` | integer | 0 | instance only | Zero-based index into the parent's `mask` (S3). Read-only (S9). | high |
| `parent` | UUID | absent | instance only | UUID of the `recurring` template task (S3). | high |
| `depends` | multi-value UUID set | empty | yes | Stored as one `dep_<uuid>` key per dependency with value ignored (S1, S2), plus a legacy comma-joined `depends` string kept in sync by `fixDependsAttribute()` (S5). Exported as a JSON array (S5). | high |
| `tags` | multi-value word set | empty | yes | Stored as one `tag_<name>` key per tag, value ignored (S1, S2), plus a legacy comma-joined `tags` string kept in sync by `fixTagsAttribute()` (S5). No spaces; cannot start with `+-*/()<>^!%=~` (S11). | high |
| `annotations` | list of {entry, description} | empty | yes | Stored as `annotation_<epoch>` -> text keys (S1, S2). Key collision within one second is resolved by incrementing the timestamp by a second (S5). No annotation IDs. | high |
| `project` | string | absent (config `default.project`) | yes | Single string; `.` denotes hierarchy, so `Home.Kitchen` is inside `Home` (S3). | high |
| `priority` | string, one of `H`,`M`,`L`,`` | absent | yes | "not really a core attribute, but a built-in UDA", configured via `uda.priority.*` (S6, S12). | high |
| `urgency` | numeric (float) | computed | n/a | Not stored. `urgency()` computes and caches in memory; `parseJSON` explicitly ignores an incoming `urgency` key; export emits it only as a decoration (S4, S5). | high |
| `rtype` | string | `periodic` | generated | Recurrence type, defaulted by `validate()` for `recurring` tasks (S5). Not documented on the docs site. | medium |
| UDAs | `string` / `numeric` / `date` / `duration` / `uuid` | optional `uda.<name>.default` | yes | Any key TaskChampion does not recognize (S1). Declared in config with `uda.<name>.type`, `.label`, optional `.values`, `.default`; may carry an urgency coefficient (S6). Orphan UDAs are preserved but not manipulable (S6). | high |

## Lifecycle

**Stored statuses: four.** `pending`, `completed`, `deleted`, `recurring`
(S5 `setStatus`, S3, S2). `waiting` is *not* stored: `setStatus()` translates
`waiting` back to `pending` before writing, and `getStatus()` re-derives
`waiting` from "pending and `wait` in the future" (S5). TaskChampion's docs also
name an `iterative` status that TaskChampion supports but Taskwarrior's
recurrence does not use (S2).

| Status | Meaning for planning | Set by |
|---|---|---|
| `pending` | Actionable task. The default. Filtered in by most reports (`+PENDING`) (S12 report filters). | `add`, `modify status:pending`, `stop` does not change it |
| `completed` | Done. Requires `end`. Removed from the working set at GC (S7). | `done`, `modify status:completed` |
| `deleted` | Cancelled but retained with `end`. Removed from the working set at GC (S7). Reversible with `undo` or `modify status:pending` (S11). | `delete` |
| `recurring` | Template task, hidden from normal reports, holds `mask`. Not user-facing (S10). | `add due:... recur:...` (validate promotes pending+due+recur with no parent) |
| `waiting` (virtual) | Hidden until `wait` passes. Not a stored state. | derived |

**Legal transitions, as evidenced:**

- `add` creates `pending`, unless `due` + `recur` are both present and there is
  no parent, in which case `recurring` (S5).
- `pending -> wait` set means hidden but still `pending` on disk; when the date
  passes the task is `pending` and visible again; the `wait` date is removed
  (S5, S7). There is no separate transition for this.
- `pending -> completed` (`done`), sets `end` (S5).
- `pending -> deleted` (`delete`), sets `end` (S5).
- `completed|deleted -> pending` ("restore"), which removes `end` (S3, S5).
- `recurring -> completed` when `until` is reached (S3).
- Template spawns `pending` children linked by `parent`/`imask`; child status
  changes write back into the template's `mask` (S10).
- `deleted -> gone` via `purge`, which is local-only, unsynced, and
  irreversible (S11).
- No transition into `recurring` other than at creation (`validate` derives it;
  the `parent`/`template` guards prevent promotion of instances).

**Identifier.** Two identifiers. The UUID is permanent and non-modifiable
(S7). The ID is "the indexes of the 'working set' of tasks (mostly pending and
recurrent tasks). The ID of a task may therefore change, but only when a report
that displays IDs is run" (S11). The working set is a `integer -> UUID` map,
explicitly "used to keep stable small-integer indexes into the tasks for users'
convenience", and is not synchronized (S13). GC renumbers IDs to keep them low
(S7). Planning consequence: any external reference must use the UUID; IDs are a
UI convenience with no stability guarantee.

## Storage and history

- **Current (3.x):** one SQLite database, `taskchampion.sqlite3`, in the data
  directory (default `~/.task`, `data.location` in S12; S14). Schema-level
  tables: `tasks` (keyed by UUID), `base_version`, `operations`,
  `working_set` (S13).
- **Legacy (2.x):** flat text `.data` files, one line per task, several
  historical line formats. `Task::determineVersion()` in S5 still documents
  them, e.g. version 4 is `[name:"value" ...]` and version 2/3 begin with
  `uuid status [tags] [attributes] description`. S14 tells users to import them
  with `task import-v2` and then delete them.
- **Atomicity:** the replica store is "transaction-protected, with the
  expectation of a serializable isolation level" (S13). TaskChampion also
  documents a design constraint the schema exists to satisfy: no
  read-modify-write. Multi-valued fields are stored as one key per member
  (`tag_<tag>`, `dep_<uuid>`, `annotation_<ts>`) precisely so that "adding a tag
  or deleting a dependency [can] be represented in a single modification", and
  concurrent replicas cannot lose each other's tags (S1, S2).
- **History:** every change becomes an operation: `Create`, `Delete(oldTask)`,
  `Update(property, oldValue, newValue, timestamp)`, `UndoPoint` (S13).
  `UndoPoint` marks user-visible transaction boundaries, so `undo` reverts a
  whole command rather than one property write (S13). `undo` implements this as
  `get_undo_operations()` + `commit_reversed_operations()`, and refuses with
  "Could not undo: other operations have occurred." if the sequence moved
  (S15). Once operations have been synchronized they "can no longer be undone"
  (S13). Synchronized operations are retained "as a log of changes to the
  relevant tasks" (S13), which is what `journal.info=1` (S12) surfaces in the
  `info` report.
- **Undone and deleted tasks:** neither is a separate file or a tombstone
  table. A deleted task is a normal task row with `status=deleted` and an `end`
  date (S3, S5); `undo` reverses the `Update` operations that produced it
  (S13). Only `purge` removes data, and it is local-only and unsynced (S11).
- **Export/import** is JSON, one object per line, ISO-8601 UTC dates, and is
  explicitly the supported integration path (S3, S7). It carries end state, not
  history (inference from S3's attribute list having no history field, and S13
  keeping operations separate).

## Dependencies, tags, annotations, UDAs

**Dependencies.** `depends` accepts a comma-separated list of IDs, UUIDs and ID
ranges, with `-` prefixing an element to remove that dependency (S11). It is
stored one key per UUID (`dep_<uuid>`) so that concurrent edits merge (S1, S2).
Enforcement that exists:

- Self-dependency rejected: "A task cannot be dependent on itself." (S5)
- Cycles rejected: "Circular dependency detected and disallowed." via
  `dependencyIsCircular()`, which only traverses the pending task cache (S5,
  S16).
- Completing or deleting a task in the middle of a chain prints "Task N is
  blocked by:" and, if `dependency.confirmation` is on, offers to repair the
  chain by re-pointing dependents at the blockers (S16, S12).
- Starting a blocked task prints the same nag (S16).
- Virtual tags `BLOCKED`/`UNBLOCKED`/`BLOCKING` and the `blocked`, `blocking`,
  `unblocked` reports exist (S11, S5).
- Urgency is adjusted: blocked `-5.0`, blocking `+8.0` (S8).

What it does **not** do: it does not refuse to complete or start a blocked
task (S16 shows a nag and a repair offer, no abort); it does not schedule,
sequence, or allocate anything; it carries no type (no finish-to-start vs
start-to-start); and it is not a graph the product reasons over beyond
cycle rejection and this one-hop chain repair. `urgency.inherit=0` by default
(S12), so by default a parent does not inherit its dependents' urgency.

**Tags vs annotations vs UDAs.**

| | Tags | Annotations | UDAs |
|---|---|---|---|
| Shape | set of single words, unordered, no spaces, restricted leading chars (S11) | ordered-by-timestamp list of {entry, description} text blobs (S3) | named scalar field with a declared type (S6) |
| Storage | one `tag_<name>` key each (S1, S2) | one `annotation_<epoch>` key each (S1, S2) | one plain key (S1) |
| Structure carried | none beyond set membership; some names are "special" (`+next`, `+nonag`, `+nocal`, `+nocolor`) (S11) | none; free text, no author, no markup, no editing, only add/remove (S7, S11) | yes: the only part of the model with a declared type, allowed-value list, default, label and urgency coefficient (S6) |
| Queryable in reports | yes, as a column and as filters | yes, count or full text | yes, as columns, filters, and orphan detection (S11 `udas` command) |
| Urgency input | `urgency.tags` scaled 0.8/0.9/1.0 by count (S8) | `urgency.annotations` scaled the same way (S8) | `urgency.uda.<name>[.<value>].coefficient` (S6, S8) |

Neither tags nor annotations can express a key/value pair. That is the entire
reason UDAs exist (S6). Annotations are immutable text: there is no command to
edit an annotation, only `annotate` and `denotate` (S11).

**UDAs.** Declared in config, not in the data: `uda.<name>.type` (one of
`string`, `numeric`, `date`, `duration`, `uuid`), `uda.<name>.label`,
optional `uda.<name>.values` for a string enum, optional
`uda.<name>.default`, optional `urgency.uda.<name>.coefficient` (S6). They are
surfaced as report columns, as filter attributes, in `info`, in `task udas`
output, and they round-trip through JSON (S6, S11). Undeclared keys found in
data are "orphan UDAs": preserved, but not usable in reports or filters (S6).
Critically, `priority` itself is a built-in UDA (`uda.priority.type=string`,
`values=H,M,L,` in S12), which is the cleanest evidence for how thin the
"core attribute" line is.

## Urgency

- **Computed, not stored.** `Task::urgency()` returns a cached `float
  urgency_value` recomputed only when `recalc_urgency` is set (S5). `parseJSON`
  ignores an incoming `urgency` key outright (S5). `composeJSON` emits
  `urgency` only when `decorate` is true, i.e. as an export decoration (S5).
- **Formula:** a polynomial sum of coefficient x normalized term, each term
  scaled to 0..1 (`U = A.t_a + B.t_b + ...`) (S5, S8).
- **Inputs and default weights** (S8, S12): `next` tag 15.0; due 12.0;
  blocking 8.0; priority H 6.0, M 3.9, L 1.8; scheduled 5.0 (only once the
  scheduled date is in the past, per S5 `urgency_scheduled`); active 4.0
  (has `start`); age 2.0 (linear to `urgency.age.max=365` days); annotations
  1.0; tags 1.0; project 1.0; waiting -3.0; blocked -5.0. Plus arbitrary
  `urgency.user.project.<p>.coefficient`, `urgency.user.tag.<t>.coefficient`,
  `urgency.user.keyword.<k>.coefficient`, `urgency.uda.<n>[.<v>].coefficient`
  (S5, S8, S12).
- **Term normalization details worth knowing:** due is mapped over a 21 day
  window from 0.2 (more than 2 weeks out) to 1.0 (7 or more days overdue)
  (S5 `urgency_due`); tag and annotation terms use 0.8 / 0.9 / 1.0 for 1 / 2 /
  3+ (S8, S5); age returns 1.0 if `entry` is missing (S5).
- **`urgency.inherit=0`** by default; when enabled, a blocking task takes the
  max urgency of its blocked descendants, plus 0.01 so it sorts above them
  (S5, S12).
- Because it is derived from "now" and from the dependency graph, urgency is a
  *ranking signal recomputed per run*, not a persisted planning input.

## What the model does not have

Absent by design, evidenced by the complete attribute enumerations in S1, S2,
S3 and S9, plus direct statements where available:

1. **No estimate attribute.** There is no effort/size/points field. The UDA doc
   says the request for an `estimate` attribute is "very common" and the
   documented answer is "use tags or annotations to approximate" or define a
   UDA (S6). A UDA named `estimate` is user data with no semantics; the product
   never computes with it except as an urgency coefficient.
2. **No time tracking.** Explicit: "this does not imply there is time tracking in
   Taskwarrior, this is simply a log of start/stop times ... Many users confuse
   this log with legitimate time tracking capabilities. Don't make this
   mistake." (S7). The only time facts are `start` (most recent start
   timestamp), `end`, and optional `journal.time` annotations for start/stop
   (S2, S12). There is no accumulated duration field, no running timer state,
   no per-day time entries.
3. **No allocation or assignment.** No assignee, owner, resource, or
   collaborator field (S3, S9). Single-user by construction; sync shares one
   user's dataset, not a team's.
4. **No per-day capacity, calendar load, or scheduling engine.** There is no
   capacity, availability, work-hours, duration, or slot concept. `scheduled`
   is a "first opportunity to begin" marker (S7); `due` is a deadline. Nothing
   in the model can express "4 hours of work on Tuesday". The only date
   arithmetic is per-task thresholds (`rc.due=7`) and urgency decay.
5. **No subtask hierarchy.** `parent` exists but means "recurrence template",
   not "parent task" (S3, S10). Project `.` hierarchy is a naming convention
   for grouping, not containment of tasks (S3).
6. **No structured annotations.** Free text only, no author, no type, no
   attachment (S7, S11).
7. **No per-dependent edge semantics.** `depends` is a bare set of UUIDs with
   no edge type (S1, S3).
8. **No priority beyond three levels** unless the user redefines the
   `uda.priority.values` list (S12), and no ranking other than urgency.
9. **No history field on the task.** Change history lives in the operation log,
   not in the task (S13).
10. **No field is required at the storage layer.** "Any key/value map is a valid
    task, including an empty task" (S2). All requirements are product-level
    validation (`validate_add`, `validate`) (S5).

## Open questions

1. **`until` semantics conflict.** S11 and S7 say `until` is a general expiry
   date after which the task "will be deleted". S3 says `until` is a recurring-
   task field: "the date after which no more recurring tasks should be
   generated. At that time, the parent recurring task is set to completed."
   Whether a non-recurring task with `until` is actually deleted, and by which
   code path (GC vs read-time), was not verified in source. Medium confidence;
   treat as unresolved.
2. **When exactly does `until` deletion happen?** No `until` handling appears in
   `Task::validate()`. It is probably in the GC path (`gc=1`, S12) but I did not
   confirm it.
3. **`urgency` column modifiability.** The `columns` output (S9) labels
   `urgency` as "Modifiable", yet the parser ignores an incoming `urgency` and
   the value is recomputed (S5). Whether `task modify urgency:N` is accepted,
   and whether a stored value has any effect, is unresolved. Probably inert.
   Low impact.
4. **`rtype`.** `validate()` defaults `rtype=periodic` on recurring tasks (S5),
   but `rtype` appears in no documentation page or in the JSON RFC. Its role in
   Taskwarrior 3 recurrence is unclear.
5. **`template` / `last` keys.** `Task::hasTag` tests `has("template")` and
   `has("last")` as template markers (S5), while S3 documents `parent`/`mask`/
   `imask`. Both may exist across versions. Not resolved.
6. **TW2 `undo.data` history file** is referenced only in third-party
   discussion, not in a primary source I read. Not used as evidence here.
7. No primary source enumerates "what the model deliberately lacks" as a
   single list; item list above is assembled from complete attribute
   enumerations (an argument from absence) plus the two explicit statements in
   S6 and S7. Flagged as interpretation in the relevant rows.
