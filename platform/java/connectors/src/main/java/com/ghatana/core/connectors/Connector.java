package com.ghatana.core.connectors;

import io.activej.promise.Promise;
import com.ghatana.platform.observability.health.HealthCheck;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Service Provider Interface for external system connectors.
 * Supports both source (input) and sink (output) operations with lifecycle management.
 *
 * <p>Connectors provide standardized integration with external systems (Kafka, files,
 * databases, message queues). They support bidirectional data flow, health monitoring,
 * metrics collection, and Promise-based async operations aligned with ActiveJ runtime.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Lifecycle management (initialize → start → stop)</li>
 * @doc.type interface
 * @doc.purpose Service Provider Interface for external system connectors with bidirectional data flow
 * @doc.layer observability
 * @doc.pattern Strategy, Service Provider Interface
 *   <li>Source/sink capability discovery (isSourceCapable/isSinkCapable)</li>
 *   <li>Health monitoring via HealthCheck interface</li>
 *   <li>Metrics tracking (messages, bytes, latency)</li>
 *   <li>Configuration-driven setup with type-safe properties</li>
 *   <li>Status tracking (UNINITIALIZED → RUNNING → STOPPED)</li>
 * </ul>
 *
 * <h2>Lifecycle States:</h2>
 * <pre>
 * UNINITIALIZED → initialize() → INITIALIZED → start() → RUNNING
 *                                                   ↓
 *                                              stop() → STOPPED
 *                                                   ↓
 *                                              (any error) → FAILED
 * </pre>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create and configure connector
 * Connector kafka = new KafkaConnector("events-ingress");
 * ConnectorConfig config = KafkaConnectorConfig.builder()
 *     .bootstrapServers("localhost:9092")
 *     .topic("events")
 *     .sourceEnabled(true)
 *     .build();
 * 
 * // Initialize and start
 * kafka.initialize(config)
 *     .then(() -> kafka.start())
 *     .whenResult(() -> logger.info("Connector running: {}", kafka.getStatus()));
 * 
 * // Monitor health
 * kafka.check()
 *     .whenResult(result -> logger.info("Health: {}", result.isHealthy()));
 * 
 * // View metrics
 * ConnectorMetrics metrics = kafka.getMetrics();
 * logger.info("Processed: {} messages", metrics.getMessagesProcessed());
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Implementations MUST be thread-safe for all methods. Use atomic state management.
 *
 * <h2>Performance Considerations:</h2>
 * - Health checks should complete in <100ms (configurable via timeout)
 * - Metrics collection is O(1) (atomic counters)
 * - Start/stop operations may block briefly for resource cleanup
 *
 * @since 1.0.0
 */
public interface Connector extends HealthCheck {
    
    /**
     * Get the connector type (e.g., "kafka", "file", "database", "memory").
     *
     * @return connector type identifier
     */
    String getType();
    
    /**
     * Get the connector name/identifier.
     *
     * @return unique connector name
     */
    String getName();
    
    /**
     * Get connector configuration.
     *
     * @return current configuration, or null if not initialized
     */
    ConnectorConfig getConfig();
    
    /**
     * Initialize the connector with configuration.
     * <p>Transitions state: UNINITIALIZED → INITIALIZED (or FAILED on error).
     *
     * @param config connector configuration
     * @return Promise that completes when initialization succeeds
     */
    Promise<Void> initialize(ConnectorConfig config);
    
    /**
     * Start the connector.
     * <p>Transitions state: INITIALIZED → RUNNING (or FAILED on error).
     * <p>Must be called after initialize().
     *
     * @return Promise that completes when connector is running
     * @throws IllegalStateException if not initialized
     */
    Promise<Void> start();
    
    /**
     * Stop the connector gracefully.
     * <p>Transitions state: RUNNING → STOPPED (or FAILED on error).
     * <p>Should release all resources (connections, threads, file handles).
     *
     * @return Promise that completes when connector is stopped
     */
    Promise<Void> stop();
    
    /**
     * Get current connector status.
     *
     * @return current lifecycle status
     */
    ConnectorStatus getStatus();
    
    /**
     * Get connector metrics.
     *
     * @return current metrics snapshot (immutable)
     */
    ConnectorMetrics getMetrics();
    
    /**
     * Whether this connector supports source operations (reading).
     *
     * @return true if connector can read from external system
     */
    boolean isSourceCapable();
    
    /**
     * Whether this connector supports sink operations (writing).
     *
     * @return true if connector can write to external system
     */
    boolean isSinkCapable();
    
