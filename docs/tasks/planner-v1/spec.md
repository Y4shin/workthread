---
type: spec
title: "workthread: a single-user task and time planner, v1"
status: draft
---

## Problem Statement

A person with more commitments than hours cannot answer three questions from the
tools they own.

- **What should I work on now?** A task list gives a list, not an order. A
  calendar gives time, not priorities. Neither tells you which of forty open
  items to open first.
- **Will this be done in time?** Task lists do not project forward. Calendars
  assume the work takes exactly as long as it was booked for, which is the
  thing nobody knows in advance.
- **Are my days going where I meant them to go?** Time trackers record time
  after the fact, if at all. They also turn untracked work into debt, because
  they measure task time when what a person actually wants to know is whether
  they were at work for the hours they planned.

The result is a familiar failure: the plan is rebuilt by hand every morning,
deadlines are discovered late, and the record of what happened is either
missing or misleading.

workthread is for one person, on one machine, with no account and no server.
That constraint is deliberate: the planning data is personal, the tool must
work on a train with no network, and a single owner removes every question
about who is allowed to change what.

## Solution

One local core holds the work, and five surfaces present it: a command line, an
agent interface over the Model Context Protocol, a daemon serving an HTTP API, a
desktop window, and a tray icon. Every surface applies the same rules to the
same data, because all of them call the same surface ports either in process or
through the daemon.

The product answers the three questions with four mechanisms.

1. **A timetable and a session.** Each weekday carries a number of net working
   minutes. Today's capacity is the session: from now until the planned leave
   time, minus the break reserve. No clock-time blocks, no multi-day
   allocation, no task split across days.
2. **An ordered today list the user never sorts.** The application orders the
   list from the properties the user supplied, shows why each item is there,
   draws a cut line where the day runs out, and states whether the plan fits.
3. **Forecasts with deadlines and notices.** Effective deadlines, projected
   finishes, slack, and risk. Ten notice types carry everything the system
   needs to say, with two levels and two lifetimes.
4. **A month that measures presence.** The month compares the hours you were
   there against the hours you planned, deliberately not task time, and
   reconciles a gap over the days that follow.

Versions are additive. A release keeps every interface an earlier release
shipped, so the command line, the agent tools, the API, the window, and the
tray are all live surfaces of v1 rather than a path to one.

### Capture and time

Capture needs one field: a title. Everything else is optional, and a task
without an estimate waits in the inbox until the user makes it ready. The title
is required and holds one to five hundred characters. Every record has an
identifier, an integer that only the data owner allocates, and its display form
is the prefix plus the number padded to at least four digits: `TSK-0001` for a
task, `PRJ-0001` for a project. The prefix marks the record kind, so a
wrong-kind reference fails loudly instead of addressing another record. The
padding is a minimum width and never significant, and every surface accepts
either the bare integer or the display form, case-insensitively, with no fuzzy
matching.

A task moves through five stored statuses: inbox, ready, in progress, completed,
cancelled. Blocked is derived, never stored, and never set by a user or an
agent. Lists have a stable default order; sorting changes display order only.

Time works like this:

- One timer runs at a time by default. Pausing stops the timer and keeps the
  task in progress, because pausing is a task act and not the end of the day.
- Starting another task returns the previous one to ready unless it was
  completed or cancelled.
- Concurrent work sits behind a configuration flag, off by default. With it on,
  a second task may start while another runs, but only when the caller says so
  explicitly. The pause heuristic survives the flag, so a paused task returns
  to ready when something else starts, and the tray and the active indicator
  show a list rather than one task.
- A manual entry covers past or current time, never future time, and never
  overlaps an open entry.
- Editing or deleting a time entry recalculates remaining effort and can clear
  an overrun.
- Starting work on a task before the day has been started is refused rather
  than inferred. The API and the agent surface return the same refusal as the
  command line.

The first version of the command line does not wait for the whole model. It
captures a task by title, lists and inspects tasks, starts and ends the day,
runs the timer, logs and corrects time, and completes a task, sorted by
deadline then priority, with no ordering engine, no projects, and no estimates.
That minimal day start is later replaced by the timetable, not supplemented by
it.

There is no first-run wizard and nothing is written to configuration before the
first task exists. The application is local-first and offline capable; all
planning data is local.

The command line is a first-class surface of v1, shipped beside the window and
the tray. Every user-facing or application-facing interface works with the
backend in the same process or by talking to the daemon, which is what makes a
standalone mode possible at all.

Covers: SRC-RULE-ORDER-0, SRC-RULE-EFFORT-5, SRC-RULE-ARCH-1..SRC-RULE-ARCH-3, SRC-RULE-DATA-1, T01-001, T02-024, T02-027, T03-005, T03-007, T03-008, T11-013, T11-022, T11-025, T12-003, T12-004, T12-008, T14-010..T14-018, T14-020, T037, T18-003, T18-009, T24-022, MAP-001, MAP-022, MAP-028, MAP-045, MAP-046, MAP-C08, CTX-standalone-mode, CTX-concurrent-work

### The work model

A project has a title, a priority, an optional deadline, and a status of active,
paused, completed, or archived. Only active projects schedule their tasks. A
paused project is not scheduled and no override schedules it.

