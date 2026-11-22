---
type: ticket
subtype: feature
title: Make the seven modules named Java modules
status: stable
workflow_state: ready
blocked_by:
- declared-stack
size: m
---

## What to build

The seven modules that reach the image are named Java modules with the inward `requires` clauses and their exports, and the API module stays on the classpath. Each built jar reports its module name, and no descriptor requires anything outside its edge row.

## Acceptance criteria

- [ ] All seven named modules build with a module descriptor; the API module has none.
- [ ] Each descriptor requires only modules its edge row allows, and exports its public package.
- [ ] The client exports the package that holds its application class.
- [ ] Each built jar reports the expected module name.
- [ ] `./gradlew build` stays green and the configuration cache is reused.

## Blocked by

- `declared-stack`

## Implementation notes

Module names follow the package: `io.github.y4shin.workthread.<module>` for `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, and `client`, derived once from `packageRoot` in the root build. `api` carries no descriptor and stays on the classpath. Each placeholder is `<Module>Placeholder` except the client's, which is `WorkthreadApplication`; the `client-image` ticket replaces it in place with the JavaFX `Application` subclass.

`verifyModuleDescriptor` runs per module on `check` and refuses a missing descriptor, an automatic descriptor, a wrong module name, a `requires` outside the edge row, a missing `requires` for a row edge, a missing export, a descriptor on `api`, and for `client` a missing application class. The `requires` rule is checked in both directions but only for modules under the package root, so a JDK or declared-stack module in `requires` is not judged by the edge table. The rule has no committed fixture yet, so its non-vacuity rests on the probes recorded in the deviation report.
