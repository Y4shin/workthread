# Itinerary Planner: Product Specification

A self-contained description of the intended v1 product, written so that a new team can implement it on any technology stack from this document alone.

**Status:** Implementable draft. Sections 1 through 6 and Section 8 describe the intended v1 product, with v2 advisory material clearly marked. Sections 7 and 10 are v2 advisory and are not binding on v1. Section 9 records product decisions that the current product definition does not settle. Those decisions MUST be resolved before the specification can be called complete.

## How to read this document

- Sections 1 and 2 define the product and its boundaries.
- Section 3 defines the vocabulary and the state model.
- Section 4 is the main body: actors, goals, interactions, outcomes, failure paths, and acceptance scenarios.
- Section 5 defines the shared rules that every story depends on.
- Sections 6 and 8 define the service architecture, data behavior, and quality expectations.
- Section 7 is v2 advisory tracker integration. It is retained for context and is not a v1 requirement.
- Section 9 lists unresolved product decisions with alternatives and consequences.
- Section 10 covers v2 advisory daily routines and future server hosting. It is not a v1 requirement.

**Normative language.** The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY, and OPTIONAL in this document are to be interpreted as described in RFC 2119. They are capitalized when used as normative keywords. Lowercase words, including "can", "cannot", "may", "should", "required", "recommended", and "optional", are ordinary prose. In particular, "can" describes a user capability and is not an RFC 2119 keyword.

---

## 1. Product purpose and intended users

### 1.1 The question the product answers

Itinerary Planner is a local-first application for a developer who accumulates many small commitments, review requests, feature ideas, and follow-ups. These items are often too small, too uncertain, or too interruptive to capture in a ticketing system at the moment they arrive. The product gives them one fast place to land, keeps them visible, and turns them into an automatically ordered plan.

The product is also a planning layer over work that already lives in a ticketing system. In v1, a task can carry lightweight source metadata that points to the external item, so the user can see that the work is tracked elsewhere. Read-only tracker context that reflects external items more richly is a v2 advisory capability in Section 7.

The primary question is:

1. What should I work on now?

Supporting questions are:

2. What have I captured that I might otherwise lose?
3. Which deadlines are becoming unsafe?

The itinerary is the primary product. Forecasts and warnings are a secondary, supporting view. The product turns tasks, projects, estimates, dependencies, deadlines, priorities, logged work, and recurring availability into:

- An **itinerary**: an automatically ordered, capacity-constrained projection of work. The user tells the system what the work is and what properties it has. The system owns the order and the plan. The user does not maintain a separate schedule.
- **Supporting forecasts and warnings**: a secondary explanation of which commitments can still be met, which are tight, which are already infeasible, and why.

Every time the user or an agent changes work, time, capacity, or constraints, the itinerary and the supporting forecasts are recomputed.

### 1.2 Intended users

**Primary user.** A regular developer at a company who manages several active projects and receives work from colleagues, reviews, bug reports, feature ideas, and personal follow-ups. Work often arrives as "please review this" or "we should add this" at a moment when acting on it would interrupt the current task. The user needs one-step capture, reliable visibility, and an automatically ordered plan. The user is not necessarily an independent worker or a full-time planner.

**Agent user.** A local MCP client acting on the user's behalf. Agents are first-class users for reading planning state and for making explicit, targeted changes. An agent MAY perform a manual override or a scheduling input edit on the user's behalf when the user asks for it. Agents are not autonomous schedulers: the application's rules decide the itinerary by default, and agents MUST NOT make arbitrary, implicit, or unattributed changes.

**Not the intended user.** A team, manager, or organization allocating work across people; a user who wants a collaborative board, chat, cloud workspace, or calendar client; a user who wants the application to make unrequested scheduling decisions with AI.

### 1.3 Product principles

**P1. Automatic, deterministic, and explainable ordering.** The user supplies what the work is and what properties it has. The system decides the order. The same inputs, current time, timezone, and horizon MUST produce the same itinerary. Every scheduled item MUST explain why it was selected. The scheduler MUST NOT be an opaque optimizer, and the user MUST NOT have to maintain the itinerary by hand.

**P2. Dependencies are protected by default.** A task MUST NOT be scheduled before its incomplete prerequisites by default. The dependency graph is a hard constraint unless the user makes an explicit, recorded override under P9.

**P3. Deadline safety outranks ordinary priority.** When forecasts are used, work that protects a feasible deadline MUST come before less urgent work with a higher ordinary priority. Priority breaks ties after deadline and dependency pressure. The itinerary remains useful when no deadlines exist.

**P4. Actual work is authoritative.** Logged time, revised estimates, completion, and blocking change future projections immediately. Historical logs remain editable. An estimate overrun is reported, not silently absorbed by changing the estimate.

**P5. One source of truth.** The desktop interface, the tray popup, and the agent interface all use the same planning rules and the same authoritative local data. A surface MUST NOT compute its own schedule or keep a divergent plan.

**P6. Local first and offline capable.** All planning data is stored on the user's computer. The core product works without an account, sign-in, or network connection. v1 supports both a local daemon mode and a single-process mode. A server-hosted daemon reachable over a network is a future possibility, not a v1 requirement.

**P7. Agents are first-class but constrained.** Agents can read and write work through a local MCP interface. They can perform explicit manual overrides and scheduling input edits on the user's behalf. Changes use explicit identifiers; they MUST NOT use fuzzy targets or implicit batches. Destructive actions, out-of-order completion, and rule overrides require explicit confirmation. Every agent change is attributable to the requesting user.

**P8. Small, focused surface.** The first release is a focused planning workflow, not a project-management suite. It does not include boards, chats, collaboration, cloud sync, calendar synchronization, or dozens of workflow states.

**P9. Rules are enforced by default, but they do not stand in the way.** Default behavior follows the product rules. When a rule blocks something the user genuinely needs to do, the application MUST offer an explicit override that is reachable without too much trouble and is never the default or obvious choice. An override explains the rule and its consequence, requires confirmation, is recorded, and remains visible. Overrides MAY relax dependency order, capacity for the current day, or the system-owned order, but they MUST NOT schedule an inbox task, a task without a positive estimate, or a task in a paused, completed, or archived project, and they MUST NOT weaken security, data integrity, or another person's data.

**P10. The application does not get in the way.** Using the app SHOULD make life easier, not harder. Common use cases MUST be fast and reachable with few steps. Where it is safe, the application MAY help proactively, for example by suggesting a missing estimate or offering to capture a task, but it MUST NOT change task properties or data without a request.

**P11. The itinerary comes first; forecasts support it.** The itinerary is the main product. Forecasts, risk values, and warnings MUST support the itinerary and MUST NOT be presented as an equal or competing product.

### 1.4 What the product is not

It is not a calendar, a billing or time-invoicing tool, a team resource planner, a kanban board, a Gantt chart, a chat application, or an autonomous AI project manager. It does not replace an external issue tracker. In v1 it does not write to a tracker. Read-only tracker context is a v2 advisory capability. A server-hosted multi-user service is not part of v1.

---

## 2. Scope and explicit non-goals

### 2.1 Capabilities in v1

- Quick capture of small tasks with only a title, so that an idea, review request, or follow-up can be recorded without interrupting the current task and without requiring a project, estimate, or deadline.
- Projects with title, priority, optional deadline, and status.
- Tasks with title, optional description and project, priority, estimate, optional deadline, source metadata, status, and dependencies.
- An inbox for captured tasks that are not ready to schedule, including tasks without an estimate.
- Automatic ordering by the system. The user supplies task properties and does not maintain a separate schedule or manually sort the itinerary.
- Dependencies with self-edge, duplicate, and cycle prevention, including a displayed cycle path. A self-edge is a dependency in which a task is its own predecessor; it is invalid because it can never be satisfied.
- Derived blocking from incomplete prerequisites, without allowing users or agents to set a blocked status directly.
- Explicit, confirmed, recorded overrides for rule-blocked actions, limited to dependency order, capacity for the current day, and system-owned order. Overrides relax those constraints but never schedule an inbox task, a task without an estimate, or a task in a paused, completed, or archived project, and never weaken security or data integrity.
- Recurring weekly working availability with zero or more intervals per weekday. Each interval includes a pause budget in minutes.
- Date-specific availability exceptions: a full day off, or replacement intervals for one date.
- A configured local timezone and a configurable planning horizon.
- A deterministic, capacity-constrained itinerary that subtracts pause budgets from available capacity and is ordered by deadline and dependency pressure.
- Per-item machine-readable reasons and human-readable explanations.
- Supporting task and project forecasts with effective deadline, projected finish, slack, risk, and explanation. Forecasts are secondary to the itinerary.
- Actionable warnings for tight and missed deadlines, estimate overruns, blocked deadline paths, excess planned work, and missing availability.
- Operating-system notifications for critical warnings and estimate overruns, plus approaching deadlines once DEC-16 defines that trigger, with user control over which notifications are enabled.
- One active timer, live elapsed time, and historical time entries that can be added, edited, and deleted.
- Remaining effort derived from the estimate and logged work, with explicit overrun handling.
- A desktop interface with Today, Tasks, Projects, Review, and Settings.
- A tray icon, a compact tray-attached popup, and native tray actions.
- A local MCP interface for agents with read and write capabilities, explicit overrides and scheduling input edits, and a change feed that reports what changed since a caller-supplied cursor.
- Two supported service modes in v1: a local daemon that serves an API and an MCP interface to desktop, tray, and agent clients, and a single-process mode in which one process owns the service, desktop window, and tray while MCP is the only external interface.
- A headless, always-running local service mode, independent of the desktop window, when the daemon mode is used.
- Local data storage, a visible data location, and an export action.
- Linux, macOS, and Windows desktop operation.

The itinerary is the primary v1 outcome. Forecasts, deadline risk, and warnings are supporting capabilities. They MUST NOT be treated as an equal or competing release goal.

### 2.2 Explicit non-goals for v1

- Multi-user collaboration, shared workspaces, accounts, sign-in, or cloud sync.
- Calendar-provider synchronization, calendar import, or calendar export.
- Arbitrary recurring task rules. Daily start and end of workday routines are a v2 advisory candidate in Section 10; arbitrary recurrence is not v1.
- Kanban boards, Gantt charts, or timeline drag-and-drop.
- Remote writes to external trackers, mirroring tracker state, or treating a tracker item as authoritative for local planning.
- Automatic AI scheduling decisions when no agent or user has explicitly invoked an operation.
- Billing, invoicing, cost tracking, or people and resource allocation.
- Multiple simultaneous timers or parallel-work accounting.
- Multi-device conflict resolution.
- Production third-party tracker integrations as a release prerequisite.
- Plugin auto-download, a plugin marketplace, or background tracker polling.
- Encryption, synchronization, import, or recovery capabilities beyond what Section 8 states.
- A server-hosted daemon reachable over a network. This is a future possibility, not v1.

### 2.3 Deferred, not promised

The following ideas are deliberately left open for later consideration but are not v1 requirements: manual itinerary pins, protected focus blocks, a user-owned itinerary order, multiple active timers, arbitrary recurring tasks, calendar integration, cloud sync, multi-device support, server-hosted daemon access, and a plugin marketplace. They do not form a roadmap. A fresh implementation MUST NOT add them in the name of completeness.

### 2.4 Scope boundaries that are easy to confuse

- **Recurring availability is not a recurring task.** Availability describes when work can happen. v1 does not create recurring task instances. A daily routine task anchored to the start or end of the workday is a v2 advisory candidate in Section 10.
- **A warning is not a notification.** A warning is first-class planning state shown in the interface. A notification is a delivery mechanism. v1 supports both: warnings appear in the application and tray, and enabled notification categories, including critical warnings and estimate overruns, can also produce operating-system notifications.
- **A task list is not a user-created list.** The product has a Tasks view. It does not have user-created lists, folders, or boards as planning entities. "List" in this specification means a view of tasks.
- **Tracker context is not synchronization.** A v2 attachment is a local, cached link. It does not make the tracker authoritative and does not write back. v1 records only lightweight source metadata.
- **A forecast is not a guarantee.** It is a projection from current data and current capacity.
- **An override is not a silent bypass.** An override is explicit, confirmed, recorded, and visible. It does not change the underlying data to pretend the rule was satisfied.

### 2.5 V2 advisory candidates

The following capabilities are described for context but are not v1 requirements: read-only tracker plugins and attachments (Section 7), daily routine tasks anchored to the start or end of a workday (Section 10.1), and a server-hosted daemon reachable over a network (Section 10.2). A v1 implementation is not bound by these sections.

---

## 3. Domain concepts and vocabulary

### 3.1 Core concepts

| Concept | Meaning |
| --- | --- |
| **Task** | A unit of work with a title, optional description and project, priority, estimate, optional deadline, source metadata, status, dependencies, and time entries. |
| **Project** | A grouping of tasks with its own title, priority, optional deadline, and status. A task MAY have no project. |
| **Inbox** | A holding state for captured tasks that are not ready to schedule, often because they lack an estimate. Quick capture creates an inbox task. Inbox tasks MUST NOT be scheduled. |
| **Quick capture** | A one-step action that records a task with only a title, without interrupting the active task or requiring a project, estimate, or deadline. |
| **Pause budget** | Minutes reserved inside an availability interval for breaks that the user takes without scheduling them in advance. It is subtracted from capacity and is not an itinerary item. The reserve is a planning allowance, not a balance tracked against actual breaks. |
| **Override** | An explicit, confirmed, recorded exception that lets a user or agent proceed when a planning rule would block an action. An override relaxes one of three constraints: dependency order, capacity for the current day, or system-owned order. It does not weaken security or data integrity. |
| **Scheduling input edit** | An explicit, validated change to a task's priority, project, estimate, deadline, or source. It changes the inputs the rules use and never sets an allocation, an itinerary position, or a rule exception. |
| **Estimate** | The planned effort for a task, expressed in minutes. See Section 9, DEC-1, for the unresolved total-versus-remaining question. |
| **Logged work** | The sum of closed time entries for a task plus the live elapsed time of its active timer. |
| **Remaining effort** | `max(estimate - logged work, 0)`. Only logged work changes it. An unfinished task with zero remaining effort receives a five-minute completion slot when it is otherwise eligible. |
| **Unallocated effort** | Within one projection, stored remaining effort minus minutes already placed for the task earlier in the same projection. It is derived per projection and never stored. |
| **Dependency** | A directed edge stating that a predecessor task MUST be completed before a successor task can begin. Dependencies are protected by default and MAY be relaxed only by an explicit override. |
| **Dependency closure** | For a deliverable, the deliverable plus all unfinished prerequisites, recursively, with each task counted once. |
| **Deadline** | An absolute instant by which a task or project should be done. It is nullable. |
| **Effective deadline** | For a task, the earlier of its own deadline and the deadline of its active project. A project's effective deadline is its own deadline. |
| **Availability** | Recurring weekly working intervals per weekday, plus date-specific exceptions. Each interval has a pause budget. |
| **Capacity** | Concrete schedulable intervals after availability, exceptions, pause budgets, elapsed time, and the planning horizon are applied. |
| **Itinerary** | The automatically ordered projection of task allocations across capacity. It is the primary product output, generated from current data, not an authoritative stored plan. |
| **Forecast** | A supporting view for a task or project: effective deadline, projected finish, slack, risk, and explanation. Forecasts are secondary to the itinerary. |
| **Deadline risk** | One of `safe`, `tight`, `missed`, `blocked`, or `unscheduled`. |
| **Warning** | A first-class, automatically resolved condition such as a missed deadline, an estimate overrun, or missing working hours. |
| **Notification** | A delivery of a selected warning or approaching deadline to the operating system. A notification is not the warning itself and MUST NOT be the only place the warning appears. |
| **Time entry** | A recorded span of work against a task, with a start, end, positive duration, optional note, and origin (timer, manual entry, or agent). |
| **Active timer** | The single currently running time entry. Only one MAY run at a time. |
| **Attachment (v2 advisory)** | A local link from a task to a remote tracker item, with cached title, status, URL, and refresh time. Context only. Not part of v1. |
| **Tracker plugin (v2 advisory)** | A locally installed adapter that lets the application search and fetch items from one configured external tracker, read-only. Not part of v1. |
| **Eligible task** | A task that may be allocated under the eligibility rules in Section 5.3. |
| **Allocation** | A scheduled block of a task within capacity, with a start, end, duration, reason, and explanation. |
| **Agent** | A local MCP client acting on the user's behalf. |
| **Change cursor** | An opaque position in the agent-visible change feed. An agent can ask what changed since a cursor instead of re-reading all planning state. |
| **Application service** | The single owner of validation, authoritative data, scheduling, forecasting, warnings, and recalculation. It runs either as a local daemon or inside the single application process. |

### 3.2 Task attributes

A task has:

- A stable opaque identifier.
- A title of 1 to 500 characters.
- An optional description.
- An optional project reference.
- A priority from 1 to 5, default 3.
- An estimate in positive whole minutes. It is required before a task can be ready or in progress. Inbox, completed, and cancelled tasks MAY lack one.
- An optional deadline.
- Source metadata: a source type defaulting to "manual", an optional source reference, and an optional source URL. This is lightweight provenance, not a tracker attachment.
- A stored status: `inbox`, `ready`, `in_progress`, `completed`, or `cancelled`.
- A completion timestamp when completed.
- Creation and update timestamps.
- Zero or more dependency edges and time entries.
- Tracker attachments and routine metadata are v2 advisory and are not part of the v1 task record.

A task MAY have no project. A task that has a project MUST reference an existing project.

### 3.3 Project attributes

A project has:

- A stable opaque identifier.
- A title of 1 to 200 characters.
- A priority from 1 to 5, default 3.
- An optional deadline.
- A status: `active`, `paused`, `completed`, or `archived`.
- Creation and update timestamps.
- Associated tasks.

A project is an organizing and deadline-bearing concept. It is not a user-created board, list, or folder.

### 3.4 Priority

| Value | Label | What it means for scheduling | Example |
| --- | --- | --- | --- |
| 1 | Critical | Interrupt or move planned work. Delay has a serious, concrete consequence. | Restore a broken checkout while customers are unable to pay. |
| 2 | High | Protect time for it and move lower-priority work if needed. Delay would materially affect a commitment or block important work. | Approve a design so engineering can start a committed project. |
| 3 | Normal | Give it a slot in the regular plan. A short delay is manageable. | Draft the next team update. |
| 4 | Low | Schedule it when higher-priority work is covered. It can roll into a later planning window at little cost. | Clean up outdated tags in the task tracker. |
| 5 | Optional | Use spare capacity. It is acceptable to leave it unscheduled. | Try a new layout for a dashboard that already works. |

Lower numbers are more important. Priority is a tie-breaker after deadline safety and dependency constraints. A task's own priority takes precedence over its project's priority when both are present. A project's priority is used only as a later tie-breaker for its tasks. The labels are ordinary product vocabulary, not RFC 2119 keywords. The meanings guide the user and the explanation, but priority never overrides dependencies or deadline pressure.

### 3.5 Task states and derived blocking

| State | Meaning | User-settable |
| --- | --- | --- |
| `inbox` | Captured but not ready to schedule. MAY lack an estimate. | Yes |
| `ready` | Schedulable. Requires a positive estimate. | Yes |
| `in_progress` | The task the user is currently working on. Requires a positive estimate. At most one task MAY be in this state. | Yes, through starting work |
| `completed` | Done and excluded from future planning. Completion timestamp is recorded. | Yes |
| `cancelled` | Abandoned and excluded from future planning. Time entries remain. | Yes |
| `blocked` | Derived state: at least one prerequisite is unfinished. MUST NOT be set directly by a user or agent. | No |

Blocked is an overlay on the task's stored state. A task can be stored as `ready` or `in_progress` and still be reported as blocked when a prerequisite is unfinished. A blocked task is not actionable and is not selected by the scheduler until its prerequisites are completed or have been allocated to finish first. A task in a paused, completed, or archived project is not blocked in this sense; it is simply ineligible for scheduling while the project is in that state. A user or agent MAY force work on a blocked task only through the explicit override rules. The task remains reported as blocked; the override does not pretend that the prerequisite was satisfied.

State rules:

- Quick capture creates an inbox task with only a title.
- Creating a task without a status creates an inbox task.
- Moving a task from inbox to ready requires a positive estimate.
- Starting work moves the task to in progress and makes it the single current task.
- Pausing stops the timer but keeps the task current and in progress. This convention is recorded in DEC-13.
- Starting another task ends the previous task's timed span and returns the previous task to ready unless it was completed or cancelled. This convention is recorded in DEC-13.
- Completing a task records a completion timestamp. Moving a task out of completed clears the timestamp and makes it plannable again.
- Cancelling a task excludes it from planning and preserves its time entries. Cancelling the active task stops its timer. A cancelled task can be reopened by changing its status to ready; it then requires a positive estimate and normal eligibility.
- A completed predecessor satisfies its dependents. A cancelled predecessor does not: its dependents remain blocked until the dependency is removed or the predecessor is completed.
- Adding a dependency to work already in progress does not stop the user's current timed span, but the task is reported as blocked and the scheduler will not select it again until the prerequisite is satisfied, unless the user makes an explicit override.

### 3.6 Project states

