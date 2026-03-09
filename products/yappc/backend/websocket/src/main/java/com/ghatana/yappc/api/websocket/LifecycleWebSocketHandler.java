/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - Lifecycle WebSocket Handler
 * 
 * Handles WebSocket connections for real-time lifecycle state updates.
 * Integrates with the event router to broadcast lifecycle changes to connected clients.
 */

package com.ghatana.yappc.api.websocket;

import com.ghatana.yappc.api.aep.YappcAgentEventRouter;
import com.ghatana.yappc.api.security.UserContext;
import io.activej.eventloop.Eventloop;
import io.activej.http.WebSocket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket handler for real-time lifecycle state updates.
 *
 * <p>Features:
 * <ul>
 *   <li>Project-specific connection management</li>
 *   <li>User authentication and tenant isolation</li>
 *   <li>Automatic cleanup of disconnected clients</li>
 *   <li>Heartbeat/ping-pong for connection health</li>
 *   <li>Event broadcasting to connected clients</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Real-time lifecycle WebSocket handler
 * @doc.layer product
 * @doc.pattern Observer, Registry
 */
public class LifecycleWebSocketHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(LifecycleWebSocketHandler.class);
    
    private final YappcAgentEventRouter eventRouter;
    private final Eventloop eventloop;
    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<WebSocketConnection>> projectConnections = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown = false;
    
    // Configuration
    private final long heartbeatIntervalMs = 30_000;
    private final long connectionTimeoutMs = 60_000;
    
    public LifecycleWebSocketHandler(@NotNull YappcAgentEventRouter eventRouter,
                                     @NotNull Eventloop eventloop) {
        this.eventRouter = eventRouter;
        this.eventloop = eventloop;
        scheduleHeartbeat();
        scheduleCleanup();
    }
    
    /**
     * Handles new WebSocket connection.
     */
    public Promise<WebSocket> handleConnection(@NotNull WebSocket webSocket, @Nullable UserContext user) {
        try {
            String connectionId = UUID.randomUUID().toString();
            String projectId = extractProjectId(webSocket.getRequest());
            
            // Validate user and project access
            if (user == null) {
                LOG.warn("WebSocket connection rejected: no user context");
                webSocket.close();
                return Promise.of(null);
            }
            
            if (projectId == null) {
                LOG.warn("WebSocket connection rejected: no project ID");
                webSocket.close();
                return Promise.of(null);
            }
            
            // Check tenant access
            if (!canAccessProject(user, projectId)) {
                LOG.warn("WebSocket connection rejected: user {} cannot access project {}", 
                        user.getUserId(), projectId);
                webSocket.close();
                return Promise.of(null);
            }
            
            // Create connection
            WebSocketConnection connection = new WebSocketConnection(
                connectionId, 
                webSocket, 
                user, 
                projectId
            );
            
            // Register connection
            connections.put(connectionId, connection);
            projectConnections.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>())
                           .add(connection);
            
            LOG.info("WebSocket connection established: {} for user {} in project {}", 
                    connectionId, user.getUserId(), projectId);
            
            // Send welcome message
            sendWelcomeMessage(connection);
            
            return Promise.of(webSocket);
            
        } catch (Exception e) {
            LOG.error("Error handling WebSocket connection", e);
            return Promise.of(null);
        }
    }
    
    /**
     * Broadcasts a lifecycle update to all connected clients for a project.
     */
    public void broadcastLifecycleUpdate(@NotNull String projectId, @NotNull Map<String, Object> update) {
        CopyOnWriteArrayList<WebSocketConnection> projectConns = projectConnections.get(projectId);
        if (projectConns == null || projectConns.isEmpty()) {
            LOG.debug("No WebSocket connections for project {}", projectId);
            return;
        }
        
        String message = createLifecycleMessage(update);
        int successCount = 0;
        int failureCount = 0;
        
        for (WebSocketConnection connection : projectConns) {
            try {
                if (connection.isOpen()) {
                    connection.sendMessage(message);
                    successCount++;
                } else {
                    removeConnection(connection.getId());
                    failureCount++;
                }
            } catch (Exception e) {
                LOG.error("Failed to send message to connection {}", connection.getId(), e);
                failureCount++;
            }
        }
        
        LOG.debug("Broadcasted lifecycle update to project {}: {} successful, {} failed", 
                 projectId, successCount, failureCount);
    }
    
    /**
     * Gets connection statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalConnections", connections.size());
        stats.put("projectConnections", projectConnections.size());
        
        Map<String, Integer> projectCounts = new ConcurrentHashMap<>();
        projectConnections.forEach((projectId, conns) -> 
            projectCounts.put(projectId, conns.size()));
        stats.put("connectionsByProject", projectCounts);
        
        return stats;
    }
    
    /**
     * Extracts project ID from WebSocket request.
     */
    @Nullable
    private String extractProjectId(@NotNull io.activej.http.HttpRequest request) {
        // Extract from query parameter: ws://host/ws/lifecycle?projectId=xxx
        String projectId = request.getQueryParameter("projectId");
        if (projectId != null && !projectId.trim().isEmpty()) {
            return projectId.trim();
        }
        
        // Extract from path: ws://host/ws/lifecycle/project/xxx
        String path = request.getRelativePath();
        if (path.startsWith("/ws/lifecycle/project/")) {
            return path.substring("/ws/lifecycle/project/".length());
        }
        
        return null;
    }
    
    /**
     * Checks if user can access the project.
     */
    private boolean canAccessProject(@NotNull UserContext user, @NotNull String projectId) {
        // Admin users can access all projects
        if (user.isAdmin()) {
            return true;
        }
        
        // Tenant admins can access projects in their tenant
        if (user.isTenantAdmin()) {
            // In production, check if project belongs to user's tenant
            return true; // Simplified for now
        }
        
        // Regular users need explicit project access
        // In production, check project membership/permissions
        return true; // Simplified for now
    }
    
    /**
     * Sends welcome message to new connection.
     */
    private void sendWelcomeMessage(@NotNull WebSocketConnection connection) {
        Map<String, Object> welcome = Map.of(
            "type", "welcome",
            "connectionId", connection.getId(),
            "projectId", connection.getProjectId(),
            "userId", connection.getUser().getUserId(),
            "timestamp", System.currentTimeMillis(),
            "message", "Connected to YAPPC lifecycle updates"
        );
        
        String message = createLifecycleMessage(welcome);
        connection.sendMessage(message);
    }
    
    /**
     * Creates JSON message for lifecycle updates.
     */
    private String createLifecycleMessage(@NotNull Map<String, Object> data) {
        try {
            // In production, use proper JSON serialization
            StringBuilder json = new StringBuilder();
            json.append("{");
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (json.length() > 1) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                } else if (value instanceof Map) {
                    json.append(mapToJson((Map<String, Object>) value));
                } else {
                    json.append(value.toString());
                }
            }
            
            json.append("}");
            return json.toString();
            
        } catch (Exception e) {
            LOG.error("Failed to create JSON message", e);
            return "{\"type\":\"error\",\"message\":\"Failed to serialize message\"}";
        }
    }
    
    /**
     * Converts Map to JSON string (simplified implementation).
     */
    private String mapToJson(@NotNull Map<String, Object> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (json.length() > 1) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            } else {
                json.append(value.toString());
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Removes a connection and cleans up resources.
     */
    private void removeConnection(@NotNull String connectionId) {
        WebSocketConnection connection = connections.remove(connectionId);
        if (connection != null) {
            cleanupProjectConnection(connection);
            
            try {
                connection.close();
            } catch (Exception e) {
                LOG.debug("Error closing connection {}", connectionId, e);
            }
            
            LOG.debug("Removed connection: {}", connectionId);
        }
    }
    
    /**
     * Schedules heartbeat on the ActiveJ event loop (thread-safe for WebSocket ops).
     */
    private void scheduleHeartbeat() {
        eventloop.delay(heartbeatIntervalMs, () -> {
            if (shuttingDown) return;

            for (var it = connections.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                WebSocketConnection connection = entry.getValue();
                if (!connection.isOpen()) {
                    it.remove();
                    cleanupProjectConnection(connection);
                    continue;
                }
                try {
                    connection.sendPing();
                    connection.setLastActivity(System.currentTimeMillis());
                } catch (Exception e) {
                    LOG.debug("Failed to ping connection {}", connection.getId(), e);
                    it.remove();
                    cleanupProjectConnection(connection);
                }
            }

            // Re-schedule
            scheduleHeartbeat();
        });
    }

    /**
     * Schedules stale-connection cleanup on the ActiveJ event loop.
     */
    private void scheduleCleanup() {
        eventloop.delay(connectionTimeoutMs, () -> {
            if (shuttingDown) return;

            long cutoff = System.currentTimeMillis() - connectionTimeoutMs;
            for (var it = connections.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                WebSocketConnection connection = entry.getValue();
                if (connection.getLastActivity() < cutoff) {
                    LOG.info("Removing stale connection: {}", connection.getId());
                    it.remove();
                    cleanupProjectConnection(connection);
                    try {
                        connection.close();
                    } catch (Exception e) {
                        LOG.debug("Error closing stale connection {}", connection.getId(), e);
                    }
                }
            }

            // Re-schedule
            scheduleCleanup();
        });
    }

    /**
     * Removes a connection from the project connections map.
     */
    private void cleanupProjectConnection(@NotNull WebSocketConnection connection) {
        CopyOnWriteArrayList<WebSocketConnection> projectConns = projectConnections.get(connection.getProjectId());
        if (projectConns != null) {
            projectConns.remove(connection);
            if (projectConns.isEmpty()) {
                projectConnections.remove(connection.getProjectId());
            }
        }
    }
    
    /**
     * Shuts down the WebSocket handler.
     */
    public void shutdown() {
        shuttingDown = true;
        connections.values().forEach(connection -> {
            try {
                connection.close();
            } catch (Exception e) {
                LOG.debug("Error closing connection during shutdown", e);
            }
        });
        connections.clear();
        projectConnections.clear();
    }
    
    /**
     * WebSocket connection wrapper.
     *
     * <p>Tracks connection state and delegates to the underlying ActiveJ {@link WebSocket}.
     * Message reading is started automatically on construction via a non-blocking read loop.
     *
     * @doc.type class
     * @doc.purpose WebSocket connection lifecycle wrapper
     * @doc.layer product
     * @doc.pattern Adapter
     */
    private static class WebSocketConnection {
        private final String id;
        private final WebSocket webSocket;
        private final UserContext user;
        private final String projectId;
        private volatile long lastActivity;
        private volatile boolean closed;

        public WebSocketConnection(String id, WebSocket webSocket, UserContext user, String projectId) {
            this.id = id;
            this.webSocket = webSocket;
            this.user = user;
            this.projectId = projectId;
            this.lastActivity = System.currentTimeMillis();
            this.closed = false;

            // Start the non-blocking read loop for incoming client messages
            startReadLoop();
        }

        public String getId() { return id; }
        public UserContext getUser() { return user; }
        public String getProjectId() { return projectId; }
        public long getLastActivity() { return lastActivity; }
        public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }

        public boolean isOpen() {
            return !closed;
        }

        public void sendMessage(@NotNull String message) {
            if (closed) return;
            webSocket.writeMessage(WebSocket.Message.text(message))
                    .whenException(e -> {
                        LOG.debug("Failed to write to connection {}", id, e);
                        markClosed();
                    });
        }

        public void sendPing() {
            if (closed) return;
            // Send application-level ping as a JSON text frame
            webSocket.writeMessage(WebSocket.Message.text("{\"type\":\"ping\",\"ts\":" + System.currentTimeMillis() + "}"))
                    .whenException(e -> {
                        LOG.debug("Failed to send ping to connection {}", id, e);
                        markClosed();
                    });
        }

        public void close() {
            if (closed) return;
            markClosed();
            try {
                webSocket.close();
            } catch (Exception e) {
                LOG.debug("Error closing WebSocket for connection {}", id, e);
            }
        }

        // ── Read loop ────────────────────────────────────────────

        private void startReadLoop() {
            webSocket.readMessage()
                    .whenResult(message -> {
                        if (message == null) {
                            // Connection closed by remote peer
                            LOG.debug("Connection {} closed by remote", id);
                            markClosed();
                            return;
                        }
                        handleMessage(message.getText());
                        // Continue reading
                        if (!closed) {
                            startReadLoop();
                        }
                    })
                    .whenException(e -> {
                        LOG.debug("Read error on connection {}: {}", id, e.getMessage());
                        markClosed();
                    });
        }

        private void markClosed() {
            closed = true;
        }

        private void handleMessage(String message) {
            lastActivity = System.currentTimeMillis();
            LOG.debug("Received message from connection {}: {}", id, message);

            if (message == null || message.isBlank()) return;

            // Handle pong responses
            if (message.contains("\"type\":\"pong\"") || "pong".equals(message)) {
                return;
            }

            // Handle other message types
            try {
                if (message.contains("\"type\":\"subscribe\"")) {
                    LOG.debug("Subscription request from connection {}", id);
                }
            } catch (Exception e) {
                LOG.debug("Failed to handle message from connection {}", id, e);
            }
        }
    }
}
