# Testing

## Framework

In the repository now:

- JUnit 5 (Jupiter) 5.14.4 and AssertJ 3.27.7 in every module, so each module's
  test task runs a real test from the first commit.
- A Java 21 toolchain per module, so a test runs on the JDK the toolchain
  resolves to regardless of the launcher JDK.

Planned, not yet present in the repository:

- Spring Boot Test (`@SpringBootTest` with `MockMvc`) for the API module.
- TestFX for JavaFX view tests, headless through Monocle where possible.

## Build tool

Gradle 9.9.1 through the committed wrapper `./gradlew`, with Kotlin DSL build
files, one version catalogue in `gradle/libs.versions.toml`, and the
configuration cache on. Always use the wrapper: the version in
`gradle/wrapper/gradle-wrapper.properties` is the one the build supports.

## Test protocol

The task-workflow `test-runner` agent reads this section. "Run these always"
commands run on every suite gate. A "run if asked" command runs only when the
caller passes its number.

Run these always:

- `./gradlew build` -- **the gate.** It compiles everything, runs every test, and
  runs every check attached to `check`: the dependency, descriptor, version, API
  snapshot, pipeline and fixture checks. A tree is green only when this passes.
- `./gradlew test` -- the tests alone, for a fast inner loop. It runs no check, so
  it passes trees that `./gradlew build` refuses. Never report a gate green from
  it.

Every build check the effort adds rides `check` and therefore `build`. A check
that only `./gradlew test` would catch does not exist; if one ever needs adding,
it goes on `check` so this section stays true.

Run if asked (by number):

1. `./gradlew :<module>:test --tests <fully.qualified.TestClass>` -- one test
   class, for a focused gate
2. `./gradlew :<module>:check` -- one module's tests and checks, without the
   other modules

## Mock conventions

- To be filled in as patterns emerge.
- Prefer a hand-written fake over a mocking framework at a module seam. The
  seam between client, core, and API is the one that must stay testable in
  both run modes.
- In the API module, drive the controller through `MockMvc` instead of mocking
  the controller itself.
- In the client module, keep view models free of JavaFX nodes so they run
  without a toolkit.

## Headless runs

The suite is toolkit-free on purpose: no test starts JavaFX, so no test needs a
display. CI holds that line. All three legs run `./gradlew build` with no Xvfb,
no GTK packages and no display step, and `verifyCiWorkflow` refuses any of them
in the committed workflow.

A test that does need the toolkit is the point at which Monocle through TestFX
gets configured here. JavaFX 21 has no headless Glass platform, Monocle has had
no release since February 2024, and Xvfb is what the dev shell already provides
for the manual launch check. Until then, do not gate the suite on UI tests.
