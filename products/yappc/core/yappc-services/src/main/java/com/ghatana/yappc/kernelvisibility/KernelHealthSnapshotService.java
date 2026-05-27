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

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides health views for Kernel ProductUnits based on ingested lifecycle data.
 *
 * <p>This service transforms raw Kernel lifecycle data into structured health views
 * for YAPPC's visibility/control-plane layer. It relies on a {@link KernelLifecycleTruthSource}
 * to provide raw lifecycle truth, then normalizes it into health summaries, timelines, and detailed views.
 *
 * <p><b>Initial Implementation (Local Filesystem)</b></p>
 * <ul>
 *   <li>Kernel health snapshots/manifests from local filesystem</li>
 *   <li>ProductUnit health summary with overall status</li>
 *   <li>Lifecycle timeline view with phase-by-phase status</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Provides health views for Kernel ProductUnits based on ingested lifecycle data
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KernelHealthSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(KernelHealthSnapshotService.class);

    private final KernelLifecycleTruthSource truthSource;

    /**
     * Creates a dev/test-only health snapshot service backed by local manifest truth.
     */
    public static KernelHealthSnapshotService forLocalDevelopment() {
        return new KernelHealthSnapshotService();
    }

    /**
     * Creates a dev/test-only health snapshot service backed by local ingest wiring.
     *
     * @param ingestService local filesystem ingest service
     */
    public static KernelHealthSnapshotService forLocalDevelopment(@NotNull KernelLifecycleEventIngestService ingestService) {
        return new KernelHealthSnapshotService(ingestService);
    }

    /**
     * Constructs a new KernelHealthSnapshotService with default local manifest truth source.
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    public KernelHealthSnapshotService() {
        this(LocalKernelManifestTruthSource.forLocalDevelopment());
    }

    /**
     * Constructs a new KernelHealthSnapshotService with explicit truth source.
     *
     * @param truthSource the truth source to use for reading Kernel lifecycle data
     */
    public KernelHealthSnapshotService(@NotNull KernelLifecycleTruthSource truthSource) {
        this.truthSource = truthSource;
    }

    /**
     * Constructs a new KernelHealthSnapshotService backed by Data Cloud lifecycle truth.
     *
     * @param dataCloudClient Data Cloud client
     * @param tenantId tenant identifier
     */
    public KernelHealthSnapshotService(@NotNull DataCloudClient dataCloudClient, @NotNull String tenantId) {
        this(new DataCloudKernelLifecycleTruthSource(dataCloudClient, tenantId));
    }

    /**
     * Constructs a new KernelHealthSnapshotService from a local manifest ingest service.
     *
     * <p>This constructor is retained for compatibility with existing tests and
     * local-dev wiring. Production code should prefer the truth-source constructor.
     *
     * @param ingestService local filesystem ingest service
     */
    @Deprecated(since = "2026-05", forRemoval = false)
    public KernelHealthSnapshotService(@NotNull KernelLifecycleEventIngestService ingestService) {
        this(LocalKernelManifestTruthSource.forLocalDevelopment(ingestService));
    }

    /**
     * Gets the health view for a specific ProductUnit.
     *
     * @param productUnitId the ProductUnit ID to get health for
     * @return promise resolving to the ProductUnit health view
     */
    public Promise<ProductUnitHealthView> getProductUnitHealth(@NotNull String productUnitId) {
        return truthSource.getProductUnitLifecycleData(productUnitId)
                .map(this::buildHealthView);
    }

    /**
     * Lists health summaries for all ProductUnits.
     *
     * @return promise resolving to a list of ProductUnit health summaries
     */
    public Promise<List<ProductUnitHealthSummary>> listProductUnitHealth() {
        return truthSource.listAllProductUnitLifecycleData()
                .map(this::buildHealthSummaries);
    }

    /**
     * Gets the lifecycle timeline view for a specific ProductUnit.
     *
     * @param productUnitId the ProductUnit ID to get timeline for
     * @return promise resolving to the lifecycle timeline view
     */
    public Promise<LifecycleTimelineView> getLifecycleTimeline(@NotNull String productUnitId) {
        return truthSource.getProductUnitLifecycleData(productUnitId)
                .map(this::buildTimelineView);
    }

    private ProductUnitHealthView buildHealthView(Map<String, Object> lifecycleData) {
        String productUnitId = (String) lifecycleData.get("productUnitId");
        String status = (String) lifecycleData.get("status");

        @SuppressWarnings("unchecked")
        Map<String, Object> healthSnapshot = (Map<String, Object>) lifecycleData.get("healthSnapshot");

        @SuppressWarnings("unchecked")
        Map<String, Object> lifecycleResult = (Map<String, Object>) lifecycleData.get("lifecycleResult");

        @SuppressWarnings("unchecked")
        Map<String, Object> deployment = (Map<String, Object>) lifecycleData.get("deployment");

        return new ProductUnitHealthView(
                productUnitId,
                inferOverallStatus(healthSnapshot, lifecycleResult),
                extractCurrentPhase(lifecycleResult),
                extractLastRunTimestamp(lifecycleResult),
                extractGateFailureCount(lifecycleData, lifecycleResult),
                extractDeploymentStatus(deployment),
                healthSnapshot,
                lifecycleResult,
                deployment
        );
    }

    private List<ProductUnitHealthSummary> buildHealthSummaries(List<Map<String, Object>> allLifecycleData) {
        List<ProductUnitHealthSummary> summaries = new ArrayList<>();

        for (Map<String, Object> data : allLifecycleData) {
            ProductUnitHealthView view = buildHealthView(data);
            summaries.add(new ProductUnitHealthSummary(
                    view.productUnitId(),
                    view.overallStatus(),
                    view.currentPhase(),
                    view.lastRunTimestamp()
            ));
        }

        return summaries;
    }

    private LifecycleTimelineView buildTimelineView(Map<String, Object> lifecycleData) {
        String productUnitId = (String) lifecycleData.get("productUnitId");

        @SuppressWarnings("unchecked")
        Map<String, Object> lifecycleResult = (Map<String, Object>) lifecycleData.get("lifecycleResult");

        List<LifecycleRunSummary> runs = extractLifecycleRuns(lifecycleResult);

        return new LifecycleTimelineView(productUnitId, runs);
    }

    private String inferOverallStatus(Map<String, Object> healthSnapshot, Map<String, Object> lifecycleResult) {
        if (healthSnapshot != null && healthSnapshot.containsKey("status")) {
            return (String) healthSnapshot.get("status");
        }

        if (lifecycleResult != null && lifecycleResult.containsKey("status")) {
            String resultStatus = (String) lifecycleResult.get("status");
            if ("succeeded".equals(resultStatus)) {
                return "healthy";
            } else if ("failed".equals(resultStatus)) {
                return "failed";
            } else if ("running".equals(resultStatus)) {
                return "in_progress";
            }
        }

        return "unknown";
    }

    private String extractCurrentPhase(Map<String, Object> lifecycleResult) {
        if (lifecycleResult == null) {
            return "unknown";
        }

        Object currentPhase = lifecycleResult.get("currentPhase");
        return currentPhase != null ? currentPhase.toString() : "unknown";
    }

    private String extractLastRunTimestamp(Map<String, Object> lifecycleResult) {
        if (lifecycleResult == null) {
            return Instant.now().toString();
        }

        Object timestamp = lifecycleResult.get("timestamp");
        return timestamp != null ? timestamp.toString() : Instant.now().toString();
    }

    private int extractGateFailureCount(Map<String, Object> lifecycleData, Map<String, Object> lifecycleResult) {
        @SuppressWarnings("unchecked")
        Map<String, Object> gates = (Map<String, Object>) lifecycleData.get("gates");

        if (gates == null && lifecycleResult != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> lifecycleGates = (Map<String, Object>) lifecycleResult.get("gates");
            gates = lifecycleGates;
        }

        if (gates == null) {
            return 0;
        }

        Object failedCount = gates.get("failedCount");
        return failedCount instanceof Number ? ((Number) failedCount).intValue() : 0;
    }

    private String extractDeploymentStatus(Map<String, Object> deployment) {
        if (deployment == null) {
            return "not_deployed";
        }

        Object status = deployment.get("status");
        return status != null ? status.toString() : "unknown";
    }

    private List<LifecycleRunSummary> extractLifecycleRuns(Map<String, Object> lifecycleResult) {
        List<LifecycleRunSummary> runs = new ArrayList<>();

        if (lifecycleResult == null) {
            return runs;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phases = (List<Map<String, Object>>) lifecycleResult.get("phases");

        if (phases != null) {
            for (Map<String, Object> phase : phases) {
                String phaseName = (String) phase.get("name");
                String phaseStatus = (String) phase.get("status");
                String timestamp = phase.get("timestamp") != null ? phase.get("timestamp").toString() : null;

                runs.add(new LifecycleRunSummary(phaseName, phaseStatus, timestamp));
            }
        }

        return runs;
    }

    /**
     * Health view for a single ProductUnit.
     */
    public static final class ProductUnitHealthView {
        private final String productUnitId;
        private final String overallStatus;
        private final String currentPhase;
        private final String lastRunTimestamp;
        private final int gateFailureCount;
        private final String deploymentStatus;
        private final Map<String, Object> healthSnapshot;
        private final Map<String, Object> lifecycleResult;
        private final Map<String, Object> deployment;

        public ProductUnitHealthView(
                String productUnitId,
                String overallStatus,
                String currentPhase,
                String lastRunTimestamp,
                int gateFailureCount,
                String deploymentStatus,
                Map<String, Object> healthSnapshot,
                Map<String, Object> lifecycleResult,
                Map<String, Object> deployment) {
            this.productUnitId = productUnitId;
            this.overallStatus = overallStatus;
            this.currentPhase = currentPhase;
            this.lastRunTimestamp = lastRunTimestamp;
            this.gateFailureCount = gateFailureCount;
            this.deploymentStatus = deploymentStatus;
            this.healthSnapshot = healthSnapshot;
            this.lifecycleResult = lifecycleResult;
            this.deployment = deployment;
        }

        public String productUnitId() { return productUnitId; }
        public String overallStatus() { return overallStatus; }
        public String currentPhase() { return currentPhase; }
        public String lastRunTimestamp() { return lastRunTimestamp; }
        public int gateFailureCount() { return gateFailureCount; }
        public String deploymentStatus() { return deploymentStatus; }
        public Map<String, Object> healthSnapshot() { return healthSnapshot; }
        public Map<String, Object> lifecycleResult() { return lifecycleResult; }
        public Map<String, Object> deployment() { return deployment; }
    }

    /**
     * Summary of ProductUnit health for list views.
     */
    public static final class ProductUnitHealthSummary {
        private final String productUnitId;
        private final String overallStatus;
        private final String currentPhase;
        private final String lastRunTimestamp;

        public ProductUnitHealthSummary(String productUnitId, String overallStatus, String currentPhase, String lastRunTimestamp) {
            this.productUnitId = productUnitId;
            this.overallStatus = overallStatus;
            this.currentPhase = currentPhase;
            this.lastRunTimestamp = lastRunTimestamp;
        }

        public String productUnitId() { return productUnitId; }
        public String overallStatus() { return overallStatus; }
        public String currentPhase() { return currentPhase; }
        public String lastRunTimestamp() { return lastRunTimestamp; }
    }

    /**
     * Lifecycle timeline view with phase-by-phase status.
     */
    public static final class LifecycleTimelineView {
        private final String productUnitId;
        private final List<LifecycleRunSummary> runs;

        public LifecycleTimelineView(String productUnitId, List<LifecycleRunSummary> runs) {
            this.productUnitId = productUnitId;
            this.runs = List.copyOf(runs);
        }

        public String productUnitId() { return productUnitId; }
        public List<LifecycleRunSummary> runs() { return runs; }
    }

    /**
     * Summary of a single lifecycle run/phase.
     */
    public static final class LifecycleRunSummary {
        private final String phase;
        private final String status;
        private final String timestamp;

        public LifecycleRunSummary(String phase, String status, String timestamp) {
            this.phase = phase;
            this.status = status;
            this.timestamp = timestamp;
        }

        public String phase() { return phase; }
        public String status() { return status; }
        public String timestamp() { return timestamp; }
    }
}
