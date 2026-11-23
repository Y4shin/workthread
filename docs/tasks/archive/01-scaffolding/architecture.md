---
type: architecture
title: "Scaffolding: the build, eight modules, and the first shippable image"
status: stable
---

# Scaffolding: the build, eight modules, and the first shippable image

## What this document is

The design record for the scaffolding effort, reconciled after the last ticket landed: it reads as what the build, the gates, and the image actually are, not as what they were planned to be. It inlines the specification's architecture content in full, then records what each ticket landed, the abstractions the code stands on, the seams where tests and tools meet the build, and the parallel versions not to invent. The specification is the authority for scope and behaviour. This document is the authority for how the code is shaped, and it changes only through the architecture writer.

## The build and the toolchain

Gradle 9.9.1 through the committed wrapper, Kotlin DSL, Java 21 toolchain per module. One version catalogue holds every library and plugin version. The configuration cache is on, because the packaging prototype proved the plugin set is cache-clean. The root build hosts the build checks and the API snapshot task; there is no separate build-logic project. All of it is Kotlin in the one `build.gradle.kts`, about 2 000 lines, which is the file to read for what `build` enforces.

`settings.gradle.kts` declares the module graph once: it holds the edge table, includes one project per row, and publishes the map to the root build. The root build derives each module's package and module name from `packageRoot`, and keeps only the JavaFX-bearing row of its own.

The version catalogue is the only place a library or plugin version appears. A subproject declares coordinates without versions.

## The module graph

Eight subprojects: `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client`. The edge table in `settings.gradle.kts` is the authoritative edge list:

| Module | May depend on |
| --- | --- |
| `contract` | nothing |
| `core` | `contract` |
| `adapters` | `contract` |
| `wiring` | `contract` |
| `cli` | `contract`, `wiring` |
| `mcp` | `contract`, `wiring` |
| `api` | `contract`, `wiring` |
| `client` | `contract`, `wiring` |

No surface module depends on another surface module. A new module requires an architecture change first, which means a row here and a row in `settings.gradle.kts`.