A task is an identifier, a required title, zero or more notes, an optional
project, a priority, an estimate, an optional deadline, optional source
metadata, and a status. Notes replace the earlier single description field: a
note is multi-line, capped in length, carries its own creation timestamp,
displays in timestamp then identifier order, and is removed as a logged change
rather than an erasure. A correction is a new note.

Priority is an integer from one to five, defaulting to three, and each value has
a label, a scheduling meaning, and an example. Estimate and deadline carry the
rules that make scheduling meaningful:

- The estimate is total effort in one stored field, in positive whole minutes,
  required before a task is ready or in progress.
- Remaining effort is derived as the total minus logged time. It is not
  clamped: it reaches zero and goes negative, and the negative is shown,
  because the overrun is information the user needs.
- Two doors lead into that one field. The total can be set directly, or the
  remaining can be set, which writes the logged time plus the new remaining as
  the total, so the subtraction and the logged minutes agree.
- Clearing the estimate of a ready task is refused, because a ready task
  keeping a positive estimate is an invariant throughout.
- The application never rewrites the estimate towards logged time and never
  completes a task automatically.
- The estimate is retained on completion and cancellation and displayed with
  the logged minutes, so the record keeps both what was expected and what
  happened.

A deadline is a sealed pair rather than one nullable timestamp: a date or an
instant. A date means before that local day ends. An instant is accepted only
for the current local day, so 15:00 on a task due tomorrow is rejected; the
rule is input validation, not a stored constraint, so an instant that later
passes simply stays. The effective deadline is the earlier of the task's own and
its active project's, and every comparison reads a date as the end of that
local day. Changing a deadline is allowed even when it produces a missed
forecast, because the user may be renegotiating.

Dependencies are directed edges with self-edges, duplicates, and cycles
refused. A cycle refusal names the tasks in the loop. Blocking is derived from
incomplete prerequisites and cannot be set by anyone. Completing a task with
incomplete prerequisites requires explicit confirmation, and without it the
request is rejected with the incomplete prerequisites named; a task already in
progress when a prerequisite is added is not stopped, but it is reported.

Nothing is deleted on request. A task is cancelled and a project is archived,
and the moment of cancellation or archiving is recorded as a timestamp on the
record. Removals that touch no history, meaning a future per-date entry or a
task with no logged time, happen at once. A removal that changes something
already reported asks for confirmation first. An edit to a scheduling input is
an explicit, validated change, not a side effect of something else.

Covers: SRC-RULE-PRIO-1, SRC-RULE-STATE-3..SRC-RULE-STATE-8, SRC-RULE-EFF-1..SRC-RULE-EFF-3, T11-023, T12-026, T12-030, T12-034..T12-037, T13-032..T13-036, T14-001..T14-005, T14-009, T14-022, T001, T17-005, T17-014, MAP-042, MAP-043, MAP-051, MAP-055, MAP-056, CTX-estimate, CTX-remaining-effort

### Planning and the session

Capacity comes from two records.

**The timetable.** A weekday holds its net working minutes and nothing else: one
window, no clock times, and no pause budget. Zero minutes is a non-working day,
and every value is a whole number of minutes. One configured planned start
supplies the clock for future days, and the observed start replaces it once the
day actually begins. A future forecast day starts at that fixed placeholder
time.

**The session.** One record per date: the start, the leave time, and the break
reserve. The configured break reserve supplies the default for a date, and the
record stores the value that date used, so a day may differ from the setting.
The day's planned span is its start plus its minutes plus its reserve. That span
is not the capacity: capacity is what remains of it from now until the leave
time, net of the reserve. The leave is derived as start plus minutes plus
reserve unless the user states one, and "stay later today" or "I must leave
earlier" are simply statements that move the leave at any moment of the day. A
day with planned work that has not been started is not a session. The session
ends when the user says it ends, and ending the day stops every open timer at
its stop time. A day left unended gets an inferred end, the end of the last
logged entry that day or the end of the planned span, and a persistent notice
asks for a real end per unfinished day. A working day never crosses midnight, so
minutes that would run past it are not part of the day.

Time off is one record per date carrying a minute count and a kind. `vacation`
replaces both the day's capacity and the month's target and cannot exceed the
weekday's minutes; `flex` replaces only the day's capacity and may be more,
fewer, or zero minutes. Entries may be recorded on past dates, and the review
shows the conflict when presence or logged task time disagrees with them.

**The month.** The target is the month's weekday minutes minus vacation. The
quantity the month measures is presence, not task time: presence is the minutes
between the session's start and its stop time, minus the break reserve, so a day
that ends early or runs late moves the balance and untracked work is not a
deficit. The balance is presence minus target inside the running month. Four
settings bound it: a permitted shortfall and a permitted surplus, each a minute
count or unlimited, clamp what a month may end at; a target shortfall and a
target surplus are where the plan aims, and a target may not exceed its
allowance unless that allowance is unlimited. The gap between the balance and
the target band is settled in thirty-minute increments over the days that
follow. A day carrying minutes of its own is never modified, and that invariant
binds every future projection algorithm rather than only this one.

**The today list** is the primary output.

- It is bounded by the day's capacity, so with three hours left it holds three
  hours of work.
- Its order is never recomputed on the clock. The plan stays, and a cut line
  moves up it showing which items no longer fit today.
