---
type: task
subtype: grilling
title: Settle the child-effort list, their scopes, order, and entry criteria
status: deprecated
workflow_state: done
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

### Ordering note added after the service-mode grilling

This task's `blocked_by` list cannot gain new blockers after creation, so record
the ordering here: do not produce the final effort list before `live-api-choice`
is done.

Reasons:

- The API child effort's scope depends on whether the API carries a live push
  channel, which is decided by `live-api-choice` from the evidence in
  `live-api-research`. A push channel adds a transport, a stream state, and a
  description format, and may add an effort of its own.
- The API is now a published contract, documented with OpenAPI and usable
  outside the application, so the child that owns it owns a contract rather than
  an internal seam. That is a different scope from the one this task's body
  assumes.
- The daemon and surface configuration split decided by `decisions-service-mode`
  also lands in that child: the daemon's process configuration, the surface's
  own configuration, and the business settings that live in the daemon's data
  and are reachable only through the API.

Blocked: Aborted by the human before any decision was settled: the JavaFX UI choice is reopened, so the client efforts cannot be scoped. Unblocks when `ui-framework-research` has reported, and when `ui-framework-prototype` has run if that research names a candidate.

### Ordering note added after the UI framework was reopened

This grilling was opened, and the human aborted it before any question was
answered. No decision was settled, and nothing in this task's body was
contradicted. The reason is a new prerequisite: the human does not want the
desktop client built in JavaFX, and asked for a research task first, then a
prototype task if the research finds a candidate.

Do not run this grilling again until:

- `ui-framework-research` is done, and
- `ui-framework-prototype` is done, or deprecated because the research found no
  candidate.

Reasons:

- Three of the handful of client efforts in the list are scoped by the toolkit.
  A Compose Multiplatform client, a JavaFX client, and a local web interface
  differ in module count, in packaging, and in what the tray effort owns.
- The research may also change the number of efforts before the desktop stage,
  because a Kotlin module and a Kotlin toolchain are build work that
  `scaffolding` would own.
- If the research confirms JavaFX, this note costs one pass. If it does not,
  the list would have been scoped against a toolkit the human had rejected.

Scope of the reopen, so this grilling does not re-litigate settled decisions:
the work logic stays in Java, the core stays framework-free, the surface ports
and the dual-mode rule do not move, and the eight-module graph keeps its
inward dependencies. Only the toolkit inside the `client` module, the packaging
route, and the tray mechanism are open.

### The block is lifted

`ui-framework-decision` settled the toolkit: JavaFX ships, on the 21 LTS line,
and the human declined the prototype. Both prerequisites named in the note above
are therefore done, and nothing else stands between this grilling and its work.

This task is workable and is the next pass's work. Its `workflow_state` still
reads `blocked`, because no `tw_*` tool clears that state; the done-marking
resolves it when the grilling settles. Do not read the state as an obstacle.

The list this grilling produces should now expect a JavaFX `client` module with
no second language, and it should not carry a Kotlin toolchain in
`scaffolding`.

### Settled: twelve child efforts in the stage order

The list is recorded in the map's `## Child efforts` section, which the task
body names as the destination for this result. Twelve efforts, in the settled
pre-v1 sequence:

1. `scaffolding`, which also ships one empty JavaFX window as a `jlink` image.
2. `work-store`.
3. `cli-basics`, plus the minimal day start the timer requires.
4. `cli-work-model`.
5. `cli-planning`.
6. `cli-forecast`.
7. `cli-review`.
8. `mcp-agent-interface`.
9. `daemon-api`.
10. `client-shell`.
11. `client-views`.
12. `tray-surface`.

**Answers taken, all as recommended.** Twelve efforts is the right
granularity, one per capability area. The data model and persistence stay
together in their own effort, second. The timer's day-start rule is honoured
from the first version by giving `cli-basics` a minimal day start whose length
comes from one configured default, which `cli-planning` later replaces with the
timetable. The desktop client is two efforts, the shell with Today first.
`scaffolding` proves a packaged empty window rather than stopping at build
files, because the module-path rule is enforced by the build.

**Alternatives rejected.**

- Nine efforts, merging three CLI pairs. Rejected because each merged effort
  would span two capability areas, which the settled order treats as a signal to
  split.
- Six efforts, roughly two per stage. Rejected for the same reason, more
  strongly, because it also merges across stage boundaries.
- The store folded into `scaffolding`, which would make scaffolding produce
  product code against the map's non-goals, or into `cli-basics`, which would
  decide the store's contract under pressure from the first command.
- Moving the timetable into `cli-basics`, or suspending the day-start rule until
  `cli-planning` arrives. The first grows the first effort across two capability
  areas; the second changes observed behaviour between two versions.
- One client effort, which would span the process setup and four views and delay
  the first window a user sees.
- `scaffolding` without a packaged window, which leaves the module-path rule
  untested until the desktop stage.

**Constraints the list carries.** Dependencies are a strict chain in stage
order, because versions are additive and each is tagged before the next opens. A
child is scoped to a capability group, never to a surface. Releases are not
assigned here; `release-plan` does that.

**Newly discovered work, charted rather than assumed.**
`cli-surface-convention` is a new grilling task. The specification never
mentions the command line interface, and nothing on the map settles its command
vocabulary, output format, or exit codes. Seven of the twelve efforts are CLI
efforts, so without one convention they would each invent a different one and
the additive-version rule would be enforced against seven vocabularies. The task
carries a recommended shape so the grilling starts from something concrete.

**Remaining fog.** None. The one gap found during this grilling is now a task,
not a fog line.

**Write-back.** The list went to `## Child efforts`, which this task's body
names as its destination. That section references this task's slug, so the
done-marking is satisfied.
