---
type: findings
title: "Prototype: Spring Boot and JavaFX in one JVM"
status: stable
source_task: javafx-spring-jvm
---

# Can one JVM host Spring Boot and JavaFX for standalone mode?

Prototype run on 2026-11-12/13 in a Nix devenv shell, Linux x86_64, Xvfb only (no real display).
Everything below is what the runs actually printed, not what the docs say should happen.

**Short answer: yes, all three shapes run in one JVM and all three shut down cleanly.
The cost is not the threading, it is the launcher: the `java` launcher refuses to start a
class that extends `javafx.application.Application` unless JavaFX is a *named module*.
Spring Boot's own main-class (variant A) is untouched by that rule and runs from the plain
classpath, with a warning. That asymmetry is what decides the packaging.**

---

## 1. Environment and versions

| item | version | note |
| --- | --- | --- |
| JDK | OpenJDK 21.0.12.1+1 (NixOS) | `JAVA_HOME` from the shell |
| Gradle | 8.14.4 | `GRADLE_USER_HOME=/tmp/planner-v1-gradle-home` |
| Spring Boot | **4.1.2** | newest *stable* on Maven Central; `4.2.0-RC2` exists but is a release candidate |
| Spring Framework | 7.0.10 | pulled by the Boot BOM |
| JavaFX | **21.0.5**, classifier `linux` | pinned by the brief; jars from Maven Central (`org.openjfx`) |
| build tool deps | `spring-boot-starter` only | no web starter, no Tomcat |

Resolved JavaFX artifacts (from `build/runtime/`):

```
javafx-base-21.0.5-linux.jar      1 module-info.class
javafx-graphics-21.0.5-linux.jar  1 module-info.class, plus all .so natives
                                  (libglass.so, libglassgtk3.so, libprism_es2.so,
                                   libjavafx_font_*.so, javafx-swt.jar)
javafx-controls-21.0.5-linux.jar  1 module-info.class
```

Note: Gradle also pulls `javafx-base-21.0.5.jar` and `javafx-graphics-21.0.5.jar` transitively.
Those are **~300-byte stubs** with no classes and no `module-info.class` — only `META-INF/MANIFEST.MF`.
They matter for the module-path experiment in §7.

## 2. Build

`build.gradle.kts` (the whole thing):

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.2"   // only for the boot-jar experiment in §7
}

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

val springBootVersion = "4.1.2"
val javafxVersion = "21.0.5"
val javafxPlatform = "linux"      // this classifier is what carries the natives

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter")

    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
}
```

Two run modes, both under `xvfb-run -a`, both wrapped in `timeout -k 5 45`:

```bash
# classpath mode (what OpenJFX calls unsupported)
java -cp build/classes/java/main:<javafx-*-linux.jar>:<spring jars> proto.VariantA

# module-path mode
java --module-path <javafx-*-linux.jar> --add-modules javafx.controls \
     -cp build/classes/java/main:<spring jars> proto.VariantA
```

Runner: `/tmp/planner-v1-javafx-spring/run.sh <label> <cp|mp> <main-class> [java args]`.
It writes `runs/<label>.log` (raw) and `runs/<label>.txt` (with rc and wall time).
`rc=124/137` is reported as `HANG (timeout after 45s)`. No run hit that.

Every run shows a stage for `-Dproto.autoQuitMs` ms (default 1200) and then quits, so a
missing window is a failure, not a wait.

## 3. Variant A — Spring Boot owns `main`, then `Platform.startup`

```java
public static void main(String[] args) throws Exception {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(SpringHost.class)
            .web(WebApplicationType.NONE)     // <- must be explicit if a web starter ever creeps in
            .run(args);

    CountDownLatch fxFinished = new CountDownLatch(1);
    Platform.startup(() -> {
        Label label = new Label(context.getBean(GreetingService.class).greeting());
        Stage stage = new Stage();
        stage.setScene(new Scene(label, 420, 120));
        stage.show();
        AutoQuit.schedule("A", () -> { Platform.exit(); fxFinished.countDown(); });
    });

    fxFinished.await();                       // FX thread is non-daemon anyway; this is for ordering
    context.close();                          // explicit close, before the JVM shutdown hook runs
}
```

`SpringHost` + `GreetingService` (the Spring side, identical for A and B):

```java
@SpringBootApplication
public class SpringHost {
    @Bean CommandLineRunner traceRunner(GreetingService greetings) {
        return args -> Trace.log("CommandLineRunner ran: " + greetings.greeting());
    }
}

