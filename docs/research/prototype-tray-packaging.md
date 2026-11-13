---
type: findings
title: "Prototype: JavaFX image packaging and the system tray"
status: stable
source_task: tray-packaging
---

# Prototype findings: JavaFX 21 image packaging + system tray

Question: can a JavaFX 21 client ship as a self-contained image from a Gradle
build, and which tray approach works on this platform?

All work happened under `/tmp/planner-v1-tray-packaging/`. Nothing under
`/home/patric/Projects/workthread` was touched. Nothing was deleted.

## 1. Environment (measured, not assumed)

| Item | Value |
| --- | --- |
| JDK | `21.0.12.1+1-nixos` (`JAVA_HOME=/nix/store/56mv72maqaj8j241d98fjcvwswaipfay-openjdk-21.0.12.1+1/lib/openjdk`) |
| Gradle | 8.14.4 (`GRADLE_USER_HOME=/tmp/planner-v1-gradle-home`) |
| jlink / jpackage | both present in that JDK, `jmods/` present |
| Display | Xvfb only (`xvfb-run -a --server-args="-screen 0 1280x800x24"`), no window manager, no desktop session, **no tray daemon / no panel** |
| JavaFX | `org.openjfx:javafx-{base,controls,graphics}:21.0.5:linux` from Maven Central |
| Plugin portal | `plugins.gradle.org` reachable; `org.openjfx.javafxplugin` 0.1.0 and `org.beryx.jlink` resolve |

`stalonetray` / `trayer` are not installed in this environment, so "tray
present" could not be simulated.

Note: `org.openjfx:javafx-graphics:21.0.5:linux@jmod` **does not exist** on
Maven Central (404). Only modular JARs are published, so every `jlink` here
consumes modular JARs, not jmods. It works, but see §6 for what jlink then
does and does not put into the image.

## 2. Answer in one table

| Route | Status | Image size (bytes) | Image size (du) | Build command |
| --- | --- | --- | --- | --- |
| 1. `jlink` via `org.beryx.jlink` 2.26.0 | **worked** | 66 344 990 | 64M | `gradle jlink` |
| 2. `jpackage` from a Gradle `Exec` task (`--type app-image`) | worked | 105 739 716 | 102M | `gradle jpackageImage` |
| 2b. same, plus `--jlink-options ...--compress=zip-6` | worked (ran) | 67 714 602 | 65M | `jpackage` (see §5) |
| 3. hand-written `jlink`, no plugin | worked | 66 304 306 | 64M | one `jlink` line (§6) |
| 3b. hand-written `jlink` without `--compress=zip-6` | built, size-only | 104 361 734 | 100M | §7 |
| 3c. hand-written `jlink` **without** `--strip-debug` | built, size-only | 354 172 843 | 338M | §7 |
| FXTrayIcon variant (hand `jlink`, `javafx.swing` added) | worked (ran) | 70 644 746 | 68M | §8 |

Every image that was *run* (route 1, route 2, route 2b, route 3, the
FXTrayIcon variant, the `mini` diagnostic and `jp-nogc`) opens its window under
`xvfb-run` and prints `MARKER_WINDOW_SHOWN showing=true`. The two
`size-probe/` and `route3-...` variants in row 3b/3c were built only to
attribute the size differences; they were not executed.

Route 1 is the route that worked first and is the recommendation; routes 2 and
3 were then run anyway so the comparison is real. Effort difference is small:
route 1 is one plugin block, route 3 is one command, route 2 is ~15 lines of
Gradle plumbing that hand-writes a `jpackage` command line.

## 3. The prototype app

`/tmp/planner-v1-tray-packaging/app/src/main/java/com/example/tray/TrayApp.java`

* one JavaFX window with a `Label`,
* an AWT tray icon (`java.awt.SystemTray` + `PopupMenu` + `TrayIcon`) with two
  items, "Hide window" and "Restore window",
* every step prints a `MARKER_` line so behaviour is readable on a headless box,
* CLI flags for the probe: `--self-test` (runs both menu actions on the FX
  thread), `--probe-unsupported-add` (calls `SystemTray.add()` anyway),
  `--exit-after-ms=N`.

