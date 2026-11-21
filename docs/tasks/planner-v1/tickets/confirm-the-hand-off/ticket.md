---
type: ticket
subtype: feature
title: Confirm the planner v1 hand-off
status: stable
workflow_state: ready
blocked_by: []
size: s
mode: human
---

## What to build

Nothing implementable. This ticket is the effort's closing check, not a unit of
work. The effort's product is a settled plan, and its implementation lives in
twelve child efforts, so the only thing left to confirm here is that the hand-off
is real and complete.

It carries no implementation on purpose, and it is marked `mode: human` so the
autonomous chain never runs for it. Closing it is a statement by a person that
the children exist and that the record behind them is trustworthy.

## Acceptance criteria

- [ ] Twelve child efforts exist under `docs/tasks/`, one for each contract in
      `docs/tasks/planner-v1/architecture.md`, in the same dependency order:
      scaffolding, work-store, cli-basics, cli-work-model, cli-planning,
      cli-forecast, cli-review, mcp-agent-interface, daemon-api, client-shell,
      client-views, tray-surface.
- [ ] Each child's map carries `origin_effort: planner-v1`, its scope, what it
      does not cover, what it depends on, its entry criteria, what it hands
      forward, and its release from the map's `## Releases` section.
- [ ] Each child's map carries a `## Requirements` section holding the exact
      requirement ranges from its contract, and the rule that every row is
      checked one by one as that effort's definition of done. An effort may not
      close while a row in its ranges is unmet and unrecorded.
- [ ] `docs/tasks/planner-v1/architecture.md` is stable, and
      `docs/tasks/planner-v1/spec.md` carries the note pointing at it.
- [ ] `python3 docs/v1/check-requirements.py` passes: every kept requirement
      owed by a child effort is covered by exactly one specification section.
- [ ] No child effort was created as a ticket, and no dependency edge between
      children was recorded as `blocked_by`; the children are linked by
      `origin_effort` and by their stated dependency order.

## Blocked by

None (can start immediately). It waits on the child efforts existing, not on
another ticket.
