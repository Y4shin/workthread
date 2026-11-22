---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for scaffolding
status: stable
workflow_state: ready
blocked_by: []
---

Settle this effort's non-goals and non-negotiable facts, including its
effort-level success test. Nothing else: the scope, the requirement list, and
the dependency position are already recorded below and come from the parent
effort, which settled them.

### The raw request, verbatim

The human typed:

    /skill:intake 01-scaffolding

That stands for the scope the parent effort settled for this child, quoted from
its map's `## Child efforts` section:

> Build files, the eight module skeletons, CI, the test harness, the `jlink`
> image build, and the version-tagging and additivity rule. It also ships one
> empty JavaFX window packaged as a `jlink` image, because the module-path rule
> is enforced by the build and is cheapest to break on the first commit.

### Destination

A repository where `./gradlew build` passes, the build refuses JavaFX on the
classpath, and an empty window ships as a `jlink` image. Nothing else: this
effort proves the shape of the build and the packaging, and implements no
product behaviour.

### Actor

The developer who builds every later child effort on this foundation.

### Classification

Feature.

### Stories

1. As a developer picking up a clean checkout, I want `./gradlew build` to
   pass, so that every later effort starts from something that compiles.
2. As a developer, I want the build to enforce the dependency rules, so that
   the core cannot quietly acquire Spring or JavaFX and break standalone mode.
3. As the person who ships, I want one command to produce a `jlink` image with
   an empty window, so that packaging is proven before any feature depends on
   it.
4. As a maintainer, I want version numbering and the additivity rule in place
   from the first release, so that no later version removes an interface an
   earlier one shipped.
5. As a test author, I want JUnit, AssertJ, and a headless JavaFX smoke path
   wired, so that tests exist from the first commit rather than being retrofitted.

### Constraints

- Java 21 LTS, pinned by toolchain. Gradle 9.9.1, Kotlin DSL, per-module
  toolchains.
- Eight modules: `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`,
  `client`. Dependencies point inward, and `core` sees only `contract`.
- JavaFX 21 LTS on the module path, enforced by the build, because the runtime
  only warns when it is on the classpath. Java 21 caps the usable line at
  23.0.2.
- Packaging is `org.beryx.jlink` with `--strip-debug --compress zip-6`. The
  image still needs the host's GTK3 and X11 libraries.
- The MCP SDK is `io.modelcontextprotocol.sdk:mcp` 2.1.0, where the `mcp`
  artifact is an aggregator over `mcp-core` and `mcp-json-jackson3`.
- The repository has no build files and no Java source yet; that was checked
  at intake.
- This effort carries no product behaviour, no record, and no use case. It also
  does not decide what any surface command is called.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section, because the parent effort's
closing ticket checks for it and because it is this effort's definition of done.
It holds two things: the requirement ranges this effort owes, and the rule that
every row is verified one by one before this effort may close.

The ranges, from the parent's architecture document under `## Contracts per
child effort`:

    T01-022, T03-001, T03-003, T03-004, T03-012, T04-001..T04-004, T04-007,
    T04-009, T04-013, T04-015..T04-018, T06-001..T06-004, T06-008, T06-012,
    T06-013, T07-004, T07-008..T07-010, T09-004, T09-007, T11-001..T11-003,
    T11-007, T11-018..T11-020, T11-032, T11-035, T11-036, T11-038, T11-039,
    T11-044..T11-049, T11-073, T11-079..T11-081, T11-088, T11-090, T17-035,
    T17-036, T21-005, T22-002..T22-004, T22-006, T22-008, T22-009, T23-008,
    T24-020, T25-041, MAP-007, MAP-014, MAP-016, MAP-019, MAP-025..MAP-027,
    MAP-029, MAP-C04, MAP-C07

That is 73 rows. Each one names a source file and line in
`docs/v1/requirements-directory.csv`, so it can be read rather than guessed.

### Position and release, already settled

First in the build order, blocked by nothing, release 0.1 Capture. Its
successor is `02-work-store`. The full order is the table under `## Effort
order` in the parent's architecture document.
