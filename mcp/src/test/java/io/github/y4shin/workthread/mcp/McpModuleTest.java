package io.github.y4shin.workthread.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpModuleTest {

    @Test
    void declaresTheMcpPackageUnderTheSharedRoot() {
        assertThat(McpModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.mcp");
    }
}
