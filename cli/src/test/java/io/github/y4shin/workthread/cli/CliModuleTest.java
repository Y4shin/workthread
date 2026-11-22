package io.github.y4shin.workthread.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CliModuleTest {

    @Test
    void declaresTheCliPackageUnderTheSharedRoot() {
        assertThat(CliModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.cli");
    }
}
