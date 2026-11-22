---
type: ticket
subtype: feature
title: Declare the real stack and prove it resolves
status: stable
workflow_state: ready
blocked_by:
- walking-skeleton
size: m
---

## What to build

Each module declares its real dependency set at the pinned versions, and the whole set resolves: JavaFX in the client, Spring Boot as a platform in the API module, the MCP SDK aggregator, the SQLite driver, and the test libraries. The resolved jars' module names are printed once and recorded, so the descriptor work in the next ticket starts from confirmed names.

## Acceptance criteria

- [ ] JavaFX 21.0.13 resolves for the client through the JavaFX plugin, with the host platform classifier.
- [ ] Spring Boot 4.2.0 is applied as a dependency platform in the API module only, and no Spring Boot plugin and no dependency-management plugin is added.
- [ ] The MCP SDK 2.1.0 aggregator resolves in the MCP module with its Jackson and schema-validator closure.
- [ ] sqlite-jdbc 3.54.1.0 resolves in the adapters module.
- [ ] JUnit 5.14.4 and AssertJ 3.27.7 resolve in every module, and the API module's test classpath is not silently on JUnit 6.1.3.
- [ ] One recorded command prints the resolved module names of the pinned jars, they match the dependency notes, and the result is recorded in this ticket's implementation notes.
- [ ] `./gradlew build` stays green.

## Blocked by

- `walking-skeleton`
