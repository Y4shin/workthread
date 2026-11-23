# Development environment

The eight modules build and the client ships a `jlink` image whose launcher
opens an empty window; see [the client image](#the-client-image). No product
behaviour exists yet, so nothing else starts. Fill in the "Starting the
application" and "Reproduction" sections when the first module lands.

## Prerequisites

Nix with flakes, `devenv` (tested with 2.4.0), and `direnv` if you want the
shell to load on `cd`. All three are present on the development machine.

## The dev shell

The shell is defined in `devenv.nix` and `devenv.yaml`, with `devenv.lock`
pinning the inputs. It provides:

| Tool | Version | Why |
| --- | --- | --- |
| JDK | 21.0.12.1 (nixpkgs `jdk21`) | The stack floor. Full JDK, so `javac` is included. |
| Gradle | 8.14.4 | The shell's own Gradle. Never the build tool: the repository pins 9.9.1 through the committed wrapper. |
| Maven | 3.10.0 | Unused. Nothing in the repository builds with it. |
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

An image is a directory tree, not a disk image and not a container. It holds
`bin/` with the launchers, `lib/` with a private Java runtime and the
application modules, `conf/`, and `release`. Copy or unpack it anywhere and run
it; nothing is installed. The pipeline uploads that tree zipped, one artifact
per platform.

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

Verified from the pipeline's own artifacts, run 45089591551 of pull request 1.

| Platform | Artifact | Result |
| --- | --- | --- |
| Linux x86_64 | `image-ubuntu-24.04`, 68 616 047 bytes unpacked | Launched. Window `workthread` at 1024x768, map state `IsViewable`, one centred label reading `workthread 0.1.0`, empty application log. |
| macOS | `image-macos-26`, 59 189 008 bytes unpacked | Not launched. No macOS host was reached in this run. |
| Windows | `image-windows-2025`, 57 778 847 bytes unpacked | Not launched. No Windows host was reached in this run. |

Both launchers are present in every image: `bin/workthread` and
`bin/workthread.bat`, so the Windows entry point is `bin\workthread.bat`.

How the Linux artifact was launched, because this host cannot run it directly.
The image was unpacked under `/tmp/ci-images/ubuntu-24.04`, then run inside an
`ubuntu:24.04` container, which matches the runner:

```sh
docker run --rm -v /tmp/ci-images/ubuntu-24.04:/image:ro ubuntu:24.04 bash -c '
  apt-get update -qq
  apt-get install -y -qq --no-install-recommends \
    xvfb libgtk-3-0t64 libgl1 libxxf86vm1 x11-utils
  cp -r /image /tmp/image && chmod +x /tmp/image/bin/*
  Xvfb :99 -screen 0 1280x800x24 -dpi 96 &
  sleep 2
  DISPLAY=:99 /tmp/image/bin/workthread &
  sleep 10
  DISPLAY=:99 xwininfo -root -tree | grep -i workthread
'
```

#### Why this host cannot launch the pipeline image directly

Running the same artifact in this dev shell fails with
`Unable to load glass GTK library`. The cause is a glibc mismatch, not the
image. The dev shell puts the Nix store's GTK on `LD_LIBRARY_PATH`, and the
image's JDK is Temurin, so its dynamic loader resolves
`/lib/x86_64-linux-gnu/libc.so.6` while the store `libglib-2.0.so.0` needs
`GLIBC_2.43`. The same file loads fine from the dev shell's own Nix JVM, which
is why the image built on this machine starts and the Temurin one does not.

Two consequences. On this host, launch a Temurin-linked image in a container or
not at all. And the two images are not interchangeable for this check: the
image built here links the Nix JDK 21.0.12.1, the pipeline's links Temurin.

#### The macOS leg links a different patch

`release` inside the images reports `JAVA_VERSION="21.0.13"` on Ubuntu and
Windows, and `"21.0.13.1"` on macOS. The pipeline pins `21.0.13` in
`actions/setup-java`, so the macOS leg linked a JDK the step did not install,
most likely the runner's preinstalled one through Gradle's toolchain
resolution. The architecture's claim that all three legs link the same patch is
false until that is fixed.

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
