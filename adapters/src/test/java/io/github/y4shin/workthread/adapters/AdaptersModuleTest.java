package io.github.y4shin.workthread.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdaptersModuleTest {

    @Test
    void declaresTheAdaptersPackageUnderTheSharedRoot() {
        assertThat(AdaptersModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.adapters");
    }
}
