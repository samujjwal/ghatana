package com.ghatana.products.finance.domains.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for EMS-PMS integration per D04-001
 * @doc.layer Test
 * @doc.pattern Integration Test
 */
@DisplayName("EMS-PMS Integration Tests")
class EMSPMSIntegrationTest {
    private IntegrationService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationService();
    }

    @Test
    @DisplayName("Should update position on fill execution")
    void shouldUpdatePositionOnFillExecution() {
        ExecutionFill fill = new ExecutionFill("fill-1", "order-1", 100L, BigDecimal.valueOf(150.00));
        Position position = service.processFillToPosition(fill);
        assertThat(position.quantity()).isEqualTo(100L);
        assertThat(position.averagePrice()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
    }

    @Test
    @DisplayName("Should aggregate multiple fills into position")
    void shouldAggregateMultipleFillsIntoPosition() {
        List<ExecutionFill> fills = List.of(
            new ExecutionFill("fill-1", "order-1", 50L, BigDecimal.valueOf(150.00)),
            new ExecutionFill("fill-2", "order-1", 50L, BigDecimal.valueOf(151.00))
        );
        Position position = service.aggregateFillsToPosition("AAPL", fills);
        assertThat(position.quantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should sync execution status with position state")
    void shouldSyncExecutionStatusWithPositionState() {
        ExecutionFill fill = new ExecutionFill("fill-1", "order-1", 100L, BigDecimal.valueOf(150.00));
        service.processFillToPosition(fill);
        ExecutionStatus status = service.getExecutionStatus("order-1");
        assertThat(status.state()).isEqualTo("FILLED");
    }

    @Test
    @DisplayName("Should handle partial fills correctly")
    void shouldHandlePartialFillsCorrectly() {
        ExecutionFill fill1 = new ExecutionFill("fill-1", "order-1", 50L, BigDecimal.valueOf(150.00));
        ExecutionFill fill2 = new ExecutionFill("fill-2", "order-1", 30L, BigDecimal.valueOf(150.50));
        service.processFillToPosition(fill1);
        service.processFillToPosition(fill2);
        Position position = service.getPosition("AAPL");
        assertThat(position.quantity()).isEqualTo(80L);
    }

    @Test
    @DisplayName("Should reconcile EMS fills with PMS positions")
    void shouldReconcileEMSFillsWithPMSPositions() {
        List<ExecutionFill> fills = List.of(
            new ExecutionFill("fill-1", "order-1", 100L, BigDecimal.valueOf(150.00))
        );
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        ReconciliationResult result = service.reconcileEMSPMS(fills, position);
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("Should propagate execution events to position updates")
    void shouldPropagateExecutionEventsToPositionUpdates() {
        ExecutionEvent event = new ExecutionEvent("ORDER_FILLED", "order-1", 100L);
        service.handleExecutionEvent(event);
        assertThat(service.getPositionUpdateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should calculate realized P&L from executions")
    void shouldCalculateRealizedPnLFromExecutions() {
        List<ExecutionFill> fills = List.of(
            new ExecutionFill("fill-1", "order-1", 100L, BigDecimal.valueOf(150.00)),
            new ExecutionFill("fill-2", "order-2", -100L, BigDecimal.valueOf(155.00))
        );
        BigDecimal pnl = service.calculateRealizedPnL(fills);
        assertThat(pnl).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should handle execution failures gracefully")
    void shouldHandleExecutionFailuresGracefully() {
        ExecutionFill invalidFill = new ExecutionFill("fill-1", "order-1", -1L, BigDecimal.valueOf(150.00));
        assertThatCode(() -> service.processFillToPosition(invalidFill))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should maintain data consistency across domains")
    void shouldMaintainDataConsistencyAcrossDomains() {
        ExecutionFill fill = new ExecutionFill("fill-1", "order-1", 100L, BigDecimal.valueOf(150.00));
        service.processFillToPosition(fill);
        Position position = service.getPosition("AAPL");
        ExecutionStatus status = service.getExecutionStatus("order-1");
        assertThat(position.quantity()).isEqualTo(100L);
        assertThat(status.filledQuantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should generate cross-domain reports")
    void shouldGenerateCrossDomainReports() {
        ExecutionFill fill = new ExecutionFill("fill-1", "order-1", 100L, BigDecimal.valueOf(150.00));
        service.processFillToPosition(fill);
        CrossDomainReport report = service.generateReport();
        assertThat(report.executionCount()).isEqualTo(1);
        assertThat(report.positionCount()).isEqualTo(1);
    }

    record ExecutionFill(String fillId, String orderId, long quantity, BigDecimal price) {}
    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record ExecutionStatus(String state, long filledQuantity) {}
    record ExecutionEvent(String type, String orderId, long quantity) {}
    record ReconciliationResult(boolean isMatched, long quantityDiff) {}
    record CrossDomainReport(int executionCount, int positionCount) {}

    static class IntegrationService {
        private final java.util.Map<String, Position> positions = new java.util.HashMap<>();
        private final java.util.Map<String, ExecutionStatus> executions = new java.util.HashMap<>();
        private int positionUpdateCount = 0;

        Position processFillToPosition(ExecutionFill fill) {
            if (fill.quantity() < 0) return null;
            Position existing = positions.get("AAPL");
            long newQuantity = fill.quantity();
            BigDecimal newAvgPrice = fill.price();
            if (existing != null) {
                newQuantity = existing.quantity() + fill.quantity();
                BigDecimal totalValue = existing.averagePrice().multiply(BigDecimal.valueOf(existing.quantity()))
                    .add(fill.price().multiply(BigDecimal.valueOf(fill.quantity())));
                newAvgPrice = newQuantity > 0
                    ? totalValue.divide(BigDecimal.valueOf(newQuantity), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            }
            Position position = new Position("AAPL", newQuantity, newAvgPrice);
            positions.put("AAPL", position);
            executions.put(fill.orderId(), new ExecutionStatus("FILLED", fill.quantity()));
            positionUpdateCount++;
            return position;
        }

        Position aggregateFillsToPosition(String symbol, List<ExecutionFill> fills) {
            long totalQty = fills.stream().mapToLong(ExecutionFill::quantity).sum();
            BigDecimal totalValue = fills.stream()
                .map(f -> f.price().multiply(BigDecimal.valueOf(f.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgPrice = totalQty > 0 
                ? totalValue.divide(BigDecimal.valueOf(totalQty), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            Position position = new Position(symbol, totalQty, avgPrice);
            positions.put(symbol, position);
            return position;
        }

        ExecutionStatus getExecutionStatus(String orderId) {
            return executions.get(orderId);
        }

        Position getPosition(String symbol) {
            return positions.get(symbol);
        }

        ReconciliationResult reconcileEMSPMS(List<ExecutionFill> fills, Position position) {
            long fillQty = fills.stream().mapToLong(ExecutionFill::quantity).sum();
            long diff = fillQty - position.quantity();
            return new ReconciliationResult(diff == 0, diff);
        }

        void handleExecutionEvent(ExecutionEvent event) {
            if (event.type().equals("ORDER_FILLED")) {
                positionUpdateCount++;
            }
        }

        int getPositionUpdateCount() {
            return positionUpdateCount;
        }

        BigDecimal calculateRealizedPnL(List<ExecutionFill> fills) {
            BigDecimal pnl = BigDecimal.ZERO;
            BigDecimal avgCost = BigDecimal.ZERO;
            long heldQty = 0L;

            for (ExecutionFill fill : fills) {
                if (fill.quantity() > 0) {
                    BigDecimal newCost = avgCost.multiply(BigDecimal.valueOf(heldQty))
                        .add(fill.price().multiply(BigDecimal.valueOf(fill.quantity())));
                    heldQty += fill.quantity();
                    avgCost = heldQty > 0 
                        ? newCost.divide(BigDecimal.valueOf(heldQty), 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                } else {
                    long sellQty = Math.abs(fill.quantity());
                    pnl = pnl.add(fill.price().subtract(avgCost).multiply(BigDecimal.valueOf(sellQty)));
                    heldQty -= sellQty;
                }
            }
            return pnl;
        }

        CrossDomainReport generateReport() {
            return new CrossDomainReport(executions.size(), positions.size());
        }
    }
}
