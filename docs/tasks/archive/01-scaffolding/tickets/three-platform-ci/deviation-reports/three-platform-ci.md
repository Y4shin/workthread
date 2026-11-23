---
type: deviation report
title: Deviation report for three-platform-ci
status: stable
---

## Deviation report: three-platform-ci

### API surface changes

- **Planned:** The architecture's per-ticket contract 7 exports "the pipeline, its
  legs, and the uploaded images. The manual check relies on downloadable images."
  The CI section fixes the rest: one job, a matrix over `ubuntu-24.04`,
  `macos-26` (Apple silicon) and `windows-2025`; `actions/setup-java` with
  `temurin` pinned to `21.0.13`; `gradle/actions/setup-gradle` for the wrapper
  validation and the Gradle user home; `./gradlew build` then
  `./gradlew :client:jlink`; the three images uploaded with seven-day retention;
  triggers on push to main, pull request and manual dispatch; a concurrency group
  that cancels superseded runs; read-only permissions with the artifact upload's
  scope confirmed on the first run; no Xvfb, GTK or jpackage.
- **Actual:** The workflow delivers the planned contract, and I re-verified it on
  this checkout rather than reading the implementation log:
  - `.github/workflows/ci.yml` (64 lines) is one job over
    `[ubuntu-24.04, macos-26, windows-2025]` with `fail-fast: false`,
    `runs-on: ${{ matrix.os }}`, `actions/checkout@v7`,
    `actions/setup-java@v6` (`distribution: temurin`, `java-version: '21.0.13'`),
    `gradle/actions/setup-gradle@v6`, `./gradlew build`, `./gradlew :client:jlink`,
    and `actions/upload-artifact@v7` of `client/build/image/` as
    `image-${{ matrix.os }}` with `retention-days: 7` and
    `if-no-files-found: error`.
  - `permissions: contents: read`; `concurrency.group:
    ${{ github.workflow }}-${{ github.ref }}` with `cancel-in-progress: true`;
    triggers `push: branches: [main]`, `pull_request:`, `workflow_dispatch:`.
    The default branch is `main` (`origin/main` merge ref), so the push trigger
    fires on the repository's real branch.
  - No step installs Xvfb, GTK or jpackage, and no step launches the image.
  - `yq -e '.' .github/workflows/ci.yml` parses. The upstream facts hold as of
    this report: the runner-images table lists `ubuntu-24.04` (x64),
    `macos-26` (arm64, and `macos-latest`), `windows-2025` (x64); all three
    image readmes list JDK `21.0.13`, with Ubuntu and Windows defaulting to
    `17.0.21`, which is exactly why the explicit setup step is load-bearing.
    The action tags `actions/checkout v7`, `actions/setup-java v6`,
    `gradle/actions v6` and `actions/upload-artifact v7` all resolve
    (`git ls-remote --tags`, checked for this report).
  - **Surface added beyond the contract:** a fourth root-build gate family, in
    `build.gradle.kts` (+468 lines, now 1788). New top-level names:
    `CiPipelineRule`, `ForbiddenCiStepRule` (both extend the existing
    `DependencyRule`), `VerifyCiWorkflow` (task type), `CiPipelineFixture`,
    `ForbiddenCiStepFixture`, `CiWorkflowFixtures`, the data lists
    `ciPipelineFixtures` (18 refusals) and `forbiddenCiStepFixtures` (5), and the
    tasks `verifyCiWorkflow` and `verifyCiWorkflowFixtures`, both on `check`.
    23 fixtures in total; `verifyCiWorkflowFixtures` prints
    `23 workflow fixtures held`.
  - The rungates pin what the image actually depends on: the three labels, no
    `-latest` label, the Temurin distribution, the `21.0.13` patch,
    `setup-gradle`, the two commands, the upload path, seven-day retention, the
    per-leg artifact name, `if-no-files-found: error`, the three triggers, the
    cancellation, and read-only permissions.
