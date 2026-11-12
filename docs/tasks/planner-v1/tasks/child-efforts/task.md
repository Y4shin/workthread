---
type: task
subtype: grilling
title: Settle the child-effort list, their scopes, order, and entry criteria
status: stable
workflow_state: ready
blocked_by:
- spec-additions
- stack-research
- decisions-estimates-time
- decisions-forecast-warnings
- decisions-service-mode
- decisions-data-lifecycle
- decisions-agent-interface
---

## Decision to settle

Produce the child-effort list that this program hands off. For each child:
a slug, a title, its scope in one paragraph, what it does not cover, the
efforts it depends on, its entry criteria, and what it hands the next effort.

The result is written to the map's `## Child efforts` section. The children
themselves are created at this effort's close, each with
`origin_effort: planner-v1`, after the human accepts the list.

## Parent decisions

Everything this effort settled: the non-negotiables, the human's additions,
the stack research, and the five themed decision tasks. Nothing here reopens a
settled decision.

## Known choices

- Granularity: one effort per architectural layer, one per user journey group,
or one per tracer bullet that cuts through every layer.
- Whether the scaffolding effort is separate from the first vertical slice.
- Where the specification's v1 capability list is split, if at all, across
  efforts.
- Whether an effort is defined by a UI surface, a service capability, or a
  release milestone.

## Recommended starting answer

A first-pass spine of medium-sized efforts, each delivering something a user
can observe, in this order: scaffolding and build; the data model and
persistence; the scheduling and forecast engine; the core service interface
and the split-mode API; the MCP agent interface; the JavaFX client shell with
Today; tasks, projects, and review views; the timer, warnings, and
notifications; the tray; export and packaging. Then quiz the human on
granularity, on the dependency edges, and on any effort that should be merged
or split further.

Each entry names the decisions it relies on, so a child never has to reopen
one.

## Downstream work

Everything after this task: to-spec, to-tickets, the one formality ticket, and
the close, where the review triage creates one follow-up effort per accepted
entry. A child that this task cannot scope precisely stays out of the list and
goes in the map's `## Fog` instead.

## Notes

### Ordering note added after the additions grilling

This task's `blocked_by` list was created before the human's additions, and
dependency edges cannot be re-wired after creation. Treat this note as part of
the task's preconditions:

- Do not run before `task-model-comparison`: the task record the child efforts
  build on is decided there.
- Do not run before `session-and-timetable`: the timetable and session replace
  the day-by-day schedule, so the scheduling scope of a child effort is not
  the specification's itinerary scope.
- Do not run before `architecture`: module boundaries and the
  in-process-or-daemon rule decide what an effort can own.
- Do not run before `version-order`: the build order decides the sequence the
  child efforts follow.
- `release-plan` runs after this task, so here the goal is the effort list, the
  scopes, and the order. Assigning efforts to releases is `release-plan`'s job.

The list must also reflect the human's build order: a CLI stage first, then
backend logic, MCP, the daemon, the desktop client, and the tray. A child
effort that cuts across stages is a signal to split it.
