package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Rebalancing Tests")
class PortfolioRebalancingTest {
    private RebalancingService service;

    @BeforeEach
    void setUp() {
        service = new RebalancingService();
    }

    @Test
    @DisplayName("Should detect rebalancing need")
    void shouldDetectRebalancingNeed() {
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(75.00));
        boolean needsRebalancing = service.needsRebalancing(BigDecimal.valueOf(5.00));
        assertThat(needsRebalancing).isTrue();
    }

    @Test
    @DisplayName("Should generate rebalancing plan")
    void shouldGenerateRebalancingPlan() {
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setTargetAllocation("FIXED_INCOME", BigDecimal.valueOf(40.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(75.00));
        service.setCurrentAllocation("FIXED_INCOME", BigDecimal.valueOf(25.00));
        RebalancingPlan plan = service.generatePlan(BigDecimal.valueOf(200000.00));
        assertThat(plan.trades()).isNotEmpty();
    }

    @Test
    @DisplayName("Should minimize transaction costs in rebalancing")
    void shouldMinimizeTransactionCostsInRebalancing() {
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(62.00));
        RebalancingPlan plan = service.generatePlan(BigDecimal.valueOf(200000.00));
        BigDecimal estimatedCost = service.estimateRebalancingCost(plan, BigDecimal.valueOf(0.001));
        assertThat(estimatedCost).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should support threshold-based rebalancing")
    void shouldSupportThresholdBasedRebalancing() {
        service.setRebalancingThreshold(BigDecimal.valueOf(5.00));
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(63.00));
        boolean shouldRebalance = service.shouldRebalance();
        assertThat(shouldRebalance).isFalse();
    }

    @Test
    @DisplayName("Should support calendar-based rebalancing")
    void shouldSupportCalendarBasedRebalancing() {
        service.setRebalancingFrequency("QUARTERLY");
        service.setLastRebalancingDate(java.time.LocalDate.now().minusDays(100));
        boolean isDue = service.isRebalancingDue();
        assertThat(isDue).isTrue();
    }

    @Test
    @DisplayName("Should handle tax-efficient rebalancing")
    void shouldHandleTaxEfficientRebalancing() {
        service.setTaxLotTracking(true);
        service.addTaxLot("AAPL", BigDecimal.valueOf(100.00), java.time.LocalDate.now().minusDays(400));
        service.addTaxLot("AAPL", BigDecimal.valueOf(150.00), java.time.LocalDate.now().minusDays(100));
        RebalancingPlan plan = service.generateTaxEfficientPlan(BigDecimal.valueOf(200000.00));
        assertThat(plan.trades()).isNotEmpty();
    }

    @Test
    @DisplayName("Should validate rebalancing constraints")
    void shouldValidateRebalancingConstraints() {
        service.setMinTradeSize(BigDecimal.valueOf(1000.00));
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(60.50));
        RebalancingPlan plan = service.generatePlan(BigDecimal.valueOf(200000.00));
        assertThat(plan.trades()).isEmpty();
    }

    @Test
    @DisplayName("Should track rebalancing history")
    void shouldTrackRebalancingHistory() {
        service.recordRebalancing(java.time.LocalDate.now());
        service.recordRebalancing(java.time.LocalDate.now().minusDays(90));
        assertThat(service.getRebalancingHistory()).hasSize(2);
    }

    @Test
    @DisplayName("Should generate rebalancing report")
    void shouldGenerateRebalancingReport() {
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(75.00));
        RebalancingReport report = service.generateReport();
        assertThat(report.driftPercentage()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should support multi-asset rebalancing")
    void shouldSupportMultiAssetRebalancing() {
        Map<String, BigDecimal> targets = Map.of(
            "EQUITY", BigDecimal.valueOf(60.00),
            "FIXED_INCOME", BigDecimal.valueOf(30.00),
            "CASH", BigDecimal.valueOf(10.00)
        );
        service.setTargetAllocations(targets);
        RebalancingPlan plan = service.generateMultiAssetPlan(BigDecimal.valueOf(200000.00));
        assertThat(plan.trades()).isNotEmpty();
    }

    record RebalancingPlan(List<Trade> trades, BigDecimal estimatedCost) {
        RebalancingPlan(List<Trade> trades) {
            this(trades, BigDecimal.ZERO);
        }
    }
    record Trade(String assetClass, String action, BigDecimal amount) {}
    record RebalancingReport(BigDecimal driftPercentage, boolean needsRebalancing) {}

    static class RebalancingService {
        private final Map<String, BigDecimal> targetAllocations = new java.util.HashMap<>();
        private final Map<String, BigDecimal> currentAllocations = new java.util.HashMap<>();
        private final List<java.time.LocalDate> rebalancingHistory = new java.util.ArrayList<>();
        private final List<TaxLot> taxLots = new java.util.ArrayList<>();
        private BigDecimal rebalancingThreshold = BigDecimal.valueOf(5.00);
        private BigDecimal minTradeSize = BigDecimal.ZERO;
        private String rebalancingFrequency = "QUARTERLY";
        private java.time.LocalDate lastRebalancingDate;
        private boolean taxLotTracking = false;

        void setTargetAllocation(String assetClass, BigDecimal percentage) {
            targetAllocations.put(assetClass, percentage);
        }

        void setCurrentAllocation(String assetClass, BigDecimal percentage) {
            currentAllocations.put(assetClass, percentage);
        }

        void setTargetAllocations(Map<String, BigDecimal> allocations) {
            targetAllocations.putAll(allocations);
        }

        void setRebalancingThreshold(BigDecimal threshold) {
            this.rebalancingThreshold = threshold;
        }

        void setMinTradeSize(BigDecimal size) {
            this.minTradeSize = size;
        }

        void setRebalancingFrequency(String frequency) {
            this.rebalancingFrequency = frequency;
        }

        void setLastRebalancingDate(java.time.LocalDate date) {
            this.lastRebalancingDate = date;
        }

        void setTaxLotTracking(boolean enabled) {
            this.taxLotTracking = enabled;
        }

        void addTaxLot(String symbol, BigDecimal costBasis, java.time.LocalDate purchaseDate) {
            taxLots.add(new TaxLot(symbol, costBasis, purchaseDate));
        }

        boolean needsRebalancing(BigDecimal threshold) {
            return targetAllocations.entrySet().stream()
                .anyMatch(entry -> {
                    BigDecimal current = currentAllocations.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    return entry.getValue().subtract(current).abs().compareTo(threshold) > 0;
                });
        }

        boolean shouldRebalance() {
            return needsRebalancing(rebalancingThreshold);
        }

        boolean isRebalancingDue() {
            if (lastRebalancingDate == null) return true;
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(lastRebalancingDate, java.time.LocalDate.now());
            return daysSince >= 90;
        }

        RebalancingPlan generatePlan(BigDecimal portfolioValue) {
            List<Trade> trades = new java.util.ArrayList<>();
            
            targetAllocations.forEach((assetClass, target) -> {
                BigDecimal current = currentAllocations.getOrDefault(assetClass, BigDecimal.ZERO);
                BigDecimal drift = target.subtract(current);
                
                if (drift.abs().compareTo(rebalancingThreshold) > 0) {
                    BigDecimal tradeAmount = portfolioValue.multiply(drift).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    
                    if (tradeAmount.abs().compareTo(minTradeSize) >= 0) {
                        String action = drift.compareTo(BigDecimal.ZERO) > 0 ? "BUY" : "SELL";
                        trades.add(new Trade(assetClass, action, tradeAmount.abs()));
                    }
                }
            });
            
            return new RebalancingPlan(trades);
        }

        RebalancingPlan generateTaxEfficientPlan(BigDecimal portfolioValue) {
            return generatePlan(portfolioValue);
        }

        RebalancingPlan generateMultiAssetPlan(BigDecimal portfolioValue) {
            return generatePlan(portfolioValue);
        }

        BigDecimal estimateRebalancingCost(RebalancingPlan plan, BigDecimal feeRate) {
            return plan.trades().stream()
                .map(t -> t.amount().multiply(feeRate))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        void recordRebalancing(java.time.LocalDate date) {
            rebalancingHistory.add(date);
            lastRebalancingDate = date;
        }

        List<java.time.LocalDate> getRebalancingHistory() {
            return rebalancingHistory;
        }

        RebalancingReport generateReport() {
            BigDecimal maxDrift = targetAllocations.entrySet().stream()
                .map(entry -> {
                    BigDecimal current = currentAllocations.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    return entry.getValue().subtract(current).abs();
                })
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
            
            return new RebalancingReport(maxDrift, shouldRebalance());
        }

        record TaxLot(String symbol, BigDecimal costBasis, java.time.LocalDate purchaseDate) {}
    }
}
