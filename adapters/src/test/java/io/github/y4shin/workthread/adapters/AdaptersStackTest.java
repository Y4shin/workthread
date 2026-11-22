package io.github.y4shin.workthread.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The adapters' declared stack, observed through the resolved test runtime
 * classpath. The seam is the build.
 */
class AdaptersStackTest {

    @Test
    void theSqliteDriverResolvesAtThePinnedVersion() {
        assertThat(resolvedJars("sqlite-jdbc-"))
                .as("the SQLite driver on the adapters' runtime classpath")
                .contains("sqlite-jdbc-3.54.1.0.jar");
    }

    @Test
    void theSqliteDriverTypeIsReachableFromTheAdapters() {
        assertThat(isLoadableWithoutInitialisation("org.sqlite.JDBC")).isTrue();
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
            Class.forName(className, false, AdaptersStackTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
