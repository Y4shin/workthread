---
type: task
subtype: prototype
title: Can one CLI serve MCP over stdio and stay usable interactively
status: stable
workflow_state: ready
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