| State | Meaning | Effect on tasks |
| --- | --- | --- |
| `active` | The project is current. | Tasks may be scheduled. The project deadline applies to its unfinished tasks. |
| `paused` | The project is temporarily set aside. | Tasks are not scheduled. The project is not reactivated automatically. |
| `completed` | The project's intended work is done. It remains in project history and in exports. | The project contributes no future tasks to the itinerary. Completing a project does not automatically complete its tasks. |
| `archived` | The project is retired from the active workspace without claiming that its work is complete. It is hidden from the default project list but retained for history and export. | The project contributes no future tasks to the itinerary. An archived project can be reactivated later. |

The practical difference is intent and visibility. Completed means the project's work is done. Archived means the project is set aside, possibly with unfinished work. Both are non-schedulable. Both preserve their tasks and history. A completed project with unfinished tasks can be reopened; an archived project can be reactivated.

Changing a project's status does not delete its tasks and does not stop an active timer; the user or agent stops the timer explicitly. A project deadline change may create a missed forecast. The application MUST report the risk, not reject the change.

### 3.7 Deadlines

A deadline is an absolute instant. The application displays it in the configured local timezone. A task deadline MAY be later than its project's deadline. The effective deadline is always the earlier of the two, so the project deadline remains binding. A task with no own deadline inherits an active project's deadline. A task with no project and no deadline has no effective deadline.

This specification does not settle whether a user-entered calendar date means the end of the local day or another instant. See DEC-2.

### 3.8 Availability and capacity

Availability has two layers:

1. **Weekly rules:** zero or more local-time intervals for each weekday. For example, Monday 09:00 to 12:30 and 13:30 to 17:30. Each interval has a start, an end, and a pause budget in minutes. Intervals MUST end after they start and MUST NOT overlap. The pause budget MUST be zero or greater and less than the interval duration. A typical lunch break is a 30 to 60 minute pause budget on the day's main interval. v1 interprets each interval within one local day; see DEC-12.
2. **Date exceptions:** at most one per local date. An `unavailable` exception removes all capacity on that date. A `replace_intervals` exception replaces that date's normal weekly intervals with the exception's intervals. Replacement intervals also have pause budgets. Setting an exception for a date creates or replaces the existing exception.

The pause budget is reserved for breaks the user takes without scheduling them in advance. It is not an itinerary item and is not placed at a fixed time. The user decides when to take the break. The itinerary schedules at most the capacity that remains after the reserve, so a break never has to be planned ahead. The reserve is a planning allowance, not a tracked balance: a break shorter than the budget leaves capacity unused, and a break longer than the budget consumes planned work time, so later items slip and may become unscheduled or missed. If the user works through the reserve, the extra time is actual logged work, not an automatic change to the availability configuration.

Capacity is the concrete set of schedulable instants produced by applying the weekly rules and exceptions over the planning horizon, subtracting each interval's pause budget, and then removing elapsed time. If the current time is inside a working interval, capacity begins now and the interval's full pause budget stays reserved. If it is before the interval, capacity begins at the interval start. If it is after the interval, that interval contributes nothing. Capacity is sorted and merged so the itinerary sees contiguous intervals. Local wall-clock times are authoritative across daylight-saving transitions, so the actual elapsed minutes in an interval reflect the offset change. The application displays pause budgets separately from allocated work.

The default planning horizon is 90 calendar days in the configured local timezone. The user can configure a horizon from 7 to 365 calendar days. The itinerary covers the horizon or stops earlier when all eligible work is allocated. Section 9, DEC-5, records the unresolved treatment of deadlines beyond the horizon.

### 3.9 Itinerary

The itinerary is a chronological sequence of allocated work. Each item has:

- The task.
- A start instant and end instant.
- An allocated number of minutes.
- A machine-readable reason.
- A human-readable explanation.

The itinerary is a projection. It is not the authoritative record of what happened and MUST NOT be edited directly. Completing work, logging time, changing an estimate, changing a deadline, changing a dependency, or changing availability causes a new projection.

The itinerary is grouped by working interval in the Today view. An item MUST NOT extend outside capacity. Work longer than the allocation chunk is split into multiple items. The system owns the order. The user does not maintain the itinerary, and v1 does not support manual pins or drag reordering. An explicit ordering override MAY move a chosen task earlier, including ahead of work already scheduled, or treat it as next; it applies to one action, is recorded and visible, and does not become a hand-maintained plan.

### 3.10 Forecasts and risk

A forecast describes whether a task or project can finish by its effective deadline, given the dependency closure and the capacity before that deadline. Forecasts are a secondary, supporting view. The itinerary remains the primary output when no forecast exists or when the user ignores forecasts. A forecast contains:

- The entity type and identifier.
- A projected finish instant, or null when no finish can be projected within the horizon.
- The effective deadline, or null when there is none.
- Slack in minutes, or null when there is no deadline.
- A risk value.
- An explanation.

Risk values:

- `safe`: a projected finish exists and slack is at least 15 percent of the closure's remaining work, or there is no deadline and a finish exists.
- `tight`: a projected finish exists and slack is non-negative but less than 15 percent of the closure's remaining work.
- `missed`: the deadline has passed with work remaining, or slack is negative.
- `blocked`: the closure contains an unresolvable prerequisite, such as a cancelled, inbox, missing, or otherwise ineligible prerequisite. A blocked deliverable MUST NOT be reported as safe. Blocked is reserved for dependency-path problems.
- `unscheduled`: there is remaining work but no projected finish within the planning horizon, including when the deliverable itself is not eligible for scheduling (for example, an inbox task, a missing estimate, or a task in a paused, completed, or archived project), and the condition is not blocked or missed.

Section 5.8 defines the exact definitions and precedence. Section 9, DEC-6, records the overlap between missed and unscheduled. Section 9, DEC-15, records the choice between blocked and unscheduled for ineligible work.

### 3.11 Warnings

A warning is a first-class condition, not a transient toast. It has a severity (`critical`, `warning`, or `info`), a type, an optional entity reference, a message, a creation time, and a resolution time. A warning resolves automatically when a recalculation finds the condition false. It is stable for the same condition and entity, so it updates rather than duplicating.

Warning types:

| Type | Meaning |
| --- | --- |
| `deadline_tight` | A task or project forecast is tight. |
| `deadline_missed` | A task or project forecast is missed. |
| `estimate_overrun` | Logged work exceeds the task's estimate. |
| `blocked_deadline_path` | A deadline-bearing task or project depends on work that cannot be scheduled. |
| `plan_over_capacity` | Total schedulable work exceeds the capacity available in the planning horizon. |
| `no_working_hours` | No working availability is configured, so no credible itinerary can be produced. |
| `dependency_cycle_prevented` | A dependency change was rejected because it would create a cycle. Its persistence is unresolved; see DEC-9. |

A notification is a delivery of a selected warning or approaching deadline to the operating system. Notifications are subject to user preferences and quiet hours. A notification MUST NOT be the only place a warning appears. The warning remains visible in the application and tray.

Section 5.13 defines warning triggers, severity, and notification behavior.

### 3.12 Time entries and the active timer

A time entry records actual work against one task. It has a stable identifier, the task, a start instant, an end instant, a duration of positive whole minutes, an optional note, an origin (`timer`, manual entry, or agent), and creation and update timestamps.

While a timer runs, the entry is open: it has no end yet, and its elapsed duration is calculated from the current time. Closing the timer sets the end and duration. At most one entry MAY be open at a time.

Manual entries cover past or current work, not future work. A manual entry MUST NOT overlap the active open entry. It MAY overlap a closed entry, but the application MUST report a non-blocking notice, because retrospective logging may intentionally cover parallel work. Parallel-work accounting itself is not a v1 feature. Pause budget is a planning reserve, not logged work; time spent on a break is not counted as work unless the user logs it explicitly.

Editing or deleting a historical entry changes logged work and therefore remaining effort, the itinerary, forecasts, and warnings. Deleting a time entry is a destructive action and requires explicit confirmation.

### 3.13 External tracker attachments (v2 advisory)

This section is v2 advisory. v1 does not implement attachments or tracker plugins. The content is retained so a future implementation has context.

An attachment links a local task to a remote work item such as an issue, ticket, pull request, or task. It stores the local task, the plugin identity, the remote identity, an optional remote URL, the cached remote title, status, and update time, and the attach and last-refresh times. An attachment requires a remote identity and a title. URL, status, and update time are optional.

An attachment is contextual. The local task remains authoritative for title, estimate, priority, dependencies, status, and time tracking. Remote status MUST NOT automatically complete, cancel, re-estimate, or re-prioritize a local task. Changing a local task MUST NOT write to the remote tracker. Attachment changes do not trigger itinerary recalculation. Completing or cancelling a task does not remove its attachments or time entries; they remain for history unless the user explicitly detaches an attachment.

### 3.14 Identifiers, time, and ordering

Every task, project, dependency, time entry, availability exception, override, and v2 attachment has a stable opaque identifier. Users and agents address records by identifier. Display titles are not identifiers and are not guaranteed unique. A change cursor identifies a position in the agent-visible change feed.

Stored timestamps are absolute instants. Display uses the configured local timezone. Weekly availability and exception dates are local calendar concepts. The implementation MAY choose the exact representation used by the agent interface; the product requires stable identifiers and unambiguous instants.

Lists and projections MUST have deterministic order. User-facing task and project lists MAY be sorted by the user, but the default order MUST be stable. The itinerary order is defined by Section 5.9.

---

## 4. User journeys and capability specifications
Each story below has an actor, a goal, a reason the capability matters, starting conditions, the main interaction, the observable outcome, alternative or failure paths, acceptance scenarios, and the shared rules it uses. Stories 4.21 and 4.22 are v2 advisory and are not v1 requirements. All other stories in this section are v1.

### 4.1 Set up timezone, working hours, and availability exceptions

**Story SETUP-1. Working time.** As a worker, I want to define when I am available, how much break time to reserve, and which days are exceptions, so that the itinerary uses my real capacity rather than an invented schedule.

**Starting conditions.** The application is installed. No account or network connection is required. The user may have no availability configured.

**Main interaction.** In Settings, the user selects a local timezone, then defines zero or more working intervals for each weekday. Each interval has a start, an end, and a pause budget for breaks. The user can add a date exception that makes a specific local date unavailable, or replaces that date's normal intervals with a different set, including replacement pause budgets. The user can view the configured intervals, pause budgets, and exceptions.

**Observable outcome.** The application stores the intervals, pause budgets, and exceptions and uses them for every itinerary and forecast. Multiple intervals on the same day are allowed, and the gap between them is not capacity. A pause budget is reserved for unscheduled breaks and is subtracted from the interval's capacity; it is not an itinerary item and is not placed at a fixed time. A day off removes all capacity on that date. A replacement exception supplies only the replacement intervals on that date. Capacity that has already elapsed is excluded. The application displays capacity and pause budgets in the configured timezone.

**Alternative and failure paths.** An interval that ends before or at its start is rejected with field-level feedback. Overlapping intervals are rejected. A negative pause budget, or a pause budget greater than or equal to the interval duration, is rejected. An invalid weekday, invalid date, unknown exception kind, or a replacement exception with no intervals is rejected. A failed availability write leaves the previous configuration intact. If no availability is configured at all, the Today view shows a persistent setup warning and does not fabricate an itinerary.

**Acceptance scenarios.**

- **Given** the user has no working hours, **when** they open Today, **then** the application shows a setup warning explaining that no itinerary can be produced, and shows no invented work.
- **Given** Monday has intervals 09:00 to 12:30 and 13:30 to 17:30, **when** the user views Monday's capacity, **then** both intervals appear and 12:30 to 13:30 is not capacity.
- **Given** a date is marked unavailable, **when** the itinerary is computed for that date, **then** no work is placed on that date.
- **Given** a date has replacement intervals 14:00 to 16:00, **when** the itinerary is computed, **then** only 14:00 to 16:00 provides capacity on that date.
- **Given** the user enters two overlapping intervals, **when** they save, **then** the application rejects the change with field-level feedback and keeps the previous availability.
- **Given** the user is currently inside a working interval, **when** the itinerary is computed, **then** capacity begins at the current time rather than at the interval's original start.
- **Given** an eight-hour interval with a 60-minute pause budget, **when** the itinerary is computed, **then** seven hours of work capacity are available and no lunch break appears as an itinerary item.
- **Given** the same interval, **when** the user takes a break without changing availability, **then** the break is not scheduled in advance and the pause budget already reserved the time.
- **Given** a 60-minute interval, **when** the user enters a pause budget of 60 minutes or more, **then** the application rejects the interval.

**Rules used.** RULE-TIME-1, RULE-VAL-1, RULE-CAP-1 through RULE-CAP-8, RULE-WARN-4.

### 4.2 Configure planning horizon and application preferences

**Story SETUP-2. Planning preferences.** As a worker, I want to configure the planning horizon, service mode, notifications, and tray behavior, so that the planner matches how far ahead I plan and how I use the application.

**Starting conditions.** The application is installed.

**Main interaction.** In Settings, the user sets the planning horizon between 7 and 365 calendar days. The default is 90 days. The user can see the local data directory and start an export. The user can choose the daemon or single-process service mode and can enable start at login where supported. The user can configure notification preferences: critical warnings, estimate overruns, approaching deadlines once DEC-16 defines that trigger, quiet hours, and platform permission. The user can configure tray preferences: show the active timer in the tray tooltip and, where the platform supports it, whether the popup hides when it loses focus. A platform that cannot support launch at login MUST NOT pretend to.

**Observable outcome.** A changed horizon changes the itinerary and forecast window after recalculation. The selected service mode determines whether the planner can outlive the desktop window. Notification preferences control which operating-system notifications are delivered. Settings persist across restarts. The data directory is visible. The export action produces a portable representation of user data.

**Alternative and failure paths.** A horizon outside 7 to 365 is rejected with field-level feedback. A platform-specific tray preference that cannot be honored is shown as unavailable or is omitted, not silently accepted. An export failure reports an error and leaves the data intact.

**Acceptance scenarios.**

- **Given** the default settings, **when** the user opens Settings, **then** the horizon shows 90 days.
- **Given** the user changes the horizon to 30 days, **when** the itinerary is recomputed, **then** work beyond the 30-day window is not scheduled.
- **Given** the user enters a horizon of 0 or 400 days, **when** they save, **then** the application rejects the value with field-level feedback.
- **Given** the platform does not support launch at login, **when** the user opens tray preferences, **then** the option is unavailable or absent and the application does not claim it will work.
- **Given** notifications are disabled, **when** a critical warning appears, **then** the warning remains visible in the application and no operating-system notification is delivered.

**Rules used.** RULE-CAP-6, RULE-VAL-1, RULE-DATA-1, RULE-NOTIF-1.

### 4.3 Create and manage projects

**Story WORK-1. Projects.** As a worker, I want to group tasks into projects with priorities, deadlines, and statuses, so that related commitments are planned together.

**Starting conditions.** The user has opened the Projects view or a task editor.

**Main interaction.** The user creates a project with a title, priority, optional deadline, and status. The user can edit those fields and change the status. The user can open a project to see its tasks, total estimated, logged, and remaining effort, projected finish, slack, and risk.

**Observable outcome.** A project appears in the Projects view. Its tasks can reference it. An active project's deadline becomes an effective deadline for its unfinished tasks when earlier than the task's own deadline. A paused, completed, or archived project contributes no schedulable tasks. A project deadline change is accepted even if it makes a forecast missed; the application reports the risk.

**Alternative and failure paths.** An empty or overlong title, an invalid priority, or an invalid status is rejected with field-level feedback. A project that does not exist cannot be assigned to a task. Changing a project's status does not delete its tasks. Completing or archiving a project with unfinished tasks is permitted; those tasks leave the plan and remain available for history.

**Acceptance scenarios.**

- **Given** a project titled "Atlas" with priority 2 and a Friday deadline, **when** a task with no own deadline is added to it, **then** the task's effective deadline is Friday.
- **Given** a project deadline is moved earlier, **when** the change would make a task miss its deadline, **then** the change is saved and a missed risk is reported rather than the change being rejected.
- **Given** a project is paused, **when** the itinerary is computed, **then** none of its tasks are scheduled.
- **Given** a project is completed, **when** the user views the Projects view, **then** it remains available in project history, is not shown as active, and its unfinished tasks are not scheduled.
- **Given** a project is archived, **when** the user views the default Projects view, **then** the project is hidden from the active list, remains available in history and export, and its unfinished tasks are not scheduled.
- **Given** a completed project with unfinished tasks, **when** the user reopens it, **then** it becomes active again and its tasks can be scheduled.
- **Given** a project title is 201 characters, **when** the user saves, **then** the application rejects it with a field-level error.

**Rules used.** RULE-VAL-1, RULE-STATE-5, RULE-EFF-1, RULE-STATE-6.

### 4.4 Create and manage tasks, including inbox tasks

**Story WORK-2. Tasks and inbox.** As a worker, I want to capture work quickly, keep unready work out of the schedule, and promote it when it is ready, so that the itinerary contains only work I can actually do.

**Starting conditions.** The user has opened Today, the Tasks view, or a task editor, or is working with an active task. A project may or may not exist.

**Main interaction.** The user creates a task with a title. A quick-capture action is reachable from Today, the task view, the tray, and a global shortcut where the platform supports it. It creates an inbox task with only a title and does not stop an active timer; details can be completed later. In the task editor the user sets description, project, priority, estimate, optional deadline, source metadata, and status. The user can move an inbox task to ready after supplying a positive estimate. The user can complete, cancel, or reopen a task.

**Observable outcome.** Quick capture records the task immediately and returns the user to the interrupted context. An inbox task appears in the task list but never in the itinerary. Capturing while a timer runs does not stop, pause, or change the timer. A ready task with a positive estimate becomes eligible. Completing a task removes it from the itinerary and records a completion time. Reopening it clears the completion time and makes it plannable again. Cancelling removes it from planning and keeps its time entries. When an inbox task lacks an estimate, the application MAY suggest that the user add one, but it MUST NOT invent an estimate or move the task out of the inbox automatically.

**Alternative and failure paths.** A ready or in-progress task without a positive estimate is rejected with a field-level error. An empty or overlong title is rejected. A task with a project that does not exist is rejected. A caller cannot set a blocked status. Completing a task with incomplete prerequisites requires explicit confirmation; without it, the completion is rejected and the incomplete prerequisites are named.

**Acceptance scenarios.**

- **Given** the user quick-adds a task with only a title, **when** it is saved, **then** it is in the inbox and does not appear in the itinerary.
- **Given** an active timer, **when** the user quick-captures a task, **then** the task is saved and the active timer continues without interruption.
- **Given** an inbox task with no estimate, **when** the user tries to mark it ready, **then** the application rejects the change and asks for a positive estimate.
- **Given** an inbox task with no estimate, **when** the user opens it, **then** the application MAY suggest adding an estimate but does not invent one or promote the task automatically.
- **Given** an inbox task with estimate 45, **when** the user marks it ready, **then** it becomes eligible for the itinerary.
- **Given** a task with an unfinished prerequisite, **when** the user completes it without confirming, **then** the application rejects the completion and names the unfinished prerequisite.
- **Given** the same task, **when** the user confirms completion, **then** the task is completed and the dependency edge remains.
- **Given** a completed task, **when** the user reopens it, **then** its completion time is cleared and it can be scheduled again.

**Rules used.** RULE-VAL-1, RULE-STATE-1, RULE-STATE-3, RULE-STATE-4.

### 4.5 Set priorities, estimates, deadlines, and sources

**Story WORK-3. Planning inputs.** As a worker, I want to give the planner enough information about each task, so that its order and forecasts are credible.

**Starting conditions.** A task exists.

**Main interaction.** The user edits the task's priority, estimate, deadline, and source metadata. The user can change these at any time, including after work has been logged. Saving valid changes triggers a recalculation.

**Observable outcome.** The task's priority, estimate, deadline, source, and effective deadline are visible in the task detail. Each priority shows its label and semantic meaning from Section 3.4. Changing an estimate changes remaining effort and future projections. Changing a deadline changes the effective deadline and forecast. Changing priority affects ordering only after deadline and dependency pressure. Source metadata is provenance and does not create an attachment.

**Alternative and failure paths.** A priority outside 1 to 5, an estimate that is not a positive whole minute value for a schedulable task, or an overlong title is rejected with field-level feedback. A deadline in the past is allowed; if work remains, it is reported as missed. A task deadline later than its project deadline is allowed; the effective deadline remains the earlier project deadline. An estimate revision below logged work produces an overrun warning and does not silently increase the estimate.

**Acceptance scenarios.**

- **Given** a task with estimate 120 and 30 minutes logged, **when** the user changes the estimate to 90, **then** the remaining effort becomes 60 and the itinerary and forecasts are recomputed.
- **Given** a task with priority 1 and a later deadline, and another task with priority 5 and an earlier feasible deadline, **when** the itinerary is computed, **then** the earlier-deadline task is scheduled first.
- **Given** a task with no deadline in an active project with a Friday deadline, **when** the user views the task, **then** its effective deadline is Friday.
- **Given** a task with a deadline later than its project's deadline, **when** the forecast is computed, **then** the project deadline is used as the effective deadline.
- **Given** a task deadline in the past with work remaining, **when** the forecast is computed, **then** the risk is missed.
- **Given** a priority picker, **when** the user opens it, **then** each value shows its label, scheduling meaning, and an example.