```
app/
  build.gradle          # route 1, inlined in §4
  settings.gradle       # rootProject.name = 'tray-prototype'
  src/main/java/module-info.java
  src/main/java/com/example/tray/TrayApp.java
```

`module-info.java`:

```java
module com.example.tray {
    requires javafx.controls;
    requires java.desktop;

    exports com.example.tray;
}
```

Development path also works: `xvfb-run -a gradle --no-daemon run --args="--self-test --exit-after-ms=4000"`
prints the same markers without any image, which is the cheap way to iterate.

`xvfb-run -a gradle --no-daemon run` needs `--no-daemon`: a long-lived Gradle
daemon keeps the `DISPLAY` it was started with.

The unqualified `exports` matters: without it a modular `Application`
subclass cannot be constructed and the app dies with
`IllegalAccessException: ... does not export com.example.tray to module javafx.graphics`
(observed on a throwaway `Mini` app).

## 4. Route 1 — `org.beryx.jlink` (worked)

Smallest build file that produced a working, runnable image
(`/tmp/planner-v1-tray-packaging/app/build.gradle`, complete):

```groovy
plugins {
    id 'application'
    id 'org.openjfx.javafxplugin' version '0.1.0'
    id 'org.beryx.jlink' version '2.26.0'
}

repositories {
    mavenCentral()
}

javafx {
    version = '21.0.5'
    modules = ['javafx.controls']
}

application {
    mainModule = 'com.example.tray'
    mainClass = 'com.example.tray.TrayApp'
}

jlink {
    options = ['--strip-debug', '--no-header-files', '--no-man-pages', '--compress', 'zip-6']
    launcher {
        name = 'tray-prototype'
    }
}
```

Commands:

```bash
cd /tmp/planner-v1-tray-packaging/app
export GRADLE_USER_HOME=/tmp/planner-v1-gradle-home
gradle --no-daemon jlink            # -> app/build/image/
xvfb-run -a --server-args="-screen 0 1280x800x24" \
  ./build/image/bin/tray-prototype --self-test --exit-after-ms=4000
```

Tasks executed: `prepareMergedJarsDir`, `createMergedModule`,
`createDelegatingModules`, `prepareModulesDir`, `jlink` (9 s total).
Image: `bin/{java,keytool,tray-prototype}`, `lib/modules` (32 MB),
`lib/server/libjvm.so` (27 MB), 74 files.

Launcher generated by the plugin:

```sh
#!/bin/sh
DIR="${0%/*}"
"$DIR/java" $CDS_JVM_OPTS -p "$DIR/../app" -m com.example.tray/com.example.tray.TrayApp "$@"
```

(`$DIR/../app` does not exist in the image — the app module ended up inside
`lib/modules`. Harmless, but it means the generated launcher is not a clean
template to copy.)

Facts worth carrying forward:

* `org.beryx.jlink` plugin marker **2.27.0 does not exist** on the plugin
  portal (`2.26.0` is the newest 2.x marker); the first attempt failed with
  `Plugin [id: 'org.beryx.jlink', version: '2.27.0'] was not found`.
* The image ends up with an extra synthetic module
  `tray.prototype.merged.module` in addition to `com.example.tray`; it is 40 KB
  of overhead and does not affect launching.
* `--compress zip-6` in the `options` list is what keeps this image at 64 MB
  instead of 100 MB (§7).

## 5. Route 2 — `jpackage` from a Gradle `Exec` task (also worked)

`/tmp/planner-v1-tray-packaging/route2-jpackage/build.gradle` (app sources are
reused via `sourceSets.main.java.srcDirs = ['../app/src/main/java']`) ends with:

```groovy
tasks.register('stageJpackageInput', Sync) {
    from tasks.jar
    from configurations.runtimeClasspath
    into layout.buildDirectory.dir('jpackage-input')
}

tasks.register('jpackageImage', Exec) {
    dependsOn 'stageJpackageInput'
    def inputDir = layout.buildDirectory.dir('jpackage-input').get().asFile
    def destDir = layout.buildDirectory.dir('jpackage').get().asFile
    doFirst { delete destDir; destDir.mkdirs() }
    commandLine 'jpackage',
            '--type', 'app-image',
            '--name', 'tray-prototype',
            '--dest', destDir.absolutePath,
            '--module-path', inputDir.absolutePath,
            '--module', 'com.example.tray/com.example.tray.TrayApp',
            '--java-options', '-XX:+UseSerialGC'
}
```