- It re-plans on a data change or at the next day, never merely because time
  passed.
- The fit signal is one line plus that cut: planned minutes, available minutes,
  and what no longer fits. It cannot say "does not fit", so the engine carries
  an explicit does-not-fit state.
- Allocation covers fourteen days concretely. A projection continues past that
  by repeating the weekday pattern and following the month's balance, and is
  marked rough; a span crossing four weeks is labelled rough.

The order is a deterministic heuristic, not a global optimizer, and it is
explainable. A task is skipped rather than removed when its prerequisites are
not yet completed or fully allocated. The system owns the order: v1 has no
manual pins, drag reordering, or protected focus blocks. Input order must not
affect the result, so reordering tasks, projects, dependencies, or capacity
records must not change the outcome.

The heuristic uses three pressures:

- **Deadline pressure**, where a prerequisite inherits the earliest effective
  deadline of the tasks it unblocks, so a chain is worked from the far end.
- **Dependency pressure**, the static least slack among the deadline-bearing
  descendants of a task.
- **Dependency depth**, the count of unfinished transitive prerequisites.

Every item carries one machine-readable reason, and the human explanation names
the decisive cause. The six reasons are `deadline_protection`,
`dependency_protection`, `task_priority`, `project_priority`, `earlier_deadline`,
and `stable_order`. `earlier_deadline` means an order decided by an earlier
effective deadline without pressure, and `stable_order` means an order decided
only by creation time or identifier. Reasons belong to items, not tasks: a task
whose work is selected for different reasons on different days carries the
reason for each.

Allocation follows eligibility and closure. A task is eligible only when its
state, project, prerequisites, and estimate allow it; eligibility gates normal
allocation, not forecasts. A closure is a deliverable plus all unfinished
prerequisites, recursively, excluding completed tasks and ineligible ones.
Closure effort sums remaining minutes and counts an unfinished zero-effort task
as five minutes. An unfinished task at or below zero remaining receives a
five-minute completion slot and is then treated as fully allocated; while a
timer runs, the running block is the claim, so an overrun keeps growing and
takes its extra minutes from what follows, which is why later items fall past
the cut line. Work that cannot be placed is unscheduled, and the application
must not fabricate a time for it; if no eligible task can be chosen, the
capacity is left unused.

Recalculation is synchronous after every saved change, whether that change is a
time entry, an estimate, a timer, a completion, or a capacity record. While a
timer runs, elapsed time and notices refresh at least once a minute. A read-only
diagnostic explains what would change and mutates nothing. Identical data,
current time, timezone, and horizon must produce identical items, forecasts,
reasons, and order, and observable order must never depend on incidental input
order, iteration order, randomness, or wall-clock granularity.

Covers: SRC-RULE-ELIG-1..SRC-RULE-ELIG-3, SRC-RULE-EFFORT-3, SRC-RULE-EFFORT-4, SRC-RULE-CAP-5..SRC-RULE-CLOS-5, SRC-RULE-ORDER-1..SRC-RULE-ALLOC-6, SRC-RULE-REASON-1..SRC-RULE-REASON-3, SRC-RULE-RECALC-1..SRC-RULE-RECALC-3, SRC-RULE-DET-1, SRC-RULE-DET-2, T02-008..T02-011, T02-013, T02-015, T08-002..T08-007, T08-009..T08-014, T11-024, T11-027, T11-033, T11-034, T13-001..T13-030, T13-038..T13-041, T13-053, T13-054, T13-056, T13-065, T14-006, T14-007, T14-019, T15-012..T15-015, T17-003, T17-004, T17-013, T17-039, T24-035, MAP-010, MAP-020, MAP-035..MAP-040, MAP-044, MAP-048, CTX-timetable..CTX-balance, CTX-vacation..CTX-reconciliation, CTX-completion-slot

### Notices and risk

A forecast answers whether a deliverable fits. It carries the effective
deadline, the projected finish, the slack, and the risk. Slack is capacity
minutes measured against the day capacities, which include the month's
reconciliation and exclude vacation and zero-length flex days, so a month in
arrears raises the projected capacity before a deadline and a month ahead lowers
it. Risk follows the familiar threshold ladder the specification set.

A notice is one concept for everything the system says. Each notice has a type,
a level, and a lifetime.

- Levels are `info` and `warning`. A warning asks something of the user; info is
  state worth knowing. The earlier `critical` severity retires, because errors
  are handled before they could become notices, so no level named critical
  survives anywhere in the product.
- Lifetimes are persistent and ephemeral. A persistent notice lasts while its
  condition holds and can be dismissed, which hides it until the condition
  clears; the dismissal is not remembered, so a recurrence shows again. An
  ephemeral notice stays until it is dismissed. Derived notices are recomputed
  projections, one-off notices are stored records, and a dismissal is stored
  either way.

The ten types, with their shipped defaults:

| Type | Level | OS delivery |
|---|---|---|
| `deadline_approaching` | warning | on |
| `deadline_tight` | warning | off |
| `deadline_missed` | warning | on |
| `blocked_deadline_path` | warning | on |
| `no_timetable` | warning | off |
| `month_will_not_settle` | warning | off |
| `missing_session_end` | warning | off |
| `estimate_overrun` | info | off |
| `horizon_over_capacity` | info | off |
| `dependency_cycle_prevented` | info | off |