**Rules used.** RULE-VAL-1, RULE-PRIO-1, RULE-EFF-1, RULE-EFFORT-1, RULE-EFFORT-2, RULE-ORDER-1, RULE-RISK-1, RULE-STATE-8.

### 4.6 Create and remove dependencies

**Story DEP-1. Dependencies.** As a worker, I want to record which work has to happen first and prevent invalid graphs, so that the itinerary does not propose a successor before its prerequisites by default.

**Starting conditions.** Two or more tasks exist.

**Main interaction.** In the task editor, the user adds a prerequisite to a task and can view both prerequisites and dependents. The user can remove a dependency. The application validates that both tasks exist, that the edge is not a self-edge, that it is not a duplicate, and that it does not create a cycle.

**Observable outcome.** A valid edge appears in both tasks' dependency views. The successor is not actionable while the predecessor is unfinished. The scheduler allocates the predecessor before the successor. Removing an edge removes the constraint. A cycle is rejected with the concrete cycle path. A self-edge is rejected because a task cannot be its own prerequisite and could never become satisfiable.

**Alternative and failure paths.** A self-edge is rejected. A duplicate edge is rejected as a conflict. An edge to a nonexistent task is rejected as not found. A cycle is rejected with the path, and no edge is stored. Reordering the dependency list or retrying an identical valid request MUST NOT produce duplicate edges.

**Acceptance scenarios.**

- **Given** tasks A, B, and C, **when** the user makes B depend on A and C depend on B, **then** the dependency views show both edges.
- **Given** that graph, **when** the user tries to make A depend on C, **then** the application rejects the change and displays a cycle path containing A, B, and C.
- **Given** a task A, **when** the user tries to make A depend on itself, **then** the change is rejected and no edge is created.
- **Given** an existing edge A to B, **when** the user adds it again, **then** the change is rejected as a conflict.
- **Given** an edge from a nonexistent task, **when** the user saves, **then** the application reports that the referenced task does not exist.

**Rules used.** RULE-VAL-1, RULE-CLOS-5, RULE-STATE-3, RULE-STATE-4.

### 4.7 Understand blocking and complete work out of order

**Story DEP-2. Blocking.** As a worker, I want to see why work is blocked and be able to record reality when work happens out of order, so that the dependency graph stays honest without trapping me.

**Starting conditions.** A task has at least one unfinished prerequisite.

**Main interaction.** The user views the blocked task and sees which prerequisite is unfinished. The task is not selectable for new scheduled work. The user can complete the prerequisite, remove the dependency, complete the blocked task with explicit confirmation if the work happened out of order, or make an explicit override to start the blocked task anyway. An override does not mark the prerequisite complete and does not hide the blocked state.

**Observable outcome.** Blocked work is visible but not scheduled. The blocker is named. Completing the prerequisite clears the blocked state. Completing a blocked task after confirmation records the completion and preserves the dependency edge. Cancelling a prerequisite does not satisfy its dependents; they remain blocked until the dependency is removed or the prerequisite is completed.

**Alternative and failure paths.** The application MUST NOT let a blocked task appear as actionable without an explicit override. A missing or ineligible prerequisite makes the dependent path blocked, not safe. The application MUST NOT silently delete or rewrite dependencies to resolve a block. An override without confirmation is rejected.

**Acceptance scenarios.**

- **Given** B depends on A and A is unfinished, **when** the user opens B, **then** B is shown as blocked and A is named as the unfinished prerequisite.
- **Given** the same state, **when** the itinerary is computed, **then** B is not allocated before A is completed or fully allocated earlier.
- **Given** A is cancelled, **when** the user checks B, **then** B remains blocked and the application explains that a cancelled prerequisite does not satisfy the dependency.
- **Given** A is completed, **when** the plan is recomputed, **then** B becomes eligible.
- **Given** B is blocked, **when** the user confirms completion, **then** B is completed and the edge A to B remains.
- **Given** B is blocked, **when** the user starts it with an explicit override, **then** the timer starts, B remains reported as blocked, and the override is visible.
- **Given** B is blocked, **when** the user starts it without an override or confirmation, **then** the start is rejected.

**Rules used.** RULE-STATE-1, RULE-STATE-3, RULE-STATE-4, RULE-ELIG-1, RULE-CLOS-3, RULE-RISK-1, RULE-OVERRIDE-1 through RULE-OVERRIDE-6.

### 4.8 See the itinerary

**Story PLAN-1. Today's plan.** As a worker, I want a credible, automatically ordered plan for the available time, so that I know what to do next without rebuilding or maintaining the plan myself.

**Starting conditions.** The user has configured availability and has at least one eligible task, or has no eligible work.

**Main interaction.** The user opens Today. The application shows the current date, remaining capacity today, the active task if any, a chronological itinerary grouped by working interval, and a warnings section. The user can start, pause, or complete work, capture a task quickly, open a task detail, or open the full task and project views. The user cannot drag to reorder the itinerary. The user can request an explicit override to work on a specific task next.

**Observable outcome.** The itinerary is the primary product output. It contains only eligible work, does not exceed capacity after pause budgets, respects dependencies, splits long work into at most 60-minute items, and orders work by the rules in Section 5.9 without hand maintenance. It starts now and can extend across future working intervals within the horizon. Each item shows the task, project when present, allocated time, a deadline or risk indicator when relevant, and an explanation on demand. Work that cannot fit is unscheduled rather than placed outside capacity. Pause budgets are shown as reserved time, not as itinerary items.

**Alternative and failure paths.** If there is no eligible work, the empty state explains which condition applies: no work, no working hours, no capacity in the horizon (for example, every day is marked unavailable), all tasks blocked, missing estimates, or work that is ineligible because its project is paused, completed, or archived. If no working hours are configured, no itinerary is fabricated. If an active dependency cycle is found, the application reports it and does not produce a partial plan. If there is capacity but no eligible work, the capacity is left unused.

**Acceptance scenarios.**

- **Given** two eligible tasks and a three-hour working interval, **when** the user opens Today, **then** the itinerary is chronological and every item lies inside the interval.
- **Given** a task with 125 minutes remaining and a three-hour interval, **when** the itinerary is computed, **then** it appears as a 60-minute item, another 60-minute item, and a 5-minute item, in that order.
- **Given** a task whose remaining work exceeds the current interval, **when** the itinerary is computed, **then** the item stops at the interval boundary and the remainder is placed in later capacity.
- **Given** an unfinished task with zero remaining effort, **when** it is otherwise eligible, **then** it receives a 5-minute completion slot and no more.
- **Given** no working hours, **when** the user opens Today, **then** the application shows the setup warning and no itinerary.
- **Given** all tasks are blocked, **when** the user opens Today, **then** the empty state says that work is blocked and names at least one blocker.
- **Given** the only remaining tasks belong to a paused project, **when** the user opens Today, **then** the empty state explains that the project is paused rather than claiming there is no work.
- **Given** the user tries to drag an item to a new position, **when** the interaction is attempted, **then** the application does not support manual reordering and the system-owned order remains.
- **Given** two tasks with the same fields and creation times supplied in a different input order, **when** the itinerary is recomputed, **then** the system produces the same deterministic order without user maintenance.
- **Given** a task the user wants next, **when** the user confirms an explicit override, **then** the task is treated as next, the override is visible, and the rest of the order is still computed by the system.

**Rules used.** RULE-ELIG-1, RULE-ORDER-1 through RULE-ORDER-3, RULE-ALLOC-1 through RULE-ALLOC-5, RULE-CAP-1 through RULE-CAP-8, RULE-OVERRIDE-1 through RULE-OVERRIDE-6.

### 4.9 Understand why work was selected

**Story PLAN-2. Explanations.** As a worker, I want to know why an item is in the plan, so that I can trust the order and correct the inputs when it is wrong.

**Starting conditions.** The itinerary contains at least one item.

**Main interaction.** The user opens the explanation for an itinerary item. The item shows a machine-readable reason and a human-readable explanation. The reason identifies deadline protection, dependency protection, task priority, or project priority. The explanation names the decisive cause, including the relevant deliverable and its slack or deadline where applicable.

**Observable outcome.** The explanation is truthful and specific. It does not claim a deadline is safe when the closure is blocked. It does not claim a guarantee. It uses the same reason and explanation that an agent receives.

**Alternative and failure paths.** When the decisive criterion is a stable tie after all preference criteria, the four-value reason set is insufficient; see DEC-7. The explanation MUST still be truthful and MUST NOT claim that priority decided the order when it did not. If the scheduling inputs change, the explanation is regenerated with the new projection.

**Acceptance scenarios.**

- **Given** a task in a tight deadline closure, **when** the user views its explanation, **then** it carries `deadline_protection` and names the deliverable and its slack.
- **Given** a prerequisite that unlocks a deadline-bearing task, **when** the user views its explanation, **then** it carries `dependency_protection` and names the descendant and its slack.
- **Given** two ordinary tasks with equal deadline and project priority, **when** the higher task priority is selected first, **then** the reason is `task_priority` and the explanation names the priority comparison.
- **Given** two tasks whose task priorities tie but whose project priorities differ, **when** the lower project priority number is selected first, **then** the reason is `project_priority`.
- **Given** the same inputs in a different input order, **when** the itinerary is regenerated, **then** the reasons and order are identical.

**Rules used.** RULE-ORDER-1, RULE-REASON-1 through RULE-REASON-3, RULE-DET-1.

### 4.10 See supporting deadline forecasts

**Story RISK-1. Deadline feasibility.** As a worker, I want a supporting view of whether each task and project can still meet its deadline, so that I can act before a commitment becomes impossible without letting forecasts dominate the plan.

**Starting conditions.** At least one unfinished task exists. A task or project may have a deadline.

**Main interaction.** The user opens a task detail, the Projects view, the Review view, or the deadline-risk section of Today. The application shows the effective deadline when one exists, projected finish, slack, risk, and explanation for each task and active project. The user can open the dependency-critical path for a project.

**Observable outcome.** Forecasts are secondary to the itinerary and never replace it. A forecast is computed from the dependency closure and capacity before the effective deadline. A project forecast includes all unfinished tasks in the project and their prerequisites. A shared prerequisite is counted once. A task with no deadline has null slack, not a sentinel value. A task that cannot be placed within the horizon is unscheduled unless it is blocked or missed, and it MUST NOT be assigned a fabricated time. A blocked path is blocked and names the missing or ineligible prerequisite. A passed deadline with work remaining is missed.

**Alternative and failure paths.** If no capacity exists, the forecast is unscheduled or missed according to Section 5.8, and it MUST NOT be classified as safe. If a prerequisite is missing or ineligible, the forecast is blocked. If the deadline is beyond the planning horizon, the treatment is unresolved; see DEC-5. A forecast failure MUST NOT present a stale forecast as current.

**Acceptance scenarios.**

- **Given** a task with closure work 100 minutes and 115 minutes of capacity before its deadline, **when** the forecast is computed, **then** slack is 15 minutes and the risk is safe at the 15 percent boundary.
- **Given** the same work with 114 minutes of capacity, **when** the forecast is computed, **then** slack is 14 minutes and the risk is tight.
- **Given** the same work with 90 minutes of capacity, **when** the forecast is computed, **then** slack is -10 minutes and the risk is missed.
- **Given** a deadline that passed with work remaining, **when** the forecast is computed, **then** the risk is missed.
- **Given** a closure containing an inbox prerequisite, **when** the forecast is computed, **then** the risk is blocked and the prerequisite is named.
- **Given** a task with no deadline, **when** the forecast is computed, **then** its slack is null and it is not reported as tight or missed solely for lacking a deadline.
- **Given** a task and a project that share a prerequisite, **when** the project forecast is computed, **then** the prerequisite's effort is counted once in the project closure.

**Rules used.** RULE-CLOS-1 through RULE-CLOS-5, RULE-SLACK-1, RULE-SLACK-2, RULE-RISK-1 through RULE-RISK-4, RULE-FCAST-1 through RULE-FCAST-5.

### 4.11 Respond to warnings

**Story RISK-2. Actionable warnings.** As a worker, I want to see and resolve the conditions that threaten my plan, so that I can change inputs instead of discovering a missed deadline too late.

**Starting conditions.** The application has recomputed the plan and found at least one warning condition.

**Main interaction.** The user opens Today or the tray popup, or the Review view. Warnings are shown as first-class state, ordered by severity. For a tight or missed deadline, the user can inspect the shortfall in minutes or hours, the affected deliverable, and the dependency path. The user can respond by changing the estimate, deadline, priority, dependency, availability, or by completing or logging work. The application does not invent a dismiss action; a warning resolves when its condition is no longer true.

**Observable outcome.** The user can enable operating-system notifications for critical warnings and approaching deadlines. A tight forecast produces a `deadline_tight` warning. A missed forecast produces a `deadline_missed` warning with its shortfall; DEC-8 governs its severity. An estimate overrun produces `estimate_overrun` and leaves the estimate unchanged. A blocked deadline path produces `blocked_deadline_path` and MUST NOT be reported as safe. Excess total work produces `plan_over_capacity`. No working hours produces `no_working_hours` and no itinerary. Warnings appear in Today and critical warnings appear in the tray popup. They resolve automatically after a recalculation that removes the condition.

**Alternative and failure paths.** A warning MUST NOT be shown as a transient toast only. A warning MUST NOT block an otherwise valid action. A warning MUST NOT claim a guarantee. A warning about a blocked path MUST name the missing or ineligible prerequisite. A warning about a shortfall MUST state the shortfall in minutes or hours.

**Acceptance scenarios.**

- **Given** a project with slack -60 minutes, **when** the plan is recomputed, **then** a `deadline_missed` warning identifies the project and states the 60-minute shortfall.
- **Given** the user adds enough working availability to remove the shortfall, **when** the plan is recomputed, **then** the warning resolves automatically.
- **Given** a task whose logged time exceeds its estimate, **when** the plan is recomputed, **then** an `estimate_overrun` warning appears and the original estimate is retained.
- **Given** no working hours, **when** the user opens Today, **then** a persistent `no_working_hours` setup warning appears and no itinerary is fabricated.
- **Given** a deadline-bearing task whose closure contains an inbox prerequisite, **when** the plan is recomputed, **then** the risk is `blocked` and the warning names the prerequisite.
- **Given** a critical warning and a warning-level warning, **when** the user opens Today, **then** the critical warning is listed first.
- **Given** notifications are enabled for critical warnings, **when** a critical warning appears, **then** an operating-system notification is delivered and the same warning remains visible in the application and tray.
- **Given** a warning resolves, **when** the plan is recomputed, **then** its notification is withdrawn or marked resolved and does not fire again for the same condition.

**Rules used.** RULE-WARN-1 through RULE-WARN-5, RULE-NOTIF-1 through RULE-NOTIF-4, RULE-RISK-1, RULE-RISK-4, RULE-FCAST-4.

### 4.12 Start, pause, and complete work

**Story DO-1. Active work.** As a worker, I want to start work and keep one trustworthy timer, so that actual effort reaches the plan without conflicting records.

**Starting conditions.** At least one ready or in-progress task exists. A task may already be active.

**Main interaction.** The user starts a task from Today, the task detail, or the tray. If another task is active, the desktop interface asks for confirmation before stopping it. The user can pause the timer, resume it, or complete the task. Pausing is how the user takes an unscheduled break; the pause budget already reserves capacity, so no break item is added to the itinerary. The active task card shows the task title, project, elapsed time, estimate, and controls. The top of the application shows a persistent active-timer indicator.

**Observable outcome.** Starting a task opens a time entry and makes it the single current task. Elapsed time is visible and contributes to logged work and remaining effort while the timer runs. Pausing closes the current entry and keeps the task current and in progress. Starting another task after confirmation closes the previous entry and returns the previous task to ready unless it was completed or cancelled. Completing the active task closes the entry and marks the task completed. The tray tooltip shows the current task and elapsed time, or "No active task."

**Alternative and failure paths.** An agent starting a task while another is active MUST fail with an active-timer conflict unless it explicitly requests stopping the active timer. A second active timer MUST NOT exist. Starting a blocked task is rejected unless the user makes an explicit dependency override, and a task started by override remains reported as blocked. Inbox tasks, tasks without an estimate, and tasks in a paused, completed, or archived project are not overridable. Completing a task with incomplete prerequisites follows the confirmation rule. If the timer cannot be persisted, the start fails and no open entry is left behind.

**Acceptance scenarios.**

- **Given** no active task, **when** the user starts task A, **then** A becomes the current task and an open time entry exists for A.
- **Given** A is active, **when** the user starts B and confirms, **then** A's entry closes, A returns to ready unless it was completed or cancelled, and B becomes current.
- **Given** A is active, **when** the user starts B and cancels the confirmation, **then** A remains active and B does not start.
- **Given** A is active, **when** an agent starts B without explicitly requesting a stop, **then** the request fails with an active-timer conflict and A remains active.
- **Given** A is active, **when** the user pauses, **then** the entry closes, the elapsed time is retained, and A remains the current in-progress task.
- **Given** A is active, **when** the user completes A, **then** the entry closes and A is completed.
- **Given** the main window is closed to the tray with A active, **when** the user checks the tray, **then** A and its increasing elapsed time are still shown.

**Rules used.** RULE-STATE-1, RULE-STATE-2, RULE-STATE-3, RULE-EFFORT-1, RULE-EFFORT-2.

### 4.13 Record and correct historical time

**Story DO-2. Historical time.** As a worker, I want to add, edit, and delete past time entries, so that the plan reflects the work that actually happened.

**Starting conditions.** A task exists.

**Main interaction.** In the task detail, the user adds a manual time entry with a start, end, duration, and optional note. The user edits or deletes an existing entry. The user can see all entries for the task. The application validates that the duration is positive, that the entry does not overlap the active open entry, and that the entry covers past or current time.

**Observable outcome.** A valid entry changes logged work and remaining effort. The itinerary, forecasts, and warnings are recomputed. Editing or deleting a historical entry triggers the same recomputation. Overlap with a closed entry is allowed and produces a non-blocking notice, not a rejection. An entry that overlaps the active open entry is rejected.

**Alternative and failure paths.** A non-positive or malformed duration is rejected. A future time entry is rejected. A manual entry that overlaps the active entry is rejected with an explanation. Deleting an entry requires explicit confirmation. A failed write leaves the prior entries and projections intact.

**Acceptance scenarios.**

- **Given** a task with estimate 120 and no logged time, **when** the user adds a 45-minute entry, **then** remaining effort becomes 75 and the forecast is recomputed.
- **Given** an active timer for a task, **when** the user tries to add a manual entry that overlaps it, **then** the entry is rejected.
- **Given** a closed 45-minute entry, **when** the user adds a 30-minute entry that overlaps it, **then** the entry is accepted and a non-blocking overlap notice is shown.
- **Given** a historical entry, **when** the user edits its duration, **then** remaining effort and the itinerary change.
- **Given** a historical entry, **when** the user deletes it, **then** remaining effort increases and the plan is recomputed.
- **Given** a time entry in the future, **when** the user saves, **then** the application rejects it.

**Rules used.** RULE-VAL-1, RULE-EFFORT-1, RULE-EFFORT-2, RULE-STATE-6.

### 4.14 Handle estimate overruns and completion

**Story DO-3. Overruns and completion.** As a worker, I want overruns to be visible and completion to remain explicit, so that the planner does not silently rewrite my estimates or mark work done for me.

**Starting conditions.** A task has logged work at or above its estimate.

**Main interaction.** The user logs time that meets or exceeds the estimate. The task remains unfinished until the user explicitly completes it. The application shows the overrun and offers the task for completion.

**Observable outcome.** Logged work at or above the estimate makes remaining effort zero. The original estimate is retained. An `estimate_overrun` warning appears when logged work exceeds the estimate. The unfinished task can receive a five-minute completion slot when otherwise eligible, but it is not marked completed automatically. Completing it records the completion. If logged work exactly equals the estimate, remaining effort is zero and there is no overrun.

**Alternative and failure paths.** The application MUST NOT increase the estimate to match logged work. The application MUST NOT complete a task because its estimate is exhausted. The application MUST NOT hide an overrun. If the user completes a task with incomplete prerequisites, the confirmation rule applies.

**Acceptance scenarios.**

- **Given** a task with estimate 60 and 45 minutes logged, **when** the user views it, **then** remaining effort is 15 and the task is not completed.
- **Given** the same task, **when** the user logs 30 more minutes, **then** remaining effort is 0, an overrun warning appears, and the estimate remains 60.
- **Given** a task with zero remaining effort that is otherwise eligible, **when** the itinerary is computed, **then** it receives a 5-minute completion slot.
- **Given** the same task, **when** the user does not complete it, **then** it remains unfinished despite the zero remaining effort.
- **Given** logged work exactly equal to the estimate, **when** the user views it, **then** remaining effort is 0 and no overrun warning is shown.

**Rules used.** RULE-EFFORT-1, RULE-ALLOC-3, RULE-WARN-1, RULE-STATE-1, RULE-STATE-3.

### 4.15 Browse tasks and inspect a task

**Story VIEW-1. Task management.** As a worker, I want to find and inspect tasks beyond today's plan, so that I can manage the whole workload.

