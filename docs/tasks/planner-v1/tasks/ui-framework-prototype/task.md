---
type: task
subtype: prototype
title: Prove the recommended UI framework runs as one packaged application from a Java entry point
status: stable
workflow_state: done
blocked_by:
- ui-framework-research
---

## Question

Does the framework the research recommends actually run as one packaged
application, started from a Java `main`, with its window built in the other
language, and meet the size, startup, and tray expectations?

## Gate

This task runs only if `ui-framework-research` recommends a candidate that keeps
the work logic in Java. If the research finds none, this task is marked
deprecated with that reason recorded, and JavaFX stays.

## Alternatives worth comparing

Only the recommended candidate against the JavaFX baseline, measured the same
way. Comparing several toolkits is the research's job, not this prototype's.

## The smallest artifact

One throwaway Gradle build that does five things:

- starts from a Java `main`;
- calls one Java class that stands in for a surface port;
- opens a window showing a list and a button that changes it;
- adds a tray icon where the platform has one;
- closes in the recorded shutdown order, surfaces then authority.

Then package it and measure.

Record: image size, cold start to first paint, whether the tray works on the
developer's desktop, what the service does with no tray available, and what the
build file had to add.

## Who must react

The human. The result is a reaction to a running window, not a specification.

## What it unblocks

`child-efforts`, and the architecture record's client, packaging, and tray
decisions.

## Downstream

The close. Delete the throwaway build; nothing here is mergeable.

## Notes

### Gate check after `ui-framework-research`: the gate did not fire

The research recommended JavaFX 21 LTS, which is the incumbent, and stated that
no prototype is earned to choose a framework, because a prototype cannot make
the UI stop being Java. The gate in this task's body therefore did not fire:
nothing non-JavaFX was recommended.

The task is not dead, it is conditional. It runs only if
`ui-framework-decision` answers that "as much as possible in Java" is a
preference and that the human still wants Compose. It is marked blocked for
that reason.

### Its scope, if it does fire

Build one throwaway Gradle project under `docs/research/prototype-compose/`
and prove these six points, in this order:

1. The Gradle graph. `contract`, `core`, `adapters`, `wiring`, `api`, `mcp`,
   and `cli` stay `java-library` modules. Only `client` carries the Kotlin
   plugin and the Compose Compiler plugin. A Java `main` in `client` launches
   the app through one `@JvmStatic` Kotlin facade, and that facade is the only
   Kotlin Java ever touches.
2. A `jpackage` image per platform built from that Java `mainClass`, with the
   `modules(...)` list complete enough to avoid `ClassNotFoundException` on
   first run.
3. The tray: icon, menu actions that call a surface port, and a popup window
   positioned at the icon. Measured on GNOME with and without the
   app-indicator extension, on KDE Plasma, on macOS, and on Windows.
4. Keyboard-only operation of the timer: start, stop, switch task, dismiss the
   popup, with visible focus at every step and no mouse.
5. Screen reader behaviour on Linux, recorded honestly. If Orca reads nothing,
   that gap is accepted in writing by the human or Compose is out.
6. Image size and cold start, measured on the same machine as the JavaFX
   numbers, reported side by side.

Delete the throwaway project when the numbers are recorded. Nothing here is
mergeable.

Blocked: Gate did not fire: `ui-framework-research` recommended the incumbent JavaFX and stated that no framework-choice prototype is earned. Runs only if `ui-framework-decision` answers that "as much as possible in Java" is a preference and the human still chooses Compose. The six points it must prove are recorded in its Notes.

### Closed without an artifact: the question is answered by evidence this effort already has

`ui-framework-decision` settled JavaFX, on the 21 LTS line, and the human
declined the prototype because JavaFX works natively inside the Java process.

The question this task asks, whether the chosen toolkit runs as one packaged
application started from a Java `main`, is therefore answered for the toolkit
that ships, and it is answered by evidence already on this map:

- `prototype-javafx-spring-jvm` built a Java `main` that hosts Spring Boot and
  JavaFX in one JVM, with the shutdown order measured at 7 ms.
- `prototype-tray-packaging` built the `jlink` image at 64 MB and proved the
  AWT-backed tray, with two packaging routes measured.

Nothing is built here. The six-point Compose scope recorded above is kept as the
reason it will never be needed, not as pending work.

Two measurements remain unowned, and both belong to efforts that build the
product rather than to this planning effort:

- image size and cold start on Linux, macOS, and Windows, measured on this
  project, because no vendor publishes the numbers;
- the tray with an anchored popup on GNOME with and without the app-indicator
  extension, on KDE Plasma, on macOS, and on Windows, recording
  `SystemTray.isSupported()` per environment. The map already routes a real
  desktop test to the tray child effort as an acceptance item, and that item now
  also carries the Wayland and GNOME question.
