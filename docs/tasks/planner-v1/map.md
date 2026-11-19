---
type: map
title: "Planner v1: decompose the specification, own architecture and product decisions"
status: stable
---
# Planner v1: decompose the specification, own architecture and product decisions

## Destination

Planner v1 is turned into a set of live child efforts, each with a settled
scope, entry criteria, a position in the pre-v1 build order, and a release.
Every cross-cutting architecture and product decision the children depend on is
settled here and recorded in this effort's architecture document. The
specification stays the reference text, inside this effort's directory; the
deviations this effort settles are recorded on this map and in the
architecture.

## Constraints

- Full map before implementation: the child-effort list, their order, their
  scopes, their entry criteria, and their releases are settled here before any
  child effort starts.
- This effort owns architecture and product decisions. Child efforts own code.
- Throwaway prototypes and spikes are allowed when a cheap artifact settles a
  question faster than prose. Nothing mergeable is produced.
- Java 21 or newer. The build tool, Java version, and test stack are settled
  here through the stack research task.
- The pre-v1 build order is the command line interface, then MCP, then the
  daemon, then the desktop client, then the tray. The backend grows to its
  final v1 stage inside the CLI stage, which spans several versions. Settled by
  `version-order`.
- The CLI stage may span several child efforts, so an effort is scoped to a
  capability group rather than to "the CLI".
- Versions are additive: a version keeps every interface an earlier version
  shipped, and each version is tagged before the next one opens.
- Every user-facing or external-facing interface works with the backend in the
  same process or by talking to the daemon.
- Child efforts are separate efforts linked by `origin_effort` on the child's
  map. They are never tickets and never `blocked_by` edges.
- Close route: to-spec, then to-tickets for the architecture document and one
  formality ticket, then finalize on branch `effort/planner-v1`.
- Writes under `docs/tasks/` go through the `tw_*` tools only.
- Dependency edges are fixed at task creation, so a late ordering constraint is
  recorded in the affected task's notes. The affected tasks are
  `child-efforts`, `decisions-service-mode`, and `decisions-agent-interface`.

## Non-goals

- No product code, no build files, no CI configuration, no module skeleton.
  The scaffolding child effort owns all of it.
- No implementation of any v1 capability, and no mergeable change to a module.
- No v2 advisory work: tracker plugins and attachments (specification
  section 7), daily routine tasks and a server-hosted daemon (section 10).
- No calendar synchronization, billing, team or resource planning, kanban,
  chat, plugin marketplace, or autonomous AI scheduling, matching
  specification sections 1.4 and 2.2.
- No release, packaging, installer, or distribution work.
- No bug triage of implemented code: none exists yet.
- No split working days: a weekday carries one window, so a future day cannot
  hold a morning block and an evening block. Decided by
  `session-and-timetable`.
- No task time as the month's accounting quantity: presence is what the account
  measures, which keeps untracked work from becoming debt. Decided by
  `session-and-timetable`.

## Non-negotiable facts

**Success test:** every child effort for planner v1 exists as a live effort
under `docs/tasks/` with its own map, scope, entry criteria, release, and
`origin_effort: planner-v1`, and every cross-cutting architecture and product
decision a child would otherwise have to settle is recorded in
`docs/tasks/planner-v1/architecture.md` or in an ADR.

- The product name is workthread everywhere. "Itinerary Planner" is only the
  title of the moved specification document.
- The reference text is `docs/tasks/planner-v1/initial_spec.md`, moved
  verbatim and not edited. Where the decisions on this map depart from it, this
  map and the architecture document are authoritative.
- Java 21 or newer. The build tool, Java version, and test stack are settled
  by this effort through the stack research task and recorded in the
  architecture document.
- Shapes: a core module holding the work logic, a command line interface, an
  MCP interface, a Spring Boot API module, a JavaFX client, and a tray.
- Work logic belongs to the core. No other module holds it, so standalone mode
  needs no fork of it.
- Every user-facing or external-facing interface works with the backend in the
  same process or by talking to the daemon, and all of them share one service
  authority (RULE-ARCH-1 to RULE-ARCH-6).
- The command line interface is a first-class v1 surface, shipped beside the
  desktop client and the tray, and it hosts MCP over stdio.
- The pre-v1 sequence is the CLI stage, then MCP, then the daemon, then the
  desktop client, then the tray, and the backend grows to its final v1 stage
  inside the CLI stage. Settled by `version-order`.
- Every version ends with a version number, works on real tasks, and keeps
  every interface an earlier version shipped. No version removes a working
  command or surface. Settled by `version-order`.
