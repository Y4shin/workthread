---
type: review
title: Finalize review of 01-scaffolding
dispositioned: true
reconcile: "done: corrected two architecture sections against the landed tree. The pipeline-check paragraph now names the installer tokens the rule actually refuses (`fakeroot`, `dpkg-deb`, `wix`) and the upload's missing-image refusal, and records that the JDK pin is text-only so a leg's real JDK is invisible to it. The CI section carries the same corrections and records that no per-leg timeout exists. Everything else matched what landed: the edge table in `settings.gradle.kts`, the four fixture families at 7, 9, 14 and 23, the disabled distributions, and the gate table by task name."
---

## Scope of this pass

The whole effort: the map, the specification, the reconciled architecture, eight ticket docs with their implementation notes, seven deviation reports, the changelog, and `git diff main...HEAD` across 80 files, fixed at `f780269`. The pass also settled the one obligation the effort had left open, the requirement verdicts, which are recorded below.

## The effort's claim

The map's success test holds. `./gradlew build` is green on a clean checkout with Java 21; the build refuses Spring or JavaFX in `core`, a dependency outside a module's edge row, and JavaFX on any runtime classpath but the client's; and the image's launcher opens one empty window. The non-goals hold: no product behaviour, no command, no store, no publishing job, and the module skeletons hold only what the build and the packaging need.

## Requirement verdicts

The map's `## Requirements` section says every one of the 73 register rows needs its own verdict with a pointer to evidence before the effort may close, and that a closing ticket records them. This phase cannot create or rewrite that planning artifact, so the verdicts are recorded here, in the close's own artifact, one line per row. The register was read only through `docs/v1/requirements-lookup.py effort 01-scaffolding`.

Verdict words: **honoured** means the statement holds in the delivered tree and the pointer names where. **partial** means part of it holds and the gap is named. **recorded** means it is a decision this effort carries forward for a later reader, with nothing to implement here. **deferred** means it is a decision for the effort that builds that area, and this effort only had to keep the ground ready.

Counts: 34 honoured, 5 partial, 9 recorded, 25 deferred. No row is unaccounted for.

### Honoured (34)

| Row | Evidence |
| --- | --- |
| T01-022 | Build tool and test harness settled: `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties`, `docs/testing.md` |
| T03-003 | Additive surfaces: `verifyContractApiSnapshot` against `contract/api-snapshot.txt` |
| T03-012 | Core stays framework-free and the build enforces it: `verifyModuleDependencies` in `build.gradle.kts` |
| T04-001 | Java 21 by toolchain: the root build's `JavaPluginExtension` block |
| T04-002 | Gradle 9.9.1, Kotlin DSL, per-module toolchains: wrapper properties and `build.gradle.kts` |
| T04-007 | JUnit 5 and AssertJ in every module: `CoreModuleTest` and the root convention block |
| T04-015 | `core` declares no Spring and no JavaFX: `core/build.gradle.kts`, enforced by the rule above |
| T04-016 | The `core` rule is a build check: `verifyModuleDependencies` plus seven fixtures |
| T04-018 | Packaging is an image, not a fat jar: `:client:jlink` is the only packaging task |
| T06-001 | A JavaFX client ships as a self-contained image: `client/build/image`, 67 612 510 bytes |
| T06-002 | Every route consumes modular jars: the plugin route, with the `jlink -v` line recorded in `docs/dev-env.md` |
| T06-003 | `org.beryx.jlink` with `--strip-debug --compress zip-6`: the `jlink` block in `client/build.gradle.kts` |
| T06-004 | Linux images need the host GTK3 and X11: `docs/dev-env.md`, `ldd` evidence included |
| T06-008 | A no-tray image saves nothing: the shipped image carries `java.desktop` |
| T07-008 | `javafx.controls` is required and present: `client/src/main/java/module-info.java` |
| T07-009 | The host-classified JavaFX jars are the route: the JavaFX plugin, asserted by `ClientStackTest` |
| T07-010 | The build enforces the module path: `verifyModuleDescriptor` plus the image's module set |
| T09-004 | `mcp` is an aggregator over `mcp-core` and `mcp-json-jackson3`: `mcp/build.gradle.kts`, `McpStackTest` |
| T11-079 | Eight modules with those names: `settings.gradle.kts` edge table |
| T11-081 | Dependencies point inward, `core` sees only `contract`: the edge table and its check |
| T11-088 | The `jlink` image with JavaFX on the module path stands: `client/build/image` |
| T11-090 | Java 21 caps the JavaFX line: 21.0.13 pinned in `gradle/libs.versions.toml` |
| T21-005 | The packaging excludes `javafx.web` and `javafx.media`: the image's module set |
| T22-002 | The client is a Java module with no second language: `client/src/main/java/module-info.java` |
| T22-003 | The JavaFX 21 LTS line stands: catalogue pin |
| T22-004 | The patch is a build detail: `libs.versions.toml` |
| T22-009 | The client ships as a `jlink` image and the build enforces it: `client-image` ticket, `verifyModuleDescriptor` |
| T24-020 | Scaffolding first, shipping the empty window image: the effort's archive record and the image |
| T25-041 | Additive versions: the snapshot's removal and reshape refusal |
| MAP-007 | Build tool, Java version, and test harness settled here: architecture "The build and the toolchain" |
| MAP-014 | Stack evidenced: `./gradlew -q printPinnedModuleNames`, 33 rows |
| MAP-016 | `jlink` with the settled options, about 64 MB: 67 612 510 bytes measured |
| MAP-029 | Eight modules with `core` seeing only `contract`: `settings.gradle.kts` |
| MAP-C04 | Java 21 or newer, stack settled here: toolchain and catalogue |

