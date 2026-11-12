---
type: task
subtype: grilling
title: Settle the pre-v1 releases, their feature sets, and which child effort lands where
status: stable
workflow_state: ready
blocked_by:
- architecture
- child-efforts
---

## Decision to settle

Settle the concrete pre-v1 release plan: which releases exist, what each one
contains, and which child effort lands in which release.

For each release, record: a name or number, the version order stage it maps to,
the feature set a user can observe, the child efforts it contains, and the
criteria that make the release done. Then say which release is v1.

## Parent decisions

`version-order` settles the stage order and the staging rule.
`child-efforts` produces the effort list, their scopes, and their order.
`architecture` fixes the boundaries the efforts work inside. This task assigns
efforts to releases; it does not rescope an effort or reorder the stages.

## Known choices

- A release per stage: CLI basics, backend, MCP, daemon, desktop, tray.
- A release for the first useful CLI, then fewer, larger releases.
- Naming releases by function rather than by number.

## Recommended starting answer

One release per stage, in the human's stated order, each with a name that says
what a user can do with it, and each containing the child efforts whose scopes
fit that stage. v1 is the release in which the desktop client and the tray
exist beside the CLI. Record the two release-level constraints that follow from
the staging rule: an earlier interface keeps working in every later release,
and no release is called done while a child effort inside it is unfinished.

## Downstream work

Feeds the close, where the child efforts are created with
`origin_effort: planner-v1`, and the report that tells the human which effort
starts first.
