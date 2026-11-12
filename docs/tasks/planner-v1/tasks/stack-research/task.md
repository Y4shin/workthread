---
type: task
subtype: research
title: Java stack for a standalone and split-mode planner with an MCP interface
status: stable
workflow_state: done
blocked_by:
- spec-additions
---

## Precise question

Which concrete Java stack satisfies all of the following at once, and what is
the evidence for each pick?

1. A core module that holds the work logic and does not depend on Spring.
2. A Spring Boot API module that carries the same calls between daemon and
   client in split mode.
3. A JavaFX client module.
4. A local MCP server interface for agents, available in both run modes.
5. A test stack that runs without a display where possible.
6. A multi-module build that can produce both run modes from one source tree.

Sub-questions to answer with evidence:

- Which MCP server implementation for Java is current, maintained, and
  usable from a plain process and from inside a Spring Boot application?
  Name the artifact, the version, the license, and the maintenance status.
- Can JavaFX and Spring Boot share one JVM cleanly in standalone mode, or does
  the shared-process shape need a different arrangement? What do the
  frameworks' own documents say about it?
- Maven or Gradle for a multi-module Java 21 project with a JavaFX module?
  Judge on multi-module ergonomics, JavaFX packaging tooling, and CI cost.
- What headless test setup exists for JavaFX (Monocle or otherwise), and what
  does it cost in configuration?
- Which embedded persistence options suit a single-writer local service, and
  what do their own documents say about concurrent readers and file locking?

## Decision it unblocks

`decisions-service-mode`, `decisions-data-lifecycle`, `decisions-agent-interface`,
and `child-efforts`, plus the build tool and test stack that this effort must
settle as a non-negotiable fact.

## Trusted source boundaries

Primary sources only: the MCP specification and its Java implementation
repository, Spring Boot reference documentation, OpenJFX documentation,
TestFX and Monocle documentation, Maven and Gradle documentation, and the
documentation of any candidate persistence library. Repository paths for
anything already recorded in `docs/testing.md` or `CONTEXT.md`. No blog posts
as load-bearing evidence; they may point at a primary source but do not
replace it.

## Evidence required for completion

A recommendation per sub-question, each with a citation and a confidence
level, distinguishing fact from assumption. A named stack list: Java version,
build tool, module layout, MCP server library, persistence option, test
libraries. A short list of what would have to change if a pick proves wrong.
Findings captured at `docs/tasks/planner-v1/tasks/stack-research/findings.md`
with the `type: findings` frontmatter, and a short write-back to the map.

## Likely dependent tasks

The three architecture-facing grillings and `child-efforts`. If the research
finds a precise requirement that no task covers, record it as discovered work
instead of widening this task.

## Notes

### Findings

Full evidence, every source URL, the passage relied on, and the contradiction
list: `docs/research/stack.md`. The record below is the decision-grade digest.

**Method.** Primary sources only: the MCP Java SDK repository, Maven Central
metadata, Spring AI and Spring Boot reference documentation, OpenJFX javadoc
and release notes, Gradle and Maven documentation, SQLite, H2, and RocksDB
documentation. Each claim is marked fact, inference, or assumption in the full
file. No claim rests on a search summary.

**Recommendation.**

| Slot | Pick |
| --- | --- |
| Java | 21 LTS, pinned by toolchain |
| Build tool | Gradle 9.9.1, Kotlin DSL, per-module toolchains |
| Modules | `core`, `mcp-adapter`, `daemon`, `api`, `apiclient`, `client`, `cli`, `standalone`, `testkit` |
| MCP library | `io.modelcontextprotocol.sdk:mcp` 2.1.0 (MIT), `StdioServerTransportProvider` |
| Spring | Spring Boot 4.x in `api` and `daemon` only, no JavaFX in that JVM |
| Persistence | SQLite via `org.xerial:sqlite-jdbc` 3.54.1.0, WAL, `busy_timeout`, one writer thread |
| Tests | JUnit 5 and AssertJ for core; JavaFX headless platform for a thin UI smoke layer |
| Packaging | `jlink` or `jpackage` image, JavaFX always on the module path |

**The load-bearing facts.**

- MCP Java SDK: `io.modelcontextprotocol.sdk:mcp`, MIT, Java 17+, versions
  2.0.0, 2.0.1, 2.1.0 stable with 2.2.0 milestones. The core module ships
  STDIO, SSE, and Streamable HTTP server transports with no web framework.
  Spring-specific transports moved to Spring AI 2.0+ (`spring-ai-starter-mcp-server`).
  Registry `<latest>` is a milestone, so pin 2.1.0.
- JavaFX must load from named modules on the module path; classpath loading is
  documented as unsupported. Consequence: a Spring Boot executable jar cannot
  host JavaFX. `Platform.startup` is the supported way to start the toolkit from
  a non-JavaFX main, and `Application.launch` may only be called once and not
  from the FX thread.
- Gradle's multi-project model is documented as the structure for work
  avoidance and parallel builds; its JavaFX plugin is variant-aware and current.
  Maven's official JavaFX plugin is at 0.0.8 from October 2021, and Maven 4 is
  still a release candidate.
- Headless JavaFX: JavaFX 26 release notes add a headless Glass platform
  selected with `-Dglass.platform=headless`, with no extra artifact. JavaFX 25
  has neither. TestFX and its Monocle artifact have had no release since
  February 2024 and stop at JavaFX 21.
- SQLite WAL: readers do not block the writer and the writer does not block
  readers, with exactly one writer at a time, and it does not work over a
  network filesystem. `busy_timeout` and `locking_mode` are the documented
  controls. H2 embedded allows one virtual machine at a time with a lock file.
  RocksDB cannot be written from more than one process.

**Consequences to record as non-negotiables.**

- `core` declares no Spring and no JavaFX dependency. Enforce it with a build
  check, or standalone mode drifts into needing Spring.
- Both run modes assemble from the same modules: standalone takes `core` plus
  `mcp-adapter` plus `client`; split takes `core` plus `mcp-adapter` plus `api`
  plus `daemon`, with `client` talking through `apiclient`.
- A Spring Boot fat jar is not a supported JavaFX host, so packaging is an
  image, not a single jar.

**What would change if a pick proves wrong.** The MCP version can be pinned and
isolated behind `mcp-adapter`. Spring AI can be added to the daemon modules
alone. The JavaFX packaging constraint is the highest-cost reversal, which is
why it is treated as a constraint. Gradle's packaging path can be replaced by
`jpackage` driven from a task, without changing the module layout. SQLite can
fall back to H2 at the cost of a migration and losing file inspectability.

**Thin evidence, stated plainly.**

- The minimum JDK for JavaFX 26 is not documented on any page verified, so
  "JavaFX 26 for the headless platform" may conflict with "Java 21".
- Whether Monocle still works against JavaFX 21.0.13 is untested.
- Tray icon: not researched. JavaFX has no system tray API; the options are the
  AWT tray or a third-party library. Nothing in the current plan covers it.
- CLI argument parsing: not researched.
- No measured CI cost comparison between Maven and Gradle.
- No spike proving a JavaFX client and a Spring Boot daemon can share the same
  DTO jar without module-path conflicts.

**Contradictions found.** The SDK repository changelog is stale against Maven
Central. Central marks a milestone as `<latest>`. The OpenJFX javadoc forbids
classpath loading while the OpenJFX Maven plugin documents a classpath launch.
TestFX still presents Monocle as the headless route while JavaFX 26 ships its
own.
