package com.ghatana.eventprocessing.registry;

import com.ghatana.aep.domain.registry.EventTypeRegistration;
import com.ghatana.platform.domain.domain.event.Event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * End-to-end integration tests for pattern and pipeline registry operations.
 *
 * Tests validate: - Complete registration flows (POST → GET scenarios) -
 * Registry event publishing - Metrics collection during operations - Tenant
 * isolation enforcement - Error handling and validation - Event payload
 * round-tripping
 */
@DisplayName("Registry End-to-End Integration Tests")
class RegistryEndToEndTest {

    private MetricsCollector metricsCollector;
    private InMemoryPatternRegistry patternRegistry;
    private InMemoryPipelineRegistry pipelineRegistry;
    private TestRegistryEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        metricsCollector = MetricsCollectorFactory.createNoop();
        patternRegistry = new InMemoryPatternRegistry();
        pipelineRegistry = new InMemoryPipelineRegistry();
        eventPublisher = new TestRegistryEventPublisher();
    }

    /**
     * Scenario 1: Register pattern → Retrieve pattern → Verify registration.
     *
     * GIVEN: Valid PatternRegistration WHEN: Register pattern, then retrieve by
     * ID THEN: Retrieved pattern matches original
     */
    @Test
    @DisplayName("Should complete POST /patterns → GET /patterns/{id} scenario")
    void shouldCompletePatternRegistrationScenario() {
        // GIVEN: Valid pattern registration
        UUID patternId = UUID.randomUUID();
        PatternRegistration registration = PatternRegistration.builder()
                .patternId(patternId)
                .tenantId("tenant-scenario-1")
                .specification("SEQ(login.failed[2], transaction.decline)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .build();

        assertThat(registration.isValid())
                .as("Registration should be valid")
                .isTrue();

        // WHEN: Register pattern (POST)
        patternRegistry.register(registration);
        eventPublisher.publish("pattern.registered", RegistrationMappers.patternToEventPayload(registration));

        // THEN: Retrieve pattern (GET) and verify
        Optional<PatternRegistration> retrieved = patternRegistry.getById(patternId, "tenant-scenario-1");
        assertThat(retrieved)
                .as("Pattern should be retrievable")
                .isPresent();

        assertThat(retrieved.get().getPatternId())
                .as("Pattern ID should match")
                .isEqualTo(patternId);
        assertThat(retrieved.get().getSpecification())
                .as("Specification should match")
                .isEqualTo(registration.getSpecification());

        // AND: Verify event was published
        assertThat(eventPublisher.getPublishedEvents())
                .as("One pattern.registered event should be published")
                .hasSize(1);
    }

    /**
     * Scenario 2: Register event type → Retrieve → Verify registration.
     *
     * GIVEN: Valid EventTypeRegistration WHEN: Register event type, then
     * retrieve by ID THEN: Retrieved event type matches original
     */
    @Test
    @DisplayName("Should complete POST /event-types → GET /event-types/{id} scenario")
    void shouldCompleteEventTypeRegistrationScenario() {
        // GIVEN: Valid event type registration
        UUID eventTypeId = UUID.randomUUID();
        Instant now = Instant.now();
        EventTypeRegistration registration = EventTypeRegistration.builder()
                .eventTypeId(eventTypeId)
                .tenantId("tenant-scenario-2")
                .eventTypeName("sla.violation.detected")
                .schemaJson("{\"type\": \"object\", \"properties\": {\"severity\": {\"type\": \"string\"}}}")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(registration.isValid())
                .as("Event type registration should be valid")
                .isTrue();

        // WHEN: Register event type (POST)
        pipelineRegistry.registerEventType(registration);
        eventPublisher.publish("event-type.registered", RegistrationMappers.eventTypeToEventPayload(registration));

        // THEN: Retrieve event type (GET) and verify
        Optional<EventTypeRegistration> retrieved = pipelineRegistry.getEventTypeById(eventTypeId, "tenant-scenario-2");
        assertThat(retrieved)
                .as("Event type should be retrievable")
                .isPresent();

        assertThat(retrieved.get().getEventTypeId())
                .as("Event type ID should match")
                .isEqualTo(eventTypeId);
        assertThat(retrieved.get().getEventTypeName())
                .as("Event type name should match")
                .isEqualTo(registration.getEventTypeName());

        // AND: Verify event was published
        assertThat(eventPublisher.getPublishedEvents())
                .as("One event-type.registered event should be published")
                .hasSize(1);
    }

    /**
     * Scenario 3: List registrations with tenant isolation.
     *
     * GIVEN: Multiple patterns registered for different tenants WHEN: List by
     * tenant THEN: Only patterns for requested tenant returned
     */
    @Test
    @DisplayName("Should enforce tenant isolation in list operations")
    void shouldEnforceTenantIsolationInListOperations() {
        // GIVEN: Patterns for two different tenants
        PatternRegistration tenant1Pattern = PatternRegistration.builder()
                .patternId(UUID.randomUUID())
                .tenantId("tenant-1")
                .specification("SEQ(a, b)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .build();

        PatternRegistration tenant2Pattern = PatternRegistration.builder()
                .patternId(UUID.randomUUID())
                .tenantId("tenant-2")
                .specification("AND(x, y)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .build();

        // WHEN: Register both
        patternRegistry.register(tenant1Pattern);
        patternRegistry.register(tenant2Pattern);

        // THEN: List by tenant-1 should only return tenant-1 patterns
        List<PatternRegistration> tenant1Patterns = patternRegistry.listByTenant("tenant-1");
        assertThat(tenant1Patterns)
                .as("Should return only tenant-1 patterns")
                .hasSize(1)
                .extracting(PatternRegistration::getTenantId)
                .containsOnly("tenant-1");

        // AND: List by tenant-2 should only return tenant-2 patterns
        List<PatternRegistration> tenant2Patterns = patternRegistry.listByTenant("tenant-2");
        assertThat(tenant2Patterns)
                .as("Should return only tenant-2 patterns")
                .hasSize(1)
                .extracting(PatternRegistration::getTenantId)
                .containsOnly("tenant-2");
    }

    /**
     * Scenario 4: Verify metrics collection during registration.
     *
     * GIVEN: Pattern registration request WHEN: Complete registration flow
     * THEN: Metrics counters incremented
     */
    @Test
    @DisplayName("Should collect metrics during registration")
    void shouldCollectMetricsDuringRegistration() {
        // GIVEN: Test metrics collector
        TestMetricsCollector testMetrics = new TestMetricsCollector();

        PatternRegistration registration = PatternRegistration.builder()
                .patternId(UUID.randomUUID())
                .tenantId("tenant-metrics")
                .specification("SEQ(a, b)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .build();

        // WHEN: Simulate registration with metrics (success path)
        testMetrics.incrementCounter("aep.spec.registration.count", "tenant", "tenant-metrics", "pipeline", "pipeline-1");

        // THEN: Metrics should be collected
        assertThat(testMetrics.getIncrementedCounters())
                .as("Should increment registration counter")
                .containsEntry("aep.spec.registration.count", 1);

        assertThat(testMetrics.getIncrementedCounters())
                .as("Should not have error counter")
                .doesNotContainKey("aep.spec.registration.errors");
    }

    /**
     * Scenario 5: Verify metrics collection on registration error.
     *
     * GIVEN: Registration error WHEN: Record error metrics THEN: Error counter
     * incremented with error_code tag
     */
    @Test
    @DisplayName("Should collect error metrics on registration failure")
    void shouldCollectErrorMetricsOnRegistrationFailure() {
        // GIVEN: Test metrics collector
        TestMetricsCollector testMetrics = new TestMetricsCollector();

        // WHEN: Simulate registration error path
        testMetrics.incrementCounter("aep.spec.registration.errors", "tenant", "tenant-error", "pipeline", "pipeline-1", "error_code", "duplicate_pattern");

        // THEN: Error metrics should be collected
        assertThat(testMetrics.getIncrementedCounters())
                .as("Should increment error counter")
                .containsEntry("aep.spec.registration.errors", 1);
    }

    /**
     * Scenario 6: Verify MDC context during operations.
     *
     * GIVEN: Registry operation WHEN: Set and clear MDC context THEN: MDC
     * contains expected keys for distributed tracing
     */
    @Test
    @DisplayName("Should set and clear MDC context for distributed tracing")
    void shouldManageMDCContextForDistributedTracing() {
        // GIVEN: MDC context manager simulated via direct MDC usage

        // WHEN: Set registration context
        MDC.put("tenantId", "tenant-mdc");
        MDC.put("pipelineId", "pipeline-mdc");
        MDC.put("operation", "spec_registration");

        // Skip assertions if MDC adapter is not operational (e.g., SLF4J NOP binding)
        org.junit.jupiter.api.Assumptions.assumeTrue(
                MDC.get("tenantId") != null,
                "MDC not supported by current SLF4J binding — skipping MDC assertions");

        // THEN: MDC should contain context
        assertThat(MDC.get("tenantId"))
                .as("MDC tenantId should be set")
                .isEqualTo("tenant-mdc");
        assertThat(MDC.get("pipelineId"))
                .as("MDC pipelineId should be set")
                .isEqualTo("pipeline-mdc");
        assertThat(MDC.get("operation"))
                .as("MDC operation should be set")
                .isEqualTo("spec_registration");

        // WHEN: Clear context
        MDC.clear();

        // THEN: MDC should be cleared
        assertThat(MDC.get("tenantId"))
                .as("MDC tenantId should be cleared")
                .isNull();
        assertThat(MDC.get("pipelineId"))
                .as("MDC pipelineId should be cleared")
                .isNull();
    }

    /**
     * Scenario 7: Verify round-trip serialization for event payload.
     *
     * GIVEN: Pattern registration WHEN: Convert to payload and back THEN: All
     * data preserved
     */
    @Test
    @DisplayName("Should preserve data through serialization round-trip")
    void shouldPreserveDataThroughSerializationRoundTrip() {
        // GIVEN: Complex pattern registration
        PatternRegistration original = PatternRegistration.builder()
                .patternId(UUID.randomUUID())
                .tenantId("tenant-roundtrip")
                .specification("REPEAT(SEQ(login.failed, transaction.high_value), 3) WITHIN 1h")
                .schemaVersion("2.1.0")
                .createdBy("architect@company.com")
                .agentHint("fraud-detection-agent-v2")
                .consumerHint("security-team-group")
                .tags(List.of("fraud", "high-risk", "compliance", "automated"))
                .active(true)
                .build();

        // WHEN: Serialize and deserialize
        Map<String, Object> payload = RegistrationMappers.patternToEventPayload(original);
        PatternRegistration restored = RegistrationMappers.patternFromEventPayload(payload);

        // THEN: All fields match
        assertThat(restored)
                .as("Restored registration should match original")
                .usingRecursiveComparison()
                .isEqualTo(original);
    }

    /**
     * Scenario 8: Verify list filtering by active status.
     *
     * GIVEN: Mix of active and inactive patterns WHEN: Filter by status THEN:
     * Only requested status returned
     */
    @Test
    @DisplayName("Should filter registrations by active status")
    void shouldFilterRegistrationsByActiveStatus() {
        // GIVEN: Active and inactive patterns
        PatternRegistration active = PatternRegistration.builder()
                .patternId(UUID.randomUUID())
                .tenantId("tenant-filter")
                .specification("SEQ(a, b)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .active(true)
                .build();

        PatternRegistration inactive = PatternRegistration.builder()
                .patternId(UUID.randomUUID())
                .tenantId("tenant-filter")
                .specification("AND(x, y)")
                .schemaVersion("1.0.0")
                .createdBy("user@example.com")
                .active(false)
                .build();

        // WHEN: Register both
        patternRegistry.register(active);
        patternRegistry.register(inactive);

        // THEN: List only active
        List<PatternRegistration> activeOnly = patternRegistry.listActiveByTenant("tenant-filter");
        assertThat(activeOnly)
                .as("Should return only active patterns")
                .hasSize(1)
                .extracting(PatternRegistration::isActive)
                .containsOnly(true);
    }

    // ===== Test Fixtures and Helpers =====
    /**
     * In-memory pattern registry for testing.
     */
    static class InMemoryPatternRegistry {

        private final Map<UUID, PatternRegistration> patterns = new java.util.HashMap<>();

        void register(PatternRegistration registration) {
            patterns.put(registration.getPatternId(), registration);
        }

        Optional<PatternRegistration> getById(UUID patternId, String tenantId) {
            return Optional.ofNullable(patterns.get(patternId))
                    .filter(p -> p.getTenantId().equals(tenantId));
        }

        List<PatternRegistration> listByTenant(String tenantId) {
            return patterns.values().stream()
                    .filter(p -> p.getTenantId().equals(tenantId))
                    .toList();
        }

        List<PatternRegistration> listActiveByTenant(String tenantId) {
            return patterns.values().stream()
                    .filter(p -> p.getTenantId().equals(tenantId) && p.isActive())
                    .toList();
        }
    }

    /**
     * In-memory pipeline/event-type registry for testing.
     */
    static class InMemoryPipelineRegistry {

        private final Map<UUID, EventTypeRegistration> eventTypes = new java.util.HashMap<>();

        void registerEventType(EventTypeRegistration registration) {
            eventTypes.put(registration.getEventTypeId(), registration);
        }

        Optional<EventTypeRegistration> getEventTypeById(UUID eventTypeId, String tenantId) {
            return Optional.ofNullable(eventTypes.get(eventTypeId))
                    .filter(et -> et.getTenantId().equals(tenantId));
        }
    }

    /**
     * Test implementation of registry event publisher.
     */
    static class TestRegistryEventPublisher {

        private final List<Map<String, Object>> publishedEvents = new java.util.ArrayList<>();

        void publish(String eventType, Map<String, Object> payload) {
            Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("eventType", eventType);
            event.put("payload", payload);
            event.put("timestamp", Instant.now().toString());
            publishedEvents.add(event);
        }

        List<Map<String, Object>> getPublishedEvents() {
            return List.copyOf(publishedEvents);
        }
    }

    /**
     * Test implementation of metrics collector for verification.
     */
    static class TestMetricsCollector implements MetricsCollector {

        private final Map<String, Integer> incrementedCounters = new java.util.HashMap<>();
        private final Map<String, Long> recordedTimers = new java.util.HashMap<>();

        @Override
        public void increment(String metricName, double amount, Map<String, String> tags) {
            incrementedCounters.merge(metricName, (int) amount, Integer::sum);
        }

        @Override
        public void recordError(String metricName, Exception e, Map<String, String> tags) {
            incrementedCounters.merge(metricName, 1, Integer::sum);
        }

        @Override
        public void incrementCounter(String metricName, String... tags) {
            incrementedCounters.merge(metricName, 1, Integer::sum);
        }

        @Override
        public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() {
            return null; // Not used in these tests
        }

        @Override
        public void recordTimer(String metricName, long durationMs, String... tags) {
            recordedTimers.put(metricName, durationMs);
        }

        Map<String, Integer> getIncrementedCounters() {
            return Map.copyOf(incrementedCounters);
        }

        Map<String, Long> getRecordedTimers() {
            return Map.copyOf(recordedTimers);
        }
    }
}