Level and OS delivery are settings per type, not constants. Persistence is not
configurable: whether a notice describes a live condition or a one-off follows
from what the notice is. `deadline_approaching` fires while an effective
deadline falls inside a lead window that defaults to twenty-four hours.
`horizon_over_capacity` carries the meaning that the tool is being used beyond
its design, a backlog too large for the horizon. Quiet hours and
one-delivery-per-condition apply to OS delivery, and the notice list is always
the authoritative place, so an OS copy is never the only trace of a condition.

The risk vocabulary distinguishes three outcomes that used to run together.
**Missed** takes precedence: a deliverable is missed when its slack is negative
or its effective deadline has passed. **Unscheduled** covers only remaining work
that cannot be placed for a reason other than deadline capacity. **Blocked** is
the dependency graph only, meaning a cancelled, missing, or otherwise ineligible
prerequisite. A task's own state, being in the inbox, lacking an estimate, or
living in a paused project is not blocked; that is ineligible, and it is
reported separately.

Two rules stay overridable: dependency order and system order. The capacity
override of the earlier specification is gone, because the session's leave time
now moves instead. An override is deliberate, recorded, and confirmed through a
two-step handshake: the call records a pending operation with a two-digit code,
the human reads the code back, and only then does the change apply. An override
is visible afterwards in the change feed, in the log, and in the interface.

**How the confirmation code reaches a human.** The code is worthless if the
agent can read the channel it arrives on, so the channels are ordered by trust.
An ephemeral notice on an attached interactive surface comes first, then an
operating-system notification, then a private log file, then a free stream that
is not carrying the protocol. A window, the tray, and an OS notification are
agent-proof; a private log file is trusted only because a well-behaved agent has
no filesystem access. The command line owns the free-stream rung, so a
standalone `mcp serve` with no window, no tray, and no OS notification still has
somewhere to print. The private log file is an artifact of its own with its own
retention question, and it lives in the data directory beside the database. With
no channel at all, the call fails with `confirmation_unavailable` rather than
proceeding, which is what makes confirmation meaningful.

Covers: SRC-RULE-EFFORT-2, SRC-RULE-SLACK-1..SRC-RULE-RISK-4, SRC-RULE-FCAST-1..SRC-RULE-FCAST-6, SRC-RULE-WARN-2..SRC-RULE-NOTIF-4, SRC-RULE-PRED-1..SRC-RULE-OVERRIDE-6, T11-014, T13-042, T13-045..T13-049, T13-055, T14-008, T15-006..T15-011, T15-016..T15-033, T15-038..T15-041, T002, T003, T004, T013, T016, T024, T025, T026, T029, T030, T20-022, MAP-047, MAP-049, MAP-050, MAP-053, CTX-notice, CTX-forecast..CTX-risk

### Review, export, and retention

**Review** measures the month, not the task. Each past day shows its target
minutes, its presence minutes, and their difference, all recomputed from the
timetable and the session records. No past list is stored and none is
reconstructed. The limitation is stated in the interface: the review answers how
the days went against the hours you planned, not what you did in them. A
consequence the user must be able to see: changing a weekday's minutes today
changes last month's review, and changing it back changes it back.

**Export** produces a zip holding one documented JSON document: a header
carrying the format version, the application version, and the export time, then
the stored records, the identifier values, and the sequence state, so the data
can be read without the database. The format is engine-independent. v1 writes
the document alone; the attachments directory that belongs in the same zip
arrives with attachments. The operation log is not part of the export. v1
exports and restores by backup, not by import.

**Backup** is the database file itself, taken with `VACUUM INTO` or by copying
the data directory while nothing writes to it. The distinction matters: the
backup restores the application, the export survives it.

**Retention** is governed by a purge age in days, off by default. Nothing is
ever deleted while the setting is off. When it is set, a record whose
cancellation or archival timestamp is older than the age is purged, and
purging an archived project takes its tasks, notes, time entries, and
dependencies with it. A batch raises an ephemeral notice naming how many
records went and how old they were. The purge is not an operation anyone can
invoke; it is a scheduled consequence of the setting. Ephemeral things do not
wait: a dismissed ephemeral notice and a finished or expired pending operation
are deleted immediately, and a dismissal record is dropped together with the
condition that created it. The operation log is kept forever in v1, and the
private confirmation log file needs its own retention rule because it is a new
artifact rather than part of the record.

The data location is visible, the timezone is configurable, and settings live
where the storage decisions put them.

Covers: SRC-RULE-DATA-2, T11-028, T11-056, T12-016, T13-050, T13-051, T034, T17-006, T17-007, T17-009, T17-011, T17-012, T17-016..T17-027, MAP-057, MAP-058, CTX-presence, CTX-export..CTX-purge

### The agent surface

An agent reaches the same rules as the human, through one tool per operation.
The surface runs only in `mcp serve`, a subcommand that owns standard input and
output for its whole lifetime, so the interactive command is never the same
process. No module may write to standard output in that mode; the logging
backend is pinned to standard error at warn level by default, because that is
the one failure that silently breaks the protocol.

The tool set is flat and mechanical:

