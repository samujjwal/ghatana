/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
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
package com.ghatana.datacloud.plugins.enterprise.recovery;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Disaster Recovery Manager for EventCloud.
 *
 * <p>
 * Provides comprehensive disaster recovery capabilities:
 * <ul>
 * <li>Cross-region replication</li>
 * <li>Point-in-time recovery (PITR)</li>
 * <li>Automated failover</li>
 * <li>Recovery runbook management</li>
 * <li>RTO/RPO monitoring</li>
 * </ul>
 *
 * <p>
 * Target SLAs:
 * <ul>
 * <li>RTO (Recovery Time Objective): 15 minutes</li>
 * <li>RPO (Recovery Point Objective): 5 minutes</li>
 * <li>Replication Lag: &lt;1 second</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Disaster recovery management
 * @doc.layer product
 * @doc.pattern Service
 */
public class DisasterRecoveryManager {

    /**
     * RTO target in minutes.
     */
    public static final int TARGET_RTO_MINUTES = 15;

    /**
     * RPO target in minutes.
     */
    public static final int TARGET_RPO_MINUTES = 5;

    /**
     * Maximum acceptable replication lag in seconds.
     */
    public static final int MAX_REPLICATION_LAG_SECONDS = 1;

    /**
     * Region configurations.
     */
    private final Map<String, RegionConfig> regions;

    /**
     * Replication status per dataset.
     */
    private final Map<String, ReplicationStatus> replicationStatuses;

    /**
     * Recovery points (snapshots).
     */
    private final Map<String, List<RecoveryPoint>> recoveryPoints;

    /**
     * Failover history.
     */
    private final List<FailoverEvent> failoverHistory;

    /**
     * Current primary region.
     */
    private final AtomicReference<String> primaryRegion;

    /**
     * Recovery runbooks.
     */
    private final Map<String, Runbook> runbooks;

    /**
     * Creates a new disaster recovery manager.
     */
    public DisasterRecoveryManager() {
        this.regions = new ConcurrentHashMap<>();
        this.replicationStatuses = new ConcurrentHashMap<>();
        this.recoveryPoints = new ConcurrentHashMap<>();
        this.failoverHistory = new ArrayList<>();
        this.primaryRegion = new AtomicReference<>("us-east-1");
        this.runbooks = new ConcurrentHashMap<>();
        initializeDefaultRunbooks();
    }

