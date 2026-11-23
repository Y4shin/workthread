---
type: deviation report
title: Deviation report for dependency-checks
status: stable
---

## Deviation report: dependency-checks

### API surface changes

- **Planned:** The architecture's ticket 4 contract exports "two checks attached
  to `check`, their failure semantics, and the fixture convention. Dependents
  rely on `build` failing on a violation, with a naming message." The gates
  section is specific: the first check "reads each module's declared dependencies
  and fails when a module depends on something its edge row forbids, naming the
  module and the dependency; it also fails when `core` declares Spring or
  JavaFX. The second inspects the runtime classpaths and fails when a JavaFX
  artifact appears on one." The seams section adds "A fixture declares a
  violation and asserts on the failure and its message"; "No third-party rule
  plugin."
- **Actual:** Both checks exist, both are attached to every module's `check`, and
  the refusals name what is wrong. The planned surface is intact and each
  criterion was probed end-to-end on this checkout (evidence below). The
  differences are in scope and mechanism, not in whether `build` fails:
  - **The runtime classpath check exempts `client`.** The document says it fails
    when a JavaFX artifact appears on *a* runtime classpath. The client resolves
    `javafx-base`, `javafx-controls` and `javafx-graphics` on `runtimeClasspath`
    at 0.1, so the literal rule fails criterion 5 ("the real build stays green").
    The implementer scoped the rule with `moduleRowsCarryingJavaFx = setOf("client")`,
    derived from the declared-stack section ("JavaFX 21.0.13 in `client`").
    Proven non-vacuous: an `adapters` module with `org.openjfx:javafx-base`
    declared is refused as `adapters:runtimeClasspath carries
    javafx-base-21.0.13.jar; JavaFX belongs to the client module alone`. The
    architecture document's gates paragraph still states the unqualified rule and
    is now stale; it changes only through the architecture writer.
  - **The edge rule governs project dependencies only.** The `May depend on`
    rows name modules, so `verifyModuleDependencies` compares only
    `ProjectDependency` names against the row. External libraries are judged only
    in `core`, and only for two families (`org.springframework*` by group,
    `org.openjfx` or a `javafx-` artifact prefix). Reading "a dependency outside
    its edge row" as any external artifact would refuse `adapters` (sqlite-jdbc),
    `mcp` (the SDK), `api` (the starter) and `client` (JavaFX). The narrowing is
    defensible but leaves a gap: `core` declaring any other third-party library
    (Guava, Jackson, a logging facade) passes, because "the core carries no
    framework" is enforced only for Spring and JavaFX.
  - **Platform declarations are filtered.** `api` imports the Spring Boot BOM
    with `platform(...)`, and `isDependencyPlatform()` drops it before the rule
    sees it. Nothing in the real build needs this today (only `api` declares a
    platform, and `api` is not `core`), and no fixture covers the filter, so it
    is currently an untested path rather than a proven one.
  - **Declared edges are read in `afterEvaluate`, across both main classpath
    hierarchies.** `core`, `adapters` and `wiring` declare edges as `api()`, the
    surfaces as `implementation()`, so the read walks
    `compileClasspath`/`runtimeClasspath` hierarchies. Consequence for dependents:
    `verifyModuleDependencies` is registered inside `afterEvaluate`, and a
    dependency a plugin adds later (the JavaFX plugin does) is invisible to it.
    That is the runtime check's ground, and the runtime check covers only JavaFX.
  - **Test scope is not enforced at all.** The read is the `main` source set's
    classpath configurations. Verified on this checkout:
    `testImplementation(project(":cli"))` added to `core` leaves both
    `:core:verifyModuleDependencies` and `:core:verifyNoJavaFxOnRuntimeClasspath`
    green. A forbidden module edge in test scope escapes the edge table.
  - **The fixture is a root Gradle task, not a test.** The convention landed as
    plain data lists (`moduleDependencyFixtures`, `runtimeClasspathFixtures`),
    two fixture classes, a `DependencyRuleFixtures` runner, and the root
    `verifyDependencyRuleFixtures` task attached to the root `check`. It prints
    `7 dependency rule fixtures held` (five refusals, two acceptances). Two
    consequences: `./gradlew test` alone does not run it (the test protocol's
    "run these always" includes `./gradlew build`, which does), and it proves
    the rule classes, not the wiring from real declarations and real resolved
    classpaths to them. The wiring is proven by end-to-end probes, in this
    report and in the implementation artifact, not by a committed fixture.
  - **New root-build surface, wider than "two checks".** Task types
    `VerifyModuleDependencies`, `VerifyNoJavaFxOnRuntimeClasspath`,
    `DependencyRule`, `ModuleDependencyRule`, `RuntimeClasspathRule`, plus
    `ModuleDependencyFixture`, `RuntimeClasspathFixture`, `DependencyRuleFixtures`,
    the two top-level data lists, `moduleEdgeRows`, `moduleRowsCarryingJavaFx`,
    `pinnedJarArtifactNames`' neighbour `isDependencyPlatform()`, and three task
    names (`verifyModuleDependencies`, `verifyNoJavaFxOnRuntimeClasspath`,
    `verifyDependencyRuleFixtures`). `build.gradle.kts` is 554 lines; all of this
    is in the root script, as the architecture requires (no build-logic project).
  - **The edge table now has a second copy.** `moduleEdgeRows` duplicates the
    architecture table; the module name list also lives in `settings.gradle.kts`.
    A stale copy would make the check enforce a stale contract, and the check
    reads subproject names, not the module descriptors the architecture names as
    "the single source for module names".
