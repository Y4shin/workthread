---
type: task
subtype: grilling
title: Add the human's extra requirements on top of the specification
status: stable
workflow_state: done
blocked_by:
- non-negotiables
---

## Decision to settle

The human wants to add requirements on top of the specification before any
research or decision grilling starts. Settle which additions go in, and where
each one lands.

This task is open-ended on purpose. The human brings the list; the session
records it.

## Parent decisions

None. This task is blocked only by the program's non-negotiables, which are
settled.

## Known choices

- The moved specification at `docs/tasks/planner-v1/initial_spec.md` is the
  frozen reference text. Additions do not edit it. They are recorded as
  decisions on the map, and as input to the architecture document.
- An addition can extend v1 scope, replace a v1 behavior, add a v2 advisory
  item, or be ruled out entirely. Each addition needs one of those
  destinations.
- An addition can also contradict a non-negotiable fact already on the map
  (product name, module shapes, the itinerary-first principle, the v1
  boundary). A contradiction reopens that fact rather than sitting next to it.
- The specification's 16 open decisions are not settled here. This task only
  adds requirements to the mix.

## Recommended starting answer

Go through the human's additions one at a time. For each, record the request
in the human's words, the destination (v1 scope, replacement of existing v1
behavior, v2 advisory, or ruled out), and the specification sections it
touches. Then fold the result into the map: new bullets under
`## Decisions so far`, corrections to `## Non-negotiable facts` where the
addition contradicts one, and new `## Child efforts` candidates.

Where an addition makes one of the five themed decision tasks larger, name it
so that task's body can be corrected instead of a second task being created.

## Downstream work

Every other planning task is blocked by this one. Additions here can widen the
stack research, add questions to a themed grilling, or add a child effort to
`child-efforts`.

## Notes

### Addition 1: task model inspired by Taskwarrior

**The human's words.** "I want the data model for the tasks to take inspiration
from the model of the app taskwarrior, so I want a research task to be created
for that at the end of the grilling and a followup grilling comparing our
currently planned one with the one from taskwarrior."

**Destination.** v1 scope and model shape, as input to the architecture
document. The frozen specification is not edited. The specification's task
model in section 3.2 stays as written.

**What this creates, at the end of this grilling.** Two tasks:

- `taskwarrior-model-research` (research): Taskwarrior's own task model and
  its semantics, from primary sources.
- `task-model-comparison` (grilling): compares the specification's task model
  with the Taskwarrior model, field by field, and decides what to adopt,
  adapt, or refuse. The comparison is where the decision lands, not here.

**Wiring.** The research is blocked by `spec-additions`. The comparison is
blocked by the research. A note goes on `child-efforts` because its existing
`blocked_by` list cannot gain the comparison after creation: the comparison
must land before the child-effort list is produced.

**Known tension to hand the comparison.** Taskwarrior has no estimate field,
no project hierarchy of the specification's shape, and it leans on tags,
annotations, urgency, user-defined attributes, and recurrence. The
specification's v1 rules out arbitrary recurring tasks and makes the estimate a
required planning input. The comparison must say which Taskwarrior ideas fit
v1 and which stay out, and whether the estimate survives in its current form.

**Not settled here.** Whether "inspiration" means borrowing field names and
semantics or only structural ideas. That is the comparison's decision.

### Addition 2: drop the day-by-day schedule, use a weekly timetable and a daily session

**The human's words.** "I want to remove unnecessary complexity for a v1 (or at
least what I think of as unnecessary): I want to drop the whole 'I have
scheduled this amount of work on this day and this amount on that day' and so
on. I want to simplify it to: I start working, I tell it when I plan to leave,
and how much breaktime I plan to have, and that is it. More complex planning
can come in post v1."

**Follow-up answers from the human.**

- Deadline pressure survives, in its simplest form: a weekly **timetable** of
  how many hours are worked on each weekday, plus the option to record
  irregular time off, both in advance and retroactively.
- The interface shows an ordered list for today, not a day-by-day schedule:
  what to work on now, what follows, and how much still fits before the
  planned leave time.
- The projection horizon is short: "not far, maybe two weeks absolute tops".
- Today's capacity is the session: now until the planned leave time, minus the
  break minutes. The timetable only sets the assumption for future days.

**What leaves v1 (deferred, not deleted).** Multi-day allocations, per-day
allocated minutes, clock-time blocks, splitting one task across days, the
capacity-constrained itinerary across a horizon, interval-based weekly
availability with pause budgets per interval, and date exceptions of the
interchangeable-intervals kind.

**What replaces it.** Two concepts, both new to the project's vocabulary:

- **Timetable:** hours per weekday, in the simplest form that carries a
  forecast.
- **Session:** the current working day. It starts when work starts, and it
  carries a planned leave time and planned break minutes. It decides today's
  capacity.

**Consequences that must be carried into the plan.**

- The map's non-negotiable fact "the itinerary is the primary output,
  forecasts and warnings support it (P11)" is replaced. The new fact: the
  ordered today list and the session are the primary output, and deadline
  forecasts support them over a horizon of two weeks at most.
- The specification's frozen text still describes the itinerary, the horizon
  of 7 to 365 days with a default of 90, interval-based availability, pause
  budgets, and allocations. It is not edited. The deviations are recorded here
  and belong in the architecture document.
