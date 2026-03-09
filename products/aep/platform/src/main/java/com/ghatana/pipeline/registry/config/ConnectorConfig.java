package com.ghatana.pipeline.registry.config;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.core.connectors.ConnectorRegistry;
import com.ghatana.core.connectors.impl.FileConnector;
import com.ghatana.core.connectors.impl.KafkaConnector;

import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ module for connector configuration and registration.
 *
 * <p>Purpose: Provides dependency injection bindings for data connectors
 * (File, Kafka, etc.) used by the Pipeline Registry. Initializes and
 * registers connectors with the central ConnectorRegistry.</p>
 *
 * @doc.type class
 * @doc.purpose Configures and registers data connectors for pipeline I/O
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 2.0.0
 */
public class ConnectorConfig extends AbstractModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConnectorConfig.class);
    
    @Provides
    ConnectorRegistry connectorRegistry(MetricsRegistry metricsRegistry) {
        // Initialize the connector registry
        ConnectorRegistry registry = ConnectorRegistry.initialize(metricsRegistry);
        
        // Register connectors
        registerFileConnectors(registry);
        registerKafkaConnectors(registry);
        
        return registry;
    }
    
    /**
     * Register file connectors.
     */
    private void registerFileConnectors(ConnectorRegistry registry) {
        try {
            // Create a file connector for logs
            FileConnector logsConnector = new FileConnector("logs-connector");
            
            // Configure the connector
            String logsPath = Paths.get(System.getProperty("java.io.tmpdir"), "pipeline-registry", "logs", "events.log").toString();
            FileConnector.FileConnectorConfig logsConfig = FileConnector.FileConnectorConfig.builder()
                .name("logs-connector")
                .filePath(logsPath)
                .sinkEnabled(true)
                .sourceEnabled(false)
                .append(true)
                .build();
            
            // Register the connector
            registry.register(logsConnector);
            
            // Initialize and start the connector
            // Note: removed .join() calls - these will complete asynchronously
            logsConnector.initialize(logsConfig);
            logsConnector.start();
            
            LOG.info("Registered and started logs file connector");
            
        } catch (Exception e) {
            LOG.error("Failed to register file connectors", e);
        }
    }
    
    /**
     * Register Kafka connectors.
     */
    private void registerKafkaConnectors(ConnectorRegistry registry) {
        try {
            // Create a Kafka connector for events
            KafkaConnector eventsConnector = new KafkaConnector("events-connector");
            
            // Configure the connector
            Map<String, Object> properties = new HashMap<>();
            properties.put("bootstrap.servers", "localhost:9092");
            properties.put("topic", "pipeline-events");
            properties.put("producer.acks", "all");
            properties.put("producer.retries", 3);
            properties.put("producer.batch.size", 16384);
            properties.put("producer.linger.ms", 1);
            properties.put("producer.compression.type", "snappy");
            properties.put("consumer.group.id", "pipeline-registry");
            properties.put("consumer.auto.offset.reset", "earliest");
            properties.put("consumer.enable.auto.commit", false);
            properties.put("source.enabled", false); // Disabled for now
            properties.put("sink.enabled", true);
            
            KafkaConnector.KafkaConnectorConfig eventsConfig = new KafkaConnector.KafkaConnectorConfig(
                "events-connector",
                properties,
                Duration.ofSeconds(30),
                3,
                Duration.ofSeconds(1),
                true
            );
            
            // Register the connector
            registry.register(eventsConnector);
            
            // Initialize the connector but don't start it yet (Kafka might not be available)
            // Note: removed .join() call - this will complete asynchronously
            eventsConnector.initialize(eventsConfig);
            
            LOG.info("Registered and initialized events Kafka connector");
            
        } catch (Exception e) {
            LOG.error("Failed to register Kafka connectors", e);
        }
    }
}
