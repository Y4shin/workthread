---
type: findings
title: "Prototype: one CLI serving MCP over stdio"
status: stable
source_task: cli-mcp-stdio
---

# Can one CLI serve MCP over stdio and stay usable interactively?

**Verdict: yes.** A `mcp serve` subcommand that owns stdin/stdout for its
lifetime works, the interactive subcommands are unaffected, and the MCP Java SDK
already handles the whole process lifecycle — no park loop, no shutdown hook, no
EOF latch. The cost of MCP mode is ~290 ms of startup and one hard invariant:
**nothing may write to stdout** while the subcommand is running.

Everything below was measured in this environment (JDK 21.0.12.1, Gradle 8.14.4,
Linux/WSL2) against a working binary at
`/tmp/planner-v1-cli-mcp/build/install/planner-cli/bin/planner-cli`.

---

## 1. Resolved versions

| Item | Value |
| --- | --- |
| Declared dependency | `io.modelcontextprotocol.sdk:mcp` |
| **Resolved version** | **2.1.0** (newest non-milestone; `2.2.0-M2` is the newest overall) |
| `mcp` artifact nature | aggregator POM only — the 2.1.0 jar is 2.3 KB and contains no classes |
| Real code artifacts | `io.modelcontextprotocol.sdk:mcp-core:2.1.0`, `io.modelcontextprotocol.sdk:mcp-json-jackson3:2.1.0` |
| JSON | `tools.jackson.core:jackson-databind:3.1.4` (Jackson 3, new `tools.jackson` coordinates) |
| Reactive | `io.projectreactor:reactor-core:3.7.0` |
| Logging | `org.slf4j:slf4j-api:2.0.17` (no binding is bundled) |
| Jars on runtime classpath | 15 total (14 transitive + the app jar) |
| Startup: `tasks list` | 34 ms |
| Startup: `mcp serve` (even with immediate EOF) | 323 ms |

```groovy
dependencies {
    implementation 'io.modelcontextprotocol.sdk:mcp:2.1.0'
    runtimeOnly 'org.slf4j:slf4j-simple:2.0.16'   // binding choice matters, see §4
}
```

## 2. The registration code that worked

`src/main/java/dev/planner/cli/McpServeCommand.java` (52 lines, whole file):

```java
package dev.planner.cli;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

final class McpServeCommand {

    private static final String SERVER_NAME = "planner-cli";
    private static final String SERVER_VERSION = "0.1.0";
    private static final String ECHO_TOOL_NAME = "echo";

    private McpServeCommand() {
    }

    static void serve() {
        var transportProvider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        var echoInputSchema = Map.<String, Object>of(
                "type", "object",
                "properties", Map.of("text", Map.of("type", "string")),
                "required", List.of("text"));

        var echoSpec = new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder(ECHO_TOOL_NAME, echoInputSchema)
                        .description("Echo back the text argument.")
                        .build(),
                (exchange, request) -> McpSchema.CallToolResult.builder()
                        .addTextContent(String.valueOf(request.arguments().get("text")))
                        .build());

        McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(echoSpec)
                .build();
    }
}
```

Note what is **absent**: no `Thread.currentThread().join()`, no shutdown hook, no
latch, no `closeGracefully()` call. `serve()` returns and the JVM keeps running.
See §5.

Entry point (`PlannerCli.main`) is ~10 lines of dispatch:

```java
if (args.length == 2 && "mcp".equals(args[0]) && "serve".equals(args[1])) {
    McpServeCommand.serve();
    return;
}
if (args.length == 2 && "tasks".equals(args[0]) && "list".equals(args[1])) {
    TasksListCommand.list();
    return;
}
usage();   // stderr + exit 2
```

Build/run:

```bash
export GRADLE_USER_HOME=/tmp/planner-v1-gradle-home
cd /tmp/planner-v1-cli-mcp && gradle installDist
build/install/planner-cli/bin/planner-cli tasks list
build/install/planner-cli/bin/planner-cli mcp serve
```

## 3. Handshake transcript

Driven by `handshake.sh` (bash coproc over pipes). Each request is written and
exactly one response line is read before sending the next — i.e. a client that
waits for the `initialize` response, which is what real clients do.
Full file: `transcript/HANDSHAKE.txt`.

