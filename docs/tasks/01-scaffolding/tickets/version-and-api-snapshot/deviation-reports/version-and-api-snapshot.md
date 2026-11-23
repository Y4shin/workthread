---
type: deviation report
title: Deviation report for version-and-api-snapshot
status: stable
---

## Deviation report: version-and-api-snapshot

### API surface changes

- **Planned:** The architecture's ticket 5 contract exports "the single version
  source, the committed surface snapshot, the comparison task and its failure
  semantics, and the tag convention". The gates section fixes the shape: `0.1.0`
  lives in one property, an annotated tag marks each release, "a committed
  snapshot holds every exported package of `contract`; `check` compares the
  current surface against it and fails when an exported type or member disappears
  or changes shape, naming what changed", a committed fixture reproduces a
  removal, "the snapshot covers `contract` only at 0.1, and it includes
  `ContractPlaceholder`", and the fixture convention is "plain data lists ...
  one runner; one root task attached to the root `check`". The packaging section
  also states, of the client window, "The label text is a literal today, and the
  single version source does not feed it yet."
- **Actual:** The promised surface is delivered under the planned names, and each
  criterion was proved on this checkout, not only read from the implementation
  log:
  - `gradle.properties` is still the one property (`version=0.1.0`).
    `git tag -l` prints nothing, so no tag exists.
  - All eight jars report it: `unzip -p <module>/build/libs/<module>-0.1.0.jar
    META-INF/MANIFEST.MF` shows `Implementation-Title: <module>` and
    `Implementation-Version: 0.1.0` on `contract`, `core`, `adapters`, `wiring`,
    `cli`, `mcp`, `api` and `client`.
  - `contract/api-snapshot.txt` is committed with a header, `package
    io.github.y4shin.workthread.contract<TAB>exported`, and `type
    ...ContractPlaceholder<TAB>public final class extends java.lang.Object`.
    `./gradlew verifyContractApiSnapshot` is green and is attached to the root
    `check`.
  - The failure path is real and names what changed. I appended
    `method ...ContractPlaceholder#ping()<TAB>public returns void` to the
    committed snapshot and the task failed with
    `contract's exported surface no longer holds method io.github.y4shin.workthread.contract.ContractPlaceholder#ping(); the snapshot holds it as 'public returns void'`.
    The file was restored (`git status` clean for `contract/`).
  - `./gradlew --offline verifyVersionAndSurfaceFixtures --rerun-tasks` prints
    `14 version and surface fixtures held` (7 version, 7 surface).
  - `./gradlew --offline build` is green, 65 actionable tasks, configuration
    cache reused.
  - `docs/releasing.md` names who tags (the maintainer, the repository owner),
    when (scope closed, three legs green, snapshot green, version matched), the
    annotated `v<version>` convention, and that 0.1.0 is not tagged here.
- **Surface added beyond the contract, all of it real and all of it load-bearing:**
  - **A new artifact surface on every module.** Every `Jar` now carries
    `Implementation-Title` and `Implementation-Version`. Downstream consumers read
    a module's version off the artifact, so this is a promise, not an internal
    detail, and `verifyVersion` depends on it.
  - **A third gate family, `verifyVersion` (root, attached to `check`).** It
    reads the eight built jars back, refuses a jar reporting another number or
    none, and refuses any file under a module's `src` that restates the number.
    The architecture's gate list names the dependency checks, the descriptor
    check and the API snapshot only. The scan is wired to real sources: a
    `// pinned at 0.1.0` comment added to `WorkthreadVersion.java` made
    `verifyVersion` fail with `client/src/main/java/.../WorkthreadVersion.java
    restates the version 0.1.0; gradle.properties is the one source`. The file
    was restored afterwards.
  - **`generateContractApiSnapshot` (root).** A regeneration task next to the
    comparison task. Not named in the ticket or the architecture, and without it
    an additive change can never be recorded, so the next removal of that element
    would be invisible.
  - **The client's label now reads the version, which reverses the architecture's
    statement.** `client` gains a `GenerateVersionResource` task writing
    `workthread-version.properties` from `project.version`, a `main` resources
    `srcDir` pointing at it, a package-private `WorkthreadVersion` reader (which
    falls back to `unknown`), a package-private static
    `WorkthreadApplication.windowLabel()`, `new Label(windowLabel())` in `start`,
    a `test` system property `workthread.version`, and `ClientVersionTest`. The
    literal `workthread 0.1.0` is gone from the source. This closes the coupling
    `client-image` forwarded to this ticket, and it contradicts the architecture
    sentence quoted above.
  - **The snapshot's scope decisions.** The file holds packages plus public and
    protected types and members (nested types included, generics in the shape),
    compared one-directionally: additions pass, removals and reshapes fail. This
    reads the architecture's "an exported type or member disappears or changes
    shape" correctly; it goes past the sentence "holds every exported package" by
    also holding types and members, which the same paragraph asks for.
  - **The fixture convention is now two runners and two root tasks, not one.**
    `VersionAndSurfaceFixtures` is a second runner and
    `verifyVersionAndSurfaceFixtures` a second root task on `check`, alongside
    `DependencyRuleFixtures` and `verifyDependencyRuleFixtures`. That reads "one
    runner; one root task" as per-family, which is what `dependency-checks`'
    deviation report says it intended, but the architecture's wording is
    singular.
