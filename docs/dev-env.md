# Development environment

The repository holds no code yet, so the application cannot be started. The
toolchain the prototypes need exists, through the devenv shell described
below. Fill in the "Starting the application" and "Reproduction" sections when
the first module lands.

## Prerequisites

Nix with flakes, `devenv` (tested with 2.4.0), and `direnv` if you want the
shell to load on `cd`. All three are present on the development machine.

## The dev shell

The shell is defined in `devenv.nix` and `devenv.yaml`, with `devenv.lock`
pinning the inputs. It provides:

| Tool | Version | Why |
| --- | --- | --- |
| JDK | 21.0.12.1 (nixpkgs `jdk21`) | The stack floor. Full JDK, so `javac` is included. |
| Gradle | 8.14.4 | Bootstrap only. A prototype pins its own version through the Gradle wrapper. |
| Maven | 3.10.0 | Fallback only. `docs/testing.md` names Maven provisionally; the stack research recommends Gradle. |
| Xvfb | current nixpkgs | A virtual display, so a JavaFX window can start in a headless session. |
| GTK and X libraries | nixpkgs | The bundled OpenJFX natives load these at runtime. They are on `LD_LIBRARY_PATH`. |
| git, curl, jq, unzip, zip | nixpkgs | Fetching and inspecting artifacts. |

Enter it with:

```sh
devenv shell
```

Or let direnv load it on `cd`:

```sh
direnv allow
```

`devenv test` builds the shell and runs its smoke check: `java -version`,
`javac -version`, and `gradle --version`.

## Verified in this environment

- `javac` and `java` are both 21.0.12.1 from nixpkgs, not the runtime-only
  `/usr/bin/java` that sits on the host path.
- A JavaFX 21.0.5 window starts from the module path under `xvfb-run`, with
  the OpenJFX Linux natives loading against the shell's GTK libraries. The
  check printed `JAVAFX_STARTED`.
- Maven Central is reachable, so Gradle can fetch OpenJFX and any other
  dependency.

## Headless JavaFX notes

- Xvfb has no OpenGL, so the ES2 pipeline logs
  `GLX extension is not supported`. Add `-Dprism.order=sw` to force software
  rendering when the noise matters.
- JavaFX must sit on the module path. Classpath loading is documented as
  unsupported. Pass `--module-path <dir>` and `--add-modules javafx.controls`,
  or let a Gradle JavaFX plugin arrange it.
- The headless Glass platform (`-Dglass.platform=headless`) exists only from
  JavaFX 26. On JavaFX 21 the alternatives are Monocle through TestFX, which
  has had no release since February 2024, or Xvfb.

## Not yet verified

- A system tray icon. JavaFX has no tray API; the candidates are the AWT
  system tray or a third-party library, and neither has been tried here.
- JavaFX and Spring Boot sharing one JVM, including the classpath restriction
  the packaging research documents.
- The MCP Java SDK over stdio from this shell.

## Starting the application

To be filled in. Two modes must be documented when they exist:

- Standalone mode: one process, no API between daemon and client.
- Split mode: daemon, Spring Boot API, and client as separate processes.

For each mode, state the command, the port if any, and how to reset the local
state.

## Reproduction

Not possible yet. When the first module lands, state here how an agent starts
the application, how it reaches a failing state, and what it must not touch.
