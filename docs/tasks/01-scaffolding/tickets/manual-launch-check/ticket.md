---
type: ticket
subtype: feature
title: Record the manual launch check on each platform
status: stable
workflow_state: ready
blocked_by:
- three-platform-ci
size: s
mode: human
---

## What to build

The image built by the pipeline is launched by hand on Linux, macOS, and Windows, and the result is recorded in the development environment document together with the runtime prerequisites each platform needs.

## Acceptance criteria

- [ ] The Linux image from the pipeline launches and shows the window.
- [ ] The macOS image does the same on macOS.
- [ ] The Windows image does the same on Windows.
- [ ] The results and the runtime prerequisites are recorded in the development environment document.

## Blocked by

- `three-platform-ci`
