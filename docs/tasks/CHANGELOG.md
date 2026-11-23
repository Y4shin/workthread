---
type: changelog
title: Task Changelog
---

# Task Changelog









## 2026-11-23, Enforce the edge table in test scope (enforce-edge-table-in-test-scope)

`verifyModuleDependencies` now reads every module's `test` source set (`testCompileClasspath` and `testRuntimeClasspath`) in the same `afterEvaluate` read that already covered `main`, and judges it with the same rule and the same refusal message. A project dependency outside the module's edge row, declared as `testImplementation`, now fails `check`.

The test scope is read as the delta against main, because `testImplementation` extends `implementation`: an inherited declaration is judged once, in main scope. `core`'s Spring and JavaFX ban now covers its test scope as well. The gathering moved into three shared helpers and the rule body into `refusalsFor`, so one implementation serves both scopes.

`verifyDependencyRuleFixtures` went from 7 to 10 cases: a test-scope project edge outside the row, Spring in `core`'s test scope, and the matching acceptance that guards the allowed edge. The count is printed from `cases().size`.

Outcome: `./gradlew build` is green with every check enabled and the configuration cache reused. No plugin, no build-logic project, and no new task.

## 2026-11-23, Record the manual launch check on each platform (manual-launch-check)

The launch check now runs against the images the pipeline builds. `docs/dev-env.md` records the three artifacts, their unpacked sizes, and both launchers, `bin/workthread` and `bin/workthread.bat`.

Result: Linux is verified. The `image-ubuntu-24.04` artifact from run 45089591551 opens a 1024x768 window titled `workthread` with one centred label reading `workthread 0.1.0`, run inside an `ubuntu:24.04` container. macOS and Windows were not launched and stay unverified, by the operator's decision.

Two findings recorded: this NixOS host cannot launch a Temurin-linked image directly, because the store GTK needs a newer glibc than the image's loader resolves; and the macOS leg linked JDK 21.0.13.1 while the other two linked 21.0.13, so the pinned patch does not hold on all legs.

## 2026-11-23, Build and package on three platforms in CI (three-platform-ci)

`.github/workflows/ci.yml` builds and packages on `ubuntu-24.04`, `macos-26`, and `windows-2025`. Each leg sets up Temurin 21.0.13, lets `gradle/actions/setup-gradle` restore the Gradle home and validate the wrapper, runs `./gradlew build` and `./gradlew :client:jlink`, then uploads the image as an artifact kept seven days.

Decisions: `fail-fast: false`, so one red leg does not hide the other two; read-only permissions, confirmed enough by the first run's upload; superseded runs cancelled by a concurrency group; no Xvfb, GTK, or jpackage step, because nothing launches. A root check now reads the workflow and refuses a label, JDK patch, artifact, or concurrency drift from the design, and the file is therefore checked as text.

Outcome: the first run is green on all three legs (run 45089591551 on pull request 1) and uploads three images: 43 971 538 bytes on Ubuntu, 41 442 989 on Windows, 39 563 002 on macOS.

## 2026-11-23, Start 0.1.0 and the contract API snapshot (version-and-api-snapshot)

The version is 0.1.0 from one property, and three enforcers read it: every jar carries it in its manifest, the client's window label renders it from a generated resource, and `verifyVersion` refuses a jar or a source file that reports another number. `check` now compares `contract`'s exported surface against a committed snapshot.

Decisions: the snapshot holds packages plus public and protected types and members, and it compares one-directionally, so an addition passes and is recorded by running `generateContractApiSnapshot`; the window's label is no longer a typed literal, which closes the coupling the image ticket forwarded.

Outcome: a removed or reshaped export fails `check` with the element name and both shapes, fourteen fixtures prove the failure paths, `docs/releasing.md` records who tags and when, no tag was created, and `./gradlew clean build` is green with 74 executed tasks.

## 2026-11-23, Ship the empty window as a jlink image (client-image)

