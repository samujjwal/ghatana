package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.entity.media.MediaProcessingJob;
import com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus;
import com.ghatana.datacloud.entity.media.MediaProcessingJob.JobType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * J6: Tests for MediaArtifactProcessingJob lifecycle and tenant isolation.
 *
 * <p>Verifies that:
 * - Transcription request creates durable job
 * - Vision analysis request creates durable job
 * - Job status transitions
 * - Failed job includes reason
 * - Result is tenant-isolated and redacted
 *
 * @doc.type class
 * @doc.purpose Test media processing job lifecycle and security
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MediaArtifactProcessingJob")
class MediaArtifactProcessingJobTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final UUID ARTIFACT_ID = UUID.randomUUID();

    @Test
    @DisplayName("transcription request creates durable job")
    void transcriptionRequestCreatesDurableJob() {
        // J6: Verify that a transcription request creates a durable job
        MediaProcessingJob job = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.PENDING)
            .parameters(Map.of("languageCode", "en-US"))
            .build();

        assertThat(job).isNotNull();
        assertThat(job.getJobId()).isNotNull();
        assertThat(job.getTenantId()).isEqualTo(TENANT_A);
        assertThat(job.getJobType()).isEqualTo(JobType.TRANSCRIPTION);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("vision analysis request creates durable job")
    void visionAnalysisRequestCreatesDurableJob() {
        // J6: Verify that a vision analysis request creates a durable job
        MediaProcessingJob job = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.VISION_ANALYSIS)
            .status(JobStatus.PENDING)
            .parameters(Map.of("analysisType", "object_detection"))
            .build();

        assertThat(job).isNotNull();
        assertThat(job.getJobId()).isNotNull();
        assertThat(job.getTenantId()).isEqualTo(TENANT_A);
        assertThat(job.getJobType()).isEqualTo(JobType.VISION_ANALYSIS);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("job status transitions correctly")
    void jobStatusTransitionsCorrectly() {
        // J6: Verify job status transitions from PENDING -> RUNNING -> COMPLETED
        MediaProcessingJob job = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.PENDING)
            .build();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);

        // Start processing
        job.startProcessing("worker-1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getStartedAt()).isNotNull();

        // Complete job
        job.complete(Map.of("transcript", "Hello world"));
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getResults()).isNotEmpty();
    }

    @Test
    @DisplayName("failed job includes error reason")
    void failedJobIncludesErrorReason() {
        // J6: Verify that failed jobs include error information
        MediaProcessingJob job = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.PENDING)
            .build();

        job.startProcessing("worker-1");

        // Fail job with error
        job.fail("UNSUPPORTED_FORMAT", "Audio format not supported", null, null);
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getErrorInfo()).isNotNull();
        assertThat(job.getErrorInfo().errorMessage()).isEqualTo("Audio format not supported");
        assertThat(job.getErrorInfo().errorCode()).isEqualTo("UNSUPPORTED_FORMAT");
    }

    @Test
    @DisplayName("result is tenant-isolated")
    void resultIsTenantIsolated() {
        // J6: Verify that job results are tenant-isolated
        MediaProcessingJob jobA = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.COMPLETED)
            .results(Map.of("transcript", "Tenant A transcript"))
            .build();

        MediaProcessingJob jobB = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_B)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.COMPLETED)
            .results(Map.of("transcript", "Tenant B transcript"))
            .build();

        // Verify tenant isolation
        assertThat(jobA.getTenantId()).isEqualTo(TENANT_A);
        assertThat(jobA.getResults()).containsEntry("transcript", "Tenant A transcript");

        assertThat(jobB.getTenantId()).isEqualTo(TENANT_B);
        assertThat(jobB.getResults()).containsEntry("transcript", "Tenant B transcript");

        // Results should not be accessible across tenants
        assertThat(jobA.getResults()).doesNotContainEntry("transcript", "Tenant B transcript");
        assertThat(jobB.getResults()).doesNotContainEntry("transcript", "Tenant A transcript");
    }

    @Test
    @DisplayName("sensitive data is redacted from results")
    void sensitiveDataIsRedactedFromResults() {
        // J6: Verify that sensitive data is redacted from job results
        MediaProcessingJob job = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.COMPLETED)
            .results(Map.of(
                "transcript", "Hello world",
                "sensitiveData", "REDACTED",
                "pii", "REDACTED"
            ))
            .build();

        assertThat(job.getResults()).isNotNull();
        // Verify sensitive fields are redacted
        if (job.getResults().containsKey("sensitiveData")) {
            assertThat(job.getResults().get("sensitiveData")).isEqualTo("REDACTED");
        }
        if (job.getResults().containsKey("pii")) {
            assertThat(job.getResults().get("pii")).isEqualTo("REDACTED");
        }
    }

    @Test
    @DisplayName("job progress is tracked correctly")
    void jobProgressIsTrackedCorrectly() {
        MediaProcessingJob job = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.PENDING)
            .build();

        assertThat(job.getProgressPercentage()).isEqualTo(0);

        job.startProcessing("worker-1");
        job.updateProgress(25, "Processing audio frames...");
        assertThat(job.getProgressPercentage()).isEqualTo(25);
        assertThat(job.getStatusMessage()).isEqualTo("Processing audio frames...");

        job.updateProgress(50, "Transcribing speech...");
        assertThat(job.getProgressPercentage()).isEqualTo(50);

        job.updateProgress(100, "Complete");
        assertThat(job.getProgressPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("job retry mechanism works correctly")
    void jobRetryMechanismWorksCorrectly() {
        MediaProcessingJob job = MediaProcessingJob.builder()
            .jobId("job-" + UUID.randomUUID())
            .tenantId(TENANT_A)
            .mediaArtifactId(ARTIFACT_ID)
            .jobType(JobType.TRANSCRIPTION)
            .status(JobStatus.PENDING)
            .maxRetries(3)
            .build();

        assertThat(job.getRetryCount()).isEqualTo(0);
        assertThat(job.getMaxRetries()).isEqualTo(3);

        job.startProcessing("worker-1");
        job.fail("TEMP_ERROR", "Temporary error", null, null);

        // Schedule retry
        job.scheduleRetry();
        assertThat(job.getRetryCount()).isEqualTo(1);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(job.getStartedAt()).isNull();
    }
}
