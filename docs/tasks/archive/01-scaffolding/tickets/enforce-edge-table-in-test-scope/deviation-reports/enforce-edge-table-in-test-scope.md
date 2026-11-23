---
type: deviation report
title: Deviation report for enforce-edge-table-in-test-scope
status: stable
---

## Deviation report: enforce-edge-table-in-test-scope

### API surface changes

- **Planned:** Extend `verifyModuleDependencies` so the edge-table refusal
  reaches test scope. The ticket names the shape precisely: read the test source
  sets' own classpaths "in the same `afterEvaluate` read", with "the same message
  shape: the module, the dependency, and the row that forbids it", and "keep the
  existing scope decisions: only project dependencies are judged against a row,
  external libraries are judged in `core` alone for Spring and JavaFX, and
  platform declarations are filtered". Fixtures extend the existing
  `FixtureChecks` family in the same plain-data shape, and
  `verifyDependencyRuleFixtures` must hold the new count. No plugin, no
  build-logic project, no new task.
- **Actual:** The planned surface is intact. `verifyModuleDependencies` now reads
  `testCompileClasspath` and `testRuntimeClasspath` in the same `afterEvaluate`
  block as `main`, and a test-only project edge outside the row fails with the
  unchanged message, e.g. `core declares a dependency on cli, which its edge row
  does not allow; the row allows contract`. Five things are worth spelling out
  because a dependent ticket can observe them:
  - **The test scope is read as a delta against `main`, not as its own full
    hierarchy.** `testImplementation` extends `implementation`, so a raw read of
    the test hierarchy would re-see every main declaration. The build subtracts
    the main edge and library lists (`projectEdgesIn(testDeclared) - edges`,
    `libraryCoordinatesIn(testDeclared) - libraryCoordinates`) and puts only the
    remainder into the new inputs. Consequence: an inherited main-scope edge is
    never re-judged as a test-scope one, so a main-scope violation is reported
    once, and `testModuleDependencies` holds only what the test scope adds. The
    task's two new `@get:Input` lists carry that delta, not the full test set.
  - **The `core` framework ban now covers test scope too.** The ticket's "keep
    the existing scope decisions" sentence does not settle whether the library
    judgement is re-applied to the new classpaths. The implementation read it as
    "the rule's decisions, applied to both scopes" and added `testLibraries` to
    the rule, the task, the fixture shape, and the registration, with a fixture
    proving it. This is the one behavioural addition beyond the criterion the
    ticket states. The narrower reading (test scope = project dependencies only)
    would drop `testLibraries` in four places and nothing else.
  - **The refusal message does not name the scope.** Both scopes produce the
    identical string, because the ticket asked for the same shape and because
    main-scope behaviour, including its message, must not change. A reader told
    `core declares a dependency on cli` must find the declaration in either
    `build.gradle.kts` block; the message does not say which.
  - **The fixture family went from 7 to 10 cases, not 8 or 9.** Two new refusals
    (a test-scope edge outside the row, and test-scope Spring in `core`) plus one
    new acceptance (`the core declares the contract in its test scope`) so the
    other half is guarded the way the family's existing acceptances guard theirs.
    `verifyDependencyRuleFixtures` prints `${cases().size} fixtures held`, so the
    count is derived and no second place restates it. The task now prints
    `10 dependency rule fixtures held`.
  - **Small internal refactor in `build.gradle.kts`.** The declaration gathering
    moved into three top-level helpers (`declaredDependenciesOf(project,
    sourceSet)`, `projectEdgesIn(...)`, `libraryCoordinatesIn(...)`), and the
    rule body moved into `refusalsFor(moduleDependencies, libraries)`, because
    each is now used by two scopes. No behaviour change beyond the test-scope
    addition; the main-scope strings are byte-identical.
