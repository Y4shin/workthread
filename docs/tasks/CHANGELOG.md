---
type: changelog
title: Task Changelog
---

# Task Changelog





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
