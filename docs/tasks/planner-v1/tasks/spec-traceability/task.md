---
type: task
subtype: grilling
title: Settle how the specification is proven complete, consistent, and traceable to the child efforts
status: stable
workflow_state: done
blocked_by: []
---

## Decisions to settle

Three decisions, each with its own mechanism, because they fail differently.

1. **Completeness.** How do we prove that the specification misses nothing the
   sources settled?
2. **Consistency.** How do we find contradictions between settled decisions
   before they reach the specification, and when does that check run?
3. **Fidelity.** How does a child effort prove it implemented the requirements
   it was given, given that this effort cannot verify implementations it does
   not build?

## The problem, measured

The decisions live in six places that are authoritative for different things:

- `docs/tasks/planner-v1/initial_spec.md`, 2,110 lines: 28 user journeys, 104
  distinct rule identifiers, and 16 unresolved decisions. It is the reference
  text, and the map supersedes it where they conflict.
- The map's `## Decisions so far`, 69 bullets, and its `## Constraints`: the
  settled decisions, each naming the task that decided it.
- `docs/tasks/planner-v1/tasks/`, 25 planning tasks holding 3,608 lines of notes
  that carry the rationale, the rejected alternatives, and the exact wording
  that did not fit a bullet.
- `docs/research/`, 8 findings files: the evidence behind the technical
  decisions.
- `CONTEXT.md`: the vocabulary every document must use.
- `docs/tasks/planner-v1/architecture.md`, still to be written by to-tickets.

Nothing currently maps one place onto another. A requirement can be settled in
a task note, restated on the map, and dropped from the specification, and no
artifact would notice. A contradiction between two decisions that touch the same
concept has nowhere to surface. And the child efforts are created at the close
with `origin_effort`, which records where they came from but not what they owe.

## Parent decisions

The close route is settled: to-spec, then to-tickets for the architecture
document and one formality ticket, then finalize on branch
`effort/planner-v1`. This task may amend that route. It does not reopen any
product or architecture decision, and it does not decide what the specification
says, only how its completeness, consistency, and fidelity are demonstrated.

## Known choices

**Completeness.**

- A requirement inventory: every numbered source item as one row with a stable
  identifier, its disposition, the specification section it lands in, and the
  child effort that implements it.
- A section-by-section walkthrough: a human reads each source section against
  the specification draft and ticks it.
- Text matching between the sources and the specification. Cheap, and it proves
  only that words recur.

**Consistency.**

- A contradiction pass over fixed axes, meaning the shared concepts that several
  decisions touch: the estimate and the remaining figure, the deadline and the
  effective date, the notice, the session and its leave and stop times, capacity
  and target and balance, the identifier, the run mode and the authority, and
  the change cursor.
- A vocabulary check against `CONTEXT.md`, refusing every synonym the glossary
  forbids.
- An adversarial read by an agent that wrote none of the documents, asked to
  report only contradictions and gaps, in a fixed output shape.
- Deriving each specification section from named decision identifiers, so two
  sections citing incompatible decisions become visible.

**Fidelity.**

- Each child effort's map carries a requirements section listing the
  specification identifiers it must satisfy, and its close checks them row by
  row.
- The one formality ticket stays a formality, and fidelity is checked per child
  at that child's own close, not here.
- A requirement identifier is quoted in the child's implementation tickets, so a
  ticket that implements nothing traceable is visible in the ticket list.

## Recommended starting answer

Do all three, but make the first two artifacts and the third a rule, because
only the first two can be checked from here.

**1. Build the inventory before writing the specification, and write the
specification by walking it.** One file,
`docs/tasks/planner-v1/traceability.md`, one row per source item: every journey
in section 4, every rule identifier in section 5, every capability in section
2.1, every completion signal in 8.8, every entry in section 9, and every map
decision bullet. Identifiers are derived mechanically so nobody invents them:
`SRC-4.12`, `SRC-RULE-5.10`, `SRC-2.1.4`, `SRC-8.8.9`, `DEC-13` for the
specification's own, and `MAP-014` for the map's bullets in document order. Each
row ends in exactly one disposition: kept, superseded by a named decision,
refused with the reason, or deferred to v2. A row with no disposition is the
failure mode this effort is trying to make impossible.

The inventory is then the specification's table of contents. `to-spec` walks it
in order, and a section that no row calls for is a section the specification
does not need. This is the one mechanism that makes "the specification missed
nothing" a checkable claim rather than a hope.

**2. Run the consistency check on axes, plus one adversarial reader.** The axis
list is finite and written down, so the check cannot quietly skip a concept.
For each axis, gather every decision and every specification rule that mentions
it and read them together, looking for one of four failures: two rules that
cannot both hold, a rule stated twice with different wording, a rule whose
vocabulary breaks `CONTEXT.md`, and a rule that silently answers one of the 16
unresolved decisions differently from the decision task. Then hand the whole set
to one agent that wrote none of it, asked for contradictions and gaps only. An
author cannot see their own contradictions, and a second reader costs one run.