@Component
public class GreetingService {
    public String greeting() { Trace.log("GreetingService.greeting() called");
                               return "hello from a Spring bean"; }
    @PreDestroy void onDestroy() { Trace.log("GreetingService @PreDestroy (bean destroyed)"); }
}
```

### Trace (module-path run, `runs/A-modulepath.log`, Spring log lines trimmed)

```
[+    0ms] [main] A main() entered
[+  740ms] [main] GreetingService.greeting() called
[+  741ms] [main] CommandLineRunner ran: hello from a Spring bean
[+  742ms] [main] A context refreshed; active=true
[+  797ms] [JavaFX Application Thread] A Platform.startup runnable: toolkit up, building stage
[+  797ms] [main] A main() returned from Platform.startup, waiting for FX to finish
[+  853ms] [JavaFX Application Thread] GreetingService.greeting() called
[+  943ms] [JavaFX Application Thread] A stage shown
--- live threads at A stage shown ---
      Common-Cleaner [daemon, TIMED_WAITING]      Finalizer [daemon, WAITING]
      InvokeLaterDispatcher [daemon, WAITING]     JavaFX Application Thread [non-daemon, RUNNABLE]
      Notification Thread [daemon, RUNNABLE]      Prism Font Disposer [daemon, WAITING]
      QuantumRenderer-0 [daemon, WAITING]         Reference Handler [daemon, RUNNABLE]
      Signal Dispatcher [daemon, RUNNABLE]        main [non-daemon, TIMED_WAITING]
[+ 2155ms] [JavaFX Application Thread] A: autoQuit firing after 1200ms
[+ 2155ms] [JavaFX Application Thread] A calling Platform.exit()
[+ 2158ms] [JavaFX Application Thread] A Platform.exit() returned
[+ 2158ms] [main] A main() saw FX finished
[+ 2160ms] [main] GreetingService @PreDestroy (bean destroyed)
[+ 2161ms] [main] A context.close() returned; active=false
[+ 2161ms] [main] A main() returning
[+ 2162ms] [exit-probe] A: non-daemon threads still alive: 'SpringApplicationShutdownHook'(RUNNABLE), 'DestroyJavaVM'(WAITING), 'exit-probe'(RUNNABLE)
[+ 2162ms] [exit-probe] A: shutdown hook done at +2162ms
```

Start order: `main` → Spring context refresh (CommandLineRunner included, on `main`) →
`Platform.startup` → JavaFX Application Thread created → stage built on the FX thread.
There is **no `JavaFX-Launcher` thread** in variant A (that thread only exists for
`Application.launch`); the FX thread is created directly by `Platform.startup`.
`CommandLineRunner` runs on `main` **before the UI exists**.

## 4. Variant B — the JavaFX Application owns launch, context starts in `init()`

```java
public class VariantB extends Application {
    private ConfigurableApplicationContext context;

    public static void main(String[] args) { launch(args); }

