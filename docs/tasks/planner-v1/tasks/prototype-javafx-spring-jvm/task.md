---
type: task
subtype: prototype
title: Can one JVM host Spring Boot and JavaFX for standalone mode
status: stable
workflow_state: done
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

## Notes

### Findings

Full evidence, the three variants, thread traces, and the shutdown
measurements: `docs/research/prototype-javafx-spring-jvm.md`.

**Question answered.** Yes: one JVM can host Spring Boot and JavaFX, with an
explicit shutdown order. Three variants ran.

| Variant | Result |
| --- | --- |
| A. Spring Boot owns `main`, starts the context, then `Platform.startup` | Runs unmodified from the plain classpath. Quit latency 7 ms, exit code 0, no leftover non-daemon threads. Recommended when Spring is in the process. |
| B. The JavaFX `Application` starts the context inside `init()` | Works, but drags Spring's whole bootstrap onto the `JavaFX-Launcher` thread and loses Spring's deduced main class. |
| C. No Spring in the process, plain JavaFX with a hand-made service | Works and is much cheaper: 165 ms to a window against 940 ms. |

**The finding that changes the plan.** Classpath JavaFX does not fail. It
warns. Everything, including `modena.css` and the GTK natives, worked with the
JavaFX jars on the application classpath. The only hard failure is the `java`
launcher's main-class check, which fires only when the main class extends
`javafx.application.Application`, and both B and C died in about 90 ms with
"JavaFX runtime components are missing" before any code ran. That check is
trivially bypassed by a non-`Application` main, and a Spring Boot fat jar does
it implicitly because `JarLauncher` is the main class. Both fat jars the
prototype built ran, classpath and all, with only a JUL warning. So packaging
a runnable jar makes the documented-unsupported configuration the default
configuration, and the module-path rule from the stack research is a rule the
build must enforce rather than one the runtime enforces.

**Second finding: the Spring context does not close on `Platform.exit()`.**
Without an explicit close it is closed by Spring Boot's shutdown hook, and the
hook ordering varied between runs. The shape that behaves is Variant A with an
explicit order: `Platform.exit()` on the FX thread, then a latch, then
`context.close()` on the main thread.

**Third finding: the module path needs two things people miss.**
`--add-modules javafx.controls`, or the app dies with
`NoClassDefFoundError: javafx/scene/Parent`, and the `-linux` classified
JavaFX jars, because the unclassified stubs fail with
`FindException: Module javafx.controls not found`.

**Recommended direction.** Variant A when Spring is in the process, with the
explicit shutdown order. Variant C is the cheaper default if standalone mode
truly needs no container, which the stack research argues is the right
reading of the standalone definition, and the prototype quantifies what that
buys: 165 ms against 940 ms to a window.

**Consequence for the architecture.** The build must enforce the module path,
because the runtime does not. This is a scaffolding and packaging requirement,
not advice.
