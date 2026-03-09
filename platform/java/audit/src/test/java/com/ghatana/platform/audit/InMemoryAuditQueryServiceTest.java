package com.ghatana.platform.audit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryAuditQueryService}.
 */
@DisplayName("InMemoryAuditQueryService")
class InMemoryAuditQueryServiceTest extends EventloopTestBase {

    private InMemoryAuditQueryService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryAuditQueryService();
    }

    @Test
    @DisplayName("should record and retrieve audit events")
    void shouldRecordAndRetrieve() {
        var event = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("USER_LOGIN")
                .principal("user@example.com")
                .resourceType("Session")
                .resourceId("session-123")
                .build();

        runPromise(() -> service.record(event)
                .then(() -> service.findByTenantId("tenant-1"))
                .map(events -> {
                    assertThat(events).hasSize(1);
                    assertThat(events.get(0).getEventType()).isEqualTo("USER_LOGIN");
                    return null;
                }));
    }

    @Test
    @DisplayName("should isolate events by tenant")
    void shouldIsolateByTenant() {
        var event1 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("ACTION_A")
                .build();
        var event2 = AuditEvent.builder()
                .tenantId("tenant-2")
                .eventType("ACTION_B")
                .build();

        runPromise(() -> service.record(event1)
                .then(() -> service.record(event2))
                .then(() -> service.findByTenantId("tenant-1"))
                .map(events -> {
                    assertThat(events).hasSize(1);
                    assertThat(events.get(0).getEventType()).isEqualTo("ACTION_A");
                    return null;
                }));
    }

    @Test
    @DisplayName("should return empty list for unknown tenant")
    void shouldReturnEmptyForUnknownTenant() {
        runPromise(() -> service.findByTenantId("nonexistent")
                .map(events -> {
                    assertThat(events).isEmpty();
                    return null;
                }));
    }
}
