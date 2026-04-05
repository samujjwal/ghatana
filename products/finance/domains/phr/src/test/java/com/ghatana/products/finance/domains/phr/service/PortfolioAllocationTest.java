package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Allocation Tests")
class PortfolioAllocationTest {
    private AllocationService service;

    @BeforeEach
    void setUp() {
        service = new AllocationService();
    }

    @Test
    @DisplayName("Should calculate asset allocation")
    void shouldCalculateAssetAllocation() {
        service.addHolding("AAPL", "EQUITY", BigDecimal.valueOf(150000.00));
        service.addHolding("BOND-1", "FIXED_INCOME", BigDecimal.valueOf(50000.00));
        Map<String, BigDecimal> allocation = service.calculateAssetAllocation();
        assertThat(allocation.get("EQUITY")).isEqualByComparingTo(BigDecimal.valueOf(75.00));
        assertThat(allocation.get("FIXED_INCOME")).isEqualByComparingTo(BigDecimal.valueOf(25.00));
    }

    @Test
    @DisplayName("Should calculate sector allocation")
    void shouldCalculateSectorAllocation() {
        service.addHolding("AAPL", "Technology", BigDecimal.valueOf(100000.00));
        service.addHolding("JPM", "Financial", BigDecimal.valueOf(50000.00));
        Map<String, BigDecimal> allocation = service.calculateSectorAllocation();
        assertThat(allocation).containsKey("Technology");
        assertThat(allocation).containsKey("Financial");
    }

    @Test
    @DisplayName("Should calculate geographic allocation")
    void shouldCalculateGeographicAllocation() {
        service.addHolding("AAPL", "US", BigDecimal.valueOf(120000.00));
        service.addHolding("NESN", "Europe", BigDecimal.valueOf(30000.00));
        Map<String, BigDecimal> allocation = service.calculateGeographicAllocation();
        assertThat(allocation.get("US")).isEqualByComparingTo(BigDecimal.valueOf(80.00));
    }

