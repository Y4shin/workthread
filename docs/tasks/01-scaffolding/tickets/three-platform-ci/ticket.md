---
type: ticket
subtype: feature
title: Build and package on three platforms in CI
status: stable
workflow_state: done
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

## Implementation notes

The first pipeline run is green on all three legs: pull request 1, run 45089591551, `ubuntu-24.04` in 1m33s, `windows-2025` in 2m10s, `macos-26` in 1m49s. The three artifacts uploaded as `image-ubuntu-24.04` (43 971 538 B), `image-windows-2025` (41 442 989 B), and `image-macos-26` (39 563 002 B), so the read-only permission scope was enough for the upload. GitHub only dispatches a new workflow once it exists on the default branch, which is why the run needed a pull request rather than a manual dispatch.

The pipeline is now also a build gate: `verifyCiWorkflow` and `ForbiddenCiStepRule` read `.github/workflows/ci.yml` as text and refuse a wrong runner label, a `-latest` label, the wrong JDK patch, a missing wrapper setup or image task, a wrong artifact path or retention, a missing concurrency group, and any Xvfb, GTK, or jpackage step. Comments are stripped before the step rules read the file. Twenty-three fixtures cover the two rules. Because the check is textual, a semantically identical reformat fails `check`. Nothing bounds a hung `:client:jlink` on a leg, so the default six-hour limit applies.
