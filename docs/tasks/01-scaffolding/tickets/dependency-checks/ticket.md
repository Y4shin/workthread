---
type: ticket
subtype: feature
title: Enforce the dependency rules in the build
status: stable
workflow_state: ready
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
