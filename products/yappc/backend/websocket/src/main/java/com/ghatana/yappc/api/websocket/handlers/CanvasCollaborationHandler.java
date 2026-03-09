/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.websocket.handlers;

import com.ghatana.yappc.api.websocket.ConnectionManager;
import com.ghatana.yappc.api.websocket.PresenceManager;
import com.ghatana.yappc.api.websocket.WebSocketConnection;
import com.ghatana.yappc.api.websocket.WebSocketMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles canvas collaboration WebSocket messages.
 *
 * @doc.type class
 * @doc.purpose Handle real-time canvas collaboration
 * @doc.layer application
 * @doc.pattern Handler
 */
@Singleton
public class CanvasCollaborationHandler implements WebSocketMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(CanvasCollaborationHandler.class);
    
    private final ConnectionManager connectionManager;
    private final PresenceManager presenceManager;
    
    @Inject
    public CanvasCollaborationHandler(ConnectionManager connectionManager, PresenceManager presenceManager) {
        this.connectionManager = connectionManager;
        this.presenceManager = presenceManager;
    }
    
    @Override
    public String getMessageType() {
        return "canvas";
    }
    
    @Override
    public void handleMessage(WebSocketConnection connection, Map<String, Object> message) {
        String action = (String) message.get("action");
        
        if (action == null) {
            sendError(connection, "Canvas message must have an 'action' field");
            return;
        }
        
        switch (action) {
            case "join":
                handleJoin(connection, message);
                break;
            case "leave":
                handleLeave(connection, message);
                break;
            case "update":
                handleUpdate(connection, message);
                break;
            case "cursor":
                handleCursor(connection, message);
                break;
            case "selection":
                handleSelection(connection, message);
                break;
            default:
                logger.warn("Unknown canvas action: {}", action);
                sendError(connection, "Unknown canvas action: " + action);
        }
    }
    
    private void handleJoin(WebSocketConnection connection, Map<String, Object> message) {
        String sessionId = (String) message.get("sessionId");
        if (sessionId == null) {
            sendError(connection, "Join message must have a 'sessionId'");
            return;
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("canvasId", message.get("canvasId"));
        
        presenceManager.userConnected(
            connection.getTenantId(),
            connection.getUserId(),
            sessionId,
            metadata
        );
        
        // Send current online users to the joining user
        Map<String, Object> response = Map.of(
            "type", "canvas:joined",
            "sessionId", sessionId,
            "onlineUsers", presenceManager.getOnlineUsersInSession(connection.getTenantId(), sessionId),
            "timestamp", System.currentTimeMillis()
        );
        connection.send(response);
        
        logger.info("User {} joined canvas session {}", connection.getUserId(), sessionId);
    }
    
    private void handleLeave(WebSocketConnection connection, Map<String, Object> message) {
        String sessionId = (String) message.get("sessionId");
        if (sessionId == null) {
            sendError(connection, "Leave message must have a 'sessionId'");
            return;
        }
        
        presenceManager.userDisconnected(
            connection.getTenantId(),
            connection.getUserId(),
            sessionId
        );
        
        logger.info("User {} left canvas session {}", connection.getUserId(), sessionId);
    }
    
    private void handleUpdate(WebSocketConnection connection, Map<String, Object> message) {
        String sessionId = (String) message.get("sessionId");
        @SuppressWarnings("unchecked")
        Map<String, Object> operation = (Map<String, Object>) message.get("operation");
        
        if (sessionId == null || operation == null) {
            sendError(connection, "Update message must have 'sessionId' and 'operation'");
            return;
        }
        
        // Broadcast the update to all other users in the session
        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("type", "canvas:update");
        broadcast.put("sessionId", sessionId);
        broadcast.put("userId", connection.getUserId());
        broadcast.put("operation", operation);
        broadcast.put("timestamp", System.currentTimeMillis());
        
        connectionManager.broadcast(connection.getTenantId(), broadcast);
        
        logger.debug("Canvas update from user {} in session {}: {}", 
            connection.getUserId(), sessionId, operation.get("type"));
    }
    
    private void handleCursor(WebSocketConnection connection, Map<String, Object> message) {
        String sessionId = (String) message.get("sessionId");
        @SuppressWarnings("unchecked")
        Map<String, Object> cursor = (Map<String, Object>) message.get("cursor");
        
        if (sessionId == null || cursor == null) {
            sendError(connection, "Cursor message must have 'sessionId' and 'cursor'");
            return;
        }
        
        presenceManager.updateCursor(
            connection.getTenantId(),
            connection.getUserId(),
            sessionId,
            cursor
        );
    }
    
    private void handleSelection(WebSocketConnection connection, Map<String, Object> message) {
        String sessionId = (String) message.get("sessionId");
        @SuppressWarnings("unchecked")
        Map<String, Object> selection = (Map<String, Object>) message.get("selection");
        
        if (sessionId == null) {
            sendError(connection, "Selection message must have 'sessionId'");
            return;
        }
        
        // Broadcast selection to other users
        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("type", "canvas:selection");
        broadcast.put("sessionId", sessionId);
        broadcast.put("userId", connection.getUserId());
        broadcast.put("selection", selection);
        broadcast.put("timestamp", System.currentTimeMillis());
        
        connectionManager.broadcast(connection.getTenantId(), broadcast);
    }
    
    private void sendError(WebSocketConnection connection, String error) {
        Map<String, Object> errorMessage = Map.of(
            "type", "error",
            "error", error,
            "timestamp", System.currentTimeMillis()
        );
        connection.send(errorMessage);
    }
}
