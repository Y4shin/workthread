---
type: task
subtype: prototype
title: Can one CLI serve MCP over stdio and stay usable interactively
status: stable
workflow_state: done
blocked_by: []
---

## The one question

Can one command line binary serve MCP over stdio and stay usable
interactively, and what does the MCP Java SDK's stdio transport cost?

## Alternatives worth comparing

- A subcommand that serves MCP and owns standard input and output for its
  whole lifetime, with every other subcommand staying interactive.
- A startup flag that switches the process into MCP mode.
- A separate launcher, so the interactive CLI and the MCP server are different
  entry points.

## Smallest artifact that answers it

A single CLI with two subcommands: one that starts an MCP server over stdio
with one trivially fake tool registered through the SDK's
`StdioServerTransportProvider`, and one that prints a fake task list. Drive the
MCP subcommand with a scripted JSON-RPC handshake over a pipe: initialize,
list tools, call the tool. Then run the interactive subcommand in the same
shell to confirm neither mode interferes with the other.

## Who must react

The human, on whether the subcommand split feels right to use. The
architecture grilling, on the seam between the CLI and MCP.

## What it unblocks

`architecture` (the CLI and MCP module seam and the stdio ownership rule), and
`decisions-agent-interface` (the surface description and the tool registration
shape).

## Where the artifact lives

Build it under `/tmp/planner-v1-cli-mcp/`, outside the repository. Record the
SDK version, the registration code that worked, the handshake transcript, and
what the two modes cost in glue code in this task's notes, then delete the
directory.

## Notes

### Findings

Full evidence, the registration code, the handshake transcript, and the API
friction: `docs/research/prototype-cli-mcp-stdio.md`.

**Question answered.** Yes: one CLI binary can serve MCP over stdio and stay
usable interactively.

**Chosen direction.** A subcommand that owns standard input and output for its
whole lifetime, `mcp serve`, with every other subcommand staying interactive.
The flag alternative costs two lines and was observed silently swallowing
`tasks list --mcp`: no table, no error, exit 0. The server code is identical in
both shapes, so the choice is about semantics and user safety, not code.

**Dependency.** `io.modelcontextprotocol.sdk:mcp:2.1.0`, the newest
non-milestone; 2.2.0-M2 exists. The `mcp` artifact is an empty aggregator: the
real code is `mcp-core` plus `mcp-json-jackson3`, which pulls Jackson 3
(`tools.jackson.core:jackson-databind`), `reactor-core`, and `slf4j-api`, for
fifteen jars and 6.1 MB.

**The finding that must become a build rule.** Standard output purity held
only because the logging backend was pinned to stderr at warn level. The
prototype proved it is load-bearing: with `logback-classic` on the classpath,
which is the Spring Boot default with a console appender pointed at standard
out and DEBUG level, fourteen log lines interleaved with the three protocol
frames and a line-parsing client dies on the first line. Any module that can
serve MCP must guarantee that nothing writes to standard out in that mode, and
the build should make the stderr configuration the default rather than a
comment.

**Lifecycle surprise.** The SDK needs no glue. The transport's reader thread is
non-daemon, so a plain serve-and-return keeps the JVM alive until standard
input reaches end of file, then exits 0. A hand-written park and a latch
version both worked and were unnecessary.

**API friction to expect.** `Tool.builder()` is deprecated and the schema must
be passed to the factory. The JSON mapper must be supplied explicitly through
`McpJsonDefaults.getMapper()`. `capabilities()` and `serverInfo()` are optional
but default to a generic server name and version. The `logging` capability is
always advertised with no way to disable it. Requests sent before initialize
get silence rather than an error, and pipelined requests followed by an
immediate end of input drop the response tail.

**Consequence for the agent interface.** The tool registration shape is known
and cheap. The risk is not the SDK; it is any logging framework that defaults
to standard out, which is exactly what Spring Boot does.
