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
- The pre-v1 build order is the command line interface, then the backend
  logic, then MCP, then the daemon, then the desktop client, then the tray.
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
- The pre-v1 build order is the CLI, then the backend logic, then MCP, then the
  daemon, then the desktop client, then the tray. A general architecture
  grilling runs after the order is settled, and the two existing service and
  agent grillings run after the architecture. A separate release plan runs
  after the architecture and the child-effort list. Decided by
  `spec-additions`.
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

## Fog


