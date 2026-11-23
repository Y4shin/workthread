---
type: task
subtype: grilling
title: Settle timetable, time off, session, fit signal, warnings, and horizon mechanics
status: deprecated
workflow_state: done
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

## Notes

### The settled model

The specification's availability model is replaced: no clock intervals per weekday, no per-interval pause budget, no `unavailable` against `replace_intervals` exceptions, no 90-day horizon with a 7-to-365 setting, and no capacity override. What stands in its place is a weekly minute budget, a session per working day, per-date entries, and a monthly account.

**The timetable.** A weekday holds its net work minutes and nothing else: one window, no clock times, no pause budget. One configured planned start supplies the clock for future days, and the observed start replaces it the moment the day begins, which is how 10:15 one morning and 10:45 the next each end thirty minutes later. A day's window is its start, plus its minutes, plus the break reserve. Zero minutes is a non-working day. Whole minutes throughout.

**The session.** One record per date: the start, the leave time, the break reserve. The leave is derived as start plus minutes plus reserve unless the user states one, and "stay later today" or "I must leave earlier" are simply statements that move it, at any moment of the day. A day with planned work that has not been started is not a session. The session ends when the user says it ends. A day left unended gets an inferred end: the end of the last task time entry logged that day, or, when nothing was logged, the start plus the day's target plus the reserve, so the day is never zero-length and the arithmetic never breaks. Either way a persistent notice asks for a real end per unfinished day. The session takes the place of §5.17's capacity override, so the overridable rules fall from three to two.

**Time off, two kinds on one record.** A date, a minute count, a kind. `vacation` replaces both the day's capacity and the month's target, and cannot exceed the weekday's minutes. `flex` replaces only the day's capacity and may be more, fewer, or zero minutes: "six hours today", "eight and a half today", "away today, hours owed". The month's total never grows; only vacation reduces it. Entries may be recorded on past dates, and the review shows the conflict when presence or logged task time disagrees with one.

**The month's account.** The target is the month's weekday minutes minus vacation. Worked is presence: the session's window minus its reserve. Deliberately not task time, because the company pays for the time at work and settling in, mail, and a glass of water are not deficits. The balance is worked minus target inside the running month. Four settings in two bands, all defaulting to zero: a permitted shortfall and permitted surplus, each a minute count or unlimited, which clamp what a month may end at; and a target shortfall and target surplus, which the plan reconciles to, catching up to the band's edge and stopping there. A target may not exceed its allowance unless that allowance is unlimited.

**Reconciliation.** The gap between the balance and the target band is settled in thirty-minute increments over the days after today that have work scheduled, in date order, cycling from the first day while the balance remains unsettled, with a final increment of the remainder below thirty minutes, and no day reduced below zero. A day carrying minutes of its own is never modified. That invariant binds every future projection algorithm, not only this one; the algorithm is a configuration point with one implementation in v1.

**Deadline granularity.** A deadline is a date or an instant, a sealed pair rather than one nullable timestamp. A date means before that local day ends. An instant is accepted only for the current local day, so 15:00 on a task due tomorrow is rejected rather than rounded. The rule is input validation, not a stored constraint, so an instant that later passes simply stays past. Project deadlines follow it too, and every effective-deadline comparison reads a date as the end of its day. This refines the deadline that `task-model-comparison` recorded as one optional instant.

**The today list.** It is bounded by the day's capacity, so with three hours left it holds three hours. It is not re-planned as time passes; the plan stays and a cut line moves up it, showing which planned items now fall past the leave. It re-plans on a data change or at the next day, never on the clock. The fit signal is one line plus that cut: planned minutes, available minutes, and what no longer fits.

**Notices.** One concept for everything the system says. A notice has a persistence flag, on while its condition holds and off for a one-off that stays until dismissed; a level of `info` or `warning`; an optional entity reference; a message; and a flag for whether it is also delivered to the operating system. The list and the dismissal live on `ForPlanningQueries`, which makes that port a query with an acknowledgement rather than adding a ninth port. The OS copy is informative only and delivered once; if the user interacts with it there, the interaction is discarded.

**Nine notice types.** `deadline_tight` (warning), `deadline_missed` (warning), `estimate_overrun` (info), `blocked_deadline_path` (warning), `dependency_cycle_prevented` (info), `no_timetable` (warning, renamed from `no_working_hours`), `horizon_over_capacity` (info, renamed from `plan_over_capacity`), `month_will_not_settle` (warning, new), `missing_session_end` (warning, new, one per unfinished day). The specification's `critical` severity retires; errors are handled before they could become notices.

**The review.** The month is the unit. Each past day shows its target minutes, its presence minutes, and the difference, with the month's balance as the headline. A day carrying vacation or flex shows the conflict when logged work disagrees with it, and time off recorded late is visible. Task time is not the compared quantity.

**The horizon.** Concrete allocation covers fourteen days. A projection continues past it by repeating the weekday pattern and following the month's balance, marked as a projection and as rough beyond four weeks. `horizon_over_capacity` carries the meaning "you are using this tool beyond its design": a backlog that fills more than the fourteen days is discouraged rather than refused. Deadlines six weeks out are ordinary; a three-hour task planned two weeks out is the case that should feel wrong.

### Alternatives rejected

- Clock intervals with a pause budget and two kinds of date exception: a day's start is observed, not planned, so the interval model would plan a start the user does not honour.
- Per-weekday start times: one planned start is enough, and the observed start covers the variation that matters.
- A session beside the capacity override: two ways to adjust today, one of them demanding confirmation.
- Whole-day-only time off: one minute count covers a full day, a half day, a longer day, and a day away with hours owed.
- Inferring the reconciliation target from the carry allowance: the human rejected exactly this implicit behaviour, which is why the target band is its own pair of settings.
- The month's worked minutes taken from time entries: it would turn untracked presence into debt the company never charged.
- Zero-length inferred sessions: rejected as distorting the account.
- Splitting a time entry at midnight or at a month boundary: it invents two records from one act of work.
- A ninth surface port for notices: the query port already carries the projection, and dismissal is a small acknowledgement.

### Consequences for dependent tasks

- `architecture`: `ForDefiningCapacity` owns the timetable, the per-date entries, and the four balancing settings; `ForTrackingTime` owns sessions; `ForPlanningQueries` gains the notice list and dismissal; `ForPlanning` receives day capacities and the month's balance as data, not a store.
- `task-model-comparison`: its deadline field is refined to the sealed date-or-instant pair.
- `decisions-estimates-time`: task time entries serve remaining effort and per-task accounting only, never the month's account.
- `decisions-forecast-warnings`: the notice model, the nine types and their levels, the retirement of `critical`, and the confidence labels on projections beyond the horizon.
- `decisions-data-lifecycle`: removal of per-date entries and of notices, and the inferred session end as a provisional value that a correction overwrites.
- `decisions-agent-interface`: agents read notices, dismiss a one-off only on explicit request, and treat a leave time as a scheduling input.
- `child-efforts`: the setup journey has to ask for the weekly minutes, the planned start, the break reserve, and the balancing settings; the review surface compares presence, not task time.

### Newly discovered work and remaining fog

Nothing became a new planning task. Two things are noted for the children rather than decided here: the setup journey's exact questions, and whether a timer left running past midnight deserves a notice of its own. The second would belong to `decisions-data-lifecycle` if it is wanted. No fog remains.