```
--- REQUEST 1 (initialize) ---
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"handshake-probe","version":"0.0.1"}}}
--- RESPONSE 1 ---
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"logging":{},"tools":{"listChanged":false}},"serverInfo":{"name":"planner-cli","version":"0.1.0"}}}

--- NOTIFICATION (no response expected) ---
{"jsonrpc":"2.0","method":"notifications/initialized"}

--- REQUEST 2 (tools/list) ---
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
--- RESPONSE 2 ---
{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","description":"Echo back the text argument.","inputSchema":{"required":["text"],"type":"object","properties":{"text":{"type":"string"}}}}]}}

--- REQUEST 3 (tools/call) ---
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hello over stdio"}}}
--- RESPONSE 3 ---
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"hello over stdio"}],"isError":false}}
```

Client closes stdin → server exits **0** on its own. Framing is one JSON object
per line, exactly as the stdio transport requires.

Additional protocol behaviour observed:

- Unknown tool → proper error, no crash:
  `{"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Unknown tool: invalid_tool_name","data":"Tool not found: nope"}}`
- Malformed (non-JSON) input line → SDK logs `ERROR ... Failed to read value`
  with a full stack trace and carries on serving. Exit code still 0.
- `tools/call` sent *before* `initialize` → **no reply at all**; the request is
  swallowed and the client hangs. The SDK does not answer with an error.

## 4. Stdout purity (the protocol-corruption question)

`purity_probe.py` spawns the binary, sends initialize + initialized + tools/list
+ tools/call, keeps stdin open until all three ids are answered, then closes it,
and classifies **every** stdout line as a protocol frame or a leak.

| Runtime classpath | Frames | Leaked stdout lines | Answered ids | Verdict |
| --- | --- | --- | --- | --- |
| Ship: `slf4j-simple` pinned to `System.err`, level `warn` | 3 | **0** | 1,2,3 | **PURE** |
| No SLF4J provider at all | 3 | **0** | 1,2,3 | **PURE** (3 warning lines, all on stderr) |
| `logback-classic:1.5.18`, default config | 3 | **14** | 1,2,3 | **CORRUPTED** |

Answer to (a): **no banner, no log line, no framework output leaks to stdout in
the shipped configuration — but only because the logging binding was chosen
deliberately.** With no provider on the classpath the SDK's slf4j calls degrade
to a NOP logger and SLF4J's own three warning lines go to stderr (harmless). With
**logback** on the classpath — the default in Spring Boot — logback's default
`ConsoleAppender` targets `System.out` at `DEBUG`, and the protocol stream is
destroyed:

```
00:39:47.755 [main] DEBUG reactor.util.Loggers -- Using Slf4j logging framework
00:39:47.797 [pool-1-thread-1] DEBUG io.modelcontextprotocol.spec.McpTransport -- Received JSON message: {"jsonrpc":"2.0",...
```

14 log lines interleaved with 3 frames. A client that parses stdout line-by-line
as JSON-RPC — which is what the stdio framing invites, and what the bash coproc
transcript in §3 does with a plain `read line` — dies on the very first line.
Note the frames *did* still arrive in that run, so a lenient client that only
looks for JSON-looking lines appears to work — which is worse, because the
corruption is intermittent and silent.

How it is suppressed, in `build.gradle`:

```groovy
application {
    applicationDefaultJvmArgs = [
            '-Dorg.slf4j.simpleLogger.logFile=System.err',
            '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn',
    ]
}
```

This bakes `DEFAULT_JVM_OPTS` into the generated start script, so the
suppression travels with the binary instead of depending on the caller. Verified
present at line 205 of `build/install/planner-cli/bin/planner-cli`.

Evidence that this is load-bearing rather than cosmetic: the malformed-input
stack trace in §3 is ~20 lines of ERROR output, and it went to **stderr**. Under
logback those same 20 lines would have gone to stdout.

Purity rule for the architecture, stated once: **in MCP mode stdout is a wire,
not a UI.** Every diagnostic channel — logging binding config, exception
printers, progress bars, any `System.out.println` — must be redirected to stderr
at process start, before the transport is constructed.

Also checked: running `planner-cli mcp serve` on a real pty produces **zero
bytes** of output (no banner) and exits 0 on EOF. A user who types it sees
nothing at all until they press Ctrl-D. No banner is correct; the total silence
is a discoverability wart (see §7).

And (b): the interactive subcommand works normally before, between, and after
MCP sessions in the same shell — `tasks list` printed its three rows and exited
0 in all three positions, and it ignores piped stdin
(`printf 'garbage\n' | planner-cli tasks list` still prints the table). Separate
processes, no shared state, no interference.

## 5. Lifecycle: the SDK already does it, and one pitfall

The first thing I built was the obvious defensive one: park the main thread
(`Thread.currentThread().join()`) and add a shutdown hook. That server **never
exited** when the client closed stdin — it had to be SIGKILLed
(`SERVER_STILL_RUNNING_AFTER_EOF=yes`, exit 143).

