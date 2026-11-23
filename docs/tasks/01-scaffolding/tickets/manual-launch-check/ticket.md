---
type: ticket
subtype: feature
title: Record the manual launch check on each platform
status: stable
workflow_state: done
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

## Implementation notes

Linux is verified from the pipeline's own artifact: `image-ubuntu-24.04` from run 45089591551 opens the window at 1024x768 with one centred label reading `workthread 0.1.0`, recorded in `docs/dev-env.md`. It runs inside an `ubuntu:24.04` container, because this NixOS host cannot dlopen the store GTK from a Temurin-linked runtime (the store glib needs `GLIBC_2.43` and the image's loader resolves `/lib/x86_64-linux-gnu/libc.so.6`).

The operator skipped the macOS and Windows launches for this effort, with the reason recorded: no reachable host for either platform, and the launch fix is cheap to redo later. Those two criteria are therefore unmet, not passed. Two facts found while checking the artifacts and recorded in `docs/dev-env.md`: every image ships `bin/workthread` and `bin/workthread.bat`, and the macOS leg linked JDK `21.0.13.1` while Ubuntu and Windows linked `21.0.13`, which contradicts the claim that all three legs link the pinned patch.
