---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the agent surface
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

> The `mcp` module; one tool per operation in snake case, prefixed by its noun,
> with strict schemas; identifiers accepted in both display forms; stable failure
> codes carrying the offending entity; the two-step confirmation handshake; the
> read of current state plus changes since a cursor; and `mcp serve` owning
> standard input and output.

### Destination

An agent surface over the same rules as the terminal, with nothing an agent can
reach that a person cannot, and nothing an agent can do without confirmation.

### Actor

The agent acting for the person, and the person delegating to it.

### Classification

Feature.

### Stories

1. As an agent, I want one tool per operation with a strict schema, so that I can
   call it correctly without guessing.
2. As an agent, I want identifiers in either form and a stable failure code with
   the offending entity, so that I can correct myself rather than retry blindly.
3. As an agent, I want to read current state plus changes since a cursor, so that
   I stay current without refetching the world.
4. As an agent proposing a destructive change, I want a pending operation instead
   of an action, so that the human confirms it out of band.
5. As a person running this on a machine with nothing else, I want `mcp serve` to
   own standard input and output completely, so that the protocol is never
   corrupted by a log line.

### Constraints

- The tool set mirrors the seven command areas and their operations rather than
  designing anew; the surface is a surface, not a launcher.
- `mcp serve` is its own process, owning standard input and output for its whole
  lifetime, and no module may write to standard output in that mode.
- Stable failure codes include `confirmation_unavailable`, `confirmation_denied`,
  and `confirmation_timeout`; the handshake is the one from the forecast effort
  and is not reimplemented.
- Reads return the complete current state plus changes since the caller's cursor,
  and hand back a fresh cursor.
- The SDK is `io.modelcontextprotocol.sdk:mcp` 2.1.0, the `mcp` artifact being an
  aggregator over `mcp-core` and `mcp-json-jackson3`; the JSON mapper is supplied
  explicitly.
- No transport other than standard input, and no write to standard output by any
  module.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.21, SRC-4.18..SRC-4.20, SRC-RULE-ACTIVITY-1..SRC-RULE-ACTIVITY-3,
    SRC-8.8.13, T02-032, T09-001, T09-002, T09-005, T09-006, T09-008..T09-015,
    T11-074..T11-077, T11-082, T11-084, T13-064, T005, T006, T007, T008, T009,
    T010, T011, T012, T014, T015, T017, T018, T019, T020, T035, T17-040,
    T18-023, T20-021, T25-010, MAP-021, MAP-052, MAP-054,
    CTX-cursor..CTX-change-feed

That is 53 rows.

### Position and release, already settled

Eighth in the build order, blocked by `07-cli-review`, release 0.6 Agents. Its
successor is `09-daemon-api`. The full order is the table under `## Effort order`
in the parent's architecture document.
