---
type: deviation report
title: Deviation report for client-image
status: stable
---

## Deviation report: client-image

### API surface changes

- **Planned:** The architecture's per-ticket contract 6 exports "the client image
  task, the launcher name, the window content, the image size, and the runtime
  prerequisites". The packaging section fixes them: the `org.beryx.jlink` plugin
  in `client`, `options = ["--strip-debug", "--compress", "zip-6"]`, a launcher
  named `workthread`, the client's descriptor exporting the `Application`
  subclass's package, an image of about 67.6 MB on Linux x86_64 that includes
  `jdk.jfr`, and a run-time need for the host's GTK3 and X11. The window carries
  the title `workthread`, is 1024x768, and shows one centred label reading
  `workthread 0.1.0`.
- **Actual:** All of it is delivered under the planned names. Re-verified on this
  checkout, not just read from the implementation log:
  - `./gradlew build` is green, "Configuration cache entry reused", and a
    `clean build --warning-mode all` produces no `warn`/`deprecat` line.
  - `:client:jlink` runs
    `jlink -v --strip-debug --compress zip-6 --add-modules java.desktop,java.xml,jdk.unsupported,jdk.jfr,io.github.y4shin.workthread.client`
    and produces `client/build/image`.
  - Image size re-measured: `67611290` bytes (64.5 MiB, 67.6 MB decimal),
    `du -sh` 65M. This reproduces the recorded number exactly.
  - `client/build/image/bin/workthread` runs
    `-p "$DIR/../app" -m io.github.y4shin.workthread.client/io.github.y4shin.workthread.client.WorkthreadApplication`,
    the cosmetic quirk the architecture accepts.
  - Image module set: `java.base`, `java.desktop`, `java.xml`, `java.prefs`,
    `java.datatransfer`, `jdk.jfr`, `jdk.unsupported`, `javafx.base`,
    `javafx.controls`, `javafx.graphics` (21.0.13), and `client`, `contract`,
    `wiring`. The synthetic merged module does not reach the image, as the
    architecture says.
  - Launch reproduced under `Xvfb :98 -screen 0 1280x800x24 -dpi 96`:
    `xwininfo -root -tree` reports `0x400002 "workthread"` with class
    `io.github.y4shin.workthread.client.WorkthreadApplication`, at
    1024x768. The application log carries only
    `ES2 Prism: Error - GLX extension is not supported`, the expected Xvfb noise,
    and JavaFX falls back to software rendering on its own.
  - The label text is the source literal `workthread 0.1.0` in
    `WorkthreadApplication.start`.
- **Surface added beyond the contract:**
  - `application { mainModule; mainClass }`. The architecture's packaging snippet
    applies the `application` plugin but configures nothing, and `jlink` cannot
    link or launcher-start the module without those two values, so the block is
    required, not optional.
  - `requires javafx.controls` in the client descriptor. This was forward-noted
    by `named-modules` against this ticket, so it is planned work, not a
    surprise. No separate `requires javafx.graphics` is needed
    (`javafx.controls` reads it).
  - `main(String[])` on the `Application` subclass, so the image launcher, the
    generated start scripts and `:client:run` share one entry point.
  - `ClientApplicationTest`, the only committed test this ticket adds. It reads
    the class's shape by reflection, so it starts no toolkit and needs no
    display, which keeps `build` toolkit-free as the architecture requires.
- **Impact:** Dependents get exactly the names the contract promised, so none has
  to renegotiate:
  - Ticket 7 (`three-platform-ci`) calls `:client:jlink` and uploads the image;
    the task name and `client/build/image` are unchanged.
  - Ticket 8 (`manual-launch-check`) launches `bin/workthread` on three hosts;
    the launcher name and the GTK3/X11 prerequisite it records are settled.
  - Ticket 5 (`version-and-api-snapshot`) inherits a real coupling it did not
    have before: the window's label holds the literal `workthread 0.1.0` next to
    the single version source in `gradle.properties`. The single source must feed
    that literal, or the window drifts on the next bump. Nothing in the build
    compares them today.
  - The client's exported package now holds a constructible JavaFX
    `Application`, so the descriptor check's client branch ("a missing
    application class, or an export set that does not cover that class") now
    guards something real. It still does not judge `requires javafx.controls`,
    because external `requires` are out of the edge table: that clause is proven
    by the image, not by `check`.

### Abstraction usage

