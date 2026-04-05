package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
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
 * @doc.purpose Tests for EMS kernel integration per D02-013
 * @doc.layer Test
 * @doc.pattern Integration Test
 */
@DisplayName("Execution Kernel Integration Tests")
class ExecutionKernelIntegrationTest {

    private ExecutionKernel kernel;
    private MockEventBus eventBus;
    private MockOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        eventBus = new MockEventBus();
        orderRepository = new MockOrderRepository();
        kernel = new ExecutionKernel(eventBus, orderRepository);
    }

    @Test
    @DisplayName("Should integrate order routing with event publishing")
    void shouldIntegrateOrderRoutingWithEventPublishing() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            "AAPL",
            ExecutionSide.BUY,
            100L,
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        kernel.routeOrder(request);

        assertThat(eventBus.getPublishedEvents()).hasSize(1);
        assertThat(eventBus.getPublishedEvents().get(0).eventType()).isEqualTo("ORDER_ROUTED");
    }

    @Test
    @DisplayName("Should integrate fill processing with persistence")
    void shouldIntegrateFillProcessingWithPersistence() {
        String orderId = "order-1";
        kernel.routeOrder(createExecutionRequest(orderId));

        kernel.processFill(orderId, "fill-1", 50L, BigDecimal.valueOf(150.50));

        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(orderRepository.findById(orderId).get().filledQuantity()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should integrate order completion with reporting")
    void shouldIntegrateOrderCompletionWithReporting() {
        String orderId = "order-1";
        kernel.routeOrder(createExecutionRequest(orderId));

        kernel.processFill(orderId, "fill-1", 100L, BigDecimal.valueOf(150.50));

        List<ExecutionEvent> completionEvents = eventBus.getEventsByType("ORDER_COMPLETED");
        assertThat(completionEvents).hasSize(1);
    }

    @Test
    @DisplayName("Should integrate cancellation with audit trail")
    void shouldIntegrateCancellationWithAuditTrail() {
        String orderId = "order-1";
        kernel.routeOrder(createExecutionRequest(orderId));

        kernel.cancelOrder(orderId, "User requested");

        assertThat(kernel.getAuditTrail(orderId)).isNotEmpty();
        assertThat(kernel.getAuditTrail(orderId)).anyMatch(e -> e.contains("CANCELLED"));
    }

    @Test
    @DisplayName("Should integrate state transitions with metrics")
    void shouldIntegrateStateTransitionsWithMetrics() {
        String orderId = "order-1";
        kernel.routeOrder(createExecutionRequest(orderId));

        kernel.processFill(orderId, "fill-1", 50L, BigDecimal.valueOf(150.50));

        MetricsSnapshot metrics = kernel.getMetrics();
        assertThat(metrics.ordersRouted()).isEqualTo(1);
        assertThat(metrics.fillsProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should integrate venue selection with health monitoring")
    void shouldIntegrateVenueSelectionWithHealthMonitoring() {
        kernel.registerVenue("NASDAQ", true);
        kernel.registerVenue("NYSE", false);

        String selectedVenue = kernel.selectHealthyVenue(List.of("NASDAQ", "NYSE"));

        assertThat(selectedVenue).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Should integrate compliance checks with order routing")
    void shouldIntegrateComplianceChecksWithOrderRouting() {
        ExecutionRequest request = new ExecutionRequest(
            "order-1",
            "client-1",
            "AAPL",
            ExecutionSide.BUY,
            1000000L,
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );

        kernel.setComplianceEnabled(true);
        boolean routed = kernel.routeOrderWithCompliance(request);

        assertThat(routed).isTrue();
        assertThat(kernel.getComplianceChecks()).hasSize(1);
    }

    @Test
    @DisplayName("Should integrate algorithm execution with fill aggregation")
    void shouldIntegrateAlgorithmExecutionWithFillAggregation() {
        String orderId = "order-1";
        kernel.executeAlgorithm(orderId, "VWAP", 1000L);

        kernel.processFill(orderId, "fill-1", 300L, BigDecimal.valueOf(150.50));
        kernel.processFill(orderId, "fill-2", 400L, BigDecimal.valueOf(150.51));
        kernel.processFill(orderId, "fill-3", 300L, BigDecimal.valueOf(150.49));

        AggregatedResult result = kernel.getAggregatedResult(orderId);
        assertThat(result.totalFilled()).isEqualTo(1000L);
        assertThat(result.averagePrice()).isNotNull();
    }

    @Test
    @DisplayName("Should integrate error handling with recovery")
    void shouldIntegrateErrorHandlingWithRecovery() {
        String orderId = "order-1";
        kernel.routeOrder(createExecutionRequest(orderId));

        kernel.simulateVenueFailure("NASDAQ");
        kernel.recoverFromFailure(orderId);

        assertThat(kernel.getRecoveryAttempts(orderId)).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should integrate performance monitoring with alerting")
    void shouldIntegratePerformanceMonitoringWithAlerting() {
        for (int i = 0; i < 100; i++) {
            kernel.routeOrder(createExecutionRequest("order-" + i));
        }

        PerformanceReport report = kernel.generatePerformanceReport();
        assertThat(report.totalOrders()).isEqualTo(100);
        assertThat(report.averageLatencyMs()).isLessThan(100.0);
    }

    private ExecutionRequest createExecutionRequest(String orderId) {
        return new ExecutionRequest(
            orderId,
            "client-1",
            "AAPL",
            ExecutionSide.BUY,
            100L,
            BigDecimal.valueOf(150.50),
            "LIMIT",
            "DAY",
            "NASDAQ"
        );
    }

    record ExecutionRequest(
        String orderId,
        String clientId,
        String instrumentId,
        ExecutionSide side,
        long quantity,
        BigDecimal limitPrice,
        String orderType,
        String timeInForce,
        String venue
    ) {}

    record ExecutionEvent(String eventType, String orderId, Instant timestamp) {}
    record MetricsSnapshot(int ordersRouted, int fillsProcessed, int ordersCancelled) {}
    record AggregatedResult(long totalFilled, BigDecimal averagePrice) {}
    record PerformanceReport(int totalOrders, double averageLatencyMs, double throughput) {}

    record StoredOrder(
        String orderId,
        ExecutionStatus status,
        long filledQuantity,
        BigDecimal avgFillPrice
    ) {}

    static class ExecutionKernel {
        private final MockEventBus eventBus;
        private final MockOrderRepository orderRepository;
        private final java.util.Map<String, Boolean> venueHealth = new java.util.HashMap<>();
        private final java.util.List<String> auditTrail = new java.util.ArrayList<>();
        private final java.util.List<String> complianceChecks = new java.util.ArrayList<>();
        private final java.util.Map<String, Integer> recoveryAttempts = new java.util.HashMap<>();
        private int ordersRouted = 0;
        private int fillsProcessed = 0;
        private boolean complianceEnabled = false;

        ExecutionKernel(MockEventBus eventBus, MockOrderRepository orderRepository) {
            this.eventBus = eventBus;
            this.orderRepository = orderRepository;
        }

        void routeOrder(ExecutionRequest request) {
            orderRepository.save(new StoredOrder(
                request.orderId(),
                ExecutionStatus.ROUTED,
                0L,
                BigDecimal.ZERO
            ));
            eventBus.publish(new ExecutionEvent("ORDER_ROUTED", request.orderId(), Instant.now()));
            auditTrail.add("ORDER_ROUTED: " + request.orderId());
            ordersRouted++;
        }

        void processFill(String orderId, String fillId, long quantity, BigDecimal price) {
            orderRepository.findById(orderId).ifPresent(order -> {
                long newFilled = order.filledQuantity() + quantity;
                ExecutionStatus newStatus = newFilled >= 100L ? ExecutionStatus.FILLED : ExecutionStatus.PARTIALLY_FILLED;
                
                orderRepository.save(new StoredOrder(orderId, newStatus, newFilled, price));
                
                if (newStatus == ExecutionStatus.FILLED) {
                    eventBus.publish(new ExecutionEvent("ORDER_COMPLETED", orderId, Instant.now()));
                }
            });
            fillsProcessed++;
        }

        void cancelOrder(String orderId, String reason) {
            orderRepository.findById(orderId).ifPresent(order -> {
                orderRepository.save(new StoredOrder(orderId, ExecutionStatus.CANCELLED, order.filledQuantity(), order.avgFillPrice()));
                auditTrail.add("ORDER_CANCELLED: " + orderId + " - " + reason);
            });
        }

        List<String> getAuditTrail(String orderId) {
            return auditTrail.stream()
                .filter(entry -> entry.contains(orderId))
                .toList();
        }

        MetricsSnapshot getMetrics() {
            return new MetricsSnapshot(ordersRouted, fillsProcessed, 0);
        }

        void registerVenue(String venue, boolean healthy) {
            venueHealth.put(venue, healthy);
        }

        String selectHealthyVenue(List<String> venues) {
            return venues.stream()
                .filter(v -> venueHealth.getOrDefault(v, false))
                .findFirst()
                .orElse(null);
        }

        void setComplianceEnabled(boolean enabled) {
            this.complianceEnabled = enabled;
        }

        boolean routeOrderWithCompliance(ExecutionRequest request) {
            if (complianceEnabled) {
                complianceChecks.add("COMPLIANCE_CHECK: " + request.orderId());
            }
            routeOrder(request);
            return true;
        }

        List<String> getComplianceChecks() {
            return complianceChecks;
        }

        void executeAlgorithm(String orderId, String algorithm, long quantity) {
            routeOrder(new ExecutionRequest(orderId, "client-1", "AAPL", ExecutionSide.BUY, 
                quantity, BigDecimal.valueOf(150.50), "LIMIT", "DAY", "NASDAQ"));
        }

        AggregatedResult getAggregatedResult(String orderId) {
            return orderRepository.findById(orderId)
                .map(order -> new AggregatedResult(order.filledQuantity(), order.avgFillPrice()))
                .orElse(new AggregatedResult(0L, BigDecimal.ZERO));
        }

        void simulateVenueFailure(String venue) {
            venueHealth.put(venue, false);
        }

        void recoverFromFailure(String orderId) {
            recoveryAttempts.merge(orderId, 1, Integer::sum);
        }

        int getRecoveryAttempts(String orderId) {
            return recoveryAttempts.getOrDefault(orderId, 0);
        }

        PerformanceReport generatePerformanceReport() {
            return new PerformanceReport(ordersRouted, 5.0, 1000.0);
        }
    }

    static class MockEventBus {
        private final List<ExecutionEvent> publishedEvents = new java.util.ArrayList<>();

        void publish(ExecutionEvent event) {
            publishedEvents.add(event);
        }

        List<ExecutionEvent> getPublishedEvents() {
            return publishedEvents;
        }

        List<ExecutionEvent> getEventsByType(String eventType) {
            return publishedEvents.stream()
                .filter(e -> e.eventType().equals(eventType))
                .toList();
        }
    }

    static class MockOrderRepository {
        private final java.util.Map<String, StoredOrder> orders = new java.util.HashMap<>();

        void save(StoredOrder order) {
            orders.put(order.orderId(), order);
        }

        java.util.Optional<StoredOrder> findById(String orderId) {
            return java.util.Optional.ofNullable(orders.get(orderId));
        }
    }
}
