---
type: task
subtype: grilling
title: Settle the overall architecture that supports the whole pre-v1 sequence
status: stable
workflow_state: done
blocked_by:
- spec-additions
- version-order
- stack-research
---

## Decision to settle

Settle workthread's overall architecture, flexible enough that the whole
pre-v1 sequence works without a rewrite.

- **Module shapes.** The core work logic, the CLI, the MCP interface, the
  Spring Boot API module, the JavaFX client, and the tray. What each one owns,
  what it must not own, and which direction the dependencies run.
- **The in-process-or-daemon rule.** Every user-facing or external-facing
  interface works with the backend in the same process or by talking to the
  daemon. How that is expressed once, so no interface grows its own service
  copy.
- **Transport.** Localhost HTTP for the API, stdio for MCP, and the CLI's two
  roles. The architecture must state how an interactive CLI session and an MCP
  stdio session stay separate, because one process cannot own the terminal's
  standard streams for both.
- **Seams and ports.** The persistence port, the service facade every front end
  uses, the clock, and the change feed. Which boundaries must be fakeable in
  tests.
- **Where the planning engine lives.** Scheduling, ordering, and forecasting as
  a pure function over data, current time, and the timetable, so the same code
  serves every mode.
- **Persistence and the single owner.** Where the data directory is, how only
  one owner writes at a time, and how the daemon and the in-process mode share
  one store.
- **Extension points for post-v1.** Multi-day allocation, intervals with pause
  budgets, and a network-hosted daemon. The architecture must say where they
  would attach, without building them.
- **The domain model.** The task record decided by `task-model-comparison`,
  placed in the core.

## Parent decisions

`spec-additions` recorded the build order and the in-process-or-daemon rule.
`version-order` settles the sequence the architecture must support.
`stack-research` supplies the evidence on the MCP library, the shared-JVM
shape, persistence candidates, and the build tool. The non-negotiables task
fixed that work logic belongs to the core.

## Known choices

- A hexagonal core with adapters for persistence, HTTP, stdio, CLI, JavaFX, and
  tray, against a layered arrangement with a service facade.
- One service interface with two hosts, in-process and remote, or two service
  implementations with a shared engine.
- The Spring Boot API as the only remote transport, or a thinner RPC seam that
  the API happens to implement.

## Recommended starting answer

A hexagonal core: domain model, services, and ports, with no Spring, JavaFX, or
MCP dependency. One service facade is the only way a front end reaches the
logic. Two hosts implement the same facade contract: an in-process host and a
remote adapter that speaks to the daemon's HTTP API. The CLI, MCP, desktop, and
tray all code against the facade or the remote adapter, never against the
storage. The scheduling and forecast engine is a pure function. Persistence,
the clock, and the change feed are ports. Post-v1 complexity attaches as new
inputs to the pure function, not as new scheduling code paths.

## Downstream work

Fixes the architecture document this effort produces, the scope of the
scaffolding child effort, and the boundaries every child effort works inside.
Feeds `release-plan` and `child-efforts`.

## Notes

### Grilling in progress: the diagram, step by step

The human asked for the architecture to be built as a diagram first, one step
at a time, and for the usual question rounds to fill the gaps afterwards. This
note records each settled step as it lands.

**Step 1, the core's internal shape.** Settled: a hexagonal core. The domain
model and the use cases sit inside, the ports at the boundary, and the
adapters outside. The core holds no Spring, JavaFX, or MCP dependency. The
human added one requirement of their own: an interface between a front end and
the core, with one implementation that runs the work logic in process and one
that forwards to the daemon, so the same front-end code serves both run modes
and the choice between them is made at startup. That is one surface port with
two implementations, not an extra layer.

**Step 2, the surface port's shape.** Settled: several small surface ports
grouped by use-case area, each operation coarse enough for the wire, rather
than one wide facade or an entity-shaped port. Recorded as accepted on the
recommendation. The human asked what the split buys; the answer is narrower
dependencies per surface, fakes that implement only what a test uses, and
surfaces that can be handed a read-only part.

**Terms.** The human asked for one common definition of each term, and settled
this after four attempts:

