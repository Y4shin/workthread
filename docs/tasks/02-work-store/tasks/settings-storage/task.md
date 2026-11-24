---
type: task
subtype: grilling
title: Settle where the business settings live now that they are not in the database
status: stable
workflow_state: ready
blocked_by: []
---

Settle how the business settings are stored now that they are not in the
database: the file, the port that reaches it, and what happens when the file is
wrong.

### Settled by the operator, and not to be reopened

The business settings do not live in the database. They live in a separate
settings file, and reading and writing that file is abstracted behind a port.
The operator's words: "we are storing settings in databases? That is stupid,
that should not be done."

This supersedes two things recorded earlier and the effort must say so:
- the map's non-negotiable fact that the eleven kinds include settings and that
  `ForStoringWork` carries all eleven, which becomes ten kinds;
- register row `T11-012`, which states in its parenthetical that
  `ForStoringWork` covers "settings, timetable" among the record kinds. The map
  already has the precedent that a register row stating nine kinds is superseded
  rather than edited.
- the settings read and write operations confirmed for `ForStoringWork` in the
  `store-contract` grilling.

Register row `SRC-RULE-DATA-3` is not superseded: it requires that upgrades and
restarts preserve settings, and a file satisfies it as long as the file survives
an upgrade and is never silently replaced by defaults.

### The settings the file carries

The timetable (seven weekdays, each a net minute count), the planned start, the
default break reserve, the four balancing values (permitted shortfall and
surplus, target shortfall and surplus), the notice levels and delivery flags per
notice type, the approach lead window (default twenty-four hours), the
concurrency setting, quiet hours, and the purge age. The configured zone is
arguably a setting too, and this task decides whether it lives in the file.

### The choices to settle

1. The format. TOML, Java properties, JSON, or a line-oriented format of our
   own. Judge each by: whether a human can edit it without reading a
   specification, whether comments survive a rewrite, whether typed values
   (minutes, a clock time, a duration, a boolean) are expressible without a
   parser of our own, and what library it costs in `adapters` through the
   version catalogue.
2. Where the file lives: the data directory beside the database, the platform
   configuration directory, or the runtime directory. The architecture already
   says the business settings live in the daemon's data and are reached only
   through the daemon and its API, and that the process configuration is the
   thing that must be readable before a database exists.
3. The port. One typed settings record read whole and replaced whole, a per-key
   get and set, or a read-only port plus a write that takes the whole record. It
   must be usable as a no-framework value by the core and replaceable by a fake
   in the core's tests.
4. Atomic write. Rewriting in place loses the file on a crash mid-write. Decide
   between a temporary file moved into place, a copy kept beside it, and both.
5. A file that is invalid, partly missing, or from a newer version. Refuse to
   start, start with defaults and raise a notice, or fail the read. Each choice
   says what the human sees and what happens to the file.
6. Defaults for absent keys and the first run: write a complete file at first
   start, write nothing until the first change, or never write and treat absence
   as the default.
7. Whether an external edit is noticed: watched, re-read on every call, or read
   once at startup. The daemon is the owner of the settings, and a surface writes
   them through it, so an edit by hand is the case in question.

### Recommended starting answer

TOML in the data directory, named for the application, with comments preserved
because we write it only through a library that keeps them or we write the whole
file and accept that comments are lost. One port, `ForStoringSettings`, reading
the whole typed settings record and replacing it whole, so a caller cannot see a
half-updated settings value and the fake is one field. A write goes to a
temporary file in the same directory and is moved into place, with the previous
file kept as one copy. A missing file means defaults and a written file at first
start. An invalid or newer file refuses to start with a message naming the file
and the first problem, because starting with silently wrong capacity settings
would make every later number wrong. The zone lives in the file.

### Consequences the answer creates

The map's kinds and the first migration, the `ForStoringWork` operation set, the
specification's settings section, the API's settings resource, the client's
settings view, and the register row that must be superseded rather than edited.

### Out of scope for this task

The surface configuration (a daemon endpoint override and a surface's own
preferences) is not the business settings and is not stored in this file.
