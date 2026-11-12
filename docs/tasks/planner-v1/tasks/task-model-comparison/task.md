---
type: task
subtype: grilling
title: Compare the planned task model with Taskwarrior's and decide what to adopt
status: stable
workflow_state: ready
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
