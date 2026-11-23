---
type: task
subtype: research
title: Build the machine-parseable requirements directory that the spec and every child effort are checked against
status: deprecated
workflow_state: done
blocked_by: []
---

## What this produces

`docs/v1/requirements-directory.csv`, one row per source item, with a fixed
header and the columns settled by `spec-traceability`:

`id,title,source,status,target,effort,spec,note`

The file sits outside `docs/tasks/` because the write guard refuses the tree and
because the directory must outlive this effort's archive.

## The sources to walk

Walk every one of these, in this order, and give each item a row:

1. `docs/tasks/planner-v1/initial_spec.md` section 2.1, every capability
   bullet, as `SRC-2.1.<n>`.
2. The same file section 4, every journey, as `SRC-4.<n>`.
3. The same file section 5, every distinct rule identifier, as
   `SRC-RULE-<id>`. There are 104.
4. The same file section 8.8, every completion signal, as `SRC-8.8.<n>`.
5. The same file section 9, every unresolved decision, as `DEC-<n>`. There are
   16, and each one has a settled answer somewhere on the map.
6. The map's `## Decisions so far`, every bullet, as `MAP-0<n>` in document
   order.
7. The map's `## Constraints`, every bullet, as `MAP-C0<n>`.
8. `CONTEXT.md`, every defined term, as `CTX-<term>` in document order. These
   are kept only when the specification depends on the term; a term that exists
   to explain the code records `refused` with `note` naming the internal
   vocabulary it describes.

The map's `## Non-goals` need no rows: they are refusals that the specification
already carries, and each becomes the `refused` disposition of the requirement
it belongs to.

## How to fill each row

- `title`: one line, the requirement in the source's own words where they are
  already short, otherwise a faithful compression. Never a paraphrase that
  widens or narrows the meaning.
- `source`: `initial_spec.md §4.12`, `map.md ## Decisions so far #14`,
  `CONTEXT.md . Leave time`.
- `status`: `kept`, `superseded`, `duplicate`, `refused`, or `deferred`.
- `target`: for `superseded`, the identifier that replaces it. For `duplicate`,
  the canonical identifier. Empty otherwise.
- `effort`: for `kept` rows only, the child effort slug that owes it, chosen
  from the settled twelve. A row that no effort owes is a row that is not
  `kept`.
- `spec`: leave empty. `to-spec` fills it when it writes the specification.
- `note`: the reason for `refused` and `deferred`, and anything a reader needs to
  follow `superseded` and `duplicate`. Empty otherwise.

A `deferred` row names `v2` in `note` and nothing else in the row pretends it is
scheduled.

## The self-check this task must pass

Write a small script, `docs/v1/check-requirements.py`, that reads the CSV and
checks the rules that do not need the specification. Run it before marking this
task done, and record its output in this task's notes.

1. No identifier appears twice.
2. Every `superseded` and `duplicate` row names a `target` that exists in the
   file.
3. Every `kept` row names an `effort` that is one of the twelve settled child
   efforts.

Rule 4, which checks the specification, needs the spec and therefore belongs to
`to-spec`. The same script grows that rule, and `to-spec` runs it against the
draft before publishing.

## What counts as done

Every source item has a row, every row carries a disposition, the self-check
passes on the three rules above, and the row count per source matches the count
in the file. A source section whose rows are fewer than its items is the failure
this task exists to prevent, so state both numbers in the notes.

## Downstream

- `to-spec`: walks the directory, writes a `Covers:` line per section, fills the
  `spec` column, runs the coverage check, and publishes only when it passes.
- `to-tickets`: writes the architecture document and creates each child effort
  with its `## Requirements` section taken from the `effort` column.
- Every child effort: checks its own list at its close.

## Notes

### Ordering note: this task runs before `to-spec`

The dependency cannot be wired as an edge, because this effort's remaining
planning graph is finished and edges are fixed at creation. Recorded here
instead, following the convention already used on this map.

This task is the last planning task. It must be done before the map is
finalized, because `to-spec` reads its output and cannot publish without it.
After it is done, the next pass reconciles the map, and the pass after that runs
`to-spec`.

Its write-back is the map's `## Requirements traceability` section, which is
already written and already names this task, so the done-marking is satisfied.
Its own record is the notes above.

The file it produces, `docs/v1/requirements-directory.csv`, and the script
beside it, `docs/v1/check-requirements.py`, are the two artefacts every later
stage depends on. Neither lives under `docs/tasks/`, and neither archives with
this effort, which is deliberate.

### Built: the requirements directory and its checker

Two artefacts, both outside `docs/tasks/` so the write guard allows them and so
they outlive this effort's archive:

- `docs/v1/requirements-directory.csv`, 310 rows and one fixed header.
- `docs/v1/check-requirements.py`, the checker, which enforces the rules that
  do not need the specification and grows rule 4 when one exists.

**Rows per source, and the item count it was checked against.** Every source
section produced exactly as many rows as it holds items, so no section was
walked partially:

| Source | Item | Rows |
|---|---|---|
| `initial_spec.md` §2.1 | capability | 25 |
| `initial_spec.md` §4 | journey | 28 |
| `initial_spec.md` §5 | rule | 104 |
| `initial_spec.md` §8.8 | completion signal | 16 |
| `initial_spec.md` §9 | unresolved decision | 16 |
| `map.md` `## Decisions so far` | decision | 69 |
| `map.md` `## Constraints` | constraint | 14 |
| `CONTEXT.md` | glossary term | 38 |

**Dispositions.** 252 kept, 44 superseded, 8 deferred, 6 refused. Every
`superseded` row points at the map decision that replaces it, which is what
makes the 44 old rules traceable rather than merely absent. The 8 deferred rows
are the five tracker-plugin rules and the two tracker journeys, all v2 advisory,
plus the post-v1 date-gating and recurrence decision. The 6 refused rows are the
glossary terms that describe the code rather than the product: core, surface,
surface port, infrastructure port, adapter, and remote proxy. They are recorded
as architecture vocabulary, and the specification must not use them.

**The interpretation that makes the effort column single-valued.** `effort`
names the first effort that must satisfy a row, not every surface that later
shows it. That is why the desktop views carry no rows of their own: the command
line delivers browse, inspect, and review first, and the window reuses the same
capabilities under the additive-version rule. Rows owned by `planner-v1` are the
ones this effort discharges itself, meaning process rules, records it writes,
and decisions that constrain the child efforts without being product
capabilities. The checker treats that value as a named owner and exempts those
rows from rule 4, because a process rule has no specification section.

**Self-check output, rules 1 to 3.**

```text
rule 4 skipped: no specification at docs/tasks/planner-v1/spec.md
310 rows: deferred 8, kept 252, refused 6, superseded 44
kept by effort: cli-basics 22, cli-forecast 48, cli-planning 56, cli-review 11,
cli-work-model 31, client-shell 6, daemon-api 19, mcp-agent-interface 14,
planner-v1 20, scaffolding 10, tray-surface 6, work-store 9

all rules pass
```

**What rule 4 will do when `to-spec` runs.** It reads a `Covers:` line under
every specification heading, and fails when a kept row owed by a child effort is
covered by no section, covered twice, or when a section cites an identifier that
does not exist or is not kept. That is the gate that stops the spec from
publishing with a requirement missing or a section nobody asked for.

**What this task did not do.** It did not write the specification, decide any
requirement, or assign a release. It recorded what each source item became and
who owes it.

**Write-back.** The map's `## Requirements traceability` section now carries the
artefact names, the counts, and the checker's rules, and names this task.
