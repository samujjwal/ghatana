package com.ghatana.tutorputor.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Production-ready job queue for background content generation tasks.
 * 
 * <p>Features:
 * <ul>
 *   <li>Priority-based job scheduling</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Dead letter queue for failed jobs</li>
 *   <li>Job status tracking and callbacks</li>
 *   <li>Rate limiting and concurrency control</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Background job queue for content generation
 * @doc.layer product
 * @doc.pattern Producer-Consumer
 */
public class ContentJobQueue {

    private static final Logger LOG = LoggerFactory.getLogger(ContentJobQueue.class);
    
    private final PriorityBlockingQueue<ContentJob> jobQueue;
    private final ConcurrentMap<String, ContentJob> activeJobs;
    private final ConcurrentMap<String, ContentJob> completedJobs;
    private final BlockingQueue<ContentJob> deadLetterQueue;
    private final ConcurrentMap<ContentJob.JobType, Function<ContentJob, Object>> jobHandlers;
    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;
    private final MeterRegistry meterRegistry;
    
    // Configuration
    private final int maxRetries;
    private final Duration baseRetryDelay;
    private final int maxConcurrentJobs;
    
    // Metrics
    private final Counter jobsSubmittedCounter;
    private final Counter jobsCompletedCounter;
    private final Counter jobsFailedCounter;
    private final Counter jobsRetriedCounter;
    private final Timer jobProcessingTimer;
    
    // Callbacks
    private Consumer<ContentJob> onJobCompleted;
    private Consumer<ContentJob> onJobFailed;
    
    private volatile boolean running = false;

    /**
     * Creates a new ContentJobQueue.
     *
     * @param config the queue configuration
     * @param meterRegistry the metrics registry
     */
    public ContentJobQueue(@NotNull QueueConfig config, @NotNull MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.maxRetries = config.maxRetries();
        this.baseRetryDelay = config.baseRetryDelay();
        this.maxConcurrentJobs = config.maxConcurrentJobs();
        
        this.jobQueue = new PriorityBlockingQueue<>(100, 
            (a, b) -> Integer.compare(b.priority(), a.priority()));
        this.activeJobs = new ConcurrentHashMap<>();
        this.completedJobs = new ConcurrentHashMap<>();
        this.deadLetterQueue = new LinkedBlockingQueue<>();
        this.jobHandlers = new ConcurrentHashMap<>();
        this.workerPool = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Initialize metrics
        this.jobsSubmittedCounter = Counter.builder("tutorputor.jobs.submitted")
            .description("Number of jobs submitted")
            .register(meterRegistry);
        this.jobsCompletedCounter = Counter.builder("tutorputor.jobs.completed")
            .description("Number of jobs completed")
            .register(meterRegistry);
        this.jobsFailedCounter = Counter.builder("tutorputor.jobs.failed")
            .description("Number of jobs failed")
            .register(meterRegistry);
        this.jobsRetriedCounter = Counter.builder("tutorputor.jobs.retried")
            .description("Number of jobs retried")
            .register(meterRegistry);
        this.jobProcessingTimer = Timer.builder("tutorputor.jobs.processing_time")
            .description("Job processing time")
            .register(meterRegistry);
        
        LOG.info("ContentJobQueue initialized with maxConcurrent={}, maxRetries={}", 
            maxConcurrentJobs, maxRetries);
    }

    /**
     * Starts the job queue processing.
     */
    public void start() {
        if (running) {
            LOG.warn("Job queue already running");
            return;
        }
        
        running = true;
        
        // Start worker threads
        for (int i = 0; i < maxConcurrentJobs; i++) {
            workerPool.submit(this::processJobs);
        }
        
        // Start cleanup scheduler
        scheduler.scheduleAtFixedRate(this::cleanupCompletedJobs, 
            5, 5, TimeUnit.MINUTES);
        
        LOG.info("ContentJobQueue started with {} workers", maxConcurrentJobs);
    }

    /**
     * Stops the job queue gracefully.
     *
     * @param timeout maximum time to wait for jobs to complete
     */
    public void stop(Duration timeout) {
        LOG.info("Stopping ContentJobQueue...");
        running = false;
        
        try {
            workerPool.shutdown();
            if (!workerPool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                workerPool.shutdownNow();
            }
            scheduler.shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
        }
        
        LOG.info("ContentJobQueue stopped. Pending jobs: {}", jobQueue.size());
    }

    /**
     * Submits a job for processing.
     *
     * @param job the job to submit
     * @return the job ID
     */
    public String submit(@NotNull ContentJob job) {
        String jobId = job.id() != null ? job.id() : UUID.randomUUID().toString();
        ContentJob jobWithId = job.withId(jobId);
        
        jobQueue.offer(jobWithId);
        jobsSubmittedCounter.increment();
        
        LOG.debug("Job submitted: {} (type={}, priority={})", 
            jobId, job.type(), job.priority());
        
        return jobId;
    }

    /**
     * Gets the status of a job.
     *
     * @param jobId the job ID
     * @return the job status
     */
    public JobStatus getStatus(String jobId) {
        if (activeJobs.containsKey(jobId)) {
            return JobStatus.PROCESSING;
        }
        
        ContentJob completed = completedJobs.get(jobId);
        if (completed != null) {
            return completed.error() != null ? JobStatus.FAILED : JobStatus.COMPLETED;
        }
        
        for (ContentJob job : jobQueue) {
            if (jobId.equals(job.id())) {
                return JobStatus.QUEUED;
            }
        }
        
        for (ContentJob job : deadLetterQueue) {
            if (jobId.equals(job.id())) {
                return JobStatus.DEAD_LETTER;
            }
        }
        
        return JobStatus.UNKNOWN;
    }