Run: `gradle --no-daemon jpackageImage` (7 s), then
`xvfb-run -a build/jpackage/tray-prototype/bin/tray-prototype --self-test --exit-after-ms=3000`.

The staged input directory holds the app JAR plus the three JavaFX linux JARs;
because the app is modular and `--module` is used, jpackage's internal `jlink`
absorbs those modules into `lib/runtime/lib/modules`, so **no app JARs are
shipped loose**: the image is `bin/tray-prototype` (native launcher),
`lib/libapplauncher.so`, `lib/app/tray-prototype.cfg`, `lib/runtime/`.

Defaults are fat: jpackage passes `--strip-native-commands --strip-debug
--no-man-pages --no-header-files` but **no `--compress`**, hence 102 MB. Adding

```
--jlink-options "--strip-debug --no-header-files --no-man-pages --compress=zip-6"
```

brings it to 67 714 602 bytes (65 MB), i.e. the same ballpark as routes 1 and 3.

Observed failure/misbehaviour: see §9, first entry — every jpackage app-image
built here prints a native `pure virtual method called` /
`terminate called without an active exception` pair at startup (reproducible,
also with a pure-JavaFX app that never touches AWT), yet the window opens and
the process exits 0.

`--type app-image` needs no `fakeroot`, `dpkg-deb` or `rpmbuild`; `.deb`/`.rpm`
were not attempted.

## 6. Route 3 — hand-written `jlink` (also worked)

```bash
FXJARS=$(find /tmp/planner-v1-gradle-home/caches/modules-2/files-2.1/org.openjfx -name '*linux.jar' | tr '\n' ':' | sed 's/:$//')
jlink --module-path "$FXJARS:/tmp/planner-v1-tray-packaging/app/build/libs/tray-prototype.jar" \
      --add-modules javafx.controls,com.example.tray \
      --output route3-handjlink-image \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --launcher tray-prototype=com.example.tray/com.example.tray.TrayApp

xvfb-run -a ./route3-handjlink-image/bin/tray-prototype --self-test --exit-after-ms=3000
```

66 304 306 bytes, same module set as route 1 minus the synthetic merged module.
jlink accepts the JavaFX modular JARs directly (no jmods needed) and keeps the
ten JavaFX native libraries (`libglassgtk3.so`, `libprism_es2.so`, …) as
resources inside `lib/modules`; the runtime loader pulls them out of the image
(§10).

The generated launcher is the stock jlink one:

```sh
#!/bin/sh
JLINK_VM_OPTIONS=
DIR=`dirname $0`
$DIR/java $JLINK_VM_OPTIONS -m com.example.tray/com.example.tray.TrayApp "$@"
```

## 7. Where the size actually goes

Measured on this Nix JDK with the same module set (`javafx.controls` +
`com.example.tray`):

| jlink options | size |
| --- | --- |
| `--strip-debug --no-header-files --no-man-pages --compress=zip-6` | 66 304 306 (64M) |
| `--strip-debug --no-header-files --no-man-pages` | 104 361 734 (100M) |
| `--no-header-files --no-man-pages` only | 354 172 843 (338M) |

The 338M figure is not a typo: without `--strip-debug`, jlink copies the
unstripped `libjvm.so` (247 MB) instead of the stripped one (28 MB). On this
JDK `--strip-debug` strips native ELF debug sections as well as class debug
attributes, so it is the single most important size option, and `--compress`
is the second.

`java.desktop` is not optional: `javafx.graphics` module descriptor says
`requires java.desktop`, and every image above therefore contains it. Measured
delta of adding `java.desktop` on top of `java.base`:
41 713 418 → 59 358 187 bytes, i.e. **+17 644 769 bytes already paid for by
JavaFX**. Dropping the AWT tray from the app saves no modules and no
meaningful size — only the app code.

## 8. Tray, approach by approach

All tray probes ran under the same `xvfb-run` Xvfb with no tray daemon.

