{ pkgs, lib, ... }:

let
  # Native libraries the bundled OpenJFX natives link against at runtime.
  # The JavaFX jars come from Maven Central; these are the Linux libraries
  # those natives load through dlopen.
  javafxLibs = with pkgs; [
    gtk3
    glib
    pango
    cairo
    gdk-pixbuf
    at-spi2-atk
    at-spi2-core
    freetype
    fontconfig
    alsa-lib
    libGL
    xorg.libX11
    xorg.libXext
    xorg.libXrender
    xorg.libXt
    xorg.libXtst
    xorg.libXi
    xorg.libXxf86vm
    xorg.xrandr
  ];
in
{
  # Java 21 is the floor the stack research settled on. The shell provides the
  # full JDK (javac included), not just the runtime that sits in /usr/bin.
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk21;
  };

  packages = with pkgs; [
    # Build tools. The prototypes use the Gradle wrapper; this Gradle is the
    # bootstrap that can generate or refresh that wrapper. Maven is here as a
    # fallback only, because docs/testing.md names it provisionally.
    gradle
    maven

    git
    curl
    jq
    unzip
    zip

    # A virtual display, so a JavaFX prototype can start in a headless session.
    xvfb
  ] ++ javafxLibs;

  env = {
    LD_LIBRARY_PATH = lib.makeLibraryPath javafxLibs;
    GRADLE_OPTS = "-Dorg.gradle.jvmargs=-Xmx2g";
    # Keep Gradle's cache inside the project so the shell does not depend on
    # a home-directory cache that a fresh clone would not have.
    GRADLE_USER_HOME = "./.gradle";
  };

  enterShell = ''
    echo "workthread dev shell"
    echo "  java:   $(java -version 2>&1 | head -1)"
    echo "  javac:  $(javac -version 2>&1 | head -1)"
    echo "  gradle: $(gradle --version 2>/dev/null | grep -E '^Gradle' | head -1)"
    echo "  maven:  $(mvn --version 2>/dev/null | head -1)"
  '';

  enterTest = ''
    java -version
    javac -version
    gradle --version
  '';
}
