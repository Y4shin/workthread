---
type: task
subtype: grilling
title: Settle whether Java stays a hard constraint and which toolkit ships the client
status: stable
workflow_state: done
blocked_by:
- ui-framework-research
---

## Decision to settle

Two answers, in this order:

1. Is "as much as possible in Java" a hard constraint, or a preference that
   yields to a better UI toolkit?
2. Given that answer, which toolkit ships the client: JavaFX 21 LTS as the
   research recommends, or Compose Multiplatform, chosen only from a running
   prototype?

The answer closes the toolkit question for this effort. Everything downstream
that says JavaFX, `jlink`, `Platform.exit()`, or `FXTrayIcon` either stands or
is superseded here.

## Parent decisions

`ui-framework-research` holds the evidence and the recommendation, at
`docs/research/ui-framework.md`. Nothing here reopens the work logic staying in
Java, the core staying framework-free, the surface ports, the dual-mode rule,
or the eight-module graph.

## The evidence this rests on

- Compose's seam is one Kotlin facade that Java calls, about ten lines. Kotlin
  calling the Java surface ports is free.
- `@Composable` code cannot be compiled by `javac`, because the runtime depends
  on parameters the Kotlin compiler generates. The whole client layer would be
  Kotlin, and only the `client` module gains the Kotlin and Compose Compiler
  plugins.
- Compose documents Linux screen-reader support as absent. Linux is a target
  platform.
- Compose's `Tray()` has an icon, a native menu, a tooltip, and notifications,
  but no anchored popup, and it is AWT underneath, so it inherits the same
  Linux weakness as JavaFX.
- Staying on Java 21 caps JavaFX at 21 LTS or 23.0.2.
- Neither toolkit publishes image size or cold start numbers, and no primary
  source covers trays on GNOME, Wayland, or KDE Plasma 6.

## Known choices

- **Hard constraint.** JavaFX 21 LTS ships, the toolkit question closes, and
  the JavaFX-dependent decisions are confirmed rather than provisional.
- **Preference, prototype first.** `ui-framework-prototype` runs the six-point
  Compose prototype, and the human chooses from a running window. The Linux
  accessibility gap must be accepted in writing, or Compose is out.
- **Preference, no prototype.** Compose ships on the written evidence, with
  image size, cold start, and tray behaviour as unmeasured risks carried into
  the client and tray efforts.

## Recommended starting answer

Answer the two questions separately, because they are not the same question and
the research shows why.

On the first, recommend **preference**. The human's own wording puts the hard
part on integration with a Java binary, which Compose satisfies, and says "at
the very least" about keeping as much as possible in Java, which reads as a
fallback rather than a floor.

On the second, recommend **prototype first, not Compose on paper**. The
research's case against Compose is documented, not hypothetical: no Linux
screen-reader support on a target platform. That is the kind of gap a running
window settles better than an argument. If the prototype shows the tray, the
popup, and the keyboard path working, Compose wins on the remaining
differences. If it does not, JavaFX 21 LTS ships and this effort is back on its
original path at the cost of one prototype.

Do not recommend "preference, no prototype". It spends the human's dislike of
JavaFX without buying evidence.

## Downstream work

- `ui-framework-prototype`, which fires only on the "prototype first" answer.
- `child-efforts`, which cannot scope the client efforts before this.
- `release-plan` behind it.
- Amendments to `## Non-negotiable facts` and to the architecture record, if
  the answer is anything other than JavaFX.

## Notes

### Settled: JavaFX ships, and no prototype runs

The human answered both questions in one line: "we go with javafx. In that case
no prototype is needed, as it works natively."

**The decision.** JavaFX ships the desktop client. The client remains a Java
module with no second language, no compiler plugin, and no interop seam. The
recommendation from `ui-framework-research` stands: the JavaFX 21 LTS line,
because Java 21 caps the usable line at 23.0.2 and anything later needs a newer
JDK. The exact patch version is a build detail for the scaffolding effort, not a
decision here.

**What the "hard or preference" question turned out to be.** It no longer
decides anything, and it is recorded as moot rather than answered. The human
chose the toolkit that satisfies "as much as possible in Java" with no
exception, so "as much as possible in Java" stands as the governing rule without
a test of how hard it is. Do not reopen it as a constraint question.

**Alternatives rejected, and why they stay rejected.**

- Compose Multiplatform, chosen on the written evidence without a prototype.
  Rejected because the evidence names a gap that only a running window could
  clear, and the human chose not to spend the prototype.
- Compose Multiplatform, chosen after the six-point prototype. Rejected by the
  same answer: no prototype is needed once JavaFX is accepted.
- SWT, Swing, and a local web interface. Rejected in the research, and the
  human's answer does not revive them. Swing remains the fallback if JavaFX
  ever becomes unavailable on a target platform, because it costs nothing to
  reach from a Java process.

**Consequences, now confirmed rather than provisional.**

- The `client` module is a Java module holding the JavaFX client, the tray, and
  the desktop entry point.
- The client ships as a `jlink` image with JavaFX on the module path, and the
  build enforces that, because the runtime only warns when JavaFX is on the
  classpath.
- The one-JVM shutdown order stands as measured: `Platform.exit()` on the FX
  thread, a latch, then `context.close()` on the main thread.
- The tray stays an AWT-backed `FXTrayIcon` guarded by `SystemTray.isSupported()`,
  degrading to the window when no tray exists.
- The Linux tray weakness and the Java 21 version cap are accepted risks, not
  open questions. The real desktop test stays an acceptance item for the tray
  child effort, and it now carries the Wayland and GNOME question the research
  could not answer from primary sources.

**Downstream.** `child-efforts` unblocks and is the next pass's work.
`ui-framework-prototype` never fires. No amended non-negotiable fact is needed,
because none of the JavaFX facts were removed from the map; they were only
marked provisional, and this answer lifts the mark.
