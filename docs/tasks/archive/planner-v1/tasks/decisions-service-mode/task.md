---
type: task
subtype: grilling
title: Settle the default service mode, timer lifecycle, and the open transport architecture
status: deprecated
workflow_state: done
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

### The settled rules

**DEC-3, the mode is detected, not chosen.** `workthread` looks for a running daemon and attaches to it when it finds one. With none running, it executes the work logic in its own process. The daemon is a subcommand of the same binary, meant to be started by a service manager such as systemd, never a second artifact. No first-run wizard, and nothing written to configuration before the first task exists. A user who wants an agent to reach the data while no window is open runs the daemon subcommand; a user who does not never meets it.

**A lost daemon is not a mode change.** While a client is attached and the daemon stops, crashes, or becomes unreachable, the call in flight fails with a clear error, and reconnecting or restarting the daemon is the fix. Continuing in the client's own process is offered as an explicit action that takes ownership of the data, never as an automatic fallback. The reason: the client has been reading a state the daemon computed, so a silent switch changes the plan, the session, and the notices underneath the user while the mode label says nothing happened.

**DEC-4, stop and crash.** A graceful stop records the instant it happened in the operation log and leaves open timers and the session open. On the next start, anything opened before that instant is presented for correction, with its end defaulting to the recorded stop. A crash leaves no fresh stop instant, so the same machinery asks. Consequence for `session-and-timetable`: the `missing_session_end` notice covers a day, and an open time entry needs the same treatment at startup, with the stop instant read from the log rather than from a heartbeat.

**Configuration has an owner, and no file is shared.** Three places:

- The daemon's process configuration, in a file or in the environment, because it has to be readable before the database opens: the data directory, the bind address, the port, the token, and `serveMcp`.
- The surface's own configuration: an endpoint override for a daemon on another host, and that surface's own preferences such as window size. Nothing business-related.
- The business settings, in the daemon's data: the timetable, the planned start, the break reserve, the four balancing values, the notice levels, the approach lead window, the concurrency setting, and quiet hours. All reached through the daemon and its API, never through a file.

Rejected: one file with sections, which is what the human named as the problem, since a client can open a file it is not supposed to write and code discipline is not a boundary; and the business settings in the daemon's own file, where a change needs a restart to apply and validation happens at a different time than it does for every other change.

**The API is a contract, not an internal pipe.** REST resources organised by record kind, versioned paths, documented with OpenAPI, and usable by consumers outside the application. The remote proxy translates surface-port calls into resource operations, so the split-mode client, the tray, and the CLI all use the same contract, and an external consumer uses what they use. The reasons the human gave: type-safe communication between processes, learning to build real Spring Boot APIs, and leaving the door open for consumers beyond MCP, an editor or agent plugin wanting proactive notifications for instance.

Two rules follow. Every operation maps to a resource or to an action on a resource, so there is no RPC-shaped endpoint. And the reads that belong together use the change cursor, which the cursor already provides.

**The live channel is deferred to evidence.** v1's API is pull-only: reads with filters and pagination, the change feed by cursor, and notices and confirmations as resources. Whether a live push channel ships at all, and whether it is server-sent events or WebSockets, is decided by two tasks created in this pass: `live-api-research` and then `live-api-choice`. The human's uncertainty is about tooling rather than protocol: Spring's support on the server, OpenAPI's coverage of event streams, AsyncAPI's coverage of WebSockets, and how well client tooling in other languages follows each.

### Consequences for dependent tasks

- `architecture`: the API module becomes a published contract with an OpenAPI document; the remote proxy maps surface ports onto resources; the daemon/surface configuration split lands here.
- `decisions-agent-interface`: the REST resources for notices and pending operations have to agree with the MCP tool shapes, since both expose the same state.
- `session-and-timetable`: an open time entry needs the same startup correction as an unended day.
- `decisions-data-lifecycle`: the stop instant is written to the operation log, which must therefore record service lifecycle events, not only record changes.
- `child-efforts`: the API child's scope depends on `live-api-choice`, and the ordering note is recorded there.
- `live-api-research` and `live-api-choice`: created in this pass, both blocked in order.

### New work and remaining fog

Two planning tasks were created, so the planning frontier is not empty and the map cannot be finalized yet:

- `live-api-research` (research), blocked by `decisions-service-mode`.
- `live-api-choice` (grilling), blocked by `live-api-research`.

No fog remains.