- **Impact:** Dependents get the names the contract promised, and the contract
  module's own effort inherits one ritual it did not have:
  - Ticket 6 (`client-image`) is affected in one measured fact: the image grew
    from 67 611 290 to 67 612 510 bytes because the client module carries the
    generated resource. I re-measured `du -sb client/build/image` as
    `67612510`, matching the updated `docs/dev-env.md` figure. Launcher name,
    module set and `:client:jlink` are unchanged. Its open item "the label is a
    literal; ticket 5 owns feeding it" is now closed.
  - Ticket 7 (`three-platform-ci`) needs no new command: every new gate rides
    `./gradlew build`, which is what each leg already runs, and
    `generateVersionResource` is part of `client`'s resources.
  - Ticket 8 (`manual-launch-check`) will observe the same label text
    (`workthread 0.1.0`) now rendered from the resource rather than typed. If a
    later bump moves the number, the window follows with no code change, so a
    launch record records the build it ran.
  - The effort that replaces `ContractPlaceholder` with real contract types now
    meets `verifyContractApiSnapshot` on its first commit. That replacement is a
    removal plus additions, so it fails `check` until
    `./gradlew generateContractApiSnapshot` is run and the diff reviewed. Nothing
    in the build forces that ritual; `docs/releasing.md` states it. This is the
    one dependent-facing change to how later work lands.
  - Every later effort that adds a version-shaped number to a module's `src`
    (a fixture string, a test expectation, a doc comment) is refused by
    `verifyVersion`.

### Abstraction usage

- Used/was specified: **yes**, with two readings worth naming.
  - Gradle's own model: `check` as the aggregate gate, task types declared in the
    root script, `Jar` manifest attributes, a generated resource directory, the
    fixture lists as plain data plus one task. No third-party rule plugin and no
    build-logic project, so "The root build hosts the build checks and the API
    snapshot task" holds. `build.gradle.kts` is now about 1 320 lines in the same
    root-script style as the existing checks.
  - The Java module system stays the single source for the surface:
    `ModuleFinder` reads `contract`'s exports from the built jar, and reflection
    reads the shape, rather than a directory listing or a source scan.
  - The record seam is respected: the image size re-measurement landed in
    `docs/dev-env.md`, not in the ticket body.
  - Reading 1: "one runner; one root task" is implemented per fixture family, so
    the build now has two of each. Reading 2: "the version is one source" is
    implemented as one property plus three enforcers (jar manifests, the source
    scan, the client resource). The source is still one; the enforcers are new.

### Out-of-scope changes

- `Implementation-Title` and `Implementation-Version` on every `Jar`: new
  artifact surface for all eight modules, added so "every module reports it" is
  read off the artifact rather than asserted about the build model.
- `verifyVersion` plus the "no source restates the number" scan: a third gate
  family the architecture's gate list does not name. Scope is each module's
  `src` tree only; build scripts, docs and generated files are deliberately
  unmapped, because fixture data and documentation must be able to name versions.
- `generateContractApiSnapshot`: a regeneration path no document names.
- Client-side additions named above: `generateVersionResource`,
  `workthread-version.properties`, `WorkthreadVersion`,
  `WorkthreadApplication.windowLabel()`, the `test` system property
  `workthread.version`, and `ClientVersionTest`. The ticket's "every module
  reports it" does not require the window to read it; `client-image`'s forwarded
  note does.
- A shared `FixtureChecks` base class, and `DependencyRuleFixtures` refactored
  onto it (its private `refusalOf` and `expectedProblem` removed). This edits the
  code `dependency-checks` shipped. The behaviour is unchanged and its seven
  fixtures still hold (`7 dependency rule fixtures held` is unchanged in the
  build), but a top-level script function could not be used because the
  configuration cache refuses to serialize the build script object.
- New `docs/releasing.md` (75 lines) and two added lines in `AGENTS.md` pointing
  at it. The ticket says the tag convention must be where a maintainer reads it;
  a new `docs/` file linked from the entry document is that.
- `docs/dev-env.md`: the image size moved to 67 612 510 bytes with a sentence
  saying why. In scope as the record seam.
