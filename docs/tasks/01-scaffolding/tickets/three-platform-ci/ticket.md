---
type: ticket
subtype: feature
title: Build and package on three platforms in CI
status: stable
workflow_state: ready
blocked_by:
- dependency-checks
- version-and-api-snapshot
- client-image
size: m
---

## What to build

The pipeline builds and packages on Ubuntu, macOS, and Windows. Each leg sets up the pinned JDK, validates the wrapper, restores the Gradle cache, runs the build and the client image task, and uploads the image. Nothing starts a toolkit, and the first run is green.

## Acceptance criteria

- [ ] The pipeline runs on the three pinned runner labels from the architecture.
- [ ] Each leg sets up Temurin 21.0.13 explicitly, validates the wrapper, and restores the Gradle user home.
- [ ] Each leg runs the build and the client image task.
- [ ] Three images are uploaded as artifacts with seven-day retention.
- [ ] No Xvfb, GTK, or jpackage step exists.
- [ ] Superseded runs are cancelled, and permissions are read-only unless the artifact upload requires more.
- [ ] The first run is green on all three legs.

## Blocked by

- `dependency-checks`
- `version-and-api-snapshot`
- `client-image`
