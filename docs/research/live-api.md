---
type: findings
title: "Live channel for the daemon: server-sent events or WebSockets"
status: stable
source_task: live-api-research
---

<!-- Provenance: produced by a background researcher against primary sources only;
     saved verbatim. Claims are tagged with their source and a confidence level, and
     the unresolvable points are listed under "Missing evidence". -->

# Research: live channel for a single-user Spring MVC daemon, SSE vs WebSocket

**Versions consulted.** Spring Framework reference **6.2.19** (the 6.x line that Spring Boot 3.5.x uses; the 7.0 line is current upstream but out of scope here), Spring Boot reference **3.5.16**, OpenAPI Specification **3.2.0**, WHATWG HTML Standard (living, fetched this run), RFC 6455 (Dec 2011), RFC 9112/9113/9114 (June 2022), nginx `ngx_http_proxy_module` docs, springdoc-openapi CHANGELOG (2.x branch through 2.10.1; main through 3.2.0), JDK 21 `java.net.http` javadoc.

## Summary

Every primary source points the same way for this project: SSE is a plain HTTP response that the Spring MVC machinery you already run can produce from one controller method, it has a standardised resume-from-cursor mechanism (`Last-Event-ID`), and it is describable in OpenAPI. WebSocket is a different protocol on the same port that needs an upgrade handshake, a separate Spring module, an application-level resume protocol you design yourself, and AsyncAPI to describe it. The one real cost of SSE in a Java-only client set is tooling: the JDK ships a WebSocket client but no `EventSource`, so each Java client needs a small SSE reader.

## 1. Server side: what Spring says