    /**
     * Gets a completed job's result.
     *
     * @param jobId the job ID
     * @return the job, or null if not found
     */
    public ContentJob getResult(String jobId) {
        return completedJobs.get(jobId);
    }

    /**
     * Sets callback for job completion.
     *
     * @param callback the callback
     */
    public void onJobCompleted(Consumer<ContentJob> callback) {
        this.onJobCompleted = callback;
    }

    /**
     * Sets callback for job failure.
     *
     * @param callback the callback
     */
    public void onJobFailed(Consumer<ContentJob> callback) {
        this.onJobFailed = callback;
    }

    /**
     * Registers a handler for a job type.
     *
     * @param type job type
     * @param handler handler implementation
     */
    public void registerHandler(
            @NotNull ContentJob.JobType type,
            @NotNull Function<ContentJob, Object> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        jobHandlers.put(type, handler);
        LOG.info("Registered job handler for {}", type);
    }

    /**
     * Gets queue statistics.
     *
     * @return queue statistics
     */
    public QueueStats getStats() {
        return new QueueStats(
            jobQueue.size(),
            activeJobs.size(),
            completedJobs.size(),
            deadLetterQueue.size()
        );
    }

    private void processJobs() {
        while (running) {
            try {
                ContentJob job = jobQueue.poll(1, TimeUnit.SECONDS);
                if (job == null) continue;
                
                // Check if we can process (concurrency limit)
                if (activeJobs.size() >= maxConcurrentJobs) {
                    jobQueue.offer(job); // Put back
                    Thread.sleep(100);
                    continue;
                }
                
                processJob(job);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Error in job processing loop", e);
            }
        }
    }

    private void processJob(ContentJob job) {
        String jobId = job.id();
        activeJobs.put(jobId, job);
        
        Instant start = Instant.now();
        LOG.info("Processing job: {} (type={}, attempt={})", 
            jobId, job.type(), job.attempts() + 1);
        
        try {
            // Execute the job
            Object result = executeJob(job);
            
            // Mark as completed
            ContentJob completedJob = job
                .withResult(result)
                .withCompletedAt(Instant.now());
            
            activeJobs.remove(jobId);
            completedJobs.put(jobId, completedJob);
            
            jobsCompletedCounter.increment();
            jobProcessingTimer.record(Duration.between(start, Instant.now()));
            
            LOG.info("Job completed: {} in {}ms", jobId, 
                Duration.between(start, Instant.now()).toMillis());
            
            if (onJobCompleted != null) {
                onJobCompleted.accept(completedJob);
            }
            
        } catch (Exception e) {
            handleJobFailure(job, e, start);
        }
    }

    private Object executeJob(ContentJob job) {
        Function<ContentJob, Object> handler = jobHandlers.get(job.type());
        if (handler == null) {
            throw new IllegalStateException("No registered handler for job type: " + job.type());
        }
        return handler.apply(job);
    }

    private void handleJobFailure(ContentJob job, Exception error, Instant start) {
        String jobId = job.id();
        activeJobs.remove(jobId);
        
        int attempts = job.attempts() + 1;
        
        LOG.warn("Job {} failed (attempt {}): {}", jobId, attempts, error.getMessage());
        
        if (attempts < maxRetries) {
            // Retry with exponential backoff
            Duration delay = baseRetryDelay.multipliedBy((long) Math.pow(2, attempts - 1));
            ContentJob retryJob = job
                .withAttempts(attempts)
                .withError(error.getMessage());
            
            scheduler.schedule(() -> {
                jobQueue.offer(retryJob);
                jobsRetriedCounter.increment();
                LOG.info("Job {} scheduled for retry in {}ms", jobId, delay.toMillis());
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
            
        } else {
            // Move to dead letter queue
            ContentJob failedJob = job
                .withAttempts(attempts)
                .withError(error.getMessage())
                .withCompletedAt(Instant.now());
            
            deadLetterQueue.offer(failedJob);
            jobsFailedCounter.increment();
            
            LOG.error("Job {} moved to dead letter queue after {} attempts", 
                jobId, attempts);
            
            if (onJobFailed != null) {
                onJobFailed.accept(failedJob);
            }
        }
        
        jobProcessingTimer.record(Duration.between(start, Instant.now()));
    }

    private void cleanupCompletedJobs() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        
        completedJobs.entrySet().removeIf(entry -> {
            Instant completedAt = entry.getValue().completedAt();
            return completedAt != null && completedAt.isBefore(cutoff);
        });
        
        LOG.debug("Cleaned up completed jobs. Remaining: {}", completedJobs.size());
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Job status.
     */
    public enum JobStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED,
        DEAD_LETTER,
        UNKNOWN
    }

    /**
     * Queue configuration.
     */
    public record QueueConfig(
        int maxConcurrentJobs,
        int maxRetries,
        Duration baseRetryDelay
    ) {
        public static QueueConfig defaults() {
            return new QueueConfig(10, 3, Duration.ofSeconds(5));
        }
    }

    /**
     * Queue statistics.
     */
    public record QueueStats(
        int queuedJobs,
        int activeJobs,
        int completedJobs,
        int deadLetterJobs
    ) {}
}
