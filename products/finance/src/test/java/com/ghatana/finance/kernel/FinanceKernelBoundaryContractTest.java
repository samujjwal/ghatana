package com.ghatana.finance.kernel;

import com.ghatana.kernel.testing.ProductKernelBoundaryAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

@DisplayName("Finance kernel boundary import contract")
class FinanceKernelBoundaryContractTest {

    @Test
    void productSourcesOnlyUseStableKernelApis() throws Exception {
        Path projectDir = Path.of(System.getProperty("ghatana.projectDir", System.getProperty("user.dir")));
        ProductKernelBoundaryAssertions.assertProductUsesOnlyStableKernelApis(
                "Finance",
                projectDir.resolve("src/main/java"),
                projectDir.resolve("domains"));
    }
}
