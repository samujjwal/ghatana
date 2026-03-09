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
@DisplayName("ProgressWebSocket Tests")
class ProgressWebSocketTest {

    private ProgressWebSocket webSocket;

    @BeforeEach
    void setUp() {
        webSocket = new ProgressWebSocket();
    }

    @Test
    @DisplayName("should start with zero connections")
    void shouldStartWithZeroConnections() {
        assertThat(webSocket.getConnectionCount()).isZero();
    }

    @Test
    @DisplayName("should report disconnected for unknown session")
    void shouldReportDisconnectedForUnknownSession() {
        assertThat(webSocket.isConnected("unknown-session")).isFalse();
    }

    @Test
    @DisplayName("should handle broadcast to non-existent session gracefully")
    void shouldHandleBroadcastToNonExistentSessionGracefully() {
        // Should not throw
        webSocket.broadcast("non-existent", "test message");
        webSocket.sendComplete("non-existent", "result");
        webSocket.sendError("non-existent", "error");
    }

    @Test
    @DisplayName("should handle broadcast to all with no connections")
    void shouldHandleBroadcastToAllWithNoConnections() {
        // Should not throw
        webSocket.broadcastToAll("test message");
    }
}
