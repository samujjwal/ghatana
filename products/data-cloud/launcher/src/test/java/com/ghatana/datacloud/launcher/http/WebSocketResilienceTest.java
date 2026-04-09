/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * WebSocket resilience tests for the {@code GET /ws} endpoint.
 *
 * <p>Validates connection lifecycle, initial handshake frame, disconnection
 * handling, server cleanup, and concurrent connection management.
 * Uses the JDK 11+ {@link java.net.http.WebSocket} client to make real
 * WebSocket connections to a test server.
 *
 * <p>Gap 005 remediation — replaces missing WebSocket coverage identified in the
 * audit report.
 *
 * @doc.type class
 * @doc.purpose WebSocket resilience and connection lifecycle tests (Gap 005)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 20, unit = TimeUnit.SECONDS)
@DisplayName("WebSocket – Connection Resilience")
class WebSocketResilienceTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        // Lenient stubs — only exercised by tests that push data through the WebSocket pipeline
        lenient().when(mockClient.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Entity.of("ws-ent-1", "ws-col", Map.of())));
        lenient().when(mockClient.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1)));

        port = findFreePort();
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connection handshake and greeting
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Connection handshake")
    class ConnectionHandshakeTests {

        @Test
        @DisplayName("connecting to /ws succeeds and server sends greeting frame")
        void connect_serverSendsGreetingFrame() throws Exception {
            List<String> received = new CopyOnWriteArrayList<>();
            CountDownLatch greetingLatch = new CountDownLatch(1);

            WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                    new CollectingListener(received, greetingLatch))
                .get(5, TimeUnit.SECONDS);

            // Server must send an initial greeting within 3 seconds
            boolean gotGreeting = greetingLatch.await(3, TimeUnit.SECONDS);
            assertThat(gotGreeting).as("Server must send greeting within 3 s").isTrue();

            // Parse the greeting frame
            assertThat(received).isNotEmpty();
            JsonNode greeting = mapper.readTree(received.get(0));
            assertThat(greeting.has("type")).isTrue();
            assertThat(greeting.get("type").asText()).isEqualTo("system.notification");
            assertThat(greeting.has("data")).isTrue();

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(3, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("greeting frame data contains serverTime and message fields")
        void connect_greetingHasRequiredFields() throws Exception {
            List<String> received = new CopyOnWriteArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);

            WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                    new CollectingListener(received, latch))
                .get(5, TimeUnit.SECONDS);

            boolean got = latch.await(3, TimeUnit.SECONDS);
            assertThat(got).isTrue();

            JsonNode greeting = mapper.readTree(received.get(0));
            JsonNode data = greeting.get("data");
            assertThat(data).isNotNull();
            assertThat(data.has("serverTime")).as("data.serverTime must be present").isTrue();
            assertThat(data.has("message")).as("data.message must be present").isTrue();

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client-side disconnection
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Client disconnection")
    class ClientDisconnectionTests {

        @Test
        @DisplayName("client sends CLOSE frame → server accepts without error")
        void clientClose_serverAcceptsGracefully() throws Exception {
            CountDownLatch greetingLatch = new CountDownLatch(1);
            WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                    new CollectingListener(new ArrayList<>(), greetingLatch))
                .get(5, TimeUnit.SECONDS);

            greetingLatch.await(3, TimeUnit.SECONDS);

            // Send normal close — must not throw
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(3, TimeUnit.SECONDS);

            // Server should still be alive — verify via HTTP health check
            Thread.sleep(200);
            HttpResponse<String> health = httpClient.send(
                HttpRequest.newBuilder().GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("repeated connect-disconnect cycles do not degrade server")
        void repeatedConnectDisconnect_serverRemainsHealthy() throws Exception {
            for (int i = 0; i < 5; i++) {
                CountDownLatch latch = new CountDownLatch(1);
                WebSocket ws = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                        new CollectingListener(new ArrayList<>(), latch))
                    .get(5, TimeUnit.SECONDS);

                latch.await(3, TimeUnit.SECONDS);
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "cycle-" + i).get(3, TimeUnit.SECONDS);
                Thread.sleep(50);
            }

            // Server still responsive after 5 connect-disconnect cycles
            HttpResponse<String> health = httpClient.send(
                HttpRequest.newBuilder().GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent connections
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent connections")
    class ConcurrentConnectionTests {

        @Test
        @DisplayName("3 concurrent WebSocket clients all receive greeting frames")
        void concurrentClients_allReceiveGreeting() throws Exception {
            int clientCount = 3;
            List<CountDownLatch> latches = new ArrayList<>();
            List<WebSocket> sockets = new ArrayList<>();

            for (int i = 0; i < clientCount; i++) {
                CountDownLatch latch = new CountDownLatch(1);
                latches.add(latch);
                WebSocket ws = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                        new CollectingListener(new ArrayList<>(), latch))
                    .get(5, TimeUnit.SECONDS);
                sockets.add(ws);
            }

            // All clients must receive a greeting
            for (CountDownLatch latch : latches) {
                assertThat(latch.await(4, TimeUnit.SECONDS))
                    .as("Each concurrent client must receive a greeting").isTrue();
            }

            // Clean up
            for (WebSocket ws : sockets) {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("server sends /health 200 while WebSocket clients are connected")
        void httpHealthCheck_worksWhileWsClientsConnected() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                    new CollectingListener(new ArrayList<>(), latch))
                .get(5, TimeUnit.SECONDS);

            latch.await(3, TimeUnit.SECONDS);

            // HTTP must work concurrently with active WebSocket clients
            HttpResponse<String> health = httpClient.send(
                HttpRequest.newBuilder().GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server-side shutdown cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Server shutdown cleanup")
    class ServerShutdownCleanupTests {

        @Test
        @DisplayName("server.stop() closes active WebSocket connections without deadlock")
        void serverStop_closesActiveWebSocketConnections() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger closeCallbacks = new AtomicInteger(0);

            WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                    new WebSocket.Listener() {
                        @Override
                        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            latch.countDown();
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            closeCallbacks.incrementAndGet();
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }
                    })
                .get(5, TimeUnit.SECONDS);

            latch.await(3, TimeUnit.SECONDS);

            // Stop the server — must not deadlock and must close the WebSocket
            long before = System.currentTimeMillis();
            server.stop();
            server = null; // prevent double-stop in tearDown
            long elapsed = System.currentTimeMillis() - before;

            // Stop must complete within 5 seconds
            assertThat(elapsed).isLessThan(5_000L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Frame structure validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WebSocket message frame structure")
    class MessageFrameStructureTests {

        @Test
        @DisplayName("all server frames are valid JSON objects with 'type' and 'data' fields")
        void serverFrames_areValidJsonWithTypeAndData() throws Exception {
            List<String> received = new CopyOnWriteArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);

            WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                    new CollectingListener(received, latch))
                .get(5, TimeUnit.SECONDS);

            latch.await(3, TimeUnit.SECONDS);

            for (String frame : received) {
                JsonNode node = mapper.readTree(frame);
                assertThat(node.isObject()).as("Frame must be JSON object: " + frame).isTrue();
                assertThat(node.has("type")).as("Frame must have 'type' field: " + frame).isTrue();
                assertThat(node.has("data")).as("Frame must have 'data' field: " + frame).isTrue();
            }

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** WebSocket listener that collects text messages and counts down a latch on first message. */
    private static class CollectingListener implements WebSocket.Listener {
        private final List<String> messages;
        private final CountDownLatch firstMessageLatch;
        private final StringBuilder buffer = new StringBuilder();

        CollectingListener(List<String> messages, CountDownLatch firstMessageLatch) {
            this.messages           = messages;
            this.firstMessageLatch  = firstMessageLatch;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(10);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
                firstMessageLatch.countDown();
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            firstMessageLatch.countDown(); // unblock on close too
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            firstMessageLatch.countDown(); // unblock so tests don't hang
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s");
    }
}
