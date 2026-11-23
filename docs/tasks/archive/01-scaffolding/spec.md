---
type: spec
title: "Scaffolding: the build, eight modules, and the first shippable image"
status: stable
---

## Problem Statement

The project has a settled design for twelve child efforts and no build to carry them. A developer who starts the first child finds no Gradle files, no Java source, and no test harness. Every later child needs the same foundation: eight modules whose dependencies point inward, a core that cannot quietly acquire Spring or JavaFX, a test harness, and a packaging route proven before any feature depends on it.

Without that foundation on the first commit, three failures become likely. Standalone mode drifts into needing a framework, because nothing stops the core from importing one. The JavaFX classpath trap is discovered late, because the runtime only warns when JavaFX is on the classpath and fails much later. And packaging is retrofitted after features exist, when the module-path rule is expensive to restore. The project also has to start its version numbering and its additivity rule at the first release, or a later version may remove an interface an earlier one shipped.

## Solution

One Gradle 9.9.1 Kotlin DSL multi-project build on a Java 21 toolchain, holding the eight named modules, with Java module descriptors on the seven that must reach the desktop image and the Spring Boot API left on the classpath. The build enforces the dependency rules itself: an outward dependency fails, Spring or JavaFX in `core` fails, and a JavaFX artifact on a runtime classpath fails. JUnit 5 and AssertJ are wired into `./gradlew build`. The version is 0.1.0, an annotated tag marks each release, and a committed snapshot of `contract`'s exported API fails the build when an export disappears.

The packaging proof is one empty JavaFX window shipped as a `jlink` image: title `workthread`, 1024x768, one centred label reading `workthread 0.1.0`. It carries no product behaviour. It exists so the module-path rule and the image build are broken on the first commit, not after the first feature. GitHub Actions builds and packages on Ubuntu, macOS, and Windows. The image launch is verified by hand once per platform, because the pipeline never starts a toolkit.

## User Stories

1. As a developer picking up a clean checkout, I want `./gradlew build` to pass, so that every later effort starts from something that compiles.
2. As a developer, I want the eight modules to exist with their final names, so that later efforts add code rather than rearranging the build.
3. As a developer, I want each module's declared dependencies to match the architecture's edge table, so that an accidental dependency is a build failure rather than a review comment.
4. As a developer, I want the build to refuse a dependency that points outward, so that the inward dependency rule survives contact with deadlines.
5. As a developer, I want the build to refuse Spring or JavaFX in `core`, so that standalone mode keeps working without a framework.
6. As a developer, I want the build to refuse a JavaFX artifact on a runtime classpath, so that the documented-as-unsupported classpath configuration cannot become the default by accident.
7. As a developer, I want a failure message that names the offending module and dependency, so that I can fix it without reading the check's source.
8. As a developer, I want one version catalogue, so that no two modules declare different versions of the same library.
9. As a developer, I want seven modules to be named Java modules with `module-info.java`, so that the client's dependency chain can be linked into an image.
10. As a developer, I want the API module to stay on the classpath, so that Spring Boot's classpath model is not fought with automatic modules.
11. As a developer, I want the declared stack to resolve on the first commit, so that dependency and module-path friction surfaces now rather than inside a feature effort.
12. As a developer, I want the Spring Boot BOM imported only in the API module and only as a platform, so that it cannot silently override the store driver or the test library versions elsewhere.
13. As a developer, I want the MCP SDK aggregator declared in the MCP module, so that its Jackson and schema-validator closure is known before the MCP effort starts.
14. As a developer, I want the SQLite driver declared in the adapters module, so that the store effort inherits a resolving dependency.
15. As a test author, I want JUnit 5 and AssertJ wired into every production module, so that tests exist from the first commit rather than being retrofitted.
16. As a test author, I want to run one test class alone, so that a focused change does not need the whole suite.
17. As a test author, I want no unit test to start a JavaFX toolkit, so that the pipeline needs no display and no Xvfb.
18. As the person who ships, I want one command to build the image, so that packaging is reproducible without manual jlink flags.
19. As the person who ships, I want the image's launcher to open the empty window, so that the module path, the JavaFX natives, and the launcher are proven together.
20. As the person who ships, I want the window to show the build's version, so that I can tell which build I launched.
21. As the person who ships, I want the image size measured and recorded, so that later changes that inflate it are visible.
22. As a maintainer, I want the version to live in one place, so that every module reports the same version and the image label matches it.
23. As a maintainer, I want an annotated tag per release, so that the version in a build can be traced to a commit.
24. As a maintainer, I want a committed snapshot of `contract`'s exported API, so that a removal is a diff before it is a build failure.
25. As a maintainer, I want the build to fail when an exported type or member disappears or changes shape, so that the additivity rule is enforced rather than remembered.
26. As a maintainer, I want a fixture that proves the additivity check fails, so that the check cannot pass by being broken.
27. As a maintainer, I want the additivity check scoped to `contract`, so that the surface I police is the one the product promises.
28. As a CI maintainer, I want the same build and image commands on Ubuntu, macOS, and Windows, so that a platform-specific failure is a build failure.
29. As a CI maintainer, I want the JDK pinned to 21.0.13 on every leg, so that the image's runtime does not silently differ per platform.
30. As a CI maintainer, I want the Gradle wrapper validated and the dependency cache restored, so that CI is both honest about the wrapper and quick.
31. As a CI maintainer, I want the pipeline to avoid Xvfb, GTK packages, and jpackage prerequisites, so that it stays cheap and fast.
32. As a CI maintainer, I want the three images uploaded with short retention, so that a failure can be inspected without filling the storage allowance.
33. As a CI maintainer, I want pinned runner labels rather than `-latest`, so that an OS migration does not change the build one morning.
34. As a person running the pipeline, I want the macOS leg on Apple silicon, so that the macOS image is the platform users actually run.
35. As a developer, I want a recorded manual launch check for each platform, so that the one thing CI does not prove has an owner and a place.
36. As a developer, I want the test protocol document to name the Gradle commands, so that the test runner and every later effort run the right suite.
37. As a developer, I want the development environment document to state the image size and its runtime prerequisites, so that a new machine knows what the image needs.
38. As a developer, I want the build to reuse the configuration cache, so that repeated builds stay quick without special flags.
39. As a later effort, I want a repository that builds without product code, so that my ticket can add behaviour without also fixing the foundation.
40. As a later effort, I want the known unproven items recorded, so that I do not mistake the first CI run for a regression.