- **Surface port** and **infrastructure port** are canonical. A surface port is
  the interface the core implements and a surface calls; an infrastructure port
  is the interface the core calls and an adapter implements.
- The names are the two peer kinds, which hold in both run modes. This is the
  reason they won. Every location metaphor, including inner and outer, changes
  meaning when the client moves from the same process as the core to another
  process. Every direction metaphor needs its word to survive being dropped.
- Cockburn's names are referenced in `CONTEXT.md` and forbidden in artifacts:
  surface port is his driving port, and infrastructure port is his driven port.
  His 2005 article also uses primary and secondary port.
- Rejected along the way: driving and driven (the two words differ by two
  letters and one is lost at reading speed), primary and secondary (reads as
  mandatory versus optional, which inverts the real distinction), inner and
  outer (a location metaphor, and it spends Cockburn's inside and outside,
  which this document needs for a different rule), incoming and outgoing, entry
  and exit, northbound and southbound, and the single nouns facade and
  boundary taken from other vocabularies.
- Interface names carry no side word, following Cockburn: `ForCapturingTasks`
  is a surface port because the core implements it, `ForStoringTasks` is an
  infrastructure port because the store implements it.

Written to `CONTEXT.md` now: Core, Surface, Surface port, Infrastructure port,
Adapter, Remote proxy. The vocabulary rule (write the full term, never the bare
word `port`) belongs in the architecture document.

**Still open from this step.** How the two run modes are defined now that the
CLI is a surface, since the existing `Client` and `Standalone mode` entries no
longer cover it. The name of the module that holds the surface ports and the
request and result types.

**Step 3, the infrastructure side.** Settled: the transaction boundary is one port call, the change log is written inside the same SQLite transaction as the data it describes, and the ports are kept few. Accepted as recommended:

- `ForStoringWork`: every record kind (tasks, projects, dependencies, time entries, settings, timetable, time off, overrides), the transaction, the change log append, and a read of changes since a cursor.
- `ForReadingClock`: now and today in the configured zone.
- `ForNotifying`: raises an operating-system notification, because in split mode the daemon has to raise it while no window is open.

The human added a fourth, reserved for attachment storage, and named the same pattern for the future plugins. Both are extension points, not v1 work: the map's non-goals still exclude attachments and tracker plugins, and an interface with no caller would be dead code in v1.

- Rename proposed and put to the human: `ForStoringAttachments` instead of `ForStoringData`, because `ForStoringWork` and `ForStoringData` differ only in the word that carries no meaning here, which is the confusion the naming rule exists to stop.
- Why attachments cannot ride on the work port: a blob lives on the filesystem, and a file write cannot join a SQLite transaction. The port needs its own compensation rule, and content-addressed file names make a retried write harmless.

Deliberately not ports: identifiers (a record-shape question for the domain model step, after `task-model-comparison`) and the data directory with the export path (adapter configuration, not a conversation the core has).

**Interface naming.** Settled: keep Cockburn's `For...` prefix for every port interface. Rejected: role nouns (`TaskCapture`, `PlanQueries`) and imperative use-case names (`CaptureTasks`).

One correction recorded with the decision, because it changes how the convention gets used: the prefix marks a port on either side, not an infrastructure port. `ForCapturingTasks` is a surface port and `ForStoringTasks` is an infrastructure port, and both carry the prefix on purpose, so the pair stays symmetric and a missing or doubled port stands out when the list is read aloud. The side is visible from what the verb is for: a surface port names what a person or an agent does to the system, an infrastructure port names what the core needs from the world. The architecture document lists the two groups separately, which is the reliable marker.

**Where the port's side is marked.** Settled: in the package, not in the identifier. Surface ports live in `port.surface.*` and infrastructure ports in `port.infrastructure.*`, and every interface keeps one purpose-shaped name with the `For...` prefix. The import line then states the side in every file, which is where a Java reader looks for it.

Rejected: a `Needs...` prefix for infrastructure ports (self-describing, but it repeats what the package says and reads oddly as a constructor parameter), and splitting the two sides by grammatical shape, with imperatives for surface ports and purposes for infrastructure ports (a rule to learn, and the first thing forgotten). Also rejected on the way: putting the side word inside the identifier at all, because it is noise in every signature and a side move would then force a rename in every caller.

