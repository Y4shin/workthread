---
type: task
subtype: grilling
title: Settle the program effort's non-goals and non-negotiable facts
status: stable
workflow_state: done
blocked_by: []
---

## Decision to settle

This effort is the program parent for planner v1. Settle its non-goals and
its non-negotiable facts, including the effort-level success test.

The shape the human already chose:

- **Full map up front.** The child-effort list, their order, their entry
  criteria, and their scopes are settled inside this effort before any
  implementation effort starts.
- **This effort owns architecture and product decisions.** It writes the
  architecture and settles the spec's unresolved product decisions. It does
  not implement. Repository scaffolding (core module, Spring Boot API,
  JavaFX client, build, CI) is a child effort that this effort only specifies.
- **The specification moves verbatim.** `docs/initial_spec.md` moved to
  `docs/tasks/planner-v1/initial_spec.md` unchanged, and stays the reference
  text.

What the grilling must produce:

1. `## Non-goals` on the map: what this program effort does not do (the
   strongest candidate is "writes product code"), plus anything the spec
   rules out that a child effort might otherwise absorb.
2. `## Non-negotiable facts` on the map: the facts every child effort must
   obey. Candidates to confirm, reject, or sharpen:
   - Three separable Java shapes: a core module holding the work logic, a
     Spring Boot API module, a JavaFX client module.
   - Both run modes are requirements: standalone process with no API, and
     split process with the API between daemon and client.
   - Work logic lives in the core, never in the API or the client, so
     standalone mode never needs a fork of it.
   - One planner service is the single authority for validation, data,
     scheduling, forecasting, warnings, and the timer (spec RULE-ARCH-1
     through RULE-ARCH-6).
   - The itinerary is the primary output; forecasts and warnings support it
     (spec P11).
   - v2 advisory sections 7 and 10 are out of v1.
3. The effort-level success test: the observable condition under which this
   effort is done.
4. A position on how child efforts are represented. Nested efforts are not a
   graph edge in this workflow: child efforts are separate efforts under
   `docs/tasks/`, referenced by the map, not wired with `blocked_by`. Confirm
   the mechanism (map section listing each child effort with its scope,
   dependency position, and entry criteria) and how a child effort's origin
   is recorded.
5. A position on product naming. The repository and `CONTEXT.md` use
   "workthread"; the specification titles the product "Itinerary Planner".
   One name must win before child efforts name anything.

## Raw request (verbatim)

> I want to create an effort that takes the spec at ./docs/initial_spec.md and
> turns it into multiple other efforts to implement the spec. This effort
> should also own the responsibility to do most of the overall architecture
> and product decisions. Make sure ./docs/initial_spec.md is moved into that
> effort's directory.

## Destination

One line, in the human's words: "A program effort that turns the specification
into the ordered set of implementation efforts, and owns the architecture and
product decisions they all depend on."

## Actor

The developer implementing planner v1, and any child effort owner who needs
scope, boundaries, and settled decisions without re-reading 2100 lines of
specification.

## Classification

Feature. Not a defect. This is a planning and decision-ownership effort, not a
code change.

## Stories

- As the developer implementing v1, I want the specification decomposed into
  ordered child efforts with explicit scopes and entry criteria, so that I can
  start work without re-deciding scope mid-flight.
- As a child effort owner, I want an architecture document that fixes module
  boundaries and the standalone-versus-split axis, so that the work logic
  exists once and both modes use it.
- As the developer, I want every unresolved specification decision settled
  once, with its consequence and rationale recorded, so that child efforts do
  not re-litigate DEC-1 through DEC-16 and the open architecture questions.
- As the developer, I want one authority for validation, data, scheduling,
  forecasts, warnings, and the timer, so that desktop, tray, and agent
  surfaces cannot drift into separate planners.
- As the developer, I want the specification to live inside this effort, so
  that the reference text cannot drift between a stray `docs/` file and the
  planning tree.

## Context the grilling needs

- The repository has no code yet. No `pom.xml`, no modules, no CI. `docs/`
  holds `initial_spec.md`, `testing.md`, `dev-env.md`, empty `adr/`, and the
  task tree.
- `docs/initial_spec.md` is 2111 lines. Sections 1 through 6 and 8 are
  binding v1 material. Sections 7 and 10 are v2 advisory. Section 9 holds 16
  unresolved product decisions (DEC-1 to DEC-16) that the spec says MUST be
  resolved before the spec can be called complete.
- Section 6.7 lists what the spec does and does not settle architecturally:
  transport, framing, endpoints, ports, process count, and internal
  interfaces are explicitly left to the implementation.
- `CONTEXT.md` fixes four domain terms: daemon, client, standalone mode, split
  mode. Child efforts must use them.
- `AGENTS.md` requires short sentences, active voice, and no em dashes in
  written artifacts.

## Constraints

- "Don't know" is an acceptable answer at grilling time; record it rather than
  guessing.
- Architecture and product decisions belong in this effort's own artifacts,
  not in a child effort.
- Scaffolding is a child effort's job. This effort may specify it.
- The moved spec is not edited.

## Notes

### Settled decisions

**1. Product name.** workthread everywhere. The repository and `CONTEXT.md`
stay authoritative, and "Itinerary Planner" survives only as the title of the
moved specification document. Rejected: renaming the product to Itinerary
Planner (adds a rename to a child effort); workthread in code with Itinerary
Planner in prose (two vocabularies to keep aligned). Consequence: every child
effort names workthread.

