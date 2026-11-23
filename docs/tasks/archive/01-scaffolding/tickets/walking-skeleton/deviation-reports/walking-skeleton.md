---
type: deviation report
title: Deviation report for walking-skeleton
status: stable
---

## Deviation report: walking-skeleton

### API surface changes

- **Planned:** The architecture's contract for ticket 1 promises the eight
  subproject names, the project-level dependency edges from the "May depend on"
  table, the version property at 0.1.0, the package root
  `io.github.y4shin.workthread` with one subpackage per module, a configured test
  task per subproject, a green `build`, and the Gradle commands in the test
  protocol document.
- **Actual:** All of that exists and was verified (`./gradlew -q projects` lists
  the root plus exactly the eight projects; `./gradlew build` is green; the test
  protocol names `./gradlew test` and `./gradlew build`). Three shape decisions
  were made inside the contract's silence:
  - Each module's main package is declared by a documentation-only
    `package-info.java` and holds no type. Consequence: every main source set
    compiles to zero class files and every module jar contains only
    `META-INF/MANIFEST.MF` (`unzip -l core/build/libs/core-0.1.0.jar`).
  - The edges are declared as `api(project(":contract"))` from `core`,
    `adapters`, and `wiring`, and `implementation(...)` from the four surface
    modules. The edge set matches the table row for row; only the configuration
    is new information.
  - `contract/build.gradle.kts` exists as a comment-only file, because its edge
    row is "nothing".
- **Impact:** One forward blocker and two shape notes.
  - Blocker for ticket 3. A module descriptor cannot export a package that holds
    no type: `javac` rejects `exports p.q;` with `package is empty or does not
    exist: p.q` when `p/q` contains only a javadoc-only `package-info.java`
    (reproduced in a two-file probe, with and without an annotation on the
    package). So `module-info.java` for the seven named modules has to wait for
    a first type per package, or ticket 3 has to add one. Ticket 6 hits the same
    wall from the other side: the client's exported application package needs
    the `Application` subclass.
  - Ticket 5 (API snapshot) is unaffected in mechanics but its `contract`
    surface is empty at 0.1, which matches "0.1 exports almost nothing" and
    means the committed fixture still has to supply the removal case on its own.
  - Ticket 4 (dependency checks) must read both the `api` and the
    `implementation` configuration to see the contract edges from `core`,
    `adapters`, and `wiring`. A check written against one configuration only
    would silently miss them.

### Abstraction usage

- Used/was specified: **yes.** Gradle's own model throughout: `java-library` on
  every subproject, a Java 21 toolchain set through `JavaPluginExtension` in the
  root convention block, the version catalogue read once at script level through
  `VersionCatalogsExtension` and referenced without versions in the subprojects,
  a single `gradle.properties` carrying `group` and `version`, `check`/`build` as
  the aggregate gate, and the JUnit Platform through `useJUnitPlatform()`. No
  custom plugin, no `build-logic` project, no second version source, no
  reimplementation of anything on the "Do not reimplement" list. The root
  `base` plugin plus a `subprojects { }` convention block is the chosen route and
  is the one the architecture implies ("the root build hosts the build checks").

### Out-of-scope changes

- `.gitignore` gained `!gradle/wrapper/gradle-wrapper.jar`. The pre-existing
  `*.jar` rule would have excluded the wrapper jar and broken a clean checkout.
  Necessary, not in the ticket criteria.
- `docs/testing.md` was edited beyond the literal criterion 7 wording: the
  "Planned, not yet present" list lost its JUnit 5 entry and the Framework
  section gained a bullet about the per-module Java 21 toolchain. In the spirit
  of criterion 7, additive to the document's purpose.
- `gradle.properties` also fixes `group=io.github.y4shin.workthread`. The spec
  named the version as the thing with one source; the group was co-located in
  the same file, single-sourced, so this is additive, not a parallel route.
- Nothing was removed, and nothing from a later ticket leaked in: no
  `module-info.java`, no stack coordinates, no dependency-rule checks, no
  `jlink`, no CI workflow, no `docs/dev-env.md` edit.
- `docs/dev-env.md` is now stale: it still says the repository holds no code and
  lists Gradle 8.14.4 as bootstrap only. That document is ticket 8's record seam,
  so leaving it is correct, but a reader between now and ticket 8 will see a
  contradiction with `docs/testing.md`.

### Ticket doc update needed?

Yes. Append to `## Implementation notes`:

- One package per module is declared by a javadoc-only `package-info.java`; no
  type and no product API were invented. Each module's `main` source set
  therefore compiles to nothing and each 0.1 jar carries only its manifest.
- Each module's single test asserts its own package name against the
  architecture's literal, because there is no product behaviour to assert yet.
- Edges use `api(project(":contract"))` from `core`, `adapters`, `wiring` and
  `implementation(...)` from `cli`, `mcp`, `api`, `client`. The table names the
  edges, not the configurations.
- The wrapper was generated in a scratch directory and copied in, because Gradle
  9.9.1 refuses to run `wrapper` in a directory with no settings file.
- The version catalogue is read once at script level in the root build, because
  the `libs` accessor does not resolve inside a `subprojects { }` block.

### User attention needed?

Yes, one decision and one accepted consequence.

1. Decision: the empty main packages block ticket 3's `exports` declarations.
   Either ticket 1 is amended to seed one throwaway type per package (a
   placeholder that ticket 3 can export and ticket 5 can snapshot), or ticket 3
   absorbs the first type per module. The architecture's ticket 3 contract
   currently reads as if the descriptors can land on today's tree, and they
   cannot.
2. Consequence to accept knowingly: at 0.1 every module jar is empty of classes.
   That is consistent with "no product behaviour in the scaffolding effort", but
   it means `contract`'s snapshot at 0.1 covers an empty surface.
