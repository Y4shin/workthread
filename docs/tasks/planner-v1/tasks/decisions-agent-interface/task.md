---
type: task
subtype: grilling
title: Settle the MCP surface, the change feed, agent attribution, and notification delivery
status: stable
workflow_state: done
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

### The settled rules

**DEC-9, a prevented cycle.** A dependency change that would close a loop is refused, and the refusal leaves an ephemeral notice naming the tasks in the loop. It is not a persistent condition: the graph never changed, so there is nothing that stays true and nothing to resolve. Rejected: leaving no trace, so an unattended agent's rejection would never reach the human; and a notice that resolves once the loop is no longer possible, which describes a condition that cannot be evaluated because the change never happened.

**Vocabulary: ephemeral.** The two notice lifetimes are **persistent** and **ephemeral**, replacing the earlier "one-off". A persistent notice lasts while its condition holds, an ephemeral one stays until it is dismissed, and dismissal of a persistent notice hides it until its condition clears.

**Tool shape. Flat, one tool per operation.** Names are snake case, prefixed by the noun they act on: `task_create`, `task_set_estimate`, `task_set_remaining`, `session_start`, `notice_dismiss`. Each tool has a strict parameter schema of its own. Managing context pressure through progressive disclosure is the agent's problem; the product's job is a well-named, strictly described inventory. Rejected: an `action` parameter, which moves the choice into a value the model gets no schema help with and makes every call a wide bag; and one tool per surface port, which gives the agent an enormous operation where most parameters are irrelevant.

**Identifiers.** Every identifier parameter accepts the bare integer or the display form, `TSK-42` or `PRJ-7`. There is no prefix matching and no fuzzy target, consistent with identifiers being integers. A display form naming the wrong record kind is an error rather than a lookup into the other kind.

**Errors.** Every failure returns a stable machine-readable code, a human message, and the offending entity or field. The known codes include `confirmation_unavailable`, `confirmation_denied`, `confirmation_timeout`, `confirmation_attempts_exhausted`, `session_not_started`, `active_timer_conflict`, `estimate_required`, `dependency_cycle`, `not_found`, and `invalid_field`.

**Confirmation. A pending operation and a two-digit code.** A call that needs a human's approval does not execute. It records a pending operation holding the intent and its own parameters, a two-digit code, a five-minute expiry, and an attempt count, and it returns the operation id together with a statement that approval is required. The code reaches the human through the strongest channel available: an ephemeral notice on an attached interactive surface, else an operating system notification, else a private log file, else a free stream that is not carrying the protocol. The human gives the code to the agent and the agent calls the confirmation operation with the operation id and the code, at which point the stored intent runs. Three failed attempts kill the pending operation, and re-requesting creates a new one with a new code. The agent keeps the operation id and never needs to show it to the human.

Trust assumptions per channel, which decide the order: a window, the tray, and an operating system notification are agent-proof; a private log file is agent-proof only if the agent has no filesystem access; a free stream only if the client does not surface it to the agent. With no channel at all, `confirmation_unavailable` still applies, as the specification says.

Rejected: a blocking handshake, which ties up the call for as long as the human takes and needs a timeout that every client then interprets; and a polling handshake with a status call, which makes every agent implement a state machine the product can hold for it. The cost accepted with the code is one extra round trip through the human, and the benefit is that confirmation works even when the only channel to the human is a desktop notification on a machine nobody is sitting at.

**Change reporting has two halves.** Every read of a changing thing takes an optional cursor and returns the complete current state plus the changes since that cursor, grouped per entity, with each group headed by a line saying that this is the history and that the current state came with the response. Separately, one general change feed returns everything since a cursor regardless of entity, grouped the same way, and its header states plainly that it must not be used to reconstruct state. Every response hands back a fresh cursor for the next call.

The cursor is opaque to the caller: it is the change log's sequence position, dressed so a caller cannot do arithmetic on it, not a wall-clock time, because two changes can share a millisecond and a timestamp would then either skip or repeat changes.

Rejected: a feed-only design, which makes the agent maintain its own shadow of the total state and get it wrong, which is the failure the human named first; and feeding projections, which fills the feed with recomputed entries nobody caused.

**Attribution.** Every change records the surface it came from, one of command line, desktop, tray, and agent, and whether a human or an agent drove it. There is no user identity field: a single-user local tool cannot honestly claim one, and a field that always holds the same value would look like authentication the moment the daemon binds a network address.

**Notification delivery.** The per-type OS flags, configurable levels, quiet hours, and one-delivery-per-condition from `decisions-forecast-warnings` stand. This pass adds the delivery ladder above, which the confirmation code shares, and the rule that the notice list is always the authoritative place, so an OS copy is never the only trace of a condition.

### Consequences for dependent tasks

- `architecture`: a pending operation is stored state with an expiry and an attempt count; the change log's sequence position becomes the cursor the agent surface hands out; the delivery ladder is a property of the surfaces, not a port.
- `session-and-timetable`: the word "ephemeral" replaces "one-off" in the notice model.
- `decisions-service-mode`: confirmation needs at least one channel to a human, so a standalone `mcp serve` with no window, tray, OS notifications, log file, or free stream can confirm nothing.
- `decisions-data-lifecycle`: pending operations expire and must be purged; ephemeral notices are stored records; the private log file is a new artifact with its own retention question.
- `child-efforts`: the MCP child owns the flat tool inventory and the schema descriptions; the client owns the ephemeral notice channel and the code display; the CLI owns the free-stream rung.

### New work and remaining fog

No new planning task. Nothing remains foggy.
