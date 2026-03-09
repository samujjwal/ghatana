package com.ghatana.core.connectors;

import com.ghatana.platform.observability.MetricsRegistry;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Registry for managing connectors in the EventCloud platform.
 * Provides lifecycle management, metrics, and health checks for all connectors.
 *
 * @doc.type class
 * @doc.purpose Centralized registry for connector lifecycle, discovery, and health management
 * @doc.layer observability
 * @doc.pattern Registry, Singleton, Lifecycle Manager
 *
 * <p>Singleton registry that tracks all connectors in the system, supports batch
 * operations (initializeAll, startAll, stopAll), and aggregates health/metrics across
 * all registered connectors. Uses concurrent data structures for thread-safe access.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Singleton pattern with thread-safe initialization</li>
 *   <li>Thread-safe concurrent registration/unregistration</li>
 *   <li>Batch lifecycle operations (initialize/start/stop all)</li>
 *   <li>Aggregated health checking across all connectors</li>
 *   <li>Type-based connector discovery</li>
 *   <li>Automatic graceful shutdown on unregister</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Initialize registry
 * ConnectorRegistry registry = ConnectorRegistry.initialize(metricsRegistry);
 * 
 * // Register connectors
 * registry.register(new KafkaConnector("events-ingress"));
 * registry.register(new FileConnector("audit-log"));
 * 
 * // Lifecycle management
 * registry.initializeAll()
 *     .then(() -> registry.startAll())
 *     .whenResult(() -> logger.info("All connectors started"));
 * 
 * // Health monitoring
 * registry.checkHealth()
 *     .whenResult(results -> 
 *         results.forEach((name, health) ->
 *             logger.info("{}: {}", name, health.isHealthy())));
 * 
 * // Shutdown
 * registry.stopAll()
 *     .whenComplete(() -> logger.info("All connectors stopped"));
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. Uses ConcurrentHashMap and atomic singleton initialization.
 *
 * <h2>Error Handling:</h2>
 * Batch operations (initializeAll, startAll, stopAll) continue on error - failures
 * are logged but don't prevent other connectors from processing.
 *
 * <h2>Performance Considerations:</h2>
 * - Registration/lookup: O(1) via ConcurrentHashMap
 * - Batch operations: O(N) parallel execution via Promises.all()
 * - Health checks: O(N) parallel with concurrent result collection
 *
 * @since 1.0.0
 */
public class ConnectorRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectorRegistry.class);
    private static final AtomicReference<ConnectorRegistry> INSTANCE = new AtomicReference<>();
    
    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();
    private final MetricsRegistry metricsRegistry;
    
    private ConnectorRegistry(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }
    
    /**
     * Initialize the global connector registry.
     */
    public static synchronized ConnectorRegistry initialize(MetricsRegistry metricsRegistry) {
        ConnectorRegistry registry = new ConnectorRegistry(metricsRegistry);
        INSTANCE.set(registry);
        return registry;
    }
    
    /**
     * Get the global connector registry instance.
     */
    public static ConnectorRegistry getInstance() {
        ConnectorRegistry registry = INSTANCE.get();
        if (registry == null) {
            throw new IllegalStateException("ConnectorRegistry not initialized. Call initialize() first.");
        }
        return registry;
    }
    
    /**
     * Register a connector.
     */
    public void register(Connector connector) {
        if (connector == null) {
            throw new IllegalArgumentException("Connector cannot be null");
        }
        
        String name = connector.getName();
        if (connectors.containsKey(name)) {
            throw new IllegalArgumentException("Connector with name '" + name + "' already registered");
        }
        
        connectors.put(name, connector);
        logger.info("Registered connector: {} (type: {})", name, connector.getType());
    }
    
    /**
     * Unregister a connector.
     */
    public Promise<Void> unregister(String name) {
        Connector connector = connectors.remove(name);
        if (connector == null) {
            return Promise.complete();
        }
        
        logger.info("Unregistering connector: {}", name);
        return connector.stop()
            .whenComplete(() -> logger.info("Unregistered connector: {}", name));
    }
    
    /**
     * Get a connector by name.
     */
    public Optional<Connector> getConnector(String name) {
        return Optional.ofNullable(connectors.get(name));
    }
    
    /**
     * Get all connectors.
     */
    public Set<Connector> getConnectors() {
        return Collections.unmodifiableSet(connectors.values().stream().collect(Collectors.toSet()));
    }
    
    /**
     * Get connectors by type.
     */
    public Set<Connector> getConnectorsByType(String type) {
        return connectors.values().stream()
            .filter(c -> c.getType().equals(type))
            .collect(Collectors.toUnmodifiableSet());
    }
    
    /**
     * Initialize all registered connectors.
     */
    public Promise<Void> initializeAll() {
        logger.info("Initializing {} connectors", connectors.size());
        
        return Promises.all(
            connectors.values().stream()
                .map(connector -> {
                    logger.info("Initializing connector: {}", connector.getName());
                    return connector.initialize(connector.getConfig())
                        .whenException(e -> logger.error("Failed to initialize connector: {}", connector.getName(), e));
                })
                .toList()
        ).toVoid();
    }
    
    /**
     * Start all registered connectors.
     */
    public Promise<Void> startAll() {
        logger.info("Starting {} connectors", connectors.size());
        
        return Promises.all(
            connectors.values().stream()
                .map(connector -> {
                    logger.info("Starting connector: {}", connector.getName());
                    return connector.start()
                        .whenException(e -> logger.error("Failed to start connector: {}", connector.getName(), e));
                })
                .toList()
        ).toVoid();
    }
    
    /**
     * Stop all registered connectors.
     */
    public Promise<Void> stopAll() {
        logger.info("Stopping {} connectors", connectors.size());
        
        return Promises.all(
            connectors.values().stream()
                .map(connector -> {
                    logger.info("Stopping connector: {}", connector.getName());
                    return connector.stop()
                        .whenException(e -> logger.error("Failed to stop connector: {}", connector.getName(), e));
                })
                .toList()
        ).toVoid();
    }
    
    /**
     * Check health of all connectors.
     */
    public Promise<Map<String, Connector.HealthCheckResult>> checkHealth() {
        return Promise.ofCallback(cb -> {
            Map<String, Connector.HealthCheckResult> results = new ConcurrentHashMap<>();
            
            Promises.all(
                connectors.values().stream()
                    .map(connector -> connector.check()
                        .whenResult(result -> results.put(connector.getName(), result))
                        .whenException(e -> {
                            logger.error("Health check failed for connector: {}", connector.getName(), e);
                            results.put(connector.getName(), Connector.HealthCheckResult.unhealthy(
                                "Health check failed: " + e.getMessage(), e));
                        }))
                    .toList()
            ).whenComplete(() -> cb.accept(results, null));
        });
    }
    
    /**
     * Get metrics for all connectors.
     */
    public Map<String, Connector.ConnectorMetrics> getMetrics() {
        return connectors.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getMetrics()
            ));
    }
}
