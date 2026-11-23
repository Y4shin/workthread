package io.github.y4shin.workthread.wiring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WiringModuleTest {

    @Test
    void declaresTheWiringPackageUnderTheSharedRoot() {
        assertThat(WiringModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.wiring");
    }
}