- Names are snake case, prefixed by the noun they act on, such as
  `task_create` or `session_start`.
- Each tool carries a strict parameter schema of its own.
- Every identifier parameter accepts the bare integer or the display form, and
  a display form naming the wrong record kind is an error rather than a lookup
  into the other kind.
- Every failure returns a stable machine-readable code, a human message, and
  the offending entity or field, including `confirmation_unavailable`,
  `confirmation_denied`, and `confirmation_timeout`.
- A call that needs a human's approval does not execute. It records a pending
  operation with a two-digit code, delivered through the channel ladder above,
  the human gives the code to the agent, and the agent calls the confirmation
  operation with the operation identifier and the code. Three failed attempts
  kill the pending operation, and re-requesting creates a new one with a new
  code.
- Every read of a changing thing takes an optional cursor and returns the
  complete current state plus everything that changed since that cursor, with a
  fresh cursor for the next call. One general change feed returns everything
  since a cursor regardless of entity, grouped the same way.

An agent cannot bypass a rule. The rules it meets are the ones the command line
meets, because both call the same ports.

Covers: SRC-RULE-ACTIVITY-1..SRC-RULE-ACTIVITY-3, T02-032, T09-001, T09-002, T09-005, T09-006, T09-008..T09-015, T11-074..T11-077, T11-082, T11-084, T13-064, T005, T006, T007, T008, T009, T010, T011, T012, T014, T015, T017, T018, T019, T020, T035, T17-040, T18-023, T20-021, MAP-021, MAP-052, MAP-054, CTX-cursor..CTX-change-feed

### The desktop client

The desktop client is a JavaFX application inside the same Java process
discipline as everything else: one JVM, JavaFX 21 on the LTS line, Spring only
where a module needs it, and the core never depending on either. The measured
shutdown order is `Platform.exit()` on the FX thread, a latch, then
`context.close()`, because the Spring context does not close on `Platform.exit()`
and the shutdown hook ordering under a fat jar is not stable.

The window has five views: Today, Tasks, Projects, Review, and Settings. Today
is the primary output and shows the fit signal, the per-item reasons, and the
cut line. Core settings are reached through the core's configuration port; a
surface's own preferences, such as theme or window placement, are that
surface's business, and there is a port for the variant where the core
remembers them. The client offers to start the session in place when the user
starts work and no session is open.

The client stays a Java module. No second language, no compiler plugin, no
interop seam. Swing remains the fallback if JavaFX ever becomes unavailable on
a target platform, because it costs nothing to keep the frame portable.

Covers: SRC-RULE-ARCH-5, T04-012, T07-001..T07-003, T07-005..T07-007, T11-030, T11-031, T11-085..T11-087, T14-021, T036, T18-012, T22-001, T22-007, T22-010, MAP-018, MAP-069, MAP-C13, CTX-client

### The tray

The tray is an application decision, not a packaging one: every JavaFX tray
library wraps AWT, so the choice is which wrapper and how it degrades. v1 uses
`FXTrayIcon`, a thin wrapper whose module descriptor requires `java.desktop` and
`javafx.swing`, and whose support check is `Desktop.isDesktopSupported() &&
SystemTray.isSupported()`. That check comes first, because unguarded
construction throws `UnsupportedOperationException` and ends the application.

The popup shows the current timer, the next item, and the warnings that ask
something of the user, and offers the native actions. No notice level named
critical exists, so the popup's content is a warning threshold rather than a
retired severity. When no tray exists, the application degrades to the window
instead of failing.

The Linux tray is the weakest part of the choice: the library's own
documentation rates Linux partially supported, AWT initialization inside the GTK
application emits a `Gdk-WARNING` about `XSetErrorHandler`, and icon
appearance, native menu firing, and GTK and AWT stability with a real tray
cannot be verified without a desktop. A real desktop test across GNOME with and
without the app indicator, KDE Plasma, macOS, and Windows is an acceptance item
for the tray child effort, recording what the support check reports on each.

Covers: T06-006, T06-007, T06-009..T06-011, T06-014..T06-016, T11-089, T22-011, T22-013, T23-009, MAP-017

## User Stories

The person using the tool.

1. As someone with more work than hours, I want to capture a task from one
   line of typing, so that a request does not have to be organised before it is
   recorded.
2. As someone capturing a task, I want it to land in an inbox until I say it is
   ready, so that I can capture in a hurry and plan later.
3. As a person planning my week, I want to record how many net minutes each
   weekday gives me, so that the plan reflects the hours I actually have.
4. As a person starting my day, I want to start a session that knows when I
   plan to leave and how much break I need, so that today's capacity is a real
   number rather than an optimistic one.
5. As a person whose plans change, I want to move my leave time at any point in
   the day, so that a late start or an early finish is one statement, not a
   re-plan.
6. As a person taking a day off, I want to record it as vacation or flex
   against a date, including a past date, so that the month tells the truth
   afterwards.
7. As a person opening the tool in the morning, I want an ordered list of what
   to work on now, so that I never sort forty items by hand.
8. As a person reading that list, I want to see why each item is there, so that
   I can disagree with the order and know what to change.
9. As a person running out of time, I want a cut line showing what no longer
   fits today, so that I am not lied to about the rest of the day.
