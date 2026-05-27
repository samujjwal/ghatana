/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.kernelvisibility;

import io.activej.promise.Promises;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for KernelHealthSnapshotService.
 */
class KernelHealthSnapshotServiceTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    private Path kernelOutputRoot;
    private KernelLifecycleEventIngestService ingestService;
    private KernelHealthSnapshotService healthService;

    @BeforeEach
    void setUp() {
        kernelOutputRoot = tempDir.resolve(".kernel");
        ingestService = KernelLifecycleEventIngestService.forLocalDevelopment(kernelOutputRoot);
        healthService = KernelHealthSnapshotService.forLocalDevelopment(ingestService);
    }

    @Test
    void localDevelopmentFactoryRejectsProductionRuntime() {
        String previousProfile = System.getProperty("yappc.runtime.profile");
        try {
            System.setProperty("yappc.runtime.profile", "production");

            assertThatThrownBy(KernelHealthSnapshotService::forLocalDevelopment)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("production must inject DataCloudKernelLifecycleTruthSource");
        } finally {
            if (previousProfile == null) {
                System.clearProperty("yappc.runtime.profile");
            } else {
                System.setProperty("yappc.runtime.profile", previousProfile);
            }
        }
    }

    @Test
    void getProductUnitHealthReturnsHealthView() throws Exception {
        // Setup: Create Kernel output directory with sample data
        Path productUnitPath = kernelOutputRoot.resolve("out/products/test-product");
        Files.createDirectories(productUnitPath);

        Map<String, Object> healthSnapshot = Map.of(
                "status", "healthy",
                "lastChecked", "2026-05-13T00:00:00Z"
        );
        Map<String, Object> lifecycleResult = Map.of(
                "status", "succeeded",
                "currentPhase", "deploy",
                "timestamp", "2026-05-13T12:00:00Z",
                "phases", List.of(
                        Map.of("name", "build", "status", "succeeded", "timestamp", "2026-05-13T10:00:00Z"),
                        Map.of("name", "deploy", "status", "succeeded", "timestamp", "2026-05-13T12:00:00Z")
                )
        );
        Map<String, Object> deployment = Map.of(
                "status", "deployed",
                "environment", "production"
        );

        Files.writeString(productUnitPath.resolve("health-snapshot.json"), "{\"status\":\"healthy\"}");
        Files.writeString(productUnitPath.resolve("lifecycle-result.json"), "{\"status\":\"succeeded\",\"currentPhase\":\"deploy\",\"timestamp\":\"2026-05-13T12:00:00Z\"}");
        Files.writeString(productUnitPath.resolve("deployment.json"), "{\"status\":\"deployed\",\"environment\":\"production\"}");

        // Execute
        KernelHealthSnapshotService.ProductUnitHealthView healthView = runPromise(() -> 
                healthService.getProductUnitHealth("test-product")
        );

        // Verify
        assertThat(healthView).isNotNull();
        assertThat(healthView.productUnitId()).isEqualTo("test-product");
        assertThat(healthView.overallStatus()).isEqualTo("healthy");
        assertThat(healthView.currentPhase()).isEqualTo("deploy");
        assertThat(healthView.deploymentStatus()).isEqualTo("deployed");
    }

    @Test
    void getProductUnitHealthHandlesMissingData() throws Exception {
        // Setup: Create empty Kernel output directory
        Path productUnitPath = kernelOutputRoot.resolve("out/products/test-product");
        Files.createDirectories(productUnitPath);

        // Execute
        KernelHealthSnapshotService.ProductUnitHealthView healthView = 
                runPromise(() -> healthService.getProductUnitHealth("test-product"));

        // Verify
        assertThat(healthView).isNotNull();
        assertThat(healthView.productUnitId()).isEqualTo("test-product");
        assertThat(healthView.overallStatus()).isEqualTo("unknown");
        assertThat(healthView.currentPhase()).isEqualTo("unknown");
        assertThat(healthView.gateFailureCount()).isEqualTo(0);
    }

    @Test
    void listProductUnitHealthReturnsSummaries() throws Exception {
        // Setup: Create multiple ProductUnits with data
        for (int i = 1; i <= 3; i++) {
            Path productUnitPath = kernelOutputRoot.resolve("out/products/product-" + i);
            Files.createDirectories(productUnitPath);
            Files.writeString(productUnitPath.resolve("health-snapshot.json"), "{\"status\":\"healthy\"}");
            Files.writeString(productUnitPath.resolve("lifecycle-result.json"), "{\"status\":\"succeeded\",\"currentPhase\":\"deploy\"}");
        }

        // Execute
        List<KernelHealthSnapshotService.ProductUnitHealthSummary> summaries = 
                runPromise(() -> healthService.listProductUnitHealth());

        // Verify
        assertThat(summaries).hasSize(3);
        assertThat(summaries.get(0).productUnitId()).isEqualTo("product-1");
        assertThat(summaries.get(1).productUnitId()).isEqualTo("product-2");
        assertThat(summaries.get(2).productUnitId()).isEqualTo("product-3");
    }

    @Test
    void listProductUnitHealthReturnsEmptyListWhenNoData() throws Exception {
        // Setup: No Kernel output directory

        // Execute
        List<KernelHealthSnapshotService.ProductUnitHealthSummary> summaries = 
                runPromise(() -> healthService.listProductUnitHealth());

        // Verify
        assertThat(summaries).isEmpty();
    }

    @Test
    void getLifecycleTimelineReturnsTimelineView() throws Exception {
        // Setup: Create Kernel output with lifecycle result
        Path productUnitPath = kernelOutputRoot.resolve("out/products/test-product");
        Files.createDirectories(productUnitPath);

        String lifecycleResultJson = """
                {
                    "status": "succeeded",
                    "currentPhase": "deploy",
                    "phases": [
                        {"name": "dev", "status": "succeeded", "timestamp": "2026-05-13T08:00:00Z"},
                        {"name": "validate", "status": "succeeded", "timestamp": "2026-05-13T09:00:00Z"},
                        {"name": "test", "status": "succeeded", "timestamp": "2026-05-13T10:00:00Z"},
                        {"name": "build", "status": "succeeded", "timestamp": "2026-05-13T11:00:00Z"},
                        {"name": "deploy", "status": "succeeded", "timestamp": "2026-05-13T12:00:00Z"}
                    ]
                }
                """;

        Files.writeString(productUnitPath.resolve("lifecycle-result.json"), lifecycleResultJson);

        // Execute
        KernelHealthSnapshotService.LifecycleTimelineView timeline = 
                runPromise(() -> healthService.getLifecycleTimeline("test-product"));

        // Verify
        assertThat(timeline).isNotNull();
        assertThat(timeline.productUnitId()).isEqualTo("test-product");
        assertThat(timeline.runs()).hasSize(5);
        assertThat(timeline.runs().get(0).phase()).isEqualTo("dev");
        assertThat(timeline.runs().get(0).status()).isEqualTo("succeeded");
        assertThat(timeline.runs().get(4).phase()).isEqualTo("deploy");
    }

    @Test
    void getLifecycleTimelineHandlesMissingPhases() throws Exception {
        // Setup: Create Kernel output without phases
        Path productUnitPath = kernelOutputRoot.resolve("out/products/test-product");
        Files.createDirectories(productUnitPath);
        Files.writeString(productUnitPath.resolve("lifecycle-result.json"), "{\"status\":\"succeeded\"}");

        // Execute
        KernelHealthSnapshotService.LifecycleTimelineView timeline = 
                runPromise(() -> healthService.getLifecycleTimeline("test-product"));

        // Verify
        assertThat(timeline).isNotNull();
        assertThat(timeline.productUnitId()).isEqualTo("test-product");
        assertThat(timeline.runs()).isEmpty();
    }

    @Test
    void inferOverallStatusFromHealthSnapshot() throws Exception {
        // Setup: Create health snapshot with status
        Path productUnitPath = kernelOutputRoot.resolve("out/products/test-product");
        Files.createDirectories(productUnitPath);
        Files.writeString(productUnitPath.resolve("health-snapshot.json"), "{\"status\":\"degraded\"}");

        // Execute
        KernelHealthSnapshotService.ProductUnitHealthView healthView = 
                runPromise(() -> healthService.getProductUnitHealth("test-product"));

        // Verify
        assertThat(healthView.overallStatus()).isEqualTo("degraded");
    }

    @Test
    void inferOverallStatusFromLifecycleResult() throws Exception {
        // Setup: Create lifecycle result with failed status
        Path productUnitPath = kernelOutputRoot.resolve("out/products/test-product");
        Files.createDirectories(productUnitPath);
        Files.writeString(productUnitPath.resolve("lifecycle-result.json"), "{\"status\":\"failed\"}");

        // Execute
        KernelHealthSnapshotService.ProductUnitHealthView healthView = 
                runPromise(() -> healthService.getProductUnitHealth("test-product"));

        // Verify
        assertThat(healthView.overallStatus()).isEqualTo("failed");
    }

    @Test
    void extractGateFailureCount() throws Exception {
        // Setup: Create gates with failures
        Path productUnitPath = kernelOutputRoot.resolve("out/products/test-product");
        Files.createDirectories(productUnitPath);
        Files.writeString(productUnitPath.resolve("gates.json"), "{\"failedCount\":3,\"totalCount\":10}");

        // Execute
        KernelHealthSnapshotService.ProductUnitHealthView healthView = 
                runPromise(() -> healthService.getProductUnitHealth("test-product"));

        // Verify
        assertThat(healthView.gateFailureCount()).isEqualTo(3);
    }
}
