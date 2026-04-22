/*
 * Copyright (c) 2024 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.InMemoryEventCloud;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link DefaultEventLogClient}.
 *
 * <p>Uses an {@link InMemoryEventCloud} for verifiable event storage.
 * Promise-returning methods are executed within the managed ActiveJ Eventloop
 * provided by {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)} // GH-90000
 * since {@code Promise.ofBlocking()} requires a reactor in the current thread. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Comprehensive unit tests for DefaultEventLogClient
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DefaultEventLogClient [GH-90000]")
class DefaultEventLogClientTest extends EventloopTestBase {

    private InMemoryEventCloud eventCloud;
    private ObjectMapper objectMapper;
    private ExecutorService executor;
    private DefaultEventLogClient client;

    @BeforeEach
    void setUp() { // GH-90000
        eventCloud = new InMemoryEventCloud(); // GH-90000
        objectMapper = new ObjectMapper() // GH-90000
                .registerModule(new JavaTimeModule()) // GH-90000
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // GH-90000
        executor = Executors.newSingleThreadExecutor(); // GH-90000
        client = new DefaultEventLogClient(eventCloud, objectMapper, executor); // GH-90000
    }

    // ==================== Construction ====================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class ConstructionTests {

        @Test
        @DisplayName("single-arg constructor creates client with defaults [GH-90000]")
        void singleArgConstructor() { // GH-90000
            DefaultEventLogClient c = new DefaultEventLogClient(eventCloud); // GH-90000
            assertThat(c).isNotNull(); // GH-90000
            assertThat(c.getPublishedCount()).isZero(); // GH-90000
            assertThat(c.getFailedCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("two-arg constructor creates client with custom ObjectMapper [GH-90000]")
        void twoArgConstructor() { // GH-90000
            ObjectMapper om = new ObjectMapper(); // GH-90000
            DefaultEventLogClient c = new DefaultEventLogClient(eventCloud, om); // GH-90000
            assertThat(c).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects null eventCloud [GH-90000]")
        void rejectsNullEventCloud() { // GH-90000
            assertThatThrownBy(() -> new DefaultEventLogClient(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("eventCloud [GH-90000]");
        }

        @Test
        @DisplayName("constructor rejects null objectMapper [GH-90000]")
        void rejectsNullObjectMapper() { // GH-90000
            assertThatThrownBy(() -> new DefaultEventLogClient(eventCloud, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("objectMapper [GH-90000]");
        }

        @Test
        @DisplayName("constructor rejects null executor [GH-90000]")
        void rejectsNullExecutor() { // GH-90000
            assertThatThrownBy(() -> new DefaultEventLogClient(eventCloud, objectMapper, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("executor [GH-90000]");
        }
    }

    // ==================== publishEvent ====================

    @Nested
    @DisplayName("publishEvent [GH-90000]")
    class PublishEventTests {

        @Test
        @DisplayName("publishes Map event to EventCloud [GH-90000]")
        void publishesMapEvent() throws Exception { // GH-90000
            Map<String, Object> event = Map.of( // GH-90000
                    "stepId", "step-1",
                    "agentId", "agent-1",
                    "status", "SUCCESS",
                    "durationMs", 42L);

            publishAndAwait(client, "tenant-1", event); // GH-90000

            List<InMemoryEventCloud.StoredEvent> stored = eventCloud.getEvents("tenant-1 [GH-90000]");
            assertThat(stored).hasSize(1); // GH-90000
            assertThat(stored.get(0).eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000

            String json = new String(stored.get(0).payload(), StandardCharsets.UTF_8); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class); // GH-90000
            assertThat(parsed).containsEntry("stepId", "step-1"); // GH-90000
            assertThat(parsed).containsEntry("agentId", "agent-1"); // GH-90000
            assertThat(parsed).containsEntry("status", "SUCCESS"); // GH-90000
            assertThat(parsed).containsKey("_publishedAt [GH-90000]");
            assertThat(parsed).containsEntry("_schemaVersion", "1.0"); // GH-90000
        }

        @Test
        @DisplayName("routes FAILED events to error event type [GH-90000]")
        void routesFailedToErrorType() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("status", "FAILED", "errorMessage", "broke")); // GH-90000

            assertThat(eventCloud.getEvents("t1 [GH-90000]").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR); // GH-90000
        }

        @Test
        @DisplayName("routes TIMEOUT events to error event type [GH-90000]")
        void routesTimeoutToErrorType() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("status", "TIMEOUT")); // GH-90000

            assertThat(eventCloud.getEvents("t1 [GH-90000]").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR); // GH-90000
        }

        @Test
        @DisplayName("routes SUCCESS events to step execution type [GH-90000]")
        void routesSuccessToStepType() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("status", "SUCCESS")); // GH-90000

            assertThat(eventCloud.getEvents("t1 [GH-90000]").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("routes RETRY events to step execution type [GH-90000]")
        void routesRetryToStepType() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("status", "RETRY")); // GH-90000

            assertThat(eventCloud.getEvents("t1 [GH-90000]").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("routes CANCELLED events to step execution type [GH-90000]")
        void routesCancelledToStepType() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("status", "CANCELLED")); // GH-90000

            assertThat(eventCloud.getEvents("t1 [GH-90000]").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("routes event without status field to step execution type [GH-90000]")
        void routesNoStatusToStepType() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("key", "value")); // GH-90000

            assertThat(eventCloud.getEvents("t1 [GH-90000]").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("publishes byte[] payload as-is without serialization [GH-90000]")
        void publishesByteArrayAsIs() throws Exception { // GH-90000
            byte[] rawPayload = "{\"raw\":true}".getBytes(StandardCharsets.UTF_8); // GH-90000

            publishAndAwait(client, "t1", rawPayload); // GH-90000

            InMemoryEventCloud.StoredEvent stored = eventCloud.getEvents("t1 [GH-90000]").get(0);
            assertThat(stored.payload()).isEqualTo(rawPayload); // GH-90000
            assertThat(stored.eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("publishes to correct tenant isolation [GH-90000]")
        void respectsTenantIsolation() throws Exception { // GH-90000
            publishAndAwait(client, "tenant-A", Map.of("data", "a")); // GH-90000
            publishAndAwait(client, "tenant-B", Map.of("data", "b")); // GH-90000

            assertThat(eventCloud.getEvents("tenant-A [GH-90000]")).hasSize(1);
            assertThat(eventCloud.getEvents("tenant-B [GH-90000]")).hasSize(1);
            assertThat(eventCloud.getEvents("tenant-C [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("rejects null tenantId [GH-90000]")
        void rejectsNullTenantId() { // GH-90000
            assertThatThrownBy(() -> client.publishEvent(null, Map.of("k", "v"))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("tenantId [GH-90000]");
        }

        @Test
        @DisplayName("rejects null event [GH-90000]")
        void rejectsNullEvent() { // GH-90000
            assertThatThrownBy(() -> client.publishEvent("t1", null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("event [GH-90000]");
        }
    }

    // ==================== Serialization ====================

    @Nested
    @DisplayName("serializeEvent [GH-90000]")
    class SerializationTests {

        @Test
        @DisplayName("enriches Map events with metadata fields [GH-90000]")
        void enrichesMapWithMetadata() throws Exception { // GH-90000
            Map<String, Object> event = new HashMap<>(); // GH-90000
            event.put("stepId", "s1"); // GH-90000
            event.put("status", "SUCCESS"); // GH-90000

            byte[] bytes = client.serializeEvent(event); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class); // GH-90000

            assertThat(parsed).containsKeys("_publishedAt", "_schemaVersion", "stepId", "status"); // GH-90000
            assertThat(parsed.get("_schemaVersion [GH-90000]")).isEqualTo("1.0 [GH-90000]");
            assertThat(Instant.parse((String) parsed.get("_publishedAt [GH-90000]"))).isBeforeOrEqualTo(Instant.now());
        }

        @Test
        @DisplayName("preserves all original Map entries in serialization [GH-90000]")
        void preservesAllMapEntries() throws Exception { // GH-90000
            Map<String, Object> event = new HashMap<>(); // GH-90000
            event.put("stepId", "s1"); // GH-90000
            event.put("agentId", "a1"); // GH-90000
            event.put("status", "SUCCESS"); // GH-90000
            event.put("durationMs", 100L); // GH-90000
            event.put("attemptNumber", 2); // GH-90000
            event.put("totalAttempts", 3); // GH-90000
            event.put("resultData", Map.of("output", "done")); // GH-90000

            byte[] bytes = client.serializeEvent(event); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class); // GH-90000

            assertThat(parsed).containsEntry("stepId", "s1"); // GH-90000
            assertThat(parsed).containsEntry("agentId", "a1"); // GH-90000
            assertThat(parsed).containsEntry("status", "SUCCESS"); // GH-90000
            assertThat(parsed).containsEntry("attemptNumber", 2); // GH-90000
            assertThat(parsed).containsEntry("totalAttempts", 3); // GH-90000
        }

        @Test
        @DisplayName("returns byte[] as-is without modification [GH-90000]")
        void byteArrayPassthrough() throws Exception { // GH-90000
            byte[] original = "raw-bytes".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] result = client.serializeEvent(original); // GH-90000
            assertThat(result).isSameAs(original); // GH-90000
        }

        @Test
        @DisplayName("serializes non-Map objects via Jackson [GH-90000]")
        void serializesArbitraryObjects() throws Exception { // GH-90000
            record SimpleEvent(String id, int value) {} // GH-90000
            byte[] bytes = client.serializeEvent(new SimpleEvent("e1", 42)); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class); // GH-90000
            assertThat(parsed).containsEntry("id", "e1"); // GH-90000
            assertThat(parsed).containsEntry("value", 42); // GH-90000
        }

        @Test
        @DisplayName("handles empty Map [GH-90000]")
        void emptyMap() throws Exception { // GH-90000
            byte[] bytes = client.serializeEvent(Map.of()); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class); // GH-90000
            assertThat(parsed).containsKeys("_publishedAt", "_schemaVersion"); // GH-90000
            assertThat(parsed).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("handles Map with null values [GH-90000]")
        void mapWithNullValues() throws Exception { // GH-90000
            Map<String, Object> event = new HashMap<>(); // GH-90000
            event.put("stepId", "s1"); // GH-90000
            event.put("errorMessage", null); // GH-90000

            byte[] bytes = client.serializeEvent(event); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class); // GH-90000
            assertThat(parsed).containsEntry("stepId", "s1"); // GH-90000
            assertThat(parsed).containsKey("errorMessage [GH-90000]");
            assertThat(parsed.get("errorMessage [GH-90000]")).isNull();
        }
    }

    // ==================== Event Type Resolution ====================

    @Nested
    @DisplayName("resolveEventType [GH-90000]")
    class EventTypeResolutionTests {

        @Test
        @DisplayName("FAILED → agent.step.error [GH-90000]")
        void failedMapsToError() { // GH-90000
            assertThat(client.resolveEventType(Map.of("status", "FAILED"))) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR); // GH-90000
        }

        @Test
        @DisplayName("TIMEOUT → agent.step.error [GH-90000]")
        void timeoutMapsToError() { // GH-90000
            assertThat(client.resolveEventType(Map.of("status", "TIMEOUT"))) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR); // GH-90000
        }

        @Test
        @DisplayName("SUCCESS → agent.step.execution [GH-90000]")
        void successMapsToStep() { // GH-90000
            assertThat(client.resolveEventType(Map.of("status", "SUCCESS"))) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("RETRY → agent.step.execution [GH-90000]")
        void retryMapsToStep() { // GH-90000
            assertThat(client.resolveEventType(Map.of("status", "RETRY"))) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("CANCELLED → agent.step.execution [GH-90000]")
        void cancelledMapsToStep() { // GH-90000
            assertThat(client.resolveEventType(Map.of("status", "CANCELLED"))) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("no status key → agent.step.execution [GH-90000]")
        void noStatusDefaultsToStep() { // GH-90000
            assertThat(client.resolveEventType(Map.of("key", "value"))) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("null status value → agent.step.execution [GH-90000]")
        void nullStatusDefaultsToStep() { // GH-90000
            Map<String, Object> event = new HashMap<>(); // GH-90000
            event.put("status", null); // GH-90000
            assertThat(client.resolveEventType(event)).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("non-Map event → agent.step.execution [GH-90000]")
        void nonMapDefaultsToStep() { // GH-90000
            assertThat(client.resolveEventType("string-event [GH-90000]")).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("byte[] event → agent.step.execution [GH-90000]")
        void byteArrayDefaultsToStep() { // GH-90000
            assertThat(client.resolveEventType(new byte[] {1, 2, 3})) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000
        }

        @Test
        @DisplayName("status as enum object uses toString() [GH-90000]")
        void statusEnumUsesToString() { // GH-90000
            assertThat(client.resolveEventType(Map.of("status", TestStatus.FAILED))) // GH-90000
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR); // GH-90000
        }

        private enum TestStatus {
            FAILED,
            SUCCESS
        }
    }

    // ==================== Metrics ====================

    @Nested
    @DisplayName("Metrics [GH-90000]")
    class MetricsTests {

        @Test
        @DisplayName("initial metrics are all zero [GH-90000]")
        void initialMetricsZero() { // GH-90000
            assertThat(client.getPublishedCount()).isZero(); // GH-90000
            assertThat(client.getFailedCount()).isZero(); // GH-90000
            assertThat(client.getAverageLatencyMs()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("publishedCount increments on success [GH-90000]")
        void publishedCountIncrements() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("k", "v")); // GH-90000
            assertThat(client.getPublishedCount()).isEqualTo(1); // GH-90000

            publishAndAwait(client, "t1", Map.of("k", "v2")); // GH-90000
            assertThat(client.getPublishedCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("failedCount increments on EventCloud failure [GH-90000]")
        void failedCountIncrements() throws Exception { // GH-90000
            DefaultEventLogClient failClient =
                    new DefaultEventLogClient(new FailingEventCloud(), objectMapper, executor); // GH-90000

            try {
                publishAndAwait(failClient, "t1", Map.of("k", "v")); // GH-90000
            } catch (Exception ignored) { // GH-90000
                // expected
            }
            assertThat(failClient.getFailedCount()).isEqualTo(1); // GH-90000
            assertThat(failClient.getPublishedCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("averageLatencyMs is positive after successful publishes [GH-90000]")
        void averageLatencyPositive() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("a", "b")); // GH-90000
            assertThat(client.getAverageLatencyMs()).isGreaterThan(0.0); // GH-90000
        }

        @Test
        @DisplayName("resetMetrics clears all counters [GH-90000]")
        void resetMetrics() throws Exception { // GH-90000
            publishAndAwait(client, "t1", Map.of("k", "v")); // GH-90000
            assertThat(client.getPublishedCount()).isEqualTo(1); // GH-90000

            client.resetMetrics(); // GH-90000
            assertThat(client.getPublishedCount()).isZero(); // GH-90000
            assertThat(client.getFailedCount()).isZero(); // GH-90000
            assertThat(client.getAverageLatencyMs()).isEqualTo(0.0); // GH-90000
        }
    }

    // ==================== Error Handling ====================

    @Nested
    @DisplayName("Error Handling [GH-90000]")
    class ErrorHandlingTests {

        @Test
        @DisplayName("wraps EventCloud exceptions in EventPublishException [GH-90000]")
        void wrapsEventCloudExceptions() { // GH-90000
            DefaultEventLogClient failClient =
                    new DefaultEventLogClient(new FailingEventCloud(), objectMapper, executor); // GH-90000

            try {
                publishAndAwait(failClient, "t1", Map.of("k", "v")); // GH-90000
                assertThat(false).as("Should have thrown [GH-90000]").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(DefaultEventLogClient.EventPublishException.class); // GH-90000
                assertThat(e.getCause()).isInstanceOf(RuntimeException.class); // GH-90000
                assertThat(e.getMessage()).contains("tenant [GH-90000]");
            }
        }

        @Test
        @DisplayName("wraps serialization failures in EventPublishException [GH-90000]")
        void wrapsSerializationFailures() { // GH-90000
            ObjectMapper brokenMapper = new ObjectMapper() { // GH-90000
                @Override
                public byte[] writeValueAsBytes(Object value) throws JsonProcessingException { // GH-90000
                    throw new com.fasterxml.jackson.core.JsonGenerationException( // GH-90000
                            "forced failure", (com.fasterxml.jackson.core.JsonGenerator) null); // GH-90000
                }
            };
            DefaultEventLogClient failClient = new DefaultEventLogClient(eventCloud, brokenMapper, executor); // GH-90000

            try {
                publishAndAwait(failClient, "t1", new Object() { // GH-90000
                    @SuppressWarnings("unused [GH-90000]")
                    public String getField() { // GH-90000
                        throw new RuntimeException("boom [GH-90000]");
                    }
                });
                assertThat(false).as("Should have thrown [GH-90000]").isTrue();
            } catch (Exception e) { // GH-90000
                assertThat(e).isInstanceOf(DefaultEventLogClient.EventPublishException.class); // GH-90000
            }
        }
    }

    // ==================== Concurrency ====================

    @Nested
    @DisplayName("Concurrency [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("concurrent publishes all succeed and metrics are accurate [GH-90000]")
        void concurrentPublishes() throws Exception { // GH-90000
            int threadCount = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount); // GH-90000
            DefaultEventLogClient concurrentClient = new DefaultEventLogClient(eventCloud, objectMapper, pool); // GH-90000

            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger failures = new AtomicInteger(); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int idx = i;
                pool.submit(() -> { // GH-90000
                    try {
                        publishAndAwait( // GH-90000
                                concurrentClient, "concurrent-tenant", Map.of("thread", idx, "status", "SUCCESS")); // GH-90000
                    } catch (Exception e) { // GH-90000
                        failures.incrementAndGet(); // GH-90000
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS); // GH-90000
            assertThat(failures.get()).isZero(); // GH-90000
            assertThat(concurrentClient.getPublishedCount()).isEqualTo(threadCount); // GH-90000
            assertThat(eventCloud.getEvents("concurrent-tenant [GH-90000]")).hasSize(threadCount);

            pool.shutdown(); // GH-90000
        }
    }

    // ==================== Round-Trip Verification ====================

    @Nested
    @DisplayName("Round-trip verification [GH-90000]")
    class RoundTripTests {

        @Test
        @DisplayName("published event can be deserialized back with all fields intact [GH-90000]")
        void roundTrip() throws Exception { // GH-90000
            Instant now = Instant.now(); // GH-90000
            Map<String, Object> original = new HashMap<>(); // GH-90000
            original.put("stepId", "step-42"); // GH-90000
            original.put("agentId", "agent-7"); // GH-90000
            original.put("status", "SUCCESS"); // GH-90000
            original.put("attemptNumber", 1); // GH-90000
            original.put("totalAttempts", 3); // GH-90000
            original.put("durationMs", 250L); // GH-90000
            original.put("eventType", "agent.step.result"); // GH-90000
            original.put("timestamp", now.toEpochMilli()); // GH-90000

            publishAndAwait(client, "tenant-rt", original); // GH-90000

            byte[] storedBytes = eventCloud.getEvents("tenant-rt [GH-90000]").get(0).payload();
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> deserialized = objectMapper.readValue(storedBytes, Map.class); // GH-90000

            assertThat(deserialized).containsEntry("stepId", "step-42"); // GH-90000
            assertThat(deserialized).containsEntry("agentId", "agent-7"); // GH-90000
            assertThat(deserialized).containsEntry("status", "SUCCESS"); // GH-90000
            assertThat(deserialized).containsEntry("attemptNumber", 1); // GH-90000
            assertThat(deserialized).containsEntry("totalAttempts", 3); // GH-90000
            assertThat(deserialized).containsEntry("eventType", "agent.step.result"); // GH-90000
            assertThat(deserialized).containsKey("_publishedAt [GH-90000]");
            assertThat(deserialized).containsEntry("_schemaVersion", "1.0"); // GH-90000
        }

        @Test
        @DisplayName("byte[] round-trip preserves exact bytes [GH-90000]")
        void byteArrayRoundTrip() throws Exception { // GH-90000
            String jsonStr = "{\"custom\":\"payload\",\"value\":123}";
            byte[] rawBytes = jsonStr.getBytes(StandardCharsets.UTF_8); // GH-90000

            publishAndAwait(client, "tenant-bytes", rawBytes); // GH-90000

            byte[] storedBytes = eventCloud.getEvents("tenant-bytes [GH-90000]").get(0).payload();
            assertThat(new String(storedBytes, StandardCharsets.UTF_8)).isEqualTo(jsonStr); // GH-90000
        }
    }

    // ==================== AgentEventEmitter Integration ====================

    @Nested
    @DisplayName("AgentEventEmitter integration [GH-90000]")
    class AgentEventEmitterIntegrationTests {

        @Test
        @DisplayName("handles event in the format produced by AgentEventEmitter.buildAgentEvent() [GH-90000]")
        void handlesAgentEventEmitterFormat() throws Exception { // GH-90000
            Map<String, Object> agentEvent = new HashMap<>(); // GH-90000
            agentEvent.put("stepId", "step-abc"); // GH-90000
            agentEvent.put("agentId", "agent-xyz"); // GH-90000
            agentEvent.put("status", "SUCCESS"); // GH-90000
            agentEvent.put("attemptNumber", 1); // GH-90000
            agentEvent.put("totalAttempts", 1); // GH-90000
            agentEvent.put("eventId", "agent-step-agent-xyz-step-abc-1-1234567890"); // GH-90000
            agentEvent.put("eventType", "agent.step.result"); // GH-90000
            agentEvent.put("timestamp", Instant.now().toEpochMilli()); // GH-90000
            agentEvent.put("durationMs", 100L); // GH-90000
            agentEvent.put("resultData", Map.of("output", "computed-value")); // GH-90000
            agentEvent.put("errorMessage", null); // GH-90000

            publishAndAwait(client, "production-tenant", agentEvent); // GH-90000

            List<InMemoryEventCloud.StoredEvent> events = eventCloud.getEvents("production-tenant [GH-90000]");
            assertThat(events).hasSize(1); // GH-90000

            InMemoryEventCloud.StoredEvent stored = events.get(0); // GH-90000
            assertThat(stored.eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> parsed = objectMapper.readValue(stored.payload(), Map.class); // GH-90000
            assertThat(parsed).containsEntry("stepId", "step-abc"); // GH-90000
            assertThat(parsed).containsEntry("agentId", "agent-xyz"); // GH-90000
            assertThat(parsed).containsEntry("eventType", "agent.step.result"); // GH-90000
            assertThat(parsed.get("resultData [GH-90000]")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("handles failed event from AgentEventEmitter format [GH-90000]")
        void handlesFailedAgentEvent() throws Exception { // GH-90000
            Map<String, Object> failedEvent = new HashMap<>(); // GH-90000
            failedEvent.put("stepId", "step-fail"); // GH-90000
            failedEvent.put("agentId", "agent-crash"); // GH-90000
            failedEvent.put("status", "FAILED"); // GH-90000
            failedEvent.put("errorMessage", "OutOfMemoryError"); // GH-90000
            failedEvent.put("durationMs", 5000L); // GH-90000

            publishAndAwait(client, "tenant-err", failedEvent); // GH-90000

            InMemoryEventCloud.StoredEvent stored =
                    eventCloud.getEvents("tenant-err [GH-90000]").get(0);
            assertThat(stored.eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR); // GH-90000
        }
    }

    // ==================== Test Helpers ====================

    /**
     * Publish an event via the given client using the managed Eventloop from
     * {@link EventloopTestBase}. Propagates any exception thrown by the Promise.
     */
    private void publishAndAwait(DefaultEventLogClient clientToUse, String tenantId, Object event) throws Exception { // GH-90000
        try {
            runPromise(() -> clientToUse.publishEvent(tenantId, event)); // GH-90000
        } catch (DefaultEventLogClient.EventPublishException e) { // GH-90000
            throw e; // already the right type — don't unwrap
        } catch (RuntimeException e) { // GH-90000
            // Unwrap checked exceptions rethrown as RuntimeException by runPromise
            if (e.getCause() instanceof Exception checked) { // GH-90000
                throw checked;
            }
            throw e;
        }
    }

    /** EventCloud that always throws on append — for error-path testing. */
    private static class FailingEventCloud implements EventCloud {
        @Override
        public String append(String tenantId, String eventType, byte[] payload) { // GH-90000
            throw new RuntimeException("Simulated EventCloud failure [GH-90000]");
        }

        @Override
        public Subscription subscribe(String tenantId, String eventType, EventHandler handler) { // GH-90000
            throw new UnsupportedOperationException(); // GH-90000
        }
    }
}
