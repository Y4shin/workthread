---
type: task
subtype: grilling
title: Settle the overall architecture that supports the whole pre-v1 sequence
status: stable
workflow_state: ready
blocked_by:
- spec-additions
- version-order
- stack-research
---

## Decision to settle

Settle workthread's overall architecture, flexible enough that the whole
pre-v1 sequence works without a rewrite.

- **Module shapes.** The core work logic, the CLI, the MCP interface, the
  Spring Boot API module, the JavaFX client, and the tray. What each one owns,
  what it must not own, and which direction the dependencies run.
- **The in-process-or-daemon rule.** Every user-facing or external-facing
  interface works with the backend in the same process or by talking to the
  daemon. How that is expressed once, so no interface grows its own service
  copy.
- **Transport.** Localhost HTTP for the API, stdio for MCP, and the CLI's two
  roles. The architecture must state how an interactive CLI session and an MCP
  stdio session stay separate, because one process cannot own the terminal's
  standard streams for both.
- **Seams and ports.** The persistence port, the service facade every front end
  uses, the clock, and the change feed. Which boundaries must be fakeable in
  tests.
- **Where the planning engine lives.** Scheduling, ordering, and forecasting as
  a pure function over data, current time, and the timetable, so the same code
  serves every mode.
- **Persistence and the single owner.** Where the data directory is, how only
  one owner writes at a time, and how the daemon and the in-process mode share
  one store.
- **Extension points for post-v1.** Multi-day allocation, intervals with pause
  budgets, and a network-hosted daemon. The architecture must say where they
  would attach, without building them.
- **The domain model.** The task record decided by `task-model-comparison`,
  placed in the core.

## Parent decisions

`spec-additions` recorded the build order and the in-process-or-daemon rule.
`version-order` settles the sequence the architecture must support.
`stack-research` supplies the evidence on the MCP library, the shared-JVM
shape, persistence candidates, and the build tool. The non-negotiables task
fixed that work logic belongs to the core.

## Known choices

- A hexagonal core with adapters for persistence, HTTP, stdio, CLI, JavaFX, and
  tray, against a layered arrangement with a service facade.
- One service interface with two hosts, in-process and remote, or two service
  implementations with a shared engine.
- The Spring Boot API as the only remote transport, or a thinner RPC seam that
  the API happens to implement.

## Recommended starting answer

A hexagonal core: domain model, services, and ports, with no Spring, JavaFX, or
MCP dependency. One service facade is the only way a front end reaches the
logic. Two hosts implement the same facade contract: an in-process host and a
remote adapter that speaks to the daemon's HTTP API. The CLI, MCP, desktop, and
tray all code against the facade or the remote adapter, never against the
storage. The scheduling and forecast engine is a pure function. Persistence,
the clock, and the change feed are ports. Post-v1 complexity attaches as new
inputs to the pure function, not as new scheduling code paths.

## Downstream work

Fixes the architecture document this effort produces, the scope of the
scaffolding child effort, and the boundaries every child effort works inside.
Feeds `release-plan` and `child-efforts`.
