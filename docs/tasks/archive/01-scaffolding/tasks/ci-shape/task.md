---
type: task
subtype: research
title: What the three-platform GitHub Actions pipeline must contain
status: deprecated
workflow_state: done
blocked_by: []
---

## The question

What must a GitHub Actions pipeline contain to run `./gradlew build` and build a JavaFX 21 `jlink` image on ubuntu, macOS, and windows with Gradle 9.9.1 and JDK 21?

Answer with the facts a ticket can turn into a workflow file, not with a tutorial:

- The exact `runs-on` values for the three runners.
- Whether a JDK 21 setup step is needed, and which action supplies it (`actions/setup-java` with `temurin`, or the pre-installed JDK).
- Whether the ubuntu job needs Xvfb and the GTK/X11 libraries. The pipeline builds the image but does not launch it, so state what each step actually requires.
- Whether `gradle/actions/setup-gradle` is current, and what it caches between runs.
- The wrapper validation step, if it is worth having.
- The macOS architecture split: the JavaFX artifacts carry classifiers (`mac`, `mac-aarch64`, `win`, `linux`), and the runner architecture decides which one the build resolves.
- How to upload the three images as artifacts, and whether that is worth the storage.
- Triggers and concurrency: push to `main`, pull request, and cancel-in-progress.

## What it unblocks

The CI ticket that will write `.github/workflows/`, and the build tickets that must keep `./gradlew build` identical locally and in the pipeline. It also settles whether the three-platform promise costs anything beyond a matrix entry.

## Trusted source boundaries

Primary sources only: the `actions/runner-images` repository (installed software per image), the `gradle/actions` and `actions/setup-java` repositories, OpenJFX documentation on platform prerequisites, the badass-jlink-plugin documentation and release notes, and the GitHub Actions workflow syntax reference. The research skill's own source rules apply. Do not use blog posts as the basis of a claim.

## Evidence required for completion

A findings file at `docs/tasks/01-scaffolding/tasks/ci-shape/findings.md` with:

- one row per runner, with the image name, architecture, JDK availability, and any JavaFX prerequisite;
- the step list a workflow file needs, with the source for each non-obvious step;
- a clear statement of what runs where and what does not run in CI (the image launch stays manual);
- confidence per claim and any unresolved question;
- the concrete cost of the three-platform choice, in minutes and moving parts.

## Likely dependent tasks

The CI implementation ticket. No other planning task depends on it.

## Notes

### Result: one matrix job, three legs, no display

A single job with `matrix.os: [ubuntu-24.04, macos-15, windows-2025]` covers the promise. Each leg needs one explicit JDK 21 step, builds with `./gradlew build`, then builds the image with `./gradlew :client:jlink`. Building and linking need no GTK, no X11, and no Xvfb: JavaFX requires GTK3 3.8+ only when the toolkit initialises, and nothing in this pipeline initialises it.

```yaml
name: ci
on:
  push: { branches: [main] }
  pull_request:
  workflow_dispatch:
permissions:
  contents: read
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
jobs:
  build:
    runs-on: ${{ matrix.os }}
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-24.04, macos-15, windows-2025]
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21.0.13'
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew build
      - run: ./gradlew :client:jlink
      - uses: actions/upload-artifact@v7
        with:
          name: image-${{ matrix.os }}
          path: client/build/image/
          retention-days: 7
          if-no-files-found: error
```

### Runner facts

| `runs-on` | Arch | JDK 21 on the image | Default `JAVA_HOME` | JavaFX classifier |
| --- | --- | --- | --- | --- |
| `ubuntu-24.04` | x64 | 21.0.13+9 | 17.0.21+9 | `linux` |
| `macos-15` | arm64 | 21.0.13+9 | 21 (already default) | `mac-aarch64` |
| `macos-15-intel` | x64 | 21.0.12+101.0 | 21 (already default) | `mac` |
| `windows-2025` | x64 | 21.0.13+9 | 17.0.21+9 | `win` |

Sources: the per-image READMEs under `https://github.com/actions/runner-images/tree/main/images`. Consequences:

- The setup step is required: ubuntu and windows default to Java 17, and the macOS Intel image ships 21.0.12 while the others ship 21.0.13. `jlink` links the JDK's own modules, so an unpinned JDK produces a different image per platform.
- Pin the patch (`'21.0.13'`), not `'21'`. `setup-java` prefers a matching JDK from the runner tool cache and uses it as-is, so a floating tag silently follows new releases.
- Do not use `-latest`. `ubuntu-latest` moves to 26.04 in November 2026 and `macos-latest` is already macOS 26 arm64. The pinned labels keep the OS baseline and the JavaFX classifier stable.
- `macos-13` no longer exists. `macos-14` is retired as of 2026-11-02. The current x64 labels are `macos-15-intel` and `macos-26-intel`.
- The installed Gradle is irrelevant and inconsistent (9.9.1 on Ubuntu, 9.7.1 on macOS Intel). Only the committed wrapper counts.

Sources for the actions: `https://github.com/actions/setup-java` (v6, `temurin`, tool-cache behaviour), `https://github.com/gradle/actions` (`setup-gradle` v6, wrapper validation, cache contents), `https://github.com/actions/upload-artifact` (v7, permission loss), `https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax` (concurrency, permissions), `https://docs.github.com/en/billing/concepts/product-billing/github-actions` (storage, minutes).

### Decisions this settles

