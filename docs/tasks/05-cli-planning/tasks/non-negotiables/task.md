---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for planning
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

> The timetable of net minutes per weekday, the full session with leave time and
> break reserve, the month's target and balance with the reconciliation rule, and
> the ordered today list with per-item reasons and the fit signal.

### Destination

The primary v1 output: an ordered day that the user never sorts by hand, drawn
against a real capacity, with the cut line at the leave time. This is the effort
that makes the product the product.

### Actor

The person opening the tool in the morning.

### Classification

Feature.

### Stories

1. As a person with a fixed working week, I want to record net minutes per
   weekday, so that capacity is a number rather than a feeling.
2. As a person starting the day, I want a session with a leave time and a break
   reserve, so that today's capacity is real.
3. As a person whose plans change, I want to move my leave time at any point in
   the day, so that a late start is one statement.
4. As a person opening the tool, I want an ordered list of what to work on now,
   so that I never sort by hand.
5. As a person reading that list, I want a reason per item and a cut line, so
   that I can see why, and what no longer fits.
6. As a person reviewing a month, I want the hours I was there against the hours
   I planned, with a catch-up that never touches a day that has its own minutes.

### Constraints

- No clock-time blocks, no multi-day allocation, and no task split across days.
  The ordered list with a cut line replaces the itinerary.
- The day's planned span is start plus minutes plus reserve; capacity is what
  remains of it from now to the leave time, net of the reserve. Presence is
  measured from the session's start to its stop time, not from the plan.
- The order is never recomputed on the clock; an overrun grows the running claim
  and pushes later items past the cut line.
- The planner is an infrastructure port taking data, an instant, and a session,
  never a store, so a later engine replaces it without the core learning about
  plugins.
- Fourteen days allocate concretely; projections past that repeat the weekday
  pattern and follow the month's balance, marked rough.
- Determinism is a requirement: identical inputs and inputs in a different order
  produce the identical list.
- No notices, no forecasts, and no overrides here; those belong to the next
  effort.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.5, SRC-2.1.13, SRC-4.9, SRC-4.14,
    SRC-RULE-ELIG-1..SRC-RULE-ELIG-3, SRC-RULE-EFFORT-3, SRC-RULE-EFFORT-4,
    SRC-RULE-CAP-5..SRC-RULE-CLOS-5, SRC-RULE-ORDER-1..SRC-RULE-ALLOC-6,
    SRC-RULE-REASON-1..SRC-RULE-REASON-3, SRC-RULE-RECALC-1..SRC-RULE-RECALC-3,
    SRC-RULE-DET-1, SRC-RULE-DET-2, SRC-8.8.2, SRC-8.8.7, SRC-8.8.9,
    T02-008..T02-011, T02-013, T02-015, T08-002..T08-007, T08-009..T08-014,
    T11-024, T11-027, T11-033, T11-034, T13-001..T13-030, T13-038..T13-041,
    T13-053, T13-054, T13-056, T13-065, T14-006, T14-007, T14-019,
    T15-012..T15-015, T17-003, T17-004, T17-013, T17-039, T24-035, T25-005,
    T25-008, MAP-010, MAP-020, MAP-035..MAP-040, MAP-044, MAP-048,
    CTX-timetable..CTX-balance, CTX-vacation..CTX-reconciliation,
    CTX-completion-slot

That is 129 rows, the largest set in the effort.

### Position and release, already settled

Fifth in the build order, blocked by `04-cli-work-model`, release 0.3 The day.
Its successor is `06-cli-forecast`. The full order is the table under
`## Effort order` in the parent's architecture document.
