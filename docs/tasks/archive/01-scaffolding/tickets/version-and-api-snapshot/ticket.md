---
type: ticket
subtype: feature
title: Start 0.1.0 and the contract API snapshot
status: deprecated
workflow_state: done
blocked_by:
- named-modules
size: m
---

## What to build

The version is 0.1.0 from one source, the annotated tag convention is written where a maintainer reads it, and `check` compares `contract`'s exported surface against a committed snapshot, so a removed or reshaped export fails the build. A fixture proves the failure. No tag is created in this effort: 0.1 closes after the command line effort.

## Acceptance criteria

- [ ] The version is 0.1.0 from one source, and every module reports it.
- [ ] The annotated tag convention is documented, it names who tags and when, and no tag is created here.
- [ ] `check` compares `contract`'s exported surface with a committed snapshot.
- [ ] Removing or reshaping an export fails `check` with a message naming what changed.
- [ ] A committed fixture proves the failure path.
- [ ] The real build stays green.

## Blocked by

- `named-modules`

## Implementation notes

The one source stays `gradle.properties` (`version=0.1.0`), and three enforcers read it: `Implementation-Title` and `Implementation-Version` on every jar, the client's generated `workthread-version.properties`, and `verifyVersion` (root, on `check`), which also refuses any file under a module's `src` that restates the number as a whole number. `generateVersionResource` writes the client resource into `client/build/generated/version-resources`, and the client's `test` task passes `workthread.version` as a system property.

The snapshot is `contract/api-snapshot.txt`, one element per line as element TAB shape, `#` header lines ignored. It covers exported packages plus public and protected types and members, nested types included, with generics in the shape. Comparison is one-directional: additions pass, removals and reshapes fail with the element name and both shapes. `verifyContractApiSnapshot` is on `check` and `generateContractApiSnapshot` rewrites the file, so additivity is a ritual and not self-recording. Fourteen fixtures run through `VersionAndSurfaceFixtures` and print `14 version and surface fixtures held`. The shared `FixtureChecks` base class holds `refusalOf` and `fixtureProblem`, and `DependencyRuleFixtures` now inherits it, because a top-level script function breaks the configuration cache. `docs/releasing.md` holds the one source, who tags, when, and the annotated `v<version>` convention; `AGENTS.md` points at it and no tag exists in this effort.
