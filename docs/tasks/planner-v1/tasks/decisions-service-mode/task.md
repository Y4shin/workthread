---
type: task
subtype: grilling
title: Settle the default service mode, timer lifecycle, and the open transport architecture
status: stable
workflow_state: ready
blocked_by:
- spec-additions
- stack-research
---

## Decision to settle

Settle the service model that both run modes share, and the lifecycle rules
around it.

- **DEC-3.** Which mode is the default installation: daemon mode,
  single-process mode, or ask at first run?
- **DEC-4.** What happens to an active timer on service stop, restart, or
  crash?
- The architecture the specification deliberately leaves open in section 6.7:
  transport, framing, endpoints, ports, process count, single-instance
  enforcement, how mode switching stops one owner before the other starts, and
  where the timer lives in each mode.

## Parent decisions

The non-negotiables task settled that both modes are required and that one
planner service is the single authority. `stack-research` supplies the
evidence about the MCP library, the shared-JVM shape, and persistence locking.

## Known choices

- DEC-3 alternatives: daemon mode by default, single-process mode by default,
  or ask at first run with an explanation of agent availability.
- DEC-4 alternatives: keep the open entry across restart, stop it at a graceful
  stop and confirm on next start, prompt after a crash to keep, stop, or
  discard the untracked interval, or discard it silently.
- Section 6.7 leaves transport and endpoints to the implementation. The
  candidates the research should rank: an HTTP API over localhost for the
  desktop and tray clients, and stdio or a local socket for MCP.

## Recommended starting answer

DEC-3: ask at first run, suggest daemon mode for a user who wants agents
available while the interface is closed, keep single-process mode as the
lightweight option, and show the current mode and a Stop Service action.

DEC-4: stop the timer at a graceful stop and ask the user to confirm the end
time on the next start. After a crash, ask whether to keep the untracked
interval, stop it at the last known time, or discard it. Never continue a timer
silently across a long shutdown.

Architecture: one service interface in the core, an HTTP adapter for desktop
and tray in split mode, MCP as the agent interface in both modes, one
single-instance lock in the data directory, and mode switching as stop then
start with the same data.

## Downstream work

Fixes the module boundaries, the process model, the transport, the data
ownership and locking rules, and the lifecycle behavior that packaging must
support. Feeds `decisions-agent-interface`, `decisions-data-lifecycle`, and
`child-efforts`, and the scaffolding child effort.

## Notes

### Ordering note added after the additions grilling

This task's `blocked_by` list cannot gain new blockers after creation, so
record the ordering here: do not run before the `architecture` grilling.

The architecture grilling settles the module shapes, the transport, the
single-owner rule, and the in-process-or-daemon rule. This task then settles
DEC-3 and DEC-4 inside that frame, plus the lifecycle details the architecture
leaves to it.

One question changes shape because of `version-order`: the default installation
mode matters only once more than one surface exists. If the pre-v1 versions
ship the CLI alone first, "the default mode" is a question about the daemon
stage, not about day one. Answer it against the release plan in mind.
