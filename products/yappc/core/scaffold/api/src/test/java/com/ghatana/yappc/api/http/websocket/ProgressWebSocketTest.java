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

package com.ghatana.yappc.api.http.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProgressWebSocket.
 *
 * @doc.type class
 * @doc.purpose WebSocket handler tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ProgressWebSocket Tests [GH-90000]")
class ProgressWebSocketTest {

    private ProgressWebSocket webSocket;

    @BeforeEach
    void setUp() { // GH-90000
        webSocket = new ProgressWebSocket(); // GH-90000
    }

    @Test
    @DisplayName("should start with zero connections [GH-90000]")
    void shouldStartWithZeroConnections() { // GH-90000
        assertThat(webSocket.getConnectionCount()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("should report disconnected for unknown session [GH-90000]")
    void shouldReportDisconnectedForUnknownSession() { // GH-90000
        assertThat(webSocket.isConnected("unknown-session [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("should handle broadcast to non-existent session gracefully [GH-90000]")
    void shouldHandleBroadcastToNonExistentSessionGracefully() { // GH-90000
        // Should not throw
        webSocket.broadcast("non-existent", "test message"); // GH-90000
        webSocket.sendComplete("non-existent", "result"); // GH-90000
        webSocket.sendError("non-existent", "error"); // GH-90000
    }

    @Test
    @DisplayName("should handle broadcast to all with no connections [GH-90000]")
    void shouldHandleBroadcastToAllWithNoConnections() { // GH-90000
        // Should not throw
        webSocket.broadcastToAll("test message [GH-90000]");
    }
}
