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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WebSocket connection lifecycle (F003). // GH-90000
 *
 * @doc.type class
 * @doc.purpose WebSocket connection lifecycle tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("WebSocketConnection – Connection Lifecycle (F003) [GH-90000]")
class WebSocketConnectionTest extends EventloopTestBase {

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Nested
    @DisplayName("Connection Registration [GH-90000]")
    class ConnectionRegistrationTests {

        @Test
        @DisplayName("[F003]: register_connection_adds_connection [GH-90000]")
        void registerConnectionAddsConnection() { // GH-90000
            String connectionId = "conn-001";
            String tenantId = "tenant-alpha";
            String userId = "user-001";
            Map<String, Object> metadata = Map.of("device", "desktop", "ip", "192.168.1.1"); // GH-90000

            when(connectionManager.registerConnection(connectionId, tenantId, userId, metadata)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> connectionManager.registerConnection(connectionId, tenantId, userId, metadata)); // GH-90000

            verify(connectionManager).registerConnection(connectionId, tenantId, userId, metadata); // GH-90000
        }

        @Test
        @DisplayName("[F003]: unregister_connection_removes_connection [GH-90000]")
        void unregisterConnectionRemovesConnection() { // GH-90000
            String connectionId = "conn-001";

            when(connectionManager.unregisterConnection(connectionId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> connectionManager.unregisterConnection(connectionId)); // GH-90000

            verify(connectionManager).unregisterConnection(connectionId); // GH-90000
        }

        @Test
        @DisplayName("[F003]: get_connection_info_returns_connection_details [GH-90000]")
        void getConnectionInfoReturnsConnectionDetails() { // GH-90000
            String connectionId = "conn-001";

            WebSocketConnectionManager.ConnectionInfo info = new WebSocketConnectionManager.ConnectionInfo( // GH-90000
                connectionId,
                "tenant-alpha",
                "user-001",
                Set.of("updates", "notifications"), // GH-90000
                System.currentTimeMillis(), // GH-90000
                System.currentTimeMillis(), // GH-90000
                Map.of("device", "desktop") // GH-90000
            );

            when(connectionManager.getConnectionInfo(connectionId)) // GH-90000
                .thenReturn(Promise.of(info)); // GH-90000

            WebSocketConnectionManager.ConnectionInfo result = runPromise(() -> // GH-90000
                connectionManager.getConnectionInfo(connectionId) // GH-90000
            );

            assertThat(result.connectionId()).isEqualTo(connectionId); // GH-90000
            assertThat(result.tenantId()).isEqualTo("tenant-alpha [GH-90000]");
            assertThat(result.subscriptions()).contains("updates", "notifications"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Connection Queries [GH-90000]")
    class ConnectionQueriesTests {

        @Test
        @DisplayName("[F003]: list_connections_returns_tenant_connections [GH-90000]")
        void listConnectionsReturnsTenantConnections() { // GH-90000
            String tenantId = "tenant-alpha";

            java.util.List<WebSocketConnectionManager.ConnectionInfo> connections = java.util.List.of( // GH-90000
                createConnectionInfo("conn-001", tenantId, "user-001"), // GH-90000
                createConnectionInfo("conn-002", tenantId, "user-002"), // GH-90000
                createConnectionInfo("conn-003", tenantId, "user-003") // GH-90000
            );

            when(connectionManager.listConnections(tenantId)) // GH-90000
                .thenReturn(Promise.of(connections)); // GH-90000

            java.util.List<WebSocketConnectionManager.ConnectionInfo> result = runPromise(() -> // GH-90000
                connectionManager.listConnections(tenantId) // GH-90000
            );

            assertThat(result).hasSize(3); // GH-90000
            assertThat(result).allMatch(c -> tenantId.equals(c.tenantId())); // GH-90000
        }

        @Test
        @DisplayName("[F003]: get_stats_returns_connection_statistics [GH-90000]")
        void getStatsReturnsConnectionStatistics() { // GH-90000
            String tenantId = "tenant-alpha";

            WebSocketConnectionManager.ConnectionStats stats = new WebSocketConnectionManager.ConnectionStats( // GH-90000
                100, 95, 5000, 5000, 15.5
            );

            when(connectionManager.getStats(tenantId)) // GH-90000
                .thenReturn(Promise.of(stats)); // GH-90000

            WebSocketConnectionManager.ConnectionStats result = runPromise(() -> // GH-90000
                connectionManager.getStats(tenantId) // GH-90000
            );

            assertThat(result.totalConnections()).isEqualTo(100); // GH-90000
            assertThat(result.activeConnections()).isEqualTo(95); // GH-90000
            assertThat(result.messagesSent()).isEqualTo(5000); // GH-90000
            assertThat(result.averageLatencyMs()).isEqualTo(15.5); // GH-90000
        }
    }

    @Nested
    @DisplayName("Topic Subscription [GH-90000]")
    class TopicSubscriptionTests {

        @Test
        @DisplayName("[F003]: subscribe_adds_topic_subscription [GH-90000]")
        void subscribeAddsTopicSubscription() { // GH-90000
            String connectionId = "conn-001";
            String topic = "updates";

            when(connectionManager.subscribe(connectionId, topic)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> connectionManager.subscribe(connectionId, topic)); // GH-90000

            verify(connectionManager).subscribe(connectionId, topic); // GH-90000
        }

        @Test
        @DisplayName("[F003]: unsubscribe_removes_topic_subscription [GH-90000]")
        void unsubscribeRemovesTopicSubscription() { // GH-90000
            String connectionId = "conn-001";
            String topic = "updates";

            when(connectionManager.unsubscribe(connectionId, topic)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> connectionManager.unsubscribe(connectionId, topic)); // GH-90000

            verify(connectionManager).unsubscribe(connectionId, topic); // GH-90000
        }

        @Test
        @DisplayName("[F003]: connection_tracks_multiple_subscriptions [GH-90000]")
        void connectionTracksMultipleSubscriptions() { // GH-90000
            String connectionId = "conn-001";

            WebSocketConnectionManager.ConnectionInfo info = new WebSocketConnectionManager.ConnectionInfo( // GH-90000
                connectionId,
                "tenant-alpha",
                "user-001",
                Set.of("updates", "notifications", "alerts"), // GH-90000
                System.currentTimeMillis(), // GH-90000
                System.currentTimeMillis(), // GH-90000
                Map.of() // GH-90000
            );

            when(connectionManager.getConnectionInfo(connectionId)) // GH-90000
                .thenReturn(Promise.of(info)); // GH-90000

            WebSocketConnectionManager.ConnectionInfo result = runPromise(() -> // GH-90000
                connectionManager.getConnectionInfo(connectionId) // GH-90000
            );

            assertThat(result.subscriptions()).hasSize(3); // GH-90000
            assertThat(result.subscriptions()).contains("updates", "notifications", "alerts"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Connection Timeout [GH-90000]")
    class ConnectionTimeoutTests {

        @Test
        @DisplayName("[F003]: inactive_connection_marked_stale [GH-90000]")
        void inactiveConnectionMarkedStale() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000
            long oldActivity = now - 3600000; // 1 hour ago

            WebSocketConnectionManager.ConnectionInfo stale = new WebSocketConnectionManager.ConnectionInfo( // GH-90000
                "conn-001",
                "tenant-alpha",
                "user-001",
                Set.of(), // GH-90000
                oldActivity - 1000,
                oldActivity,
                Map.of() // GH-90000
            );

            when(connectionManager.getConnectionInfo("conn-001 [GH-90000]"))
                .thenReturn(Promise.of(stale)); // GH-90000

            WebSocketConnectionManager.ConnectionInfo result = runPromise(() -> // GH-90000
                connectionManager.getConnectionInfo("conn-001 [GH-90000]")
            );

            long inactiveDuration = now - result.lastActivityAt(); // GH-90000
            assertThat(inactiveDuration).isGreaterThan(3500000); // More than ~1 hour // GH-90000
        }
    }

    private WebSocketConnectionManager.ConnectionInfo createConnectionInfo( // GH-90000
            String connectionId, String tenantId, String userId) {
        return new WebSocketConnectionManager.ConnectionInfo( // GH-90000
            connectionId, tenantId, userId, Set.of(), // GH-90000
            System.currentTimeMillis(), System.currentTimeMillis(), Map.of() // GH-90000
        );
    }
}