- Capacity comes from a timetable of hours per weekday plus time off recorded
  ahead or retroactively. Today's capacity is the session: from now until the
  planned leave time, minus the planned break minutes.
- The primary output is the ordered today list with deadline forecasts. v1 has
  no multi-day allocation, no clock-time blocks, and no task split across days.
- The forecast horizon is two weeks at most, and it bounds deadline risk only.
- Every unresolved specification decision (DEC-1 to DEC-16) is settled in this
  effort before decomposition, so no child effort re-litigates one.
- The client is delivered as a `jlink` image, not a fat jar, and JavaFX stays
  on the module path. The build must enforce that, because the runtime only
  warns when JavaFX is on the classpath. The image still needs the host's GTK3
  and X11 libraries.
- The MCP server runs only in a subcommand that owns standard input and output,
  and no module writes to standard output in that mode. A logging default that
  points at standard output breaks the protocol.
- Child efforts are separate efforts under `docs/tasks/`, recorded in this
  map's `## Child efforts` section with scope, dependency position, entry
  criteria, and release, and linked by `origin_effort` on the child's map. They
  are never tickets and never `blocked_by` edges.
- The architecture document at `docs/tasks/planner-v1/architecture.md` is the
  design record child efforts copy from. It archives with this effort.
- The single implementation ticket is a formality. It carries no
  implementation and is marked `mode: human`, so the autonomous chain never
  runs for it.

## Decisions so far

- The product is called workthread everywhere. "Itinerary Planner" survives
  only as the title of the moved specification. Decided by the
  non-negotiables task.
- The success test, the non-goals, and the route to the close are recorded in
  this map from the `non-negotiables` task: to-spec, then to-tickets for the
  architecture document and exactly one formality ticket, then finalize on
  branch `effort/planner-v1`.
- The single implementation ticket is a formality marking: it carries no
  implementation, and `mode: human` keeps the autonomous chain off it.
  Decided by the `non-negotiables` task.
- One architecture document at `docs/tasks/planner-v1/architecture.md` is the
  design record, written by to-tickets and reconciled at the close. Decided by
  the `non-negotiables` task.
- The 16 unresolved specification decisions are settled here in five themed
  grillings, one grilling per pass, before decomposition. Decided by the
  `non-negotiables` task.
- Stack research on the Java side runs before the service-mode decision and
  the decomposition. Decided by the `non-negotiables` task.
- The build tool, Java version, and test harness are settled by this effort,
  not by the scaffolding child. Decided by the `non-negotiables` task.
- An open-ended grilling task runs first, so the human can add requirements on
  top of the specification before any other planning task starts. Decided by
  the `non-negotiables` task.
- The task record takes its inspiration from Taskwarrior's model, compared
  field by field against the specification's model. The research and the
  comparison are charted, and the comparison decides what is adopted. Decided
  by `spec-additions`.
- The day-by-day schedule leaves v1. A timetable of hours per weekday plus
  time off, and a daily session with a planned leave time and break minutes,
  replace it. The interface shows an ordered today list with a fit signal.
  Decided by `spec-additions`.
- Deadline pressure stays at its simplest: ordering still protects deadlines,
  with a forecast horizon of two weeks at most. Decided by `spec-additions`.
- The pre-v1 order is the CLI stage, then MCP, then the daemon, then the
  desktop client, then the tray, with the backend grown to its final v1 stage
  inside the CLI stage. A general architecture grilling runs after the order
  is settled, and the two existing service and agent grillings run after the
  architecture. A separate release plan runs after the architecture and the
  child-effort list. Decided by `spec-additions` and amended by
  `version-order`.
- The command line interface is a first-class v1 surface that hosts MCP over
  stdio, and every interface works with the backend in process or through the
  daemon. Decided by `spec-additions`.
- The stack is evidenced, not guessed, by the `stack-research` task: Java 21,
  Gradle 9.9.1, the MCP Java SDK 2.1.0 over stdio, SQLite through sqlite-jdbc
  in WAL mode with one writer, JUnit 5 and AssertJ, and a JavaFX image where
  JavaFX stays on the module path because classpath loading is unsupported.
  The architecture task confirms and records it; the evidence is
  `docs/research/stack.md`.
- Taskwarrior's task model is documented field by field by the
  `taskwarrior-model-research` task: a flat extensible key/value record, four
  stored statuses with `waiting` derived, one-key-per-member sets, an operation
  log for history, a computed urgency that is never stored, and deliberately no
  estimate, no time tracking, and no capacity. The comparison task decides what
  workthread adopts; the evidence is `docs/research/taskwarrior-model.md`.
