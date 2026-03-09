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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced WebSocket manager with channels, heartbeat, and reconnection support.
 * Framework-independent — uses {@link WebSocketSession} abstraction for transport.
 *
 * @doc.type class
 * @doc.purpose Advanced WebSocket management
 * @doc.layer platform
 * @doc.pattern Manager
 */
public final class WebSocketManager {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketManager.class);

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CLIENT_TIMEOUT = Duration.ofSeconds(60);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> channelSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionChannels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Duration heartbeatInterval;
    private final Duration clientTimeout;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public WebSocketManager() {
        this(DEFAULT_HEARTBEAT_INTERVAL, DEFAULT_CLIENT_TIMEOUT);
    }

    public WebSocketManager(Duration heartbeatInterval, Duration clientTimeout) {
        this.heartbeatInterval = heartbeatInterval;
        this.clientTimeout = clientTimeout;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-manager");
            t.setDaemon(true);
            return t;
        });
        startHeartbeat();
    }

    // ── Framework-independent message handlers ──────────────────────────────

    /**
     * Handle a new WebSocket connection.
     *
     * @param sessionId unique session identifier
     * @param session   transport-agnostic session handle
     */
    public void handleConnect(String sessionId, WebSocketSession session) {
        registerSession(sessionId, session);
        sendToSession(sessionId, Map.of(
                "type", "connected",
                "sessionId", sessionId,
                "channels", getActiveChannels(),
                "timestamp", Instant.now().toString()));
        LOG.info("WebSocket connected: {}", sessionId);
    }

    /**
     * Handle an incoming text message.
     *
     * @param sessionId sender session
     * @param rawJson   the raw JSON message string
     */
    @SuppressWarnings("unchecked")
    public void handleMessage(String sessionId, String rawJson) {
        updateActivity(sessionId);
        try {
            Map<String, Object> message = objectMapper.readValue(rawJson, Map.class);
            String type = (String) message.get("type");
            if (type == null) {
                LOG.warn("Message without type from session {}", sessionId);
                return;
            }
            switch (type.toLowerCase()) {
                case "ping" -> sendToSession(sessionId,
                        Map.of("type", "pong", "timestamp", Instant.now().toString()));
                case "pong" -> handlePong(sessionId);
                case "subscribe" -> {
                    String channel = (String) message.get("channel");
                    if (channel != null) {
                        subscribe(sessionId, channel);
                        sendToSession(sessionId, Map.of("type", "subscribed",
                                "channel", channel, "timestamp", Instant.now().toString()));
                    }
                }
                case "unsubscribe" -> {
                    String channel = (String) message.get("channel");
                    if (channel != null) {
                        unsubscribe(sessionId, channel);
                        sendToSession(sessionId, Map.of("type", "unsubscribed",
                                "channel", channel, "timestamp", Instant.now().toString()));
                    }
                }
                default -> LOG.debug("Unknown message type '{}' from session {}", type, sessionId);
            }
        } catch (Exception e) {
            LOG.error("Error processing message from session {}", sessionId, e);
        }
    }

    /**
     * Handle a WebSocket close event.
     */
    public void handleClose(String sessionId, int statusCode, String reason) {
        unregisterSession(sessionId);
        LOG.info("WebSocket closed: {} (status: {}, reason: {})", sessionId, statusCode, reason);
    }

    /**
     * Handle a WebSocket error.
     */
    public void handleError(String sessionId, Throwable error) {
        LOG.error("WebSocket error for session {}", sessionId, error);
    }

    // ── Session management ──────────────────────────────────────────────────

    public void registerSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        sessionChannels.put(sessionId, ConcurrentHashMap.newKeySet());
        LOG.info("Session registered: {}", sessionId);
    }

    public void unregisterSession(String sessionId) {
        sessions.remove(sessionId);
        Set<String> channels = sessionChannels.remove(sessionId);
        if (channels != null) {
            for (String channel : channels) {
                Set<String> subscribers = channelSubscriptions.get(channel);
                if (subscribers != null) {
                    subscribers.remove(sessionId);
                    if (subscribers.isEmpty()) channelSubscriptions.remove(channel);
                }
            }
        }
        LOG.info("Session unregistered: {}", sessionId);
    }

    public void subscribe(String sessionId, String channel) {
        channelSubscriptions.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionChannels.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(channel);
    }

    public void unsubscribe(String sessionId, String channel) {
        Set<String> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            subscribers.remove(sessionId);
            if (subscribers.isEmpty()) channelSubscriptions.remove(channel);
        }
        Set<String> channels = sessionChannels.get(sessionId);
        if (channels != null) channels.remove(channel);
    }

    public void broadcastToChannel(String channel, Object message) {
        Set<String> subscribers = channelSubscriptions.get(channel);
        if (subscribers != null) {
            for (String sid : subscribers) sendToSession(sid, message);
        }
    }

    public void sendToSession(String sessionId, Object message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) session.send(message);
    }

    public void broadcastToAll(Object message) {
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) session.send(message);
        }
    }

    public void updateActivity(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) session.updateLastActivity();
    }

    public void handlePong(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) session.handlePong();
    }

    public int getSessionCount() { return sessions.size(); }

    public int getChannelSubscriberCount(String channel) {
        Set<String> subs = channelSubscriptions.get(channel);
        return subs != null ? subs.size() : 0;
    }

    public Set<String> getActiveChannels() { return Set.copyOf(channelSubscriptions.keySet()); }

    public boolean isConnected(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    public void shutdown() {
        running.set(false);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("WebSocket manager shut down");
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::performHeartbeat,
                heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void performHeartbeat() {
        if (!running.get()) return;
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            if (session.isTimedOut(clientTimeout)) {
                LOG.warn("Session {} timed out, closing", sessionId);
                session.close("Timeout");
                unregisterSession(sessionId);
                continue;
            }
            if (session.isOpen()) session.sendPing();
        }
    }

    // ── WebSocket session abstraction ───────────────────────────────────────

    /**
     * Transport-agnostic WebSocket session interface.
     */
    public interface WebSocketSession {
        boolean isOpen();
        void send(Object message);
        void sendPing();
        void close(String reason);
        void updateLastActivity();
        void handlePong();
        boolean isTimedOut(Duration timeout);
    }

    /**
     * Default in-process session implementation.
     */
    public static class DefaultWebSocketSession implements WebSocketSession {
        private final String sessionId;
        private final java.util.function.Consumer<Object> sendFn;
        private final java.util.function.Consumer<String> closeFn;
        private volatile Instant lastActivity = Instant.now();
        private volatile Instant lastPong = Instant.now();
        private volatile boolean open = true;

        public DefaultWebSocketSession(String sessionId,
                                        java.util.function.Consumer<Object> sendFn,
                                        java.util.function.Consumer<String> closeFn) {
            this.sessionId = sessionId;
            this.sendFn = sendFn;
            this.closeFn = closeFn;
        }

        @Override public boolean isOpen() { return open; }
        @Override public void send(Object message) { if (open) sendFn.accept(message); }
        @Override public void sendPing() {
            if (open) sendFn.accept(Map.of("type", "ping", "timestamp", Instant.now().toString()));
        }
        @Override public void close(String reason) { open = false; closeFn.accept(reason); }
        @Override public void updateLastActivity() { lastActivity = Instant.now(); }
        @Override public void handlePong() { lastPong = Instant.now(); }
        @Override public boolean isTimedOut(Duration timeout) {
            return Duration.between(lastActivity, Instant.now()).compareTo(timeout) > 0;
        }
    }
}
