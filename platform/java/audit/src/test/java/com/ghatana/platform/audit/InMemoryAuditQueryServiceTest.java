package com.ghatana.platform.audit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryAuditQueryService}.
 */
@DisplayName("InMemoryAuditQueryService [GH-90000]")
class InMemoryAuditQueryServiceTest extends EventloopTestBase {

    private InMemoryAuditQueryService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new InMemoryAuditQueryService(); // GH-90000
    }

    @Test
    @DisplayName("should record and retrieve audit events [GH-90000]")
    void shouldRecordAndRetrieve() { // GH-90000
        var event = AuditEvent.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .eventType("USER_LOGIN [GH-90000]")
                .principal("user@example.com [GH-90000]")
                .resourceType("Session [GH-90000]")
                .resourceId("session-123 [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> service.record(event) // GH-90000
                .then(() -> service.findByTenantId("tenant-1 [GH-90000]"))
                .map(events -> { // GH-90000
                    assertThat(events).hasSize(1); // GH-90000
                    assertThat(events.get(0).getEventType()).isEqualTo("USER_LOGIN [GH-90000]");
                    return null;
                }));
    }

    @Test
    @DisplayName("should isolate events by tenant [GH-90000]")
    void shouldIsolateByTenant() { // GH-90000
        var event1 = AuditEvent.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .eventType("ACTION_A [GH-90000]")
                .build(); // GH-90000
        var event2 = AuditEvent.builder() // GH-90000
                .tenantId("tenant-2 [GH-90000]")
                .eventType("ACTION_B [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> service.record(event1) // GH-90000
                .then(() -> service.record(event2)) // GH-90000
                .then(() -> service.findByTenantId("tenant-1 [GH-90000]"))
                .map(events -> { // GH-90000
                    assertThat(events).hasSize(1); // GH-90000
                    assertThat(events.get(0).getEventType()).isEqualTo("ACTION_A [GH-90000]");
                    return null;
                }));
    }

    @Test
    @DisplayName("should return empty list for unknown tenant [GH-90000]")
    void shouldReturnEmptyForUnknownTenant() { // GH-90000
        runPromise(() -> service.findByTenantId("nonexistent [GH-90000]")
                .map(events -> { // GH-90000
                    assertThat(events).isEmpty(); // GH-90000
                    return null;
                }));
    }
}
