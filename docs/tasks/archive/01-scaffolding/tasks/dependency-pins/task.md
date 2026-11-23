---
type: task
subtype: research
title: Which versions and JPMS shapes does the declared stack pin
status: deprecated
workflow_state: done
blocked_by: []
---

## The question

What exact version does each declared library pin at 0.1, and what module name does each contribute on the module path?

The map pins these as starting values: JavaFX 21.0.13 in `client`, Spring Boot 4.2.0 in `api`, the MCP SDK 2.1.0 aggregator in `mcp`, sqlite-jdbc 3.54.1.0 in `adapters`, JUnit 5.14.4 and AssertJ 3.27.7 for tests. Confirm or correct each, and answer:

- **JavaFX.** 21.0.13 or 21.0.13.1? Which artifact set does the JavaFX Gradle plugin resolve per platform, and does the plugin version chosen in `packaging-stack` cover 21.0.13?
- **Spring Boot.** 4.2.0 is current and needs Java 17+ and Gradle 8.14+/9.x. Which Jackson line does it bring? The MCP SDK's `mcp-json-jackson3` name suggests Jackson 3, and `api` is the only module that would see both.
- **MCP SDK.** The 2.1.0 aggregator over `mcp-core` and `mcp-json-jackson3`: what is its transitive closure, what module names do those jars declare, and does `mcp`'s `module-info.java` need `requires` clauses for them? The constraint pins 2.1.0 even though 2.2.0 is current; note what 2.2.0 would change, but do not move the pin.
- **sqlite-jdbc.** Its automatic module name, and whether the native extraction changes with the module path.
- **Test libraries.** The architecture records "JUnit 5 and AssertJ". JUnit 6.1.3 exists and AssertJ 4.0.0-M1 is a milestone. Recommend the line to pin, with the reason. Keeping JUnit 5 is allowed; moving it needs a stated benefit.
- **Gradle plugin versions.** The wrapper is 9.9.1 and `org.beryx.jlink` is 4.2.0. Name any other build plugin the stack needs.

For each jar, state one of: named module with its `module-info` name, automatic module with its `Automatic-Module-Name`, or classpath-only. A named module cannot require an unnamed jar, so this decides which subprojects may depend on what.

## What it unblocks

The version catalogue (`gradle/libs.versions.toml`), every `module-info.java` `requires` clause, and the `api` classpath decision. It is the input the build tickets need to write dependency declarations that resolve on the first try.

## Trusted source boundaries

Maven Central metadata and POM files, the jars' own `module-info.class` and `META-INF/MANIFEST.MF`, project release notes and changelogs, and the OpenJFX documentation. Everything material carries a URL or a repository path. No aggregator sites.

## Evidence required for completion

A findings file at `docs/tasks/01-scaffolding/tasks/dependency-pins/findings.md` with:

- a table of coordinate, version, module name or classpath, and source;
- the recommended version set, distinguishing settled pins from recommendations;
- the Jackson overlap between Spring Boot and the MCP SDK, and the consequence for `api`;
- conflicts or surprises (a jar with no automatic name, a duplicate module name, a version that requires a newer JDK);
- unresolved questions, if any.

## Likely dependent tasks

The build tickets that write `settings.gradle.kts`, the version catalogue, and the module descriptors. `packaging-stack` may share the JavaFX plugin question; neither blocks the other.

## Notes

### Result: the seven pins hold, with three findings that change the build

The starting pins survive. Three findings need to reach the build tickets: the MCP SDK's payload jars are automatic modules, Spring Boot's BOM carries versions that contradict the map, and the Jackson 2/3 worry is not a conflict.

### Version table

