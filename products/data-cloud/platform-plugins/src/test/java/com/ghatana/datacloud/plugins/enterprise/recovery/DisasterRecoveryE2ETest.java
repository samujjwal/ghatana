package com.ghatana.datacloud.plugins.enterprise.recovery;

import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.DRMetrics;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.FailoverResult;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.FailoverTestResult;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.HealthStatus;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.RecoveryPoint;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.RecoveryResult;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.RegionConfig;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.RegionRole;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.RegionStatus;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.ReplicationHealthReport;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.ReplicationState;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.ReplicationStatus;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.Runbook;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end disaster recovery validation tests for {@link DisasterRecoveryManager}.
 *
 * <p>These tests exercise the complete DR procedure as it would occur during an actual
 * incident: region setup → replication → snapshot creation → health check → failover
 * pre-flight → failover execution → PITR recovery → metric validation → runbook audit.
 *
 * <p>All methods use {@code runPromise()} from {@link EventloopTestBase} consistent with
 * the ActiveJ async contract. No blocking I/O is performed.
 *
 * @doc.type test
 * @doc.purpose End-to-end validation of the complete DR procedure (backup, failover, PITR, runbooks, metrics)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Data Cloud — Disaster Recovery End-to-End Validation")
class DisasterRecoveryE2ETest extends EventloopTestBase {

    // ── Region identifiers ────────────────────────────────────────────────────
    private static final String PRIMARY_REGION = "us-east-1";
    private static final String STANDBY_REGION = "eu-west-1";

    // ── Dataset identifiers ───────────────────────────────────────────────────
    private static final String CRITICAL_DATASET = "tenant-acme:events-prod";
    private static final String ANALYTICS_DATASET = "tenant-acme:analytics-prod";

    private DisasterRecoveryManager drManager;
    private RegionConfig primaryConfig;
    private RegionConfig standbyConfig;

    @BeforeEach
    void setUp() {
        drManager = new DisasterRecoveryManager();

        primaryConfig = RegionConfig.builder()
                .regionId(PRIMARY_REGION)
                .displayName("US East (Primary)")
                .role(RegionRole.PRIMARY)
                .status(RegionStatus.HEALTHY)
                .endpoint("https://us-east-1.datacloud.ghatana.internal")
                .build();

        standbyConfig = RegionConfig.builder()
                .regionId(STANDBY_REGION)
                .displayName("EU West (Standby)")
                .role(RegionRole.STANDBY)
                .status(RegionStatus.HEALTHY)
                .endpoint("https://eu-west-1.datacloud.ghatana.internal")
                .build();
    }

    // =========================================================================
    // SCENARIO 1 — Complete failover from primary outage to standby recovery
    // =========================================================================

    /**
     * Full DR scenario:
     * 1. Register primary + standby regions
     * 2. Start replication for critical datasets
     * 3. Create periodic recovery point snapshots
     * 4. Verify healthy replication before incident
     * 5. Run pre-failover readiness test
     * 6. Execute failover to standby region
     * 7. Confirm RTO target is met
     * 8. Perform PITR recovery on the critical dataset
     * 9. Assert recovery integrity (source, target, recovery point reference)
     */
    @Nested
    @DisplayName("Scenario 1 — Primary Region Failure and Standby Promotion")
    class PrimaryFailoverScenario {

