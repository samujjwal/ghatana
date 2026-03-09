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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles team chat WebSocket messages.
 *
 * @doc.type class
 * @doc.purpose Handle real-time team chat
 * @doc.layer application
 * @doc.pattern Handler
 */
@Singleton
public class ChatHandler implements WebSocketMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);
    
    private final ConnectionManager connectionManager;
    
    @Inject
    public ChatHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    @Override
    public String getMessageType() {
        return "chat";
    }
    
    @Override
    public void handleMessage(WebSocketConnection connection, Map<String, Object> message) {
        String action = (String) message.get("action");
        
        if (action == null) {
            sendError(connection, "Chat message must have an 'action' field");
            return;
        }
        
        switch (action) {
            case "send":
                handleSendMessage(connection, message);
                break;
            case "typing":
                handleTyping(connection, message);
                break;
            case "read":
                handleRead(connection, message);
                break;
            case "react":
                handleReaction(connection, message);
                break;
            default:
                logger.warn("Unknown chat action: {}", action);
                sendError(connection, "Unknown chat action: " + action);
        }
    }
    
    private void handleSendMessage(WebSocketConnection connection, Map<String, Object> message) {
        String channelId = (String) message.get("channelId");
        String text = (String) message.get("text");
        
        if (channelId == null || text == null) {
            sendError(connection, "Send message must have 'channelId' and 'text'");
            return;
        }
        
        // In production, save to database first
        String messageId = UUID.randomUUID().toString();
        
        // Broadcast to all users in the channel
        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("type", "chat:message");
        broadcast.put("messageId", messageId);
        broadcast.put("channelId", channelId);
        broadcast.put("userId", connection.getUserId());
        broadcast.put("text", text);
        broadcast.put("timestamp", System.currentTimeMillis());
        
        // Include optional fields
        if (message.containsKey("parentId")) {
            broadcast.put("parentId", message.get("parentId"));
        }
        if (message.containsKey("attachments")) {
            broadcast.put("attachments", message.get("attachments"));
        }
        if (message.containsKey("mentions")) {
            broadcast.put("mentions", message.get("mentions"));
        }
        
        connectionManager.broadcast(connection.getTenantId(), broadcast);
        
        logger.info("Chat message sent by user {} in channel {}", connection.getUserId(), channelId);
    }
    
    private void handleTyping(WebSocketConnection connection, Map<String, Object> message) {
        String channelId = (String) message.get("channelId");
        Boolean isTyping = (Boolean) message.get("isTyping");
        
        if (channelId == null || isTyping == null) {
            sendError(connection, "Typing message must have 'channelId' and 'isTyping'");
            return;
        }
        
        // Broadcast typing indicator
        Map<String, Object> broadcast = Map.of(
            "type", "chat:typing",
            "channelId", channelId,
            "userId", connection.getUserId(),
            "isTyping", isTyping,
            "timestamp", System.currentTimeMillis()
        );
        
        connectionManager.broadcast(connection.getTenantId(), broadcast);
    }
    
    private void handleRead(WebSocketConnection connection, Map<String, Object> message) {
        String channelId = (String) message.get("channelId");
        String messageId = (String) message.get("messageId");
        
        if (channelId == null || messageId == null) {
            sendError(connection, "Read message must have 'channelId' and 'messageId'");
            return;
        }
        
        // In production, update read status in database
        
        // Broadcast read receipt
        Map<String, Object> broadcast = Map.of(
            "type", "chat:read",
            "channelId", channelId,
            "messageId", messageId,
            "userId", connection.getUserId(),
            "timestamp", System.currentTimeMillis()
        );
        
        connectionManager.broadcast(connection.getTenantId(), broadcast);
    }
    
    private void handleReaction(WebSocketConnection connection, Map<String, Object> message) {
        String messageId = (String) message.get("messageId");
        String emoji = (String) message.get("emoji");
        
        if (messageId == null || emoji == null) {
            sendError(connection, "Reaction message must have 'messageId' and 'emoji'");
            return;
        }
        
        // In production, save reaction to database
        
        // Broadcast reaction
        Map<String, Object> broadcast = Map.of(
            "type", "chat:reaction",
            "messageId", messageId,
            "userId", connection.getUserId(),
            "emoji", emoji,
            "timestamp", System.currentTimeMillis()
        );
        
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
