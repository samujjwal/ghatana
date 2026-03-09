/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.websocket.handlers;

import com.ghatana.yappc.api.websocket.ConnectionManager;
import com.ghatana.yappc.api.websocket.WebSocketConnection;
import com.ghatana.yappc.api.websocket.WebSocketMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Handles notification WebSocket messages.
 *
 * @doc.type class
 * @doc.purpose Handle real-time notifications
 * @doc.layer application
 * @doc.pattern Handler
 */
@Singleton
public class NotificationHandler implements WebSocketMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(NotificationHandler.class);
    
    private final ConnectionManager connectionManager;
    
    @Inject
    public NotificationHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    @Override
    public String getMessageType() {
        return "notification";
    }
    
    @Override
    public void handleMessage(WebSocketConnection connection, Map<String, Object> message) {
        String action = (String) message.get("action");
        
        if (action == null) {
            sendError(connection, "Notification message must have an 'action' field");
            return;
        }
        
        switch (action) {
            case "subscribe":
                handleSubscribe(connection, message);
                break;
            case "unsubscribe":
                handleUnsubscribe(connection, message);
                break;
            case "mark_read":
                handleMarkRead(connection, message);
                break;
            default:
                logger.warn("Unknown notification action: {}", action);
                sendError(connection, "Unknown notification action: " + action);
        }
    }
    
    private void handleSubscribe(WebSocketConnection connection, Map<String, Object> message) {
        // In production, track subscription preferences
        logger.info("User {} subscribed to notifications", connection.getUserId());
        
        Map<String, Object> response = Map.of(
            "type", "notification:subscribed",
            "timestamp", System.currentTimeMillis()
        );
        connection.send(response);
    }
    
    private void handleUnsubscribe(WebSocketConnection connection, Map<String, Object> message) {
        // In production, remove subscription
        logger.info("User {} unsubscribed from notifications", connection.getUserId());
        
        Map<String, Object> response = Map.of(
            "type", "notification:unsubscribed",
            "timestamp", System.currentTimeMillis()
        );
        connection.send(response);
    }
    
    private void handleMarkRead(WebSocketConnection connection, Map<String, Object> message) {
        String notificationId = (String) message.get("notificationId");
        
        if (notificationId == null) {
            sendError(connection, "Mark read message must have 'notificationId'");
            return;
        }
        
        // In production, update notification read status in database
        logger.info("User {} marked notification {} as read", connection.getUserId(), notificationId);
        
        Map<String, Object> response = Map.of(
            "type", "notification:read",
            "notificationId", notificationId,
            "timestamp", System.currentTimeMillis()
        );
        connection.send(response);
    }
    
    /**
     * Send a notification to a specific user (called by NotificationService).
     */
    public void sendNotification(String tenantId, String userId, Map<String, Object> notification) {
        Map<String, Object> message = Map.of(
            "type", "notification:new",
            "notification", notification,
            "timestamp", System.currentTimeMillis()
        );
        
        connectionManager.sendToUser(tenantId, userId, message);
        logger.info("Sent notification to user {} in tenant {}", userId, tenantId);
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
