/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.eventstore.service;

import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.test.EventloopTestBase;
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
        await(promise);

        assertTrue(promise.isResult(), "Event should be published successfully");
    }

    @Test
    @DisplayName("Should publish event with tenant ID")
    void shouldPublishEventWithTenantId() {
        Map<String, Object> payload = Map.of("action", "test", "data", "sample");

        Promise<Void> promise = eventStoreService.publish("tenant.topic", "tenant-1", payload);
        await(promise);

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
        await(promise);

        assertTrue(promise.isResult(), "Structured event should be published successfully");
    }

    @Test
    @DisplayName("Should fail to publish when service not started")
    void shouldFailToPublishWhenServiceNotStarted() {
        eventStoreService.stop();

        Map<String, Object> payload = Map.of("key", "value");
        Promise<Void> promise = eventStoreService.publish("test.topic", payload);
        await(promise);

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
        await(promise);

        assertTrue(promise.isResult(), "Batch should be published successfully");
    }

    @Test
    @DisplayName("Should handle empty batch gracefully")
    void shouldHandleEmptyBatchGracefully() {
        List<EventStoreService.EventData> events = List.of();

        Promise<Void> promise = eventStoreService.publishBatch(events);
        await(promise);

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
        await(promise);

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
        await(promise);

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

        @Override
        public Promise<AppendResult> append(AppendRequest request) {
            events.add(request.event());
            return Promise.of(new AppendResult() {
                @Override
                public String partitionId() { return "0"; }
                @Override
                public long offset() { return events.size() - 1; }
            });
        }

        @Override
        public Promise<List<AppendResult>> appendBatch(List<AppendRequest> requests) {
            List<AppendResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                events.add(requests.get(i).event());
                final int offset = events.size() - 1;
                results.add(() -> offset);
            }
            return Promise.of(results);
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }

    /**
     * Test implementation of KernelContext.
     */
    private static class TestKernelContext implements KernelContext {
        @Override
        public String getKernelId() {
            return "test-kernel";
        }

        @Override
        public String getTenantId() {
            return "test-tenant";
        }

        @Override
        public <T> void registerService(Class<T> serviceClass, T service) {
            // No-op for testing
        }

        @Override
        public <T> T getService(Class<T> serviceClass) {
            return null;
        }

        @Override
        public ConfigManager getConfig() {
            return ConfigManager.createDefault("test");
        }

        @Override
        public java.util.concurrent.Executor getExecutor(String name) {
            return java.util.concurrent.Executors.newSingleThreadExecutor();
        }

        @Override
        public boolean hasCapability(String capabilityId) {
            return true;
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() {
            return java.util.Set.of();
        }
    }
}
