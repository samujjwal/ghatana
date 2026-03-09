package com.ghatana.datacloud.infrastructure.backpressure;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Backpressure Manager for Data-Cloud ingest and processing.
 * 
 * <p>Implements backpressure handling to prevent system overload during
 * high-throughput data ingestion. Uses bounded queues, flow control signals,
 * and adaptive rate limiting.</p>
 * 
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Bounded queues for ingest operations</li>
 *   <li>Flow control signals (accept/reject/throttle)</li>
 *   <li>Adaptive rate limiting based on system load</li>
 *   <li>Queue depth monitoring</li>
 *   <li>Metrics collection</li>
 * </ul>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * BackpressureManager backpressure = new BackpressureManager(
 *     BackpressureConfig.builder()
 *         .maxQueueSize(10000)
 *         .highWatermark(0.8)
 *         .lowWatermark(0.2)
 *         .build()
 * );
 * 
 * // Check if we can accept more data
 * FlowControl control = backpressure.checkFlowControl();
 * if (control.canAccept()) {
 *     // Process data
 * } else if (control.shouldThrottle()) {
 *     // Apply backpressure
 *     Thread.sleep(control.getBackoffMs());
 * }
 * }</pre>
 * 
 * @doc.type service
 * @doc.purpose Backpressure handling for high-throughput ingest
 * @doc.layer infrastructure
 * @doc.pattern Flow Control, Adaptive Rate Limiting
 */
public class BackpressureManager {
    private static final Logger logger = LoggerFactory.getLogger(BackpressureManager.class);
    
    private final BackpressureConfig config;
    private final BlockingQueue<Object> ingestQueue;
    private final AtomicLong totalProcessed;
    private final AtomicLong totalRejected;
    private final AtomicLong totalThrottled;
    private volatile long lastAdjustmentTime;
    private volatile int currentRateLimit;
    
    /**
     * Creates backpressure manager with configuration.
     * 
     * @param config backpressure configuration
     */
    public BackpressureManager(BackpressureConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.ingestQueue = new LinkedBlockingQueue<>(config.getMaxQueueSize());
        this.totalProcessed = new AtomicLong(0);
        this.totalRejected = new AtomicLong(0);
        this.totalThrottled = new AtomicLong(0);
        this.lastAdjustmentTime = System.currentTimeMillis();
        this.currentRateLimit = config.getInitialRateLimit();
        
        logger.info("BackpressureManager initialized: maxQueue={}, highWater={}, lowWater={}",
            config.getMaxQueueSize(), config.getHighWatermark(), config.getLowWatermark());
    }
    
    /**
     * Checks flow control status and returns control decision.
     * 
     * @return flow control decision
     */
    public FlowControl checkFlowControl() {
        int queueSize = ingestQueue.size();
        int maxSize = config.getMaxQueueSize();
        double queueUtilization = (double) queueSize / maxSize;
        
        // Adjust rate limit based on queue depth
        adjustRateLimit(queueUtilization);
        
        // Determine flow control action
        if (queueUtilization >= config.getHighWatermark()) {
            // Queue is full, reject new requests
            totalRejected.incrementAndGet();
            logger.warn("Queue full: utilization={}, rejecting requests", queueUtilization);
            return FlowControl.reject("Queue at high watermark");
        } else if (queueUtilization >= config.getMediumWatermark()) {
            // Queue is getting full, throttle
            long backoffMs = calculateBackoff(queueUtilization);
            totalThrottled.incrementAndGet();
            logger.debug("Queue high: utilization={}, throttling for {}ms", 
                queueUtilization, backoffMs);
            return FlowControl.throttle(backoffMs);
        } else {
            // Queue has capacity, accept
            totalProcessed.incrementAndGet();
            return FlowControl.accept();
        }
    }
    
