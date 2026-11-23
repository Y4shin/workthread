---
type: map
title: "Scaffolding: the build, eight modules, and the first shippable image"
status: deprecated
origin_effort: planner-v1
ready_for_spec: true
---
# Scaffolding: the build, eight modules, and the first shippable image

## Destination

Done looks like this. A clean checkout builds with Gradle 9.9.1 on Java 21. The eight named modules exist, and the build refuses an outward dependency, a Spring or JavaFX dependency in `core`, and a JavaFX artifact on the classpath. JUnit 5 and AssertJ run inside `./gradlew build`. The version is 0.1.0, an annotated tag marks each release, and a `contract` API snapshot fails the build when a shipped export disappears. `./gradlew jlink` produces an image whose launcher opens one empty JavaFX window on Linux, macOS, and Windows. GitHub Actions runs the build and the image build on those three platforms. Nothing in the image is product behaviour.

## Constraints

- Java 21 LTS pinned by toolchain. Gradle 9.9.1, Kotlin DSL, per-module toolchains.
- Eight subprojects: `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client`. Dependencies point inward, and `core` sees only `contract`.
- JavaFX 21 on the module path, enforced by the build, because the runtime only warns when it is on the classpath. Java 21 caps the usable line at 23.0.2.
- Packaging is `org.beryx.jlink` 4.2.0 with `--strip-debug --compress zip-6`. The plugin must be at least 3.1.2, the first release that runs on Gradle 9; 4.2.0 is current. The Linux x86_64 image is about 67.6 MB with these options, which includes `jdk.jfr` because `jdeps` follows JavaFX's JFR logging classes. The image still needs the host's GTK3 and X11 libraries at run time.
- The MCP SDK is `io.modelcontextprotocol.sdk:mcp` 2.1.0, where the `mcp` artifact is an aggregator over `mcp-core` and `mcp-json-jackson3`; the JSON mapper is supplied explicitly. `mcp-core` and `mcp-json-jackson3` are automatic modules, so `jlink` refuses them and no image can contain the MCP surface. The 0.1 image is the client only.
- Spring Boot 4.2.0's BOM manages `junit-jupiter` 6.1.3 and `sqlite-jdbc` 3.53.4.0, both of which contradict this map's pins. Import it with `platform()` in `api` only, and never apply `io.spring.dependency-management`.
- The repository has no build files and no Java source yet.
- This effort carries no product behaviour, no record, and no use case. It does not decide what any surface command is called.
- First in the build order, blocked by nothing, release 0.1 Capture. Its successor is `02-work-store`.

## Non-goals

- **Any product behaviour.** No domain model, no store, no use case, no command, no daemon. The module skeletons hold only what the build and the packaging need.
- **Automated publishing.** No release job and no artifact upload to a registry. The version rule is documented and enforced by the build, not by publishing.

Installers (deb, rpm, MSI) are not ruled out here, and this effort ships none: the deliverable is the `jlink` runtime image. CI is in scope.

## Non-negotiable facts

**Success test:** `./gradlew build` passes on a clean checkout with Java 21; the build fails when `core` declares Spring or JavaFX, or when a JavaFX artifact reaches the classpath; and the jlink image's launcher opens one empty window.

