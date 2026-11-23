# Development environment

The repository holds no product code yet: the eight modules carry placeholders,
so there is nothing to plan work with. The one thing that starts is the client's
packaging proof, a `jlink` image whose launcher opens an empty window; see
[the client image](#the-client-image). Fill in the "Starting the application"
and "Reproduction" sections when the first module lands.

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

## The client image

The packaging proof: one stripped, compressed runtime image whose launcher
opens the empty window. Built and measured in this environment on Linux
x86_64.

Build it:

```sh
./gradlew :client:jlink
```

The image lands in `client/build/image`, and its one launcher is
`client/build/image/bin/workthread` (plus the generated `workthread.bat`). The
build passes the settled options, recorded verbatim from `:client:jlink --info`:

```
jlink -v --strip-debug --compress zip-6 \
      --module-path <jdk>/jmods/:client/build/jlinkbase/jlinkjars \
      --add-modules java.desktop,java.xml,jdk.unsupported,jdk.jfr,io.github.y4shin.workthread.client \
      --output client/build/image
```

Measured facts:

| Fact | Value |
| --- | --- |
| Image size, Linux x86_64 | 67 612 510 bytes (64.5 MiB, 67.6 MB); `du -sh` reports 65M |
| Modules in the image | `java.base` plus `java.desktop`, `java.xml`, `java.prefs`, `java.datatransfer`, `jdk.unsupported`, `jdk.jfr`, `javafx.base`, `javafx.controls` and `javafx.graphics` at 21.0.13, and `io.github.y4shin.workthread.client`, `.contract`, `.wiring` |
| Launcher command | `"$DIR/java" $CDS_JVM_OPTS -p "$DIR/../app" -m io.github.y4shin.workthread.client/io.github.y4shin.workthread.client.WorkthreadApplication` |
| Configuration cache | reuse on a second `:client:jlink`; no Gradle warning in `build` or `jlink` with `--warning-mode all` |

The size is measured after the window's label took its version from the build:
the client module carries the generated `workthread-version.properties`, which is
1 220 bytes more than the image measured before the label read it.

The launcher points at `$DIR/../app`, which does not exist inside the image:
the application module lives in `lib/modules` and the launcher works anyway.
The quirk is cosmetic and accepted.

### Launching it

```sh
xvfb-run -a --server-args="-screen 0 1280x800x24 -dpi 96" \
  ./client/build/image/bin/workthread
```

JavaFX needs an X display. Under Xvfb the ES2 pipeline logs
`ES2 Prism: Error - GLX extension is not supported` and falls back to software
rendering by itself; the window still comes up, and no `-Dprism.order=sw` is
needed. Pass `-dpi 96` to Xvfb, or the window is measured at the server's DPI
scale (100 dpi gave 1068x801 for a 1024x768 stage).

### The manual launch record

Verified once on this machine, on 2026-11-23, with the image built above and
the launch command above:

- `xwininfo -root -tree` reported the window `0x400002 "workthread"` with
  class `io.github.y4shin.workthread.client.WorkthreadApplication`, at
  1024x768, map state `IsViewable`.
- A screen capture of that display showed the empty 1024x768 window with one
  centred label reading `workthread 0.1.0`.
- The application log carried only the expected Xvfb GLX message
  (`ES2 Prism: Error - GLX extension is not supported`).

### Runtime prerequisites

A Linux host needs GTK3 and the X11 libraries; the image does not bundle them.
Recorded from `ldd` on the native libraries JavaFX extracts to
`~/.openjfx/cache/21.0.13+2/amd64`:

- `libglassgtk3.so` needs `libgtk-3.so.0`, `libgdk-3.so.0`, `libgio-2.0.so.0`,
  `libgobject-2.0.so.0`, `libgthread-2.0.so.0`, `libpango-1.0.so.0`,
  `libpangocairo-1.0.so.0`, `libcairo.so.2`, `libcairo-gobject.so.2`,
  `libgdk_pixbuf-2.0.so.0`, `libfontconfig.so.1`, `libfreetype.so.6`, and
  `libX11.so.6`, `libXext.so.6`, `libXi.so.6`, `libXtst.so.6`,
  `libXrender.so.1`, `libXrandr.so.2`, `libXcursor.so.1`, `libXinerama.so.1`,
  `libXfixes.so.3`, `libXcomposite.so.1`, `libXdamage.so.1`.
- `libprism_es2.so` additionally needs `libGL.so.1` and `libXxf86vm.so.1`.
- Proven by running the launcher with an emptied environment and no
  `LD_LIBRARY_PATH`: `libprism_es2.so` failed with
  `libXxf86vm.so.1: cannot open shared object file` and `libglassgtk3.so` with
  `libXtst.so.6: cannot open shared object file`. The dev shell supplies both
  families, which is why the launch above works there.
- First launch writes the extracted natives to `~/.openjfx/cache`, so a
  read-only home directory is a second prerequisite the image does not state.

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