| Approach | `SystemTray.isSupported()` | Result |
| --- | --- | --- |
| AWT `java.awt.SystemTray` + `PopupMenu` (in the image, route 1/2/3) | `false` | unguarded `add()` throws `UnsupportedOperationException: The system tray is not supported on the current platform.`; guarded app prints `MARKER_TRAY_UNSUPPORTED` and keeps a working window |
| `FXTrayIcon` 4.2.3 (JavaFX API, in the image) | `false` (`FXTrayIcon.isSupported()` also `false`) | constructor/`Builder.build()` throws `UnsupportedOperationException: SystemTray icons are not supported by the current desktop environment.`; **unguarded it kills the app** (`Exception in Application start method` → `Exception running application`, exit 1, window disappears) |
| `Dorkbox SystemTray` 4.4 | not run | docs-only, see below |
| No tray in the image at all | n/a | viable: `java.desktop` is already in every JavaFX image, so "no tray" is an app-code decision, not a packaging one |

The two menu actions themselves are exercised by `--self-test` and work:

```
MARKER_WINDOW_HIDDEN
MARKER_SELFTEST_AFTER_HIDE showing=false
MARKER_WINDOW_RESTORED showing=true
MARKER_SELFTEST_AFTER_RESTORE showing=true
```

What is *not* verified: that the icon appears, and that the native menu items
invoke those actions on a desktop with a real tray.

### FXTrayIcon 4.2.3 (`com.dustinredmond.fxtrayicon:FXTrayIcon`)

* It is a wrapper, not an alternative implementation: `module-info` is
  `requires javafx.controls, javafx.swing, javafx.graphics, java.desktop`, and
  the class doc says "The FXTrayIcon class handles all the messy AWT and Swing
  parts of constructing an icon". `isSupported()` is literally
  `Desktop.isDesktopSupported() && SystemTray.isSupported()`, and the
  constructor calls `SystemTray.getSystemTray()`.
  **So yes: a JavaFX tray library adds AWT (and `javafx.swing`) anyway.**
* Cost in the image: the `javafx.swing` variant of the app is
  70 644 746 bytes vs 66 304 306 for the AWT version (**+4 340 440 bytes**,
  ~4.1 MiB), adding modules `javafx.swing`, `jdk.unsupported.desktop`,
  `com.dustinredmond.fxtrayicon`.
* It also drags `org.openjfx:javafx-swing:19` transitively (resolved up to
  21.0.5 by the JavaFX plugin).
* Its README, "Supported operating systems" table, says for Linux:
  *"Partially supported — Some desktop environments that support
  java.awt.SystemTray are supported. Many are **not**. You should not rely on
  the `isSupported` method as a matter of truth, testing on individual desktop
  environments is strongly encouraged."*

### Dorkbox SystemTray 4.4 (`com.dorkbox:SystemTray`) — docs only, not run

README facts (verbatim gist):

* "Professional, cross-platform SystemTray support for *Swing/AWT*,
  *GtkStatusIcon*, and *AppIndicator* on Java 8+." Linux/Unix auto-selects the
  native GtkStatusIcon or AppIndicator menus and falls back to Swing "in cases
  where libraries are missing or there are un-resolvable GTK version conflicts".
* "Sun/Oracle system-tray and SWT system-tray implementations **do not**
  support app-indicators, which are necessary on different distributions of
  Linux/Unix."
* "GNOME3 desktop environments hide or remove entirely system tray icons
  (hidden from 3.16-3.25, removed from 3.26+)"; Debian + GNOME 3 "will only
  show in a tray via pressing SUPER+M"; Ubuntu 16.04+ with JavaFX "require
  `libappindicator1` because of JavaFX GTK and indicator panel
  incompatibilities"; WSL needs extra work.
* The compatibility table lists many ✓ rows for specific distro+DE pairs, all
  conditioned on `AutoDetect` mode.

Dependency weight, resolved from Maven Central (20 JARs, ~9.9 MB): `SystemTray`,
`Collections`, `Desktop`, `Executor`, `JNA`(dorkbox), `Utilities`, `OS`,
`Updates`, `javassist 3.29.2`, `jna-jpms` + `jna-platform-jpms` 5.13.0,
`java-uuid-generator`, `slf4j-api`, `kotlin-stdlib 1.8.20` (+ `-common`,
`-jdk7`, `-jdk8`), `kotlinx-coroutines-core-jvm` + `-jdk8`, `annotations`.
Its AWT/Swing menu classes are in the same JAR (`dorkbox/systemTray/ui/swing/`,
36 `swing`/`awt` class paths), so Dorkbox also implies AWT on the classpath
and, on Linux, additionally native GTK/appindicator glue through JNA.

