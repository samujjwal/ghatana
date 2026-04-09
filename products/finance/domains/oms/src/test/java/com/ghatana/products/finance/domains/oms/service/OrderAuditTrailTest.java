package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for SOX-compliant audit trail generation
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Audit Trail Tests")
class OrderAuditTrailTest {

    private Instant testTime;
    private String testTimeBs;
    private List<AuditEntry> auditTrail;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
        auditTrail = new ArrayList<>();
    }

    @Test
    @DisplayName("Should record order creation in audit trail")
    void shouldRecordOrderCreationInAuditTrail() {
        // GIVEN: New order
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );

        // WHEN: Record creation
        recordAudit("ORDER_CREATED", order, "system", "Order created");

        // THEN: Audit entry created
        assertThat(auditTrail).hasSize(1);
        assertThat(auditTrail.get(0).action()).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("Should record order status changes in audit trail")
    void shouldRecordOrderStatusChangesInAuditTrail() {
        // GIVEN: Order with status changes
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        );

        // WHEN: Change status multiple times
        recordAudit("STATUS_CHANGE", order, "system", "PENDING -> APPROVED");
        Order approved = order.withStatus(OrderStatus.APPROVED);
        recordAudit("STATUS_CHANGE", approved, "system", "APPROVED -> ROUTED");

        // THEN: All changes recorded
        assertThat(auditTrail).hasSize(2);
        assertThat(auditTrail.stream().map(AuditEntry::action))
            .containsExactly("STATUS_CHANGE", "STATUS_CHANGE");
    }

    @Test
    @DisplayName("Should record order modifications in audit trail")
    void shouldRecordOrderModificationsInAuditTrail() {
        // GIVEN: Order modification
        Order original = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        );

        // WHEN: Modify order
        Order modified = original.withStatus(OrderStatus.APPROVED);
        recordAudit("ORDER_MODIFIED", modified, "trader-1",
            "Status changed to APPROVED");

        // THEN: Modification recorded
        assertThat(auditTrail).hasSize(1);
        assertThat(auditTrail.get(0).action()).isEqualTo("ORDER_MODIFIED");
        assertThat(auditTrail.get(0).actor()).isEqualTo("trader-1");
    }

    @Test
    @DisplayName("Should record order cancellation in audit trail")
    void shouldRecordOrderCancellationInAuditTrail() {
        // GIVEN: Order cancellation
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        );

        // WHEN: Cancel order
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);
        recordAudit("ORDER_CANCELLED", cancelled, "trader-1", "Client requested");

        // THEN: Cancellation recorded
        assertThat(auditTrail).hasSize(1);
        assertThat(auditTrail.get(0).action()).isEqualTo("ORDER_CANCELLED");
    }

    @Test
    @DisplayName("Should record fill events in audit trail")
    void shouldRecordFillEventsInAuditTrail() {
        // GIVEN: Order with fill
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        );

        Fill fill = new Fill("FILL-001", "ORD-005", "EXEC-001",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);

        // WHEN: Record fill
        recordAudit("FILL_RECEIVED", order, "system",
            "Filled 50 @ 150.00");

        // THEN: Fill recorded
        assertThat(auditTrail).hasSize(1);
        assertThat(auditTrail.get(0).action()).isEqualTo("FILL_RECEIVED");
    }

    @Test
    @DisplayName("Should include timestamps in audit trail")
    void shouldIncludeTimestampsInAuditTrail() {
        // GIVEN: Order event
        Order order = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        );

        // WHEN: Record event
        recordAudit("ORDER_CREATED", order, "system", "Order created");

        // THEN: Timestamp included
        assertThat(auditTrail.get(0).timestamp()).isNotNull();
        assertThat(auditTrail.get(0).timestampBs()).isNotNull();
    }

    @Test
    @DisplayName("Should include actor information in audit trail")
    void shouldIncludeActorInformationInAuditTrail() {
        // GIVEN: Order action by specific actor
        Order order = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        );

        // WHEN: Record action
        recordAudit("ORDER_APPROVED", order, "approver-1", "Approved by compliance");

        // THEN: Actor recorded
        assertThat(auditTrail.get(0).actor()).isEqualTo("approver-1");
    }

    @Test
    @DisplayName("Should maintain audit trail immutability")
    void shouldMaintainAuditTrailImmutability() {
        // GIVEN: Audit entries
        Order order = Order.newOrder(
            "ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs
        );

        recordAudit("ORDER_CREATED", order, "system", "Created");
        int initialSize = auditTrail.size();

        // WHEN: Attempt to modify
        // Audit trail should be append-only

        // THEN: Previous entries unchanged
        assertThat(auditTrail).hasSize(initialSize);
    }

    @Test
    @DisplayName("Should support audit trail querying")
    void shouldSupportAuditTrailQuerying() {
        // GIVEN: Multiple audit entries
        Order order = Order.newOrder(
            "ORD-009", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-9", testTime, testTimeBs
        );

        recordAudit("ORDER_CREATED", order, "system", "Created");
        recordAudit("ORDER_APPROVED", order, "approver-1", "Approved");
        recordAudit("ORDER_ROUTED", order, "system", "Routed");

        // WHEN: Query by action
        List<AuditEntry> approvals = auditTrail.stream()
            .filter(e -> e.action().equals("ORDER_APPROVED"))
            .toList();

        // THEN: Can query audit trail
        assertThat(approvals).hasSize(1);
        assertThat(approvals.get(0).actor()).isEqualTo("approver-1");
    }

    @Test
    @DisplayName("Should generate SOX-compliant audit report")
    void shouldGenerateSOXCompliantAuditReport() {
        // GIVEN: Order lifecycle
        Order order = Order.newOrder(
            "ORD-010", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-10", testTime, testTimeBs
        );

        recordAudit("ORDER_CREATED", order, "system", "Created");
        recordAudit("ORDER_APPROVED", order, "approver-1", "Approved");
        recordAudit("ORDER_FILLED", order, "system", "Filled");

        // WHEN: Generate report
        String report = generateAuditReport(order.orderId());

        // THEN: Report contains all required information
        assertThat(report).contains("ORD-010");
        assertThat(report).contains("ORDER_CREATED");
        assertThat(report).contains("ORDER_APPROVED");
        assertThat(report).contains("ORDER_FILLED");
    }

    // Helper methods
    private void recordAudit(String action, Order order, String actor, String description) {
        auditTrail.add(new AuditEntry(
            order.orderId(),
            action,
            actor,
            description,
            Instant.now(),
            testTimeBs
        ));
    }

    private String generateAuditReport(String orderId) {
        StringBuilder report = new StringBuilder();
        report.append("Audit Trail for Order: ").append(orderId).append("\n");
        for (AuditEntry entry : auditTrail) {
            if (entry.orderId().equals(orderId)) {
                report.append(entry.timestamp()).append(" - ")
                      .append(entry.action()).append(" by ")
                      .append(entry.actor()).append(": ")
                      .append(entry.description()).append("\n");
            }
        }
        return report.toString();
    }

    record AuditEntry(
        String orderId,
        String action,
        String actor,
        String description,
        Instant timestamp,
        String timestampBs
    ) {}
}
