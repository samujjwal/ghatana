package com.ghatana.flashit.kernel;

import com.ghatana.kernel.testing.ProductKernelBoundaryAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

@DisplayName("FlashIt kernel boundary import contract")
class FlashItKernelBoundaryContractTest {

    @Test
    void productSourcesOnlyUseStableKernelApis() throws Exception {
        Path projectDir = Path.of(System.getProperty("ghatana.projectDir", System.getProperty("user.dir")));
        ProductKernelBoundaryAssertions.assertProductUsesOnlyStableKernelApis(
                "FlashIt",
                projectDir.resolve("src/main/java"),
                projectDir.resolve("backend"),
                projectDir.resolve("client"));
    }
}
