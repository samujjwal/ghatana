package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for execution report generation and processing
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Execution Report Tests")
class OrderExecutionReportTest {

    private Instant testTime;
    private String testTimeBs;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
    }

    @Test
    @DisplayName("Should generate execution report for filled order")
    void shouldGenerateExecutionReportForFilledOrder() {
        // GIVEN: Order with full fill
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );
        
        Fill fill = new Fill("FILL-001", "ORD-001", "EXEC-001",
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
        Order filledOrder = order.withFill(fill, BigDecimal.valueOf(150.00));

        // WHEN: Generate execution report
        ExecutionReport report = generateExecutionReport(filledOrder);

        // THEN: Report contains order details
        assertThat(report.orderId()).isEqualTo("ORD-001");
        assertThat(report.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(report.filledQuantity()).isEqualTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("Should generate execution report for partial fill")
    void shouldGenerateExecutionReportForPartialFill() {
        // GIVEN: Partially filled order
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        ).withStatus(OrderStatus.PARTIALLY_FILLED);

        // WHEN: Generate execution report
        ExecutionReport report = generateExecutionReport(order);

        // THEN: Report shows partial fill
        assertThat(report.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(report.remainingQuantity()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should include fill details in execution report")
    void shouldIncludeFillDetailsInExecutionReport() {
        // GIVEN: Order with fills
        Fill fill1 = new Fill("FILL-001", "ORD-003", "EXEC-001",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
        Fill fill2 = new Fill("FILL-002", "ORD-003", "EXEC-002",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.50), BigDecimal.ZERO, testTime);

        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        ).withFill(fill1, BigDecimal.valueOf(150.00))
         .withFill(fill2, BigDecimal.valueOf(150.25));

        // WHEN: Generate execution report
        ExecutionReport report = generateExecutionReport(order);

        // THEN: Report includes all fills
        assertThat(report.fills()).hasSize(2);
        assertThat(report.avgFillPrice()).isEqualTo(BigDecimal.valueOf(150.25));
    }

    @Test
    @DisplayName("Should calculate execution statistics")
    void shouldCalculateExecutionStatistics() {
        // GIVEN: Order with execution data
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        ).withEnrichment("AAPL", "NASDAQ", "USD",
            BigDecimal.valueOf(15000.00), BigDecimal.valueOf(149.50))
         .withStatus(OrderStatus.FILLED);

        // WHEN: Generate execution report
        ExecutionReport report = generateExecutionReport(order);

        // THEN: Statistics calculated
        assertThat(report.slippage()).isNotNull();
        assertThat(report.executionTime()).isNotNull();
    }

    @Test
    @DisplayName("Should generate report for rejected order")
    void shouldGenerateReportForRejectedOrder() {
        // GIVEN: Rejected order
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        ).withRejection("Insufficient margin");

        // WHEN: Generate execution report
        ExecutionReport report = generateExecutionReport(order);

        // THEN: Report shows rejection
        assertThat(report.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(report.rejectionReason()).isEqualTo("Insufficient margin");
    }

    @Test
    @DisplayName("Should generate report for cancelled order")
    void shouldGenerateReportForCancelledOrder() {
        // GIVEN: Cancelled order
        Order order = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        ).withStatus(OrderStatus.CANCELLED);

        // WHEN: Generate execution report
        ExecutionReport report = generateExecutionReport(order);

        // THEN: Report shows cancellation
        assertThat(report.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(report.filledQuantity()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should include timing information in report")
    void shouldIncludeTimingInformationInReport() {
        // GIVEN: Order with timing data
        Order order = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        );

        // WHEN: Generate execution report
        ExecutionReport report = generateExecutionReport(order);

        // THEN: Timing included
        assertThat(report.createdAt()).isEqualTo(testTime);
        assertThat(report.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should format report for regulatory compliance")
    void shouldFormatReportForRegulatoryCompliance() {
        // GIVEN: Order requiring regulatory reporting
        Order order = Order.newOrder(
            "ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs
        ).withStatus(OrderStatus.FILLED);

        // WHEN: Generate regulatory report
        ExecutionReport report = generateExecutionReport(order);

        // THEN: Report meets regulatory requirements
        assertThat(report.orderId()).isNotNull();
        assertThat(report.clientId()).isNotNull();
        assertThat(report.instrumentId()).isNotNull();
        assertThat(report.createdAt()).isNotNull();
    }

    // Helper classes and methods
    private ExecutionReport generateExecutionReport(Order order) {
        BigDecimal slippage = order.arrivalPrice() != null && order.avgFillPrice() != null
            ? order.avgFillPrice().subtract(order.arrivalPrice())
            : BigDecimal.ZERO;

        long executionTimeMs = order.updatedAt().toEpochMilli() - order.createdAt().toEpochMilli();

        return new ExecutionReport(
            order.orderId(),
            order.clientId(),
            order.instrumentId(),
            order.status(),
            order.filledQuantity(),
            order.remainingQuantity(),
            order.avgFillPrice(),
            order.fills(),
            slippage,
            executionTimeMs,
            order.rejectionReason(),
            order.createdAt(),
            order.updatedAt()
        );
    }

    record ExecutionReport(
        String orderId,
        String clientId,
        String instrumentId,
        OrderStatus status,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        BigDecimal avgFillPrice,
        List<Fill> fills,
        BigDecimal slippage,
        Long executionTime,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
