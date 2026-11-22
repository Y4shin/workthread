---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the desktop shell
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

> JavaFX and Spring in one JVM with the measured shutdown order; navigation; and
> the Today view including the fit signal, the per-item reasons, and the cut
> line.

### Destination

A window showing the primary output, driven by the same ports as the command
line, in either run mode.

### Actor

The person who prefers a window, and the later views that mount inside this
shell.

### Classification

Feature.

### Stories

1. As a person opening the application, I want a window with the ordered today
   list, so that I can see what to work on without the terminal.
2. As a person reading the list, I want the fit signal, the reasons, and the cut
   line, so that the window is not a worse version of the command line.
3. As a person with a daemon running, I want the window to attach to it, and to
   work alone when it is not, so that I never choose a mode.
4. As a person closing the window, I want the process to shut down in a defined
   order, so that nothing is half-written and nothing hangs.

### Constraints

- JavaFX 21 LTS, Spring only where a module needs it, and the core depending on
  neither; the client stays a Java module with no second language.
- The shutdown order is measured and fixed: `Platform.exit()` on the FX thread,
  a latch, then `context.close()`, because the context does not close on
  `Platform.exit()` and the hook order under a fat jar is not stable.
- Navigation and the shell hold no work logic; everything goes through the
  surface ports, so the window and the terminal cannot disagree.
- Core settings go through the core's configuration port; surface preferences
  such as theme and window placement are the surface's own, with a port for the
  variant where the core remembers them.
- The client and the tray ship from the `client` module as a `jlink` image with
  JavaFX on the module path.
- Only the Today view here: Tasks, Projects, Review, and Settings belong to the
  next effort.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.19, SRC-RULE-ARCH-5, T04-012, T07-001..T07-003,
    T07-005..T07-007, T11-030, T11-031, T11-085..T11-087, T18-012, T22-001,
    T22-007, T22-010, MAP-018, MAP-069, MAP-C13, CTX-client

That is 22 rows.

### Position and release, already settled

Tenth in the build order, blocked by `09-daemon-api`, release 0.8 The window. Its
successor is `11-client-views`. The full order is the table under
`## Effort order` in the parent's architecture document.
