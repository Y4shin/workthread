---
type: task
subtype: prototype
title: Can a JavaFX client ship as an image, and which tray approach works
status: stable
workflow_state: done
blocked_by: []
---

## The one question

Can a JavaFX 21 client be shipped as a self-contained image from a Gradle
build, and which tray approach actually works on this platform?

## Alternatives worth comparing

Packaging:

- `jlink` driven by a third-party Gradle plugin such as `org.beryx.jlink`.
- `jpackage` driven from a plain Gradle `Exec` task.
- A hand-written `jlink` command with no plugin.

Tray:

- The AWT system tray (`java.awt.SystemTray`), which pulls in `java.desktop`
  and needs a native image.
- A third-party JavaFX tray library.
- No tray in the image at all, deferring the tray to its own stage.

## Smallest artifact that answers it

A Gradle project with one JavaFX window, one tray icon with a two-item menu,
and one action that hides and restores the window. Build it into an image by
the first packaging route, run it under `xvfb-run`, and check three things:
the window starts from the image, the tray icon appears where the platform
supports a tray, and the image size is recorded. Then try the second packaging
route and compare the effort.

## Who must react

The human, for the tray behavior, since a system tray is visual and
platform-specific. The architecture grilling, for the packaging constraint.

## What it unblocks

`architecture` (the packaging and image constraint, and which module a tray
library belongs to) and `child-efforts` (the tray child effort's scope).

## Where the artifact lives

Build it under `/tmp/planner-v1-tray-packaging/`, outside the repository, so
throwaway code never lands. Record the observations, the build files that
mattered, and the image size in this task's notes, then delete the directory.

## Notes

### Findings

Full evidence, the build files that worked, image sizes, and the failure
modes: `docs/research/prototype-tray-packaging.md`.

**Question answered.** Yes: a JavaFX 21 client ships as a self-contained image
from a Gradle build. All three routes worked.

| Route | Effort | Image size |
| --- | --- | --- |
| `org.beryx.jlink` 2.26.0, one plugin block, one task | least | 66,344,990 B (64 MB) |
| `jpackage --type app-image` from a Gradle `Exec` | more | 105,739,716 B default, 67,714,602 B with jlink compress options |
| Hand-written `jlink` task | more build code | 66,304,306 B |

Size came from the options, not the tool: dropping `--strip-debug` on this
JDK yields 354 MB, dropping `--compress` 104 MB. The `--jmod` artifacts that
older guides mention do not exist on Maven Central; every route consumes
modular jars.

**Chosen direction.** The `org.beryx.jlink` plugin, with `--strip-debug
--compress zip-6`. It was the least effort of three working routes and it
generates the launcher.

**The image is not fully self-contained on Linux.** Every image still needs
the host's GTK3 and X11 libraries. With an empty `LD_LIBRARY_PATH` the image
aborts on `libXxf86vm.so.1` and `libXtst.so.6`. That is a distribution
constraint for the desktop stage, not a packaging-route difference, and it must
be recorded in the packaging child effort's scope.

**Tray.** Every JavaFX tray library wraps AWT. `FXTrayIcon` 4.2.3 is a thin
wrapper whose module descriptor requires `java.desktop` and `javafx.swing`, and
whose support check is `Desktop.isDesktopSupported() && SystemTray.isSupported()`.
Its own README rates Linux "partially supported" and says not to rely on
`isSupported`. The Dorkbox library adds twenty jars including JNA, Kotlin, and
Javassist. Because `javafx.graphics` already requires `java.desktop`, a
no-tray image saves nothing, so the tray is an application decision rather
than a packaging one.

**Chosen direction.** `FXTrayIcon` guarded by `SystemTray.isSupported()`, with
graceful degradation to the window when no tray exists. Unguarded construction
throws `UnsupportedOperationException` and ends the application, which the
prototype reproduced.

**Hazards actually observed.** Every `jpackage` image printed an unexplained
"pure virtual method called" at startup, including a pure-JavaFX app, and the
app still ran. JavaFX natives are extracted from the image and cached in
`~/.openjfx/cache`, which works with a read-only home. AWT initialization
inside the GTK app emits a `Gdk-WARNING` about `XSetErrorHandler`.

**Not verifiable here.** Whether the icon appears, whether the native menu
fires, GTK and AWT stability with a real tray, GNOME and AppIndicator behavior,
HiDPI, and the deb and rpm artifacts. This machine has no tray daemon and no
window manager, so a real desktop test is an acceptance item for the tray
child effort.
