/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.websocket;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manager for WebSocket connections.
 *
 * <p>Handles connection lifecycle, message routing, and broadcast.
 *
 * @doc.type interface
 * @doc.purpose WebSocket connection management
 * @doc.layer product
 * @doc.pattern Connection Manager
 */
public interface WebSocketConnectionManager {

    /**
     * Register new connection.
     *
     * @param connectionId unique connection ID
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @param metadata connection metadata
     * @return promise completing when registered
     */
    Promise<Void> registerConnection(String connectionId, String tenantId, String userId, Map<String, Object> metadata);

    /**
     * Unregister connection.
     *
     * @param connectionId connection ID
     * @return promise completing when unregistered
     */
    Promise<Void> unregisterConnection(String connectionId);

    /**
     * Send message to specific connection.
     *
     * @param connectionId target connection
     * @param message message to send
     * @return promise of true if sent
     */
    Promise<Boolean> sendToConnection(String connectionId, WebSocketMessage message);

    /**
     * Broadcast message to tenant.
     *
     * @param tenantId tenant identifier
     * @param message message to broadcast
     * @return promise of recipient count
     */
    Promise<Integer> broadcastToTenant(String tenantId, WebSocketMessage message);

    /**
     * Send message to user across all connections.
     *
     * @param userId user identifier
     * @param message message to send
     * @return promise of recipient count
     */
    Promise<Integer> sendToUser(String userId, WebSocketMessage message);

    /**
     * Subscribe connection to topic.
     *
     * @param connectionId connection ID
     * @param topic topic name
     * @return promise completing when subscribed
     */
    Promise<Void> subscribe(String connectionId, String topic);

    /**
     * Unsubscribe connection from topic.
     *
     * @param connectionId connection ID
     * @param topic topic name
     * @return promise completing when unsubscribed
     */
    Promise<Void> unsubscribe(String connectionId, String topic);

    /**
     * Publish message to topic.
     *
     * @param topic topic name
     * @param message message to publish
     * @return promise of recipient count
     */
    Promise<Integer> publishToTopic(String topic, WebSocketMessage message);

    /**
     * Get connection info.
     *
     * @param connectionId connection ID
     * @return promise of connection info
     */
    Promise<ConnectionInfo> getConnectionInfo(String connectionId);

    /**
     * List all connections for tenant.
     *
     * @param tenantId tenant identifier
     * @return promise of connection list
     */
    Promise<List<ConnectionInfo>> listConnections(String tenantId);

    /**
     * Get connection statistics.
     *
     * @param tenantId tenant identifier
     * @return promise of statistics
     */
    Promise<ConnectionStats> getStats(String tenantId);

    /**
     * WebSocket message.
     */
    record WebSocketMessage(
        String type,
        String payload,
        Map<String, Object> metadata,
        long timestamp
    ) {
        public static WebSocketMessage of(String type, String payload) {
            return new WebSocketMessage(type, payload, Map.of(), System.currentTimeMillis());
        }
    }

    /**
     * Connection information.
     */
    record ConnectionInfo(
        String connectionId,
        String tenantId,
        String userId,
        Set<String> subscriptions,
        long connectedAt,
        long lastActivityAt,
        Map<String, Object> metadata
    ) {}

    /**
     * Connection statistics.
     */
    record ConnectionStats(
        int totalConnections,
        int activeConnections,
        long messagesSent,
        long messagesReceived,
        double averageLatencyMs
    ) {}
}
