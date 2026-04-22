/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("AuditLoggingTest [GH-90000]")
@Tag("governance [GH-90000]")
class AuditLoggingTest {

    private AuditLogger logger;
    private AuditStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new AuditStore(); // GH-90000
        logger = new AuditLogger(store); // GH-90000
    }

    // ── Event emission ────────────────────────────────────────────────────────

    @Test
    @DisplayName("data read event is recorded [GH-90000]")
    void dataReadEventIsRecorded() { // GH-90000
        logger.log(AuditEvent.read("tenant-1", "user-a", "record-42")); // GH-90000
        assertThat(store.getEvents("tenant-1 [GH-90000]")).hasSize(1);
    }

    @Test
    @DisplayName("data write event is recorded [GH-90000]")
    void dataWriteEventIsRecorded() { // GH-90000
        logger.log(AuditEvent.write("tenant-1", "user-a", "record-42")); // GH-90000
        assertThat(store.getEvents("tenant-1 [GH-90000]")).hasSize(1);
    }

    @Test
    @DisplayName("data delete event is recorded [GH-90000]")
    void dataDeleteEventIsRecorded() { // GH-90000
        logger.log(AuditEvent.delete("tenant-1", "admin-a", "record-42")); // GH-90000
        assertThat(store.getEvents("tenant-1 [GH-90000]")).hasSize(1);
    }

    @Test
    @DisplayName("policy change event is recorded [GH-90000]")
    void policyChangeEventIsRecorded() { // GH-90000
        logger.log(AuditEvent.policyChange("tenant-1", "admin-a", "retention-policy")); // GH-90000
        List<AuditEvent> events = store.getEvents("tenant-1 [GH-90000]");
        assertThat(events).hasSize(1); // GH-90000
        assertThat(events.get(0).action()).isEqualTo(AuditAction.POLICY_CHANGE); // GH-90000
    }

    // ── Required metadata ─────────────────────────────────────────────────────

    @Test
    @DisplayName("each event has a non-null timestamp [GH-90000]")
    void eventHasTimestamp() { // GH-90000
        logger.log(AuditEvent.read("tenant-1", "user-a", "r1")); // GH-90000
        AuditEvent event = store.getEvents("tenant-1 [GH-90000]").get(0);
        assertThat(event.timestamp()).isNotNull(); // GH-90000
        assertThat(event.timestamp()).isBefore(Instant.now().plusSeconds(1)); // GH-90000
    }

    @Test
    @DisplayName("each event has a unique ID [GH-90000]")
    void eventHasUniqueId() { // GH-90000
        logger.log(AuditEvent.read("tenant-1", "user-a", "r1")); // GH-90000
        logger.log(AuditEvent.read("tenant-1", "user-a", "r2")); // GH-90000
        List<AuditEvent> events = store.getEvents("tenant-1 [GH-90000]");
        assertThat(events).extracting(AuditEvent::eventId).doesNotHaveDuplicates(); // GH-90000
    }

    @Test
    @DisplayName("event captures the acting principal (userId) [GH-90000]")
    void eventCapturesPrincipal() { // GH-90000
        logger.log(AuditEvent.write("tenant-1", "alice", "r1")); // GH-90000
        AuditEvent event = store.getEvents("tenant-1 [GH-90000]").get(0);
        assertThat(event.actorId()).isEqualTo("alice [GH-90000]");
    }

    @Test
    @DisplayName("event captures the resource identifier [GH-90000]")
    void eventCapturesResource() { // GH-90000
        logger.log(AuditEvent.delete("tenant-1", "admin", "record-99")); // GH-90000
        AuditEvent event = store.getEvents("tenant-1 [GH-90000]").get(0);
        assertThat(event.resourceId()).isEqualTo("record-99 [GH-90000]");
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("events for different tenants are stored separately [GH-90000]")
    void eventsAreTenantIsolated() { // GH-90000
        logger.log(AuditEvent.read("tenant-a", "u1", "r1")); // GH-90000
        logger.log(AuditEvent.read("tenant-b", "u2", "r2")); // GH-90000
        assertThat(store.getEvents("tenant-a [GH-90000]")).hasSize(1);
        assertThat(store.getEvents("tenant-b [GH-90000]")).hasSize(1);
        assertThat(store.getEvents("tenant-a [GH-90000]").get(0).actorId()).isEqualTo("u1 [GH-90000]");
        assertThat(store.getEvents("tenant-b [GH-90000]").get(0).actorId()).isEqualTo("u2 [GH-90000]");
    }

    @Test
    @DisplayName("querying unknown tenant returns empty list [GH-90000]")
    void unknownTenantReturnsEmptyList() { // GH-90000
        assertThat(store.getEvents("ghost-tenant [GH-90000]")).isEmpty();
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("events are returned in chronological order [GH-90000]")
    void eventsAreChronological() { // GH-90000
        logger.log(AuditEvent.read("tenant-1", "u1", "r1")); // GH-90000
        logger.log(AuditEvent.write("tenant-1", "u1", "r2")); // GH-90000
        logger.log(AuditEvent.delete("tenant-1", "admin", "r3")); // GH-90000
        List<AuditEvent> events = store.getEvents("tenant-1 [GH-90000]");
        for (int i = 1; i < events.size(); i++) { // GH-90000
            boolean ordered = !events.get(i).timestamp().isBefore(events.get(i - 1).timestamp()); // GH-90000
            assertThat(ordered).isTrue(); // GH-90000
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("events can be filtered by action type [GH-90000]")
    void filterByAction() { // GH-90000
        logger.log(AuditEvent.read("tenant-1", "u1", "r1")); // GH-90000
        logger.log(AuditEvent.write("tenant-1", "u1", "r2")); // GH-90000
        logger.log(AuditEvent.read("tenant-1", "u2", "r3")); // GH-90000

        List<AuditEvent> reads = store.getEventsByAction("tenant-1", AuditAction.READ); // GH-90000
        assertThat(reads).hasSize(2); // GH-90000
        assertThat(reads).extracting(AuditEvent::action).containsOnly(AuditAction.READ); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum AuditAction { READ, WRITE, DELETE, POLICY_CHANGE }

    record AuditEvent( // GH-90000
            String eventId,
            Instant timestamp,
            String tenantId,
            String actorId,
            String resourceId,
            AuditAction action
    ) {
        static AuditEvent read(String tenant, String actor, String resource) { // GH-90000
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.READ); // GH-90000
        }

        static AuditEvent write(String tenant, String actor, String resource) { // GH-90000
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.WRITE); // GH-90000
        }

        static AuditEvent delete(String tenant, String actor, String resource) { // GH-90000
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.DELETE); // GH-90000
        }

        static AuditEvent policyChange(String tenant, String actor, String resource) { // GH-90000
            return new AuditEvent(UUID.randomUUID().toString(), Instant.now(), tenant, actor, resource, AuditAction.POLICY_CHANGE); // GH-90000
        }
    }

    static class AuditStore {
        private final Map<String, List<AuditEvent>> tenantEvents = new HashMap<>(); // GH-90000

        void append(AuditEvent event) { // GH-90000
            tenantEvents.computeIfAbsent(event.tenantId(), k -> new ArrayList<>()).add(event); // GH-90000
        }

        List<AuditEvent> getEvents(String tenantId) { // GH-90000
            return Collections.unmodifiableList(tenantEvents.getOrDefault(tenantId, List.of())); // GH-90000
        }

        List<AuditEvent> getEventsByAction(String tenantId, AuditAction action) { // GH-90000
            return getEvents(tenantId).stream() // GH-90000
                    .filter(e -> e.action() == action) // GH-90000
                    .toList(); // GH-90000
        }
    }

    static class AuditLogger {
        private final AuditStore store;

        AuditLogger(AuditStore store) { // GH-90000
            this.store = store;
        }

        void log(AuditEvent event) { // GH-90000
            store.append(event); // GH-90000
        }
    }
}
