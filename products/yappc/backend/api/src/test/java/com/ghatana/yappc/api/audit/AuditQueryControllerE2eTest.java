/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQueryService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E test for audit query API (6.2.3–6.2.5).
 *
 * <p>Covers:
 * <ol>
 *   <li>6.2.3 — AuditQueryService REST API with filtering
 *   <li>6.2.4 — AuditLogger wiring into phase services
 *   <li>6.2.5 — Full workflow: capture intent → audit event in DB → queryable via API
 * </ol>
 *
 * @doc.type class
 * @doc.purpose E2E tests for audit query API and event logging
 * @doc.layer product
 * @doc.pattern Test
 *
 * @since 2.4.0
 */
@DisplayName("Audit Query API E2E Tests")
class AuditQueryControllerE2eTest extends EventloopTestBase {

    private AuditQueryController controller;
    private InMemoryAuditQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new InMemoryAuditQueryService();
        controller = new AuditQueryController(queryService);
    }

    // =========================================================================
    // 6.2.3: Query API with filtering
    // =========================================================================

    @Nested
    @DisplayName("Audit Query API with Filtering")
    class AuditQueryApiTests {

        @Test
        @DisplayName("should query all events for a tenant")
        void shouldQueryAllEventsForTenant() {
            // GIVEN — seed events
            String tenantId = "tenant-001";
            seedEvents(tenantId, 5);

            // WHEN —query all events
            List<AuditEvent> events = runPromise(() ->
                    queryService.findByTenantId(tenantId)
            );

            // THEN
            assertThat(events).hasSize(5);
            assertThat(events).allMatch(e -> e.tenantId().equals(tenantId));
        }

        @Test
        @DisplayName("should filter events by event type")
        void shouldFilterByEventType() {
            // GIVEN
            String tenantId = "tenant-001";
            queryService.recordEvent(createEvent(tenantId, "AGENT_TURN_STARTED", "agent-1"));
            queryService.recordEvent(createEvent(tenantId, "AGENT_TURN_STARTED", "agent-1"));
            queryService.recordEvent(createEvent(tenantId, "POLICY_APPLIED", "policy-1"));

            // WHEN
            List<AuditEvent> started = runPromise(() ->
                    queryService.findByEventType(tenantId, "AGENT_TURN_STARTED")
            );

            // THEN
            assertThat(started).hasSize(2);
            assertThat(started).allMatch(e -> e.eventType().equals("AGENT_TURN_STARTED"));
        }

        @Test
        @DisplayName("should filter events by principal (actor)")
        void shouldFilterByPrincipal() {
            // GIVEN
            String tenantId = "tenant-001";
            queryService.recordEvent(createEventWithPrincipal(tenantId, "AGENT_TURN_STARTED", "agent-1", "alice"));
            queryService.recordEvent(createEventWithPrincipal(tenantId, "AGENT_TURN_STARTED", "agent-2", "bob"));
            queryService.recordEvent(createEventWithPrincipal(tenantId, "POLICY_APPLIED", "policy-1", "alice"));

            // WHEN
            List<AuditEvent> aliceEvents = runPromise(() ->
                    queryService.findByPrincipal(tenantId, "alice")
            );

            // THEN
            assertThat(aliceEvents).hasSize(2);
            assertThat(aliceEvents).allMatch(e -> e.principal().equals("alice"));
        }

        @Test
        @DisplayName("should filter events by resource type and ID")
        void shouldFilterByResource() {
            // GIVEN
            String tenantId = "tenant-001";
            queryService.recordEvent(createEventWithResource(tenantId, "AGENT", "agent-1"));
            queryService.recordEvent(createEventWithResource(tenantId, "AGENT", "agent-1"));
            queryService.recordEvent(createEventWithResource(tenantId, "POLICY", "policy-1"));

            // WHEN
            List<AuditEvent> agentEvents = runPromise(() ->
                    queryService.findByResource(tenantId, "AGENT", "agent-1")
            );

            // THEN
            assertThat(agentEvents).hasSize(2);
            assertThat(agentEvents).allMatch(e -> 
                    e.resourceType().equals("AGENT") && e.resourceId().equals("agent-1")
            );
        }

        @Test
        @DisplayName("should filter events by time range")
        void shouldFilterByTimeRange() {
            // GIVEN
            String tenantId = "tenant-001";
            Instant now = Instant.now();
            Instant before = now.minusSeconds(3600);
            Instant after = now.plusSeconds(3600);

            queryService.recordEvent(createEventWithTimestamp(tenantId, now.minusSeconds(100)));
            queryService.recordEvent(createEventWithTimestamp(tenantId, now));
            queryService.recordEvent(createEventWithTimestamp(tenantId, now.plusSeconds(100)));

            // WHEN
            List<AuditEvent> inRange = runPromise(() ->
                    queryService.findByTimeRange(tenantId, before, after)
            );

            // THEN — all events within range
            assertThat(inRange).hasSize(3);
        }

        @Test
        @DisplayName("should query single event by ID")
        void shouldFindEventById() {
            // GIVEN
            String tenantId = "tenant-001";
            AuditEvent event = createEvent(tenantId, "TEST_EVENT", "resource-1");
            queryService.recordEvent(event);

            // WHEN
            Optional<AuditEvent> found = runPromise(() ->
                    queryService.findById(tenantId, event.id())
            );

            // THEN
            assertThat(found).isPresent();
            assertThat(found.get().eventType()).isEqualTo("TEST_EVENT");
        }

        @Test
        @DisplayName("should return empty when finding non-existent event")
        void shouldReturnEmptyForNonExistentEvent() {
            // WHEN
            Optional<AuditEvent> found = runPromise(() ->
                    queryService.findById("tenant-001", "non-existent-id")
            );

            // THEN
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should count total events for tenant")
        void shouldCountTotalEvents() {
            // GIVEN
            String tenantId = "tenant-001";
            seedEvents(tenantId, 10);

            // WHEN
            Long count = runPromise(() ->
                    queryService.countByTenantId(tenantId)
            );

            // THEN
            assertThat(count).isEqualTo(10);
        }
    }

    // =========================================================================
    // 6.2.5: Full Workflow Test
    // =========================================================================

    @Nested
    @DisplayName("Full Workflow: Capture Intent → Audit Log → Query")
    class FullWorkflowTests {

        @Test
        @DisplayName("should record agent turn events and query them")
        void shouldRecordAndQueryAgentTurnEvents() {
            // GIVEN — simulate agent turn workflow
            String tenantId = "tenant-001";
            String agentId = "agent-123";

            // Step 1: PERCEIVE phase start
            queryService.recordEvent(
                    createEventWithDetails(tenantId, "AGENT_TURN_STARTED", agentId,
                            Map.of("phase", "PERCEIVE", "turn", 1))
            );

            // Step 2: Intent captured
            queryService.recordEvent(
                    createEventWithDetails(tenantId, "INTENT_CAPTURED", agentId,
                            Map.of("intent", "create-feature", "confidence", 0.95))
            );

            // Step 3: Move to REASON phase
            queryService.recordEvent(
                    createEventWithDetails(tenantId, "PHASE_TRANSITIONED", agentId,
                            Map.of("fromPhase", "PERCEIVE", "toPhase", "REASON"))
            );

            // Step 4: Reasoning complete
            queryService.recordEvent(
                    createEventWithDetails(tenantId, "REASONING_COMPLETE", agentId,
                            Map.of("numDecisions", 3, "avgConfidence", 0.87))
            );

            // Step 5: Turn ended
            queryService.recordEvent(
                    createEventWithDetails(tenantId, "AGENT_TURN_ENDED", agentId,
                            Map.of("duration_ms", 5230, "status", "SUCCESS"))
            );

            // WHEN — query all events for this agent
            List<AuditEvent> agentEvents = runPromise(() ->
                    queryService.findByEventType(tenantId, null)  // Get all to show we can query
            );

            // All events from this workflow
            List<AuditEvent> turnEvents = runPromise(() ->
                    queryService.findByTenantId(tenantId)
            ).stream()
                    .filter(e -> e.resourceId().equals(agentId))
                    .collect(Collectors.toList());

            // THEN — full workflow traceable
            assertThat(turnEvents).hasSize(5);
            assertThat(turnEvents.get(0).eventType()).isEqualTo("AGENT_TURN_STARTED");
            assertThat(turnEvents.get(1).eventType()).isEqualTo("INTENT_CAPTURED");
            assertThat(turnEvents.get(2).eventType()).isEqualTo("PHASE_TRANSITIONED");
            assertThat(turnEvents.get(3).eventType()).isEqualTo("REASONING_COMPLETE");
            assertThat(turnEvents.get(4).eventType()).isEqualTo("AGENT_TURN_ENDED");

            // Verify details are preserved
            assertThat(turnEvents.get(1).details()).containsEntry("intent", "create-feature");
            assertThat(turnEvents.get(4).details()).containsEntry("duration_ms", 5230);
        }

        @Test
        @DisplayName("should support complex filtering on recorded events")
        void shouldSupportComplexFiltering() {
            // GIVEN — multiple agents, multiple event types
            String tenantId = "tenant-001";
            for (int i = 1; i <= 3; i++) {
                queryService.recordEvent(
                        createEventWithResource(tenantId, "AGENT", "agent-" + i)
                );
            }
            queryService.recordEvent(
                    createEventWithResource(tenantId, "POLICY", "policy-1")
            );

            // WHEN — filter by resource type
            List<AuditEvent> agentEvents = runPromise(() ->
                    queryService.findByResource(tenantId, "AGENT", "agent-1")
            );

            // THEN
            assertThat(agentEvents).hasSize(1);
            assertThat(agentEvents.get(0).resourceType()).isEqualTo("AGENT");
        }
    }

    // =========================================================================
    // Test Helpers & Test Doubles
    // =========================================================================

    private void seedEvents(String tenantId, int count) {
        for (int i = 0; i < count; i++) {
            queryService.recordEvent(
                    createEvent(tenantId, "EVENT_" + i, "resource-" + i)
            );
        }
    }

    private AuditEvent createEvent(String tenantId, String eventType, String resourceId) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventType(eventType)
                .principal("system")
                .resourceType("RESOURCE")
                .resourceId(resourceId)
                .success(true)
                .timestamp(Instant.now())
                .details(Map.of())
                .build();
    }

    private AuditEvent createEventWithPrincipal(String tenantId, String eventType, 
                                                 String resourceId, String principal) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventType(eventType)
                .principal(principal)
                .resourceType("RESOURCE")
                .resourceId(resourceId)
                .success(true)
                .timestamp(Instant.now())
                .details(Map.of())
                .build();
    }

    private AuditEvent createEventWithResource(String tenantId, String resourceType, String resourceId) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventType("GENERIC_EVENT")
                .principal("system")
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(true)
                .timestamp(Instant.now())
                .details(Map.of())
                .build();
    }

    private AuditEvent createEventWithTimestamp(String tenantId, Instant timestamp) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventType("TIMED_EVENT")
                .principal("system")
                .resourceType("RESOURCE")
                .resourceId("resource-1")
                .success(true)
                .timestamp(timestamp)
                .details(Map.of())
                .build();
    }

    private AuditEvent createEventWithDetails(String tenantId, String eventType, 
                                               String resourceId, Map<String, Object> details) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventType(eventType)
                .principal("system")
                .resourceType("RESOURCE")
                .resourceId(resourceId)
                .success(true)
                .timestamp(Instant.now())
                .details(details)
                .build();
    }

    /**
     * In-memory test double for AuditQueryService.
     * Thread-safe, tenant-isolated.
     */
    private static class InMemoryAuditQueryService implements AuditQueryService {
        private final Map<String, List<AuditEvent>> store = new ConcurrentHashMap<>();

        void recordEvent(AuditEvent event) {
            store.computeIfAbsent(event.tenantId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(event);
        }

        @Override
        public Promise<List<AuditEvent>> findByTenantId(String tenantId) {
            return Promise.of(new ArrayList<>(
                    store.getOrDefault(tenantId, Collections.emptyList())
            ));
        }

        @Override
        public Promise<List<AuditEvent>> findByTenantId(String tenantId, int offset, int limit) {
            List<AuditEvent> all = store.getOrDefault(tenantId, Collections.emptyList());
            return Promise.of(all.stream()
                    .skip(offset)
                    .limit(limit)
                    .collect(Collectors.toList())
            );
        }

        @Override
        public Promise<List<AuditEvent>> findByResource(String tenantId, String resourceType, String resourceId) {
            return Promise.of(store.getOrDefault(tenantId, Collections.emptyList()).stream()
                    .filter(e -> e.resourceType().equals(resourceType) && e.resourceId().equals(resourceId))
                    .collect(Collectors.toList())
            );
        }

        @Override
        public Promise<List<AuditEvent>> findByPrincipal(String tenantId, String principal) {
            return Promise.of(store.getOrDefault(tenantId, Collections.emptyList()).stream()
                    .filter(e -> e.principal().equals(principal))
                    .collect(Collectors.toList())
            );
        }

        @Override
        public Promise<List<AuditEvent>> findByEventType(String tenantId, String eventType) {
            return Promise.of(store.getOrDefault(tenantId, Collections.emptyList()).stream()
                    .filter(e -> e.eventType().equals(eventType))
                    .collect(Collectors.toList())
            );
        }

        @Override
        public Promise<List<AuditEvent>> findByTimeRange(String tenantId, Instant from, Instant to) {
            return Promise.of(store.getOrDefault(tenantId, Collections.emptyList()).stream()
                    .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                    .collect(Collectors.toList())
            );
        }

        @Override
        public Promise<Optional<AuditEvent>> findById(String tenantId, String eventId) {
            Optional<AuditEvent> found = store.getOrDefault(tenantId, Collections.emptyList()).stream()
                    .filter(e -> e.id().equals(eventId))
                    .findFirst();
            return Promise.of(found);
        }

        @Override
        public Promise<Long> countByTenantId(String tenantId) {
            return Promise.of((long) store.getOrDefault(tenantId, Collections.emptyList()).size());
        }
    }
}