        @Test
        @DisplayName("should execute the complete DR procedure within RTO/RPO targets")
        void shouldExecuteCompleteDrProcedure() {
            // ── Step 1: Register regions ───────────────────────────────────────────
            RegionConfig registeredPrimary = runPromise(() ->
                    drManager.registerRegion(PRIMARY_REGION, primaryConfig));
            RegionConfig registeredStandby = runPromise(() ->
                    drManager.registerRegion(STANDBY_REGION, standbyConfig));

            assertThat(registeredPrimary.getRegionId()).isEqualTo(PRIMARY_REGION);
            assertThat(registeredStandby.getRegionId()).isEqualTo(STANDBY_REGION);

            // ── Step 2: Set primary and start replication ─────────────────────────
            runPromise(() -> drManager.setPrimaryRegion(PRIMARY_REGION));

            ReplicationStatus criticalReplication = runPromise(() ->
                    drManager.startReplication(CRITICAL_DATASET, PRIMARY_REGION, List.of(STANDBY_REGION)));
            ReplicationStatus analyticsReplication = runPromise(() ->
                    drManager.startReplication(ANALYTICS_DATASET, PRIMARY_REGION, List.of(STANDBY_REGION)));

            assertThat(criticalReplication.getState()).isEqualTo(ReplicationState.ACTIVE);
            assertThat(analyticsReplication.getState()).isEqualTo(ReplicationState.ACTIVE);

            // ── Step 3: Create periodic recovery point snapshots ──────────────────
            Instant preSnapshotTime = Instant.now();

            RecoveryPoint snapshot1 = runPromise(() ->
                    drManager.createRecoveryPoint(CRITICAL_DATASET, "hourly-snapshot-T0"));
            RecoveryPoint snapshot2 = runPromise(() ->
                    drManager.createRecoveryPoint(CRITICAL_DATASET, "hourly-snapshot-T1"));
            RecoveryPoint analyticsSnapshot = runPromise(() ->
                    drManager.createRecoveryPoint(ANALYTICS_DATASET, "daily-snapshot-T0"));

            assertThat(snapshot1.getPointId()).isNotBlank();
            assertThat(snapshot2.getPointId()).isNotBlank();
            assertThat(analyticsSnapshot.getPointId()).isNotBlank();
            assertThat(snapshot1.getDatasetId()).isEqualTo(CRITICAL_DATASET);

            List<RecoveryPoint> criticalPoints = runPromise(() ->
                    drManager.listRecoveryPoints(CRITICAL_DATASET));
            assertThat(criticalPoints).hasSize(2);

            // ── Step 4: Verify healthy replication state before incident ──────────
            ReplicationHealthReport preIncidentHealth = runPromise(() ->
                    drManager.checkReplicationHealth());

            assertThat(preIncidentHealth.getTotalDatasets()).isGreaterThanOrEqualTo(2);
            assertThat(preIncidentHealth.getOverallHealth())
                    .as("Replication must be HEALTHY or DEGRADED (not CRITICAL) before failover")
                    .isNotEqualTo(HealthStatus.CRITICAL);

            // ── Step 5: Pre-failover readiness test ───────────────────────────────
            FailoverTestResult readinessTest = runPromise(() ->
                    drManager.testFailover(STANDBY_REGION));

            assertThat(readinessTest.isPassed())
                    .as("Failover readiness test must pass before executing actual failover")
                    .isTrue();
            assertThat(readinessTest.getTargetRegion()).isEqualTo(STANDBY_REGION);
            assertThat(readinessTest.getChecks()).isNotEmpty();

            // ── Step 6: Execute failover to standby ───────────────────────────────
            FailoverResult failover = runPromise(() ->
                    drManager.initiateFailover("primary-hardware-failure", STANDBY_REGION));

            assertThat(failover.isSuccess())
                    .as("Failover must succeed")
                    .isTrue();
            assertThat(failover.getToRegion())
                    .as("Failover target must be the standby region")
                    .isEqualTo(STANDBY_REGION);
            assertThat(failover.getFromRegion())
                    .as("Failover source must be the former primary region")
                    .isEqualTo(PRIMARY_REGION);
            assertThat(failover.getSteps())
                    .as("Failover must record all execution steps")
                    .isNotEmpty();

            // ── Step 7: Validate RTO target ───────────────────────────────────────
            assertThat(failover.isMetRTOTarget())
                    .as("Failover must complete within the RTO target of %d minutes",
                            DisasterRecoveryManager.TARGET_RTO_MINUTES)
                    .isTrue();

            // ── Step 8: Perform PITR to recover critical dataset ──────────────────
            Instant recoveryTargetTime = preSnapshotTime.plusSeconds(120);
            RecoveryResult pitrResult = runPromise(() ->
                    drManager.performPITR(
                            CRITICAL_DATASET,
                            recoveryTargetTime,
                            CRITICAL_DATASET + ":recovered"));

            assertThat(pitrResult.isSuccess())
                    .as("PITR must succeed after recovery points exist")
                    .isTrue();
            assertThat(pitrResult.getSourceDatasetId()).isEqualTo(CRITICAL_DATASET);
            assertThat(pitrResult.getTargetDatasetId()).isEqualTo(CRITICAL_DATASET + ":recovered");
            assertThat(pitrResult.getRecoveryPointUsed())
                    .as("PITR must reference a concrete recovery point")
                    .isNotBlank();
            assertThat(pitrResult.getRecoveryDuration())
                    .as("Recovery duration must be reported")
                    .isNotNull();
        }
    }

    // =========================================================================
    // SCENARIO 2 — Multi-dataset replication health monitoring
    // =========================================================================

    @Nested
    @DisplayName("Scenario 2 — Multi-Dataset Replication Health")
    class MultiDatasetReplicationScenario {