`./gradlew :client:jlink` now builds a runtime image whose launcher opens one empty window: title `workthread`, 1024x768, one centred label reading `workthread 0.1.0`. The client's placeholder became the real JavaFX `Application` subclass, and its descriptor gained `requires javafx.controls`.

Decisions: the `application` plugin joins `client` with `mainModule` and `mainClass`, because `jlink` cannot link or start a module without them; both plugin versions moved into the version catalogue.

Outcome: the Linux x86_64 image measures 67 611 290 bytes, launches under a virtual display, and needs GTK3, X11, `libGL`, `libXxf86vm`, and a writable `~/.openjfx/cache`. The build stays warning-free with the configuration cache reused; `:client:assemble` also emits two distributions that nothing consumes.

## 2026-11-22, Make the seven modules named Java modules (named-modules)

The seven modules that reach the image are now named Java modules. Each carries a `module-info.java` under `io.github.y4shin.workthread.<module>`, requires exactly its edge row, and exports its package; `api` stays on the classpath with no descriptor.

Decisions: the module name is the package name, derived once from `packageRoot` in the root build, so the name and the single export cannot drift. Each module carries one placeholder type, because a descriptor cannot export an empty package; the client's is `WorkthreadApplication`, the stand-in the image ticket replaces in place.

Outcome: `./gradlew clean build` is green with 66 executed tasks and the configuration cache reused, and each built jar reports its expected module name.

## 2026-11-22, Enforce the dependency rules in the build (dependency-checks)

The build now refuses a dependency that leaves the architecture's edge table, Spring or JavaFX in `core`, and a JavaFX artifact on any runtime classpath other than the client's. Each refusal names the module, the dependency, and the allowed row.

Decisions: the runtime classpath rule exempts `client`, because the declared stack puts JavaFX there and the literal rule would fail the real build; the edge rule judges project dependencies only, since `adapters`, `mcp`, `api`, and `client` legitimately declare libraries; the client's exemption is recorded in the architecture, not just in the build.

Outcome: seven fixtures prove the refusals, the fixture task rides `./gradlew check`, and `./gradlew clean build` stays green with 58 executed tasks and the configuration cache reused. Test scope is not covered: a forbidden edge declared as `testImplementation` still passes.

## 2026-11-22, Declare the real stack and prove it resolves (declared-stack)

Every module now declares its real dependency set at the pinned versions, and the whole set resolves: JavaFX 21.0.13 in `client` through the JavaFX plugin with the host classifier, Spring Boot 4.2.0 in `api` as a platform plus one versionless starter, the MCP SDK 2.1.0 aggregator with an explicit Jackson mapper in `mcp`, sqlite-jdbc 3.54.1.0 in `adapters`, JUnit 5.14.4 and AssertJ 3.27.7 everywhere.

Decisions: `api` declares `spring-boot-starter-webmvc` so the platform's effect is observable, which settles servlet over reactive for the API effort; `api` holds JUnit 5 with `enforcedPlatform(junit-bom)`, because a soft platform loses to Spring Boot's 6.1.3 constraint.

Outcome: `./gradlew -q printPinnedModuleNames` prints the resolved module names of 33 jars, read with `ModuleFinder`, and `./gradlew clean build` is green with the configuration cache reused.

## 2026-11-22, Walking skeleton: wrapper, eight subprojects, green build (walking-skeleton)

The repository now builds: a committed Gradle 9.9.1 wrapper on Java 21, eight subprojects (`contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client`) with the inward project dependencies from the architecture's edge table, JUnit 5 and AssertJ in every module, and version 0.1.0 from one property.

Decisions: the root build hosts one module convention and no custom plugin; `contract` is declared with `api(...)` from the three inward modules and `implementation(...)` from the four surface modules, because only the inward ones expose contract types; the version catalogue is read once at script level, since the `libs` accessor does not resolve inside `subprojects { }`.

Outcome: `./gradlew clean build` is green with 41 executed tasks and the configuration cache is reused on a second run.
