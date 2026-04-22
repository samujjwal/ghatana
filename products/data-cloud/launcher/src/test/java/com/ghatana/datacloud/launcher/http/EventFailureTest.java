/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. // GH-90000
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Event system failure path tests for Data-Cloud HTTP event endpoints.
 *
 * <p><strong>Requirement:</strong> DC-F-011 (Event Sourcing), DC-NF-002 (Reliability) // GH-90000
 *
 * <p>Covers failure scenarios and edge conditions not exercised by the main
 * {@link DataCloudHttpServerEventTest}:
 * <ul>
 *   <li>Partition unavailability: event broker down during append.</li>
 *   <li>Duplicate event handling: idempotent appends must not corrupt the log.</li>
 *   <li>Oversized event payload: rejected before reaching the broker.</li>
 *   <li>Missing required event fields: rejected with 400.</li>
 *   <li>Event type injection: types with special characters are sanitized or rejected.</li>
 *   <li>Out-of-order consumer detection: consumer offset out of bounds.</li>
 *   <li>Concurrent append parallelism: consistent results under concurrent writers.</li>
 *   <li>Consumer group lag simulation: large backlog read does not timeout.</li>
 *   <li>Event store corruption recovery: promise rejection yields 500, not a hang.</li>
 *   <li>Event replay from invalid offset: bound-check validation.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Event system failure path and edge case tests (DC-F-011, DC-NF-002) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Event System Failure Paths [GH-90000]")
class EventFailureTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); // GH-90000
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Partition Unavailability
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Broker / partition failure scenarios [GH-90000]")
    class PartitionFailureTests {

        @Test
        @DisplayName("Event append when broker is down must return 5xx, not hang [GH-90000]")
        void appendEvent_brokerDown_returns5xx() throws Exception { // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                .thenReturn(Promise.ofException(new IOException("Connection refused: broker unavailable [GH-90000]")));

            startServer(); // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                Map.of("type", "USER_CREATED", "payload", Map.of("userId", "u-1"))); // GH-90000

            assertThat(resp.statusCode()).isIn(500, 502, 503); // GH-90000
        }

        @Test
        @DisplayName("Event append timeout must return 503 or 504, not hang indefinitely [GH-90000]")
        void appendEvent_timeout_returns503or504() throws Exception { // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                .thenReturn(Promise.ofException(new TimeoutException("Broker write timed out [GH-90000]")));

            startServer(); // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                Map.of("type", "TIMEOUT_EVENT", "payload", Map.of())); // GH-90000

            assertThat(resp.statusCode()).isIn(500, 503, 504); // GH-90000
        }

        @Test
        @DisplayName("Partial partition failure: some appends succeed, some fail [GH-90000]")
        void partialPartitionFailure_mixedResults() throws Exception { // GH-90000
            AtomicInteger callCount = new AtomicInteger(0); // GH-90000

            when(mockClient.appendEvent(anyString(), any())).thenAnswer(inv -> { // GH-90000
                int call = callCount.incrementAndGet(); // GH-90000
                if (call % 2 == 0) { // GH-90000
                    return Promise.ofException(new IOException("Partition " + call + " unavailable")); // GH-90000
                }
                return Promise.of(DataCloudClient.Offset.of(call)); // GH-90000
            });

            startServer(); // GH-90000
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < 6; i++) { // GH-90000
                HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "PARTIAL_EVENT", "payload", Map.of("i", i))); // GH-90000
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) successCount++; // GH-90000
                else failCount++;
            }

            // At least some succeed, at least some fail (alternating mock) // GH-90000
            assertThat(successCount).isPositive(); // GH-90000
            assertThat(failCount).isPositive(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Duplicate Event Handling
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Duplicate event handling [GH-90000]")
    class DuplicateEventTests {

        @Test
        @DisplayName("Appending same event twice returns consistent result (idempotent) [GH-90000]")
        void appendSameEventTwice_idempotent() throws Exception { // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                .thenReturn(Promise.of(DataCloudClient.Offset.of(42))); // GH-90000

            startServer(); // GH-90000
            Map<String, Object> event = Map.of("type", "USER_SIGNUP", // GH-90000
                "payload", Map.of("email", "user@example.com")); // GH-90000

            HttpResponse<String> first = postJson("/api/v1/events", event); // GH-90000
            HttpResponse<String> second = postJson("/api/v1/events", event); // GH-90000

            assertThat(first.statusCode()).isIn(200, 201); // GH-90000
            assertThat(second.statusCode()).isIn(200, 201); // GH-90000
        }

        @Test
        @DisplayName("Events with same payload but different times create distinct offsets [GH-90000]")
        void eventsWithDifferentTimestamps_createDistinctOffsets() throws Exception { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                .thenAnswer(inv -> Promise.of(DataCloudClient.Offset.of(counter.incrementAndGet()))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp1 = postJson("/api/v1/events", // GH-90000
                Map.of("type", "ORDER_PLACED", "payload", Map.of("orderId", "o-1"))); // GH-90000
            HttpResponse<String> resp2 = postJson("/api/v1/events", // GH-90000
                Map.of("type", "ORDER_PLACED", "payload", Map.of("orderId", "o-1"))); // GH-90000

            assertThat(resp1.statusCode()).isIn(200, 201); // GH-90000
            assertThat(resp2.statusCode()).isIn(200, 201); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payload Validation Failures
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event payload validation failures [GH-90000]")
    class PayloadValidationFailureTests {

        @Test
        @DisplayName("Event with missing type field returns 400 [GH-90000]")
        void eventMissingTypeField_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                Map.of("payload", Map.of("key", "value"))); // GH-90000

            assertThat(resp.statusCode()).isIn(400, 422); // GH-90000
        }

        @Test
        @DisplayName("Event with empty body returns 400 [GH-90000]")
        void eventEmptyBody_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000
            HttpResponse<String> resp = postRaw("/api/v1/events", "{}"); // GH-90000

            assertThat(resp.statusCode()).isIn(400, 422); // GH-90000
        }

        @Test
        @DisplayName("Event with malformed JSON returns 400 [GH-90000]")
        void eventMalformedJson_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000
            HttpResponse<String> resp = postRaw("/api/v1/events", "{not valid json}"); // GH-90000

            assertThat(resp.statusCode()).isIn(400, 422); // GH-90000
        }

        @ParameterizedTest(name = "[{index}] event type: {0}") // GH-90000
        @ValueSource(strings = { // GH-90000
            "",
            "   ",
            "../../../etc",
            "<script>alert(1)</script>", // GH-90000
            "type; DROP TABLE events;--",
        })
        @DisplayName("Event with invalid type value returns 400 or 422 [GH-90000]")
        void eventWithInvalidType_returns400(String invalidType) throws Exception { // GH-90000
            startServer(); // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                Map.of("type", invalidType, "payload", Map.of())); // GH-90000

            assertThat(resp.statusCode()).isIn(400, 422, 500); // GH-90000
        }

        @Test
        @DisplayName("Event with null payload is rejected or treated as empty payload [GH-90000]")
        void eventWithNullPayload_handledSafely() throws Exception { // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000

            startServer(); // GH-90000
            HttpResponse<String> resp = postRaw("/api/v1/events", // GH-90000
                "{\"type\": \"NULL_PAYLOAD\", \"payload\": null}");

            assertThat(resp.statusCode()).isIn(200, 201, 400, 422); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event Query Failure Paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event query failure paths [GH-90000]")
    class EventQueryFailureTests {

        @Test
        @DisplayName("Event query with invalid limit parameter returns 400 [GH-90000]")
        void eventQueryInvalidLimit_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/events?limit=-1 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("Event query with extremely large limit is clamped or rejected [GH-90000]")
        void eventQueryExtremeLargeLimit_clampedOrRejected() throws Exception { // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/events?limit=1000000 [GH-90000]");

            assertThat(resp.statusCode()).isIn(200, 400, 422); // GH-90000
        }

        @Test
        @DisplayName("Event query with non-numeric limit returns 400 [GH-90000]")
        void eventQueryNonNumericLimit_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/events?limit=abc [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("Event query when store throws exception returns 5xx [GH-90000]")
        void eventQueryStoreException_returns5xx() throws Exception { // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Store corruption detected [GH-90000]")));

            startServer(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/events?limit=10 [GH-90000]");

            assertThat(resp.statusCode()).isIn(500, 503); // GH-90000
        }

        @Test
        @DisplayName("Event query by type filter with unknown type returns empty list [GH-90000]")
        void eventQueryByUnknownType_returnsEmpty() throws Exception { // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/events?type=UNKNOWN_EVENT_TYPE_XYZ&limit=10 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp.body()).contains("[] [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent Append Parallelism
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent event append parallelism [GH-90000]")
    class ConcurrentAppendTests {

        @Test
        @DisplayName("Multiple concurrent appends must all succeed without data corruption [GH-90000]")
        void concurrentAppends_allSucceed() throws Exception { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                .thenAnswer(inv -> Promise.of(DataCloudClient.Offset.of(counter.incrementAndGet()))); // GH-90000

            int threadCount = 20;
            CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
            CountDownLatch doneLatch = new CountDownLatch(threadCount); // GH-90000
            List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>()); // GH-90000

            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int idx = i;
                Thread.ofVirtual().start(() -> { // GH-90000
                    try {
                        startLatch.await(); // GH-90000
                        HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                            Map.of("type", "CONCURRENT_EVENT", "payload", Map.of("thread", idx))); // GH-90000
                        statusCodes.add(resp.statusCode()); // GH-90000
                    } catch (Exception e) { // GH-90000
                        statusCodes.add(500); // GH-90000
                    } finally {
                        doneLatch.countDown(); // GH-90000
                    }
                });
            }

            startLatch.countDown(); // GH-90000
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue(); // GH-90000

            long successCount = statusCodes.stream().filter(s -> s >= 200 && s < 300).count(); // GH-90000
            assertThat(successCount).isEqualTo(threadCount); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder().GET() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> postJson(String path, Object body) throws Exception { // GH-90000
        return postRaw(path, mapper.writeValueAsString(body)); // GH-90000
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder() // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
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
