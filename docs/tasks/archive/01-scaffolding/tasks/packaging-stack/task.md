---
type: task
subtype: prototype
title: Does org.beryx.jlink 4.2.0 build the image on Gradle 9.9.1 with JavaFX 21
status: deprecated
workflow_state: done
blocked_by: []
---

## The one question

Can `org.beryx.jlink` 4.2.0 build a JavaFX 21.0.13 `jlink` image on Gradle 9.9.1 and JDK 21 with `--strip-debug --compress zip-6`, and what image size, plugin DSL, and host prerequisites does that line produce?

## Why the settled evidence does not answer it

The parent packaging prototype proved the route on plugin 2.26.0 and Gradle 8.x: one plugin block, one task, a 66,344,990 B image. Version 2.26.0 does not run on Gradle 9. The plugin's own release notes say v3.1.2 (2025-08-03) "adds Gradle 9 compatibility", and v4.0.0 (2026-03-28) requires Gradle 8.14.4 or newer, supports Java 17 to 26, is configuration-cache compatible, moved to Groovy 4, and removed `mergedModuleJarVersion`. The 4.x DSL may differ from 2.26.0, and the image size may have moved.

`docs/research/prototype-tray-packaging.md` holds the old evidence and the build files that worked there.

## Alternatives worth comparing

- Plugin 4.2.0, the current release (2026-10-30).
- Plugin 3.2.1, the last of the 3.x line, if 4.2.0 fails on Gradle 9.9.1 or on configuration-cache.
- The JavaFX Gradle plugin (`org.openjfx.javafxplugin`) against a hand-declared module path with the platform classifier, because the client module needs one of them for its `requires javafx.controls` to resolve. Record which one the image build needs.
- If both fail: a hand-written `jlink` task was already compared on the old line and cost more build code. Only revisit it if neither plugin works.

## Smallest artifact that answers it

A throwaway Gradle project under `/tmp/scaffolding-packaging/`, outside the repository:

- Gradle 9.9.1 wrapper, JDK 21 toolchain, Kotlin DSL.
- One named module with `module-info.java` requiring `javafx.controls`, holding a JavaFX `Application`: title `workthread`, a 1024x768 stage, one centred label reading `workthread 0.1.0`.
- A `jlink { ... }` block with `options = ['--strip-debug', '--compress', 'zip-6']` and a launcher.
- JavaFX 21.0.13 from Maven Central.

Run `./gradlew jlink`, then launch the generated image's launcher under `xvfb-run -a` and confirm the stage appears (the throwaway app may print the stage title and size from `Platform.runLater` to make that visible). Record the image size and the exact plugin block.

## Record in the notes

- The exact `jlink` block and file list that worked.
- Image size, and how it compares with the 66,344,990 B from the old line.
- Any Gradle 9.9.1 warning or failure, and any configuration-cache problem.
- Which JavaFX dependency route the build needs.
- What could not be checked here: the macOS and windows image builds can only be proven by the first CI run, because this machine is Linux only. Note the expectation and the open risk.

## Who must react

Nobody. The evidence is mechanical. Report the result to the operator only if neither plugin line works.

## What it unblocks

The client and image implementation tickets, the plugin pin in the map, and the image-size expectation in `docs/dev-env.md`. Delete `/tmp/scaffolding-packaging/` afterwards and keep the findings in this task's notes.

## Notes

### Result: yes, on plugin 4.2.0, first try

`org.beryx.jlink` 4.2.0 builds and runs a JavaFX 21.0.13 image on Gradle 9.9.1 with JDK 21.0.12.1, from one plugin block, with no `mergedModule` and no module-path inference setting. Configuration cache is clean. The image is 67,608,316 B, 1.9% larger than the 66,344,990 B the 2.26.0 line produced.

### Working configuration

```kotlin
plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "4.2.0"
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

javafx {
    version = "21.0.13"
    modules = listOf("javafx.controls")
}

application {
    mainModule = "com.example.workthread"
    mainClass = "com.example.workthread.WorkthreadApp"
}

jlink {
    options = listOf("--strip-debug", "--compress", "zip-6")
    launcher { name = "workthread" }
}
```

`module-info.java` is `module com.example.workthread { requires javafx.controls; exports com.example.workthread; }`. The `exports` is required: without it JavaFX cannot construct the `Application` subclass.

Gradle 9.9.1 wrapper generated with the shell's Gradle 8.14.4 (`gradle wrapper --gradle-version 9.9.1 --distribution-type bin`). JavaFX comes from `org.openjfx.javafxplugin` 0.1.0, the newest published version, which rewrites the coordinates to the host classifier.

### Observations

