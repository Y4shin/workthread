---
type: task
subtype: grilling
title: Settle deadline instants, forecast risk precedence, and warning severity
status: stable
workflow_state: ready
blocked_by:
- spec-additions
---

## Decision to settle

Settle how deadlines, risk, and warnings behave, so the itinerary's supporting
view has one set of rules.

- **DEC-2.** What instant does a date-only deadline represent: end of the local
  day, start of it, a working-hour boundary, or a required time?
- **DEC-5.** How do forecasts treat a deadline beyond the planning horizon?
  Horizon-bounded capacity, capacity extended to the deadline, or an explicit
  beyond-horizon state?
- **DEC-6.** When is a deliverable `unscheduled` rather than `missed`?
- **DEC-7.** How are reasons labeled when the decisive criterion is an earlier
  effective deadline that is not under pressure, or only a stable tie-breaker?
- **DEC-8.** Which warning types are critical, warning, or informational, and
  which are notification-eligible?
- **DEC-15.** Which ineligible conditions produce `blocked` risk instead of
  `unscheduled` risk?
- **DEC-16.** Which condition defines an approaching deadline for a
  notification?

## Parent decisions

The non-negotiables task settled the effort boundary and the itinerary-first
principle. `decisions-estimates-time` settles what remaining effort means,
which this task uses but does not settle again.

## Known choices

Each DEC in specification section 9 lists its own alternatives, user-visible
consequence, current treatment, and a suggested resolution. The forecast risk
values are `safe`, `tight`, `missed`, `blocked`, and `unscheduled`; the
warning types are `deadline_tight`, `deadline_missed`, `estimate_overrun`,
`blocked_deadline_path`, `plan_over_capacity`, `no_working_hours`, and
`dependency_cycle_prevented`.

## Recommended starting answer

- DEC-2: end of the local day, with an optional time, and the interpretation
  stated in the interface.
- DEC-5: use alternative C, an explicit beyond-horizon state. It is honest and
  it cannot report a false safe or a false missed.
- DEC-6: `missed` when a deadline exists and slack is negative. `unscheduled`
  otherwise when there is remaining work and no projected finish.
- DEC-7: label an earlier-deadline selection `deadline_protection` even without
  pressure, and label pure tie-break selections `task_priority` or
  `project_priority`. The explanation carries the tie-break detail.
- DEC-8: assign a severity table over the seven warning types once, and derive
  notification eligibility from severity plus the user's preferences.
- DEC-15: dependency-path problems are `blocked`; ineligibility that is not a
  dependency problem is `unscheduled`.
- DEC-16: notify when a forecast flips to `tight` or `missed`, and when the
  effective deadline falls within the next 24 hours of available capacity.

## Downstream work

Fixes the risk and warning rules used by the scheduler, the forecast view, the
notification layer, and the agent surface. Feeds `decisions-agent-interface`,
`decisions-data-lifecycle` where warnings are stored, and `child-efforts`.
