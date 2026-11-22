package io.github.y4shin.workthread.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The client's declared stack, observed through the resolved test runtime
 * classpath. The seam is the build: these tests read the jars Gradle actually
 * resolved rather than a build script's text.
 */
class ClientStackTest {

    @Test
    void javafxResolvesAtThePinnedVersionWithTheHostPlatformClassifier() {
        assertThat(resolvedJars("javafx-controls-"))
                .as("javafx-controls on the client's runtime classpath")
                .isNotEmpty()
                .allSatisfy(jar -> assertThat(jar)
                        .as("JavaFX 21.0.13, host platform classifier")
                        .isEqualTo("javafx-controls-21.0.13-" + hostJavaFxClassifier() + ".jar"));
    }

    // The OpenJFX naming: linux, linux-aarch64, mac, mac-aarch64, win, win-aarch64.
    // The expected value comes from the host, not from the build script.
    private static String hostJavaFxClassifier() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String platform = os.startsWith("mac") || os.startsWith("darwin") ? "mac" : os.startsWith("windows") ? "win" : "linux";
        return arch.equals("aarch64") ? platform + "-aarch64" : platform;
    }

    @Test
    void theControlsModuleIsReachableFromTheClient() {
        assertThat(isLoadableWithoutInitialisation("javafx.scene.control.Label")).isTrue();
    }

    private static List<String> resolvedJars(String prefix) {
        List<String> names = new ArrayList<>();
        for (String property : List.of("java.class.path", "jdk.module.path")) {
            String value = System.getProperty(property);
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String entry : value.split(File.pathSeparator)) {
                String name = Path.of(entry).getFileName().toString();
                if (name.startsWith(prefix)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static boolean isLoadableWithoutInitialisation(String className) {
        try {
            Class.forName(className, false, ClientStackTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
