---
type: map
title: "Planner v1: decompose the specification, own architecture and product decisions"
status: stable
---
# Planner v1: decompose the specification, own architecture and product decisions

## Destination

Planner v1 is turned into a set of live child efforts, each with a settled
scope, entry criteria, and a position in the implementation order. Every
cross-cutting architecture and product decision the children depend on is
settled here and recorded in this effort's architecture document. The
specification stays the reference text, inside this effort's directory.

## Constraints

- Full map before implementation: the child-effort list, their order, their
  scopes, and their entry criteria are settled here before any child effort
  starts.
- This effort owns architecture and product decisions. Child efforts own code.
- Throwaway prototypes and spikes are allowed when a cheap artifact settles a
  question faster than prose. Nothing mergeable is produced.
- Java 21 or newer, with three separable shapes: a core module holding the
  work logic, a Spring Boot API module, and a JavaFX client module.
- Both run modes are required: standalone mode with no API between daemon and
  client, and split mode with the API between them.
- Child efforts are separate efforts linked by `origin_effort` on the child's
  map. They are never tickets and never `blocked_by` edges.
- Close route: to-spec, then to-tickets for the architecture document and one
  formality ticket, then finalize on branch `effort/planner-v1`.
- Writes under `docs/tasks/` go through the `tw_*` tools only.

## Non-goals

- No product code, no build files, no CI configuration, no module skeleton.
  The scaffolding child effort owns all of it.
- No implementation of any v1 capability, and no mergeable change to a module.
- No v2 advisory work: tracker plugins and attachments (specification
  section 7), daily routine tasks and a server-hosted daemon (section 10).
- No calendar synchronization, billing, team or resource planning, kanban,
  chat, plugin marketplace, or autonomous AI scheduling, matching
  specification sections 1.4 and 2.2.
- No release, packaging, installer, or distribution work.
- No bug triage of implemented code: none exists yet.

## Non-negotiable facts

**Success test:** every child effort for planner v1 exists as a live effort
under `docs/tasks/` with its own map, scope, entry criteria, and
`origin_effort: planner-v1`, and every cross-cutting architecture and product
decision a child would otherwise have to settle is recorded in
`docs/tasks/planner-v1/architecture.md` or in an ADR.

- The product name is workthread everywhere. "Itinerary Planner" is only the
  title of the moved specification document.
- The reference text is `docs/tasks/planner-v1/initial_spec.md`, moved
  verbatim and not edited.
- Java 21 or newer. The build tool, Java version, and test stack are settled
  by this effort through the stack research task and recorded in the
  architecture document.
- Three separable shapes: a core module holding the work logic, a Spring Boot
  API module, and a JavaFX client module.
- Both run modes are requirements: standalone mode with no API between daemon
  and client, and split mode with the API between them.
- Work logic belongs to the core. The API module and the client never hold it,
  so standalone mode needs no fork of it.
- One planner service is the single authority for validation, authoritative
  data, scheduling, forecasting, warnings, recalculation, and the active timer
  (RULE-ARCH-1 to RULE-ARCH-6).
- The itinerary is the primary output. Forecasts and warnings support it
  (principle P11).
- Every unresolved specification decision (DEC-1 to DEC-16) is settled in this
  effort before decomposition, so no child effort re-litigates one.
- Child efforts are separate efforts under `docs/tasks/`, recorded in this
  map's `## Child efforts` section with scope, dependency position, and entry
  criteria, and linked by `origin_effort` on the child's map. They are never
  tickets and never `blocked_by` edges.
- The architecture document at `docs/tasks/planner-v1/architecture.md` is the
  design record child efforts copy from. It archives with this effort.
- The single implementation ticket is a formality. It carries no
  implementation and is marked `mode: human`, so the autonomous chain never
  runs for it.

## Decisions so far

- The product is called workthread everywhere. "Itinerary Planner" survives
  only as the title of the moved specification. Decided by the
  non-negotiables task.
- The success test, the non-goals, and the route to the close are recorded in
  this map from the `non-negotiables` task: to-spec, then to-tickets for the
  architecture document and exactly one formality ticket, then finalize on
  branch `effort/planner-v1`.
- The single implementation ticket is a formality marking: it carries no
  implementation, and `mode: human` keeps the autonomous chain off it.
  Decided by the `non-negotiables` task.
- One architecture document at `docs/tasks/planner-v1/architecture.md` is the
  design record, written by to-tickets and reconciled at the close. Decided by
  the `non-negotiables` task.
- The 16 unresolved specification decisions are settled here in five themed
  grillings, one grilling per pass, before decomposition. Decided by the
  `non-negotiables` task.
- Stack research on the Java side runs before the service-mode decision and
  the decomposition. Decided by the `non-negotiables` task.
- The build tool, Java version, and test harness are settled by this effort,
  not by the scaffolding child. Decided by the `non-negotiables` task.
- An open-ended grilling task runs first, so the human can add requirements on
  top of the specification before any other planning task starts. Decided by
  the `non-negotiables` task.

## Fog