- The client ships as a `jlink` image built by the `org.beryx.jlink` plugin
  with `--strip-debug` and `--compress zip-6`, at 64 MB. Two routes were
  measured against it and both worked; the plugin won on least build code.
  The image still needs the host's GTK3 and X11 libraries. Chosen from the
  `prototype-tray-packaging` evidence.
- The tray is an AWT-backed JavaFX tray, `FXTrayIcon`, guarded by
  `SystemTray.isSupported()` so a missing tray degrades to the window instead
  of ending the application. Every JavaFX tray library wraps AWT, and
  `javafx.graphics` already requires `java.desktop`, so a no-tray image saves
  nothing. A real desktop test is an acceptance item for the tray child
  effort. Decided by `prototype-tray-packaging`.
- One JVM can host Spring Boot and JavaFX, with Spring owning `main`, starting
  the context, then `Platform.startup`, and an explicit shutdown order:
  `Platform.exit()` on the FX thread, a latch, then `context.close()` on the
  main thread. Measured at 7 ms to quit. The context does not close on
  `Platform.exit()` by itself. Decided by `prototype-javafx-spring-jvm`.
- Classpath JavaFX warns and then works when the main class does not extend
  `Application`, and a Spring Boot fat jar makes that the default
  configuration. The module-path rule is therefore enforced by the build, not
  by the runtime. Decided by `prototype-javafx-spring-jvm`.
- Ordering is deadline first, dependency pressure second, and priority only as
  a tie-break, and a prerequisite inherits the earliest effective deadline of
  the tasks it unblocks. Without the inheritance every rule misses a deadline
  in one adapted worked example by 30 minutes. Decided by
  `prototype-ordering-engine`.
- The MCP server runs only in a dedicated `mcp serve` subcommand that owns
  standard input and output, and nothing may write to standard output in that
  mode. A logging backend defaulting to standard output broke the protocol in
  the prototype. Decided by `prototype-cli-mcp-stdio`.
- The first two stages of the earlier order collapse into one CLI stage made of
  several versions. It runs until the core product is usable from the CLI, and
  the MCP stage opens only after that. The first version carries capture,
  list and inspect, the timer, logged time, and complete, with a fixed sort of
  deadline then priority. Decided by `version-order`.
- The CLI stage's versions follow capability areas: capture, list, timer and
  complete; then projects, dependencies, estimates, priority and deadlines;
  then timetable, session and the ordered today list; then forecasts, warnings
  and overrides; then review, export and settings. The grouping is an ordering
  constraint, and `release-plan` names the releases and places the child
  efforts. Decided by `version-order`.
- The Spring Boot API module arrives with the daemon stage and not earlier, so
  the core stays framework-free and the build can enforce it. v1 is the
  release after the tray stage, and it meets the completion signals in
  specification section 8.8. Decided by `version-order`.
- The core is hexagonal, with two port families: surface ports, which the core
  implements and a surface calls, and infrastructure ports, which the core
  calls and an adapter implements. Both use the `For...` name and the side is
  carried by the package, `port.surface` and `port.infrastructure`, never by
  the identifier. A surface is any driving adapter, the daemon's API listener
  included. Decided by `architecture`.
- Seven surface ports carry the use cases by area: `ForCapturingTasks`,
  `ForManagingProjects`, `ForPlanningQueries`, `ForTrackingTime`,
  `ForOverridingRules`, `ForDefiningCapacity`, and `ForConfiguring`. The
  override port carries the confirmation channel, and capacity is separate
  from configuration because changing capacity recomputes the plan. A
  surface's own preferences are its own file and never a port. Decided by
  `architecture`.
- The core's infrastructure ports are `ForPlanning`, `ForStoringWork`,
  `ForReadingClock`, and `ForNotifying`, with `ForStoringAttachments` and
  `ForRememberingSurfacePreferences` reserved. One port call is one
  transaction, the change log is written inside it, and `snapshotFor(asOf)`
  gives the planner one consistent input. The planner takes data, never a
  store. Decided by `architecture`.
- One configurator carries the in-process-or-daemon rule, from an explicit
  startup configuration with three shapes: standalone, daemon, and split
  surface. `serveMcp` is the only optional surface in a process. Only one
  process may own the data, and a second one fails with a clear message, so
  sharing a backend across processes requires the daemon. Decided by
  `architecture`.
