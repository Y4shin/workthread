---
type: findings
title: "How Java data layers model one atomic multi-record write, and the shape ForStoringWork adopts"
status: stable
source_task: transaction-modeling
---

# Transaction modeling: one atomic multi-record write, in five Java data layers

## Summary

Every established Java data layer offers the same three calling conventions for
one atomic multi-record write, and they differ only in who owns the connection:
a repository method that owns it and takes a value (Spring Data), a
callback that owns it and hands the caller a framework handle (Spring
`TransactionTemplate`, Jakarta Persistence `runInTransaction`, jOOQ, Jdbi), or a
caller-opened transaction over a long-lived handle (JPA `EntityTransaction`,
plain JDBC). Nested calls join the outer physical transaction everywhere, with
savepoints as the only way to get a nested rollback, and JDBC has no nested
begin at all.

A port whose rule is one call is one transaction and whose core may hold no
framework type fits the first convention: a **change set parameter on one port
method**, with the per-kind operations as default methods that delegate to it.
The callback shapes cost a framework type at the fake; the caller-opened shapes
cost a connection or an entity manager at the fake, which turns the fake into a
database.

## What this note is answering

`docs/tasks/02-work-store/tasks/transaction-modeling/task.md` asks five
questions about how a Java repository-style port models an atomic change that
spans several records, and which shape fits a port whose rule is one call is
one transaction. The constraints the answer must respect come from
`docs/tasks/02-work-store/map.md`:

- one port call is one transaction;
- the change log row for a change is written in that same transaction;
- the change log's sequence position is the cursor;
- integrity is checked at the port, and an atomic operation may defer that
  check until the combined execution ends;
- `core` depends on no module but `contract` and holds no framework type, and
  the fake the core's own tests use holds none either;
- one writer thread, and every write transaction opens with `BEGIN IMMEDIATE`.

The driver-level facts behind the last point are not re-researched here. They
are in `docs/research/sqlite-mechanics.md`, cited below as
[sqlite-mechanics] where they matter: `setAutoCommit(false)` executes
`begin immediate;`, `commit()` and `rollback()` each issue their statement and
then open a fresh transaction, and `setAutoCommit(true)` closes that empty one
(section 2). Nothing in this note was executed. Every claim is read from the
source or documentation of the library named beside it.

## Sub-question 1: the shapes, and the calling convention each imposes

| Shape | Who opens | Who closes | What the caller passes | Handle type in the port |
| --- | --- | --- | --- | --- |
| Spring Data repository method | the AOP proxy around the repository | the same proxy | a value (the entities) | none |
| Spring `TransactionTemplate` | the template | the template | a callback | `TransactionStatus` |
| JPA `EntityManagerFactory.runInTransaction` | the factory | the factory | a callback | `EntityManager` |
| JPA resource-local `EntityTransaction` | the caller | the caller | nothing, the caller holds the `EntityManager` | `EntityManager` |
| jOOQ `DSLContext.transaction` | the `DSLContext`, via `TransactionProvider` | the same | a callback | `Configuration` |
| Jdbi `inTransaction` | the `Jdbi` object or the `Handle` | the same | a callback | `Handle` |
| Plain JDBC | the caller | the caller | nothing, the caller owns the `Connection` | `Connection` |