**Starting conditions.** At least one task exists, possibly none.

**Main interaction.** The user opens the Tasks view and searches or sorts the task list. The list shows title, project, status, priority, estimate, logged time, remaining effort, effective deadline, dependencies, source, and forecast risk. The user opens a task detail to edit fields, manage dependencies, view prerequisites and dependents, view time entries, view source metadata, and see the forecast explanation and projected finish.

**Observable outcome.** Search narrows the list to matching tasks. The list includes inbox, ready, in-progress, completed, and cancelled tasks unless a filter narrows it. Sorting changes the display order without changing the itinerary order. The task detail reflects saved changes after recalculation. Field-level errors appear next to the fields that caused them. A dependency cycle error includes the path. The detail shows both sides of each dependency.

**Alternative and failure paths.** If no tasks exist, the view shows an empty state. Invalid input is rejected and the previous values remain. A task that no longer exists is reported as not found. The view does not compute its own schedule or forecast.

**Acceptance scenarios.**

- **Given** tasks with different titles and projects, **when** the user searches for a term, **then** only tasks matching the term are listed.
- **Given** the task list, **when** the user sorts by priority, **then** the display order changes while the itinerary order is unaffected.
- **Given** a task with a prerequisite and a dependent, **when** the user opens its detail, **then** both are shown.
- **Given** an invalid estimate, **when** the user saves the task, **then** the error appears at the estimate field and the previous value remains.
- **Given** a dependency cycle attempt, **when** the user saves, **then** the cycle path is displayed.

**Rules used.** RULE-VAL-1, RULE-ORDER-0, RULE-FCAST-4, RULE-REASON-2, RULE-RISK-4.

### 4.16 Review projects and deadline-critical paths

**Story VIEW-2. Project health.** As a worker, I want to see project-level effort and deadline risk, so that I can understand which commitments are in danger.

**Starting conditions.** At least one project exists.

**Main interaction.** The user opens the Projects view. Active projects show priority, deadline, total estimated, logged, and remaining effort, projected finish, slack, and risk. The user opens a project detail to see its tasks and the deadline-critical dependency path where one exists.

**Observable outcome.** Project totals sum the project's tasks: estimated total includes all tasks, logged total includes all time entries, and remaining total includes only unfinished, non-cancelled tasks. The project forecast uses the project's own deadline and the project closure. The deadline-critical path identifies the prerequisite chain that determines the project finish. A project with insufficient capacity before its deadline is visually marked and explains the shortfall in minutes or hours.

**Alternative and failure paths.** A project with no active tasks has no forecast. A paused, completed, or archived project is not shown as active. A project whose closure contains a blocked prerequisite is blocked, not safe. A project with no deadline has null slack.

**Acceptance scenarios.**

- **Given** an active project with three tasks totaling 150 minutes, **when** the user opens the project, **then** the totals show 150 minutes of remaining work and the forecast values.
- **Given** a project with 150 minutes of closure work and 90 minutes of capacity before its deadline, **when** the user views it, **then** it is marked missed with a 60-minute shortfall.
- **Given** a project with a shared prerequisite used by two tasks, **when** the project forecast is computed, **then** the prerequisite is counted once.
- **Given** a project with no deadline, **when** the user views it, **then** slack is null and the project is not reported as tight solely for lacking a deadline.

**Rules used.** RULE-CLOS-1 through RULE-CLOS-4, RULE-FCAST-2, RULE-RISK-1 through RULE-RISK-4.

### 4.17 Review planned versus logged work and upcoming capacity

**Story VIEW-3. Review.** As a worker, I want a compact retrospective, so that I can compare what I planned with what happened and prepare for the next week.

**Starting conditions.** The user has planned or logged work.

**Main interaction.** The user opens the Review view. It shows planned versus logged time for today and the current local calendar week, tasks with estimate overruns, deadline forecasts for active projects, and capacity remaining for the next seven local calendar days starting today. The availability of planned history for past days is unresolved; see DEC-14.

**Observable outcome.** Planned time for today comes from the current itinerary. Logged time comes from time entries and the active timer. Overruns are listed. Active-project forecasts show their risk. The next seven days' capacity reflects weekly availability and exceptions. The source of planned time for past days is unresolved; see DEC-14.

**Alternative and failure paths.** With no history, the view shows zero or an empty state rather than fabricated values. The Review view does not replace the Today view or the task/project detail views. It does not compute its own rules.

**Acceptance scenarios.**

- **Given** 120 minutes planned today and 90 minutes logged, **when** the user opens Review, **then** it shows 120 planned and 90 logged.
- **Given** a task with logged time above its estimate, **when** the user opens Review, **then** the task is listed as an overrun.
- **Given** a week with a day off, **when** the user views the next seven days, **then** the day off contributes no capacity.
- **Given** an active project with a missed forecast, **when** the user opens Review, **then** the project appears with its risk.

**Rules used.** RULE-EFFORT-1, RULE-CAP-1 through RULE-CAP-6, RULE-FCAST-2.

### 4.18 Let an agent read planning state

**Story AGENT-1. Agent read access.** As an agent, I want to read the current plan, risks, projects, tasks, dependencies, and logs, so that I can give the user context-aware help.

**Starting conditions.** The planner service is running, either as a local daemon or inside the desktop application, and the agent is a local MCP client. The service may have no tasks.

**Main interaction.** The agent requests a read view: today's itinerary and active task, active risks and warnings, a project's details and forecast, or a task's details, dependencies, time entries, overrides, and forecast. It can list and filter tasks by project, status, due window, risk, or text. It can ask what changed since a change cursor. In v2 it can list available tracker plugins. The application returns the same data and projections that the desktop interface shows.

**Observable outcome.** The agent sees the current itinerary, remaining capacity, active timer, warnings, task and project details, dependencies, time entries, overrides, and supporting forecasts. The agent sees explanations for scheduled items. The change feed returns created, updated, completed, cancelled, time-entry, override, and availability changes since the supplied cursor, with a new cursor. In v2, the agent does not see plugin secrets. Read operations do not mutate data.

**Alternative and failure paths.** If the planner service is unavailable, the agent receives a clear unavailable-service error. A request for a nonexistent entity returns a not-found error. A malformed or unauthorized request returns a validation error. An expired or unknown change cursor returns a clear error with the earliest cursor the service can serve. The agent MUST NOT open the local data store directly or compute a different plan.

**Acceptance scenarios.**

- **Given** the planner service is running with a plan, **when** an agent reads today, **then** it receives the same ordered items, reasons, capacity, active task, and warnings as the desktop interface.
- **Given** an agent requests a task that does not exist, **when** the request is handled, **then** it receives a clear not-found error.
- **Given** the application is not running, **when** an agent requests today's plan, **then** it receives a clear unavailable-service error.
- **Given** a task with dependencies and time entries, **when** an agent reads it, **then** it receives the dependencies, logs, overrides, and forecast.
- **Given** a change cursor from an earlier read, **when** an agent requests changes since that cursor, **then** it receives the changes made since then and a new cursor.
- **Given** an unknown change cursor, **when** an agent requests changes, **then** it receives a clear error and can request a full read.

**Rules used.** RULE-ORDER-1, RULE-REASON-2, RULE-FCAST-4, RULE-ARCH-1, RULE-ACTIVITY-1 through RULE-ACTIVITY-3.

### 4.19 Let an agent change work

**Story AGENT-2. Agent write access.** As an agent, I want to create, update, complete, cancel, link, time, adjust scheduling inputs, and explicitly override work, so that I can keep the plan current while the user works.

**Starting conditions.** The planner service is running. The agent uses explicit identifiers and the same planner service as the desktop interface.

**Main interaction.** The agent creates and updates tasks and projects; sets and removes dependencies; completes or cancels tasks; creates, updates, and deletes time entries; starts and stops a timer; sets working hours and exceptions; edits scheduling inputs when the user asks; requests explicit overrides when the user asks; and requests schedule diagnostics. In v2 it can list plugins and search, attach, refresh, or detach tracker context. Every change uses explicit identifiers and structured fields. An agent MUST NOT invent an override or scheduling input edit that the user did not request.

**Observable outcome.** A successful change takes effect once, triggers the same recalculation as a desktop change, and is visible to the desktop and tray after refresh. An agent override is recorded with the actor, target, affected rule, and time, and remains visible in the affected task and itinerary. An agent-created task appears in the task list. An agent-logged time entry changes remaining effort and forecasts. An agent-completed task leaves the itinerary. An agent-started timer becomes the single active timer if the request is allowed. The resulting plan is identical to a desktop-originated change with the same data.

**Alternative and failure paths.** Fuzzy target selection is rejected. A change that would affect multiple tasks implicitly is rejected. A destructive operation without explicit confirmation is rejected. A second timer without an explicit stop request is rejected. Completion with incomplete prerequisites requires confirmation. An override without explicit user intent and confirmation is rejected. A scheduling input edit that is arbitrary or unattributed is rejected. Invalid fields, missing entities, conflicts, and dependency cycles return the same structured errors as desktop operations. An agent MUST NOT write to a remote tracker.

**Acceptance scenarios.**

- **Given** the planner service is running, **when** an agent creates a task with an explicit project and estimate, **then** the task appears in the desktop task list after refresh.
- **Given** a task with an active timer, **when** an agent starts another task without requesting a stop, **then** the request fails with an active-timer conflict.
- **Given** the same state, **when** the agent starts another task and explicitly requests a stop, **then** the previous timer closes and the new task becomes active.
- **Given** a time entry, **when** an agent deletes it without confirmation, **then** the request is rejected; with confirmation, it succeeds and the plan is recomputed.
- **Given** a task with an unfinished prerequisite, **when** an agent completes it without confirmation, **then** the request is rejected; with confirmation, it succeeds.
- **Given** a dependency change that would create a cycle, **when** an agent requests it, **then** the request is rejected with the cycle path and no edge is stored.
- **Given** a user asks an agent to work on a blocked task, **when** the agent requests an explicit override with confirmation, **then** the task can start, remains reported as blocked, and the override is recorded.
- **Given** an agent requests an override while the desktop window is attached, **when** the request is handled, **then** the confirmation appears on the desktop and the override proceeds only after the user confirms.
- **Given** an agent tries to override a rule without user intent, **when** the request is handled, **then** it is rejected.

**Rules used.** RULE-VAL-1, RULE-STATE-2, RULE-STATE-3, RULE-STATE-6, RULE-STATE-8, RULE-RECALC-1, RULE-ARCH-2, RULE-OVERRIDE-1 through RULE-OVERRIDE-6.

### 4.20 Agent safety and unavailable service

**Story AGENT-3. Agent boundaries.** As a user, I want agent access to be safe and predictable, so that automation cannot silently damage my plan or claim access it does not have.

**Starting conditions.** An agent is configured as a local MCP client. The planner service may be running as a daemon, may be inside the desktop application, or may be unavailable.

**Main interaction.** The agent performs reads and writes through the local interface. The planner service validates every request and applies the same rules as the desktop interface.

**Observable outcome.** Explicit identifiers are required for changes. No operation changes several tasks implicitly. Destructive operations require confirmation. Overrides require explicit user intent and confirmation; scheduling input edits require explicit user intent. In v2, the agent MUST NOT receive plugin secrets. The agent MUST NOT access the local data store directly. When the planner service is unavailable, the agent receives a clear unavailable-service error and does not fall back to a stale or independent plan.

**Alternative and failure paths.** In v2, a disabled or unavailable tracker plugin returns a plugin error, and a plugin timeout or malformed response is contained and does not impair local planning. A request from another operating-system user is rejected. The agent MUST NOT start a competing service, a second data owner, or a mode that bypasses the configured service mode.

**Acceptance scenarios.**

- **Given** the planner service is unavailable, **when** an agent attempts any operation, **then** it receives a clear unavailable-service error.
- **Given** an agent request with a title instead of an identifier, **when** a change is attempted, **then** the request is rejected because explicit identifiers are required.
- **Given** an agent request that would update several tasks implicitly, **when** it is attempted, **then** the request is rejected.
- **Given** an agent request for an override without user intent, **when** it is handled, **then** the request is rejected.
- **Given** a destructive operation, **when** the agent omits confirmation, **then** the operation is rejected.

**Rules used.** RULE-ARCH-1, RULE-ARCH-2, RULE-ARCH-4, RULE-STATE-6, RULE-OVERRIDE-1 through RULE-OVERRIDE-6.

### 4.21 Configure an external tracker plugin (v2 advisory)

**V2 advisory.** This story is not part of v1. It is retained so a future implementation has context.

**Story TRACK-1. Tracker setup.** As a worker, I want to configure a read-only tracker connection, so that I can attach remote context to my local tasks.

**Starting conditions.** The user has a tracker plugin available locally. No plugin is required for the core product.

**Main interaction.** In Settings, the user sees discovered plugins, their enabled or disabled state, their configuration fields, and their last error. The user supplies the non-secret settings and any secret credentials, enables the plugin, and can disable it. A disabled plugin MUST NOT run. The user can see which tracker connection is configured.

**Observable outcome.** An enabled, validly configured plugin can be selected in the task editor for search. A plugin with invalid configuration or a missing secret MUST NOT be enabled. Secrets are not stored in the planner's local data, shown in plugin-visible configuration, written to logs, or returned to agents. The application does not auto-download plugins.

**Alternative and failure paths.** An invalid plugin definition is rejected and reported. A plugin whose required configuration is incomplete stays disabled. A plugin error is shown in Settings without breaking local planning. A platform that cannot protect a secret MUST NOT store it in plain local data.

**Acceptance scenarios.**

- **Given** no plugins installed, **when** the user opens Settings, **then** the core planner works and the tracker section shows no configured plugins.
- **Given** a plugin with required configuration missing, **when** the user tries to enable it, **then** the application rejects the enablement and shows the missing fields.
- **Given** a validly configured plugin, **when** the user enables it, **then** it becomes selectable for tracker search.
- **Given** a plugin with a secret credential, **when** the user inspects the configuration or an agent reads planner state, **then** the secret is not shown.
- **Given** a plugin that fails to initialize, **when** the user opens the planner, **then** local tasks, scheduling, and forecasts continue to work.

**Rules used.** RULE-PLUG-1 through RULE-PLUG-4, RULE-DATA-2.

### 4.22 Search, attach, refresh, and detach tracker context (v2 advisory)

**V2 advisory.** This story is not part of v1. It is retained so a future implementation has context.

**Story TRACK-2. Tracker context.** As a worker, I want to search a chosen tracker and attach a specific remote item to a local task, so that external context is available without letting the tracker control my plan.

**Starting conditions.** At least one plugin is enabled and configured. At least one local task exists.

**Main interaction.** In the task editor, the user chooses one enabled plugin, enters a query, reviews the candidate results with title, status, and URL, and explicitly attaches one result. The attachment appears with cached metadata and a last-refresh time. The user can open the remote item, refresh the cached metadata, or detach it. The user can repeat the search for another item. If the same remote item is already attached to another local task, the application asks for explicit confirmation before attaching it again.

**Observable outcome.** Attaching stores a local link and cached remote metadata. The remote title and status are labeled read-only. The local task's title, estimate, priority, dependencies, status, and completion are unchanged. Attaching and refreshing do not trigger itinerary recalculation. Refreshing updates only the cached metadata. Detaching removes the local link and leaves the remote item unchanged. A local change MUST NOT write to the remote tracker.

**Alternative and failure paths.** Searching with no plugin selected is rejected; the application MUST NOT search all trackers implicitly. A plugin that is disabled or unavailable MUST NOT be searched. A search returns no results or an error without changing local data. A refresh failure leaves the previous cached metadata and last-refresh time visible. A plugin response with invalid or unsafe text is rejected or safely normalized. Offline, the cached metadata remains visible.

**Acceptance scenarios.**

- **Given** two enabled plugins, **when** the user opens tracker search, **then** the user MUST choose one plugin and the application does not search both automatically.
- **Given** a search result, **when** the user attaches it to a task, **then** the attachment shows the cached title, status, URL, and last-refresh time, and the local planning fields are unchanged.
- **Given** the same remote item already attached to another task, **when** the user attaches it again, **then** the application asks for confirmation.
- **Given** an attachment, **when** the user refreshes it while offline, **then** the refresh fails, the cached metadata remains, and local planning is unaffected.
- **Given** an attachment, **when** the user detaches it, **then** the local link is removed and the remote item is unchanged.
- **Given** an attached remote item, **when** the user completes or re-estimates the local task, **then** nothing is written to the remote tracker.

**Rules used.** RULE-PLUG-1 through RULE-PLUG-5, RULE-DATA-2.

### 4.23 Export and back up local data

**Story DATA-1. Data portability.** As a worker, I want to export and back up my planning data, so that my history is portable and recoverable without an account.

**Starting conditions.** The application has local data, possibly including projects, tasks, dependencies, time entries, availability, overrides, and settings. In v2 it may also include tracker attachments.

**Main interaction.** In Settings, the user sees the local data directory and starts an export. The application produces a portable representation of the planning data. The user can copy the local data location for a backup while no service owner is running.

**Observable outcome.** The export contains the user's planning data in a documented, portable form, including tasks, projects, dependencies, time entries, availability, overrides, and settings. When v2 tracker plugins are present, the export excludes their secrets. The v1 export contains no attachment data because attachments are v2. The user can keep the export outside the application. Restarting the application preserves the data. The application works offline.

**Alternative and failure paths.** This specification does not define an import flow, automatic backup, synchronization, encryption, or recovery service. The application MUST NOT claim those capabilities. An export failure reports an error and leaves the local data intact. Secrets MUST NOT be included in an export.

**Acceptance scenarios.**

- **Given** local tasks, projects, dependencies, time entries, and availability, **when** the user exports, **then** the export contains that planning data in a portable form.
- **Given** a v2 attachment with a secret, **when** the user exports, **then** the secret is not present in the export.
- **Given** the user copies the local data location while no service owner is running, **when** the copy is restored as the data location and the service starts, **then** the planning data is present.
- **Given** no network connection, **when** the user uses tasks, projects, availability, the itinerary, time tracking, and exports, **then** they work.

**Rules used.** RULE-DATA-1 through RULE-DATA-3.

### 4.24 Run the application from the tray

**Story APP-1. Tray workflow.** As a worker, I want the application to remain available when its window is closed, so that I can check and control work without reopening the full planner.

**Starting conditions.** The planner service is running in either supported mode and the platform provides a system tray.

**Main interaction.** The tray is a client of the planner service; in single-process mode the tray is co-hosted with the service. Closing the main window hides it instead of quitting. The tray icon remains. A left click toggles a compact tray-attached popup. A right click opens a native menu with Open Planner, Start or Pause Current Task, Complete Current Task, and Quit. In daemon mode, Quit closes the desktop window only; the tray client keeps running, and the tray menu owns Stop Service. In single-process mode, Quit stops the process and the service. The tooltip shows the current task and elapsed time, or "No active task." The popup shows the current task and timer, Start, Pause, and Complete controls, the next two itinerary tasks, critical warnings, and an Open Planner action. Escape hides the popup; where the platform supports it, losing focus also hides it.

**Observable outcome.** The timer continues while the window is hidden. The popup shows the same active task and critical warnings as the desktop. The menu actions use the same service as the desktop. Open Planner restores the main window. The popup is compact and tray-anchored, stays above other windows where the platform supports it, and does not become a second full application.

**Alternative and failure paths.** On a platform or compositor where focus-loss hiding is unreliable, Escape and explicit toggle or hide still work. If the tray is unavailable, the application MUST NOT claim tray support. Linux tray and focus behavior varies by desktop environment; support MUST be verified on common environments and limitations documented. Quit is explicit; closing the window is not quit. In daemon mode, quitting the interface does not stop the daemon, and stopping the daemon is a separate explicit action. In single-process mode, quitting stops both the interface and the service.

**Acceptance scenarios.**

- **Given** the main window is open, **when** the user closes it, **then** the window hides, the tray remains, and the timer keeps running.
- **Given** the tray icon, **when** the user left-clicks, **then** the compact popup opens or closes.
- **Given** the tray icon, **when** the user right-clicks, **then** the native menu shows Open Planner, Start or Pause Current Task, Complete Current Task, and Quit, plus Stop Service in daemon mode.
- **Given** the popup is open, **when** the user presses Escape, **then** the popup hides.
- **Given** a critical warning exists, **when** the user opens the popup, **then** the critical warning is shown.
- **Given** an active task, **when** the user hovers the tray icon, **then** the tooltip shows the task title and elapsed time.
- **Given** daemon mode, **when** the user quits the desktop window, **then** the daemon and tray client remain available to agents and Stop Service is still reachable from the tray menu.

**Rules used.** RULE-ARCH-1, RULE-ARCH-3, RULE-WARN-3.

### 4.25 Quit, restart, and service availability

**Story APP-2. Lifecycle.** As a worker, I want to understand what stays alive in daemon mode and in single-process mode, so that I do not lose time or data and agents do not claim access they lack.

**Starting conditions.** The planner service is running in the selected mode, possibly with the main window hidden and a timer active. An agent may attempt to connect at any time.