10. As a person looking a week ahead, I want a forecast that says whether a
    deadline is at risk, so that I renegotiate early rather than late.
11. As a person who has just been warned, I want the warning to name the
    deliverable and the reason, so that I know which lever to pull.
12. As a person who works better in the morning, I want a notice to reach me
    through the operating system when a deadline is close, so that I do not
    have to watch the window.
13. As a person who dislikes interruptions, I want quiet hours and per-type
    delivery settings, so that the tool tells me things when I am willing to
    listen.
14. As a person who dismissed a notice, I want it to come back if the condition
    returns, so that a dismissal is not a permanent mute.
15. As a person with a project that has slipped, I want to pause it, so that my
    list stops proposing work I cannot do yet.
16. As a person with work that depends on other work, I want the dependency
    recorded once, so that the order derives it rather than me remembering it.
17. As a person who tried to create a loop, I want the refusal to name the
    tasks in the loop, so that I can fix it in one move.
18. As a person completing work out of order, I want the tool to ask before it
    lets me, so that a deliberate exception is deliberate.
19. As a person estimating work, I want remaining effort derived from the total
    and the logged time, so that I only estimate once.
20. As a person who overran an estimate, I want to see the negative remaining,
    so that the mistake is visible instead of absorbed.
21. As a person setting a deadline, I want to say "by Friday" or "by 15:00"
    and have both mean something exact, so that a date and an instant are not
    confused.
22. As a person whose project has a deadline, I want each task to inherit the
    earlier of the two, so that project pressure reaches the work.
23. As a person starting the timer, I want it to refuse before my day has
    started, so that I do not accumulate time against a session that does not
    exist.
24. As a person pausing work, I want the task to stay in progress, so that a
    coffee break is not a state change.
25. As a person switching tasks, I want the previous one to return to ready, so
    that the list stays honest about what I am doing.
26. As a person who works on two things at once, I want that behind a setting
    that ships off, so that the default stays simple.
27. As a person correcting my time, I want to edit or delete an entry and see
    the plan follow, so that a fixed record is a fixed plan.
28. As a person who forgot to end the day, I want the tool to infer an end and
    ask me to confirm it, so that the record is roughly right without effort.
29. As a person reviewing my month, I want the hours I was there against the
    hours I planned, so that untracked work is not counted as debt.
30. As a person whose month is in arrears, I want the plan to catch up over the
    following days, so that a bad week does not become a bad quarter.
31. As a person protecting a day, I want a day with its own minutes never to be
    touched by the catch-up, so that an exception stays an exception.
32. As a person keeping records, I want to export a documented archive, so that
    my data survives the application.
33. As a person worried about loss, I want a backup that restores the
    application, so that a disk failure is recoverable.
34. As a person who cancelled work, I want it retained rather than deleted, so
    that history stays and a mistake is reversible.
35. As a person with a stale archive, I want an optional purge age that is off
    by default, so that deletion happens only when I asked for it.
36. As a person moving between machines, I want the data location visible and
    the timezone configurable, so that I can find my data and trust the times.
37. As a person who prefers the terminal, I want every capability reachable
    from the command line, so that the window is a convenience rather than a
    requirement.
38. As a person interrupted constantly, I want a tray icon with the current
    timer and the next item, so that a glance is enough.
39. As a person on a machine without a system tray, I want the application to
    fall back to the window rather than die, so that a missing feature is not a
    crash.
40. As a person with no network, I want the whole tool to work offline, so that
    a train is not an outage.
41. As a person who dislikes configuration, I want no first-run wizard and
    nothing written until I have a task, so that the tool starts empty and
    quiet.
42. As a person upgrading, I want every command, flag, and output shape to keep
    working, so that a new version is not a migration.
43. As a person browsing work, I want to filter and inspect tasks without the
    order changing, so that looking is not editing.
44. As a person with a long backlog, I want the tool to tell me it is over
    capacity rather than pretend, so that the mismatch is visible.
45. As a person on Linux, macOS, or Windows, I want the same product, so that
    my choice of desktop is not my choice of tool.

The agent working on the person's behalf.

46. As an agent, I want one flat tool per operation with a strict schema, so
    that I can call it correctly without guessing.
47. As an agent, I want identifiers accepted in either form, so that I can use
    what the user said.
48. As an agent, I want a stable failure code with the offending entity, so
    that I can correct myself instead of retrying blindly.
49. As an agent, I want a change feed with a cursor, so that I can stay current
    without polling everything.
50. As an agent proposing a destructive change, I want to receive a pending
    operation instead of acting, so that the human confirms it out of band.
51. As an agent, I want to read the same notices the human sees, so that I can
    act on what the tool has already concluded.
52. As an agent serving a user who is away from the machine, I want a clear
    unavailable-confirmation failure, so that I stop rather than invent consent.
53. As an agent, I want the rules enforced on me exactly as on the human, so
    that delegation is not escalation.

The operator of the deployment.

54. As an operator, I want one process to own the data, so that two clients
    cannot corrupt it.
55. As an operator, I want a daemon that runs without a window, so that the
    service exists whether or not someone is looking.
56. As an operator, I want a token required on a non-loopback bind and optional
    on loopback, so that exposure is a deliberate act.
