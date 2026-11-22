---
type: changelog
title: Task Changelog
---

# Task Changelog


## 2026-11-22, Declare the real stack and prove it resolves (declared-stack)

Every module now declares its real dependency set at the pinned versions, and the whole set resolves: JavaFX 21.0.13 in `client` through the JavaFX plugin with the host classifier, Spring Boot 4.2.0 in `api` as a platform plus one versionless starter, the MCP SDK 2.1.0 aggregator with an explicit Jackson mapper in `mcp`, sqlite-jdbc 3.54.1.0 in `adapters`, JUnit 5.14.4 and AssertJ 3.27.7 everywhere.

Decisions: `api` declares `spring-boot-starter-webmvc` so the platform's effect is observable, which settles servlet over reactive for the API effort; `api` holds JUnit 5 with `enforcedPlatform(junit-bom)`, because a soft platform loses to Spring Boot's 6.1.3 constraint.

Outcome: `./gradlew -q printPinnedModuleNames` prints the resolved module names of 33 jars, read with `ModuleFinder`, and `./gradlew clean build` is green with the configuration cache reused.

## 2026-11-22, Walking skeleton: wrapper, eight subprojects, green build (walking-skeleton)

The repository now builds: a committed Gradle 9.9.1 wrapper on Java 21, eight subprojects (`contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client`) with the inward project dependencies from the architecture's edge table, JUnit 5 and AssertJ in every module, and version 0.1.0 from one property.

Decisions: the root build hosts one module convention and no custom plugin; `contract` is declared with `api(...)` from the three inward modules and `implementation(...)` from the four surface modules, because only the inward ones expose contract types; the version catalogue is read once at script level, since the `libs` accessor does not resolve inside `subprojects { }`.

Outcome: `./gradlew clean build` is green with 41 executed tasks and the configuration cache is reused on a second run.
