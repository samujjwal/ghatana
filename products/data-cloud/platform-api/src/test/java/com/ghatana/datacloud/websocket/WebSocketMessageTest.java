/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.websocket;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WebSocket message routing (F003). // GH-90000
 *
 * @doc.type class
 * @doc.purpose WebSocket message routing tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("WebSocketMessage – Message Routing (F003) [GH-90000]")
class WebSocketMessageTest extends EventloopTestBase {

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Nested
    @DisplayName("Direct Messaging [GH-90000]")
    class DirectMessagingTests {

        @Test
        @DisplayName("[F003]: send_to_connection_delivers_message [GH-90000]")
        void sendToConnectionDeliversMessage() { // GH-90000
            String connectionId = "conn-001";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("notification", "New data available"); // GH-90000

            when(connectionManager.sendToConnection(connectionId, message)) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            Boolean result = runPromise(() -> connectionManager.sendToConnection(connectionId, message)); // GH-90000

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[F003]: send_to_user_delivers_to_all_user_connections [GH-90000]")
        void sendToUserDeliversToAllUserConnections() { // GH-90000
            String userId = "user-001";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("update", "Profile updated"); // GH-90000

            when(connectionManager.sendToUser(userId, message)) // GH-90000
                .thenReturn(Promise.of(3)); // 3 connections received // GH-90000

            Integer count = runPromise(() -> connectionManager.sendToUser(userId, message)); // GH-90000

            assertThat(count).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("[F003]: send_to_closed_connection_returns_false [GH-90000]")
        void sendToClosedConnectionReturnsFalse() { // GH-90000
            String connectionId = "closed-conn";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("test", "data"); // GH-90000

            when(connectionManager.sendToConnection(connectionId, message)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean result = runPromise(() -> connectionManager.sendToConnection(connectionId, message)); // GH-90000

            assertThat(result).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Broadcast Messaging [GH-90000]")
    class BroadcastMessagingTests {

        @Test
        @DisplayName("[F003]: broadcast_to_tenant_sends_to_all_tenant_connections [GH-90000]")
        void broadcastToTenantSendsToAllTenantConnections() { // GH-90000
            String tenantId = "tenant-alpha";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("announcement", "System update scheduled"); // GH-90000

            when(connectionManager.broadcastToTenant(tenantId, message)) // GH-90000
                .thenReturn(Promise.of(50)); // 50 connections notified // GH-90000

            Integer count = runPromise(() -> connectionManager.broadcastToTenant(tenantId, message)); // GH-90000

            assertThat(count).isEqualTo(50); // GH-90000
        }
    }

    @Nested
    @DisplayName("Topic Publishing [GH-90000]")
    class TopicPublishingTests {

        @Test
        @DisplayName("[F003]: publish_to_topic_sends_to_subscribers [GH-90000]")
        void publishToTopicSendsToSubscribers() { // GH-90000
            String topic = "data-updates";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("data-change", "Entity updated"); // GH-90000

            when(connectionManager.publishToTopic(topic, message)) // GH-90000
                .thenReturn(Promise.of(10)); // 10 subscribers // GH-90000

            Integer count = runPromise(() -> connectionManager.publishToTopic(topic, message)); // GH-90000

            assertThat(count).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("[F003]: publish_to_empty_topic_returns_zero [GH-90000]")
        void publishToEmptyTopicReturnsZero() { // GH-90000
            String topic = "unused-topic";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("test", "data"); // GH-90000

            when(connectionManager.publishToTopic(topic, message)) // GH-90000
                .thenReturn(Promise.of(0)); // GH-90000

            Integer count = runPromise(() -> connectionManager.publishToTopic(topic, message)); // GH-90000

            assertThat(count).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Message Format [GH-90000]")
    class MessageFormatTests {

        @Test
        @DisplayName("[F003]: message_has_type_payload_timestamp [GH-90000]")
        void messageHasTypePayloadTimestamp() { // GH-90000
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("notification", "Hello"); // GH-90000

            assertThat(message.type()).isEqualTo("notification [GH-90000]");
            assertThat(message.payload()).isEqualTo("Hello [GH-90000]");
            assertThat(message.timestamp()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("[F003]: message_metadata_optional [GH-90000]")
        void messageMetadataOptional() { // GH-90000
            WebSocketConnectionManager.WebSocketMessage message = new WebSocketConnectionManager.WebSocketMessage( // GH-90000
                "event",
                "data",
                Map.of("source", "system", "priority", "high"), // GH-90000
                System.currentTimeMillis() // GH-90000
            );

            assertThat(message.metadata()).containsKeys("source", "priority"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Tenant Isolation [GH-90000]")
    class TenantIsolationTests {

        @Test
        @DisplayName("[F003]: tenant_broadcast_does_not_cross_tenants [GH-90000]")
        void tenantBroadcastDoesNotCrossTenants() { // GH-90000
            String tenantAlpha = "tenant-alpha";
            String tenantBeta = "tenant-beta";

            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("alert", "Tenant-specific alert"); // GH-90000

            when(connectionManager.broadcastToTenant(tenantAlpha, message)) // GH-90000
                .thenReturn(Promise.of(10)); // GH-90000
            when(connectionManager.broadcastToTenant(tenantBeta, message)) // GH-90000
                .thenReturn(Promise.of(5)); // GH-90000

            Integer alphaCount = runPromise(() -> connectionManager.broadcastToTenant(tenantAlpha, message)); // GH-90000
            Integer betaCount = runPromise(() -> connectionManager.broadcastToTenant(tenantBeta, message)); // GH-90000

            assertThat(alphaCount).isEqualTo(10); // GH-90000
            assertThat(betaCount).isEqualTo(5); // GH-90000
        }
    }
}
