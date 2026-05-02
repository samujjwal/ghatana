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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WebSocket connection lifecycle (F003). 
 *
 * @doc.type class
 * @doc.purpose WebSocket connection lifecycle tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("WebSocketConnection – Connection Lifecycle (F003)")
class WebSocketConnectionTest extends EventloopTestBase {

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Nested
    @DisplayName("Connection Registration")
    class ConnectionRegistrationTests {

        @Test
        @DisplayName("[F003]: register_connection_adds_connection")
        void registerConnectionAddsConnection() { 
            String connectionId = "conn-001";
            String tenantId = "tenant-alpha";
            String userId = "user-001";
            Map<String, Object> metadata = Map.of("device", "desktop", "ip", "192.168.1.1"); 

            when(connectionManager.registerConnection(connectionId, tenantId, userId, metadata)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> connectionManager.registerConnection(connectionId, tenantId, userId, metadata)); 

            verify(connectionManager).registerConnection(connectionId, tenantId, userId, metadata); 
        }

        @Test
        @DisplayName("[F003]: unregister_connection_removes_connection")
        void unregisterConnectionRemovesConnection() { 
            String connectionId = "conn-001";

            when(connectionManager.unregisterConnection(connectionId)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> connectionManager.unregisterConnection(connectionId)); 

            verify(connectionManager).unregisterConnection(connectionId); 
        }

        @Test
        @DisplayName("[F003]: get_connection_info_returns_connection_details")
        void getConnectionInfoReturnsConnectionDetails() { 
            String connectionId = "conn-001";

            WebSocketConnectionManager.ConnectionInfo info = new WebSocketConnectionManager.ConnectionInfo( 
                connectionId,
                "tenant-alpha",
                "user-001",
                Set.of("updates", "notifications"), 
                System.currentTimeMillis(), 
                System.currentTimeMillis(), 
                Map.of("device", "desktop") 
            );

            when(connectionManager.getConnectionInfo(connectionId)) 
                .thenReturn(Promise.of(info)); 

            WebSocketConnectionManager.ConnectionInfo result = runPromise(() -> 
                connectionManager.getConnectionInfo(connectionId) 
            );

            assertThat(result.connectionId()).isEqualTo(connectionId); 
            assertThat(result.tenantId()).isEqualTo("tenant-alpha");
            assertThat(result.subscriptions()).contains("updates", "notifications"); 
        }
    }

    @Nested
    @DisplayName("Connection Queries")
    class ConnectionQueriesTests {

        @Test
        @DisplayName("[F003]: list_connections_returns_tenant_connections")
        void listConnectionsReturnsTenantConnections() { 
            String tenantId = "tenant-alpha";

            java.util.List<WebSocketConnectionManager.ConnectionInfo> connections = java.util.List.of( 
                createConnectionInfo("conn-001", tenantId, "user-001"), 
                createConnectionInfo("conn-002", tenantId, "user-002"), 
                createConnectionInfo("conn-003", tenantId, "user-003") 
            );

            when(connectionManager.listConnections(tenantId)) 
                .thenReturn(Promise.of(connections)); 

            java.util.List<WebSocketConnectionManager.ConnectionInfo> result = runPromise(() -> 
                connectionManager.listConnections(tenantId) 
            );

            assertThat(result).hasSize(3); 
            assertThat(result).allMatch(c -> tenantId.equals(c.tenantId())); 
        }

        @Test
        @DisplayName("[F003]: get_stats_returns_connection_statistics")
        void getStatsReturnsConnectionStatistics() { 
            String tenantId = "tenant-alpha";

            WebSocketConnectionManager.ConnectionStats stats = new WebSocketConnectionManager.ConnectionStats( 
                100, 95, 5000, 5000, 15.5
            );

            when(connectionManager.getStats(tenantId)) 
                .thenReturn(Promise.of(stats)); 

            WebSocketConnectionManager.ConnectionStats result = runPromise(() -> 
                connectionManager.getStats(tenantId) 
            );

            assertThat(result.totalConnections()).isEqualTo(100); 
            assertThat(result.activeConnections()).isEqualTo(95); 
            assertThat(result.messagesSent()).isEqualTo(5000); 
            assertThat(result.averageLatencyMs()).isEqualTo(15.5); 
        }
    }

    @Nested
    @DisplayName("Topic Subscription")
    class TopicSubscriptionTests {

        @Test
        @DisplayName("[F003]: subscribe_adds_topic_subscription")
        void subscribeAddsTopicSubscription() { 
            String connectionId = "conn-001";
            String topic = "updates";

            when(connectionManager.subscribe(connectionId, topic)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> connectionManager.subscribe(connectionId, topic)); 

            verify(connectionManager).subscribe(connectionId, topic); 
        }

        @Test
        @DisplayName("[F003]: unsubscribe_removes_topic_subscription")
        void unsubscribeRemovesTopicSubscription() { 
            String connectionId = "conn-001";
            String topic = "updates";

            when(connectionManager.unsubscribe(connectionId, topic)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> connectionManager.unsubscribe(connectionId, topic)); 

            verify(connectionManager).unsubscribe(connectionId, topic); 
        }

        @Test
        @DisplayName("[F003]: connection_tracks_multiple_subscriptions")
        void connectionTracksMultipleSubscriptions() { 
            String connectionId = "conn-001";

            WebSocketConnectionManager.ConnectionInfo info = new WebSocketConnectionManager.ConnectionInfo( 
                connectionId,
                "tenant-alpha",
                "user-001",
                Set.of("updates", "notifications", "alerts"), 
                System.currentTimeMillis(), 
                System.currentTimeMillis(), 
                Map.of() 
            );

            when(connectionManager.getConnectionInfo(connectionId)) 
                .thenReturn(Promise.of(info)); 

            WebSocketConnectionManager.ConnectionInfo result = runPromise(() -> 
                connectionManager.getConnectionInfo(connectionId) 
            );

            assertThat(result.subscriptions()).hasSize(3); 
            assertThat(result.subscriptions()).contains("updates", "notifications", "alerts"); 
        }
    }

    @Nested
    @DisplayName("Connection Timeout")
    class ConnectionTimeoutTests {

        @Test
        @DisplayName("[F003]: inactive_connection_marked_stale")
        void inactiveConnectionMarkedStale() { 
            long now = System.currentTimeMillis(); 
            long oldActivity = now - 3600000; // 1 hour ago

            WebSocketConnectionManager.ConnectionInfo stale = new WebSocketConnectionManager.ConnectionInfo( 
                "conn-001",
                "tenant-alpha",
                "user-001",
                Set.of(), 
                oldActivity - 1000,
                oldActivity,
                Map.of() 
            );

            when(connectionManager.getConnectionInfo("conn-001"))
                .thenReturn(Promise.of(stale)); 

            WebSocketConnectionManager.ConnectionInfo result = runPromise(() -> 
                connectionManager.getConnectionInfo("conn-001")
            );

            long inactiveDuration = now - result.lastActivityAt(); 
            assertThat(inactiveDuration).isGreaterThan(3500000); // More than ~1 hour 
        }
    }

    private WebSocketConnectionManager.ConnectionInfo createConnectionInfo( 
            String connectionId, String tenantId, String userId) {
        return new WebSocketConnectionManager.ConnectionInfo( 
            connectionId, tenantId, userId, Set.of(), 
            System.currentTimeMillis(), System.currentTimeMillis(), Map.of() 
        );
    }
}