- Specification sections whose v1 content changes: 1.1, 1.3 (P1, P3, P11),
  2.1, 3.1 (Capacity, Allocation, Itinerary, Pause budget), 3.8, 3.9, 4.1,
  4.8, 4.9, 4.17, 5.6, 5.10.
- Decision tasks whose questions change: `decisions-forecast-warnings` (the
  horizon is two weeks at most, which trivialises DEC-5 and changes DEC-14),
  `decisions-data-lifecycle` (DEC-11 becomes "how is a time-off entry removed",
  and DEC-12 about intervals crossing midnight becomes moot because the
  timetable stores hours per weekday), and `child-efforts` (the scheduling
  projection work shrinks, the session and timetable work appears).
- A new grilling is needed for the mechanics: what the timetable stores and at
  what granularity, whether time off is whole-day or partial, how the fit
  signal for today is computed, which of the specification's warning types
  survive (`plan_over_capacity`, `no_working_hours`), and whether Review
  compares logged work against the timetable for past days.
- The project's vocabulary needs the two new terms, `timetable` and
  `session`, in `CONTEXT.md`. That belongs to the formality ticket's
  deliverable, whose subject is now wider than the product name.

**Not settled here.** Exact timetable granularity, partial-day time off, the
surviving warning types, and the Review comparison. They go to the new
grilling.

### Addition 3: pre-v1 build order, then a flexible architecture, then a release plan

**The human's words.** "I want the general architecture of this app's to be a
grilling decision in a followup grilling that should come soon after this one.
But before that grilling, I want to have a grilling on a plan for which
versions are built pre v1 with which features in which order. So basically what
I can already say, that I want to start off with just creating a cli with the
basics, then expand the functionality of the logic and backend to be at the
'final stage' of what we want to achive for v1, then I want to add an MCP, then
I want to be able to create a daemon, which can be talked to by the CLI (and
the MCP is hosted via stdio on the CLI by either hosting the backend itself in
process or connecting to a daemon) (generally all user, or external-app facing
interfaces should work with the backend running in process, or via
communicating with the daemon) and after that we finally go to the main UI and
then we also add the tray icon/menu. And as you can see, for this to be
possible, we need to decide on a flexible architecture that can support all
this early on and after that we need to decide on a release plan of what
releases we want to create pre-v1 and what features are part of them (and
therefore which efforts belong where)."

**The starting build order the human stated.**

1. A command line interface with the basics.
2. The logic and backend grown to the final v1 stage.
3. The MCP interface.
4. A daemon the CLI can talk to. MCP is hosted over stdio on the CLI, either by
   running the backend in the same process or by talking to the daemon.
5. The desktop user interface.
6. The tray icon and menu.

**The rule that outlives the order.** Every user-facing or external
application-facing interface works either with the backend in the same process
or by talking to the daemon. That generalises the specification's
single-authority principle from desktop and tray to every surface, including
the CLI.

**Settled by follow-up answers.**

- The general architecture grilling runs first, and the two decision tasks
  already charted for the service model and the agent surface run afterwards
  to settle their own narrow details.
- Two plan tasks, not one. A version-order grilling runs before the
  architecture. A release-plan grilling runs after it, naming the concrete
  pre-v1 releases, their feature sets, and which child effort lands in which
  release.
- The command line interface is a first-class v1 surface, shipped beside the
  desktop client and the tray, and it hosts MCP over stdio.

**Consequences that must be carried into the plan.**

- The map's non-negotiable fact listing three separable shapes (core, Spring
  Boot API module, JavaFX client module) is incomplete. The shapes become the
  core work logic, the CLI, the MCP interface, the Spring Boot API module, the
  JavaFX client, and the tray, with the in-process-or-daemon rule applying to
  every front end.
- The task graph gains four tasks: `version-order` and `architecture` (both
  early), `release-plan` (after the architecture and after the child-effort
  list), and the mechanics grilling for the timetable and session from
  addition 2.
- `child-efforts` gains a second reason it must not run early: the
  architecture and the version order decide what an effort can contain. Its
  `blocked_by` list cannot gain those tasks after creation, so the ordering is
  recorded in its notes.
- `decisions-service-mode` and `decisions-agent-interface` must run after the
  architecture grilling. Their `blocked_by` lists cannot gain it after
  creation, so the ordering is recorded in their notes too.
- A production consideration the architecture grilling inherits: a CLI that
  hosts MCP over stdio cannot also own the terminal's standard streams for
  interactive use. The architecture must say how the CLI separates an
  interactive session from an MCP stdio session.

### Closing note: what this grilling created

The human ended the grilling. Six tasks were created, in dependency order, and
three existing tasks carry ordering notes because their `blocked_by` lists
could not gain the new blockers after creation.

Created:

- `taskwarrior-model-research` (research), blocked by this task.
- `task-model-comparison` (grilling), blocked by the research.
- `session-and-timetable` (grilling), blocked by this task.
- `version-order` (grilling), blocked by this task.
- `architecture` (grilling), blocked by this task, `version-order`, and
  `stack-research`.
- `release-plan` (grilling), blocked by `architecture` and `child-efforts`.

Ordering notes written into `child-efforts`, `decisions-service-mode`, and
`decisions-agent-interface`.

The map was rewritten: the destination now includes the build order and the
releases, the non-negotiable facts replace the itinerary-first fact and the
three-shape fact, and the constraints record the new order and the edge
limitation.
