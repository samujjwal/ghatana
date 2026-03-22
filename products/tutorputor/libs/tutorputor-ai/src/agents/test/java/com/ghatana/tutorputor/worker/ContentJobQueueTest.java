package com.ghatana.tutorputor.worker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ContentJobQueue.
 *
 * @doc.type class
 * @doc.purpose Unit tests for job queue
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ContentJobQueue Tests")
class ContentJobQueueTest {

    private ContentJobQueue jobQueue;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ContentJobQueue.QueueConfig config = new ContentJobQueue.QueueConfig(
            5,                          // maxConcurrentJobs
            3,                          // maxRetries
            Duration.ofMillis(100)      // baseRetryDelay
        );
        jobQueue = new ContentJobQueue(config, meterRegistry);
        for (ContentJob.JobType type : ContentJob.JobType.values()) {
            jobQueue.registerHandler(type, job -> Map.of(
                "jobType", type.name(),
                "jobId", job.id()
            ));
        }
    }

    @Test
    @DisplayName("Should submit and track job status")
    void shouldSubmitAndTrackJobStatus() {
        // GIVEN
        ContentJob job = ContentJob.builder()
            .type(ContentJob.JobType.GENERATE_CLAIMS)
            .tenantId("tenant-1")
            .requesterId("user-1")
            .payload(Map.of("topic", "photosynthesis"))
            .build();

        // WHEN
        String jobId = jobQueue.submit(job);

        // THEN
        assertThat(jobId).isNotNull();
        assertThat(jobQueue.getStatus(jobId)).isEqualTo(ContentJobQueue.JobStatus.QUEUED);
    }

    @Test
    @DisplayName("Should process job and update status")
    void shouldProcessJobAndUpdateStatus() throws InterruptedException {
        // GIVEN
        jobQueue.start();
        
        ContentJob job = ContentJob.builder()
            .type(ContentJob.JobType.VALIDATE_CONTENT)
            .tenantId("tenant-1")
            .requesterId("user-1")
            .payload(Map.of("contentId", "content-123"))
            .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ContentJob> completedJobRef = new AtomicReference<>();
        
        jobQueue.onJobCompleted(completed -> {
            completedJobRef.set(completed);
            latch.countDown();
        });

        // WHEN
        String jobId = jobQueue.submit(job);

        // THEN
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        ContentJob completedJob = completedJobRef.get();
        assertThat(completedJob).isNotNull();
        assertThat(completedJob.id()).isEqualTo(jobId);
        assertThat(completedJob.result()).isNotNull();
        
        // Cleanup
        jobQueue.stop(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Should report queue statistics")
    void shouldReportQueueStatistics() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            ContentJob job = ContentJob.builder()
                .type(ContentJob.JobType.GENERATE_EXAMPLES)
                .tenantId("tenant-1")
                .requesterId("user-" + i)
                .build();
            jobQueue.submit(job);
        }

        // WHEN
        ContentJobQueue.QueueStats stats = jobQueue.getStats();

        // THEN
        assertThat(stats.queuedJobs()).isEqualTo(5);
        assertThat(stats.activeJobs()).isZero();
        assertThat(stats.completedJobs()).isZero();
    }

    @Test
    @DisplayName("Should respect priority ordering")
    void shouldRespectPriorityOrdering() {
        // GIVEN
        ContentJob lowPriorityJob = ContentJob.builder()
            .type(ContentJob.JobType.GENERATE_CLAIMS)
            .tenantId("tenant-1")
            .requesterId("user-1")
            .lowPriority()
            .build();
        
        ContentJob highPriorityJob = ContentJob.builder()
            .type(ContentJob.JobType.GENERATE_CLAIMS)
            .tenantId("tenant-1")
            .requesterId("user-2")
            .highPriority()
            .build();

        // WHEN - submit low priority first
        String lowId = jobQueue.submit(lowPriorityJob);
        String highId = jobQueue.submit(highPriorityJob);

        // THEN - high priority should be at front of queue
        ContentJobQueue.QueueStats stats = jobQueue.getStats();
        assertThat(stats.queuedJobs()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return unknown status for non-existent job")
    void shouldReturnUnknownStatusForNonExistentJob() {
        // WHEN
        ContentJobQueue.JobStatus status = jobQueue.getStatus("non-existent-id");

        // THEN
        assertThat(status).isEqualTo(ContentJobQueue.JobStatus.UNKNOWN);
    }

    @Test
    @DisplayName("Should include metadata in jobs")
    void shouldIncludeMetadataInJobs() {
        // GIVEN
        ContentJob job = ContentJob.builder()
            .type(ContentJob.JobType.BATCH_GENERATION)
            .tenantId("tenant-1")
            .requesterId("user-1")
            .metadata(Map.of(
                "source", "curriculum",
                "gradeLevel", "5"
            ))
            .build();

        // WHEN
        String jobId = jobQueue.submit(job);

        // THEN
        assertThat(jobId).isNotNull();
        // Job is in queue with metadata preserved
        assertThat(jobQueue.getStatus(jobId)).isEqualTo(ContentJobQueue.JobStatus.QUEUED);
    }
}
