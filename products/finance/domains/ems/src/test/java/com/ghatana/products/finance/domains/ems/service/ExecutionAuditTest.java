package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for audit trail generation per D02-007
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Execution Audit Tests")
class ExecutionAuditTest {

    private AuditTrailService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditTrailService();
    }

    @Test
    @DisplayName("Should create audit entry for order routing")
    void shouldCreateAuditEntryForOrderRouting() {
        AuditEntry entry = auditService.logRouting(
            "order-1",
            "routing-1",
            "NASDAQ",
            "user-1",
            Instant.now()
        );

        assertThat(entry.eventType()).isEqualTo("ORDER_ROUTED");
        assertThat(entry.orderId()).isEqualTo("order-1");
        assertThat(entry.venue()).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Should create audit entry for fill")
    void shouldCreateAuditEntryForFill() {
        AuditEntry entry = auditService.logFill(
            "order-1",
            "fill-1",
            100L,
            BigDecimal.valueOf(150.50),
            "user-1",
            Instant.now()
        );

        assertThat(entry.eventType()).isEqualTo("FILL_RECEIVED");
        assertThat(entry.quantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should create audit entry for cancellation")
    void shouldCreateAuditEntryForCancellation() {
        AuditEntry entry = auditService.logCancellation(
            "order-1",
            "routing-1",
            "User requested",
            "user-1",
            Instant.now()
        );

        assertThat(entry.eventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(entry.reason()).isEqualTo("User requested");
    }

    @Test
    @DisplayName("Should create audit entry for rejection")
    void shouldCreateAuditEntryForRejection() {
        AuditEntry entry = auditService.logRejection(
            "order-1",
            "routing-1",
            "Insufficient funds",
            "system",
            Instant.now()
        );

        assertThat(entry.eventType()).isEqualTo("ORDER_REJECTED");
        assertThat(entry.reason()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("Should maintain audit trail chronology")
    void shouldMaintainAuditTrailChronology() {
        Instant now = Instant.now();

        auditService.logRouting("order-1", "routing-1", "NASDAQ", "user-1", now);
        auditService.logFill("order-1", "fill-1", 50L, BigDecimal.valueOf(150.50), "user-1", now.plusSeconds(10));
        auditService.logFill("order-1", "fill-2", 50L, BigDecimal.valueOf(150.51), "user-1", now.plusSeconds(20));

        List<AuditEntry> trail = auditService.getAuditTrail("order-1");

        assertThat(trail).hasSize(3);
        assertThat(trail.get(0).timestamp()).isBefore(trail.get(1).timestamp());
        assertThat(trail.get(1).timestamp()).isBefore(trail.get(2).timestamp());
    }

    @Test
    @DisplayName("Should include user context in audit entries")
    void shouldIncludeUserContextInAuditEntries() {
        AuditEntry entry = auditService.logRouting(
            "order-1",
            "routing-1",
            "NASDAQ",
            "trader-123",
            Instant.now()
        );

        assertThat(entry.userId()).isEqualTo("trader-123");
    }

    @Test
    @DisplayName("Should track state transitions")
    void shouldTrackStateTransitions() {
        StateTransition transition = auditService.logStateTransition(
            "order-1",
            ExecutionStatus.ROUTED,
            ExecutionStatus.PARTIALLY_FILLED,
            "user-1",
            Instant.now()
        );

        assertThat(transition.fromState()).isEqualTo(ExecutionStatus.ROUTED);
        assertThat(transition.toState()).isEqualTo(ExecutionStatus.PARTIALLY_FILLED);
    }

    @Test
    @DisplayName("Should support audit trail filtering by event type")
    void shouldSupportAuditTrailFilteringByEventType() {
        auditService.logRouting("order-1", "routing-1", "NASDAQ", "user-1", Instant.now());
        auditService.logFill("order-1", "fill-1", 50L, BigDecimal.valueOf(150.50), "user-1", Instant.now());
        auditService.logFill("order-1", "fill-2", 50L, BigDecimal.valueOf(150.51), "user-1", Instant.now());

        List<AuditEntry> fills = auditService.getAuditTrailByType("order-1", "FILL_RECEIVED");

        assertThat(fills).hasSize(2);
        assertThat(fills).allMatch(e -> e.eventType().equals("FILL_RECEIVED"));
    }

    @Test
    @DisplayName("Should generate audit report")
    void shouldGenerateAuditReport() {
        auditService.logRouting("order-1", "routing-1", "NASDAQ", "user-1", Instant.now());
        auditService.logFill("order-1", "fill-1", 100L, BigDecimal.valueOf(150.50), "user-1", Instant.now());

        AuditReport report = auditService.generateReport("order-1");

        assertThat(report.orderId()).isEqualTo("order-1");
        assertThat(report.eventCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should support audit trail export")
    void shouldSupportAuditTrailExport() {
        auditService.logRouting("order-1", "routing-1", "NASDAQ", "user-1", Instant.now());
        auditService.logFill("order-1", "fill-1", 100L, BigDecimal.valueOf(150.50), "user-1", Instant.now());

        String export = auditService.exportAuditTrail("order-1", "JSON");

        assertThat(export).isNotEmpty();
        assertThat(export).contains("order-1");
    }

    record AuditEntry(
        String entryId,
        String eventType,
        String orderId,
        String userId,
        Instant timestamp,
        String venue,
        Long quantity,
        BigDecimal price,
        String reason
    ) {
        AuditEntry(String eventType, String orderId, String userId, Instant timestamp) {
            this(UUID.randomUUID().toString(), eventType, orderId, userId, timestamp, null, null, null, null);
        }
    }

    record StateTransition(
        String orderId,
        ExecutionStatus fromState,
        ExecutionStatus toState,
        String userId,
        Instant timestamp
    ) {}

    record AuditReport(String orderId, int eventCount, Instant generatedAt) {}

    static class AuditTrailService {
        private final List<AuditEntry> entries = new java.util.ArrayList<>();

        AuditEntry logRouting(String orderId, String routingId, String venue, String userId, Instant timestamp) {
            AuditEntry entry = new AuditEntry(
                UUID.randomUUID().toString(),
                "ORDER_ROUTED",
                orderId,
                userId,
                timestamp,
                venue,
                null,
                null,
                null
            );
            entries.add(entry);
            return entry;
        }

        AuditEntry logFill(String orderId, String fillId, long quantity, BigDecimal price, String userId, Instant timestamp) {
            AuditEntry entry = new AuditEntry(
                UUID.randomUUID().toString(),
                "FILL_RECEIVED",
                orderId,
                userId,
                timestamp,
                null,
                quantity,
                price,
                null
            );
            entries.add(entry);
            return entry;
        }

        AuditEntry logCancellation(String orderId, String routingId, String reason, String userId, Instant timestamp) {
            AuditEntry entry = new AuditEntry(
                UUID.randomUUID().toString(),
                "ORDER_CANCELLED",
                orderId,
                userId,
                timestamp,
                null,
                null,
                null,
                reason
            );
            entries.add(entry);
            return entry;
        }

        AuditEntry logRejection(String orderId, String routingId, String reason, String userId, Instant timestamp) {
            AuditEntry entry = new AuditEntry(
                UUID.randomUUID().toString(),
                "ORDER_REJECTED",
                orderId,
                userId,
                timestamp,
                null,
                null,
                null,
                reason
            );
            entries.add(entry);
            return entry;
        }

        StateTransition logStateTransition(String orderId, ExecutionStatus fromState, ExecutionStatus toState,
                                          String userId, Instant timestamp) {
            return new StateTransition(orderId, fromState, toState, userId, timestamp);
        }

        List<AuditEntry> getAuditTrail(String orderId) {
            return entries.stream()
                .filter(e -> e.orderId().equals(orderId))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
        }

        List<AuditEntry> getAuditTrailByType(String orderId, String eventType) {
            return entries.stream()
                .filter(e -> e.orderId().equals(orderId) && e.eventType().equals(eventType))
                .toList();
        }

        AuditReport generateReport(String orderId) {
            long count = entries.stream().filter(e -> e.orderId().equals(orderId)).count();
            return new AuditReport(orderId, (int) count, Instant.now());
        }

        String exportAuditTrail(String orderId, String format) {
            List<AuditEntry> trail = getAuditTrail(orderId);
            return "{\"orderId\":\"" + orderId + "\",\"events\":" + trail.size() + "}";
        }
    }
}
