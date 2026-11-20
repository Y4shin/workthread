# Catalogue build record

What the rebuild produced, what the reviewers found, and what I did with each
finding. The catalogue itself is `docs/v1/requirements-directory.csv`; the
frozen extraction is `docs/v1/requirements-catalog.csv`, committed as snapshot
`a0ee2a8`.

## The run

Thirty extraction children, four at a time, one per source in chronological
order: the specification at order 0, the twenty-seven planning tasks at orders 1
to 27, the map at 28, the glossary at 29. They produced 1159 rows and made no
judgements. One child concatenated them, wrote `manifest.md`, and committed the
snapshot. Seven disposition children then took slices of the frozen catalogue
(`dispositions/d1.csv` to `d7.csv`), four at a time, and one child applied them
in slice order, later slice winning.

The result: 977 kept, 82 superseded, 61 duplicate, 25 deferred, 14 refused.

## The one supervisor decision

The specification slice found a contradiction in its own brief: it was told to
read the whole catalogue and to look backwards only, and for order 0 there is
nothing behind it. I allowed forward citations when the later row explicitly
names or plainly restates the subject, which is why 51 specification rows are
superseded by decisions from orders 13 to 28. I also kept the sixteen section
8.8 completion signals as kept rows with an effort, rather than folding them
into duplicates, because each one is a behaviour someone must demonstrate end
to end.

## Verification findings and what happened to each

Four read-only reviewers ran: file integrity, replacement chronology, source
fidelity on a 73-row sample, and coverage. All four reported zero defects
against their own criteria and raised four notes. Triage:

- **Fixed.** The coverage reviewer showed that `check-requirements.py` compared
  parts to the directory by identifier and count only, so a substitution that
  kept the count would pass. Rule 6 now rebuilds the expected extraction rows
  from the parts and compares `id`, `order`, `source_file`, `line`, `section`
  and `title` row for row, and checks the frozen catalogue the same way.
- **Fixed.** Three rows quoting the first directory's counts (`T27-030`,
  `T27-031`, `T27-032`) are annotated as historical, because the rebuild
  replaced those counts. `T26-002` records the seven-column header the first
  directory settled; it is annotated with the four columns the shipped header
  added, since the requirement survives and only the column list grew.
- **Accepted.** Citations may land on a wrapped continuation line inside the
  statement's own paragraph. The fidelity reviewer sampled 73 rows across all
  thirty orders and found no title that misstates its source. Every cited line
  resolves to non-blank text, which the checker enforces.
- **Accepted.** `T12-024` is a duplicate whose canonical row `T12-017` is
  refused. That is correct: the row summarises refusals, so the best statement
  of the subject is the refusal itself.
- **Accepted.** The CSV uses CRLF line endings, which RFC 4180 permits and the
  CSV reader handles.

One reviewer claim did not survive checking: it reported `T10-001` citing a
blank line. Line 51 of that file is the heading the row quotes, and no row in
the catalogue cites a blank line.

## What the dispositions mean, and what they do not

`planner-v1` owns 290 kept rows. Those are the record's non-capability
statements: process rules, evidence that a later task consumed, and records
this effort writes. They are exempt from specification coverage, and a
specification writer must not copy them into the product description. The
distribution is concentrated exactly where the process lives: the
non-negotiables task (33), the specification additions (29), the Taskwarrior
model research (50), the child-effort scoping (30), and the two traceability
tasks (46).

The catalogue records what the record says and who owes it. It does not prove
that a later row contradicts an earlier kept row; that is a semantic judgement,
and the guard is the fidelity sample plus the specification's adversarial read.

## A citation decayed, and the checker caught half of it

`tw_finalize_map` added one line to the map's frontmatter, which moved every
line number in the map down by one. All eighty-three map rows then cited the
wrong line. The checker noticed only the two that landed on a blank line, so the
other eighty-one were silently off by one until they were found. Every map row
was re-anchored and the parts, the catalogue and the directory were corrected
together.

The lesson is that a line citation is only true while its source stands still.
The sources were frozen before extraction for exactly this reason, and the one
file the workflow tools still write is the one that moved.
