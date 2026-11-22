---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for review and export
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

> Review projections over past days and months, export as the documented JSON
> archive with `VACUUM INTO` for a backup, the configurable purge age, the local
> timezone, and settings.

### Destination

A command line interface that meets the core product scope. On finishing this
effort the core product is usable end to end from a terminal, which is the gate
the build order sets for opening the agent stage.

### Actor

The person keeping records and reviewing whether the days went where they meant
them to.

### Classification

Feature.

### Stories

1. As a person reviewing a month, I want each past day's target minutes, presence
   minutes, and their difference, so that the month tells the truth.
2. As a person whose weekday hours changed, I want last month's review to change
   too, and to understand why, so that the review is honest about being
   recomputed.
3. As a person keeping records, I want one documented archive I could read
   without the application, so that my data outlives it.
4. As a person worried about loss, I want a backup that restores the application,
   so that a disk failure is recoverable.
5. As a person with stale archives, I want a purge age that is off by default, so
   that deletion happens only when I asked for it.

### Constraints

- Review measures presence, not task time; presence is start to stop minus the
  reserve. No past list is stored and none is reconstructed.
- The export is a zip holding one documented JSON document, with a header
  carrying the format version, the application version, and the export time. The
  format is engine-independent and versioned; v1 writes no attachments directory.
- The backup is the database file taken with `VACUUM INTO` or by copying the data
  directory while nothing writes; the export survives the application, the
  backup restores it.
- The purge age is off by default, nothing is deleted while it is off, and the
  purge is a scheduled consequence rather than an operation anyone can invoke.
- Ephemeral notices and finished pending operations are deleted immediately; the
  operation log is kept forever in v1.
- The timezone, the settings, and the data location are reachable from the
  `config` area.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.24, SRC-4.16, SRC-4.23, SRC-RULE-DATA-2, SRC-8.8.16, T11-028,
    T11-056, T12-016, T13-050, T13-051, T034, T17-006, T17-007, T17-009,
    T17-011, T17-012, T17-016..T17-027, T25-009, MAP-057, MAP-058,
    CTX-presence, CTX-export..CTX-purge

That is 34 rows.

### Position and release, already settled

Seventh in the build order, blocked by `06-cli-forecast`, release 0.5 Review and
export. Its successor is `08-mcp-agent-interface`. The full order is the table
under `## Effort order` in the parent's architecture document.
