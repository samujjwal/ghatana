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

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final UUID PROJECT_ID = UUID.randomUUID(); // GH-90000
    private static final ScanType SCAN_TYPE = ScanType.FULL;

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("pending() creates job with PENDING status and defaults")
        void pendingCreatesJobWithPendingStatusAndDefaults() { // GH-90000
            // WHEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000

            // THEN
            assertThat(job.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(job.getProjectId()).isEqualTo(PROJECT_ID); // GH-90000
            assertThat(job.getScanType()).isEqualTo(SCAN_TYPE); // GH-90000
            assertThat(job.getStatus()).isEqualTo(ScanStatus.PENDING); // GH-90000
            assertThat(job.getFindingsCount()).isZero(); // GH-90000
            assertThat(job.getCriticalCount()).isZero(); // GH-90000
            assertThat(job.getHighCount()).isZero(); // GH-90000
            assertThat(job.getMediumCount()).isZero(); // GH-90000
            assertThat(job.getLowCount()).isZero(); // GH-90000
            assertThat(job.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(job.getUpdatedAt()).isNotNull(); // GH-90000
            assertThat(job.getStartedAt()).isNull(); // GH-90000
            assertThat(job.getCompletedAt()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("pending() throws NullPointerException when workspaceId is null")
        void pendingThrowsWhenWorkspaceIdNull() { // GH-90000
            assertThatThrownBy(() -> ScanJob.pending(null, PROJECT_ID, SCAN_TYPE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("pending() throws NullPointerException when projectId is null")
        void pendingThrowsWhenProjectIdNull() { // GH-90000
            assertThatThrownBy(() -> ScanJob.pending(WORKSPACE_ID, null, SCAN_TYPE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("projectId must not be null");
        }

        @Test
        @DisplayName("pending() throws NullPointerException when scanType is null")
        void pendingThrowsWhenScanTypeNull() { // GH-90000
            assertThatThrownBy(() -> ScanJob.pending(WORKSPACE_ID, PROJECT_ID, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("scanType must not be null");
        }
    }

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        @DisplayName("start() transitions to RUNNING and sets startedAt")
        void startTransitionsToRunning() { // GH-90000
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000
            Instant beforeStart = Instant.now(); // GH-90000

            // WHEN
            ScanJob result = job.start(); // GH-90000

            // THEN
            assertThat(result).isSameAs(job); // GH-90000
            assertThat(job.getStatus()).isEqualTo(ScanStatus.RUNNING); // GH-90000
            assertThat(job.getStartedAt()).isAfterOrEqualTo(beforeStart); // GH-90000
            assertThat(job.getUpdatedAt()).isAfterOrEqualTo(beforeStart); // GH-90000
            assertThat(job.getCompletedAt()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("complete() transitions to COMPLETED and sets completedAt")
        void completeTransitionsToCompleted() { // GH-90000
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000
            job.start(); // GH-90000
            Instant beforeComplete = Instant.now(); // GH-90000

            // WHEN
            ScanJob result = job.complete(); // GH-90000

            // THEN
            assertThat(result).isSameAs(job); // GH-90000
            assertThat(job.getStatus()).isEqualTo(ScanStatus.COMPLETED); // GH-90000
            assertThat(job.getCompletedAt()).isAfterOrEqualTo(beforeComplete); // GH-90000
            assertThat(job.getErrorMessage()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("fail() transitions to FAILED with error message")
        void failTransitionsToFailed() { // GH-90000
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000
            job.start(); // GH-90000
            String errorMsg = "Connection timeout to cloud provider";

            // WHEN
            ScanJob result = job.fail(errorMsg); // GH-90000

            // THEN
            assertThat(result).isSameAs(job); // GH-90000
            assertThat(job.getStatus()).isEqualTo(ScanStatus.FAILED); // GH-90000
            assertThat(job.getErrorMessage()).isEqualTo(errorMsg); // GH-90000
            assertThat(job.getCompletedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("full lifecycle from PENDING to COMPLETED")
        void fullLifecycle() { // GH-90000
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000

            // WHEN
            job.start().complete(); // GH-90000

            // THEN
            assertThat(job.getStatus()).isEqualTo(ScanStatus.COMPLETED); // GH-90000
            assertThat(job.getStartedAt()).isNotNull(); // GH-90000
            assertThat(job.getCompletedAt()).isNotNull(); // GH-90000
            assertThat(job.getStartedAt()).isBefore(job.getCompletedAt()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Duration Calculation Tests")
    class DurationTests {

        @Test
        @DisplayName("getDurationMs() returns -1 before start")
        void getDurationMsReturnsNegativeBeforeStart() { // GH-90000
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000
            assertThat(job.getDurationMs()).isEqualTo(-1); // GH-90000
        }

        @Test
        @DisplayName("getDurationMs() returns -1 while running")
        void getDurationMsReturnsNegativeWhileRunning() { // GH-90000
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000
            job.start(); // GH-90000
            assertThat(job.getDurationMs()).isEqualTo(-1); // GH-90000
        }

        @Test
        @DisplayName("getDurationMs() returns positive value after completion")
        void getDurationMsReturnsPositiveAfterCompletion() throws InterruptedException { // GH-90000
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000
            job.start(); // GH-90000
            Thread.sleep(10); // Ensure measurable duration // GH-90000
            job.complete(); // GH-90000

            // THEN
            assertThat(job.getDurationMs()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("getDurationMs() returns positive value after failure")
        void getDurationMsReturnsPositiveAfterFailure() throws InterruptedException { // GH-90000
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000
            job.start(); // GH-90000
            Thread.sleep(10); // GH-90000
            job.fail("Error");

            // THEN
            assertThat(job.getDurationMs()).isGreaterThan(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Findings Count Tests")
    class FindingsCountTests {

        @Test
        @DisplayName("builder defaults all counts to zero")
        void builderDefaultsCountsToZero() { // GH-90000
            ScanJob job = ScanJob.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .projectId(PROJECT_ID) // GH-90000
                    .scanType(SCAN_TYPE) // GH-90000
                    .build(); // GH-90000

            assertThat(job.getFindingsCount()).isZero(); // GH-90000
            assertThat(job.getCriticalCount()).isZero(); // GH-90000
            assertThat(job.getHighCount()).isZero(); // GH-90000
            assertThat(job.getMediumCount()).isZero(); // GH-90000
            assertThat(job.getLowCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("can set finding counts via builder")
        void canSetFindingCountsViaBuilder() { // GH-90000
            ScanJob job = ScanJob.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .projectId(PROJECT_ID) // GH-90000
                    .scanType(SCAN_TYPE) // GH-90000
                    .findingsCount(100) // GH-90000
                    .criticalCount(5) // GH-90000
                    .highCount(15) // GH-90000
                    .mediumCount(30) // GH-90000
                    .lowCount(50) // GH-90000
                    .build(); // GH-90000

            assertThat(job.getFindingsCount()).isEqualTo(100); // GH-90000
            assertThat(job.getCriticalCount()).isEqualTo(5); // GH-90000
            assertThat(job.getHighCount()).isEqualTo(15); // GH-90000
            assertThat(job.getMediumCount()).isEqualTo(30); // GH-90000
            assertThat(job.getLowCount()).isEqualTo(50); // GH-90000
        }

        @Test
        @DisplayName("finding counts can be updated via setters")
        void findingCountsCanBeUpdatedViaSetters() { // GH-90000
            // GIVEN
            ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, SCAN_TYPE); // GH-90000

            // WHEN
            job.setFindingsCount(25); // GH-90000
            job.setCriticalCount(2); // GH-90000
            job.setHighCount(5); // GH-90000
            job.setMediumCount(8); // GH-90000
            job.setLowCount(10); // GH-90000

            // THEN
            assertThat(job.getFindingsCount()).isEqualTo(25); // GH-90000
            assertThat(job.getCriticalCount()).isEqualTo(2); // GH-90000
            assertThat(job.getHighCount()).isEqualTo(5); // GH-90000
            assertThat(job.getMediumCount()).isEqualTo(8); // GH-90000
            assertThat(job.getLowCount()).isEqualTo(10); // GH-90000
        }
    }

    @Nested
    @DisplayName("Scan Type Tests")
    class ScanTypeTests {

        @Test
        @DisplayName("can create job for each scan type")
        void canCreateJobForEachScanType() { // GH-90000
            for (ScanType type : ScanType.values()) { // GH-90000
                ScanJob job = ScanJob.pending(WORKSPACE_ID, PROJECT_ID, type); // GH-90000
                assertThat(job.getScanType()).isEqualTo(type); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            ScanJob job1 = ScanJob.builder().id(id).scanType(ScanType.FULL).build(); // GH-90000
            ScanJob job2 = ScanJob.builder().id(id).scanType(ScanType.QUICK).build(); // GH-90000

            assertThat(job1).isEqualTo(job2); // GH-90000
            assertThat(job1.hashCode()).isEqualTo(job2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            ScanJob job1 = ScanJob.builder().id(UUID.randomUUID()).build(); // GH-90000
            ScanJob job2 = ScanJob.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(job1).isNotEqualTo(job2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("can store JSON configuration")
        void canStoreJsonConfiguration() { // GH-90000
            String config = """
                    {
                        "scanners": ["sast", "sca", "secrets"],
                        "maxDepth": 10,
                        "includeDevDeps": false
                    }
                    """;

            ScanJob job = ScanJob.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .projectId(PROJECT_ID) // GH-90000
                    .scanType(SCAN_TYPE) // GH-90000
                    .config(config) // GH-90000
                    .build(); // GH-90000

            assertThat(job.getConfig()).isEqualTo(config); // GH-90000
        }

        @Test
        @DisplayName("can store description")
        void canStoreDescription() { // GH-90000
            ScanJob job = ScanJob.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .projectId(PROJECT_ID) // GH-90000
                    .scanType(SCAN_TYPE) // GH-90000
                    .description("Nightly security scan for production deployment")
                    .build(); // GH-90000

            assertThat(job.getDescription()).isEqualTo("Nightly security scan for production deployment");
        }
    }
}
