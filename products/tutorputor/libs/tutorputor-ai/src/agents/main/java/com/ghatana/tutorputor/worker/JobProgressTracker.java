package com.ghatana.tutorputor.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Progress tracker for content generation jobs.
 * 
 * <p>Provides real-time progress updates for long-running jobs through
 * a subscription-based model. Supports both polling and push-based updates.
 *
 * @doc.type class
 * @doc.purpose Job progress tracking and notification
 * @doc.layer product
 * @doc.pattern Observer
 */
public class JobProgressTracker {

    private static final Logger LOG = LoggerFactory.getLogger(JobProgressTracker.class);

    private final ConcurrentMap<String, JobProgress> progressMap;
    private final ConcurrentMap<String, Set<Consumer<JobProgress>>> subscribers;
    private final ScheduledExecutorService cleanupExecutor;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter progressUpdatesCounter;
    private final Counter subscriptionsCounter;
    private final Timer updateLatencyTimer;

    /**
     * Creates a new progress tracker.
     *
     * @param meterRegistry the metrics registry
     */
    public JobProgressTracker(@NotNull MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.progressMap = new ConcurrentHashMap<>();
        this.subscribers = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofPlatform().name("progress-cleanup").daemon(true).unstarted(r));
        
        // Initialize metrics
        this.progressUpdatesCounter = Counter.builder("tutorputor.progress.updates")
            .description("Number of progress updates")
            .register(meterRegistry);
        this.subscriptionsCounter = Counter.builder("tutorputor.progress.subscriptions")
            .description("Number of active subscriptions")
            .register(meterRegistry);
        this.updateLatencyTimer = Timer.builder("tutorputor.progress.update_latency")
            .description("Latency of progress updates")
            .register(meterRegistry);
        
        // Start cleanup task
        cleanupExecutor.scheduleAtFixedRate(this::cleanupOldProgress, 
            5, 5, TimeUnit.MINUTES);
        
