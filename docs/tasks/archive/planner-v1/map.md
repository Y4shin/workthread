---
type: map
title: "Planner v1: decompose the specification, own architecture and product decisions"
status: deprecated
ready_for_spec: true
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
  formality ticket, then finalize on branch `effort/planner-v1`. `to-spec`
  writes the specification by walking `docs/v1/requirements-directory.csv`,
  runs the coverage check and the adversarial read, and does not publish until
  both pass, per `spec-traceability`.
- Writes under `docs/tasks/` go through the `tw_*` tools only.
- Dependency edges are fixed at task creation, so a late ordering constraint is
  recorded in the affected task's notes. The affected tasks are
  `child-efforts`, `decisions-service-mode`, `decisions-agent-interface`,
  `ui-framework-prototype`, and `requirements-directory`.
- The desktop client's toolkit is settled by `ui-framework-decision`: JavaFX
  ships, the client stays a Java module, and the work logic stays in Java. The
  JavaFX-dependent decisions on this map are therefore confirmed and not
  provisional. Accepted risks: Java 21 caps the usable JavaFX line at 23.0.2,
  and the Linux system tray is the weakest part of the choice, which the tray
  child effort tests on a real desktop.
- The command line interface follows the convention settled by
  `cli-surface-convention`, which the architecture document carries in full.
  Commands are noun first, `workthread <area> <action>`, over seven areas that
  map one to one onto the surface ports, plus `mcp serve` and `confirm`. One
  command is one port operation. Output is text, with one global
  `--format json` printing one document per invocation; errors go to standard
  error with the stable failure code. Exit codes are `0` success, `1` reserved
  for an unknown or uncategorised error, `2` usage, `3` validation refused, `4`
  rule refused including an unavailable confirmation, `5` service unreachable.
  Only a confirmed removal or an override may prompt, and only on a terminal;
  without one the command returns a pending operation and exits `4`, completed
  by `workthread confirm <id> <code>`. Shell completion is v2. Every later CLI
  child names its commands inside these areas, and the additive-version rule
  covers commands, flags, JSON shapes, and exit codes alike.

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
- No WebSockets in v1, and no second description format: the live channel is
  server-sent events inside the one OpenAPI document. Decided by
  `live-api-choice`.
- No pushed projections: the stream carries the current cursor and notice
  events only, and every list, forecast, and review is read over REST. Decided
  by `live-api-choice`.

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
- The run mode is detected rather than chosen: `workthread` attaches to a
  running daemon and otherwise runs the work logic in its own process, and the
  daemon is a subcommand meant to be started by a service manager. A daemon
  lost mid-run fails the call and offers an explicit takeover, never a silent
  mode switch. Decided by `decisions-service-mode`.
- A graceful stop records its instant and leaves timers and the session open,
  and the next start presents anything opened before that instant for
  correction, defaulting the end to the recorded stop; a crash takes the same
  route. Decided by `decisions-service-mode`.
- Configuration has an owner and no file is shared: the daemon's process
  settings live in a file or the environment, a surface's own settings in its
  own configuration, and every business setting in the daemon's data, reachable
  only through its API. Decided by `decisions-service-mode`.
- The API is a versioned, OpenAPI-documented REST contract organised by record
  kind, used by the application's own surfaces in split mode and by any
  external consumer, with every operation expressed as a resource or an action
  on one and no RPC-shaped endpoints. Whether it carries a live push channel,
  and whether that is server-sent events or WebSockets, is decided by
  `live-api-research` and `live-api-choice` before the child effort list is
  produced. Decided by `decisions-service-mode`.
- The live channel's evidence is captured by `live-api-research` at
  `docs/research/live-api.md`: server-sent events need one controller method
  and carry `Last-Event-ID` for resumption, OpenAPI 3.2 describes an event
  stream per event while AsyncAPI's first-party generators are archived, and
  the JDK ships a WebSocket client but no event-stream client. `live-api-choice`
  decides from that evidence. Decided by `live-api-research`.
- v1 ships server-sent events on the same HTTP API, port, document, and token
  rule, resuming through the `Last-Event-ID` header carrying the change cursor.
  WebSockets are refused for v1 with the evidence recorded. Decided by
  `live-api-choice`.
- The stream carries notifications only: a `state` event with the current
  cursor when it opens, `changed` events as records change, and `notice` events
  with type and level. Every list, forecast, and review is still read over
  REST, so reconnect needs no replay buffer. Decided by `live-api-choice`.
