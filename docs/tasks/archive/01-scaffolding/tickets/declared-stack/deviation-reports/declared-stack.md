---
type: deviation report
title: Deviation report for declared-stack
status: stable
---

## Deviation report: declared-stack

### API surface changes

- **Planned:** The architecture's ticket 2 contract exports "the resolved
  dependency set at the pinned versions, the Spring Boot platform scoped to
  `api`, the JavaFX platform-classifier route, and a recorded confirmation of the
  resolved module names". The declared stack section names JavaFX 21.0.13 through
  `org.openjfx.javafxplugin` 0.1.0, Spring Boot 4.2.0 as "the dependency platform
  only" in `api`, the MCP SDK 2.1.0 aggregator "with the JSON mapper supplied
  explicitly", sqlite-jdbc 3.54.1.0 in `adapters`, and JUnit 5.14.4 plus AssertJ
  3.27.7 for tests. Spring Boot's BOM "is applied only in `api`, where JUnit 5 is
  declared explicitly".
- **Actual:** All four exports exist and reproduce. JavaFX, the MCP closure,
  sqlite-jdbc, JUnit 5.14.4 and AssertJ 3.27.7 resolve at the pinned versions, the
  Spring Boot artifacts resolve versionless from the platform, and
  `./gradlew -q printPinnedModuleNames` prints names read from the resolved jars
  with `ModuleFinder` rather than inferred from a POM. Verified on this checkout:
  the printed table matches the implementation artifact line for line, and
  `./gradlew build` is green with `Configuration cache entry reused`. Five details
  differ from the document:
  - **`api` declares `spring-boot-starter-webmvc`.** The architecture pins only
    the platform. A versionless artifact has to exist for the platform to be
    observable, and the implementer chose the web starter. Consequence, confirmed
    by `:api:dependencies --configuration testRuntimeClasspath`: `api` now carries
    `tomcat-embed-core` 11.0.27, `spring-webmvc` 7.1.0, `spring-boot-autoconfigure`,
    logback 1.6.6, `log4j-to-slf4j` and micrometer on top of the BOM's constraint
    list. Nothing in this effort consumes it; it is a one-line change. It does
    pre-decide servlet (not reactive) for the API effort.
  - **JUnit 5 is held in `api` by `enforcedPlatform`, not "declared explicitly".**
    `api/build.gradle.kts` adds `testImplementation(enforcedPlatform(libs.junit.bom))`.
    That is Gradle's own model, not a dependency-management plugin, so the "no
    dependency-management plugin" rule holds. It is stronger than the document's
    wording, and stronger than a soft `platform()`: `:api:dependencyInsight
    --dependency junit-jupiter-api --configuration testRuntimeClasspath` now reports
    `5.14.4` with selection reasons "By constraint" and "Forced", while the
    platform shows `junit-jupiter-api:6.1.3 -> 5.14.4`. The cost is that any later
    consumer of `api`'s test classpath cannot request another JUnit line.
  - **New root-build surface.** A `PrintPinnedModuleNames` task type, one
    `printPinnedModuleNames` task per subproject over `testRuntimeClasspath`, and a
    root aggregate that prints the per-module reports in module order. The contract
    promised "a recorded confirmation", not a named task. The root build is the
    architecture's declared host for the build checks and the snapshot task, so
    the host is right, but ticket 4 now shares that file with this machinery.
  - **Three module-name facts contradict the `dependency-pins` notes.**
    `org.assertj:assertj-core` 3.27.7 is a **named** module `org.assertj.core`
    (`META-INF/versions/9/module-info.class`), not automatic as the notes say.
    `jackson-annotations` 2.21 is a **named** module
    `com.fasterxml.jackson.annotation`, where the notes say "automatic, not
    verified on the jar". `reactor-core` 3.7.0 (`reactor.core`) and
    `spring-boot` (`spring.boot`) are automatic and unclassified in the notes.
    Minor drifts: `api` resolves `jackson-annotations` 2.21, not the notes'
    2.21.8, and `mcp` resolves `slf4j-api` 2.0.17 where the notes record 2.0.16
    resolving to 2.0.20 under the Boot BOM (only `api` sees 2.0.20).
  - **Guard tests with no red state.** `CoreStackTest` and `ContractStackTest`
    assert properties the build already held. They are scoping regressions guards,
    not red-first tests, and the artifact says so.
- **Impact:** On dependent tickets:
  - **Ticket 3 (named modules).** The names it needs are now confirmed:
    `javafx.base`, `javafx.controls`, `javafx.graphics`, `io.modelcontextprotocol.sdk.mcp`
    plus its two automatic payloads, `com.networknt.schema`,
    `org.xerial.sqlitejdbc`, `org.junit.jupiter.api`, `tools.jackson.databind`,
    `org.slf4j`, `reactor.core`. Two cautions. It must not treat `assertj-core` or
    `jackson-annotations` as automatic, because both are named. And the recorded
    artifact list omits `jackson-core`, `jackson-dataformat-yaml`, `itu`,
    `snakeyaml-engine` and `reactive-streams`, all of which resolve on `mcp`'s
    classpath, so those names stay unconfirmed if the `mcp` descriptor needs more
    than the SDK's own five `requires` clauses.
  - **Ticket 3, forward risk.** `client` applies the JavaFX plugin without the
    `application` plugin, and no JavaFX jar was proven on the module path. When
    `client` gains a descriptor, JavaFX has to reach the module path and the
    plugin configuration may need `mainModule`/`patch-module` wiring that this
    ticket did not prove. The guard tests read both `java.class.path` and
    `jdk.module.path`, so the failure will be loud, not silent.
  - **Ticket 4 (dependency checks).** The check for Spring or JavaFX in `core` is
    unaffected, and `core` verifies clean today. The checks that walk runtime
    classpaths must tolerate the new Spring Boot closure in `api`; only JavaFX is
    forbidden there. Ticket 4 also writes into the same root script that now holds
    the report task and `pinnedJarArtifactNames`.
  - **Ticket 5 (API snapshot).** Unaffected. `contract` still declares no
    libraries and resolves the shared test libraries from the root convention.
  - **Ticket 6 (client image).** Unchanged constraint: `mcp-core` and
    `mcp-json-jackson3` are automatic, so no image can hold the MCP surface. The
    client's three JavaFX jars are all named, which the image work needs.

