/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.inject.annotation.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes WebSocket messages to appropriate handlers based on message type.
 *
 * @doc.type class
 * @doc.purpose Message routing for WebSocket communications
 * @doc.layer infrastructure
 * @doc.pattern Router
 */
public class MessageRouter {
    private static final Logger logger = LoggerFactory.getLogger(MessageRouter.class);
    
    private final Map<String, WebSocketMessageHandler> handlers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    
    @Inject
    public MessageRouter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Register a message handler for a specific message type.
     */
    public void registerHandler(String messageType, WebSocketMessageHandler handler) {
        handlers.put(messageType, handler);
        logger.info("Registered WebSocket handler for message type: {}", messageType);
    }
    
    /**
     * Route a message to the appropriate handler.
     */
    public void routeMessage(WebSocketConnection connection, String messageText) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = objectMapper.readValue(messageText, Map.class);
            String type = (String) message.get("type");
            
            if (type == null) {
                logger.warn("Message missing 'type' field from user {}", connection.getUserId());
                sendError(connection, "Message must have a 'type' field");
                return;
            }
            
            WebSocketMessageHandler handler = handlers.get(type);
            if (handler == null) {
                logger.warn("No handler found for message type: {}", type);
                sendError(connection, "Unknown message type: " + type);
                return;
            }
            
            handler.handleMessage(connection, message);
            
        } catch (Exception e) {
            logger.error("Error routing message from user {}", connection.getUserId(), e);
            sendError(connection, "Error processing message: " + e.getMessage());
        }
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
