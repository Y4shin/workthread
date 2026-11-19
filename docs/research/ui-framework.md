---
type: findings
title: "Desktop UI framework for a Java work logic: Compose Multiplatform, JavaFX, SWT, Swing, or a local web interface"
status: stable
source_task: ui-framework-research
---

<!-- Provenance: produced by a background researcher against primary sources only;
     saved verbatim. Claims are tagged with their source and a confidence level, and
     the unresolvable points are listed under "Missing evidence". -->

# Research: which desktop UI framework should ship the workthread client

## Question

Which desktop UI framework should ship the client of a Java 21 single-user task and time-planning application, given that the work logic stays in Java?

Hard constraint given by the human: the UI framework must integrate into a Java binary, so the UI code is callable from, or co-hosted with, a Java entry point, and as much as possible stays in Java.

## Repository context

workthread is a Java 21 application. A framework-free Java core sits behind two kinds of ports: surface ports (the core implements them, a surface calls them) and infrastructure ports (the core calls them, an adapter implements them). Planned Gradle modules: `contract`, `core`, `adapters`, `wiring`, `api` (Spring Boot daemon), `mcp`, `cli`, `client`.

The desktop client is a surface. The same client code must reach the core in-process (standalone mode) and, in split mode, through a remote proxy that implements the same surface ports over the daemon's HTTP API. Requirements that constrain the framework choice: a task timer, a system tray icon with a popup, keyboard-only operation, and a packaged desktop image on Linux, macOS, and Windows.

Current decision: JavaFX, delivered as a jlink image with JavaFX on the module path. The human does not want JavaFX and will accept it only if nothing else fits.

## Method and evidence rules

Every claim below carries a link and a confidence level. Claims marked "inference" are the researcher's reasoning over documented facts, not statements found in a source. Benchmark and size figures are called out in "Missing evidence" where no primary measurement was found. Versions were checked on 18 November 2026 against Maven Central `maven-metadata.xml` timestamps and the vendors' own release pages.

---

## Candidate 1: Compose Multiplatform desktop target (JetBrains, Kotlin on the JVM)

### a) Entry point and interop

**A plain Java `main` can own the process, but it cannot own the window.** Compose Desktop entry points are Kotlin top level functions that take a `@Composable` lambda: "On desktop, Compose Multiplatform is built into a JVM app. The call to the common `App()` composable is made in the `main()` function, wrapped in an `application()` (or a `singleWindowApplication()`) call" ([Kotlin docs, native application entry points](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-entry-points.html), high).

