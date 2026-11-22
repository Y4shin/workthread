package io.github.y4shin.workthread.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractModuleTest {

    @Test
    void declaresTheContractPackageUnderTheSharedRoot() {
        assertThat(ContractModuleTest.class.getPackageName())
                .isEqualTo("io.github.y4shin.workthread.contract");
    }
}
