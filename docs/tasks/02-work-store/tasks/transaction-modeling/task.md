---
type: task
subtype: research
title: How Java ports model atomic multi-record writes and transaction boundaries
status: stable
workflow_state: done
blocked_by: []
---

What are the established ways a Java repository-style port models an atomic
change that spans several records, and which one fits a port whose rule is one
call is one transaction?

The operator asked for this task directly, while answering the
`store-contract` grilling: when one user action changes two records, the
question of whether the port takes a change set or forces two calls is a
transaction question, and it should be answered from what Java actually does
rather than from a preference. `store-contract` cannot close its item 5 until
this lands.

### Sub-questions, each with a primary-source citation

1. Which shapes do established Java data layers expose for one atomic multi-
   record write? Cover at least: Spring Data repositories (save/saveAll and
   `@Transactional` proxying), JPA `EntityManager` and its transaction require-
   ments, jOOQ's `DSLContext.transaction` and `trx` handle style, Jdbi's
   `withHandle`/`inTransaction` callback, and plain JDBC `Connection` with
   auto-commit off. State the calling convention each one imposes: who opens the
   transaction, who closes it, and whether the caller passes a handle or the
   port owns the connection.
2. What does each shape cost when the port must be replaceable by a fake in the
   core's own tests, given the core may hold no framework type? A callback that
   hands the caller a transaction handle leaks a framework-shaped type into the
   port; a unit-of-work object, a change-set parameter, and a lambda taking the
   port itself do not.
3. Is a port method that accepts a list of changes and applies them in one
   transaction still "one call, one transaction", or does it make the port the
   owner of a use case's atomicity? Find how each shape above answers that.
4. What does each cost at the change log, where one row per changed record must
   be written in the same transaction, and where a consumer reads the rows as
   one cursor range?
5. Nested and re-entrant calls: what do the shapes above do when a port call is
   already inside a transaction opened by an outer port call? Same transaction,
   separate transaction, or refusal.

### Trusted sources

Spring Framework and Spring Data reference documentation and source; Jakarta
Persistence specification; jOOQ reference manual and its `TransactionProvider`
source; Jdbi documentation and source; the JDBC specification and the
`java.sql.Connection` javadoc. Anything from a blog is a lead, not evidence.

### Evidence required

A findings note at `docs/research/transaction-modeling.md`, one citation per
material claim, a confidence per item, a recommendation that names which shape
the port adopts, and the shape's cost at the fake the core's tests use.
Unresolved points are named, not smoothed over.

### Likely dependents

`store-contract` item 5 (compound changes), the specification's port section,
and every later effort whose use case changes two records at once.

## Notes

All five sub-questions answered with primary-source citations and a confidence
per item. The findings are at `docs/research/transaction-modeling.md`.

The recommendation the port adopts: the adapter implements one write method
taking an ordered change set, and each per-kind operation is a default method
that builds a one-element change set and delegates to it. The port therefore
keeps the per-kind operations `store-contract` chose, while one adapter method
owns the transaction, the change-log rows, and the end of the deferred integrity
check.

What the evidence rules out, and why:
- A callback that hands the caller a framework handle (jOOQ, Jdbi,
  `TransactionTemplate`, JPA `runInTransaction`) puts a framework type in
  `contract`, so the core's fake cannot be built without the framework.
- A caller-opened transaction names `java.sql.Connection` in `contract`, which
  the map forbids and which turns the fake into a database.
- Two port calls give two transactions, so a cursor range stops being a set
  boundary and the deferred check has no end to run at.
- A unit-of-work object needs the core to carry a mutable object the fake must
  model; a lambda taking the port itself gives the port a re-entrancy rule.
- Nested calls join the outer transaction in every shape checked, with savepoints
  the only partial rollback, and JDBC has no nested begin.

Left for the specification to pin, not decided here: the concrete change records
one per operation per kind, the rule for a change set that names one record
twice, the statement that no other port method opens a transaction, and whether
the deferred integrity check can be asked for explicitly.

The cost at the fake is stated in the note: one method, a staged copy of the
record map and the log, and a reference swap to publish, with no framework type
involved.