The package root is `io.github.y4shin.workthread`, one subpackage per module, and the module name is the package name. `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, and `client` carry `module-info.java` under these names:

| Module | Package and module name | Type in the exported package |
| --- | --- | --- |
| `contract` | `io.github.y4shin.workthread.contract` | `ContractPlaceholder` |
| `core` | `io.github.y4shin.workthread.core` | `CorePlaceholder` |
| `adapters` | `io.github.y4shin.workthread.adapters` | `AdaptersPlaceholder` |
| `wiring` | `io.github.y4shin.workthread.wiring` | `WiringPlaceholder` |
| `cli` | `io.github.y4shin.workthread.cli` | `CliPlaceholder` |
| `mcp` | `io.github.y4shin.workthread.mcp` | `McpPlaceholder` |
| `client` | `io.github.y4shin.workthread.client` | `WorkthreadApplication` |

`api` has no descriptor and runs on the classpath, because Spring Boot's classpath model fights named modules. Its jar therefore derives an automatic name from its file name.

A module descriptor cannot export a package that holds no type: `javac` refuses an `exports` clause for a package whose only file is a javadoc-only `package-info.java`. So each named module carries one type in its exported package, beside the `package-info.java` that still documents the package. Six of the seven are markers with a private constructor, `<Module>Placeholder`, and the marker is the module's only type until the effort that owns the module adds real ones; that effort replaces the marker instead of deleting it, which keeps `contract`'s API snapshot honest. `client` went first: the marker landed as `WorkthreadApplication` and the image ticket replaced it in place with the JavaFX `Application` subclass of the same name, so `client` now holds its real entry point plus a package-private version reader. The naming difference is stated where the convention lives, in the root build beside `clientApplicationClass` and in the class's own documentation.

Anything that names a module inherits this table: `requires` clauses, the image's module set, and any `--add-modules` line.

## The declared stack

JavaFX 21.0.13 in `client`, through the JavaFX Gradle plugin 0.1.0, which supplies the host's platform classifier. Spring Boot 4.2.0 in `api`, brought in with the dependency platform only: no Spring Boot plugin and no dependency-management plugin, because the BOM alone is enough and the management plugin would override the store driver version. The MCP SDK 2.1.0 aggregator in `mcp`, with the JSON mapper supplied explicitly. sqlite-jdbc 3.54.1.0 in `adapters`. JUnit 5.14.4 and AssertJ 3.27.7 for tests. `org.beryx.jlink` 4.2.0 for packaging.

Six facts hold the list together:

- JavaFX stays on 21.0.13. The 21.0.13.1 re-spin changes only `javafx.web`, which the client never loads.
- The MCP SDK's `mcp` jar is a named module, but its payload jars `mcp-core` and `mcp-json-jackson3` are automatic modules. `jlink` refuses automatic modules, so no image can contain the MCP surface.
- Spring Boot's BOM manages JUnit 6.1.3 and sqlite-jdbc 3.53.4.0, so it is applied only in `api`. `api` holds JUnit 5 with `testImplementation(enforcedPlatform(libs.junit.bom))`, because both a soft `platform()` and an explicitly versioned Jupiter dependency lose to the platform's 6.1.3 constraint. `enforcedPlatform` is Gradle's own model, not a dependency-management plugin. The cost is that no later consumer of `api`'s test classpath can request another JUnit line.
- `api` declares `spring-boot-starter-webmvc` as its one versionless Spring Boot artifact, so the platform's effect is observable and the servlet stack is what the module resolves. This follows the repository's existing MockMvc convention and settles servlet over reactive for the API effort. Swapping the starter is a one-line change.
- Jackson 2 versus 3 is settled: both sides use Jackson 3 (`tools.jackson` 3.1.8 from Spring Boot, 3.1.4 from the MCP SDK, resolved upward), both reuse the 2.21 annotations artifact, and no exclusion is needed. `api` never sees the MCP SDK, because surface modules never depend on each other.
- The module names are confirmed from the resolved jars, not from the notes: `javafx.base`, `javafx.controls`, `javafx.graphics`, `io.modelcontextprotocol.sdk.mcp`, `com.networknt.schema`, `org.xerial.sqlitejdbc`, `org.junit.jupiter.api`, `tools.jackson.databind`, `org.slf4`. `assertj-core` 3.27.7 is named `org.assertj.core` and `jackson-annotations` 2.21 is named `com.fasterxml.jackson.annotation`, so neither is automatic. `reactor-core` and `spring-boot` are automatic. `./gradlew -q printPinnedModuleNames` reprints the table from the resolved jars with `ModuleFinder`; it reports one row per resolved jar across all eight modules.

Stack tests and fixture data restate some pinned versions, so a bump edits more than the catalogue. Six `*StackTest` classes name the Spring Boot, JUnit, AssertJ, sqlite-jdbc and JavaFX versions they resolve, and the root build holds `PINNED_JDK` and two jar-name fixtures. The catalogue remains the only source the build reads; the rest are assertions that the resolved thing is the pinned thing.

## The build's own gates

**The dependency checks.** Two root-build checks, attached to `check`.

The first reads each module's declared dependencies in `afterEvaluate`, across both classpath hierarchies of both source sets, and fails when a project dependency leaves the module's edge row, naming the module and the dependency. It also fails when `core` declares Spring or JavaFX. Test scope is read: a forbidden edge declared as `testImplementation` fails, and a declaration the test source set inherits from `main` is judged once, so one violation earns one refusal. External libraries are judged in `core` alone, for those two families, because the other modules legitimately declare libraries. Platform declarations are filtered before the rule sees them, and no fixture covers that filter, so it is an untested path rather than a proven one.

The second inspects each module's resolved `runtimeClasspath` and fails when a JavaFX artifact appears on it. The client is the one module allowed to carry JavaFX, because the declared stack puts it there; the rule refuses JavaFX on every other module's runtime classpath.

The runtime classpath check reads the `runtimeClasspath` configuration of the main source set only; the test source set is the declared-dependency rule's business.

**The descriptor check.** One per module, attached to `check`, reading the built jar with `ModuleFinder`. It refuses a missing descriptor, an automatic descriptor, a wrong module name, a `requires` outside the edge row, a missing `requires` for an edge the row names, a missing export, and a descriptor on `api`. For `client` it also refuses a missing application class, or an export set that does not cover that class. It judges build modules only, so a JDK or declared-stack module in `requires` is not judged by the edge table.

**The version check.** `verifyVersion` at the root, attached to `check`. It reads the eight built jars back and refuses a jar that reports another number or none, and it refuses any file under a module's `src` that restates the number as a whole number. The match is bounded, so `21.0.13` at version `0.1.0` is not a copy. Build scripts, documentation, and generated files are out of its scope on purpose, so fixture data and prose may name a version.

**The pipeline check.** `verifyCiWorkflow` at the root, attached to `check`, reading `.github/workflows/ci.yml` as text. It refuses a runner label that is not the pinned one, a `-latest` label, the wrong JDK patch as text, a missing wrapper setup or image task, a wrong artifact path or retention, a missing concurrency group, an artifact upload that tolerates a missing image, and any Xvfb or GTK step. It also refuses `jpackage` and the installer prerequisites that only an installer needs: `fakeroot`, `dpkg-deb`, and `wix`. It matches action names without their majors, so a major bump passes, and it reads the file as text, so a semantically identical reformat fails. Both are the accepted cost of not carrying a YAML parser in the root build.

**The fixture convention.** One plain data list per rule, holding either a violation and the message its refusal must carry, or a permitted declaration with no expectation. `FixtureChecks` owns the refusal helper, the count line, and the wording of a disagreement, and one registration loop gives every family its task on the root `check`. A family declares only its cases, and a family made of two rules holds one list per rule.

| Family | Fixtures | Cases |
| --- | --- | --- |
| Dependency rules | 10 | edge row and `core`'s Spring and JavaFX ban in both source sets, allowed declarations |
| Module descriptor | 9 | missing, misnamed, `requires` outside the row, missing `requires`, missing export, missing application class, descriptor on the classpath module, two accepted |
| Version and surface | 14 | jar reports another number or none, a source restating it, snapshot removal and reshape |
| Pipeline | 23 | one refusal per pinned fact, plus a comment-only accepted case |

No third-party rule plugin.

**The gates by task name.** Every gate rides `check`, so `./gradlew build` runs the set and `./gradlew test` alone runs none of the fixture runners.

| Gate | Task | Scope |
| --- | --- | --- |
| Edge rows, and `core`'s Spring and JavaFX ban | `verifyModuleDependencies` | per module |
| No JavaFX outside `client` | `verifyNoJavaFxOnRuntimeClasspath` | per module |
| Descriptors | `verifyModuleDescriptor` | per module |
| Version, in the jars and in `src` | `verifyVersion` | root |
| Contract surface | `verifyContractApiSnapshot` | root |
| The committed pipeline | `verifyCiWorkflow` | root |
| Fixtures | `verifyDependencyRuleFixtures`, `verifyModuleDescriptorRuleFixtures`, `verifyVersionAndSurfaceFixtures`, `verifyCiWorkflowFixtures` | root |

**Two things a new module meets.** A subproject with no row in the edge table fails at configuration time, before any task runs, with a message saying a new module needs an architecture change first: `./gradlew help` fails too, not just `check`. And the edge table exists in one place, `settings.gradle.kts`; the root build keeps only `moduleRowsCarryingJavaFx`, so a change to the graph and a change to the enforcement land together or the build enforces a stale contract.

**Version and additivity.** `0.1.0` lives in one property, `gradle.properties`, and three enforcers read it: `Implementation-Title` and `Implementation-Version` on every jar, the client's generated version resource, and `verifyVersion`. An annotated `v<version>` git tag marks each release, and `docs/releasing.md` names who tags, when, and the snapshot ritual. No tag exists for this effort.

A committed snapshot at `contract/api-snapshot.txt` holds every exported package of `contract` plus its public and protected types and members. `verifyContractApiSnapshot` compares it and fails when an element disappears or changes shape, naming the element and both shapes. Additions pass, so recording one is a ritual rather than a gate: `generateContractApiSnapshot` rewrites the file and the diff is the review. A committed fixture reproduces the removal path. The snapshot covers `contract` only at 0.1, and it includes `ContractPlaceholder`.

## Packaging

The `org.beryx.jlink` plugin builds the runtime image from the client module with `--strip-debug --compress zip-6` and a launcher named `workthread`. The `application` plugin is required as well: `jlink` cannot link or start the module without `mainModule` and `mainClass`. Both plugin versions come from the version catalogue, and the JavaFX block is what puts JavaFX on the module path. No `mergedModule` block and no module-path inference setting are needed.

```kotlin
plugins {
    application
    alias(libs.plugins.javafx)
    alias(libs.plugins.jlink)
}