**3. Make fidelity a rule the child efforts inherit, plus one record at this
close.** Every child map carries a `## Requirements` section quoting the
identifier ranges it must satisfy, and the close writes that section into each
child's map when it creates it, so no child starts without its list. Each
child's own finalize review then checks its list row by row. This effort cannot
verify code it does not build, so it verifies the one thing it can: that every
requirement was handed to a child that owes it.

Every clause here is cheap and none of it needs a new tool: a table, an axis
list, a section in a child map, and one delegated read.

## Downstream work

- `to-spec`, whose section order now comes from the inventory.
- `to-tickets`, which writes the architecture document and the one formality
  ticket, and which carries the three mechanisms into the architecture.
- The close, which creates each child effort with its `## Requirements` section.
- Every child effort, whose finalize review checks its own list.
- The map's close route, which this task may amend.

## Notes

### Settled: a CSV directory, five dispositions, two checks

**The directory.** `docs/v1/requirements-directory.csv`, outside `docs/tasks/`
so that the write guard allows it and so that it survives this effort's
archive. Plain CSV with one fixed header row and one row per source item:

`id,title,source,status,target,effort,spec,note`

Identifiers are derived mechanically and never invented: `SRC-4.12` for a
journey, `SRC-RULE-5.10` for a rule, `SRC-2.1.4` for a capability in section
2.1, `SRC-8.8.9` for a completion signal, `DEC-13` for one of the
specification's unresolved decisions, `MAP-014` for a map decision bullet, and
`MAP-C07` for a map constraint. That is roughly 275 rows against the sources as
they stand.

`status` is one of five: `kept`, `superseded`, `duplicate`, `refused`,
`deferred`. `target` carries the replacing or canonical identifier for
`superseded` and `duplicate`. `effort` names the child effort that owes the row.
`spec` names the specification section that satisfies it. `refused` and
`deferred` carry their reason in `note`.

**Why CSV rather than markdown.** A script splits it with no parsing rules of
its own, a spreadsheet opens it for a human review, and a diff shows one row
changed rather than a reflowed table.

**Why outside the effort tree.** The write guard refuses the built-in write
under `docs/tasks/`, which I verified rather than assumed: a probe file was
refused with the lockdown message. Nothing under the effort tree is writable
except through a tool, and no tool writes a derived artefact. A file outside the
tree is also the better home, because the directory must outlive the effort: the
child efforts point at it, and an audit after the archive still needs it.

**The four rules the CSV must satisfy, and a script can check.**

1. No identifier appears twice.
2. Every `superseded` or `duplicate` row names a `target` that exists.
3. Every `kept` row names an `effort` that exists as a child effort, and that
   effort's map lists the identifier in its `## Requirements` section.
4. Every `kept` row names a `spec` section that exists, and every specification
   section's `Covers:` line lists identifiers that exist and are `kept`.

**Who builds it.** A new planning task, `requirements-directory`, running before
the spec. It walks every source, fills the dispositions, and assigns each `kept`
row to a child effort from the settled effort list. It is separate from
`to-spec` on purpose: the completeness checklist must not be built by the author
of the thing it checks.

**Verification, split at the point where it can actually run.** Rules 1, 2 and
the effort assignment can be checked the moment the CSV exists, and the building
task is not done until its own self-check passes them. Rules 3 and 4 need the
specification, so they belong to `to-spec`: it writes the draft with a `Covers:`
line in every section, runs the coverage check against the CSV, and does not
publish until the check passes. That is the gate the earlier version of this
plan was missing.

**The consistency pass.** The same `to-spec` run hands the draft to one agent
that wrote none of the documents, asked only for contradictions and gaps, with
the source set as its input. Each finding is either fixed or accepted in
writing before publication. The axis list the reader is pointed at is finite and
written down: the estimate and remaining, the deadline and effective date, the
notice, the session with its leave and stop times, capacity and target and
balance, the identifier, the run mode and the authority, and the change cursor.
A finite list is what keeps a shared concept from being quietly skipped.

**Fidelity to the child efforts.** Each child effort's map carries a
`## Requirements` section listing the identifier ranges it owes, written at
creation from the CSV's `effort` column, so no child starts without its list.
Each child's own finalize review checks that list row by row. This effort
verifies only what it can: that every requirement was handed to a child that
owes it.

**Alternatives rejected.**

- The specification carrying the inventory itself. Rejected because the same
  author would build the checklist and the answer.
- A map section, or a planning task's notes, as the home. Both are tool-written
  and inside the effort, but the directory has to outlive the archive and has to
  be scriptable by a later auditor.
- Markdown table, YAML records, and pipe-delimited lines inside a fence.
  Rejected for CSV, which a script, a spreadsheet, and a diff all handle.
- A free-text disposition per row. Rejected because a fixed vocabulary is what
  makes "no row was quietly dropped" checkable.
- Two adversarial readers. Rejected as twice the cost for a third judgment
  problem the first reader does not create.
- Identifiers quoted in every implementation ticket. Rejected because the child
  effort decides its own internal granularity; the obligation is stated once, at
  the effort level.

**Downstream.** `requirements-directory` (new planning task), `to-spec` (draft,
coverage check, adversarial read, then publish), `to-tickets` (architecture and
the child efforts, each with its `## Requirements` section), and every child
effort's own close.