- `./gradlew jlink` succeeded on the first try, no warnings, 22 s. `--warning-mode all` on a clean run printed nothing. `--configuration-cache` stored on the first run, reused on the second, and reused on a `clean jlink` that executed every task; `org.gradle.configuration-cache=true` behaves the same. A full `clean jlink` with the cache took 8 s, a no-op 445 to 500 ms, a cold cache 26 to 27 s.
- The image launcher ran under `xvfb-run`: `MARKER_STAGE_SHOWN showing=true title=workthread width=1024.27 height=768.20`. Xvfb has no OpenGL, so JavaFX logs `ES2 Prism: Error - GLX extension is not supported` and falls back to software rendering by itself. It does not abort and needs no `-Dprism.order=sw`. If that flag is wanted, pass it through `JAVA_TOOL_OPTIONS` or `CDS_JVM_OPTS`: the launcher puts trailing arguments after `-m`, so a bare `-D` argument becomes an application argument.
- The effective command is `jlink --strip-debug --compress zip-6 --module-path <jdk>/jmods:build/jlinkbase/jlinkjars --add-modules java.desktop,java.xml,jdk.unsupported,jdk.jfr,com.example.workthread --output build/image`.
- Image contents: `bin/{java,jfr,keytool,workthread,workthread.bat}`, `conf`, `include`, `legal`, `lib/modules` (32.6 MB), `man`, `release`. The `release` file lists `java.base java.datatransfer java.xml java.prefs java.desktop jdk.jfr javafx.base jdk.unsupported javafx.graphics javafx.controls com.example.workthread`.
- Two cosmetic quirks, both inherited from the old line: the launcher script points at `-p "$DIR/../app"`, a directory that does not exist in the image (the app module is inside `lib/modules`, so it still runs), and the plugin leaves a 293 B `scaffolding-packaging.merged.module-null.jar` in `build/jlinkbase/jlinkjars/` that is not put into the image.

### Image size attribution

| Route | Options | Bytes | vs 66,344,990 B |
| --- | --- | --- | --- |
| 4.2.0, settled options | `--strip-debug --compress zip-6` | 67,608,316 | +1,263,326 (+1.90%) |
| 4.2.0, plus old options | `--no-header-files --no-man-pages` added | 67,093,427 | +748,437 (+1.13%) |
| 3.2.1, settled options | `--strip-debug --compress zip-6` | 66,829,910 | +484,920 (+0.73%) |

`include/` (202,642 B) and `man/` (312,247 B) account for about 515 KB of the difference. The rest is `jdk.jfr` (673,271 B of `lib/modules` plus `bin/jfr` and its legal and man entries). 4.2.0 runs a `jdeps` step that adds `jdk.jfr`, because `javafx.base` contains `com.sun.javafx.logging.jfr.*`. 3.2.1 did not add it and still ran, because those classes load only when a JFR event is emitted. Expect **about 67.6 MB** for the Linux x86_64 image with the settled options; `docs/dev-env.md` should record that, not 66.3 MB.

### The JavaFX route, and its trap

`org.openjfx.javafxplugin` 0.1.0 is the route to use: one declaration covers compile, `run`, and the image, and it derives the host classifier.

The hand-declared route also works, but only with an explicit classifier:

```kotlin
implementation("org.openjfx:javafx-controls:21.0.13:linux")
```

Without the classifier, `org.openjfx:javafx-*:21.0.13` resolves to a 302 B stub jar with no `module-info`. Gradle puts it on the classpath and `compileJava` fails with `error: module not found: javafx.controls`. The published POMs point at the real artifact through a Maven profile placeholder, and OpenJFX publishes no Gradle Module Metadata for these coordinates, so Gradle cannot choose a variant.

Neither route cross-builds: `jlink` links the running JDK's own `jmods`, so the image always targets the build host.

### Rejected

- Plugin 3.2.1: works (66,829,910 B) but prints `Deprecated Gradle features ... incompatible with Gradle 10` and a module-name warning on its generated descriptor. 4.2.0 is clean, so 3.2.1 is not a fallback worth carrying.
- Adding `--no-header-files --no-man-pages`: saves about 515 KB and was not asked for. The settled options stand.

### Unverified on this machine

Linux x86_64 only. The macOS image (classifier `mac-aarch64` or `mac`), the Windows image (`win`), a real desktop with OpenGL and a window manager, and the tray remain unproven. The plugin and the `jlink` block are OS-neutral, so the same build file should work, but the first CI run on each runner is the proof. The stage came up at 1024.27 x 768.20 rather than exactly 1024x768, which is Xvfb DPI scaling, not a Workthread bug.

The throwaway project lived at `/tmp/scaffolding-packaging/` and was deleted. Full report: `/tmp/wayfinder-01/packaging-stack.md`.