Then I built the "correct" version: wrap `System.in` in a `FilterInputStream`
that counts down a latch on EOF, and call `closeGracefully()` from main. That
works and exits 0 — and costs 98 lines instead of 52 for one tool, plus a
`CountDownLatch` and a stream wrapper in the hot path of every read.

Then I tested the version with **no lifecycle code at all** — `serve()` returns
immediately after `build()`. It works identically and exits 0 on EOF. A thread
dump while serving shows why:

```
"pool-1-thread-1" ... non-daemon        <- the stdio reader, keeps the JVM alive
"DestroyJavaVM"  ... waiting on condition  <- main already returned
```

The transport's reader thread is non-daemon, so the JVM lives exactly as long as
the client holds stdin open, and dies when it does not. **The cheapest correct
implementation is the one with no lifecycle code**, which is what ships here.
The dependency to be aware of: this relies on the transport thread not being
daemon. If a future SDK version makes it daemon, `main` returning would exit
instantly and the server would do nothing. A one-line regression test (§8) is
worth more than defensive glue.

**The pitfall found along the way:** if a client writes its requests *pipelined*
and closes stdin immediately, the tail of the responses is silently dropped —
reproducibly, 6/6 runs:

```
stdin closed 0.00s after last write -> ids [1, 2]     (tools/call reply lost)
stdin held   0.10s                  -> ids [1, 2]
stdin held   0.25s                  -> ids [1, 2]
stdin held   0.50s                  -> ids [1, 2, 3]
```

This is not a bug in the glue (it reproduces identically with zero glue) and not
a per-request delay: measured response latencies are id 1 @ 389 ms, id 2 @ 396 ms,
id 3 @ 405 ms — the ~390 ms is one-off SDK/JVM startup. The real rule is:
**EOF on stdin cancels everything not yet dispatched.** Real MCP clients wait for
the `initialize` response before sending anything else and keep the pipe open, so
they never observe it — but any future "pipe a script into `mcp serve`" test
harness will hit it, and will look like a server bug. Whoever owns the stdio
seam owns this rule.

## 6. API friction

Things that cost time or need a non-obvious call:

1. **The `mcp` artifact is empty.** `io.modelcontextprotocol.sdk:mcp:2.1.0` is a
   2.3 KB aggregator with no classes; the code lives in `mcp-core` and
   `mcp-json-jackson3`. Fine as a dependency, surprising when you open the jar.
2. **A JSON mapper must be supplied explicitly.** `new StdioServerTransportProvider()`
   without an argument does not exist in 2.1.0; the constructor takes an
   `McpJsonMapper`. The idiom is `McpJsonDefaults.getMapper()`, which is a
   `ServiceLoader` lookup that only succeeds because `mcp-json-jackson3` is on the
   classpath. Drop that artifact and the failure is at runtime, not compile time.
3. **`Tool.builder()` and `Tool.builder(String)` are deprecated in 2.1.0.**
   Both compile with `-Xlint:deprecation` warnings (and break a `-Werror`
   build). The blessed forms are `Tool.builder(name, Map<String,Object> schema)`
   or `Tool.builder(name, McpJsonMapper, String schemaJson)` — i.e. the input
   schema must be present at builder-factory time. The whole tree compiles with
   zero warnings once you use those.
4. **`capabilities()` and `serverInfo()` are optional but their defaults are
   traps.** Omit both and the server still serves tools correctly, but advertises:
   `{"logging":{},"tools":{}}` and
   `"serverInfo":{"name":"Java SDK MCP Server","version":"0.15.0"}` — a stale
   hardcoded version string from the SDK's own pom. Every MCP client UI would
   show that name. Nothing enforces setting it.
5. **`logging` capability is advertised even when you never asked for it.** With
   an explicit `.capabilities(...tools(false)...)` the response is
   `{"logging":{},"tools":{"listChanged":false}}`. The builder has a no-arg
   `logging()` to enable it, but no way to switch it off. A client is therefore
   invited to send `logging/setLevel`, which this server does not handle.
6. **Reactor leaks into the API surface.** `notifyClients`, `closeGracefully` and
   the transport provider all return `Mono`; declaring `sync` needs reactor on
   the classpath (`reactor-core` 3.7.0, 1.8 MB + 12 KB `reactive-streams`) even
   though the tool handler is an ordinary `BiFunction`. The whole runtime
   classpath is 6.1 MB across 15 jars, and `mcp serve` takes 323 ms to start
   versus 34 ms for `tasks list` — for a one-tool string-echo server.
