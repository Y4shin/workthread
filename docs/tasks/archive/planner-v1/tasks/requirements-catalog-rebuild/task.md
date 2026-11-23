---
type: task
subtype: research
title: Rebuild the requirements catalogue as a sourced, chronologically ordered corpus
status: deprecated
workflow_state: done
blocked_by: []
---

The first directory was built in one pass by one agent, which made every
judgement at once: what counts as a requirement, where it came from, and what
became of it. That hides the two questions the directory exists to answer. Did
we read everything? And when two statements disagree, which one won?

This task rebuilds it as a corpus with separated stages, so each question is
answered by a different agent that can be checked on its own.

**The shape.** One chain of children, in four stages.

1. **Extraction, one child per source.** Thirty sources in chronological order:
   the original specification at order 0, then the twenty-seven planning tasks
   in the order they were worked at orders 1 to 27, then the effort map at 28
   and the glossary at 29. Each child reads only its own file and writes
   `docs/v1/catalog/parts/<order>-<slug>.csv`, one row per settled statement,
   with the file and the line it came from. No child judges: nothing is marked
   superseded, duplicate, deferred, or out of scope, and no effort is assigned.
   Four children run at a time.
2. **Snapshot.** One child concatenates the parts in order into
   `docs/v1/requirements-catalog.csv`, writes a manifest of the units, and
   commits both on `main`. After that commit the corpus is frozen evidence.
3. **Disposition, one child per slice.** Seven children take contiguous slices
   of the catalogue and decide, for every row they own, whether it is kept,
   superseded, duplicated, refused, or deferred, and which effort owes it. They
   look backwards only: earlier rows for duplicates, and their own slice for
   whatever replaces a row. A later slice may flip a row of an earlier slice,
   and the later verdict wins. Each writes a delta under
   `docs/v1/catalog/dispositions/`. One child then applies the deltas in slice
   order to produce `docs/v1/requirements-directory.csv`.
4. **Verification.** Four read-only reviewers, each with one question:
   integrity of the file, whether every replacement points forward in time and
   no chain cycles, whether the titles match their cited source lines, and
   whether the concatenation dropped or renumbered anything.

**Why the order matters.** A requirement can only be stale because something
later replaced it. Ordering the corpus by when it was written turns an
unbounded comparison into a search that only moves backwards, which is both
cheaper and the honest model of how the decisions were actually made.

**Acceptance.** Every source contributed a part file; the catalogue holds every
part row exactly once with its line intact; every row carries a disposition;
every `kept` row names an effort; every replacement points at a row that exists
at the same or a later order; every verification finding is either fixed or
recorded as accepted. `docs/v1/check-requirements.py` passes on the rebuilt
directory.

**Not in scope.** Writing the specification, deciding any requirement, or
assigning a release. This task records what the record says and who owes it.

## Notes

### What ran

One chain of 43 children through `docs/v1/catalog/workflow.js`, four at a time
wherever the stage allowed it.

- **Extraction, 30 children.** One per source, in chronological order: the
  specification at order 0, the 27 planning tasks at orders 1 to 27, the map at
  28, the glossary at 29. Each wrote
  `docs/v1/catalog/parts/<order>-<slug>.csv`, one row per settled statement,
  with the source file and the line it starts on. No child judged anything.
- **Snapshot, one child.** Concatenated the parts into
  `docs/v1/requirements-catalog.csv`, wrote `manifest.md`, and committed both as
  `a0ee2a8`.
- **Disposition, seven children then one merge.** Slices d1 (the specification
  alone) through d7, each writing `dispositions/<slice>.csv`, with the merge
  applying them in slice order so the later verdict wins.
- **Verification, four reviewers.** File integrity, replacement chronology,
  source fidelity over a 73-row sample, coverage against the parts.

### Rows per source

| Order | Source | Rows |
|---|---|---|
| 0 | `initial_spec.md` | 189 |
| 1 to 27 | the 27 planning tasks | 807 |
| 28 | `map.md` | 83 |
| 29 | `CONTEXT.md` | 38 |
| | **total** | **1159** |

The specification part came in at exactly the predicted 189: 25 capabilities,
28 journeys, 104 rules, 16 completion signals, and 16 decisions. The task parts
are much larger than the map's summaries of them, which is the reason for
rebuilding: the map records 69 decisions, the tasks behind them hold 807.

### The one decision the chain could not make itself

The specification slice found that its brief contradicted itself: it was told to
read the whole catalogue and to look backwards only, which for order 0 leaves
nothing behind it. I allowed forward citations when the later row explicitly
names or plainly restates the subject, and I kept the sixteen section 8.8
completion signals as kept rows with an effort rather than folding them into
duplicates, because each is a behaviour someone must demonstrate end to end.
That decision is why 51 specification rows are superseded by decisions from
orders 13 to 28.

### Dispositions

977 kept, 82 superseded, 61 duplicate, 25 deferred, 14 refused. The 290
`planner-v1` rows are the record's non-capability statements: process rules,
evidence a later task consumed, and records this effort writes. They cluster
where the process lives: the non-negotiables task (33), the specification
additions (29), the Taskwarrior model research (50), the child-effort scoping
(30), and the two traceability tasks (46).

### Verification and triage

All four reviews reported zero defects against their criteria and raised four
notes. Two were real and are fixed: the checker compared parts to the directory
by identifier and count only, so rule 6 now compares the extraction columns row
for row against both the directory and the frozen catalogue; and four rows that
quote a replaced artifact now carry a note saying so. Two are accepted: a
citation may land on a wrapped continuation line inside the statement's own
paragraph, and `T12-024` duplicates a row that is itself refused, which is
correct because it summarises refusals.

One reviewer claim did not survive checking: it reported `T10-001` citing a
blank line, but line 51 of that file is the heading the row quotes, and no row
in the catalogue cites a blank line.

The full record is `docs/v1/catalog/triage.md`.

### What the catalogue does not prove

It records what the planning record says and who owes it. It does not prove that
a later row contradicts an earlier kept row, because that is a semantic
judgement, and the guard for it is the fidelity sample plus the specification's
adversarial read.

### Write-back

The map's `## Requirements traceability` section now describes the rebuilt
catalogue, its chronology, its dispositions, what an owner means, the six
checker rules that run today, and the seventh that gates `to-spec`.
