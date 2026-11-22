package io.github.y4shin.workthread.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiModuleTest {

    @Test
    void declaresTheApiPackageUnderTheSharedRoot() {
        assertThat(ApiModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.api");
    }
}