- One event-stream reader lives in the module holding the remote proxy and
  serves the desktop, tray, and command line clients, with polling kept as a
  fallback. The stream sends a fifteen-second heartbeat and the server recycles
  a connection after thirty minutes. Decided by `live-api-choice`.
- The desktop client's toolkit is put to the evidence by
  `ui-framework-research`, at `docs/research/ui-framework.md`: Compose
  Multiplatform, JavaFX, SWT with JFace, Swing, and a local web interface were
  compared against the rule that the work logic stays in Java. Nothing beats
  JavaFX on that rule, so the recommendation is JavaFX 21 LTS, because Java 21
  caps the usable JavaFX line at 23.0.2. Compose Multiplatform is the runner-up.
  Its seam is one Kotlin facade that Java calls, and Kotlin calls Java for free,
  but `@Composable` code cannot be compiled by `javac`, so the whole client
  layer would be Kotlin, and its Linux screen-reader support is documented as
  absent. Decided by `ui-framework-research`.
- JavaFX ships the desktop client, on the 21 LTS line. The human settled it and
  declined the prototype, because JavaFX works natively inside the Java
  process. The client stays a Java module with no second language, no compiler
  plugin, and no interop seam, so the JavaFX-dependent decisions on this map are
  confirmed rather than provisional: the `client` module's toolkit, the `jlink`
  image rule, the one-JVM shutdown order, and the AWT-backed tray. Accepted
  risks, not open questions: Java 21 caps the usable JavaFX line at 23.0.2, and
  the Linux tray is the weakest part of the choice. Decided by
  `ui-framework-decision`.

## Fog

## Child efforts

Settled by `child-efforts` in one grilling, after the UI framework was decided.
Twelve efforts, listed by stage in the five sections below this one: CLI stage,
MCP stage, Daemon stage, Desktop stage, and Tray stage. That grouping follows
the settled pre-v1 sequence, and each stage section holds its numbered efforts
in order.

These are live efforts, created at this effort's close, each with
`origin_effort: planner-v1` on its own map. Releases are not assigned here:
`release-plan` names them. Each child is scoped to a capability group, never to
a surface, and a child that would cut across stages is a signal to split it. The
command line interface's own vocabulary, output format, and exit codes are
settled by `cli-surface-convention` before the first CLI child starts.

## CLI stage

### 1. scaffolding

- **Scope.** Build files, the eight module skeletons, CI, the test harness, the
  `jlink` image build, and the version-tagging and additivity rule. It also
  ships one empty JavaFX window packaged as a `jlink` image, because the
  module-path rule is enforced by the build and is cheapest to break on the
  first commit.
- **Does not cover.** Any product behaviour, any record, any use case.
- **Depends on.** Nothing. It is the first child.
- **Entry criteria.** None. The stack is settled by `stack-research` and the
  prototypes, and the JavaFX choice is settled by `ui-framework-decision`.
- **Hands forward.** A repository where `./gradlew build` passes, the build
  refuses JavaFX on the classpath, and an empty window ships as an image.
- **Relies on.** The eight-module graph, the Gradle and JUnit stack, and the
  `jlink` packaging rule from `architecture`.

### 2. work-store

- **Scope.** The task and project record model, including notes, statuses, and
  identifiers; the SQLite store behind `ForStoringWork`; ordered migrations with
  a version and checksums; the operation log; the change log with cursors; the
  ownership lock; the data location; and `snapshotFor`.
- **Does not cover.** The planner, any surface, export, or purge.
- **Depends on.** `scaffolding`.
- **Entry criteria.** The build passes and `contract` exists.
- **Hands forward.** A store that round-trips a task and a project, answers
  "what changed since this cursor", and refuses a second owner.
- **Relies on.** The task model from `task-model-comparison`, the persistence
  decisions from `architecture`, and the change feed shape from
  `decisions-agent-interface`.

### 3. cli-basics

- **Scope.** The `cli` module and its argument parsing; the standalone
  configurator and runtime from `wiring`; capture, list, inspect, and complete;
  the timer and logged time; and the minimal day start the timer requires, with
  the day length read from one configured default.
- **Does not cover.** Projects, dependencies, estimates, priority, deadlines,
  the timetable, the today list, and anything after them.
- **Depends on.** `work-store`.
- **Entry criteria.** The store round-trips records, and
  `cli-surface-convention` has settled the command vocabulary and output
  format.