57. As an operator, I want graceful stop to record the instant and leave open
    entries for correction, so that a restart is not a data loss.
58. As an operator, I want a lost daemon to offer takeover explicitly rather
    than silently change modes, so that a crash has one visible consequence.
59. As an operator, I want one OpenAPI document describing the REST API and the
    stream, so that a consumer is written from one source.
60. As an operator, I want the stream to resume from a cursor, so that a
    reconnect is not a full refetch.
61. As an operator, I want polling to remain available, so that a dropped
    stream is a latency problem rather than an outage.

Covers: SRC-2.1.1..SRC-2.1.25, SRC-4.3..SRC-4.28

## Implementation Decisions

### Build and modules

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

Covers: T01-022, T03-001, T03-003, T03-004, T03-012, T04-001..T04-004, T04-007, T04-009, T04-013, T04-015..T04-018, T06-001..T06-004, T06-008, T06-012, T06-013, T07-004, T07-008..T07-010, T09-004, T09-007, T11-001..T11-003, T11-007, T11-018..T11-020, T11-032, T11-035, T11-036, T11-038, T11-039, T11-044..T11-049, T11-073, T11-079..T11-081, T11-088, T11-090, T17-035, T17-036, T21-005, T22-002..T22-004, T22-006, T22-008, T22-009, T23-008, T24-020, MAP-007, MAP-014, MAP-016, MAP-019, MAP-025..MAP-027, MAP-029, MAP-C04, MAP-C07

### Storage and the record model

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

Covers: SRC-RULE-ID-1, SRC-RULE-STATE-1, SRC-RULE-RECALC-4, SRC-RULE-DATA-3, T04-006, T04-014, T05-051..T05-054, T11-010..T11-012, T11-050..T11-055, T12-001, T12-002, T12-005, T12-007, T12-009..T12-014, T12-029, T12-031..T12-033, T12-038, T021, T022, T023, T027, T028, T032, T033, T17-008, T17-010, T17-015, T17-028..T17-034, T17-037, T17-038, T17-041, T18-025, T20-023, MAP-031..MAP-033, MAP-059, CTX-note

### The command line surface

The shape is `workthread <area> <action> [arguments]`, with seven areas that map
one to one onto the surface ports: `task` on `ForCapturingTasks`, `project` on
`ForManagingProjects`, `plan` on `ForPlanningQueries` carrying today, forecast,
review, and notices, `timer` on `ForTrackingTime`, `override` on
`ForOverridingRules`, `capacity` on `ForDefiningCapacity` carrying the timetable
and time off, and `config` on `ForConfiguring` carrying timezone, settings, and
export. Two commands sit outside the areas because they are not port
operations: `mcp serve`, which hosts the agent surface, and `confirm <id>
<code>`, which completes a pending operation.

One command maps to one port operation, and the action is an imperative verb. No
command exists only to reshape data the core does not produce. Every later
command line effort names its commands inside these seven areas, and a new area
means a new surface port.

Output is text by default. One global `--format json` prints exactly one
documented JSON document per invocation, and `--format text` is the same as
omitting the flag. JSON is never coloured; text honours `NO_COLOR`. A command
that returns no data ignores the flag silently rather than refusing it. Errors
always go to standard error, and under `--format json` an error is a JSON
document on standard error carrying the same stable failure code.

Six exit codes, with the catch-all pinned low so added codes append above it:
`0` success, `1` reserved for an unknown or internal error where the caller may
still parse the output, `2` usage error meaning the invocation itself is wrong,
`3` validation refused meaning the input is not well formed, `4` rule refused
meaning well-formed input breaks a rule or a confirmation was unavailable, and
`5` service unreachable. The split that matters is three against four: malformed
input is the caller's mistake, a rule refusal is the product disagreeing.

Exactly three things need confirmation before they happen: a removal that
changes something already reported, an override, and completing a task with
incomplete prerequisites. All three use one mechanism. On a terminal the
command prints the two-digit code and reads it back. Without a terminal it
prints the pending operation's identifier and requirement in the requested
format and exits `4`, to be completed by `workthread confirm <id> <code>`. This
is the same handshake the agent surface uses, so there is one confirmation
mechanism with two presentations.

An identifier is accepted as the bare integer or as the prefixed display form,
with no fuzzy matching. Every command answers `--help`, `workthread help
<area>` lists one area's commands, and `--version` prints the version. Additive
versions mean a command, its flags, its JSON shape, and its exit codes keep
working once shipped, and the JSON envelope is fixed once by the first command
line effort; it carries both the bare integer and the display form of an
identifier.

Covers: T25-001..T25-043, MAP-013, MAP-C14

### The daemon and the API

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

Covers: SRC-RULE-ARCH-4, T03-011, T11-040..T11-042, T11-058..T11-063, T11-065, T11-078, T031, T18-001, T18-002, T18-004..T18-008, T18-010, T18-011, T18-013..T18-018, T18-022, T19-003, T19-014..T19-021, T20-001..T20-018, T24-015, MAP-024, MAP-030, MAP-060..MAP-067, CTX-daemon, CTX-split-mode, CTX-event-stream

## Testing Decisions

