---
type: task
subtype: grilling
title: Settle the command line interface's vocabulary, output, and exit codes
status: stable
workflow_state: done
blocked_by: []
---

## Decision to settle

Settle the command line interface as a surface: its command vocabulary, its
output format, its error output, and its exit codes. One convention, written
once, that the seven CLI-stage child efforts follow instead of each inventing
its own.

## Why this exists

The specification never mentions the command line interface. The map records it
only as a first-class v1 surface, as the host of `mcp serve`, and as the first
stage of the pre-v1 order. Nothing states how a command is named, what it
prints, or how a script detects failure. Without this, seven child efforts
would answer those questions separately, and the additive-version rule would be
enforced against seven different vocabularies.

Found during the `child-efforts` grilling.

## Parent decisions

- The command line interface is a first-class v1 surface, shipped beside the
  client and the tray, and it hosts MCP over standard input and output.
- Every version keeps every interface an earlier version shipped, so a command
  that exists in one version keeps working in the next.
- One surface port carries the use cases of one area, so a command maps to a
  port operation rather than to a bespoke path.
- Identifiers are accepted as the bare integer or the prefixed display form,
  with no fuzzy matching, and failures carry a stable code and the offending
  entity or field.
- Nothing is deleted on request: a task is cancelled and a project archived.
- The engine writes nothing to standard output in `mcp serve` mode.

## Known choices

- **One command per use case.** Capture, list, show, start, pause, complete, log,
  plan, review, export, settings. Explicit verbs, no interactive mode as the only
  path.
- **Noun-verb or verb-noun.** `workthread task add` versus `workthread add task`.
- **Output.** Human-readable tables and sentences by default, with one
  machine-readable mode, and whether that mode is `--json` on every command, a
  global flag, or a separate `--porcelain` style.
- **Exit codes.** A small fixed set: success, usage error, validation refused,
  rule refused, and unavailable service. Or one code with a code carried in the
  output.
- **Interaction.** Whether any command may prompt, and if so whether the
  confirmation override is the only prompt.
- **Discoverability.** `help` per command, a manual page, and shell completion,
  and whether completion is v1.

## Recommended starting answer

Settle a thin convention and no more, because the child efforts need a
consistent shape rather than a finished product.

- Verb first, noun second: `workthread add task`, `workthread list tasks`,
  `workthread show task 12`, `workthread start task 12`, `workthread plan`,
  `workthread review month`. Verb first reads as an action, and the noun stays
  out of the way when it is implied.
- One command per use case, mapped to one surface port operation, so the command
  set stays a mirror of the use cases rather than a second design.
- Human output by default; a single `--json` flag that works on every command
  that returns data; nothing else. Errors go to standard error, always.
- Five exit codes: `0` success, `1` usage error, `2` validation refused, `3`
  rule refused including a confirmation that was unavailable, `4` the service is
  unreachable. The stable failure code from the agent surface also appears in
  the error output, so a script can read either.
- One prompt only: the confirmation override. Everything else takes its input
  from arguments, and a command that needs input it was not given fails with a
  usage error instead of asking.
- Shell completion is v2: it is cosmetic, and it is the part most likely to
  ossify the vocabulary too early.

## Downstream work

- `cli-basics` and every later CLI child, whose commands follow this.
- `mcp-agent-interface`, which mirrors the use cases rather than the commands.
- The architecture document at `docs/tasks/planner-v1/architecture.md`, which
  carries the convention as a section.

## Notes

### The settled convention

The user accepted the convention as written, with one change to the exit codes:
the reserved catch-all moves to `1` so it is not sandwiched in the middle when
later codes are added.

**Shape.** `workthread <area> <action> [arguments]`. Seven areas, one per
surface port, so no area name is invented:

- `task`, on `ForCapturingTasks`;
- `project`, on `ForManagingProjects`;
- `plan`, on `ForPlanningQueries`, carrying today, forecast, review, and
  notices;
