/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.eventstore.service;

import com.ghatana.core.event.cloud.AppendResult;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventCloud.AppendRequest;
import com.ghatana.core.event.cloud.EventCloud.EventConsumer;
import com.ghatana.core.event.cloud.EventCloud.EventEnvelope;
import com.ghatana.core.event.cloud.EventCloud.HistoryQuery;
import com.ghatana.core.event.cloud.EventCloud.HistoryScan;
import com.ghatana.core.event.cloud.EventCloud.Page;
import com.ghatana.core.event.cloud.EventCloud.Selection;
import com.ghatana.core.event.cloud.EventCloud.StartingPositions;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventStream;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.types.identity.PartitionId;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.config.ConfigManager;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for EventStoreService.
 *
 * <p>Validates event publishing, batch operations, and production-grade
 * functionality of the event store service.</p>
 *
 * @doc.type test
 * @doc.purpose Validate EventStoreService functionality
 * @doc.layer test
 * @doc.pattern UnitTest
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("EventStoreService Tests")
public class EventStoreServiceTest extends EventloopTestBase {

    private EventStoreService eventStoreService;
    private KernelContext context;
    private EventCloud eventCloud;

    @BeforeEach
    void setUp() {
        context = createTestContext();
        eventCloud = new InMemoryEventCloud();
        eventStoreService = new EventStoreService(context, eventCloud);
        eventStoreService.start();
    }

    // ==================== Event Publishing Tests ====================

    @Test
    @DisplayName("Should publish simple event")
    void shouldPublishSimpleEvent() {
        Map<String, Object> payload = Map.of("key", "value", "number", 42);

        Promise<Void> promise = eventStoreService.publish("test.topic", payload);
        runPromise(() -> promise);

        assertTrue(promise.isResult(), "Event should be published successfully");
    }

    @Test
    @DisplayName("Should publish event with tenant ID")
    void shouldPublishEventWithTenantId() {
        Map<String, Object> payload = Map.of("action", "test", "data", "sample");

        Promise<Void> promise = eventStoreService.publish("tenant.topic", "tenant-1", payload);
        runPromise(() -> promise);

        assertTrue(promise.isResult(), "Event with tenant should be published successfully");
    }

    @Test
    @DisplayName("Should publish structured event with full metadata")
    void shouldPublishStructuredEventWithFullMetadata() {
        Map<String, Object> data = Map.of("step", "initialization", "status", "success");

        Promise<Void> promise = eventStoreService.publish(
            "workflow.event",
            "tenant-1",
            "run-123",
            "WORKFLOW",
            "init",
            data,
            Instant.now()
        );
        runPromise(() -> promise);

        assertTrue(promise.isResult(), "Structured event should be published successfully");
    }

    @Test
    @DisplayName("Should fail to publish when service not started")
    void shouldFailToPublishWhenServiceNotStarted() {
        eventStoreService.stop();

        Map<String, Object> payload = Map.of("key", "value");
        Promise<Void> promise = eventStoreService.publish("test.topic", payload);

        assertTrue(promise.isException(), "Should fail when service not started");
    }

    // ==================== Batch Publishing Tests ====================

    @Test
    @DisplayName("Should publish batch of events")
    void shouldPublishBatchOfEvents() {
        List<EventStoreService.EventData> events = List.of(
            EventStoreService.EventData.of("topic.1", Map.of("data", "value1")),
            EventStoreService.EventData.of("topic.2", "tenant-1", Map.of("data", "value2")),
            EventStoreService.EventData.of("topic.3", Map.of("data", "value3"))
        );

        Promise<Void> promise = eventStoreService.publishBatch(events);
        runPromise(() -> promise);

        assertTrue(promise.isResult(), "Batch should be published successfully");
    }

    @Test
    @DisplayName("Should handle empty batch gracefully")
    void shouldHandleEmptyBatchGracefully() {
        List<EventStoreService.EventData> events = List.of();

        Promise<Void> promise = eventStoreService.publishBatch(events);
        runPromise(() -> promise);

        assertTrue(promise.isResult(), "Empty batch should complete successfully");
    }

    // ==================== Health Check Tests ====================

    @Test
    @DisplayName("Should be healthy when started")
    void shouldBeHealthyWhenStarted() {
        assertTrue(eventStoreService.isHealthy(), "Service should be healthy when started");
    }

    @Test
    @DisplayName("Should be unhealthy when stopped")
    void shouldBeUnhealthyWhenStopped() {
        eventStoreService.stop();

        assertFalse(eventStoreService.isHealthy(), "Service should be unhealthy when stopped");
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle null topic gracefully")
    void shouldHandleNullTopicGracefully() {
        assertThrows(NullPointerException.class, () ->
            eventStoreService.publish(null, Map.of("key", "value"))
        );
    }

    @Test
    @DisplayName("Should handle null payload gracefully")
    void shouldHandleNullPayloadGracefully() {
        assertThrows(NullPointerException.class, () ->
            EventStoreService.EventData.of("topic", null)
        );
    }

    @Test
    @DisplayName("Should handle large payload")
    void shouldHandleLargePayload() {
        // Create a large payload
        StringBuilder largeValue = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeValue.append("data");
        }
        Map<String, Object> payload = Map.of("largeData", largeValue.toString());

        Promise<Void> promise = eventStoreService.publish("large.payload", payload);
        runPromise(() -> promise);

        assertTrue(promise.isResult(), "Large payload should be published successfully");
    }