- **Hands forward.** A working command line application where a task is
  captured, found, worked, and completed, and where time is corrected.
- **Relies on.** `decisions-estimates-time` for the day-start rule and the
  timer states, and `architecture` for the single configurator.

### 4. cli-work-model

- **Scope.** Projects, dependencies with derived blocking and cycle refusal
  including the path, estimates as total effort with derived remaining, semantic
  priority, deadlines as the sealed date-or-instant pair, and cancel and
  archive.
- **Does not cover.** Ordering, capacity, forecasts, and any projection.
- **Depends on.** `cli-basics`.
- **Entry criteria.** `cli-basics` is tagged and its commands keep working.
- **Hands forward.** A task set carrying every scheduling input the planner
  needs.
- **Relies on.** The task model decisions, `decisions-estimates-time`, the
  deadline rule from `session-and-timetable`, and the cancel and archive rule
  from `decisions-data-lifecycle`.

### 5. cli-planning

- **Scope.** The timetable of net minutes per weekday, the full session with
  leave time and break reserve, the month's target and balance with the
  reconciliation rule, and the ordered today list with per-item reasons and the
  fit signal.
- **Does not cover.** Forecasts, warnings, notices, overrides, and review.
- **Depends on.** `cli-work-model`.
- **Entry criteria.** Estimates, deadlines, and dependencies all exist.
- **Hands forward.** The primary v1 output: an ordered day that the user never
  sorts by hand, with the cut line at the leave time.
- **Relies on.** `session-and-timetable` in full, `prototype-ordering-engine`,
  and `ForPlanning` as an infrastructure port taking data and not a store.

### 6. cli-forecast

- **Scope.** Forecasts with effective deadline, projected finish, slack, and
  risk; the ten notice types; operating-system delivery through `ForNotifying`;
  and the confirmed override with its two-step handshake.
- **Does not cover.** Review, export, and settings.
- **Depends on.** `cli-planning`.
- **Entry criteria.** The ordered list computes from one snapshot.
- **Hands forward.** The supporting risk layer and the confirmation channel
  that the agent surface reuses.
- **Relies on.** `decisions-forecast-warnings`, `decisions-agent-interface`
  for the confirmation handshake, and the notice concept from
  `session-and-timetable`.

### 7. cli-review

- **Scope.** Review projections over past days and months, export as the
  documented JSON archive with `VACUUM INTO` for a backup, the configurable
  purge age, the local timezone, and settings.
- **Does not cover.** Anything owned by an earlier capability area.
- **Depends on.** `cli-forecast`.
- **Entry criteria.** Notices and the override path exist.
- **Hands forward.** A command line interface that meets the core product
  scope, which is the gate for opening the MCP stage.
- **Relies on.** `decisions-data-lifecycle` in full.

## MCP stage

### 8. mcp-agent-interface

- **Scope.** The `mcp` module; one tool per operation in snake case, prefixed by
  its noun, with strict schemas; identifiers accepted in both display forms;
  stable failure codes carrying the offending entity; the two-step confirmation
  handshake; the read of current state plus changes since a cursor; and
  `mcp serve` owning standard input and output.
- **Does not cover.** Any transport other than standard input, and any write to
  standard output by any module.
- **Depends on.** `cli-review`.
- **Entry criteria.** Every use case is reachable from the command line, so the
  tool set mirrors it rather than designing anew.
- **Hands forward.** An agent surface over the same rules, with the change feed
  the API will reuse.
- **Relies on.** `decisions-agent-interface` in full, and
  `prototype-cli-mcp-stdio` for the standard-stream rule.

## Daemon stage

### 9. daemon-api

- **Scope.** The `api` module; the Spring Boot listener; the versioned OpenAPI
  contract organised by record kind; the server-sent event stream with
  `Last-Event-ID` resumption and its heartbeat; the remote proxy implementing
  every surface port, with the single stream reader; the daemon and split shapes
  in the configurator; the token rule; and graceful stop with takeover.
- **Does not cover.** The desktop window and the tray.
- **Depends on.** `mcp-agent-interface`.
- **Entry criteria.** The change feed and its cursor exist and are tested.
- **Hands forward.** A daemon any surface can attach to, and a client that can
  run in either mode without knowing which.
- **Relies on.** `decisions-service-mode`, `live-api-choice`, and the
  one-owner rule from `architecture`.

