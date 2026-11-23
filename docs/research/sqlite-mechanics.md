---
type: findings
title: "What the pinned SQLite driver and engine give for WAL, transactions, migrations, and durability"
status: stable
source_task: sqlite-mechanics
---

# SQLite mechanics: driver 3.54.1.0 and SQLite 3.54.1

## Pinned versions

`gradle/libs.versions.toml` pins `sqlite-jdbc = "3.54.1.0"` and
`adapters/build.gradle.kts` resolves `libs.sqliteJdbc` only in `adapters`.

- The tag `3.54.1.0` exists upstream and was cut 2026-10-29
  ([xerial/sqlite-jdbc tags](https://github.com/xerial/sqlite-jdbc/tags)).
- Its release notes say "upgrade to sqlite 3.54.1"
  ([release 3.54.1.0](https://github.com/xerial/sqlite-jdbc/releases/tag/3.54.1.0)),
  so the engine is SQLite 3.54.1. Confidence: high.
- The jar ships its own compiled SQLite per platform, so the engine version
  follows the driver version, not the host.
- Version table above Spring Boot's BOM (3.53.4.0) is only declared in
  `adapters`, so `api` keeps the older engine. That asymmetry does not affect
  this effort, which stores through `adapters` only.

Every driver claim below is read from the source tree at tag `3.54.1.0`, not
from prose. Where the source and the README disagree, the source wins.

---

## 1. Connection setup: WAL, `busy_timeout`, `synchronous`, `foreign_keys`

**Claim.** `journal_mode=WAL` is the only one of the four that persists in the
database file. The other three are per connection and must be set on every new
connection. All four must run before the first `BEGIN`.
**Sources.** [PRAGMA journal_mode](https://sqlite.org/pragma.html#pragma_journal_mode),
[PRAGMA busy_timeout](https://sqlite.org/pragma.html#pragma_busy_timeout),
[PRAGMA foreign_keys](https://sqlite.org/pragma.html#pragma_foreign_keys),
[PRAGMA synchronous](https://sqlite.org/pragma.html#pragma_synchronous).
**Support.** Direct evidence:

- "The WAL journaling mode is persistent; after being set it stays in effect
  across multiple database connections and after closing and reopening the
  database." (pragma.html)
- "Each database connection can only have a single busy handler. This PRAGMA
  sets the busy handler for the process, possibly overwriting any previously set
  busy handler." (pragma.html)
- "This pragma is a no-op within a transaction; foreign key constraint
  enforcement may only be enabled or disabled when there is no pending BEGIN or
  SAVEPOINT." (pragma.html). Default is OFF.
- "the journal_mode cannot be changed while a transaction is active."
  (pragma.html)
- `synchronous` is a per-connection pager flag: `Pager.syncFlags` and
  `Pager.walSyncFlags`, the latter holding "sync flags used for transaction
  commits in the WAL file"
  ([pager.c](https://raw.githubusercontent.com/sqlite/sqlite/master/src/pager.c)).
  Nothing in the file header records it. Did not find a doc sentence stating
  persistence either way, so the negative claim rests on the source and on the
  absence of a header field. Confidence: high for per connection, medium that
  the docs never say it.

**Order that matters.** `busy_timeout` first (so the WAL switch itself waits on
another connection's lock instead of failing), then `journal_mode=WAL` (outside
any transaction, before any statement opens an implicit one), then
`synchronous`, then `foreign_keys`. `page_size`, if ever set, must precede WAL
because "It is not possible to change the page_size after entering WAL mode".

**Driver caveat.** Do not let `SQLiteConfig` apply these pragmas for you:
`SQLiteConfig.apply(Connection)` iterates `pragmaTable.keySet()` (a
`Properties`, i.e. a `Hashtable`), so the order is unspecified
([SQLiteConfig.java](https://raw.githubusercontent.com/xerial/sqlite-jdbc/3.54.1.0/src/main/java/org/sqlite/SQLiteConfig.java)).
The store must issue the four pragmas explicitly, in the order above.
**Support.** Direct evidence (source). **Confidence:** high.

**Driver defaults worth knowing.** `SQLiteConfig` sets
`setOpenMode(SQLiteOpenMode.OPEN_URI)` and
`setBusyTimeout(3000)` by default, so a stock connection already carries
`pragma busy_timeout=3000` and URI filenames are enabled
([SQLiteConfig.java](https://raw.githubusercontent.com/xerial/sqlite-jdbc/3.54.1.0/src/main/java/org/sqlite/SQLiteConfig.java)).
The store should set its own timeout and not inherit 3000 ms silently.

Statements the store will use, each executed with `Statement.execute`, each
outside any transaction:

```sql
PRAGMA busy_timeout = 5000;
PRAGMA journal_mode = WAL;      -- returns one row: 'wal'
PRAGMA synchronous = FULL;      -- 2
PRAGMA foreign_keys = ON;       -- 1
```

Assertions a test can make on the same connection:

```sql
PRAGMA journal_mode;   -- 'wal'
PRAGMA busy_timeout;   -- 5000
PRAGMA synchronous;    -- 2
PRAGMA foreign_keys;   -- 1
```

`PRAGMA journal_mode=WAL` returns a result row, so it goes through
`executeQuery` or `execute`; `executeUpdate` is wrong for it.

---

## 2. Transaction control through JDBC: one port call, one transaction

**Claim.** With `SQLiteConfig.TransactionMode.IMMEDIATE` the driver's
transaction prefix is literally `begin immediate;`. `setAutoCommit(false)`
executes it at once, and `commit()` / `rollback()` each issue `commit;` or
`rollback;` and then immediately start a **new** transaction.
**Sources.**
[SQLiteConnectionConfig.java](https://raw.githubusercontent.com/xerial/sqlite-jdbc/3.54.1.0/src/main/java/org/sqlite/SQLiteConnectionConfig.java),
[SQLiteConnection.java](https://raw.githubusercontent.com/xerial/sqlite-jdbc/3.54.1.0/src/main/java/org/sqlite/SQLiteConnection.java).
**Support.** Direct evidence:

```java
beginCommandMap.put(TransactionMode.DEFERRED,  "begin;");
beginCommandMap.put(TransactionMode.IMMEDIATE, "begin immediate;");
beginCommandMap.put(TransactionMode.EXCLUSIVE, "begin exclusive;");
```

```java
public void setAutoCommit(boolean ac) throws SQLException {
    if (connectionConfig.isAutoCommit() == ac) return;
    connectionConfig.setAutoCommit(ac);
    if (this.getConnectionConfig().isAutoCommit()) { db.exec("commit;", ac); ... }
    else { db.exec(this.transactionPrefix(), ac); ... }
}
public void commit() { db.exec("commit;", ...); db.exec(this.transactionPrefix(), ...); }
public void rollback() { db.exec("rollback;", ...); db.exec(this.transactionPrefix(), ...); }
```

**Consequences the specification must state.**

1. The default mode is `DEFERRED`, and the driver does **not** upgrade a
   deferred transaction on its own unless `jdbc_explicit_readonly` is enabled
   (`JDBC3Connection.tryEnforceTransactionMode()` is gated on
   `isExplicitReadOnly()`,
   [JDBC3Connection.java](https://raw.githubusercontent.com/xerial/sqlite-jdbc/3.54.1.0/src/main/java/org/sqlite/jdbc3/JDBC3Connection.java)).
   A deferred read-then-write in WAL mode can fail to upgrade and return
   `SQLITE_BUSY`. `IMMEDIATE` avoids that class of failure. SQLite documents
   the upgrade: "Subsequent write statements will upgrade the transaction to a
   write transaction if possible, or return SQLITE_BUSY"
   ([lang_transaction](https://sqlite.org/lang_transaction.html)).
   **Confidence:** high.
2. After `commit()` the driver leaves a fresh `begin immediate;` open. If the
   store does nothing else, an idle write transaction is held between port
   calls. That blocks a second writer, and it makes `VACUUM INTO` on the same
   connection fail with "cannot VACUUM from within a transaction". Closing the
   transaction therefore needs `commit()` followed by `setAutoCommit(true)`,
   which issues `commit;` on the empty transaction just opened.
   **Support.** Interpretation from the quoted source. **Confidence:** high.
3. If the `begin immediate;` that `commit()` issues *after* committing fails
   (for example `SQLITE_BUSY` from another writer), `commit()` throws even
   though the caller's work is committed. The port must not treat a failed
   `commit()` as "nothing was written" without checking. **Support.**
   Researcher inference from the source; no upstream issue found for it.
   **Confidence:** medium.
4. `setAutoCommit(false)` records the flag *before* the `begin` is attempted,
   so a failed begin leaves the driver believing a transaction is open when
   SQLite has none. Upstream issue
   [#683](https://github.com/xerial/sqlite-jdbc/issues/683) reports exactly
   this and was closed `NOT_PLANNED` ("Closing this as no feedback was
   received"), so the behaviour stands. The only recovery is to discard the
   connection. **Support.** Direct evidence (issue plus source).
   **Confidence:** high.

**Claim.** A connection in a bad state poisons later calls silently.
**Support.** Direct evidence for the SQLite side: `BEGIN` inside a transaction
fails, and `COMMIT` with no transaction fails with SQLITE_ERROR; both strings
come from the `OP_AutoCommit` case in
[vdbe.c](https://raw.githubusercontent.com/sqlite/sqlite/master/src/vdbe.c):

```c
sqlite3VdbeError(p, (!desiredAutoCommit)?"cannot start a transaction within a transaction":
   ((iRollback)?"cannot rollback - no transaction is active":
                "cannot commit - no transaction is active"));
```

Also documented: "An attempt to invoke the BEGIN command within a transaction
will fail with an error"
([lang_transaction](https://sqlite.org/lang_transaction.html)). If the engine
side transaction disappears while the driver still thinks `autoCommit=false`,
the next statements run as SQLite implicit transactions, one per statement, so
the change and its change log row stop being atomic. **Support.**
Interpretation. **Confidence:** medium-high.

**Claim.** Most statement errors leave the transaction open, so the port must
roll back explicitly; a few errors roll the whole transaction back first.
**Sources.** [lang_transaction, section 3](https://sqlite.org/lang_transaction.html).
**Support.** Direct evidence: "If certain kinds of errors occur within a
transaction, the transaction may or may not be rolled back automatically. The
errors that can cause an automatic rollback include: SQLITE_FULL, SQLITE_IOERR,
SQLITE_INTERRUPT, SQLITE_NOMEM ... An application might want to respond to the
errors listed above by explicitly issuing a ROLLBACK command. ... If the
transaction has already been rolled back automatically by the error response,
then the ROLLBACK command will fail with an error, but no harm is caused by
this." **Confidence:** high. A `ROLLBACK` that fails for this reason must be
swallowed, not surfaced.

Port call shape the store will use:

```java
try {
    connection.setAutoCommit(false);       // executes "begin immediate;"
    // every statement of the port call, including the change log insert
    connection.commit();                   // "commit;" then "begin immediate;"
} catch (SQLException failure) {
    try { connection.rollback(); }         // "rollback;" then "begin immediate;"
    catch (SQLException ignored) { /* already rolled back by the engine: drop the connection */ }
    throw translate(failure);
} finally {
    connection.setAutoCommit(true);        // "commit;", closing the re-opened empty transaction
}
```

---

## 3. `AUTOINCREMENT` and `sqlite_sequence`

**Claim.** After a committed delete the next identifier is not the deleted one;
after a rollback the value may be reused; a file copy preserves the counter only
if the `-wal` file travels with the database.
**Source.** [SQLite Autoincrement](https://sqlite.org/autoinc.html),
[vacuum.c](https://raw.githubusercontent.com/sqlite/sqlite/master/src/vacuum.c),
[WAL](https://sqlite.org/wal.html).
**Support.** Direct evidence:

- "The ROWID chosen for the new row is at least one larger than the largest
  ROWID that has ever before existed in that same table." ... "Only ROWID
  values from previous transactions that were committed are considered. ROWID
  values that were rolled back are ignored and can be reused." (autoinc.html)
  So after a delete the counter keeps its high-water mark, and the never-reuse
  rule is a fact for committed values. After a rollback the value was never
  observable, so reuse of an uncommitted value is harmless but must be stated
  as a caveat, not hidden.
- "The sqlite_sequence table is created automatically ... whenever a normal
  table that contains an AUTOINCREMENT column is created. A row ... is created
  the first time the AUTOINCREMENT table is written and updated on any
  subsequent writes that increase the maximum rowid." (autoinc.html)
- "The content of the sqlite_sequence table can be modified using ordinary
  UPDATE, INSERT, and DELETE statements. But making modifications to this table
  will likely perturb the AUTOINCREMENT key generation algorithm." The store
  must never write to it. (autoinc.html)
- `VACUUM` / `VACUUM INTO` copies table content, including `sqlite_sequence`.
  vacuum.c skips only its `CREATE` (`WHERE type='table' AND
  name<>'sqlite_sequence'`) because the statement is recreated implicitly when
  the AUTOINCREMENT table is created, and then copies rows for every table with
  a root page (`INSERT INTO vacuum_db.x SELECT * FROM main.x`).
  **Confidence:** high for VACUUM preserving the counter.
- "If a database file is separated from its WAL file, then transactions that
  were previously committed to the database might be lost, or the database file
  might become corrupted. The only safe way to remove a WAL file is to open the
  database file ... then immediately close the database using sqlite3_close()."
  (wal.html)

**Inference, flagged.** A pre-migration copy taken by copying only
`work.db` while committed transactions still sit in `work.db-wal` can restore a
publicly visible identifier whose `sqlite_sequence` update was newer than the
copy, which is exactly the reuse the map forbids. This is why the copy must be
`VACUUM INTO` (or the whole data directory, taken while no writer runs), never
a lone `.db` copy. **Confidence:** medium-high, needs a test that writes, does
not checkpoint, copies only the db, and shows the counter regressing.

---

## 4. `VACUUM INTO` as the pre-migration copy

**Claim.** Available in this version, refuses to run inside a transaction, and
refuses a destination that already exists with content.
**Sources.** [VACUUM](https://sqlite.org/lang_vacuum.html),
[release 3.27.0](https://sqlite.org/releaselog/3_27_0.html),
[vacuum.c](https://raw.githubusercontent.com/sqlite/sqlite/master/src/vacuum.c).
**Support.** Direct evidence:

- "Added the VACUUM INTO command" in 3.27.0 (2019-02-07). The bundled engine
  is 3.54.1, so it is present. **Confidence:** high.
- Destination: "The file named by the INTO clause must not previously exist, or
  else it must be an empty file, or the VACUUM INTO command will fail with an
  error." The source's message is `output file already exists`
  (`sqlite3OsFileSize(...) > 0`). The argument is "a scalar expression, such as
  a text literal", and can be a URI filename when URI filenames are enabled
  (the driver enables `SQLITE_OPEN_URI` by default). **Confidence:** high.
- Transaction: `sqlite3RunVacuum` starts with
  `if( !db->autoCommit ){ sqlite3SetString(pzErrMsg, db, "cannot VACUUM from
  within a transaction"); return SQLITE_ERROR; }`, and that check precedes the
  `if( pOut )` branch, so it applies to `VACUUM INTO` as well. The same
  function also refuses `cannot VACUUM - SQL statements in progress` when
  another statement is active on the connection. The doc sentence "A VACUUM
  will fail if there is an open transaction on the database connection that is
  attempting to run the VACUUM" agrees. **Confidence:** high.
- Snapshot quality: "The VACUUM INTO command is transactional in the sense that
  the generated output database is a consistent snapshot of the original
  database." For INTO, the source opens only a read transaction on the source
  (`sqlite3BtreeBeginTrans(pMain, pOut==0 ? 2 : 0, 0)`), so it does not block
  the writer for the duration. If the source's `synchronous` is NORMAL or FULL,
  SQLite fsyncs the output. **Confidence:** high.
- Interrupted run: "if the VACUUM INTO command is interrupted by an unplanned
  shutdown or power loss, then the generated output database might be
  incomplete and corrupt." **Confidence:** high.

**Inference, flagged.** A failed `VACUUM INTO` leaves the partial destination
file behind: `end_of_vacuum` closes and detaches the attached output database
and returns the error, and no code path deletes `zOut`. The store must write to
a temporary name, treat "same name exists" as failure, and delete a leftover
before retrying. **Confidence:** medium.

**Inference, flagged.** The output is a fresh file with no WAL flag in its
header, so it opens in rollback-journal (`DELETE`) mode, not WAL; the store's
normal open sequence sets WAL again. **Confidence:** medium, needs a one-line
test (`PRAGMA journal_mode` on the copy).

---

## 5. Migrations inside a transaction

**Claim.** DDL is transactional; a failed script leaves the previous schema,
provided the runner rolls back. One statement family refuses to run inside a
transaction and two pragmas silently do nothing inside one.
**Sources.** [lang_transaction](https://sqlite.org/lang_transaction.html),
[PRAGMA](https://sqlite.org/pragma.html), [VACUUM](https://sqlite.org/lang_vacuum.html),
[vacuum.c](https://raw.githubusercontent.com/sqlite/sqlite/master/src/vacuum.c).
**Support.** Direct evidence:

- Refuses to run inside a transaction: `VACUUM` and `VACUUM INTO` (error, see
  item 4). Changing `journal_mode` is refused or ignored: "Note also that the
  journal_mode cannot be changed while a transaction is active." No other
  statement in the store's planned set refuses.
- Silently a no-op inside a transaction: `PRAGMA foreign_keys` ("This pragma is
  a no-op within a transaction"). A migration script that turns foreign keys on
  would appear to succeed and change nothing.
- Rollback restores the schema: transactions are atomic, and the rollback of a
  DDL statement is ordinary rollback because the schema is stored in
  `sqlite_schema` pages. The auto-rollback list of item 2 applies here too, so
  a rollback that fails because the engine already rolled back is a success.
  **Confidence:** high on atomicity; the "DDL is transactional" phrasing is
  interpretation of that guarantee (sqlite.org does not have a separate
  "transactional DDL" page).
- Version and checksum rows in the same transaction as the script: yes. They
  are ordinary inserts into an ordinary table, so they commit with the DDL. The
  driver's `commit()` then `begin immediate;` sequence does not split them.
  **Confidence:** high (interpretation from items 1 and 2, plus SQLite's atomic
  commit).
- Order of operations that follows: read the stored version and checksum
  *before* opening the migration transaction, refuse a version newer than the
  code, take the copy with `VACUUM INTO` on a connection in autocommit mode,
  then apply each pending script in its own transaction with its
  version/checksum rows.

Migration runner shape the store will use:

```java
// 0. the copy connection is separate and stays in autocommit mode
try (Statement s = copyConnection.createStatement()) {
    s.execute("VACUUM INTO " + quoteLiteral(copyPath.toString()));
}

// 1. read and refuse before writing anything
long version = selectSchemaVersion();
if (version > CODE_VERSION) throw new SchemaNewerThanCode();

// 2. one transaction per script, version and checksum written inside it
connect.setAutoCommit(false);                  // "begin immediate;"
for (String statement : script.statements()) statement.execute(statement);
insertVersionAndChecksum(script.version(), script.checksum());
connect.commit();                              // atomic with the DDL
connect.setAutoCommit(true);                   // close the re-opened empty transaction
```

---

## 6. Reproducing an unclean close from a test

**Claim.** A committed record survives a killed JVM at every `synchronous`
setting; `synchronous=FULL` is the setting that makes the claim independent of
the operating system and file system.
**Sources.** [PRAGMA synchronous](https://sqlite.org/pragma.html#pragma_synchronous),
[WAL](https://sqlite.org/wal.html),
[Process.destroyForcibly](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Process.html).
**Support.** Direct evidence: "WAL mode is always consistent with
synchronous=NORMAL, but WAL mode does lose durability. A transaction committed
in WAL mode with synchronous=NORMAL might roll back following a power loss or
system crash. **Transactions are durable across application crashes regardless
of the synchronous setting or journal mode.**" (pragma.html). The engine writes
through to the operating system, so a killed process loses only what was never
written, which a committed transaction has been. **Support for the JVM
mapping:** the quote covers the class of failure; that `destroyForcibly()`
produces exactly that class is interpretation, backed by the Java contract:
"Kills the process forcibly. ... Forcible process destruction is defined as the
immediate termination of a process, whereas normal termination allows the
process to shut down cleanly." **Confidence:** high that the record is readable
after the kill; high that `FULL` is the portable choice; the second is a
recommendation, not a measured result.

What a kill does to the files and to the next opener:

- A killed process never closes the database, so no checkpoint runs and
  `work.db-wal` and `work.db-shm` remain next to `work.db`. The WAL is part of
  the persistent state: "The WAL file is part of the persistent state of the
  database and should be kept with the database if the database is copied or
  moved." (wal.html)
- The next open recovers: "If the last connection to a database crashed, then
  the first new connection to open the database will start a recovery process.
  An exclusive lock is held during recovery", and a third connection arriving
  during recovery gets `SQLITE_BUSY` (wal.html). Tests that reopen in two
  threads at once will see that.
- A clean close is the wrong test: closing the last connection checkpoints and
  removes the WAL, so the test would never exercise WAL recovery. The child must
  be killed without `close()`, and the parent must assert that
  `work.db-wal` existed before reopening.

Cross-platform notes for the test:

- Use `Process.destroyForcibly()` and `waitFor()`, not `destroy()`, because
  "The default implementation of this method invokes destroy() and so may not
  forcibly terminate the process" (Process javadoc). On Unix that is SIGKILL,
  on Windows TerminateProcess.
- Use `synchronous=FULL` in the store and in the test, so the result does not
  depend on when the platform flushes the OS cache. With `NORMAL`, WAL commits
  are not fsynced at all: "with synchronous=NORMAL in WAL mode, transaction
  commit is not synced" (pager.c), and the doc states the power-loss
  consequence above.
- macOS needs `PRAGMA fullfsync=ON` for a real power-loss guarantee, because
  that is the only platform with `F_FULLFSYNC` ("Only Mac OS-X supports
  F_FULLFSYNC", pragma.html). This is about power loss, not about a killed
  process, so it is optional for this effort's success test.
- Two assertions, not one: the record reads back unchanged, and its
  `sqlite_sequence` high-water mark did not regress.

---

## 7. Where an in-process database differs

**Claim.** An in-memory database cannot do WAL, cannot be locked, disappears
with its connection, and cannot be opened by a second process. A test on
`:memory:` can prove SQL logic and nothing else this effort claims.
**Sources.** [PRAGMA journal_mode](https://sqlite.org/pragma.html#pragma_journal_mode),
[PRAGMA locking_mode](https://sqlite.org/pragma.html#pragma_locking_mode),
[In-Memory Databases](https://sqlite.org/inmemorydb.html),
[pager.c](https://raw.githubusercontent.com/sqlite/sqlite/master/src/pager.c).
**Support.** Direct evidence:

- "Note that the journal_mode for an in-memory database is either MEMORY or OFF
  and can not be changed to a different value. An attempt to change the
  journal_mode of an in-memory database to any setting other than MEMORY or OFF
  is ignored." (pragma.html) So `PRAGMA journal_mode=WAL` does not fail, it is
  ignored, and returns `memory`. A test asserting `wal` would fail loudly, which
  is the only good outcome.
- "For a temporary or in-memory database (neither of which require any locks),
  this variable is always set to EXCLUSIVE_LOCK" (pager.c, on `Pager.eLock`).
  And "The temp database ... and in-memory databases always uses exclusive
  locking mode. The locking mode of temp and in-memory databases cannot be
  changed." (pragma.html)
- "Every :memory: database is distinct from every other. So, opening two
  database connections each with the filename ':memory:' will create two
  independent in-memory databases." (inmemorydb.html) A second opener cannot be
  refused, because there is nothing to refuse.
- No `-wal` and `-shm` files exist, so nothing about checkpointing, recovery, or
  WAL growth is exercised. Durability across a kill is meaningless: the content
  never left the heap.
- Shared-cache in-memory (`jdbc:sqlite:file::memory:?cache=shared`) makes one
  in-memory database visible to several connections in the same process, and
  still has no WAL (inmemorydb.html plus the journal_mode rule above).

**Consequence for the test builder.** The real-file default in the map is not a
preference, it is the only mode in which items 1, 4, 6 and 8 are testable. An
in-memory mode may exist for speed, but the success test must always run on a
real file, and one test must assert `PRAGMA journal_mode` is `wal` on the real
file.

---

## 8. The ownership lock

**Claim.** `FileChannel.tryLock` on `owner.lock`, taken before the database
opens, refuses a second owner by returning `null`; the same JVM's second
attempt throws instead. The lock file must never be replaced or reopened.
**Sources.**
[FileChannel.tryLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html),
[FileLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html),
[fcntl F_SETLK](https://man7.org/linux/man-pages/man2/F_SETLK.2const.html).
**Support.** Direct evidence:

- "Returns: A lock object representing the newly-acquired lock, or `null` if
  the lock could not be acquired because another program holds an overlapping
  lock." Throws `OverlappingFileLockException` "If a lock that overlaps the
  requested region is already held by this Java virtual machine", and
  `NonWritableChannelException` "If this channel was not opened for writing"
  (FileChannel javadoc). So the channel opens with `WRITE` (or `READ, WRITE`),
  and a same-process second attempt throws rather than returning `null`; the
  store must catch it and treat it as "already owned".
- Lock lifetime: "It remains valid until the lock is released by invoking the
  release() method, by closing the channel that was used to acquire it, or by
  the termination of the Java virtual machine, whichever comes first."
  (FileLock javadoc)
- "As well as being removed by an explicit F_UNLCK, record locks are
  automatically released when the process terminates." and "If a process closes
  *any* file descriptor referring to a file, then all of the process's locks on
  that file are released, regardless of the file descriptor(s) on which the
  locks were obtained." (F_SETLK man page) So: a killed owner leaves no stale
  lock, and the store must never open `owner.lock` a second time in the same
  JVM, because that close would drop the lock it thinks it holds.
- Windows: `FileLock` maps to the platform's native facility and the docs warn
  that "whether native file locks are advisory or mandatory is
  configurable"/platform dependent, recommending they be used "as if they were
  advisory locks". A second process still fails to acquire, which is all the
  store needs.
- No interaction with SQLite: SQLite locks the database file, the `-wal` file
  and the `-shm` wal-index (wal.html, on locks held during recovery and
  checkpointing). `owner.lock` is a sibling path that SQLite never opens, so
  holding a `FileChannel` lock on it does not disturb the engine, and the
  engine's own locks do not disturb the ownership lock. **Confidence:** high.

**Inference, flagged.** Replacing `owner.lock` (delete, then create) breaks
exclusion: POSIX record locks are held on the file, so the first owner keeps a
lock on the old file while a second process creates and locks a new one at the
same path. Both owners then believe they own the data directory. This is why
the map's "the file the lock is held on is never replaced" is load bearing.
**Confidence:** high in mechanism, though the man page cited does not spell out
the inode case; a test that deletes and recreates the lock file while holding
the lock would confirm it.

**Inference, flagged.** Taking the lock before opening the database is what
makes the refusal deterministic. SQLite's own locks are taken lazily, so a
second owner that opens the database first and loses the lock race has already
run recovery and pragmas against the file. The ordering in the map is right and
cannot be reversed. **Confidence:** high.

Lock acquisition the store will use:

```java
Path lockPath = runtimeDir.resolve("owner.lock");           // runtime dir, not data dir
FileChannel channel = FileChannel.open(lockPath,
        StandardOpenOption.CREATE, StandardOpenOption.WRITE); // never READ-only
FileLock lock = channel.tryLock(0L, Long.MAX_VALUE, false);  // shared = false
if (lock == null) {
    channel.close();
    throw new DataDirectoryAlreadyOwned(lockPath);           // another process owns it
}
// keep channel and lock open for the process lifetime; never delete or replace the file
```

Wrap `OverlappingFileLockException` into the same refusal, and close the channel
on every failure path so a failed start does not hold the lock.

---

## Full open sequence the store will use

```java
// 1. lock first, before any database work
Path lockPath = runtimeDir.resolve("owner.lock");
FileChannel lockChannel = FileChannel.open(lockPath, CREATE, WRITE);
FileLock lock = lockChannel.tryLock(0L, Long.MAX_VALUE, false);
if (lock == null) throw new DataDirectoryAlreadyOwned(lockPath);

// 2. driver configuration, code-side only
SQLiteConfig config = new SQLiteConfig();
config.setOpenMode(SQLiteOpenMode.READWRITE);                 // CREATE and URI are driver defaults
config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
Connection connection = config.createConnection("jdbc:sqlite:" + dataFile.toAbsolutePath());

// 3. pragmas, explicit and ordered, outside any transaction
try (Statement s = connection.createStatement()) {
    s.execute("PRAGMA busy_timeout = 5000");
    s.execute("PRAGMA journal_mode = WAL");
    s.execute("PRAGMA synchronous = FULL");
    s.execute("PRAGMA foreign_keys = ON");
}

// 4. version check, copy, migrations (see item 5)
// 5. the port's writer thread owns this connection from here on
```

Do not pass those pragmas through `SQLiteConfig` properties: the driver applies
properties in unspecified order (item 1).

---

## Contradictions and disputes

1. **"VACUUM INTO is transactional" versus "cannot VACUUM from within a
   transaction".** The doc page says both: the INTO form "is transactional in
   the sense that the generated output database is a consistent snapshot", and
   "A VACUUM will fail if there is an open transaction on the database
   connection that is attempting to run the VACUUM". They are compatible once
   read precisely: the first describes the snapshot guarantee, the second the
   calling rule. The source settles it: the `autoCommit` check runs before the
   INTO branch. Recorded because the specification will quote the doc sentence.
2. **Driver autocommit flag versus engine state.** The JDBC contract says
   `getAutoCommit()` reports whether the connection is in autocommit; the driver
   reports its own flag, which `setAutoCommit` sets before the `BEGIN` can fail
   ([#683](https://github.com/xerial/sqlite-jdbc/issues/683), closed
   `NOT_PLANNED`). The engine can therefore be in the opposite state. The
   specification must not trust `getAutoCommit()` as evidence that a
   transaction is open.
3. **`commit()` can fail after committing.** Not a documented contradiction,
   just a driver shape that breaks the naive reading of JDBC: `commit()` runs
   two statements, so a failure of the second is reported as a failed commit.
   No upstream issue found; recorded as researcher inference.
4. **Secondary sources disagree on `busy_timeout` scope.** Blog and Q&A posts
   treat `busy_timeout` as persistent. The pragma doc says it sets the busy
   handler for the connection. The doc wins; those posts are leads, not
   evidence.

## Missing evidence

- Nothing in this note was executed. Every driver claim is read from source at
  tag `3.54.1.0`; every engine claim from sqlite.org or sqlite source. There is
  no run of the pinned jar in this environment.
- The output journal mode of a `VACUUM INTO` copy is inferred, not observed
  (item 4).
- A failed `VACUUM INTO` leaving a partial destination file is inferred from
  `end_of_vacuum`; not observed.
- Whether `commit()` surfacing a failed post-commit `begin immediate;` has been
  reported upstream: the issue tracker was read for the autocommit issue only,
  not searched exhaustively.
- The compile options of the SQLite bundled in the jar (`SQLITE_THREADSAFE`,
  `SQLITE_DEFAULT_FOREIGN_KEYS`) were not verified. The store sets
  `foreign_keys` explicitly, so the default does not matter, but the threading
  mode does if the writer connection is ever touched by a second thread.
- Windows: that `LockFileEx` locks are released on forced process termination
  is assumed from the platform's documented behaviour, not verified here.
- `sqlite_sequence` behaviour after `VACUUM INTO` then restore is derived from
  the vacuum source; no test was run.

## Next steps

1. Write the spike test that answers item 6 and the two `VACUUM INTO`
   inferences at once: real file, `synchronous=FULL`, child JVM commits, parent
   `destroyForcibly()`, parent reopens, asserts the record and asserts
   `PRAGMA journal_mode` on a `VACUUM INTO` copy is `delete` before the store
   sets `wal`.
2. Add one test that deletes the highest identifier, inserts again, and asserts
   a higher identifier, plus one that copies only the `.db` before a checkpoint
   and shows the counter regressing. The second documents why the copy rule
   exists.
3. Decide the `busy_timeout` value and put it in the specification; the driver's
   silent 3000 ms default is not a decision.
