---
type: task
subtype: grilling
title: Settle the pre-v1 releases, their feature sets, and which child effort lands where
status: deprecated
workflow_state: done
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

## Notes

### Settled: ten releases, 0.1 to 0.9 and then 1.0

Recorded in the map's `## Releases` section, which this task's write-back owns.

**Answers taken, all as recommended.**

- One release per observable capability: nine pre-v1 releases, not one per stage
  and not one per effort.
- The desktop stage is one release holding both client efforts, so the window
  arrives with all five views rather than as a Today-only shell.
- v1 is `1.0` after the tray release, and it adds no capability.

**A conflict resolved, worth keeping on the record.** This task's own
recommended starting answer said v1 is the release in which the client and the
tray exist beside the command line. `version-order` settled that v1 is the
release *after* the tray stage, with every stage inside the pre-v1 run. The
settled decision won: `0.9` carries the tray, `1.0` demonstrates the completion
signals. The draft predates that decision.

**Groundwork folded, not numbered.** `scaffolding` and `work-store` ship nothing
a user can observe, and the staging rule says a version is done when it works on
real tasks. They therefore sit inside the `0.1` boundary, and `0.1` is done only
when all three of its efforts are done. Giving them a number would have put two
releases in the sequence that add nothing a user can do.

**Naming and tags.** A number plus a functional name: `0.1 Capture`, `0.2 The
work model`, `0.3 The day`, `0.4 Risk`, `0.5 Review and export`, `0.6 Agents`,
`0.7 The daemon`, `0.8 The window`, `0.9 The tray`, `1.0`. Tags follow the
numbers, `v0.1.0` through `v1.0.0`, so lexical order matches release order.

**Alternatives rejected.**

- One release per stage, five. Rejected because the first release would hold
  seven efforts and five capability areas would ship as a single step, which
  hides the capability axis `version-order` settled.
- One release per child effort with the groundwork numbered. Rejected because it
  puts two releases in the sequence that add nothing a user can do, against the
  rule that a version is done when it works on real tasks.
- A Today-only window first, then the remaining views. Rejected because it ships
  a window that cannot create a task, when the command line already can.
- v1 as the tray release. Rejected as a contradiction of `version-order`.

**Constraints the plan carries.**

- No release is done while a child effort inside it is unfinished.
- Every interface an earlier release shipped keeps working in every later one.
- Each release is tagged before the next opens.
- Each release is exercised on real data, not on fixtures.

**Downstream.** The close creates the twelve child efforts with
`origin_effort: planner-v1`, each carrying its release in its own map. The report
that ends this effort names the first child, `scaffolding`, and the release it
lands in, `0.1`. The release plan is also what the human tracks progress
against, since the child efforts are tracked individually.

**Write-back.** The plan is the map's `## Releases` section, which names this
task.
