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

`contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, and `client` carry `module-info.java` and are named modules. `api` has no descriptor and runs on the classpath, because Spring Boot's classpath model fights named modules. The package root is `io.github.y4shin.workthread`, one subpackage per module.

## The declared stack

JavaFX 21.0.13 in `client`, through the JavaFX Gradle plugin 0.1.0, which supplies the host's platform classifier. Spring Boot 4.2.0 in `api`, brought in with the dependency platform only: no Spring Boot plugin and no dependency-management plugin, because the BOM alone is enough and the management plugin would override the store driver version. The MCP SDK 2.1.0 aggregator in `mcp`, with the JSON mapper supplied explicitly. sqlite-jdbc 3.54.1.0 in `adapters`. JUnit 5.14.4 and AssertJ 3.27.7 for tests. `org.beryx.jlink` 4.2.0 for packaging.

Four version facts hold the list together:

- JavaFX stays on 21.0.13. The 21.0.13.1 re-spin changes only `javafx.web`, which the client never loads.
- The MCP SDK's `mcp` jar is a named module, but its payload jars `mcp-core` and `mcp-json-jackson3` are automatic modules. `jlink` refuses automatic modules, so no image can contain the MCP surface.
- Spring Boot's BOM manages JUnit 6.1.3 and sqlite-jdbc 3.53.4.0, so it is applied only in `api`, where JUnit 5 is declared explicitly.
- Jackson 2 versus 3 is settled: both sides use Jackson 3 (`tools.jackson` 3.1.8 from Spring Boot, 3.1.4 from the MCP SDK, resolved upward), both reuse the 2.21 annotations artifact, and no exclusion is needed. `api` never sees the MCP SDK, because surface modules never depend on each other.

## The build's own gates

**The dependency checks.** Two root-build Kotlin checks, attached to `check`. The first reads each module's declared dependencies and fails when a module depends on something its edge row forbids, naming the module and the dependency; it also fails when `core` declares Spring or JavaFX. The second inspects the runtime classpaths and fails when a JavaFX artifact appears on one. The messages name the offending module and dependency. No third-party rule plugin.

**Version and additivity.** `0.1.0` lives in one property, and an annotated git tag marks each release. A committed snapshot holds every exported package of `contract`; `check` compares the current surface against it and fails when an exported type or member disappears or changes shape, naming what changed. Because 0.1 exports almost nothing, a committed fixture reproduces a removal so the check's failure path is proven. The snapshot covers `contract` only at 0.1.

## Packaging

The `org.beryx.jlink` plugin builds the runtime image from the client module with `--strip-debug --compress zip-6` and a launcher named `workthread`. The prototype that proved this line produced the configuration below: no `mergedModule` block and no module-path inference setting were needed, and the `javafx` block is what puts JavaFX on the module path.

```kotlin
plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "4.2.0"
}

javafx {
    version = "21.0.13"
    modules = listOf("javafx.controls")
}

jlink {
    options = listOf("--strip-debug", "--compress", "zip-6")
    launcher { name = "workthread" }
}
```

The client's descriptor must export the package holding its `Application` subclass, or JavaFX cannot construct it. The resulting image is about 67.6 MB on Linux x86_64. It includes `jdk.jfr`, because `jdeps` follows JavaFX's JFR logging classes. It still needs the host's GTK3 and X11 libraries at run time. The generated launcher points at a module directory that does not exist inside the image; the application module is in the linked runtime and the launcher works regardless. That quirk is cosmetic and accepted.

The empty window carries the title `workthread`, is 1024x768, and shows one centred label reading `workthread 0.1.0`. It is the packaging proof, not a feature, and it holds no state.

## CI

One job with a matrix over `ubuntu-24.04`, `macos-26` (Apple silicon), and `windows-2025`. Each leg sets up Temurin 21.0.13 explicitly, because Ubuntu and Windows default to Java 17 and the macOS Intel image ships a different 21 patch. `gradle/actions/setup-gradle` restores the Gradle user home and validates the wrapper, so no separate wrapper-validation step is needed. Each leg runs the build and then the client's `jlink` task. There is no Xvfb, GTK, or jpackage prerequisite, because nothing launches. The three images are uploaded as artifacts with seven-day retention; a delivered artifact is not executable after upload, so it is for inspection only. Triggers are push to the main branch, pull request, and manual dispatch, with a concurrency group that cancels superseded runs. Permissions can be read-only; the artifact upload's scope is confirmed on the first run. Known risk: Windows path length, kept low by short module and image names.

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
