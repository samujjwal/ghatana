package com.ghatana.tutorputor.worker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JobProgressTracker.
 *
 * @doc.type class
 * @doc.purpose Unit tests for progress tracking
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("JobProgressTracker Tests")
class JobProgressTrackerTest {

    private JobProgressTracker progressTracker;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        progressTracker = new JobProgressTracker(meterRegistry);
    }

    @Test
    @DisplayName("Should track job progress")
    void shouldTrackJobProgress() {
        // GIVEN
        String jobId = "job-001";
        
        // WHEN
        progressTracker.initializeJob(jobId, 10, "Generate claims for photosynthesis");
        progressTracker.updateProgress(jobId, 3, "Fetching knowledge base");
        progressTracker.updateProgress(jobId, 5, "Generating content");
        progressTracker.updateProgress(jobId, 8, "Validating content");

        // THEN
        JobProgressTracker.JobProgress progress = progressTracker.getProgress(jobId);
        assertThat(progress).isNotNull();
        assertThat(progress.completedSteps()).isEqualTo(8);
        assertThat(progress.totalSteps()).isEqualTo(10);
        assertThat(progress.progressPercent()).isEqualTo(80);
        assertThat(progress.status()).isEqualTo(JobProgressTracker.JobProgressStatus.IN_PROGRESS);
        assertThat(progress.steps()).hasSize(3);
    }

    @Test
    @DisplayName("Should complete job and record result")
    void shouldCompleteJobAndRecordResult() {
        // GIVEN
        String jobId = "job-002";
        progressTracker.initializeJob(jobId, 5, "Content validation");
        progressTracker.updateProgress(jobId, 5, "Final validation");

        // WHEN
        progressTracker.completeJob(jobId, "Validation passed with 95% confidence");

        // THEN
        JobProgressTracker.JobProgress progress = progressTracker.getProgress(jobId);
        assertThat(progress.status()).isEqualTo(JobProgressTracker.JobProgressStatus.COMPLETED);
        assertThat(progress.currentStep()).isEqualTo("Validation passed with 95% confidence");
        assertThat(progress.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should fail job with error message")
    void shouldFailJobWithErrorMessage() {
        // GIVEN
        String jobId = "job-003";
        progressTracker.initializeJob(jobId, 10, "Generate simulation");
        progressTracker.updateProgress(jobId, 3, "Processing");

        // WHEN
        progressTracker.failJob(jobId, "LLM rate limit exceeded");

        // THEN
        JobProgressTracker.JobProgress progress = progressTracker.getProgress(jobId);
        assertThat(progress.status()).isEqualTo(JobProgressTracker.JobProgressStatus.FAILED);
        assertThat(progress.error()).isEqualTo("LLM rate limit exceeded");
        assertThat(progress.completedSteps()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should notify subscribers of progress updates")
    void shouldNotifySubscribersOfProgressUpdates() throws InterruptedException {
        // GIVEN
        String jobId = "job-004";
        AtomicInteger updateCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        progressTracker.initializeJob(jobId, 5, "Test job");
        
        progressTracker.subscribe(jobId, progress -> {
            updateCount.incrementAndGet();
            latch.countDown();
        });

        // WHEN
        progressTracker.updateProgress(jobId, 1, "Step 1");
        progressTracker.updateProgress(jobId, 2, "Step 2");

        // THEN
        boolean completed = latch.await(1, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(updateCount.get()).isGreaterThanOrEqualTo(3); // Initial + 2 updates
    }

    @Test
    @DisplayName("Should unsubscribe from updates")
    void shouldUnsubscribeFromUpdates() throws InterruptedException {
        // GIVEN
        String jobId = "job-005";
        AtomicInteger updateCount = new AtomicInteger(0);

        progressTracker.initializeJob(jobId, 5, "Test job");
        
        JobProgressTracker.Subscription subscription = progressTracker.subscribe(jobId, 
            progress -> updateCount.incrementAndGet());

        progressTracker.updateProgress(jobId, 1, "Step 1");
        
        // WHEN
        subscription.unsubscribe();
        progressTracker.updateProgress(jobId, 2, "Step 2");

        // THEN
        Thread.sleep(100); // Small delay to ensure update would have been delivered
        assertThat(updateCount.get()).isEqualTo(2); // Initial + first update only
    }

    @Test
    @DisplayName("Should calculate estimated remaining time")
    void shouldCalculateEstimatedRemainingTime() throws InterruptedException {
        // GIVEN
        String jobId = "job-006";
        progressTracker.initializeJob(jobId, 10, "Long running job");
        
        // Simulate some time passing
        Thread.sleep(100);
        progressTracker.updateProgress(jobId, 5, "Halfway done");

        // WHEN
        JobProgressTracker.JobProgress progress = progressTracker.getProgress(jobId);
        Duration remaining = progress.estimatedRemaining();

        // THEN
        assertThat(remaining).isNotNull();
        assertThat(remaining.toMillis()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should get jobs by status")
    void shouldGetJobsByStatus() {
        // GIVEN
        progressTracker.initializeJob("pending-1", 5, "Pending job 1");
        progressTracker.initializeJob("pending-2", 5, "Pending job 2");
        progressTracker.initializeJob("in-progress-1", 5, "In progress job");
        progressTracker.updateProgress("in-progress-1", 2, "Working");
        progressTracker.initializeJob("completed-1", 5, "Completed job");
        progressTracker.completeJob("completed-1", "Done");

        // WHEN
        var pendingJobs = progressTracker.getJobsByStatus(JobProgressTracker.JobProgressStatus.PENDING);
        var inProgressJobs = progressTracker.getJobsByStatus(JobProgressTracker.JobProgressStatus.IN_PROGRESS);
        var completedJobs = progressTracker.getJobsByStatus(JobProgressTracker.JobProgressStatus.COMPLETED);

        // THEN
        assertThat(pendingJobs).hasSize(2);
        assertThat(inProgressJobs).hasSize(1);
        assertThat(completedJobs).hasSize(1);
    }

    @Test
    @DisplayName("Should get batch progress")
    void shouldGetBatchProgress() {
        // GIVEN
        progressTracker.initializeJob("batch-1", 10, "Job 1");
        progressTracker.initializeJob("batch-2", 10, "Job 2");
        progressTracker.initializeJob("batch-3", 10, "Job 3");
        
        progressTracker.updateProgress("batch-1", 5, "Halfway");
        progressTracker.updateProgress("batch-2", 10, "Done");
        progressTracker.updateProgress("batch-3", 2, "Starting");

        // WHEN
        var batchProgress = progressTracker.getProgressBatch(
            java.util.List.of("batch-1", "batch-2", "batch-3", "non-existent"));

        // THEN
        assertThat(batchProgress).hasSize(3);
        assertThat(batchProgress.get("batch-1").progressPercent()).isEqualTo(50);
        assertThat(batchProgress.get("batch-2").progressPercent()).isEqualTo(100);
        assertThat(batchProgress.get("batch-3").progressPercent()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should record elapsed time")
    void shouldRecordElapsedTime() throws InterruptedException {
        // GIVEN
        String jobId = "job-007";
        progressTracker.initializeJob(jobId, 5, "Timed job");
        
        Thread.sleep(50);
        progressTracker.completeJob(jobId, "Done");

        // WHEN
        JobProgressTracker.JobProgress progress = progressTracker.getProgress(jobId);
        Duration elapsed = progress.elapsed();

        // THEN
        assertThat(elapsed.toMillis()).isGreaterThanOrEqualTo(50);
    }
}
