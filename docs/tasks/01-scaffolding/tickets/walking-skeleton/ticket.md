---
type: ticket
subtype: feature
title: "Walking skeleton: wrapper, eight subprojects, green build"
status: stable
workflow_state: ready
blocked_by: []
size: m
---

## What to build

A clean checkout builds with the committed Gradle 9.9.1 wrapper on Java 21. The eight named subprojects exist with the inward project dependencies from the architecture, each has a JUnit 5 and AssertJ test, the version is 0.1.0 from one source, and the package root is `io.github.y4shin.workthread`. The test protocol document names the Gradle commands.

## Acceptance criteria

- [ ] `./gradlew --version` reports Gradle 9.9.1 on a Java 21 launcher.
- [ ] `./gradlew build` passes on a clean checkout.
- [ ] The build declares exactly the eight subprojects with the settled names and the inward project dependencies from the architecture's edge table.
- [ ] Every subproject's test task executes at least one JUnit 5 test that uses AssertJ.
- [ ] The version is 0.1.0 from one source, and every subproject reports it.
- [ ] The package root is `io.github.y4shin.workthread`, one subpackage per module.
- [ ] The test protocol document names the Gradle build and test commands as the always-run protocol and no longer calls the build tool provisional.
- [ ] A second `./gradlew build` reuses the configuration cache.

## Blocked by

None (can start immediately).

## Implementation notes

Each module's main package is declared by a javadoc-only `package-info.java`; no type and no product API were invented, so every 0.1 jar carries only its manifest. One test per module asserts its own package name against the architecture's literal, because there is no product behaviour to assert yet.

Edges use `api(project(":contract"))` from `core`, `adapters`, and `wiring`, and `implementation(...)` from `cli`, `mcp`, `api`, and `client`: the architecture's table names the edges, not the configurations. The version catalogue is read once at script level in the root build, because the `libs` accessor does not resolve inside a `subprojects { }` block. The wrapper was generated in a scratch directory and copied in, because Gradle 9.9.1 refuses to run the `wrapper` task in a directory with no settings file.

`.gitignore` had to un-ignore `gradle/wrapper/gradle-wrapper.jar`, because the existing `*.jar` rule would have dropped it and broken a clean checkout. `gradle.properties` also fixes `group=io.github.y4shin.workthread`, co-located with the single version source. A module descriptor cannot export a package that holds no type (`javac` rejects it with "package is empty or does not exist"), which constrains the `named-modules` ticket: its descriptors need a first type per exported package.