        LOG.info("JobProgressTracker initialized");
    }

    /**
     * Initializes tracking for a job.
     *
     * @param jobId the job ID
     * @param totalSteps total number of steps
     * @param description job description
     */
    public void initializeJob(
            @NotNull String jobId, 
            int totalSteps, 
            @NotNull String description) {
        JobProgress progress = new JobProgress(
            jobId,
            description,
            0,
            totalSteps,
            JobProgressStatus.PENDING,
            null,
            null,
            Instant.now(),
            null,
            List.of()
        );
        progressMap.put(jobId, progress);
        notifySubscribers(jobId, progress);
        LOG.debug("Initialized progress tracking for job: {}", jobId);
    }

    /**
     * Updates job progress.
     *
     * @param jobId the job ID
     * @param completedSteps number of completed steps
     * @param currentStep description of current step
     */
    public void updateProgress(
            @NotNull String jobId, 
            int completedSteps, 
            @NotNull String currentStep) {
        Instant start = Instant.now();
        
        JobProgress existing = progressMap.get(jobId);
        if (existing == null) {
            LOG.warn("Cannot update progress for unknown job: {}", jobId);
            return;
        }
        
        List<String> steps = new ArrayList<>(existing.steps());
        steps.add(currentStep);
        
        JobProgress updated = new JobProgress(
            jobId,
            existing.description(),
            completedSteps,
            existing.totalSteps(),
            JobProgressStatus.IN_PROGRESS,
            currentStep,
            null,
            existing.startedAt(),
            null,
            steps
        );
        
        progressMap.put(jobId, updated);
        notifySubscribers(jobId, updated);
        
        progressUpdatesCounter.increment();
        updateLatencyTimer.record(Duration.between(start, Instant.now()));
        
        LOG.debug("Updated progress for job {}: {}/{} - {}", 
            jobId, completedSteps, existing.totalSteps(), currentStep);
    }

    /**
     * Marks a job as completed.
     *
     * @param jobId the job ID
     * @param resultSummary summary of the result
     */
    public void completeJob(@NotNull String jobId, @NotNull String resultSummary) {
        JobProgress existing = progressMap.get(jobId);
        if (existing == null) {
            LOG.warn("Cannot complete unknown job: {}", jobId);
            return;
        }
        
        JobProgress completed = new JobProgress(
            jobId,
            existing.description(),
            existing.totalSteps(),
            existing.totalSteps(),
            JobProgressStatus.COMPLETED,
            resultSummary,
            null,
            existing.startedAt(),
            Instant.now(),
            existing.steps()
        );
        
        progressMap.put(jobId, completed);
        notifySubscribers(jobId, completed);
        
        LOG.info("Job completed: {} in {}ms", jobId, 
            Duration.between(existing.startedAt(), Instant.now()).toMillis());
    }

    /**
     * Marks a job as failed.
     *
     * @param jobId the job ID
     * @param error the error message
     */
    public void failJob(@NotNull String jobId, @NotNull String error) {
        JobProgress existing = progressMap.get(jobId);
        if (existing == null) {
            LOG.warn("Cannot fail unknown job: {}", jobId);
            return;
        }
        
        JobProgress failed = new JobProgress(
            jobId,
            existing.description(),
            existing.completedSteps(),
            existing.totalSteps(),
            JobProgressStatus.FAILED,
            existing.currentStep(),
            error,
            existing.startedAt(),
            Instant.now(),
            existing.steps()
        );
        
        progressMap.put(jobId, failed);
        notifySubscribers(jobId, failed);
        
        LOG.warn("Job failed: {} - {}", jobId, error);
    }

    /**
     * Gets current progress for a job.
     *
     * @param jobId the job ID
     * @return the progress, or null if not found
     */
    public JobProgress getProgress(@NotNull String jobId) {
        return progressMap.get(jobId);
    }

    /**
     * Subscribes to progress updates for a job.
     *
     * @param jobId the job ID
     * @param callback the callback to invoke on updates
     * @return a subscription that can be used to unsubscribe
     */
    public Subscription subscribe(
            @NotNull String jobId, 
            @NotNull Consumer<JobProgress> callback) {
        subscribers.computeIfAbsent(jobId, k -> ConcurrentHashMap.newKeySet())
            .add(callback);
        subscriptionsCounter.increment();
        
        // Send current progress immediately if available
        JobProgress current = progressMap.get(jobId);
        if (current != null) {
            callback.accept(current);
        }
        
        return () -> {
            Set<Consumer<JobProgress>> subs = subscribers.get(jobId);
            if (subs != null) {
                subs.remove(callback);
            }
        };
    }

    /**
     * Gets progress for multiple jobs.
     *
     * @param jobIds the job IDs
     * @return map of job ID to progress
     */
    public Map<String, JobProgress> getProgressBatch(@NotNull Collection<String> jobIds) {
        Map<String, JobProgress> result = new HashMap<>();
        for (String jobId : jobIds) {
            JobProgress progress = progressMap.get(jobId);
            if (progress != null) {
                result.put(jobId, progress);
            }
        }
        return result;
    }

    /**
     * Gets all jobs by status.
     *
     * @param status the status to filter by
     * @return list of matching jobs
     */
    public List<JobProgress> getJobsByStatus(@NotNull JobProgressStatus status) {
        return progressMap.values().stream()
            .filter(p -> p.status() == status)
            .toList();
    }

    /**
     * Shuts down the tracker.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        progressMap.clear();
        subscribers.clear();
        LOG.info("JobProgressTracker shut down");
    }

    private void notifySubscribers(String jobId, JobProgress progress) {
        Set<Consumer<JobProgress>> subs = subscribers.get(jobId);
        if (subs != null && !subs.isEmpty()) {
            for (Consumer<JobProgress> callback : subs) {
                try {
                    callback.accept(progress);
                } catch (Exception e) {
                    LOG.warn("Error notifying subscriber for job {}", jobId, e);
                }
            }
        }
    }

    private void cleanupOldProgress() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(2));
        
        progressMap.entrySet().removeIf(entry -> {
            JobProgress progress = entry.getValue();
            if (progress.completedAt() != null && progress.completedAt().isBefore(cutoff)) {
                subscribers.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        LOG.debug("Cleaned up old progress entries. Remaining: {}", progressMap.size());
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Job progress status.
     */
    public enum JobProgressStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Job progress data.
     */
    public record JobProgress(
        String jobId,
        String description,
        int completedSteps,
        int totalSteps,
        JobProgressStatus status,
        String currentStep,
        String error,
        Instant startedAt,
        Instant completedAt,
        List<String> steps
    ) {
        /**
         * Gets progress as a percentage.
         *
         * @return progress percentage (0-100)
         */
        public int progressPercent() {
            if (totalSteps == 0) return 0;
            return (int) Math.round((completedSteps * 100.0) / totalSteps);
        }

        /**
         * Gets elapsed time.
         *
         * @return elapsed duration
         */
        public Duration elapsed() {
            Instant end = completedAt != null ? completedAt : Instant.now();
            return Duration.between(startedAt, end);
        }

        /**
         * Estimates remaining time based on current progress.
         *
         * @return estimated remaining duration, or null if cannot estimate
         */
        public Duration estimatedRemaining() {
            if (completedSteps == 0 || status != JobProgressStatus.IN_PROGRESS) {
                return null;
            }
            long elapsedMs = elapsed().toMillis();
            long msPerStep = elapsedMs / completedSteps;
            int remainingSteps = totalSteps - completedSteps;
            return Duration.ofMillis(msPerStep * remainingSteps);
        }
    }

    /**
     * Subscription handle for unsubscribing.
     */
    @FunctionalInterface
    public interface Subscription {
        void unsubscribe();
    }
}
