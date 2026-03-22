/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.api.http.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebSocket handler for real-time progress updates.
 * Framework-independent — uses send/close function references for transport.
 *
 * @doc.type class
 * @doc.purpose WebSocket progress broadcasting
 * @doc.layer platform
 * @doc.pattern Observer
 */
public final class ProgressWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressWebSocket.class);

    private final Map<String, ProgressSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    // ── Framework-independent handlers ──────────────────────────────────────

    /**
     * Handle a new WebSocket connection.
     *
     * @param sessionId unique identifier
     * @param sendFn    function to send a serialised message to the client
     * @param closeFn   function to close the transport
     */
    public void handleConnect(String sessionId, Consumer<String> sendFn, Runnable closeFn) {
        sessions.put(sessionId, new ProgressSession(sessionId, sendFn, closeFn));
        LOG.info("WebSocket connected: sessionId={}", sessionId);
        sendJson(sessionId, new ConnectionMessage("connected", sessionId, Instant.now().toString()));
    }

    /**
     * Handle an incoming text message.
     */
    public void handleMessage(String sessionId, String rawJson) {
        LOG.debug("WebSocket message from {}: {}", sessionId, rawJson);
        try {
            ClientMessage msg = objectMapper.readValue(rawJson, ClientMessage.class);
            switch (msg.type()) {
                case "ping" -> sendJson(sessionId, new PongMessage("pong", Instant.now().toString()));
                case "subscribe" -> {
                    LOG.debug("Session {} subscribed to channel: {}", sessionId, msg.channel());
                    sendJson(sessionId, new SubscribedMessage("subscribed", msg.channel()));
                }
                case "unsubscribe" -> {
                    LOG.debug("Session {} unsubscribed from channel: {}", sessionId, msg.channel());
                    sendJson(sessionId, new UnsubscribedMessage("unsubscribed", msg.channel()));
                }
                default -> LOG.warn("Unknown message type: {}", msg.type());
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse WebSocket message: {}", rawJson);
            sendJson(sessionId, new ErrorMessage("error", "Invalid message format"));
        }
    }

    /**
     * Handle WebSocket close.
     */
    public void handleClose(String sessionId) {
        sessions.remove(sessionId);
        LOG.info("WebSocket disconnected: sessionId={}", sessionId);
    }

    /**
     * Handle WebSocket error.
     */
    public void handleError(String sessionId, Throwable error) {
        LOG.error("WebSocket error for session {}: {}", sessionId,
                error != null ? error.getMessage() : "unknown");
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public void broadcast(String sessionId, Object progress) {
        sendJson(sessionId, new ProgressMessage("progress", progress, Instant.now().toString()));
    }

    public void broadcastToAll(Object message) {
        for (ProgressSession s : sessions.values()) {
            try { s.sendFn().accept(objectMapper.writeValueAsString(message)); }
            catch (JsonProcessingException e) { LOG.error("Failed to serialise broadcast", e); }
        }
    }

    public void sendComplete(String sessionId, Object result) {
        sendJson(sessionId, new CompleteMessage("complete", result, Instant.now().toString()));
    }

    public void sendError(String sessionId, String error) {
        sendJson(sessionId, new ErrorMessage("error", error));
    }

    public int getConnectionCount() { return sessions.size(); }

    public boolean isConnected(String sessionId) { return sessions.containsKey(sessionId); }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void sendJson(String sessionId, Object message) {
        ProgressSession session = sessions.get(sessionId);
        if (session != null) {
            try {
                session.sendFn().accept(objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException e) {
                LOG.error("Failed to serialize WebSocket message", e);
            }
        }
    }

    private record ProgressSession(String sessionId, Consumer<String> sendFn, Runnable closeFn) {}

    // Message types
    public record ClientMessage(String type, String channel, Map<String, Object> data) {}
    public record ConnectionMessage(String type, String sessionId, String timestamp) {}
    public record ProgressMessage(String type, Object data, String timestamp) {}
    public record CompleteMessage(String type, Object result, String timestamp) {}
    public record ErrorMessage(String type, String error) {}
    public record PongMessage(String type, String timestamp) {}
    public record SubscribedMessage(String type, String channel) {}
    public record UnsubscribedMessage(String type, String channel) {}
}
