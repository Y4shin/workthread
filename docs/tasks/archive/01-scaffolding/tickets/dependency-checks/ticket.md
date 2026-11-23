---
type: ticket
subtype: feature
title: Enforce the dependency rules in the build
status: deprecated
workflow_state: done
blocked_by:
- declared-stack
size: m
---

## What to build

The build refuses a dependency that leaves the edge table, Spring or JavaFX in the core, and a JavaFX artifact on a runtime classpath. Each refusal names what is wrong, is attached to `check`, and is proven by a fixture, so the failure path is behaviour rather than a hope.

## Acceptance criteria

- [ ] A module declaring a dependency outside its edge row fails `check`, and the message names the module and the dependency.
- [ ] `core` declaring Spring or JavaFX fails `check`, and the message names the dependency.
- [ ] A JavaFX artifact on a runtime classpath fails `check`, and the message names the configuration and the artifact.
- [ ] A fixture proves each failure, and the fixture runs as part of the suite.
- [ ] The real build stays green with all checks enabled.
- [ ] No third-party rule plugin is added.

## Blocked by

- `declared-stack`

## Implementation notes

The three task names are `verifyModuleDependencies` and `verifyNoJavaFxOnRuntimeClasspath`, each per module and attached to that module's `check`, and `verifyDependencyRuleFixtures` at the root, attached to the root `check`. `moduleRowsCarryingJavaFx = setOf("client")`: the runtime classpath check refuses JavaFX everywhere except the client, because the declared stack puts JavaFX there and the literal rule would fail the real build. The architecture's gates paragraph now states the same scope.

The edge rule compares project dependencies only; external libraries are judged in `core` alone, for the Spring and JavaFX families, and platform declarations are filtered. The edge table is a data copy in the root build (`moduleEdgeRows`), so it must be updated with the architecture. A subproject with no row fails at configuration time, before any task runs. Test scope is not read at all: `core` with `testImplementation(project(":cli"))` stays green, which is a gap the edge table does not cover yet.
