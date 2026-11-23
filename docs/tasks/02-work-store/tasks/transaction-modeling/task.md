---
type: task
subtype: research
title: How Java ports model atomic multi-record writes and transaction boundaries
status: stable
workflow_state: ready
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
