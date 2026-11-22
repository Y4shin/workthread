---
type: deviation report
title: Deviation report for named-modules
status: stable
---

## Deviation report: named-modules

### API surface changes

- **Planned:** The architecture's ticket 3 contract exports "the module names and
  exported packages of the seven named modules, the client's exported application
  package, and `api` on the classpath. Dependents rely on the descriptor names for
  `requires` clauses and for the image's module set." The module graph section
  fixes the package root at `io.github.y4shin.workthread`, one subpackage per
  module, says the seven carry `module-info.java` and `api` does not, and requires
  one placeholder type per named module "so its descriptor can export the
  package". The packaging section adds "The client's descriptor must export the
  package holding its `Application` subclass, or JavaFX cannot construct it."
- **Actual:** All five acceptance criteria hold on this checkout, with four
  differences a dependent ticket can feel:
  - **The module names were never stated in the design record.** The build
    adopted the convention "module name = its package", so the seven are
    `io.github.y4shin.workthread.contract`, `.core`, `.adapters`, `.wiring`,
    `.cli`, `.mcp`, `.client`, derived once from a `packageRoot` value in the root
    build. Confirmed from the jars: `jar --describe-module` reports each declared
    name, and `api` reports "No module descriptor found. Derived automatic
    module." The convention is the natural reading of "one subpackage per module"
    and matches the packaging prototype's `module com.example.workthread` shape,
    but it is a decision this ticket made, not one it read. Anything that names a
    module (`requires` clauses in tickets 6, the image's module set, a `jlink`
    `--add-modules` line) now inherits it.
  - **The ticket added a build gate the design record's gate list does not
    have.** `build.gradle.kts` grows 158 lines: `packageRoot`, `qualifiedNameOf`,
    `modulesOnTheClasspath`, `clientApplicationClass`, rule class
    `ModuleDescriptorRule` (a `DependencyRule`), task type
    `VerifyModuleDescriptor`, a per-module `verifyModuleDescriptor` task attached
    to every module's `check`. It reads the built jar with `ModuleFinder` and
    refuses a missing descriptor, an automatic descriptor, a wrong module name, a
    `requires` outside the edge row, a missing `requires` for a row edge, a
    missing export, a descriptor on `api`, and (client) a missing application
    class or an export that does not cover its package. The architecture's "The
    build's own gates" section enumerates two dependency checks plus the fixture
    runner and the API snapshot; this is a third check family outside that list.
    The ticket's criteria ask only that the descriptors be correct, not that the
    build enforce them.
  - **The `requires` rule is scoped narrower and wider than criterion 2 reads.**
    Wider: it refuses a *missing* `requires` for a row edge, because criterion 2's
    "requires only modules its edge row allows" is satisfied vacuously by an empty
    descriptor while the ticket's *What to build* promises "the inward `requires`
    clauses". Narrower: it judges only build modules (names under the package
    root), so a JDK module or a declared-stack module in `requires`
    (`javafx.controls`, `io.modelcontextprotocol.sdk.mcp`) is not judged by the
    edge table. The implementer probed that a temporary `requires javafx.controls`
    on `client` compiles and passes, so ticket 6 is not blocked.
  - **The client's placeholder is named `WorkthreadApplication`**, not a neutral
    `<Module>Placeholder` like the other six. Criterion 3 ("exports the package
    that holds its application class") had no testable subject otherwise, so the
    rule reads `io/github/y4shin/workthread/client/WorkthreadApplication.class`
    out of the client jar. It is a private-constructor marker type, not a JavaFX
    `Application` subclass, and `client`'s descriptor does not yet require
    `javafx.controls`: the export is proven, the construction is not.
- **Impact:** ticket 4 (`dependency-checks`, already landed) is unaffected in
  behaviour: its rule compares *project* names against the edge table, so the
  build now carries two name spaces, project name and module name, with the
  module name derived from the project name and never hand-written. Ticket 5
  (`version-and-api-snapshot`) must snapshot `ContractPlaceholder` as part of
  `contract`'s exported surface, because the placeholder is now a real exported
  type and the snapshot compares against it; replacing it later is a removal
  unless ticket 5 records it. Ticket 6 (`client-image`) inherits the module names,
  must add `requires javafx.controls` itself (the check will not remind it, since
  external `requires` are unjudged), and replaces `WorkthreadApplication` in place
  with the `Application` subclass. Ticket 7 (`three-platform-ci`) builds the same
  jars, so its three legs inherit the descriptor check as part of `build`.

### Abstraction usage

- Used/was specified: yes, with one gap. "The Java module system. Descriptors are
  the single source for module names and exports. Do not derive a module set from
  a directory listing." The descriptors are the source and no directory listing
  exists. `moduleEdgeRows` remains the one edge table and the new rule consumes it
  rather than restating it. "A second dependency checker" was not built: the new
  rule subclasses the existing `DependencyRule`, so it extends the existing family
  as the design asks. Gradle's own model is used throughout; no custom plugin.
  The gap is the fixture convention: "A fixture declares a violation and asserts
  on the failure and its message" and "a fixture proves the rule classes". The
  seven committed fixtures still cover only the two dependency rules, so
  `ModuleDescriptorRule` is the one rule class with no fixture. Non-vacuity was
  proven by six throwaway probes recorded in the implementation artifact, applied
  and reverted, not by a committed fixture.

### Out-of-scope changes

- **Added:** the whole `verifyModuleDescriptor` verification surface listed above
  (rule class, task type, task registration, three top-level values, two helper
  functions) and its dependence from every module's `check`. Nothing in the
  ticket's criteria or the architecture's gate list asked for it. It is the one
  change a later effort can trip over, because it can fail a build that the design
  record says should pass.
- **Added, in scope:** one placeholder type in each of the seven exported
  packages. The architecture sanctions this ("Each of the seven named modules
  therefore carries one placeholder type"), though a stale paragraph in the
  declared-stack section still says "No module holds a type yet: each main package
  is declared by a `package-info.java` only". The two paragraphs disagree; the
  implementation followed the placeholder paragraph and left every
  `package-info.java` in place.
- **Renamed inside the branch:** `ClientPlaceholder` became `WorkthreadApplication`
  in the criterion-3 slice. It nets out to one added file, no removal.
- **Not done, and arguably owed:** no fixture for the new rule; no architecture
  document edit to record the module names, the new check, or the corrected
  placeholder paragraph. `docs/tasks/` is tool-written, so neither is a mistake,
  but both leave the design record staler than the build.
- **Untouched:** `docs/testing.md` and `docs/dev-env.md`; no test protocol change
  is needed, because `./gradlew build` already covers the new task.

### Ticket doc update needed?

yes. Append to `## Implementation notes`:

- Module names follow the package: `io.github.y4shin.workthread.<module>` for
  `contract`, `core`, `adapters`, `wiring`, `cli`, `mcp`, `client`; derived once
  from `packageRoot` in the root build. `api` carries no descriptor and stays on
  the classpath, enforced as "no explicit descriptor", so its jar still derives an
  automatic name `api@0.1.0` from the file name.
- Each placeholder is `<Module>Placeholder` except `client`'s, which is
  `WorkthreadApplication`; criterion 3 is verified against that marker type, which
  ticket 6 replaces with the JavaFX `Application` subclass.
- New task `verifyModuleDescriptor` per module, attached to `check`, and its
  refusals: missing descriptor, automatic descriptor, wrong module name,
  `requires` outside the edge row, missing `requires` for a row edge, missing
  export, descriptor present on `api`, missing client application class, export
  not covering it. Every message names the module.
- The two scoping decisions: `requires` is checked in both directions, and only
  for modules under the package root, so JDK and declared-stack modules are not
  judged by the edge table.
- Forward note: ticket 6 adds `requires javafx.controls` to `client`'s descriptor;
  probed to compile and pass the check.
- Criterion 4's name assertion is a regression guard with no red state of its own;
  probe 1 in the implementation artifact proves it can fail.

### User attention needed?

yes, two items:

1. The module names are now a contract that tickets 6, 7, and the MCP effort read
   (`requires` clauses, image module set, `jlink --add-modules`), and no document
   states them. If the architecture writer would name them differently, now is far
   cheaper than after the image and the CI legs exist.
2. A new build gate arrived that the architecture's gate list does not contain and
   that the ticket's criteria do not ask for, without the fixture the fixture
   convention calls for. It is defensible under "A ticket never asks a later
   effort to remember a rule the build can enforce", but it makes `build` fail on
   descriptor drift, so it should be recorded in the architecture (and given a
   fixture, or the convention narrowed) rather than left as an undocumented third
   check family.
