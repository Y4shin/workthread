---
type: task
subtype: grilling
title: Settle the MCP surface, the change feed, agent attribution, and notification delivery
status: stable
workflow_state: ready
blocked_by:
- spec-additions
- stack-research
---

## Decision to settle

Settle the agent surface and the notification delivery it shares with the
desktop and tray.

- **DEC-9.** Does a prevented dependency cycle persist as a warning?
- The MCP surface the specification requires but does not enumerate: the tool
  set for reading planning state and for explicit writes, how identifiers are
  passed, which operations need confirmation, and what an override request
  returns when no interactive surface is attached.
- The change feed: what a cursor identifies, what a caller sees for changed,
  deleted, and derived records, and whether projections are fed or must be
  re-read.
- Attribution and safety: how every agent change is attributed to the
  requesting user, and how the surface refuses fuzzy targets and implicit
  batches.
- Notification delivery: which warning categories are notification-eligible,
  how quiet hours and platform permission are represented, and how the
  application guarantees a notification is never the only place a warning
  appears.

## Parent decisions

The non-negotiables task settled the effort boundary. `decisions-service-mode`
settles the transport and the process model. `decisions-forecast-warnings`
settles severity and the approaching-deadline condition. This task turns those
into an interface rather than re-deciding them.

## Known choices

DEC-9 alternatives: no persisted warning, a persisted informational warning the
user acknowledges, or a persisted warning that resolves when the attempted
cycle is no longer possible.

For the surface, the specification's constraints are explicit: explicit
identifiers, no fuzzy targets, no implicit batches, confirmation for
destructive actions, out-of-order completion, and rule overrides, and the
`confirmation_unavailable` error when no interactive surface is attached.

## Recommended starting answer

DEC-9: no persisted warning. Reject the dependency change, name the cycle path
in the response, and let the interface show the rejection. A warning that can
never resolve itself adds a permanent inbox item for a user action that already
failed safely.

Surface: one MCP tool per domain operation, mirroring the service interface,
with explicit identifier parameters. Reads return the same projection the
other surfaces show. Overrides are their own explicit operation with a
confirmation handshake. The change feed is a monotonic sequence per change with
a cursor the caller passes back.

Notifications: eligible categories are the ones `decisions-forecast-warnings`
marks critical or warning, subject to quiet hours and platform permission, with
the warning always visible in the interface.

## Downstream work

Fixes the agent-facing contract, the change feed's shape, and the notification
layer's rules. Feeds `child-efforts`, the child that owns the MCP interface,
and the child that owns notifications and tray behavior.

## Notes

### Ordering note added after the additions grilling

This task's `blocked_by` list cannot gain new blockers after creation, so
record the ordering here: do not run before the `architecture` grilling.

The additions changed this task's frame in two ways:

- The CLI is a first-class v1 surface and hosts MCP over stdio. The
  architecture grilling settles how an interactive CLI session and an MCP stdio
  session stay separate, since one process cannot own the terminal's standard
  streams for both. This task's surface description must match that decision.
- The agent surface stays the same contract in both run modes, so the tool set
  is defined once against the core service facade, not once per host.

DEC-9 is still this task's only numbered decision; the rest is the surface
design that `architecture` deliberately leaves here.