Precedent: the Java hexagonal convention popularised by Tom Hombergs, `port.in` and `port.out` packages with purpose-shaped names inside. This project spells them `port.surface` and `port.infrastructure` to match its own vocabulary.

**Step 4, the surface ports.** Settled: seven, one per area, plus a preferences seam that is not a port.

- `ForCapturingTasks`: create and edit a task, set priority, estimate, deadline, and source, attach it to a project.
- `ForManagingProjects`: create, edit, and close a project.
- `ForPlanningQueries`: the today list with per-item reasons and the fit signal, the forecasts, the warnings, and the review projections. Each result carries the instant it was computed for.
- `ForTrackingTime`: start, pause, complete, log and correct time. The timer state returned is always the service's, never a surface's memory of it.
- `ForOverridingRules`: the confirmed override. It takes the confirmation channel as part of the call, so a headless daemon answers `confirmation_unavailable` instead of pretending.
- `ForDefiningCapacity`: the timetable and the time off entries, including retroactive ones. Split from configuration because changing capacity forces a recomputation and changing a preference does not, and because the engine's input dependency must stay narrow.
- `ForConfiguring`: the local timezone, the notification categories and quiet hours with the platform permission state, reading the data location, and starting an export.

Rejected: three wide ports (writes, reads, configuration), because the tray needs a read-only port and the override would hide its confirmation inside unrelated writes; one port per specification story, roughly twenty, which turns a document unit into a boundary; and putting capacity or the service mode inside `ForConfiguring`. The service mode is startup configuration read by the configurator before the core starts, and the planning horizon is not promised by the port at all, because the two-week horizon replaced the 7 to 365 day setting and `decisions-forecast-warnings` has not settled whether any horizon setting survives.

**The configuration split.** Settled: core behaviour settings go through `ForConfiguring`; a surface's own preferences (theme, window geometry, tray tooltip, popup behaviour, start at login) are the surface's own file in the platform config directory, never in the data directory, never in an export, and never able to influence the plan. The human wanted the option of having the core hold a surface's preferences. Resolved as: `ForRememberingSurfacePreferences` is a real port for the variant where the core remembers them, used only by an adapter that chooses that; the client's own preferences interface has a file adapter by default. v1 builds the interface and the file adapter and reserves the port and the remembering adapter, on the same footing as the attachment port.

**Mixing implementations against mixing authorities.** Settled as the rule this decision rests on: a surface may hold different implementations for different ports, which is what wiring per port means. It may not hold two authorities for the work logic at once, because that would give one process two owners of the data and break RULE-ARCH-1. A local adapter is only acceptable for something the core does not own, which is why the preferences seam is not called a port: a port is the core's boundary, and "the core implements it" must stay true.

**Step 5, the planning engine.** Settled, after a detour that clarified the terms: the planning interface is a genuine infrastructure port, `ForPlanning`, and the built-in engine is its default implementation. The human's reason is a later plugin that provides a different planner, which makes the boundary real rather than theoretical, so the port is declared now.

Two conditions attach to it, and they are what keep a plugin from becoming a second authority:

- The port takes data, never a store. `ForPlanning.plan(PlanningInput input, Instant asOf, Session session)` is the shape; the use case loads the snapshot through `ForStoringWork.snapshotFor(asOf)` and passes it in. A plugin that reached the store directly would bypass validation, the change log, and the rules, which RULE-ARCH-1 forbids.
- The port interface and the input and output types have to live where a plugin can depend on them without depending on the core's internals. That is a module-placement consequence the architecture document has to carry.

Honest cost recorded with it: the input and output types become a contract earlier than they otherwise would, so `task-model-comparison`'s record decisions will land on a published shape. Mitigations: the plugin contract stays minimal and versioned, and v1 ships no plugin discovery at all, only the port and the built-in implementation.

Rejected on the way, and worth keeping rejected: the engine reading its own data through `ForStoringWork`. Two reasons. A test of the engine then needs a fake store instead of a value, and the worked examples in the specification stop being golden tests. And two reads inside one computation can see two different states, so the plan can describe a world that never existed. One snapshot removes both.

