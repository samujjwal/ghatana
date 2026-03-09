package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ScanJob} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates ScanJob entity behavior, state machine transitions, and metrics
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ScanJob Domain Model Tests")
class ScanJobTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ScanType SCAN_TYPE = ScanType.FULL;

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("pending() creates job with PENDING status and defaults")
        void pendingCreatesJobWithPendingStatusAndDefaults() {
            // WHEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);

            // THEN
            assertThat(job.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(job.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(job.getScanType()).isEqualTo(SCAN_TYPE);
            assertThat(job.getStatus()).isEqualTo(ScanStatus.PENDING);
            assertThat(job.getFindingsCount()).isZero();
            assertThat(job.getCriticalCount()).isZero();
            assertThat(job.getHighCount()).isZero();
            assertThat(job.getMediumCount()).isZero();
            assertThat(job.getLowCount()).isZero();
            assertThat(job.getCreatedAt()).isNotNull();
            assertThat(job.getUpdatedAt()).isNotNull();
            assertThat(job.getStartedAt()).isNull();
            assertThat(job.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("pending() throws NullPointerException when workspaceId is null")
        void pendingThrowsWhenWorkspaceIdNull() {
            assertThatThrownBy(() -> ScanJob.pending(null, PROJECT_ID, SCAN_TYPE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("pending() throws NullPointerException when projectId is null")
        void pendingThrowsWhenProjectIdNull() {
            assertThatThrownBy(() -> ScanJob.pending(WORKSPACE_ID, null, SCAN_TYPE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("projectId must not be null");
        }

        @Test
        @DisplayName("pending() throws NullPointerException when scanType is null")
        void pendingThrowsWhenScanTypeNull() {
            assertThatThrownBy(() -> ScanJob.pending(WORKSPACE_ID, PROJECT_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("scanType must not be null");
        }
    }

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        @DisplayName("start() transitions to RUNNING and sets startedAt")
        void startTransitionsToRunning() {
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);
            Instant beforeStart = Instant.now();

            // WHEN
            ScanJob result = job.start();

            // THEN
            assertThat(result).isSameAs(job);
            assertThat(job.getStatus()).isEqualTo(ScanStatus.RUNNING);
            assertThat(job.getStartedAt()).isAfterOrEqualTo(beforeStart);
            assertThat(job.getUpdatedAt()).isAfterOrEqualTo(beforeStart);
            assertThat(job.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("complete() transitions to COMPLETED and sets completedAt")
        void completeTransitionsToCompleted() {
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);
            job.start();
            Instant beforeComplete = Instant.now();

            // WHEN
            ScanJob result = job.complete();

            // THEN
            assertThat(result).isSameAs(job);
            assertThat(job.getStatus()).isEqualTo(ScanStatus.COMPLETED);
            assertThat(job.getCompletedAt()).isAfterOrEqualTo(beforeComplete);
            assertThat(job.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("fail() transitions to FAILED with error message")
        void failTransitionsToFailed() {
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);
            job.start();
            String errorMsg = "Connection timeout to cloud provider";

            // WHEN
            ScanJob result = job.fail(errorMsg);

            // THEN
            assertThat(result).isSameAs(job);
            assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED);
            assertThat(job.getErrorMessage()).isEqualTo(errorMsg);
            assertThat(job.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("full lifecycle from PENDING to COMPLETED")
        void fullLifecycle() {
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);

            // WHEN
            job.start().complete();

            // THEN
            assertThat(job.getStatus()).isEqualTo(ScanStatus.COMPLETED);
            assertThat(job.getStartedAt()).isNotNull();
            assertThat(job.getCompletedAt()).isNotNull();
            assertThat(job.getStartedAt()).isBefore(job.getCompletedAt());
        }
    }

    @Nested
    @DisplayName("Duration Calculation Tests")
    class DurationTests {

        @Test
        @DisplayName("getDurationMs() returns -1 before start")
        void getDurationMsReturnsNegativeBeforeStart() {
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);
            assertThat(job.getDurationMs()).isEqualTo(-1);
        }

        @Test
        @DisplayName("getDurationMs() returns -1 while running")
        void getDurationMsReturnsNegativeWhileRunning() {
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);
            job.start();
            assertThat(job.getDurationMs()).isEqualTo(-1);
        }

        @Test
        @DisplayName("getDurationMs() returns positive value after completion")
        void getDurationMsReturnsPositiveAfterCompletion() throws InterruptedException {
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);
            job.start();
            Thread.sleep(10); // Ensure measurable duration
            job.complete();

            // THEN
            assertThat(job.getDurationMs()).isGreaterThan(0);
        }

        @Test
        @DisplayName("getDurationMs() returns positive value after failure")
        void getDurationMsReturnsPositiveAfterFailure() throws InterruptedException {
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);
            job.start();
            Thread.sleep(10);
            job.fail("Error");

            // THEN
            assertThat(job.getDurationMs()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Findings Count Tests")
    class FindingsCountTests {

        @Test
        @DisplayName("builder defaults all counts to zero")
        void builderDefaultsCountsToZero() {
            ScanJob job = ScanJob.builder()
                    .workspaceId(WORKSPACE_ID)
                    .projectId(PROJECT_ID)
                    .scanType(SCAN_TYPE)
                    .build();

            assertThat(job.getFindingsCount()).isZero();
            assertThat(job.getCriticalCount()).isZero();
            assertThat(job.getHighCount()).isZero();
            assertThat(job.getMediumCount()).isZero();
            assertThat(job.getLowCount()).isZero();
        }

        @Test
        @DisplayName("can set finding counts via builder")
        void canSetFindingCountsViaBuilder() {
            ScanJob job = ScanJob.builder()
                    .workspaceId(WORKSPACE_ID)
                    .projectId(PROJECT_ID)
                    .scanType(SCAN_TYPE)
                    .findingsCount(100)
                    .criticalCount(5)
                    .highCount(15)
                    .mediumCount(30)
                    .lowCount(50)
                    .build();

            assertThat(job.getFindingsCount()).isEqualTo(100);
            assertThat(job.getCriticalCount()).isEqualTo(5);
            assertThat(job.getHighCount()).isEqualTo(15);
            assertThat(job.getMediumCount()).isEqualTo(30);
            assertThat(job.getLowCount()).isEqualTo(50);
        }

        @Test
        @DisplayName("finding counts can be updated via setters")
        void findingCountsCanBeUpdatedViaSetters() {
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE);

            // WHEN
            job.setFindingsCount(25);
            job.setCriticalCount(2);
            job.setHighCount(5);
            job.setMediumCount(8);
            job.setLowCount(10);

            // THEN
            assertThat(job.getFindingsCount()).isEqualTo(25);
            assertThat(job.getCriticalCount()).isEqualTo(2);
            assertThat(job.getHighCount()).isEqualTo(5);
            assertThat(job.getMediumCount()).isEqualTo(8);
            assertThat(job.getLowCount()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Scan Type Tests")
    class ScanTypeTests {

        @Test
        @DisplayName("can create job for each scan type")
        void canCreateJobForEachScanType() {
            for (ScanType type : ScanType.values()) {
                ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, type);
                assertThat(job.getScanType()).isEqualTo(type);
            }
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            ScanJob job1 = ScanJob.builder().id(id).scanType(ScanType.FULL).build();
            ScanJob job2 = ScanJob.builder().id(id).scanType(ScanType.QUICK).build();

            assertThat(job1).isEqualTo(job2);
            assertThat(job1.hashCode()).isEqualTo(job2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            ScanJob job1 = ScanJob.builder().id(UUID.randomUUID()).build();
            ScanJob job2 = ScanJob.builder().id(UUID.randomUUID()).build();

            assertThat(job1).isNotEqualTo(job2);
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("can store JSON configuration")
        void canStoreJsonConfiguration() {
            String config = """
                    {
                        "scanners": ["sast", "sca", "secrets"],
                        "maxDepth": 10,
                        "includeDevDeps": false
                    }
                    """;

            ScanJob job = ScanJob.builder()
                    .workspaceId(WORKSPACE_ID)
                    .projectId(PROJECT_ID)
                    .scanType(SCAN_TYPE)
                    .config(config)
                    .build();

            assertThat(job.getConfig()).isEqualTo(config);
        }

        @Test
        @DisplayName("can store description")
        void canStoreDescription() {
            ScanJob job = ScanJob.builder()
                    .workspaceId(WORKSPACE_ID)
                    .projectId(PROJECT_ID)
                    .scanType(SCAN_TYPE)
                    .description("Nightly security scan for production deployment")
                    .build();

            assertThat(job.getDescription()).isEqualTo("Nightly security scan for production deployment");
        }
    }
}
