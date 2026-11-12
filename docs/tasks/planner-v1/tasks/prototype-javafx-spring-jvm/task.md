---
type: task
subtype: prototype
title: Can one JVM host Spring Boot and JavaFX for standalone mode
status: stable
workflow_state: ready
blocked_by: []
---

## The one question

Can one JVM host Spring Boot and JavaFX cleanly enough for standalone mode,
and what does JavaFX's module-path rule force on the packaging?

## Alternatives worth comparing

- Spring Boot owns `main`, starts the context, then starts JavaFX through
  `Platform.startup`.
- The JavaFX `Application` owns launch, and the Spring context starts inside
  `init()`.
- No Spring in the standalone process at all: the core plus the JavaFX client,
  with the Spring Boot API only in split mode, which is what the stack
  research concluded the current mode definition already allows.

## Smallest artifact that answers it

A Gradle project with one Spring bean that produces a string, one
`CommandLineRunner`, and one JavaFX window that displays the string. Record
the start order and the thread each step runs on, then quit and record whether
the context closes and whether the process exits without a hang. Then repeat
without Spring, and compare.

## Who must react

The human, on which shape feels right to maintain. The architecture grilling,
on the process model.

## What it unblocks

`architecture` (the standalone process shape and the module boundary), and
`decisions-service-mode` (the lifecycle questions DEC-4 sits inside).

## Where the artifact lives

Build it under `/tmp/planner-v1-javafx-spring/`, outside the repository.
Record the start order, the thread names, the shutdown behavior, and the
minimal glue code that worked in this task's notes, then delete the directory.
