---
type: task
subtype: grilling
title: Decide the live API's push channel from the research evidence
status: deprecated
workflow_state: done
blocked_by:
- live-api-research
---

## Decision to settle

From the research evidence, decide whether v1 ships a live push channel at all,
and if it does, which transport carries it and what it is allowed to carry.

The research settles what is true of Spring, OpenAPI, AsyncAPI, and the client
tooling. This session settles the product choice, which the evidence informs
but does not make.

## Known choices

- No push channel in v1, with consumers polling the change cursor.
- Server-sent events on the existing HTTP API, resuming from the change cursor.
- WebSockets, described with AsyncAPI, with the same resumption problem solved
  by hand.
- Push of change notifications only, with every projection still pulled by the
  consumer that needs it.

## Recommended starting answer

Decide against the research, with one preference stated up front: a pushed
change notification is not a pushed projection. Whatever the transport, the
stream should carry "something changed, here is the cursor", so the existing
transaction-per-call rule and the projection-per-caller rule survive untouched.
The alternative, pushing recomputed lists per subscriber, turns the daemon into
a per-client planner and is a much larger commitment than a notification
channel.

## Downstream work

Fixes the API child effort's scope in `child-efforts`, and therefore what
`release-plan` can promise, and whether the API document is enough to describe
the whole contract or needs a second description format.

## Notes

### The settled rules

**v1 ships server-sent events.** On the same HTTP API, the same port, the same OpenAPI document, and the same token rule. Resumption is the `Last-Event-ID` header carrying the change cursor, which the HTML standard defines and which RFC 6455 does not offer for WebSockets. WebSockets are refused for v1 on the recorded evidence: bidirectionality buys nothing when every mutation is already a REST call, the protocol carries no cursor to resume from, and AsyncAPI's first-party code generators are archived, so a WebSocket client cannot be generated from a description today.

**The stream carries notifications, not projections.** Three event types: `state`, sent once when the stream opens and carrying the current cursor; `changed`, sent as stored records change and carrying the cursor; and `notice`, carrying a notice's identifier, type, and level, so a consumer can decide whether it deserves the operating system's attention. Every list, forecast, and review is still read over REST. On reconnect the client compares the `state` cursor with its own and refetches over REST when they differ, so there is no replay buffer and no race between a replay finishing and the live tail starting.

**One Java reader.** The JDK ships a WebSocket client and no event-stream client, so one reader lives in the module that holds the remote proxy and serves the desktop, tray, and command line clients. It parses the stream, resends the cursor as `Last-Event-ID`, and backs off on failure. Plain polling stays available as a fallback, because a dropped stream is a latency problem rather than a correctness problem.

**Operational shape.** A comment heartbeat every fifteen seconds, which survives the sixty-second default idle read timeout of a default nginx proxy and satisfies Spring's own note that a stream must send data periodically because a write fails once the client is gone. A server-side emitter timeout of thirty minutes, so a connection is recycled through the deliberate reconnect path instead of living for days. The event id is the change cursor, so the standard resume mechanism does the work the project already needed a cursor for.

**What this does to the API document.** Now: the response is declared as `text/event-stream` with an explicit event schema, which OpenAPI 3.1 expresses as one payload schema. When the pinned springdoc line emits OpenAPI 3.2, the endpoint gains `itemSchema` so each event in the stream is described independently. The research records this as the one place where the specification is ahead of the tooling, and it changes nothing else.

### Alternatives rejected

- No push channel in v1: correct but inattentive, since every consumer would poll on a timer and the latency of a change would become a guess.
- WebSockets in v1: the three reasons above, against a channel that would be used one way in practice.
- Pushing projections per subscriber: the daemon would plan once per open connection and become a second source of truth that can disagree with a REST read.
- Pushing record diffs: a second contract with the REST one, to be kept in step forever.
- Replaying each missed change on reconnect: a replay buffer plus the race between replay and live tail, which is where live channels usually leak.
- A reader per surface: three copies of reconnect and backoff logic, free to disagree.
- No fallback to polling: a dropped stream would become a correctness problem.
- No heartbeat, or a sixty-second heartbeat: silent death behind a proxy, or a heartbeat that lands exactly on the default proxy timeout.

### Consequences for dependent tasks

- `child-efforts`: the API child owns the stream endpoint and the event schema; the module holding the remote proxy owns the reader; the desktop, tray, and command line clients consume it.
- `decisions-agent-interface`: MCP is unchanged, since the agent surface stays tools over stdio; an agent that wants push reads the API stream like any other consumer.
- `decisions-forecast-warnings`: the notice event carries type and level, so an external consumer applies the same level-based judgement the product applies.
- `decisions-data-lifecycle`: the cursor is a log position, so resumption reads the log the project already keeps; the stream adds no storage of its own.
- `release-plan`: the stream lands with the daemon stage, because that is when the API exists.

### New work and remaining fog

No new planning task, and no fog. The research's three unresolved items, the springdoc `itemSchema` support, the cost of the Java reader, and whether anything buffers the stream in split mode, are recorded in the findings as next steps for the child that builds it rather than as open decisions.