**1.1 Spring Data: the repository method is the boundary, and the caller passes
no handle.** `SimpleJpaRepository` is annotated `@Transactional(readOnly = true)`
at class level, and each write method carries a plain `@Transactional`
([SimpleJpaRepository.java](https://github.com/spring-projects/spring-data-jpa/blob/main/spring-data-jpa/src/main/java/org/springframework/data/jpa/repository/support/SimpleJpaRepository.java)).
`saveAll` is one of those methods, and its body loops over the elements calling
`save(entity)`:

```java
@Override
@Transactional
public <S extends T> List<S> saveAll(Iterable<S> entities) {
    List<S> result = new ArrayList<>();
    for (S entity : entities) {
        result.add(save(entity));
    }
    return result;
}
```

The proxy opens the transaction before the method body runs and commits or rolls
it back after it returns. The caller passes a value and never sees a transaction
object. **Support:** direct evidence (source). **Confidence:** high.

**1.2 Spring: joining is the default, and it is thread-bound.**
`PROPAGATION_REQUIRED` "enforces a physical transaction, either locally for the
current scope if no transaction exists yet or participating in an existing
'outer' transaction defined for a larger scope"
([Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)).
`REQUIRED` is also the propagation a plain `@Transactional` implies, described on
the enum as "Support a current transaction, create a new one if none exists ...
This is the default setting of a transaction annotation"
([Propagation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html)).
Where the transaction lives: "This annotation commonly works with thread-bound
transactions managed by a `PlatformTransactionManager`, exposing a transaction to
all data access operations within the current execution thread"
([Transactional](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html)).
Two consequences matter for a port. The boundary is opened by a proxy, so "only
external method calls coming in through the proxy are intercepted ... self
invocation ... does not lead to an actual transaction at runtime even if the
invoked method is marked with `@Transactional`"
([Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)).
And the "current transaction" is ambient thread state, not a parameter.
**Support:** direct evidence. **Confidence:** high.

**1.3 Spring programmatic: a callback that hands over a framework object.**
`TransactionTemplate.execute(TransactionCallback)` opens and closes the
transaction and passes a `TransactionStatus` into the callback, which the
callback uses to mark a rollback. Spring states the coupling plainly: "using the
`TransactionTemplate` absolutely couples you to Spring's transaction
infrastructure and APIs"
([Programmatic Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)).
**Support:** direct evidence. **Confidence:** high.

**1.4 Jakarta Persistence, two conventions in one API.**
Resource-local: the caller opens and closes. "The `getTransaction()` method of
`EntityManager` returns an instance of the `EntityTransaction` interface", and
`EntityTransaction.begin` "Start a resource transaction", throwing
`IllegalStateException` "if `isActive()` is true"; `commit` "Commit the current
resource transaction, writing any unflushed changes to the database", throwing
`IllegalStateException` "if `isActive()` is false"
([Jakarta Persistence 3.2, 7.5.3](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html),
[EntityTransaction](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityTransaction.html)).
A write outside a transaction is refused, not tolerated: "The `persist`,
`merge`, `remove`, and `refresh` methods must be invoked within a transaction
context when an entity manager with a transaction-scoped persistence context is
used. If there is no transaction context, the `jakarta.persistence.TransactionRequiredException`
is thrown" (same spec, section 3.2).

Since 3.2 the specification also defines a callback convention on the factory,
which is the same shape as `TransactionTemplate`: "The argument function is
executed in the context of a transaction associated with this new
`EntityManager`", and it is "associated with a new transaction" only when there
is no ambient one (section 7.6, same spec). The callback receives an
`EntityManager`. **Support:** direct evidence. **Confidence:** high.

**1.5 jOOQ: the `DSLContext` opens, and the callback gets a derived
`Configuration`.** `DSLContext.transaction(TransactionalRunnable)` takes a
lambda whose parameter is a `Configuration`: "Note how the lambda expression
receives a new, derived configuration which should be used within the local
scope", and "Implicit commit executed here" marks the end of the scope
([Transaction management](https://www.jooq.org/doc/latest/manual/sql-execution/transaction-management/)).
The transaction itself is opened and closed by the configured
`TransactionProvider`, whose `begin`, `commit`, and `rollback` methods take a
`TransactionContext`
([TransactionProvider](https://www.jooq.org/javadoc/latest/org.jooq/org/jooq/TransactionProvider.html)).
The default provider acquires the `Connection` on `begin` and releases it after
the top-level `commit` or `rollback`
([DefaultTransactionProvider.java](https://github.com/jOOQ/jOOQ/blob/main/jOOQ/src/main/java/org/jooq/impl/DefaultTransactionProvider.java)).
**Support:** direct evidence. **Confidence:** high.

**1.6 Jdbi: the callback gets a `Handle`.** "The `inTransaction` and
`useTransaction` methods on the `Jdbi` object create a new `Handle`, open a
transaction and pass it to the callback code. The `inTransaction` and
`useTransaction` methods on `Handle` objects open a transaction on the handle
itself and then pass it to the callback code"
([Jdbi 3 Developer Guide, 11.1](https://jdbi.org/)).
`withHandle` is the same callback without a transaction. Handles from these
methods are "fully managed and will be closed when the callback exits" (same
guide, in the section on Jdbi callbacks). **Support:** direct evidence.
**Confidence:** high.

**1.7 Plain JDBC: there is no transaction object, only the connection state.**
"By default a `Connection` object is in auto-commit mode ... If auto-commit mode
has been disabled, the method `commit` must be called explicitly in order to
commit changes; otherwise, database changes will not be saved", and once disabled
"its SQL statements are grouped into transactions that are terminated by a call
to either the method `commit` or the method `rollback`"
([java.sql.Connection, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html)).
The caller opens (`setAutoCommit(false)`), the caller closes (`commit` or
`rollback`), and nothing hands out a handle because the caller already holds the
only handle there is. **Support:** direct evidence. **Confidence:** high.

**1.8 The same seven in one sentence each.** Spring Data: the port owns the
connection, the caller passes values. Spring template, JPA `runInTransaction`,
jOOQ, Jdbi: the port owns the connection and passes back a framework object.
JPA resource-local and JDBC: the caller owns the connection through a long-lived
handle and the "port" is that handle. **Support:** interpretation of 1.1 to 1.7.
**Confidence:** high.

## Sub-question 2: what each shape costs at the fake in the core's tests

The rule is that the core holds no framework type, so the fake implements the
port without one. Measured against that, the shapes split three ways.

**2.1 The callback-with-handle shapes leak a type into the port signature.**
A port method `R atomic(Function<Handle, R> work)` names the handle type in the
interface, so `contract` depends on the framework, and the fake must construct an
instance of `Handle`, `Configuration`, `TransactionStatus`, or `EntityManager` to
call the core's lambda at all. Spring says so itself about its own programmatic
shape: it "absolutely couples you to Spring's transaction infrastructure and
APIs" ([Programmatic Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)).
For the core's own tests this is not a neutral cost: the fake either needs the
framework on the test classpath or cannot exist. **Support:** direct evidence for
the coupling sentence, interpretation for the fake consequence.
**Confidence:** high.

**2.2 The caller-opened shapes put a transport type in the port, which makes the
fake a database.** A port that hands out `java.sql.Connection` puts no framework
type in `contract`, since `Connection` is a JDK type, but it does put the
adapter's transport into the port. The fake then has to be a JDBC
implementation, so the core's tests need a second database (an in-memory engine,
or a wrapper over the real one). That is worse than a framework type for this
project: `docs/research/sqlite-mechanics.md` section 7 records that an
in-memory database cannot do WAL, cannot be locked, and cannot be opened by a
second process, so a JDBC-backed fake silently tests less than the real store
while looking like the real store. **Support:** interpretation, plus the cited
section 7 facts. **Confidence:** high.

**2.3 The three value shapes hold no framework type, and differ in how much
machinery the fake must copy.**

- **Change-set parameter** (`Result<Applied> applyAll(ChangeSet)`): the fake
  holds a `Map` and a `List`, applies each change to a staged copy, and publishes
  the staged copy plus the log entries in one step. There is no ambient state and
  no lifecycle to model.
- **Unit-of-work object** (`Result<R> withUnitOfWork(UnitOfWork<R>)`): no
  framework type either, provided the object is ours. The core must carry a
  mutable object across calls, and the fake must model that object's state
  machine (open, committed, rolled back, used after close). Strictly more surface
  than a value, for the same atomicity.
- **Lambda taking the port itself** (`Result<R> atomic(Function<ForStoringWork, R> work)`):
  no framework type, and the fake's implementation can be one line,
  `work.apply(this)`. The cost moves elsewhere: the fake must then model a
  nesting rule (inner calls must not commit), which needs a depth counter, and
  the port must decide what a nested call does. Jdbi and jOOQ both show that the
  handle-passing shapes end up carrying exactly this state, a depth counter and a
  savepoint stack, in `DefaultTransactionProvider` and in
  `BoundLocalTransactionHandler`.

**2.4 The cheapest fake is the one that implements one method and models one
rule.** A change set gives that. The fake's atomicity is a map copy, its log is
an `ArrayList`, and its refusal path is "do not publish the staged copy".
**Support:** researcher inference from 2.1 to 2.3. **Confidence:** high.

## Sub-question 3: does a list-taking method still mean one call, one transaction

**3.1 Mechanically, yes.** A method that applies a list is one call and one
transaction, so the letter of the rule holds. A caller cannot get half of it.

**3.2 But it does make the port the owner of that set's atomicity, and
established layers disagree about whether that is acceptable.** Spring Data
accepts it: `saveAll` is `@Transactional`, so the repository method owns the
batch's atomicity, and the docs treat the repository as a legitimate place for a
boundary while recommending that callers "declare transaction boundaries when
starting a unit of work"
([Transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)).
`SimpleJpaRepository.saveAll` calls `save` on `this`, so the inner calls do not go
through the proxy, and the batch is atomic only because of `saveAll`'s own
annotation. That is direct evidence of the framework relying on the batch method
being the boundary. **Support:** direct evidence from source plus the proxy rule
in 1.2; the "atomic only because of `saveAll`'s annotation" step is
interpretation. **Confidence:** high.

**3.3 The layers that keep the boundary out of the write call keep composability
instead.** In JPA a write call owns nothing: `persist` needs a transaction and
throws `TransactionRequiredException` without one, and the boundary belongs to
`EntityTransaction` or to the container, which the caller drives (1.4). In JDBC
the boundary is the connection state (1.7). In both, the caller can widen the
transaction around as many calls as it likes, which is exactly what a change-set
port gives up.

**3.4 The two properties a change-set port cannot have, and the one it gains.**
It cannot be widened by a caller (there is no ambient transaction to join, and
no handle to join with), and it cannot be narrowed per change beyond the set's
membership. What it gains is that the port sees every change before the first
statement runs, which is the precondition for the map's deferred integrity rule:
only the owner of the end of the transaction can defer a check to it. The
deferral requirement in `docs/tasks/02-work-store/tasks/store-contract/task.md`
item 6 is therefore already an acceptance of port-owned atomicity.
**Support:** interpretation. **Confidence:** high.

**3.5 The shape that follows.** If the port owns the atomicity of a set, the set
must be "any number of changes", not a fixed pair for a named use case. A
`storeTaskWithNote` style method would fix the ownership at the smallest unit
and leave the second compound use case without a shape. A general `ChangeSet`
keeps one boundary kind for all use cases. **Support:** researcher inference.
**Confidence:** high.

## Sub-question 4: what each shape costs at the change log

The rule is one row per changed record, written in the transaction that makes the
change, with the row's sequence position acting as the cursor
(`docs/tasks/02-work-store/map.md`).

**4.1 With a change set, the log is written by the component that knows what
changed, inside the transaction that commits it.** The adapter walks the change
list, writes each record and one log row for it, and commits once. Spring
Modulith's publication registry is the same arrangement in a different idiom:
"On event publication, it finds out about the transactional event listeners that
will get the event delivered and writes entries for each of them ... into an
event publication log as part of the original business transaction"
([Working with Application Events](https://docs.spring.io/spring-modulith/reference/events.html)).
**Support:** direct evidence for the pattern, interpretation for the mapping.
**Confidence:** high.

**4.2 With a callback that hands out a handle, the port stops knowing what
changed.** The caller's writes go through the handle, not through a port method,
so either the caller writes the log row (which moves the map's rule into every
call site and into the core, where a framework-shaped log entry now lives) or
the adapter diffs the database after the fact. Both are worse than 4.1. Jdbi's
own design accepts this split: the handle carries `savepoint`,
`rollbackToSavepoint`, and `releaseSavepoint`, so transaction bookkeeping is
available to the caller, and the port is not the only writer.
**Support:** interpretation, from the handle API in 1.6 and
[LocalTransactionHandler.java](https://github.com/jdbi/jdbi/blob/master/core/src/main/java/org/jdbi/v3/core/transaction/LocalTransactionHandler.java).
**Confidence:** high.

**4.3 With two port calls, the cursor range stops being a set boundary.** Two
calls are two transactions ([sqlite-mechanics] section 2), so a reader can call
`changesSince` between them and observe half of a compound change, and a crash
between them leaves half applied. That is the failure the change set exists to
prevent, and it is the one the map's success test ("every change since a cursor
is returned exactly once") would not catch, because each half was returned
exactly once. **Support:** interpretation. **Confidence:** high.

**4.4 One transaction puts the set's rows in one unbroken run of sequence
positions, and a consumer reads them in one call.** Because the sequence is
allocated by the one writer inside the transaction, every row of the set is
allocated before the commit, and the consumer's next `changesSince(cursor)`
returns all of them or none. Two caveats follow and both belong in the
specification:

- **Gaps are legal.** SQLite does not reuse a rolled-back `AUTOINCREMENT` value,
  and the value is never reused after a delete
  ([sqlite-mechanics] section 3, quoting
  [SQLite Autoincrement](https://sqlite.org/autoinc.html)). A reader must treat
  the sequence as monotonic, not dense, and must never infer a missing row from a
  skipped position. **Support:** direct evidence for the reuse rule,
  interpretation for the reader rule. **Confidence:** high.
- **One row per changed record, not per change.** A set that names the same
  record twice would produce two rows for one record. The adapter must either
  reject such a set or collapse it to the net effect. This note recommends
  rejecting, because collapsing makes the log a record of effects while the
  caller wrote intents. **Support:** researcher inference. **Confidence:**
  medium.

**4.5 The batch write itself is cheap.** N log rows inside one transaction is
either N statements or one multi-row insert, and the driver's transaction
boundary does not care which ([sqlite-mechanics] section 2). Nothing about the
change set makes the log expensive. **Confidence:** high.

## Sub-question 5: nested and re-entrant calls

Nested means "a port call already inside a transaction opened by an outer port
call". The shapes answer it four different ways, and none of them opens a second
physical transaction.

**5.1 Spring: join the same physical transaction, with a logical scope per
method.** "When the propagation setting is `PROPAGATION_REQUIRED`, a logical
transaction scope is created for each method upon which the setting is applied
... In the case of standard `PROPAGATION_REQUIRED` behavior, all these scopes are
mapped to the same physical transaction", and an inner scope's rollback-only
marker surfaces at the outer commit as `UnexpectedRollbackException`
([Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)).
The other two answers exist as propagation values: `REQUIRES_NEW` "Create a new
transaction, and suspend the current transaction if one exists" and `NESTED`
"Execute within a nested transaction if a current transaction exists, behave like
`REQUIRED` otherwise"
([Propagation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html)).
**Support:** direct evidence. **Confidence:** high.

**5.2 Jakarta Persistence: refuse a write with no transaction, and join when the
callback shape is used.** A write without a transaction throws
`TransactionRequiredException` (1.4). `runInTransaction` joins what is already
there: "If the transaction type of the persistence unit is JTA, and there is a
JTA transaction already associated with the caller, then the `EntityManager` is
associated with this current transaction", and it starts a new one only
otherwise (spec section 7.6). A second `EntityTransaction.begin()` while one is
active is an `IllegalStateException` (1.4). So: join in the callback shape,
refuse in the explicit shape. **Support:** direct evidence. **Confidence:** high.

**5.3 jOOQ: join, and make the inner commit a no-op.** A nested
`transaction` call pushes onto the provider's savepoint stack. Its `commit`
releases the savepoint and then, when the stack is not empty, does nothing: the
source comment reads "Nested commits have no effect". Its `rollback` rolls back
to the inner savepoint when nesting is enabled, and when nesting is disabled the
ignored marker defers the rollback: the source comment reads "ROLLBACK is only
effective if an exception reaches the top-level transaction", so an inner
failure poisons the outer transaction instead of unwinding it
([DefaultTransactionProvider.java](https://github.com/jOOQ/jOOQ/blob/main/jOOQ/src/main/java/org/jooq/impl/DefaultTransactionProvider.java)).
The manual's example marks the same point, "Implicit savepoint created here"
([Transaction management](https://www.jooq.org/doc/latest/manual/sql-execution/transaction-management/)).
**Support:** direct evidence. **Confidence:** high.

**5.4 Jdbi: join, with the isolation level as the only refusal.** `Handle.inTransaction`
is `isInTransaction() ? callback.withHandle(this) : transactionHandler.inTransaction(this, callback)`,
so a nested call runs the callback in the open transaction and the outer call
commits. The guide states it: "By default, if these methods are called while the
handle is already in an open transaction, the existing transaction is reused (no
nested transaction is created)", and "transactions are not nested"
([Jdbi 3 Developer Guide, 11.1 and SQL Object transactions](https://jdbi.org/),
[Handle.java](https://github.com/jdbi/jdbi/blob/master/core/src/main/java/org/jdbi/v3/core/Handle.java)).
The variant that takes an isolation level throws `TransactionException` when the
level differs from the open transaction's, and joins without a check for
`UNKNOWN`. **Support:** direct evidence. **Confidence:** high.

**5.5 JDBC: there is nothing to nest.** The interface has `commit`, `rollback`,
`setSavepoint`, `rollback(Savepoint)`, and `releaseSavepoint`, and no operation
that begins a transaction inside a transaction. A savepoint is "a point within
the current transaction ... When a transaction is rolled back to a savepoint all
changes made after that savepoint are undone"
([Savepoint](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Savepoint.html)),
and `commit` "Makes all changes made since the previous commit/rollback
permanent" (1.7), which is a flat model with no inner boundary. Two quirks a port
must not rely on: `setSavepoint` called outside a transaction "will be started at
this newly created savepoint", and changing the auto-commit flag "during a
transaction" commits it
([java.sql.Connection](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html)).
**Support:** direct evidence. **Confidence:** high.

**5.6 What the change-set port does: refuse, structurally.** There is no ambient
transaction and no handle, so core code has no way to be inside a port call when
it calls the port again. Re-entrancy is not a runtime check, it is
unrepresentable. That is the strongest of the five answers, because it needs no
depth counter, no savepoint stack, and no error path.

If a later effort adds a callback method to the port, the answer should be
refusal rather than joining, for a reason that is specific to this store: the
deferred integrity check of the map has to know where the combined execution
ends, and a joined inner call cannot know whether it is the end. jOOQ's ignored
savepoint, which defers an inner rollback to the top level, is the same problem
solved by giving up the inner boundary. Refusing keeps the check's end
unambiguous. **Support:** researcher inference. **Confidence:** high for the
first paragraph, medium for the second.

## Recommendation: the change set is the transaction boundary

**Named shape: change-set parameter on one port method.** The port keeps its
per-kind operations, and each of them is a default method that builds a one
element change set and delegates to the single write method the adapter
implements. This is the "shared generic path behind the per-kind methods" that
`store-contract` asked for, and it is the only one of the seven shapes whose
signature holds no framework type and no transport type.

Why not the alternatives, in one line each:

- **Callback with a handle (jOOQ, Jdbi, Spring template, JPA `runInTransaction`):**
  names a framework type in `contract`, so the fake cannot be built without the
  framework (2.1), and it re-opens the nesting question that SQLite's single
  writer and the deferred integrity check both want closed (5.6).
- **Caller-opened transaction (JDBC, JPA `EntityTransaction`):** names
  `java.sql.Connection` in `contract`, which makes the fake a database and
  contradicts the map's rule that `contract` and `core` hold no driver type
  (2.2).
- **Two port calls:** two transactions, so the cursor range stops being a set
  boundary and the deferred check has no end to defer to (4.3, 3.4).
- **Unit-of-work object:** the same atomicity for more surface, because the core
  must carry a mutable object and the fake must model its lifecycle (2.3).
- **Lambda taking the port itself:** no framework type, but the fake must model
  nesting, and the port takes on a re-entrancy rule it otherwise cannot have
  (2.3, 5.6).

### The signatures

```java
package io.github.y4shin.workthread.contract;

/** The one write method an adapter implements. One call, one transaction. */
public interface ForStoringWork {

    @CheckReturnValue
    Result<Applied> applyAll(ChangeSet changes);

    // One settled operation per kind. Each is one change, so each is one
    // transaction, and none of them needs its own adapter method.
    @CheckReturnValue
    default Result<Task> storeTask(TaskDraft draft) {
        return applyAll(ChangeSet.of(new Change.NewTask(draft))).map(Applied::onlyTask);
    }

    @CheckReturnValue
    default Result<Note> storeNote(NoteDraft draft) {
        return applyAll(ChangeSet.of(new Change.NewNote(draft))).map(Applied::onlyNote);
    }

    @CheckReturnValue
    default Result<Void> removeNote(NoteId note) {
        return applyAll(ChangeSet.of(new Change.RemoveNote(note))).map(Applied::nothing);
    }

    // ... one per settled operation per kind, each delegating the same way ...

    @CheckReturnValue
    Result<List<ChangeEntry>> changesSince(Cursor cursor);

    @CheckReturnValue
    <R> Result<Snapshot<R>> snapshotFor(SnapshotQuery<R> query, Cursor cursor);
}
```

```java
/** One intended write of one record. One change produces at most one log row. */
public sealed interface Change
        permits Change.NewTask, Change.EditTask, Change.RemoveTask,
                Change.NewNote, Change.RemoveNote /* , one per kind and operation */ {

    record NewTask(TaskDraft draft) implements Change {}
    record EditTask(TaskId id, TaskDiff diff) implements Change {}
    record RemoveTask(TaskId id) implements Change {}
    record NewNote(NoteDraft draft) implements Change {}
    record RemoveNote(NoteId id) implements Change {}
}

/**
 * Changes that commit together or not at all. One call with one of these is
 * one transaction, one log range, and one integrity check at the end.
 */
public record ChangeSet(List<Change> changes) {

    public ChangeSet {
        changes = List.copyOf(changes);
        // reject an empty set, and reject two changes naming the same record (4.4)
    }

    public static ChangeSet of(Change... changes) {
        return new ChangeSet(List.of(changes));
    }

    public static ChangeSet of(List<Change> changes) {
        return new ChangeSet(changes);
    }
}

/** One outcome per change, in the order the changes were given. */
public sealed interface Outcome permits Outcome.Wrote, Outcome.Removed {
    record Wrote(Stored stored) implements Outcome {}
    record Removed(Reference reference) implements Outcome {}
}

public record Applied(List<Outcome> outcomes) {

    /** The one narrowing in `contract`: valid only for a set of exactly one change. */
    Task onlyTask() { ... }

    Note onlyNote() { ... }

    Void nothing() { ... }
}

/** A stored record with the identifier the store allocated. */
public sealed interface Stored
        permits Stored.Task, Stored.Note /* , one per kind */ {
    record Task(Task record, TaskId id) implements Stored {}
    record Note(Note record, NoteId id) implements Stored {}
}
```

Three notes on the signatures, so the specification does not have to rediscover
them:

1. **`Applied` holds one outcome per change, in order.** That is what makes the
   `only*()` narrowing well defined and what lets a caller match a compound
   change's results without a cast. The narrowing is unchecked arithmetic on a
   list of length one; it lives in `contract` behind a documented invariant, so
   the adapter never sees it. An alternative that removes it entirely is a
   second overload `Result<R> apply(Change<R>)` with `Change<R>` parameterised by
   its stored type, at the cost of an overload pair whose two members differ by
   one argument. Either way the fake implements one method.
2. **No cursor in the write result.** `store-contract` item 7 settled that the
   cursor belongs to the reads, and a change set does not change that. A caller
   that wants a cursor makes one more call to `snapshotFor` or `changesSince`.
3. **No `commit` or `rollback` on the port, and no `begin`.** The transaction has
   no name in `contract` at all, which is the point: `contract` cannot express
   "the transaction is still open", so no caller can get it wrong.

### The cost at the fake

One method, one rule, no framework type:

```java
final class InMemoryForStoringWork implements ForStoringWork {

    private Map<Reference, Object> committed = new LinkedHashMap<>();
    private List<ChangeEntry> log = new ArrayList<>();
    private long sequence;

    @Override
    public Result<Applied> applyAll(ChangeSet changes) {
        Map<Reference, Object> staged = new LinkedHashMap<>(committed);   // 1. stage
        List<ChangeEntry> stagedLog = new ArrayList<>(log);
        List<Outcome> outcomes = new ArrayList<>();
        for (Change change : changes.ordered()) {
            outcomes.add(applyTo(staged, stagedLog, change));             // 2. one log row per change
        }
        Result<Void> checked = checkIntegrity(staged);                    // 3. deferred to the end of the set
        if (checked.isFailure()) return checked.propagate();
        committed = staged;                                               // 4. publish, all at once
        log = stagedLog;
        return Result.of(new Applied(outcomes));
    }
}
```

What this buys the core's tests: all-or-nothing is a reference swap, the log
range is the list it built in step 2, the deferred integrity refusal is a return
in step 3, and a deliberately corrupt set can prove that nothing was published.
A callback shape's fake would need the same logic plus a handle type, and a
JDBC shape's fake would need a database.

### What the specification must then pin

- The concrete change records, one per operation per kind, matching the
  operation set `store-contract` settles.
- The rule for a set that names one record twice: reject, or collapse (4.4).
- The registered reader rule: the cursor is monotonic, never dense (4.4).
- That no other method on the port opens a transaction, and that the adapter's
  `applyAll` never calls the port again (5.6).
- Whether the deferred integrity check can also be asked for explicitly, or only
  happens at the end of a set.

## Contradictions

1. **Jdbi's source and Jdbi's guide look like they disagree, and do not.**
   `BoundLocalTransactionHandler.inTransaction` throws
   `IllegalStateException("Already in transaction")`, while the guide says the
   existing transaction is reused. `Handle.inTransaction` guards the call with
   `isInTransaction()`, so the throwing branch is unreachable from the public
   API. Recorded because the source line is the one a reader finds first.
   Sources: [LocalTransactionHandler.java](https://github.com/jdbi/jdbi/blob/master/core/src/main/java/org/jdbi/v3/core/transaction/LocalTransactionHandler.java),
   [Handle.java](https://github.com/jdbi/jdbi/blob/master/core/src/main/java/org/jdbi/v3/core/Handle.java),
   [Jdbi 3 Developer Guide](https://jdbi.org/).
2. **jOOQ's nesting is savepoint-based, its blog calls it Spring's `NESTED`
   semantics, and its disabled-nesting mode is neither.** With `nested()` true an
   inner rollback undoes only the inner work; with `nested()` false the inner
   rollback is deferred to the top level, so the inner failure unwinds
   everything. The blog ("jOOQ implements NESTED semantics ... or fail nesting")
   is a project blog and was treated as a lead, not as evidence; the source
   settles it. Source:
   [DefaultTransactionProvider.java](https://github.com/jOOQ/jOOQ/blob/main/jOOQ/src/main/java/org/jooq/impl/DefaultTransactionProvider.java).
3. **"A list is atomic" is asserted widely and is only true because of the
   annotation.** Spring Data's `saveAll` is atomic because of its own
   `@Transactional`, not because the method is a batch; a caller that reaches the
   implementation without the proxy gets N transactions. Sources: 1.1, 1.2. A
   Stack Overflow question ("Is CrudRepository.save(Iterable) atomic?") was found
   during the search and rejected as evidence for exactly this reason.
4. **The JDBC specification could not be read.** `java.sql.Connection` and
   `Savepoint` were read at their JDK 21 javadoc, which is normative API
   documentation, but the JDBC 4.3 specification HTML returned HTTP 403, so no
   sentence from the specification itself is quoted here. The claim "JDBC has no
   nested transactions" therefore rests on the API's shape (no begin-in-begin
   operation) and on `commit`'s flat description, not on a specification
   sentence.

## Missing evidence

- **Nothing was executed.** No library was run, so every claim is a reading of
  the library's own source or documentation. The fake sketch in "The cost at the
  fake" is illustrative Java, not compiled or tested.
- **The JDBC 4.3 specification text.** Not reachable (HTTP 403), so the
  nested-transaction claim is API-shape evidence only.
- **Whether the SQLite driver supports nested savepoints through JDBC.**
  `Connection.setSavepoint` is available in the driver, but this note did not
  verify it, because the recommendation does not use savepoints. It matters only
  if a later effort adds a callback method.
- **How the change log's sequence is allocated.** Whether it is an
  `AUTOINCREMENT` column on the log table or `max(sequence) + 1` inside the
  transaction is a schema decision this note did not make. Either way the rows of
  one set are allocated inside one transaction; the gap rule in 4.4 holds for the
  first, and needs re-checking for the second if the table is ever written by
  anything but the one writer.
- **The cost of a large set.** No measurement of a 50 change set against SQLite
  through the pinned driver. The expectation is that it is one transaction of
  about 100 statements and that this is not a bottleneck for a single-user work
  store, but that is unmeasured.
- **Spring Framework's `@Transactional` default propagation** is stated from the
  `Propagation` enum's `REQUIRED` constant description. The reference page for
  propagation describes `REQUIRED` without using the word default, so the two
  are quoted together in 1.2 rather than merged into one sentence.

## Sources

Kept:

- [Transaction Propagation, Spring Framework](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
  and [Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html):
  the join rule, the physical/logical split, and the proxy rule that makes
  self invocation non-transactional.
- [Transactional](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html),
  [Propagation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Propagation.html),
  [TransactionDefinition](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/TransactionDefinition.html):
  thread-bound state, and the propagation values that name the three possible
  answers to nesting.
- [Programmatic Transaction Management, Spring Framework](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html):
  the plainest first-party statement that a callback transaction shape couples
  the caller to the framework.
- [Transactionality, Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
  and [SimpleJpaRepository.java](https://github.com/spring-projects/spring-data-jpa/blob/main/spring-data-jpa/src/main/java/org/springframework/data/jpa/repository/support/SimpleJpaRepository.java):
  a real product where the repository method is the transaction boundary, and the
  source showing a batched write is the boundary's owner.
- [Jakarta Persistence 3.2 specification](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html)
  (sections 3.2, 3.4, 7.5, 7.6) and [EntityTransaction](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityTransaction.html):
  refusal without a transaction, the caller-opened boundary, and the 3.2 callback
  shape that joins an ambient JTA transaction.
- [jOOQ Transaction management](https://www.jooq.org/doc/latest/manual/sql-execution/transaction-management/),
  [TransactionProvider](https://www.jooq.org/javadoc/latest/org.jooq/org/jooq/TransactionProvider.html),
  [DefaultTransactionProvider](https://github.com/jOOQ/jOOQ/blob/main/jOOQ/src/main/java/org/jooq/impl/DefaultTransactionProvider.java):
  the callback shape, and the exact nesting behaviour including "Nested commits
  have no effect".
- [Jdbi 3 Developer Guide](https://jdbi.org/), [Handle.java](https://github.com/jdbi/jdbi/blob/master/core/src/main/java/org/jdbi/v3/core/Handle.java),
  [LocalTransactionHandler.java](https://github.com/jdbi/jdbi/blob/master/core/src/main/java/org/jdbi/v3/core/transaction/LocalTransactionHandler.java),
  [TransactionHandler.java](https://github.com/jdbi/jdbi/blob/master/core/src/main/java/org/jdbi/v3/core/transaction/TransactionHandler.java):
  the handle-passing callback, join-on-re-entry, and the close check that refuses
  a handle with an open transaction.
- [java.sql.Connection, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html)
  and [java.sql.Savepoint](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Savepoint.html):
  auto-commit, the flat commit model, and savepoints as the only partial rollback.
- [Working with Application Events, Spring Modulith](https://docs.spring.io/spring-modulith/reference/events.html):
  the established pattern of writing one log row per change "as part of the
  original business transaction".
- `docs/research/sqlite-mechanics.md`, sections 2, 3 and 7: the driver's
  `BEGIN IMMEDIATE` handling, the never-reuse rule, and why an in-memory database
  cannot stand in for the real store.
- `docs/tasks/02-work-store/map.md` and `docs/tasks/02-work-store/tasks/store-contract/task.md`:
  the constraints the recommendation must satisfy.

Rejected or deprioritized:

- The jOOQ blog post "Nested Transactions in jOOQ": maintainer-written, but a
  blog, and the source settles the question better.
- A Stack Overflow question on whether `save(Iterable)` is atomic, and one on
  nested transactions in JDBC: leads only.
- Two blog posts on JDBC savepoints and nested transactions: they assert that you
  can fake nested transactions with savepoints, which the JDK javadoc already
  states more precisely.
- The JDBC 4.3 specification HTML: HTTP 403, not readable.
- The `Propagation` and `TransactionDefinition` API pages under
  `docs.spring.io/.../javadoc-api`: fetched, but their constant detail blocks did
  not survive extraction, so the `REQUIRED` description is quoted from those two
  pages together with the reference page rather than from one sentence.

## Next steps

1. Settle the change set's shape in `store-contract` item 5, and with it the two
   open rules: reject or collapse a duplicate record in one set, and whether the
   deferred integrity check is only at set end.
2. Let `edit-shape` name the per-kind edit records, since `Change` will carry one
   of them (`TaskDiff`, `NoteDiff`) as its payload.
3. Pin the log sequence's allocation in the schema, and write the reader rule
   from 4.4 into the specification with one test: delete the highest identifier,
   write again, and assert the consumer's next range is monotonic across the gap.
