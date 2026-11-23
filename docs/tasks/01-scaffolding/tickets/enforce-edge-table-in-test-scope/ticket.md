---
type: ticket
subtype: feature
title: Enforce the edge table in test scope
status: stable
workflow_state: ready
blocked_by: []
size: s
---

## What to build

`verifyModuleDependencies` reads each module's main `compileClasspath` and `runtimeClasspath` hierarchies only, so a module edge declared as `testImplementation(project(":cli"))` in `core` passes both dependency checks. It was verified during `dependency-checks`: the line was added, both checks stayed green, and the line was removed again. Extend the rule so the same edge-table refusal covers test scope.

The rule should read the test source sets' own classpaths, in the same `afterEvaluate` read and with the same message shape: the module, the dependency, and the row that forbids it. Keep the existing scope decisions: only project dependencies are judged against a row, external libraries are judged in `core` alone for Spring and JavaFX, and platform declarations are filtered.

Fixtures go through the existing `FixtureChecks` family. Each new refusal needs a case in the same plain-data shape, and `verifyDependencyRuleFixtures` must hold the new count.

## Acceptance criteria

- [ ] A project dependency outside a module's edge row, declared as `testImplementation`, fails `check`, and the message names the module, the dependency, and the row.
- [ ] `./gradlew build` stays green with every check enabled, and the configuration cache is reused.
- [ ] A fixture proves the test-scope refusal, and the fixture runs as part of the suite.
- [ ] The main-scope refusals keep their behaviour and their fixtures.
- [ ] No third-party rule plugin and no build-logic project is added.

## Blocked by

None. Every ticket of the effort that this one depends on has landed.
