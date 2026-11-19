---
type: task
subtype: research
title: Choose server-sent events or WebSockets for the API's live channel
status: stable
workflow_state: done
blocked_by:
- decisions-service-mode
---

## Precise question

Should the workthread API carry a live push channel, and if so, is it
server-sent events or WebSockets? The answer must be evidence, not preference,
because the reason the human is unsure is tooling rather than protocol design:
Spring's own support on the server side, how well each protocol is described by
an API document, and how well client tooling follows each description.

The API is settled as a versioned, OpenAPI-documented REST contract usable by
consumers outside the application, and it is pull-only until this task and
`live-api-choice` say otherwise. So the question is narrow: for a
single-owner daemon that pushes "something changed" and "this notice was
raised" to a handful of local or remote consumers, which transport carries it,
and what does each one cost in code, documentation, and operational surprises?

## Sub-questions to answer with evidence

1. Server side: what does Spring Boot 3.x / Spring Framework 6.x document for
   server-sent events (Spring MVC emitters and Spring WebFlux streams) and for
   WebSockets (including STOMP)? Which one is a smaller addition to a Spring
   Boot module that already serves a JSON REST API?
2. Description: does the current OpenAPI specification cover an event-stream
   response with per-event schemas, and which tool generators honour it? Does
   AsyncAPI cover WebSockets, and what is its generator situation? Cite the
   specifications and at least one generator per language the human might use
   (Go, TypeScript, Python).
3. Resumability: how does a client resume a dropped event stream (the
   `Last-Event-ID` header, and what it means for the change cursor), against
   how a WebSocket client reconnects and replays. Which one makes "give me
   everything after this cursor" simpler?
4. Operations: proxies, HTTP/1.1 connection limits, HTTP/2 multiplexing,
   buffering middleboxes, and keep-alive behaviour during quiet hours of a day
   where nothing changes. Which transport survives a corporate proxy and a
   sleeping laptop with less custom code?
5. Direction: is there any v1 or near-post-v1 need for client-to-server
   messages over the same channel? Write operations, confirmations, and
   dismissals are planned as REST calls, so a bidirectional channel buys
   nothing unless something real needs it.
6. Cost in this codebase: what a stream means for the single-owner model, the
   transaction-per-port-call rule, and the change log cursor. The daemon
   computes projections per caller; a pushed projection is a different thing
   from a pushed change notification, and the distinction should be named.

## Trusted source boundaries

Primary sources only: the Spring Framework and Spring Boot reference
documentation for MVC, WebFlux, and WebSocket support; the OpenAPI
specification and its release notes; the AsyncAPI specification; the HTTP and
WebSocket RFCs where a claim depends on protocol behaviour; and the
documentation of any generator cited. No blog posts as load-bearing evidence;
they may point at a primary source but do not replace it.

## Evidence required for completion

A recommendation with a citation and a confidence level per sub-question, a
comparison table over the six questions, and an explicit statement of what
would have to change for the recommendation to be wrong. Findings captured at
`docs/tasks/planner-v1/tasks/live-api-research/findings.md` with the
`type: findings` frontmatter, plus a short write-back to the map.

## What it unblocks

`live-api-choice`, and through it the API child effort's scope in
`child-efforts`. If the research finds a hard requirement that no planning task
covers, record it as discovered work instead of widening this task.

## Notes

### Findings

Full evidence, every source URL, the comparison table, and the point-by-point confidence notes are at `docs/research/live-api.md`. This is the decision-graded digest.

**Method.** A background researcher worked from primary sources only: the Spring Framework 6.2 and Spring Boot 3.5 reference documentation, the OpenAPI 3.2.0 specification and its release notes, the springdoc changelog, the WHATWG HTML standard's server-sent events section, RFC 6455, RFC 9112, 9113, and 9114, the nginx proxy module documentation, the AsyncAPI release metadata, and the JDK 21 `java.net.http` javadoc. Blog posts were excluded as load-bearing evidence.

**Server side favours server-sent events, and Spring itself says so.** An SSE endpoint is one controller method returning `SseEmitter` with `produces=text/event-stream`, writing events in the standard format, with async support enabled and a timeout configured. WebSocket is a separate module, a handshake configuration, and a broker if STOMP is used. Spring's own guidance is explicit: "If the volume of messages is relatively low (for example, monitoring network failures) HTTP streaming or polling can provide an effective solution. It is the combination of low latency, high frequency, and high volume that make the best case for the use of WebSocket." A change-cursor feed is the low-volume case.

**Java 21 removes the classic objection.** With `spring.threads.virtual.enabled=true`, a blocked write per idle connection costs a virtual thread rather than a platform thread, so the usual reason to reach for WebFlux or WebSocket does not apply. The reference notes that individual writes remain blocking and happen on a separate thread; that is acceptable at this scale.

**Resumption is decisive.** The HTML standard defines `id:`, `retry:`, and the `Last-Event-ID` request header, so "give me everything after this cursor" is a header on the reconnect request the server already handles. RFC 6455 provides no cursor and no replay, only reconnection guidance with random initial delay and exponential backoff. A WebSocket resume would be a protocol we design, buffer, and race against the live stream.

**Description is mixed, and the human's suspicion was right in one half and wrong in the other.** OpenAPI 3.2.0, released 2025, added sequential media types and `itemSchema`, which describes each event of an event stream independently. So the same document can describe the REST API and the stream. AsyncAPI, the WebSocket-side standard, is at 3.1.0, but its first-party per-language generator templates are archived in the `asyncapi-archived-repos` organisation, so generating a WebSocket client from a description is not a supported path today.

**The real cost of SSE in this codebase is the Java client.** The JDK ships a `WebSocket` client and no `EventSource`, so the JavaFX, tray, and CLI clients each need a small SSE reader: a line parser, the `Last-Event-ID` header, and backoff. That is the strongest argument for WebSocket, and it is a bounded, testable piece of code. It is also worth stating plainly that "server-sent events reconnect automatically" is a browser `EventSource` property, not a wire guarantee, so the reconnect path is hand-written either way.

**Nothing was measured, and three things are unresolved.** No primary source states that springdoc cannot emit an `itemSchema` description; the claim rests on changelog absence from the 2.x line through 2.10.1 and the main line through 3.2.0, so treat it as unproven but likely. AsyncAPI 3.1.0's WebSocket binding contents were not read. Whether a generator treats a 3.0 or 3.1 `text/event-stream` schema as one payload was not tested. The three next steps recorded in the findings are: generate an API document from a throwaway stream endpoint and see what springdoc emits with the version pinned, time-box a spike of a hundred-line Java client reader against the real daemon, and confirm whether anything sits between client and daemon in split mode that buffers a streamed response.

**What this task does not decide.** `live-api-choice` decides. The evidence points at server-sent events, and it names the conditions that would flip the answer: a consumer that must send high-frequency messages on the same channel, a consumer that only speaks WebSocket, a middlebox that buffers streamed HTTP but passes an upgrade, a need for binary frames or sub-second round trips, or a Java reader that turns out to cost more than the decision assumes.
