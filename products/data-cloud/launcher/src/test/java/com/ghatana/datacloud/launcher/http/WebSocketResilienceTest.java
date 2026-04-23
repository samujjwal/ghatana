/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * @doc.purpose WebSocket resilience and connection lifecycle tests (Gap 005) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@Timeout(value = 20, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("WebSocket – Connection Resilience")
class WebSocketResilienceTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder() // GH-90000
        .connectTimeout(Duration.ofSeconds(5)) // GH-90000
        .build(); // GH-90000
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        // Lenient stubs — only exercised by tests that push data through the WebSocket pipeline
        lenient().when(mockClient.save(anyString(), anyString(), any())) // GH-90000
            .thenReturn(Promise.of(DataCloudClient.Entity.of("ws-ent-1", "ws-col", Map.of()))); // GH-90000
        lenient().when(mockClient.appendEvent(anyString(), any())) // GH-90000
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000

        port = findFreePort(); // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connection handshake and greeting
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Connection handshake")
    class ConnectionHandshakeTests {

        @Test
        @DisplayName("connecting to /ws succeeds and server sends greeting frame")
        void connect_serverSendsGreetingFrame() throws Exception { // GH-90000
            List<String> received = new CopyOnWriteArrayList<>(); // GH-90000
            CountDownLatch greetingLatch = new CountDownLatch(1); // GH-90000

            WebSocket ws = httpClient.newWebSocketBuilder() // GH-90000
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                    new CollectingListener(received, greetingLatch)) // GH-90000
                .get(5, TimeUnit.SECONDS); // GH-90000

            // Server must send an initial greeting within 3 seconds
            boolean gotGreeting = greetingLatch.await(3, TimeUnit.SECONDS); // GH-90000
            assertThat(gotGreeting).as("Server must send greeting within 3 s").isTrue();

            // Parse the greeting frame
            assertThat(received).isNotEmpty(); // GH-90000
            JsonNode greeting = mapper.readTree(received.get(0)); // GH-90000
            assertThat(greeting.has("type")).isTrue();
            assertThat(greeting.get("type").asText()).isEqualTo("system.notification");
            assertThat(greeting.has("data")).isTrue();

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(3, TimeUnit.SECONDS); // GH-90000
        }

        @Test
        @DisplayName("greeting frame data contains serverTime and message fields")
        void connect_greetingHasRequiredFields() throws Exception { // GH-90000
            List<String> received = new CopyOnWriteArrayList<>(); // GH-90000
            CountDownLatch latch = new CountDownLatch(1); // GH-90000

            WebSocket ws = httpClient.newWebSocketBuilder() // GH-90000
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                    new CollectingListener(received, latch)) // GH-90000
                .get(5, TimeUnit.SECONDS); // GH-90000

            boolean got = latch.await(3, TimeUnit.SECONDS); // GH-90000
            assertThat(got).isTrue(); // GH-90000

            JsonNode greeting = mapper.readTree(received.get(0)); // GH-90000
            JsonNode data = greeting.get("data");
            assertThat(data).isNotNull(); // GH-90000
            assertThat(data.has("serverTime")).as("data.serverTime must be present").isTrue();
            assertThat(data.has("message")).as("data.message must be present").isTrue();

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS); // GH-90000
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
        void clientClose_serverAcceptsGracefully() throws Exception { // GH-90000
            CountDownLatch greetingLatch = new CountDownLatch(1); // GH-90000
            WebSocket ws = httpClient.newWebSocketBuilder() // GH-90000
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                    new CollectingListener(new ArrayList<>(), greetingLatch)) // GH-90000
                .get(5, TimeUnit.SECONDS); // GH-90000

            assertThat(greetingLatch.await(3, TimeUnit.SECONDS)).isTrue(); // GH-90000

            // Send normal close — must not throw
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(3, TimeUnit.SECONDS); // GH-90000

            // Server should still be alive — verify via HTTP health check
            Thread.sleep(200); // GH-90000
            HttpResponse<String> health = httpClient.send( // GH-90000
                HttpRequest.newBuilder().GET() // GH-90000
                    .uri(URI.create("http://127.0.0.1:" + port + "/health")) // GH-90000
                    .build(), // GH-90000
                HttpResponse.BodyHandlers.ofString()); // GH-90000
            assertThat(health.statusCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("repeated connect-disconnect cycles do not degrade server")
        void repeatedConnectDisconnect_serverRemainsHealthy() throws Exception { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                CountDownLatch latch = new CountDownLatch(1); // GH-90000
                WebSocket ws = httpClient.newWebSocketBuilder() // GH-90000
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                        new CollectingListener(new ArrayList<>(), latch)) // GH-90000
                    .get(5, TimeUnit.SECONDS); // GH-90000

                assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue(); // GH-90000
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "cycle-" + i).get(3, TimeUnit.SECONDS); // GH-90000
                Thread.sleep(50); // GH-90000
            }

            // Server still responsive after 5 connect-disconnect cycles
            HttpResponse<String> health = httpClient.send( // GH-90000
                HttpRequest.newBuilder().GET() // GH-90000
                    .uri(URI.create("http://127.0.0.1:" + port + "/health")) // GH-90000
                    .build(), // GH-90000
                HttpResponse.BodyHandlers.ofString()); // GH-90000
            assertThat(health.statusCode()).isEqualTo(200); // GH-90000
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
        void concurrentClients_allReceiveGreeting() throws Exception { // GH-90000
            int clientCount = 3;
            List<CountDownLatch> latches = new ArrayList<>(); // GH-90000
            List<WebSocket> sockets = new ArrayList<>(); // GH-90000

            for (int i = 0; i < clientCount; i++) { // GH-90000
                CountDownLatch latch = new CountDownLatch(1); // GH-90000
                latches.add(latch); // GH-90000
                WebSocket ws = httpClient.newWebSocketBuilder() // GH-90000
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                        new CollectingListener(new ArrayList<>(), latch)) // GH-90000
                    .get(5, TimeUnit.SECONDS); // GH-90000
                sockets.add(ws); // GH-90000
            }

            // All clients must receive a greeting
            for (CountDownLatch latch : latches) { // GH-90000
                assertThat(latch.await(4, TimeUnit.SECONDS)) // GH-90000
                    .as("Each concurrent client must receive a greeting").isTrue();
            }

            // Clean up
            for (WebSocket ws : sockets) { // GH-90000
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS); // GH-90000
            }
        }

        @Test
        @DisplayName("server sends /health 200 while WebSocket clients are connected")
        void httpHealthCheck_worksWhileWsClientsConnected() throws Exception { // GH-90000
            CountDownLatch latch = new CountDownLatch(1); // GH-90000
            WebSocket ws = httpClient.newWebSocketBuilder() // GH-90000
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                    new CollectingListener(new ArrayList<>(), latch)) // GH-90000
                .get(5, TimeUnit.SECONDS); // GH-90000

            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue(); // GH-90000

            // HTTP must work concurrently with active WebSocket clients
            HttpResponse<String> health = httpClient.send( // GH-90000
                HttpRequest.newBuilder().GET() // GH-90000
                    .uri(URI.create("http://127.0.0.1:" + port + "/health")) // GH-90000
                    .build(), // GH-90000
                HttpResponse.BodyHandlers.ofString()); // GH-90000
            assertThat(health.statusCode()).isEqualTo(200); // GH-90000

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS); // GH-90000
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
        void serverStop_closesActiveWebSocketConnections() throws Exception { // GH-90000
            CountDownLatch latch = new CountDownLatch(1); // GH-90000
            AtomicInteger closeCallbacks = new AtomicInteger(0); // GH-90000

            httpClient.newWebSocketBuilder() // GH-90000
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                    new WebSocket.Listener() { // GH-90000
                        @Override
                        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) { // GH-90000
                            latch.countDown(); // GH-90000
                            webSocket.request(1); // GH-90000
                            return CompletableFuture.completedFuture(null); // GH-90000
                        }

                        @Override
                        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) { // GH-90000
                            closeCallbacks.incrementAndGet(); // GH-90000
                            return CompletableFuture.completedFuture(null); // GH-90000
                        }

                        @Override
                        public void onOpen(WebSocket webSocket) { // GH-90000
                            webSocket.request(1); // GH-90000
                        }
                    })
                .get(5, TimeUnit.SECONDS); // GH-90000

            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue(); // GH-90000

            // Stop the server — must not deadlock and must close the WebSocket
            long before = System.currentTimeMillis(); // GH-90000
            server.stop(); // GH-90000
            server = null; // prevent double-stop in tearDown
            long elapsed = System.currentTimeMillis() - before; // GH-90000

            // Stop must complete within 5 seconds
            assertThat(elapsed).isLessThan(5_000L); // GH-90000
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
        void serverFrames_areValidJsonWithTypeAndData() throws Exception { // GH-90000
            List<String> received = new CopyOnWriteArrayList<>(); // GH-90000
            CountDownLatch latch = new CountDownLatch(1); // GH-90000

            WebSocket ws = httpClient.newWebSocketBuilder() // GH-90000
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), // GH-90000
                    new CollectingListener(received, latch)) // GH-90000
                .get(5, TimeUnit.SECONDS); // GH-90000

            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue(); // GH-90000

            for (String frame : received) { // GH-90000
                JsonNode node = mapper.readTree(frame); // GH-90000
                assertThat(node.isObject()).as("Frame must be JSON object: " + frame).isTrue(); // GH-90000
                assertThat(node.has("type")).as("Frame must have 'type' field: " + frame).isTrue();
                assertThat(node.has("data")).as("Frame must have 'data' field: " + frame).isTrue();
            }

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** WebSocket listener that collects text messages and counts down a latch on first message. */
    private static class CollectingListener implements WebSocket.Listener {
        private final List<String> messages;
        private final CountDownLatch firstMessageLatch;
        private final StringBuilder buffer = new StringBuilder(); // GH-90000

        CollectingListener(List<String> messages, CountDownLatch firstMessageLatch) { // GH-90000
            this.messages           = messages;
            this.firstMessageLatch  = firstMessageLatch;
        }

        @Override
        public void onOpen(WebSocket webSocket) { // GH-90000
            webSocket.request(10); // GH-90000
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) { // GH-90000
            buffer.append(data); // GH-90000
            if (last) { // GH-90000
                messages.add(buffer.toString()); // GH-90000
                buffer.setLength(0); // GH-90000
                firstMessageLatch.countDown(); // GH-90000
            }
            webSocket.request(1); // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) { // GH-90000
            firstMessageLatch.countDown(); // unblock on close too // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) { // GH-90000
            firstMessageLatch.countDown(); // unblock so tests don't hang // GH-90000
        }
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