**2. Success test.** This effort is done when every child effort for planner
v1 exists as a live effort under `docs/tasks/` with its own map, scope, entry
criteria, and `origin_effort: planner-v1`, and every cross-cutting
architecture and product decision a child would otherwise have to settle is
recorded in `docs/tasks/planner-v1/architecture.md` or in an ADR.

**3. Non-goals.** No product code, no build files, no CI configuration, no
module skeleton. No implementation of any v1 capability. No v2 advisory work
(specification sections 7 and 10). No calendar, billing, team, kanban, chat,
plugin marketplace, or autonomous scheduling scope. No release or packaging
work.

**4. Route to the close.** to-spec writes the specification, to-tickets writes
`architecture.md` and exactly one ticket, then finalize closes the effort on
branch `effort/planner-v1`, merged back with `--no-ff` and pushed. The single
ticket is a formality: it carries no implementation, it is marked
`mode: human` so the router hard-refuses autonomous dispatch, and it exists
only to clear the finalizable gate.

Mechanism evidence gathered during the grilling:

- `tw_archive_effort` and the implementation gate both refuse an effort that
  has a spec and zero tickets ("has a spec but no tickets": ticket generation
  has not run), so an empty ticket list cannot reach the close.
- An effort with no spec and no tickets is finalizable, which is the rejected
  alternative: map straight to finalize. Rejected because the human wants the
  standard arc and the architecture document.
- `finalize-effort` discloses `tw_create_effort`, and the tool takes
  `origin_effort`. A `follow-up` review disposition requires a live child
  effort whose map carries the origin slug. This is the sanctioned way to
  create child efforts.
- `finalize-effort` holds `tw_write_architecture`, so the close can reconcile
  the design record.
- This repository has no `.changeset/config.json`, so the changeset step will
  refuse and be reported as skipped. The archive gate does not require it.

**5. Decision record.** One document, `docs/tasks/planner-v1/architecture.md`,
written by to-tickets through `tw_write_architecture` and reconciled at the
close. Child efforts copy the parts they own. Rejected: ADRs alongside (kept
available for a decision that later meets the ADR bar, but not the default
home); map sections only (the map cannot carry a full design).

**6. The 16 open decisions.** Grouped into five themed grillings, because one
grilling runs per Wayfinder pass. Rejected: one task per decision (sixteen
passes); one long grilling (too much in one sitting). Each decision belongs to
exactly one group:

- estimates and time semantics: DEC-1, DEC-13
- forecasts, risk, and warnings: DEC-2, DEC-5, DEC-6, DEC-7, DEC-8, DEC-15,
  DEC-16
- service mode and lifecycle: DEC-3, DEC-4, plus the open architecture of
  section 6.7
- data lifecycle: DEC-10, DEC-11, DEC-12, DEC-14, plus the persistence choice
- agent interface: DEC-9, plus the MCP surface, the change feed, agent
  attribution, and notification delivery

**7. Stack research.** One research task on the Java stack before the
service-mode decision and the decomposition: MCP server library for Java,
keeping the core independent of Spring, JavaFX test tooling, and the
persistence candidates. Rejected: no research, relying on the provisional plans
in `docs/testing.md`.

**8. Build tool and test harness.** Settled in this effort, not by the
scaffolding child, and recorded in the architecture document. Rejected:
leaving the choice to the scaffolding child (the child would then settle a
decision the children all depend on); recording options only (defers a
decision the human already owns).

**9. Additions first.** The human wants to add requirements on top of the
specification before any research or decision grilling starts, so an
open-ended grilling task runs first and every other planning task is blocked
by it.

### Child efforts

Child efforts are separate efforts under `docs/tasks/`, recorded in the map's
`## Child efforts` section with scope, dependency position, and entry
criteria. Each is created at the close as an accepted follow-up, with
`origin_effort: planner-v1` on its map, after a human yes. They are never
tickets and never `blocked_by` edges.

### Downstream work this creates

The map was charted after this task, in dependency order:

1. `spec-additions` (grilling): the human adds requirements on top of the
   specification. Blocked by `non-negotiables`.
2. `stack-research` (research): the Java stack. Blocked by `spec-additions`.
3. `decisions-estimates-time` (grilling): DEC-1, DEC-13. Blocked by
   `spec-additions`.
4. `decisions-forecast-warnings` (grilling): DEC-2, DEC-5, DEC-6, DEC-7,
   DEC-8, DEC-15, DEC-16. Blocked by `spec-additions`.
5. `decisions-service-mode` (grilling): DEC-3, DEC-4, and section 6.7.
   Blocked by `spec-additions` and `stack-research`.
6. `decisions-data-lifecycle` (grilling): DEC-10, DEC-11, DEC-12, DEC-14, plus
   persistence. Blocked by `spec-additions` and `stack-research`.
7. `decisions-agent-interface` (grilling): DEC-9, the MCP surface, the change
   feed, attribution, and notification delivery. Blocked by `spec-additions`
   and `stack-research`.
8. `child-efforts` (grilling): the child-effort list. Blocked by every task
   above.

### Remaining fog

None beyond the tasks listed above. The child-effort list itself is the
`child-efforts` task, not an open question.
