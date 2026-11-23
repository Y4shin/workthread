---
type: task
subtype: grilling
title: Settle estimate semantics, pause behavior, and remaining effort
status: deprecated
workflow_state: done
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

## Notes

### The settled rules

**The estimate is total effort, in one stored field.** Remaining effort is derived as the total minus logged minutes, which means logging work reduces what is left, an overrun leaves the estimate untouched, and the plan follows the time actually spent. Remaining effort is not clamped: it reaches zero and goes negative, and the negative is shown, because running long is information.

**Two doors into that one field.** The total can be set directly, and the remaining can be set, which writes the logged time plus the new remaining as the total. Nothing else is stored. "I have about three hours left" is a real edit and should not require arithmetic.

**A ready task keeps a positive estimate.** Clearing the estimate of a ready task is refused, with an error saying the task must move to the inbox first or keep a positive estimate. Ready meaning a positive estimate is an invariant throughout the specification, and an edit to one field must not change a task's status behind the caller's back. Moving tasks to the inbox is its own operation.

**Zero or negative remaining effort in the plan.** A task never claims negative effort. An unfinished task at or below zero receives a five-minute completion slot as a planning placeholder, but only while it is not running: while a timer runs, the running block is the claim, so an overrun keeps growing and pushes the rest of the day's plan later until it is stopped. Stopping or completing it fixes the block and brings the slot back. The graphic the human gave: the day is a stack of blocks whose height is time, ordinary blocks keep their size until the work is finished and the size is locked, and an overrun keeps growing until it stops.

**Overrun reporting.** Any task at or below zero remaining effort raises a persistent `estimate_overrun` notice at level info, whose point is to ask the human to revise the total. The application never rewrites the estimate towards logged time, and completion is never automatic. Editing or deleting a time entry recalculates remaining effort and can clear the overrun.

**Pause and switching.** Pausing stops the timer and keeps the task in progress. Starting another task returns the previous one to ready unless it was completed or cancelled. Pausing is a task act, not the end of the day.

**Concurrent work, behind a configuration flag.** With the flag off, exactly the single-timer treatment above holds. With it on, a second task may start while another runs, without stopping it, but only when the caller asks for that explicitly: an ordinary start while something runs fails, because a second task is not always meant. The pause heuristic survives the flag, so a paused task returns to ready when something else starts after it. The flag defaults to off. Consequences: §3.5's "at most one task MAY be in progress" and §3.12's "at most one entry MAY be open" become conditional; the tray and the active indicator show a list rather than one task; a manual entry must not overlap any open entry; and ending the day stops every open timer at its stop time.

**A task timer needs a started day.** Starting work on a task before the day has been started is refused, not inferred: the API and the agent interface fail with an error saying a session must be started first, and the client offers to start the session in place as part of that interaction. The alternative, inferring the session from the first timer, was rejected in `session-and-timetable` because settle-in time would stop counting.

**On a finished task.** The estimate is retained on completion and cancellation and displayed with the logged minutes, so the guess and the outcome stay side by side.

### Alternatives rejected

- Storing remaining effort, or storing both a total and a remaining: the first counts work twice, the second doubles the fields and needs a consistency rule.
- Offering only a total edit, or only a remaining edit: one forces arithmetic, the other contradicts the stored meaning.
- Refusing to let remaining effort go negative: it hides the signal that a task is running long.
- Auto-raising the estimate when logged time passes it: the silent rewrite the overrun rule forbids, and it destroys the estimate as a record.
- Completing a task automatically at zero remaining: the application marking work done for a person.
- Planning the five-minute slot while the task is running: the same task would appear both as running and as planned.
- Returning a paused task to ready immediately, or making pause and start timer-only: the first invents a "last active" state, the second lets several tasks look in progress.
- Making concurrency the default, or allowing an unmarked second start: accidental parallel work is hard to notice and hard to undo.

### Consequences for dependent tasks

- `session-and-timetable`: ending the day stops every open timer at its stop time, and start-day is the precondition for starting work.
- `architecture`: `ForTrackingTime` carries the concurrency setting and the day-started precondition, and the active task is a set rather than a single value.
- `decisions-forecast-warnings`: `estimate_overrun` is persistent, and no forecast may treat a task that is running and over its estimate as out of work.
- `decisions-agent-interface`: a concurrent start carries explicit intent, and a start without a session fails with a named error.
- `child-efforts`: the tray and the active indicator show a list when concurrency is on, and the estimate surface offers both doors.

### New work and remaining fog

No new planning task. One default is recorded rather than asked: the concurrency flag ships off, so the conservative behaviour is what a new installation has. No fog remains.
