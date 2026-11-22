---
type: changelog
title: Task Changelog
---

# Task Changelog

## 2026-11-22, Walking skeleton: wrapper, eight subprojects, green build (walking-skeleton)

The repository now builds: a committed Gradle 9.9.1 wrapper on Java 21, eight subprojects (`contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `api`, `client`) with the inward project dependencies from the architecture's edge table, JUnit 5 and AssertJ in every module, and version 0.1.0 from one property.

Decisions: the root build hosts one module convention and no custom plugin; `contract` is declared with `api(...)` from the three inward modules and `implementation(...)` from the four surface modules, because only the inward ones expose contract types; the version catalogue is read once at script level, since the `libs` accessor does not resolve inside `subprojects { }`.

Outcome: `./gradlew clean build` is green with 41 executed tasks and the configuration cache is reused on a second run.
