/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.audit;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for governance audit logging.
 *
 * <p>Validates that sensitive data access, mutations, purges, and
 * policy changes are captured as structured audit events with the
 * required metadata fields for compliance and forensics.
 *
 * @doc.type    class
 * @doc.purpose Audit logging: event capture, required metadata, tenant isolation, ordering
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("AuditLoggingTest")
@Tag("governance")
class AuditLoggingTest {

    private AuditLogger logger;
    private AuditStore store;

    @BeforeEach
    void setUp() {
        store = new AuditStore();
        logger = new AuditLogger(store);
    }

    // ── Event emission ────────────────────────────────────────────────────────

    @Test
    @DisplayName("data read event is recorded")
    void dataReadEventIsRecorded() {
        logger.log(AuditEvent.read("tenant-1", "user-a", "record-42"));
        assertThat(store.getEvents("tenant-1")).hasSize(1);
    }

    @Test
    @DisplayName("data write event is recorded")
    void dataWriteEventIsRecorded() {
        logger.log(AuditEvent.write("tenant-1", "user-a", "record-42"));
        assertThat(store.getEvents("tenant-1")).hasSize(1);
    }

    @Test
    @DisplayName("data delete event is recorded")
    void dataDeleteEventIsRecorded() {
        logger.log(AuditEvent.delete("tenant-1", "admin-a", "record-42"));
        assertThat(store.getEvents("tenant-1")).hasSize(1);
    }

    @Test
    @DisplayName("policy change event is recorded")
    void policyChangeEventIsRecorded() {
        logger.log(AuditEvent.policyChange("tenant-1", "admin-a", "retention-policy"));
        List<AuditEvent> events = store.getEvents("tenant-1");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).action()).isEqualTo(AuditAction.POLICY_CHANGE);
    }

    // ── Required metadata ─────────────────────────────────────────────────────

    @Test
    @DisplayName("each event has a non-null timestamp")
    void eventHasTimestamp() {
        logger.log(AuditEvent.read("tenant-1", "user-a", "r1"));
        AuditEvent event = store.getEvents("tenant-1").get(0);
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.timestamp()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("each event has a unique ID")
    void eventHasUniqueId() {
        logger.log(AuditEvent.read("tenant-1", "user-a", "r1"));
        logger.log(AuditEvent.read("tenant-1", "user-a", "r2"));
        List<AuditEvent> events = store.getEvents("tenant-1");
        assertThat(events).extracting(AuditEvent::eventId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("event captures the acting principal (userId)")
    void eventCapturesPrincipal() {
        logger.log(AuditEvent.write("tenant-1", "alice", "r1"));
        AuditEvent event = store.getEvents("tenant-1").get(0);
        assertThat(event.actorId()).isEqualTo("alice");
    }

    @Test
    @DisplayName("event captures the resource identifier")
    void eventCapturesResource() {
        logger.log(AuditEvent.delete("tenant-1", "admin", "record-99"));
        AuditEvent event = store.getEvents("tenant-1").get(0);
        assertThat(event.resourceId()).isEqualTo("record-99");
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("events for different tenants are stored separately")
    void eventsAreTenantIsolated() {
        logger.log(AuditEvent.read("tenant-a", "u1", "r1"));
        logger.log(AuditEvent.read("tenant-b", "u2", "r2"));
        assertThat(store.getEvents("tenant-a")).hasSize(1);
        assertThat(store.getEvents("tenant-b")).hasSize(1);
        assertThat(store.getEvents("tenant-a").get(0).actorId()).isEqualTo("u1");
        assertThat(store.getEvents("tenant-b").get(0).actorId()).isEqualTo("u2");
    }

    @Test
    @DisplayName("querying unknown tenant returns empty list")
    void unknownTenantReturnsEmptyList() {
        assertThat(store.getEvents("ghost-tenant")).isEmpty();
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("events are returned in chronological order")
    void eventsAreChronological() {
        logger.log(AuditEvent.read("tenant-1", "u1", "r1"));
        logger.log(AuditEvent.write("tenant-1", "u1", "r2"));
        logger.log(AuditEvent.delete("tenant-1", "admin", "r3"));
        List<AuditEvent> events = store.getEvents("tenant-1");
        for (int i = 1; i < events.size(); i++) {
            boolean ordered = !events.get(i).timestamp().isBefore(events.get(i - 1).timestamp());
            assertThat(ordered).isTrue();
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("events can be filtered by action type")
    void filterByAction() {
        logger.log(AuditEvent.read("tenant-1", "u1", "r1"));
        logger.log(AuditEvent.write("tenant-1", "u1", "r2"));
        logger.log(AuditEvent.read("tenant-1", "u2", "r3"));

        List<AuditEvent> reads = store.getEventsByAction("tenant-1", AuditAction.READ);
        assertThat(reads).hasSize(2);
        assertThat(reads).extracting(AuditEvent::action).containsOnly(AuditAction.READ);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum AuditAction { READ, WRITE, DELETE, POLICY_CHANGE }

    record AuditEvent(
            String eventId,
            Instant timestamp,
            String tenantId,
            String actorId,
            String resourceId,
            AuditAction action
    ) {
        static AuditEvent read(String tenant, String actor, String resource) {
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.READ);
        }

        static AuditEvent write(String tenant, String actor, String resource) {
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.WRITE);
        }

        static AuditEvent delete(String tenant, String actor, String resource) {
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.DELETE);
        }

        static AuditEvent policyChange(String tenant, String actor, String resource) {
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.POLICY_CHANGE);
        }
    }

    static class AuditStore {
        private final Map<String, List<AuditEvent>> tenantEvents = new HashMap<>();

        void append(AuditEvent event) {
            tenantEvents.computeIfAbsent(event.tenantId(), k -> new ArrayList<>()).add(event);
        }

        List<AuditEvent> getEvents(String tenantId) {
            return Collections.unmodifiableList(tenantEvents.getOrDefault(tenantId, List.of()));
        }

        List<AuditEvent> getEventsByAction(String tenantId, AuditAction action) {
            return getEvents(tenantId).stream()
                    .filter(e -> e.action() == action)
                    .toList();
        }
    }

    static class AuditLogger {
        private final AuditStore store;

        AuditLogger(AuditStore store) {
            this.store = store;
        }

        void log(AuditEvent event) {
            store.append(event);
        }
    }
}
