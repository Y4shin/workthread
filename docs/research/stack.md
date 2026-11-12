---
type: findings
title: "Java stack for a local-first planner with standalone and split modes"
status: stable
source_task: stack-research
---

# Stack research: Java stack for a local-first planner with standalone and split modes

Type: findings. Evidence base: primary sources only (MCP Java SDK repo and Maven Central
metadata, Spring AI reference, OpenJFX javadoc/module-info/release notes, Spring Boot
reference, Gradle and Maven documentation, SQLite, H2, RocksDB documentation).
Every finding is tagged `[fact]` (source says it), `[inference]` (my reading of the
sources), or `[assumption]` (not evidenced, chosen as a working default).

## Question

Which concrete Java stack satisfies all of the following at once, with evidence?

1. A core module holding the work logic that does not depend on Spring.
2. A Spring Boot API module that carries the same calls between daemon and client in
   split mode.
3. A JavaFX client module.
4. A local MCP server interface for agents, available in both run modes.
5. A test stack that runs without a display where possible.
6. A multi-module build that can produce both run modes from one source tree.

Sub-questions: (1) which MCP server implementation for Java is current, maintained, and
usable both from a plain process and inside Spring Boot; (2) can JavaFX and Spring Boot
share one JVM cleanly; (3) Maven or Gradle; (4) what headless JavaFX test setup exists and
what it costs; (5) which embedded persistence option fits a single-writer local service
with concurrent readers and file locking.

## Sources

Primary sources kept, with the passage relied on.

1. **MCP Java SDK repository (modelcontextprotocol/java-sdk), README** —
   https://github.com/modelcontextprotocol/java-sdk
   Passage: "A set of projects that provide Java SDK integration for the Model Context
   Protocol ... supporting both synchronous and asynchronous communication patterns."
   Badge: "License MIT", "Java Version 17+". Team listed: Christian Tzolov, Dariusz
   Jędrzejczyk, Daniel Garnier-Moiroux. README also states the SDK is validated against
   the MCP conformance suite (server 40/40 passed) and that Spring AI extends it.

