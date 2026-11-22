---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the tray
status: stable
workflow_state: ready
blocked_by: []
---

Settle this effort's non-goals and non-negotiable facts, including its
effort-level success test. The scope, the requirement list, and the position are
recorded below and come from the parent effort.

### The raw request, verbatim

The human asked for all twelve child efforts in one pass of intake. This child's
scope, quoted from the parent map's `## Child efforts` section:

> The tray icon, the popup showing the current timer, the next item, and critical
> notices, the native actions, and degradation to the window when no tray
> exists. It carries the real desktop test across GNOME with and without the
> app-indicator extension, KDE Plasma, macOS, and Windows, recording
> `SystemTray.isSupported()` in each.

### Destination

v1 itself, the release after this stage: a tray that shows the current timer, the
next item, and the warnings that ask something of the user, and that degrades to
the window instead of failing.

### Actor

The person interrupted constantly, who wants a glance rather than a window.

### Classification

Feature.

### Stories

1. As a person at my desk, I want a tray icon with the current timer, so that a
   glance is enough.
2. As a person about to switch tasks, I want the next item in the popup, so that
   the decision is already made.
3. As a person with a warning, I want it visible from the tray, so that I do not
   have to open the window to be told.
4. As a person on a machine with no tray, I want the application to open the
   window instead of dying, so that a missing feature is not a crash.
5. As a maintainer, I want the support check recorded on four desktops, so that
   the weakest part of the JavaFX choice is measured rather than assumed.

### Constraints

- `FXTrayIcon` guarded by `Desktop.isDesktopSupported() &&
  SystemTray.isSupported()`, checked before construction, because unguarded
  construction throws and ends the application.
- The popup shows the current timer, the next item, and the warnings that ask
  something of the user. No notice level named critical exists; the retired
  severity must not come back through this surface.
- The popup is an ephemeral notice channel, so it also participates in the
  confirmation code's delivery ladder.
- The native actions go through the same ports as every other surface.
- The real desktop test is an acceptance item, not an optional check: GNOME with
  and without the app-indicator extension, KDE Plasma, macOS, and Windows, with
  what the support check reports recorded on each.
- Linux is expected to be the weak case: AWT inside the GTK application emits a
  `Gdk-WARNING`, and icon appearance and native menu firing cannot be verified
  headlessly.
- No new planning capability of any kind.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.20, SRC-2.1.25, SRC-4.24, SRC-8.8.12, SRC-8.8.14, T06-006, T06-007,
    T06-009..T06-011, T06-014..T06-016, T11-089, T22-011, T22-013, T23-009,
    MAP-017

That is 18 rows.

### Position and release, already settled

Twelfth and last in the build order, blocked by `11-client-views`, release 0.9
The tray. Nothing follows it inside the pre-v1 run; 1.0 is the completion gate
with no new capability. The full order is the table under `## Effort order` in
the parent's architecture document.
