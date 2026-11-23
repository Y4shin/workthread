---
type: review
title: End-of-effort review of 01-scaffolding, after the ninth ticket
pass: end-of-effort
dispositioned: false
---

## What was reviewed

`git diff main...HEAD` on `effort/01-scaffolding`: 84 files, +5 631 / -55, at the fixed point `f780269` and head `d44cac3`. The specification source was the nine ticket docs, their deviation reports, and the effort's architecture document; the standards source was `AGENTS.md`, `CONTEXT.md`, `docs/testing.md`, `docs/releasing.md`, and the Fowler smell baseline. Both axes ran read-only on fresh context and are reported side by side.

This pass ran after the ninth ticket, `enforce-edge-table-in-test-scope`, landed. It replaces the review recorded after the first eight tickets; that review's findings were dispositioned into `review.md`, and its text stays in the history at `c168667`.

## The effort holds

The reviewer re-ran the build rather than reading the log: `./gradlew build` green with the configuration cache reused, every fixture family printing its count, and `printPinnedModuleNames` printing 33 rows. The eight subprojects, the edge rows, the seven descriptors and `api`'s absence, the pinned versions, the pipeline's labels and steps, the `jlink` options and launcher, the protocol documents, and the `ContractPlaceholder` snapshot all match the specification. The new test-scope refusal behaves as its ticket states: a project dependency outside the row declared as `testImplementation` fails with the module, the dependency, and the row in the message.

## Fixed before this review was recorded

Both axes named the same defect: the architecture document said test scope is not read and counted seven dependency fixtures, while the build read both source sets and held ten. The architecture writer landed that correction in this run, together with a ninth "What each ticket landed" entry and the fixture-convention sentence, so the document now matches the tree.

## Per-axis worst issue

- **Standards worst issue:** `gradle/libs.versions.toml` and the architecture both claim the catalogue is "the only place a library or plugin version appears", while six `*StackTest` classes, two root-build jar-name fixtures, and `PINNED_JDK` restate pinned versions. A bump edits eight places.
- **Spec worst issue:** the macOS and Windows manual launch checks are unmet, so the map's "verified by hand once per platform" holds on Linux only.

## Open findings

Recorded for triage at the next finalize. None blocks the effort from closing on the operator's decision.

## Dispositions

- `CONTEXT.md` has no entry for `contract`, `wiring`, or `adapters`, against `AGENTS.md`'s rule that CONTEXT.md is the glossary for naming.: not yet triaged
- `gradle/libs.versions.toml` and the architecture both say the catalogue is the only place a version appears, while six `*StackTest` classes, two root-build fixtures, and `PINNED_JDK` restate pinned versions.: not yet triaged
- `docs/dev-env.md` still opens its reproduction section with "Not possible yet. When the first module lands", and eight modules have landed.: not yet triaged
- Four `cases()` builders repeat the same `FixtureCase(what = ..., expectedRefusal = ..., rule = ...)` block, differing only in the rule constructor.: not yet triaged
- The macOS and Windows manual launch checks are unmet, with the reason recorded, so the effort closes on one of three platforms.: not yet triaged
- Long sentences in the architecture prose and the changelog run against `AGENTS.md`'s short-sentence rule.: not yet triaged
- `modulesOnTheClasspath` and `moduleRowsCarryingJavaFx` are one-element sets read at one site each, where an equality test would do.: not yet triaged