- `timer`, on `ForTrackingTime`;
- `override`, on `ForOverridingRules`;
- `capacity`, on `ForDefiningCapacity`, carrying timetable and time off;
- `config`, on `ForConfiguring`, carrying timezone, settings, and export.

Two commands sit outside an area because they are not port operations:
`mcp serve`, which hosts the agent surface and owns standard input and output,
and `confirm <id> <code>`, which completes a pending operation through the
confirmation channel carried by `ForOverridingRules`.

One command maps to one port operation, and the action is an imperative verb.
No command exists only to reshape data the core does not produce.

**Output.** Text by default. One global `--format json` prints exactly one
documented JSON document per invocation, and `--format text` is the same as
omitting it. JSON is never coloured. Text honours `NO_COLOR`. A command that
returns no data ignores the flag silently rather than refusing it. Errors are
always written to standard error, and under `--format json` the error is a JSON
document on standard error carrying the same stable failure code the agent
surface returns, plus the offending entity or field.

**Exit codes.** Six, with the catch-all pinned low so added codes append above
it:

- `0` success;
- `1` reserved for an unknown, internal, or uncategorised error, where the
  caller may still parse the output to react;
- `2` usage error, meaning the invocation itself is wrong;
- `3` validation refused, meaning the input is not well formed;
- `4` rule refused, meaning well-formed input breaks a rule, including a
  confirmation that was unavailable;
- `5` the service is unreachable.

The split that matters is `3` against `4`: malformed input is the caller's
mistake, a rule refusal is the product working as designed.

**Confirmation.** Only a confirmed removal or an override may prompt, and only
when standard input is a terminal. On a terminal the command prints the
two-digit code and reads it back. Without a terminal it prints the pending
operation's identifier and requirement in the chosen format, exits `4`, and
`workthread confirm <id> <code>` completes it. This is the same pending-operation
handshake the agent surface uses, so there is one confirmation path across
surfaces, not two.

**Identifiers and help.** An identifier is accepted as the bare integer or as
the prefixed display form, with no fuzzy matching, and JSON output carries both.
Every command answers `--help`, `workthread help <area>` lists the commands of
one area, and `--version` prints the version.

**Deferred.** Shell completion is v2. It is cosmetic, and it would freeze the
vocabulary before the capability efforts have filled it in.

### Alternatives rejected

- **Verb first**, `workthread add task`. Rejected because the agent surface is
  already noun-prefixed snake case by decision, and this would name one
  operation in two orders across two surfaces.
- **Flat verbs**, `workthread add`. Rejected because the verbs multiply and
  nothing maps onto the seven surface ports.
- **A `--json` flag of its own.** Rejected in favour of a global
  `--format text|json`, which leaves room for another format without adding a
  flag.
- **Separate porcelain commands.** Rejected because it doubles the command
  surface for one output mode.
- **A distinct exit code per refusal rule.** Rejected because the set would grow
  with every rule and become a second vocabulary to keep in step. The rule lives
  in the stable failure code inside the error output instead.
- **Never prompting.** Rejected for a person at a terminal; the TTY check keeps
  a script from hanging.
- **Prompting whenever input is missing.** Rejected because it makes behaviour
  depend on how the command was started.
- **Five codes without a catch-all.** Rejected by the user, who wants an
  unknown-error code a client can branch on, placed at `1` so later codes append
  above it.

### Constraints this adds

- Every later CLI child names its commands inside these seven areas; a new area
  means a new surface port, not a new prefix.
- Additive versions mean a command, its flags, its JSON shape, and its exit
  codes keep working once shipped.
- The JSON document per invocation is a published shape, so `cli-basics` fixes
  the envelope once, with the stable failure code and the entity fields, and the
  later efforts fill in the payloads.

### Downstream

- `cli-basics` and every later CLI child effort.
- `mcp-agent-interface`, which mirrors the use cases rather than the commands.
- `docs/tasks/planner-v1/architecture.md`, which carries the convention as a
  section for the child efforts to copy.

**Write-back.** The convention is recorded on the map as a constraint naming
this task, with the detail above kept here for the architecture document to
lift.