- The eight subprojects keep their names and the inward rule. The architecture's `May depend on` table is the authoritative edge list. A new module needs an architecture change first.
- Java 21 toolchain per module, Gradle 9.9.1 through the committed wrapper, Kotlin DSL.
- JavaFX 21.0.13 on the module path only, through `org.openjfx.javafxplugin` 0.1.0. The build refuses JavaFX on the classpath. The image comes from `org.beryx.jlink` 4.2.0 with `--strip-debug --compress zip-6`. The client module must export the package that holds its `Application` subclass, or JavaFX cannot construct it.
- `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, and `client` are named JPMS modules with `module-info.java`. `api` runs on the classpath because Spring Boot's classpath model fights named modules.
- Declared stack at 0.1: JavaFX 21.0.13 in `client`, Spring Boot 4.2.0 in `api` through `platform()` only, the MCP SDK 2.1.0 aggregator in `mcp`, sqlite-jdbc 3.54.1.0 in `adapters`, JUnit 5.14.4 and AssertJ 3.27.7 for tests. No Spring Boot Gradle plugin and no dependency-management plugin at 0.1.
- The MCP SDK's `mcp` jar is a named module, but `mcp-core` and `mcp-json-jackson3` are automatic modules, and `jlink` refuses an automatic module. No image can contain the MCP surface.
- Jackson 3 is the shared JSON line: Spring Boot brings `tools.jackson` 3.1.8 and the MCP SDK asks for 3.1.4, so Gradle resolves up with no exclusion. Both reuse `jackson-annotations` 2.21.x. `api` sees only Spring Boot, because surface modules never depend on each other.
- The two refusal rules are root-build Kotlin checks attached to `check`. No third-party rule plugin.
- Package root `io.github.y4shin.workthread`, one subpackage per module.
- Version 0.1.0 in `gradle.properties`, an annotated git tag per release, a committed snapshot of every exported package of `contract`, and a `check` failure when an export disappears or changes shape. A committed fixture proves the failure path.
- The window carries the title `workthread`, is 1024x768, and shows one centred label reading `workthread <version>`.
- CI uses pinned runner labels, not `-latest`, and one macOS leg on Apple silicon. Each leg runs `./gradlew build` and then `./gradlew :client:jlink`. CI never launches the image and needs no Xvfb or GTK step, so unit tests must not start a toolkit. The launch is verified by hand once per platform and recorded in `docs/dev-env.md`.

## Requirements

These 73 rows are this effort's definition of done, taken from the `effort` column of the requirements register and copied from the parent architecture's contract for scaffolding:

```text
T01-022, T03-001, T03-003, T03-004, T03-012, T04-001..T04-004, T04-007,
T04-009, T04-013, T04-015..T04-018, T06-001..T06-004, T06-008, T06-012,
T06-013, T07-004, T07-008..T07-010, T09-004, T09-007, T11-001..T11-003,
T11-007, T11-018..T11-020, T11-032, T11-035, T11-036, T11-038, T11-039,
T11-044..T11-049, T11-073, T11-079..T11-081, T11-088, T11-090, T17-035,
T17-036, T21-005, T22-002..T22-004, T22-006, T22-008, T22-009, T23-008,
T24-020, T25-041, MAP-007, MAP-014, MAP-016, MAP-019, MAP-025..MAP-027,
MAP-029, MAP-C04, MAP-C07
```

The register is `docs/v1/requirements-directory.csv`, and the only supported way to read it is the lookup script `docs/v1/requirements-lookup.py`. `python3 docs/v1/requirements-lookup.py effort 01-scaffolding` prints every row for this effort with the register's header; `python3 docs/v1/requirements-lookup.py id <id>` prints one row with the same header. Any requirements lookup must use the script: do not open, grep, or filter the register by hand. If a lookup the script does not offer is needed, extend the script first, with the operator's approval.

Every row is verified one by one before this effort may close. The closing ticket records one verdict per row id, each with a pointer to the evidence in the repository. A group verdict does not cover a row, and a passing build does not by itself satisfy one.

## Fog

## Decisions so far

- The effort reuses the parent architecture (`docs/tasks/planner-v1/architecture.md`) and `CONTEXT.md`. It introduces no product vocabulary.
- `non-negotiables` settled the non-goals, the success test, the frozen build facts, the seven-named-module split with `api` on the classpath, the declared stack, the root-build enforcement checks, the package root, the version and API-snapshot rule, the window content, and a three-platform pipeline that builds but does not launch the image. Rejected options are in that task's Notes.
- The packaging plugin moves from the parent prototype's 2.26.0 to 4.2.0, because 2.26.0 does not run on Gradle 9.
- `packaging-stack` proved the line end to end: plugin 4.2.0 on Gradle 9.9.1 with JDK 21 builds and runs a JavaFX 21.0.13 image, first try, warning-free, configuration-cache clean, in about 9 to 15 s warm and 27 s cold. The image is 67,608,316 B. The route is `org.openjfx.javafxplugin` 0.1.0; a hand-declared module path works only with an explicit platform classifier, because the unclassified artifact is a stub jar with no `module-info`. `jdk.jfr` is in the module set because `jdeps` follows JavaFX's JFR logging classes. Only Linux x86_64 was exercised; macOS and Windows are proven by the first CI run.
- `ci-shape` settled the pipeline: `ubuntu-24.04`, `macos-26` (Apple silicon), and `windows-2025`; `actions/setup-java@v6` with `temurin` pinned to `21.0.13`; `gradle/actions/setup-gradle@v6`, which also validates the wrapper; `./gradlew build`, then `./gradlew :client:jlink`; artifact upload with `retention-days: 7`. No GTK, Xvfb, or jpackage prerequisites. The setup step is required because ubuntu and windows default to Java 17, and the macOS Intel image ships a different 21 patch.
- The macOS label is `macos-26`, corrected from the research's `macos-15`: macOS 26 is the current image (`macos-latest` points to it and `macos-26` ships JDK 21.0.13 as its default), while macOS 15 is the older of the two supported images and therefore first in line for deprecation when macOS 27 reaches GA. `ubuntu-24.04` remains correct because `ubuntu-latest` has not moved to 26.04, and `windows-2025` is current. The calibration: pin exact labels, never `-latest`, and re-check the runner-images table when a label ages. The correction is appended to the `ci-shape` task's Notes.
- `dependency-pins` confirmed the version set and three build rules. JavaFX stays 21.0.13: the 21.0.13.1 re-spin changes only `javafx.web`, which the client never loads, and the JavaFX plugin has not been tried with a four-segment version. JUnit 5.14.4 and AssertJ 3.27.7 stay. The MCP SDK's payload jars are automatic modules, so the MCP surface can never be linked into an image. Spring Boot's BOM manages JUnit 6.1.3 and sqlite-jdbc 3.53.4.0, so it is applied with `platform()` in `api` only and JUnit 5 is declared explicitly there. The Jackson 2/3 clash does not exist: both sides use Jackson 3 and share the 2.21 annotations artifact. The module names come from each project's descriptor at its release tag; the first resolving build confirms them with `jar --describe-module`, and that check is recorded in the task's Notes.