7. **The schema is plain `Map<String,Object>`**, so there is no compile-time
   check that the JSON Schema you hand a tool is valid, and no generated schema
   from a record/POJO. For a real tool set this is the seam most likely to get
   messy (typos in `required`, silently ignored properties).
8. **No error on a pre-initialize request** (§3) — silence, not a JSON-RPC
   error. Any harness that forgets `notifications/initialized` will hang.

## 7. Two shippable shapes

I built both and measured them. `McpServeCommand.java` is **byte-identical**
between them; only the entry-point dispatch differs.

### A. Subcommand: `planner-cli mcp serve` (shipped, recommended)

```java
if (args.length == 2 && "mcp".equals(args[0]) && "serve".equals(args[1])) {
    McpServeCommand.serve();
    return;
}
```

- Code: 26 non-blank lines in `PlannerCli.java`, of which ~4 are the MCP branch.
- Read: "this is the HTTP-server-shaped command", the stdio ownership is visible
  in the command line the client is configured with, and it composes — a future
  `mcp serve --transport http` or `mcp inspect` has an obvious home.
- MCP mode cannot be entered by accident: the first token must be `mcp`.
- Cost: two words to type in a client config, and `planner-cli mcp serve` from a
  terminal *looks like nothing happened* (zero bytes until Ctrl-D). That silence
  needs a `--help` line and docs.

### B. Startup flag: `planner-cli --mcp`

Built as `variant-flag/`, verified passing the same probe (`PURE`, ids 1,2,3).

```java
for (String arg : args) {
    if ("--mcp".equals(arg)) {
        McpServeCommand.serve();
        return;
    }
}
```

- Code: 28 non-blank lines, i.e. **+2 lines** versus A. Within noise.
- The real cost is user confusion, not code: the flag is scanned anywhere, so it
  silently overrides the subcommand. Observed failure mode —

  ```
  $ planner-cli tasks list --mcp
  $ echo $?
  0
  ```

  No task table, no error, exit 0. The user asked for the table and got a silent
  no-op (with a real terminal instead of `/dev/null` it hangs reading the tty
  instead). That is the same muscle-memory mistake as `git commit --amend`
  typo'd into a different flag, except the CLI cannot tell you it happened.
- Also: the flag has to be threaded through every subcommand's parser forever,
  and `--mcp` on a subcommand that already owns stdout (`tasks list`) is a
  meaningless combination the parser cannot reject.

### C. Separate launcher (considered, not built)

An `planner-cli-mcp` entry point or a `planner-mcp` script. Costs a second
generated start script / a second `mainClass` for the same code, and splits the
thing the user installs into two. It buys nothing over A, because A already
gives MCP its own `mcp serve` command — and it loses A's guarantee that the
interactive and protocol surfaces share one version, one jar, one release.
Useful only if the MCP server ever needs a different runtime profile (heap,
no-CLI-dependency isolation), which this prototype shows it does not.

## 8. Recommendation

Ship **A: `planner-cli mcp serve`**. It is the same code as B, cannot be entered
by accident, and makes the stdio-ownership seam visible in the client config.
Encode the two rules as tests rather than glue:

1. **Stdout purity test** — run the subcommand through `purity_probe.py`-style
   harness; assert every stdout line parses as JSON-RPC and stderr is where
   everything else goes. This is the one test that catches a future logback
   dependency, a stray `println`, or a framework that prints a startup banner.
2. **Lifecycle test** — feed initialize/tools-list/tools-call over a pipe with
   stdin held open, assert ids 1,2,3 answered and exit code 0 after EOF. This
   catches the day the transport thread becomes a daemon thread.

## 9. Artifacts (kept, nothing deleted)

```
/tmp/planner-v1-cli-mcp/
  build.gradle, settings.gradle          # SDK 2.1.0 + slf4j-simple to stderr
  src/main/java/dev/planner/cli/         # PlannerCli, McpServeCommand, TasksListCommand
  handshake.sh                           # coproc JSON-RPC driver
  purity_probe.py                        # stdout line classifier (§4)
  transcript/HANDSHAKE.txt               # the transcript in §3
  transcript/stdout.frames, stderr.log   # last run's raw streams (stderr empty)
  transcript-{no-binding,logback}/       # §4 variant captures
  variants/{no-binding,logback,return-main}/   # classpath / lifecycle variants
  variant-flag/                          # shape B, built and measured
  variant-bare/                          # no serverInfo/capabilities (§6.4)
  variant-park.java.bak, variant-eof-glue.java.bak  # the two lifecycle variants passed over (59 / 98 lines)
```
