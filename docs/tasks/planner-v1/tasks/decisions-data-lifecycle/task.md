---
type: task
subtype: grilling
title: Settle deletion, exception removal, midnight intervals, Review history, and persistence
status: stable
workflow_state: ready
blocked_by:
- spec-additions
- stack-research
---

## Decision to settle

Settle what can be deleted, how storage behaves, and what history is kept.

- **DEC-10.** Can tasks and projects be permanently deleted, or only cancelled
  and archived?
- **DEC-11.** How are availability exceptions removed: an explicit delete, a
  toggle, or overwriting with an empty value?
- **DEC-12.** Can a weekly working interval cross local midnight? v1 currently
  interprets each interval within one local day.
- **DEC-14.** How does the Review view obtain planned time for past days and
  weeks: a stored projection snapshot, or a recomputation from the current
  rules?
- The persistence choice the effort must settle as a non-negotiable fact:
  schema migration strategy, file location and visibility, export format, and
  how only one service owner writes at a time.

## Parent decisions

The non-negotiables task settled that data stays local and that one service
owns it. `stack-research` supplies the persistence candidate evidence and its
locking behavior.

## Known choices

- DEC-10 alternatives: no permanent deletion (cancel a task, archive a
  project), permanent deletion with confirmation, or deletion only after a
  retention window.
- DEC-11 alternatives: an explicit remove action, a toggle that reverts to the
  weekly rule, or replacing the exception with an empty interval list.
- DEC-12 alternatives: forbid crossing midnight, split the interval into two
  local-day intervals, or interpret an interval by its start day.
- DEC-14 alternatives: recompute past projections from current data, store a
  daily projection snapshot, or store only allocations that actually happened.
- Persistence candidates: an embedded relational database with a migration
  tool, or versioned local files, in the visible data directory the
  specification requires.

## Recommended starting answer

DEC-10: no permanent deletion. Cancel a task, archive a project, keep the
history, and state why in the interface.

DEC-11: an explicit remove action that reverts the date to the weekly rule.

DEC-12: forbid crossing midnight, with a validation message that tells the user
to create two intervals. It keeps capacity computation per local day and keeps
daylight-saving behavior explainable.

DEC-14: recompute past projections from current data and label them as a
projection, because the specification already treats the itinerary as derived,
not stored. Store only what actually happened: time entries and completion
timestamps.

Persistence: the research task's recommendation, provided it supports one
writer, a visible data directory, atomic upgrades, and an export that does not
depend on the storage engine.

## Downstream work

Fixes the data model's lifecycle rules, the migration path, the export format,
and the Review view's data source. Feeds `child-efforts`, the scaffolding
child, and any child that owns import and export.
