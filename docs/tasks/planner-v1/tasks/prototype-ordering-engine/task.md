---
type: task
subtype: prototype
title: Does the timetable and session model still order work sensibly
status: stable
workflow_state: ready
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
