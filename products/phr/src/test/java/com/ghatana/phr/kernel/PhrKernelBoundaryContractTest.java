package com.ghatana.phr.kernel;

import com.ghatana.kernel.testing.ProductKernelBoundaryAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

@DisplayName("PHR kernel boundary import contract")
class PhrKernelBoundaryContractTest {

    @Test
    void productSourcesOnlyUseStableKernelApis() throws Exception {
        Path projectDir = Path.of(System.getProperty("ghatana.projectDir", System.getProperty("user.dir")));
        ProductKernelBoundaryAssertions.assertProductUsesOnlyStableKernelApis(
                "PHR",
                projectDir.resolve("src/main/java"),
                projectDir.resolve("domains/healthcare/src/main/java"));
    }
}
