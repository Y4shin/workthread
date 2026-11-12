---
type: task
subtype: grilling
title: Add the human's extra requirements on top of the specification
status: stable
workflow_state: ready
blocked_by:
- non-negotiables
---

## Decision to settle

The human wants to add requirements on top of the specification before any
research or decision grilling starts. Settle which additions go in, and where
each one lands.

This task is open-ended on purpose. The human brings the list; the session
records it.

## Parent decisions

None. This task is blocked only by the program's non-negotiables, which are
settled.

## Known choices

- The moved specification at `docs/tasks/planner-v1/initial_spec.md` is the
  frozen reference text. Additions do not edit it. They are recorded as
  decisions on the map, and as input to the architecture document.
- An addition can extend v1 scope, replace a v1 behavior, add a v2 advisory
  item, or be ruled out entirely. Each addition needs one of those
  destinations.
- An addition can also contradict a non-negotiable fact already on the map
  (product name, module shapes, the itinerary-first principle, the v1
  boundary). A contradiction reopens that fact rather than sitting next to it.
- The specification's 16 open decisions are not settled here. This task only
  adds requirements to the mix.

## Recommended starting answer

Go through the human's additions one at a time. For each, record the request
in the human's words, the destination (v1 scope, replacement of existing v1
behavior, v2 advisory, or ruled out), and the specification sections it
touches. Then fold the result into the map: new bullets under
`## Decisions so far`, corrections to `## Non-negotiable facts` where the
addition contradicts one, and new `## Child efforts` candidates.

Where an addition makes one of the five themed decision tasks larger, name it
so that task's body can be corrected instead of a second task being created.

## Downstream work

Every other planning task is blocked by this one. Additions here can widen the
stack research, add questions to a themed grilling, or add a child effort to
`child-efforts`.