application {
    mainModule = "io.github.y4shin.workthread.client"
    mainClass = "io.github.y4shin.workthread.client.WorkthreadApplication"
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
}

jlink {
    options = listOf("--strip-debug", "--compress", "zip-6")
    launcher { name = "workthread" }
}
```

The client's descriptor requires `javafx.controls`, which reads `javafx.graphics` transitively, and exports the package holding its `Application` subclass, or JavaFX cannot construct it. The application class carries `main`, so the image launcher, the generated start scripts, and `:client:run` share one entry point.

The image is `client/build/image` and the launcher is `client/build/image/bin/workthread`, with the generated `workthread.bat` beside it, so the Windows entry point ships too. On Linux x86_64 the image is 67 612 510 bytes, 1 220 bytes more than before the window's label took its version from the build. It carries `java.base`, `java.desktop`, `java.xml`, `java.prefs`, `java.datatransfer`, `jdk.unsupported`, `jdk.jfr`, and `javafx.base`, `javafx.controls`, `javafx.graphics` 21.0.13, plus `client`, `contract`, and `wiring`. It includes `jdk.jfr` because `jdeps` follows JavaFX's JFR logging classes. The JDK module set is inferred by the plugin, not declared by a descriptor, so the descriptor-as-single-source rule covers the project and JavaFX modules only.

At run time the image needs the host's GTK3 and X11 libraries, `libGL` and `libXxf86vm.so.1` for the ES2 pipeline, and a writable `~/.openjfx/cache` where the first launch extracts the JavaFX natives. The generated launcher points at a module directory that does not exist inside the image; the application module is in the linked runtime and the launcher works regardless. That quirk is cosmetic and accepted.

`client` applies `application`, so `distTar` and `distZip` would join `build`. Both are disabled: the image is the shipping artifact and nothing consumes a distribution.

The empty window carries the title `workthread`, is 1024x768, and shows one centred label reading `workthread <version>`. The label renders the single version source through a generated `workthread-version.properties` resource, not a typed literal, so a version bump moves the window with no code change. The window is the packaging proof, not a feature, and it holds no state.

## CI

One job with a matrix over `ubuntu-24.04`, `macos-26` (Apple silicon), and `windows-2025`. Each leg sets up Temurin 21.0.13 explicitly, because Ubuntu and Windows default to Java 17 and the macOS Intel image ships a different 21 patch. `gradle/actions/setup-gradle` restores the Gradle user home and validates the wrapper, so no separate wrapper-validation step is needed. Each leg runs `./gradlew build` and then `./gradlew :client:jlink`. There is no Xvfb, GTK, or jpackage prerequisite, because nothing launches. The three images are uploaded as artifacts with seven-day retention, one per leg named `image-<runner>`, and the upload refuses to pass when no image is found; a delivered artifact is not executable after upload, so it is for inspection only. Triggers are push to the main branch, pull request, and manual dispatch, with a concurrency group that cancels superseded runs. Permissions are read-only. Known risk: Windows path length, kept low by short module and image names.

The first run was green on all three legs, with `fail-fast: false` so one red leg would not hide the others. Uploaded artifact sizes were 43 971 538 bytes on `ubuntu-24.04`, 39 563 002 on `macos-26`, and 41 442 989 on `windows-2025`; each leg links its own platform's JavaFX jars, so the three differ. Unpacked, the three images measure 68 616 047, 59 189 008, and 57 778 847 bytes.

The pipeline is itself a build gate, `verifyCiWorkflow` on `check`, described under the build's gates. It pins the three labels, the Temurin 21.0.13 patch as text, the seven-day retention, the presence of each step, and the upload's refusal of a missing image. No job carries `timeout-minutes`, so a hung `:client:jlink` waits out GitHub's default six-hour limit; that gap has no ticket.

**The patch pin did not hold.** `release` inside the uploaded images reports `JAVA_VERSION="21.0.13"` on `ubuntu-24.04` and `windows-2025`, and `"21.0.13.1"` on `macos-26`. Gradle's toolchain resolution on that runner found a JDK of its own in preference to the one the setup step installed, so the three legs did not link one patch. The images build and launch regardless. The gate pins the workflow's text, so it cannot see which JDK the leg actually linked; `docs/dev-env.md` holds the observation.

**The launch check.** It ran on one of the three platforms. `image-ubuntu-24.04` was unpacked and launched inside an `ubuntu:24.04` container, because this development host's Nix GTK cannot be loaded by a Temurin-linked runtime; the window came up at 1024x768 with one centred label reading `workthread 0.1.0`. The macOS and Windows artifacts were downloaded and inspected but never launched, because no host for either platform was reached in this effort, so those two criteria are unmet rather than passed. The record, including the runtime prerequisites `ldd` produced, lives in `docs/dev-env.md`.

## Abstractions to use

- **Gradle's own model.** The `java-library` and `application` plugins, per-module toolchains, `platform()` for the Spring Boot BOM, `enforcedPlatform()` for the JUnit BOM in one module, the version catalogue, `check` as the aggregate gate, and the JUnit Platform. Nothing here needs a custom plugin.
- **The Java module system.** Descriptors are the single source for module names and exports. Do not derive a module set from a directory listing.
- **The two installed plugins.** `org.openjfx.javafxplugin` 0.1.0 owns the JavaFX coordinates and the platform classifier; `org.beryx.jlink` 4.2.0 owns the image and launcher. Both are proven on Gradle 9.9.1, and both plugin versions live in the catalogue's `[plugins]` block beside JavaFX's library version.
- **The requirements lookup script.** Every requirement read goes through it; the rule lives in the effort maps and the specification.
- **The two protocol documents.** The test protocol document holds the commands the suite gate runs; the development environment document holds the image facts and the manual launch record.

## Seams

- **The build is the seam.** `./gradlew build` is what every later effort meets, and it is the only command whose green counts as a gate. A ticket never asks a later effort to remember a rule the build can enforce.
- **The rule checks are seams.** A fixture declares a violation and asserts on the failure and its message. The message is part of the behaviour.
- **The API snapshot is a seam.** The committed surface file is the contract; the fixture is the proof that removal fails.
- **The launcher is a seam.** The image's launcher script and the window it opens are how packaging is observed.
- **The committed workflow is a seam.** The pipeline check reads `.github/workflows/ci.yml` as lines, so the file's shape, not only its meaning, is under `check`.
- **The pipeline legs are the platform seam.** Each operating system builds on its own host; nothing cross-builds.
- **The development environment document is the record seam.** The manual launch result lands there, not in a ticket body.

## Do not reimplement

- A second dependency checker. A new gate extends `BuildRule`, and its proof extends `FixtureChecks`.
- A separate build-logic project. The checks live in the root build.
- A second JavaFX declaration route. The plugin is the one; a hand-declared module path is a documented fallback, not a parallel route.
- A second version source. The property is the one.
- A second image tool. `org.beryx.jlink` is the one.
- A Spring Boot Gradle plugin or a dependency-management plugin. The BOM as a platform is enough.
- A hand-pinned Jackson. The BOM supplies it and the two lines are compatible.
- A second lookup path for requirements. Extend the script instead.
- A YAML parser in the root build for the pipeline check. The file is read as text and the reformatting cost is accepted.
- A second fixture runner. Extend `FixtureChecks`; a top-level script function breaks the configuration cache.
- Xvfb, GTK packages, or a display step in CI. Keep tests toolkit-free.
- `-latest` runner labels. Pin exact labels and re-read the runner-images table when one ages.

## What each ticket landed

The tickets were numbered in dependency order. Each entry is what that ticket left behind for the tickets after it, as built.

**1. Walking skeleton.** The committed Gradle 9.9.1 wrapper, the eight subprojects, the inward edges (`api(project(":contract"))` from `core`, `adapters`, and `wiring`; `implementation(...)` from the four surface modules), `group` and `version` in `gradle.properties`, `packageRoot`, a Java 21 toolchain per module, one JUnit 5 and AssertJ test per module asserting its own package, and the Gradle commands in the test protocol document. Its `package-info.java`-only packages blocked the descriptors, which is what forced the marker types.

**2. Declared stack.** The pinned dependency set, the Spring Boot platform in `api` with `spring-boot-starter-webmvc` as its one versionless artifact, `enforcedPlatform(libs.junit.bom)` holding JUnit 5 in `api`, the JavaFX plugin route in `client`, and `printPinnedModuleNames`, which reprints the module-name table from the resolved jars with `ModuleFinder`.

**3. Named modules.** The seven descriptors under `io.github.y4shin.workthread.<module>`, one marker per exported package, `WorkthreadApplication` as `client`'s, `api` with no descriptor, and `verifyModuleDescriptor` on every module's `check`. The ticket left the rule without fixtures, proven by probes only; the end-of-effort coherence pass gave it a `DescriptorShape` seam and nine committed fixtures.

**4. Dependency rules.** `verifyModuleDependencies` and `verifyNoJavaFxOnRuntimeClasspath` per module, the fixture family behind `verifyDependencyRuleFixtures` with ten fixtures (six refusals, four acceptances, two refusals and two acceptances reading the test source set), the `client` exemption in the JavaFX rule, and the configuration-time refusal of a subproject with no edge row.

**5. Version and contract surface.** `Implementation-Title` and `Implementation-Version` on every jar, the generated `workthread-version.properties` behind the window label, `verifyVersion` with its `src` scan, the committed `contract/api-snapshot.txt` (one package, one type), `verifyContractApiSnapshot`, `generateContractApiSnapshot`, fourteen version and surface fixtures, and `docs/releasing.md` with the tag ritual. No tag was created.

**6. Client image.** The `application` and `jlink` configuration in `client`, `requires javafx.controls` in the descriptor, `WorkthreadApplication` as a real `Application` subclass with `main`, the `client/build/image` tree with its `workthread` launcher and `workthread.bat`, and `ClientApplicationTest`, which reads the class by reflection and starts no toolkit. The ticket also brought `distTar` and `distZip` into `build`; the coherence pass disabled both.

**7. Three-platform pipeline.** `.github/workflows/ci.yml`, its three legs, the uploaded `image-<runner>` artifacts, and the fourth fixture family that guards the file as text: `verifyCiWorkflow` and `verifyCiWorkflowFixtures`, twenty-three fixtures. The first run was green on all three legs.

**8. Manual launch check.** The record in `docs/dev-env.md`: the Linux launch from the pipeline's own artifact, the runtime prerequisites read from `ldd`, and the macOS patch divergence found while inspecting the artifacts. The macOS and Windows launches did not happen, because no host for either platform was reached; those two criteria are recorded as unmet rather than passed.

**9. Edge table in test scope.** A ticket the end-of-effort review of this effort opened. `verifyModuleDependencies` now reads the `test` source set in the same `afterEvaluate` read that reads `main`, so a forbidden edge declared as `testImplementation` is refused with the same message a main-scope one earns, and `core`'s Spring and JavaFX ban covers `core`'s test scope. The test set's declarations are read as the delta against `main`, so a declaration the test classpath inherits is judged once. The family grew by two refusals and one acceptance. `verifyNoJavaFxOnRuntimeClasspath` was not extended to the test runtime classpath.

## How this document changes

Through the architecture writer only. An implementation ticket that needs a design change records it here in the same write, so the next ticket reads the current design rather than a stale one. The specification's architecture content is archival from the moment this document exists, and nothing is deleted from the specification.
