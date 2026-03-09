package com.ghatana.core.connectors;

import io.activej.promise.Promise;
import com.ghatana.platform.observability.health.HealthCheck;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base implementation of Connector interface providing common functionality.
 *
 * <p>Provides template pattern for connector lifecycle, automatic state management,
 * metrics tracking, and health checking. Subclasses implement only the core operations
 * (doInitialize, doStart, doStop, doHealthCheck).
 *
 * <h2>Implements:</h2>
 * <ul>
 * @doc.type class
 * @doc.purpose Base implementation providing template pattern for connector lifecycle and metrics
 * @doc.layer observability
 * @doc.pattern Template Method, Lifecycle Management
 *   <li>Lifecycle state machine (UNINITIALIZED → INITIALIZED → RUNNING → STOPPED)</li>
 *   <li>Automatic status transitions with error handling</li>
 *   <li>Atomic metrics tracking (messages, bytes, processing time)</li>
 *   <li>Default health check implementation</li>
 *   <li>Utility methods for recording metrics</li>
 * </ul>
 *
 * <h2>Subclass Responsibilities:</h2>
 * Subclasses MUST implement:
 * - {@code doInitialize()}: Connect to external system, validate config
 * - {@code doStart()}: Begin processing (subscribe, open files, etc.)
 * - {@code doStop()}: Release resources (close connections, stop threads)
 * - {@code doHealthCheck()}: Verify system connectivity (optional override)
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * public class MyConnector extends BaseConnector {
 *     public MyConnector(String name) {
 *         super(name, "my-type");
 *     }
 *     
 *     @Override
 *     protected Promise<Void> doInitialize(ConnectorConfig config) {
 *         // Connect to external system
 *         return connectToSystem(config);
 *     }
 *     
 *     @Override
 *     protected Promise<Void> doStart() {
 *         // Begin processing
 *         return startProcessing();
 *     }
 *     
 *     @Override
 *     protected Promise<Void> doStop() {
 *         // Release resources
 *         return cleanup();
 *     }
 *     
 *     public void processMessage(String msg) {
 *         long start = System.currentTimeMillis();
 *         try {
 *             // Process message
 *             recordMessage(true, msg.length(), System.currentTimeMillis() - start);
 *         } catch (Exception e) {
 *             recordMessage(false, 0, 0);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. All state and metrics use atomic operations.
 *
 * <h2>State Transitions:</h2>
 * Status transitions are automatic and atomic:
 * - initialize() sets INITIALIZING → calls doInitialize() → sets INITIALIZED (or FAILED)
 * - start() sets STARTING → calls doStart() → sets RUNNING (or FAILED)
 * - stop() sets STOPPING → calls doStop() → sets STOPPED (or FAILED)
 *
 * @since 1.0.0
 */
public abstract class BaseConnector implements Connector {
    
    protected final String name;
    protected final String type;
    protected final AtomicReference<ConnectorStatus> status = new AtomicReference<>(ConnectorStatus.UNINITIALIZED);
    protected final AtomicReference<ConnectorConfig> config = new AtomicReference<>();
    
    // Metrics tracking
    protected final AtomicLong messagesProcessed = new AtomicLong(0);
    protected final AtomicLong messagesSucceeded = new AtomicLong(0);
    protected final AtomicLong messagesFailed = new AtomicLong(0);
    protected final AtomicLong bytesProcessed = new AtomicLong(0);
    protected final AtomicLong totalProcessingTime = new AtomicLong(0);
    protected final AtomicReference<Instant> lastActivity = new AtomicReference<>(Instant.now());
    
    protected BaseConnector(String name, String type) {
        this.name = name;
        this.type = type;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public ConnectorConfig getConfig() {
        return config.get();
    }
    
    @Override
    public ConnectorStatus getStatus() {
        return status.get();
    }
    
    @Override
    public Promise<Void> initialize(ConnectorConfig config) {
        this.config.set(config);
        status.set(ConnectorStatus.INITIALIZING);
        
        return doInitialize(config)
            .whenResult(v -> status.set(ConnectorStatus.INITIALIZED))
            .whenException(e -> status.set(ConnectorStatus.FAILED));
    }
    
    @Override
    public Promise<Void> start() {
        if (status.get() != ConnectorStatus.INITIALIZED) {
            return Promise.ofException(new IllegalStateException("Connector not initialized"));
        }
        
        status.set(ConnectorStatus.STARTING);
        
        return doStart()
            .whenResult(v -> status.set(ConnectorStatus.RUNNING))
            .whenException(e -> status.set(ConnectorStatus.FAILED));
    }
    
    @Override
    public Promise<Void> stop() {
        if (status.get() == ConnectorStatus.STOPPED) {
            return Promise.complete();
        }
        
        status.set(ConnectorStatus.STOPPING);
        
        return doStop()
            .whenResult(v -> status.set(ConnectorStatus.STOPPED))
            .whenException(e -> status.set(ConnectorStatus.FAILED));
    }
    
    @Override
    public ConnectorMetrics getMetrics() {
        return new ConnectorMetricsImpl();
    }
    
    @Override
    public Promise<HealthCheckResult> check() {
        ConnectorStatus currentStatus = status.get();
        
        if (currentStatus == ConnectorStatus.RUNNING) {
            return doHealthCheck()
                .map(result -> result != null ? result : 
                    HealthCheckResult.healthy("Connector is running"));
        } else if (currentStatus == ConnectorStatus.FAILED) {
            return Promise.of(HealthCheckResult.unhealthy("Connector is in failed state"));
        } else {
            return Promise.of(HealthCheckResult.degraded(
                "Connector is not running: " + currentStatus.getDescription(), 
                Map.of("status", currentStatus.getDescription()), 
                Duration.ZERO));
        }
    }
    
    @Override
    public Duration getTimeout() {
        ConnectorConfig cfg = config.get();
        return cfg != null ? cfg.getTimeout() : Duration.ofSeconds(5);
    }
    
    @Override
    public boolean isCritical() {
        return false; // Connectors are typically not critical for liveness
    }
    
    // Protected methods for subclasses to implement
    
    protected abstract Promise<Void> doInitialize(ConnectorConfig config);
    
    protected abstract Promise<Void> doStart();
    
    protected abstract Promise<Void> doStop();
    
    protected Promise<HealthCheckResult> doHealthCheck() {
        return Promise.of(HealthCheckResult.healthy("Default health check passed"));
    }
    
    // Utility methods for subclasses
    
    protected void recordMessage(boolean success, long bytes, long processingTimeMs) {
        messagesProcessed.incrementAndGet();
        if (success) {
            messagesSucceeded.incrementAndGet();
        } else {
            messagesFailed.incrementAndGet();
        }
        bytesProcessed.addAndGet(bytes);
        totalProcessingTime.addAndGet(processingTimeMs);
        lastActivity.set(Instant.now());
    }
    
    private class ConnectorMetricsImpl implements ConnectorMetrics {
        @Override
        public long getMessagesProcessed() {
            return messagesProcessed.get();
        }
        
        @Override
        public long getMessagesSucceeded() {
            return messagesSucceeded.get();
        }
        
        @Override
        public long getMessagesFailed() {
            return messagesFailed.get();
        }
        
        @Override
        public long getBytesProcessed() {
            return bytesProcessed.get();
        }
        
        @Override
        public Duration getAverageProcessingTime() {
            long processed = getMessagesProcessed();
            if (processed == 0) {
                return Duration.ZERO;
            }
            return Duration.ofMillis(totalProcessingTime.get() / processed);
        }
        
        @Override
        public long getConnectionCount() {
            return status.get() == ConnectorStatus.RUNNING ? 1 : 0;
        }
        
        @Override
        public long getActiveConnections() {
            return getConnectionCount();
        }
        
        @Override
        public Instant getLastActivity() {
            return lastActivity.get();
        }
        
        @Override
        public Map<String, Object> getCustomMetrics() {
            return Map.of(
                "status", status.get().getDescription(),
                "uptime", Duration.between(lastActivity.get(), Instant.now()).toString()
            );
        }
    }
}
