---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the desktop views
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

> Tasks, Projects, Review, and Settings, including the two editing doors onto the
> estimate and the dependency editor with its cycle message.

### Destination

A complete desktop client against the specification's five views, on top of the
shell.

### Actor

The person doing everything the command line can do, but in a window.

### Classification

Feature.

### Stories

1. As a person in the window, I want the Tasks view, so that I can browse, filter,
   and inspect work without the order changing under me.
2. As a person with several streams, I want the Projects view, so that I can see
   and change project state.
3. As a person reviewing, I want the Review view, so that the month is visible
   without the terminal.
4. As a person adjusting an estimate, I want both doors, the total and the
   remaining, so that the window matches the rule rather than approximating it.
5. As a person creating a dependency, I want the cycle message with the tasks in
   the loop, so that I can fix it in the window.

### Constraints

- The shell's navigation contract is fixed and this effort does not change it.
- Views hold no state of their own: the ports are the source of truth, so the
  window and the terminal cannot disagree.
- The estimate keeps two doors, and setting the remaining writes logged time plus
  the new remaining as the total.
- The dependency editor refuses a cycle with the tasks in the loop named, the
  same refusal the command line gives.
- Settings reached here are core settings through `ForConfiguring`, plus the
  surface's own preferences.
- No new planning capability of any kind. Anything the views show already exists
  behind a port.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    T14-021, T036

That is 2 rows, and the small number is deliberate: the command line delivers
browse, inspect, and review first, and these views reuse those capabilities. The
rows are the ones only the window can satisfy.

### Position and release, already settled

Eleventh in the build order, blocked by `10-client-shell`, release 0.8 The
window. Its successor is `12-tray-surface`. The full order is the table under
`## Effort order` in the parent's architecture document.
