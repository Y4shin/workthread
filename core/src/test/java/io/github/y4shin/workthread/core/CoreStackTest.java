package io.github.y4shin.workthread.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The core depends on the contract alone: the framework-bearing modules keep
 * their stacks to themselves. The seam is the build, read through the resolved
 * test runtime classpath.
 */
class CoreStackTest {

    @Test
    void theFrameworkStacksOfOtherModulesAreAbsentFromTheCore() {
        assertThat(isLoadableWithoutInitialisation("org.springframework.boot.SpringApplication"))
                .as("Spring Boot in the core")
                .isFalse();
        assertThat(isLoadableWithoutInitialisation("javafx.scene.control.Label"))
                .as("JavaFX in the core")
                .isFalse();
    }

    private static boolean isLoadableWithoutInitialisation(String className) {
        try {
            Class.forName(className, false, CoreStackTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
