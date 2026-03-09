package com.ghatana.core.websocket;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;

/**
 * Manages active WebSocket connections with topic-based organization.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized management of WebSocket connections with support for:
 * - Topic-based subscription management
 * - Tenant isolation
 * - Broadcast and targeted messaging
 * - Connection lifecycle tracking
 *
 * <p><b>Architecture Role</b><br>
 * Connection manager in libs/java/activej-websocket for the Hybrid Backend.
 * Used by:
 * - WebSocketServer - Register/unregister connections
 * - StreamPublisher - Find connections for broadcast
 * - EventCloud - Send real-time events
 * - AEP - Send pipeline/agent state updates
 *
 * <p><b>Thread Safety</b><br>
 * All operations are thread-safe using ConcurrentHashMap and CopyOnWriteArraySet.
 *
 * @doc.type class
 * @doc.purpose WebSocket connection manager with topic-based routing
 * @doc.layer platform
 * @doc.pattern Service
 */
@Slf4j
public class WebSocketConnectionManager {

    /**
     * Connections organized by topic
     * Key: topic name
     * Value: Set of connections subscribed to that topic
     */
    private final Map<String, Set<WebSocketConnection>> connectionsByTopic = new ConcurrentHashMap<>();

    /**
     * All connections indexed by connection ID
     */
    private final Map<String, WebSocketConnection> connectionsById = new ConcurrentHashMap<>();

    /**
     * Connections organized by tenant for tenant-scoped operations
     */
    private final Map<String, Set<WebSocketConnection>> connectionsByTenant = new ConcurrentHashMap<>();

    /**
     * Register a new connection
     *
     * @param connection Connection to register
     */
    public void addConnection(WebSocketConnection connection) {
        String id = connection.getConnectionId();
        String topic = connection.getTopic();
        String tenantId = connection.getTenantId();

        // Add to ID index
        connectionsById.put(id, connection);

        // Add to topic set
        connectionsByTopic
                .computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>())
                .add(connection);

        // Add to tenant set
        if (tenantId != null && !tenantId.isEmpty()) {
            connectionsByTenant
                    .computeIfAbsent(tenantId, k -> new CopyOnWriteArraySet<>())
                    .add(connection);
        }

