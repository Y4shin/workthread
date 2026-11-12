---
type: task
subtype: grilling
title: Settle the pre-v1 build order and the staging rule for each stage
status: stable
workflow_state: ready
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
