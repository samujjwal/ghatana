package com.ghatana.yappc.api.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import io.activej.inject.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConnectionManager.
 *
 * @doc.type class
 * @doc.purpose connection manager
 * @doc.layer product
 * @doc.pattern Service
 */
public class ConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);
    // Map<TenantId, Map<ConnectionId, Connection>>
    private final Map<String, Map<String, WebSocketConnection>> connections = new ConcurrentHashMap<>();

    @Inject
    public ConnectionManager() {}

    public void add(WebSocketConnection connection) {
        connections.computeIfAbsent(connection.getTenantId(), k -> new ConcurrentHashMap<>())
                   .put(connection.getId(), connection);
        logger.info("User {} connected to tenant {}", connection.getUserId(), connection.getTenantId());
    }

    public void remove(WebSocketConnection connection) {
        Map<String, WebSocketConnection> tenantConns = connections.get(connection.getTenantId());
        if (tenantConns != null) {
            tenantConns.remove(connection.getId());
            if (tenantConns.isEmpty()) {
                connections.remove(connection.getTenantId());
            }
        }
        logger.info("User {} disconnected from tenant {}", connection.getUserId(), connection.getTenantId());
    }

    public void broadcast(String tenantId, Object message) {
        Map<String, WebSocketConnection> tenantConns = connections.get(tenantId);
        if (tenantConns != null) {
            tenantConns.values().forEach(c -> c.send(message));
        }
    }

    public void sendToUser(String tenantId, String userId, Object message) {
        Map<String, WebSocketConnection> tenantConns = connections.get(tenantId);
        if (tenantConns != null) {
            tenantConns.values().stream()
                .filter(c -> c.getUserId().equals(userId))
                .forEach(c -> c.send(message));
        }
    }
}