**The Kotlin to Java direction is the designed direction and needs no glue.** "Existing Java code can be called from Kotlin in a natural way"; Java collections, getters and setters, checked exceptions and platform types all work directly ([Calling Java from Kotlin](https://kotlinlang.org/docs/java-interop.html), high). So the intended seam, a Kotlin UI calling the Java `contract` interfaces and Java surface ports, costs nothing. This is the important half for workthread.

**The Java to Kotlin direction needs a small facade, and the language documents exactly which annotations it needs** ([Calling Kotlin from Java](https://kotlinlang.org/docs/java-to-kotlin-interop.html), high):

- A companion member is only a real static method with `@JvmStatic`. Without it Java must write `C.Companion.callNonStatic()`.
- A function with default arguments is visible as the full signature only. `@JvmOverloads` generates the overloads. Note the compiler also offers `@IntroducedAt` for versioned overloads, which conflicts with `@JvmOverloads`.
- A top level function in `app.kt` compiles into `org.example.AppKt`, so Java calls `AppKt.getTime()`. `@file:JvmName` fixes the facade class name.
- Kotlin does not declare checked exceptions, so Java cannot catch them unless the Kotlin side carries `@Throws`.
- `internal` declarations are public in the bytecode with mangled names. They are reachable from Java but not pleasantly.
- Interface default methods do become real Java default methods, but only under the `-jvm-default` mode in effect (default `enable` also emits `DefaultImpls` compatibility bridges).

**Concrete glue for workthread is about ten lines**, and it is Kotlin, not Java:

```kotlin
// client/src/main/kotlin/workthread/client/Launcher.kt
object Launcher {
    @JvmStatic
    fun run(host: SurfacePorts) = application {
        Window(onCloseRequest = ::exitApplication, title = "workthread") { App(host) }
    }
}
```

```java
// client/src/main/java/workthread/client/Main.java
public final class Main {
    public static void main(String[] args) {
        Launcher.run(Wiring.standalone());
    }
}
```

Passing a Java interface (`SurfacePorts`) into Kotlin is the free direction. Passing a Kotlin lambda into Java is not: `() -> Unit` arrives as `Function0<Unit>`, so Java callbacks need a lambda that returns `Unit.INSTANCE`. (Interop rules: [Calling Kotlin from Java](https://kotlinlang.org/docs/java-to-kotlin-interop.html), high. Facade pattern: researcher inference, medium.)

**The friction the task asks to name, in order of real cost:**

1. **`@Composable` code is not compilable by javac.** The Compose runtime depends on compiler generated parameters (a `Composer` and changed-bit integers) plus group bookkeeping. "Compose Multiplatform requires the Compose Compiler Gradle plugin applied with the same version as the Kotlin Multiplatform plugin" ([Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), high). Consequence: every line of UI must live in a Kotlin source set with that plugin applied. There is no supported way to write a Compose screen in Java. (Inference from the requirement above, high.)
2. **Coroutines.** A Compose Desktop app runs on a Kotlin coroutine dispatcher backed by the AWT event thread. Any Java code that must "call the UI" needs a Kotlin wrapper, not a `CompletableFuture` that pokes state. `suspend` functions are uncompilable from Java without a generated `Continuation` parameter, so Java callers need a `runBlocking` or callback wrapper written in Kotlin ([Calling Kotlin Suspending Functions from Java, Baeldung](https://www.baeldung.com/kotlin/suspend-functions-from-java), medium: secondary source, used only for the mechanism, which follows from the Kotlin compiler's documented signature lowering).
3. **Data classes.** A Kotlin `data class` gives Java `component1()`, `component2()`, `copy()`, `copy$default()`. Usable, ugly, and it leaks Kotlin idioms into any Java adapter that touches UI models. Prefer plain Java records as the shared model and keep data classes inside `client`. (Inference from the documented generated members, high.)
4. **Kotlin stdlib and coroutines on the classpath.** A Compose client module pulls `kotlin-stdlib` and `kotlinx-coroutines`. That is unavoidable and it means the shipped image contains Kotlin even if nobody writes Kotlin outside `client`. (Inference from the documented dependency model, high.)

The net architectural read: the seam is one directional and thin. Kotlin UI calls Java core for free. Java never calls the UI except through one facade class. But **the entire client layer becomes a Kotlin module**, which is the direct cost against "as much as possible stays in Java".

### b) Build

The client module needs three Gradle plugins: `kotlin("jvm")`, `id("org.jetbrains.compose")`, and the Compose Compiler plugin whose version must match the Kotlin plugin ([Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), high). Compose Multiplatform 1.8.0 and later require Kotlin 2.1.0 or newer (same page, high).

`contract`, `core`, `adapters`, `wiring`, `api`, `mcp`, and `cli` can stay `java-library` modules with no Kotlin plugin. Only `client` takes the Kotlin toolchain. There is no second JDK: the Kotlin compiler runs inside the Gradle daemon and targets the project's JVM toolchain. Verified build shape from JetBrains' own example: `plugins { kotlin("jvm"); id("org.jetbrains.compose") }`, `implementation(compose.desktop.currentOs)` ([Native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html), high). Whether Gradle's Kotlin DSL plus a Kotlin compiler task measurably slows the build was not measured (see Missing evidence).

### c) Packaging

The Compose Gradle plugin wraps `jpackage` and `jlink`: "The plugin simplifies the process of packaging applications into native distributions using `jpackage`" and "To minimize package size, the Gradle plugin uses the jlink tool" ([Native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html), high). Formats: `.dmg`, `.pkg`, `.exe`, `.msi`, `.deb`, `.rpm`. **Cross-compilation is not supported**: "to build a `.dmg` binary, you have to run the `packageDmg` task on macOS" (same page, high). So three packaging jobs on three operating systems.

Known traps, all documented: `mainClass` may name any class with a `main` method, including a Java class (same page, high). JDK modules are **not** inferred, so a `ClassNotFoundException` at runtime is fixed by listing `modules("java.sql")` or running `suggestModules` (same page, high). Packaging needs JDK 17 or later because of `jpackage`, and the desktop runtime needs JDK 11 or later because of Skia memory management ([Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), high).

Native artifacts: Compose Desktop renders through Skiko, which ships Skia plus window-management glue as per-platform runtime jars (`skiko-awt-runtime-<os>-<arch>`, `skiko-platform-awt-runtime-<os>-<arch>`) ([Skiko README](https://github.com/JetBrains/skiko), high). Skiko's own platform list for the JVM is Linux x86_64 and arm64, Windows x86_64, macOS x86_64 and arm64 (same page, high).

Supported platforms for Compose Multiplatform 1.12.2: Windows 10 (x86-64, arm64), macOS 13 arm64, Ubuntu 20.04 (x86-64, arm64), and "all Compose Multiplatform releases support only 64-bit platforms" ([Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), high). Note the tension with the Skiko page, recorded under Contradictions.

Image size and cold start: **no primary measurement exists in the vendor documentation.** JetBrains documents the mechanism (jlink plus manual module list) and a `suggestModules` helper, not numbers. Any figure must be measured on this project.

### d) System tray

Compose Desktop has a first class `Tray()` composable, usable inside `application()` with or without a window ([Tray and notifications](https://kotlinlang.org/docs/multiplatform/compose-desktop-tray.html), high). Documented surface:

- `icon` as a `Painter` (drawn, not a bitmap file).
- `menu` with `Item(...)` entries: this covers "native menu actions".
- `tooltip`, `state: TrayState`, and `rememberNotification` plus `trayState.sendNotification(...)` for OS notifications.
- `onAction`: "a double click on Windows and a right-click on macOS".
- The menu opens "on a right-click on Windows and on a left-click on macOS".
- A tray-only application is supported: call `Tray()` alone and `exitApplication()` from a menu item.

**Popup anchored to the icon: not in the documented API.** The `Tray` parameters are icon, menu, state, tooltip and onAction. There is no anchor rectangle and no popup parameter, so a workthread popup window would have to be a Compose `Window` positioned from `java.awt.MouseInfo.getPointerInfo()` when the tray action fires. That is the same trick every other toolkit on this list needs. (Inference from the documented parameter list, medium.)

**Platform without a tray:** "If the platform doesn't support one, `Tray()` outputs an error to the standard error stream instead of throwing an exception. Check the `isTraySupported` property" (same page, high). That is a better failure mode than most toolkits, but it means the fallback UX is entirely the application's job.

### e) Accessibility and keyboard operation

The documented matrix ([Support for desktop accessibility features](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html), high, but the page is dated 3 December 2024, see Contradictions):

- macOS: fully supported.
- Windows: "Supported via Java Access Bridge", which is off by default (`jabswitch.exe /enable`), and a distribution must add the `jdk.accessibility` module.
- Linux: **"Not supported"**.

Keyboard: Compose ships a focus system (`Modifier.focusable`, `FocusRequester`, `focusProperties`), key events, and a documented tabbing navigation topic ([Desktop-only API](https://kotlinlang.org/docs/multiplatform/compose-desktop-components.html), high). Keyboard-only operation of a timer and a task list is buildable, but focus order and mnemonic behaviour are per-composable work, and there is no native menu-bar accelerator story for the app window beyond Compose's own `MenuBar`.

Verdict for this project: on Linux, one of the three target platforms, Compose gives no screen-reader support at all. JetBrains staff stated the reason in 2021: "We currently rely on JDK support for a11y and for Linux it is not that good" ([compose-multiplatform#1366](https://github.com/JetBrains/compose-multiplatform/issues/1366), medium: issue comment, not a current statement).

### f) Maturity, cadence, licence, risk

- Licence: Apache 2.0 ([LICENSE.txt](https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt), high).
- Current stable: **1.12.2**. 1.13.0-rc01 is in flight; Maven Central metadata `lastUpdated` for `org.jetbrains.compose:compose-gradle-plugin` is `20261118100925` ([maven-metadata.xml](https://repo1.maven.org/maven2/org/jetbrains/compose/compose-gradle-plugin/maven-metadata.xml), high).
- Cadence: "We align our Compose Multiplatform release cycle with the release cycle of Jetpack Compose", with "1-3 months" between the two, plus frequent alpha/beta/rc builds ([Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), high).
- Risk: the desktop target has shipped since 2021 and is stable, but its accessibility story is incomplete on Linux and its tray story is menu-only plus a hand-positioned window. The UI layer is Kotlin-only and version-locked to a compiler plugin. JetBrains is the sole steward of the desktop target.

---

## Candidate 2: JavaFX

### a) Entry point and interop

No interop seam exists, in either direction. A Java `main` extends `javafx.application.Application` or calls `Platform.startup`, and the scene graph is plain Java. JavaFX also embeds in Swing through `JFXPanel` when a Swing shell is wanted ([JavaFX 23 javadoc index for `javafx.embed.swing`](https://openjfx.io/javadoc/23/javafx.swing/javafx/embed/swing/JFXPanel.html), high). This is the only candidate where the requirement "as much as possible stays in Java" is satisfied with zero exceptions.

### b) Build

One toolchain. `org.openjfx.javafxplugin` declares modules and version and "will include JavaFX dependencies for the current platform", with targets `linux`, `linux-aarch64`, `win`, `osx`, `osx-aarch64` ([JavaFX Gradle Plugin README](https://github.com/openjfx/javafx-gradle-plugin), high). Nothing forces a second language, a second compiler, or a compiler plugin.

### c) Packaging

JavaFX ships as "a platform-specific SDK, as a number of jmods, and as a set of artifacts in Maven Central" ([openjfx.io](https://openjfx.io/), high). jmods on the module path plus `jpackage` (which itself runs `jlink`) is the documented route for JVM apps generally ([jpackage Packaging Overview](https://docs.oracle.com/en/java/javase/26/jpackage/packaging-overview.html), high). The exact JavaFX-plus-jlink recipe is Gluon/jpackage territory rather than a single canonical page, so confidence in the mechanics is medium and in the availability of jmods is high.

Native artifacts that ship: the Glass window toolkit and Prism renderer per platform, plus, if `javafx.web` is included, a full WebKit build (JavaFX 23 "Update WebKit to 617.1", with follow-ups "Cherry-pick WebKit 617.1 stabilization fixes" and "Additional WebKit 617.1 fixes from WebKitGTK 2.42.5", [JavaFX 23 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-23.md), high). Leaving `javafx.web` and `javafx.media` out of the module list is the main size lever, and it is available.

Platform requirements: Linux needs GTK 3.8 or later on JavaFX 21 ("The JavaFX GTK2 library has been removed", [JavaFX 21 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-21.md), high) and GTK 3.20 or later on JavaFX 25 ([JavaFX 25 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-25.md), high). macOS 11 or later on JavaFX 21 (same page, high). Supported platform matrix from the vendor: Windows x64, Linux x64, macOS x64, macOS aarch64, with Linux aarch64 "provided, not supported" ([Gluon JavaFX downloads](https://gluonhq.com/products/javafx/), high for the statement, medium for the support commitment since Gluon sells the support contract).

**Java 21 caps the JavaFX line, and this matters.** JavaFX 23 requires JDK 21 or later; JavaFX 25 "requires JDK 23 or later"; JavaFX 26 requires JDK 24 or later ([release notes 23, 25, 26](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-26.md), high). So on a Java 21 runtime the usable choices are JavaFX 21 LTS (min JDK 17, latest 21.0.13.1 in November 2026), 22.0.2, and 23.0.2 (last of its line, January 2025) ([Gluon roadmap table](https://gluonhq.com/products/javafx/), high). JavaFX 27.0.1.1 is current for the platform overall, and this project cannot use it on Java 21.

### d) System tray

**JavaFX has no tray API.** The request is JBS issue JDK-8092115, "Add JavaFX support for SystemTray", opened 2011, still open, 34 votes, blocked on JDK-8090475 ([JDK-8092115](https://bugs.openjdk.org/browse/JDK-8092115), high). The practical route is `java.awt.SystemTray` and `java.awt.TrayIcon` from `java.desktop`, which means mixing AWT into a JavaFX process. `TrayIcon` supports a native `PopupMenu` for menu actions, and non-menu popups are done by listening to mouse events and showing an undecorated JavaFX `Stage` positioned from `MouseInfo` ([Java Tutorials, How to Use the System Tray](https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html), high for the AWT API; the cross-toolkit positioning is inference, medium).

Two documented costs: threading discipline between the AWT event thread and the FX application thread, and `SystemTray.isSupported()` returning false. A 2020 openjfx-dev post states the Linux problem bluntly: "java.awt.TrayIcon is a pretty outdated API with an especially bad implementation on Linux ... in many cases SystemTray.isSupported() returns false despite there being a tray" ([openjfx-dev, April 2020](https://mail.openjdk.org/pipermail/openjfx-dev/2020-April/026001.html), medium: mailing list, dated).

### e) Accessibility and keyboard operation

JavaFX has a real accessibility API: `AccessibleRole`, `AccessibleAttribute`, `AccessibleAction`, with per-role attribute and action contracts documented in the javadoc (for example the TEXT_AREA and TABLE_VIEW roles) ([AccessibleRole, JavaFX 23](https://openjfx.io/javadoc/23/javafx.graphics/javafx/scene/AccessibleRole.html), high). Release notes for 21 and 23 list accessibility fixes in `controls` and `accessibility` subcomponents, so the implementation is maintained ([JavaFX 23 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-23.md), high).

Keyboard operation is the strongest of the four native options: built-in focus traversal, mnemonics, `Scene` accelerators, `KeyCombination` accelerators, and accessible defaults on every control. That is what a timer that must be driven from the keyboard wants.

Gap: I could not fetch the OpenJFX wiki accessibility page (HTTP 403), so the per-platform backend status on Linux (ATK) is unverified here. See Missing evidence.

### f) Maturity, cadence, licence, risk

- Licence: "GPL v2 with the Classpath exception" ([openjdk/jfx README](https://github.com/openjdk/jfx), high). Classpath exception means shipping it inside a proprietary image is fine.
- Cadence: every six months, March and September, with LTS every two years: 21 LTS (Sep 2023), 25 LTS (Sep 2025), 26 (Mar 2026), 27 (Sep 2026) ([Gluon roadmap](https://gluonhq.com/products/javafx/), high).
- Risk: the lowest technical risk of the group on this constraint, and the highest "the human does not want it" risk. The toolkit line usable on Java 21 (21 LTS or 23.0.2) is older than the JDK the project targets.

---

## Candidate 3: SWT and JFace (with Eclipse RCP noted)

### a) Entry point and interop

Plain Java. The canonical program is `new Display()`, `new Shell(display)`, then a `readAndDispatch` loop until dispose ([SWT README](https://github.com/eclipse-platform/eclipse.platform.swt), high). JFace adds viewers and databinding on top; Eclipse RCP adds a plugin framework, an OSGi runtime, and a workbench. Full interop with Java, zero glue.

### b) Build

One toolchain. SWT is distributed as "platform-specific jar files"; the Maven coordinates are fragments such as `org.eclipse.platform:org.eclipse.swt.gtk.linux.x86_64` ([SWT downloads](https://www.eclipse.org/swt/), high). No plugin, no compiler plugin, no second language.

### c) Packaging

`jpackage` and `jlink` work, but SWT is a set of jars with natives inside rather than JPMS modules with jmods, so the image is built from a class path plus a module path and needs `--add-modules`/`--add-opens` care. On Linux the host must have GTK 3 and its dependencies. Supported fragments listed by the project: Windows x86_64, Mac/Cocoa x86_64, Linux/GTK x86_64, Linux/GTK ppc64le ([SWT downloads](https://www.eclipse.org/swt/), high for the list, low for currentness: the page does not list macOS aarch64 even though that artifact is published). Image size and startup were not measured (Missing evidence).

### d) System tray

`org.eclipse.swt.widgets.Tray` and `TrayItem` exist since SWT 3.0: "Instances of this class represent the system tray that is part of the task bar status area on some operating systems" ([Tray javadoc](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/swt/widgets/Tray.html), high).

`TrayItem` gives `setImage`, `setToolTipText`, `setToolTip(ToolTip)`, `addSelectionListener`, `addMenuDetectListener`, `setVisible`, and a `setHighlightImage` for macOS ([TrayItem javadoc](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/swt/widgets/TrayItem.html), high). There is **no** `setMenu`: the menu is shown by the application inside a `MenuDetectListener`. The custom `ToolTip` class is the closest thing to an anchored popup in the tray API; anything richer means positioning a borderless `Shell` yourself. `Tray` itself "is part of the task bar status area on some operating systems", so the no-tray case is expected and must be handled by the app (same pages, high).

### e) Accessibility and keyboard operation

SWT maps to native widgets, so screen readers see native controls. The Eclipse project maintains an accessibility wiki and SWT has a documented accessibility test plan ([SWT resources](https://www.eclipse.org/swt/), medium: the links exist, the per-platform status was not readable). Keyboard traversal, mnemonics with `&`, and native menus are inherited from the platform, which is the best case for a keyboard-only tool.

### f) Maturity, cadence, licence, risk

- Licence: EPL-2.0 ([eclipse-platform/eclipse.platform.swt](https://github.com/eclipse-platform/eclipse.platform.swt), high).
- Current version: 3.135.0 is the latest and release on Maven Central ([maven-metadata.xml](https://repo1.maven.org/maven2/org/eclipse/platform/org.eclipse.swt/maven-metadata.xml), high). Versions track the Eclipse release train, so roughly quarterly.
- Risk: the README is candid: "Please bear in mind that this project is almost entirely developed by volunteers. If you do not provide the implementation yourself (or pay for it), the bug might never get fixed" ([SWT README](https://github.com/eclipse-platform/eclipse.platform.swt), high). The same README describes Initiative 31, an evaluation of replacing the three platform implementations with one Skia-based implementation, which has produced a demonstrator but no production plan. Outside Eclipse RCP the widget ecosystem is thin: no modern styling system, no declarative layout.
- Eclipse RCP specifically: it would bring an OSGi runtime, a workbench, and a plugin model that this project's port-and-adapter architecture does not need. It solves problems workthread does not have. (Inference, high.)

---

## Candidate 4: Swing

### a) Entry point and interop

Plain Java, `SwingUtilities.invokeLater`, `JFrame`. Zero glue. This is the only candidate that needs nothing beyond the JDK that is already there.

### b) Build

Nothing. `java.desktop` is part of the JDK; no plugin, no extra dependency, no second language.

### c) Packaging

`jpackage` with `jlink` and a small module list (`java.base`, `java.desktop`, plus what the app uses). No third party native runtime ships beyond what the JDK already carries, and the JDK's AWT layer needs GTK/X11 on Linux. This is the smallest plausible image of the five, and I could not find a primary measurement (Missing evidence).

### d) System tray

The reference implementation for trays on the JVM: `java.awt.SystemTray`, `TrayIcon`, native `PopupMenu`, `DisplayMessage`, and `isSupported()`. Anchored popups are done from `TrayIcon` mouse listeners plus `MouseInfo`, or by attaching a `JPopupMenu` to a `JWindow` placed near the icon ([How to Use the System Tray](https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html), high). The Linux caveat is identical to JavaFX's, since it is the same AWT code (see the openjfx-dev post above, medium).

### e) Accessibility and keyboard operation

Mature and boring: `InputMap`/`ActionMap`, mnemonics, focus traversal policies, and Java Accessibility (Java Access Bridge on Windows, AT-SPI on Linux, native on macOS). Highest confidence item in this section, lowest confidence in the specific per-platform statement because I did not fetch a current primary page for it (Missing evidence). Keyboard-only operation is genuinely first class.

### f) Maturity, cadence, licence, risk

- Licence: GPLv2 with Classpath exception, as part of OpenJDK.
- Cadence: ships with the JDK, twice a year, and has for decades. Java 21 LTS includes it.
- Risk: essentially none of abandonment, and essentially none of renewal either. Look and feel is dated; Nimbus and Metal are the built-ins, and modern styling means FlatLaf or similar. HiDPI is workable but not automatic everywhere. For a keyboard-first time planner, Swing is unfashionable but entirely capable, and it carries zero toolchain cost.

---

## Candidate 5: a local web interface (browser or embedded WebView, Java backend)

Three distinct shapes, all worth separating.

### 5a. Server plus the user's browser

**a)** A Java `main` starts an embedded server and opens the default browser. In workthread this is nearly free architecturally: the `api` module already exists, so the client becomes a second surface served from the daemon. The window is not yours. The seam that needs glue is the UI itself: HTML, CSS, and either hand written JS or a framework with a Node build.

**b)** A Node/pnpm toolchain appears the moment a front end framework is used. Vaadin Flow keeps UI code in Java by rendering on the server, but its current release still requires Node 24 and pnpm 11 alongside Spring Boot 4 and Java 21+ ([Vaadin 25.0.0 release notes](https://github.com/vaadin/platform/releases/tag/25.0.0), high).

**c)** jpackage can ship the server plus JVM. The browser is a dependency on the user's machine, at an unknown version.

**d)** Tray: the browser cannot do it. A separate tray process would be needed, which is a break of the "one client" idea even if the architecture already names the tray as its own surface.

**e)** Accessibility: best in class, if browser-based. Screen readers, zoom, high contrast all work through the OS.

**f)** Vaadin Flow is Apache 2.0 for the framework ([vaadin/flow](https://github.com/vaadin/flow), medium: licence file present with Apache headers; the commercial Pro component set is separate). Browsers are the most mature UI platform in existence.

### 5b. Embedded Chromium (JCEF)

JCEF embeds a full Chromium. The current build is CEF 152.0.6 on Chromium 152 ([jcefbuild release](https://github.com/jcefmaven/jcefbuild/releases/latest), high). `jcefmaven` offers two strategies: natives downloaded at first run (about 1 MB base artifact) or bundled per platform. The bundled native artifact is a full Chromium distribution, so it is large per platform; the number is not stated on a primary page I could reach (Missing evidence). Tray is not provided; you would still need AWT or JavaFX around it. Accessibility of embedded Chromium inside a Java window is not the same as a real browser and needs its own verification.

### 5c. JavaFX WebView

Same Java process, same `jpackage` image, WebKit 617.1 on a WebKitGTK 2.42 line base ([JavaFX 23 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-23.md), high). That is the oldest engine of the three, and it drags `javafx.web` plus its WebKit natives into the image. It buys a web UI without a Node build only if the UI is simple HTML and vanilla JS.

**Assessment for workthread:** the local web interface is the only option that dissolves the standalone/split distinction, because the UI always talks HTTP. That is architecturally attractive and architecturally dangerous at once: it would mean the client can no longer exercise surface ports in-process, and the "same client code in both modes" requirement would become "same UI, two transports". It also fails the tray requirement outright in the browser shape and fails "as much as possible stays in Java" in every shape except server-rendered Vaadin. (Inference, high.)

---

## Candidate 6: others the evidence turned up

- **Vaadin Flow**: covered in 5a. Server-side Java UI, so logic stays Java, but a Node toolchain and a browser are introduced, and the tray requirement is unmet.
- **Eclipse RCP**: covered under SWT. Adds OSGi and a workbench this project does not need.
- **JCEF and JavaFX WebView**: covered in 5b and 5c.
- No other JVM-hosted cross-platform toolkit appeared with a credible tray plus keyboard plus packaging story that beats the four above. GTK bindings for Java (java-gnome, gtk4-java) were not evaluated: they are unmaintained or single-platform in practice, and they would fail the Windows requirement. (Inference, medium: not sourced.)

---

## Comparison table

| | Interop from Java | Build toolchain | Packaging route | Image size | Tray | Platforms | Licence | Maturity |
|---|---|---|---|---|---|---|---|---|
| **Compose Multiplatform 1.12.2** | Java `main` calls one Kotlin `@JvmStatic` facade. Kotlin calls Java for free. No `@Composable` code in Java. | Kotlin plugin + Compose plugin + Compose Compiler plugin (version locked to Kotlin 2.1+) | Compose Gradle plugin over jpackage/jlink, no cross-compile | Not published; Skia/Skiko natives per platform | `Tray()` with menu, tooltip, notifications, `onAction`; no anchored popup; `isTraySupported` | Win x64/arm64, macOS 13 arm64, Linux x64/arm64, 64-bit only | Apache 2.0 | Stable since 2021, 6-month-ish cadence, Linux a11y unsupported |
| **JavaFX 21 LTS / 23.0.2** | None needed. Pure Java. | One (Java). Optional JavaFX Gradle plugin | jmods on module path, jpackage/jlink | Not published; Glass/Prism natives; WebKit only if `javafx.web` | No JavaFX API (JDK-8092115 open); AWT `SystemTray` + undecorated `Stage` | Win x64, Linux x64, macOS x64/arm64; Linux arm64 unsupported by vendor | GPLv2+CE | 6-month cadence, LTS every 2 years, capped at 23.0.2 on Java 21 |
| **SWT 3.135.0 / JFace** | None needed. Pure Java plus native fragments. | One (Java) | jpackage/jlink from jars; GTK3 required | Not published; natives inside fragments | `Tray`/`TrayItem`, menu via `MenuDetectListener`, no anchored popup | Win x64, macOS x64, Linux GTK x64/ppc64le | EPL-2.0 | Volunteer-maintained; rewrite under study |
| **Swing** | None needed. Pure Java. | One (Java), nothing added | jpackage/jlink, smallest module set | Not published; no third-party native runtime | `SystemTray`/`TrayIcon`, native `PopupMenu`, anchored `JPopupMenu` possible | Every JDK platform | GPLv2+CE | Oldest and most stable; dated look |
| **Browser + Java server** | No UI interop; HTTP only | Java plus Node/pnpm for Vaadin or any JS framework | jpackage for the server; browser is the user's | Server image only | Impossible in browser | Any modern browser | Vaadin Flow Apache 2.0; browser | Highest (browser), medium (Vaadin 25.0.0) |
| **Embedded Chromium (JCEF/CEF 152)** | Java calls into Chromium; UI in HTML/JS | Java plus front-end build | jpackage plus large native bundle | Chromium per platform, size unverified | Not provided | Win/macOS/Linux per jcefbuild | BSD-style (CEF), verify | Maintained, version-chained to CEF |

---

## Recommendation

**Ship JavaFX, on JavaFX 21 LTS, as a jlink image with JavaFX on the module path.** The evidence does not support replacing it.

Why, in the order the constraint demands:

1. It is the only candidate where the UI is Java, callable from a Java `main`, with no second language, no compiler plugin, and no interop annotations. Compose fails this by construction: the UI layer becomes Kotlin, and the client module gains a compiler plugin whose version must track Kotlin's.
2. It has the strongest keyboard story of the native options (focus traversal, mnemonics, accelerators) and a maintained accessibility API, which is what a keyboard-first timer needs.
3. It has jmods, so `jlink` and `jpackage` produce an image with no extra runtime discovery work.
4. Its licence, GPLv2 with the Classpath exception, is the same as the JDK's, so there is no new licence question.

Runner-up: **Compose Multiplatform**. It is the only candidate that could plausibly displace JavaFX, because it is the only one whose interop cost is a fixed one-time facade rather than a per-screen tax, and because its declarative model suits a state-driven timer and list UI.

**The strongest argument against the recommendation:** JavaFX on Linux does not actually deliver a first-class Linux client either. Its tray is AWT, which on GNOME and Wayland frequently reports `isSupported() == false`; its Linux accessibility backend could not be verified from primary sources here; and staying on Java 21 pins the toolkit to JavaFX 21 LTS or 23.0.2 while the rest of the JavaFX line has moved to 27. So the recommendation may be choosing "familiar and Java" over "better on Linux", not "better on Linux". If the tray on Linux is a hard requirement rather than a nice-to-have, no toolkit on this list satisfies it without a desktop-environment-specific fallback, and that should be decided by measurement, not by framework choice.

**Specific evidence that would change the answer:**

- A measured spike showing `Tray()` plus an anchored Compose `Window` behaves acceptably on GNOME/Wayland, KDE, macOS and Windows, **and** the team accepting Kotlin in `client`. Under those two conditions Compose wins, because the remaining JavaFX advantages are then stylistic.
- A Compose Multiplatform release that documents Linux accessibility support and a tray anchor rectangle. That removes the two documented functional gaps at once.
- A finding that JavaFX 21 LTS or 23.0.2 cannot show a tray popup under Wayland at all while AWT `SystemTray` works under XWayland, which would flip the tray comparison in Compose's favour only if Compose's tray path avoids AWT. It does not: Compose's `Tray` is also AWT based (inference from the documented `Painter`/`TrayState` model plus the JVM-only desktop target, medium).
- A measured Compose image and cold start that are clearly better than JavaFX's. Unlikely to matter, but it would remove the "unknown cost" objection.

---

## Does the recommendation earn a throwaway prototype?

**No, not to choose a framework.** JavaFX already satisfies the stated hard constraint with zero interop, and the alternative's disqualifiers (Kotlin-only UI layer, no Linux accessibility) are documented, not hypothetical. A prototype cannot make "the UI is no longer Java" go away.

**But the Java 21 pin and the tray both deserve a two-day spike before the choice is locked**, and it is the same spike either way:

1. A `jlink`/`jpackage` image on Linux, macOS, and Windows from a `mainClass` that is a plain Java class, with JavaFX on the module path and `javafx.web` and `javafx.media` excluded. Record image size and cold start, since neither is published for any candidate.
2. A tray icon with a native menu **and** an anchored popup window opened at the icon, tested on GNOME with and without the app-indicator extension, on KDE, on macOS, and on Windows. Record `SystemTray.isSupported()` per environment and what the app does when it is false.
3. Keyboard-only operation of the timer: start, stop, switch task, dismiss the popup, with focus visible at every step and no mouse.

**If the human wants Compose anyway, the prototype must prove exactly these, in this order:**

1. The Gradle graph: `contract`, `core`, `adapters`, `wiring`, `api`, `mcp`, `cli` remain `java-library` modules; only `client` carries the Kotlin plugin and the Compose Compiler plugin. A Java `main` in `client` launches the app through one `@JvmStatic` Kotlin facade, and that facade is the only Kotlin that Java ever touches.
2. A `jpackage` image per platform built from that Java `mainClass`, with `modules(...)` complete enough to avoid `ClassNotFoundException` on first run.
3. The tray: icon, menu actions that call surface ports, and a popup window positioned at the icon. Measured on the same four environments as above.
4. Keyboard-only operation of the timer with visible focus, with no reliance on `SwingPanel` and no AWT `Frame` in the shell.
5. Screen reader behaviour on Linux recorded honestly. If Orca reads nothing, that is a documented gap the human accepts in writing, or Compose is out.
6. Measured image size and cold start, reported next to the JavaFX numbers from the same machine.

If that prototype is not run, the recommendation stands as written, and the honest summary is: **nothing on this list beats JavaFX on the stated constraint, and Compose's costs are structural, not cosmetic.**

---

## Contradictions

1. **Windows arm64 for Compose.** Compose Multiplatform 1.12.2 lists Windows 10 "x86-64, arm64" ([Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)), while Skiko's own README lists only "Kotlin/JVM on Windows(x86_64)" ([Skiko README](https://github.com/JetBrains/skiko)). Either the Skiko README is stale or the compatibility table is optimistic. Unresolved; low impact for a single-user app, but it means "Windows arm64" should not be promised.
2. **Compose accessibility freshness.** The desktop accessibility matrix (macOS fully, Windows via JAB, Linux not supported) is dated 3 December 2024 ([page](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html)) while Compose Multiplatform is at 1.12.2 in November 2026. The page may be stale. It is not contradicted by anything I found, and the 2021 maintainer comment in [issue #1366](https://github.com/JetBrains/compose-multiplatform/issues/1366) points the same way, so I treat it as probably still true but not current.
3. **SWT platform list.** The SWT downloads page lists Mac/Cocoa x86_64 and Linux GTK ppc64le but not macOS aarch64 or Windows arm64, while those artifacts are published. The page is stale ([SWT](https://www.eclipse.org/swt/)).
4. **JavaFX macOS minimum.** The compatibility table entry reads "macOS 13 arm64" as a minimum version, while Gluon lists macOS x64 and aarch64 as supported platform/architecture pairs. The two are answering different questions; not a real conflict, recorded because the table is easy to misread.
5. **source_check outcome.** I ran `source_check` on the Compose accessibility claim. It returned `missing-evidence` (confidence 0.20) and surfaced no passages, even though its own source list includes the JetBrains documentation page I had already fetched and read. I therefore rely on the fetched page directly. This is a validation limitation, not a contradiction of the claim.

## Missing evidence

1. **Image size and cold start for every candidate.** No primary source publishes numbers. JetBrains documents the jlink mechanism and `suggestModules` but no figure; Gluon publishes downloads, not sizes; SWT, Swing, JCEF, and Vaadin publish nothing comparable. Any size claim in a decision document must be measured on this project, on the three target operating systems.
2. **JavaFX accessibility backend status per platform.** The OpenJFX wiki accessibility page returned HTTP 403 on every attempt. I verified the JavaFX accessibility API and that accessibility defects are fixed each release, but not the Linux (ATK) or Windows backend statement.
3. **SWT accessibility per platform.** The Eclipse accessibility wiki was reachable only as a link. The claim that SWT inherits native screen-reader behaviour is inference from the native-widget design, not a sourced statement.
4. **Swing accessibility per platform.** Same gap. The AWT accessibility bridges are real, but I did not fetch a current primary page for the Linux AT-SPI path.
5. **Tray behaviour on Wayland and GNOME for all candidates.** Only a 2020 mailing list post states the Linux problem. No current primary source for GNOME 4x with or without an app-indicator extension, and none for KDE Plasma 6.
6. **Compose Desktop on Wayland.** Skiko and Compose Desktop rendering under Wayland was not researched. If the client must run natively under Wayland, this is an open risk for both Compose and JavaFX.
7. **JCEF bundled native size.** The jcefbuild release page names the Chromium version but not the artifact sizes. The "about 100 MB per platform" figure appears only in a secondary wiki (DeepWiki) and is deliberately not used.
8. **Vaadin Flow licence detail.** The repository carries Apache 2.0 headers, but the split between the free framework and the commercial component set was not verified from a licence page.
9. **Build-time cost of adding Kotlin to the Gradle build** was not measured.
10. **GTK version on the target Linux distribution.** JavaFX 21 needs GTK 3.8+, JavaFX 25 needs GTK 3.20+, and this project's distribution is unknown.

## Sources

- Kept: [Compatibility and versions, Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html). Platform matrix, Kotlin and Compose Compiler plugin requirement, JDK 11 runtime and JDK 17 packaging floors, release cadence.
- Kept: [Native distributions, Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html). The jpackage/jlink route, format list, no cross-compilation, manual `modules` list, `mainClass` may be any Java class.
- Kept: [Tray and notifications, Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-desktop-tray.html). The full documented `Tray()` parameter list and the `isTraySupported` fallback.
- Kept: [Support for desktop accessibility features, Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html). The macOS/Windows/Linux matrix and the `jdk.accessibility` requirement.
- Kept: [Calling Kotlin from Java](https://kotlinlang.org/docs/java-to-kotlin-interop.html). Every interop friction named in the Compose section.
- Kept: [Calling Java from Kotlin](https://kotlinlang.org/docs/java-interop.html). Confirms the cheap direction.
- Kept: [Native application entry points, Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-entry-points.html). The `application {}` entry point shape.
- Kept: [Swing interoperability, Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-desktop-swing-interoperability.html). `ComposePanel` and `SwingPanel`, i.e. the hybrid escape hatch.
- Kept: [Desktop-only API index, Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-desktop-components.html). Confirms tray, menu bar, keyboard, and tabbing topics exist as documented desktop features.
- Kept: [compose-multiplatform releases](https://github.com/JetBrains/compose-multiplatform/releases) and [compose-gradle-plugin maven-metadata](https://repo1.maven.org/maven2/org/jetbrains/compose/compose-gradle-plugin/maven-metadata.xml). 1.12.2 stable, 1.13.0-rc01 in flight, timestamp.
- Kept: [Skiko README](https://github.com/JetBrains/skiko). The Skia native artifacts and the JVM platform list.
- Kept: [openjdk/jfx README](https://github.com/openjdk/jfx) and [openjfx.io](https://openjfx.io/). GPLv2+CE, SDK/jmods/Maven distribution forms.
- Kept: [Gluon JavaFX downloads and roadmap](https://gluonhq.com/products/javafx/). The only primary source for the release table with LTS markers, minimum JDK per release, and dates.
- Kept: [JavaFX 21](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-21.md), [23](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-23.md), [25](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-25.md), [26](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-26.md) release notes. Minimum JDK per release, GTK requirements, WebKit 617.1.
- Kept: [JavaFX Gradle Plugin README](https://github.com/openjfx/javafx-gradle-plugin). Module declaration, platform targets, native dependency scope.
- Kept: [JDK-8092115](https://bugs.openjdk.org/browse/JDK-8092115). JavaFX has no system tray and the request is still open.
- Kept: [openjfx-dev, April 2020](https://mail.openjdk.org/pipermail/openjfx-dev/2020-April/026001.html). The Linux AWT tray problem, dated and attributed.
- Kept: [JavaFX 23 AccessibleRole javadoc](https://openjfx.io/javadoc/23/javafx.graphics/javafx/scene/AccessibleRole.html). The accessibility API surface.
- Kept: [jpackage Packaging Overview](https://docs.oracle.com/en/java/javase/26/jpackage/packaging-overview.html). jpackage generates its runtime with jlink; module path options.
- Kept: [How to Use the System Tray](https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html). The AWT tray API that JavaFX, Compose, and Swing all end up using.
- Kept: [SWT project page](https://www.eclipse.org/swt/), [SWT README](https://github.com/eclipse-platform/eclipse.platform.swt), [Tray javadoc](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/swt/widgets/Tray.html), [TrayItem javadoc](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/swt/widgets/TrayItem.html), [SWT maven-metadata](https://repo1.maven.org/maven2/org/eclipse/platform/org.eclipse.swt/maven-metadata.xml). Platform fragments, volunteer maintenance, tray API shape, current version.
- Kept: [Vaadin 25.0.0 release notes](https://github.com/vaadin/platform/releases/tag/25.0.0) and [vaadin/flow](https://github.com/vaadin/flow). Node 24, pnpm 11, Spring Boot 4, Java 21+, Apache 2.0 headers.
- Kept: [jcefbuild latest release](https://github.com/jcefmaven/jcefbuild/releases/latest). CEF 152.0.6 on Chromium 152, dated.
- Deprioritized: [Best Practices for Interop, InformIT](https://www.informit.com/articles/article.aspx?p=2952625&seqNum=3). Secondary, redundant with the Kotlin documentation.
- Deprioritized: [Kotlin Java Interop Handbook](https://sakydev.github.io/kotlin-interview-handbook/java-interop.html). Third-party restatement of `@JvmStatic` behaviour.
- Deprioritized: [Baeldung, calling suspend functions from Java](https://www.baeldung.com/kotlin/suspend-functions-from-java). Used only for the `Continuation` mechanism, which the compiler's documented signature lowering already implies.
- Deprioritized: [DeepWiki, Compose native packaging](https://deepwiki.com/JetBrains/compose-multiplatform/3.4-native-packaging-and-distribution) and [DeepWiki, jcefmaven user guide](https://deepwiki.com/jcefmaven/jcefmaven/2-user-guide). Machine-generated summaries; not used as evidence.
- Deprioritized: [Stack Overflow, Kotlin Multiplatform Java sources on desktop](https://stackoverflow.com/questions/79633289/kotlin-multiplatform-java-code-not-found-on-desktop-app). Anecdotal.
- Rejected: OpenJFX wiki accessibility page and JavaFX Releases wiki page. HTTP 403, so unobtainable rather than unhelpful; recorded under Missing evidence.

## Next steps

1. Run the two-day spike (jlink image on three operating systems, tray plus anchored popup in four desktop environments, keyboard-only timer) and record size, cold start, and `isSupported()` per environment.
2. Decide explicitly whether "as much as possible in Java" is a hard constraint or a preference. Everything else follows from that answer.
3. If the answer is "a preference", run the six-point Compose prototype above before writing any client code.
