package com.ghatana.datacloud.backpressure;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Backpressure management for Data-Cloud operations.
 *
 * <p><b>Purpose</b><br>
 * Prevents system overload by managing request flow:
 * <ul>
 *   <li>Semaphore-based concurrency limiting</li>
 *   <li>Queue-based request buffering</li>
 *   <li>Adaptive rate adjustment</li>
 *   <li>Load shedding under pressure</li>
 * </ul>
 *
 * <p><b>Six Pillars Compliance</b><br>
 * <ul>
 *   <li><b>Scalability</b>: Graceful degradation under load</li>
 *   <li><b>Reliability</b>: Prevents cascading failures</li>
 *   <li><b>Observability</b>: Exposes backpressure metrics</li>
 * </ul>
 *
 * <p><b>Backpressure Strategies</b><br>
 * <pre>
 * 1. DROP      - Reject new requests when overloaded
 * 2. BUFFER    - Queue requests up to limit
 * 3. THROTTLE  - Slow down request processing
 * 4. ADAPTIVE  - Dynamically adjust based on load
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * BackpressureManager bp = BackpressureManager.builder()
 *     .maxConcurrent(100)
 *     .queueCapacity(1000)
 *     .strategy(BackpressureStrategy.ADAPTIVE)
 *     .build();
 * 
 * // Execute with backpressure
 * Promise<T> result = bp.execute(() -> {
 *     return expensiveOperation();
 * });
 * 
 * // Or with priority
 * Promise<T> result = bp.execute(Priority.HIGH, () -> {
 *     return criticalOperation();
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Backpressure management for Data-Cloud
 * @doc.layer infrastructure
 * @doc.pattern Bulkhead, Circuit Breaker
 */
public class BackpressureManager {
    private static final Logger logger = LoggerFactory.getLogger(BackpressureManager.class);
    
    /**
     * Backpressure strategies.
     */
    public enum BackpressureStrategy {
        /** Drop new requests when overloaded */
        DROP,
        /** Buffer requests up to queue capacity */
        BUFFER,
        /** Slow down processing rate */
        THROTTLE,
        /** Dynamically adjust based on load */
        ADAPTIVE
    }
    
    /**
     * Request priority levels.
     */
    public enum Priority {
        LOW(0), NORMAL(1), HIGH(2), CRITICAL(3);
        
        private final int value;
        
        Priority(int value) {
            this.value = value;
        }
        
        public int getValue() { return value; }
    }
    
    // Configuration
    private final int maxConcurrent;
    private final int queueCapacity;
    private final BackpressureStrategy strategy;
    private final Duration timeout;
    
    // State
    private final Semaphore semaphore;
    private final BlockingQueue<PendingRequest<?>> pendingQueue;
    private final AtomicInteger activeRequests;
    private final AtomicLong totalRequests;
    private final AtomicLong droppedRequests;
    private final AtomicLong completedRequests;
    private final AtomicLong failedRequests;
    
    // Adaptive state
    private volatile double currentLoadFactor;
    private volatile int adaptiveLimit;
    private final ScheduledExecutorService adaptiveScheduler;
    
    // Metrics
    private final BackpressureMetrics metrics;
    
    private BackpressureManager(Builder builder) {
        this.maxConcurrent = builder.maxConcurrent;
        this.queueCapacity = builder.queueCapacity;
        this.strategy = builder.strategy;
        this.timeout = builder.timeout;
        
        this.semaphore = new Semaphore(maxConcurrent);
        this.pendingQueue = new PriorityBlockingQueue<>(queueCapacity, 
            Comparator.comparingInt(r -> -r.priority.getValue()));
        this.activeRequests = new AtomicInteger(0);
        this.totalRequests = new AtomicLong(0);
        this.droppedRequests = new AtomicLong(0);
        this.completedRequests = new AtomicLong(0);
        this.failedRequests = new AtomicLong(0);
        
        this.currentLoadFactor = 0.0;
        this.adaptiveLimit = maxConcurrent;
        this.metrics = new BackpressureMetrics(this);
        
        if (strategy == BackpressureStrategy.ADAPTIVE) {
            this.adaptiveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "backpressure-adaptive");
                t.setDaemon(true);
                return t;
            });
            startAdaptiveMonitoring();
        } else {
            this.adaptiveScheduler = null;
        }
    }
    
    /**
     * Execute operation with backpressure control.
     *
     * @param operation the operation to execute
     * @return promise with result
     */
    public <T> Promise<T> execute(Supplier<Promise<T>> operation) {
        return execute(Priority.NORMAL, operation);
    }
    
    /**
     * Execute operation with priority and backpressure control.
     *
     * @param priority the request priority
     * @param operation the operation to execute
     * @return promise with result
     */
    public <T> Promise<T> execute(Priority priority, Supplier<Promise<T>> operation) {
        totalRequests.incrementAndGet();
        
        return switch (strategy) {
            case DROP -> executeWithDrop(priority, operation);
            case BUFFER -> executeWithBuffer(priority, operation);
            case THROTTLE -> executeWithThrottle(priority, operation);
            case ADAPTIVE -> executeWithAdaptive(priority, operation);
        };
    }
    
    /**
     * Execute synchronously with backpressure.
     *
     * @param operation the operation to execute
     * @return the result
     */
    public <T> T executeSync(Supplier<T> operation) throws BackpressureException {
        return executeSync(Priority.NORMAL, operation);
    }
    
    /**
     * Execute synchronously with priority and backpressure.
     *
     * @param priority the request priority
     * @param operation the operation to execute
     * @return the result
     */
    public <T> T executeSync(Priority priority, Supplier<T> operation) throws BackpressureException {
        totalRequests.incrementAndGet();
        
        if (!tryAcquire(priority)) {
            droppedRequests.incrementAndGet();
            throw new BackpressureException("System overloaded, request rejected");
        }
        
        try {
            activeRequests.incrementAndGet();
            T result = operation.get();
            completedRequests.incrementAndGet();
            return result;
        } catch (Exception e) {
            failedRequests.incrementAndGet();
            throw e;
        } finally {
            activeRequests.decrementAndGet();
            semaphore.release();
        }
    }
    
    // ==================== Strategy Implementations ====================
    
    private <T> Promise<T> executeWithDrop(Priority priority, Supplier<Promise<T>> operation) {
        if (!semaphore.tryAcquire()) {
            droppedRequests.incrementAndGet();
            logger.warn("Request dropped due to backpressure (active: {}, max: {})", 
                activeRequests.get(), maxConcurrent);
            return Promise.ofException(new BackpressureException("System overloaded, request dropped"));
        }
        
        return executeWithSemaphore(operation);
    }
    
    private <T> Promise<T> executeWithBuffer(Priority priority, Supplier<Promise<T>> operation) {
        if (semaphore.tryAcquire()) {
            return executeWithSemaphore(operation);
        }
        
        // Try to queue
        if (pendingQueue.size() >= queueCapacity) {
            droppedRequests.incrementAndGet();
            logger.warn("Request dropped - queue full (queue: {}, capacity: {})",
                pendingQueue.size(), queueCapacity);
            return Promise.ofException(new BackpressureException("Queue capacity exceeded"));
        }
        
        // Add to queue and return promise
        CompletableFuture<T> future = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        PendingRequest<T> request = new PendingRequest<>(priority, 
            (Supplier<Promise<Object>>) (Supplier<?>) operation, 
            (CompletableFuture<Object>) (CompletableFuture<?>) future);
        
        pendingQueue.offer(request);
        logger.debug("Request queued (queue size: {})", pendingQueue.size());
        
        // Try to process queue
        processQueue();
        
        return Promise.ofFuture(future);
    }
    
    private <T> Promise<T> executeWithThrottle(Priority priority, Supplier<Promise<T>> operation) {
        try {
            // Wait with timeout
            if (!semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                droppedRequests.incrementAndGet();
                logger.warn("Request timed out waiting for slot");
                return Promise.ofException(new BackpressureException("Request timed out waiting for slot"));
            }
            return executeWithSemaphore(operation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Promise.ofException(new BackpressureException("Request interrupted"));
        }
    }
    
    private <T> Promise<T> executeWithAdaptive(Priority priority, Supplier<Promise<T>> operation) {
        // Use adaptive limit
        int currentLimit = Math.max(1, adaptiveLimit);
        
        if (activeRequests.get() >= currentLimit) {
            // Check priority - critical always allowed
            if (priority != Priority.CRITICAL) {
                if (pendingQueue.size() >= queueCapacity) {
                    droppedRequests.incrementAndGet();
                    return Promise.ofException(new BackpressureException("Adaptive limit reached"));
                }
                
                // Queue non-critical requests
                CompletableFuture<T> future = new CompletableFuture<>();
                @SuppressWarnings("unchecked")
                PendingRequest<T> request = new PendingRequest<>(priority,
                    (Supplier<Promise<Object>>) (Supplier<?>) operation,
                    (CompletableFuture<Object>) (CompletableFuture<?>) future);
                pendingQueue.offer(request);
                processQueue();
                return Promise.ofFuture(future);
            }
        }
        
        if (!semaphore.tryAcquire()) {
            // Force acquire for critical
            if (priority == Priority.CRITICAL) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Promise.ofException(new BackpressureException("Interrupted"));
                }
            } else {
                return executeWithBuffer(priority, operation);
            }
        }
        
        return executeWithSemaphore(operation);
    }
    
    private <T> Promise<T> executeWithSemaphore(Supplier<Promise<T>> operation) {
        activeRequests.incrementAndGet();
        
        try {
            return operation.get()
                .whenComplete((result, error) -> {
                    activeRequests.decrementAndGet();
                    semaphore.release();
                    
                    if (error != null) {
                        failedRequests.incrementAndGet();
                    } else {
                        completedRequests.incrementAndGet();
                    }
                    
                    // Process queued requests
                    processQueue();
                });
        } catch (Exception e) {
            activeRequests.decrementAndGet();
            semaphore.release();
            failedRequests.incrementAndGet();
            return Promise.ofException(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void processQueue() {
        while (!pendingQueue.isEmpty() && semaphore.tryAcquire()) {
            PendingRequest<?> request = pendingQueue.poll();
            if (request == null) {
                semaphore.release();
                break;
            }
            
            activeRequests.incrementAndGet();
            
            try {
                request.operation.get()
                    .whenComplete((result, error) -> {
                        activeRequests.decrementAndGet();
                        semaphore.release();
                        
                        if (error != null) {
                            failedRequests.incrementAndGet();
                            request.future.completeExceptionally(error);
                        } else {
                            completedRequests.incrementAndGet();
                            ((CompletableFuture<Object>) request.future).complete(result);
                        }
                        
                        processQueue();
                    });
            } catch (Exception e) {
                activeRequests.decrementAndGet();
                semaphore.release();
                failedRequests.incrementAndGet();
                request.future.completeExceptionally(e);
            }
        }
    }
    
    private boolean tryAcquire(Priority priority) {
        if (priority == Priority.CRITICAL) {
            try {
                return semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return semaphore.tryAcquire();
    }
    
    // ==================== Adaptive Monitoring ====================
    
    private void startAdaptiveMonitoring() {
        adaptiveScheduler.scheduleAtFixedRate(() -> {
            try {
                updateAdaptiveLimit();
            } catch (Exception e) {
                logger.error("Error in adaptive monitoring", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private void updateAdaptiveLimit() {
        // Calculate load factor
        int active = activeRequests.get();
        int queued = pendingQueue.size();
        long failed = failedRequests.get();
        long total = totalRequests.get();
        
        double failRate = total > 0 ? (double) failed / total : 0;
        double utilization = (double) active / maxConcurrent;
        double queueUtilization = (double) queued / queueCapacity;
        
        // Weighted load factor
        currentLoadFactor = (utilization * 0.5) + (queueUtilization * 0.3) + (failRate * 0.2);
        
        // Adjust limit based on load
        if (currentLoadFactor > 0.9) {
            // High load - reduce limit
            adaptiveLimit = Math.max(1, (int) (adaptiveLimit * 0.9));
            logger.debug("Reducing adaptive limit to {} (load: {:.2f})", adaptiveLimit, currentLoadFactor);
        } else if (currentLoadFactor < 0.5 && adaptiveLimit < maxConcurrent) {
            // Low load - increase limit
            adaptiveLimit = Math.min(maxConcurrent, (int) (adaptiveLimit * 1.1));
            logger.debug("Increasing adaptive limit to {} (load: {:.2f})", adaptiveLimit, currentLoadFactor);
        }
    }
    
    // ==================== Status & Metrics ====================
    
    /**
     * Get current backpressure status.
     *
     * @return status information
     */
    public BackpressureStatus getStatus() {
        return new BackpressureStatus(
            activeRequests.get(),
            maxConcurrent,
            pendingQueue.size(),
            queueCapacity,
            totalRequests.get(),
            completedRequests.get(),
            failedRequests.get(),
            droppedRequests.get(),
            currentLoadFactor,
            adaptiveLimit,
            strategy
        );
    }
    
    /**
     * Get metrics collector.
     *
     * @return metrics
     */
    public BackpressureMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Check if system is under pressure.
     *
     * @return true if under pressure
     */
    public boolean isUnderPressure() {
        return currentLoadFactor > 0.7 || activeRequests.get() > (maxConcurrent * 0.9);
    }
    
    /**
     * Check if system is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return !isUnderPressure() && semaphore.availablePermits() > 0;
    }
    
    /**
     * Shutdown the backpressure manager.
     */
    public void shutdown() {
        if (adaptiveScheduler != null) {
            adaptiveScheduler.shutdown();
            try {
                if (!adaptiveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    adaptiveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                adaptiveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int maxConcurrent = 100;
        private int queueCapacity = 1000;
        private BackpressureStrategy strategy = BackpressureStrategy.ADAPTIVE;
        private Duration timeout = Duration.ofSeconds(30);
        
        public Builder maxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
            return this;
        }
        
        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }
        
        public Builder strategy(BackpressureStrategy strategy) {
            this.strategy = strategy;
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public BackpressureManager build() {
            return new BackpressureManager(this);
        }
    }
    
    // ==================== Inner Classes ====================
    
    private static class PendingRequest<T> {
        final Priority priority;
        final Supplier<Promise<Object>> operation;
        final CompletableFuture<Object> future;
        
        @SuppressWarnings("unchecked")
        PendingRequest(Priority priority, Supplier<Promise<Object>> operation, CompletableFuture<Object> future) {
            this.priority = priority;
            this.operation = operation;
            this.future = future;
        }
    }
    
    /**
     * Backpressure status information.
     */
    public record BackpressureStatus(
        int activeRequests,
        int maxConcurrent,
        int queuedRequests,
        int queueCapacity,
        long totalRequests,
        long completedRequests,
        long failedRequests,
        long droppedRequests,
        double loadFactor,
        int adaptiveLimit,
        BackpressureStrategy strategy
    ) {
        public double getUtilization() {
            return (double) activeRequests / maxConcurrent;
        }
        
        public double getQueueUtilization() {
            return (double) queuedRequests / queueCapacity;
        }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) completedRequests / totalRequests : 1.0;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("active_requests", activeRequests);
            map.put("max_concurrent", maxConcurrent);
            map.put("utilization", getUtilization());
            map.put("queued_requests", queuedRequests);
            map.put("queue_capacity", queueCapacity);
            map.put("queue_utilization", getQueueUtilization());
            map.put("total_requests", totalRequests);
            map.put("completed_requests", completedRequests);
            map.put("failed_requests", failedRequests);
            map.put("dropped_requests", droppedRequests);
            map.put("success_rate", getSuccessRate());
            map.put("load_factor", loadFactor);
            map.put("adaptive_limit", adaptiveLimit);
            map.put("strategy", strategy.name());
            return map;
        }
    }
    
    /**
     * Metrics for backpressure monitoring.
     */
    public static class BackpressureMetrics {
        private final BackpressureManager manager;
        
        BackpressureMetrics(BackpressureManager manager) {
            this.manager = manager;
        }
        
        public int getActiveRequests() { return manager.activeRequests.get(); }
        public int getQueuedRequests() { return manager.pendingQueue.size(); }
        public long getTotalRequests() { return manager.totalRequests.get(); }
        public long getCompletedRequests() { return manager.completedRequests.get(); }
        public long getFailedRequests() { return manager.failedRequests.get(); }
        public long getDroppedRequests() { return manager.droppedRequests.get(); }
        public double getLoadFactor() { return manager.currentLoadFactor; }
        public int getAdaptiveLimit() { return manager.adaptiveLimit; }
        public int getAvailableSlots() { return manager.semaphore.availablePermits(); }
    }
}
