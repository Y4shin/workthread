---
type: ticket
subtype: feature
title: Enforce the edge table in test scope
status: stable
workflow_state: done
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

## Implementation notes

Both source sets are read. `verifyModuleDependencies` gathers the `main` classpath hierarchies and now gathers the `test` source set's `testCompileClasspath` and `testRuntimeClasspath` in the same `afterEvaluate` block, through the shared helpers `declaredDependenciesOf`, `projectEdgesIn` and `libraryCoordinatesIn`, and the rule body moved into `refusalsFor` so one implementation serves both scopes. Test scope is read as the delta against main, because `testImplementation` extends `implementation`: a main-scope violation is reported once rather than twice, and an inherited edge is not re-judged. The message is unchanged in both scopes, `<module> declares a dependency on <dependency>, which its edge row does not allow; the row allows <row>`, and the scope decisions carry over: the `core` framework ban covers `core`'s test scope as well, and platform declarations are still filtered before any rule sees them. `ModuleDependencyFixture` gained `testModuleDependencies` and `testLibraries`, and the family went from 7 to 10 cases: two refusals (`the core declares the cli in its test scope`, `the core declares Spring in its test scope`) and one acceptance (`the core declares the contract in its test scope`); the count is printed from `cases().size`, nothing restates it. `docs/tasks/01-scaffolding/architecture.md` still says test scope is not read and still counts seven fixtures (five refusals, two acceptances); the paragraph should say both source sets are read and the family holds ten cases (seven refusals, three acceptances), and that edit belongs to the architecture writer. The `testFixtures` source set and any `jvmTestSuite` suites are still not read; no module uses them, so it stays out of scope.