- The build is eight modules: `contract`, `core`, `adapters`, `wiring`, `api`,
  `mcp`, `cli`, and `client`, with the core seeing only `contract`.
  Persistence is SQLite in WAL, with an ownership lock in the platform runtime
  directory, ordered SQL migrations checked against a version and checksums,
  and `VACUUM INTO` for an export. A loopback bind needs no token, a
  non-loopback bind needs a token and a fixed port, and no credential is
  written to a shared file. Decided by `architecture`.
- The two run modes are defined by where the work logic runs. The full design
  record is `docs/tasks/planner-v1/architecture.md`, written by to-tickets from
  this task. Post-v1 work attaches at named points: a replacement planner or
  store through its port, attachments through a reserved port, tracker plugins
  as a jar with a manifest and a configuration section of their own, a network
  daemon at the bind address, and a fifth surface as a new module. None of it
  is built in v1. Decided by `architecture`.
- The task record is reconciled against Taskwarrior's field by field: the
  identifier is a monotonic integer that is opaque by rule and displayed as
  `TSK-0001` or `PRJ-0001`, the title stays required, and §3.2's optional
  description is replaced by timestamped, append-only notes. Tags,
  user-defined attributes, urgency, the `wait`, `scheduled`, and `until`
  dates, and recurrence are refused, each with its reason. Decided by
  `task-model-comparison`.
- The identifier's guarantee rests on three written constraints:
  `AUTOINCREMENT` so a value is never reused, the data owner as the only
  allocator, and nothing ever renumbering, with a test that a deleted id is
  never reused. Decided by `task-model-comparison`.
- History lives in the operation log and not in the task record, and
  requirements are rules in the core rather than constraints in the store,
  with identity, status, and the timestamps the one exception. Decided by
  `task-model-comparison`.
- Date gating, as a single "not before" date, and recurrence are the two named
  changes immediately after v1, in that order. Every other refusal from this
  comparison returns without a schedule. Decided by `task-model-comparison`.
- A weekday carries only its net work minutes, one window, with no clock
  intervals and no pause budget. One configured planned start supplies the
  clock for future days and is replaced by the observed start once the day
  begins, so arriving later means leaving later. Decided by
  `session-and-timetable`.
- A session is the record for one date: its start, its leave time, and its
  break reserve. The leave is derived from the day's minutes and can be moved
  at any moment, which is how "stay later today" and "I must leave earlier"
  are said. The session takes the place of §5.17's capacity override, so two
  rules stay overridable. An unended day gets an inferred end and a persistent
  notice. Decided by `session-and-timetable`.
- Time off is one record per date with a minute count and a kind: `vacation`
  reduces the month's target as well as the day's capacity, `flex` changes only
  the day's capacity in either direction so the difference is compensated
  elsewhere, and zero means a day away. Decided by `session-and-timetable`.
- The month is the account. The target is the month's weekday minutes minus
  vacation, and worked is session presence minus the reserve, deliberately not
  task time, because untracked presence is not a deficit. The balance runs
  inside the month under four settings in two bands: a permitted shortfall and
  surplus that clamp the month's close, and a target shortfall and surplus that
  the plan reconciles to, all defaulting to zero. Decided by
  `session-and-timetable`.
- Reconciliation settles the balance in thirty-minute increments over the days
  after today that have work, in date order, cycling while unsettled, never
  below zero, and never touching a day that carries its own minutes. That last
  is an invariant binding every future projection algorithm, and the algorithm
  is a configuration point with one implementation in v1. Decided by
  `session-and-timetable`.
- Concrete allocation covers fourteen days. Projections continue past it,
  marked as approximations and rough beyond four weeks, with
  `horizon_over_capacity` as the signal that the tool is being used beyond its
  design. The today list is bounded by the day's capacity, is not re-planned as
  time passes, and shows a cut line where the plan passes the leave time.
  Decided by `session-and-timetable`.
- One `notice` concept carries everything the system says: a persistence flag,
  a level of `info` or `warning`, and an OS delivery flag, with the list and
  the dismissal on `ForPlanningQueries`. There are nine notice types and the
  specification's `critical` severity retires. The review compares the month
  day by day on presence, not on task time. Decided by
  `session-and-timetable`.
- A deadline is a date or an instant, a sealed pair: a date means before that
  local day ends, an instant is accepted only for the current local day, and
  every effective-deadline comparison reads a date as the end of its day.
  Decided by `session-and-timetable`.
- The estimate is total effort in one stored field, with remaining effort
  derived as the total minus logged minutes and allowed to go negative. The
  editing surfaces offer two doors into that one field: set the total, or set
  the remaining, which writes the logged time plus the new remaining. A ready
  task keeps a positive estimate, so clearing it is refused rather than
  silently moving the task to the inbox. Decided by `decisions-estimates-time`.
