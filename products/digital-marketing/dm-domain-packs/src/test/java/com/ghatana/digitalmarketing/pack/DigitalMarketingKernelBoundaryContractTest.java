package com.ghatana.digitalmarketing.pack;

import com.ghatana.kernel.testing.ProductKernelBoundaryAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

@DisplayName("DMOS kernel boundary import contract")
class DigitalMarketingKernelBoundaryContractTest {

    @Test
    void productSourcesOnlyUseStableKernelApis() throws Exception {
        Path projectDir = Path.of(System.getProperty("ghatana.projectDir", System.getProperty("user.dir")));
        ProductKernelBoundaryAssertions.assertProductUsesOnlyStableKernelApis(
                "Digital Marketing",
                projectDir.resolve("../dm-domain-packs/src/main/java"),
                projectDir.resolve("../dm-api/src/main/java"),
                projectDir.resolve("../dm-application/src/main/java"),
                projectDir.resolve("../dm-domain/src/main/java"),
                projectDir.resolve("../dm-infra/src/main/java"),
                projectDir.resolve("../dm-kernel-bridge/src/main/java"),
                projectDir.resolve("../dm-persistence/src/main/java"));
    }
}
