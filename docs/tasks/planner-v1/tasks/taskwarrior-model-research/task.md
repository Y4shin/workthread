---
type: task
subtype: research
title: Taskwarrior's task data model and its semantics
status: stable
workflow_state: ready
blocked_by:
- spec-additions
---

## Precise question

What is Taskwarrior's task data model, field by field, and what are its
semantics?

Answer at least these:

- Every field stored on a task: name, type, nullability, default, and meaning.
  Include the description, project, priority, due, scheduled, wait, until,
  depends, tags, annotations, user-defined attributes, urgency, recur, entry,
  modified, start, end, and status.
- The identifier: what Taskwarrior uses, and why.
- The lifecycle: which statuses exist, which transitions are legal, and what
  each state means for planning.
- The storage format on disk, its atomicity and history behavior, and how
  undone and deleted tasks are represented.
- Dependencies: what `depends` accepts, how it is enforced, and what it does
  not do.
- Annotations and tags: how they differ, and whether either carries structure.
- User-defined attributes: how they are declared, typed, and surfaced.
- Urgency: which inputs feed it, how it is computed, and whether it is stored.
- Recurrence and the wait and scheduled dates: what they mean and what they do
  not mean.
- What the model deliberately does not have: estimates, allocations, per-day
  capacity, time tracking beyond a start time.

## Decision or task it unblocks

`task-model-comparison`, which compares this model with the specification's
task model and decides what to adopt.

## Trusted source boundaries

Primary sources only: the Taskwarrior documentation site, its man pages
including `task(1)`, `taskrc(5)`, and the data format documentation, and the
Taskwarrior source repository. Cite the page or file for every claim. Do not
treat blog posts or third-party summaries as load-bearing evidence.

## Evidence required for completion

A field table with type, meaning, default, and nullability. A lifecycle
description with the legal transitions. A list of what the model lacks. Each
material claim with a citation and a confidence note. Findings captured at
`docs/tasks/planner-v1/tasks/taskwarrior-model-research/findings.md` with the
`type: findings` frontmatter, plus a short write-back to the map.

## Likely dependent tasks

`task-model-comparison`. If the research finds a model feature that changes v1
scope, record it as discovered work instead of widening this task.
