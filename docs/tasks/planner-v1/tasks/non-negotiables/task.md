---
type: task
subtype: grilling
title: Settle the program effort's non-goals and non-negotiable facts
status: stable
workflow_state: ready
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
