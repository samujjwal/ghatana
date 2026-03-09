package com.ghatana.yappc.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.IWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocketConnection.
 *
 * @doc.type class
 * @doc.purpose web socket connection
 * @doc.layer product
 * @doc.pattern Service
 */
public class WebSocketConnection {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConnection.class);
    private final String id;
    private final String userId;
    private final String tenantId;
    private final IWebSocket socket;
    private final ObjectMapper mapper;

    public WebSocketConnection(String id, String userId, String tenantId, IWebSocket socket, ObjectMapper mapper) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.socket = socket;
        this.mapper = mapper;
    }

    public void send(Object message) {
        try {
            String json = mapper.writeValueAsString(message);
            socket.writeMessage(IWebSocket.Message.text(json));
        } catch (Exception e) {
            logger.error("Failed to send message to user {}", userId, e);
        }
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public void close() { socket.close(); }
}