- **Impact:** Later efforts that add a module, or add a dependency to `core`'s
  tests, meet a stricter gate: `core`'s test classpath may no longer carry
  Spring or JavaFX. Every other module's test-scope external libraries remain
  unjudged, exactly as its main-scope ones are. The JavaFX runtime check
  (`verifyNoJavaFxOnRuntimeClasspath`) still reads only the main
  `runtimeClasspath`, so a non-`core` module could put JavaFX on its test
  runtime classpath and pass; that asymmetry is unchanged by this ticket and is
  not covered by its criteria. The edge table still has its second copy in
  `moduleEdgeRows`, so the usual "graph change and enforcement land together"
  rule applies.

### Abstraction usage

- Used/was specified: **yes.** The change uses Gradle's own model only: the
  existing `SourceSetContainer`, `Configuration.hierarchy`,
  `ProjectDependency`/`ExternalModuleDependency`, `@get:Input` task properties,
  and the existing `BuildRule`/`FixtureChecks`/`fixtureFamilies` conventions. No
  third-party rule plugin, no build-logic project, no second checker, no second
  fixture runner, and no new task. `settings.gradle.kts` is untouched. The
  `afterEvaluate` read is the same one the main scope already used.

### Out-of-scope changes

- The three top-level gathering helpers and `refusalsFor` are new names in the
  root script, added to share the work between two scopes. Internal only.
- `VerifyModuleDependencies` gained two inputs and its description now reads "in
  either source set". `ModuleDependencyFixture` and
  `DependencyRuleFixtures` gained the matching two fields.
- No removals. No module build file, catalogue entry, plugin declaration, or
  `settings.gradle.kts` change. `verifyNoJavaFxOnRuntimeClasspath` is untouched.
- **`docs/tasks/01-scaffolding/architecture.md` was not updated and is now stale
  in two places.** The build-gates paragraph still says "Test scope is not read:
  the checks cover the main source sets, so a forbidden module edge declared as
  `testImplementation` passes today", and the fixture table still says the
  dependency family holds 7 cases. The "What each ticket landed" section has no
  entry for this ticket. Leaving the architecture writer's document alone is
  correct process, but the design record and the build currently disagree.
- The working tree carries one unstaged modification, `docs/tasks/state.yaml`
  (`task: enforce-edge-table-in-test-scope`), which is the workflow's own
  progress file, not part of this change. Nothing is staged. The two checkpoint
  commits on the branch touch `build.gradle.kts` only.

### Ticket doc update needed?

Yes. Append to `## Implementation notes`:

- `verifyModuleDependencies` reads both source sets in the one `afterEvaluate`
  read: `main` and `test`, across each set's compile and runtime classpath
  hierarchies. Test-scope input lists are the delta against `main`, so inherited
  declarations are judged once.
- The task gained two inputs, `testModuleDependencies` and `testLibraries`;
  `ModuleDependencyFixture` and `DependencyRuleFixtures` gained the same two
  fields. The refusal message is identical in both scopes and does not name the
  scope.
- `core`'s Spring/JavaFX ban now applies to its test scope as well. Every other
  module's test-scope external libraries remain unjudged.
- The fixture family holds 10 cases (7 refusals, 3 acceptances) and prints
  `10 dependency rule fixtures held`; the count is derived from the list.
- `verifyNoJavaFxOnRuntimeClasspath` was not extended to test runtime
  classpaths; only the declared-dependency rule gained test scope.
- `docs/tasks/01-scaffolding/architecture.md` needs the architecture writer: the
  "Test scope is not read" sentence, the fixture table's 7, and a "What each
  ticket landed" entry for this ticket.

### User attention needed?

Yes.

1. The `core` framework ban now covers its test scope. That is a scope decision
   the ticket does not state literally ("keep the existing scope decisions" can
   be read either way), it is proven by a fixture, and it is the one thing a
   later `core` test author will notice. If the narrower reading was intended,
   `testLibraries` comes out of four places and nothing else changes.
2. The architecture document is stale exactly where this ticket changes the
   design. It is the effort's interface contract, so the architecture writer
   should land the two amendments and the landed-ticket entry before the next
   effort reads it.
3. Residual gap, not introduced here: the JavaFX runtime check still reads only
   the main `runtimeClasspath`. A non-`core` module with JavaFX in test scope is
   refused by nothing. If the edge table is meant to reach test scope for the
   JavaFX rule too, that is a follow-up ticket.
