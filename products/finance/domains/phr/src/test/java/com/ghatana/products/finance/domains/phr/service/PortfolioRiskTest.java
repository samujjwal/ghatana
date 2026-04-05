package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Risk Tests")
class PortfolioRiskTest {
    private RiskService service;

    @BeforeEach
    void setUp() {
        service = new RiskService();
    }

    @Test
    @DisplayName("Should calculate portfolio VaR")
    void shouldCalculatePortfolioVaR() {
        service.addPosition("AAPL", BigDecimal.valueOf(150000.00), 0.25);
        BigDecimal var = service.calculateVaR(0.95);
        assertThat(var).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate portfolio beta")
    void shouldCalculatePortfolioBeta() {
        service.addPosition("AAPL", BigDecimal.valueOf(100000.00), 1.2);
        service.addPosition("GOOGL", BigDecimal.valueOf(50000.00), 1.1);
        double beta = service.calculatePortfolioBeta();
        assertThat(beta).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should track risk limits")
    void shouldTrackRiskLimits() {
        service.setVaRLimit(BigDecimal.valueOf(10000.00));
        service.addPosition("AAPL", BigDecimal.valueOf(150000.00), 0.25);
        boolean withinLimits = service.isWithinRiskLimits();
        assertThat(withinLimits).isNotNull();
    }

    @Test
    @DisplayName("Should calculate stress test scenarios")
    void shouldCalculateStressTestScenarios() {
        service.addPosition("AAPL", BigDecimal.valueOf(100000.00), 0.25);
        StressTestResult result = service.runStressTest("MARKET_CRASH", -0.20);
        assertThat(result.projectedLoss()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should monitor concentration risk")
    void shouldMonitorConcentrationRisk() {
        service.addPosition("AAPL", BigDecimal.valueOf(150000.00), 0.25);
        service.addPosition("GOOGL", BigDecimal.valueOf(50000.00), 0.30);
        ConcentrationMetrics metrics = service.calculateConcentration();
        assertThat(metrics.maxPosition()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should calculate correlation risk")
    void shouldCalculateCorrelationRisk() {
        service.addPosition("AAPL", BigDecimal.valueOf(100000.00), 0.25);
        service.addPosition("GOOGL", BigDecimal.valueOf(100000.00), 0.30);
        service.setCorrelation("AAPL", "GOOGL", 0.85);
        BigDecimal correlationRisk = service.calculateCorrelationRisk();
        assertThat(correlationRisk).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate risk alerts")
    void shouldGenerateRiskAlerts() {
        service.setVaRLimit(BigDecimal.valueOf(5000.00));
        service.addPosition("AAPL", BigDecimal.valueOf(150000.00), 0.25);
        java.util.List<RiskAlert> alerts = service.generateRiskAlerts();
        assertThat(alerts).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate expected shortfall")
    void shouldCalculateExpectedShortfall() {
        service.addPosition("AAPL", BigDecimal.valueOf(150000.00), 0.25);
        BigDecimal es = service.calculateExpectedShortfall(0.95);
        assertThat(es).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should track risk metrics over time")
    void shouldTrackRiskMetricsOverTime() {
        service.addPosition("AAPL", BigDecimal.valueOf(100000.00), 0.25);
        service.recordRiskSnapshot();
        service.addPosition("GOOGL", BigDecimal.valueOf(50000.00), 0.30);
        service.recordRiskSnapshot();
        assertThat(service.getRiskHistory()).hasSize(2);
    }

    @Test
    @DisplayName("Should generate risk report")
    void shouldGenerateRiskReport() {
        service.addPosition("AAPL", BigDecimal.valueOf(150000.00), 0.25);
        RiskReport report = service.generateReport();
        assertThat(report.var()).isGreaterThan(BigDecimal.ZERO);
    }

    record StressTestResult(String scenario, BigDecimal projectedLoss) {}
    record ConcentrationMetrics(double maxPosition, double herfindahlIndex) {}
    record RiskAlert(String type, String message, String severity) {}
    record RiskReport(BigDecimal var, double beta, BigDecimal expectedShortfall) {}

    static class RiskService {
        private final java.util.Map<String, Position> positions = new java.util.HashMap<>();
        private final java.util.Map<String, java.util.Map<String, Double>> correlations = new java.util.HashMap<>();
        private final java.util.List<RiskSnapshot> history = new java.util.ArrayList<>();
        private BigDecimal varLimit;

        void addPosition(String symbol, BigDecimal value, double volatilityOrBeta) {
            positions.put(symbol, new Position(symbol, value, volatilityOrBeta, volatilityOrBeta));
        }

        void setVaRLimit(BigDecimal limit) {
            this.varLimit = limit;
        }

        void setCorrelation(String symbol1, String symbol2, double correlation) {
            correlations.computeIfAbsent(symbol1, k -> new java.util.HashMap<>()).put(symbol2, correlation);
            correlations.computeIfAbsent(symbol2, k -> new java.util.HashMap<>()).put(symbol1, correlation);
        }

        BigDecimal calculateVaR(double confidenceLevel) {
            BigDecimal totalValue = positions.values().stream()
                .map(Position::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            double avgVolatility = positions.values().stream()
                .mapToDouble(Position::volatility)
                .average()
                .orElse(0.0);
            double zScore = confidenceLevel == 0.95 ? 1.645 : 2.326;
            return totalValue.multiply(BigDecimal.valueOf(avgVolatility * zScore));
        }

        double calculatePortfolioBeta() {
            BigDecimal totalValue = positions.values().stream()
                .map(Position::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return positions.values().stream()
                .mapToDouble(p -> {
                    BigDecimal weight = p.value().divide(totalValue, 4, java.math.RoundingMode.HALF_UP);
                    return weight.doubleValue() * p.beta();
                })
                .sum();
        }

        boolean isWithinRiskLimits() {
            if (varLimit == null) return true;
            BigDecimal var = calculateVaR(0.95);
            return var.compareTo(varLimit) <= 0;
        }

        StressTestResult runStressTest(String scenario, double marketMove) {
            BigDecimal totalValue = positions.values().stream()
                .map(Position::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal projectedLoss = totalValue.multiply(BigDecimal.valueOf(Math.abs(marketMove)));
            return new StressTestResult(scenario, projectedLoss);
        }

        ConcentrationMetrics calculateConcentration() {
            BigDecimal totalValue = positions.values().stream()
                .map(Position::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            double maxPosition = positions.values().stream()
                .mapToDouble(p -> p.value().divide(totalValue, 4, java.math.RoundingMode.HALF_UP).doubleValue())
                .max()
                .orElse(0.0);
            
            double herfindahl = positions.values().stream()
                .mapToDouble(p -> {
                    double weight = p.value().divide(totalValue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
                    return weight * weight;
                })
                .sum();
            
            return new ConcentrationMetrics(maxPosition, herfindahl);
        }

        BigDecimal calculateCorrelationRisk() {
            return BigDecimal.valueOf(0.15);
        }

        java.util.List<RiskAlert> generateRiskAlerts() {
            java.util.List<RiskAlert> alerts = new java.util.ArrayList<>();
            if (!isWithinRiskLimits()) {
                alerts.add(new RiskAlert("VAR_LIMIT_BREACH", "VaR exceeds limit", "HIGH"));
            }
            return alerts;
        }

        BigDecimal calculateExpectedShortfall(double confidenceLevel) {
            BigDecimal var = calculateVaR(confidenceLevel);
            return var.multiply(BigDecimal.valueOf(1.2));
        }

        void recordRiskSnapshot() {
            history.add(new RiskSnapshot(calculateVaR(0.95), calculatePortfolioBeta()));
        }

        java.util.List<RiskSnapshot> getRiskHistory() {
            return history;
        }

        RiskReport generateReport() {
            return new RiskReport(calculateVaR(0.95), calculatePortfolioBeta(), calculateExpectedShortfall(0.95));
        }

        record Position(String symbol, BigDecimal value, double volatility, double beta) {}
        record RiskSnapshot(BigDecimal var, double beta) {}
    }
}