`CONTEXT.md` was amended: the infrastructure port entry now says a built-in implementation may ship with the product and stand behind the same port. `ForPlanning` is the first port for which that is true.

**Composition and startup wiring is queued, not settled.** The human deferred the question of how constructor dependencies are built and composed at startup, and said that for v1 it is moot for `ForPlanning` because there will most likely be only one implementation. Recorded as an open topic for a later step in this grilling, not as a decision. When it comes, it covers: how each process builds its ports, how the mode chooses between the in-process core and the remote proxy, and how the daemon builds the same core.

**Step 6, the configurator.** Settled: one shared configurator class expresses the in-process-or-daemon rule in one place. It takes a narrow `StartupConfig` and yields a runtime with the surface ports populated and, when this process holds the work logic, the core with its infrastructure ports populated from the same configuration. Every process's entry point calls it; no surface branches on the mode itself. Rejected: a configurator per process, each interpreting the configuration, and implicit detection of a running daemon, which would make a command's effect depend on whether a daemon happened to be up.

**Step 7, what the configurator returns.** Settled: one runtime type with two axes, not two runtime types. The daemon is a surface too: the API listener is a driving adapter that calls the same surface ports, so it belongs in the same list as the CLI, the MCP server, the client, and the tray.

```java
record Runtime(List<Surface> surfaces, Authority authority) {}
```

`Authority` is where the work logic is from that process's point of view: the in-process core, or the remote proxy over HTTP. Shutdown is stated once: the surfaces close first, then the authority. In the daemon the listener stops accepting and then the core closes the store; in the desktop process the JavaFX exit comes before the context close, which is the order the prototype measured.

Rejected: two runtime types, with the daemon outside the surface list, which was my proposal and the human's objection was correct. Rejected earlier and still: the HTTP listener modelled as something other than a surface, which would split one concept into two names.

`CONTEXT.md` was amended: the surface entry now says a surface is a driving adapter that exposes the work logic to something outside the core, and lists the API listener on the same footing as the CLI, the MCP server, the client, and the tray.

**`StartupConfig` is an interface, not a record.** The human rejected the fixed record because a file-shaped source is not universal: an OS registry, macOS defaults, GSettings, or a set of flags cannot produce a config file path and a data directory path. A platform that cannot supply a value must be able to say so. The record becomes one implementation of the interface, used by tests.

**No invalid states in the type.** The human asked for tagged unions. The settled shape is a sealed interface with one record per process shape, so a split-mode surface cannot exist without an endpoint and a daemon cannot exist without a credential, and the mode is not a field that can disagree with the fields beside it. Sealed classes arrived in Java 17 and pattern matching for switch with record patterns in Java 21, which is the project's floor, so the exhaustive switch needs no `default` and a new process shape makes the compiler list every place that must handle it.

**How the startup configuration is read.** Settled: one plain class per source, no interface. `StartupConfigSource` reads the platform's own storage: flags and environment for the CLI, a config file, the Windows registry, macOS defaults. It is selected in `main`, because it has to run before anything else exists, so nothing can take it polymorphically and no test needs to fake it; a test builds the `StartupConfig` value directly.

The payloads inside the sum type are values, not interfaces: `Endpoint(URI uri)`, `Credential(String token)`, `DataLocation(Path root)`. An interface inside a variant would let any implementation answer anything, which reopens the invalid states the sealed sum just closed. A URI already covers loopback and a remote host, so `Endpoint` needs no variants today, and a different kind of data location would arrive as a sealed sum with one `permits` line.

The rule this rests on, to be applied deliberately elsewhere: a sealed sum is for a closed set of shapes whose valid combinations differ, which is the process shape; an interface is for behaviour that varies, which is what the ports are; a value is for data whose shape does not vary, which is what the config payloads are.

Naming: `StartupConfigSource`, not `StartupConfigProvider`, because it reads from the platform and because `Provider` already means something in Java libraries.

**Step 8, persistence.** Settled as option (a) with the runtime location resolved separately.

