---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the daemon and API
status: stable
workflow_state: ready
blocked_by: []
---

Settle this effort's non-goals and non-negotiable facts, including its
effort-level success test. The scope, the requirement list, and the position are
recorded below and come from the parent effort.

### The raw request, verbatim

The human asked for all twelve child efforts in one pass of intake. This child's
scope, quoted from the parent map's `## Child efforts` section:

> The `api` module; the Spring Boot listener; the versioned OpenAPI contract
> organised by record kind; the server-sent event stream with `Last-Event-ID`
> resumption and its heartbeat; the remote proxy implementing every surface port,
> with the single stream reader; the daemon and split shapes in the configurator;
> the token rule; and graceful stop with takeover.

### Destination

A daemon any surface can attach to, and a client that runs in either mode
without knowing which. The same rules, with the work logic optionally in another
process.

### Actor

The person running the tool in more than one place at a time, and the
application itself, which must behave identically either way.

### Classification

Feature.

### Stories

1. As a person with a window, a terminal, and an agent, I want one process to own
   the data and the rest to attach, so that three surfaces do not fight.
2. As a person on another machine, I want a split client to talk to the daemon,
   so that the data stays in one place.
3. As an operator, I want a non-loopback bind to require a token and a fixed
   port, so that exposure is deliberate.
4. As a person restarting the daemon, I want graceful stop to record the instant
   and leave open entries for correction, so that a restart loses nothing.
5. As a consumer writing against the API, I want one versioned OpenAPI document
   describing the resources and the stream, so that I write from one source.

### Constraints

- The API is a contract: REST resources by record kind, versioned, one OpenAPI
  document, no RPC-shaped endpoint.
- The live channel is server-sent events on the same API, port, document, and
  version; resumption is `Last-Event-ID` carrying the change cursor; three event
  kinds, `state`, `changed`, and `notice`; no pushed projections.
- The remote proxy implements every surface port and translates calls to
  resource operations, so a surface cannot tell which mode it is in.
- One owner per data directory, enforced by the lock; a lost daemon is not a mode
  change, and takeover is explicit.
- Business settings live in the daemon's data and are reached only through it;
  process configuration is separate; no credential is published in the runtime
  directory.
- Heartbeat every fifteen seconds, emitter timeout of thirty minutes, virtual
  threads enabled.
- No desktop window and no tray here.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.23, SRC-4.25, SRC-4.27, SRC-RULE-ARCH-4, SRC-8.8.15, T03-011,
    T11-040..T11-042, T11-058..T11-063, T11-065, T11-078, T031, T18-001,
    T18-002, T18-004..T18-008, T18-010, T18-011, T18-013..T18-018, T18-022,
    T19-003, T19-014..T19-021, T20-001..T20-018, T24-015, MAP-024, MAP-030,
    MAP-060..MAP-067, CTX-daemon, CTX-split-mode, CTX-event-stream

That is 73 rows.

### Position and release, already settled

Ninth in the build order, blocked by `08-mcp-agent-interface`, release 0.7 The
daemon. Its successor is `10-client-shell`. The full order is the table under
`## Effort order` in the parent's architecture document.