## Desktop stage

### 10. client-shell

- **Scope.** JavaFX and Spring in one JVM with the measured shutdown order;
  navigation; and the Today view including the fit signal, the per-item reasons,
  and the cut line.
- **Does not cover.** The other four views and the tray.
- **Depends on.** `daemon-api`.
- **Entry criteria.** The remote proxy and the standalone configurator both
  work, so the shell runs in either mode.
- **Hands forward.** A window showing the primary output, driven by the same
  ports as the command line.
- **Relies on.** `prototype-javafx-spring-jvm` for the process and shutdown
  order, and `ForPlanningQueries` as its read surface.

### 11. client-views

- **Scope.** Tasks, Projects, Review, and Settings, including the two editing
  doors onto the estimate and the dependency editor with its cycle message.
- **Does not cover.** The Today view and the tray.
- **Depends on.** `client-shell`.
- **Entry criteria.** The shell runs in both modes and the shutdown order is
  proven.
- **Hands forward.** A complete desktop client against the specification's five
  views.
- **Relies on.** The remaining surface ports, and `CONTEXT.md` for the domain
  vocabulary the labels use.

## Tray stage

### 12. tray-surface

- **Scope.** The tray icon, the popup showing the current timer, the next item,
  and critical notices, the native actions, and degradation to the window when
  no tray exists. It carries the real desktop test across GNOME with and without
  the app-indicator extension, KDE Plasma, macOS, and Windows, recording
  `SystemTray.isSupported()` in each.
- **Does not cover.** Any new planning capability.
- **Depends on.** `client-views`.
- **Entry criteria.** The client is complete and packaged as an image.
- **Hands forward.** v1 itself, the release after this stage.
- **Relies on.** `prototype-tray-packaging`, with the Linux tray recorded as the
  weakest part of the JavaFX choice.

## Releases

Settled by `release-plan`. Ten releases: nine inside the pre-v1 run and `1.0`
after them. Releases are numbered and named by what a user can do with them, and
tags follow the numbers from `v0.1.0` to `v1.0.0`, so lexical order matches
release order.

Three rules apply to every release below, and they come from `version-order`:

- no release is called done while a child effort inside it is unfinished;
- every interface an earlier release shipped keeps working in every later one,
  and no release removes a command or a surface that already worked;
- each release is exercised on real data, not on fixtures, and is tagged before
  the next one opens.

### 0.1 Capture

- **Observable.** Capture a task by title, list and inspect it, start and end
  the day, run the timer, log and correct time, complete a task.
- **Efforts.** `scaffolding`, `work-store`, `cli-basics`.
- **Done when.** A captured task survives a restart, the timer refuses to start
  before the day is started, and the binary ships as a `jlink` image with JavaFX
  on the module path.

### 0.2 The work model

- **Observable.** Projects, tasks inside projects, dependencies with derived
  blocking and a refused cycle carrying its path, estimates with derived
  remaining, semantic priority, deadlines in both forms, cancel and archive.
- **Efforts.** `cli-work-model`.
- **Done when.** A blocked task cannot be started, a cycle is refused with the
  tasks in the loop named, and an estimate drives the remaining figure.

### 0.3 The day

- **Observable.** A timetable of minutes per weekday, a session with leave time
  and break minutes, the ordered today list with per-item reasons and the cut
  line, the month's target and balance with reconciliation.
- **Efforts.** `cli-planning`.
- **Done when.** The list is ordered from real data without anyone sorting it by
  hand, and the fit signal matches the remaining capacity.

### 0.4 Risk

- **Observable.** Forecasts with slack and risk, warnings as notices,
  operating-system notifications, and the confirmed override.
- **Efforts.** `cli-forecast`.
- **Done when.** A missed deadline is reported before it passes where the
  forecast can place a finish, and an override is recorded, confirmed, and
  visible afterwards.

### 0.5 Review and export

- **Observable.** Review of past days, the export archive, a database backup,
  the purge age, the timezone, and settings.
- **Efforts.** `cli-review`.
- **Done when.** The core product is usable end to end from the command line,
  which is the gate the build order sets for opening the agent stage.

### 0.6 Agents

- **Observable.** An agent reads and writes through one tool per operation, with
  strict schemas, the two-step confirmation, and the change feed.
- **Efforts.** `mcp-agent-interface`.
- **Done when.** An agent completes a real operation from a cold start, and
  nothing writes to standard output in serve mode.