### Partial (5)

| Row | What holds, and what does not |
| --- | --- |
| T04-004 | The MCP aggregator resolves at 2.1.0 in `mcp`; no `StdioServerTransportProvider` is used, because no MCP surface exists until effort 08 |
| T06-013 | Natives extract to `~/.openjfx/cache`; the claim that it works with a read-only home does not hold, because the first launch needs a writable cache, which `docs/dev-env.md` now records |
| T11-080 | The five modules exist under those names; their responsibilities are empty until the efforts that fill them |
| T22-008 | The client module and its desktop entry point exist; the tray is effort 12 |
| T23-008 | Sizes measured for all three artifacts (68 616 047, 59 189 008, 57 778 847 bytes unpacked); cold start was not measured, and two of the three launches did not happen |

### Recorded (9)

| Row | Where it is carried |
| --- | --- |
| T03-001 | `docs/releasing.md`: the staircase and the per-version tag rule |
| T03-004 | `docs/releasing.md`: a version is tagged before the next opens |
| T04-009 | Architecture "The declared stack": the SDK's transports belong to effort 08 |
| T04-013 | `docs/dev-env.md` "Headless JavaFX notes": FX 26 adds the headless platform, FX 21 has none |
| T06-012 | `docs/dev-env.md`: the jpackage "pure virtual method called" symptom, and why the pipeline check refuses installer prerequisites |
| T07-004 | Architecture "Packaging": the image is the route, so no runnable jar is shipped |
| MAP-019 | Architecture "Packaging": the classpath and fat-jar hazard, never exercised because the client extends `Application` and ships as an image |
| MAP-C07 | `docs/releasing.md`: additive versions, tagged in sequence |
| T22-006 | Architecture "Abstractions to use": the rule stands, and no interop seam exists |

### Deferred (25)

`T09-007`, `T11-001`, `T11-002`, `T11-003`, `T11-007`, `T11-018`, `T11-019`, `T11-020`, `T11-032`, `T11-035`, `T11-036`, `T11-038`, `T11-039`, `T11-044`, `T11-045`, `T11-046`, `T11-047`, `T11-048`, `T11-049`, `T11-073`, `MAP-025`, `MAP-026`, `MAP-027` are the port, configurator, runtime, and core-shape decisions. All of them belong to the efforts that build the core and the surfaces, and this effort kept the ground ready: `contract` carries no inward dependency, `core` cannot see a surface, and the surface is snapshotted. `T17-035` and `T17-036` are the storage locations, which belong to effort 02 and have no code here.

## The delivered code

The build enforces more than its plan promised: every gate rides `check`, four fixture families hold 7, 9, 14 and 23 cases, and the coherence pass removed the duplication the advisory review called out. The gap the operator chose to close is test scope, where a forbidden module edge declared as `testImplementation` passes; it is now a ticket in this effort.

The rest are smaller and are dispositioned below: versions restated outside the catalogue, two documents that describe the repository's shape as it was before this effort, a stale image size in one ticket note, and a CI matrix with no per-leg timeout.

## Dispositions

- The map's requirement gate was unmet: the 73 register rows had no verdicts and no closing ticket recorded them; this artifact now records one verdict per row, with evidence, because the planning artifact that would hold them is not writable by this phase.: informational (accepted)
- The macOS and Windows launch checks are unmet; only the Linux image was launched, so the effort closes on one of three platforms. The operator accepted this gap.: informational (accepted)
- Test-scope module edges are unenforced: a forbidden edge declared as `testImplementation` passes both dependency checks.: ticket `enforce-edge-table-in-test-scope`
- The macOS CI leg linked JDK 21.0.13.1 against the pinned 21.0.13, and the pipeline check pins the workflow's text so it cannot see which JDK a leg linked. The operator accepted this as it stands; `docs/dev-env.md` and the architecture record it.: informational (accepted)
- No CI leg carries `timeout-minutes`, so a hung `:client:jlink` waits out GitHub's six-hour default.: informational (accepted)
- Pinned versions are restated outside the version catalogue in six `*StackTest` classes, two root-build jar-name fixtures, and `PINNED_JDK`; a bump fails the tests that assert them, so the drift is loud rather than silent.: informational (accepted)
- `client-image`'s ticket note records 67 611 290 bytes and calls it a match with the architecture, while the shipped image is 67 612 510 bytes; the note is under `docs/tasks/` and the architecture holds the right figure.: informational (accepted)
- `AGENTS.md` still names three target shapes and neither the eight modules nor the edge table, though the "not scaffolded yet" line is gone; the shapes it names remain the design's separable targets.: informational (accepted)
- `CONTEXT.md` has no entry for `contract`, `wiring`, or the plural `adapters`, against the rule that it is the glossary; the glossary lags the names the architecture now fixes.: informational (accepted)
- Long sentences appear in the changelog and in architecture prose against `AGENTS.md`'s rule of short sentences and active voice.: informational (accepted)
- `ForbiddenCiStepRule` refuses `fakeroot`, `dpkg-deb`, and `wix`, and the workflow refuses an upload that tolerates a missing image; this phase's reconcile recorded both in the architecture.: informational (accepted)
- The root build is about 2 000 lines holding six rule families, which the architecture accepts because a build-logic project is forbidden.: informational (accepted)