- **Impact:** No dependent ticket has to renegotiate a name, but two dependents
  inherit consequences they did not have before:
  - Ticket 8 (`manual-launch-check`) downloads the three images. The artifact
    names are now enforced by the build as `image-${{ matrix.os }}`, i.e.
    `image-ubuntu-24.04`, `image-macos-26`, `image-windows-2025`; an upload that
    loses the per-leg name or the seven-day retention fails `build`. Ticket 8's
    instructions should use those exact names.
  - Every later effort's `./gradlew build` now also reads
    `.github/workflows/ci.yml`, so a workflow edit that drops a leg, the JDK step,
    a command, or the upload fails the build locally, with a message naming the
    loss. That is cheap and useful, and it also means the build now depends on a
    file outside Gradle.
  - **Fidelity gap, probed on the real file.** The check reads lines; it does not
    parse YAML (deliberate: no second library route in the root build). That
    makes `build` enforce an undocumented *formatting* convention, not only the
    pipeline's shape. Two probes, each restored afterwards:
    - rewriting the triggers as the semantically identical
      `on: [push, pull_request, workflow_dispatch]` is refused three times
      (`the pipeline is not triggered by a pull request`, `... by a push to
      main`, `... by workflow_dispatch`);
    - `runs-on: ${{matrix.os}}` (no space inside the braces) is refused with
      `the pipeline's job runs on no matrix label; the three labels are three
      hosts only if the job runs on them`.

    So a YAML formatter (prettier, a `yq` round-trip, a bot rewrite) or a human
    tidy-up can turn a green local build red with no semantic change. Related,
    smaller gaps in the same place: matching is per file, not per job, so a
    second job could carry the build steps while the matrix job runs nothing; and
    any line counts as a step, so a step `name:` containing `xvfb` or `gtk` is
    refused while the same words in a comment are permitted (the permitted case
    is a committed fixture). Probed: adding `name: build without xvfb` to the
    real job made `:verifyCiWorkflow` fail with `the pipeline uses xvfb; nothing
    in the pipeline launches a toolkit, so a display is not needed`.
  - **Action majors are not checked.** `actions/setup-java@v6` and
    `gradle/actions/setup-gradle@v6` were specified; `actions/checkout@v7` and
    `actions/upload-artifact@v7` were chosen by this ticket and are pinned
    nowhere but the workflow. The rule matches action names, not majors, so a
    major bump passes `check`. Pinned: the labels, the JDK patch, the retention
    number. That is a reasonable line, but it means "pin what the image and the
    storage cost depend on" is a rule in the report, not in the build.

### Abstraction usage

- Used/was specified: **yes**, with one exception worth naming.
  - Gradle's own model: the checks live in the root build, no build-logic
    project, no third-party rule plugin, `check` as the aggregate gate. Nothing
    is a custom plugin.
  - Fixture convention: two plain data lists, one runner (`CiWorkflowFixtures`,
    extending the existing `FixtureChecks`), two root tasks on `check`. The rule
    classes extend the existing `DependencyRule`, so `refusalOf` and
    `fixtureProblem` are reused rather than reimplemented. This honours "extend
    the two root-build checks", and it keeps the family's shape identical to the
    three that came before, so the fourth family reads as a member rather than a
    parallel route.
  - "The pipeline legs are the platform seam. Each operating system builds on its
    own host; nothing cross-builds." Followed: the matrix runs both commands on
    each host, and no artifact is carried between legs.
  - "Do not reimplement: Xvfb, GTK packages, or a display step in CI." Followed,
    and now enforced.
  - "Pin exact labels and re-read the runner-images table when one ages."
    Followed; the labels were re-read for this report and are current, and the
    `-latest` refusal in the rule is the enforcement.
  - "The development environment document is the record seam." Untouched, and
    correctly so: this ticket adds no image fact and no launch result. The image
    size is unchanged at 67 612 510 bytes.
  - "No new always-run command": `docs/testing.md` is untouched, because both
    new tasks ride `./gradlew build`, which is already "run these always".
  - **Exception.** The architecture's "The build's own gates" section enumerates
    three families and their fixture counts (7, 0, 14). A fourth family with 23
    fixtures now rides `check`, and `architecture.md` was not updated, although
    the same document says an implementation ticket that needs a design change
    "records it here in the same write"; it also says the document changes
    "through the architecture writer only". The two sentences conflict, and this
    ticket took the conservative branch: report, do not edit. Consequence: the
    design record now under-describes `./gradlew build` by one gate family and 23
    fixtures, the same way it under-describes the client exemption recorded in
    `dependency-checks`.
  - The rule's `RUNNER_LABELS` and `PINNED_JDK` are a second copy of the
    architecture's CI paragraph, in the pattern `moduleEdgeRows` set for the edge
    table. A stale copy would enforce a stale pipeline, and nothing compares the
    two. The JDK pin `21.0.13` also shares its digits with the catalogue's JavaFX
    `21.0.13`; they are independent facts (a JDK patch and a JavaFX stack
    version), and nothing ties them, so a future JavaFX bump must not move the CI
    pin.

### Out-of-scope changes