    /**
     * Connector configuration interface.
     * <p>Provides type-safe access to connector properties with defaults.
     *
     * @immutability Implementations should be immutable
     * @thread-safety Thread-safe reads
     */
    interface ConnectorConfig {
        /**
         * Get connector name.
         *
         * @return connector name
         */
        String getName();
        
        /**
         * Get connector type.
         *
         * @return connector type (e.g., "kafka", "file")
         */
        String getType();
        
        /**
         * Get all configuration properties.
         *
         * @return immutable map of properties
         */
        Map<String, Object> getProperties();
        
        /**
         * Get typed property value.
         *
         * @param key  property key
         * @param type expected type
         * @param <T>  value type
         * @return property value, or null if not present
         * @throws IllegalArgumentException if value is not of expected type
         */
        <T> T getProperty(String key, Class<T> type);
        
        /**
         * Get typed property value with default.
         *
         * @param key          property key
         * @param type         expected type
         * @param defaultValue default if not present
         * @param <T>          value type
         * @return property value, or defaultValue if not present
         */
        <T> T getProperty(String key, Class<T> type, T defaultValue);
        
        /**
         * Get operation timeout.
         *
         * @return timeout for connector operations
         */
        Duration getTimeout();
        
        /**
         * Get retry attempts for failed operations.
         *
         * @return number of retry attempts
         */
        int getRetryAttempts();
        
        /**
         * Get delay between retries.
         *
         * @return retry delay
         */
        Duration getRetryDelay();
        
        /**
         * Whether connector is enabled.
         *
         * @return true if connector should be active
         */
        boolean isEnabled();
    }
    
    /**
     * Connector status enumeration.
     * <p>Represents connector lifecycle state with transition validation.
     *
     * @immutability Enum values are immutable
     * @thread-safety Thread-safe (enum)
     */
    enum ConnectorStatus {
        /** Connector created but not initialized */
        UNINITIALIZED("Uninitialized"),
        
        /** Initialization in progress */
        INITIALIZING("Initializing"),
        
        /** Initialized but not started */
        INITIALIZED("Initialized"),
        
        /** Start operation in progress */
        STARTING("Starting"),
        
        /** Connector is running and processing data */
        RUNNING("Running"),
        
        /** Stop operation in progress */
        STOPPING("Stopping"),
        
        /** Connector has stopped gracefully */
        STOPPED("Stopped"),
        
        /** Connector encountered an error */
        FAILED("Failed");
        
        private final String description;
        
        ConnectorStatus(String description) {
            this.description = description;
        }
        
        /**
         * Get human-readable description.
         *
         * @return status description
         */
        public String getDescription() {
            return description;
        }
        
        /**
         * Check if connector is running.
         *
         * @return true if status is RUNNING
         */
        public boolean isRunning() {
            return this == RUNNING;
        }
        
        /**
         * Check if connector is stopped.
         *
         * @return true if STOPPED or FAILED
         */
        public boolean isStopped() {
            return this == STOPPED || this == FAILED;
        }
        
        /**
         * Check if connector is transitioning between states.
         *
         * @return true if INITIALIZING, STARTING, or STOPPING
         */
        public boolean isTransitioning() {
            return this == INITIALIZING || this == STARTING || this == STOPPING;
        }
    }
    
    /**
     * Connector metrics interface.
     * <p>Provides immutable snapshot of connector performance metrics.
     *
     * @immutability Snapshot is immutable (values may change on next call)
     * @thread-safety Thread-safe reads (atomic counters)
     */
    interface ConnectorMetrics {
        /**
         * Total messages processed (source + sink).
         *
         * @return message count since connector start
         */
        long getMessagesProcessed();
        
        /**
         * Messages processed successfully.
         *
         * @return success count
         */
        long getMessagesSucceeded();
        
        /**
         * Messages that failed processing.
         *
         * @return failure count
         */
        long getMessagesFailed();
        
        /**
         * Total bytes processed (payload size).
         *
         * @return byte count
         */
        long getBytesProcessed();
        
        /**
         * Average time to process one message.
         *
         * @return average processing time, or ZERO if no messages processed
         */
        Duration getAverageProcessingTime();
        
        /**
         * Total connections established (historical).
         *
         * @return connection count
         */
        long getConnectionCount();
        
        /**
         * Currently active connections.
         *
         * @return active connection count
         */
        long getActiveConnections();
        
        /**
         * Timestamp of last activity (message processed).
         *
         * @return last activity time
         */
        Instant getLastActivity();
        
        /**
         * Custom connector-specific metrics.
         *
         * @return map of metric name to value
         */
        Map<String, Object> getCustomMetrics();
    }
}
