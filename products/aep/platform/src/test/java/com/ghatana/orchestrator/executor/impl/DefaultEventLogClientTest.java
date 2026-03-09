/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.InMemoryEventCloud;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for {@link DefaultEventLogClient}.
 *
 * <p>Uses an {@link InMemoryEventCloud} for verifiable event storage.
 * Promise-returning methods are executed within an ActiveJ {@link Eventloop}
 * since {@code Promise.ofBlocking()} requires a reactor in the current thread.
 */
@DisplayName("DefaultEventLogClient")
class DefaultEventLogClientTest {

    private InMemoryEventCloud eventCloud;
    private ObjectMapper objectMapper;
    private ExecutorService executor;
    private DefaultEventLogClient client;

    @BeforeEach
    void setUp() {
        eventCloud = new InMemoryEventCloud();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        executor = Executors.newSingleThreadExecutor();
        client = new DefaultEventLogClient(eventCloud, objectMapper, executor);
    }

    // ==================== Construction ====================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("single-arg constructor creates client with defaults")
        void singleArgConstructor() {
            DefaultEventLogClient c = new DefaultEventLogClient(eventCloud);
            assertThat(c).isNotNull();
            assertThat(c.getPublishedCount()).isZero();
            assertThat(c.getFailedCount()).isZero();
        }

        @Test
        @DisplayName("two-arg constructor creates client with custom ObjectMapper")
        void twoArgConstructor() {
            ObjectMapper om = new ObjectMapper();
            DefaultEventLogClient c = new DefaultEventLogClient(eventCloud, om);
            assertThat(c).isNotNull();
        }