- A task never claims negative effort. An unfinished task at or below zero
  remaining effort receives a five-minute completion slot, but while a timer
  runs the running block is the claim instead, so an overrun keeps growing and
  pushes the rest of the day later until it is stopped. Any overrun raises a
  persistent `estimate_overrun` notice, the application never rewrites the
  estimate, and completion stays explicit. Decided by
  `decisions-estimates-time`.
- Pausing keeps the task in progress and starting another returns it to ready.
  Concurrent tasks are allowed behind a configuration setting that ships off,
  and with it on a second concurrent start still requires explicit intent from
  the caller. The single active task becomes a set. Decided by
  `decisions-estimates-time`.
- A task timer cannot start unless the day has been started: the interfaces
  fail with an error naming that, and the client offers to start the session in
  place. Ending the day stops every open timer at its stop time. Decided by
  `decisions-estimates-time`.
- A deliverable is missed when its slack is negative or its deadline has passed
  with work remaining, whether or not a finish could be placed. Unscheduled
  covers only remaining work that cannot be placed for a reason other than
  deadline capacity, and blocked stays tied to the dependency graph, so a
  task's own ineligibility is unscheduled. Decided by
  `decisions-forecast-warnings`.
- Itinerary reasons are six values: the specification's four plus
  `earlier_deadline` and `stable_order`. Decided by
  `decisions-forecast-warnings`.
- A tenth notice type, `deadline_approaching`, is raised while an effective
  deadline falls within a configurable lead window that defaults to
  twenty-four hours and work remains. Levels and OS delivery are settings with
  defaults rather than constants, persistence is not, and a persistent notice
  may be dismissed and returns if its condition recurs. Decided by
  `decisions-forecast-warnings`.
- Slack is measured against the day capacities, which include the monthly
  reconciliation and exclude vacation and zero-length flex days, and against
  projections past the horizon, labelled rough beyond four weeks. Decided by
  `decisions-forecast-warnings`.
- A dependency change that would close a loop is refused and leaves an
  ephemeral notice naming the tasks in the loop; it is not a persisting
  condition. The two notice lifetimes are persistent and ephemeral. Decided by
  `decisions-agent-interface`.
- The agent surface is a flat set of one tool per operation, named snake case
  and prefixed by its noun, each with a strict schema. Identifiers are accepted
  as the bare integer or as the prefixed display form, with no fuzzy matching,
  and every failure returns a stable code, a message, and the offending entity
  or field. Decided by `decisions-agent-interface`.
- Confirmation is a two-step handshake: the call records a pending operation
  holding its own intent and returns its id with a requirement, a two-digit
  code reaches the human through the strongest available channel, and the agent
  confirms with the id and the code, with three attempts and a five-minute
  expiry. Decided by `decisions-agent-interface`.
- Every read of changing state returns the whole current state plus the changes
  since an optional opaque cursor, grouped per entity with a header against
  reconstructing state, and one general change feed does the same without an
  entity. Every change records the surface it came from and whether a human or
  an agent drove it. Decided by `decisions-agent-interface`.
- Nothing is deleted on request: a task is cancelled and a project archived.
  A configurable purge age, off by default, permanently deletes cancelled and
  archived records older than that and says that purging a project takes its
  tasks with it. Cancelling and archiving record the moment they happened, and
  the purge reads it. Decided by `decisions-data-lifecycle`.
- Removal asks for confirmation when it changes something already reported, so
  a past per-date entry, a closed time entry, or a note, and never for
  plan-only removals. Decided by `decisions-data-lifecycle`.
- The review recomputes each past day's target and presence from current
  configuration, stores no past itinerary, and says plainly that it measures
  the days against the hours expected now. Reconstructing the timetable from
  the operation log is the named upgrade. Decided by
  `decisions-data-lifecycle`.
- The export is a zip holding one documented JSON document with a format
  version, engine-independent and carrying identifiers and the sequence state,
  plus an attachments directory when attachments arrive. A backup is the
  database file itself, and the export excludes the operation log. Decided by
  `decisions-data-lifecycle`.
- Dismissed ephemeral notices, finished or expired pending operations, and the
  dismissal record of a resolved persistent notice are deleted at once; the
  operation log is kept forever in v1. Each migration runs in one transaction
  with its version row, the application refuses a database newer than it knows,
  and a pre-migration copy is kept beside the database. Decided by
  `decisions-data-lifecycle`.

## Fog


