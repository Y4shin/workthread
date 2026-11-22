---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for scaffolding
status: stable
workflow_state: done
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

## Notes

### Settled: non-goals

- **Any product behaviour.** No domain model, no store, no use case, no command, no daemon. The module skeletons hold only what the build and the packaging need.
- **Automated publishing.** No release job, no artifact upload to a registry. The version rule is documented and enforced by the build, not by publishing.

Installers (deb, rpm, MSI) were not made a non-goal, but the effort ships none: the deliverable is the `jlink` runtime image. CI is in scope. The section deliberately does not rule either out, so a later effort may add installers without re-opening this decision.

### Settled: the success test

> `./gradlew build` passes on a clean checkout with Java 21; the build fails when `core` declares Spring or JavaFX, or when a JavaFX artifact reaches the classpath; and the jlink image's launcher opens one empty window.

The launcher half is verified by hand once on each platform, not in CI (see below).

### Settled: the frozen facts

- **Eight names, inward rule.** `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client` stay, and a module never depends outward. The architecture's `May depend on` table is the authoritative edge list the build check reads. A new module needs an architecture change before it can appear.
- **Toolchain.** Java 21 toolchain per module, Gradle 9.9.1 through the committed wrapper, Kotlin DSL build files.
- **JavaFX on the module path only.** The build refuses a JavaFX artifact on the classpath. The image comes from `org.beryx.jlink` with `--strip-debug --compress zip-6`.

The module-rule options were put a second time because the first round omitted them. The operator then froze the names, the inward rule, the edge table, and the no-new-module rule. The `Named JPMS modules` option was not chosen, and the JPMS question was settled separately below.

### Settled: build shape

- **Seven named JPMS modules.** `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `client` carry `module-info.java`. `api` stays on the classpath because Spring Boot's classpath model fights named modules. `client` and its inward modules are therefore named, which is what `jlink` needs.
- **The real stack is declared now.** Spring Boot in `api`, the MCP SDK 2.1.0 aggregator in `mcp`, sqlite-jdbc in `adapters`, JavaFX in `client`, JUnit 5 and AssertJ everywhere. Dependency resolution and JPMS friction must surface on the first commit, not in a later effort.
- **Root-build checks.** Plain Kotlin in the root build, attached to `check`. One check fails when `core` declares Spring or JavaFX; one fails when a JavaFX artifact appears on a runtime classpath. Messages name the offending dependency. No third-party rule plugin.
- **Package root.** `io.github.y4shin.workthread`, one subpackage per module.
- **Version rule.** `0.1.0` in `gradle.properties`, an annotated git tag per release, and a committed snapshot of every exported package of `contract`. `check` fails when an exported type or member disappears or changes shape. A committed fixture proves the failure path, because 0.1 exports almost nothing.
- **The window.** Title `workthread`, 1024x768, one centred label reading `workthread <version>`.

### Settled: pipeline and image proof

- GitHub Actions on ubuntu, macOS, and windows. Each job runs `./gradlew build` and builds the `jlink` image.
- CI does not launch the image. The operator's reasoning: whether a window opens is stable once it works, and a launch step costs more than it catches. The launch is verified by hand once on each platform and recorded in `docs/dev-env.md`. A CI launch step may be added later only if it stays cheap to write and run.
- No `--self-test` flag, no TestFX harness, and no start-and-check step in the pipeline for 0.1.

### Rejected alternatives

- **All eight subprojects named modules.** Spring Boot as automatic modules is unverified and fights the framework. Rejected.
- **Only the image path named (`client`, `wiring`, `contract`, `core`).** `cli`, `mcp`, and `adapters` gain nothing and the mixed layout complicates the checks. Rejected.
- **JavaFX and test libraries only; the rest of the stack later.** Deferred resolution moves build friction into the effort that can least afford it. Rejected.
- **Self-test flag, TestFX in-process test, or start-and-check in CI.** All cost application code or pipeline time for a result the operator will verify by hand once. Rejected.
- **Additivity check against the previous release jar.** There is no previous release at 0.1. Rejected for the contract snapshot plus fixture.
- **Snapshot every module's exports.** More surface to police for no gain at 0.1. Rejected for `contract` only.
- **Third-party dependency-rule plugin, JUnit test over module descriptors, review checklist.** Rejected for the root-build checks.
- **Defer versioning, or tag and version without a build check.** The parent scope requires the additivity rule from the first release. Rejected.
- **Bare or full-screen window.** The version label is evidence that the image was stamped by this build. Rejected.

### Facts found while grilling, with sources

- `org.beryx.jlink` 2.26.0, the version the packaging prototype used, does not run on Gradle 9. Release note v3.1.2 (2025-08-03) "adds Gradle 9 compatibility"; v4.0.0 (2026-03-28) requires Gradle 8.14.4 or newer and supports Java 17 to 26. The latest release is 4.2.0 (2026-10-30) per `https://plugins.gradle.org/m2/org/beryx/jlink/org.beryx.jlink.gradle.plugin/maven-metadata.xml`. The plugin version must therefore move to 4.2.0, and the prototype's 66 MB result must be re-checked on the new line.
- Gradle 9.9.1 exists; 9.9.2 is current (`https://services.gradle.org/versions/all`). The wrapper stays on 9.9.1 as settled.
- Spring Boot 4.2.0 requires Java 17 or newer and Gradle 8.14+ or 9.x (`https://docs.spring.io/spring-boot/system-requirements.html`). It fits Java 21 and Gradle 9.9.1.
- Maven Central current releases: JavaFX 21 line 21.0.13, sqlite-jdbc 3.54.1.0, JUnit 5 line 5.14.4 (JUnit 6.1.3 exists), AssertJ 3.27.7, MCP SDK 2.2.0 (the settled constraint pins 2.1.0).

### New work this created

- `packaging-stack`: prototype the packaging stack on the new plugin line, because the recorded evidence was produced on 2.26.0 and Gradle 8.
- `ci-shape`: research the three-platform runner facts.
- `dependency-pins`: research the pinned library versions and each jar's JPMS status.

No fog remains: every open question is precise enough to be one of those three tasks.