**Main interaction.** In daemon mode, the user starts or stops the daemon explicitly, and the desktop and tray connect to it as clients. In single-process mode, the desktop process owns the service, window, and tray. The user closes the window, quits the interface, stops the service, and later restarts it.

**Observable outcome.** In daemon mode, closing the interface or quitting it leaves the daemon, timer, and agent access alive. Stopping the daemon stops the service; agents then receive a clear unavailable-service error. In single-process mode, closing the window hides it and keeps the service and agent access alive, and quitting the application stops the service. In both modes, restarting preserves local data and settings and recomputes the plan. The behavior of an open timer across service stop, restart, or crash is unresolved; see DEC-4.

**Alternative and failure paths.** If the service fails, the desktop and tray show an error and do not compute their own schedules. If an agent cannot reach the service, it does not open the data store directly and does not fabricate a plan. The agent MUST NOT start a competing service or take ownership of the data. Switching modes requires stopping the current service and starting the other mode; data is preserved and MUST NOT be written by two owners at once.

**Acceptance scenarios.**

- **Given** daemon mode with the interface closed, **when** an agent requests the plan, **then** the daemon serves it and the timer continues.
- **Given** daemon mode, **when** the user stops the service, **then** an agent request receives an unavailable-service error.
- **Given** single-process mode with the window hidden, **when** an agent requests the plan, **then** the service serves it and the timer continues.
- **Given** single-process mode, **when** the user quits the application, **then** the service stops and an agent request receives an unavailable-service error.
- **Given** the service is not running, **when** the user starts it, **then** the previous tasks, projects, time entries, availability, overrides, and settings are present and the plan is recomputed.
- **Given** the service is unavailable while the window is open, **when** the user views Today, **then** the application reports the problem rather than showing a fabricated plan.

**Rules used.** RULE-ARCH-1 through RULE-ARCH-6, RULE-DATA-1.

### 4.26 Override a rule deliberately

**Story OVERRIDE-1. Escape hatch.** As a user, I want a deliberate way to proceed when a planning rule blocks something I really need, so that the rules help by default without trapping me.

**Starting conditions.** An action is blocked by a planning rule, such as a dependency, capacity, or ordering rule. The user is authenticated as the local owner.

**Main interaction.** The application explains which rule blocked the action, what the consequence is, and what the user can do instead. An Override control is available behind an explicit action, never as the default button. The user confirms the override. The application records the actor, target, affected rule, time, and optional reason, then applies the action. The override applies to that action, not to the whole configuration. A capacity override adjusts the current local day's working window, and an ordering override moves the chosen task earlier or makes it next; neither changes weekly availability or creates a persistent order.

**Observable outcome.** The action proceeds and the plan is recomputed. The override remains visible on the affected task, allocation, or history entry. Any resulting risk is reported as overridden, not as safe. The underlying data is not falsified: a blocked task remains blocked, a missing prerequisite remains missing, and a paused project remains paused. Security rules, data-integrity rules, secret handling, destructive confirmations, and another person's data cannot be overridden.

**Alternative and failure paths.** An override without confirmation is rejected. An override of a non-overridable rule is rejected with an explanation. An agent override requires explicit user intent in the request and confirmation from an attached interactive surface; without one, it is rejected. An override that would corrupt data or bypass authentication is rejected. If the action still fails after the override, no partial change is left behind.

**Acceptance scenarios.**

- **Given** a blocked task, **when** the user opens the blocked action, **then** the default is not to start it and an Override control is available behind an explicit step.
- **Given** the same task, **when** the user confirms an override to start it, **then** the timer starts, the task remains reported as blocked, and the override is visible.
- **Given** an override of a security or data-integrity rule, **when** the user attempts it, **then** the application rejects it.
- **Given** the user arrives 30 minutes late, **when** the user confirms a capacity override that shifts today's window by 30 minutes, **then** planning uses the shifted window and the override is recorded.
- **Given** a task already scheduled after another task, **when** the user confirms an ordering override to run it next, **then** it precedes the other task, the override is visible, and no persistent pin is created.
- **Given** an agent request that includes an override, **when** the user has not asked for it, **then** the request is rejected.

**Rules used.** RULE-OVERRIDE-1 through RULE-OVERRIDE-6, RULE-ARCH-2, RULE-STATE-1.

### 4.27 Use the planner without the desktop window

**Story SERVICE-1. Headless service.** As a user, I want the planning service to keep running without a desktop window, so that agents and tray clients can keep working when the interface is closed or absent.

**Starting conditions.** The user has selected daemon mode or single-process mode. The platform supports the selected mode.

**Main interaction.** In daemon mode, the user starts the service at login or on demand. The daemon runs without a window, owns the planning data and rules, and serves the desktop, tray, and agent clients. Clients connect and disconnect without stopping the service. In single-process mode, the service starts and stops with the application.

**Observable outcome.** In daemon mode, agents and clients can read and write while no desktop window exists. The timer continues in the service. In single-process mode, agent access exists only while the application runs. Both modes use the same rules, data, and projections. Only one service owns the data at a time.

**Alternative and failure paths.** A daemon that cannot start reports a clear error and does not leave a partial service. A second daemon for the same data MUST NOT start. A client that cannot reach the service reports the problem and does not compute its own plan. A mode switch stops one owner before the other starts.

**Acceptance scenarios.**

- **Given** daemon mode with no window open, **when** an agent reads the itinerary, **then** it receives the current plan and active timer.
- **Given** daemon mode with no window open, **when** the user opens the desktop client, **then** it connects to the running service and shows the same state.
- **Given** single-process mode, **when** the application is not running, **then** an agent request receives an unavailable-service error.
- **Given** daemon mode with no interactive surface attached, **when** an agent requests an override, **then** the request is rejected with `confirmation_unavailable` and no override is recorded.
- **Given** daemon mode with the desktop window attached, **when** an agent requests an override, **then** the confirmation appears on the desktop and the override proceeds only after the user confirms.
- **Given** a second daemon start for the same data, **when** it is attempted, **then** it is rejected and the first daemon remains the only owner.

**Rules used.** RULE-ARCH-1 through RULE-ARCH-6, RULE-DATA-1.

### 4.28 Receive notifications for critical conditions

**Story NOTIF-1. Notifications.** As a worker, I want operating-system notifications for critical warnings, estimate overruns, and, once DEC-16 defines the trigger, approaching deadlines, so that I notice them without watching the application.

**Starting conditions.** The planner service is running in either mode and the user has configured notification preferences.

**Main interaction.** In Settings, the user enables or disables notification categories: critical warnings, estimate overruns, and approaching deadlines once DEC-16 defines that trigger. The user can set quiet hours and grant or deny the platform notification permission. A notification shows a short title, the affected task or project, and the reason. Activating it opens the relevant view.

**Observable outcome.** When an enabled condition becomes true, one notification is delivered per condition and entity. The same warning remains visible in the application and tray. When the condition resolves, the notification is withdrawn or marked resolved and does not fire again for the same condition. Quiet hours suppress delivery without hiding the warning. A denied permission is reported in Settings and in the application; the application does not repeatedly prompt.

**Alternative and failure paths.** A notification MUST NOT be the only place a warning appears. A warning MUST NOT be dismissed only by dismissing a notification. A notification failure does not block planning. A platform without notification support is shown as unsupported, not silently accepted.

**Acceptance scenarios.**

- **Given** notifications are enabled for critical warnings, **when** a critical warning appears, **then** one operating-system notification is delivered and the warning remains in the application.
- **Given** quiet hours are active, **when** a critical warning appears, **then** no notification is delivered and the warning remains visible.
- **Given** a warning resolves, **when** the plan is recomputed, **then** the notification is withdrawn or marked resolved.
- **Given** the platform denies notification permission, **when** the user opens Settings, **then** the application reports that notifications are unavailable.

**Rules used.** RULE-NOTIF-1 through RULE-NOTIF-4, RULE-WARN-1, RULE-WARN-3.

---

## 5. Shared behavioral rules

The rules in this section are referenced by the stories. They define the observable behavior that all surfaces MUST share.

### 5.1 Identifiers, time, and ordering

**RULE-ID-1.** Every task, project, dependency, time entry, availability exception, override, and v2 attachment has a stable opaque identifier. Users and agents address records by identifier. Titles are not identifiers and need not be unique.

**RULE-TIME-1.** Deadlines and time entries are absolute instants. Display uses the configured local timezone. Weekly availability and exception dates are local calendar concepts. A date exception applies to that local date. The instant represented by a date-only deadline is unresolved; see DEC-2.

**RULE-ORDER-0.** Task and project lists have a stable default order. User-selected sorting changes display order only. It MUST NOT change the itinerary order.

**RULE-PRIO-1.** Priorities are integers from 1 to 5. Each value has a label, a scheduling meaning, and an example as defined in Section 3.4. Lower numbers are more important. Priority is considered only after deadline pressure and dependency protection. A task priority takes precedence over a project priority when both are present.

### 5.2 Validation and state rules

**RULE-VAL-1.** The following inputs are invalid and MUST be rejected with field-level feedback:

- A task title outside 1 to 500 characters.
- A project title outside 1 to 200 characters.
- A priority outside 1 to 5.
- A schedulable task without a positive whole-minute estimate.
- A time entry without a positive whole-minute duration.
- A working interval whose end is not after its start.
- Overlapping working intervals.
- A negative pause budget, or a pause budget greater than or equal to its interval duration.
- An invalid weekday or exception date.
- An exception kind other than unavailable or replacement intervals.
- A replacement exception with no intervals.
- A dependency with a missing endpoint, a self-edge, a duplicate, or a cycle.
- A project or task status outside the supported values.
- A caller-supplied blocked task status.

**RULE-STATE-1.** Stored task states are inbox, ready, in progress, completed, and cancelled. Blocked is derived and MUST NOT be set directly. Completed records a completion time; leaving completed clears it. Cancelled and completed tasks are excluded from scheduling. Inbox tasks MUST NOT be scheduled under normal rules. Ready and in-progress tasks require a positive estimate. A completed predecessor satisfies its dependents; a cancelled predecessor does not. An explicit dependency override MAY start a blocked task, but it does not change the stored state or make the task normally eligible.

**RULE-STATE-2.** At most one task is in progress and current. Starting another task requires stopping the current timer. The desktop interface asks for confirmation. The agent interface MUST receive an explicit stop request. The previous task returns to ready unless it was completed or cancelled. Pausing stops the timer but keeps the task current and in progress. These pause and switch conventions are recorded in DEC-13.

**RULE-STATE-3.** Completing a task with incomplete prerequisites requires explicit confirmation. Without confirmation, the request is rejected and the incomplete prerequisites are named. With confirmation, the task is completed and the dependency edges remain.

**RULE-STATE-4.** Cancelling a task excludes it from scheduling and preserves its time entries. Cancelling the active task stops its timer. Cancelling does not satisfy the task's dependents; they remain blocked until the dependency is removed or the predecessor is completed.

**RULE-STATE-5.** Project statuses are active, paused, completed, and archived. Under normal rules, only active projects contribute schedulable tasks. Paused, completed, and archived projects do not. No override schedules a task from a paused project. Completed means the project's intended work is done and the project remains in history. Archived means the project is set aside without claiming completion and is hidden from the default project list. Both remain exportable and can be reopened. Changing a project's status does not delete its tasks.

**RULE-STATE-6.** Tasks and projects are not permanently deleted in v1; they are cancelled or archived. Time entries can be deleted with explicit confirmation. See DEC-10 for the unresolved conflict.

**RULE-STATE-7.** A task that is already in progress when a new prerequisite is added is not stopped, but it is reported as blocked and is not selected for further scheduling until the prerequisite is satisfied, unless the user makes an explicit override.

**RULE-STATE-8.** A scheduling input edit is an explicit, validated change to a task's priority, project, estimate, deadline, or source. It changes the inputs the rules use. It MUST NOT set an allocation, an itinerary position, or a rule exception, and it is recorded in the change feed with its actor.

### 5.3 Eligibility for scheduling

**RULE-ELIG-1.** A task is eligible for allocation under the normal rules only when all of the following hold:

- Its stored status is ready or in progress.
- It is not completed, cancelled, or in the inbox.
- It has a positive estimate.
- It belongs to no project, or to an active project.
- It is not blocked at the moment of selection: every prerequisite is completed or has been fully allocated earlier in the itinerary.
- If its remaining effort is zero, the five-minute completion slot applies; otherwise its remaining effort is positive.

**RULE-ELIG-2.** A task that is not eligible is not allocated under the normal rules. It may still receive a forecast of blocked or unscheduled. Ineligibility MUST NOT be hidden as safe. An explicit override MAY allocate a task whose only ineligibility is dependency order, but the task remains ineligible for normal scoring, is marked as overridden, and does not make the underlying condition disappear. A task that is in the inbox, lacks a positive estimate, or belongs to a paused, completed, or archived project is not overridable.

**RULE-ELIG-3.** A task in a paused project is not scheduled while the project is paused, and no override schedules it. Reactivating the project makes its tasks eligible again, subject to the other rules.

### 5.4 Logged work, remaining effort, and estimates

**RULE-EFFORT-1.** `logged minutes = sum of closed time entry durations + live elapsed minutes of the open entry, if any`. `remaining effort = max(estimate - logged minutes, 0)`. Only logged work changes remaining effort. `unallocated effort` is derived within one projection as remaining effort minus minutes already placed for the task earlier in the same projection. It is never stored, and a placed allocation does not change remaining effort.

**RULE-EFFORT-2.** If logged work exceeds the estimate, the estimate is retained and an `estimate_overrun` warning is reported. The application MUST NOT silently increase the estimate. If logged work equals the estimate, remaining effort is zero and there is no overrun.

**RULE-EFFORT-3.** An unfinished task with zero remaining effort is still unfinished. When it is otherwise eligible, it receives a five-minute completion slot. The five minutes is a planning placeholder, not a claim about how long completion will take. The task is not completed automatically.

**RULE-EFFORT-4.** Editing or deleting a time entry, revising an estimate, starting or stopping a timer, completing a task, or cancelling a task changes remaining effort and requires a recalculation.

**RULE-EFFORT-5.** A manual time entry covers past or current time, not future time. It MUST NOT overlap the active open entry. It MAY overlap a closed entry, but the application MUST report a non-blocking overlap notice. Parallel-work accounting is not a v1 feature.

### 5.5 Effective deadlines

**RULE-EFF-1.** A task's effective deadline is the earlier of its own deadline and the deadline of its active project. If only one exists, that one applies. If neither exists, the effective deadline is null. A task deadline later than the project deadline is allowed and does not remove the project deadline.

**RULE-EFF-2.** A project's effective deadline is its own deadline, which may be null. A project with no deadline has null slack.

**RULE-EFF-3.** Changing a project or task deadline is allowed even if it produces a missed forecast. The application reports the resulting risk rather than rejecting the change.

### 5.6 Availability and capacity
**RULE-CAP-1.** Weekly availability consists of zero or more local-time intervals per weekday. Each interval MUST end after it starts and MUST NOT overlap another interval on the same weekday. Each interval has a pause budget in whole minutes. The pause budget MUST be zero or greater and less than the interval duration. v1 interprets each interval within one local day. See DEC-12 for overnight intervals.

**RULE-CAP-2.** A date exception is keyed by local date. At most one exception exists per date. Setting an exception for an existing date replaces it. An `unavailable` exception removes all capacity on that date. A `replace_intervals` exception replaces the normal weekly intervals with its intervals, including their pause budgets. Removing an exception is unresolved; see DEC-11.

**RULE-CAP-3.** Capacity expansion processes each local calendar day in the horizon. It applies the date exception if present, otherwise the weekly rules. If a recorded capacity override exists for the current local day, the override's window replaces that day's working window for planning. It converts local wall times to absolute instants using the configured timezone. It subtracts each interval's pause budget from that interval, without placing the pause at a fixed time. It clips the start to the current time and the end to the horizon. It sorts intervals and merges touching or overlapping schedulable intervals.

**RULE-CAP-4.** If the current time is inside a working interval, capacity begins now and the interval's full pause budget stays reserved. If it is before the interval, capacity begins at the interval start. If it is after the interval, that interval contributes no capacity.

**RULE-CAP-5.** Local wall-clock times are authoritative across daylight-saving transitions. An interval that spans a transition has the actual elapsed duration implied by the timezone rules. The application MUST NOT assume every local day has 24 hours.

**RULE-CAP-6.** The default planning horizon is 90 calendar days. The user can configure 7 to 365 calendar days. The itinerary covers the horizon or stops earlier when all eligible work is allocated. Capacity outside the horizon is not used for itinerary allocation. Forecast treatment of deadlines beyond the horizon is unresolved; see DEC-5.

**RULE-CAP-7.** If no working availability is configured, the application MUST show a persistent `no_working_hours` setup warning and MUST NOT fabricate an itinerary.

**RULE-CAP-8.** A pause budget is a reserve for breaks the user takes without scheduling them ahead of time. It MUST NOT appear as an itinerary item or be assigned a fixed time. The itinerary schedules at most the capacity that remains after the reserve. The reserve is a planning allowance, not a tracked balance. A break shorter than the budget leaves capacity unused; a break longer than the budget consumes planned work time, so later items slip and may become unscheduled or missed. The application displays pause budgets separately from allocated work. If the user works through the reserve, the extra time is actual logged work, not an automatic change to the availability configuration.

### 5.7 Dependency closures

**RULE-CLOS-1.** A dependency closure for a deliverable is the deliverable plus all unfinished prerequisites, recursively. Each task is counted once, even if several paths reach it.

**RULE-CLOS-2.** A task closure is the task plus its prerequisites. A project closure is all unfinished tasks in the project plus all of their prerequisites. A project closure may include tasks from other projects.

**RULE-CLOS-3.** Completed tasks are not in a closure. A cancelled, inbox, paused-project, completed-project, missing, or otherwise unresolvable prerequisite makes the path blocked. It is not counted as work to perform. A shared prerequisite is counted once.

**RULE-CLOS-4.** Closure effort is the sum of remaining minutes, with unfinished zero-effort tasks counted as five minutes. It is not the sum of original estimates. Unallocated closure effort is closure effort minus minutes already allocated for the closure earlier in the same projection.

**RULE-CLOS-5.** A dependency cycle in the stored graph prevents a valid itinerary. The application MUST report the cycle with its path and MUST NOT produce a partial plan.

### 5.8 Slack and risk

**RULE-SLACK-1.** For a deliverable with an effective deadline, `slack = capacity minutes from now until the effective deadline - unallocated closure effort`. Capacity is measured after pause budgets are subtracted and is clipped to now and to the planning horizon. For a deliverable with no deadline, slack is null, not zero and not a sentinel.

**RULE-SLACK-2.** Slack is a per-deliverable feasibility measure. It does not subtract work allocated to other deadline closures. It answers whether this closure could fit in the capacity before its deadline if it had that capacity to itself. Aggregate contention is covered by the `plan_over_capacity` warning.

**RULE-SLACK-3.** Capacity before a deadline counts only real capacity intervals. If none exist, capacity is zero. If the deadline has passed and work remains, the deliverable is missed regardless of the arithmetic.

**RULE-RISK-1.** Risk is determined in this order:

1. `blocked`, if the closure contains an unresolvable prerequisite, such as a cancelled, inbox, missing, or otherwise ineligible prerequisite. Blocked MUST NOT be classified as safe. Blocked is reserved for dependency-path problems, not for the deliverable's own ineligibility.
2. `missed`, if the deadline has passed with work remaining, or slack is negative.
3. `unscheduled`, if there is remaining work but no projected finish within the planning horizon, including when the deliverable itself is not eligible for scheduling (inbox, missing estimate, or a paused, completed, or archived project), and the condition is not blocked or missed.
4. `tight`, if a projected finish exists and slack is non-negative but less than 15 percent of the closure effort.
5. `safe`, if a projected finish exists and slack is at least 15 percent of the closure effort, or if there is no deadline and a projected finish exists.

**RULE-RISK-2.** The 15 percent threshold is computed against closure effort, not the task's own estimate. Slack exactly equal to 15 percent is safe. Slack below 15 percent is tight. Negative slack is missed.

**RULE-RISK-3.** A task forecast uses the task's effective deadline. A project forecast uses the project's deadline. A task and its project can therefore have different risks.

**RULE-RISK-4.** A blocked path MUST be explained by naming the missing or ineligible prerequisite. The application MUST NOT classify a blocked path as safe or hide the blocker behind a generic error.

**RULE-RISK-5.** The overlap between missed and unscheduled is unresolved; see DEC-6. The blocked/unscheduled taxonomy is DEC-15. The precedence above is the preferred reading.

### 5.9 Deterministic selection order

**RULE-ORDER-1.** The selection order is a deterministic heuristic, not a global optimizer. It guarantees a stable, explainable order that respects the hard constraints; it does not guarantee that every feasible deadline is met. At each capacity decision, the application considers only eligible tasks and chooses in this order:

1. **Deadline-pressure tier.** Tasks that belong to at least one tight or missed deadline closure. Order by the least slack among those closures, most negative first.
2. **Dependency-protection tier.** Tasks that are prerequisites, direct or transitive, of a deadline-bearing deliverable and are not in the deadline-pressure tier. Order by the least slack of the deadline-bearing descendant or descendants they unlock.
3. **Ordinary tier.** All other eligible tasks.