    /**
     * Tries to enqueue an item with backpressure handling.
     * 
     * @param item item to enqueue
     * @return promise of enqueue result
     */
    public Promise<EnqueueResult> enqueue(Object item) {
        Objects.requireNonNull(item, "item cannot be null");
        
        FlowControl control = checkFlowControl();
        
        if (!control.canAccept()) {
            if (control.shouldReject()) {
                return Promise.of(EnqueueResult.rejected("Queue full"));
            } else if (control.shouldThrottle()) {
                // Apply backoff and retry in a blocking-safe way
                return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                    try {
                        Thread.sleep(control.getBackoffMs());
                        ingestQueue.put(item);
                        return EnqueueResult.accepted();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return EnqueueResult.failed("Interrupted");
                    }
                });
             }
         }

        try {
            boolean offered = ingestQueue.offer(item, config.getOfferTimeoutMs(), TimeUnit.MILLISECONDS);
            if (offered) {
                return Promise.of(EnqueueResult.accepted());
            } else {
                return Promise.of(EnqueueResult.rejected("Timeout"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Promise.of(EnqueueResult.failed("Interrupted"));
        }
    }
    
    /**
     * Drains items from queue for processing.
     * 
     * @param maxItems maximum items to drain
     * @return list of drained items
     */
    public List<Object> drain(int maxItems) {
        List<Object> items = new ArrayList<>(maxItems);
        ingestQueue.drainTo(items, maxItems);
        return items;
    }
    
    /**
     * Gets current queue depth.
     * 
     * @return queue size
     */
    public int getQueueDepth() {
        return ingestQueue.size();
    }
    
    /**
     * Gets queue utilization percentage.
     * 
     * @return utilization 0.0-1.0
     */
    public double getQueueUtilization() {
        return (double) ingestQueue.size() / config.getMaxQueueSize();
    }
    
    /**
     * Gets backpressure statistics.
     * 
     * @return statistics map
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "queueSize", ingestQueue.size(),
            "maxQueueSize", config.getMaxQueueSize(),
            "utilization", getQueueUtilization(),
            "totalProcessed", totalProcessed.get(),
            "totalRejected", totalRejected.get(),
            "totalThrottled", totalThrottled.get(),
            "currentRateLimit", currentRateLimit
        );
    }
    
    /**
     * Adjusts rate limit based on queue utilization.
     * 
     * @param utilization queue utilization 0.0-1.0
     */
    private void adjustRateLimit(double utilization) {
        long now = System.currentTimeMillis();
        if (now - lastAdjustmentTime < config.getAdjustmentIntervalMs()) {
            return;
        }
        
        lastAdjustmentTime = now;
        
        if (utilization >= config.getHighWatermark()) {
            // Reduce rate limit
            currentRateLimit = Math.max(
                config.getMinRateLimit(),
                (int) (currentRateLimit * 0.8)
            );
            logger.debug("Rate limit reduced to {}", currentRateLimit);
        } else if (utilization <= config.getLowWatermark()) {
            // Increase rate limit
            currentRateLimit = Math.min(
                config.getMaxRateLimit(),
                (int) (currentRateLimit * 1.2)
            );
            logger.debug("Rate limit increased to {}", currentRateLimit);
        }
    }
    
    /**
     * Calculates backoff time based on queue utilization.
     * 
     * @param utilization queue utilization 0.0-1.0
     * @return backoff time in milliseconds
     */
    private long calculateBackoff(double utilization) {
        // Exponential backoff: 10ms at 50%, 100ms at 80%, 1000ms at 95%
        if (utilization < 0.5) {
            return 0;
        } else if (utilization < 0.8) {
            return (long) ((utilization - 0.5) * 200); // 0-60ms
        } else if (utilization < 0.95) {
            return (long) (60 + (utilization - 0.8) * 560); // 60-150ms
        } else {
            return 1000; // 1s at very high utilization
        }
    }
    
    /**
     * Flow control decision.
     */
    public static class FlowControl {
        private final FlowControlAction action;
        private final String reason;
        private final long backoffMs;
        
        private FlowControl(FlowControlAction action, String reason, long backoffMs) {
            this.action = action;
            this.reason = reason;
            this.backoffMs = backoffMs;
        }
        
        public boolean canAccept() {
            return action == FlowControlAction.ACCEPT;
        }
        
        public boolean shouldThrottle() {
            return action == FlowControlAction.THROTTLE;
        }
        
        public boolean shouldReject() {
            return action == FlowControlAction.REJECT;
        }
        
        public long getBackoffMs() {
            return backoffMs;
        }
        
        public String getReason() {
            return reason;
        }
        
        public static FlowControl accept() {
            return new FlowControl(FlowControlAction.ACCEPT, "Queue has capacity", 0);
        }
        
        public static FlowControl throttle(long backoffMs) {
            return new FlowControl(FlowControlAction.THROTTLE, "Queue high", backoffMs);
        }
        
        public static FlowControl reject(String reason) {
            return new FlowControl(FlowControlAction.REJECT, reason, 0);
        }
    }
    
    /**
     * Enqueue result.
     */
    public static class EnqueueResult {
        private final EnqueueStatus status;
        private final String message;
        
        private EnqueueResult(EnqueueStatus status, String message) {
            this.status = status;
            this.message = message;
        }
        
        public boolean isSuccess() {
            return status == EnqueueStatus.ACCEPTED;
        }
        
        public EnqueueStatus getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public static EnqueueResult accepted() {
            return new EnqueueResult(EnqueueStatus.ACCEPTED, "Item enqueued");
        }
        
        public static EnqueueResult rejected(String reason) {
            return new EnqueueResult(EnqueueStatus.REJECTED, reason);
        }
        
        public static EnqueueResult failed(String reason) {
            return new EnqueueResult(EnqueueStatus.FAILED, reason);
        }
    }
    
    enum FlowControlAction {
        ACCEPT, THROTTLE, REJECT
    }
    
    enum EnqueueStatus {
        ACCEPTED, REJECTED, FAILED
    }
}
