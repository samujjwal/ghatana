package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for risk dashboard metrics and analytics per Risk-008
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Risk Dashboard Tests")
class RiskDashboardTest {
    private RiskDashboardService service;

    @BeforeEach
    void setUp() {
        service = new RiskDashboardService();
    }

    @Test
    @DisplayName("Should calculate firm-wide risk summary")
    void shouldCalculateFirmWideRiskSummary() {
        Map<String, BigDecimal> risks = Map.of(
            "MARKET", BigDecimal.valueOf(5000000),
            "CREDIT", BigDecimal.valueOf(3000000),
            "OPERATIONAL", BigDecimal.valueOf(1000000),
            "LIQUIDITY", BigDecimal.valueOf(2000000)
        );
        RiskSummary summary = service.calculateFirmWideRisk(risks);
        assertThat(summary.totalRisk()).isEqualByComparingTo(BigDecimal.valueOf(11000000));
        assertThat(summary.dominantRiskType()).isEqualTo("MARKET");
    }

    @Test
    @DisplayName("Should aggregate risk by business unit")
    void shouldAggregateRiskByBusinessUnit() {
        List<BusinessUnitRisk> buRisks = List.of(
            new BusinessUnitRisk("Equities", BigDecimal.valueOf(3000000), BigDecimal.valueOf(0.15)),
            new BusinessUnitRisk("FixedIncome", BigDecimal.valueOf(4000000), BigDecimal.valueOf(0.12)),
            new BusinessUnitRisk("Derivatives", BigDecimal.valueOf(2500000), BigDecimal.valueOf(0.18))
        );
        Map<String, RiskMetrics> aggregated = service.aggregateByBusinessUnit(buRisks);
        assertThat(aggregated).hasSize(3);
    }

