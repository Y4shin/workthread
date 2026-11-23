---
type: architecture
title: "workthread v1: architecture and per-effort contracts"
status: stable
---

## What this document is

The living design record for workthread v1. It inlines the architecture content
of `spec.md` in full, then adds the implementation-facing material the child
efforts need: what each one exports, what it must not change, the seams where
tests and tools meet the code, and the parallel versions nobody should invent.
It archives with this effort, and child efforts copy from it rather than
re-deriving the design.

The authority for product behaviour is the specification. The authority for
planning decisions is the map. This document is the authority for how the code
is shaped, and it changes only through the architecture writer.

The ticket graph for this effort carries one formality ticket, because the work
lands in twelve child efforts rather than in tickets here. So the contracts
below are per child effort, not per ticket, and each one names the requirement
ranges it owes from `docs/v1/requirements-directory.csv`.

Start here if you are picking up one child: the build order, with each effort's
predecessor, release, and requirement count, is the table under
[`## Effort order`](#effort-order). Then read that effort's contract under
`## Contracts per child effort`, which states what it exports and what it must
not change.

## The build and the module graph

Java 21 LTS, pinned by toolchain. Gradle 9.9.1 with the Kotlin DSL and
per-module toolchains. JUnit 5 and AssertJ for the core, with a thin JavaFX
headless smoke layer. The Model Context Protocol SDK is
`io.modelcontextprotocol.sdk:mcp` 2.1.0 under MIT; the `mcp` artifact is an empty
aggregator, so the real code is `mcp-core` plus `mcp-json-jackson3`, and the
JSON mapper must be supplied explicitly.

Eight modules, with dependencies pointing inward: `contract` holds both port
families and the types they exchange; `core` holds the domain model and the use
cases and sees only `contract`; `adapters` sees only `contract`; `wiring` holds
the startup configuration sum, one source per platform, the configurator, and
the runtime record; `cli`, `mcp`, `api`, and `client` are the four process
shapes. A build check enforces that `core` declares neither Spring nor JavaFX,
because otherwise standalone mode drifts into needing both.

The core is hexagonal. Surface ports are grouped by use-case area, each
operation coarse enough for the wire. Interface names carry Cockburn's `For`
prefix on both sides, so `ForCapturingTasks` is a surface port and
`ForStoringWork` an infrastructure port, and the side is marked by the package
rather than by the identifier. The plugin contract stays minimal and versioned;
v1 ships no plugin discovery, only the boundary.

One configurator expresses the in-process-or-daemon rule once: it takes a
`StartupConfig` and returns one runtime value with two axes, a list of surfaces
and one authority. `StartupConfig` is a sealed sum with one record per process
shape, so a split-mode surface cannot exist without an endpoint and a
credential, and a standalone surface cannot exist with one. `StartupConfigSource`
is one plain class per platform, named for what it does because it reads from
the platform rather than providing a value.

The client and the tray ship in the `client` module as a `jlink` image built by
`org.beryx.jlink` with `--strip-debug --compress zip-6`, JavaFX on the module
path, enforced by the build. The image is not fully self-contained on Linux: it
still needs the host's GTK3 and X11 libraries. A Spring Boot fat jar is not a
supported JavaFX host, so packaging is an image rather than a single jar.
Java 21 caps the usable JavaFX line at 23.0.2, because later lines need later
JDKs; the exact patch version is a build detail.

The staging rule is a monotone staircase. Every stage, and every version inside
a stage, ends with a version number, works on real tasks, and keeps every
interface an earlier version shipped. No version removes a working command or
surface, and stages run in sequence, so a version is tagged before the next one
opens. A runnable jar makes the documented-unsupported classpath configuration
the default, which is why the build enforces the module path and why the
scaffolding effort ships an empty window as an image from its first commit.

Recorded hazards that the build must live with: every `jpackage` image printed
an unexplained "pure virtual method called" at startup on this project and the
application still ran; JavaFX natives are extracted and cached in the user's
home; and `javafx.graphics` already requires `java.desktop`, so a no-tray image
saves nothing.

### What each module holds

| Module | Holds | May depend on |
|---|---|---|
| `contract` | both port families and every type crossing them | nothing |
| `core` | the domain model, the use cases, the planner engine, migrations | `contract` |
| `adapters` | the SQLite store, the clock, the notifier, the planner adapter | `contract` |
| `wiring` | `StartupConfig`, its sources, the configurator, the runtime record | `contract` |
| `cli` | argument parsing, command dispatch, output formatting | `contract`, `wiring` |
| `mcp` | the tool surface over standard input and output | `contract`, `wiring` |
| `api` | the REST contract, the event stream, the remote proxy | `contract`, `wiring` |
| `client` | the JavaFX window, the tray, the desktop entry point | `contract`, `wiring` |

The arrow never reverses. `core` must not see `adapters`, and no surface module
may depend on another surface module: a surface that needs another surface's
work asks the authority for it.

## Ports and the surface vocabulary

Surface ports, one per command area and per editing concern:

- `ForCapturingTasks` creates and edits tasks, sets priority, estimate,
  deadline, and source, and attaches notes.
- `ForManagingProjects` creates, edits, and closes projects.
- `ForTrackingTime` starts, pauses, completes, logs, and corrects time.
- `ForPlanningQueries` returns the today list with reasons and the fit signal,
  forecasts, notices, and review projections.
- `ForDefiningCapacity` holds the timetable and the time-off entries, including
  retroactive ones.
- `ForOverridingRules` records a deliberate override.
- `ForConfiguring` holds the timezone, the settings, and export.

Infrastructure ports, implemented by adapters:

- `ForStoringWork` covers every record kind: tasks, projects, dependencies, time
  entries, settings, time off, notes, sessions, notices, pending operations, the
  change log, and the operation log.
- `ForPlanning` takes data, never a store: `plan(PlanningInput input, Instant
  asOf, Session session)`. The built-in engine is its default implementation, so
  a later plugin can replace it without the core learning about plugins.
- `ForReadingClock` returns now and today in the configured zone. Nothing in the
  core reads the system clock directly.
- `ForNotifying` raises an operating-system notification, because in split mode
  the daemon has to raise it.
- `ForRememberingSurfacePreferences` exists for the variant where the core
  remembers a surface's own preferences, and is not required by v1's default.

A surface may hold different implementations for different ports, but never two
authorities for the same data. A surface also may hold capabilities of its own,
and those must never read or write the core's data.

## Storage and the record model

SQLite through `org.xerial:sqlite-jdbc` 3.54.1.0, in WAL mode with a
`busy_timeout` and one writer thread. WAL runs exactly one writer at a time and
does not work over a network filesystem, which is consistent with one owner per
data directory.

The transaction boundary is one port call, and the ports are kept few. The
change log is written inside the same transaction as the data it describes, so
a change and its event cannot disagree.

Locations are explicit. The data directory holds `workthread.db` with its `-wal`
and `-shm` siblings in the platform data location, together with the export
definitions, the confirmation code log file, and the most recent pre-migration
copy. The runtime directory holds `owner.lock`, `owner.json`, and the transport
credential; on Linux it is `$XDG_RUNTIME_DIR/workthread`, falling back to
`$TMPDIR/workthread-$UID` at mode 0700. Ownership is an exclusive file lock on
`owner.lock`, taken before the database opens, and `owner.json` is a hint while
the lock is the truth, replaced by writing a temporary file and moving it.

Migrations are ordered SQL scripts shipped inside the core and applied at
startup inside a transaction, each with its version and checksum written in the
same transaction so a failed migration leaves nothing half-applied. The
application refuses to open a database whose schema version is newer than it
knows, and writes a copy of the database beside it before migrations run,
keeping the most recent one.

The identifier is a monotonic integer allocated only by the data owner, and it
is opaque by rule: no surface, agent, or plugin may interpret one, compare two,
or assume adjacency. The export and the change log carry the bare integers, and
a restore by file copy keeps the sequence state. A test that deletes a record
and asserts the next identifier is not reused belongs in the first tick of the
store.

The record is the reconciled shape, not the Taskwarrior shape: an identifier, a
required title, zero or more notes, an optional project, a priority, an
estimate, an optional deadline, source metadata, and a stored status of inbox,
ready, in progress, completed, or cancelled, with blocked derived and never
stored. Notes replace the single description field, which is the append-only
journal idea applied to text. History leaves the task record and lives in the
operation log. Product rules that the store cannot express stay product rules,
with one deliberate exception: the ready-state estimate rule is enforced where
it can be.

From the Taskwarrior comparison, four ideas were carried over: the flat
extensible attribute set with one key per member for set-valued fields, the
operation log as history, a virtual derived status rather than a stored one, and
the discipline of separating stored facts from computed ones. What workthread
needs and Taskwarrior lacks is the estimate, logged work, remaining effort, the
timetable, and the session, which is why the shape is adapted rather than
adopted.

The cursor is opaque to the caller: it is the change log's sequence position,
dressed so a caller cannot construct one. Every change records the surface it
came from, one of command line, desktop, tray, and agent, and there is no user
identity field, because a single-user local tool cannot honestly claim one.

Pending operations and ephemeral notices are stored state with lifetimes: a
pending operation carries an expiry and an attempt count, and both are deleted
when they finish or expire. The stop instant a graceful shutdown records is
written to the operation log, which is why the log must therefore outlive the
process, and because the cursor is a log position, resumption reads the log the
projections are built from.

### What the store must hold, in record terms

Nine stored record kinds, and each one exists for a reason a child effort can
cite:

- **Task** and **project**, with the fields above.
- **Dependency**, a directed edge between two tasks.
- **Note**, owned by a task, with its own timestamp and identifier ordering.
- **Time entry**, one per interval of logged work, open or closed.
- **Session**, one per date, carrying start, leave, and the break reserve that
  date used.
- **Time off**, one per date, carrying a minute count and a kind.
- **Settings**, the business settings as one record family: the timetable, the
  planned start, the default break reserve, the two bands, the notice levels and
  delivery flags, the lead window, the concurrency flag, quiet hours, and the
  purge age.
- **Notice**, persistent or ephemeral, with a type and a level, plus its
  dismissal.
- **Pending operation**, with its code, expiry, attempt count, and payload.

Two logs sit beside them: the change log, whose sequence position is the cursor
and whose rows carry the surface, and the operation log, which is history and is
kept forever in v1.

## Cross-cutting mechanisms

Four mechanisms appear in more than one effort. Each has exactly one owner, and
every other effort calls it rather than reimplementing it.

**The authority.** `Authority` answers where the work logic is from a process's
point of view: the in-process core, or a remote proxy that forwards to the
daemon. Surfaces never know which. Shutdown order is fixed: surfaces close
first, then the authority.

**The cursor.** One general change feed, ordered by the change log's sequence
position, returns everything since a cursor regardless of entity. Every read of
a changing thing returns the complete current state plus changes since the
caller's cursor, plus a fresh cursor. The same cursor is the `Last-Event-ID`
value on the stream.

**The confirmation handshake.** Exactly three things need confirmation: a
removal that changes something already reported, an override, and completing a
task with incomplete prerequisites. The call records a pending operation with a
two-digit code and does not execute. The code reaches the human through the
delivery ladder: an ephemeral notice on an attached interactive surface, then an
OS notification, then the private log file in the data directory, then a free
stream that is not carrying the protocol. A window, the tray, and an OS
notification are agent-proof. With no channel, the call fails with
`confirmation_unavailable`. Three failed attempts kill the operation.

**The notice.** One concept with a type, a level of `info` or `warning`, and a
persistent or ephemeral lifetime. Derived notices are recomputed projections,
one-off notices are stored records, dismissals are stored. Level and OS delivery
are per-type settings; persistence is not configurable. The notice list is
always authoritative, so an OS copy is never the only trace.

## The seven command areas

The command line shape is `workthread <area> <action> [arguments]`, one command
per port operation, and this table is binding on every CLI child effort:

| Area | Port | Carries |
|---|---|---|
| `task` | `ForCapturingTasks` | create, edit, inspect, list, notes |
| `project` | `ForManagingProjects` | create, edit, close, list |
| `plan` | `ForPlanningQueries` | today, forecast, review, notices |
| `timer` | `ForTrackingTime` | start, pause, stop, complete, log, correct |
| `override` | `ForOverridingRules` | record an override |
| `capacity` | `ForDefiningCapacity` | timetable, time off |
| `config` | `ForConfiguring` | timezone, settings, export |

`mcp serve` and `confirm <id> <code>` sit outside the areas because they are not
port operations. A new area means a new surface port, and that is a design
change rather than a feature.

Output is text by default; one global `--format json` prints exactly one
documented JSON document per invocation; `--format text` is the default spelled
out; JSON is never coloured and text honours `NO_COLOR`; a command returning no
data ignores the flag silently; errors go to standard error, as JSON under
`--format json`, carrying the same stable failure code.

Six exit codes: `0` success, `1` reserved for an unknown or internal error, `2`
usage, `3` validation refused, `4` rule refused including an unavailable
confirmation, `5` service unreachable. Three against four is the split that
matters.

Identifiers are accepted bare or prefixed, case-insensitively, with no fuzzy
matching. Every command answers `--help`, `workthread help <area>` lists an
area, and `--version` prints the version. Commands, flags, JSON shapes, and exit
codes are additive once shipped; the JSON envelope is fixed once by `cli-basics`
and carries both the bare integer and the display form.

Which commands exist inside an area is settled by the effort that fills the
area, not here. This document fixes the shape, the output contract, and the exit
codes, and each CLI child names its commands inside them.

## The daemon and the API

The Spring Boot API module arrives with the daemon stage and not earlier. The
daemon is a surface too: its listener is a driving adapter that calls the same
surface ports as every other surface, and `Authority` is where the work logic is
from that process's point of view, either the in-process core or a remote proxy.
Shutdown is stated once: the surfaces close first, then the authority, and in
the daemon the listener is a surface.

The two run modes are defined by where the work logic runs, not by which
processes exist. A standalone surface owns the data; a split surface holds an
endpoint and a credential and defers to the daemon. The mode is detected, not
chosen: the binary looks for a running daemon and attaches to it, and the daemon
is a subcommand of the same binary, meant to be started by a service manager.
A lost daemon is not a mode change: while a client is attached and the daemon
stops, the client reports it, and continuing in the client's own process is
offered as an explicit action that takes ownership.

Configuration has an owner, and no file is shared. The daemon's process
configuration lives in a file or the environment because it must be readable
before the daemon has a database. The business settings live in the daemon's
data and are reached only through the daemon and its API: the timetable, the
planned start, the break reserve that supplies each date's default, the four
balancing values, the notice levels, the approach lead window, the concurrency
setting, quiet hours, and the purge age. A surface's own configuration is
separate again: an endpoint override for a daemon on another host, and that
surface's preferences.

The token rule follows from the bind. A loopback bind needs no token by default
and can require one as a choice. A non-loopback bind requires a token and a
fixed port, and the daemon refuses to start without both. The token comes from
configuration rather than from a file written at start, as an environment
variable or a configuration entry, and rotating it means restarting the daemon.
No credential is ever published in the runtime directory, because a published
credential outlives the process that wrote it. `owner.json` still publishes the
endpoint for local discovery, and a local client that is configured to use it
reads the endpoint rather than the credential.

The API is a contract, not an internal pipe. REST resources are organised by
record kind and versioned, described in one OpenAPI document, and every
operation maps to a resource or an action on a resource, so there is no
RPC-shaped endpoint. The remote proxy translates surface-port calls into
resource operations, so a split-mode client does not know which mode it is in.
The reads that belong together use the change cursor the feed already provides.

The live channel is server-sent events on the same HTTP API, the same port, the
same OpenAPI document, and the same version. Resumption is the `Last-Event-ID`
header carrying the change cursor. Three event kinds are sent: `state` once when
the stream opens carrying the current cursor, `changed` as stored records change
carrying the cursor, and `notice` carrying a notice's identifier, type, and
level so a consumer can decide whether it deserves attention. The stream carries
notifications, not projections: every list, forecast, and review is still read
over REST, and on reconnect the client compares cursors and refetches when they
disagree. There is no replay buffer and therefore no race between a replay
finishing and the live tail starting. One Java reader lives in the module that
holds the remote proxy and serves the desktop, the tray, and the command line;
it parses the stream, resends the cursor, and backs off on failure. Plain
polling stays available as a fallback, because a dropped stream is a latency
problem rather than an outage.

Operationally: a comment heartbeat every fifteen seconds, which survives the
sixty-second default idle read timeout; a server-side emitter timeout of thirty
minutes, so a connection is recycled deliberately; and virtual threads enabled,
so a blocked write per idle connection costs a virtual thread rather than a
platform one. The response is declared as `text/event-stream` with an explicit
event schema, which OpenAPI 3.1 expresses; when the pinned documentation
generator emits OpenAPI 3.2 the endpoint gains `itemSchema` so each event is
described rather than pointed at.

Graceful stop records the instant it happened in the operation log and leaves
open timers and session open. On the next start, anything opened before that
instant is presented for correction with its end time proposed. A crash leaves
no fresh stop instant, so the same machinery asks. Only one process may hold the
data, and a second process that finds the lock held fails with a clear message
rather than waiting.

## Contracts per child effort

## Contracts per child effort

Twelve efforts in dependency order. Each entry states what the effort exports to
the efforts that depend on it, what it must not change, and the requirement
ranges it owes. The ranges are the `## Requirements` content for the child's own
map, taken from the `effort` column of `docs/v1/requirements-directory.csv`.

Six ranges changed after the scaffolding effort closed, when 25 rows that the
scaffolding could not honour were reassigned to the efforts whose contracts own
them. The register is authoritative where a range here disagrees with it.

### 1. scaffolding

Exports: a repository where `./gradlew build` passes with the eight modules, the
dependency rules enforced by the build, JUnit and AssertJ wired, the version
tagging rule in place, and one empty JavaFX window shipping as a `jlink` image
with JavaFX on the module path.

Must not change: the module names and the inward dependency rule. Later efforts
add code, never modules, unless the architecture changes first.

Requirement ranges: MAP-007, MAP-014, MAP-016, MAP-019, MAP-029, MAP-C04,
MAP-C07, T01-022, T03-001, T03-003..T03-004, T03-012, T04-001..T04-002,
T04-004, T04-007, T04-009, T04-013, T04-015..T04-016, T04-018,
T06-001..T06-004, T06-008, T06-012..T06-013, T07-004, T07-008..T07-010,
T09-004, T11-079..T11-081, T11-088, T11-090, T21-005, T22-002..T22-004,
T22-006, T22-008..T22-009, T23-008, T24-020, T25-041

### 2. work-store

Exports: a store that round-trips a task and a project, answers "what changed
since this cursor", refuses a second owner, migrates forward only, and hands the
rest of the product a `ForStoringWork` that is the single transaction boundary.

Must not change: the record shape and the identifier rule. A surface that wants
a new field asks for a migration; it does not add a column.

Requirement ranges: CTX-note, MAP-027, MAP-031..MAP-033, MAP-059,
SRC-RULE-DATA-3, SRC-RULE-ID-1, SRC-RULE-RECALC-4, SRC-RULE-STATE-1,
T021..T023, T027..T028, T032..T033, T04-006, T04-014, T05-051..T05-054,
T11-001, T11-010..T11-012, T11-018, T11-050..T11-055, T12-001..T12-002,
T12-005, T12-007, T12-009..T12-014, T12-029, T12-031..T12-033, T12-038,
T17-008, T17-010, T17-015, T17-028..T17-038, T17-041, T18-025, T20-023

### 3. cli-basics

Exports: the `cli` module with argument parsing, the command envelope and the
six exit codes, the standalone configurator and runtime, capture, list, inspect,
complete, the timer, logged time, and the minimal day start the timer requires.

Must not change: the command shape, the output contract, the exit codes, and the
JSON envelope. Every later CLI effort lives inside them.

Requirement ranges: CTX-concurrent-work, CTX-standalone-mode, MAP-001, MAP-013,
MAP-022, MAP-025..MAP-026, MAP-028, MAP-045..MAP-046, MAP-C08, MAP-C14,
SRC-2.1.1, SRC-4.12..SRC-4.13, SRC-4.15, SRC-8.8.1,
SRC-RULE-ARCH-1..SRC-RULE-ARCH-3, SRC-RULE-DATA-1, SRC-RULE-EFFORT-5,
SRC-RULE-ORDER-0, T037, T01-001, T02-024, T02-027, T03-005,
T03-007..T03-008, T11-002..T11-003, T11-007, T11-013, T11-019..T11-020,
T11-022, T11-025, T11-032, T11-038, T11-044, T11-046, T11-048..T11-049,
T12-003..T12-004, T12-008, T14-010..T14-015, T14-017..T14-018, T14-020,
T18-003, T18-009, T24-022, T25-001..T25-003, T25-006, T25-012..T25-030,
T25-034..T25-038, T25-042

### 4. cli-work-model

Exports: a task set carrying every scheduling input the planner needs, meaning
projects, dependencies with derived blocking and a cycle refusal carrying its
path, estimates with derived remaining, semantic priority, the sealed deadline
pair, source metadata, and cancel and archive.

Must not change: the record fields it adds are store-level and go through a
migration; the surfaces it adds go inside the seven areas.

Requirement ranges: SRC-2.1.2..SRC-2.1.4, SRC-2.1.6, SRC-2.1.7, SRC-2.1.18,
SRC-4.3..SRC-4.7, SRC-RULE-PRIO-1, SRC-RULE-STATE-3..SRC-RULE-STATE-8,
SRC-RULE-EFF-1..SRC-RULE-EFF-3, SRC-8.8.3, SRC-8.8.4, SRC-8.8.6, T11-023,
T12-026, T12-030, T12-034..T12-037, T13-032..T13-036, T14-001..T14-005,
T14-009, T14-022, T001, T17-005, T17-014, T25-004, MAP-042, MAP-043, MAP-051,
MAP-055, MAP-056, CTX-estimate, CTX-remaining-effort

### 5. cli-planning

Exports: the primary output. The timetable, the full session, the month's target
and balance with reconciliation, the ordered today list with per-item reasons,
the fit signal and the cut line, behind `ForPlanningQueries` and
`ForDefiningCapacity`.

Must not change: `ForPlanning` takes data and never a store, and the planner
stays a replaceable infrastructure port with the built-in engine as its default.

Requirement ranges: CTX-balance, CTX-break-reserve, CTX-completion-slot,
CTX-flex, CTX-leave-time, CTX-planned-start, CTX-reconciliation, CTX-session,
CTX-stop-time, CTX-target, CTX-timetable, CTX-vacation, MAP-010, MAP-020,
MAP-035..MAP-040, MAP-044, MAP-048, SRC-2.1.5, SRC-2.1.13, SRC-4.9, SRC-4.14,
SRC-8.8.2, SRC-8.8.7, SRC-8.8.9, SRC-RULE-ALLOC-3..SRC-RULE-ALLOC-4,
SRC-RULE-ALLOC-6, SRC-RULE-CAP-5, SRC-RULE-CAP-7,
SRC-RULE-CLOS-1..SRC-RULE-CLOS-5, SRC-RULE-DET-1..SRC-RULE-DET-2,
SRC-RULE-EFFORT-3..SRC-RULE-EFFORT-4, SRC-RULE-ELIG-1..SRC-RULE-ELIG-3,
SRC-RULE-ORDER-1..SRC-RULE-ORDER-2, SRC-RULE-ORDER-4..SRC-RULE-ORDER-5,
SRC-RULE-REASON-1..SRC-RULE-REASON-3,
SRC-RULE-RECALC-1..SRC-RULE-RECALC-3, T02-008..T02-009, T02-011, T02-013,
T02-015, T08-002..T08-007, T08-009..T08-014, T11-024, T11-027,
T11-033..T11-036, T13-001..T13-030, T13-038..T13-041, T13-053..T13-054,
T13-056, T13-065, T14-006..T14-007, T14-019, T15-012..T15-015,
T17-003..T17-004, T17-013, T17-039, T24-035, T25-005, T25-008

### 6. cli-forecast

Exports: forecasts with effective deadline, projected finish, slack and risk;
the ten notice types with their levels and lifetimes; OS delivery; and the
confirmation handshake with its delivery ladder, which every later surface
reuses rather than reimplementing.

Must not change: the notice model, the risk vocabulary, and the handshake. The
API and the agent surface reuse all three.

Requirement ranges: SRC-2.1.14, SRC-2.1.15, SRC-4.10, SRC-4.11, SRC-4.26,
SRC-4.28, SRC-RULE-EFFORT-2, SRC-RULE-SLACK-1..SRC-RULE-RISK-4,
SRC-RULE-FCAST-1..SRC-RULE-FCAST-6, SRC-RULE-WARN-2..SRC-RULE-NOTIF-4,
SRC-RULE-PRED-1..SRC-RULE-OVERRIDE-6, SRC-8.8.5, SRC-8.8.8, SRC-8.8.10,
SRC-8.8.11, T11-014, T13-042, T13-045..T13-049, T13-055, T14-008,
T15-006..T15-011, T15-016..T15-033, T15-038..T15-041, T002, T003, T004, T013,
T016, T024, T025, T026, T029, T030, T20-022, T25-007, T25-011,
T25-031..T25-033, MAP-047, MAP-049, MAP-050, MAP-053, CTX-notice,
CTX-forecast..CTX-risk

### 7. cli-review

Exports: review projections over past days and months, the export archive, the
backup path, the purge age, the timezone, and settings. With this effort the
command line meets the core product scope, which is the gate for opening the
agent stage.

Must not change: export format versioning. The format is published, so a later
change adds a version rather than editing one.

Requirement ranges: SRC-2.1.24, SRC-4.16, SRC-4.23, SRC-RULE-DATA-2,
SRC-8.8.16, T11-028, T11-056, T12-016, T13-050, T13-051, T034, T17-006,
T17-007, T17-009, T17-011, T17-012, T17-016..T17-027, T25-009, MAP-057,
MAP-058, CTX-presence, CTX-export..CTX-purge

### 8. mcp-agent-interface

Exports: the `mcp` module, one flat tool per operation with strict schemas, the
stable failure codes, the confirmation handshake over the tool surface, the read
with a cursor, and `mcp serve` owning standard input and output.

Must not change: nothing writes to standard output in serve mode, and the tool
set mirrors the command areas rather than designing anew.

Requirement ranges: CTX-change-feed, CTX-cursor, CTX-pending-operation,
MAP-021, MAP-052, MAP-054, SRC-2.1.21, SRC-4.18..SRC-4.20, SRC-8.8.13,
SRC-RULE-ACTIVITY-1..SRC-RULE-ACTIVITY-3, T005..T012, T014..T015, T017..T020,
T035, T02-032, T09-001..T09-002, T09-005..T09-015, T11-074..T11-077, T11-082,
T11-084, T13-064, T17-040, T18-023, T20-021, T25-010

### 9. daemon-api

Exports: the `api` module, the Spring Boot listener as a driving adapter, the
versioned OpenAPI contract by record kind, the event stream with resumption, the
remote proxy and its single stream reader, the mode detection, the token rule,
and graceful stop with takeover.

Must not change: the remote proxy's promise, which is that a surface cannot tell
which mode it is in, and the one-owner rule.

Requirement ranges: CTX-daemon, CTX-event-stream, CTX-split-mode, MAP-024,
MAP-030, MAP-060..MAP-067, SRC-2.1.23, SRC-4.25, SRC-4.27, SRC-8.8.15,
SRC-RULE-ARCH-4, T031, T03-011, T11-039..T11-042, T11-045, T11-047,
T11-058..T11-063, T11-065, T11-073, T11-078, T18-001..T18-002,
T18-004..T18-008, T18-010..T18-011, T18-013..T18-018, T18-022, T19-003,
T19-014..T19-021, T20-001..T20-002, T20-004..T20-015, T20-017..T20-018,
T24-015

### 10. client-shell

Exports: JavaFX and Spring in one JVM with the measured shutdown order,
navigation, and the Today view with the fit signal, the reasons, and the cut
line, driven by the same ports as the command line.

Must not change: the shutdown order, and the rule that the shell holds no work
logic of its own.

Requirement ranges: SRC-2.1.19, SRC-RULE-ARCH-5, T04-012, T07-001..T07-003,
T07-005..T07-007, T11-030, T11-031, T11-085..T11-087, T18-012, T22-001,
T22-007, T22-010, MAP-018, MAP-069, MAP-C13, CTX-client

### 11. client-views

Exports: the Tasks, Projects, Review, and Settings views, the two editing doors
onto the estimate, and the dependency editor with its cycle message.

Must not change: the shell's navigation contract and the views' source of truth,
which is the ports rather than local state.

Requirement ranges: T14-021, T036

### 12. tray-surface

Exports: the tray icon, the popup with the current timer, the next item and the
warnings that ask something of the user, the native actions, and degradation to
the window, plus the real desktop test record across GNOME, KDE Plasma, macOS,
and Windows.

Must not change: the guard. `Desktop.isDesktopSupported() &&
SystemTray.isSupported()` comes first, always.

Requirement ranges: SRC-2.1.20, SRC-2.1.25, SRC-4.24, SRC-8.8.12, SRC-8.8.14,
T06-006, T06-007, T06-009..T06-011, T06-014..T06-016, T11-089, T22-011,
T22-013, T23-009, MAP-017

## Seams

Where a test, a tool, or a later effort meets the code, and what it may rely on.

- **The ports are the seam.** A test drives a surface port and asserts the
  result. It does not reach behind the port to inspect a field, and it does not
  assert on the shape of an internal call.
- **The store is a seam.** `adapters` is tested against a real SQLite file,
  because WAL, the lock, migrations, and the change log are the behaviour.
- **The clock is a seam.** `ForReadingClock` is injected, so a test can place the
  product at any instant without touching the system clock.
- **The notifier is a seam.** `ForNotifying` is injected, so a test can assert
  that a delivery happened without an operating system.
- **The planner is a seam.** `ForPlanning` takes `PlanningInput`, `Instant`, and
  `Session`, so the ordering engine is testable as a pure function and
  replaceable at the same boundary.
- **The command line is a seam.** A test invokes a command and asserts output,
  exit code, and the pending operation when no terminal is present.
- **The tool surface is a seam.** A test speaks the protocol over standard input
  and output, which also proves that nothing else writes to standard output.
- **The API is a seam.** A test consumes the OpenAPI document, so a change to
  the wire is visible as a contract change.

## Do not reimplement

The parallel versions nobody should invent, each with the thing to use instead:

- A second scheduling algorithm beside `ForPlanning`. The planner is one
  replaceable port; a different engine replaces it, and does not sit beside it.
- A second clock or a direct `Instant.now()` in a use case. Use
  `ForReadingClock`.
- A second change feed, or a per-entity "what changed" endpoint. Use the one
  cursor.
- A second confirmation mechanism, or a permission check that bypasses the
  pending operation. Use the handshake.
- A second notice model, a severity named `critical`, or an OS notification that
  is the only record of a condition. Use the notice list.
- A second identifier display form, or a surface that formats identifiers its
  own way. Use the one formatter in `contract`.
- A second store, a second writer, or a transaction that spans two port calls.
  The transaction boundary is one call.
- A second configuration reader, or a surface that reads settings from a file.
  The daemon owns business settings; the process configuration has one source.
- A second modal for "dangerous action". Removal, override, and out-of-order
  completion all use the one handshake.
- A second export format, or an import. v1 exports and restores by backup.

## How this document changes

Through the architecture writer only. A child effort that needs a design change
records it here, in the same write, so the next child reads the current design
rather than a stale one. The specification's architecture content is archival
from the moment this document exists, and nothing is deleted from the
specification.

**Definition of done for a child effort.** The requirement ranges under each
contract above are that effort's obligation, not a summary of it. An effort is
not allowed to close until every row in its ranges has been checked one by one:
the behaviour exists, a test drives it, or the row is recorded as not applicable
with a reason. The check is row by row because a range can hide one unmet
obligation behind thirty met ones, and because the rows are the only statement
of what the effort was for. Each child's map carries the same list and the same
rule under its `## Requirements` section, so the obligation travels with the
effort rather than living only in this document.

## Effort order

The twelve child efforts, in the order they must be built. `blocked_by` names
the effort whose hand-off the next one needs, so position alone is never the
rule: read the edge.

The slug carries the position, so the order is visible in a directory listing
and in a command, without opening this document. The number in the slug is
permanent: an effort inserted between two of these later takes a suffixed slug
rather than renumbering the ones after it.

| # | Effort slug | Section in the map | Blocked by | Release | Kept requirements |
|---|---|---|---|---|---|
| 1 | `01-scaffolding` | 1 | none | 0.1 | 73 |
| 2 | `02-work-store` | 2 | `01-scaffolding` | 0.1 | 61 |
| 3 | `03-cli-basics` | 3 | `02-work-store` | 0.1 | 75 |
| 4 | `04-cli-work-model` | 4 | `03-cli-basics` | 0.2 | 53 |
| 5 | `05-cli-planning` | 5 | `04-cli-work-model` | 0.3 | 129 |
| 6 | `06-cli-forecast` | 6 | `05-cli-planning` | 0.4 | 94 |
| 7 | `07-cli-review` | 7 | `06-cli-forecast` | 0.5 | 34 |
| 8 | `08-mcp-agent-interface` | 8 | `07-cli-review` | 0.6 | 53 |
| 9 | `09-daemon-api` | 9 | `08-mcp-agent-interface` | 0.7 | 73 |
| 10 | `10-client-shell` | 10 | `09-daemon-api` | 0.8 | 22 |
| 11 | `11-client-views` | 11 | `10-client-shell` | 0.8 | 2 |
| 12 | `12-tray-surface` | 12 | `11-client-views` | 0.9 | 18 |

Four facts this table carries, so no reader has to reconstruct them:

- **The graph is one chain**, not a tree. Every effort has exactly one
  predecessor and at most one successor, so no two efforts are siblings that
  could proceed in parallel. That is a property of this design, not a rule; if a
  later change makes two efforts independent, the table says so and the numbering
  stops implying order.
- **Release does not order efforts.** 0.1 holds three, 0.8 holds two. The
  release is what a user can do with the result; the chain is what the code
  needs.
- **`blocked_by` here means hand-off, not a ticket edge.** Child efforts are
  linked by `origin_effort` on their own maps, never by `blocked_by` between
  efforts. This table is documentation of the build order, and the map's
  `## Child efforts` section remains the authoritative source for the same
  edges.
- **The map and the closing ticket use the bare names.** They are the planning
  record as it was written, and they are not rewritten. `3. cli-basics` in the
  map and `03-cli-basics` here are the same effort, and the `## Section in the
  map` column is the translation.

Each child's map must carry its own row when it is created: slug, position,
predecessor, release, and the requirement ranges from its contract below. That
is what lets an agent start on one child with no access to this effort's
conversation, and it is why the order is recorded here rather than assumed.