- Data location: `workthread.db` plus its `-wal` and `-shm` siblings, in the platform data directory (`$XDG_DATA_HOME/workthread`, `~/Library/Application Support/workthread`, `%LOCALAPPDATA%\workthread`).
- Runtime location: `owner.lock`, `owner.json`, and the transport credential, resolved by `StartupConfigSource` and never known to the store. Linux uses `$XDG_RUNTIME_DIR/workthread`, falling back to `$TMPDIR/workthread-$UID` at mode 0700 with a warning where the variable is unset, as the XDG specification asks. macOS uses `$TMPDIR/workthread`, per user and per session. Windows uses `%LOCALAPPDATA%\workthread\runtime`, because `%TEMP%` can be cleaned underneath a running process and macOS `/tmp` is shared by all users.
- Ownership: an exclusive `FileChannel.tryLock` on `owner.lock`, taken before the database opens and released on shutdown. `FileChannel.tryLock` behaves the same on all three platforms, so the mechanism has no platform branch; only the location resolution does, and only `StartupConfigSource` holds it.
- `owner.json` is a hint and the lock is the truth: after a crash the metadata can be stale, so a caller confirms by connecting and never decides ownership from the file. The metadata is replaced by writing a temporary file and renaming it, and the file the lock is held on is never replaced, because the lock is bound to the open file and a rename would let a second process take a second lock on the same data.
- Migrations: ordered SQL scripts shipped inside the core, applied at startup inside a transaction, with the applied version and each script's checksum in a table, and a refusal to run when the database is newer than the binary. No migration library.
- Export: `VACUUM INTO` for a consistent single-file snapshot from a live database. A file copy is only valid with no owner running. The export is the data directory and nothing else, which is why runtime state moved out of it.

Rejected: SQLite `locking_mode=EXCLUSIVE` for ownership, which forbids a read-only second connection and reports the conflict as a generic busy error; a pid file, which survives a crash and would let a second process start on live data; storing runtime state in the data directory on every platform; and the OS temporary directory as the runtime location on macOS and Windows.

**Step 9b, local authentication, revised by the human.** The token file is gone. No credential is ever published in the runtime directory, because publishing one is the thing the human was uncomfortable with, and a published credential outlives the reason it exists.

- A loopback bind needs no token by default. It can require one, as a configuration choice, for a shared machine.
- A non-loopback bind requires a token and a fixed port, and the daemon refuses to start without both.
- The token comes from configuration, not from a file written at start: an environment variable for the daemon, and each client's own configuration or environment for the split case. It is never logged and never appears in an error message or an export.
- `owner.json` still publishes the endpoint for local discovery. A local client that is configured to require a token gets that token from its own configuration, so the two never travel together through the data or runtime directory.
- Rotating a token means restarting the daemon.

The reasoning kept from the earlier recommendation: the threat is the user's own browser posting to a loopback port, so a token is worth having where the machine is shared or the bind is not loopback. The reasoning dropped: publishing it to a file.

**Planning detail belongs to the implementing efforts.** The human asked that the concrete implementation of `StartupConfig` and of the configurator be left to the efforts that build them. This record keeps decisions, constraints, and the reasons; the class sketches in the conversation were explanatory and are not the record.

**Step 12, the two modes.** Settled: they are defined by where the work logic runs, not by which processes exist. Standalone mode is an arrangement where a surface reaches the work logic in the same process, with no API between them. Split mode is one where a surface reaches it in another process, through the API. `Client` keeps meaning the JavaFX surface.

Rejected: keeping the client-and-daemon wording and appending a sentence for the CLI, which leaves the CLI as an exception to its own glossary, and dropping the two mode names in favour of describing each surface separately, which costs the two words the whole effort uses to talk about deployments.

`CONTEXT.md` was amended: the opening paragraph now describes surfaces and the core rather than the JavaFX client and a daemon, `Client` is defined as a surface rather than a process, and both mode entries carry the new definition.

**The extension points and plugins.** Settled and recorded: as listed in the conversation. A plugin is a jar with a manifest declaring its id, its version, the contract version it was built against, and the infrastructure ports it provides; the startup configuration selects which implementation serves which port; the plugin owns the schema of its own configuration section, which the loader hands it to validate and parse; registration is `META-INF/services`; only `wiring` resolves a port to an implementation; a missing, ambiguous, or version-mismatched choice fails startup naming the port and the plugin.

