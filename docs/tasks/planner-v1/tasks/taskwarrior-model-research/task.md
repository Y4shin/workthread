---
type: task
subtype: research
title: Taskwarrior's task data model and its semantics
status: stable
workflow_state: done
blocked_by:
- spec-additions
---

## Precise question

What is Taskwarrior's task data model, field by field, and what are its
semantics?

Answer at least these:

- Every field stored on a task: name, type, nullability, default, and meaning.
  Include the description, project, priority, due, scheduled, wait, until,
  depends, tags, annotations, user-defined attributes, urgency, recur, entry,
  modified, start, end, and status.
- The identifier: what Taskwarrior uses, and why.
- The lifecycle: which statuses exist, which transitions are legal, and what
  each state means for planning.
- The storage format on disk, its atomicity and history behavior, and how
  undone and deleted tasks are represented.
- Dependencies: what `depends` accepts, how it is enforced, and what it does
  not do.
- Annotations and tags: how they differ, and whether either carries structure.
- User-defined attributes: how they are declared, typed, and surfaced.
- Urgency: which inputs feed it, how it is computed, and whether it is stored.
- Recurrence and the wait and scheduled dates: what they mean and what they do
  not mean.
- What the model deliberately does not have: estimates, allocations, per-day
  capacity, time tracking beyond a start time.

## Decision or task it unblocks

`task-model-comparison`, which compares this model with the specification's
task model and decides what to adopt.

## Trusted source boundaries

Primary sources only: the Taskwarrior documentation site, its man pages
including `task(1)`, `taskrc(5)`, and the data format documentation, and the
Taskwarrior source repository. Cite the page or file for every claim. Do not
treat blog posts or third-party summaries as load-bearing evidence.

## Evidence required for completion

A field table with type, meaning, default, and nullability. A lifecycle
description with the legal transitions. A list of what the model lacks. Each
material claim with a citation and a confidence note. Findings captured at
`docs/tasks/planner-v1/tasks/taskwarrior-model-research/findings.md` with the
`type: findings` frontmatter, plus a short write-back to the map.

## Likely dependent tasks

`task-model-comparison`. If the research finds a model feature that changes v1
scope, record it as discovered work instead of widening this task.

## Notes

### Findings

Full evidence, source table, and the open questions: `docs/research/taskwarrior-model.md`.
The record below is the decision-grade digest.

**Method.** Primary sources only: the Taskwarrior documentation site, its man
pages, and the Taskwarrior and TaskChampion source repositories. Four source
contradictions are recorded in the full file rather than smoothed over.

**The model in one paragraph.** A task is a flat key/value map of strings, with
no required key at the storage layer. Recognized keys are a small set; every
other key is a user-defined attribute. Multi-valued fields are stored one key
per member (`tag_<name>`, `dep_<uuid>`, `annotation_<epoch>`) so a single
modification can add or remove one member without a read-modify-write.

**Field table, condensed.**

- `uuid`: permanent, non-modifiable identity. The numeric `id` is only a line
  number in the working set and changes, so external references must use the
  UUID.
- `status`: one of `pending`, `completed`, `deleted`, `recurring`. `waiting` is
  virtual, derived from a pending task whose `wait` date is in the future, and
  is translated back to `pending` on write.
- `description`: one line, required by validation, not by storage.
- `entry`, `modified`: creation and last-change timestamps.
- `start`: the most recent start time. Its presence defines "active". It is a
  single timestamp, not an interval and not a total.
- `end`: completion or deletion time; removed when the task is restored.
- `due`: deadline.
- `scheduled`: first opportunity to begin work; a readiness label, not a
  constraint.
- `wait`: hidden until the date passes; reaching it removes the date.
- `until`: expiry. The sources disagree on whether it deletes a normal task or
  only ends a recurrence series; unresolved.
- `recur`, `mask`, `imask`, `parent`: recurrence template and its instances.
- `depends`: a set of UUIDs, one key each.
- `tags`: a set of words, no spaces, restricted leading characters.
- `annotations`: free text keyed by timestamp, add-only, no author, no ids.
- `project`: one string, with `.` for a naming hierarchy.
- `priority`: `H`, `M`, `L`, or empty. It is literally a built-in user-defined
  attribute, which shows how thin the core-attribute line is.
- `urgency`: a float that is computed and cached in memory, never stored, and
  explicitly ignored on input.
- User-defined attributes: declared in configuration with a type of string,
  numeric, date, duration, or uuid, plus an optional allowed-value list,
  default, label, and urgency coefficient.

**Lifecycle.** Four stored statuses and one derived. Legal transitions:
create to pending; pending to completed or deleted, each setting `end`;
completed or deleted back to pending, clearing `end`; recurring to completed at
`until`; deleted to gone only by `purge`, which is local, unsynced, and
irreversible. Recurrence templates spawn children linked by `parent` and
`imask`, and child status writes back into the template's `mask`.

**Storage and history.** One SQLite database, `taskchampion.sqlite3`, in the
data directory, replacing the 2.x text files. The store is transaction
protected at serializable isolation. Every change becomes an operation:
create, delete with the old task, update with old and new value and timestamp,
and an undo point that marks a command boundary. Undo reverses operations back
to a boundary and refuses if the sequence moved. Tasks carry no history field;
the operation log is the history. Deleted and undone tasks are not tombstones
in a separate file: a deleted task is a normal row with a status and an end
date.

**Dependencies, tags, annotations, user-defined attributes.** Dependencies
accept ids, UUIDs, and ranges, reject self-edges, and reject cycles by
traversing the pending cache. Completing or starting a blocked task prints a
warning and offers to repair the chain; it does not refuse. There is no edge
type and no scheduling. Tags are an unstructured word set. Annotations are
immutable free text. Neither can express a key and value, which is exactly why
user-defined attributes exist. Urgency is a weighted polynomial over due,
blocking, priority, scheduled, active, age, annotations, tags, project,
waiting, and blocked, with per-tag, per-project, and per-attribute coefficients.

**What the model deliberately does not have.** No estimate. No time tracking:
the documentation says explicitly that start and stop times are not time
tracking. No assignment or owner. No per-day capacity, availability, or
scheduling. No subtask hierarchy; `parent` means a recurrence template. No
structured annotation, no edge type, no history on the task.

**Why this matters for workthread.** Taskwarrior ranks with a computed urgency
over a graph of dates and dependencies. workthread needs an estimate, logged
work, remaining effort, a timetable, and a session, so it needs a model that
carries numbers and time entries, not only dates and tags. The ideas worth
carrying over are the flat extensible attribute set, one key per member for
set-valued fields, an operation log for history, a virtual derived status
rather than a stored one, and the discipline of separating stored facts from
computed ones (urgency is recomputed; it is never an input).

**Open questions carried forward.** Whether `until` deletes a non-recurring
task. When exactly that deletion happens. Whether the `urgency` column is
modifiable in practice. What `rtype` does. Whether `template` and `last` are
older template markers or still live.

**What the comparison task decides.** Which of these ideas workthread adopts:
tags, annotations, user-defined attributes, a computed urgency beside the 1 to
5 priority, the wait, scheduled, or until dates, recurrence, the flat
extensible record shape, and the operation-log history model.
