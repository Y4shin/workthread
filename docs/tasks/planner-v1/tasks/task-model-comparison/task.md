---
type: task
subtype: grilling
title: Compare the planned task model with Taskwarrior's and decide what to adopt
status: stable
workflow_state: done
blocked_by:
- taskwarrior-model-research
---

## Decision to settle

Field by field, what does workthread's task model adopt from Taskwarrior's,
adapt, or refuse?

Compare the specification's task record (section 3.2) with the researched
Taskwarrior model. For each Taskwarrior field or concept, decide one of: keep
the specification as written, adopt Taskwarrior's semantics, add the concept,
defer it post-v1, or refuse it and say why.

The concepts that need an explicit answer:

- tags, and whether they replace or complement projects;
- annotations, and whether they replace or complement a description;
- user-defined attributes, and whether v1 has an extension point at all;
- urgency versus the specification's 1 to 5 priority, and whether a computed
  urgency replaces, feeds, or sits beside priority;
- the wait, scheduled, and until dates, and what they would mean for a
  timetable-based planner;
- recurrence, which the specification rules out as arbitrary recurrence in v1;
- the identifier choice;
- the lifecycle statuses;
- the absence of an estimate field, which workthread requires for scheduling.

## Parent decisions

The non-negotiables task fixed the effort boundary. `spec-additions` recorded
the human's requirement that the model take inspiration from Taskwarrior.
`taskwarrior-model-research` supplies the evidence.

`decisions-estimates-time` settles what an estimate means. This task does not
reopen that; it may only record that the estimate stays, and why a model
without one does not fit.

## Known choices

- Adopt the concepts wholesale and adapt the specification to match.
- Adopt a subset that serves the timetable and session model, and refuse the
  rest with a recorded reason.
- Borrow only the structural ideas, such as a small typed field set and a
  user-defined attribute mechanism, and keep the specification's field names.

## Recommended starting answer

Adopt the subset, not the model. Take tags if they earn their place against
projects, take the free-form annotation idea if it is cheaper than a
description, and refuse user-defined attributes, urgency, and recurrence in
v1 as scope the timetable-and-session product does not need. Keep the
specification's priorities, statuses, dependencies, and the estimate. Record
each refusal with its reason so a later version can revisit it without
re-debating.

## Downstream work

The decided field set is the core's task model. It feeds `architecture`, the
formality ticket that records vocabulary in `CONTEXT.md`, and `child-efforts`.

## Notes

### The comparison, round 1

Both models were presented field by field first: the specification's task record at §3.2 to §3.7 with the identifier rules at §3.14 and RULE-ID-1, against the researched Taskwarrior record. Sixteen concerns were compared. Two of them adopt a Taskwarrior idea rather than refusing it: history leaves the task record and the store stays permissive. Seven were open; three are now settled.

**Row 1, the identifier. Settled: a monotonic integer.** Allocated only by the data owner, declared `AUTOINCREMENT` so a value is never reused and never renumbered, which is the whole guarantee. Opaque by rule: no surface, agent, or plugin may interpret an id, compare ids, or assume adjacency, and that rule is what satisfies RULE-ID-1's word "opaque", not the value itself.

- Display convention, presentation only: `TSK-0001` for tasks and `PRJ-0001` for projects, with the prefix marking the record kind so a wrong-kind reference fails loudly instead of silently addressing another record. Padding is a minimum width, never significant, and the parser accepts the prefix or the bare number, case-insensitively.
- The export and the change log carry the bare integers. A restore by file copy keeps `sqlite_sequence` with them, and a future import must set the sequence to the highest id it carries before accepting a write.
- A test that deletes a record and asserts the next id does not reuse it belongs in the first ticket that touches the store.
- Rejected: a UUID as the only identity with unique-prefix addressing, an integer plus a UUID side column as two identities every interface must choose between, and Taskwarrior's integer, which is reassigned as its working set changes.
- Costs accepted: two data sets cannot be merged later without renumbering, and the id reveals how many records exist. A UUID side column can be added later for merging if it is ever wanted.