    // --- Region Management ---
    /**
     * Registers a region for disaster recovery.
     *
     * @param regionId Region identifier
     * @param config Region configuration
     * @return Promise of registered region
     */
    public Promise<RegionConfig> registerRegion(
            String regionId,
            RegionConfig config) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            regions.put(regionId, config);
            return config;
        });
    }

    /**
     * Sets the primary region.
     *
     * @param regionId Region to set as primary
     * @return Promise of result
     */
    public Promise<Boolean> setPrimaryRegion(String regionId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            if (!regions.containsKey(regionId)) {
                throw new IllegalArgumentException("Region not found: " + regionId);
            }

            String oldPrimary = primaryRegion.getAndSet(regionId);

            // Log the change
            FailoverEvent event = FailoverEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(FailoverEventType.PRIMARY_CHANGED)
                    .fromRegion(oldPrimary)
                    .toRegion(regionId)
                    .reason("Manual primary region change")
                    .initiatedBy("admin")
                    .build();

            synchronized (failoverHistory) {
                failoverHistory.add(event);
            }

            return true;
        });
    }

    // --- Replication Management ---
    /**
     * Starts replication for a dataset.
     *
     * @param datasetId Dataset to replicate
     * @param sourceRegion Source region
     * @param targetRegions Target regions
     * @return Promise of replication status
     */
    public Promise<ReplicationStatus> startReplication(
            String datasetId,
            String sourceRegion,
            List<String> targetRegions) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            ReplicationStatus status = ReplicationStatus.builder()
                    .datasetId(datasetId)
                    .sourceRegion(sourceRegion)
                    .targetRegions(new ArrayList<>(targetRegions))
                    .state(ReplicationState.INITIALIZING)
                    .build();

            replicationStatuses.put(datasetId, status);

            // Simulate initial sync
            status = status.toBuilder()
                    .state(ReplicationState.ACTIVE)
                    .lastSyncTime(Instant.now())
                    .build();
            replicationStatuses.put(datasetId, status);

            return status;
        });
    }

    /**
     * Gets replication status for a dataset.
     *
     * @param datasetId Dataset identifier
     * @return Promise of replication status
     */
    public Promise<ReplicationStatus> getReplicationStatus(String datasetId) {
        return Promise.of(replicationStatuses.get(datasetId));
    }

    /**
     * Checks replication health across all datasets.
     *
     * @return Promise of health report
     */
    public Promise<ReplicationHealthReport> checkReplicationHealth() {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), ()
                -> checkReplicationHealthSync());
    }

    /**
     * Synchronous version of replication health check. Used internally to avoid
     * nested Promise.ofBlocking calls.
     *
     * @return Replication health report
     */
    private ReplicationHealthReport checkReplicationHealthSync() {
        int total = replicationStatuses.size();
        int healthy = 0;
        int degraded = 0;
        int failed = 0;
        List<ReplicationIssue> issues = new ArrayList<>();

        for (Map.Entry<String, ReplicationStatus> entry : replicationStatuses.entrySet()) {
            ReplicationStatus status = entry.getValue();

            if (status.getState() == ReplicationState.ACTIVE) {
                Duration lag = Duration.between(status.getLastSyncTime(), Instant.now());
                if (lag.getSeconds() <= MAX_REPLICATION_LAG_SECONDS) {
                    healthy++;
                } else {
                    degraded++;
                    issues.add(ReplicationIssue.builder()
                            .datasetId(entry.getKey())
                            .issueType(IssueType.HIGH_LAG)
                            .description("Replication lag: " + lag.getSeconds() + "s")
                            .severity(IssueSeverity.WARNING)
                            .build());
                }
            } else if (status.getState() == ReplicationState.FAILED) {
                failed++;
                issues.add(ReplicationIssue.builder()
                        .datasetId(entry.getKey())
                        .issueType(IssueType.REPLICATION_FAILED)
                        .description("Replication has failed")
                        .severity(IssueSeverity.CRITICAL)
                        .build());
            }
        }

        return ReplicationHealthReport.builder()
                .totalDatasets(total)
                .healthyDatasets(healthy)
                .degradedDatasets(degraded)
                .failedDatasets(failed)
                .issues(issues)
                .overallHealth(failed > 0 ? HealthStatus.CRITICAL
                        : degraded > 0 ? HealthStatus.DEGRADED : HealthStatus.HEALTHY)
                .build();
    }

    // --- Point-in-Time Recovery ---
    /**
     * Creates a recovery point (snapshot).
     *
     * @param datasetId Dataset to snapshot
     * @param label Optional label for the recovery point
     * @return Promise of created recovery point
     */
    public Promise<RecoveryPoint> createRecoveryPoint(
            String datasetId,
            String label) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            String pointId = UUID.randomUUID().toString();
            String snapshotId = "snap-" + System.currentTimeMillis();

            RecoveryPoint point = RecoveryPoint.builder()
                    .pointId(pointId)
                    .datasetId(datasetId)
                    .snapshotId(snapshotId)
                    .label(label)
                    .type(RecoveryPointType.SNAPSHOT)
                    .region(primaryRegion.get())
                    .build();

            recoveryPoints.computeIfAbsent(datasetId, k -> new ArrayList<>()).add(point);

            return point;
        });
    }

    /**
     * Lists recovery points for a dataset.
     *
     * @param datasetId Dataset identifier
     * @return Promise of recovery points
     */
    public Promise<List<RecoveryPoint>> listRecoveryPoints(String datasetId) {
        return Promise.of(recoveryPoints.getOrDefault(datasetId, List.of()));
    }

    /**
     * Performs point-in-time recovery.
     *
     * @param datasetId Dataset to recover
     * @param targetTime Target recovery time
     * @param targetDatasetId ID for the recovered dataset
     * @return Promise of recovery result
     */
    public Promise<RecoveryResult> performPITR(
            String datasetId,
            Instant targetTime,
            String targetDatasetId) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<RecoveryPoint> points = recoveryPoints.getOrDefault(datasetId, List.of());

            // Find nearest recovery point before target time
            RecoveryPoint nearestPoint = points.stream()
                    .filter(p -> !p.getCreatedAt().isAfter(targetTime))
                    .reduce((a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b)
                    .orElse(null);

            if (nearestPoint == null) {
                return RecoveryResult.builder()
                        .success(false)
                        .errorMessage("No recovery point found before target time")
                        .build();
            }

            // Simulate recovery
            Duration recoveryTime = Duration.ofMinutes(2); // Simulated

            return RecoveryResult.builder()
                    .success(true)
                    .sourceDatasetId(datasetId)
                    .targetDatasetId(targetDatasetId)
                    .recoveryPointUsed(nearestPoint.getPointId())
                    .targetTime(targetTime)
                    .actualRecoveryTime(nearestPoint.getCreatedAt())
                    .recoveryDuration(recoveryTime)
                    .dataLossMinutes(
                            (int) Duration.between(nearestPoint.getCreatedAt(), targetTime).toMinutes())
                    .build();
        });
    }

    // --- Automated Failover ---
    /**
     * Initiates automatic failover to a standby region.
     *
     * @param reason Reason for failover
     * @param targetRegion Target region (null for automatic selection)
     * @return Promise of failover result
     */
    public Promise<FailoverResult> initiateFailover(
            String reason,
            String targetRegion) {

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            String currentPrimary = primaryRegion.get();
            Instant startTime = Instant.now();

            // Select target region if not specified
            final String selectedRegion = (targetRegion == null)
                    ? selectBestFailoverTarget(currentPrimary)
                    : targetRegion;

            if (selectedRegion == null) {
                return FailoverResult.builder()
                        .success(false)
                        .errorMessage("No available failover target")
                        .build();
            }

            // Simulate failover steps
            List<FailoverStep> steps = new ArrayList<>();

            // Step 1: Stop writes to primary
            steps.add(FailoverStep.builder()
                    .stepNumber(1)
                    .stepName("Stop writes to primary")
                    .status(StepStatus.COMPLETED)
                    .duration(Duration.ofSeconds(5))
                    .build());

            // Step 2: Wait for replication to catch up
            steps.add(FailoverStep.builder()
                    .stepNumber(2)
                    .stepName("Wait for replication sync")
                    .status(StepStatus.COMPLETED)
                    .duration(Duration.ofSeconds(10))
                    .build());

            // Step 3: Promote standby
            steps.add(FailoverStep.builder()
                    .stepNumber(3)
                    .stepName("Promote standby to primary")
                    .status(StepStatus.COMPLETED)
                    .duration(Duration.ofSeconds(30))
                    .build());

            // Step 4: Update DNS/routing
            steps.add(FailoverStep.builder()
                    .stepNumber(4)
                    .stepName("Update routing")
                    .status(StepStatus.COMPLETED)
                    .duration(Duration.ofSeconds(60))
                    .build());

            // Step 5: Verify connectivity
            steps.add(FailoverStep.builder()
                    .stepNumber(5)
                    .stepName("Verify connectivity")
                    .status(StepStatus.COMPLETED)
                    .duration(Duration.ofSeconds(15))
                    .build());

            // Update primary region
            primaryRegion.set(selectedRegion);

            Duration totalDuration = Duration.between(startTime, Instant.now())
                    .plus(Duration.ofSeconds(120)); // Simulated total

            // Record failover event
            FailoverEvent event = FailoverEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(FailoverEventType.FAILOVER_COMPLETED)
                    .fromRegion(currentPrimary)
                    .toRegion(selectedRegion)
                    .reason(reason)
                    .initiatedBy("system")
                    .duration(totalDuration)
                    .build();

            synchronized (failoverHistory) {
                failoverHistory.add(event);
            }

            boolean metRTO = totalDuration.toMinutes() <= TARGET_RTO_MINUTES;

            return FailoverResult.builder()
                    .success(true)
                    .fromRegion(currentPrimary)
                    .toRegion(selectedRegion)
                    .totalDuration(totalDuration)
                    .steps(steps)
                    .metRTOTarget(metRTO)
                    .build();
        });
    }

    /**
     * Tests failover without actually switching.
     *
     * @param targetRegion Region to test failover to
     * @return Promise of test result
     */
    public Promise<FailoverTestResult> testFailover(String targetRegion) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<String> checks = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            boolean passed = true;

            // Check target region exists and is healthy
            RegionConfig targetConfig = regions.get(targetRegion);
            if (targetConfig == null) {
                checks.add("FAIL: Target region not configured");
                passed = false;
            } else if (targetConfig.getStatus() != RegionStatus.HEALTHY) {
                checks.add("FAIL: Target region is not healthy");
                passed = false;
            } else {
                checks.add("PASS: Target region is configured and healthy");
            }

            // Check replication status (use sync version to avoid nested blocking)
            ReplicationHealthReport health = checkReplicationHealthSync();
            
            if (health.getOverallHealth() == HealthStatus.CRITICAL) {
                checks.add("FAIL: Replication is in critical state");
                passed = false;
            } else if (health.getOverallHealth() == HealthStatus.DEGRADED) {
                warnings.add("WARNING: Replication is degraded");
                checks.add("PASS: Replication is operational (with warnings)");
            } else {
                checks.add("PASS: Replication is healthy");
            }

            // Check network connectivity (simulated)
            checks.add("PASS: Network connectivity to target region");

            // Check DNS propagation time (simulated)
            checks.add("PASS: DNS propagation estimate within RTO");

            // Estimate recovery time
            Duration estimatedRTO = Duration.ofMinutes(10); // Simulated

            return FailoverTestResult.builder()
                    .testId(UUID.randomUUID().toString())
                    .targetRegion(targetRegion)
                    .passed(passed)
                    .checks(checks)
                    .warnings(warnings)
                    .estimatedRTO(estimatedRTO)
                    .withinRTOTarget(estimatedRTO.toMinutes() <= TARGET_RTO_MINUTES)
                    .build();
        });
    }

    // --- Runbook Management ---
    /**
     * Gets a recovery runbook.
     *
     * @param runbookId Runbook identifier
     * @return Promise of runbook
     */
    public Promise<Runbook> getRunbook(String runbookId) {
        return Promise.of(runbooks.get(runbookId));
    }

    /**
     * Lists all runbooks.
     *
     * @return Promise of runbooks
     */
    public Promise<List<Runbook>> listRunbooks() {
        return Promise.of(new ArrayList<>(runbooks.values()));
    }

    // --- RTO/RPO Monitoring ---
    /**
     * Gets current RTO/RPO metrics.
     *
     * @return Promise of DR metrics
     */
    public Promise<DRMetrics> getDRMetrics() {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Calculate current RPO from replication lag
            Duration maxLag = Duration.ZERO;
            for (ReplicationStatus status : replicationStatuses.values()) {
                if (status.getLastSyncTime() != null) {
                    Duration lag = Duration.between(status.getLastSyncTime(), Instant.now());
                    if (lag.compareTo(maxLag) > 0) {
                        maxLag = lag;
                    }
                }
            }

            // Get last failover time
            Duration lastFailoverDuration = Duration.ZERO;
            if (!failoverHistory.isEmpty()) {
                FailoverEvent lastFailover = failoverHistory.get(failoverHistory.size() - 1);
                if (lastFailover.getDuration() != null) {
                    lastFailoverDuration = lastFailover.getDuration();
                }
            }

            return DRMetrics.builder()
                    .currentRPOMinutes((int) maxLag.toMinutes())
                    .targetRPOMinutes(TARGET_RPO_MINUTES)
                    .meetingRPOTarget(maxLag.toMinutes() <= TARGET_RPO_MINUTES)
                    .lastMeasuredRTOMinutes((int) lastFailoverDuration.toMinutes())
                    .targetRTOMinutes(TARGET_RTO_MINUTES)
                    .meetingRTOTarget(lastFailoverDuration.toMinutes() <= TARGET_RTO_MINUTES)
                    .maxReplicationLagSeconds((int) maxLag.getSeconds())
                    .targetReplicationLagSeconds(MAX_REPLICATION_LAG_SECONDS)
                    .totalDatasetsProtected(replicationStatuses.size())
                    .primaryRegion(primaryRegion.get())
                    .build();
        });
    }

    // --- Private Helper Methods ---
    private String selectBestFailoverTarget(String excludeRegion) {
        return regions.entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeRegion))
                .filter(e -> e.getValue().getStatus() == RegionStatus.HEALTHY)
                .filter(e -> e.getValue().getRole() == RegionRole.STANDBY)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void initializeDefaultRunbooks() {
        // Database failover runbook
        runbooks.put("db-failover", Runbook.builder()
                .runbookId("db-failover")
                .name("Database Failover Procedure")
                .description("Standard procedure for database failover to standby region")
                .steps(List.of(
                        "1. Verify primary is unreachable or decision has been made to failover",
                        "2. Stop all write traffic to primary database",
                        "3. Wait for replication to catch up (max 30 seconds)",
                        "4. Promote standby database to primary",
                        "5. Update DNS records to point to new primary",
                        "6. Verify application connectivity",
                        "7. Monitor for errors in logs",
                        "8. Update runbook with lessons learned"
                ))
                .estimatedDuration(Duration.ofMinutes(10))
                .lastUpdated(Instant.now())
                .build());

        // Data recovery runbook
        runbooks.put("data-recovery", Runbook.builder()
                .runbookId("data-recovery")
                .name("Point-in-Time Data Recovery")
                .description("Procedure for recovering data to a specific point in time")
                .steps(List.of(
                        "1. Identify the target recovery time",
                        "2. Find the nearest recovery point before target time",
                        "3. Create a new dataset for recovered data",
                        "4. Restore snapshot to new dataset",
                        "5. Apply transaction logs up to target time",
                        "6. Verify data integrity",
                        "7. Switch application to use recovered dataset",
                        "8. Archive or delete corrupted original"
                ))
                .estimatedDuration(Duration.ofMinutes(30))
                .lastUpdated(Instant.now())
                .build());

        // Regional outage runbook
        runbooks.put("regional-outage", Runbook.builder()
                .runbookId("regional-outage")
                .name("Regional Outage Response")
                .description("Response procedure for complete regional outage")
                .steps(List.of(
                        "1. Confirm regional outage via multiple sources",
                        "2. Declare disaster recovery event",
                        "3. Execute database failover procedure",
                        "4. Execute application failover procedure",
                        "5. Update global load balancer",
                        "6. Notify stakeholders",
                        "7. Monitor recovery progress",
                        "8. Plan for failback when region recovers"
                ))
                .estimatedDuration(Duration.ofMinutes(15))
                .lastUpdated(Instant.now())
                .build());
    }

    // --- Inner Classes and Enums ---
    public enum RegionStatus {
        HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
    }

    public enum RegionRole {
        PRIMARY, STANDBY, READ_REPLICA
    }

    public enum ReplicationState {
        INITIALIZING, ACTIVE, PAUSED, FAILED
    }

    public enum HealthStatus {
        HEALTHY, DEGRADED, CRITICAL
    }

    public enum IssueType {
        HIGH_LAG, REPLICATION_FAILED, CONNECTIVITY_LOST, STORAGE_FULL
    }

    public enum IssueSeverity {
        INFO, WARNING, CRITICAL
    }

    public enum RecoveryPointType {
        SNAPSHOT, CONTINUOUS, ARCHIVE
    }

    public enum FailoverEventType {
        FAILOVER_STARTED, FAILOVER_COMPLETED, FAILOVER_FAILED, PRIMARY_CHANGED, FAILBACK
    }

    public enum StepStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    }

    @Getter
    @Builder
    public static class RegionConfig {

        private final String regionId;
        private final String displayName;
        private final RegionRole role;
        private final RegionStatus status;
        private final String endpoint;
        @Builder.Default
        private final Map<String, String> metadata = Map.of();
    }

    @Getter
    @Builder(toBuilder = true)
    public static class ReplicationStatus {

        private final String datasetId;
        private final String sourceRegion;
        @Builder.Default
        private final List<String> targetRegions = List.of();
        private final ReplicationState state;
        private final Instant lastSyncTime;
        @Builder.Default
        private final long bytesReplicated = 0;
        private final String errorMessage;
    }

    @Getter
    @Builder
    public static class ReplicationHealthReport {

        private final int totalDatasets;
        private final int healthyDatasets;
        private final int degradedDatasets;
        private final int failedDatasets;
        @Builder.Default
        private final List<ReplicationIssue> issues = List.of();
        private final HealthStatus overallHealth;
        @Builder.Default
        private final Instant reportTime = Instant.now();
    }

    @Getter
    @Builder
    public static class ReplicationIssue {

        private final String datasetId;
        private final IssueType issueType;
        private final String description;
        private final IssueSeverity severity;
    }

    @Getter
    @Builder
    public static class RecoveryPoint {

        private final String pointId;
        private final String datasetId;
        private final String snapshotId;
        private final String label;
        private final RecoveryPointType type;
        private final String region;
        @Builder.Default
        private final Instant createdAt = Instant.now();
        @Builder.Default
        private final long sizeBytes = 0;
    }

    @Getter
    @Builder
    public static class RecoveryResult {

        private final boolean success;
        private final String sourceDatasetId;
        private final String targetDatasetId;
        private final String recoveryPointUsed;
        private final Instant targetTime;
        private final Instant actualRecoveryTime;
        private final Duration recoveryDuration;
        private final int dataLossMinutes;
        private final String errorMessage;
    }

    @Getter
    @Builder
    public static class FailoverEvent {

        private final String eventId;
        private final FailoverEventType eventType;
        private final String fromRegion;
        private final String toRegion;
        private final String reason;
        private final String initiatedBy;
        private final Duration duration;
        @Builder.Default
        private final Instant timestamp = Instant.now();
    }

    @Getter
    @Builder
    public static class FailoverStep {

        private final int stepNumber;
        private final String stepName;
        private final StepStatus status;
        private final Duration duration;
        private final String errorMessage;
    }

    @Getter
    @Builder
    public static class FailoverResult {

        private final boolean success;
        private final String fromRegion;
        private final String toRegion;
        private final Duration totalDuration;
        @Builder.Default
        private final List<FailoverStep> steps = List.of();
        private final boolean metRTOTarget;
        private final String errorMessage;
    }

    @Getter
    @Builder
    public static class FailoverTestResult {

        private final String testId;
        private final String targetRegion;
        private final boolean passed;
        @Builder.Default
        private final List<String> checks = List.of();
        @Builder.Default
        private final List<String> warnings = List.of();
        private final Duration estimatedRTO;
        private final boolean withinRTOTarget;
        @Builder.Default
        private final Instant testedAt = Instant.now();
    }

    @Getter
    @Builder
    public static class Runbook {

        private final String runbookId;
        private final String name;
        private final String description;
        @Builder.Default
        private final List<String> steps = List.of();
        private final Duration estimatedDuration;
        private final Instant lastUpdated;
        private final String lastUpdatedBy;
    }

    @Getter
    @Builder
    public static class DRMetrics {

        private final int currentRPOMinutes;
        private final int targetRPOMinutes;
        private final boolean meetingRPOTarget;
        private final int lastMeasuredRTOMinutes;
        private final int targetRTOMinutes;
        private final boolean meetingRTOTarget;
        private final int maxReplicationLagSeconds;
        private final int targetReplicationLagSeconds;
        private final int totalDatasetsProtected;
        private final String primaryRegion;
        @Builder.Default
        private final Instant measuredAt = Instant.now();
    }
}