## Implementation Decisions

**Build and toolchain.** Gradle 9.9.1 through the committed wrapper, Kotlin DSL, Java 21 toolchain per module. One version catalogue holds every library and plugin version. The configuration cache is on, because the packaging prototype proved the plugin set is cache-clean. The root build hosts the build checks and the API snapshot task; there is no separate build-logic project.

**Modules and edges.** Eight subprojects: `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client`. The architecture's `May depend on` table is the authoritative edge list. `contract` depends on nothing; `core` on `contract`; `adapters` on `contract`; `wiring` on `contract`; `cli`, `mcp`, `api`, and `client` on `contract` and `wiring`. No surface module depends on another surface module. A new module requires an architecture change first.

**Java modules.** `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, and `client` carry `module-info.java` and are named modules. `api` has no descriptor and runs on the classpath, because Spring Boot's classpath model fights named modules. The package root is `io.github.y4shin.workthread`, one subpackage per module.

**Declared stack at 0.1.** JavaFX 21.0.13 in `client`, through the JavaFX Gradle plugin 0.1.0, which supplies the host's platform classifier. Spring Boot 4.2.0 in `api`, brought in with the dependency platform only: no Spring Boot plugin and no dependency-management plugin, because the BOM alone is enough and the management plugin would override the store driver version. The MCP SDK 2.1.0 aggregator in `mcp`, with the JSON mapper supplied explicitly. sqlite-jdbc 3.54.1.0 in `adapters`. JUnit 5.14.4 and AssertJ 3.27.7 for tests. `org.beryx.jlink` 4.2.0 for packaging.

**Version and dependency facts that shaped the list.** JavaFX stays on 21.0.13: the 21.0.13.1 re-spin changes only `javafx.web`, which the client never loads. The MCP SDK's `mcp` jar is a named module, but its payload jars `mcp-core` and `mcp-json-jackson3` are automatic modules; `jlink` refuses automatic modules, so no image can contain the MCP surface. Spring Boot's BOM manages JUnit 6.1.3 and sqlite-jdbc 3.53.4.0, so it is applied only in `api`, where JUnit 5 is declared explicitly. The Jackson 2 versus 3 question is settled: both sides use Jackson 3 (`tools.jackson` 3.1.8 from Spring Boot, 3.1.4 from the MCP SDK, resolved upward), both reuse the 2.21 annotations artifact, and no exclusion is needed. `api` never sees the MCP SDK, because surface modules never depend on each other.

**The dependency checks.** Two root-build Kotlin checks, attached to `check`. The first reads each module's declared dependencies and fails when a module depends on something its edge row forbids, naming the module and the dependency; it also fails when `core` declares Spring or JavaFX. The second inspects the runtime classpaths and fails when a JavaFX artifact appears on one. The messages name the offending module and dependency. No third-party rule plugin.

**Version and additivity.** `0.1.0` lives in one property, and an annotated git tag marks each release. A committed snapshot holds every exported package of `contract`; `check` compares the current surface against it and fails when an exported type or member disappears or changes shape, naming what changed. Because 0.1 exports almost nothing, a committed fixture reproduces a removal so the check's failure path is proven. The snapshot covers `contract` only at 0.1.

**Packaging.** The `org.beryx.jlink` plugin builds the runtime image from the client module with `--strip-debug --compress zip-6` and a launcher named `workthread`. The prototype that proved this line produced the configuration below, which is the decision-rich core: no `mergedModule` block and no module-path inference setting were needed, and the `javafx` block is what puts JavaFX on the module path.

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

**The empty window.** Title `workthread`, 1024x768, one centred label reading `workthread 0.1.0`. It is the packaging proof, not a feature, and it holds no state.

**CI.** One job with a matrix over `ubuntu-24.04`, `macos-26` (Apple silicon), and `windows-2025`. Each leg sets up Temurin 21.0.13 explicitly, because Ubuntu and Windows default to Java 17 and the macOS Intel image ships a different 21 patch. `gradle/actions/setup-gradle` restores the Gradle user home and validates the wrapper, so no separate wrapper-validation step is needed. Each leg runs the build and then the client's `jlink` task. There is no Xvfb, GTK, or jpackage prerequisite, because nothing launches. The three images are uploaded as artifacts with seven-day retention; a delivered artifact is not executable after upload, so it is for inspection only. Triggers are push to the main branch, pull request, and manual dispatch, with a concurrency group that cancels superseded runs. Permissions can be read-only; the artifact upload's scope is confirmed on the first run. Known risk: Windows path length, kept low by short module and image names.

**Documents this effort brings current.** The test protocol document names the Gradle commands instead of the provisional Maven ones. The development environment document records the image size, its GTK3 and X11 prerequisite, and the manual launch check per platform.

## Testing Decisions

**What makes a good test.** A test drives external behaviour and asserts an observable result. It does not reach into internals, assert on the shape of a private call, or depend on the order of unrelated tests. For this effort the observable results are the build's own behaviour: whether a forbidden dependency fails the build, whether a removed export fails the snapshot comparison, and whether the suite runs without a display.

**What is tested at 0.1.** Every production module carries at least one JUnit 5 test, so the test wiring is proven per module rather than once. The two dependency checks are exercised by fixtures that declare a forbidden dependency and expect the build to fail; the failure path is the behaviour, so the fixtures assert on the failure and its message. The additivity check is exercised by a committed fixture that removes an export and expects the comparison to fail. The image build is not a unit test: the pipeline builds it, and the launch is a recorded manual check. No test starts a toolkit, because that would drag a display into the pipeline.

**Which modules are tested.** All eight are compiled and at least smoke-tested by the suite. `contract` additionally carries the API snapshot's subject. The `core` checks are exercised through a fixture subproject or a temporary project the test builds, so the production build stays free of deliberately broken modules.

**Prior art.** None exists in the repository: this effort writes the first tests. The convention later efforts follow is the parent architecture's seams: tests meet the code at a port, a store, a clock, a notifier, the command line, the tool surface, or the API document. The test protocol document holds the commands the suite gate runs.

**Test protocol.** The always-run commands become the Gradle build and the full test suite. The provisional Maven commands and the note that the build tool is undecided are removed. The single-class command names the Gradle test filter.

## Out of Scope

- Any product behaviour: no domain model, no store, no use case, no command, no daemon. The module skeletons hold only what the build and the packaging need.
- Automated publishing: no release job, no artifact upload to a registry. The version rule is enforced by the build, not by publishing.
- Installers: this effort ships none. The deliverable is the runtime image, and a later effort may add installers without re-opening the non-goals.
- Surface command names and output shapes: settled by a later effort.
- The system tray, and any real desktop behaviour beyond the empty window.
- Cross-building an image for another operating system: each pipeline leg builds on its own host.
- The verification of the 73 requirement rows is an obligation of this effort's close, not of this specification's text.

## Further Notes

The effort's definition of done is the 73 rows the requirements register holds for `01-scaffolding`, recorded in the effort's map under `## Requirements` at `docs/tasks/01-scaffolding/map.md`. The register is read only through its lookup script: `python3 docs/v1/requirements-lookup.py effort 01-scaffolding` prints every row for this effort with the register's header, and `python3 docs/v1/requirements-lookup.py id T01-022` prints one row with the same header. Any requirements lookup must use the script; do not open, grep, or filter `docs/v1/requirements-directory.csv` by hand. If a lookup the script does not offer is needed, extend the script first, with the operator's approval. Every row gets its own verdict, with a pointer to evidence, before the effort may close; a passing build does not satisfy a row, and the map remains the record of this effort's obligation.

Two items remain unproven when the specification is written. Only Linux x86_64 was exercised for the image, so the macOS and Windows image builds are proven by the first pipeline run. The jar module names come from each project's descriptor at its release tag, and the first build that resolves the dependencies should confirm them by printing the descriptors.

The image-size expectation is about 67.6 MB on Linux x86_64, not the 66.3 MB of the old packaging prototype. The difference is the header files, the man pages, and `jdk.jfr`. If the old size is ever wanted back, `--no-header-files --no-man-pages` removes about 515 KB.

Runner labels age. The pipeline pins exact labels and never `-latest`, and the runner-images table is re-read when a label ages; that is how the macOS leg became `macos-26` rather than `macos-15`.

The MCP SDK's automatic modules are a permanent constraint on the MCP surface, not a 0.1 detail: a later effort that tries to put the MCP server into an image will hit `jlink`'s refusal. Record it where that effort will read it.

The JavaFX 21.0.13.1 re-spin exists and is deferred. Bumping is one version string and one image build, and it can happen when a reason appears.

> The architecture content in this specification is archival: the living architecture document is [architecture.md](architecture.md); update it there, not here.
