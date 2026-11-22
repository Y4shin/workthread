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
