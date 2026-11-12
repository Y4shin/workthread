---
type: task
subtype: research
title: Java stack for a standalone and split-mode planner with an MCP interface
status: stable
workflow_state: ready
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