    @Test
    @DisplayName("Should handle special characters in payload")
    void shouldHandleSpecialCharactersInPayload() {
        Map<String, Object> payload = Map.of(
            "special", "value with \"quotes\" and {braces} and [brackets]",
            "unicode", "日本語テスト",
            "newline", "line1\nline2\tline3"
        );

        Promise<Void> promise = eventStoreService.publish("special.payload", payload);
        runPromise(() -> promise);

        assertTrue(promise.isResult(), "Special characters should be handled successfully");
    }

    // ==================== Private Helper Methods ====================

    private KernelContext createTestContext() {
        return new TestKernelContext();
    }

    /**
     * Simple in-memory EventCloud implementation for testing.
     */
    private static class InMemoryEventCloud implements EventCloud {
        private final java.util.List<EventRecord> events = new java.util.ArrayList<>();
        private final java.util.concurrent.atomic.AtomicLong offsetCounter = new java.util.concurrent.atomic.AtomicLong(0);

        @Override
        public Promise<AppendResult> append(AppendRequest request) {
            events.add(request.event());
            long off = offsetCounter.getAndIncrement();
            return Promise.of(new AppendResult(PartitionId.of("0"), Offset.of(String.valueOf(off)), java.time.Instant.now()));
        }

        @Override
        public Promise<java.util.List<AppendResult>> appendBatch(java.util.List<AppendRequest> requests) {
            java.util.List<AppendResult> results = new java.util.ArrayList<>();
            for (AppendRequest req : requests) {
                events.add(req.event());
                long off = offsetCounter.getAndIncrement();
                results.add(new AppendResult(PartitionId.of("0"), Offset.of(String.valueOf(off)), java.time.Instant.now()));
            }
            return Promise.of(results);
        }

        @Override
        public EventStream subscribe(TenantId tenant, Selection selection, StartingPositions start) {
            return new EventStream() {
                @Override public void request(long n) {}
                @Override public void onEvent(EventConsumer consumer) {}
                @Override public void pause() {}
                @Override public void resume() {}
                @Override public void close() {}
            };
        }

        @Override
        public Promise<Page> query(HistoryQuery query) {
            return Promise.of(new Page(java.util.List.of(), false, Offset.of("0")));
        }

        @Override
        public HistoryScan scan(HistoryQuery query) {
            return new HistoryScan() {
                @Override public void onBatch(java.util.function.Consumer<java.util.List<EventEnvelope>> consumer) {}
                @Override public void start() {}
                @Override public void pause() {}
                @Override public void resume() {}
                @Override public void close() {}
            };
        }

        public boolean isHealthy() {
            return true;
        }
    }

            /**
     * Test implementation of KernelContext for unit testing.
     */
    private static class TestKernelContext implements KernelContext {
        private final java.util.concurrent.ConcurrentHashMap<Class<?>, Object> services =
                new java.util.concurrent.ConcurrentHashMap<>();

        // ---- Dependency Lookup ----

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getDependency(Class<T> type) {
            T result = (T) services.get(type);
            if (result == null) throw new IllegalStateException("No service: " + type.getName());
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) {
            return java.util.Optional.ofNullable((T) services.get(type));
        }

        @Override
        public <T> boolean hasDependency(Class<T> type) {
            return services.containsKey(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getDependency(String name, Class<T> type) {
            return (T) services.get(type);
        }

        // ---- Event System ----

        @Override
        public <E> void registerEventHandler(Class<E> eventType,
                com.ghatana.kernel.event.EventHandler<E> handler) { /* no-op */ }

        @Override
        public <E> void unregisterEventHandler(Class<E> eventType,
                com.ghatana.kernel.event.EventHandler<E> handler) { /* no-op */ }

        @Override
        public <E> void publishEvent(E event) { /* no-op */ }

        // ---- Tenant & Runtime ----

        @Override
        public com.ghatana.kernel.context.KernelTenantContext getTenantContext() {
            return null;
        }

        @Override
        public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) {
            return null;
        }

        @Override
        public io.activej.eventloop.Eventloop getEventloop() {
            return io.activej.eventloop.Eventloop.create();
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() {
            return java.util.Set.of();
        }

        @Override
        public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) {
            return true;
        }

        @Override
        public <T> T getConfig(String key, Class<T> type) {
            return null;
        }

        @Override
        public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) {
            return java.util.Optional.empty();
        }

        @Override
        public String getKernelVersion() {
            return "test-1.0.0";
        }

        @Override
        public String getEnvironment() {
            return "test";
        }

        @Override
        public java.util.concurrent.Executor getExecutor(String executorName) {
            return java.util.concurrent.ForkJoinPool.commonPool();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> java.util.Optional<T> getCapability(String capabilityId) {
            return java.util.Optional.empty();
        }

        @Override
        public <T> void registerService(Class<T> type, T service) {
            services.put(type, service);
        }

        // ---- Test Helper (not part of interface) ----

        @SuppressWarnings("unchecked")
        public <T> T getService(Class<T> serviceClass) {
            return (T) services.get(serviceClass);
        }
    }
}