        log.info("WebSocket connection registered: id={}, topic={}, tenant={}",
                id, topic, tenantId);
    }

    /**
     * Remove a connection
     *
     * @param connection Connection to remove
     */
    public void removeConnection(WebSocketConnection connection) {
        String id = connection.getConnectionId();
        String topic = connection.getTopic();
        String tenantId = connection.getTenantId();

        // Remove from ID index
        connectionsById.remove(id);

        // Remove from topic set
        Set<WebSocketConnection> topicConnections = connectionsByTopic.get(topic);
        if (topicConnections != null) {
            topicConnections.remove(connection);
            if (topicConnections.isEmpty()) {
                connectionsByTopic.remove(topic);
            }
        }

        // Remove from tenant set
        if (tenantId != null && !tenantId.isEmpty()) {
            Set<WebSocketConnection> tenantConnections = connectionsByTenant.get(tenantId);
            if (tenantConnections != null) {
                tenantConnections.remove(connection);
                if (tenantConnections.isEmpty()) {
                    connectionsByTenant.remove(tenantId);
                }
            }
        }

        log.info("WebSocket connection unregistered: id={}, topic={}", id, topic);
    }

    /**
     * Get connection by ID
     *
     * @param connectionId Connection ID
     * @return Connection or null if not found
     */
    public WebSocketConnection getConnection(String connectionId) {
        return connectionsById.get(connectionId);
    }

    /**
     * Get all connections for a topic
     *
     * @param topic Topic name
     * @return Set of connections (may be empty, never null)
     */
    public Set<WebSocketConnection> getConnectionsForTopic(String topic) {
        Set<WebSocketConnection> connections = connectionsByTopic.get(topic);
        return connections != null ? Set.copyOf(connections) : Set.of();
    }

    /**
     * Get all connections for a tenant
     *
     * @param tenantId Tenant ID
     * @return Set of connections (may be empty, never null)
     */
    public Set<WebSocketConnection> getConnectionsForTenant(String tenantId) {
        Set<WebSocketConnection> connections = connectionsByTenant.get(tenantId);
        return connections != null ? Set.copyOf(connections) : Set.of();
    }

    /**
     * Get all connections matching a filter
     *
     * @param filter Predicate to match connections
     * @return Set of matching connections
     */
    public Set<WebSocketConnection> findConnections(Predicate<WebSocketConnection> filter) {
        return connectionsById.values().stream()
                .filter(filter)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Broadcast message to all connections on a topic
     *
     * @param topic   Topic to broadcast to
     * @param message Message to send
     * @return Promise that completes when all sends are initiated
     */
    public Promise<Void> broadcast(String topic, String message) {
        Set<WebSocketConnection> connections = getConnectionsForTopic(topic);
        if (connections.isEmpty()) {
            log.debug("No connections for topic: {}", topic);
            return Promise.complete();
        }

        log.debug("Broadcasting to {} connections on topic: {}", connections.size(), topic);

        return Promises.all(
                connections.stream()
                        .filter(WebSocketConnection::isOpen)
                        .map(conn -> conn.send(message)
                                .whenException(e -> log.warn("Failed to send to {}: {}",
                                        conn.getConnectionId(), e.getMessage())))
                        .toList()
        );
    }

    /**
     * Broadcast message to all connections on a topic for a specific tenant
     *
     * @param topic    Topic to broadcast to
     * @param tenantId Tenant ID to filter by
     * @param message  Message to send
     * @return Promise that completes when all sends are initiated
     */
    public Promise<Void> broadcastToTenant(String topic, String tenantId, String message) {
        Set<WebSocketConnection> topicConnections = getConnectionsForTopic(topic);
        if (topicConnections.isEmpty()) {
            return Promise.complete();
        }

        Set<WebSocketConnection> tenantConnections = topicConnections.stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .filter(WebSocketConnection::isOpen)
                .collect(java.util.stream.Collectors.toSet());

        if (tenantConnections.isEmpty()) {
            log.debug("No connections for topic {} and tenant {}", topic, tenantId);
            return Promise.complete();
        }

        log.debug("Broadcasting to {} connections on topic {} for tenant {}",
                tenantConnections.size(), topic, tenantId);

        return Promises.all(
                tenantConnections.stream()
                        .map(conn -> conn.send(message)
                                .whenException(e -> log.warn("Failed to send to {}: {}",
                                        conn.getConnectionId(), e.getMessage())))
                        .toList()
        );
    }

    /**
     * Broadcast binary data to all connections on a topic
     *
     * @param topic Topic to broadcast to
     * @param data  Binary data to send
     * @return Promise that completes when all sends are initiated
     */
    public Promise<Void> broadcast(String topic, byte[] data) {
        Set<WebSocketConnection> connections = getConnectionsForTopic(topic);
        if (connections.isEmpty()) {
            return Promise.complete();
        }

        return Promises.all(
                connections.stream()
                        .filter(WebSocketConnection::isOpen)
                        .map(conn -> conn.send(data)
                                .whenException(e -> log.warn("Failed to send to {}: {}",
                                        conn.getConnectionId(), e.getMessage())))
                        .toList()
        );
    }

    /**
     * Get count of active connections
     *
     * @return Number of connections
     */
    public int getConnectionCount() {
        return connectionsById.size();
    }

    /**
     * Get count of connections for a topic
     *
     * @param topic Topic name
     * @return Number of connections
     */
    public int getConnectionCount(String topic) {
        Set<WebSocketConnection> connections = connectionsByTopic.get(topic);
        return connections != null ? connections.size() : 0;
    }

    /**
     * Get all active topics
     *
     * @return Set of topic names
     */
    public Set<String> getActiveTopics() {
        return Set.copyOf(connectionsByTopic.keySet());
    }

    /**
     * Close all connections
     *
     * @return Promise that completes when all connections are closed
     */
    public Promise<Void> closeAll() {
        log.info("Closing all {} WebSocket connections", connectionsById.size());

        return Promises.all(
                connectionsById.values().stream()
                        .map(WebSocketConnection::close)
                        .toList()
        ).then($ -> {
            connectionsByTopic.clear();
            connectionsByTenant.clear();
            connectionsById.clear();
            return Promise.complete();
        });
    }

    /**
     * Close all connections for a tenant
     *
     * @param tenantId Tenant ID
     * @return Promise that completes when all connections are closed
     */
    public Promise<Void> closeAllForTenant(String tenantId) {
        Set<WebSocketConnection> connections = getConnectionsForTenant(tenantId);
        if (connections.isEmpty()) {
            return Promise.complete();
        }

        log.info("Closing {} WebSocket connections for tenant {}", connections.size(), tenantId);

        return Promises.all(
                connections.stream()
                        .map(conn -> conn.close().then($ -> {
                            removeConnection(conn);
                            return Promise.complete();
                        }))
                        .toList()
        );
    }

    /**
     * Cleanup stale connections (not active for given duration)
     *
     * @param maxIdleSeconds Maximum idle time in seconds
     * @return Number of connections closed
     */
    public int cleanupStaleConnections(long maxIdleSeconds) {
        java.time.Instant cutoff = java.time.Instant.now().minusSeconds(maxIdleSeconds);
        int closedCount = 0;

        for (WebSocketConnection conn : connectionsById.values()) {
            java.time.Instant lastActivity = conn.getLastActivityAt();
            if (lastActivity != null && lastActivity.isBefore(cutoff)) {
                conn.close();
                removeConnection(conn);
                closedCount++;
            }
        }

        if (closedCount > 0) {
            log.info("Closed {} stale WebSocket connections (idle > {}s)", closedCount, maxIdleSeconds);
        }

        return closedCount;
    }
}
