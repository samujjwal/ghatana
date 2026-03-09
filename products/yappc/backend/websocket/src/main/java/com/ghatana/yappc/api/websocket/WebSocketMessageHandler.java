/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.websocket;

import java.util.Map;

/**
 * Interface for WebSocket message handlers.
 *
 * @doc.type interface
 * @doc.purpose Handle specific types of WebSocket messages
 * @doc.layer infrastructure
 * @doc.pattern Handler
 */
public interface WebSocketMessageHandler {
    
    /**
     * Handle a WebSocket message.
     *
     * @param connection The WebSocket connection
     * @param message The parsed message data
     */
    void handleMessage(WebSocketConnection connection, Map<String, Object> message);
    
    /**
     * Get the message type this handler processes.
     */
    String getMessageType();
}
