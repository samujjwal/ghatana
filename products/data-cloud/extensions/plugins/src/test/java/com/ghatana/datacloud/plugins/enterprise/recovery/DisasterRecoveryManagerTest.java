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
@DisplayName("DisasterRecoveryManager Tests")
class DisasterRecoveryManagerTest extends EventloopTestBase {

    private DisasterRecoveryManager drManager;
    private RegionConfig primaryConfig;
    private RegionConfig secondaryConfig;

    @BeforeEach
    void setUp() { 
        drManager = new DisasterRecoveryManager(); 

        primaryConfig = RegionConfig.builder() 
                .regionId("us-east-1")
                .displayName("US East")
                .role(RegionRole.PRIMARY) 
                .status(RegionStatus.HEALTHY) 
                .endpoint("https://us-east-1.example.com")
                .build(); 

        secondaryConfig = RegionConfig.builder() 
                .regionId("eu-west-1")
                .displayName("EU West")
                .role(RegionRole.STANDBY) 
                .status(RegionStatus.HEALTHY) 
                .endpoint("https://eu-west-1.example.com")
                .build(); 
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create manager without errors")
        void shouldCreateManager() { 
            assertThatCode(() -> new DisasterRecoveryManager()).doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("should expose target SLA constants")
        void shouldExposeTargetSLAConstants() { 
            assertThat(DisasterRecoveryManager.TARGET_RTO_MINUTES).isEqualTo(15); 
            assertThat(DisasterRecoveryManager.TARGET_RPO_MINUTES).isEqualTo(5); 
            assertThat(DisasterRecoveryManager.MAX_REPLICATION_LAG_SECONDS).isEqualTo(1); 
        }
    }

    // =========================================================================
    // REGION MANAGEMENT
    // =========================================================================

    @Nested
    @DisplayName("registerRegion and setPrimaryRegion")
    class RegionManagement {

        @Test
        @DisplayName("should register a region and return its config")
        void shouldRegisterRegion() { 
            RegionConfig result = runPromise(() -> 
                    drManager.registerRegion("us-east-1", primaryConfig)); 

            assertThat(result).isNotNull(); 
            assertThat(result.getRegionId()).isEqualTo("us-east-1");
            assertThat(result.getRole()).isEqualTo(RegionRole.PRIMARY); 
        }

        @Test
        @DisplayName("should set primary region when it exists")
        void shouldSetPrimaryRegion() { 
            runPromise(() -> drManager.registerRegion("us-east-1", primaryConfig)); 

            Boolean success = runPromise(() -> drManager.setPrimaryRegion("us-east-1"));
            assertThat(success).isTrue(); 
        }

        @Test
        @DisplayName("should fail to set primary region when region is not registered")
        void shouldFailToSetPrimaryRegionWhenNotRegistered() { 
            assertThatThrownBy(() -> 
                    runPromise(() -> drManager.setPrimaryRegion("unknown-region")))
                    .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    // =========================================================================
    // REPLICATION
    // =========================================================================

    @Nested
    @DisplayName("startReplication and getReplicationStatus")
    class ReplicationManagement {

        @Test
        @DisplayName("should start replication and return ACTIVE status")
        void shouldStartReplication() { 
            ReplicationStatus status = runPromise(() -> 
                    drManager.startReplication("dataset-1", "us-east-1", List.of("eu-west-1")));

            assertThat(status).isNotNull(); 
            assertThat(status.getDatasetId()).isEqualTo("dataset-1");
            assertThat(status.getState()).isEqualTo(ReplicationState.ACTIVE); 
        }

        @Test
        @DisplayName("should return replication status for a started dataset")
        void shouldReturnReplicationStatus() { 
            runPromise(() -> 
                    drManager.startReplication("ds-abc", "us-east-1", List.of("eu-west-1")));

            ReplicationStatus status = runPromise(() -> 
                    drManager.getReplicationStatus("ds-abc"));

            assertThat(status).isNotNull(); 
            assertThat(status.getDatasetId()).isEqualTo("ds-abc");
        }

        @Test
        @DisplayName("should return null replication status for unknown dataset")
        void shouldReturnNullStatusForUnknownDataset() { 
            ReplicationStatus status = runPromise(() -> 
                    drManager.getReplicationStatus("does-not-exist"));
            assertThat(status).isNull(); 
        }

        @Test
        @DisplayName("should return replication health report")
        void shouldReturnReplicationHealthReport() { 
            ReplicationHealthReport report = runPromise(() -> 
                    drManager.checkReplicationHealth()); 

            assertThat(report).isNotNull(); 
            assertThat(report.getOverallHealth()).isNotNull(); 
        }
    }

    // =========================================================================
    // RECOVERY POINTS
    // =========================================================================

    @Nested
    @DisplayName("createRecoveryPoint and listRecoveryPoints")
    class RecoveryPoints {

        @Test
        @DisplayName("should create a recovery point for a dataset")
        void shouldCreateRecoveryPoint() { 
            RecoveryPoint point = runPromise(() -> 
                    drManager.createRecoveryPoint("dataset-1", "daily-snapshot")); 

            assertThat(point).isNotNull(); 
            assertThat(point.getPointId()).isNotBlank(); 
            assertThat(point.getDatasetId()).isEqualTo("dataset-1");
        }

        @Test
        @DisplayName("should list recovery points for a dataset")
        void shouldListRecoveryPoints() { 
            runPromise(() -> drManager.createRecoveryPoint("ds-2", "label-1")); 
            runPromise(() -> drManager.createRecoveryPoint("ds-2", "label-2")); 

            List<RecoveryPoint> points = runPromise(() -> 
                    drManager.listRecoveryPoints("ds-2"));

            assertThat(points).hasSize(2); 
        }

        @Test
        @DisplayName("should return empty list when no recovery points exist")
        void shouldReturnEmptyListWhenNoPoints() { 
            List<RecoveryPoint> points = runPromise(() -> 
                    drManager.listRecoveryPoints("no-points-here"));
            assertThat(points).isEmpty(); 
        }
    }

    // =========================================================================
    // PITR
    // =========================================================================

    @Nested
    @DisplayName("performPITR")
    class PointInTimeRecovery {

        @Test
        @DisplayName("should return failure when no recovery point exists")
        void shouldReturnFailureWhenNoRecoveryPoint() { 
            RecoveryResult result = runPromise(() -> 
                    drManager.performPITR("dataset-pitr", Instant.now(), "recovered-dataset")); 

            assertThat(result).isNotNull(); 
            assertThat(result.isSuccess()).isFalse(); 
        }

        @Test
        @DisplayName("should perform PITR when recovery point exists")
        void shouldPerformPITRWithExistingPoint() { 
            Instant before = Instant.now(); 
            runPromise(() -> drManager.createRecoveryPoint("dataset-pitr", "snap")); 

            RecoveryResult result = runPromise(() -> 
                    drManager.performPITR("dataset-pitr", 
                            before.plusSeconds(60), "recovered-pitr")); 

            assertThat(result).isNotNull(); 
            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getSourceDatasetId()).isEqualTo("dataset-pitr");
        }
    }

    // =========================================================================
    // FAILOVER
    // =========================================================================

    @Nested
    @DisplayName("initiateFailover and testFailover")
    class Failover {

        @Test
        @DisplayName("should initiate failover to a target region")
        void shouldInitiateFailover() { 
            runPromise(() -> drManager.registerRegion("us-east-1", primaryConfig)); 
            runPromise(() -> drManager.registerRegion("eu-west-1", secondaryConfig)); 
            runPromise(() -> drManager.setPrimaryRegion("us-east-1"));

            FailoverResult result = runPromise(() -> 
                    drManager.initiateFailover("primary-failure", "eu-west-1")); 

            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("should test failover without executing it")
        void shouldTestFailover() { 
            runPromise(() -> drManager.registerRegion("eu-west-1", secondaryConfig)); 

            FailoverTestResult testResult = runPromise(() -> 
                    drManager.testFailover("eu-west-1"));

            assertThat(testResult).isNotNull(); 
            assertThat(testResult.getTargetRegion()).isEqualTo("eu-west-1");
        }
    }

    // =========================================================================
    // RUNBOOKS
    // =========================================================================

    @Nested
    @DisplayName("getRunbook and listRunbooks")
    class Runbooks {

        @Test
        @DisplayName("should list all pre-loaded runbooks")
        void shouldListRunbooks() { 
            List<Runbook> runbooks = runPromise(() -> drManager.listRunbooks()); 
            assertThat(runbooks).isNotEmpty(); 
        }

        @Test
        @DisplayName("should retrieve runbook by ID")
        void shouldGetRunbookById() { 
            List<Runbook> runbooks = runPromise(() -> drManager.listRunbooks()); 
            String firstId = runbooks.get(0).getRunbookId(); 

            Runbook runbook = runPromise(() -> drManager.getRunbook(firstId)); 
            assertThat(runbook).isNotNull(); 
            assertThat(runbook.getRunbookId()).isEqualTo(firstId); 
        }

        @Test
        @DisplayName("should return null for unknown runbook ID")
        void shouldReturnNullForUnknownRunbook() { 
            Runbook runbook = runPromise(() -> drManager.getRunbook("nonexistent-rb"));
            assertThat(runbook).isNull(); 
        }
    }

    // =========================================================================
    // DR METRICS
    // =========================================================================

    @Nested
    @DisplayName("getDRMetrics")
    class DRMetricsTest {

        @Test
        @DisplayName("should return DR metrics with target values")
        void shouldReturnDRMetrics() { 
            DRMetrics metrics = runPromise(() -> drManager.getDRMetrics()); 

            assertThat(metrics).isNotNull(); 
            assertThat(metrics.getTargetRPOMinutes()).isEqualTo(DisasterRecoveryManager.TARGET_RPO_MINUTES); 
            assertThat(metrics.getTargetRTOMinutes()).isEqualTo(DisasterRecoveryManager.TARGET_RTO_MINUTES); 
        }
    }
}
