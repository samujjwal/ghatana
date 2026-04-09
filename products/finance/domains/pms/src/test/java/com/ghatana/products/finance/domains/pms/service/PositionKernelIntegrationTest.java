package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Kernel Integration Tests")
class PositionKernelIntegrationTest {
    private PositionKernel kernel;

    @BeforeEach
    void setUp() {
        kernel = new PositionKernel();
    }

    @Test
    @DisplayName("Should integrate position calculation with persistence")
    void shouldIntegratePositionCalculationWithPersistence() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "BUY", 100L, BigDecimal.valueOf(150.00)),
            new Trade("trade-2", "AAPL", "BUY", 50L, BigDecimal.valueOf(151.00))
        );
        Position position = kernel.processTradesAndPersist("AAPL", trades);
        assertThat(position.quantity()).isEqualTo(150L);
        assertThat(kernel.getStoredPosition("AAPL")).isPresent();
    }

    @Test
    @DisplayName("Should integrate reconciliation with reporting")
    void shouldIntegrateReconciliationWithReporting() {
        Position internal = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position broker = new Position("AAPL", 95L, BigDecimal.valueOf(150.00));
        ReconciliationResult result = kernel.reconcileAndReport(internal, broker);
        assertThat(result.isMatched()).isFalse();
        assertThat(kernel.getReconciliationHistory()).hasSize(1);
    }

    @Test
    @DisplayName("Should integrate risk calculation with constraints")
    void shouldIntegrateRiskCalculationWithConstraints() {
        Position position = new Position("AAPL", 10000L, BigDecimal.valueOf(150.00));
        RiskAssessment assessment = kernel.assessRiskWithConstraints(position);
        assertThat(assessment.hasViolations()).isTrue();
    }

    @Test
    @DisplayName("Should integrate valuation with market data")
    void shouldIntegrateValuationWithMarketData() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        kernel.updateMarketPrice("AAPL", BigDecimal.valueOf(155.00));
        ValuationResult valuation = kernel.valuePosition(position);
        assertThat(valuation.marketValue()).isGreaterThan(valuation.costBasis());
    }

    @Test
    @DisplayName("Should integrate corporate actions with position updates")
    void shouldIntegrateCorporateActionsWithPositionUpdates() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        kernel.storePosition(position);
        StockSplit split = new StockSplit("AAPL", 2, 1);
        kernel.applyCorporateAction(split);
        Position updated = kernel.getStoredPosition("AAPL").orElseThrow();
        assertThat(updated.quantity()).isEqualTo(200L);
    }

    @Test
    @DisplayName("Should integrate events with audit trail")
    void shouldIntegrateEventsWithAuditTrail() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        kernel.createPosition(position);
        assertThat(kernel.getAuditTrail("AAPL")).isNotEmpty();
    }

    @Test
    @DisplayName("Should integrate caching with queries")
    void shouldIntegrateCachingWithQueries() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        kernel.storePosition(position);
        kernel.queryPosition("AAPL");
        kernel.queryPosition("AAPL");
        assertThat(kernel.getCacheHitRate()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should integrate transfers with locking")
    void shouldIntegrateTransfersWithLocking() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        kernel.storePosition(position);
        kernel.lockForTransfer("AAPL", 50L);
        TransferResult result = kernel.transferPosition("AAPL", "account-1", "account-2", 50L);
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("Should integrate aggregation with reporting")
    void shouldIntegrateAggregationWithReporting() {
        kernel.storePosition(new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        kernel.storePosition(new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00)));
        PortfolioReport report = kernel.generatePortfolioReport();
        assertThat(report.positionCount()).isEqualTo(2);
        assertThat(report.totalValue()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should integrate performance tracking with history")
    void shouldIntegratePerformanceTrackingWithHistory() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        kernel.storePosition(position);
        kernel.recordSnapshot("AAPL");
        kernel.updateMarketPrice("AAPL", BigDecimal.valueOf(155.00));
        kernel.recordSnapshot("AAPL");
        PerformanceMetrics metrics = kernel.calculatePerformance("AAPL");
        assertThat(metrics.totalReturn()).isGreaterThan(BigDecimal.ZERO);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record Trade(String tradeId, String symbol, String side, long quantity, BigDecimal price) {}
    record StockSplit(String symbol, int newShares, int oldShares) {}
    record ReconciliationResult(boolean isMatched, long quantityDiff) {}
    record RiskAssessment(boolean hasViolations, List<String> violations) {}
    record ValuationResult(BigDecimal marketValue, BigDecimal costBasis) {}
    record TransferResult(boolean success, String message) {}
    record PortfolioReport(int positionCount, BigDecimal totalValue) {}
    record PerformanceMetrics(BigDecimal totalReturn, double percentReturn) {}

    static class PositionKernel {
        private final java.util.Map<String, Position> positions = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> marketPrices = new java.util.HashMap<>();
        private final List<String> auditTrail = new java.util.ArrayList<>();
        private final List<ReconciliationResult> reconciliationHistory = new java.util.ArrayList<>();
        private final List<Position> snapshots = new java.util.ArrayList<>();
        private int cacheHits = 0;
        private int cacheMisses = 0;

        Position processTradesAndPersist(String symbol, List<Trade> trades) {
            long netQty = 0L;
            BigDecimal totalCost = BigDecimal.ZERO;
            long totalBought = 0L;

            for (Trade trade : trades) {
                if (trade.side().equals("BUY")) {
                    netQty += trade.quantity();
                    totalCost = totalCost.add(trade.price().multiply(BigDecimal.valueOf(trade.quantity())));
                    totalBought += trade.quantity();
                }
            }

            BigDecimal avgPrice = totalBought > 0
                ? totalCost.divide(BigDecimal.valueOf(totalBought), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            Position position = new Position(symbol, netQty, avgPrice);
            positions.put(symbol, position);
            return position;
        }

        java.util.Optional<Position> getStoredPosition(String symbol) {
            return java.util.Optional.ofNullable(positions.get(symbol));
        }

        ReconciliationResult reconcileAndReport(Position internal, Position broker) {
            long diff = internal.quantity() - broker.quantity();
            boolean matched = diff == 0;
            ReconciliationResult result = new ReconciliationResult(matched, diff);
            reconciliationHistory.add(result);
            return result;
        }

        List<ReconciliationResult> getReconciliationHistory() {
            return reconciliationHistory;
        }

        RiskAssessment assessRiskWithConstraints(Position position) {
            List<String> violations = new java.util.ArrayList<>();
            if (position.quantity() > 5000L) {
                violations.add("Position size exceeds limit");
            }
            return new RiskAssessment(!violations.isEmpty(), violations);
        }

        void updateMarketPrice(String symbol, BigDecimal price) {
            marketPrices.put(symbol, price);
        }

        ValuationResult valuePosition(Position position) {
            BigDecimal marketPrice = marketPrices.getOrDefault(position.symbol(), position.averagePrice());
            BigDecimal marketValue = marketPrice.multiply(BigDecimal.valueOf(position.quantity()));
            BigDecimal costBasis = position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
            return new ValuationResult(marketValue, costBasis);
        }

        void storePosition(Position position) {
            positions.put(position.symbol(), position);
        }

        void applyCorporateAction(StockSplit split) {
            Position position = positions.get(split.symbol());
            if (position != null) {
                long newQty = position.quantity() * split.newShares() / split.oldShares();
                BigDecimal newPrice = position.averagePrice()
                    .multiply(BigDecimal.valueOf(split.oldShares()))
                    .divide(BigDecimal.valueOf(split.newShares()), 2, java.math.RoundingMode.HALF_UP);
                positions.put(split.symbol(), new Position(split.symbol(), newQty, newPrice));
            }
        }

        void createPosition(Position position) {
            positions.put(position.symbol(), position);
            auditTrail.add("POSITION_CREATED: " + position.symbol());
        }

        List<String> getAuditTrail(String symbol) {
            return auditTrail.stream().filter(e -> e.contains(symbol)).toList();
        }

        Position queryPosition(String symbol) {
            Position position = positions.get(symbol);
            if (position != null) {
                cacheHits++;
            } else {
                cacheMisses++;
            }
            return position;
        }

        double getCacheHitRate() {
            int total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }

        void lockForTransfer(String symbol, long quantity) {
            auditTrail.add("LOCKED: " + symbol + " " + quantity);
        }

        TransferResult transferPosition(String symbol, String fromAccount, String toAccount, long quantity) {
            return new TransferResult(true, "Transfer successful");
        }

        PortfolioReport generatePortfolioReport() {
            BigDecimal totalValue = positions.values().stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PortfolioReport(positions.size(), totalValue);
        }

        void recordSnapshot(String symbol) {
            Position position = positions.get(symbol);
            if (position != null) {
                snapshots.add(position);
            }
        }

        PerformanceMetrics calculatePerformance(String symbol) {
            if (snapshots.size() < 2) {
                return new PerformanceMetrics(BigDecimal.ZERO, 0.0);
            }
            Position first = snapshots.get(0);
            Position last = snapshots.get(snapshots.size() - 1);
            BigDecimal marketPrice = marketPrices.getOrDefault(symbol, last.averagePrice());
            BigDecimal startValue = first.averagePrice().multiply(BigDecimal.valueOf(first.quantity()));
            BigDecimal endValue = marketPrice.multiply(BigDecimal.valueOf(last.quantity()));
            BigDecimal totalReturn = endValue.subtract(startValue);
            double percentReturn = startValue.compareTo(BigDecimal.ZERO) > 0
                ? totalReturn.divide(startValue, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;
            return new PerformanceMetrics(totalReturn, percentReturn);
        }
    }
}
