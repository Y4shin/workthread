---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the work model
status: stable
workflow_state: ready
blocked_by: []
---

Settle this effort's non-goals and non-negotiable facts, including its
effort-level success test. The scope, the requirement list, and the position are
recorded below and come from the parent effort.

### The raw request, verbatim

The human asked for all twelve child efforts in one pass of intake. This child's
scope, quoted from the parent map's `## Child efforts` section:

> Projects, dependencies with derived blocking and cycle refusal including the
> path, estimates as total effort with derived remaining, semantic priority,
> deadlines as the sealed date-or-instant pair, and cancel and archive.

### Destination

A task set carrying every scheduling input the planner needs: projects, tasks,
dependencies with derived blocking, estimates with derived remaining, priority,
deadlines, and the cancel and archive path. This is the last effort before
anything is ordered.

### Actor

The person modelling their work, and the planner that will read these fields.

### Classification

Feature.

### Stories

1. As a person with more than one stream of work, I want projects, so that tasks
   can be grouped and paused together.
2. As a person with work that depends on other work, I want to record the
   dependency once, so that blocking derives rather than my memory.
3. As a person who mistyped a dependency, I want a cycle refused with the tasks
   in the loop named, so that I can fix it in one move.
4. As a person estimating, I want a total effort and a derived remaining, so
   that I estimate once and the record keeps both expected and actual.
5. As a person setting a deadline, I want a date and an instant to mean
   different, exact things, so that "by Friday" and "by 15:00" are not confused.
6. As a person with work I will not do, I want to cancel a task or archive a
   project, so that it leaves the plan without leaving the record.

### Constraints

- The record shape and the fields are settled: the work model adds fields
  through a store migration, never by a surface writing its own table.
- Estimates are total effort in positive whole minutes, required before a task
  is ready or in progress; remaining is derived and may go negative.
- The deadline is a sealed pair: a date, or an instant accepted only for the
  current local day, with the effective deadline being the earlier of task and
  project.
- Dependencies refuse self-edges, duplicates, and cycles; blocking is derived
  and cannot be set.
- Nothing is deleted on request; cancel and archive record a timestamp.
- This effort implements no ordering, no capacity, no forecast, and no
  projection.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.2..SRC-2.1.4, SRC-2.1.6, SRC-2.1.7, SRC-2.1.18, SRC-4.3..SRC-4.7,
    SRC-RULE-PRIO-1, SRC-RULE-STATE-3..SRC-RULE-STATE-8,
    SRC-RULE-EFF-1..SRC-RULE-EFF-3, SRC-8.8.3, SRC-8.8.4, SRC-8.8.6, T11-023,
    T12-026, T12-030, T12-034..T12-037, T13-032..T13-036, T14-001..T14-005,
    T14-009, T14-022, T001, T17-005, T17-014, T25-004, MAP-042, MAP-043,
    MAP-051, MAP-055, MAP-056, CTX-estimate, CTX-remaining-effort

That is 53 rows.

### Position and release, already settled

Fourth in the build order, blocked by `03-cli-basics`, release 0.2 The work
model. Its successor is `05-cli-planning`. The full order is the table under
`## Effort order` in the parent's architecture document.
