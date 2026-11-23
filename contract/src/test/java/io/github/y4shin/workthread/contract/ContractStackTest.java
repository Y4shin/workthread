package io.github.y4shin.workthread.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The test libraries every module runs, observed from the module that declares
 * no dependencies of its own: whatever resolves here comes from the shared
 * build convention alone. The seam is the build.
 */
class ContractStackTest {

    @Test
    void thePinnedTestLibrariesResolveForAModuleWithNoDependenciesOfItsOwn() {
        assertThat(resolvedJars("junit-jupiter-"))
                .as("JUnit Jupiter on the contract module's test classpath")
                .contains("junit-jupiter-api-5.14.4.jar");
        assertThat(resolvedJars("assertj-core-"))
                .as("AssertJ on the contract module's test classpath")
                .contains("assertj-core-3.27.7.jar");
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
}
