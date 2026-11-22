---
type: task
subtype: grilling
title: Settle the non-goals, the non-negotiable facts, and the success test for forecasts
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

> Forecasts with effective deadline, projected finish, slack, and risk; the ten
> notice types; operating-system delivery through `ForNotifying`; and the
> confirmed override with its two-step handshake.

### Destination

The risk layer above the plan: what will not fit, said early enough to act on,
and the one confirmation mechanism every later surface reuses.

### Actor

The person who needs to renegotiate a deadline before it passes, and the agent
that must ask a human before a dangerous change.

### Classification

Feature.

### Stories

1. As a person with a deadline, I want a projected finish and a slack figure, so
   that I know whether it fits before it is too late.
2. As a person told something is at risk, I want a notice that names the
   deliverable and the reason, so that I know which lever to pull.
3. As a person who dislikes interruptions, I want per-type levels and OS
   delivery, quiet hours, and one delivery per condition, so that I am told
   things when I am willing to listen.
4. As a person overriding a rule deliberately, I want the override recorded and
   confirmed with a code, so that it cannot happen by accident.
5. As an agent, I want a call that needs approval to return a pending operation
   instead of acting, so that the human confirms out of band.

### Constraints

- Ten notice types with the shipped levels and delivery defaults; `critical`
  retires, and errors are handled before they become notices.
- Notice lifetimes are persistent or ephemeral and are not configurable;
  derived notices are recomputed, one-off notices are stored, dismissals are
  stored and not remembered.
- Slack is measured against day capacities that include the month's
  reconciliation and exclude vacation and zero-length flex days.
- Missed, unscheduled, and blocked are three different things and must not run
  together.
- Two rules stay overridable: dependency order and system order. The capacity
  override is gone because the leave time moves instead.
- The confirmation handshake is the only mechanism: a pending operation, a
  two-digit code delivered through the channel ladder, three failed attempts.
  Later surfaces reuse it and must not reimplement it.
- OS delivery goes through `ForNotifying`, because in split mode the daemon
  raises it.
- No review, no export, and no settings here.

### Also settle, because intake could not write it

This map must gain a `## Requirements` section holding the ranges below and the
rule that every row is checked one by one before this effort may close. The rows
carry their source file and line in `docs/v1/requirements-directory.csv`.

    SRC-2.1.14, SRC-2.1.15, SRC-4.10, SRC-4.11, SRC-4.26, SRC-4.28,
    SRC-RULE-EFFORT-2, SRC-RULE-SLACK-1..SRC-RULE-RISK-4,
    SRC-RULE-FCAST-1..SRC-RULE-FCAST-6, SRC-RULE-WARN-2..SRC-RULE-NOTIF-4,
    SRC-RULE-PRED-1..SRC-RULE-OVERRIDE-6, SRC-8.8.5, SRC-8.8.8, SRC-8.8.10,
    SRC-8.8.11, T11-014, T13-042, T13-045..T13-049, T13-055, T14-008,
    T15-006..T15-011, T15-016..T15-033, T15-038..T15-041, T002, T003, T004,
    T013, T016, T024, T025, T026, T029, T030, T20-022, T25-007, T25-011,
    T25-031..T25-033, MAP-047, MAP-049, MAP-050, MAP-053, CTX-notice,
    CTX-forecast..CTX-risk

That is 94 rows.

### Position and release, already settled

Sixth in the build order, blocked by `05-cli-planning`, release 0.4 Risk. Its
successor is `07-cli-review`. The full order is the table under `## Effort order`
in the parent's architecture document.
