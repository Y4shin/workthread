---
type: task
subtype: grilling
title: Settle deadline instants, forecast risk precedence, and warning severity
status: deprecated
workflow_state: done
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

## Notes

### The settled rules

Three of this task's seven questions were already answered by earlier passes and are not reopened here. DEC-2, the instant a date-only deadline means, was settled by `session-and-timetable`: a deadline is a sealed date or instant, a date means before that local day ends, and an instant is accepted only for the current local day. DEC-5, deadlines past the planning horizon, was settled in substance there too: allocation covers fourteen days and projections continue past it by repeating the weekday timetable, marked as approximations and rough beyond four weeks. DEC-8's severity question was replaced by the notice model, with a level of info or warning and OS delivery as a flag instead of critical, warning, and informational.

**DEC-6, missed against unscheduled. Missed takes precedence.** A deliverable is missed when its slack is negative or its effective deadline has passed with work remaining, whether or not a finish could be placed. Unscheduled covers only remaining work that cannot be placed for a reason other than deadline capacity. The human's framing: a task that cannot be done by its deadline is missed even if the planner never scheduled it. The rejected reading would report "the planner could not place this" about work that in truth does not fit, sending the user after capacity problems instead of a decision about scope or the deadline.

**DEC-15, blocked against unscheduled. Blocked is the dependency graph only.** A cancelled, missing, or otherwise ineligible prerequisite makes the path blocked. The task's own state, being in the inbox, lacking an estimate, or living in a paused, completed, or archived project, makes it unscheduled. Rejected: blocked for any ineligibility, which overloads the word and sends the user looking for a dependency that is not there, and a sixth risk value, which every surface and every agent consumer would have to learn for a case already covered. One consequence to accept: since projections now run past the horizon, unscheduled is rare and means either that no capacity exists at all or that the task cannot be scheduled in its own right.

**DEC-7, reasons. Six values.** `deadline_protection`, `dependency_protection`, `task_priority`, and `project_priority` as the specification names them, plus `earlier_deadline` for an order decided by an earlier effective deadline without pressure, and `stable_order` for an order decided only by creation time or identifier. The human explanation still carries the detail. Rejected: four values with `deadline_protection` covering earlier deadlines, which states a pressure that did not exist in the one field designed to be trusted, and a nullable reason, which weakens the promise that every item carries one.

**DEC-16, an approaching deadline. A tenth notice type.** `deadline_approaching` is raised while an effective deadline falls within a lead window and the closure has unfinished work, for tasks and projects alike. The lead window is configurable and defaults to twenty-four hours. Rejected: no such type, which leaves the calendar unmentioned when a deadline is tomorrow with comfortable slack, and an implementation-defined trigger, which cannot be configured or reasoned about.

**DEC-8, levels and delivery.** A table with defaults, every entry overridable in configuration:

| Type | Level | OS delivery |
| --- | --- | --- |
| `deadline_approaching` | warning | on |
| `deadline_tight` | warning | off |
| `deadline_missed` | warning | on |
| `blocked_deadline_path` | warning | on |
| `no_timetable` | warning | off |
| `month_will_not_settle` | warning | off |
| `missing_session_end` | warning | off |
| `estimate_overrun` | info | off |
| `horizon_over_capacity` | info | off |
| `dependency_cycle_prevented` | info | off |

Levels mean: a warning asks something of the user, info is state worth knowing. `estimate_overrun` at info is the clearest case, because an overrun is routine and a desktop notification for each one trains the user to ignore them. Rejected: everything at warning, which empties the word, and OS delivery for every warning, which turns a transient no-timetable condition into repeated desktop interruptions.

**Configurability as the rule.** The human cannot judge these defaults without using the product, so each notice type's level and OS delivery are settings with defaults rather than constants, and the approach lead window is a setting too. Persistence is not configurable: whether a notice describes a live condition or a one-off is a property of its source, not a preference.

**Dismissal of a persistent notice.** A persistent notice may be dismissed, which hides it until its condition clears, and the dismissal is not remembered, so a recurrence shows again. That gives a condition the ephemeral presentation the human described without pretending the condition went away. Quiet hours and one-delivery-per-condition from the specification's notification rules apply to the OS delivery.

### Consequences for dependent tasks

- `session-and-timetable`: the count of notice types rises from nine to ten, and a dismissed persistent notice is hidden until its condition clears.
- `architecture`: the four notice settings become configuration state; `ForPlanningQueries` returns notices with their dismissal state.
- `decisions-estimates-time`: `estimate_overrun` is a persistent notice at info by default rather than a warning.
- `decisions-agent-interface`: itinerary reasons are a six-value set, and agents see notices but do not dismiss them without an explicit request.
- `decisions-data-lifecycle`: derived notices are recomputed projections, one-off notices are stored records, and a dismissal is stored state with a lifetime tied to its condition.
- `child-efforts`: slack and therefore risk follow the day capacities, which include the monthly reconciliation and exclude vacation and zero-length flex days, so a month in arrears raises the projected capacity before a deadline and a month ahead lowers it. A forecast whose span crosses four weeks is labelled rough.

### Alternatives rejected

Recorded per question above. The one worth repeating: computing risk against capacity bounded by the fourteen allocated days, which would report a comfortable six-week deadline as missed, and the opposite, computing it against unbounded capacity without saying that the projection is an approximation.

### New work and remaining fog

No new planning task. Nothing remains foggy.
