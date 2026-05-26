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

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for KernelActionRecommendationService.
 */
@DisplayName("KernelActionRecommendationService")
class KernelActionRecommendationServiceTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    private Path kernelOutputRoot;
    private KernelActionRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        kernelOutputRoot = tempDir.resolve(".kernel");
        KernelLifecycleEventIngestService ingestService =
                new KernelLifecycleEventIngestService(kernelOutputRoot);
        KernelHealthSnapshotService healthService = new KernelHealthSnapshotService(ingestService);
        recommendationService = new KernelActionRecommendationService(healthService);
    }

    @Test
    @DisplayName("recommendActions returns info recommendation when no lifecycle data exists")
    void recommendActionsReturnsInfoWhenNoData() {
        List<KernelActionRecommendationService.ActionRecommendation> recommendations =
                runPromise(() -> recommendationService.recommendActions("unknown-product"));

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations)
                .anyMatch(r -> "info".equals(r.severity()) && "run_lifecycle".equals(r.actionType()));
    }

    @Test
    @DisplayName("recommendActions returns no critical recommendations when ProductUnit is healthy")
    void recommendActionsReturnsNoCriticalWhenHealthy() throws Exception {
        Path productPath = kernelOutputRoot.resolve("out/products/healthy-product");
        Files.createDirectories(productPath);
        Files.writeString(productPath.resolve("lifecycle-result.json"),
                """
                {
                  "status": "succeeded",
                  "currentPhase": "deploy",
                  "timestamp": "2026-05-13T12:00:00Z"
                }
                """);
        Files.writeString(productPath.resolve("lifecycle-health-snapshot.json"),
                """
                {
                  "status": "healthy",
                  "lastChecked": "2026-05-13T12:00:00Z"
                }
                """);
        Files.writeString(productPath.resolve("deployment-manifest.json"),
                """
                {
                  "status": "deployed",
                  "environment": "local"
                }
                """);

        List<KernelActionRecommendationService.ActionRecommendation> recommendations =
                runPromise(() -> recommendationService.recommendActions("healthy-product"));

        assertThat(recommendations)
                .noneMatch(r -> "critical".equals(r.severity()));
    }

    @Test
    @DisplayName("recommendActions returns critical recommendation for failed ProductUnit")
    void recommendActionsReturnsCriticalForFailed() throws Exception {
        Path productPath = kernelOutputRoot.resolve("out/products/failed-product");
        Files.createDirectories(productPath);
        Files.writeString(productPath.resolve("lifecycle-result.json"),
                """
                {
                  "status": "failed",
                  "currentPhase": "build",
                  "timestamp": "2026-05-13T12:00:00Z",
                  "gates": {
                    "failedCount": 2,
                    "gates": [
                      {
                        "id": "build-gate",
                        "status": "failed",
                        "reason": "Build failed",
                        "owner": "platform-runtime",
                        "evidence": "evidence-build-1",
                        "nextAction": "Repair build script and retry validation"
                      }
                    ]
                  }
                }
                """);
        Files.writeString(productPath.resolve("lifecycle-health-snapshot.json"),
                "{\"status\": \"failed\"}");

        List<KernelActionRecommendationService.ActionRecommendation> recommendations =
                runPromise(() -> recommendationService.recommendActions("failed-product"));

        assertThat(recommendations)
                .anyMatch(r -> "critical".equals(r.severity()));
        assertThat(recommendations)
                .filteredOn(r -> "review_gates".equals(r.actionType()))
                .anySatisfy(r -> {
                    assertThat(r.owner()).isEqualTo("platform-runtime");
                    assertThat(r.reason()).isEqualTo("Build failed");
                    assertThat(r.evidenceId()).isEqualTo("evidence-build-1");
                    assertThat(r.nextAction()).isEqualTo("Repair build script and retry validation");
                });
    }

    @Test
    @DisplayName("recommendActions returns warning recommendation for degraded ProductUnit")
    void recommendActionsReturnsWarningForDegraded() throws Exception {
        Path productPath = kernelOutputRoot.resolve("out/products/degraded-product");
        Files.createDirectories(productPath);
        Files.writeString(productPath.resolve("lifecycle-result.json"),
                """
                {
                  "status": "degraded",
                  "currentPhase": "deploy",
                  "timestamp": "2026-05-13T12:00:00Z"
                }
                """);
        Files.writeString(productPath.resolve("health-snapshot.json"),
                "{\"status\": \"degraded\"}");
        Files.writeString(productPath.resolve("deployment.json"),
                "{\"status\": \"failed\"}");

        List<KernelActionRecommendationService.ActionRecommendation> recommendations =
                runPromise(() -> recommendationService.recommendActions("degraded-product"));

        assertThat(recommendations)
                .anyMatch(r -> "warning".equals(r.severity()) || "critical".equals(r.severity()));
    }

    @Test
    @DisplayName("explainGateFailure returns explanation when gate data is absent")
    void explainGateFailureReturnsDefaultExplanationWhenNoGateData() throws Exception {
        Path productPath = kernelOutputRoot.resolve("out/products/gate-product");
        Files.createDirectories(productPath);
        Files.writeString(productPath.resolve("lifecycle-result.json"),
                "{\"status\": \"failed\", \"currentPhase\": \"build\"}");
        Files.writeString(productPath.resolve("lifecycle-health-snapshot.json"),
                "{\"status\": \"failed\"}");

        KernelActionRecommendationService.GateFailureExplanation explanation =
                runPromise(() -> recommendationService.explainGateFailure("gate-product", "build-gate"));

        assertThat(explanation).isNotNull();
        assertThat(explanation.gateId()).isEqualTo("build-gate");
    }

    @Test
    @DisplayName("ActionRecommendation preserves all fields")
    void actionRecommendationPreservesFields() {
        KernelActionRecommendationService.ActionRecommendation rec =
                new KernelActionRecommendationService.ActionRecommendation(
                        "critical", "Build failed", "Fix build errors", "review_gates");

        assertThat(rec.severity()).isEqualTo("critical");
        assertThat(rec.title()).isEqualTo("Build failed");
        assertThat(rec.description()).isEqualTo("Fix build errors");
        assertThat(rec.actionType()).isEqualTo("review_gates");
        assertThat(rec.owner()).isEmpty();
        assertThat(rec.reason()).isEmpty();
        assertThat(rec.evidenceId()).isEmpty();
        assertThat(rec.nextAction()).isEmpty();
    }
}
