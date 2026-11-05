# Testing

## Framework

Planned, not yet present in the repository:

- JUnit 5 (Jupiter) for unit tests.
- Spring Boot Test (`@SpringBootTest` with `MockMvc`) for the API module.
- TestFX for JavaFX view tests, headless through Monocle where possible.

## Build tool

Provisional: Maven, through the committed wrapper `./mvnw`. The build tool is
not chosen yet. If the project adopts Gradle, replace every command below and
delete this note.

## Test protocol

The task-workflow `test-runner` agent reads this section. "Run these always"
commands run on every suite gate. A "run if asked" command runs only when the
caller passes its number.

Run these always:

- `./mvnw -q test` -- the full test suite
- `./mvnw -q -DskipTests verify` -- compile and run the configured checks

Run if asked (by number):

1. `./mvnw -q -Dtest=<TestClass> test` -- one test class, for a focused gate

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

JavaFX tests need a display. Add the Monocle configuration here when CI exists.
Until then, do not gate the suite on UI tests.