**Row 2, the title. Settled: keep §3.2 as written.** Required, 1 to 500 characters. It now carries the identity of the task, since the description is gone.

**Row 3, the description. Settled: replaced by notes.** The human's reason: an agent cannot overwrite a description that does not exist, so losing information becomes harder than keeping it. The append-only journal idea, applied to text.

- The name is **note**, not Taskwarrior's annotation, because it is the word a person uses and the one the MCP tool will carry.
- A note is multi-line with a length cap, and carries its own creation timestamp. Display order is timestamp then id.
- A note may be removed, and the removal is a change in the operation log, so the text survives in history. A correction is a new note.
- This is a recorded deviation from the frozen specification: §3.2's optional description is gone, the map and the architecture document carry the deviation, and the export carries a list where it carried a string.

**Row 14, date gating. Settled: refused in v1, and named as a first change after v1.** The shape it would take is one date, "not before", not Taskwarrior's trio of `wait`, `scheduled`, and `until`. Its arrival touches three rule sets at once: eligibility, because a task cannot be scheduled before the date; warnings, because a deadline can become unsafe on a task that may not start; and the fit signal. So it needs its own grilling rather than a field added quietly, and it is recorded as the smaller of the two post-v1 changes, so it is the first unless an order is decided otherwise.

**Row 15, recurrence. Settled: refused in v1, and named as a change after v1.** The reference shape is Taskwarrior's: a template record plus instances, with a per-instance status ledger on the template. The specification rules out arbitrary recurring tasks for v1 in section 2.2, and this map's non-goals keep it out, so nothing changes about v1 scope. What changes is the record: recurrence is not "some day", it is the second named change after v1, which keeps a later debate anchored to a decided order instead of reopening the question from nothing.

**Why these two and not others.** They are the two refusals this comparison makes that the human wants to see return soon, as opposed to tags, user-defined attributes, urgency, and annotations, which are refused without a scheduled return. Recorded here so the distinction is not lost: refused for v1, queued right after it.

**Rows 4, 13, 5, and 11. Settled as recommended.**

- Tags, row 4: none in v1. No planning rule would read one, and each surface would carry a field, a filter syntax, an editor, an MCP schema, and an export column for something the client cannot compute with. Revisit trigger: the user wants a cross-project cut such as "everything waiting on someone else". Projects stay as §3.3 writes them.
- User-defined attributes, row 13: none, and no extension point in the record. A free-form typed field would be a second extension path with no validation that the planning rules can read, where the post-v1 plugin story gives an extension with a port, a configuration schema, and validation.
- Urgency, row 5: none. The 1 to 5 priority stays the only importance signal, in the position the ordering prototype settled, after deadline and dependency pressure. Urgency would be a second ranking signal with tunable weights competing with those rules and making every per-item explanation conditional.
- Statuses, row 11: the five stored statuses plus derived `blocked` stay. The `inbox` against `ready` split is the one place this model is richer than Taskwarrior's, and it is where the estimate becomes required; collapsing it into a derived property would make every surface recompute schedulability and be able to disagree.

**Rows adopted from Taskwarrior rather than refused.** History leaves the task record and lives in the operation log (row 12), which the architecture task had already decided. Requirements are a product rule rather than a storage constraint (row 16), with one deliberate exception: the store enforces identity, status, and the timestamps, because a record it cannot identify or place in time is not worth storing.

**The reconciled record.** An identifier (monotonic integer, opaque by rule, displayed as `TSK-0001` or `PRJ-0001`); a required title of 1 to 500 characters; zero or more notes; an optional project reference; a priority of 1 to 5 defaulting to 3; an estimate in positive whole minutes, required before a task is ready or in progress; an optional deadline whose effective value is the earlier of task and project; source metadata (type defaulting to manual, an optional reference, an optional URL); a stored status of inbox, ready, in progress, completed, or cancelled, with blocked derived and never stored; a completion timestamp; creation and update timestamps; dependency edges and time entries.

**The one deviation from the frozen specification.** §3.2's optional description is replaced by notes. The identifier's concreteness is not a deviation: §3.14 allows any stable opaque identifier. Sealed in the record so the child efforts do not have to rediscover it.
