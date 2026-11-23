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

## Implementation notes

The image is `client/build/image`, the launcher `client/build/image/bin/workthread`, built by `./gradlew :client:jlink` with `--strip-debug --compress zip-6` and main class `io.github.y4shin.workthread.client.WorkthreadApplication`. Measured on Linux x86_64: 67 611 290 bytes (64.5 MiB), matching the architecture's 67.6 MB. The image carries the three project modules, `javafx.base`, `javafx.controls` and `javafx.graphics` 21.0.13, and the plugin's inferred JDK module set.

The manual launch ran under `Xvfb :98 -screen 0 1280x800x24 -dpi 96`; without `-dpi 96` the window measures 1068x801 through the server's DPI scale. Xvfb logs `ES2 Prism: Error - GLX extension is not supported`, JavaFX falls back to software rendering, and no `-Dprism.order=sw` is needed. Runtime prerequisites, recorded in `docs/dev-env.md`: GTK3 and the X11 family, `libGL` and `libXxf86vm.so.1`, and a writable `~/.openjfx/cache` for the first launch. Two open items: `:client:assemble` now also emits `distTar` and `distZip` that nothing consumes, and the window's label holds `0.1.0` as a literal that the single version source does not feed yet.
