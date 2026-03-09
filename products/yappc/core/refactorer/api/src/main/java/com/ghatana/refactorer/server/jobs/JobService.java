package com.ghatana.refactorer.server.jobs;

import com.ghatana.yappc.refactorer.event.EventBus;
import com.ghatana.refactorer.server.auth.AccessPolicy;
import com.ghatana.refactorer.server.observability.OTelInitializer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coordinates queueing and status management for jobs.
 *
 * @doc.type class
 * @doc.purpose Implement the core business rules behind job submission,
 * cancellation, and lifecycle transitions.
 * @doc.layer product
 * @doc.pattern Service
 */
public final class JobService {

    private static final Logger logger = LogManager.getLogger(JobService.class);

    private final JobQueue jobQueue;
    private final JobStore jobStore;
    private final AccessPolicy accessPolicy;
    private final EventBus eventBus;
    private final Counter jobsSubmittedCounter;
    private final Counter jobsCancelledCounter;
    private final Counter jobsSucceededCounter;
    private final Counter jobsFailedCounter;
    private final Timer jobDurationTimer;

    public JobService(JobQueue jobQueue, JobStore jobStore, AccessPolicy accessPolicy, EventBus eventBus) {
        this.jobQueue = Objects.requireNonNull(jobQueue, "jobQueue must not be null");
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore must not be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");

        MeterRegistry registry = OTelInitializer.getMeterRegistry();
        this.jobsSubmittedCounter = registry.counter("polyfix.jobs.submitted");
        this.jobsCancelledCounter = registry.counter("polyfix.jobs.cancelled");
        this.jobsSucceededCounter = registry.counter("polyfix.jobs.succeeded");
        this.jobsFailedCounter = registry.counter("polyfix.jobs.failed");
        this.jobDurationTimer = registry.timer("polyfix.jobs.duration");

        Gauge.builder("polyfix.jobs.queue.size", jobQueue, JobQueue::size).register(registry);
    }

    /**
     * Enqueues a new job for execution.
     */
    public JobRecord submit(JobSubmission submission) {
        Objects.requireNonNull(submission, "submission must not be null");
        accessPolicy.ensureAuthenticated(submission.tenantContext());

        String jobId = generateJobId();
        String tenantId = submission.tenantContext().tenantId();
        JobRecord record
                = JobRecord.newQueued(
                        jobId, tenantId, submission.attributes());
        jobStore.create(record);
        jobQueue.enqueue(record);
        jobsSubmittedCounter.increment();

        // Emit job.started event to EventCloud
        try {
            eventBus.publish(JobEvents.jobStarted(jobId, tenantId, "refactor"))
                    .whenComplete((res, ex) -> {
                        if (ex == null) {
                            logger.info("Emitted job.started event for job {}", jobId);
                        } else {
                            logger.warn("Failed to emit job.started event for job {}: {}", jobId, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            logger.warn("Error publishing job.started event: {}", e.getMessage());
        }

        return record;
    }

    /**
     * Returns the current job record if it exists.
     */
    public Optional<JobRecord> get(String jobId) {
        return jobStore.get(jobId);
    }

    /**
     * Cancels a job if it exists.
     */
    public Optional<JobRecord> cancel(String jobId) {
        jobQueue.remove(jobId);
        Optional<JobRecord> result
                = jobStore.update(
                        jobId,
                        current
                        -> current.transition(
                                JobState.CANCELLED,
                                current.currentPass(),
                                "Cancelled by request"));
        result.ifPresent(
                record -> {
                    jobsCancelledCounter.increment();
                    recordJobDuration(record);

                    // Emit job.cancelled event
                    try {
                        eventBus.publish(JobEvents.jobCancelled(jobId, record.tenantId(), "refactor"))
                                .whenComplete((res, ex) -> {
                                    if (ex == null) {
                                        logger.info("Emitted job.cancelled event for job {}", jobId);
                                    } else {
                                        logger.warn("Failed to emit job.cancelled event for job {}: {}", jobId, ex.getMessage());
                                    }
                                });
                    } catch (Exception e) {
                        logger.warn("Error publishing job.cancelled event: {}", e.getMessage());
                    }
                });
        return result;
    }

    /**
     * Marks a job as running.
     */
    public Optional<JobRecord> markRunning(String jobId, int pass) {
        return jobStore.update(
                jobId,
                current -> current.transition(JobState.RUNNING, pass, current.errorMessage()));
    }

    /**
     * Marks a job as completed successfully.
     */
    public Optional<JobRecord> markSucceeded(String jobId, int pass) {
        Optional<JobRecord> result
                = jobStore.update(
                        jobId, current -> current.transition(JobState.SUCCEEDED, pass, null));
        result.ifPresent(
                record -> {
                    jobsSucceededCounter.increment();
                    recordJobDuration(record);

                    // Emit job.completed event
                    long durationMs = Math.max(0, record.updatedAt() - record.createdAt());
                    try {
                        eventBus.publish(JobEvents.jobCompleted(jobId, record.tenantId(), "refactor", durationMs))
                                .whenComplete((res, ex) -> {
                                    if (ex == null) {
                                        logger.info("Emitted job.completed event for job {}", jobId);
                                    } else {
                                        logger.warn("Failed to emit job.completed event for job {}: {}", jobId, ex.getMessage());
                                    }
                                });
                    } catch (Exception e) {
                        logger.warn("Error publishing job.completed event: {}", e.getMessage());
                    }
                });
        return result;
    }

    /**
     * Marks a job as failed with the given error message.
     */
    public Optional<JobRecord> markFailed(String jobId, int pass, String error) {
        Optional<JobRecord> result
                = jobStore.update(jobId, current -> current.transition(JobState.FAILED, pass, error));
        result.ifPresent(
                record -> {
                    jobsFailedCounter.increment();
                    recordJobDuration(record);

                    // Emit job.failed event
                    try {
                        eventBus.publish(JobEvents.jobFailed(jobId, record.tenantId(), "refactor", error))
                                .whenComplete((res, ex) -> {
                                    if (ex == null) {
                                        logger.info("Emitted job.failed event for job {}", jobId);
                                    } else {
                                        logger.warn("Failed to emit job.failed event for job {}: {}", jobId, ex.getMessage());
                                    }
                                });
                    } catch (Exception e) {
                        logger.warn("Error publishing job.failed event: {}", e.getMessage());
                    }
                });
        return result;
    }

    /**
     * Number of queued jobs.
     */
    public int queuedJobs() {
        return jobQueue.size();
    }

    private String generateJobId() {
        return "job-" + UUID.randomUUID();
    }

    private void recordJobDuration(JobRecord record) {
        long durationMillis = Math.max(0, record.updatedAt() - record.createdAt());
        jobDurationTimer.record(Duration.ofMillis(durationMillis));
    }
}