2. **MCP Java SDK, `docs/server.md`** — https://github.com/modelcontextprotocol/java-sdk/blob/main/docs/server.md
   Passage: "The core `io.modelcontextprotocol.sdk:mcp` module provides STDIO, SSE, and
   Streamable HTTP server transport implementations without requiring external web
   frameworks." Also: `StdioServerTransportProvider transportProvider = new
   StdioServerTransportProvider(McpJsonDefaults.getMapper());` and server registration via
   `McpServer.sync(transportProvider).serverInfo("my-server","1.0.0").capabilities(...).build()`
   followed by `syncServer.addTool(syncToolSpecification)`.
   Also: "Spring-specific transport implementations (`mcp-spring-webflux`,
   `mcp-spring-webmvc`) are now part of Spring AI 2.0+ (group `org.springframework.ai`) and
   are no longer shipped by this SDK."

3. **MCP Java SDK, CHANGELOG** — https://github.com/modelcontextprotocol/java-sdk/blob/main/CHANGELOG.md
   Passage: release-line table "2.0.x | 2.0.1 (2026-08-19) | 2025-11-25 | Active
   development", "1.1.x ... Security patches only", and an "Unreleased (2.2.0)" section
   describing a stateless server for the 2026-07-28 spec revision. Note: this table is
   already behind Maven Central (see Contradictions).

4. **Maven Central metadata, `io.modelcontextprotocol.sdk:mcp` and `:mcp-bom`** —
   https://repo1.maven.org/maven2/io/modelcontextprotocol/sdk/mcp/maven-metadata.xml
   Passage: `<latest>2.2.0-M2</latest>`, `<release>2.2.0-M2</release>`, version list ends
   `... 2.0.0, 2.0.1, 2.1.0, 2.2.0-M1, 2.2.0-M2`, `<lastUpdated>20261102174816</lastUpdated>`.
   `mcp-bom` lists the same versions, so the BOM tracks the SDK.

5. **MCP Java SDK release v2.1.0** — https://github.com/modelcontextprotocol/java-sdk/releases/tag/v2.1.0
   Passage: highlights include "Tool list filtering", "HTTP client transport rework", and
   the bug fix "STDIO `sendMessage` is now thread-safe."

6. **Spring AI reference, MCP Server Boot Starter** —
   https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html
   Passage: STDIO row: dependency `spring-ai-starter-mcp-server`, property
   `spring.ai.mcp.server.stdio=true`. "STDIO - In process (e.g. server runs inside the host
   application) protocol. Communication is over standard in and standard out." Also: "The
   HTTP-based server transports (SSE, Streamable-HTTP, and Stateless) expose an
   unauthenticated JSON-RPC endpoint by default." And annotation registration:
   `@McpTool(name="add", description="...")` on a `@Component` method, auto-configuration
   scans for annotated beans.

7. **Maven Central metadata, `org.springframework.ai:spring-ai-bom`** —
   https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml
   Passage: `<latest>2.1.0-RC2</latest>`; stable versions stop at `2.0.2`.

8. **OpenJFX `javafx.graphics` module-info (master, 2025 copyright)** —
   https://github.com/openjdk/jfx/blob/master/modules/javafx.graphics/src/main/java/module-info.java
   Passage: "The JavaFX classes must be loaded from a set of named `javafx.*` modules on
   the *module path*. Loading the JavaFX classes from the classpath is not supported. See
   `Platform.startup` for more information."

9. **OpenJFX javadoc, `javafx.application.Application` (JavaFX 21)** —
   https://openjfx.io/javadoc/21/javafx.graphics/javafx/application/Application.html
   Passages: "The JavaFX runtime does the following, in order, whenever an application is
   launched: 1. Starts the JavaFX runtime ... 2. Constructs an instance of the specified
   Application class 3. Calls the `init()` method 4. Calls the `start(Stage)` method
   5. Waits for the application to finish". "The `init` method is called on the launcher
   thread, not on the JavaFX Application Thread." "`launch` ... must not be called more
   than once ... Throws IllegalStateException if this method is called from the JavaFX
   application thread." "If the `Application` subclass is in a named module then that
   class must be accessible to the `javafx.graphics` module."

10. **OpenJFX javadoc, `javafx.application.Platform` (JavaFX 21)** —
    https://openjfx.io/javadoc/21/javafx.graphics/javafx/application/Platform.html
    Passages: `startup(Runnable)`: "This method starts the JavaFX runtime ... Because this
    method starts the JavaFX runtime, there is not yet any JavaFX Application Thread, so it
    is normal that this method is called directly on the main thread of the application."
    "A warning is logged when the JavaFX runtime is started if the JavaFX classes are not
    loaded from the expected named module." `runLater`: "long-running operations should be
    done on a background thread". `exit()`: "the JavaFX launcher will call the Application
    stop method and terminate the JavaFX application thread." `setImplicitExit`: "If this
    attribute is true, the JavaFX runtime will implicitly shutdown when the last window is
    closed."

11. **Spring Boot reference, SpringApplication** —
    https://docs.spring.io/spring-boot/reference/features/spring-application.html
    Passages: "Each `SpringApplication` registers a shutdown hook with the JVM to ensure
    that the `ApplicationContext` closes gracefully on exit." Events in order
    (`ApplicationEnvironmentPreparedEvent` through `ApplicationStartedEvent`,
    `ContextRefreshedEvent`). "Both interfaces [ApplicationRunner, CommandLineRunner] work
    in the same way and offer a single `run` method, which is called just before
    `SpringApplication.run(...)` completes." (Page links to Spring Boot 4.1.2 API docs.)

12. **Gradle, current version endpoint** — https://services.gradle.org/versions/current
    Passage: `{"version":"9.9.1","buildTime":"20261110162215+0000", "current":true,
    "released":true}`.

13. **Gradle, Best Practices for Structuring Builds** —
    https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html
    Passage: "Splitting your build's source into multiple Gradle projects (modules) is
    essential for leveraging Gradle's automatic work avoidance and parallelization
    features. When a source file changes, Gradle only recompiles the affected projects. If
    all your sources reside in a single project, Gradle can't avoid recompilation and won't
    be able to run tasks in parallel."

14. **Gradle Plugin Portal metadata, `org.openjfx.javafxplugin`** —
    https://plugins.gradle.org/m2/org/openjfx/javafxplugin/org.openjfx.javafxplugin.gradle.plugin/maven-metadata.xml
    Passage: `<latest>0.1.0</latest>`, `<lastUpdated>20230904180532</lastUpdated>`.
    Plugin README (https://github.com/openjfx/javafx-gradle-plugin): "BSD-3 license",
    `javafx { modules = ['javafx.controls','javafx.fxml'] }`, per-platform targets
    (`linux`, `win`, `mac`, `mac-aarch64`, ...), `configuration`/`configurations` to control
    scope, and `0.1.0` "introduced ... variant-aware dependency management, and support for
    Gradle's built-in JPMS functionality".

15. **OpenJFX Maven plugin, README and Central metadata** —
    https://github.com/openjfx/javafx-maven-plugin and
    https://repo1.maven.org/maven2/org/openjfx/javafx-maven-plugin/maven-metadata.xml
    Passage: `<latest>0.0.8</latest>`, `<lastUpdated>20211005021249</lastUpdated>`.
    README: `mvn javafx:run`, `mvn javafx:jlink`, "Apache License 2.0", and
    `runtimePathOption` with value `CLASSPATH` requiring "a Launcher class (like this one)
    ... required to run a JavaFX application".

16. **Maven Central metadata, `org.apache.maven:maven-core`** —
    https://repo1.maven.org/maven2/org/apache/maven/maven-core/maven-metadata.xml
    Passage: `<latest>4.0.0-rc-7</latest>`; stable line ends `3.10.0`.

17. **OpenJFX release notes, JavaFX 26** —
    https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-26.md
    Passages: enhancements table lists "JDK-8324941 | POC for Headless platform for JavaFX
    | window-toolkit"; bug-fix table lists "JDK-8364687 | Enable headless with
    `-Dglass.platform=headless`". No match for "Monocle" anywhere in the 26 notes.

18. **OpenJFX release notes, JavaFX 25** —
    https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-25.md
    Passage: no match for "Headless" or "glass.platform" (the headless platform is not in
    25).

19. **TestFX repository** — https://github.com/TestFX/TestFX
    Passages: README: "Headless testing using Monocle"; "If you wish to test in a headless
    environment your must add Monocle as a test dependency:
    `org.testfx:openjfx-monocle:<version>`" with `-Dtestfx.headless=true
    -Dprism.order=sw`. GitHub API `commits?per_page=1`: latest commit
    `ba81f939` "v4.0.18 (#802)", dated 2024-02-11.

20. **TestFX/Monocle repository** — https://github.com/TestFX/Monocle
    Passage: GitHub API `commits?per_page=1`: latest commit `f6b0df63` "Update readme
    (#105)", dated 2024-02-16.

21. **Maven Central metadata, `org.testfx:openjfx-monocle` and `org.testfx:testfx-core`** —
    https://repo1.maven.org/maven2/org/testfx/openjfx-monocle/maven-metadata.xml and
    https://repo1.maven.org/maven2/org/testfx/testfx-core/maven-metadata.xml
    Passage: openjfx-monocle `<latest>21.0.2</latest>`, `<lastUpdated>20240216171703</lastUpdated>`;
    testfx-core `<latest>4.0.18</latest>`, `<lastUpdated>20240211043724</lastUpdated>`.

22. **SQLite, Write-Ahead Logging** — https://www.sqlite.org/wal.html
    Passages: "WAL provides more concurrency as readers do not block writers and a writer
    does not block readers. Reading and writing can proceed concurrently." "since there is
    only one WAL file, there can only be one writer at a time." "all readers must exist on
    the same machine. This is why the write-ahead log implementation will not work on a
    network filesystem." "WAL works best with smaller transactions."

23. **SQLite, PRAGMA reference** — https://www.sqlite.org/pragma.html
    Passages: `PRAGMA busy_timeout = milliseconds` "is an alternative to the
    `sqlite3_busy_timeout()` C-language interface"; maximum 2147483647 ms. `locking_mode`
    EXCLUSIVE: "The application wants to prevent other processes from accessing the
    database file"; "WAL databases can be accessed in EXCLUSIVE mode without the use of
    shared memory."

24. **H2, Features / Connection Modes** — https://www.h2database.com/html/features.html
    Passages: embedded mode: "a database may only be open in one virtual machine (and class
    loader) at any time"; "There is no limit on the number of database open concurrently, or
    on the number of open connections." File locking: "Whenever a database is opened, a lock
    file is created ... The default method is `FILE` and uses a watchdog thread"; methods
    `FILE`, `SOCKET`, `FS` (native `FileChannel.lock`), `NO` ("unsafe"). Multithreading:
    "An application should normally use one connection per thread." Lock timeout: `SET
    LOCK_TIMEOUT`. Files: `<database>.mv.db`, `<database>.lock.db`.

25. **RocksDB FAQ** — https://github.com/facebook/rocksdb/wiki/RocksDB-FAQ
    Passage: "Q: Can I write to RocksDB using multiple processes? A: No. ... If no write goes
    to the database, it can be opened in read-only mode from multiple processes."

26. **sqlite-jdbc repository and license** — https://github.com/xerial/sqlite-jdbc and
    https://raw.githubusercontent.com/xerial/sqlite-jdbc/master/LICENSE
    Passages: LICENSE is "Apache License Version 2.0". README documents versioning ("follows
    the version of the SQLite library that is bundled in the jar, with an extra digit") and
    a shade-plugin transformer note. Central metadata: `<latest>3.54.1.0</latest>`.

## Findings

### Sub-question 1: MCP server implementation for Java

**Answer: use the official MCP Java SDK, `io.modelcontextprotocol.sdk:mcp`, version 2.1.0,
with `mcp-bom` 2.1.0. It works standalone (STDIO transport, no web framework) and inside
Spring Boot (either the SDK directly or the Spring AI starter).**

1. `[fact]` Coordinates, license, Java floor: group `io.modelcontextprotocol.sdk`, artifacts
   `mcp` (aggregate), `mcp-core`, `mcp-json-jackson2`, `mcp-json-jackson3`, `mcp-bom`;
   license MIT; Java 17+. **Sources:** 1, 4. **Support:** direct evidence.
2. `[fact]` Current version: Maven Central's newest release is `2.2.0-M2`, a milestone. The
   newest non-milestone is `2.1.0`. So "current stable" is 2.1.0, not
   `mcp`-metadata-`latest`. **Sources:** 4, 5. **Support:** direct evidence (metadata list)
   plus `[inference]` (milestone suffix = pre-release).
3. `[fact]` Maintenance: active. The SDK repo has a release-line policy, a security policy, a
   conformance suite run against the MCP conformance project (server 40/40), and Spring AI
   extends it rather than replacing it. **Sources:** 1, 3. **Support:** direct evidence.
4. `[fact]` Plain-process use: the core module ships STDIO, SSE, and Streamable HTTP server
   transports "without requiring external web frameworks". Registering a server is
   `McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))`
   `.serverInfo(...).capabilities(ServerCapabilities.builder().tools(true).build()).build()`
   then `addTool(syncToolSpecification)`. **Source:** 2. **Support:** direct evidence.
5. `[fact]` Spring Boot use: the SDK's Spring transports were moved out; Spring AI 2.0+
   provides `spring-ai-starter-mcp-server` with `spring.ai.mcp.server.stdio=true` for
   in-process STDIO, or `spring-ai-starter-mcp-server-webmvc` with
   `spring.ai.mcp.server.protocol=STREAMABLE`, plus `@McpTool` annotation scanning.
   **Sources:** 2, 6. **Support:** direct evidence.
6. `[fact]` Spring AI's current versions: latest stable `2.0.2`, with `2.1.0-RC2` in
   progress. **Source:** 7. **Support:** direct evidence.
7. `[fact]` Security note for split mode: Spring AI's HTTP MCP transports are
   "unauthenticated ... by default" and "any client that can reach the endpoint can list and
   invoke every registered tool". STDIO is "not network-accessible". **Source:** 6.
   **Support:** direct evidence.
8. `[inference]` For workthread, the clean shape is the SDK alone (no Spring AI) for the MCP
   adapter module, because the adapter must exist in both run modes and Spring AI would drag
   Spring into a module that also runs in standalone mode. Spring AI only pays off if MCP
   tools must be discovered as Spring beans.

**Confidence: high** for artifact, version, license, maintenance, and the two registration
paths. **Medium** for the recommendation to skip Spring AI, which is a design judgement.

### Sub-question 2: JavaFX and Spring Boot in one JVM

**Answer: it can be made to work, but not by making the `Application` subclass the Spring
Boot main class and not from a Spring Boot fat jar. The supported shape is: Spring Boot owns
the main thread and the lifecycle, and JavaFX is started explicitly on the module path. For
workthread, standalone mode should not need Spring at all, which removes the problem.**

1. `[fact]` Hard constraint: "The JavaFX classes must be loaded from a set of named
   `javafx.*` modules on the module path. Loading the JavaFX classes from the classpath is
   not supported." Starting the runtime from the classpath logs a warning about not being
   loaded "from the expected named module". **Sources:** 8, 10. **Support:** direct
   evidence. **Consequence `[inference]`:** a Spring Boot executable jar (nested jars,
   `spring-boot-loader`, classpath launch) is not a supported JavaFX host. Packaging must
   keep JavaFX on a real module path (`jlink`/`jpackage` image, or `--module-path` plus
   `--add-modules`).
2. `[fact]` JavaFX lifecycle is its own: the launcher starts the runtime, constructs the
   `Application`, calls `init()` on the launcher thread, `start(Stage)` on the FX thread,
   then waits; `Application.launch` "must not be called more than once" and throws if called
   from the FX thread. `Platform.exit()` makes the launcher call `stop()` and terminate the
   FX thread. **Sources:** 9, 10. **Support:** direct evidence.
3. `[fact]` An escape hatch exists for a non-JavaFX main: `Platform.startup(Runnable)`
   starts the runtime, "it is normal that this method is called directly on the main thread
   of the application", and it throws if the runtime is already running. **Source:** 10.
   **Support:** direct evidence.
4. `[fact]` Spring Boot's lifecycle is a `main`-thread bootstrap plus a JVM shutdown hook
   that closes the `ApplicationContext`; `CommandLineRunner`/`ApplicationRunner` run "just
   before `SpringApplication.run(...)` completes". **Source:** 11. **Support:** direct
   evidence.
5. `[inference]` Joining the two lifecycles is manual work with a fixed set of hazards:
   start FX after the context is refreshed (e.g. from a runner or a
   `ContextRefreshedEvent` listener, not from a bean constructor); close the context from
   `Application.stop()` or rely on the shutdown hook but then accept that `stop()` may not
   run; never touch `Stage`/`Scene` off the FX thread; do blocking core calls on background
   threads and hop back with `Platform.runLater` (the `Platform` javadoc explicitly warns
   that long-running work must not run on the FX thread); and set `implicitExit` false if
   the tray icon must outlive the last window (default `true` closes the runtime when the
   last window closes).
6. `[fact]` In this project the question is narrower than it looks: standalone mode is
   defined as daemon and client in one process *with no API between them*, so Spring Boot is
   not required in that process at all. **Source:** project `CONTEXT.md`. **Support:**
   direct evidence from the repo, not from a vendor doc.

**Confidence: high** that one JVM works with the module-path/`Platform.startup`
arrangement, and high that a Spring Boot fat jar cannot host JavaFX. **Medium** on the
specific glue code, since no vendor document prescribes the exact integration; that part is
inference from the two lifecycle documents.

### Sub-question 3: Maven or Gradle

**Answer: Gradle for this shape. The JavaFX-specific argument is thin in both directions;
the multi-module and CI arguments are not.**

1. `[fact]` Gradle treats multiple projects as the recommended structure: modularising
   "is essential for leveraging Gradle's automatic work avoidance and parallelization
   features", and "when a source file changes, Gradle only recompiles the affected
   projects". **Source:** 13. **Support:** direct evidence.
2. `[fact]` Gradle's JavaFX plugin (`org.openjfx.javafxplugin` 0.1.0, BSD-3) is
   variant-aware, handles per-platform JavaFX artifacts (`linux`, `win`, `mac`,
   `mac-aarch64`, ...), and since 0.1.0 relies on Gradle's JPMS handling instead of rewriting
   the classpath/module path. Released 2023-09-04. **Source:** 14. **Support:** direct
   evidence.
3. `[fact]` Maven's JavaFX plugin (`org.openjfx:javafx-maven-plugin`) is at 0.0.8 with
   Central `lastUpdated` 2021-10-05, and it is the only official route to `javafx:run` and
   `javafx:jlink`. **Source:** 15. **Support:** direct evidence.
4. `[fact]` Maven 4 is still a release candidate (`4.0.0-rc-7`); the stable line is 3.10.0.
   **Source:** 16. **Support:** direct evidence.
5. `[fact]` Gradle's current release is 9.9.1 (built 2026-11-10). **Source:** 12.
   **Support:** direct evidence.
6. `[inference]` Weighing:
   - Multi-module ergonomics: Gradle wins (convention plugins, `include`, per-module
     toolchains, task avoidance). Maven multi-module works but each module repeats plugin
     configuration and there is no build cache.
   - JavaFX packaging: a genuine trade-off. Maven has an *official* `javafx:jlink`, but it is
     five years stale and does not know about modern Gradle-style metadata. Gradle has *no*
     official packaging task; `jlink`/`jpackage` need either an `Exec` task or a third-party
     plugin (`org.beryx.jlink`, 4.1.1, July 2026, per the Gradle Plugin Portal listing). So
     Gradle buys fresher dependency handling and pays with a third-party packaging plugin.
   - CI cost: Gradle's parallel execution plus configuration cache and build cache reduce
     wall time on a cold-ish CI agent; Maven's `-T` gives parallelism but no cache. No
     primary benchmark exists for *this* project shape, so this is a qualitative judgement,
     not a measurement.
7. `[fact]` Contradiction worth knowing: the JavaFX javadoc says classpath loading "is not
   supported", while the OpenJFX Maven plugin documents a `CLASSPATH` `runtimePathOption`
   that works via a separate Launcher class. **Sources:** 8, 15. **Support:** direct
   evidence of both statements. `[inference]` Treat classpath launches as tolerated-but-
   unsupported; do not build the product's run modes on them.

**Confidence: medium-high.** The facts are solid; the weighting between Gradle's cache and
Maven's official jlink is a judgement that a spike could overturn quickly.

### Sub-question 4: Headless JavaFX tests

**Answer: on JavaFX 26+ the runtime has a first-class headless Glass platform, selected with
`-Dglass.platform=headless`, with no extra artifact. On JavaFX 21 (the LTS that pairs with
Java 21) the only in-JVM option is the Monocle artifact from TestFX, which has been
unmaintained since February 2024.**

1. `[fact]` JavaFX 26 release notes list "JDK-8324941 POC for Headless platform for JavaFX"
   and "JDK-8364687 Enable headless with `-Dglass.platform=headless`". JavaFX 25 notes
   contain neither string. **Sources:** 17, 18. **Support:** direct evidence. `[inference]`
   The headless platform ships inside `javafx.graphics`, so no extra dependency and no
   custom Maven repo are needed on 26+.
2. `[fact]` TestFX 4.0.18 is the last release (2024-02-11 last commit); its README still
   instructs adding `org.testfx:openjfx-monocle` and setting `-Dtestfx.headless=true
   -Dprism.order=sw`. **Source:** 19. **Support:** direct evidence.
3. `[fact]` The Monocle artifact's newest version is `21.0.2`, published 2024-02-16, so it
   tracks JavaFX 21 and nothing later. `testfx-core` stops at 4.0.18 from 2024-02-11.
   **Source:** 21. **Support:** direct evidence. `[inference]` Both are effectively frozen.
4. `[inference]` Configuration cost:
   - JavaFX 26 headless: one JVM system property in the test task. Cheapest possible.
   - JavaFX 21 + Monocle: extra test-scoped dependency on a stale artifact, plus
     `-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw`, plus the
     risk that Monocle's internals no longer match the 21.0.x runtime you actually ship.
   - Xvfb on CI: no code change, but a display server and a slower, flakier pipeline.
5. `[inference]` Cheapest overall test strategy: keep the FX surface thin (view models,
   presenters, and the core tested as plain JUnit with no toolkit), and use the headless
   platform only for a small smoke layer that builds a scene and asserts a couple of nodes.
   The FX-thread rule from the `Platform` javadoc makes heavy UI tests expensive regardless
   of headless support.

**Confidence: high** that the headless platform exists in JavaFX 26 and not in 25; **high**
that TestFX/Monocle are unmaintained. **Medium** on whether Monocle still works against
JavaFX 21.0.13 (untested), and **low** on the exact JUnit pairing, which I did not research.

### Sub-question 5: Embedded persistence

**Answer: SQLite through `org.xerial:sqlite-jdbc` in WAL mode, with `busy_timeout` set and a
single writer thread. H2 is the fallback if a pure-Java, single-file-per-database store with
no native binaries matters more than file-level visibility. An embedded key-value store is a
poor fit for the "visible data directory" requirement.**

1. `[fact]` SQLite WAL gives exactly the requested concurrency shape: "readers do not block
   writers and a writer does not block readers", but "since there is only one WAL file, there
   can only be one writer at a time". It also requires all readers on "the same machine" and
   "will not work on a network filesystem". **Source:** 22. **Support:** direct evidence.
2. `[fact]` `PRAGMA busy_timeout = <ms>` is the documented way to make a second connection
   wait instead of failing, and `locking_mode=EXCLUSIVE` lets one process hold the file and
   deny access to others. **Source:** 23. **Support:** direct evidence.
3. `[fact]` sqlite-jdbc is Apache-2.0, versioned after the bundled SQLite (current
   3.54.1.0), and needs a `META-INF/services/java.sql.Driver` shade transformer if the jar is
   ever shaded. **Source:** 26. **Support:** direct evidence. `[inference]` It ships native
   libraries per platform, so cross-platform packaging for a desktop app must include the
   right natives per target (Gradle/Maven classifiers), which is a real packaging cost.
4. `[fact]` H2 embedded is explicitly single-JVM: "a database may only be open in one virtual
   machine (and class loader) at any time", with a `.lock.db` lock file and `FILE_LOCK`
   modes (`FILE` watchdog, `SOCKET`, `FS` = `FileChannel.lock`, `NO` = unsafe). It supports
   many connections within that JVM, advises "one connection per thread", and has
   `SET LOCK_TIMEOUT`. **Source:** 24. **Support:** direct evidence.
5. `[fact]` RocksDB cannot be written from multiple processes ("A: No"); read-only
   multi-process access is possible. **Source:** 25. **Support:** direct evidence.
   `[inference]` A KV store also fails the "visible data directory" requirement in spirit:
   the data is an opaque LSM tree, not inspectable rows, and schema evolution becomes
   application code.
6. `[inference]` For this project the decisive points are: the daemon is the only writer in
   both modes, so "one writer" is trivially satisfiable in every candidate; readers inside
   one JVM are cheap in all three; therefore the choice should be made on inspectability,
   query power, migration tooling, and packaging weight. SQLite wins on inspectability
   (a normal file, `sqlite3` CLI, `VACUUM INTO` for backups) and on concurrency semantics
   that are documented down to the lock level. H2 wins on being pure Java (no natives to
   bundle per platform) and on being slightly simpler to embed in a Spring Boot context.

**Confidence: high** on the quoted locking and concurrency behaviour of all three.
**Medium** on the SQLite pick itself: it is a reasoned trade-off, not a documented
requirement.

## Recommendation

Named stack list.

| Slot | Pick | Why (evidence) |
|---|---|---|
| Java | **21 LTS**, pinned by toolchain with `--release 21` | The task's floor; MCP SDK needs 17+ (source 1). JavaFX 21.0.x is the matching LTS line (source 21 version list). |
| Build tool | **Gradle 9.9.1** with Kotlin DSL and per-module toolchains | Multi-project work avoidance and parallelism (source 13); variant-aware JavaFX plugin (source 14); Gradle 9.9.1 current (source 12). Maven 4 is still an RC (source 16). |
| Module layout | `core`, `mcp-adapter`, `daemon`, `api` (Spring Boot), `apiclient`, `client` (JavaFX + tray), `cli`, `standalone`, `testkit` | Keeps Spring only in `api`/`daemon` and JavaFX only in `client`; `core` and `mcp-adapter` stay framework-free so standalone and split mode share them (sources 2, 8; project `CONTEXT.md`). |
| MCP library | **`io.modelcontextprotocol.sdk:mcp` 2.1.0** via `mcp-bom` 2.1.0, MIT, `StdioServerTransportProvider` for the local agent interface; no Spring AI in `core` | Standalone transports without a web framework (source 2); 2.1.0 is the newest stable (source 4); MIT and active (sources 1, 3, 5). |
| Spring Boot | **4.x line** (Spring Boot 4.1.2 as documented), Spring Web MVC, **no** JavaFX in that JVM | Boot's own lifecycle and shutdown hook (source 11); JavaFX module-path rule makes a shared JVM fragile (source 8). |
| Persistence | **SQLite via `org.xerial:sqlite-jdbc` 3.54.1.0**, WAL, `busy_timeout`, one writer thread, one connection per reader thread | One writer / many readers, documented (sources 22, 23); Apache-2.0 (source 26). H2 held in reserve (source 24). |
| Test libraries | **JUnit 5 + AssertJ** for core; **JavaFX 26 headless (`-Dglass.platform=headless`)** for a thin UI smoke layer; **TestFX 4.0.18 only if unavoidable** | Headless platform is first-class in 26 (source 17) and absent in 25 (source 18); TestFX/Monocle frozen since Feb 2024 (sources 19, 20, 21). |
| Packaging | `jpackage`/`jlink` image produced from the Gradle build (`Exec` task or `org.beryx.jlink` 4.1.1); JavaFX always on the module path | Classpath JavaFX is unsupported (source 8); no official Gradle jlink (portal listing), Maven's official one is stale (source 15). |

Two build-level consequences to record as non-negotiables:

- `core` declares no Spring and no JavaFX dependency; enforce with a Gradle check (for
  example an `ArchUnit`-style rule or a dependency assertion) so standalone mode cannot
  drift into needing Spring.
- Both run modes come from the same modules: `standalone` assembles `core` + `mcp-adapter` +
  `client`; `daemon` assembles `core` + `mcp-adapter` + `api`; `client` in split mode talks
  to `apiclient` only.

## What would change if a pick proves wrong

- **If MCP SDK 2.x breaks between minors** (the repo says the stateless server API "may
  change in minor releases until 3.0"): pin 2.1.0 exactly, keep the tool definitions behind
  an `mcp-adapter` module, and accept staying on 2.1.0 until 3.0. No structural change.
- **If Spring AI turns out to be required** (for example agents need MCP resources discovered
  as beans): add `spring-ai-starter-mcp-server` to `api`/`daemon` only, and keep standalone on
  the raw SDK. The adapter interface is the seam; nothing in `core` moves.
- **If JavaFX-on-classpath turns out to be required** (say, a single fat jar is a hard product
  requirement): the whole packaging story changes, and the honest options are a `jpackage`
  image per platform or a launcher-class shim. This is the highest-cost reversal on the list,
  which is why the module-path rule is treated as a constraint rather than a preference.
- **If Gradle's packaging path proves too thin** (third-party jlink plugin does not produce
  the tray-app image you need): switch the packaging module to Maven with
  `javafx:jlink`, or keep Gradle and drive `jpackage` from an `Exec` task. Module layout is
  build-tool neutral, so this is a build-file change, not a code change.
- **If headless UI tests prove too thin**: upgrade the JavaFX line to 26+ first (cheapest
  fix, buys the built-in headless platform), then consider TestFX, then Xvfb.
- **If SQLite's native binaries make packaging painful** (five platform jars, code signing on
  macOS): switch to H2 embedded, which has the same single-JVM lock model and a documented
  lock-timeout. Cost: SQL/data migration and losing direct file inspectability.
- **If concurrent readers turn out to be cross-process** (a second process reading the data
  directory while the daemon writes): SQLite WAL still allows that on one host (source 22),
  but H2 embedded does not (source 24), and RocksDB does not for writes (source 25). That
  would confirm SQLite rather than overturn it.

## Open questions

1. The minimum JDK for JavaFX 26/27 is not documented on any page I verified. If JavaFX 26
   requires a JDK newer than 21, the "JavaFX 26 for the headless platform" recommendation
   conflicts with "Java 21". **Needs:** the OpenJFX release notes or `README` statement of
   the minimum JDK per release.
2. Whether Monocle still ships inside `javafx.graphics` and still works against JavaFX
   21.0.13. The `javafx.graphics` `module-info` does not export `com.sun.glass.ui.monocle`,
   and I could not list the package directory (GitHub API returned 404 for the path). So
   "Monocle is in the JDK-independent OpenJFX build" remains unverified.
3. Tray icon: not researched. JavaFX has no `SystemTray` API; the usual options are AWT
   `java.awt.SystemTray` (which drags in `java.desktop` and platform quirks) or a
   third-party library. This is a genuine gap that no task currently covers, and it affects
   the `client` module's dependencies.
4. CLI argument parsing: not researched (nothing in the brief asked for it, but the surface
   exists). Candidate would be picocli; unverified here.
5. Spring Boot version pin: the reference documentation I read renders as Spring Boot 4.1.2,
   but I did not verify the exact release line to pin, nor Spring Boot 4's baseline JDK.
6. No measured CI cost comparison between Maven and Gradle for this module shape. The
   judgement is qualitative.
7. No spike has confirmed that a JavaFX 21 client and a Spring Boot 4 daemon can share the
   exact same DTO jar without module-path/classpath conflicts.

## Contradictions

1. **MCP SDK version: repo CHANGELOG vs Maven Central.** `CHANGELOG.md` on the default branch
   lists "2.0.x | 2.0.1 | Active development" as the current line and describes 2.2.0 as
   unreleased, while Maven Central's metadata already contains `2.1.0`, `2.2.0-M1`, and
   `2.2.0-M2`. **Sources:** 3 vs 4. Resolution used: Maven Central metadata wins for
   published versions; the in-repo changelog is stale.
2. **"Latest" is a milestone.** Central metadata marks `2.2.0-M2` as both `<latest>` and
   `<release>`, even though it is a milestone. Anyone reading `latest` naively will pin a
   pre-release. **Source:** 4. Resolution: pin 2.1.0.
3. **JavaFX classpath: javadoc vs OpenJFX tooling.** The `javafx.graphics` and `Application`
   documentation say classpath loading "is not supported", while the OpenJFX Maven plugin
   documents a `CLASSPATH` `runtimePathOption` that works with a launcher class.
   **Sources:** 8, 9, 15. Not resolved; treated as unsupported-but-possible, and the
   recommendation avoids it.
4. **Monocle's status.** TestFX's README still presents Monocle as *the* headless route,
   while OpenJFX 26 ships a headless platform that has nothing to do with Monocle.
   **Sources:** 17, 19. Not a factual conflict, but the guidance in the ecosystem is now
   wrong for JavaFX 26+.

## Missing evidence

- Minimum JDK per JavaFX release (see Open questions 1).
- Monocle's presence and usability on the current JavaFX line (open question 2).
- Tray icon approach: no primary source consulted at all.
- CLI parsing library: not investigated.
- No benchmark or CI timing data for Maven vs Gradle on this shape.
- No spike proving JavaFX 21 + Spring Boot 4 coexistence, only documentation-level reasoning.
- JUnit 5 exact version, and whether the project wants JUnit 6: not checked.
- Spring Boot's documented stance (if any) on JavaFX: I found none in the reference docs I
  read, so the negative is unproven; the module-path constraint from OpenJFX is the
  load-bearing fact instead.

## Researcher notes on method

- Interactive curator not used (`workflow: none`), per brief. Two searches hit provider rate
  limits; the gaps were closed by fetching primary URLs (Maven Central metadata, GitHub raw
  files, javadoc, vendor reference docs) directly. No claim in this document rests on a
  search-result summary alone.
- Preference order used: repository source and release notes > published metadata > vendor
  reference documentation > javadoc. Blog posts and forum threads were used only to locate
  primary sources and are not cited as evidence.
- A `source_check` pass was not run: the tool was not available in this session, so every
  load-bearing claim is backed by a URL whose passage is quoted in `## Sources`, and the
  quotes can be re-checked directly.
