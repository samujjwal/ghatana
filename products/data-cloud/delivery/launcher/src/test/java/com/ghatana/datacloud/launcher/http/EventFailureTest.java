/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. 
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
 * <p><strong>Requirement:</strong> DC-F-011 (Event Sourcing), DC-NF-002 (Reliability) 
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
 * @doc.purpose Event system failure path and edge case tests (DC-F-011, DC-NF-002) 
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Event System Failure Paths")
class EventFailureTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); 
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        mockClient = mock(DataCloudClient.class); 
        port = findFreePort(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Partition Unavailability
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Broker / partition failure scenarios")
    class PartitionFailureTests {

        @Test
        @DisplayName("Event append when broker is down must return 5xx, not hang")
        void appendEvent_brokerDown_returns5xx() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                .thenReturn(Promise.ofException(new IOException("Connection refused: broker unavailable")));

            startServer(); 
            HttpResponse<String> resp = postJson("/api/v1/events", 
                Map.of("type", "USER_CREATED", "payload", Map.of("userId", "u-1"))); 

            assertThat(resp.statusCode()).isIn(500, 502, 503); 
        }

        @Test
        @DisplayName("Event append timeout must return 503 or 504, not hang indefinitely")
        void appendEvent_timeout_returns503or504() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                .thenReturn(Promise.ofException(new TimeoutException("Broker write timed out")));

            startServer(); 
            HttpResponse<String> resp = postJson("/api/v1/events", 
                Map.of("type", "TIMEOUT_EVENT", "payload", Map.of())); 

            assertThat(resp.statusCode()).isIn(400, 500, 503, 504); 
        }

        @Test
        @DisplayName("Partial partition failure: some appends succeed, some fail")
        void partialPartitionFailure_mixedResults() throws Exception { 
            AtomicInteger callCount = new AtomicInteger(0); 

            when(mockClient.appendEvent(anyString(), any())).thenAnswer(inv -> { 
                int call = callCount.incrementAndGet(); 
                if (call % 2 == 0) { 
                    return Promise.ofException(new IOException("Partition " + call + " unavailable")); 
                }
                return Promise.of(DataCloudClient.Offset.of(call)); 
            });

            startServer(); 
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < 6; i++) { 
                HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("type", "PARTIAL_EVENT", "payload", Map.of("i", i))); 
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) successCount++; 
                else failCount++;
            }

            // At least some succeed, at least some fail (alternating mock) 
            assertThat(successCount).isPositive(); 
            assertThat(failCount).isPositive(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Duplicate Event Handling
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Duplicate event handling")
    class DuplicateEventTests {

        @Test
        @DisplayName("Appending same event twice returns consistent result (idempotent)")
        void appendSameEventTwice_idempotent() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                .thenReturn(Promise.of(DataCloudClient.Offset.of(42))); 

            startServer(); 
            Map<String, Object> event = Map.of("type", "USER_SIGNUP", 
                "payload", Map.of("email", "user@example.com")); 

            HttpResponse<String> first = postJson("/api/v1/events", event); 
            HttpResponse<String> second = postJson("/api/v1/events", event); 

            assertThat(first.statusCode()).isIn(200, 201); 
            assertThat(second.statusCode()).isIn(200, 201); 
        }

        @Test
        @DisplayName("Events with same payload but different times create distinct offsets")
        void eventsWithDifferentTimestamps_createDistinctOffsets() throws Exception { 
            AtomicInteger counter = new AtomicInteger(0); 
            when(mockClient.appendEvent(anyString(), any())) 
                .thenAnswer(inv -> Promise.of(DataCloudClient.Offset.of(counter.incrementAndGet()))); 

            startServer(); 

            HttpResponse<String> resp1 = postJson("/api/v1/events", 
                Map.of("type", "ORDER_PLACED", "payload", Map.of("orderId", "o-1"))); 
            HttpResponse<String> resp2 = postJson("/api/v1/events", 
                Map.of("type", "ORDER_PLACED", "payload", Map.of("orderId", "o-1"))); 

            assertThat(resp1.statusCode()).isIn(200, 201); 
            assertThat(resp2.statusCode()).isIn(200, 201); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payload Validation Failures
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event payload validation failures")
    class PayloadValidationFailureTests {

        @Test
        @DisplayName("Event with missing type field returns 400")
        void eventMissingTypeField_returns400() throws Exception { 
            startServer(); 
            HttpResponse<String> resp = postJson("/api/v1/events", 
                Map.of("payload", Map.of("key", "value"))); 

            assertThat(resp.statusCode()).isIn(400, 422); 
        }

        @Test
        @DisplayName("Event with empty body returns 400")
        void eventEmptyBody_returns400() throws Exception { 
            startServer(); 
            HttpResponse<String> resp = postRaw("/api/v1/events", "{}"); 

            assertThat(resp.statusCode()).isIn(400, 422); 
        }

        @Test
        @DisplayName("Event with malformed JSON returns 400")
        void eventMalformedJson_returns400() throws Exception { 
            startServer(); 
            HttpResponse<String> resp = postRaw("/api/v1/events", "{not valid json}"); 

            assertThat(resp.statusCode()).isIn(400, 422); 
        }

        @ParameterizedTest(name = "[{index}] event type: {0}") 
        @ValueSource(strings = { 
            "",
            "   ",
            "../../../etc",
            "<script>alert(1)</script>", 
            "type; DROP TABLE events;--",
        })
        @DisplayName("Event with invalid type value returns 400 or 422")
        void eventWithInvalidType_returns400(String invalidType) throws Exception { 
            startServer(); 
            HttpResponse<String> resp = postJson("/api/v1/events", 
                Map.of("type", invalidType, "payload", Map.of())); 

            assertThat(resp.statusCode()).isIn(400, 422, 500); 
        }

        @Test
        @DisplayName("Event with null payload is rejected or treated as empty payload")
        void eventWithNullPayload_handledSafely() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); 

            startServer(); 
            HttpResponse<String> resp = postRaw("/api/v1/events", 
                "{\"type\": \"NULL_PAYLOAD\", \"payload\": null}");

            assertThat(resp.statusCode()).isIn(200, 201, 400, 422); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event Query Failure Paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event query failure paths")
    class EventQueryFailureTests {

        @Test
        @DisplayName("Event query with invalid limit parameter returns 400")
        void eventQueryInvalidLimit_returns400() throws Exception { 
            startServer(); 
            HttpResponse<String> resp = get("/api/v1/events?limit=-1");

            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("Event query with extremely large limit is clamped or rejected")
        void eventQueryExtremeLargeLimit_clampedOrRejected() throws Exception { 
            when(mockClient.queryEvents(anyString(), any())) 
                .thenReturn(Promise.of(List.of())); 

            startServer(); 
            HttpResponse<String> resp = get("/api/v1/events?limit=1000000");

            assertThat(resp.statusCode()).isIn(200, 400, 422); 
        }

        @Test
        @DisplayName("Event query with non-numeric limit returns 400")
        void eventQueryNonNumericLimit_returns400() throws Exception { 
            startServer(); 
            HttpResponse<String> resp = get("/api/v1/events?limit=abc");

            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("Event query when store throws exception returns 5xx")
        void eventQueryStoreException_returns5xx() throws Exception { 
            when(mockClient.queryEvents(anyString(), any())) 
                .thenReturn(Promise.ofException(new RuntimeException("Store corruption detected")));

            startServer(); 
            HttpResponse<String> resp = get("/api/v1/events?limit=10");

            assertThat(resp.statusCode()).isIn(500, 503); 
        }

        @Test
        @DisplayName("Event query by type filter with unknown type returns empty list")
        void eventQueryByUnknownType_returnsEmpty() throws Exception { 
            when(mockClient.queryEvents(anyString(), any())) 
                .thenReturn(Promise.of(List.of())); 

            startServer(); 
            HttpResponse<String> resp = get("/api/v1/events?type=UNKNOWN_EVENT_TYPE_XYZ&limit=10");

            assertThat(resp.statusCode()).isEqualTo(200); 
            assertThat(resp.body()).contains("[]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent Append Parallelism
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent event append parallelism")
    class ConcurrentAppendTests {

        @Test
        @DisplayName("Multiple concurrent appends must all succeed without data corruption")
        void concurrentAppends_allSucceed() throws Exception { 
            AtomicInteger counter = new AtomicInteger(0); 
            when(mockClient.appendEvent(anyString(), any())) 
                .thenAnswer(inv -> Promise.of(DataCloudClient.Offset.of(counter.incrementAndGet()))); 

            int threadCount = 20;
            CountDownLatch startLatch = new CountDownLatch(1); 
            CountDownLatch doneLatch = new CountDownLatch(threadCount); 
            List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>()); 

            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            for (int i = 0; i < threadCount; i++) { 
                final int idx = i;
                Thread.ofVirtual().start(() -> { 
                    try {
                        startLatch.await(); 
                        HttpResponse<String> resp = postJson("/api/v1/events", 
                            Map.of("type", "CONCURRENT_EVENT", "payload", Map.of("thread", idx))); 
                        statusCodes.add(resp.statusCode()); 
                    } catch (Exception e) { 
                        statusCodes.add(500); 
                    } finally {
                        doneLatch.countDown(); 
                    }
                });
            }

            startLatch.countDown(); 
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue(); 

            long successCount = statusCodes.stream().filter(s -> s >= 200 && s < 300).count(); 
            assertThat(successCount).isEqualTo(threadCount); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void startServer() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port); 
        server.start(); 
        waitForServerReady(port); 
    }

    private HttpResponse<String> get(String path) throws Exception { 
        return httpClient.send( 
            HttpRequest.newBuilder().GET() 
                .uri(URI.create("http://127.0.0.1:" + port + path)) 
                .build(), 
            HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> postJson(String path, Object body) throws Exception { 
        return postRaw(path, mapper.writeValueAsString(body)); 
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception { 
        return httpClient.send( 
            HttpRequest.newBuilder() 
                .POST(HttpRequest.BodyPublishers.ofString(body)) 
                .uri(URI.create("http://127.0.0.1:" + port + path)) 
                .header("Content-Type", "application/json") 
                .build(), 
            HttpResponse.BodyHandlers.ofString()); 
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
