---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for the command line basics
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

> The `cli` module and its argument parsing; the standalone configurator and
> runtime from `wiring`; capture, list, inspect, and complete; the timer and
> logged time; and the minimal day start the timer requires, with the day length
> read from one configured default.

### Destination

A working command line application where a task is captured, found, worked, and
completed, time is corrected, and the whole thing runs in one process with no
daemon. This is where the command envelope, the exit codes, and the JSON shape
are fixed for every later CLI effort.

### Actor

The person who prefers a terminal, and every later CLI effort that must live
inside the shape this one fixes.

### Classification

Feature.

### Stories

1. As a person at a prompt, I want to capture a task with one line, so that
   recording a request costs nothing.
2. As a person listing work, I want to see tasks and inspect one, so that I can
   find what I captured a week ago.
3. As a person working, I want to start the timer, pause it, and complete the
   task, so that the record matches what I did.
4. As a person correcting a mistake, I want to edit or delete a time entry, so
   that a wrong minute does not stay wrong.
5. As a scripter, I want every command to answer `--help`, print one JSON
   document under `--format json`, and return a documented exit code, so that I
   can drive it from a shell.

### Constraints

- Seven areas mapped one to one onto the surface ports, plus `mcp serve` and
  `confirm`; the shape and the exit codes are settled by the parent effort and
  are not re-decided here.
- Text output by default, one global `--format json`, errors on standard error,
  `NO_COLOR` honoured, six exit codes with `1` reserved.
- Prompting only for the three confirmation cases, and only on a terminal;
  otherwise a pending operation and exit `4`.
- The first version sorts by deadline then priority, with no ordering engine, no
  projects, and no estimates.
- Standalone mode only: the `wiring` configurator with one authority and one
  surface, and no daemon.
- The minimal day start exists only because the timer needs it; the timetable
  replaces it later, and it is not extended here.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.1, SRC-4.12, SRC-4.13, SRC-4.15, SRC-RULE-ORDER-0,
    SRC-RULE-EFFORT-5, SRC-RULE-ARCH-1..SRC-RULE-ARCH-3, SRC-RULE-DATA-1,
    SRC-8.8.1, T01-001, T02-024, T02-027, T03-005, T03-007, T03-008, T11-013,
    T11-022, T11-025, T12-003, T12-004, T12-008, T14-010..T14-018, T14-020,
    T037, T18-003, T18-009, T24-022, T25-001..T25-003, T25-006,
    T25-012..T25-030, T25-034..T25-038, T25-042, MAP-001, MAP-013, MAP-022,
    MAP-028, MAP-045, MAP-046, MAP-C08, MAP-C14, CTX-standalone-mode,
    CTX-concurrent-work

That is 75 rows.

### Position and release, already settled

Third in the build order, blocked by `02-work-store`, release 0.1 Capture. Its
successor is `04-cli-work-model`. The full order is the table under
`## Effort order` in the parent's architecture document.