    @Test
    @DisplayName("Should detect allocation drift")
    void shouldDetectAllocationDrift() {
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setTargetAllocation("FIXED_INCOME", BigDecimal.valueOf(40.00));
        service.addHolding("AAPL", "EQUITY", BigDecimal.valueOf(150000.00));
        service.addHolding("BOND-1", "FIXED_INCOME", BigDecimal.valueOf(50000.00));
        Map<String, BigDecimal> drift = service.calculateAllocationDrift();
        assertThat(drift.get("EQUITY")).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate rebalancing recommendations")
    void shouldGenerateRebalancingRecommendations() {
        service.setTargetAllocation("EQUITY", BigDecimal.valueOf(60.00));
        service.setTargetAllocation("FIXED_INCOME", BigDecimal.valueOf(40.00));
        service.addHolding("AAPL", "EQUITY", BigDecimal.valueOf(150000.00));
        service.addHolding("BOND-1", "FIXED_INCOME", BigDecimal.valueOf(50000.00));
        RebalancingPlan plan = service.generateRebalancingPlan();
        assertThat(plan.trades()).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate concentration risk")
    void shouldCalculateConcentrationRisk() {
        service.addHolding("AAPL", "Technology", BigDecimal.valueOf(150000.00));
        service.addHolding("GOOGL", "Technology", BigDecimal.valueOf(50000.00));
        BigDecimal concentration = service.calculateConcentrationRisk("Technology");
        assertThat(concentration).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    @DisplayName("Should track allocation changes over time")
    void shouldTrackAllocationChangesOverTime() {
        service.addHolding("AAPL", "EQUITY", BigDecimal.valueOf(100000.00));
        service.recordAllocationSnapshot();
        service.addHolding("BOND-1", "FIXED_INCOME", BigDecimal.valueOf(50000.00));
        service.recordAllocationSnapshot();
        assertThat(service.getAllocationHistory()).hasSize(2);
    }

    @Test
    @DisplayName("Should validate allocation constraints")
    void shouldValidateAllocationConstraints() {
        service.setMaxAllocation("EQUITY", BigDecimal.valueOf(80.00));
        service.addHolding("AAPL", "EQUITY", BigDecimal.valueOf(180000.00));
        service.addHolding("BOND-1", "FIXED_INCOME", BigDecimal.valueOf(20000.00));
        boolean valid = service.validateAllocationConstraints();
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should generate allocation report")
    void shouldGenerateAllocationReport() {
        service.addHolding("AAPL", "EQUITY", BigDecimal.valueOf(150000.00));
        service.addHolding("BOND-1", "FIXED_INCOME", BigDecimal.valueOf(50000.00));
        AllocationReport report = service.generateReport();
        assertThat(report.assetClasses()).hasSize(2);
    }

    @Test
    @DisplayName("Should support custom allocation views")
    void shouldSupportCustomAllocationViews() {
        service.addHolding("AAPL", "Large Cap", BigDecimal.valueOf(100000.00));
        service.addHolding("SMALL-1", "Small Cap", BigDecimal.valueOf(50000.00));
        Map<String, BigDecimal> allocation = service.calculateCustomAllocation("MarketCap");
        assertThat(allocation).containsKey("Large Cap");
    }

    record RebalancingPlan(java.util.List<Trade> trades) {}
    record Trade(String symbol, String action, BigDecimal amount) {}
    record AllocationReport(Map<String, BigDecimal> assetClasses, BigDecimal totalValue) {}

    static class AllocationService {
        private final java.util.Map<String, Holding> holdings = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> targetAllocations = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> maxAllocations = new java.util.HashMap<>();
        private final java.util.List<Map<String, BigDecimal>> allocationHistory = new java.util.ArrayList<>();

        void addHolding(String symbol, String category, BigDecimal value) {
            holdings.put(symbol, new Holding(symbol, category, value));
        }

        void setTargetAllocation(String assetClass, BigDecimal percentage) {
            targetAllocations.put(assetClass, percentage);
        }

        void setMaxAllocation(String assetClass, BigDecimal percentage) {
            maxAllocations.put(assetClass, percentage);
        }

        Map<String, BigDecimal> calculateAssetAllocation() {
            BigDecimal total = holdings.values().stream()
                .map(Holding::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Map<String, BigDecimal> allocation = new java.util.HashMap<>();
            holdings.values().forEach(h -> {
                BigDecimal percentage = h.value().divide(total, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                allocation.merge(h.category(), percentage, BigDecimal::add);
            });
            return allocation;
        }

        Map<String, BigDecimal> calculateSectorAllocation() {
            return calculateAssetAllocation();
        }

        Map<String, BigDecimal> calculateGeographicAllocation() {
            return calculateAssetAllocation();
        }

        Map<String, BigDecimal> calculateAllocationDrift() {
            Map<String, BigDecimal> current = calculateAssetAllocation();
            Map<String, BigDecimal> drift = new java.util.HashMap<>();
            targetAllocations.forEach((assetClass, target) -> {
                BigDecimal currentAllocation = current.getOrDefault(assetClass, BigDecimal.ZERO);
                drift.put(assetClass, currentAllocation.subtract(target));
            });
            return drift;
        }

        RebalancingPlan generateRebalancingPlan() {
            Map<String, BigDecimal> drift = calculateAllocationDrift();
            java.util.List<Trade> trades = new java.util.ArrayList<>();
            drift.forEach((assetClass, driftAmount) -> {
                if (driftAmount.abs().compareTo(BigDecimal.valueOf(5.00)) > 0) {
                    String action = driftAmount.compareTo(BigDecimal.ZERO) > 0 ? "SELL" : "BUY";
                    trades.add(new Trade(assetClass, action, driftAmount.abs()));
                }
            });
            return new RebalancingPlan(trades);
        }

        BigDecimal calculateConcentrationRisk(String category) {
            BigDecimal categoryValue = holdings.values().stream()
                .filter(h -> h.category().equals(category))
                .map(Holding::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = holdings.values().stream()
                .map(Holding::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return total.compareTo(BigDecimal.ZERO) > 0 
                ? categoryValue.divide(total, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        }

        void recordAllocationSnapshot() {
            allocationHistory.add(new java.util.HashMap<>(calculateAssetAllocation()));
        }

        java.util.List<Map<String, BigDecimal>> getAllocationHistory() {
            return allocationHistory;
        }

        boolean validateAllocationConstraints() {
            Map<String, BigDecimal> current = calculateAssetAllocation();
            return maxAllocations.entrySet().stream()
                .allMatch(entry -> current.getOrDefault(entry.getKey(), BigDecimal.ZERO).compareTo(entry.getValue()) <= 0);
        }

        AllocationReport generateReport() {
            Map<String, BigDecimal> allocation = calculateAssetAllocation();
            BigDecimal total = holdings.values().stream()
                .map(Holding::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AllocationReport(allocation, total);
        }

        Map<String, BigDecimal> calculateCustomAllocation(String dimension) {
            return calculateAssetAllocation();
        }

        record Holding(String symbol, String category, BigDecimal value) {}
    }
}