- **The macOS leg is Apple silicon.** `macos-15` gives `mac-aarch64`. An Intel leg is `macos-15-intel` (`mac`) and would be a fourth leg, not part of the three-platform promise. macOS minutes cost about ten times Linux, so the Intel leg is a separate decision if Intel Macs must be supported.
- **`gradle/actions/setup-gradle` handles the wrapper.** From v4 it validates the wrapper on every run, so a separate `wrapper-validation` step is redundant. It caches Gradle User Home (wrapper distributions, dependencies, build-script caches) and never the project's `build/` output. The default cache provider is "Enhanced Caching", free for public repositories and a free preview for private ones; `cache-provider: basic` is the MIT path. Non-default branches only read the cache.
- **No Xvfb and no GTK packages in the pipeline.** This constrains the build tickets: a unit test that constructs a `Stage` or calls `Platform.startup` will need a display and would drag Xvfb in. Keep the tests toolkit-free; the launch check stays manual.
- **No jpackage prerequisites.** No `fakeroot`, `dpkg-deb`, or WiX; the pipeline builds an image, not an installer.

### Artifact upload caveats

- Uploading a directory zips it and loses permissions: every file becomes `644` and every directory `755`. A downloaded image is not executable. If a runnable artifact is ever needed, `tar` it first and upload with `archive: false`.
- Symlink handling is undocumented and historically buggy (`https://github.com/actions/upload-artifact/issues/93`). Do not treat the artifact as the delivered image.
- Storage counts against the plan: 500 MB free, 1 GB Pro, 2 GB Team, shared with Packages; default retention 90 days. The measured image is 67.6 MB on Linux, so three per run plus zip overhead comes close to the free allowance after one or two runs. `retention-days: 7` keeps it small.
- Whether `permissions: contents: read` covers the artifact upload is not documented; the upload uses the Actions runtime token. If the first run is refused with 403, add the scope it names.

### Known failure modes

- **Windows path length.** The Win32 default is 260 characters, and the plugin has a history of long-path failures (`https://github.com/beryx/badass-jlink-plugin/issues/67`; 4.0.0 shipped Windows `FileNotFoundException` fixes). The workspace root is short (`D:\a\workthread\workthread`), so the risk is low. Keep module and image names short.
- **Silent classifier mismatch.** A hand-rolled JavaFX block keyed on `os.arch` can pick `mac` on an arm64 runner, and the failure appears only at launch, which CI does not do. Use `org.openjfx.javafxplugin`, which derives the classifier from the host, and have the build print the resolved classifier.
- **Caching.** Do not cache `build/`: it would restore stale images. Cache scopes are per branch, and PR entries are effectively single-run.

### Cost

About 4 to 8 minutes per leg for build plus 1 to 2 minutes for the image; roughly 15 to 30 runner minutes per run, 5 to 10 of them macOS. Private repositories pay $0.006 per Linux minute, $0.010 per Windows, and $0.062 per macOS; public repositories use free standard runners. The real cost is the moving parts: three JDK provisioning paths, three cache scopes, three native-library sets, three manual launch checks, and Windows path exposure.

### Unresolved

- Whether the artifact upload needs a permission scope; verify on the first run.
- Whether the hosted Windows image enables long paths (`LongPathsEnabled`); the image README does not say.
- Whether artifacts are metered for public repositories; the billing page does not say.
- Whether `org.beryx.jlink` 4.2.0 is tested on Gradle 9.9.x specifically. Its window is "8.14.4 or newer", and the first CI run is the real proof; the local prototype passed.

Full report: `/tmp/wayfinder-01/ci-shape.md` and the child artifact `research.md` under `~/.pi/agent/sessions/--home-patric-Projects-workthread--/subagent-artifacts/outputs/`.

### Correction: the macOS leg is `macos-26`

The body above was written against the runner-images table as the research read it and names `macos-15` as the macOS leg. That label is still a supported GA image, but it is not the current one. Re-checked against `https://github.com/actions/runner-images/blob/main/README.md`:

| Label | OS | Arch | Note |
| --- | --- | --- | --- |
| `macos-26` (also `macos-latest`) | macOS 26 | arm64 | current GA; JDK 21.0.13 is the default |
| `macos-26-intel` | macOS 26 | x64 | current x64 |
| `macos-15` | macOS 15 | arm64 | older of the two supported images |
| `macos-15-intel` | macOS 15 | x64 | older x64 |
| `macos-14` | macOS 14 | x64 | deprecated, retired 2026-11-02 |

No macOS 27 image exists; `xcode-27` in the same table is an Xcode preview, not an OS. The platform's support policy keeps two GA images and begins deprecating the oldest when a newer one reaches GA, so macOS 15 is next in line.

The macOS leg is therefore `macos-26` (arm64): JDK 21.0.13 is already the default there, the JavaFX classifier is still `mac-aarch64`, and it matches the patch pinned for the other two legs. Everything else in this note stands: the `macos-15-intel` and `macos-26-intel` facts describe the optional Intel leg, which is still not part of the three-platform promise.

`ubuntu-24.04` is unchanged: `ubuntu-latest` still points to 24.04 even though a `ubuntu-26.04` label exists. `windows-2025` is also unchanged, and it is what `windows-latest` points to.

The map's `## Decisions so far` records the corrected label and the reason. Keep the calibration rule: pin exact labels, never `-latest`, and re-read the runner-images table when a label ages.
