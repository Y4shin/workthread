package io.github.y4shin.workthread.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientModuleTest {

    @Test
    void declaresTheClientPackageUnderTheSharedRoot() {
        assertThat(ClientModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.client");
    }
}