**Claim:** An SSE endpoint in Spring MVC is a controller method returning `SseEmitter` with `produces=text/event-stream`; `SseEmitter` is a subclass of `ResponseBodyEmitter` and writes events "formatted according to the W3C SSE specification". **Sources:** [Spring Framework 6.2, Asynchronous Requests, "SSE"](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#mvc-ann-async-sse). **Support:** direct evidence. **Confidence:** high.

**Claim:** Async processing must be enabled at the container level, and `configureAsyncSupport` on `WebMvcConfigurer` sets the default timeout and the `AsyncTaskExecutor`; the reference states the default executor "is not suitable for production under load", and that the default timeout "depends on the underlying Servlet container, unless it is set explicitly". Timeouts can also be set per `SseEmitter`. **Sources:** [same page, "Configuration"](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#mvc-ann-async-configuration-spring-mvc). **Support:** direct evidence. **Confidence:** high.

**Claim:** Spring's own docs warn that the Servlet API gives no notification when a client disappears, so an SSE stream must be kept alive with periodic sends, and they point at STOMP/SockJS as the alternative that has "a built-in heartbeat mechanism". **Sources:** [same page, "Disconnects"](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#mvc-ann-async-disconnects). **Support:** direct evidence. **Confidence:** high.

**Claim:** On Java 21 a single `spring.threads.virtual.enabled=true` is the whole configuration for virtual threads; they are daemon threads (so `spring.main.keep-alive` may be needed to keep the JVM up). **Sources:** [Spring Boot 3.5.16 reference, "Virtual threads"](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.virtual-threads). **Support:** direct evidence. **Confidence:** high. Framework-level caveat, verbatim: "individual writes to the response remain blocking (and are performed on a separate thread)" ([6.2 async vs WebFlux](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#mvc-ann-async-vs-webflux)). **Researcher inference:** with virtual threads, one blocked write per idle SSE connection stops being a thread-budget question, which removes the classic reason to reach for WebFlux. **Confidence:** medium (inference, not stated by the source).

**Claim:** Spring's WebSocket support is a separate concern: the protocol "is a different TCP protocol from HTTP", interacts via an HTTP `Upgrade`, and "if a WebSocket server is running behind a web server (for example, nginx), you likely need to configure it to pass WebSocket upgrade requests on". Spring also states directly: "If the volume of messages is relatively low (for example, monitoring network failures) HTTP streaming or polling can provide an effective solution. It is the combination of low latency, high frequency, and high volume that make the best case for the use of WebSocket." **Sources:** [Spring Framework 6.2, WebSocket, "HTTP Versus WebSocket" and "When to Use WebSockets"](https://docs.spring.io/spring-framework/reference/6.2/web/websocket.html). **Support:** direct evidence. **Confidence:** high. This is the framework's own guidance against WebSocket for a low-volume change-cursor feed.

**Claim:** STOMP over WebSocket costs a `@Configuration` class annotated `@EnableWebSocketMessageBroker` implementing `WebSocketMessageBrokerConfigurer`, with `registerStompEndpoints` and `configureMessageBroker` (simple broker on `/topic`, `/queue`). **Sources:** [Spring Framework 6.2, "Enable STOMP"](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/enable.html). **Support:** direct evidence. **Confidence:** high.

**Researcher inference (smaller addition):** SSE adds one annotated method plus a registry of live emitters; WebSocket adds a dependency (`spring-websocket`, plus `spring-messaging` for STOMP), a configuration class, an endpoint/handshake registration, and a broker decision. SSE is the smaller addition to a module that already serves JSON REST. **Confidence:** high (inference from the above).

## 2. Description: OpenAPI, springdoc, AsyncAPI

**Claim:** OpenAPI **3.2.0** (released 2025-09-19) is the version that added this. It defines "sequential media types", lists `text/event-stream` among them, and defines `itemSchema`, which "MUST be applied to each item in the stream independently". For SSE specifically, implementations "MUST work with event data after it has been parsed according to the `text/event-stream` specification", must model `retry` as `type: integer`, and the spec prints a generic per-event schema: `data` (string, required), `event`, `id`, `retry`. JSON-in-`data` is described with `contentMediaType`/`contentSchema`. **Sources:** [OpenAPI Specification v3.2.0, Media Type Object / Sequential Media Types / Special Considerations for Server-Sent Events](https://spec.openapis.org/oas/v3.2.0.html); release note [OAS 3.2.0 Released!](https://github.com/OAI/OpenAPI-Specification/releases/tag/3.2.0) ("Streaming support: sequential media types including SSE"). **Support:** direct evidence. **Confidence:** high.

**Claim:** springdoc-openapi moved its default to OpenAPI 3.1 in **2.8.0 (2025-01-03)** and has no changelog entry for OpenAPI 3.2 or `itemSchema` in either the 2.x line (up to 2.10.1, on the `spring-boot-3` branch) or main (up to 3.2.0, 2026-10-24). A GitHub issue search for `text/event-stream` in the repo returns three unrelated issues and no SSE feature work. **Sources:** [CHANGELOG, spring-boot-3 branch](https://github.com/springdoc/springdoc-openapi/blob/spring-boot-3/CHANGELOG.md); [CHANGELOG, main](https://github.com/springdoc/springdoc-openapi/blob/main/CHANGELOG.md); [issue search](https://api.github.com/search/issues?q=repo%3Aspringdoc%2Fspringdoc-openapi+%22text%2Fevent-stream%22). **Support:** direct evidence of absence, **source interpretation** for the conclusion. **Confidence:** medium. Treat "springdoc will not emit a 3.2 `itemSchema` description" as unproven-but-likely; it is absence of evidence, not a documented "unsupported".

**Researcher inference:** with springdoc 2.x (Boot 3.x) you can still declare the response media type `text/event-stream` with an explicit `@Content(mediaType = "text/event-stream", schema = ...)`; in OAS 3.0/3.1 that schema describes the whole content, so generators see one payload shape rather than a per-event shape. **Confidence:** medium (inference; not verified by generating a client).

**Claim:** AsyncAPI's current spec release is **3.1.0 (2026-01-31)**. **Sources:** [asyncapi/spec latest release](https://api.github.com/repos/asyncapi/spec/releases/latest). **Support:** direct evidence. **Confidence:** high. It is the standard channel/message document format for non-HTTP transports; I did **not** verify the contents of its WebSocket binding in this pass (see Missing evidence).

**Claim:** AsyncAPI's first-party per-language code generator templates are archived. `asyncapi/java-spring-template`, `asyncapi/nodejs-template` and `asyncapi/python-paho-template` now resolve to the `asyncapi-archived-repos` organisation with `archived: true`. The `asyncapi/generator` tool itself is not archived (last push 2026-11-14). **Sources:** [repo metadata](https://api.github.com/repos/asyncapi/java-spring-template), [nodejs-template](https://api.github.com/repos/asyncapi/nodejs-template), [python-paho-template](https://api.github.com/repos/asyncapi/python-paho-template), [generator](https://api.github.com/repos/asyncapi/generator). **Support:** direct evidence. **Confidence:** high for the archived state; **researcher inference** that "generate a WS client from AsyncAPI" is therefore not a supported path today. **Confidence:** medium-high.

## 3. Resumption

**Claim:** The HTML Standard defines the whole mechanism. On reconnect, if the "last event ID string" is not empty, the user agent sets the `Last-Event-ID` request header to it; the `id:` field sets that string (an empty value resets it); `retry:` sets the reconnection time; `Last-Event-ID` is "essentially any UTF-8 encoded string" without NUL/LF/CR. **Sources:** [WHATWG HTML Standard, 9.2 Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html) (sections 9.2.2 processing model, 9.2.4 the `Last-Event-ID` header, 9.2.5 parsing). **Support:** direct evidence. **Confidence:** high.

**Claim:** RFC 6455 gives a WebSocket client nothing equivalent. Its only reconnect guidance is section 7.2.3 "Recovering from Abnormal Closure": clients SHOULD use backoff, first attempt delayed by a random amount ("a value chosen randomly between 0 and 5 seconds is a reasonable initial delay"), then "truncated binary exponential backoff". The RFC defines no cursor, no replay, no sequence number. **Sources:** [RFC 6455 section 7.2.3](https://www.rfc-editor.org/rfc/rfc6455.html#section-7.2.3). **Support:** direct evidence for the backoff text; **absence** for replay. **Confidence:** high that replay is undefined by the spec.

**Researcher inference:** "give me everything after this cursor" is a one-line server-side query for SSE because the cursor arrives as a request header on the reconnect request the server already handles; for WebSocket you must define a subscribe/resume message, keep a buffer of missed events server-side, and handle the race between "replay finished" and "live stream started". That is protocol design of your own, not configuration. **Confidence:** high (inference).

## 4. Operations

**Claim:** Intermediaries buffer by default and time out idle reads. nginx's `proxy_buffering` defaults to `on` (response saved into `proxy_buffer_size`/`proxy_buffers`); the `X-Accel-Buffering` response header enables/disables buffering per response; `proxy_read_timeout` defaults to `60s` and is "set only between two successive read operations... If the proxied server does not transmit anything within this time, the connection is closed." **Sources:** [nginx `ngx_http_proxy_module`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering). **Support:** direct evidence. **Confidence:** high. Consequence: an SSE stream needs a heartbeat shorter than the idle timeout (Spring's Disconnects section already requires one) and buffering disabled.

**Claim:** No primary HTTP source sets a per-host connection limit. RFC 9112 says only that "Most clients maintain multiple connections in parallel, including more than one connection per server endpoint", and that servers are "designed to maintain thousands of concurrent connections". **Sources:** [RFC 9112 section 9](https://www.rfc-editor.org/rfc/rfc9112.html#section-9). **Support:** direct evidence. **Confidence:** high. The familiar "6 per host" number is browser policy, not the RFC; do not cite it as a protocol limit.

**Claim:** HTTP/2 and HTTP/3 remove the connection-count question. RFC 9113: "A single HTTP/2 connection can contain multiple concurrently open streams, with either endpoint interleaving frames from multiple streams." RFC 9114: HTTP/1.1 "does not include a multiplexing layer" so "multiple TCP connections are often used to service requests in parallel", and notes HTTP/2's multiplexing is invisible to TCP loss recovery, so a lost packet stalls all streams. **Sources:** [RFC 9113 section 5](https://www.rfc-editor.org/rfc/rfc9113.html#section-5); [RFC 9114 section 1.1](https://www.rfc-editor.org/rfc/rfc9114.html#section-1.1). **Support:** direct evidence. **Confidence:** high.

**Claim:** WebSocket's operational risk is the upgrade path and idle culling, stated by both sides. RFC 6455 designs for "proxies and intermediaries" but admits "some complexity specific to the current environment", and notes that connections on port 443 were "significantly more likely to succeed". Spring adds: "restrictive proxies that are outside of your control may preclude WebSocket interactions, either because they are not configured to pass on the `Upgrade` header or because they close long-lived connections that appear idle." **Sources:** [RFC 6455 sections 1.1 and 1.8](https://www.rfc-editor.org/rfc/rfc6455.html#section-1.1); [Spring Framework 6.2 WebSocket, "When to Use WebSockets"](https://docs.spring.io/spring-framework/reference/6.2/web/websocket.html#websocket-when-to-use). **Support:** direct evidence. **Confidence:** high.

**Researcher inference (proxy and sleeping laptop):** both protocols survive a sleep/wake cycle only by reconnecting, and both need a heartbeat to survive idle timeouts (SSE: a comment event; WebSocket: RFC 6455 Ping/Pong, section 5.5.2). SSE wins on proxies because it is an ordinary HTTP response, so no upgrade traversal is needed and standard auth/observability apply; WebSocket wins in the narrow case where a middlebox buffers or truncates streamed HTTP bodies but passes `Upgrade` on 443. **Confidence:** medium (inference).

**Claim (important nuance):** "reconnects automatically" is a property of the browser `EventSource` API, not of the wire format. **Sources:** [HTML Standard 9.2.2 processing model](https://html.spec.whatwg.org/multipage/server-sent-events.html#processing-model) defines reconnection for user agents. **Support:** source interpretation. **Confidence:** high. A JavaFX or CLI consumer must implement reconnect plus `Last-Event-ID` itself; that is a few dozen lines, but it is code either way.

## 5. Direction

**Claim:** SSE is unidirectional at the API level: the `EventSource` IDL exposes a constructor, `url`, `withCredentials`, the three ready-state constants, `readyState`, `onopen`/`onmessage`/`onerror`, and `close()` — no method that sends data. **Sources:** [HTML Standard, "The EventSource interface"](https://html.spec.whatwg.org/multipage/server-sent-events.html#the-eventsource-interface). **Support:** direct evidence (IDL inspected; absence of a send method). **Confidence:** high. The only client-to-server signal beyond the initial request is the `Last-Event-ID` header on reconnect.

**Claim:** WebSocket is bidirectional by definition: "a two-way communication channel where each side can, independently from the other, send data at will". **Sources:** [RFC 6455 section 1.2](https://www.rfc-editor.org/rfc/rfc6455.html#section-1.2). **Support:** direct evidence. **Confidence:** high.

**Researcher inference:** the project's rule that all state changes are REST matches SSE exactly. A WebSocket channel would be used one-way in practice while still carrying a full-duplex capability, its own framing, and its own auth story. **Confidence:** high.

## 6. Consequences for this module

SSE, concretely: one `@GetMapping(path="/events", produces=MediaType.TEXT_EVENT_STREAM_VALUE)` returning `SseEmitter`; hold the emitters in a small registry; set a timeout (`new SseEmitter(timeout)` or `configureAsyncSupport`); send a comment-only heartbeat on a scheduler so disconnects are detected (Spring: "it is important to send data periodically, since the write fails if the client has disconnected"); on `IOException` do **not** call `complete`/`completeWithError` — the container notifies and Spring MVC runs the exception resolvers ([SseEmitter / Object streaming notes](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#mvc-ann-async-objects)). With `spring.threads.virtual.enabled=true` the blocked writes cost virtual threads, not platform threads ([Boot 3.5 "Virtual threads"](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.virtual-threads)).

WebSocket, concretely: `spring-websocket` dependency; either a `WebSocketHandler` plus handshake configuration, or `@EnableWebSocketMessageBroker` with `registerStompEndpoints`/`configureMessageBroker` and a broker ([Enable STOMP](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/enable.html)); plus a subscribe/resume message you design, because RFC 6455 provides none.

Tooling asymmetry worth stating plainly: the JDK 21 `java.net.http` package provides `HttpClient`, `HttpRequest`, `HttpResponse` and a `WebSocket` client, and no `EventSource` ([JDK 21 java.net.http package summary](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/package-summary.html)). So the JavaFX/tray/CLI clients get a WebSocket client for free and must hand-roll SSE parsing. That is the strongest argument for WebSocket in this codebase, and it is a small, bounded, testable piece of client code.

## Comparison

| Question | SSE | WebSocket |
|---|---|---|
| 1. Server side | One `SseEmitter` method + emitter registry; async already there; heartbeat needed ([ref](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html#mvc-ann-async-sse)) | New module, handshake config, broker if STOMP ([ref](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/enable.html)); Spring itself says low volume favours HTTP streaming ([ref](https://docs.spring.io/spring-framework/reference/6.2/web/websocket.html#websocket-when-to-use)) |
| 2. Description | OAS 3.2 `itemSchema` per event ([ref](https://spec.openapis.org/oas/v3.2.0.html)); springdoc emits 3.1, no 3.2 evidence | AsyncAPI 3.1.0 exists ([ref](https://api.github.com/repos/asyncapi/spec/releases/latest)) but first-party codegen templates are archived ([ref](https://api.github.com/repos/asyncapi/java-spring-template)) |
| 3. Resumption | `Last-Event-ID` + `id:` + `retry:` built into the spec ([ref](https://html.spec.whatwg.org/multipage/server-sent-events.html)) | Nothing; only reconnect-with-backoff ([RFC 6455 s7.2.3](https://www.rfc-editor.org/rfc/rfc6455.html#section-7.2.3)) |
| 4. Operations | Plain HTTP; needs heartbeat + buffering off ([nginx](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering)); h2/h3 multiplexing helps ([RFC 9113 s5](https://www.rfc-editor.org/rfc/rfc9113.html#section-5)) | Upgrade may be blocked or idle-culled ([Spring](https://docs.spring.io/spring-framework/reference/6.2/web/websocket.html#websocket-when-to-use)); 443 more reliable ([RFC 6455 s1.8](https://www.rfc-editor.org/rfc/rfc6455.html#section-1.8)) |
| 5. Direction | Server to client only; `EventSource` has no send ([ref](https://html.spec.whatwg.org/multipage/server-sent-events.html#the-eventsource-interface)) | Bidirectional by design ([RFC 6455 s1.2](https://www.rfc-editor.org/rfc/rfc6455.html#section-1.2)) |
| 6. Cost here | One endpoint, one registry, heartbeat, per-client parser | Dependency, config class, broker, custom resume protocol, JDK client for free |

## Recommendation

Use **SSE over the existing HTTP API**, on the same port and the same OpenAPI document, with `id:` carrying the change cursor, and treat "resume" as `GET /events` plus the `Last-Event-ID` header. Document the endpoint in OAS as `text/event-stream` with an explicit event schema now, and move to `itemSchema` when the springdoc line you use emits OpenAPI 3.2. Keep the MCP stdio surface and REST mutations untouched; SSE carries only the cursor and the notice.

This recommendation is wrong if any of these hold:

- A consumer must send high-frequency messages client-to-server over the same channel. Today every mutation is REST, so this is the case that would flip it.
- You must support a consumer that only speaks WebSocket, typically a browser-hosted plugin whose network path mangles streamed HTTP bodies. A middlebox that buffers or truncates `text/event-stream` but passes `Upgrade` on 443 defeats SSE and not WebSocket.
- You need binary frames, sub-100 ms round trips, or per-message flow control.
- Your consumers proliferate into browser tabs on HTTP/1.1, where SSE streams compete for the browser's per-host connection budget. Not your current shape: a handful of JVM clients, mostly on loopback.
- The Java client parser turns out to be more than a day's work for the desktop client, or you cannot accept a hand-written reconnect path in three clients.

## Contradictions

- **"SSE reconnects automatically" vs a Java-only client set.** True for browsers ([HTML Standard](https://html.spec.whatwg.org/multipage/server-sent-events.html#processing-model)), false as a wire-protocol guarantee. Both statements appear in the sources; the browser behaviour does not transfer to JavaFX.
- **Spring's Disconnects section points at STOMP/SockJS for heartbeats**, i.e. it recommends the heavier option for a problem a comment-line heartbeat solves. Recorded, not resolved; the recommendation above follows the lighter path and adds the heartbeat.
- **"No per-host connection limit" vs common operational folklore.** RFC 9112 sets no number; blog-level advice usually quotes browser policy. Treated as folklore, not evidence.

## Missing evidence

- No primary source was found stating that springdoc-openapi 2.x *cannot* emit a `text/event-stream` response or an OAS 3.2 `itemSchema`; the claim rests on changelog absence. Verify by generating a spec from a test controller before relying on it.
- AsyncAPI 3.1.0's WebSocket binding contents were not read in this pass; only the spec release and the archived generator templates were verified.
- The claim that generated clients treat an OAS 3.0/3.1 `text/event-stream` schema as a single payload was not tested against a generator. It is inference.
- No measurement of SSE behaviour through this project's actual proxies. The nginx defaults are general, not specific to your deployment.
- Whether a given JavaFX SSE library is maintained was not investigated.

## Sources

- [Spring Framework 6.2.19, Asynchronous Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html) — SseEmitter, ResponseBodyEmitter, timeouts, disconnects, async config.
- [Spring Framework 6.2.19, WebSocket](https://docs.spring.io/spring-framework/reference/6.2/web/websocket.html) — upgrade handshake, proxy caveats, "when to use WebSockets".
- [Spring Framework 6.2.19, Enable STOMP](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/enable.html) — the configuration class a STOMP endpoint costs.
- [Spring Boot 3.5.16 reference, Virtual threads](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.virtual-threads) — `spring.threads.virtual.enabled`, daemon-thread caveat.
- [OpenAPI Specification v3.2.0](https://spec.openapis.org/oas/v3.2.0.html) and [OAS 3.2.0 release](https://github.com/OAI/OpenAPI-Specification/releases/tag/3.2.0) — sequential media types, `itemSchema`, SSE considerations.
- [springdoc-openapi CHANGELOG (spring-boot-3)](https://github.com/springdoc/springdoc-openapi/blob/spring-boot-3/CHANGELOG.md), [(main)](https://github.com/springdoc/springdoc-openapi/blob/main/CHANGELOG.md) — OAS 3.1 default since 2.8.0; no 3.2 entry.
- [WHATWG HTML Standard, Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html) — `Last-Event-ID`, `id:`, `retry:`, `EventSource` IDL, reconnection.
- [RFC 6455, The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455.html) — bidirectionality, Upgrade, proxy caveats, abnormal-closure recovery, Ping/Pong.
- [RFC 9112](https://www.rfc-editor.org/rfc/rfc9112.html), [RFC 9113](https://www.rfc-editor.org/rfc/rfc9113.html), [RFC 9114](https://www.rfc-editor.org/rfc/rfc9114.html) — connection handling, multiplexing, HTTP/3 framing over QUIC.
- [nginx `ngx_http_proxy_module`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html) — `proxy_buffering` default `on`, `proxy_read_timeout` default `60s`, `X-Accel-Buffering`.
- [AsyncAPI spec latest release](https://api.github.com/repos/asyncapi/spec/releases/latest) and [generator template metadata](https://api.github.com/repos/asyncapi/java-spring-template) — 3.1.0 current; templates archived.
- [JDK 21 `java.net.http` package summary](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/package-summary.html) — WebSocket client present, no EventSource.
- Rejected: vendor blogs and Medium posts on SSE vs WebSocket (non-primary, redundant with the specs); Stack Overflow answers on springdoc version pinning (superseded by the changelog).

## Next steps

1. Generate `/v3/api-docs` from a throwaway SSE controller and confirm how the `text/event-stream` content is rendered by the springdoc version you pin.
2. Time-box a spike: 100-line JavaFX `EventSource` reader (line parser, `Last-Event-ID`, backoff) against the real daemon.
3. Confirm what, if anything, sits between client and daemon in split mode, and whether it buffers `text/event-stream`.