| Coordinate | Pin | Module shape | Source |
| --- | --- | --- | --- |
| `org.openjfx:javafx-controls` and friends | 21.0.13 (keep; see below) | named: `javafx.controls`, `javafx.graphics`, `javafx.base` | Maven Central listing, JavaFX 21 release notes |
| `org.springframework.boot` BOM | 4.2.0 | n/a (BOM, `api` only) | `spring-boot-dependencies-4.2.0.pom` |
| `tools.jackson.core:jackson-databind` | 3.1.8 from the Boot BOM, 3.1.4 from the MCP SDK | named: `tools.jackson.databind` | Boot BOM, `mcp` module descriptor |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21.8 (Boot), 2.21 (MCP and Jackson 3) | automatic, `com.fasterxml.jackson.annotation` (not verified on the jar) | both BOMs |
| `io.modelcontextprotocol.sdk:mcp` | 2.1.0 (settled) | named: `io.modelcontextprotocol.sdk.mcp`, an aggregator with no code | `mcp/src/main/java/module-info.java` at tag `v2.1.0` |
| `io.modelcontextprotocol.sdk:mcp-core` | 2.1.0 | **automatic**: `io.modelcontextprotocol.sdk.mcp.core` | `mcp-core/pom.xml` at `v2.1.0` |
| `io.modelcontextprotocol.sdk:mcp-json-jackson3` | 2.1.0 | **automatic**: `io.modelcontextprotocol.sdk.mcp.json.jackson3` | `mcp-json-jackson3/pom.xml` at `v2.1.0` |
| `com.networknt:json-schema-validator` | 3.0.6 | named: `com.networknt.schema` | POM, and `mcp`'s `requires` clause |
| `org.xerial:sqlite-jdbc` | 3.54.1.0 (settled) | named: `org.xerial.sqlitejdbc`, from a multi-release `META-INF/versions/9/module-info.class` | `src/main/java9/module-info.java` at tag `3.54.1.0` |
| `org.junit:junit-bom` | 5.14.4 (keep) | n/a (BOM) | `junit-bom` metadata |
| `org.assertj:assertj-core` | 3.27.7 (keep) | automatic: `org.assertj.core` | project manifest record |
| `org.openjfx.javafxplugin` | 0.1.0 | n/a | proven by `packaging-stack` |
| `org.beryx.jlink` | 4.2.0 | n/a | proven by `packaging-stack` |

### Finding 1: the MCP payload is automatic, so it cannot enter an image

`mcp` is a named module, but it carries no code. Its descriptor exists to repeat the `requires` clauses its payload cannot supply:

```java
module io.modelcontextprotocol.sdk.mcp {
    requires transitive io.modelcontextprotocol.sdk.mcp.core;
    requires transitive io.modelcontextprotocol.sdk.mcp.json.jackson3;
    requires org.slf4j;
    requires tools.jackson.databind;
    requires com.networknt.schema;
}
```

`mcp-core` and `mcp-json-jackson3` are plain jars with `Automatic-Module-Name` and no `module-info.class`. `jlink` refuses automatic modules (`Error: automatic module cannot be used with jlink`), so a `jlink` image can never contain the MCP surface. That does not touch 0.1, because the image is `client` and the client depends on neither. It is a permanent property of the SDK at 2.1.0 and must be recorded where the MCP effort will read it.

The rest of the closure: `slf4j-api` 2.0.16 resolving to 2.0.20 under the Boot BOM, `reactor-core` 3.7.0, `reactive-streams` 1.0.4, `json-schema-validator` 3.0.6, `jackson-dataformat-yaml` 3.1.4, `itu` 1.14.0. `jakarta.servlet-api` is `provided` and not transitive.

### Finding 2: Spring Boot's BOM manages versions that contradict this map

`spring-boot-dependencies` 4.2.0 manages `junit-jupiter` **6.1.3** (the map pins JUnit 5.14.4) and `sqlite-jdbc` **3.53.4.0** (the map pins 3.54.1.0). A BOM imported with `platform()` contributes constraints, not enforcement, and Gradle takes the highest requested version:

- A direct `sqlite-jdbc:3.54.1.0` beats the BOM's 3.53.4.0.
- A direct `junit-jupiter:5.14.4` loses to the BOM's 6.1.3.

