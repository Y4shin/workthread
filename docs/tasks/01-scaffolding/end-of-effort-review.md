---
type: review
title: End-of-effort review of 01-scaffolding
pass: end-of-effort
dispositioned: false
---

## What was reviewed

`git diff main...HEAD` on `effort/01-scaffolding`: 80 files, 4 867 insertions, 34 deletions, at the fixed point `f780269`. The specification source was the eight ticket docs plus the effort's architecture document. The standards source was `AGENTS.md`, `CONTEXT.md`, `docs/testing.md`, `docs/releasing.md`, and the Fowler smell baseline. Both axes ran read-only on fresh context and are reported side by side.

## The effort holds

The reviewer re-ran the build rather than reading the log: `./gradlew --offline build` green with the configuration cache reused, the fixture families holding their counts, and `printPinnedModuleNames` printing 33 rows. The eight subprojects, the edge rows, the seven descriptors and `api`'s absence, the pinned versions, the pipeline's labels and steps, the `jlink` options and launcher, the protocol documents, and the `ContractPlaceholder` snapshot all match the specification.

## Fixed before this review was recorded

The end-of-effort coherence pass landed after the review and closed three of its findings: `AGENTS.md` no longer says the modules are not scaffolded, the base class is `BuildRule` rather than `DependencyRule`, and the three duplicated fixture runners collapsed into one registration loop over `FixtureChecks`. `docs/testing.md` also gained the sentence that names `./gradlew build` as the gate, which closed the finding that `./gradlew test` alone could look green on a tree the gate refuses.

## Per-axis worst issue

The standards axis named the stale `AGENTS.md` line, which is fixed. The specification axis named the macOS leg linking JDK 21.0.13.1 against a pinned 21.0.13, which is now recorded in the architecture and in `docs/dev-env.md` but not repaired.

## Open findings

Recorded for triage at finalize. None blocks the effort from closing on the operator's decision, and each is one line.

## Dispositions

- AGENTS.md still describes "three target shapes" and neither the eight modules nor the edge table, so the repository's own description of its shape is behind the build.: not yet triaged
- CONTEXT.md has no entry for `contract`, `wiring`, or the plural `adapters`, against the rule that CONTEXT.md is the glossary for naming.: not yet triaged
- Pinned versions are restated outside the version catalogue in six `*StackTest` classes, two root-build jar-name fixtures, and `PINNED_JDK`, so a version bump edits six or more places.: not yet triaged
- `client-image`'s ticket note still records 67 611 290 bytes and calls it a match with the architecture, while the image the branch ships is 67 612 510 bytes.: not yet triaged
- `ForbiddenCiStepRule` refuses `fakeroot`, `dpkg-deb`, and `wix` beyond the Xvfb, GTK, and jpackage step the architecture names, and the workflow sets `if-no-files-found: error` which no ticket or architecture line asks for.: not yet triaged
- Test-scope module edges are unenforced: a forbidden edge declared as `testImplementation` passes both dependency checks.: not yet triaged
- The macOS and Windows launch checks are unmet, with the reason recorded, so the effort closes on one of three platforms.: not yet triaged
- The macOS leg links JDK 21.0.13.1 while the pipeline pins 21.0.13, most likely because Gradle's toolchain resolution preferred the runner's own JDK; the pipeline check reads text and cannot catch it.: not yet triaged
- The root build remains about 2 000 lines holding six rule families, which is partly by design because the architecture forbids a build-logic project.: not yet triaged
- `modulesOnTheClasspath` and `moduleRowsCarryingJavaFx` are one-element sets built to avoid a special case, and `PrintPinnedModuleNames` writes a report only the root printer reads back.: not yet triaged
- No CI leg carries `timeout-minutes`, so a hung `:client:jlink` waits out GitHub's six-hour default.: not yet triaged
- Long sentences appear in the changelog and the architecture prose against `AGENTS.md`'s rule of short sentences and active voice.: not yet triaged
