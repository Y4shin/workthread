---
type: task
subtype: prototype
title: Can a JavaFX client ship as an image, and which tray approach works
status: stable
workflow_state: ready
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
