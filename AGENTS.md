# Agent conventions

This repository uses the `pi-task-workflow` task workflow. Read `CONTEXT.md`
before naming anything, and treat it as the glossary.

## Where work lives

- `docs/tasks/` is the planning substrate. The `tw_*` tools are the only
  writers of files under `docs/tasks/`. Do not edit them by hand.
- `docs/tasks/<effort>/map.md` is the effort's map. A planning task is
  `docs/tasks/<effort>/tasks/<slug>/task.md`. An implementation ticket is
  `docs/tasks/<effort>/tickets/<slug>/ticket.md`.
- `docs/tasks/out-of-scope/index.md` holds requests that were consciously
  ruled out. Read it before proposing work that was already rejected.
- `docs/adr/` holds decisions that are hard to reverse, surprising, and a real
  trade-off. Most decisions are not ADR-worthy.
- `docs/bugs/` is a static archive and is not scaffolded. A bug enters as an
  effort, not as a file there.

## Vocabulary

Use the canonical long forms, "planning task" and "implementation ticket".
Short forms only where the kind is already unambiguous. When a term is missing
or fuzzy, resolve it in `CONTEXT.md` rather than inventing a synonym.

## Invocation

- User-invoked skills run when the human types `/skill:<name>`: intake,
  wayfinder, to-spec, to-tickets, setup-workflow, and the discipline skills.
- Model-invoked skills are dispatched by the harness: implement-ticket,
  finalize-effort, task-workflow-overview, task-workflow-doctor.
- A skill declares invocation in its own frontmatter. Do not restate a
  skill's body in another skill; point at it.

## Repository shape

Java, with three target shapes that the design must keep separable:

- a core module holding the work logic
- a Spring Boot API module
- a JavaFX client module

Both run modes are requirements: standalone mode with no API, and split mode
with the API between daemon and client. Logic that belongs to the work goes in
the core, not in the API or the client, or standalone mode will need a fork of
it.

The modules are not scaffolded yet.

## Writing

- No em dashes. Use a comma, a colon, a semicolon, or a new sentence.
- Short sentences, active voice.
- Quote a YAML frontmatter value that contains a colon and a space. An
  unquoted `: ` makes the file invalid, and the task tools then skip the file
  silently.

## Gates

- `docs/testing.md` holds the test protocol. The `test-runner` agent reads it.
  Keep the "Run these always" commands current when the build changes.
- `docs/dev-env.md` holds how to start the environment and how a reproduction
  runs. Fill it in before the first ticket needs a reproduction.
