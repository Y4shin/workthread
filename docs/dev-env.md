# Development environment

The repository holds no code yet, so nothing can be started. Fill this file in
when the first module lands. Until then an agent must not attempt a
reproduction against a running application.

## Prerequisites

Planned:

- A JDK, version 21 or newer. Record the exact version here once it is pinned.
- The Maven wrapper, committed together with the first module.

## Starting the application

To be filled in. Two modes must be documented when they exist:

- Standalone mode: one process, no API between daemon and client.
- Split mode: daemon, Spring Boot API, and client as separate processes.

For each mode, state the command, the port if any, and how to reset the local
state.

## Reproduction

Not possible yet. When the first module lands, state here how an agent starts
the application, how it reaches a failing state, and what it must not touch.