### Interaction observed when AWT is touched from a JavaFX GTK app

Whenever the AWT tray code runs (even just `SystemTray.isSupported()`), the
process prints:

```
Gdk-WARNING **: XSetErrorHandler() called with a GDK error trap pushed. Don't do that.
```

It does **not** appear for a pure-FX JavaFX app in the same image
(`mini` probe, §9). Nothing crashed here, but this is the seam where
JavaFX-GTK and AWT-X11 share the process, and it only matters on a desktop
where AWT actually initialises a tray.

## 9. Failure modes seen (all of them)

1. `pure virtual method called` + `terminate called without an active
   exception` at startup, in **every** `jpackage --type app-image` build here
   (4 builds / 8 runs: default, `--jlink-options`, no-GC, and a pure-JavaFX
   `Mini` app with no AWT code at all). Window still opens, exit code 0. Not
   seen in route 1 or route 3 images. Unattributed: jpackage app-launcher vs.
   GTK teardown in this Nix/WSL environment. Needs a real desktop to judge.
2. `ES2 Prism: Error - GLX extension is not supported` — expected under Xvfb
   (no GLX). Prism falls back to the software pipeline and the window shows;
   `-Dprism.order=sw` is not needed.
3. `SystemTray.getSystemTray().add(icon)` → `UnsupportedOperationException`
   on an unsupported platform (AWT), and `FXTrayIcon` throws from its
   constructor. Unguarded, that exception ends the app; guarded, the app runs
   with a window and no tray.
4. `Plugin [id: 'org.beryx.jlink', version: '2.27.0'] was not found` — the
   marker does not exist; 2.26.0 is the newest 2.x.
5. `IllegalAccessException: ... does not export com.example.mini to module
   javafx.graphics` when `module-info.java` exports nothing — modular JavaFX
   apps must export their package.
6. `javafx-graphics-21.0.5-linux.jmod` → 404 on Maven Central. jmods are not
   published; use the modular JARs.
7. Running an image with an empty `LD_LIBRARY_PATH` aborts with
   `UnsatisfiedLinkError: ... libXxf86vm.so.1: cannot open shared object file`
   and `libXtst.so.6` — the JavaFX natives need the host's X11/GTK3 stack
   (§10).
8. Stale-daemon trap: `gradle run` under `xvfb-run` with a long-lived Gradle
   daemon reuses the *daemon's* environment, so `DISPLAY` may not be the one
   you just created. Used `--no-daemon` for every run here.

## 10. What the image still needs from the host

The image is self-contained for the JVM, the JavaFX modules and the JavaFX
native libraries — but not for the native libraries' own dependencies.
`libglassgtk3.so` links against `libgtk-3.so.0`, `libgdk-3.so.0`, `libpango`,
`libcairo`, `libgdk_pixbuf`, `libgio/glib/gobject`, `libXtst.so.6`, `libX11`,
`libXi`, `libXfixes`, `libepoxy`, `libfontconfig`, `libharfbuzz`,
`libfribidi`, `libatk-bridge`. On a normal desktop distro those are present; on
a minimal container or a NixOS host they must be provided (here they come from
`LD_LIBRARY_PATH`).

Native loading path, verified with `-Dprism.verbose=true` on the route 1 image:

```
WARNING: java.lang.UnsatisfiedLinkError: Can't load library: .../image/lib/libprism_es2.so
Loaded library /libprism_es2.so from resource
```

i.e. the loader first tries `image/lib`, then falls back to the copy embedded in
`lib/modules`. With `-Duser.home` pointed at a fresh directory the image
extracts the ten JavaFX `.so` files to `$HOME/.openjfx/cache/21.0.5+1/amd64/`;
with a read-only `user.home` it still starts (loads straight from the image).
So the cache is a best-effort optimisation, not a requirement. All ten `.so`
files are present inside both the route 1 and route 3 images
(`jimage list <image>/lib/modules | grep '\.so$'` → 10 entries), so nothing is
downloaded or shelled out at run time.

