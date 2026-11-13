---
type: task
subtype: prototype
title: Does the timetable and session model still order work sensibly
status: stable
workflow_state: done
blocked_by: []
---

## The one question

Does the simplified capacity model, a timetable of minutes per weekday plus a
session that runs from now to the planned leave time minus break minutes, still
produce an ordering and a fit signal a user would trust?

## Alternatives worth comparing

- Effective deadline first, with dependency pressure second and priority as a
  tie-break.
- Priority first, with the effective deadline as a tie-break.
- Dependencies first, then the effective deadline, then priority.

And for the fit signal: mark the task that would cross the planned leave time
and stop the list there, or keep listing everything with a marker per item.

## Smallest artifact that answers it

One self-contained Java class with no framework and no storage. It takes a
small set of tasks with estimates, effective deadlines, and dependency edges,
a timetable, and a session, then prints the ordered today list with the point
where the session runs out. Run it against the worked examples in the
specification's section 5.19, adapted to the timetable and session model, and
against two hand-made cases: one with a dependency chain and a near deadline,
and one where nothing fits today.

## Who must react

The human, asking one question: does this look like a plan you would follow?
That reaction is the evidence.

## What it unblocks

`session-and-timetable` (whether the fit signal and the surviving warning
types work), `decisions-forecast-warnings` (whether the risk labels still read
correctly under the shorter horizon), and `child-efforts` (the scheduling
engine's scope).

## Where the artifact lives

Build it under `/tmp/planner-v1-ordering/`, outside the repository. Record the
ordering rule that won, the outputs of the worked examples, and the snippet
that encodes the rule in this task's notes, then delete the directory.

## Notes

### Findings

Full evidence, the cases verbatim, the comparison table, and the rule
snippets: `docs/research/prototype-ordering-engine.md`.

**Question answered.** Yes, with one criteria gap. The timetable and session
model reproduce the specification's capacity arithmetic exactly, and the
ordering survives the model change wherever the work is deadline-driven.

**What the three rules did.**

- Rule A (effective deadline, then dependency pressure, then task priority)
  reproduces the specification's first worked example minute for minute.
- Rule B (priority first) picks the wrong first task in that example, and the
  work then misses an 11:00 project deadline.
- Rule C (dependency depth first) protects a chain only until its first step,
  and misses a noon deadline in the chain case.
- Rule A is the only rule that met every deadline in the chain case.

**The gap all three rules share.** A prerequisite with no deadline of its own
sinks below any deadline-bearing task, so in the adapted second worked example
the successor misses its 11:00 deadline by 30 minutes under every rule. That is
a gap in the selection criteria, not in the capacity model.

**Chosen direction, confirmed by the human.** Rule A with inherited deadlines:
a prerequisite inherits the earliest effective deadline of the tasks it
unblocks. The prototype checked that arithmetic by hand and did not implement
it; the scheduling child implements it. The chain day under rule A is the one
the human accepted: three prerequisites in sequence, the deadline-bearing task
at 11:00, and a 45 minute task due the next noon carried to Thursday.

**Second gap, recorded rather than fixed.** The fit signal is honest about
where the session ends, but it cannot say "does not fit". In the case with a
15 minute session and a 45 minute task it printed a 15 minute item that looks
like finished work. The scheduling engine needs an explicit does-not-fit state,
and `session-and-timetable` must name it.

**Model details the prototype had to choose, and the choices.** Timetable
values are net working minutes per weekday. A future forecast day starts at a
fixed placeholder time. The session is a hard boundary, and a chunk never
crosses the planned leave time. The five minute completion slot applies only
to zero remaining effort. Dependency pressure is static least slack among the
deadline-bearing descendants, and dependency depth is the count of unfinished
transitive prerequisites.
