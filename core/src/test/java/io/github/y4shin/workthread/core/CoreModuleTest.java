package io.github.y4shin.workthread.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreModuleTest {

    @Test
    void declaresTheCorePackageUnderTheSharedRoot() {
        assertThat(CoreModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.core");
    }
}