- The fourth gate family described above, including `fail-fast: false` and
  `if-no-files-found: error` in the workflow. The architecture does not ask for
  either; both are the right reading of "a green run means all three" and "a leg
  that uploads nothing must not pass", and both are enforced.
- The 468 added lines of root build. That is the largest single addition of this
  ticket's diff and the only part of it a later effort must read.
- No `timeout-minutes` on the job or on any step. Not asked for anywhere, and it
  means a hung `:client:jlink` sits for GitHub's default six-hour limit. No
  decision owns this.
- No removals. No module build file, no catalogue entry, no test, and no `docs/`
  file changed. In particular `docs/testing.md` and `docs/dev-env.md` are
  untouched, and both are correct to leave alone.
- No architecture edit, for the reason in the previous section.
- Verification I ran for this report, on this checkout, with the tree restored
  afterwards (`git status` shows only the workflow harness's
  `docs/tasks/state.yaml`, nothing staged):
  - `./gradlew --offline build` -> `BUILD SUCCESSFUL`, `Configuration cache
    entry reused`, 67 actionable tasks.
  - `./gradlew --offline verifyCiWorkflow verifyCiWorkflowFixtures` ->
    `23 workflow fixtures held`.
  - Wiring probe: `retention-days: 7` changed to `90` in the real workflow made
    `:verifyCiWorkflow` fail with `the artifact upload keeps no seven days; three
    images per run exhaust the free storage otherwise`. Restored, re-verified
    green. The task therefore reads the committed file, not a copy.
  - The two formatting probes and the step-`name:` probe above, each restored
    and re-verified green.

### Ticket doc update needed?

Yes. Append to `## Implementation notes` (the ticket has no such section yet):

- The workflow is `.github/workflows/ci.yml`: one job, matrix
  `[ubuntu-24.04, macos-26, windows-2025]`, `fail-fast: false`,
  `actions/checkout@v7`, `actions/setup-java@v6` with `temurin`/`21.0.13`,
  `gradle/actions/setup-gradle@v6`, `./gradlew build`, `./gradlew :client:jlink`,
  `actions/upload-artifact@v7` with `name: image-${{ matrix.os }}`,
  `path: client/build/image/`, `retention-days: 7`,
  `if-no-files-found: error`.
- Run policy: `permissions: contents: read`, concurrency group
  `${{ github.workflow }}-${{ github.ref }}` with `cancel-in-progress: true`,
  triggers push to `main`, pull request, `workflow_dispatch`.
- Two build tasks guard it: `verifyCiWorkflow` reads the real file,
  `verifyCiWorkflowFixtures` holds 23 fixtures; both ride `check`, so
  `./gradlew build` validates the pipeline on every leg and no new always-run
  command was added.
- The check is line matching with comments stripped, not YAML parsing. It
  enforces a formatting convention too: flow-style `on:` lists and
  `${{matrix.os}}` without the inner space are refused, as are step `name:`
  lines mentioning `xvfb`, `gtk`, `jpackage`, `fakeroot`, `dpkg-deb` or `wix`.
- Action majors are matched by name, not version; the pinned facts the check
  enforces are the three labels, the Temurin `21.0.13` patch and the seven-day
  retention.
- Not observed here: the first run's green result on the three legs, and whether
  `permissions: contents: read` is enough for `actions/upload-artifact@v7`. Both
  need a push.

### User attention needed?

Yes.

1. **The first run is still unproven.** The ticket's last criterion, "the first
   run is green on all three legs", cannot be observed from this checkout: the
   ticket branch is local and pushing is the orchestrator's call. Local evidence
   is the exact two commands on Linux, a green `./gradlew build` with the
   pipeline check riding it, `yq` parsing, and the upstream label, JDK and
   action-tag checks. If the first run refuses the upload, add the named
   permission scope and relax the check's permission rule in the same commit.
2. **The architecture's gate list is stale by one family.** "The build's own
   gates" names three families and their fixture counts. A fourth (the pipeline
   check, 23 fixtures) now rides `check`. The architecture writer should name it,
   or say why the pipeline is not a build gate. Recording it is also what unblocks
   a later reader from trusting the gate list.
3. **The formatting convention is a decision nobody asked for.** Because the
   check is textual, a semantically identical workflow can fail `build` on all
   three legs. Either accept the convention as the price of not adding a YAML
   parser, or put `actionlint` in the pipeline (the implementation validated the
   file with it once, externally, and did not commit it). Worth deciding before
   the first formatter or bot touches the file.
4. **No timeout on the legs.** Nothing bounds a hung `:client:jlink`; the default
   six-hour limit applies. Small, cheap to fix, and no ticket owns it.