A good test drives the product through a port and asserts the observable result.
It does not reach into the domain to inspect a field, and it does not assert on
the shape of an internal call. The rules in this specification are the oracle:
each rule identifier names a behaviour a test can drive, which is why the rules
are stated as obligations rather than as descriptions.

- **`core`** is tested in isolation and fast, because it holds the work logic
  and every ordering, eligibility, closure, slack, and reconciliation rule.
  Identical inputs must produce identical output, so determinism is asserted
  directly: the same data with a shuffled input order must produce the same list.
- **`adapters`** are tested against a real SQLite file rather than a mock,
  because WAL, the ownership lock, migrations, and the change log are the
  behaviour. A deleted record must not hand its identifier to a new one, and a
  failed migration must leave nothing half-applied.
- **`contract`** is tested for compatibility: the types a plugin could depend on
  must stay minimal and versioned.
- **`cli`** is tested at the command level, including the six exit codes, the
  one-document-per-invocation rule for `--format json`, and the pending
  operation when no terminal is present.
- **`mcp`** is tested over standard input and output, including that nothing
  else writes to standard output in serve mode and that a failure carries its
  stable code.
- **`api`** is tested as a contract: every operation maps to a resource, the
  OpenAPI document describes the stream, and a stream reconnect resumes from a
  cursor.
- **`client`** gets a thin headless smoke layer through the JavaFX platform,
  enough to prove the frame builds and the shutdown order holds.
- **The tray** gets no automated test worth the cost. It gets a real desktop
  test across GNOME with and without the app indicator, KDE Plasma, macOS, and
  Windows, recording what the support check reports on each.

Prior art: the repository has no tests yet. The scaffolding effort establishes
the harness, and the store's round-trip and non-reuse tests should be the first
tick, because they prove the two properties every later test relies on.

The acceptance surface for v1 is the sixteen completion signals the
specification states: capture while the timer runs, the timetable and the
session, projects and tasks, the dependency graph, blocking with an override,
estimates and priorities and deadlines, the ordered list with explanations,
forecasts, start and pause and complete with the plan following, historical time
with remaining and forecasts and notices following, notices delivered by the
operating system, the tray popup, the agent surface, identical results across
surfaces, both service modes, and offline operation with export. Each must be
demonstrated on real data before 1.0, and the two that name the retired critical
level are read as the warnings that ask something of the user.

Covers: SRC-8.8.1..SRC-8.8.16

## Out of Scope

- Tracker plugins and attachments, which are v2 advisory work, along with the
  plugin rules and the two journeys that describe them.
- Date gating and recurrence, the two named changes after v1, in that order.
- Daily routine tasks and a server-hosted daemon.
- Calendar synchronization, billing, team or resource planning, kanban, chat, a
  plugin marketplace, and autonomous scheduling.
- Split working days: a weekday carries one window, so a future day cannot hold
  a morning block and an evening block.
- Task time as the month's accounting quantity: presence is what the month
  measures, which keeps untracked work from becoming debt.
- WebSockets, and a second description format: the live channel is server-sent
  events inside the one OpenAPI document.
- Pushed projections: the stream carries the cursor and notice events only.
- Manual pins, drag reordering, and protected focus blocks.
- Multi-day allocation, clock-time blocks, and any task split across days.
- Shell completion, which is v2.
- Import. v1 exports, and it restores by backup.

## Further Notes

**Releases.** Nine releases inside the pre-v1 run and then 1.0: 0.1 Capture, 0.2
The work model, 0.3 The day, 0.4 Risk, 0.5 Review and export, 0.6 Agents, 0.7
The daemon, 0.8 The window, 0.9 The tray, and 1.0 as the completion gate. Each
release is numbered and named by what a user can do with it, tagged before the
next opens, and exercised on real data rather than fixtures. No release is done
while a child effort inside it is unfinished.

**The order of work.** The command line stage runs first and spans several
efforts, because the backend grows to its final v1 stage inside it. Then the
agent surface, then the daemon, then the window, then the tray. The order is
what makes each stage shippable: an interface is added only after the capability
behind it is usable on real work.

**Why there is no day-by-day schedule.** The obvious design, allocating
clock-time blocks across future days, fails three ways. It pretends to know what
a day will look like a week out. It produces a plan that is wrong by mid-morning
and has to be rebuilt. And it makes the user maintain capacity that the
timetable already knows. A weekly minute budget plus a session per day carries
the same forecast with none of those costs, and the ordered list with a cut line
delivers what the block schedule was for: knowing what fits today.

**Traceability.** Every requirement this specification covers is a row in
`docs/v1/requirements-directory.csv`, ordered by when it was written and citing
the file and line it came from. Each section above carries a `Covers:` line
naming the rows it accounts for. The checker in `docs/v1/check-requirements.py`
fails when a kept requirement is covered by no section, covered twice, or cited
while not kept. The build record, including what the reviewers found, is
`docs/v1/catalog/triage.md`.

**The measured risks.** The Linux tray is the weakest part of the JavaFX
choice, because AWT and GTK coexist badly and a real tray cannot be tested
headlessly. Java 21 caps the usable JavaFX line at 23.0.2. Server-sent events in
the OpenAPI document depend on the documentation generator's line, which is why
`itemSchema` waits for OpenAPI 3.2 output. Image size and cold start were
measured on this project rather than assumed.