## 11. Exact image sizes (bytes, as measured)

```
app/build/image                                     66344990   64M   route 1 (beryx)
route3-handjlink-image                              66304306   64M   route 3 (hand jlink)
fxtray-image                                        70644746   68M   FXTrayIcon variant
route2-jpackage/build/jpackage/tray-prototype      105739716  102M   route 2 (jpackage, default)
jp-compressed/tray-prototype                        67714602   65M   route 2 + --jlink-options --compress=zip-6
size-probe/strip-nocompress                        104361734  100M   jlink, --strip-debug, no compress
size-probe/nocompress                              354172843  338M   jlink, no --strip-debug
```

`du -sh` rounds the two 64 MB images to 64M; they differ by 40 684 bytes, the
synthetic module route 1 adds.

## 12. Recommendations

* **Packaging: `org.beryx.jlink` 2.26.0 with `--strip-debug` + `--compress
  zip-6`** (route 1). One plugin block in `build.gradle`, one task (`jlink`),
  63 MB image, launcher generated, and it handles the modular-JAR-instead-of-
  jmods problem for you. Keep `--compress=zip-6`; keep `--strip-debug` (it is
  worth 250 MB on this JDK). If a `.deb`/installable ever matters, route 2
  (`jpackage`, with `--jlink-options ...--compress=zip-6`) is the escape hatch at
  the cost of hand-maintained command-line plumbing and one native-startup
  anomaly to chase.
* **Tray: assume AWT.** Every JavaFX tray library checked wraps or reuses AWT
  (`FXTrayIcon` literally; Dorkbox keeps Swing/AWT menus and adds JNA +
  appindicator glue). `java.desktop` is already in every JavaFX image, so the
  tray choice is about *app code and behaviour*, not about module weight. The
  tray must degrade: gate everything on `SystemTray.isSupported()` and keep the
  window usable when it is false, because in the only environment available
  here it *is* false, and FXTrayIcon's own README tells you not to trust that
  check on Linux.
* Do not build a "no-tray image" as a separate artefact. Same image, tray
  installed only when supported.

## 13. Could not be verified without a real desktop session

* That a tray icon actually appears, and that the native menu items really
  invoke hide/restore. `SystemTray.isSupported()` is `false` here and there is
  no tray daemon or panel to attach to.
* Whether JavaFX-GTK + AWT-X11 coexistence is stable once a tray *is* present
  (the `Gdk-WARNING ... XSetErrorHandler()` line is the only signal, and the
  AWT tray path never ran to completion).
* Which desktop environments behave like FXTrayIcon's "partially supported"
  caveat, or whether Dorkbox's AppIndicator path helps on GNOME (needs
  `libappindicator`/`libayatana-appindicator` installed and a session bus).
* Whether the `pure virtual method called` message from `jpackage` images has
  any user-visible effect (window and exit code were fine here).
* HiDPI/tray-icon scaling and tooltip behaviour.
* `.deb`/`.rpm` packaging (`jpackage --type deb/rpm` needs `fakeroot`/`rpmbuild`,
  not attempted).

## 14. Layout of what was left behind

```
/tmp/planner-v1-tray-packaging/
  FINDINGS.md                 this file
  app/                        route 1 project (beryx jlink) + build/image
  route2-jpackage/            route 2 project (jpackage Exec) + build/jpackage
  route3-handjlink-image/     route 3 image
  fxtray/                     FXTrayIcon 4.2.3 variant + fxtray-image/
  mini/                       pure-JavaFX diagnostic app + jpackage image
  jp-nogc/, jp-compressed/    jpackage option probes
  size-probe/                 jlink size probes
  logs/                       stdout of every run referenced above
  docs/                       FXTrayIcon + Dorkbox READMEs, SystemTray pom/jar
  probe/, probe2/             artifact inspection scratch
  home*/, freshhome*/, rohome/, tmpdir/  user.home isolation probes
```

Delete with `rm -rf /tmp/planner-v1-tray-packaging` when the effort no longer
needs it (also `/tmp/planner-v1-gradle-home` if the cache is unwanted).