Within a tier:

- Earlier effective deadline first. A task with an effective deadline comes before a task with no effective deadline.
- Lower task priority number first.
- Lower project priority number first, when both tasks have projects. If either task has no project, this criterion does not separate them; continue to the next criterion.
- Older creation time first.
- Stable identifier order last.

**RULE-ORDER-2.** A task is skipped, not removed, when its prerequisites are not yet completed or fully allocated. A successor can be scheduled after its predecessor's last allocated chunk, not merely after the predecessor's first chunk.

**RULE-ORDER-3.** The application re-evaluates the candidate set after every allocation chunk, not only at working-interval boundaries. A task can receive several consecutive chunks or be replaced by more urgent work.

**RULE-ORDER-4.** The system owns the itinerary order by default. v1 has no manual pins, drag reordering, or protected focus blocks. An explicit ordering override MAY move a chosen task earlier, including ahead of work already scheduled, or treat it as next. The override applies to one action, is visible and recorded, and does not create a persistent user-owned order.

**RULE-ORDER-5.** Input order MUST NOT affect the result. Reordering tasks, projects, dependencies, or capacity intervals MUST produce the same itinerary and forecasts.

### 5.10 Allocation

**RULE-ALLOC-1.** Allocate `min(unallocated effort, 60 minutes, remaining capacity in the current interval)`. Split an allocation at the current interval boundary. The application MUST NOT allocate outside capacity or beyond unallocated effort.

**RULE-ALLOC-2.** Sixty minutes is a maximum chunk, not a target. A task with less unallocated effort or less available capacity receives a shorter item.

**RULE-ALLOC-3.** A task with zero remaining effort receives a five-minute completion slot, then is treated as fully allocated for the rest of the horizon. The slot is created only when the task is otherwise eligible. If the current capacity interval has fewer than five minutes left, the task waits for a later interval; the slot is not shortened.

**RULE-ALLOC-4.** Work that cannot be allocated within the horizon is unscheduled. The application MUST NOT fabricate a time for it.

**RULE-ALLOC-5.** Each itinerary item has the task, start, end, allocated minutes, a machine-readable reason, and a human-readable explanation. The item MUST NOT extend beyond the capacity interval that contains it.

**RULE-ALLOC-6.** If no eligible task can be chosen, the capacity is left unused. The application MUST NOT fill it with ineligible work under normal rules. An explicit dependency override MAY start a blocked task, and the item is marked as overridden.

### 5.11 Forecasts

**RULE-FCAST-1.** A task forecast is produced for each unfinished, non-cancelled task. Its projected finish is the latest end of the allocations for the task and its prerequisite closure, but only if every task in that closure is fully allocated within the horizon. Otherwise the projected finish is null.

**RULE-FCAST-2.** A project forecast is produced for each active project with at least one unfinished, non-cancelled task. Its closure follows RULE-CLOS-2. Its projected finish is the latest finish across the closure if every task in the closure is fully allocated within the horizon; otherwise it is null. Its effective deadline is the project's deadline.

**RULE-FCAST-3.** A task with no deadline has null effective deadline and null slack. A project with no deadline has null effective deadline and null slack. Null is not a sentinel and MUST be distinguishable from zero.

**RULE-FCAST-4.** A forecast explanation MUST state the risk, the closure effort, the slack when a deadline exists, and the effective deadline. For missed, it MUST state the shortfall, which is the amount by which closure effort exceeds the capacity before the deadline. For blocked, it MUST name the unresolvable prerequisite. For unscheduled, it MUST say that no finish can be projected within the horizon.

**RULE-FCAST-5.** Forecasts are projections. They are not persisted as the authoritative plan and MUST be recomputed with the itinerary.

**RULE-FCAST-6.** Forecasts are a supporting view. They MUST NOT replace, outrank, or block the itinerary. The itinerary remains available and primary when no forecast exists or when forecast data is unavailable.

### 5.12 Reasons and explanations

**RULE-REASON-1.** Every itinerary item carries one machine-readable reason from this set:

- `deadline_protection`: selected because of deadline pressure, either because it belongs to a tight or missed closure or because its earlier effective deadline decided the order.
- `dependency_protection`: selected because it unlocks a deadline-bearing descendant.
- `task_priority`: selected from ordinary work because task priority decided the order.
- `project_priority`: selected from ordinary work because project priority decided the order.

When the decisive criterion is a stable tie after all preference criteria, the four-value set is insufficient; see DEC-7. The explanation MUST remain truthful.

**RULE-REASON-2.** The human explanation MUST name the decisive cause. For deadline pressure, name the deliverable, its effective deadline, and its slack. For dependency protection, name the descendant and its slack. For priority, name the comparison. For a stable tie, say that the higher criteria were equal and the order is stable. Do not claim a guarantee.

**RULE-REASON-3.** Reasons and explanations are per itinerary item, not per task. If a task's chunks are selected for different reasons, each item carries its own reason.

### 5.13 Warnings

**RULE-WARN-1.** Warning types and triggers:

| Type | Trigger | Suggested severity |
| --- | --- | --- |
| `deadline_tight` | A task or project forecast is tight. | warning |
| `deadline_missed` | A task or project forecast is missed. | critical |
| `estimate_overrun` | Logged work exceeds the task estimate. | warning |
| `blocked_deadline_path` | A deadline-bearing closure contains an unresolvable prerequisite. | critical |
| `plan_over_capacity` | Total remaining effort that MUST be scheduled for eligible work, including prerequisite closures, exceeds the capacity available in the planning horizon. | warning |
| `no_working_hours` | No working availability is configured. | critical |
| `dependency_cycle_prevented` | A dependency change was rejected for creating a cycle. | info |

The severity mapping is a suggestion, not fully specified; see DEC-8.

**RULE-WARN-2.** Warnings are first-class state. They are not transient toasts. A warning is stable for the same condition and entity, so it updates rather than duplicates. It resolves automatically when a recalculation finds the condition false. No manual dismissal action is specified.

**RULE-WARN-3.** Today lists warnings critical first, then warnings, then informational items. The tray popup shows critical warnings. Critical warnings and estimate overruns MAY also produce notifications when enabled, and approaching deadlines MAY once DEC-16 defines that trigger. A warning does not change data and does not block a valid action.

**RULE-WARN-4.** If no working hours are configured, the application shows the setup warning and does not fabricate an itinerary. If a deadline path is blocked, the risk is blocked and the missing or ineligible prerequisite is named. If total work exceeds horizon capacity, the application warns that some work is unscheduled.

**RULE-WARN-5.** A warning is a projection, not a guarantee.

**RULE-NOTIF-1.** Notifications are opt-in by category. The supported categories are critical warnings, estimate overruns, and approaching deadlines once DEC-16 defines that trigger. The user can set quiet hours and control the platform notification permission. Quiet hours are a local-time window in the configured timezone; a window whose end is before its start crosses midnight. During quiet hours the application suppresses delivery, and a condition that is still true when the window ends is delivered then. A notification MUST respect those settings.

**RULE-NOTIF-2.** The application delivers at most one notification per condition and entity. It MUST deduplicate repeated recalculation of the same condition. When the condition resolves, the notification is withdrawn or marked resolved and MUST NOT fire again for the same condition.

**RULE-NOTIF-3.** A notification is delivery, not planning state. The warning MUST remain visible in the application and tray. A notification MUST NOT be the only place a warning appears, and dismissing a notification MUST NOT dismiss the warning.

**RULE-NOTIF-4.** A denied permission, unsupported platform, or delivery failure MUST be reported without blocking planning. The application MUST NOT repeatedly prompt for permission after a denial.

### 5.14 Recalculation and refresh

**RULE-RECALC-1.** The application MUST recompute the itinerary, forecasts, and warnings synchronously after every saved change that affects tasks, projects, dependencies, estimates, deadlines, statuses, time entries, timers, availability, or the planning horizon.

**RULE-RECALC-2.** While a timer is active, the application refreshes elapsed time and warnings at least once per minute.

**RULE-RECALC-3.** A read-only diagnostic operation explains what would change, but it MUST NOT mutate data. The scheduler recalculates automatically; the user is not required to press a replan button.

**RULE-RECALC-4.** A change is atomic: all related changes take effect or none do. If a recalculation fails after a saved change, the saved change MUST remain, and the application MUST report that the projection is unavailable or out of date rather than presenting the old projection as current.

### 5.15 Determinism and stability

**RULE-DET-1.** Identical data, current time, timezone, and horizon MUST produce identical itinerary items, forecasts, reasons, and explanations.

**RULE-DET-2.** Observable order MUST NOT depend on incidental input order, incidental iteration order, randomness, or wall-clock drift. Stable tie-breakers are part of the product behavior.

### 5.16 Prediction versus guarantee

**RULE-PRED-1.** A forecast is a prediction from current data. The application MUST describe deadlines as projected, at risk, tight, missed, blocked, or unscheduled. It MUST NOT promise that a deadline will be met.

### 5.17 Overrides

**RULE-OVERRIDE-1.** An override is explicit, confirmed, recorded, and scoped to one action. It MUST identify the actor, target, affected rule, time, and optional reason. It MUST remain visible on the affected task, allocation, or history entry. An override is never the default action and MUST NOT be inferred from a normal action.

**RULE-OVERRIDE-2.** The overridable rules are exactly three planning constraints: dependency order, capacity boundaries for the current local day, and system-owned ordering. An override MAY start a blocked task, adjust today's working window, or move a chosen task earlier. Every other constraint is absolute: an override MUST NOT schedule an inbox task, a task without a positive estimate, or a task in a paused, completed, or archived project, and MUST NOT schedule a completed or cancelled task. An override MUST NOT bypass validation or weaken security, authentication, data integrity, secret handling, destructive confirmations, or another person's data.

**RULE-OVERRIDE-3.** An override does not falsify the underlying data. A blocked task remains blocked, a missing prerequisite remains missing, a paused project remains paused, and a risk remains reported. An overridden itinerary item MUST be marked as overridden and MUST NOT be presented as satisfying the normal rule or as safe.

**RULE-OVERRIDE-4.** An agent override requires explicit user intent in the request and confirmation from an attached interactive surface. The service MUST forward the confirmation to the most appropriate attached surface: the desktop window when open, otherwise the tray popup. If no interactive surface is attached, the service MUST reject the override with `confirmation_unavailable` and MUST NOT self-confirm. The application MUST reject an implicit, bulk, unattributed, or unconfirmed override.

**RULE-OVERRIDE-5.** A capacity override adjusts the current local day's working window: its start or its end. The adjusted window MUST remain a valid interval within that local day and MUST keep its pause budget less than its duration. It is used for planning that day only. It does not change weekly availability, date exceptions, pause budgets, or another day.

**RULE-OVERRIDE-6.** An ordering override applies to one task and one action. It MAY move the task earlier, including ahead of already scheduled work, or treat it as next. It expires when the task receives its next allocation or when the user cancels it, and it does not create a persistent user-owned order.

### 5.18 Agent change feed

**RULE-ACTIVITY-1.** The planner service maintains a durable, ordered change feed for agent-visible changes: task and project creation or update, status and completion changes, dependency changes, time-entry changes, timer changes, availability changes, overrides, and settings that affect planning. Each entry has a stable sequence position, a change kind, the affected entity, the time, the actor, and a short summary. Secrets MUST NOT appear in the feed.

**RULE-ACTIVITY-2.** An agent can request changes since an opaque cursor. The response returns the changes in order, a new cursor, and a clear indication when the requested cursor is too old or unknown. The feed is durable across service restart.

**RULE-ACTIVITY-3.** The change feed is a notification aid, not a replacement for authoritative reads. An agent that needs current values MUST read the affected entity or the current projection.

### 5.19 Worked examples

The examples use explicit times and a fixed local timezone. All arithmetic is in whole minutes. Pause budgets are zero unless an example states otherwise. They are illustrative; the rules above govern any input. The reason on the last remaining item in an example is trivial by construction; DEC-7 covers selections that only a stable tie-breaker decides.

#### EX-1. Deadline pressure outranks ordinary priority

**Inputs.**

- Current time: Monday 09:00.
- Availability: Monday 09:00 to 14:00, no exceptions.
- Project Atlas: active, deadline Monday 11:00, priority 3.
- Task A: belongs to Atlas, estimate 105 minutes, task priority 5, no own deadline. Effective deadline is 11:00.
- Task B: no project, estimate 120 minutes, task priority 1, deadline Monday 17:00.
- Task C: no project, estimate 60 minutes, task priority 3, deadline Monday 17:00.
- No dependencies, no logged work.

**Closures and slack.**

- Atlas project closure: {A}, 105 minutes. Capacity to 11:00 is 120 minutes. Slack 15. 15 percent of 105 is 15.75. Slack 15 is non-negative and below 15.75, so Atlas is tight.
- Task A forecast: same closure, slack 15, tight.
- Task B closure: {B}, 120 minutes. Capacity to 17:00 is 300 minutes. Slack 180. 15 percent of 120 is 18. Safe.
- Task C closure: {C}, 60 minutes. Capacity to 17:00 is 300 minutes. Slack 240. Safe.

**Allocation.**

- A is the only task in a tight closure, so it is selected first. It is allocated 60 minutes from 09:00 to 10:00.
- Re-evaluation: A has 45 minutes of unallocated effort. Capacity to 11:00 from 10:00 is 60. Slack 15, still tight. A is allocated 45 minutes from 10:00 to 10:45.
- A is fully allocated. B and C are ordinary work. B has the earlier deadline (17:00, equal to C) and higher task priority (1 versus 3), so B is selected. It receives 60 minutes from 10:45 to 11:45, then 60 minutes from 11:45 to 12:45.
- C receives 60 minutes from 12:45 to 13:45. The remaining 15 minutes of capacity are unused.

**Expected itinerary.**

| Start | End | Task | Minutes | Reason |
| --- | --- | --- | --- | --- |
| 09:00 | 10:00 | A | 60 | `deadline_protection` |
| 10:00 | 10:45 | A | 45 | `deadline_protection` |
| 10:45 | 11:45 | B | 60 | `task_priority` |
| 11:45 | 12:45 | B | 60 | `task_priority` |
| 12:45 | 13:45 | C | 60 | `task_priority` |

**Expected forecasts.**

- A: finish 10:45, effective deadline 11:00, slack 15, tight.
- B: finish 12:45, deadline 17:00, slack 180, safe.
- C: finish 13:45, deadline 17:00, slack 240, safe.
- Atlas: finish 10:45, effective deadline 11:00, slack 15, tight.

**What it demonstrates.** A tight deadline closure outranks a higher-priority task with no deadline pressure. The task-level and project-level thresholds use closure effort, not the task's own estimate. The reason on the last remaining item is trivial in this example; DEC-7 covers selections decided only by stable tie-breakers.

#### EX-2. Dependency protection with a safe deadline

**Inputs.**

- Current time: Tuesday 09:00.
- Availability: Tuesday 09:00 to 12:00, no exceptions.
- Project Atlas: active, deadline Tuesday 11:00, priority 3.
- Task P: no project, estimate 30 minutes, task priority 5, no deadline. It is a prerequisite of S.
- Task S: belongs to Atlas, estimate 60 minutes, task priority 5, no own deadline. Effective deadline is 11:00.
- Task R: no project, estimate 60 minutes, task priority 1, deadline Tuesday 12:00.
- Dependency: P is a prerequisite of S.
- No logged work.

**Closures and slack.**

- S closure: {P, S}, 90 minutes. Capacity to 11:00 is 120 minutes. Slack 30. 15 percent of 90 is 13.5. Safe.
- Atlas project closure: {P, S}, 90 minutes. Slack 30. Safe.
- R closure: {R}, 60 minutes. Capacity to 12:00 is 180 minutes. Slack 120. Safe.

**Selection.**

- No closure is tight or missed, so there is no deadline-pressure tier.
- P is a prerequisite of S, a deadline-bearing deliverable. P is in the dependency-protection tier with S's slack of 30. S and R are ordinary work.
- P is selected first and allocated 30 minutes from 09:00 to 09:30.
- S and R are now both eligible. S has effective deadline 11:00; R has 12:00. S is selected and allocated 60 minutes from 09:30 to 10:30.
- R is selected and allocated 60 minutes from 10:30 to 11:30. The remaining 30 minutes of capacity are unused.

**Expected itinerary.**

| Start | End | Task | Minutes | Reason |
| --- | --- | --- | --- | --- |
| 09:00 | 09:30 | P | 30 | `dependency_protection` |
| 09:30 | 10:30 | S | 60 | `deadline_protection` |
| 10:30 | 11:30 | R | 60 | `deadline_protection` |

**Expected forecasts.**

- P: finish 09:30, no deadline, slack null, safe.
- S: finish 10:30, effective deadline 11:00, slack 30, safe.
- R: finish 11:30, deadline 12:00, slack 120, safe.
- Atlas: finish 10:30, effective deadline 11:00, slack 30, safe.

**What it demonstrates.** A prerequisite is pulled forward because it unlocks a deadline-bearing deliverable, even though the closure is safe and the prerequisite has no deadline and a low priority. The successor is still scheduled after its prerequisite's final allocated chunk.

#### EX-3. A missed project deadline and an estimate overrun

**Part A. Missed project deadline.**

**Inputs.**

- Current time: Thursday 09:00.
- Availability: Thursday 09:00 to 11:00, no exceptions.
- Project Atlas: active, deadline Thursday 11:00.
- Task P: no project, estimate 60 minutes, prerequisite of S.
- Task S: belongs to Atlas, estimate 120 minutes, no own deadline. Effective deadline 11:00.
- No logged work.

**Closures and slack.**

- Atlas project closure: {P, S}, 180 minutes. Capacity to 11:00 is 120 minutes. Slack -60. Missed.
- S closure: {P, S}, 180 minutes. Slack -60. Missed.
- P has no deadline, so its slack is null.

**Allocation.**

- P and S both belong to Atlas's missed closure, so both are in the deadline-pressure tier with slack -60. P is the only ready task because S depends on P. P is allocated 60 minutes from 09:00 to 10:00.
- P is fully allocated. S is ready and is allocated 60 minutes from 10:00 to 11:00. S has 60 minutes remaining, but no capacity remains in the horizon for this example.

**Expected itinerary.**

| Start | End | Task | Minutes | Reason |
| --- | --- | --- | --- | --- |
| 09:00 | 10:00 | P | 60 | `deadline_protection` |
| 10:00 | 11:00 | S | 60 | `deadline_protection` |

**Expected forecasts.**

- P: finish 10:00, no deadline, slack null, safe.
- S: no projected finish within the horizon, effective deadline 11:00, slack -60, missed.
- Atlas: no projected finish within the horizon, effective deadline 11:00, slack -60, missed.

**Expected warning.** A critical `deadline_missed` warning for S and Atlas, stating a 60-minute shortfall.

**Part B. Estimate overrun and completion slot.**

**Inputs.**

- Current time: Friday 09:00.
- Availability: Friday 09:00 to 10:00, no exceptions.
- Task T: estimate 60 minutes, deadline Friday 10:00. One closed time entry of 45 minutes. No active timer.

**First state.**

- Logged work 45, remaining 15. Capacity to 10:00 is 60. Slack 45. 15 percent of 15 is 2.25. Safe.
- T is allocated 15 minutes from 09:00 to 09:15. Projected finish 09:15, safe.

**After a retroactive 30-minute entry.**

- Logged work becomes 75. Remaining is max(60 - 75, 0) = 0. The estimate remains 60.
- An `estimate_overrun` warning appears, reporting 15 minutes of overrun.
- The task is still unfinished. It is otherwise eligible and receives a five-minute completion slot.
- Capacity to 10:00 is 60. Slack is 60 - 5 = 55. 15 percent of 5 is 0.75. Safe.
- T is allocated 5 minutes from 09:00 to 09:05. Projected finish 09:05, safe.

**What it demonstrates.** Logged work reduces remaining effort and can change the itinerary. An overrun is reported without silently rewriting the estimate. A zero-effort unfinished task is not completed automatically; it receives a minimal completion slot and remains unfinished until the user explicitly completes it.

---

#### EX-4. A pause budget reserves break time without scheduling it

**Inputs.**

- Current time: Tuesday 09:00.
- Availability: Tuesday 09:00 to 12:00, pause budget 30 minutes.
- Task D: no project, estimate 150 minutes, no deadline.
- No dependencies, no logged work.

**Capacity.**

- The interval is 180 minutes. The reserve is 30 minutes, so capacity is 150 minutes.
- No break is placed on the itinerary.

**Expected itinerary.**

| Start | End | Task | Minutes | Reason |
| --- | --- | --- | --- | --- |
| 09:00 | 10:00 | D | 60 | `task_priority` |
| 10:00 | 11:00 | D | 60 | `task_priority` |
| 11:00 | 11:30 | D | 30 | `task_priority` |

**What it demonstrates.** The reserve reduces capacity by a fixed amount and never appears as an item. D is the only task, so its reason label is trivial. If the user takes a 30-minute break, the plan holds. If the user takes an unplanned 45-minute break at 10:00, the last 15 minutes no longer fit before 12:00 and are reported as unscheduled; the application neither extends the reserve nor moves the work automatically. If the user skips the break, the plan is unchanged and the user finishes early.

---

