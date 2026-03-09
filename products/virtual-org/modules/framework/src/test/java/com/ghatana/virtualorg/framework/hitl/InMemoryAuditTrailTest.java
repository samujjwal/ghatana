package com.ghatana.virtualorg.framework.hitl;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InMemoryAuditTrail.
 *
 * @doc.type class
 * @doc.purpose Unit tests for audit trail
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryAuditTrail Tests")
class InMemoryAuditTrailTest extends EventloopTestBase {

    private InMemoryAuditTrail auditTrail;

    @BeforeEach
    void setUp() {
        auditTrail = new InMemoryAuditTrail(1000);
    }

    @Test
    @DisplayName("should record tool execution")
    void shouldRecordToolExecution() {
        // WHEN
        AuditEntry entry = runPromise(() -> auditTrail.recordToolExecution(
                "agent-1",
                "github.create_pr",
                Map.of("title", "Fix bug"),
                Map.of("pr_number", 123)
        ));

        // THEN
        assertThat(entry).isNotNull();
        assertThat(entry.eventType()).isEqualTo(AuditEntry.EventTypes.TOOL_EXECUTION);
        assertThat(entry.agentId()).isEqualTo("agent-1");
    }

    @Test
    @DisplayName("should record decision")
    void shouldRecordDecision() {
        // WHEN
        AuditEntry entry = runPromise(() -> auditTrail.recordDecision(
                "agent-1",
                "Approve PR",
                "Code looks good, tests pass",
                0.95
        ));

        // THEN
        assertThat(entry).isNotNull();
        assertThat(entry.eventType()).isEqualTo(AuditEntry.EventTypes.DECISION);
        assertThat((Double) entry.data().get("confidence")).isEqualTo(0.95);
    }

    @Test
    @DisplayName("should record approval")
    void shouldRecordApproval() {
        // WHEN
        AuditEntry entry = runPromise(() -> auditTrail.recordApproval(
                "agent-1",
                "req-123",
                "deploy-to-prod",
                true,
                "john@example.com",
                "Approved for release"
        ));

        // THEN
        assertThat(entry).isNotNull();
        assertThat(entry.eventType()).isEqualTo(AuditEntry.EventTypes.APPROVAL);
        assertThat((Boolean) entry.data().get("approved")).isTrue();
    }

