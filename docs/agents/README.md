# Agent configuration

Per-repo settings that the task-workflow skills and agents read. Keep this
folder small: it holds configuration, not documentation.

## Test runner

The `test-runner` agent reads its protocol from `docs/testing.md`. Repository
specific notes about running a single test, or about a test that must stay
outside the gate, belong in that file's protocol section.

## Reviewers

No repository specific review rules yet. A rule belongs here when it is
narrower than the `code-review` skill's standards, for example a required
migration when a persisted format changes.

## Reproduction

No repository specific reproduction rules yet. They belong in
`docs/dev-env.md`, which is the file the bug diagnosis path reads.