    @Override public void init() {
        context = new SpringApplicationBuilder(SpringHost.class)
                .web(WebApplicationType.NONE).run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override public void start(Stage stage) {
        stage.setScene(new Scene(new Label(context.getBean(GreetingService.class).greeting()), 420, 120));
        stage.show();
        AutoQuit.schedule("B", Platform::exit);
    }

    @Override public void stop() { context.close(); }   // runs on the FX thread
}
```

### Trace (`runs/B-modulepath.log`)

```
[+    0ms] [main] B main() entered, calling Application.launch
[+    8ms] [JavaFX-Launcher] B init() entered
             ... Spring banner + "Starting application" logged on JavaFX-Launcher ...
[+  638ms] [JavaFX-Launcher] GreetingService.greeting() called
[+  638ms] [JavaFX-Launcher] CommandLineRunner ran: hello from a Spring bean
[+  639ms] [JavaFX-Launcher] B init() context refreshed; active=true
[+  646ms] [JavaFX Application Thread] B start() entered
[+  708ms] [JavaFX Application Thread] GreetingService.greeting() called
[+  782ms] [JavaFX Application Thread] B stage shown
--- live threads at B stage shown ---
      JavaFX Application Thread [non-daemon, RUNNABLE]   JavaFX-Launcher [non-daemon, WAITING]
      main [non-daemon, WAITING]                        QuantumRenderer-0 [daemon, WAITING]
      InvokeLaterDispatcher [daemon, WAITING]           Prism Font Disposer [daemon, WAITING]
      Notification Thread [daemon, RUNNABLE]            Common-Cleaner [daemon, TIMED_WAITING]
      Finalizer [daemon, WAITING]                       Reference Handler [daemon, RUNNABLE]
      Signal Dispatcher [daemon, RUNNABLE]
[+ 1995ms] [JavaFX Application Thread] B: autoQuit firing after 1200ms
[+ 1995ms] [JavaFX Application Thread] B calling Platform.exit()
[+ 1996ms] [JavaFX Application Thread] B stop() entered; active=true
[+ 1998ms] [JavaFX Application Thread] GreetingService @PreDestroy (bean destroyed)
[+ 1999ms] [JavaFX Application Thread] B stop() context closed; active=false
[+ 2001ms] [main] B main() returned from launch
[+ 2002ms] [exit-probe] B: shutdown hook running; contextActive=false
[+ 2002ms] [exit-probe] B: shutdown hook done at +2002ms
```

Start order: `main` → `Application.launch` → `JavaFX-Launcher` thread → `init()` (context
refresh + CommandLineRunner, all on `JavaFX-Launcher`) → `JavaFX Application Thread` →
`start()`. ctx.close() happens in `stop()`, on the FX thread, after `Platform.exit()`.

**Observed detail that matters:** because `SpringApplication.run` is invoked from `init()` and
not from a `main` method, Spring cannot deduce the main application class. The log identity
degrades from `proto.VariantA : Starting VariantA` to
`o.s.boot.SpringApplication : Starting application`. Cosmetic here; it also means Boot's
"deduced main class" (used by e.g. `spring-boot:run`, some test slices, `@SpringBootTest`
lookup) is not what you expect.

## 5. Variant C — no Spring in the process

```java
public class VariantC extends Application {
    private final PlainGreetingService greetings = new PlainGreetingService(); // hand-made

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        stage.setScene(new Scene(new Label(greetings.greeting()), 420, 120));
        stage.show();
        AutoQuit.schedule("C", Platform::exit);
    }
}
```

### Trace (`runs/C-modulepath.log`)

```
[+    0ms] [main] C main() entered, calling Application.launch
[+   17ms] [JavaFX Application Thread] C start() entered
[+   78ms] [JavaFX Application Thread] PlainGreetingService.greeting() called
[+  164ms] [JavaFX Application Thread] C stage shown
--- live threads at stage shown ---
      JavaFX Application Thread [non-daemon, RUNNABLE]   JavaFX-Launcher [non-daemon, WAITING]
      main [non-daemon, WAITING]                        QuantumRenderer-0 [daemon, WAITING]
      InvokeLaterDispatcher [daemon, WAITING]           Prism Font Disposer [daemon, WAITING]
      Notification Thread [daemon, RUNNABLE]            Common-Cleaner [daemon, TIMED_WAITING]
      Finalizer [daemon, WAITING]                       Reference Handler [daemon, RUNNABLE]
      Signal Dispatcher [daemon, RUNNABLE]