- **Impact:** Ticket 5 (version and API snapshot) shares the same root script and
  the same fixture convention and can add its own data list and task without
  touching these, as the implementation intended. Ticket 6 (jlink image) depends
  on the client exemption staying true: this ticket enforces "no JavaFX leak into
  other modules" but does not force the client's own JavaFX onto the module path,
  so the client's JavaFX-on-`runtimeClasspath` situation survives into the image
  ticket. Tickets 7 and 8 rely on `build` failing on a violation, which holds.
  Any later effort that adds a library to `core` finds the gate silent unless it
  is Spring or JavaFX.

### Abstraction usage

- Used/was specified: **yes**, with one reading worth naming. The checks use
  Gradle's own model only (`java-library`, per-module `check`, task types in the
  root build, `Configuration` hierarchies, `Category` attributes), no third-party
  rule plugin, no build-logic project, no second checker beyond the two the
  document names, and `check` as the aggregate gate. Two points differ from the
  document's wording rather than from its intent: the edge table is a data copy
  in the build instead of being read from a single source, and the module set
  comes from the subprojects, not from the Java module descriptors that the
  document calls "the single source for module names". The rule classes are plain
  Kotlin in the root script, which is what "The root build hosts the build checks"
  asks for.

### Out-of-scope changes

- A subproject with no row in `moduleEdgeRows` fails at **configuration** time
  with `Module ':x' has no row in the architecture's edge table; a new module
  requires an architecture change first.` The ticket does not ask for this, it is
  not attached to `check`, it fails any Gradle invocation (including
  `./gradlew tasks` and `./gradlew help`), and no fixture proves it.
- `moduleRowsCarryingJavaFx` hard-codes `client` as the one JavaFX-bearing
  module, in the same way the edge table hard-codes the eight names.
- `isDependencyPlatform()` filters `REGULAR_PLATFORM` and `ENFORCED_PLATFORM` by
  name (`Category.REGULAR_PLATFORM` is a `String` constant in Gradle, not a
  `Category`), and no fixture covers it.
- No removals. No module build file, no catalogue entry, no plugin declaration,
  and no `docs/` file changed. In particular `docs/testing.md` was not touched,
  which is correct: the new tasks ride `./gradlew build`, already a "run these
  always" command.
- Verification performed for this report, on this checkout, with the working
  tree restored to clean afterwards (`git status` shows only the workflow's own
  `docs/tasks/state.yaml` modification, nothing staged): `./gradlew --offline
  build` green with `Configuration cache entry reused`; `build --dry-run` lists
  `:verifyDependencyRuleFixtures` and all sixteen per-module checks; a temporary
  `implementation(project(":cli"))` in `core` refused with `core declares a
  dependency on cli, which its edge row does not allow; the row allows
  contract`; a temporary `org.openjfx:javafx-base:21.0.13` in `core` refused with
  `core declares org.openjfx:javafx-base, which is JavaFX; the core carries no
  framework`, repeated three times with the same result; a temporary
  `org.openjfx:javafx-base` in `adapters` refused with `adapters:runtimeClasspath
  carries javafx-base-21.0.13.jar; JavaFX belongs to the client module alone`;
  changing the JavaFX prefix constant made `verifyDependencyRuleFixtures` fail
  naming the adapters fixture, so the fixture is non-vacuous.
- One anomaly during those probes, recorded for honesty: after a failed offline
  resolution had discarded a configuration-cache entry, one run of
  `:core:verifyModuleDependencies` passed while `core/build.gradle.kts` declared
  `org.openjfx:javafx-base` (the daemon appears to have configured from a stale
  build-script snapshot). Three immediate repeats refused correctly, and every
  later run behaved. I could not reproduce it, and it looks like Gradle daemon
  file-watching state rather than the rule.

### Ticket doc update needed?

Yes. Append to `## Implementation notes`:

- The three task names: `verifyModuleDependencies` (per module, attached to that
  module's `check`), `verifyNoJavaFxOnRuntimeClasspath` (per module, attached to
  that module's `check`), `verifyDependencyRuleFixtures` (root, attached to the
  root `check`).
- `moduleRowsCarryingJavaFx = setOf("client")`: the runtime classpath check
  refuses JavaFX on every runtime classpath except the client's, because the
  declared stack puts JavaFX there and a literal rule would fail the real build.
  The architecture's gates paragraph needs the same amendment.
- The edge rule compares project dependencies only; external libraries are judged
  in `core` alone, for Spring and JavaFX families. Platform declarations are
  filtered. Both are scoped this way because `adapters`, `mcp`, `api` and `client`
  legitimately declare libraries.
- The declared edges are read in `afterEvaluate` across both main classpath
  hierarchies (edges are `api()` in `core`, `adapters`, `wiring` and
  `implementation()` in the surfaces). Test scope is not read at all.
- The fixture convention: plain data lists plus one runner plus one verifying
  task, so ticket 5 adds its own list and task. `./gradlew test` does not run it;
  `./gradlew build` and `./gradlew check` do.
- A subproject with no edge row fails at configuration time, before any task.

### User attention needed?

Yes.

1. Criterion 3 and criterion 5 contradict each other read literally, and the
   implementer resolved the contradiction by exempting `client` from the runtime
   classpath rule. That is the only reading under which "the real build stays
   green" holds, and it matches the declared stack, but it is a scope decision the
   architecture document does not contain. The architecture writer should amend
   the gates paragraph; until then the design record and the build disagree.
2. Test-scope module edges are unenforced (verified: `core` with
   `testImplementation(project(":cli"))` is green). If the edge table is meant to
   hold for tests too, this is a follow-up ticket, not a fix inside this one.
3. `core`'s "no framework" rule names Spring and JavaFX only, so a future
   framework added to `core` passes. Whether that is acceptable is a product
   judgement for the efforts that add libraries.