## 6. Service architecture and deployment modes

v1 supports two deployment modes for the same planner service. The mode is a deployment and lifecycle choice, not a difference in planning behavior. Both modes use the same rules, the same local data, and the same projections.

### 6.1 Daemon mode

In daemon mode, a local background service owns validation, authoritative data, scheduling, forecasting, warnings, recalculation, and the active timer. It serves the desktop client, the tray client, and agent clients. The daemon can run without a desktop window and can outlive the desktop interface. It starts at login where supported or on explicit request. In v1 the daemon is local to the user's computer and is not exposed to the network.

The desktop and tray are clients. They connect to the daemon, display shared state, and send explicit requests. Closing or quitting the interface does not stop the daemon. Stopping the daemon is a separate explicit action.

### 6.2 Single-process mode

In single-process mode, one process owns the planner service, the desktop window, and the tray. MCP is the only interface that leaves the process. There is no separate API listener. Closing the main window hides it and keeps the process, timer, tray, and agent access alive. Quitting the process stops the service and agent access.

### 6.3 Conceptual diagram

```text
Daemon mode

Desktop client ------+
Tray client ---------+--> Local daemon ---> Local planning data
Agent client --------+          |
   (MCP)                        +--> scheduling, forecasts, warnings,
                                     timer, change feed

Single-process mode

Desktop window ------+
Tray icon/menu ------+--> Planner service (same process) --> Local planning data
Agent client --------+          |
   (MCP)                        +--> scheduling, forecasts, warnings,
                                     timer, change feed
```

The diagram is conceptual. It does not prescribe transport, framing, endpoints, ports, process names, or internal interfaces.

### 6.4 Responsibilities

| Component | Responsibility | What it MUST NOT do |
| --- | --- | --- |
| Planner service | Validate, persist, schedule, forecast, warn, recalculate, own the timer, and coordinate atomic changes. | Let any surface bypass its rules or become a second data owner. |
| Daemon host (daemon mode) | Own the service process, data ownership, startup, single-instance behavior, and shutdown. | Let a second daemon write the same data. |
| Desktop client (daemon mode) | Present Today, Tasks, Projects, Review, and Settings; collect input; show results and errors. | Compute its own schedule, forecast, or validation rules; write the data store directly. |
| Desktop window and service (single-process mode) | Present the interface and host the service in one process. | Split into two competing planners or expose an unintended second interface. |
| Tray icon, menu, and popup | Present current task, timer, next work, and critical warnings; offer quick actions. | Keep a divergent plan or bypass the service. |
| Agent client | Read and write through MCP using explicit identifiers and explicit overrides. | Open the local data store directly, guess targets, make implicit batch changes, or start a competing service. |
| Local planning data | Durable authoritative records for tasks, projects, dependencies, time, availability, overrides, and settings. | Be written by desktop, tray, or agent adapters directly, or by two service owners at once. |
| Tracker integration (v2 advisory) | Perform optional read-only remote searches and fetches for configured plugins; cache attachment metadata. | Write to remote trackers or change local planning fields. |

### 6.5 Shared state and cross-surface effects

All surfaces in either mode share one authoritative planning state:

- A task created or changed in the desktop interface is visible to the tray and to agents after the current projection is recomputed.
- A task created or changed by an agent is visible in the desktop and tray after refresh.
- Starting or stopping a timer in one surface changes the current task everywhere because the service owns the timer.
- Logging, editing, or deleting time in one surface changes remaining effort and every forecast.
- Changing availability, deadlines, priorities, dependencies, or overrides in one surface changes the itinerary and warnings everywhere.
- The agent change feed reports the same changes that the other surfaces see.
- An agent-requested override is confirmed on an attached interactive surface; when none is attached, the service rejects it with `confirmation_unavailable`.
- In v2, attaching, refreshing, or detaching tracker context changes only the local attachment view. It does not change the itinerary.
- A surface MUST NOT keep a private schedule or a private validation rule set. Presentation caches are allowed only if they refresh from the service and MUST NOT override it.

### 6.6 Lifecycle

**Daemon mode startup.** The daemon starts at login where supported or on explicit request. It acquires sole ownership of the local data, applies required data upgrades, loads settings, and computes the current projection. The desktop and tray connect when they start. No account or network connection is required.

**Daemon mode interface close or quit.** Closing the window hides the desktop client. Quitting the interface disconnects the client. The daemon, timer, and agent access remain available. Stopping the daemon is a separate explicit action.

**Daemon mode stop.** Stopping the daemon shuts down the service and releases data ownership. Agents then receive a clear unavailable-service error. Local data remains. The behavior of an active timer across a stop, restart, or crash is unresolved; see DEC-4.

**Single-process startup.** The process starts the planner service before exposing window or tray operations. It acquires sole ownership of the local data, applies required data upgrades, loads settings, and computes the current projection.

**Single-process window close or quit.** Closing the main window hides it. The process, tray, timer, and agent access remain alive. Quitting the process stops the service and agent access. Local data remains. The behavior of an active timer across service stop, restart, or crash is unresolved; see DEC-4.

**Opening the window.** The window shows the current shared state. Opening it does not create a second service or a second plan.

**Restarting.** Local data and settings are preserved. The service recomputes the projection. The active-timer behavior across shutdown is unresolved.

**Switching modes.** The current service owner MUST stop and release the data before the other mode starts. The new mode loads the same data and computes the same projection. Two service owners MUST NOT write the same data at once.

**Service unavailable to an agent.** The agent receives a clear unavailable-service response. It MUST NOT open the data store directly, start a competing service, or fabricate a plan.

**Service failure inside a running service.** The desktop and tray MUST report the problem and MUST NOT compute their own fallback schedule.

### 6.7 Established service facts and open architectural decisions

This specification establishes that:

- There is one planner service that is the single authority for rules and data.
- v1 supports daemon mode and single-process mode.
- Daemon mode can run headless and can outlive the desktop interface.
- Single-process mode owns the service, window, and tray in one process, and MCP is its only external interface.
- In both modes, desktop and tray surfaces are clients of the service, not independent planners.
- Agents reach the same service through MCP.
- Only one service owner MUST write the local data at a time.
- A server-hosted daemon reachable over a network is a future possibility, not a v1 requirement.

This specification does not settle which mode is the default installation. That is DEC-3.

This specification does not prescribe transport, framing, endpoints, ports, process count, or internal interfaces. The implementation MAY choose those, provided that all surfaces share one authority, changes are atomic, agents cannot access the data store directly, and only one owner writes the data.

### 6.8 Architecture rules

**RULE-ARCH-1.** One planner service is the single authority for validation, authoritative data, scheduling, forecasting, warnings, recalculation, and the active timer. Desktop, tray, and agent surfaces MUST use it. They MUST NOT compute their own schedule, validation, or forecast, and they MUST NOT access the authoritative data directly.

**RULE-ARCH-2.** Every surface applies the same rules to the same data. A change through the desktop, tray, or agent interface MUST produce the same result for identical input and state. There MUST be no entry-point-specific semantics.

**RULE-ARCH-3.** After a successful change and its recalculation, all open surfaces reflect the new state. A surface MUST NOT keep a divergent plan. Presentation caches are allowed only if they refresh from the service and MUST NOT override it.

**RULE-ARCH-4.** In daemon mode, the daemon owns the service and data, can run without a desktop window, and can outlive the desktop interface. Closing or quitting the interface MUST NOT stop the daemon. Stopping the daemon is an explicit action that ends agent access. The active timer belongs to the daemon, subject to DEC-4.

**RULE-ARCH-5.** In single-process mode, one process owns the service, desktop window, and tray. MCP is the only external interface. Closing the main window hides it and keeps the service, tray, timer, and agent access alive. Quitting the process stops the service and agent access.

**RULE-ARCH-6.** Mode switching requires stopping the current service owner before starting the other mode. The new mode loads the same data and produces the same projection. Two service owners MUST NOT write the same data at once. Mode is a deployment choice, not a separate planning semantics.

---

## 7. V2 advisory: plugin and external-tracker capabilities

**Status: v2 only and advisory.** Nothing in this section is a v1 requirement. It is retained so that a future implementation has context, but a v1 implementation is not bound by it.

### 7.1 Purpose and user value

External trackers hold work items such as issues, tickets, pull requests, or tasks. The planner lets the user attach a specific remote item to a local task as context. The local task remains the planning object; the remote item supplies context such as its title, status, URL, and last update.

This is valuable when work arrives from a tracker but the user needs a local estimate, priority, dependency, deadline, and time log. The user can see the remote context without turning the planner into a tracker client.

### 7.2 What read-only means

Read-only is a capability boundary:

- A plugin can search its configured tracker and fetch a specific remote item.
- A plugin MUST NOT create, edit, comment on, transition, or otherwise write remote data.
- A local change MUST NOT write to the remote tracker.
- A remote status MUST NOT automatically complete, cancel, re-estimate, or re-prioritize a local task.
- The attachment is a contextual link, not a mirror.

Read-only does not mean the local attachment is immutable. The user can attach, refresh, detach, and attach the same remote item to another local task with explicit confirmation. It means the remote system MUST NOT be mutated.

### 7.3 Setup and configuration

- The user installs a tracker plugin locally. The plugin implementation does not auto-download or install plugins from the network.
- The plugin implementation supports one configured connection per plugin.
- Settings lists discovered plugins, their enabled or disabled state, their configuration, and their last error.
- The user supplies the plugin's non-secret configuration and any secret credentials.
- A plugin with incomplete or invalid configuration MUST NOT be enabled.
- A disabled plugin MUST NOT be executed.
- Ordinary configuration MAY be stored with local planning data. Secret credentials MUST be stored in the operating system's protected credential storage and MUST NOT appear in local planning data, plugin-visible configuration, logs, exports, or agent responses.
- The application controls all remote reads for the plugin and protects the credential from it. The application MUST NOT grant a plugin access to local files, processes, planning data, or arbitrary network destinations. Remote reads are restricted to the tracker destinations configured for that plugin, are bounded in time and response size, and are validated before display.
- A broken or unavailable plugin MUST NOT prevent local tasks, scheduling, forecasts, or time tracking from working.

### 7.4 Search, attach, refresh, and detach

**Search.** The user selects one enabled, configured plugin and enters a query. The application returns a bounded list of candidate remote items with title, status, and URL. Results MUST NOT be attached automatically. The application MUST NOT search all trackers implicitly.

**Attach.** The user chooses one result and attaches it to an explicit local task. The application fetches or accepts the remote item's normalized metadata and stores a local attachment with the attach time and last-refresh time. The remote fields are labeled read-only. Attaching MUST NOT change the local task's estimate, priority, dependencies, status, or completion, and MUST NOT recalculate the itinerary. If the same remote item is already attached to another local task, the application requires explicit confirmation before attaching it again.

**Refresh.** The user can refresh one attachment. Refreshing updates only the cached remote title, status, URL, and update time. It is a remote read. It does not change local planning fields or the itinerary. The plugin implementation does not poll in the background and does not refresh automatically.

**Detach.** The user can detach an attachment with explicit confirmation. Detaching removes the local link only. It does not change the remote item.

**Open remote item.** The user can open the remote URL in the normal way. The application MUST NOT imply that the remote system is now under its control.

### 7.5 Offline, failure, and limitations

- Cached attachment metadata remains visible when the tracker is offline or a refresh fails. The last-refresh time remains visible.
- A plugin search, refresh, timeout, resource-limit termination, malformed response, missing plugin, disabled plugin, or capability denial is reported as a contained error. Local planning remains fully usable.
- Disabling a plugin does not remove existing local attachments. Their cached metadata remains visible; search and refresh become unavailable until the plugin is enabled again.
- A plugin response is untrusted. The application MUST validate it and render remote text as plain text, not executable or active content.
- v2 has no production tracker integration requirement. The core product remains usable with no plugin installed.
- v2 has no background polling, no remote writes, no auto-download, no plugin marketplace, and no multi-connection configuration per plugin.
- Attachment changes do not trigger itinerary recalculation because they are contextual only.

### 7.6 Credentials and trust

- Secrets belong to the operating system's protected credential storage, keyed to the plugin and connection.
- The plugin receives only validated non-secret configuration.
- The application uses the credential only for its own remote read. The plugin MUST NOT receive the credential directly.
- Logs record only non-sensitive request outcomes. They MUST NOT contain credentials or full remote response bodies.
- Exports exclude secrets.
- Agent reads of planner state exclude secrets.
- A plugin MUST NOT modify the local planning data. Attachment changes are performed only by an explicit user or agent request to the application service.

### 7.7 Plugin rules

**RULE-PLUG-1.** A plugin is read-only with respect to remote systems. It can search and fetch remote items. It MUST NOT create, edit, comment on, transition, or otherwise write remote data. A local change MUST NOT write to a remote tracker. A remote status MUST NOT automatically change a local task's status, estimate, priority, dependencies, or completion.

**RULE-PLUG-2.** The user installs and configures a plugin locally. v2 supports one configured connection per plugin, MUST NOT auto-download plugins, and MUST NOT execute a disabled plugin. A plugin with invalid or incomplete configuration MUST NOT be enabled. Settings shows the plugin's state, configuration, and last error.

**RULE-PLUG-3.** Tracker operations are explicit. The user or agent selects one configured plugin; the application MUST NOT search all trackers implicitly. Search returns candidates without attaching them. Attaching requires an explicit local task and remote item. Refreshing updates only cached metadata. Detaching removes only the local link. Attachment changes do not trigger itinerary recalculation.

**RULE-PLUG-4.** Secret credentials MUST be stored in the operating system's protected credential storage and MUST NOT be present in local planning data, plugin-visible configuration, logs, exports, or agent responses. The application uses the credential only for its own remote read; the plugin MUST NOT receive it.

**RULE-PLUG-5.** Plugins and their output are untrusted. A plugin MUST NOT access local files, processes, planning data, or arbitrary network destinations. Remote reads are restricted to configured tracker destinations, bounded in time and response size, and validated before display. Plugin errors, timeouts, resource-limit termination, malformed responses, and disabled or missing plugins are contained and do not impair local planning. Disabling a plugin preserves existing local attachments and their cached metadata; search and refresh become unavailable until it is enabled again.

---

## 8. User-visible quality expectations and data behavior

### 8.1 Local-first and privacy

- All planning data is stored locally on the user's computer.
- The core product works without an account, sign-in, cloud service, or network connection.
- In v2, optional tracker integrations MAY use the network. They are never required for tasks, projects, availability, scheduling, forecasts, time tracking, warnings, notifications, or data export. v1 uses no network connection for core planning.
- This specification does not define telemetry, analytics, encryption at rest, synchronization, import, automatic backup, or recovery services. The implementation MUST NOT claim them.
- This specification does not require encryption at rest. The local data is protected by the operating system's user account.

### 8.2 Data continuity and export

- All planning data lives in a user-visible local data location.
- The user can start an export from Settings. The export is a documented, portable representation of the user's planning data.
- In v2, the export excludes plugin secrets. In v1 there are no plugin secrets or attachment records in the export.
- Application upgrades MUST preserve existing tasks, projects, dependencies, time entries, availability, overrides, and settings. In v2 they also preserve attachments. A failed upgrade MUST NOT silently discard data.
- Copying the local data location while no service owner is running preserves the user's data. This is a backup-friendly property, not a synchronization feature.
- Restarting the service or application preserves data and settings and recomputes the plan.
- Tracker secrets are not part of the local planning data or the export. In v2, moving data to another machine or operating-system account may require re-entering tracker credentials.

**RULE-DATA-1.** The core product is local-first and offline capable. All planning data is stored locally. The core works without an account, sign-in, cloud service, or network. In v2, optional tracker integrations MAY use the network but are never required.

**RULE-DATA-2.** The local data location is visible to the user. The user can export a documented, portable representation of the planning data, including overrides. In v2, exports exclude plugin secrets. This specification does not define import, automatic backup, synchronization, encryption, or recovery services, and the application MUST NOT claim them.

**RULE-DATA-3.** Application upgrades and restarts preserve tasks, projects, dependencies, time entries, availability, overrides, and settings. In v2 they also preserve attachments. A failed upgrade or recalculation MUST NOT silently discard data or present a stale projection as current.

### 8.3 Validation and error behavior

- Invalid input produces field-level feedback in the desktop interface and a structured error for an agent.
- Missing records produce a clear not-found result.
- Conflicts such as duplicate dependencies produce a clear conflict result.
- Dependency cycles produce a clear rejection with the concrete cycle path.
- Attempting a second active timer produces an active-timer conflict unless the request explicitly stops the current timer.
- Completing a task with incomplete prerequisites without confirmation produces a confirmation-required result that names the prerequisites.
- A destructive operation without confirmation is rejected.
- An agent request when the planner service is unavailable produces a clear unavailable-service result.
- No working availability produces a setup warning, not a fabricated plan.
- An override without confirmation produces a confirmation-required result that names the affected rule. An agent override with no attached interactive surface produces a `confirmation_unavailable` error.
- In v2, plugin failures produce a plugin-specific error and do not break local planning.
- Raw or low-level storage errors MUST NOT be shown as the user-facing explanation. The application MUST NOT leak internals while reporting a failure.

### 8.4 Atomicity and consistency

- A change is all-or-nothing. A failed task or project change, dependency validation and change, timer start or stop, time-entry edit, or availability replacement MUST leave the previous data intact.
- A successful change produces exactly one recalculation and one consistent view of the new state.
- A failed recalculation MUST NOT discard a saved change. The application MUST report that the projection is unavailable or out of date rather than presenting a stale plan as current.
- The desktop, tray, and agent interface see the same results for the same data. There is no entry-point-specific scheduling or validation.

### 8.5 Accessibility and keyboard operation

- Primary workflows MUST be operable by keyboard: navigation, task and project editing, starting and stopping work, completing a task, and using the tray popup.
- Interactive controls need accessible labels and a visible focus state.
- Critical actions MUST NOT be mouse-only.
- The application SHOULD preserve a sensible reading and focus order when the itinerary or warnings update.
- The product does not specify a particular accessibility standard. It requires usable keyboard access and accessible structure for the primary workflows.

### 8.6 Cross-platform behavior

- The desktop application MUST work on Linux, macOS, and Windows.
- The core planning behavior MUST be identical across platforms.
- Tray support and focus behavior vary by Linux desktop environment and Wayland compositor. The application MUST NOT claim uniform tray behavior.
- Escape and an explicit toggle or hide action MUST always work. Hide-on-focus-loss is optional and MUST NOT be the only way to close the popup.
- Linux tray support SHOULD be verified on at least one GNOME-based, one KDE-based, and one wlroots-based environment before claiming broad support. Limitations MUST be documented.
- Platform-specific features such as launch at login are offered only where the platform supports them.

### 8.7 Empty states and feedback

- The Today view MUST distinguish at least these empty causes: no tasks, no working hours, no capacity in the horizon, all work blocked, missing estimates, and work that is ineligible because its project is paused, completed, or archived.
- A task or project list with no records shows an empty state, not a fabricated row.
- In v2, a tracker search with no results says so without changing data.
- The application shows a persistent active-timer indicator and a count of critical warnings.
- Saving errors appear next to the relevant field.
- A failed refresh or recalculation is visible. The application MUST NOT silently show old values as current.
- The application MUST NOT show a "safe" or "all clear" state when the underlying data is blocked, unscheduled, or unavailable.

### 8.8 v1 completion signals

v1 is complete when a user can:

1. Quick-capture a task with only a title while a timer runs, without interrupting the timer or losing the task.
2. Define working hours for a week, set a pause budget for breaks, and add a one-off day off.
3. Create projects and tasks with the required fields and attach tasks to projects.
4. Create a dependency graph that rejects self-edges, duplicates, and cycles with a path.
5. See a task with an unfinished dependency kept out of actionable work until its prerequisite is completed or planned first, and use an explicit override when needed.
6. Define estimates, semantic priorities, and deadlines that change the itinerary.
7. See an automatically ordered itinerary that fits future working capacity after pause budgets, explains each item, and does not need hand maintenance.
8. See supporting forecasts that explain deadline risk without replacing or outranking the itinerary.
9. Start, pause, complete, and time work, and see the plan update. The user can take a break without scheduling it in advance.
10. Add, edit, and delete historical time entries and see remaining effort, itinerary, projected completion, and warnings update.
11. See warnings in the application and tray and receive operating-system notifications for enabled notification categories.
12. See the tray popup show the current timer, next work, and critical warnings, and open the planner.
13. Use an agent to create and update tasks, set dependencies, log time, start and stop work, complete tasks, edit scheduling inputs, request explicit overrides, and read the updated plan and change feed.
14. Get identical schedule results from the desktop, tray, and agent interfaces for identical data.
15. Run the planner in daemon mode without a desktop window or in single-process mode, switch modes without losing data, and keep only one service owner.
16. Use the application offline and export or copy local data.

This list is an acceptance summary, not a second feature list. The stories and rules above are authoritative.

---

## 9. Unresolved product decisions

These decisions are not settled by the current product definition. Each entry states the question, the supported alternatives, the user-visible consequence, and the current treatment in this specification. A team MUST resolve them before claiming complete implementation readiness. Where a suggestion is given, it is advice, not an established requirement.

### DEC-1. Does "estimate" mean total effort or remaining effort?