        @Test
        @DisplayName("should track replication health across multiple datasets")
        void shouldTrackReplicationHealthAcrossDatasets() {
            runPromise(() -> drManager.registerRegion(PRIMARY_REGION, primaryConfig));
            runPromise(() -> drManager.registerRegion(STANDBY_REGION, standbyConfig));

            String[] datasets = { "tenant-a:events", "tenant-b:events", "tenant-c:analytics" };
            for (String ds : datasets) {
                runPromise(() -> drManager.startReplication(ds, PRIMARY_REGION, List.of(STANDBY_REGION)));
            }

            ReplicationHealthReport report = runPromise(() -> drManager.checkReplicationHealth());

            assertThat(report.getTotalDatasets()).isGreaterThanOrEqualTo(datasets.length);
            assertThat(report.getHealthyDatasets()).isGreaterThanOrEqualTo(datasets.length);
            assertThat(report.getFailedDatasets()).isZero();
            assertThat(report.getOverallHealth())
                    .as("All freshly started replications must be healthy")
                    .isEqualTo(HealthStatus.HEALTHY);
        }
    }

    // =========================================================================
    // SCENARIO 3 — Recovery point progression and PITR coverage
    // =========================================================================

    @Nested
    @DisplayName("Scenario 3 — Recovery Point Coverage Validates RPO")
    class RecoveryPointCoverageScenario {

        @Test
        @DisplayName("should maintain sufficient recovery points to meet RPO target")
        void shouldMaintainSufficientRecoveryPointsForRPO() {
            int expectedSnapshots = 3;
            for (int i = 0; i < expectedSnapshots; i++) {
                runPromise(() -> drManager.createRecoveryPoint(CRITICAL_DATASET, "rpo-snap"));
            }

            List<RecoveryPoint> points = runPromise(() ->
                    drManager.listRecoveryPoints(CRITICAL_DATASET));

            assertThat(points).hasSize(expectedSnapshots);

            // All points must reference the correct dataset
            assertThat(points)
                    .allSatisfy(p -> assertThat(p.getDatasetId()).isEqualTo(CRITICAL_DATASET));

            // All points must have unique IDs
            long uniqueIds = points.stream().map(RecoveryPoint::getPointId).distinct().count();
            assertThat(uniqueIds)
                    .as("Each recovery point must have a unique identifier")
                    .isEqualTo(expectedSnapshots);

            // PITR with a target time after all snapshots should succeed
            Instant targetTime = Instant.now().plusSeconds(60);
            RecoveryResult result = runPromise(() ->
                    drManager.performPITR(CRITICAL_DATASET, targetTime, "rpo-validated-target"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDataLossMinutes())
                    .as("Data loss estimate must be within RPO target of %d minutes",
                            DisasterRecoveryManager.TARGET_RPO_MINUTES)
                    .isLessThanOrEqualTo(DisasterRecoveryManager.TARGET_RPO_MINUTES);
        }
    }

    // =========================================================================
    // SCENARIO 4 — DR metrics reflect post-failover state
    // =========================================================================

    @Nested
    @DisplayName("Scenario 4 — DR Metrics Post-Failover")
    class DRMetricsScenario {

        @Test
        @DisplayName("should expose DR metrics that reflect the post-failover state")
        void shouldExposeDRMetricsAfterFailover() {
            runPromise(() -> drManager.registerRegion(PRIMARY_REGION, primaryConfig));
            runPromise(() -> drManager.registerRegion(STANDBY_REGION, standbyConfig));
            runPromise(() -> drManager.startReplication(CRITICAL_DATASET, PRIMARY_REGION, List.of(STANDBY_REGION)));
            runPromise(() -> drManager.initiateFailover("test-failover", STANDBY_REGION));

            DRMetrics metrics = runPromise(() -> drManager.getDRMetrics());

            assertThat(metrics).isNotNull();
            assertThat(metrics.getTotalDatasetsProtected())
                    .as("DR metrics must count replicated datasets")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    // =========================================================================
    // SCENARIO 5 — Runbook availability for incident response
    // =========================================================================

    @Nested
    @DisplayName("Scenario 5 — Runbook Availability for Incident Response")
    class RunbookAvailabilityScenario {

        @Test
        @DisplayName("should provide pre-populated runbooks for known failure scenarios")
        void shouldProvideRunbooksForKnownFailureScenarios() {
            List<Runbook> runbooks = runPromise(() -> drManager.listRunbooks());

            assertThat(runbooks)
                    .as("At least one pre-populated DR runbook must exist")
                    .isNotEmpty();

            // All runbooks must have identifiable metadata
            assertThat(runbooks)
                    .allSatisfy(rb -> {
                        assertThat(rb.getRunbookId()).isNotBlank();
                        assertThat(rb.getName()).isNotBlank();
                    });
        }

        @Test
        @DisplayName("should retrieve a specific runbook by ID")
        void shouldRetrieveSpecificRunbookById() {
            List<Runbook> runbooks = runPromise(() -> drManager.listRunbooks());
            assertThat(runbooks).isNotEmpty();

            String firstRunbookId = runbooks.get(0).getRunbookId();

            Runbook retrieved = runPromise(() -> drManager.getRunbook(firstRunbookId));

            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getRunbookId()).isEqualTo(firstRunbookId);
            assertThat(retrieved.getName()).isNotBlank();
        }
    }
}