### 0.7 The daemon

- **Observable.** A daemon serving the versioned REST contract and the event
  stream while no window exists, a remote proxy in split mode, the token rule,
  graceful stop, and takeover.
- **Efforts.** `daemon-api`.
- **Done when.** An external consumer drives the API, a second owner is refused,
  and a surface switches modes without losing data.

### 0.8 The window

- **Observable.** The JavaFX client with Today, Tasks, Projects, Review, and
  Settings, running in either mode.
- **Efforts.** `client-shell`, `client-views`.
- **Done when.** The command line and the window produce identical plans from
  identical data.

### 0.9 The tray

- **Observable.** A tray icon, a popup with the current timer, the next item and
  critical notices, native actions, and degradation to the window when no tray
  exists.
- **Efforts.** `tray-surface`.
- **Done when.** The real desktop test passes on GNOME with and without the app
  indicator, on KDE Plasma, on macOS, and on Windows.

### 1.0

- **Observable.** No new capability.
- **Efforts.** None. Every child effort is finished by `0.9`.
- **Done when.** Every completion signal in specification section 8.8 is
  demonstrated on real data and every earlier interface still works.

## Requirements traceability

Rebuilt by `requirements-catalog-rebuild`. The directory now comes from the
whole planning record rather than from a single parsing pass, and every row
cites the file and the line it came from.

**The catalogue.** `docs/v1/requirements-directory.csv` holds 1159 rows under
the header `id,order,source_file,line,section,title,status,target,effort,spec,note`.
The `order` column is the chronology: 0 is the original specification, 1 to 27
are the twenty-seven planning tasks in the order they were worked, 28 is this
map, and 29 is `CONTEXT.md`. The extraction itself is frozen beside it in
`docs/v1/requirements-catalog.csv`, committed as snapshot `a0ee2a8`, with one
part per source under `docs/v1/catalog/parts/`.

**Why the order matters.** A statement can only be stale because something
later replaced it, so ordering the corpus by when it was written turns an
unbounded comparison into a search that only moves backwards. Each disposition
slice decides its own rows and may flip earlier ones; a later slice overrules an
earlier one, which mirrors how the decisions were actually made.

**Dispositions.** 977 rows are kept, 82 superseded, 61 duplicate, 25 deferred,
and 14 refused. Every superseded row names the later row that replaced it, every
duplicate names the row that states the subject best, and every non-kept row
carries a note saying why. The deferred rows are the v2 tracker work, the
post-v1 date gating and recurrence, and the attachments that arrive with them.
The refused rows are the six glossary terms that describe code internals plus
eight model options deliberately rejected in v1.

**What an owner means.** `effort` names the first child effort that must satisfy
a row, not every surface that later shows it, which is why the desktop views
carry almost no rows of their own: the command line delivers those capabilities
first. The value `planner-v1` marks the 290 rows this effort discharges itself:
process rules, evidence a later task consumed, and records it writes. Those rows
are exempt from specification coverage, and the specification must not copy them
into the product description.

**The checker.** `docs/v1/check-requirements.py` enforces six rules now: exact
header, unique identifiers and non-decreasing order, a citation that resolves to
non-blank source text, the five statuses with a note on every non-kept row, an
owner that exists on every kept row, and replacement chains that point forward
in time and terminate. Rule 6 compares the parts to the directory and to the
catalogue row for row, not merely by count, so a substitution cannot slip
through.

**The seventh rule, waiting.** When `to-spec` writes the specification, every
kept row owned by a child effort must be named by exactly one `Covers:` line,
every cited identifier must exist and be kept, and the `spec` column must name
the section that covers the row. `to-spec` does not publish until that passes.

**Consistency.** The same `to-spec` run hands the draft to one agent that wrote
none of the documents, asked only for contradictions and gaps, against the
shared concepts: the estimate and remaining, the deadline and effective date,
the notice, the session with its leave and stop times, capacity and target and
balance, the identifier, the run mode and the authority, and the change cursor.

**Fidelity into the children.** Each child effort's map carries a `## Requirements`
section listing the identifier ranges it owes, written at creation from the
`effort` column, and each child's finalize review checks that list row by row.

The build record, including the reviewer findings and their triage, is
`docs/v1/catalog/triage.md`. The checker is planning tooling for these
artefacts, not product code, and the build's non-goals do not cover it.