    @Test
    @DisplayName("should record state change")
    void shouldRecordStateChange() {
        // WHEN
        AuditEntry entry = runPromise(() -> auditTrail.recordStateChange(
                "agent-1",
                "IDLE",
                "RUNNING",
                "Task started"
        ));

        // THEN
        assertThat(entry).isNotNull();
        assertThat(entry.eventType()).isEqualTo(AuditEntry.EventTypes.STATE_CHANGE);
        assertThat(entry.data().get("old_state")).isEqualTo("IDLE");
        assertThat(entry.data().get("new_state")).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("should record error")
    void shouldRecordError() {
        // WHEN
        AuditEntry entry = runPromise(() -> auditTrail.recordError(
                "agent-1",
                "ToolExecutionError",
                "Connection refused",
                Map.of("retry_count", 3)
        ));

        // THEN
        assertThat(entry).isNotNull();
        assertThat(entry.eventType()).isEqualTo(AuditEntry.EventTypes.ERROR);
        assertThat(entry.data().get("error_type")).isEqualTo("ToolExecutionError");
    }

    @Test
    @DisplayName("should get entries for agent")
    void shouldGetEntriesForAgent() {
        // GIVEN
        runPromise(() -> auditTrail.recordEvent("agent-1", "custom", Map.of()));
        runPromise(() -> auditTrail.recordEvent("agent-2", "custom", Map.of()));
        runPromise(() -> auditTrail.recordEvent("agent-1", "custom", Map.of()));

        // WHEN
        List<AuditEntry> entries = runPromise(()
                -> auditTrail.getEntriesForAgent("agent-1"));

        // THEN
        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(e -> e.agentId().equals("agent-1"));
    }

    @Test
    @DisplayName("should get entries by type")
    void shouldGetEntriesByType() {
        // GIVEN
        runPromise(() -> auditTrail.recordDecision("agent-1", "d1", "r1", 0.8));
        runPromise(() -> auditTrail.recordError("agent-1", "e1", "m1", Map.of()));
        runPromise(() -> auditTrail.recordDecision("agent-1", "d2", "r2", 0.9));

        // WHEN
        List<AuditEntry> entries = runPromise(()
                -> auditTrail.getEntriesByType(AuditEntry.EventTypes.DECISION));

        // THEN
        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(e
                -> e.eventType().equals(AuditEntry.EventTypes.DECISION));
    }

    @Test
    @DisplayName("should query with time range")
    void shouldQueryWithTimeRange() {
        // GIVEN
        Instant now = Instant.now();
        runPromise(() -> auditTrail.recordEvent("agent-1", "custom", Map.of()));

        // WHEN
        AuditQuery query = AuditQuery.builder()
                .timeRange(now.minusSeconds(60), now.plusSeconds(60))
                .build();

        List<AuditEntry> entries = runPromise(() -> auditTrail.query(query));

        // THEN
        assertThat(entries).hasSize(1);
    }

    @Test
    @DisplayName("should query with multiple filters")
    void shouldQueryWithMultipleFilters() {
        // GIVEN
        runPromise(() -> auditTrail.recordDecision("agent-1", "d1", "r1", 0.8));
        runPromise(() -> auditTrail.recordError("agent-1", "e1", "m1", Map.of()));
        runPromise(() -> auditTrail.recordDecision("agent-2", "d2", "r2", 0.9));

        // WHEN
        AuditQuery query = AuditQuery.builder()
                .agentId("agent-1")
                .eventTypes(Set.of(AuditEntry.EventTypes.DECISION))
                .build();

        List<AuditEntry> entries = runPromise(() -> auditTrail.query(query));

        // THEN
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).agentId()).isEqualTo("agent-1");
        assertThat(entries.get(0).eventType()).isEqualTo(AuditEntry.EventTypes.DECISION);
    }

    @Test
    @DisplayName("should sort by timestamp descending")
    void shouldSortByTimestampDescending() throws InterruptedException {
        // GIVEN
        runPromise(() -> auditTrail.recordEvent("agent-1", "first", Map.of()));
        Thread.sleep(10); // Small delay to ensure different timestamps
        runPromise(() -> auditTrail.recordEvent("agent-1", "second", Map.of()));

        // WHEN
        AuditQuery query = AuditQuery.builder()
                .agentId("agent-1")
                .sortOrder(AuditQuery.SortOrder.DESCENDING)
                .build();

        List<AuditEntry> entries = runPromise(() -> auditTrail.query(query));

        // THEN
        assertThat(entries.get(0).eventType()).isEqualTo("second");
        assertThat(entries.get(1).eventType()).isEqualTo("first");
    }

    @Test
    @DisplayName("should respect limit and offset")
    void shouldRespectLimitAndOffset() {
        // GIVEN
        for (int i = 0; i < 10; i++) {
            runPromise(() -> auditTrail.recordEvent("agent-1", "event", Map.of()));
        }

        // WHEN
        AuditQuery query = AuditQuery.builder()
                .agentId("agent-1")
                .limit(3)
                .offset(2)
                .build();

        List<AuditEntry> entries = runPromise(() -> auditTrail.query(query));

        // THEN
        assertThat(entries).hasSize(3);
    }

    @Test
    @DisplayName("should evict old entries when at capacity")
    void shouldEvictOldEntries() {
        // GIVEN - small capacity audit trail
        InMemoryAuditTrail small = new InMemoryAuditTrail(10);

        // WHEN - add more than capacity
        for (int i = 0; i < 15; i++) {
            runPromise(() -> small.recordEvent("agent-1", "event", Map.of()));
        }

        // THEN - should not exceed capacity
        assertThat(small.size()).isLessThanOrEqualTo(10);
    }
}
