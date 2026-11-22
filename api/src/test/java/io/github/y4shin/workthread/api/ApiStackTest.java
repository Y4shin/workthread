package io.github.y4shin.workthread.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The API module's declared stack, observed through the resolved test runtime
 * classpath. The seam is the build.
 */
class ApiStackTest {

    @Test
    void springBootResolvesAtThePinnedVersionThroughTheDependencyPlatform() {
        assertThat(resolvedJars("spring-boot-"))
                .as("spring-boot on the API module's runtime classpath")
                .contains("spring-boot-4.2.0.jar", "spring-boot-starter-webmvc-4.2.0.jar");
    }

    @Test
    void theSpringBootTypeIsReachableFromTheApi() {
        assertThat(isLoadableWithoutInitialisation("org.springframework.boot.SpringApplication")).isTrue();
    }

    @Test
    void theTestClasspathStaysOnThePinnedFifthJUnitLineDespiteTheSpringBootPlatform() {
        assertThat(resolvedJars("junit-jupiter-"))
                .as("JUnit Jupiter on the API module's test classpath")
                .contains("junit-jupiter-api-5.14.4.jar")
                .allSatisfy(jar -> assertThat(jar).doesNotContain("-6."));
        assertThat(resolvedJars("junit-platform-"))
                .as("the JUnit Platform on the API module's test classpath")
                .isNotEmpty()
                .allSatisfy(jar -> assertThat(jar).doesNotContain("-6."));
        assertThat(resolvedJars("assertj-core-"))
                .as("AssertJ on the API module's test classpath")
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

    private static boolean isLoadableWithoutInitialisation(String className) {
        try {
            Class.forName(className, false, ApiStackTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
