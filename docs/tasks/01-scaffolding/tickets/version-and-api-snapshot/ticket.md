---
type: ticket
subtype: feature
title: Start 0.1.0 and the contract API snapshot
status: stable
workflow_state: ready
blocked_by:
- named-modules
size: m
---

## What to build

The version is 0.1.0 from one source, the annotated tag convention is written where a maintainer reads it, and `check` compares `contract`'s exported surface against a committed snapshot, so a removed or reshaped export fails the build. A fixture proves the failure. No tag is created in this effort: 0.1 closes after the command line effort.

## Acceptance criteria

- [ ] The version is 0.1.0 from one source, and every module reports it.
- [ ] The annotated tag convention is documented, it names who tags and when, and no tag is created here.
- [ ] `check` compares `contract`'s exported surface with a committed snapshot.
- [ ] Removing or reshaping an export fails `check` with a message naming what changed.
- [ ] A committed fixture proves the failure path.
- [ ] The real build stays green.

## Blocked by

- `named-modules`