The rule: import the Boot BOM with `platform()` in `api` only, and declare the JUnit 5 BOM or an explicit Jupiter 5.14.4 test dependency in `api` so Spring Boot's 6.1.3 does not leak in. Do not apply `io.spring.dependency-management`; it applies Maven-style overriding and would downgrade `sqlite-jdbc` to 3.53.4.0. Do not apply the Spring Boot plugin at 0.1 either: the BOM is enough, and `bootJar` belongs to the daemon effort.

### Finding 3: the Jackson 2/3 clash does not exist

Spring Boot 4.2.0's JSON engine is Jackson 3 (`spring-boot-jackson` depends on `tools.jackson.core:jackson-databind` 3.1.8), and it also imports the Jackson 2 BOM (2.21.8) for compatibility. The MCP SDK wants `tools.jackson:jackson-databind` 3.1.4. Both are the same line, with a four-release patch drift that Gradle resolves upward. Jackson 3 deliberately keeps the Jackson 2 annotations artifact: `jackson-annotations` 2.21.8 versus 2.21 is the one shared artifact, and the same 2.21 line wins.

Consequences:
- No exclusion and no forced version in `api`.
- Do not declare Jackson by hand; let the BOM supply it.
- The `api` module never sees the MCP SDK under the `May depend on` table, so the patch drift is a future one-JVM risk, not a 0.1 one.

### Decisions recorded

- **JavaFX stays 21.0.13.** The 21.0.13.1 re-spin (2026-11-16) carries exactly one change, `JDK-8392009` "Update libxml2 to 2.15.4" in `javafx.web`, and the client's module set never loads `javafx.web`. The proven build therefore transfers without a benefit to buying, and the JavaFX Gradle plugin 0.1.0 has not been exercised with a four-segment version. Bump later if a reason appears; the cost is one string in the build and one `:client:jlink` run.
- **JUnit 5.14.4 and AssertJ 3.27.7 stay.** JUnit 6.1.3 would work on Java 21, but the architecture and the success test record JUnit 5, and the only gain is removing the one Spring Boot override. AssertJ 4.0.0-M1 is still a milestone, is binary-incompatible with 3.x, and marks 3.x as `forRemoval`; a foundation commit should not take it.
- **No extra Gradle plugins.** `java`, `application`, `java-library`, and `java-platform` are built in; the version catalogue is built in; Gradle 9 runs the JUnit Platform without a plugin; the refusal checks are root-build Kotlin; the image uses `org.beryx.jlink` plus `org.openjfx.javafxplugin` only.
- **sqlite-jdbc 3.54.1.0 is a named module** (`org.xerial.sqlitejdbc`), so `adapters` can `requires org.xerial.sqlitejdbc` if later work reaches an image. Today only `client` does, and it does not touch the store.

### Rejected

- Moving to JavaFX 21.0.13.1: one `javafx.web` fix, an unproven four-segment version path, no client benefit.
- Moving to JUnit 6.1.3: contradicts the recorded constraint for a small convenience.
- AssertJ 4.0.0-M1: milestone, breaking, no need.
- `io.spring.dependency-management`: downgrades `sqlite-jdbc` against the map.
- The Spring Boot Gradle plugin at 0.1: the BOM is enough and no `bootJar` is produced.

### Open

- `jar --describe-module --file` was not run: this child had no shell tool. Module names come from each project's `module-info.java` at the release tag, the POM's `Automatic-Module-Name`, or a published `requires` clause. The first build ticket should print the descriptor for `mcp`, `mcp-core`, `mcp-json-jackson3`, `sqlite-jdbc`, `json-schema-validator`, `slf4j-api`, `reactor-core`, `jackson-annotations`, `junit-jupiter-api`, and `assertj-core`. The ones marked "not verified on the jar" in the table are the ones to confirm.
- Whether the JDK honours sqlite-jdbc's multi-release descriptor: the name is the same either way, only the classification would change.
- Whether `api` ever hosts the MCP surface in one JVM: the architecture forbids surface-to-surface dependencies, so not now.

Full report: `/tmp/wayfinder-01/dependency-pins.md` and the child artifact `research.md` under `~/.pi/agent/sessions/--home-patric-Projects-workthread--/subagent-artifacts/outputs/`.