**Question.** Is a task's estimate the total effort planned for the task, or the effort remaining from now?

**Conflict.** One product definition describes the estimate as "the user's current best guess of remaining effort." The remaining-effort rule defines remaining effort as `max(estimate - logged work, 0)`. These cannot both hold after work is logged: if the estimate is already remaining effort, subtracting logged work counts the work twice.

**Alternatives.**

- **A. Total effort.** The estimate is total planned effort. Remaining effort is estimate minus logged work. Revising the estimate revises total planned effort. An overrun leaves the estimate unchanged and produces a warning. This matches the explicit remaining-effort formula and the rule that an overrun MUST NOT silently increase the estimate.
- **B. Remaining effort.** The estimate is the current remaining effort. Logging work does not subtract from it automatically; the user or agent MUST revise it, or the application MUST reduce it when logging. This matches the "remaining effort" wording but contradicts the formula.
- **C. Two values.** Keep a total estimate and a separate remaining estimate. This is more expressive but adds a second field to the desktop and agent surfaces and requires rules for keeping them consistent.

**Consequence.** Forecast slack, itinerary length, overrun warnings, and estimate-revision behavior all differ. Under A, logging 30 minutes against a 120-minute estimate leaves 90. Under B, the same log leaves 120 unless the estimate is revised.

**Current treatment.** This specification uses A, total effort, because it is the only reading consistent with the explicit formula, the overrun rule, and the requirement that logged work change projections. If B is intended, RULE-EFFORT-1 MUST be replaced and the overrun rule reconsidered.

**Suggested resolution.** Choose A. If users think in remaining effort, present it as "estimate at start" plus a derived "remaining" value rather than redefining the field.

### DEC-2. What instant does a date-only deadline represent?

**Question.** When a user enters a calendar date rather than a specific time, what absolute instant is the deadline?

**Alternatives.**

- End of the local day, for example 23:59 on that date. This matches the common meaning of "due Friday" and gives the user the whole day.
- Start of the local day, which makes the deadline strict but surprising.
- A default working-hour boundary, such as 17:00.
- Require an explicit time for every deadline.

**Consequence.** The choice changes whether work on the deadline day counts, how much capacity is available before the deadline, whether a deadline is reported as missed, and how much slack is shown.

**Current treatment.** Deadlines are absolute instants. The worked examples use explicit times. The desktop date-to-instant rule is not specified.

**Suggested resolution.** Treat a date-only deadline as the end of that local day, and allow an optional time. State the interpretation in the interface so the user is not surprised.

### DEC-3. Which service mode is the default installation?

**Question.** v1 supports daemon mode and single-process mode. Which mode should a new installation use by default, and how should the user choose?

**Alternatives.**

- **A. Daemon mode by default.** The daemon starts at login and owns the service and data. The desktop and tray are clients. Agents can work while the interface is closed. This favors always-on agent access and a stable timer, at the cost of a resident background process and a clearer separation between "quit the interface" and "stop the service".
- **B. Single-process mode by default.** The desktop process owns the service, window, and tray. This is simpler to install and reason about, and it stops cleanly when the user quits, but agents cannot work while the application is not running.
- **C. Ask at first run.** The user chooses the mode during setup, with a short explanation of agent availability and background behavior. The choice can be changed later by stopping one owner and starting the other.

**Consequence.** Agent availability, startup behavior, resource use, and the meaning of "quit" differ by default. A user who expects agents to keep working after closing the window will be surprised by single-process mode. A user who expects quit to stop everything will be surprised by daemon mode.

**Current treatment.** Both modes are established v1 capabilities. The default is not settled. The lifecycle rules in Section 6 apply to both.

**Suggested resolution.** Ask at first run and suggest daemon mode for users who want agents available while the interface is closed. Keep single-process mode as the lightweight option. Make the current mode and the Stop Service action visible.

### DEC-4. What happens to an active timer on service stop, restart, or crash?

**Question.** When an open timed span exists and the service stops, restarts, or crashes, does the span continue, stop, or require correction?

**Alternatives.**

- Keep the open entry across restart, so elapsed time includes the time the application was not running.
- Stop the entry at a graceful service stop using the stop time, and prompt on next start to confirm or adjust it.
- Prompt on next launch to choose the end time, especially after a crash.
- Discard an untracked interval after a crash.

**Consequence.** Logged work, remaining effort, forecasts, and overrun warnings can be materially wrong. A timer that continues through a weekend would overcount work; a timer that is silently discarded would undercount it. Daemon mode and single-process mode can differ in how long the service stays alive, so the rule MUST be stated per service stop, not per interface close.

**Current treatment.** Open entries persist while the service runs. The service stop, restart, and crash behavior is not specified.

**Suggested resolution.** Stop the timer at a graceful service stop and ask the user to confirm the end time on next start. After a crash, ask whether to keep the untracked interval, stop it at the last known time, or discard it. Do not silently continue a timer across a long shutdown.

### DEC-5. How should forecasts treat deadlines beyond the planning horizon?

**Question.** The itinerary uses a configurable horizon of 7 to 365 days. A deadline may be later than the horizon. How should risk be computed then?

**Alternatives.**

- **A. Horizon-bounded forecast.** Use only capacity within the horizon. A far deadline can appear tight, missed, or unscheduled solely because the plan does not look far enough ahead.
- **B. Deadline-bounded forecast capacity.** Keep the itinerary bounded by the horizon but extend capacity for forecast purposes to the deadline. The forecast can be safe even though no itinerary items are shown beyond the horizon.
- **C. Explicit beyond-horizon state.** Report that the deadline is beyond the planning horizon and cannot be certified yet, rather than claiming safe or missed.

**Consequence.** Long deadlines can produce false alarms under A or false confidence under B. C is honest but requires a user-facing state beyond the five risk values.

**Current treatment.** The itinerary and slack use the horizon. The treatment of deadlines beyond the horizon is unresolved. The current rules would classify a far deadline using horizon-bounded capacity.

**Suggested resolution.** Use B or C. Alternative A is best avoided for deadlines that extend well beyond the horizon because it can report a missed deadline that is actually feasible.

### DEC-6. When should a deliverable be unscheduled rather than missed?

**Question.** A deadline-bearing deliverable can have negative slack and also no projected finish within the horizon. Should the risk be missed or unscheduled?

**Alternatives.**

- **Missed when slack is negative.** Negative slack means the work cannot fit before the deadline, regardless of whether a finish was projected. Unscheduled is reserved for work with no deadline or for work that cannot be placed for reasons other than deadline capacity.
- **Unscheduled when no finish can be projected.** The absence of a finish is the dominant fact. Missed is reserved for a finish that exists but occurs after the deadline, or a deadline that has already passed.

**Consequence.** The user sees either "this deadline is infeasible" or "the planner could not place this work." The warning severity and the suggested response differ.

**Current treatment.** RULE-RISK-1 uses blocked, missed, unscheduled, tight, safe, with missed before unscheduled. A negative-slack deliverable is missed even if no finish is projected. This is a proposed precedence, not an explicit requirement.

**Suggested resolution.** Missed when a deadline exists and slack is negative. Use unscheduled for a deliverable with no deadline, or one that cannot be placed within the horizon for a reason other than deadline capacity.

### DEC-7. How should reasons label earlier-deadline and stable-tie selections?

**Question.** The reason set has four values: deadline_protection, dependency_protection, task_priority, and project_priority. The ordering rules also use earlier effective deadline, creation time, and identifier. How should an item be labeled when its decisive criterion is an earlier deadline that is not under pressure, or only a stable tie-breaker?

**Alternatives.**

- Extend the vocabulary with `earlier_deadline` and `stable_order`. This makes every reason truthful and machine-readable.
- Use `deadline_protection` for any deadline-driven choice, including an earlier deadline, and use `task_priority` as a fallback for stable ties. This keeps four values but can misstate the cause.
- Make the reason nullable when no preference decided the order. The human explanation carries the full cause.
- Reuse the four values as broad categories and accept that the explanation, not the reason, is the precise account.

**Consequence.** Agent consumers may use the reason to explain or group work. An untruthful reason undermines the explainability principle. A nullable reason weakens the "every item has a reason" requirement.

**Current treatment.** `deadline_protection` covers tight and missed closures and earlier-effective-deadline decisions. Stable-tie labeling is unresolved. The explanation MUST NOT claim a priority that did not decide the order.

**Suggested resolution.** Add `stable_order` for selections decided only by creation time or identifier, and either add `earlier_deadline` or explicitly define `deadline_protection` to include earlier-deadline decisions. Keep the reason and the explanation consistent.

### DEC-8. Which warnings are critical, warning, or informational?

**Question.** The product defines three severities and says critical warnings appear in Today and the tray. It does not map each warning type to a severity or say which warnings may produce operating-system notifications.

**Alternatives.**

- The suggested mapping in RULE-WARN-1: critical for missed deadlines, blocked deadline paths, and missing working hours; warning for tight deadlines, estimate overruns, and excess planned work; informational for prevented cycles.
- A stricter mapping that makes only missed deadlines critical.
- A broader mapping that makes any deadline risk above safe critical.

**Consequence.** The mapping determines what appears in the tray, what may produce a notification, and how alarmed the user is. If everything is critical, nothing is.

**Current treatment.** RULE-WARN-1 gives a suggested mapping and marks it as a suggestion. The mapping is not an established requirement.

**Suggested resolution.** Use the suggested mapping. Reserve critical for conditions that make a commitment unachievable or prevent a plan from existing. Let the user opt in to notifications for critical warnings and estimate overruns, and for approaching deadlines once DEC-16 defines the trigger, and keep notifications off for informational conditions.

### DEC-9. Does a prevented dependency cycle persist as a warning?

**Question.** When a dependency change is rejected for creating a cycle, does the application record a persistent informational warning, or only show a transient validation message with the cycle path?

**Alternatives.**

- A persistent informational warning so the user can review why the change was refused.
- A transient validation message only; the cycle is never stored, so there is no ongoing condition.
- Both: a transient message now, plus an optional history entry.

**Consequence.** Persistent warnings add history and clutter. A transient message is simpler and matches the fact that no cycle exists in the data.

**Current treatment.** The warning type is listed, but its persistence is unresolved. The rule requires the cycle path to be shown at rejection time.

**Suggested resolution.** Use transient validation feedback with the cycle path. Do not persist a warning for a rejected change unless the product later needs an audit history.

### DEC-10. Can tasks and projects be permanently deleted?

**Question.** Are tasks and projects ever hard-deleted, or are they only cancelled or archived?

**Conflict.** RULE-STATE-6 says tasks and projects are never permanently deleted in v1 and that only time entries can be deleted, with confirmation. P7 requires confirmation for destructive actions, and the agent capability list includes cancel for tasks and status changes for projects but not delete. An earlier draft assumed a rule that named delete for tasks and projects; no current rule does.

**Alternatives.**

- No hard delete for tasks and projects. Cancel tasks and archive projects. Time entries can be deleted with confirmation. This preserves history and dependencies.
- Hard delete with confirmation, defined handling for time entries and dependent edges.
- Hard delete only for empty projects or tasks with no history.

**Consequence.** Hard deletion can break dependencies, orphan time entries, and destroy history. No deletion preserves history but can leave clutter.

**Current treatment.** This specification uses no hard delete for tasks and projects. Time entries can be deleted with confirmation. The conflict is unresolved.

**Suggested resolution.** Keep no hard delete in v1. If deletion is required, define exactly what happens to time entries, dependencies, and v2 attachments before adding it.

### DEC-11. How are availability exceptions removed?

**Question.** At most one exception exists per local date. Setting one for a date replaces it. How does the user remove an exception?

**Alternatives.**

- An explicit remove action for the date.
- Setting an exception with an empty replacement, which is currently invalid.
- Deleting the date exception through a general data edit, which is not specified.

**Consequence.** A mistaken day off or holiday can persist indefinitely and suppress capacity the user expects.

**Current treatment.** Setting an exception for a date creates or replaces it. Removal is unresolved.

**Suggested resolution.** Add an explicit remove action for a date exception. Do not overload an empty replacement interval list.

### DEC-12. Can a weekly working interval cross local midnight?

**Question.** Can the user define an interval such as 22:00 to 02:00?

**Alternatives.**

- Disallow it. Each interval MUST end after it starts on the same local day. Night work is expressed as two intervals, but 22:00 to 24:00 and 00:00 to 02:00 cannot both be represented if the end MUST be after the start and 24:00 is not a valid time of day.
- Allow it by interpreting an end at or before the start as the next local day.
- Allow it with an explicit day offset or a separate overnight rule.

**Consequence.** Night-shift users cannot express their availability under the strict rule. The current model stores a start, an end, and a pause budget, all as local times or durations, so crossing midnight needs an explicit convention.

**Current treatment.** v1 interprets each interval within one local day and requires the end to be after the start. The overnight case is unresolved.

**Suggested resolution.** Decide whether night work is in scope. If it is, add an explicit end-day convention. If it is not, state the limitation in the interface rather than leaving users to guess.

### DEC-13. Does pausing a timer change task status?

**Question.** When the user pauses the active timer, does the task remain in progress and current, or return to ready? When the user starts a different task, what happens to the previous task's status?

**Alternatives.**

- Pause stops the timer but keeps the task current and in progress. Starting another task returns the previous task to ready unless it was completed or cancelled. This preserves a single current task and makes the tray's current-task display meaningful.
- Pause returns the task to ready immediately. Starting another task leaves the previous task ready. The application MUST remember which task was last active separately.
- Pause and start are purely timer operations and do not change task status. This would allow several tasks to be in progress, violating the one-in-progress rule unless "in progress" is redefined.

**Consequence.** Task-list status, the current-task indicator, whether the user has to restart a timer, and whether several tasks can appear in progress.

**Current treatment.** This specification uses the first alternative: pause keeps the task current and in progress; starting another task returns the previous task to ready unless it was completed or cancelled.

**Suggested resolution.** Keep the first alternative. It satisfies the one-in-progress rule without inventing a separate "last active" state.

### DEC-14. How does Review obtain planned time for past days and weeks?

**Question.** The Review view compares planned versus logged time for today and the current local calendar week. The itinerary is a forward projection and is not authoritative stored history. How is planned time for days that have already passed obtained?

**Alternatives.**

- **A. Daily plan snapshot.** When the plan is computed, retain a lightweight per-day total of planned minutes, and optionally per-task allocations. Review compares logged time against the snapshot from the start of the day or the latest snapshot for that day. This makes the retrospective meaningful. It adds local history that MUST be preserved and exported.
- **B. Current-plan comparison.** Review compares logged time with the current forward plan. Past days show logged time only, or planned time is the current plan for the remainder of the period. This is simpler but not a true retrospective and can mislead.
- **C. Recompute the historical plan.** Store enough historical state, such as task and availability versions, to recompute what the plan was at a past instant. This is more accurate but significantly more complex.
- **D. Narrow Review.** Compare planned versus logged only for today, and show logged time plus upcoming capacity for the week. This avoids the missing history but drops the weekly planned-versus-logged comparison.

**Consequence.** Without planned history, the weekly comparison can be impossible or misleading. With snapshots or historical recomputation, the application needs storage, retention, and export rules for planned history.

**Current treatment.** Review uses the current itinerary for planned time and time entries plus the active timer for logged time. The source of past planned time is not defined. This is unresolved.

**Suggested resolution.** If the weekly planned-versus-logged comparison is required, use A: a lightweight daily planned-minutes snapshot. It is much simpler than full historical recomputation and is sufficient for a compact review. If the comparison is not essential, use D and describe Review as today's comparison plus upcoming capacity.

### DEC-15. Which ineligible conditions produce blocked risk rather than unscheduled risk?

**Question.** A task can be impossible to schedule for several reasons: it is in the inbox, it has no estimate, its project is paused, completed, or archived, or a prerequisite is cancelled, ineligible, or missing. Which of these should be reported as `blocked`, and which as `unscheduled`?

**Alternatives.**

- **A. Blocked only for dependency-path problems.** If the deliverable's own state makes it ineligible (inbox, no estimate, paused, completed, or archived project), report unscheduled. If a prerequisite is unresolvable, report blocked. This keeps blocked tied to the dependency graph and makes the user's next action clear: fix the dependency or make the prerequisite workable.
- **B. Blocked for any condition that prevents scheduling**, including the deliverable's own ineligibility. This is broader and may be more alarming, but it overloads blocked and makes dependency problems harder to distinguish from setup problems.
- **C. Add a separate `ineligible` risk value** for the deliverable's own state and keep blocked for dependency paths. This is the most precise but extends the five-value risk set.

**Consequence.** The risk shown on a task or project, the warning type (`blocked_deadline_path`), the severity, and the user's next action differ. A user seeing blocked may look for a dependency, while a user seeing unscheduled may look for availability or readiness.

**Current treatment.** This specification uses A: blocked is reserved for dependency-path problems; the deliverable's own ineligibility is unscheduled. This is a proposed taxonomy, not fully explicit in the product definition.

**Suggested resolution.** Use A. If the distinction is too subtle for users, use C and show a clear "not ready" reason instead of overloading blocked.

### DEC-16. Which condition defines an approaching deadline?

**Question.** Notifications offer an approaching-deadlines category, but no warning type or rule defines when a deadline is approaching. What condition should raise it?

**Alternatives.**

- **A. A fixed lead window.** Notify when an effective deadline is within a configured lead time, for example 24 hours, and the deliverable has unfinished work.
- **B. A risk transition.** Notify when a deliverable's risk changes from safe to tight, or when its slack falls below one working day, independently of the 15 percent threshold.
- **C. Implementation-defined.** Leave the trigger to the implementation and document it in the settings surface, keeping the category optional.

**Consequence.** Without a definition the category cannot fire, and an implementation may either invent a trigger or silently drop the category. The choice also affects deduplication and quiet-hours behavior.

**Current treatment.** The category is named in Sections 2.1, 3.1, 4.2, 4.11, 4.28, and RULE-NOTIF-1, but no trigger is defined. The category is not enabled until this decision is resolved.

**Suggested resolution.** Use A with a user-configurable lead time, defaulting to 24 hours, and reuse the warning deduplication rules. Keep the category off when the user has no deadlines.

---

## 10. V2 advisory: daily routines and future service hosting

**Status: v2 only and advisory.** Nothing in this section is a v1 requirement. It is retained so that a future implementation has context, but a v1 implementation is not bound by it.

### 10.1 Daily routine tasks

A daily routine task is a small recurring task anchored to the start or end of a workday. Examples are a morning inbox check, a daily stand-up note, or an end-of-day shutdown checklist. The user wants the routine to appear as real work on each applicable day without creating every occurrence by hand.

A v2 implementation SHOULD support:

- A routine definition with a title, estimate, priority, optional project, active weekdays, and an anchor: start of the first working interval or end of the last working interval.
- One occurrence per applicable working day. The occurrence is a real task-like planning object with its own completion and time entries, while the routine definition remains active.
- Start-of-day routines are scheduled at the first available capacity after the pause budget is reserved. End-of-day routines are scheduled at the end of the last working interval.
- Completion applies to that day's occurrence only. The routine remains active for the next applicable day.
- Editing a routine affects future occurrences, not completed history.
- A missed occurrence is reported without silently marking it complete.

Open questions for v2: whether a missed routine occurrence remains in history, how routines interact with dependencies and project deadlines, whether a routine can be skipped for one day, and how many routines are practical before they crowd out the itinerary. These questions are not v1 requirements.

### 10.2 Server-hosted daemon

A future deployment may run the daemon on a server and let users reach it over a network. That mode would require authentication, multi-user authorization, transport security, remote data ownership, conflict handling, and a clear privacy model. It is not v1. The v1 daemon is local to the user's computer and is not exposed to the network.

---

## Appendix A. Glossary of rule and story identifiers

This appendix is an index only. The normative text is in the referenced sections.

| Prefix | Meaning |
| --- | --- |
| P1 to P11 | Product principles, Section 1.3 |
| SETUP | Setup and preferences stories, Sections 4.1 and 4.2 |
| WORK | Project and task stories, Sections 4.3 to 4.5 |
| DEP | Dependency and blocking stories, Sections 4.6 and 4.7 |
| PLAN | Itinerary and explanation stories, Sections 4.8 and 4.9 |
| RISK | Forecast and warning stories, Sections 4.10 and 4.11 |
| DO | Active work, time, and overrun stories, Sections 4.12 to 4.14 |
| VIEW | Task, project, and review stories, Sections 4.15 to 4.17 |
| AGENT | Agent stories, Sections 4.18 to 4.20 |
| TRACK | Tracker stories (v2 advisory), Sections 4.21 and 4.22 |
| DATA | Data portability story, Section 4.23 |
| APP | Tray and lifecycle stories, Sections 4.24 and 4.25 |
| OVERRIDE | Explicit override story, Section 4.26 |
| SERVICE | Headless service story, Section 4.27 |
| NOTIF | Notification story, Section 4.28 |
| RULE-* | Shared rules, Sections 5 through 8 |
| EX-1 to EX-4 | Worked examples, Section 5.19 |
| DEC-1 to DEC-16 | Unresolved product decisions, Section 9 |
| V2 advisory | Tracker plugins (Section 7), daily routines and server hosting (Section 10) |