Costs to carry into the architecture document, because none is free: class loader isolation needs a plugin `ModuleLayer` under the module path the client image uses; the contract version has to be checked at load; `StartupConfig` carries a plugin's section untyped, because its type belongs to the plugin; and a plugin's secrets stay out of the export and the logs.

The human asked that the obligations of a replacement store port not be recorded yet, since plugins are post-v1. Kept instead: the invariants already exist as v1 decisions, the ownership lock and the migration check from step 8, and the transaction and change log from step 3, so the plugin work inherits them rather than re-deciding them.

**Step 9, superseded in form by the human.** The authority policy enum was rejected as forced. Settled instead:

- The authority is carried by the `StartupConfig` variant: `Standalone` owns the data, `SplitSurface` carries the endpoint and the credential, `DaemonProcess` owns the data and serves the API.
- `serveMcp` is a value on the configuration: `NONE`, `STDIO`, or `STREAMABLE_HTTP`. It is the only optional surface, because which binary starts already fixes the others: the CLI entry point hosts the CLI surface, the desktop entry point hosts the client and the tray, the daemon entry point hosts the API listener.
- `mcp serve --stdio` is its own process, never the interactive command's process, so the terminal's standard streams are never shared.
- The container case falls out with no new machinery: `SplitSurface` plus `serveMcp = STDIO` is an MCP surface on that host whose core tools are proxied to the daemon.
- `STREAMABLE_HTTP` exists in the enum for the shape but not in v1, because the map's rule says the MCP server runs only in a subcommand that owns standard input and output.

**One owner across processes, settled as option (b).** Only one process may hold the data. A second process that finds the lock held fails with a clear message telling the user to stop the other process or run the daemon, and sharing one backend among several surface processes requires the daemon. Rejected: a long-lived owner publishing its endpoint so that another process can join it, because the product does not have to support every arrangement of running things, and the daemon is the supported way to share.

**Step 10, the module graph.** Settled: eight modules. `contract` (both port families and the types they exchange), `core` (domain model, use cases, the built-in planner, migration scripts), `adapters` (SQLite store, system clock, OS notifier), `wiring` (the startup config sum, one source per platform, the configurator, the runtime record), `api` (Spring Boot HTTP listener and the daemon's entry point), `mcp` (the MCP surface and its stdio transport), `cli` (the CLI surface and its entry point), `client` (JavaFX client, tray, and the desktop entry point). Dependencies point inward: `core` sees only `contract`, `adapters` sees only `contract`, `wiring` sees `contract`, `core`, and `adapters`, and the three executable modules see `wiring` plus their own surface, with `cli` also depending on `mcp`.

The MCP surface is its own module because it is a surface, not a launcher: it has two possible hosts and two transports, and putting it inside `cli` would make the CLI binary the only way to run agent tools. A daemon hosting it cannot use stdio, so daemon-hosted MCP means HTTP transport, which the map's current non-negotiable fact excludes. So v1 hosts MCP from the CLI over stdio, and whether the daemon also hosts it belongs to `decisions-agent-interface`, which already owns the MCP surface.

**Correction to this record.** An earlier block says the two run modes are still open. Step 12 closed that.

**Pending the human, one line each.** The attachment port's name: `ForStoringAttachments` is proposed, and `ForStoringData` was the human's own first wording. And the rules for a surface that holds local capabilities of its own, which the container example introduced: scoped to a configured root, never touching the core's data, and declared apart from the tools the core supplies.

**The attachment port's name, settled.** `ForStoringAttachments`, not `ForStoringData`, for the same reason the naming rule exists: the two names differ only in the word that says nothing here. The port stays reserved for post-v1 work.

**A surface that holds local capabilities of its own.** The human's container example introduced this: an MCP server on another host or in a container cannot read paths the daemon cannot see, so the surface itself must supply those tools. Recorded narrowly, to what follows from settled principles rather than from rules the human has not agreed to: a surface may hold capabilities of its own, and they must never read or write the core's data. Where such a tool is scoped, and how the tool list declares which tools come from the core and which from the surface's own host, are surface-design matters for the MCP work, not architecture decisions.