        @Test
        @DisplayName("constructor rejects null eventCloud")
        void rejectsNullEventCloud() {
            assertThatThrownBy(() -> new DefaultEventLogClient(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("eventCloud");
        }

        @Test
        @DisplayName("constructor rejects null objectMapper")
        void rejectsNullObjectMapper() {
            assertThatThrownBy(() -> new DefaultEventLogClient(eventCloud, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("objectMapper");
        }

        @Test
        @DisplayName("constructor rejects null executor")
        void rejectsNullExecutor() {
            assertThatThrownBy(() -> new DefaultEventLogClient(eventCloud, objectMapper, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("executor");
        }
    }

    // ==================== publishEvent ====================

    @Nested
    @DisplayName("publishEvent")
    class PublishEventTests {

        @Test
        @DisplayName("publishes Map event to EventCloud")
        void publishesMapEvent() throws Exception {
            Map<String, Object> event = Map.of(
                    "stepId", "step-1",
                    "agentId", "agent-1",
                    "status", "SUCCESS",
                    "durationMs", 42L
            );

            publishAndAwait(client, "tenant-1", event);

            List<InMemoryEventCloud.StoredEvent> stored = eventCloud.getEvents("tenant-1");
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);

            String json = new String(stored.get(0).payload(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            assertThat(parsed).containsEntry("stepId", "step-1");
            assertThat(parsed).containsEntry("agentId", "agent-1");
            assertThat(parsed).containsEntry("status", "SUCCESS");
            assertThat(parsed).containsKey("_publishedAt");
            assertThat(parsed).containsEntry("_schemaVersion", "1.0");
        }

        @Test
        @DisplayName("routes FAILED events to error event type")
        void routesFailedToErrorType() throws Exception {
            publishAndAwait(client, "t1", Map.of("status", "FAILED", "errorMessage", "broke"));

            assertThat(eventCloud.getEvents("t1").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR);
        }

        @Test
        @DisplayName("routes TIMEOUT events to error event type")
        void routesTimeoutToErrorType() throws Exception {
            publishAndAwait(client, "t1", Map.of("status", "TIMEOUT"));

            assertThat(eventCloud.getEvents("t1").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR);
        }

        @Test
        @DisplayName("routes SUCCESS events to step execution type")
        void routesSuccessToStepType() throws Exception {
            publishAndAwait(client, "t1", Map.of("status", "SUCCESS"));

            assertThat(eventCloud.getEvents("t1").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("routes RETRY events to step execution type")
        void routesRetryToStepType() throws Exception {
            publishAndAwait(client, "t1", Map.of("status", "RETRY"));

            assertThat(eventCloud.getEvents("t1").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("routes CANCELLED events to step execution type")
        void routesCancelledToStepType() throws Exception {
            publishAndAwait(client, "t1", Map.of("status", "CANCELLED"));

            assertThat(eventCloud.getEvents("t1").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("routes event without status field to step execution type")
        void routesNoStatusToStepType() throws Exception {
            publishAndAwait(client, "t1", Map.of("key", "value"));

            assertThat(eventCloud.getEvents("t1").get(0).eventType())
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("publishes byte[] payload as-is without serialization")
        void publishesByteArrayAsIs() throws Exception {
            byte[] rawPayload = "{\"raw\":true}".getBytes(StandardCharsets.UTF_8);

            publishAndAwait(client, "t1", rawPayload);

            InMemoryEventCloud.StoredEvent stored = eventCloud.getEvents("t1").get(0);
            assertThat(stored.payload()).isEqualTo(rawPayload);
            assertThat(stored.eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("publishes to correct tenant isolation")
        void respectsTenantIsolation() throws Exception {
            publishAndAwait(client, "tenant-A", Map.of("data", "a"));
            publishAndAwait(client, "tenant-B", Map.of("data", "b"));

            assertThat(eventCloud.getEvents("tenant-A")).hasSize(1);
            assertThat(eventCloud.getEvents("tenant-B")).hasSize(1);
            assertThat(eventCloud.getEvents("tenant-C")).isEmpty();
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() {
            assertThatThrownBy(() -> client.publishEvent(null, Map.of("k", "v")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("rejects null event")
        void rejectsNullEvent() {
            assertThatThrownBy(() -> client.publishEvent("t1", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("event");
        }
    }

    // ==================== Serialization ====================

    @Nested
    @DisplayName("serializeEvent")
    class SerializationTests {

        @Test
        @DisplayName("enriches Map events with metadata fields")
        void enrichesMapWithMetadata() throws Exception {
            Map<String, Object> event = new HashMap<>();
            event.put("stepId", "s1");
            event.put("status", "SUCCESS");

            byte[] bytes = client.serializeEvent(event);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class);

            assertThat(parsed).containsKeys("_publishedAt", "_schemaVersion", "stepId", "status");
            assertThat(parsed.get("_schemaVersion")).isEqualTo("1.0");
            assertThat(Instant.parse((String) parsed.get("_publishedAt"))).isBeforeOrEqualTo(Instant.now());
        }

        @Test
        @DisplayName("preserves all original Map entries in serialization")
        void preservesAllMapEntries() throws Exception {
            Map<String, Object> event = new HashMap<>();
            event.put("stepId", "s1");
            event.put("agentId", "a1");
            event.put("status", "SUCCESS");
            event.put("durationMs", 100L);
            event.put("attemptNumber", 2);
            event.put("totalAttempts", 3);
            event.put("resultData", Map.of("output", "done"));

            byte[] bytes = client.serializeEvent(event);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class);

            assertThat(parsed).containsEntry("stepId", "s1");
            assertThat(parsed).containsEntry("agentId", "a1");
            assertThat(parsed).containsEntry("status", "SUCCESS");
            assertThat(parsed).containsEntry("attemptNumber", 2);
            assertThat(parsed).containsEntry("totalAttempts", 3);
        }

        @Test
        @DisplayName("returns byte[] as-is without modification")
        void byteArrayPassthrough() throws Exception {
            byte[] original = "raw-bytes".getBytes(StandardCharsets.UTF_8);
            byte[] result = client.serializeEvent(original);
            assertThat(result).isSameAs(original);
        }

        @Test
        @DisplayName("serializes non-Map objects via Jackson")
        void serializesArbitraryObjects() throws Exception {
            record SimpleEvent(String id, int value) {}
            byte[] bytes = client.serializeEvent(new SimpleEvent("e1", 42));
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class);
            assertThat(parsed).containsEntry("id", "e1");
            assertThat(parsed).containsEntry("value", 42);
        }

        @Test
        @DisplayName("handles empty Map")
        void emptyMap() throws Exception {
            byte[] bytes = client.serializeEvent(Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class);
            assertThat(parsed).containsKeys("_publishedAt", "_schemaVersion");
            assertThat(parsed).hasSize(2);
        }

        @Test
        @DisplayName("handles Map with null values")
        void mapWithNullValues() throws Exception {
            Map<String, Object> event = new HashMap<>();
            event.put("stepId", "s1");
            event.put("errorMessage", null);

            byte[] bytes = client.serializeEvent(event);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(bytes, Map.class);
            assertThat(parsed).containsEntry("stepId", "s1");
            assertThat(parsed).containsKey("errorMessage");
            assertThat(parsed.get("errorMessage")).isNull();
        }
    }

    // ==================== Event Type Resolution ====================

    @Nested
    @DisplayName("resolveEventType")
    class EventTypeResolutionTests {

        @Test
        @DisplayName("FAILED → agent.step.error")
        void failedMapsToError() {
            assertThat(client.resolveEventType(Map.of("status", "FAILED")))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR);
        }

        @Test
        @DisplayName("TIMEOUT → agent.step.error")
        void timeoutMapsToError() {
            assertThat(client.resolveEventType(Map.of("status", "TIMEOUT")))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR);
        }

        @Test
        @DisplayName("SUCCESS → agent.step.execution")
        void successMapsToStep() {
            assertThat(client.resolveEventType(Map.of("status", "SUCCESS")))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("RETRY → agent.step.execution")
        void retryMapsToStep() {
            assertThat(client.resolveEventType(Map.of("status", "RETRY")))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("CANCELLED → agent.step.execution")
        void cancelledMapsToStep() {
            assertThat(client.resolveEventType(Map.of("status", "CANCELLED")))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("no status key → agent.step.execution")
        void noStatusDefaultsToStep() {
            assertThat(client.resolveEventType(Map.of("key", "value")))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("null status value → agent.step.execution")
        void nullStatusDefaultsToStep() {
            Map<String, Object> event = new HashMap<>();
            event.put("status", null);
            assertThat(client.resolveEventType(event))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("non-Map event → agent.step.execution")
        void nonMapDefaultsToStep() {
            assertThat(client.resolveEventType("string-event"))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("byte[] event → agent.step.execution")
        void byteArrayDefaultsToStep() {
            assertThat(client.resolveEventType(new byte[]{1, 2, 3}))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);
        }

        @Test
        @DisplayName("status as enum object uses toString()")
        void statusEnumUsesToString() {
            assertThat(client.resolveEventType(Map.of("status", TestStatus.FAILED)))
                    .isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR);
        }

        private enum TestStatus { FAILED, SUCCESS }
    }

    // ==================== Metrics ====================

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("initial metrics are all zero")
        void initialMetricsZero() {
            assertThat(client.getPublishedCount()).isZero();
            assertThat(client.getFailedCount()).isZero();
            assertThat(client.getAverageLatencyMs()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("publishedCount increments on success")
        void publishedCountIncrements() throws Exception {
            publishAndAwait(client, "t1", Map.of("k", "v"));
            assertThat(client.getPublishedCount()).isEqualTo(1);

            publishAndAwait(client, "t1", Map.of("k", "v2"));
            assertThat(client.getPublishedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("failedCount increments on EventCloud failure")
        void failedCountIncrements() throws Exception {
            DefaultEventLogClient failClient = new DefaultEventLogClient(
                    new FailingEventCloud(), objectMapper, executor);

            try {
                publishAndAwait(failClient, "t1", Map.of("k", "v"));
            } catch (Exception ignored) {
                // expected
            }
            assertThat(failClient.getFailedCount()).isEqualTo(1);
            assertThat(failClient.getPublishedCount()).isZero();
        }

        @Test
        @DisplayName("averageLatencyMs is positive after successful publishes")
        void averageLatencyPositive() throws Exception {
            publishAndAwait(client, "t1", Map.of("a", "b"));
            assertThat(client.getAverageLatencyMs()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("resetMetrics clears all counters")
        void resetMetrics() throws Exception {
            publishAndAwait(client, "t1", Map.of("k", "v"));
            assertThat(client.getPublishedCount()).isEqualTo(1);

            client.resetMetrics();
            assertThat(client.getPublishedCount()).isZero();
            assertThat(client.getFailedCount()).isZero();
            assertThat(client.getAverageLatencyMs()).isEqualTo(0.0);
        }
    }

    // ==================== Error Handling ====================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("wraps EventCloud exceptions in EventPublishException")
        void wrapsEventCloudExceptions() {
            DefaultEventLogClient failClient = new DefaultEventLogClient(
                    new FailingEventCloud(), objectMapper, executor);

            try {
                publishAndAwait(failClient, "t1", Map.of("k", "v"));
                assertThat(false).as("Should have thrown").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(DefaultEventLogClient.EventPublishException.class);
                assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
                assertThat(e.getMessage()).contains("tenant");
            }
        }

        @Test
        @DisplayName("wraps serialization failures in EventPublishException")
        void wrapsSerializationFailures() {
            ObjectMapper brokenMapper = new ObjectMapper() {
                @Override
                public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
                    throw new com.fasterxml.jackson.core.JsonGenerationException(
                            "forced failure", (com.fasterxml.jackson.core.JsonGenerator) null);
                }
            };
            DefaultEventLogClient failClient = new DefaultEventLogClient(
                    eventCloud, brokenMapper, executor);

            try {
                publishAndAwait(failClient, "t1", new Object() {
                    @SuppressWarnings("unused")
                    public String getField() { throw new RuntimeException("boom"); }
                });
                assertThat(false).as("Should have thrown").isTrue();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(DefaultEventLogClient.EventPublishException.class);
            }
        }
    }

    // ==================== Concurrency ====================

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("concurrent publishes all succeed and metrics are accurate")
        void concurrentPublishes() throws Exception {
            int threadCount = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            DefaultEventLogClient concurrentClient = new DefaultEventLogClient(
                    eventCloud, objectMapper, pool);

            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger failures = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        publishAndAwait(concurrentClient, "concurrent-tenant",
                                Map.of("thread", idx, "status", "SUCCESS"));
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            assertThat(failures.get()).isZero();
            assertThat(concurrentClient.getPublishedCount()).isEqualTo(threadCount);
            assertThat(eventCloud.getEvents("concurrent-tenant")).hasSize(threadCount);

            pool.shutdown();
        }
    }

    // ==================== Round-Trip Verification ====================

    @Nested
    @DisplayName("Round-trip verification")
    class RoundTripTests {

        @Test
        @DisplayName("published event can be deserialized back with all fields intact")
        void roundTrip() throws Exception {
            Instant now = Instant.now();
            Map<String, Object> original = new HashMap<>();
            original.put("stepId", "step-42");
            original.put("agentId", "agent-7");
            original.put("status", "SUCCESS");
            original.put("attemptNumber", 1);
            original.put("totalAttempts", 3);
            original.put("durationMs", 250L);
            original.put("eventType", "agent.step.result");
            original.put("timestamp", now.toEpochMilli());

            publishAndAwait(client, "tenant-rt", original);

            byte[] storedBytes = eventCloud.getEvents("tenant-rt").get(0).payload();
            @SuppressWarnings("unchecked")
            Map<String, Object> deserialized = objectMapper.readValue(storedBytes, Map.class);

            assertThat(deserialized).containsEntry("stepId", "step-42");
            assertThat(deserialized).containsEntry("agentId", "agent-7");
            assertThat(deserialized).containsEntry("status", "SUCCESS");
            assertThat(deserialized).containsEntry("attemptNumber", 1);
            assertThat(deserialized).containsEntry("totalAttempts", 3);
            assertThat(deserialized).containsEntry("eventType", "agent.step.result");
            assertThat(deserialized).containsKey("_publishedAt");
            assertThat(deserialized).containsEntry("_schemaVersion", "1.0");
        }

        @Test
        @DisplayName("byte[] round-trip preserves exact bytes")
        void byteArrayRoundTrip() throws Exception {
            String jsonStr = "{\"custom\":\"payload\",\"value\":123}";
            byte[] rawBytes = jsonStr.getBytes(StandardCharsets.UTF_8);

            publishAndAwait(client, "tenant-bytes", rawBytes);

            byte[] storedBytes = eventCloud.getEvents("tenant-bytes").get(0).payload();
            assertThat(new String(storedBytes, StandardCharsets.UTF_8)).isEqualTo(jsonStr);
        }
    }

    // ==================== AgentEventEmitter Integration ====================

    @Nested
    @DisplayName("AgentEventEmitter integration")
    class AgentEventEmitterIntegrationTests {

        @Test
        @DisplayName("handles event in the format produced by AgentEventEmitter.buildAgentEvent()")
        void handlesAgentEventEmitterFormat() throws Exception {
            Map<String, Object> agentEvent = new HashMap<>();
            agentEvent.put("stepId", "step-abc");
            agentEvent.put("agentId", "agent-xyz");
            agentEvent.put("status", "SUCCESS");
            agentEvent.put("attemptNumber", 1);
            agentEvent.put("totalAttempts", 1);
            agentEvent.put("eventId", "agent-step-agent-xyz-step-abc-1-1234567890");
            agentEvent.put("eventType", "agent.step.result");
            agentEvent.put("timestamp", Instant.now().toEpochMilli());
            agentEvent.put("durationMs", 100L);
            agentEvent.put("resultData", Map.of("output", "computed-value"));
            agentEvent.put("errorMessage", null);

            publishAndAwait(client, "production-tenant", agentEvent);

            List<InMemoryEventCloud.StoredEvent> events = eventCloud.getEvents("production-tenant");
            assertThat(events).hasSize(1);

            InMemoryEventCloud.StoredEvent stored = events.get(0);
            assertThat(stored.eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_STEP);

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(stored.payload(), Map.class);
            assertThat(parsed).containsEntry("stepId", "step-abc");
            assertThat(parsed).containsEntry("agentId", "agent-xyz");
            assertThat(parsed).containsEntry("eventType", "agent.step.result");
            assertThat(parsed.get("resultData")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("handles failed event from AgentEventEmitter format")
        void handlesFailedAgentEvent() throws Exception {
            Map<String, Object> failedEvent = new HashMap<>();
            failedEvent.put("stepId", "step-fail");
            failedEvent.put("agentId", "agent-crash");
            failedEvent.put("status", "FAILED");
            failedEvent.put("errorMessage", "OutOfMemoryError");
            failedEvent.put("durationMs", 5000L);

            publishAndAwait(client, "tenant-err", failedEvent);

            InMemoryEventCloud.StoredEvent stored = eventCloud.getEvents("tenant-err").get(0);
            assertThat(stored.eventType()).isEqualTo(DefaultEventLogClient.EVENT_TYPE_AGENT_ERROR);
        }
    }

    // ==================== Test Helpers ====================

    /**
     * Publish an event via the given client within an ActiveJ Eventloop context.
     * {@code Promise.ofBlocking()} requires a reactor — this method creates a
     * temporary Eventloop, posts the publish operation, runs the loop to
     * completion, and propagates any exception.
     */
    private static void publishAndAwait(DefaultEventLogClient clientToUse,
                                        String tenantId, Object event) throws Exception {
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        AtomicReference<Exception> error = new AtomicReference<>();

        eventloop.post(() ->
                clientToUse.publishEvent(tenantId, event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                error.set(ex);
                            }
                        })
        );

        eventloop.run();
        if (error.get() != null) {
            throw error.get();
        }
    }

    /** EventCloud that always throws on append — for error-path testing. */
    private static class FailingEventCloud implements EventCloud {
        @Override
        public String append(String tenantId, String eventType, byte[] payload) {
            throw new RuntimeException("Simulated EventCloud failure");
        }

        @Override
        public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
            throw new UnsupportedOperationException();
        }
    }
}
