package com.ghatana.pipeline.registry.ingress;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec.ConnectorType;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.ghatana.pipeline.registry.connector.HttpIngressConnector;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes ingress requests to appropriate connectors based on path/topic and
 * tenant.
 *
 * <p>
 * <b>Purpose</b><br>
 * Central router for HTTP and queue-based event ingestion. Routes incoming
 * events from external producers (HTTP, Kafka, RabbitMQ, SQS) to correct
 * connectors and pipelines based on request path, topic, and tenant context.
 *
 * <p>
 * <b>Routing Rules</b><br>
 * 1. HTTP requests matched by path pattern
 * (/api/v1/connectors/{connectorId}/events)<br>
 * 2. Queue topics matched by connector topic configuration<br>
 * 3. Tenant extracted from HTTP headers or queue headers<br>
 * 4. Events routed to appropriate HTTP/queue ingress connector<br>
 * 5. Metrics and logs emitted for each routing decision<br>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Part of ingress layer (P3-04). Enables external producers to send events via
 * HTTP or message queues, with automatic routing to registered connectors.
 * Metrics tagged with connector_id, tenant, event_type, pipeline_id (if
 * applicable).
 *
 * @see ConnectorSpec
 * @see Event
 * @doc.type class
 * @doc.purpose Request routing for ingress connectors
 * @doc.layer product
 * @doc.pattern Router
 */
@Slf4j
@RequiredArgsConstructor
public class IngressConnectorRouter {

    private final MetricsCollector metricsCollector;
    private final Map<String, ConnectorSpec> connectorRegistry = new ConcurrentHashMap<>();
    private final Map<String, String> pathToConnectorIdMapping = new ConcurrentHashMap<>();
    private final Map<String, String> topicToConnectorIdMapping = new ConcurrentHashMap<>();
    private final Map<String, HttpIngressConnector> activeConnectors = new ConcurrentHashMap<>();

    /**
     * Registers an active ingress connector instance.
     *
     * @param connector the active connector instance
     */
    public void registerActiveConnector(HttpIngressConnector connector) {
        if (connector == null) {
            throw new IllegalArgumentException("Connector cannot be null");
        }
        activeConnectors.put(connector.getId(), connector);
        log.info("Registered active ingress connector: {}", connector.getId());
    }

    /**
     * Unregisters an active ingress connector instance.
     *
     * @param connectorId the connector ID
     */
    public void unregisterActiveConnector(String connectorId) {
        if (connectorId != null) {
            activeConnectors.remove(connectorId);
            log.info("Unregistered active ingress connector: {}", connectorId);
        }
    }

    /**
     * Registers an ingress connector spec for routing.
     *
     * @param connector connector spec to register
     */
    public void registerConnector(ConnectorSpec connector) {
        if (connector == null) {
            throw new IllegalArgumentException("Connector spec cannot be null");
        }

        if (!isIngressConnectorType(connector.getType())) {
            throw new IllegalArgumentException(
                    "Connector " + connector.getId() + " is not an ingress type");
        }

        connectorRegistry.put(connector.getId(), connector);
        log.info("Registered ingress connector '{}' (type: {})",
                connector.getId(), connector.getType());

        // Register routing mappings
        if (connector.getType() == ConnectorType.HTTP_INGRESS) {
            registerHttpConnector(connector);
        } else if (connector.getType() == ConnectorType.QUEUE_SOURCE) {
            registerQueueConnector(connector);
        }

        // Emit registration metric
        metricsCollector.incrementCounter(
                "aep.ingress.connector.registered",
                "connector_id", connector.getId(),
                "type", connector.getType().toString(),
                "tenant", connector.getTenantId()
        );
    }

    /**
     * Routes HTTP request to appropriate connector by path.
     *
     * @param path HTTP request path
     * @param tenant tenant ID
     * @param event event to route
     * @return Promise with routing result
     */
    public Promise<Boolean> routeHttpRequest(String path, String tenant, Event event) {
        if (path == null || path.trim().isEmpty()) {
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "empty_path",
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(new IllegalArgumentException("Path cannot be empty"));
        }

        if (event == null) {
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "null_event",
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(new IllegalArgumentException("Event cannot be null"));
        }

        // Extract connector ID from path
        String connectorId = extractConnectorIdFromPath(path);
        if (connectorId == null) {
            log.warn("No connector mapping found for path '{}'", path);
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "no_route",
                    "path", path,
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(
                    new IllegalArgumentException("No ingress connector found for path: " + path));
        }

        ConnectorSpec connector = connectorRegistry.get(connectorId);
        if (connector == null) {
            log.warn("Connector '{}' not found in registry", connectorId);
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "connector_not_found",
                    "connector_id", connectorId,
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(
                    new IllegalStateException("Connector not found: " + connectorId));
        }

        // Set MDC context
        MDC.put("connectorId", connectorId);
        MDC.put("ingressPath", path);
        MDC.put("tenantId", Optional.ofNullable(tenant).orElse("unknown"));
        MDC.put("eventId", event.getId().toString());

        // Emit routing metric
        metricsCollector.incrementCounter(
                "aep.ingress.router.http.routed",
                "connector_id", connectorId,
                "path", path,
                "event_type", event.getType(),
                "tenant", Optional.ofNullable(tenant).orElse("unknown")
        );

        log.debug("Routed HTTP request to connector '{}' for path '{}', tenant '{}'",
                connectorId, path, tenant);

        HttpIngressConnector activeConnector = activeConnectors.get(connectorId);
        if (activeConnector != null) {
            return activeConnector.ingest(event, null)
                    .map(v -> true);
        } else {
            log.warn("Active connector instance not found for ID: {}", connectorId);
            // Fallback: if no active connector, we might just acknowledge receipt if it's a registry-only mode
            // But for now, let's return false or error
            return Promise.ofException(new IllegalStateException("Active connector not found: " + connectorId));
        }
    }

