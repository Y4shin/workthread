---
type: architecture
title: "Scaffolding: the build, eight modules, and the first shippable image"
status: stable
---

# Scaffolding: the build, eight modules, and the first shippable image

## What this document is

The living design record for the scaffolding effort. It inlines the specification's architecture content in full, then adds what the implementation tickets need: per-ticket exports and interface contracts, the abstractions to stand on, the seams where tests and tools meet the build, and the parallel versions not to invent. The authority for scope and behaviour is the specification. This document is the authority for how the code is shaped, and it changes only through the architecture writer.

## The build and the toolchain

Gradle 9.9.1 through the committed wrapper, Kotlin DSL, Java 21 toolchain per module. One version catalogue holds every library and plugin version. The configuration cache is on, because the packaging prototype proved the plugin set is cache-clean. The root build hosts the build checks and the API snapshot task; there is no separate build-logic project.

The version catalogue is the only place a library or plugin version appears. A subproject declares coordinates without versions.

## The module graph

Eight subprojects: `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client`. The architecture's `May depend on` table is the authoritative edge list:

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

No surface module depends on another surface module. A new module requires an architecture change first.

The package root is `io.github.y4shin.workthread`, one subpackage per module, and the module name is the package name. `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, and `client` carry `module-info.java` under these names, derived once from `packageRoot` in the root build:

| Module | Package and module name | Placeholder type |
| --- | --- | --- |
| `contract` | `io.github.y4shin.workthread.contract` | `ContractPlaceholder` |
| `core` | `io.github.y4shin.workthread.core` | `CorePlaceholder` |
| `adapters` | `io.github.y4shin.workthread.adapters` | `AdaptersPlaceholder` |
| `wiring` | `io.github.y4shin.workthread.wiring` | `WiringPlaceholder` |
| `cli` | `io.github.y4shin.workthread.cli` | `CliPlaceholder` |
| `mcp` | `io.github.y4shin.workthread.mcp` | `McpPlaceholder` |
| `client` | `io.github.y4shin.workthread.client` | `WorkthreadApplication` |

`api` has no descriptor and runs on the classpath, because Spring Boot's classpath model fights named modules.

A module descriptor cannot export a package that holds no type, so each named module carries one placeholder type in its exported package. The placeholder is the module's only type until the effort that owns the module adds real ones: that effort replaces the placeholder instead of deleting it, which keeps `contract`'s API snapshot honest. `client`'s placeholder is the stand-in for its JavaFX `Application` subclass, which the image work replaces in place.

Anything that names a module inherits this table: `requires` clauses, the image's module set, and any `--add-modules` line.

## The declared stack

JavaFX 21.0.13 in `client`, through the JavaFX Gradle plugin 0.1.0, which supplies the host's platform classifier. Spring Boot 4.2.0 in `api`, brought in with the dependency platform only: no Spring Boot plugin and no dependency-management plugin, because the BOM alone is enough and the management plugin would override the store driver version. The MCP SDK 2.1.0 aggregator in `mcp`, with the JSON mapper supplied explicitly. sqlite-jdbc 3.54.1.0 in `adapters`. JUnit 5.14.4 and AssertJ 3.27.7 for tests. `org.beryx.jlink` 4.2.0 for packaging.

Six facts hold the list together:

- JavaFX stays on 21.0.13. The 21.0.13.1 re-spin changes only `javafx.web`, which the client never loads.
- The MCP SDK's `mcp` jar is a named module, but its payload jars `mcp-core` and `mcp-json-jackson3` are automatic modules. `jlink` refuses automatic modules, so no image can contain the MCP surface.
- Spring Boot's BOM manages JUnit 6.1.3 and sqlite-jdbc 3.53.4.0, so it is applied only in `api`. `api` holds JUnit 5 with `testImplementation(enforcedPlatform(libs.junit.bom))`, because both a soft `platform()` and an explicitly versioned Jupiter dependency lose to the platform's 6.1.3 constraint. `enforcedPlatform` is Gradle's own model, not a dependency-management plugin.
- `api` declares `spring-boot-starter-webmvc` as its one versionless Spring Boot artifact, so the platform's effect is observable and the servlet stack is what the module resolves. This follows the repository's existing MockMvc convention and settles servlet over reactive for the API effort. Swapping the starter is a one-line change.
- Jackson 2 versus 3 is settled: both sides use Jackson 3 (`tools.jackson` 3.1.8 from Spring Boot, 3.1.4 from the MCP SDK, resolved upward), both reuse the 2.21 annotations artifact, and no exclusion is needed. `api` never sees the MCP SDK, because surface modules never depend on each other.
- The module names are confirmed from the resolved jars, not from the notes: `javafx.base`, `javafx.controls`, `javafx.graphics`, `io.modelcontextprotocol.sdk.mcp`, `com.networknt.schema`, `org.xerial.sqlitejdbc`, `org.junit.jupiter.api`, `tools.jackson.databind`, `org.slf4`. `assertj-core` 3.27.7 is named `org.assertj.core` and `jackson-annotations` 2.21 is named `com.fasterxml.jackson.annotation`, so neither is automatic. `reactor-core` and `spring-boot` are automatic. `./gradlew -q printPinnedModuleNames` reprints the table from the resolved jars with `ModuleFinder`.

## The build's own gates

**The dependency checks.** Two root-build Kotlin checks, attached to `check`, plus one fixture runner.

The first reads each module's declared dependencies in `afterEvaluate`, across both the `compileClasspath` and `runtimeClasspath` hierarchies, and fails when a project dependency leaves the module's edge row, naming the module and the dependency. It also fails when `core` declares Spring or JavaFX. External libraries are judged in `core` alone, for those two families, because the other modules legitimately declare libraries. Platform declarations are filtered before the rule sees them.

The second inspects each module's resolved `runtimeClasspath` and fails when a JavaFX artifact appears on it. The client is the one module allowed to carry JavaFX, because the declared stack puts it there; the rule refuses JavaFX on every other module's runtime classpath.

Test scope is not read: the checks cover the main source sets, so a forbidden module edge declared as `testImplementation` passes today.

**The descriptor check.** One per module, attached to `check`, reading the built jar with `ModuleFinder`. It refuses a missing descriptor, an automatic descriptor, a wrong module name, a `requires` outside the edge row, a missing `requires` for an edge the row names, a missing export, and a descriptor on `api`. For `client` it also refuses a missing application class, or an export set that does not cover that class. It judges build modules only, so a JDK or declared-stack module in `requires` is not judged by the edge table.

**The version check.** `verifyVersion` at the root, attached to `check`. It reads the eight built jars back and refuses a jar that reports another number or none, and it refuses any file under a module's `src` that restates the number as a whole number. Build scripts, documentation, and generated files are out of its scope on purpose, so fixture data and prose may name a version.

**The pipeline check.** `verifyCiWorkflow` at the root, attached to `check`, reading `.github/workflows/ci.yml` as text. It refuses a runner label that is not the pinned one, a `-latest` label, the wrong JDK patch, a missing wrapper setup or image task, a wrong artifact path or retention, a missing concurrency group, and any Xvfb, GTK, or jpackage step. It reads the file as text, so a semantically identical reformat also fails; that is the accepted cost of not carrying a YAML parser in the root build.

**The fixture convention.** One plain data list per rule family, holding either a violation and the message its refusal must carry, or a permitted declaration with no expectation; one runner per family; one root task per family, attached to the root `check`. The dependency rules carry seven committed fixtures, the version and surface rules fourteen, and the pipeline rules twenty-three. The descriptor rule currently carries none, and its non-vacuity rests on probes recorded in the `named-modules` deviation report; giving it fixtures is open work.

No third-party rule plugin.

**Version and additivity.** `0.1.0` lives in one property, `gradle.properties`, and three enforcers read it: `Implementation-Title` and `Implementation-Version` on every jar, the client's generated version resource, and `verifyVersion`. An annotated `v<version>` git tag marks each release, and `docs/releasing.md` names who tags, when, and the snapshot ritual.

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

The image is `client/build/image` and the launcher is `client/build/image/bin/workthread`. On Linux x86_64 it is 67 612 510 bytes. It carries `java.base`, `java.desktop`, `java.xml`, `java.prefs`, `java.datatransfer`, `jdk.unsupported`, `jdk.jfr`, and `javafx.base`, `javafx.controls`, `javafx.graphics` 21.0.13, plus `client`, `contract`, and `wiring`. It includes `jdk.jfr` because `jdeps` follows JavaFX's JFR logging classes. The JDK module set is inferred by the plugin, not declared by a descriptor, so the descriptor-as-single-source rule covers the project and JavaFX modules only.

At run time the image needs the host's GTK3 and X11 libraries, `libGL` and `libXxf86vm.so.1` for the ES2 pipeline, and a writable `~/.openjfx/cache` where the first launch extracts the JavaFX natives. The generated launcher points at a module directory that does not exist inside the image; the application module is in the linked runtime and the launcher works regardless. That quirk is cosmetic and accepted.

Because `client` applies `application`, `:client:assemble` also emits `distTar` and `distZip`. Nothing consumes those distributions; the image is the shipping artifact. Whether they stay is open.

The empty window carries the title `workthread`, is 1024x768, and shows one centred label reading `workthread <version>`. The label renders the single version source through a generated `workthread-version.properties` resource, not a typed literal, so a version bump moves the window with no code change. The window is the packaging proof, not a feature, and it holds no state.

## CI

One job with a matrix over `ubuntu-24.04`, `macos-26` (Apple silicon), and `windows-2025`. Each leg sets up Temurin 21.0.13 explicitly, because Ubuntu and Windows default to Java 17 and the macOS Intel image ships a different 21 patch. `gradle/actions/setup-gradle` restores the Gradle user home and validates the wrapper, so no separate wrapper-validation step is needed. Each leg runs `./gradlew build` and then `./gradlew :client:jlink`. There is no Xvfb, GTK, or jpackage prerequisite, because nothing launches. The three images are uploaded as artifacts with seven-day retention, one per leg named `image-<runner>`; a delivered artifact is not executable after upload, so it is for inspection only. Triggers are push to the main branch, pull request, and manual dispatch, with a concurrency group that cancels superseded runs. Permissions are read-only. Known risk: Windows path length, kept low by short module and image names.

The first run was green on all three legs, with `fail-fast: false` so one red leg would not hide the others. Uploaded image sizes were 43 971 538 bytes on `ubuntu-24.04`, 39 563 002 on `macos-26`, and 41 442 989 on `windows-2025`; each leg links its own platform's JavaFX jars, so the three differ.

The pipeline is itself a build gate: a root check reads the committed workflow, refuses a runner label that is not the pinned one, a `-latest` label, the wrong JDK patch, a missing wrapper setup or image task, a wrong artifact path or retention, a missing concurrency group, and any Xvfb, GTK, or jpackage step. The file is therefore checked as text, so a semantically identical reformat fails `check`; that is the accepted price of not carrying a YAML parser in the root build.

## Abstractions to use

- **Gradle's own model.** The `java-library` and `application` plugins, per-module toolchains, `platform()` for the Spring Boot BOM, the version catalogue, `check` as the aggregate gate, and the JUnit Platform. Nothing here needs a custom plugin.
- **The Java module system.** Descriptors are the single source for module names and exports. Do not derive a module set from a directory listing.
- **The two installed plugins.** `org.openjfx.javafxplugin` 0.1.0 owns the JavaFX coordinates and the platform classifier; `org.beryx.jlink` 4.2.0 owns the image and launcher. Both are proven on Gradle 9.9.1.
- **The requirements lookup script.** Every requirement read goes through it; the rule lives in the effort maps and the specification.
- **The two protocol documents.** The test protocol document holds the commands the suite gate runs; the development environment document holds the image facts and the manual launch record.

## Seams

- **The build is the seam.** `./gradlew build` is what every later effort meets. A ticket never asks a later effort to remember a rule the build can enforce.
- **The rule checks are seams.** A fixture declares a violation and asserts on the failure and its message. The message is part of the behaviour.
- **The API snapshot is a seam.** The committed surface file is the contract; the fixture is the proof that removal fails.
- **The launcher is a seam.** The image's launcher script and the window it opens are how packaging is observed.
- **The pipeline legs are the platform seam.** Each operating system builds on its own host; nothing cross-builds.
- **The development environment document is the record seam.** The manual launch result lands there, not in a ticket body.

## Do not reimplement

- A second dependency checker. Extend the two root-build checks.
- A separate build-logic project. The checks live in the root build.
- A second JavaFX declaration route. The plugin is the one; a hand-declared module path is a documented fallback, not a parallel route.
- A second version source. The property is the one.
- A second image tool. `org.beryx.jlink` is the one.
- A Spring Boot Gradle plugin or a dependency-management plugin. The BOM as a platform is enough.
- A hand-pinned Jackson. The BOM supplies it and the two lines are compatible.
- A second lookup path for requirements. Extend the script instead.
- Xvfb, GTK packages, or a display step in CI. Keep tests toolkit-free.
- `-latest` runner labels. Pin exact labels and re-read the runner-images table when one ages.

## Per-ticket interface contracts

The tickets are numbered in dependency order. Each contract is what the ticket promises the tickets that depend on it.

**1. Walking skeleton: wrapper, eight subprojects, green build.** Exports the eight subproject names, the project-level dependency edges, the version property at 0.1.0, the package root, a configured test task per subproject, a green `build`, and the Gradle commands in the test protocol document. Dependents rely on the names and on the task graph.

**2. Declare the real stack and prove it resolves.** Exports the resolved dependency set at the pinned versions, the Spring Boot platform scoped to `api`, the JavaFX platform-classifier route, and a recorded confirmation of the resolved module names. Dependents rely on the coordinates and on the fact that resolution is already proven.

**3. Make the seven modules named Java modules.** Exports the module names and exported packages of the seven named modules, the client's exported application package, and `api` on the classpath. Dependents rely on the descriptor names for `requires` clauses and for the image's module set.

**4. Enforce the dependency rules in the build.** Exports two checks attached to `check`, their failure semantics, and the fixture convention. Dependents rely on `build` failing on a violation, with a naming message.

**5. Start 0.1.0 and the contract API snapshot.** Exports the single version source, the committed surface snapshot, the comparison task and its failure semantics, and the tag convention. Dependents rely on `check` covering the surface.

**6. Ship the empty window as a jlink image.** Exports the client image task, the launcher name, the window content, the image size, and the runtime prerequisites. Dependents rely on the task and the launcher.

**7. Build and package on three platforms in CI.** Exports the pipeline, its legs, and the uploaded images. The manual check relies on downloadable images.

**8. Record the manual launch check on each platform.** Terminal: it exports nothing beyond the recorded evidence in the development environment document.

## How this document changes

Through the architecture writer only. An implementation ticket that needs a design change records it here in the same write, so the next ticket reads the current design rather than a stale one. The specification's architecture content is archival from the moment this document exists, and nothing is deleted from the specification.