[+ 1383ms] [JavaFX Application Thread] C calling Platform.exit()
[+ 1388ms] [main] C main() returned from launch
[+ 1389ms] [exit-probe] C: non-daemon threads still alive: 'exit-probe'(RUNNABLE), 'DestroyJavaVM'(WAITING)
```

No Spring, no thread to reconcile: `main` sits in `Application.launch` the whole time.

## 6. Shutdown results

"Quit latency" = FX thread calls `Platform.exit()` → JVM shutdown hook finished, all from the
in-process trace. "Wall" = whole `xvfb-run -a java …` invocation (xvfb-run + JVM launch adds
~60 ms; a bare `java -version` is 29 ms here, so compare variants to each other, not to zero).

| run | mode | result | in-process total | quit latency | context closed? | process exit | non-daemon left |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A | classpath | clean exit, rc 0 | 2127 ms | 7 ms (2120→2127) | yes, `ctx.close()` on `main`, active=false | yes | none |
| A | module path | clean exit, rc 0 | 2162 ms | 7 ms (2155→2162) | yes, `ctx.close()` on `main` | yes | none |
| A, no explicit close | classpath | clean exit, rc 0 | 2167 ms | 7 ms | yes, but by `SpringApplicationShutdownHook` *after* our hook | yes | none |
| A, no explicit close | module path | clean exit, rc 0 | 2174 ms | 7 ms | yes, same, `@PreDestroy` at +2175 ms | yes | none |
| B | module path | clean exit, rc 0 | 2002 ms | 7 ms (1995→2002) | yes, in `stop()` on FX thread | yes | none |
| B | classpath | **rc 1 in 95 ms** | — | — | never started | yes (immediately) | — |
| C | module path | clean exit, rc 0 | 1389 ms | 6 ms | n/a | yes | none |
| C | classpath | **rc 1 in 85 ms** | — | — | never started | yes | — |
| C + launcher wrapper | classpath | clean exit, rc 0 | 1532 ms | 5 ms | n/a | yes | none |

At shutdown the only non-daemon threads still alive were `DestroyJavaVM`, our `exit-probe`
hook thread, and sometimes `SpringApplicationShutdownHook` — i.e. framework threads, never a
leftover FX thread. `Platform.exit()` really does tear down `JavaFX Application Thread`,
`JavaFX-Launcher`, `QuantumRenderer-0` and `InvokeLaterDispatcher`.

Observations worth keeping:

* **The Spring context does not close itself on `Platform.exit()`.** With no explicit close it
  is closed by Boot's own JVM shutdown hook, at JVM-exit time, concurrently with yours.
  In `runs/A-classpath-noClose.log` the probe caught `SpringApplicationShutdownHook` in state
  `RUNNABLE` in one run and `TERMINATED` in another — hook ordering is not deterministic.
  If you need "context closed *before* the process is gone" or "UI gone, daemon still serving",
  you must close it yourself in a defined place.
* **Startup cost:** Spring costs ~600–750 ms before the window is up (A: stage at ~940 ms,
  B: stage at ~780 ms, C: stage at ~165 ms). Same JVM, same FX toolkit — the delta is Spring.
* `main` in variant A is parked on a latch while the UI is up. That is not required for
  liveness: `JavaFX Application Thread` is **non-daemon**, so the JVM stays alive after `main`
  returns. The latch is only there to make the "FX finished → close context → return" order
  testable.

## 7. Module path vs classpath — what the rule actually forces

OpenJFX documents classpath loading as unsupported. What that means in practice, measured:

| main class shape | JavaFX on classpath | JavaFX on module path (`--add-modules javafx.controls`) |
| --- | --- | --- |
| `proto.VariantA` (plain `main`, uses `Platform.startup`) | **works**, rc 0, with a warning | works, **no warning** |
| `proto.VariantB` (`extends Application`, has `main`) | **hard fail, rc 1**: `Error: JavaFX runtime components are missing, and are required to run this application` | works |
| `proto.VariantC` (`extends Application`, has `main`) | **hard fail, rc 1**, same message | works |
| `proto.VariantCLauncher` (plain `main`, calls `Application.launch(VariantC.class, …)`) | **works**, rc 0, with a warning | works |

Both failure cases die in ~90 ms **before any of our code runs** — not a single trace line was
printed. That is the `java` launcher inspecting the main class, not JavaFX.

The warning, in both logging flavours we had:

```
# under Spring Boot (logback picks up the JUL record through jul-to-slf4j):
WARN 470086 --- [main] javafx : Unsupported JavaFX configuration: classes were loaded from 'unnamed module @1a052a00'

