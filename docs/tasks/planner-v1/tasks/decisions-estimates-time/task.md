---
type: task
subtype: grilling
title: Settle estimate semantics, pause behavior, and remaining effort
status: stable
workflow_state: ready
blocked_by:
- spec-additions
---

## Decision to settle

Settle what "estimate" means and how logged work interacts with it.

- **DEC-1.** Is a task's estimate total effort or remaining effort? The
  specification computes remaining effort as `max(estimate - logged work, 0)`
  and also calls the estimate "the user's current best guess of remaining
  effort". Both cannot hold after work is logged.
- **DEC-13.** Does pausing a timer change task status? The specification keeps
  the task current and in progress in one place and returns the previous task
  to ready in another.
- The derived consequences that follow from those two answers: the five-minute
  completion slot for an unfinished task with zero remaining effort, overrun
  handling (report, never absorb), the effect of editing or deleting a
  historical time entry, and what the estimate field shows when the task is
  completed or cancelled.

## Parent decisions

The non-negotiables task settled the effort boundary. Nothing else gates this.

## Known choices

- **DEC-1 alternatives.** A: estimate is total effort, remaining is
  estimate minus logged work. B: estimate is remaining effort, so logging does
  not subtract. C: two fields, a total estimate and a separate remaining
  estimate.
- **DEC-13 alternatives.** Pausing keeps the task in progress with no open
  entry. Pausing returns the task to ready. Starting another task always ends
  the previous timed span and returns the previous task to ready unless it was
  completed or cancelled.

## Recommended starting answer

DEC-1: choose A, total effort. It is the only reading consistent with the
remaining-effort formula, the overrun rule, and the requirement that logged
work changes projections. Show the derived remaining value in the interface so
a user who thinks in remaining effort is not misled.

DEC-13: pausing stops the timer and keeps the task in progress, as recorded.
Starting another task ends the previous span and returns the previous task to
ready. That keeps "at most one task in progress" true and keeps the state
readable from the timer alone.

## Downstream work

The answer fixes: the remaining-effort rule, overrun warnings, the five-minute
completion slot, forecast slack, the data model's estimate field, and the MCP
surface's estimate operations. It feeds `decisions-forecast-warnings`,
`decisions-data-lifecycle`, and `child-efforts`.
