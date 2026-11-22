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
