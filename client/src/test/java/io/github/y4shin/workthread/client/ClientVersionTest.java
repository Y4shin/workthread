package io.github.y4shin.workthread.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The window's label carries the build's version, and that version is the one
 * the build writes from {@code gradle.properties}: the label must not hold a
 * second copy of the number, or it drifts on the next bump.
 *
 * <p>The build passes its own version in as {@code workthread.version}, so this
 * test compares the label against the one source rather than against a copy of
 * the same literal.
 */
class ClientVersionTest {

    private static final String BUILD_VERSION = System.getProperty("workthread.version", "");

    @Test
    void theWindowLabelCarriesTheBuildVersion() {
        assertThat(BUILD_VERSION)
                .as("the build's version, passed in by the test task")
                .isNotEmpty();
        assertThat(WorkthreadApplication.windowLabel())
                .as("the label the window shows")
                .isEqualTo("workthread " + BUILD_VERSION);
    }
}
