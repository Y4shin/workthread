---
type: task
subtype: grilling
title: Settle the pre-v1 build order and the staging rule for each stage
status: deprecated
workflow_state: done
blocked_by:
- spec-additions
---

## Decision to settle

Settle the order in which the pre-v1 versions are built, and the rule that
makes each stage shippable.

The human already stated the order:

1. A command line interface with the basics.
2. The logic and backend grown to the final v1 stage.
3. The MCP interface.
4. A daemon the CLI can talk to, with MCP hosted over stdio on the CLI.
5. The desktop user interface.
6. The tray icon and menu.

What this task decides on top of that:

- What "the basics" means for the CLI stage: which operations the first stage
  must support, and what it may fake or omit.
- What "the final v1 stage" means for the backend: which capabilities must be
  in it before the MCP stage starts.
- Whether the stages are strictly sequential or may overlap, and whether a
  stage may leave an earlier interface behind.
- The staging rule: what every stage must keep working, and what makes a stage
  done. Candidates are "every earlier interface keeps working" and "each stage
  is usable on real work".
- Which stage introduces the Spring Boot API module: with the daemon stage, or
  earlier inside the process.
- How the CLI's two uses are staged: an interactive session and an MCP stdio
  server. A single process cannot give both the terminal's standard streams.
- What "v1" means as an end state, so the release plan can say which release is
  v1.

## Parent decisions

`spec-additions` recorded the human's build order and the rule that every
interface works with the backend in process or through the daemon. The
non-negotiables task fixed the effort boundary.

## Known choices

- A strict staircase where each stage is the next version, or overlapping
  stages inside one version.
- Building the daemon before MCP, which is the human's stated order, or MCP
  first because it can run against the in-process backend.
- Introducing the Spring Boot API only when a second process needs it, or
  early to keep the seam honest from the start.

## Recommended starting answer

Keep the human's order. Make each stage a version that works on real tasks.
Name the CLI stage's scope as capture, list, start and stop, log time, and
complete, against the in-process backend with no daemon. The backend stage
adds the timetable, the session, ordering, deadline forecasts, and warnings.
The MCP stage adds a subcommand that serves MCP over stdio and nothing else.
The daemon stage introduces the Spring Boot API and a client adapter behind the
same core interface. The desktop stage adds the JavaFX client, and the tray
comes last. The staging rule: every earlier interface keeps working at every
later stage. Define v1 as the release in which the desktop client and the tray
exist beside the CLI.

## Downstream work

Feeds `architecture`, which must support the whole sequence early, and
`release-plan`, which names the concrete releases and their feature sets.

## Notes

### Settled: the pre-v1 build order and the staging rule

Answers in the order asked. Q1 and Q3 to Q7 were accepted as recommended;
Q2 came back amended by the human.

**Q1, the staging rule.** Accepted as recommended: a monotone staircase.
Every stage, and every version inside a stage, ends with its own version
number. A version is done when it works on real tasks, not when its checklist
is closed. Every interface an earlier version shipped keeps working at every
later version, inside the same stage or a later one: no version removes a
command or a surface that already works. Stages run in sequence, so a version
is tagged before the next one opens. Rejected: a version number per stage with
earlier interfaces allowed to disappear, and internal milestones with only v1
tagged.

**Q2, the CLI stage, amended by the human.** The human's words: "all the
functionality regarding the core 'product' (tasks, projects, dependencies,
scheduling based around estimated times, dependencies, and projects and so on)
working in CLI before we move on to the next thing like i.e. the daemon or so,
but that may happen over multiple efforts and multiple versions". So the CLI
stage does not end when the basics work. It runs until the core product is
usable from the CLI, and its internal split is a version question, not a stage
question. The first version's scope stands as recommended and was not
contradicted: capture a task by title, list and inspect tasks, start and stop
the timer, log and correct time, complete a task, with a fixed sort of deadline
then priority and no ordering engine, no projects, no dependencies. Projects,
dependencies, estimates, and scheduling arrive in later versions of the same
stage. Rejected as stage boundaries, not as contents: basics plus projects and
dependencies as the whole stage, and the full ordering engine in the first
version. Not asked, already settled elsewhere: SQLite from the first version
(stack-research) and `mcp serve` as a CLI subcommand that owns standard input
and output (prototype-cli-mcp-stdio).

**Q3, the backend before MCP.** Accepted as recommended: everything in
specification section 2.1 that is not an interface, meaning projects,
dependencies, estimates, timetable and session, ordering, forecasts, warnings,
overrides, review projections, time entries, and export. Rejected: holding back
export and review projections, and holding back forecasts and warnings until the
desktop client. The reason is the agent stories: an MCP surface over a backend
without forecasts and warnings would ship a contract that has to change.

**Q4, the Spring Boot API module.** Accepted as recommended: it arrives with
the daemon stage and not earlier. The core stays framework-free, so the CLI
versions carry no Spring dependency, and the build can enforce that rule.
Rejected: Spring in the first version to exercise the seam early, and Spring
only when the desktop client exists.

**Q5, what v1 means.** Accepted as recommended: v1 is the release after the
tray stage, so all stages sit inside the pre-v1 run and the release tagged v1
meets the completion signals in specification section 8.8. Rejected: v1 at the
desktop release with the tray as a post-v1 patch, and v1 at the MCP release.

**Q6, CLI stage versus backend stage.** Accepted as recommended: the first two
stages of the earlier order collapse into one CLI stage made of several
versions. The MCP stage opens only when the core product is complete and usable
from the CLI. Rejected: two named stages that share the CLI surface and differ
only by a capability checklist, and two stages where the second adds a surface
of its own.

**Q7, what splits the CLI stage into versions.** Accepted as recommended: by
capability area, roughly capture, list, timer and complete; then projects,
dependencies, estimates, priority, and deadlines; then timetable, session, and
the ordered today list; then forecasts, warnings, and overrides; then review,
export, and settings. This is recorded as an ordering constraint, not as the
final release list. Rejected: two versions only, and leaving the grouping
entirely to `release-plan`.

**Constraints this adds.** No version inside the CLI stage may remove a command
an earlier version shipped. A stage may span several child efforts, so an
effort is scoped to a capability group, not to "the CLI".

**Downstream consequences.** `release-plan` inherits the collapsed stage list,
the capability axis, and the v1 tag position. `child-efforts` must expect
several efforts inside the CLI stage. `architecture` must support the whole
sequence with a framework-free core, an interactive CLI and an `mcp serve`
subcommand in one binary, and additive versions. `decisions-service-mode` bites
only at the daemon stage.

**Fog and new work.** None for this task. Nothing here reopens the
specification.

**Map write-back.** The build-order constraint and the CLI non-negotiable fact
were amended, and the decided stage list and staging rule were added to
`## Decisions so far`.
