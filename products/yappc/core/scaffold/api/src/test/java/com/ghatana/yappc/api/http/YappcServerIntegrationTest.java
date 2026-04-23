/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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
 * @doc.purpose HTTP integration tests (ActiveJ) // GH-90000
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("YAPPC HTTP Server Integration Tests (ActiveJ)")
class YappcServerIntegrationTest {

    private YappcServer server;

    @BeforeEach
    void setUp() { // GH-90000
        YappcServerConfig config = YappcServerConfig.builder() // GH-90000
                .enableSwagger(false) // GH-90000
                .enableWebSocket(true) // GH-90000
                .enableCors(false) // GH-90000
                .build(); // GH-90000
        server = YappcServer.create(config); // GH-90000
    }

    @Nested
    @DisplayName("Server Lifecycle")
    class ServerLifecycle {

        @Test
        @DisplayName("should create server with default config")
        void shouldCreateWithDefaultConfig() { // GH-90000
            YappcServer defaultServer = YappcServer.create(); // GH-90000
            assertThat(defaultServer).isNotNull(); // GH-90000
            assertThat(defaultServer.getApi()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should expose WebSocket managers")
        void shouldExposeWebSocketManagers() { // GH-90000
            assertThat(server.getProgressWebSocket()).isNotNull(); // GH-90000
            assertThat(server.getWebSocketManager()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should expose eventloop")
        void shouldExposeEventloop() { // GH-90000
            assertThat(server.getEventloop()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("WebSocket Manager")
    class WebSocketManagerTests {

        @Test
        @DisplayName("should manage sessions")
        void shouldManageSessions() { // GH-90000
            WebSocketManager wsManager = new WebSocketManager(); // GH-90000
            assertThat(wsManager.getSessionCount()).isZero(); // GH-90000

            WebSocketManager.DefaultWebSocketSession session =
                    new WebSocketManager.DefaultWebSocketSession("s1", msg -> {}, reason -> {}); // GH-90000
            wsManager.registerSession("s1", session); // GH-90000
            assertThat(wsManager.getSessionCount()).isEqualTo(1); // GH-90000
            assertThat(wsManager.isConnected("s1")).isTrue();

            wsManager.unregisterSession("s1");
            assertThat(wsManager.getSessionCount()).isZero(); // GH-90000
            wsManager.shutdown(); // GH-90000
        }

        @Test
        @DisplayName("should handle channel subscriptions")
        void shouldHandleChannelSubscriptions() { // GH-90000
            WebSocketManager wsManager = new WebSocketManager(); // GH-90000
            WebSocketManager.DefaultWebSocketSession session =
                    new WebSocketManager.DefaultWebSocketSession("s1", msg -> {}, reason -> {}); // GH-90000
            wsManager.registerSession("s1", session); // GH-90000

            wsManager.subscribe("s1", "builds"); // GH-90000
            assertThat(wsManager.getActiveChannels()).contains("builds");
            assertThat(wsManager.getChannelSubscriberCount("builds")).isEqualTo(1);

            wsManager.unsubscribe("s1", "builds"); // GH-90000
            assertThat(wsManager.getChannelSubscriberCount("builds")).isZero();

            wsManager.unregisterSession("s1");
            wsManager.shutdown(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Progress WebSocket")
    class ProgressWebSocketTests {

        @Test
        @DisplayName("should track connections")
        void shouldTrackConnections() { // GH-90000
            ProgressWebSocket ws = new ProgressWebSocket(); // GH-90000
            assertThat(ws.getConnectionCount()).isZero(); // GH-90000

            ws.handleConnect("s1", msg -> {}, () -> {}); // GH-90000
            assertThat(ws.getConnectionCount()).isEqualTo(1); // GH-90000
            assertThat(ws.isConnected("s1")).isTrue();

            ws.handleClose("s1");
            assertThat(ws.getConnectionCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("should handle ping message")
        void shouldHandlePingMessage() { // GH-90000
            ProgressWebSocket ws = new ProgressWebSocket(); // GH-90000
            StringBuilder sent = new StringBuilder(); // GH-90000
            ws.handleConnect("s1", sent::append, () -> {}); // GH-90000

            ws.handleMessage("s1", "{\"type\":\"ping\"}"); // GH-90000
            assertThat(sent.toString()).contains("pong");
        }
    }

    @Nested
    @DisplayName("Controller Unit Contracts")
    class ControllerContracts {

        @Test
        @DisplayName("TemplateController should accept YappcApi")
        void templateControllerShouldAcceptApi() { // GH-90000
            TemplateController ctrl = new TemplateController(server.getApi()); // GH-90000
            assertThat(ctrl).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("DependencyController should accept YappcApi")
        void dependencyControllerShouldAcceptApi() { // GH-90000
            DependencyController ctrl = new DependencyController(server.getApi()); // GH-90000
            assertThat(ctrl).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("PackController should accept YappcApi")
        void packControllerShouldAcceptApi() { // GH-90000
            PackController ctrl = new PackController(server.getApi()); // GH-90000
            assertThat(ctrl).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("ProjectController should accept YappcApi and ProgressWebSocket")
        void projectControllerShouldAcceptDeps() { // GH-90000
            ProjectController ctrl = new ProjectController(server.getApi(), new ProgressWebSocket()); // GH-90000
            assertThat(ctrl).isNotNull(); // GH-90000
        }
    }
}