    @Test
    @DisplayName("Should calculate risk concentration metrics")
    void shouldCalculateRiskConcentration() {
        Map<String, BigDecimal> positionRisks = Map.of(
            "Position1", BigDecimal.valueOf(2000000),
            "Position2", BigDecimal.valueOf(1500000),
            "Position3", BigDecimal.valueOf(1000000),
            "Position4", BigDecimal.valueOf(500000)
        );
        ConcentrationMetrics metrics = service.calculateConcentration(positionRisks);
        assertThat(metrics.hhi()).isGreaterThan(BigDecimal.ZERO);
        assertThat(metrics.top5Concentration()).isLessThanOrEqualTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should calculate risk-adjusted return metrics")
    void shouldCalculateRiskAdjustedReturn() {
        BigDecimal pnl = BigDecimal.valueOf(10000000);
        BigDecimal var = BigDecimal.valueOf(5000000);
        RiskAdjustedReturn rar = service.calculateRiskAdjustedReturn(pnl, var);
        assertThat(rar.rovar()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
        assertThat(rar.raroc()).isPositive();
    }

    @Test
    @DisplayName("Should monitor real-time risk metrics")
    void shouldMonitorRealTimeRiskMetrics() {
        List<RiskMetric> metrics = List.of(
            new RiskMetric("VAR", BigDecimal.valueOf(5000000), LocalDateTime.now()),
            new RiskMetric("Delta", BigDecimal.valueOf(1000000), LocalDateTime.now()),
            new RiskMetric("Gamma", BigDecimal.valueOf(50000), LocalDateTime.now())
        );
        RealTimeRiskSnapshot snapshot = service.captureRealTimeMetrics(metrics);
        assertThat(snapshot.metrics()).hasSize(3);
        assertThat(snapshot.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should generate risk heatmap")
    void shouldGenerateRiskHeatmap() {
        Map<String, Map<String, BigDecimal>> riskGrid = Map.of(
            "EQUITY", Map.of("MARKET", BigDecimal.valueOf(3000000), "LIQUIDITY", BigDecimal.valueOf(1000000)),
            "BOND", Map.of("CREDIT", BigDecimal.valueOf(2000000), "RATE", BigDecimal.valueOf(1500000)),
            "FX", Map.of("MARKET", BigDecimal.valueOf(1000000), "LIQUIDITY", BigDecimal.valueOf(500000))
        );
        RiskHeatmap heatmap = service.generateHeatmap(riskGrid);
        assertThat(heatmap.cells()).hasSize(6);
    }

    @Test
    @DisplayName("Should track risk limit utilization over time")
    void shouldTrackLimitUtilizationOverTime() {
        List<LimitUtilizationPoint> history = List.of(
            new LimitUtilizationPoint(LocalDateTime.now().minusHours(4), BigDecimal.valueOf(0.7)),
            new LimitUtilizationPoint(LocalDateTime.now().minusHours(3), BigDecimal.valueOf(0.75)),
            new LimitUtilizationPoint(LocalDateTime.now().minusHours(2), BigDecimal.valueOf(0.8)),
            new LimitUtilizationPoint(LocalDateTime.now().minusHours(1), BigDecimal.valueOf(0.85))
        );
        LimitUtilizationTrend trend = service.analyzeLimitTrend(history);
        assertThat(trend.trendDirection()).isEqualTo("INCREASING");
    }

    @Test
    @DisplayName("Should calculate risk appetite utilization")
    void shouldCalculateRiskAppetiteUtilization() {
        Map<String, BigDecimal> riskAppetite = Map.of(
            "MARKET", BigDecimal.valueOf(10000000),
            "CREDIT", BigDecimal.valueOf(8000000),
            "OPERATIONAL", BigDecimal.valueOf(5000000)
        );
        Map<String, BigDecimal> currentRisks = Map.of(
            "MARKET", BigDecimal.valueOf(6000000),
            "CREDIT", BigDecimal.valueOf(4000000),
            "OPERATIONAL", BigDecimal.valueOf(2000000)
        );
        RiskAppetiteUtilization utilization = service.calculateRiskAppetiteUtilization(riskAppetite, currentRisks);
        assertThat(utilization.overallUtilization()).isLessThan(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should generate executive risk report")
    void shouldGenerateExecutiveRiskReport() {
        ExecutiveRiskReport report = service.generateExecutiveReport(
            BigDecimal.valueOf(10000000),
            BigDecimal.valueOf(8000000),
            List.of("MARKET", "CREDIT"),
            5
        );
        assertThat(report.totalRiskExposure()).isPositive();
        assertThat(report.topRisks()).isNotEmpty();
    }

    record RiskSummary(BigDecimal totalRisk, String dominantRiskType, Map<String, BigDecimal> breakdown) {}
    record BusinessUnitRisk(String name, BigDecimal var, BigDecimal utilization) {}
    record RiskMetrics(BigDecimal var, BigDecimal es, BigDecimal notional) {}
    record ConcentrationMetrics(BigDecimal hhi, BigDecimal top5Concentration, BigDecimal entropy) {}
    record RiskAdjustedReturn(BigDecimal rovar, BigDecimal raroc) {}
    record RiskMetric(String name, BigDecimal value, LocalDateTime timestamp) {}
    record RealTimeRiskSnapshot(List<RiskMetric> metrics, LocalDateTime timestamp) {}
    record RiskHeatmap(List<HeatmapCell> cells, BigDecimal maxValue) {}
    record HeatmapCell(String row, String column, BigDecimal value, String color) {}
    record LimitUtilizationPoint(LocalDateTime timestamp, BigDecimal utilization) {}
    record LimitUtilizationTrend(String trendDirection, double slope, BigDecimal projectedUtilization) {}
    record RiskAppetiteUtilization(Map<String, BigDecimal> byType, BigDecimal overallUtilization) {}
    record ExecutiveRiskReport(BigDecimal totalRiskExposure, BigDecimal capitalUtilized, List<String> topRisks, int openIssues) {}

    static class RiskDashboardService {
        RiskSummary calculateFirmWideRisk(Map<String, BigDecimal> risks) {
            BigDecimal total = risks.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            String dominant = risks.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("NONE");
            return new RiskSummary(total, dominant, risks);
        }

        Map<String, RiskMetrics> aggregateByBusinessUnit(List<BusinessUnitRisk> buRisks) {
            Map<String, RiskMetrics> result = new HashMap<>();
            for (BusinessUnitRisk bu : buRisks) {
                result.put(bu.name(), new RiskMetrics(bu.var(), bu.var().multiply(BigDecimal.valueOf(1.2)), bu.var().multiply(BigDecimal.valueOf(10))));
            }
            return result;
        }

        ConcentrationMetrics calculateConcentration(Map<String, BigDecimal> positionRisks) {
            BigDecimal total = positionRisks.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal hhi = BigDecimal.ZERO;
            for (BigDecimal risk : positionRisks.values()) {
                BigDecimal share = risk.divide(total, 4, java.math.RoundingMode.HALF_UP);
                hhi = hhi.add(share.multiply(share));
            }
            List<BigDecimal> sorted = positionRisks.values().stream().sorted(java.util.Comparator.reverseOrder()).toList();
            BigDecimal top5 = sorted.stream().limit(5).reduce(BigDecimal.ZERO, BigDecimal::add).divide(total, 4, java.math.RoundingMode.HALF_UP);
            return new ConcentrationMetrics(hhi, top5, BigDecimal.valueOf(-hhi.doubleValue() * Math.log(hhi.doubleValue())));
        }

        RiskAdjustedReturn calculateRiskAdjustedReturn(BigDecimal pnl, BigDecimal var) {
            BigDecimal rovar = pnl.divide(var, 2, java.math.RoundingMode.HALF_UP);
            BigDecimal raroc = pnl.divide(var.multiply(BigDecimal.valueOf(2)), 2, java.math.RoundingMode.HALF_UP);
            return new RiskAdjustedReturn(rovar, raroc);
        }

        RealTimeRiskSnapshot captureRealTimeMetrics(List<RiskMetric> metrics) {
            return new RealTimeRiskSnapshot(metrics, LocalDateTime.now());
        }

        RiskHeatmap generateHeatmap(Map<String, Map<String, BigDecimal>> riskGrid) {
            List<HeatmapCell> cells = new ArrayList<>();
            BigDecimal max = BigDecimal.ZERO;
            for (String row : riskGrid.keySet()) {
                for (Map.Entry<String, BigDecimal> col : riskGrid.get(row).entrySet()) {
                    BigDecimal value = col.getValue();
                    if (value.compareTo(max) > 0) max = value;
                    String color = value.compareTo(BigDecimal.valueOf(2000000)) > 0 ? "RED" : value.compareTo(BigDecimal.valueOf(1000000)) > 0 ? "YELLOW" : "GREEN";
                    cells.add(new HeatmapCell(row, col.getKey(), value, color));
                }
            }
            return new RiskHeatmap(cells, max);
        }

        LimitUtilizationTrend analyzeLimitTrend(List<LimitUtilizationPoint> history) {
            if (history.size() < 2) return new LimitUtilizationTrend("FLAT", 0, BigDecimal.ZERO);
            BigDecimal first = history.get(0).utilization();
            BigDecimal last = history.get(history.size() - 1).utilization();
            String direction = last.compareTo(first) > 0 ? "INCREASING" : last.compareTo(first) < 0 ? "DECREASING" : "FLAT";
            double slope = last.subtract(first).doubleValue() / (history.size() - 1);
            return new LimitUtilizationTrend(direction, slope, last.add(BigDecimal.valueOf(slope)));
        }

        RiskAppetiteUtilization calculateRiskAppetiteUtilization(Map<String, BigDecimal> appetite, Map<String, BigDecimal> current) {
            Map<String, BigDecimal> byType = new HashMap<>();
            BigDecimal totalUtilization = BigDecimal.ZERO;
            int count = 0;
            for (String type : appetite.keySet()) {
                BigDecimal app = appetite.get(type);
                BigDecimal curr = current.getOrDefault(type, BigDecimal.ZERO);
                BigDecimal util = curr.divide(app, 4, java.math.RoundingMode.HALF_UP);
                byType.put(type, util);
                totalUtilization = totalUtilization.add(util);
                count++;
            }
            return new RiskAppetiteUtilization(byType, count > 0 ? totalUtilization.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }

        ExecutiveRiskReport generateExecutiveReport(BigDecimal exposure, BigDecimal capital, List<String> topRisks, int issues) {
            return new ExecutiveRiskReport(exposure, capital, topRisks, issues);
        }
    }
}