# variant C, no Spring on the classpath (java.util.logging):
Nov 13, 2026 12:40:50 AM com.sun.javafx.application.PlatformImpl startup
WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module @4ba661fa'
```

Consequences, in order of how much they cost:

1. **The module path is only mandatory for a main class that extends `Application`.**
   Variant A never trips the rule; variants B and C do, and their classpath failure is total.
2. **On the classpath the UI still works when you get past the launcher.** Variant A renders a
   `Label`, which means `modena.css` and the default skin were loaded from the *classpath* jar
   fine, and the GTK natives were extracted from `javafx-graphics-21.0.5-linux.jar` on the
   classpath fine. So "unsupported" is a real warning with real teeth (the launcher error,
   no support), not an immediate crash in the runtime.
3. **The launcher check is trivially bypassed, which is the actual hazard.** Wrap the launch in
   a non-`Application` main (`VariantCLauncher`) and classpath mode runs — with the warning.
   Ship a Spring Boot fat jar and the same thing happens: the `java` launcher sees
   `org.springframework.boot.loader.launch.JarLauncher`, never `VariantB`, so the check never
   fires. Demonstrated:

   | boot jar (`java -jar`) | JavaFX source | result |
   | --- | --- | --- |
   | `variantA-boot.jar` (mainClass `proto.VariantA`) | nested in the jar's `BOOT-INF/lib` | clean exit, rc 0, ~2.3 s, **same unsupported-configuration warning** |
   | `variantB-boot.jar` (mainClass `proto.VariantB`, an `Application`) | nested in the jar | clean exit, rc 0, ~2.3 s, **JUL `WARNING: Unsupported JavaFX configuration`**, `init()` on `JavaFX-Launcher` at +138 ms |

   So a Spring Boot jar layout makes the "classpath" invalid configuration the *default*
   configuration. Nobody notices until a native/CSS/media path breaks or a support request
   gets rejected.
4. **Module-path mode has its own traps, all observable here:**
   * `--add-modules javafx.controls` is not optional. Without it:
     `Error: Unable to initialize main class proto.VariantA` /
     `Caused by: java.lang.NoClassDefFoundError: javafx/scene/Parent`.
   * The module path must contain the **`-linux` (platform-classified) jars**, not the stubs.
     With only `javafx-base-21.0.5.jar` + `javafx-graphics-21.0.5.jar` (the empty stubs Gradle
     also downloads):
     `Error occurred during initialization of boot layer` /
     `java.lang.module.FindException: Module javafx.controls not found`.
   * Pointing `--module-path` at the *whole* dependency directory (the 5 javafx jars — 2 of them
     empty stubs — plus the 21 Spring/logging jars) still worked, and module resolution picked
     the real jars:
     ```
     root javafx.controls file:.../javafx-controls-21.0.5-linux.jar
     javafx.controls requires javafx.graphics file:.../javafx-graphics-21.0.5-linux.jar
     javafx.controls requires javafx.base     file:.../javafx-base-21.0.5-linux.jar
     ```
     (the resolution output above shows the `-linux` jars winning over the class-less stubs, which
     would otherwise be automatic modules of the same name).
     Working is not the same as supported — Spring's jars become automatic modules in that
     setup, which is a separate unsupported configuration. Keep the module path to JavaFX only:
     FX on the module path, application + Spring on the classpath, app in the unnamed module
     (the unnamed module reads all named modules, so this is legal).
5. **The `main` of the process decides the fate of the runtime.** Whichever class the OS
   launcher sees as main determines whether JavaFX must be a named module. That is a packaging
   constraint, not a code constraint: pick one owner for `main` and build the packaging around it.

## 8. Minimal glue that worked (variant A, the recommended shape)

No framework integration, no "JavaFX-Spring" bridge class, no `Application` subclass. The
whole seam is 12 lines in a plain `main`:

```java
public static void main(String[] args) throws Exception {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(SpringHost.class)
            .web(WebApplicationType.NONE)
            .run(args);

    CountDownLatch fxFinished = new CountDownLatch(1);
    Platform.startup(() -> {
        Label label = new Label(context.getBean(GreetingService.class).greeting());
        Stage stage = new Stage();
        stage.setScene(new Scene(label, 420, 120));
        stage.show();
        AutoQuit.schedule("A", () -> { Platform.exit(); fxFinished.countDown(); });  // replace with a real Quit action
    });

    fxFinished.await();    // only needed if you want "close context, then return from main"
    context.close();
}
```

Rules the runs proved:

* `Platform.startup` must be called **once**, and it returns immediately — it does not block and
  does not tell you when FX dies. A `CountDownLatch` released from the FX thread (or a
  `Platform.addListener`-style teardown) is the minimum glue needed for an ordered shutdown.
* Bean access from the FX thread (`context.getBean(...)`) is fine; no `Platform.runLater`
  bouncing needed for reads, and no bean in this prototype needed `@Lazy`/`@DependsOn` on FX.
* `context.close()` must be called by you if you care when it happens.
* `spring.main.web-application-type=none` (or `.web(WebApplicationType.NONE)`) is worth setting
  explicitly the moment any dependency could pull a web starter in — otherwise `SpringApplication`
  tries to start a server before the UI is up.

## 9. Hazards actually observed (not predicted)

1. **Classpath JavaFX does not fail; it warns.** The only hard failure is the `java` launcher's
   main-class check (`Error: JavaFX runtime components are missing…`). Everything after that
   works on the classpath in this prototype — including CSS/skin loading and native extraction.
   Anyone who tests only variant A will conclude classpath is fine. It is not supported, it just
   works here.
2. **Boot jar packaging silently moves you into the unsupported configuration** for both
   variants and hides the launcher error entirely (§7, item 3). Nothing in the build fails; you get one
   `WARNING` line in `java.util.logging` from `PlatformImpl startup` that is easy to never see.
3. **`ES2 Prism: Error - GLX extension is not supported / GLX version 1.3 or higher is required`**
   appears in every single run on this environment (Xvfb has no GL) and the UI still renders.
   In a headless-adjacent CI this is noise, not a failure — but it is the first line of any log
   and will be mistaken for a bug.
4. **Shutdown-hook ordering is not deterministic.** With no explicit `context.close()`, the same
   build produced `SpringApplicationShutdownHook` `RUNNABLE` in one run and `TERMINATED` in the
   next while our probe hook ran. Anything that must happen before the process dies has to be
   called explicitly, not left to hooks.
5. **Variant B loses the deduced main application class** and logs as `Starting application`
   (§4) — a small but real integration wrinkle that shows up in log identity and Boot tooling.
6. **Variant B puts the whole Spring bootstrap on the `JavaFX-Launcher` thread** (context refresh,
   `CommandLineRunner`, bean initialisation), not on `main`. Anything that assumes
   `Thread.currentThread() == main` during startup (thread-local request/holder patterns,
   "main thread" assertions) breaks quietly in B and not in A.
7. **No run hung.** Every variant that started reached a clean rc 0 exit within ~2.3 s wall,
   with `main` non-daemon and the FX thread non-daemon both going away by themselves. With no
   Spring in the process the whole thing is ~1.6 s wall.

## 10. Recommendation

**Variant A — Spring Boot owns `main`, starts the context, then `Platform.startup`** — is the
shape to keep.

Why, from the runs rather than from taste:

* It is the only shape that runs unmodified from the plain classpath, so it does not depend on
  how the app is packaged or launched.
* It keeps the Spring bootstrap on `main` and the deduced application class intact, so ordinary
  Boot tooling and log identity behave.
* Its shutdown order is explicit and testable: `Platform.exit()` on the FX thread → latch →
  `context.close()` on `main` → return. Measured quit latency 7 ms.
* It needs no `Application` subclass at all, so the module-path rule never applies to the
  process' main class.

Variant C (no Spring in the process) is the cheapest by a wide margin (~165 ms to window vs
~940 ms) and is a legitimate standalone mode if the standalone process only needs the core plus
the JavaFX client — but it costs you the Spring container in that mode by construction.

Variant B is the one to avoid: it buys nothing that A does not have, it forces JavaFX onto the
module path (or a launcher/fat-jar workaround), and it drags Spring startup onto the FX launcher
thread.

For packaging: keep JavaFX on the **module path** (`--module-path` + `--add-modules
javafx.controls`, platform-classified jars, FX jars only on that path) if you can control the
launch command; otherwise accept the classpath warning knowingly, and prefer a plain
`main`-owns-everything shape or a boot jar over an `Application`-subclass main class, because
those are the only two ways the launcher lets you through without the module path.

## 11. Not tested (do not treat as evidence)

* A Spring context that owns non-daemon threads of its own (web starter / Tomcat, a scheduler,
  a message listener). Those would very plausibly keep the JVM alive after `Platform.exit()` and
  turn "clean quit" into a hang. Everything here used `spring-boot-starter` with
  `WebApplicationType.NONE`.
* Closing the window with the X button (implicit exit) instead of `Platform.exit()`. Both should
  land in the same place, but only `Platform.exit()` was exercised.
* `Platform.exit()` from a non-FX thread (a service command, a controller).
* Any platform other than Linux x86_64, and any display other than Xvfb (no GPU pipeline, so no
  Prism ES2/HW path was exercised).
* JDK versions other than 21; JavaFX versions other than 21.0.5 (pinned by the brief).

## 12. Files

```
/tmp/planner-v1-javafx-spring/
  build.gradle.kts                      # boot plugin is only for the §7 boot-jar experiment
  settings.gradle.kts
  run.sh                                # one-shot runner: run.sh <label> <cp|mp> <main> [args]
  src/main/java/proto/
    Trace.java                          # [+ms] [thread,daemon] line + optional thread dump
    ExitProbe.java                      # shutdown hook: non-daemon thread inventory
    AutoQuit.java                       # PauseTransition quit driver (proto.autoQuitMs)
    GreetingService.java  SpringHost.java
    PlainGreetingService.java
    VariantA.java  VariantB.java  VariantC.java  VariantCLauncher.java
  runs/*.log  runs/*.txt                # raw output + rc/wall per run
  build/libs/variantA-boot.jar  variantB-boot.jar
```

Reproduction: `gradle copyRuntime compileJava bootJar bootJarVariantB`, then
`./run.sh <label> <cp|mp> <main-class>`. Note `gradle clean` wipes `build/runtime`, which is the
directory `run.sh` builds its classpath from — re-run `copyRuntime` before running the matrix.

Nothing was deleted.