### Abstraction usage

- Used/was specified: **yes.** Gradle's own model throughout: `platform()` for
  the Spring Boot BOM, `enforcedPlatform()` for the JUnit BOM in one module,
  typed lazy task registration with `Property`/`ListProperty`/`ConfigurableFileCollection`/`RegularFileProperty`,
  and the version catalogue as the only version source. A grep for a literal
  `version = "..."` in any `build.gradle.kts` returns nothing; the JavaFX plugin
  version lives in the catalogue's `[plugins]` block and the JavaFX library
  version is read as `libs.versions.javafx`. The Java module system is used where
  the document says to: `ModuleFinder.of(jar)` reads the descriptor at execution
  time, and no descriptor was invented early. The JavaFX plugin is the single
  JavaFX route; no jar is declared by hand and no module path is hand-built. No
  build-logic project, no second version source, no dependency-management plugin.
  Verified: `./gradlew -q :api:tasks --all | grep -ci boot` prints `0`, and the
  only plugin applied by any module script is `org.openjfx.javafxplugin` in
  `client`.

### Out-of-scope changes

- Root `build.gradle.kts` gained the `PrintPinnedModuleNames` task type, the
  per-module task registration, the root aggregate, and the
  `pinnedJarArtifactNames` list. Needed for criterion 6, wider than the ticket's
  wording.
- `api` gained a web starter and the embedded Tomcat closure, as above.
- Two guard tests (`CoreStackTest`, `ContractStackTest`) that assert existing
  scoping instead of a new behaviour.
- The `resolvedJars` helper is duplicated in six test classes. Sharing it would
  need a test-fixtures source set on `contract`, which is new build surface the
  ticket does not ask for.
- Nothing was removed. No `module-info.java`, no dependency-rule check, no
  `jlink`, no CI workflow, no `docs/` edit. `docs/testing.md` and
  `docs/dev-env.md` are untouched, so `dev-env.md` stays stale as the
  walking-skeleton report predicted, and that is still ticket 8's seam.

### Ticket doc update needed?

Yes. The ticket has no `## Implementation notes` section yet, and criterion 6
requires the recorded command and its result there. The implementation worker had
no writer tool for `docs/tasks/`. Append to `## Implementation notes`:

- The recorded command is `./gradlew -q printPinnedModuleNames`, tar-separated:
  module, module name, shape, jar. It reproduces exactly as recorded in the
  implementation artifact, the table with 33 rows spanning all eight modules.
- Three corrections to the `dependency-pins` notes: `assertj-core` 3.27.7 is
  named `org.assertj.core`, `jackson-annotations` 2.21 is named
  `com.fasterxml.jackson.annotation`, and `reactor-core` 3.7.0 plus `spring-boot`
  4.2.0 are automatic. `api` resolves `jackson-annotations` 2.21, not 2.21.8, and
  `mcp` resolves `slf4j-api` 2.0.17 against `api`'s 2.0.20.
- `api` declares `spring-boot-starter-webmvc` as its first versionless Spring
  Boot artifact, so the platform's effect is observable. Swapping the starter is
  a one-line change.
- `api` holds JUnit 5 with `enforcedPlatform(libs.junit.bom)` on
  `testImplementation`, because both a soft `platform()` and an explicit
  versioned Jupiter dependency lose to the platform's 6.1.3 constraint.
- `CoreStackTest` and `ContractStackTest` are regression guards with no red
  state, and the classpath-reading helper is duplicated per module on purpose.
- The walking-skeleton blocker still stands: no module has a first type, so
  `named-modules` still cannot write `exports` clauses without adding one.

### User attention needed?

Yes. Nothing blocks, but three points want a decision or a document change.

1. **A starter is now in `api`.** The spec pinned the platform and said nothing
   about a starter. 0.1's `api` therefore carries embedded Tomcat 11 and Spring
   MVC 7. Decide whether the scaffolding keeps a starter (it proves the platform
   resolves) or whether the architecture should name the starter, so the API
   effort inherits a decision rather than a side effect. Servlet versus reactive
   is settled by this choice.
2. **The JUnit remedy in the dependency notes does not work.** The
   `dependency-pins` notes offer "declare the JUnit 5 BOM or an explicit Jupiter
   5.14.4 test dependency in `api`". Both lose: the request stays 5.14.4 while
   the platform's constraint is 6.1.3, and Gradle takes the constraint.
   `enforcedPlatform` is the mechanism that holds. Either the architecture's "JUnit
   5 is declared explicitly" wording changes, or it should state the enforced
   platform. As written, the next reader will implement the failing remedy again.
3. **The dependency notes need the three module-name corrections** before ticket 3
   reads them. The corrections live in a task doc's `## Notes`, which this ticket
   cannot write; the harvest step or the architecture writer should carry them
   into the effort map.