- Used/was specified: yes, with two gaps.
  - Gradle's own model, as listed: the root's `java-library` plus this ticket's
    `application` plugin, per-module toolchain, `check` as the aggregate gate,
    JUnit Platform. No custom plugin, no separate build-logic project.
  - The two installed plugins own their routes. `org.openjfx.javafxplugin` still
    supplies the JavaFX coordinates and the host classifier; `org.beryx.jlink`
    owns the image and the launcher; there is no second image tool and no
    hand-declared module path.
  - "The development environment document is the record seam": the image size,
    the verbatim `jlink` command, the module list, the launcher quirk, the manual
    launch record, and the GTK3/X11 prerequisite all landed in `docs/dev-env.md`,
    not in the ticket body. Followed.
  - "The version catalogue is the only place a library or plugin version
    appears": followed, and it forced a deliberate divergence from the
    architecture's own snippet, which writes both plugin versions inline. The
    `jlink` entry now lives in `gradle/libs.versions.toml` next to `javafx`.
  - Gap 1: the architecture's packaging snippet is not the build that exists.
    It has no `application {}` block and inline plugin versions, so it cannot be
    copied into a build file and succeed. Nothing in the architecture document
    was edited, so a later reader comparing snippet to code sees drift, and the
    drift is in the direction the prose forbids (inline versions).
  - Gap 2: `--add-modules java.desktop,java.xml,jdk.unsupported,jdk.jfr` is
    inferred by the plugin, not declared from a descriptor. "Descriptors are the
    single source for module names" holds for the three project modules in the
    image and for the JavaFX modules, but not for the JDK modules the image
    carries.

### Out-of-scope changes

- **`build` now produces distributions.** `client` applies `application`, so
  `startScripts`, `distTar` and `distZip` join `:client:assemble`; `./gradlew
  build` writes `client/build/distributions/client-0.1.0.zip` (8.0 MB) and
  `.tar`. Nothing consumes them, and the `jlink` image is the shipping artifact
  the architecture names. No ticket decides whether they stay, and ticket 7 will
  now build them on all three platforms.
- `main(String[])` added to `WorkthreadApplication`, which no acceptance
  criterion asks for (benign, see above).
- `ClientApplicationTest` added: a new committed test at the launcher seam. It
  covers class shape only; the image itself is still covered by command
  evidence, not a test, because `:client:jlink` is deliberately not attached to
  `build`.
- `docs/dev-env.md` intro paragraph rewritten (it used to say the repository
  holds no code) plus a 90-line "The client image" section. In scope: the record
  seam.
- `docs/testing.md` untouched, so no "Run these always" command changed.
  `:client:jlink` stays a separate command, which is what the architecture's CI
  section describes.
- No architecture edit. The prose was already right about size, options and
  prerequisites; only the code snippet is now stale.

### Ticket doc update needed?

Yes. Append to `## Implementation notes` (the ticket has no such section yet):

- The image is `client/build/image`; the launcher is
  `client/build/image/bin/workthread`. Built by `./gradlew :client:jlink` with
  `--strip-debug --compress zip-6` and main class
  `io.github.y4shin.workthread.client.WorkthreadApplication`.
- Measured on Linux x86_64: 67 611 290 bytes (64.5 MiB / 67.6 MB), `du -sh` 65M.
  Matches the architecture's 67.6 MB.
- Image modules: `java.base`, `java.desktop`, `java.xml`, `java.prefs`,
  `java.datatransfer`, `jdk.unsupported`, `jdk.jfr`, `javafx.base`,
  `javafx.controls`, `javafx.graphics` 21.0.13, and
  `io.github.y4shin.workthread.client`, `.contract`, `.wiring`.
- The manual launch used Xvfb at `-dpi 96`. Without it the window measures
  1068x801, because of the server's DPI scale. Xvfb logs
  `ES2 Prism: Error - GLX extension is not supported`; JavaFX falls back to
  software rendering, and no `-Dprism.order=sw` is needed.
- Runtime prerequisites: GTK3 and the X11 family (the list is in
  `docs/dev-env.md`), `libGL`/`libXxf86vm.so.1` for the ES2 pipeline, and a
  writable `~/.openjfx/cache` where the first launch extracts the JavaFX
  natives.
- `client` applies the `application` plugin, so `distTar`/`distZip` run in
  `build` and ship a classpath-style distribution nobody consumes.
- The label text `workthread 0.1.0` is a literal; the single version source
  from ticket 5 should feed it when that lands.
- The launcher's `-p "$DIR/../app"` points at a directory the image does not
  contain, and the launcher works regardless, as the architecture says.

### User attention needed?

Yes, two open decisions, neither blocking a dependent ticket:

1. **The distributions.** `./gradlew build` now emits `client-0.1.0.zip` and
   `.tar` in addition to the image. Keep them or turn them off? The choice was
   left to a later packaging effort, and ticket 7 will carry them to three
   platforms in the meantime.
2. **The version literal.** The window's label is a hand-written `0.1.0`.
   Ticket 5 owns the single version source; until it feeds this literal, the
   window can advertise a version the build does not have.

Worth recording as well, though no ticket owns it: the image needs a writable
`~/.openjfx/cache` on first launch, and a read-only home is a run-time
prerequisite the image does not state. Ticket 8 records platform prerequisites
and is the natural owner.

Command-evidence caveat for whoever verifies next: `client/build/image` is shared
state in this checkout. A `jlink` run overlapping another Gradle run in the same
working tree can leave a partial image (a reading of 34 955 306 bytes was
observed mid-flight, against the settled 67 611 290), and a `jlink` that finds
the directory tampered with exits 4. Re-run `./gradlew clean :client:jlink` alone
before trusting a size.
