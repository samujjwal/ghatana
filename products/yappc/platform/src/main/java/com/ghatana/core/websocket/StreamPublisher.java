package com.ghatana.core.websocket;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes events to WebSocket connections on specific topics.
 *
 * <p><b>Purpose</b><br>
 * Provides a high-level API for publishing real-time events to WebSocket clients.
 * Handles message formatting, batching, and delivery with configurable options.
 *
 * <p><b>Architecture Role</b><br>
 * Event publisher in libs/java/activej-websocket for the Hybrid Backend.
 * Used by:
 * - EventCloud - Publish events to subscribers
 * - AEP - Publish pipeline/pattern state changes
 * - Brain - Publish AI advisory notifications
 * - Monitoring - Publish metrics and alerts
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StreamPublisher publisher = StreamPublisher.builder()
 *     .connectionManager(manager)
 *     .heartbeatInterval(Duration.ofSeconds(30))
 *     .build();
 *
 * // Publish single event
 * publisher.publish("events/orders", orderEvent);
 *
 * // Publish to specific tenant
 * publisher.publishToTenant("events/orders", "tenant-123", orderEvent);
 *
 * // Start heartbeat
 * publisher.startHeartbeat();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose WebSocket event publisher with batching and heartbeat
 * @doc.layer platform
 * @doc.pattern Service
 */
@Slf4j
public class StreamPublisher {

    /**
     * Connection manager for accessing WebSocket connections
     */
    @Getter
    private final WebSocketConnectionManager connectionManager;

    /**
     * Heartbeat interval
     */
    private final Duration heartbeatInterval;

    /**
     * Scheduler for periodic tasks
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "websocket-publisher");
        t.setDaemon(true);
        return t;
    });

    /**
     * Heartbeat task handle
     */
    private ScheduledFuture<?> heartbeatTask;

    @Builder
    public StreamPublisher(
            WebSocketConnectionManager connectionManager,
            Duration heartbeatInterval
    ) {
        this.connectionManager = connectionManager;
        this.heartbeatInterval = heartbeatInterval != null ? heartbeatInterval : Duration.ofSeconds(30);
    }

    /**
     * Publish an event to all subscribers of a topic
     *
     * @param topic   Topic name
     * @param payload Event payload
     * @return Promise that completes when broadcast is initiated
     */
    public <T> Promise<Void> publish(String topic, T payload) {
        WebSocketMessage<T> message = WebSocketMessage.data(topic, payload);
        return connectionManager.broadcast(topic, message.toJson());
    }

    /**
     * Publish an event to subscribers of a topic for a specific tenant
     *
     * @param topic    Topic name
     * @param tenantId Tenant ID
     * @param payload  Event payload
     * @return Promise that completes when broadcast is initiated
     */
    public <T> Promise<Void> publishToTenant(String topic, String tenantId, T payload) {
        WebSocketMessage<T> message = WebSocketMessage.data(topic, payload);
        return connectionManager.broadcastToTenant(topic, tenantId, message.toJson());
    }

    /**
     * Publish a raw message to all subscribers of a topic
     *
     * @param topic   Topic name
     * @param message Pre-formatted message
     * @return Promise that completes when broadcast is initiated
     */
    public Promise<Void> publishRaw(String topic, String message) {
        return connectionManager.broadcast(topic, message);
    }

    /**
     * Publish binary data to all subscribers of a topic
     *
     * @param topic Topic name
     * @param data  Binary data
     * @return Promise that completes when broadcast is initiated
     */
    public Promise<Void> publishBinary(String topic, byte[] data) {
        return connectionManager.broadcast(topic, data);
    }

    /**
     * Send an error message to all subscribers of a topic
     *
     * @param topic   Topic name
     * @param code    Error code
     * @param message Error message
     * @return Promise that completes when broadcast is initiated
     */
    public Promise<Void> publishError(String topic, String code, String message) {
        WebSocketMessage<Void> errorMessage = WebSocketMessage.error(topic, code, message);
        return connectionManager.broadcast(topic, errorMessage.toJson());
    }

    /**
     * Signal completion of a stream to all subscribers
     *
     * @param topic Topic name
     * @return Promise that completes when broadcast is initiated
     */
    public Promise<Void> publishComplete(String topic) {
        WebSocketMessage<Void> completeMessage = WebSocketMessage.complete(topic);
        return connectionManager.broadcast(topic, completeMessage.toJson());
    }

    /**
     * Start periodic heartbeat to all connections
     */
    public void startHeartbeat() {
        if (heartbeatTask != null) {
            return;
        }

        log.info("Starting WebSocket heartbeat (interval: {}s)", heartbeatInterval.getSeconds());

        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeatToAll();
            } catch (Exception e) {
                log.error("Heartbeat error: {}", e.getMessage());
            }
        }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Stop heartbeat
     */
    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
            log.info("Stopped WebSocket heartbeat");
        }
    }

    /**
     * Send heartbeat to all connections
     */
    private void sendHeartbeatToAll() {
        WebSocketMessage<Void> heartbeat = WebSocketMessage.heartbeat();
        String message = heartbeat.toJson();

        for (String topic : connectionManager.getActiveTopics()) {
            connectionManager.broadcast(topic, message)
                    .whenException(e -> log.warn("Heartbeat failed for topic {}: {}", topic, e.getMessage()));
        }
    }

    /**
     * Get statistics
     *
     * @return Publisher statistics
     */
    public PublisherStats getStats() {
        return PublisherStats.builder()
                .totalConnections(connectionManager.getConnectionCount())
                .activeTopics(connectionManager.getActiveTopics().size())
                .heartbeatActive(heartbeatTask != null)
                .build();
    }

    /**
     * Shutdown the publisher
     */
    public void shutdown() {
        stopHeartbeat();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("StreamPublisher shutdown complete");
    }

    /**
     * Publisher statistics
     */
    @lombok.Data
    @Builder
    public static class PublisherStats {
        private int totalConnections;
        private int activeTopics;
        private boolean heartbeatActive;
    }
}
