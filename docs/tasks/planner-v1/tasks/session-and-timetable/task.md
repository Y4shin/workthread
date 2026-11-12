---
type: task
subtype: grilling
title: Settle timetable, time off, session, fit signal, warnings, and horizon mechanics
status: stable
workflow_state: ready
blocked_by:
- spec-additions
---

## Decision to settle

Settle the mechanics of the timetable and the session, the two concepts that
replace the day-by-day schedule.

- **Timetable.** How many hours are worked on each weekday. The granularity
  (whole hours or minutes), how a non-working day is expressed, whether it is
  one recurring weekly template or editable per week, and what an empty
  timetable does to the interface.
- **Time off.** Whether it is whole-day or partial, how far ahead it can be
  booked, what recording it retroactively changes, and how it is removed once
  already a decision task owns removal semantics for other records.
- **Session.** When a session starts, whether the start is explicit or implied
  by the first timer, the planned leave time, the planned break minutes, what
  the interface shows when the leave time passes, what happens at local
  midnight, and whether a finished session can be corrected.
- **Fit signal.** How "what still fits before you leave" is computed from
  remaining effort and the ordering, and what it says when the next task does
  not fit.
- **Warnings.** Which of the specification's warning types survive in the new
  model: `no_working_hours` becomes a missing timetable, and
  `plan_over_capacity` becomes a today-cannot-fit condition. Confirm which
  remain and with which severity.
- **Review.** Whether the Review surface compares logged work against the
  timetable for past days, and what the comparison shows when time off was
  recorded late.
- **Horizon.** Whether the two-week maximum is a fixed value or a setting.

## Parent decisions

`spec-additions` recorded the human's requirement. The non-negotiables task
fixed the effort boundary. The specification's availability and capacity rules
(sections 3.8 and 5.6) are the text being replaced, and they are not edited.

## Known choices

- Timetable as minutes per weekday, with a whole-day-off entry in the time-off
  list.
- Time off as an explicit record per date, whole-day only, or with partial
  hours.
- Fit signal as a computed sum of remaining effort against remaining session
  minutes, or as a per-task "fits" flag only.
- Horizon fixed at fourteen days, or a setting from one to fourteen.

## Recommended starting answer

Timetable: minutes per weekday in one recurring weekly template, where zero
means a non-working day. Time off: whole-day records with an optional partial
hour count, bookable ahead and addable retroactively. Session: starts on an
explicit action or the first timer, carries the planned leave time and break
minutes, keeps counting after the leave time with a visible overrun, and ends
at midnight or when the user ends it. Fit signal: the ordered today list shows
how many minutes remain before the planned leave and marks the task that would
run past it. Horizon: fixed at fourteen days, no setting. Review: compare
logged work against the timetable for past days and show late-recorded time
off.

## Downstream work

Fixes the capacity computation, the session model, the today list's fit
signal, and the surviving warning types. Feeds `architecture`,
`decisions-forecast-warnings`, `decisions-data-lifecycle`, and
`child-efforts`.
