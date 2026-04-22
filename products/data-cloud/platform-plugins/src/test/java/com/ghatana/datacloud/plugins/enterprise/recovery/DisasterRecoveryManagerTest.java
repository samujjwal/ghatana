package com.ghatana.datacloud.plugins.enterprise.recovery;

import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.DRMetrics;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.FailoverResult;
import com.ghatana.datacloud.plugins.enterprise.recovery.DisasterRecoveryManager.FailoverTestResult;
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

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DisasterRecoveryManager}.
 *
 * <p>DisasterRecoveryManager is entirely in-memory and has no external dependencies,
 * making it straightforward to test via {@code EventloopTestBase}.
 *
 * @doc.type test
 * @doc.purpose Validate region management, replication, recovery points, failover, runbooks, and DR metrics
 * @doc.layer product
 */
@DisplayName("DisasterRecoveryManager Tests [GH-90000]")
class DisasterRecoveryManagerTest extends EventloopTestBase {

    private DisasterRecoveryManager drManager;
    private RegionConfig primaryConfig;
    private RegionConfig secondaryConfig;

    @BeforeEach
    void setUp() { // GH-90000
        drManager = new DisasterRecoveryManager(); // GH-90000

        primaryConfig = RegionConfig.builder() // GH-90000
                .regionId("us-east-1 [GH-90000]")
                .displayName("US East [GH-90000]")
                .role(RegionRole.PRIMARY) // GH-90000
                .status(RegionStatus.HEALTHY) // GH-90000
                .endpoint("https://us-east-1.example.com [GH-90000]")
                .build(); // GH-90000

        secondaryConfig = RegionConfig.builder() // GH-90000
                .regionId("eu-west-1 [GH-90000]")
                .displayName("EU West [GH-90000]")
                .role(RegionRole.STANDBY) // GH-90000
                .status(RegionStatus.HEALTHY) // GH-90000
                .endpoint("https://eu-west-1.example.com [GH-90000]")
                .build(); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should create manager without errors [GH-90000]")
        void shouldCreateManager() { // GH-90000
            assertThatCode(() -> new DisasterRecoveryManager()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should expose target SLA constants [GH-90000]")
        void shouldExposeTargetSLAConstants() { // GH-90000
            assertThat(DisasterRecoveryManager.TARGET_RTO_MINUTES).isEqualTo(15); // GH-90000
            assertThat(DisasterRecoveryManager.TARGET_RPO_MINUTES).isEqualTo(5); // GH-90000
            assertThat(DisasterRecoveryManager.MAX_REPLICATION_LAG_SECONDS).isEqualTo(1); // GH-90000
        }
    }

    // =========================================================================
    // REGION MANAGEMENT
    // =========================================================================

    @Nested
    @DisplayName("registerRegion and setPrimaryRegion [GH-90000]")
    class RegionManagement {

        @Test
        @DisplayName("should register a region and return its config [GH-90000]")
        void shouldRegisterRegion() { // GH-90000
            RegionConfig result = runPromise(() -> // GH-90000
                    drManager.registerRegion("us-east-1", primaryConfig)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getRegionId()).isEqualTo("us-east-1 [GH-90000]");
            assertThat(result.getRole()).isEqualTo(RegionRole.PRIMARY); // GH-90000
        }

        @Test
        @DisplayName("should set primary region when it exists [GH-90000]")
        void shouldSetPrimaryRegion() { // GH-90000
            runPromise(() -> drManager.registerRegion("us-east-1", primaryConfig)); // GH-90000

            Boolean success = runPromise(() -> drManager.setPrimaryRegion("us-east-1 [GH-90000]"));
            assertThat(success).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should fail to set primary region when region is not registered [GH-90000]")
        void shouldFailToSetPrimaryRegionWhenNotRegistered() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> drManager.setPrimaryRegion("unknown-region [GH-90000]")))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // =========================================================================
    // REPLICATION
    // =========================================================================

    @Nested
    @DisplayName("startReplication and getReplicationStatus [GH-90000]")
    class ReplicationManagement {

        @Test
        @DisplayName("should start replication and return ACTIVE status [GH-90000]")
        void shouldStartReplication() { // GH-90000
            ReplicationStatus status = runPromise(() -> // GH-90000
                    drManager.startReplication("dataset-1", "us-east-1", List.of("eu-west-1 [GH-90000]")));

            assertThat(status).isNotNull(); // GH-90000
            assertThat(status.getDatasetId()).isEqualTo("dataset-1 [GH-90000]");
            assertThat(status.getState()).isEqualTo(ReplicationState.ACTIVE); // GH-90000
        }

        @Test
        @DisplayName("should return replication status for a started dataset [GH-90000]")
        void shouldReturnReplicationStatus() { // GH-90000
            runPromise(() -> // GH-90000
                    drManager.startReplication("ds-abc", "us-east-1", List.of("eu-west-1 [GH-90000]")));

            ReplicationStatus status = runPromise(() -> // GH-90000
                    drManager.getReplicationStatus("ds-abc [GH-90000]"));

            assertThat(status).isNotNull(); // GH-90000
            assertThat(status.getDatasetId()).isEqualTo("ds-abc [GH-90000]");
        }

        @Test
        @DisplayName("should return null replication status for unknown dataset [GH-90000]")
        void shouldReturnNullStatusForUnknownDataset() { // GH-90000
            ReplicationStatus status = runPromise(() -> // GH-90000
                    drManager.getReplicationStatus("does-not-exist [GH-90000]"));
            assertThat(status).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return replication health report [GH-90000]")
        void shouldReturnReplicationHealthReport() { // GH-90000
            ReplicationHealthReport report = runPromise(() -> // GH-90000
                    drManager.checkReplicationHealth()); // GH-90000

            assertThat(report).isNotNull(); // GH-90000
            assertThat(report.getOverallHealth()).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // RECOVERY POINTS
    // =========================================================================

    @Nested
    @DisplayName("createRecoveryPoint and listRecoveryPoints [GH-90000]")
    class RecoveryPoints {

        @Test
        @DisplayName("should create a recovery point for a dataset [GH-90000]")
        void shouldCreateRecoveryPoint() { // GH-90000
            RecoveryPoint point = runPromise(() -> // GH-90000
                    drManager.createRecoveryPoint("dataset-1", "daily-snapshot")); // GH-90000

            assertThat(point).isNotNull(); // GH-90000
            assertThat(point.getPointId()).isNotBlank(); // GH-90000
            assertThat(point.getDatasetId()).isEqualTo("dataset-1 [GH-90000]");
        }

        @Test
        @DisplayName("should list recovery points for a dataset [GH-90000]")
        void shouldListRecoveryPoints() { // GH-90000
            runPromise(() -> drManager.createRecoveryPoint("ds-2", "label-1")); // GH-90000
            runPromise(() -> drManager.createRecoveryPoint("ds-2", "label-2")); // GH-90000

            List<RecoveryPoint> points = runPromise(() -> // GH-90000
                    drManager.listRecoveryPoints("ds-2 [GH-90000]"));

            assertThat(points).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no recovery points exist [GH-90000]")
        void shouldReturnEmptyListWhenNoPoints() { // GH-90000
            List<RecoveryPoint> points = runPromise(() -> // GH-90000
                    drManager.listRecoveryPoints("no-points-here [GH-90000]"));
            assertThat(points).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // PITR
    // =========================================================================

    @Nested
    @DisplayName("performPITR [GH-90000]")
    class PointInTimeRecovery {

        @Test
        @DisplayName("should return failure when no recovery point exists [GH-90000]")
        void shouldReturnFailureWhenNoRecoveryPoint() { // GH-90000
            RecoveryResult result = runPromise(() -> // GH-90000
                    drManager.performPITR("dataset-pitr", Instant.now(), "recovered-dataset")); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should perform PITR when recovery point exists [GH-90000]")
        void shouldPerformPITRWithExistingPoint() { // GH-90000
            Instant before = Instant.now(); // GH-90000
            runPromise(() -> drManager.createRecoveryPoint("dataset-pitr", "snap")); // GH-90000

            RecoveryResult result = runPromise(() -> // GH-90000
                    drManager.performPITR("dataset-pitr", // GH-90000
                            before.plusSeconds(60), "recovered-pitr")); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getSourceDatasetId()).isEqualTo("dataset-pitr [GH-90000]");
        }
    }

    // =========================================================================
    // FAILOVER
    // =========================================================================

    @Nested
    @DisplayName("initiateFailover and testFailover [GH-90000]")
    class Failover {

        @Test
        @DisplayName("should initiate failover to a target region [GH-90000]")
        void shouldInitiateFailover() { // GH-90000
            runPromise(() -> drManager.registerRegion("us-east-1", primaryConfig)); // GH-90000
            runPromise(() -> drManager.registerRegion("eu-west-1", secondaryConfig)); // GH-90000
            runPromise(() -> drManager.setPrimaryRegion("us-east-1 [GH-90000]"));

            FailoverResult result = runPromise(() -> // GH-90000
                    drManager.initiateFailover("primary-failure", "eu-west-1")); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should test failover without executing it [GH-90000]")
        void shouldTestFailover() { // GH-90000
            runPromise(() -> drManager.registerRegion("eu-west-1", secondaryConfig)); // GH-90000

            FailoverTestResult testResult = runPromise(() -> // GH-90000
                    drManager.testFailover("eu-west-1 [GH-90000]"));

            assertThat(testResult).isNotNull(); // GH-90000
            assertThat(testResult.getTargetRegion()).isEqualTo("eu-west-1 [GH-90000]");
        }
    }

    // =========================================================================
    // RUNBOOKS
    // =========================================================================

    @Nested
    @DisplayName("getRunbook and listRunbooks [GH-90000]")
    class Runbooks {

        @Test
        @DisplayName("should list all pre-loaded runbooks [GH-90000]")
        void shouldListRunbooks() { // GH-90000
            List<Runbook> runbooks = runPromise(() -> drManager.listRunbooks()); // GH-90000
            assertThat(runbooks).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should retrieve runbook by ID [GH-90000]")
        void shouldGetRunbookById() { // GH-90000
            List<Runbook> runbooks = runPromise(() -> drManager.listRunbooks()); // GH-90000
            String firstId = runbooks.get(0).getRunbookId(); // GH-90000

            Runbook runbook = runPromise(() -> drManager.getRunbook(firstId)); // GH-90000
            assertThat(runbook).isNotNull(); // GH-90000
            assertThat(runbook.getRunbookId()).isEqualTo(firstId); // GH-90000
        }

        @Test
        @DisplayName("should return null for unknown runbook ID [GH-90000]")
        void shouldReturnNullForUnknownRunbook() { // GH-90000
            Runbook runbook = runPromise(() -> drManager.getRunbook("nonexistent-rb [GH-90000]"));
            assertThat(runbook).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // DR METRICS
    // =========================================================================

    @Nested
    @DisplayName("getDRMetrics [GH-90000]")
    class DRMetricsTest {

        @Test
        @DisplayName("should return DR metrics with target values [GH-90000]")
        void shouldReturnDRMetrics() { // GH-90000
            DRMetrics metrics = runPromise(() -> drManager.getDRMetrics()); // GH-90000

            assertThat(metrics).isNotNull(); // GH-90000
            assertThat(metrics.getTargetRPOMinutes()).isEqualTo(DisasterRecoveryManager.TARGET_RPO_MINUTES); // GH-90000
            assertThat(metrics.getTargetRTOMinutes()).isEqualTo(DisasterRecoveryManager.TARGET_RTO_MINUTES); // GH-90000
        }
    }
}
