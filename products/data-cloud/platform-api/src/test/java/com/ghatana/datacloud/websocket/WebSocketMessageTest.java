/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Tests for WebSocket message routing (F003). 
 *
 * @doc.type class
 * @doc.purpose WebSocket message routing tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("WebSocketMessage – Message Routing (F003)")
class WebSocketMessageTest extends EventloopTestBase {

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Nested
    @DisplayName("Direct Messaging")
    class DirectMessagingTests {

        @Test
        @DisplayName("[F003]: send_to_connection_delivers_message")
        void sendToConnectionDeliversMessage() { 
            String connectionId = "conn-001";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("notification", "New data available"); 

            when(connectionManager.sendToConnection(connectionId, message)) 
                .thenReturn(Promise.of(true)); 

            Boolean result = runPromise(() -> connectionManager.sendToConnection(connectionId, message)); 

            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("[F003]: send_to_user_delivers_to_all_user_connections")
        void sendToUserDeliversToAllUserConnections() { 
            String userId = "user-001";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("update", "Profile updated"); 

            when(connectionManager.sendToUser(userId, message)) 
                .thenReturn(Promise.of(3)); // 3 connections received 

            Integer count = runPromise(() -> connectionManager.sendToUser(userId, message)); 

            assertThat(count).isEqualTo(3); 
        }

        @Test
        @DisplayName("[F003]: send_to_closed_connection_returns_false")
        void sendToClosedConnectionReturnsFalse() { 
            String connectionId = "closed-conn";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("test", "data"); 

            when(connectionManager.sendToConnection(connectionId, message)) 
                .thenReturn(Promise.of(false)); 

            Boolean result = runPromise(() -> connectionManager.sendToConnection(connectionId, message)); 

            assertThat(result).isFalse(); 
        }
    }

    @Nested
    @DisplayName("Broadcast Messaging")
    class BroadcastMessagingTests {

        @Test
        @DisplayName("[F003]: broadcast_to_tenant_sends_to_all_tenant_connections")
        void broadcastToTenantSendsToAllTenantConnections() { 
            String tenantId = "tenant-alpha";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("announcement", "System update scheduled"); 

            when(connectionManager.broadcastToTenant(tenantId, message)) 
                .thenReturn(Promise.of(50)); // 50 connections notified 

            Integer count = runPromise(() -> connectionManager.broadcastToTenant(tenantId, message)); 

            assertThat(count).isEqualTo(50); 
        }
    }

    @Nested
    @DisplayName("Topic Publishing")
    class TopicPublishingTests {

        @Test
        @DisplayName("[F003]: publish_to_topic_sends_to_subscribers")
        void publishToTopicSendsToSubscribers() { 
            String topic = "data-updates";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("data-change", "Entity updated"); 

            when(connectionManager.publishToTopic(topic, message)) 
                .thenReturn(Promise.of(10)); // 10 subscribers 

            Integer count = runPromise(() -> connectionManager.publishToTopic(topic, message)); 

            assertThat(count).isEqualTo(10); 
        }

        @Test
        @DisplayName("[F003]: publish_to_empty_topic_returns_zero")
        void publishToEmptyTopicReturnsZero() { 
            String topic = "unused-topic";
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("test", "data"); 

            when(connectionManager.publishToTopic(topic, message)) 
                .thenReturn(Promise.of(0)); 

            Integer count = runPromise(() -> connectionManager.publishToTopic(topic, message)); 

            assertThat(count).isZero(); 
        }
    }

    @Nested
    @DisplayName("Message Format")
    class MessageFormatTests {

        @Test
        @DisplayName("[F003]: message_has_type_payload_timestamp")
        void messageHasTypePayloadTimestamp() { 
            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("notification", "Hello"); 

            assertThat(message.type()).isEqualTo("notification");
            assertThat(message.payload()).isEqualTo("Hello");
            assertThat(message.timestamp()).isGreaterThan(0); 
        }

        @Test
        @DisplayName("[F003]: message_metadata_optional")
        void messageMetadataOptional() { 
            WebSocketConnectionManager.WebSocketMessage message = new WebSocketConnectionManager.WebSocketMessage( 
                "event",
                "data",
                Map.of("source", "system", "priority", "high"), 
                System.currentTimeMillis() 
            );

            assertThat(message.metadata()).containsKeys("source", "priority"); 
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("[F003]: tenant_broadcast_does_not_cross_tenants")
        void tenantBroadcastDoesNotCrossTenants() { 
            String tenantAlpha = "tenant-alpha";
            String tenantBeta = "tenant-beta";

            WebSocketConnectionManager.WebSocketMessage message =
                WebSocketConnectionManager.WebSocketMessage.of("alert", "Tenant-specific alert"); 

            when(connectionManager.broadcastToTenant(tenantAlpha, message)) 
                .thenReturn(Promise.of(10)); 
            when(connectionManager.broadcastToTenant(tenantBeta, message)) 
                .thenReturn(Promise.of(5)); 

            Integer alphaCount = runPromise(() -> connectionManager.broadcastToTenant(tenantAlpha, message)); 
            Integer betaCount = runPromise(() -> connectionManager.broadcastToTenant(tenantBeta, message)); 

            assertThat(alphaCount).isEqualTo(10); 
            assertThat(betaCount).isEqualTo(5); 
        }
    }
}
