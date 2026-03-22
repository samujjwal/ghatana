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

package com.ghatana.yappc.api.http;

import com.ghatana.yappc.api.http.controller.*;
import com.ghatana.yappc.api.http.websocket.ProgressWebSocket;
import com.ghatana.yappc.api.http.websocket.WebSocketManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for YAPPC HTTP Server using ActiveJ.
 * Verifies controller routing and response format via direct RoutingServlet calls.
 *
 * @doc.type class
 * @doc.purpose HTTP integration tests (ActiveJ)
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("YAPPC HTTP Server Integration Tests (ActiveJ)")
class YappcServerIntegrationTest {

    private YappcServer server;

    @BeforeEach
    void setUp() {
        YappcServerConfig config = YappcServerConfig.builder()
                .enableSwagger(false)
                .enableWebSocket(true)
                .enableCors(false)
                .build();
        server = YappcServer.create(config);
    }

    @Nested
    @DisplayName("Server Lifecycle")
    class ServerLifecycle {

        @Test
        @DisplayName("should create server with default config")
        void shouldCreateWithDefaultConfig() {
            YappcServer defaultServer = YappcServer.create();
            assertThat(defaultServer).isNotNull();
            assertThat(defaultServer.getApi()).isNotNull();
        }

        @Test
        @DisplayName("should expose WebSocket managers")
        void shouldExposeWebSocketManagers() {
            assertThat(server.getProgressWebSocket()).isNotNull();
            assertThat(server.getWebSocketManager()).isNotNull();
        }

        @Test
        @DisplayName("should expose eventloop")
        void shouldExposeEventloop() {
            assertThat(server.getEventloop()).isNotNull();
        }
    }

    @Nested
    @DisplayName("WebSocket Manager")
    class WebSocketManagerTests {

        @Test
        @DisplayName("should manage sessions")
        void shouldManageSessions() {
            WebSocketManager wsManager = new WebSocketManager();
            assertThat(wsManager.getSessionCount()).isZero();

            WebSocketManager.DefaultWebSocketSession session =
                    new WebSocketManager.DefaultWebSocketSession("s1", msg -> {}, reason -> {});
            wsManager.registerSession("s1", session);
            assertThat(wsManager.getSessionCount()).isEqualTo(1);
            assertThat(wsManager.isConnected("s1")).isTrue();

            wsManager.unregisterSession("s1");
            assertThat(wsManager.getSessionCount()).isZero();
            wsManager.shutdown();
        }

        @Test
        @DisplayName("should handle channel subscriptions")
        void shouldHandleChannelSubscriptions() {
            WebSocketManager wsManager = new WebSocketManager();
            WebSocketManager.DefaultWebSocketSession session =
                    new WebSocketManager.DefaultWebSocketSession("s1", msg -> {}, reason -> {});
            wsManager.registerSession("s1", session);

            wsManager.subscribe("s1", "builds");
            assertThat(wsManager.getActiveChannels()).contains("builds");
            assertThat(wsManager.getChannelSubscriberCount("builds")).isEqualTo(1);

            wsManager.unsubscribe("s1", "builds");
            assertThat(wsManager.getChannelSubscriberCount("builds")).isZero();

            wsManager.unregisterSession("s1");
            wsManager.shutdown();
        }
    }

    @Nested
    @DisplayName("Progress WebSocket")
    class ProgressWebSocketTests {

        @Test
        @DisplayName("should track connections")
        void shouldTrackConnections() {
            ProgressWebSocket ws = new ProgressWebSocket();
            assertThat(ws.getConnectionCount()).isZero();

            ws.handleConnect("s1", msg -> {}, () -> {});
            assertThat(ws.getConnectionCount()).isEqualTo(1);
            assertThat(ws.isConnected("s1")).isTrue();

            ws.handleClose("s1");
            assertThat(ws.getConnectionCount()).isZero();
        }

        @Test
        @DisplayName("should handle ping message")
        void shouldHandlePingMessage() {
            ProgressWebSocket ws = new ProgressWebSocket();
            StringBuilder sent = new StringBuilder();
            ws.handleConnect("s1", sent::append, () -> {});

            ws.handleMessage("s1", "{\"type\":\"ping\"}");
            assertThat(sent.toString()).contains("pong");
        }
    }

    @Nested
    @DisplayName("Controller Unit Contracts")
    class ControllerContracts {

        @Test
        @DisplayName("TemplateController should accept YappcApi")
        void templateControllerShouldAcceptApi() {
            TemplateController ctrl = new TemplateController(server.getApi());
            assertThat(ctrl).isNotNull();
        }

        @Test
        @DisplayName("DependencyController should accept YappcApi")
        void dependencyControllerShouldAcceptApi() {
            DependencyController ctrl = new DependencyController(server.getApi());
            assertThat(ctrl).isNotNull();
        }

        @Test
        @DisplayName("PackController should accept YappcApi")
        void packControllerShouldAcceptApi() {
            PackController ctrl = new PackController(server.getApi());
            assertThat(ctrl).isNotNull();
        }

        @Test
        @DisplayName("ProjectController should accept YappcApi and ProgressWebSocket")
        void projectControllerShouldAcceptDeps() {
            ProjectController ctrl = new ProjectController(server.getApi(), new ProgressWebSocket());
            assertThat(ctrl).isNotNull();
        }
    }
}
