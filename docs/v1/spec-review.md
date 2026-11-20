# Specification review record

Two adversarial reads of `docs/tasks/planner-v1/spec.md`, both by an agent that
wrote none of the material, checking only contradictions and gaps. The first
found thirteen findings. The second verified each fix and found one regression
that the fix introduced. All fourteen are resolved.

## Round one: thirteen findings

Four were errors in prose that contradicted the row it claimed to cover:

- The presence figure was defined as the day's window minus the reserve, where
  the window was itself start plus minutes plus reserve, so presence could never
  differ from the plan. Corrected to stop minus start minus reserve, with the
  planned span kept as a separate quantity.
- The estimate's second door wrote "total minus logged" where the row writes
  logged plus the new remaining as the total.
- Slack was said to include all time off, where the row excludes vacation and
  zero-length flex days.
- The export was described as a bare JSON document, where the rows make it a zip
  holding one documented JSON document whose header carries the export time.

Three were gaps: a subject claimed by a Covers line and stated nowhere.

- The delivery ladder for the confirmation code, which four sections cited and
  none described: interactive notice, then OS notification, then private log
  file, then free stream not carrying the protocol, with `confirmation_unavailable`
  when no channel exists.
- Ending the day stopping every open timer at its stop time.
- The break reserve was both a per-date field and one global setting, with no
  rule saying which supplies the other.

Three were contradictions between the specification and its own cited rows.

- The today list said it never re-plans on the clock while an overrun pushes the
  rest of the day. Resolved as: the order never changes on the clock, the
  running item's claim grows, and the cut line advances.
- The tray was said to show critical notices after the critical level retired.
- `FXTrayIcon` requires `javafx.swing`, not `javafx.graphics`, and its support
  check is `Desktop.isDesktopSupported() && SystemTray.isSupported()`.

Two were settled by choosing:

- Only removal and override could prompt for confirmation, while completing a
  task with incomplete prerequisites also requires it. The specification now
  names three cases sharing one handshake. This is a union of two rows rather
  than a new decision, and it is the place most worth objecting to.
- The identifier display form was left undecided between `TSK-42` and
  `TSK-0001`. The specification adopts the padded form with padding as a
  minimum width that is never significant.

## Round two: verification and one regression

Every finding was confirmed fixed with a quoted deciding sentence. One new
contradiction appeared: after the presence fix, "capacity" named two quantities,
the planned span and the net schedulable minutes. The specification now calls
the span the planned span and reserves capacity for the net figure. A diff
against the previous draft shows that hunk and nothing else.

## Verdict

`publishable`. The checks that support it: every one of the 689 rows routed to a
specification section is covered exactly once, no section cites a row that is
not kept, every citation resolves to source text, and the alternative reads
above found no remaining contradiction.

What the review does not prove: that the specification is a good product
specification, only that it is internally consistent and that it accounts for
the record. That judgement is the operator's.