- No removals. `docs/testing.md` is untouched (correct: the new tasks ride
  `./gradlew build`, already a "run these always" command). No tag was created.
  No architecture edit, so three architecture statements are now stale: the image
  figure 67 611 290 (now 67 612 510), the gate list that omits `verifyVersion`,
  and "The label text is a literal today, and the single version source does not
  feed it yet".

### Ticket doc update needed?

Yes. Append to `## Implementation notes` (the ticket has no such section yet):

- The one source stays `gradle.properties` (`version=0.1.0`). Three enforcers
  read it: `Implementation-Title`/`Implementation-Version` on every jar, the
  client's generated `workthread-version.properties`, and `verifyVersion` (root,
  on `check`), which also refuses any file under a module's `src` that restates
  the number as a whole number (bounded regex, so `21.0.13` and `10.1.0` at
  version `1.0` are not copies).
- Version tasks: `verifyVersion` (root, on `check`). Client tasks:
  `generateVersionResource` (writes the resource into
  `client/build/generated/version-resources`), and `test` passes
  `workthread.version` as a system property.
- Snapshot: `contract/api-snapshot.txt`, element TAB shape, `#` header lines
  ignored, covers exported packages plus public and protected types and members
  (nested types included, generics in the shape). Comparison is one-directional:
  additions pass, removals and reshapes fail with the element name and both
  shapes. Tasks: `verifyContractApiSnapshot` (root, on `check`) and
  `generateContractApiSnapshot` (root, rewrites the file; the diff is the
  review). Additivity is not self-recording.
- Fixtures: `versionFixtures` (7) and `apiSurfaceFixtures` (7), run by
  `VersionAndSurfaceFixtures` and the root `verifyVersionAndSurfaceFixtures` task
  on `check`, printing `14 version and surface fixtures held`. The shared
  `FixtureChecks` base class holds `refusalOf` and `fixtureProblem`;
  `DependencyRuleFixtures` now inherits it, because a top-level script function
  breaks the configuration cache.
- `docs/releasing.md` holds the one source, who tags, when, the annotated
  `v<version>` convention, and the snapshot ritual. `AGENTS.md` points at it.
  No tag exists in this effort.
- Image size re-measured at 67 612 510 bytes (`du -sb`), 1 220 bytes more than
  before the version resource; `docs/dev-env.md` records it.

### User attention needed?

Yes, four items, none blocking a dependent ticket:

1. **The client now contradicts the architecture on purpose.** The architecture
   says the window label is a literal the single source does not feed; the build
   now feeds it. That closed `client-image`'s forward note and is the right
   outcome, but the architecture writer should amend that sentence, along with
   the stale 67 611 290 byte figure and the gate list that omits `verifyVersion`.
2. **A third gate family exists.** `verifyVersion` is beyond the architecture's
   gate list. Its "no second copy" scan is a real new rule with a real scope
   (module `src` trees only, whole-number matching), and it can refuse a later
   effort's test fixture that legitimately wants to name a version.
3. **Additivity is a ritual, not a gate.** An addition passes `check` and is
   invisible to the snapshot until someone runs `generateContractApiSnapshot`. A
   later removal of an unrecorded addition therefore goes unnoticed. If that is
   not acceptable for the contract module, the fix is a follow-up ticket, not a
   change inside this one.
4. **`dependency-checks`' fixtures class was refactored** onto a new shared base.
   Behaviour is unchanged and its seven fixtures still hold, but the ticket that
   owns that code did not ask for it.

### Verification performed for this report

On this checkout, with the working tree restored to clean afterwards (`git
status` shows only the workflow's own `docs/tasks/state.yaml` modification,
nothing staged, no tag created):

- `./gradlew --offline build` green, `Configuration cache entry reused`, 65
  actionable tasks. `verifyVersion` and `verifyContractApiSnapshot` execute
  inside it.
- `./gradlew --offline verifyVersionAndSurfaceFixtures --rerun-tasks` prints
  `14 version and surface fixtures held`.
- `./gradlew --offline verifyContractApiSnapshot` green; refusing a snapshot
  element the jar does not hold, with the message quoted above. File restored.
- `./gradlew --offline verifyVersion` green; refusing the version literal in
  `WorkthreadVersion.java`, with the message quoted above. File restored.
- Eight jar manifests read back, each `Implementation-Version: 0.1.0`; the client
  jar carries `workthread-version.properties`; `jimage list` on the image's
  `lib/modules` shows `workthread-version.properties` in module
  `io.github.y4shin.workthread.client`.
- `du -sb client/build/image` = `67612510`, matching `docs/dev-env.md`.
- `git tag -l` empty. `docs/testing.md` and all of `docs/tasks/` unchanged by the
  ticket's seven commits.