    /**
     * Routes queue message to appropriate connector by topic.
     *
     * @param topic queue topic
     * @param tenant tenant ID
     * @param event event to route
     * @return Promise with routing result
     */
    public Promise<Boolean> routeQueueMessage(String topic, String tenant, Event event) {
        if (topic == null || topic.trim().isEmpty()) {
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "empty_topic",
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(new IllegalArgumentException("Topic cannot be empty"));
        }

        if (event == null) {
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "null_event",
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(new IllegalArgumentException("Event cannot be null"));
        }

        // Find connector by topic
        String connectorId = topicToConnectorIdMapping.get(topic);
        if (connectorId == null) {
            log.warn("No connector mapping found for topic '{}'", topic);
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "no_route",
                    "topic", topic,
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(
                    new IllegalArgumentException("No ingress connector found for topic: " + topic));
        }

        ConnectorSpec connector = connectorRegistry.get(connectorId);
        if (connector == null) {
            log.warn("Connector '{}' not found in registry", connectorId);
            metricsCollector.incrementCounter(
                    "aep.ingress.router.error",
                    "reason", "connector_not_found",
                    "connector_id", connectorId,
                    "tenant", Optional.ofNullable(tenant).orElse("unknown")
            );
            return Promise.ofException(
                    new IllegalStateException("Connector not found: " + connectorId));
        }

        // Set MDC context
        MDC.put("connectorId", connectorId);
        MDC.put("ingressTopic", topic);
        MDC.put("tenantId", Optional.ofNullable(tenant).orElse("unknown"));
        MDC.put("eventId", event.getId().toString());

        // Emit routing metric
        metricsCollector.incrementCounter(
                "aep.ingress.router.queue.routed",
                "connector_id", connectorId,
                "topic", topic,
                "event_type", event.getType(),
                "tenant", Optional.ofNullable(tenant).orElse("unknown")
        );

        log.debug("Routed queue message to connector '{}' for topic '{}', tenant '{}'",
                connectorId, topic, tenant);

        // In production, would route to actual queue source connector here
        return Promise.of(true);
    }

    /**
     * Unregisters a connector from routing.
     *
     * @param connectorId connector ID
     */
    public void unregisterConnector(String connectorId) {
        if (connectorRegistry.remove(connectorId) != null) {
            // Also remove from mapping tables
            pathToConnectorIdMapping.values().removeIf(id -> id.equals(connectorId));
            topicToConnectorIdMapping.values().removeIf(id -> id.equals(connectorId));

            log.info("Unregistered ingress connector '{}'", connectorId);
            metricsCollector.incrementCounter(
                    "aep.ingress.connector.unregistered",
                    "connector_id", connectorId
            );
        }
    }

    /**
     * Gets connector specification by ID.
     *
     * @param connectorId connector ID
     * @return connector spec or null if not found
     */
    public ConnectorSpec getConnector(String connectorId) {
        return connectorRegistry.get(connectorId);
    }

    // ==================== Helper Methods ====================
    /**
     * Registers HTTP ingress connector routing.
     */
    private void registerHttpConnector(ConnectorSpec connector) {
        // Extract path from config and register mapping
        // Config format: {"path": "/api/v1/events", "port": 8080, ...}
        String path = extractPathFromConfig(connector.getProperties());
        if (path != null && !path.isEmpty()) {
            pathToConnectorIdMapping.put(path, connector.getId());
            log.debug("Mapped HTTP path '{}' to connector '{}'", path, connector.getId());
        }
    }

    /**
     * Registers queue source connector routing.
     */
    private void registerQueueConnector(ConnectorSpec connector) {
        // Extract topic from config and register mapping
        // Config format: {"topic": "test-topic", "brokerUrls": [...], ...}
        String topic = extractTopicFromConfig(connector.getProperties());
        if (topic != null && !topic.isEmpty()) {
            topicToConnectorIdMapping.put(topic, connector.getId());
            log.debug("Mapped queue topic '{}' to connector '{}'", topic, connector.getId());
        }
    }

    /**
     * Extracts connector ID from HTTP request path.
     *
     * Expected path format: /api/v1/connectors/{connectorId}/events
     */
    private String extractConnectorIdFromPath(String path) {
        // Direct mapping lookup first
        if (pathToConnectorIdMapping.containsKey(path)) {
            return pathToConnectorIdMapping.get(path);
        }

        // Pattern-based extraction: /api/v1/connectors/{connectorId}/events
        String pattern = "/api/v1/connectors/";
        if (path.startsWith(pattern)) {
            int start = pattern.length();
            int end = path.indexOf("/", start);
            if (end > start) {
                return path.substring(start, end);
            } else if (end < 0) {
                return path.substring(start);
            }
        }

        return null;
    }

    /**
     * Extracts path from connector config properties.
     */
    private String extractPathFromConfig(Map<String, String> properties) {
        if (properties != null) {
            return properties.get("path");
        }
        return null;
    }

    /**
     * Extracts topic from connector config properties.
     */
    private String extractTopicFromConfig(Map<String, String> properties) {
        if (properties != null) {
            return properties.get("topic");
        }
        return null;
    }

    /**
     * Checks if connector type is ingress (source).
     */
    private boolean isIngressConnectorType(ConnectorType type) {
        return type == ConnectorType.HTTP_INGRESS
                || type == ConnectorType.QUEUE_SOURCE
                || type == ConnectorType.EVENT_CLOUD_SOURCE;
    }
}
