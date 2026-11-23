---
type: task
subtype: research
title: Choose the desktop UI framework, keeping the work logic in Java
status: deprecated
workflow_state: done
blocked_by: []
---

## Question

Which UI framework ships the desktop client, given that the work logic stays in
Java and the client must be callable from, or co-hosted with, a Java entry
point? Rank the candidates against that rule, report what each one costs in
build and packaging, and name at least one candidate a prototype can exercise.
If no candidate meets the rule, say so plainly: JavaFX then stays.

## Decision it unblocks

`child-efforts`, which cannot settle the client efforts before this. It also
reopens four things this effort had already settled:

- the `client` module's toolkit, since the module graph names a JavaFX client;
- the `jlink` image rule, since the image is built by a JavaFX plugin today;
- the Spring and UI one-JVM shutdown order, measured with JavaFX;
- the tray choice, which is an AWT-backed JavaFX tray today.

The recorded JavaFX decisions stand until this task reports. The answer
supersedes them or confirms them.

## Candidates to compare, at minimum

- Compose Multiplatform, JetBrains, Kotlin, JVM desktop target.
- JavaFX, the current decision.
- SWT and JFace, native widgets.
- Swing, no dependency, dated.
- A local web interface, served by the daemon or by a standalone process, opened
  in the user's browser or in an embedded WebView.
- Anything else the evidence turns up.

## Questions the evidence must answer

1. Entry point and interop. Can a plain Java `main` own an application whose
   window is built in Kotlin, and which direction of the seam needs glue? Name
   the concrete mechanism: a `@JvmStatic` launcher, a facade class, or the known
   Kotlin friction in default arguments, suspend functions, and top-level
   declarations.
2. Build. Does the candidate need a Kotlin toolchain beside the Java one in a
   Gradle build, and can the core and contract modules stay Java-only?
3. Packaging. What produces a runnable desktop image: `jpackage`, `jlink`, or
   the framework's own tooling? What native artifacts ship, and what do image
   size and cold start come to on Linux, macOS, and Windows?
4. Tray. Icon, a popup anchored to it, native actions, and what happens when the
   platform has no tray.
5. Accessibility and keyboard operation (specification 8.5) and cross-platform
   behaviour (8.6). What the toolkit gives and what must be built.
6. The dual-mode rule. The client is a surface, so the same client code must
   reach the core in process and over the remote proxy, and the one-JVM setup
   must shut down in the recorded order.

## Trusted sources

Primary only: JetBrains Compose Multiplatform and Kotlin documentation, OpenJDK,
the JavaFX project, Eclipse SWT, and the `jpackage` and `jlink` documentation.
A secondary source may point at a primary one but may not be the basis of a
claim.

## Evidence required

One findings file with, per candidate: the interop mechanism, the build and
packaging route, the image size where measurable, the tray story, and a
confidence level. Then one recommendation, the runner-up, the risk that would
change the answer, and whether the recommended candidate earns a prototype.

## Likely dependent tasks

- `ui-framework-prototype`, blocked by this task, which builds only if a
  candidate passes.
- `child-efforts`, and `release-plan` behind it.

## Notes

### Result: nothing beats JavaFX on the stated constraint

Findings are at `docs/research/ui-framework.md`, saved verbatim from the
researcher, with each claim carrying its source and a confidence level. Six
candidates were compared against the rule that the work logic stays in Java and
the window must be reachable from a Java entry point: Compose Multiplatform,
JavaFX, SWT with JFace, Swing, a local web interface in four shapes, and
Vaadin Flow.

**The verdict.** JavaFX stays, on JavaFX 21 LTS. It is the only candidate whose
UI is Java, callable from a plain Java `main`, with no second language, no
compiler plugin, and no interop annotations. It has the strongest keyboard story
of the native options and a maintained accessibility API. Its jmods make `jlink`
and `jpackage` straightforward, and its licence is the JDK's own.

**What Compose Multiplatform actually costs.** The seam is thinner than feared
and lopsided in the project's favour. Kotlin calls Java for free, so the
intended direction, a Kotlin UI calling the Java surface ports, needs no glue.
Java calls the window through one Kotlin `@JvmStatic` facade, about ten lines.
The costs are structural, not stylistic:

- `@Composable` code cannot be compiled by `javac` at all, because the Compose
  runtime depends on parameters the Kotlin compiler generates. Every line of
  client UI would therefore be Kotlin. The `client` module would need the Kotlin
  plugin plus the Compose Compiler plugin, version-locked to the Kotlin plugin.
- Linux screen-reader support is documented as not supported, and Linux is one
  of the three target platforms. Windows needs Java Access Bridge turned on and
  the `jdk.accessibility` module in the image.
- The `Tray()` composable gives an icon, a native menu, a tooltip, and
  notifications, but no anchored popup, so the workthread popup would still be a
  window positioned by hand from mouse coordinates. Its tray is AWT underneath
  anyway, so it inherits the same Linux weakness JavaFX has.
- Every other module stays `java-library`. Only `client` changes language.

**The finding that cuts against the recommendation.** JavaFX does not deliver a
first-class Linux client either. Its tray is AWT, which on GNOME and Wayland
often reports `isSupported() == false`, so the degradation rule already decided
carries the real weight. Staying on Java 21 also pins the toolkit: JavaFX 23
requires JDK 21, JavaFX 25 requires JDK 23, and JavaFX 27 is out of reach, so the
usable line is 21 LTS or 23.0.2.

**Runner-up and the flip condition.** Compose Multiplatform is the runner-up,
the only candidate that could displace JavaFX. It would win if the human accepts
Kotlin in the client and a measured spike shows its tray and a hand-positioned
popup behave acceptably on GNOME, KDE, macOS, and Windows. A future Compose
release that documents Linux accessibility and a tray anchor rectangle would
remove both functional gaps at once.

**No prototype is earned to choose a framework.** A prototype cannot make the
UI stop being Java. The researcher names a different spike, inside the client
effort rather than before it: package a Java `main` with `javafx.web` and
`javafx.media` excluded, measure image size and cold start on the three
platforms, and test the tray with an anchored popup on four desktop
environments. Image size and cold start for every candidate are published by
nobody, so they must be measured on this project. That spike belongs to the tray
and client efforts, where the map already routes the real desktop test.

**What this hands to the human.** One question, not a framework comparison:
is "as much as possible in Java" a hard constraint or a preference? If it is
hard, JavaFX 21 LTS is settled and JavaFX stays. If it is a preference, the
human can still choose Compose, and the six-point Compose prototype already
charted as `ui-framework-prototype` is what decides it.

**Map write-back.** The recommendation, the Java 21 pin, and the runner-up are
recorded in `## Decisions so far`.
