---
type: ticket
subtype: feature
title: Ship the empty window as a jlink image
status: stable
workflow_state: ready
blocked_by:
- named-modules
size: m
---

## What to build

`./gradlew :client:jlink` builds the image with the settled options, and its launcher opens the empty window: title `workthread`, 1024x768, one centred label reading `workthread 0.1.0`. The build is warning-free with the configuration cache, the size is measured, and the runtime prerequisites are recorded.

## Acceptance criteria

- [ ] The image task succeeds with `--strip-debug --compress zip-6` and a launcher named `workthread`.
- [ ] The launcher opens the window with the title, size, and version label, verified once locally under a virtual display.
- [ ] The build produces no Gradle warning and the configuration cache is reused.
- [ ] The Linux image size is measured and recorded, together with the GTK3 and X11 runtime prerequisite.
- [ ] `./gradlew build` stays green.

## Blocked by

- `named-modules`
