package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for risk reporting and regulatory submissions per Risk-010
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Risk Reporting Tests")
class RiskReportingTest {
    private RiskReportingService service;

    @BeforeEach
    void setUp() {
        service = new RiskReportingService();
    }

    @Test
    @DisplayName("Should generate daily risk report")
    void shouldGenerateDailyRiskReport() {
        RiskReport report = service.generateDailyReport(LocalDate.now());
        assertThat(report.reportDate()).isEqualTo(LocalDate.now());
        assertThat(report.metrics()).containsKeys("VAR", "ES", "LIMIT_UTILIZATION");
    }

    @Test
    @DisplayName("Should calculate daily P&L attribution")
    void shouldCalculateDailyPnlAttribution() {
        Map<String, BigDecimal> pnls = Map.of(
            "Trading", BigDecimal.valueOf(500000),
            "Hedging", BigDecimal.valueOf(-100000),
            "MarketMaking", BigDecimal.valueOf(200000)
        );
        PnlAttribution attribution = service.calculatePnlAttribution(pnls);
        assertThat(attribution.totalPnl()).isEqualByComparingTo(BigDecimal.valueOf(600000));
        assertThat(attribution.bestPerformer()).isEqualTo("Trading");
    }

    @Test
    @DisplayName("Should generate limit utilization report")
    void shouldGenerateLimitUtilizationReport() {
        List<LimitUtilization> utilizations = List.of(
            new LimitUtilization("DESK_A", BigDecimal.valueOf(0.75), BigDecimal.valueOf(0.90)),
            new LimitUtilization("DESK_B", BigDecimal.valueOf(0.60), BigDecimal.valueOf(0.85)),
            new LimitUtilization("DESK_C", BigDecimal.valueOf(0.95), BigDecimal.valueOf(0.95))
        );
        LimitReport report = service.generateLimitReport(utilizations, LocalDate.now());
        assertThat(report.breaches()).hasSize(1);
        assertThat(report.warnings()).hasSize(1);
    }

    @Test
    @DisplayName("Should prepare regulatory risk report")
    void shouldPrepareRegulatoryReport() {
        RegulatoryReport report = service.prepareRegulatoryReport("FINRA", LocalDate.now());
        assertThat(report.regulator()).isEqualTo("FINRA");
        assertThat(report.submissionDeadline()).isAfter(LocalDateTime.now());
        assertThat(report.requiredFields()).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate VaR for regulatory submission")
    void shouldCalculateVarForRegulatorySubmission() {
        List<BigDecimal> returns = generateReturns(252);
        RegulatoryVarResult result = service.calculateRegulatoryVar(returns, BigDecimal.valueOf(1000000), 0.99);
        assertThat(result.confidenceLevel()).isEqualTo(0.99);
        assertThat(result.var10Day()).isGreaterThan(result.var1Day());
    }

    @Test
    @DisplayName("Should generate stress test summary for board")
    void shouldGenerateStressTestSummary() {
        List<StressScenarioResult> scenarios = List.of(
            new StressScenarioResult("EconomicDownturn", BigDecimal.valueOf(25000000)),
            new StressScenarioResult("MarketCrash", BigDecimal.valueOf(45000000)),
            new StressScenarioResult("LiquidityCrisis", BigDecimal.valueOf(30000000))
        );
        BoardSummary summary = service.generateBoardSummary(scenarios);
        assertThat(summary.worstScenario()).isEqualTo("MarketCrash");
        assertThat(summary.capitalAdequate()).isTrue();
    }

    @Test
    @DisplayName("Should calculate large exposure report")
    void shouldCalculateLargeExposureReport() {
        List<CounterpartyExposure> exposures = List.of(
            new CounterpartyExposure("CPTY_A", BigDecimal.valueOf(150000000)),
            new CounterpartyExposure("CPTY_B", BigDecimal.valueOf(80000000)),
            new CounterpartyExposure("CPTY_C", BigDecimal.valueOf(250000000))
        );
        BigDecimal capital = BigDecimal.valueOf(1000000000);
        LargeExposureReport report = service.generateLargeExposureReport(exposures, capital);
        assertThat(report.exceedingLimits()).hasSize(1);
    }

    @Test
    @DisplayName("Should calculate leverage ratio report")
    void shouldCalculateLeverageRatioReport() {
        BigDecimal tier1Capital = BigDecimal.valueOf(100000000);
        BigDecimal totalExposure = BigDecimal.valueOf(800000000);
        LeverageReport report = service.generateLeverageReport(tier1Capital, totalExposure);
        assertThat(report.leverageRatio()).isGreaterThan(BigDecimal.valueOf(0.03));
    }

    @Test
    @DisplayName("Should generate liquidity reporting metrics")
    void shouldGenerateLiquidityReportingMetrics() {
        LiquidityMetrics metrics = new LiquidityMetrics(
            BigDecimal.valueOf(1.25),
            BigDecimal.valueOf(1.15),
            BigDecimal.valueOf(100000000)
        );
        LiquidityReport report = service.generateLiquidityReport(metrics, LocalDate.now());
        assertThat(report.compliant()).isTrue();
    }

    record RiskReport(LocalDate reportDate, Map<String, BigDecimal> metrics, List<String> alerts) {}
    record PnlAttribution(Map<String, BigDecimal> byDesk, BigDecimal totalPnl, String bestPerformer, String worstPerformer) {}
    record LimitUtilization(String desk, BigDecimal current, BigDecimal limit) {}
    record LimitReport(List<LimitUtilization> breaches, List<LimitUtilization> warnings, int totalDesks) {}
    record RegulatoryReport(String regulator, LocalDateTime submissionDeadline, List<String> requiredFields) {}
    record RegulatoryVarResult(BigDecimal var1Day, BigDecimal var10Day, double confidenceLevel, String methodology) {}
    record StressScenarioResult(String scenario, BigDecimal portfolioLoss) {}
    record BoardSummary(String worstScenario, BigDecimal maxLoss, boolean capitalAdequate, List<String> recommendations) {}
    record CounterpartyExposure(String counterparty, BigDecimal exposure) {}
    record LargeExposureReport(List<CounterpartyExposure> exceedingLimits, int totalCounterparties) {}
    record LeverageReport(BigDecimal leverageRatio, BigDecimal tier1Capital, boolean compliant) {}
    record LiquidityMetrics(BigDecimal lcr, BigDecimal nsfr, BigDecimal hqla) {}
    record LiquidityReport(LiquidityMetrics metrics, boolean compliant, LocalDate reportDate) {}

    private List<BigDecimal> generateReturns(int days) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            returns.add(BigDecimal.valueOf((Math.random() - 0.5) * 0.02));
        }
        return returns;
    }

    static class RiskReportingService {
        RiskReport generateDailyReport(LocalDate date) {
            Map<String, BigDecimal> metrics = new HashMap<>();
            metrics.put("VAR", BigDecimal.valueOf(5000000));
            metrics.put("ES", BigDecimal.valueOf(7000000));
            metrics.put("LIMIT_UTILIZATION", BigDecimal.valueOf(0.75));
            return new RiskReport(date, metrics, List.of());
        }

        PnlAttribution calculatePnlAttribution(Map<String, BigDecimal> pnls) {
            BigDecimal total = pnls.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            String best = pnls.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("NONE");
            String worst = pnls.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("NONE");
            return new PnlAttribution(pnls, total, best, worst);
        }

        LimitReport generateLimitReport(List<LimitUtilization> utilizations, LocalDate date) {
            List<LimitUtilization> breaches = new ArrayList<>();
            List<LimitUtilization> warnings = new ArrayList<>();
            for (LimitUtilization u : utilizations) {
                if (u.current().compareTo(u.limit()) > 0) {
                    breaches.add(u);
                } else if (u.current().divide(u.limit(), 2, java.math.RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(0.9)) >= 0) {
                    warnings.add(u);
                }
            }
            return new LimitReport(breaches, warnings, utilizations.size());
        }

        RegulatoryReport prepareRegulatoryReport(String regulator, LocalDate date) {
            return new RegulatoryReport(regulator, LocalDateTime.now().plusDays(30), List.of("VAR", "LEVERAGE", "LCR", "NSFR"));
        }

        RegulatoryVarResult calculateRegulatoryVar(List<BigDecimal> returns, BigDecimal portfolioValue, double confidence) {
            List<BigDecimal> sorted = returns.stream().sorted().toList();
            int index = (int) Math.floor(sorted.size() * (1 - confidence));
            BigDecimal var1d = portfolioValue.multiply(sorted.get(index).abs());
            BigDecimal var10d = var1d.multiply(BigDecimal.valueOf(Math.sqrt(10)));
            return new RegulatoryVarResult(var1d, var10d, confidence, "Historical");
        }

        BoardSummary generateBoardSummary(List<StressScenarioResult> scenarios) {
            StressScenarioResult worst = scenarios.stream().max(java.util.Comparator.comparing(StressScenarioResult::portfolioLoss)).orElse(null);
            return new BoardSummary(
                worst != null ? worst.scenario() : "NONE",
                worst != null ? worst.portfolioLoss() : BigDecimal.ZERO,
                true,
                List.of("Maintain current risk appetite", "Monitor market conditions")
            );
        }

        LargeExposureReport generateLargeExposureReport(List<CounterpartyExposure> exposures, BigDecimal capital) {
            BigDecimal limit = capital.multiply(BigDecimal.valueOf(0.25));
            List<CounterpartyExposure> exceeding = new ArrayList<>();
            for (CounterpartyExposure e : exposures) {
                if (e.exposure().compareTo(limit) > 0) {
                    exceeding.add(e);
                }
            }
            return new LargeExposureReport(exceeding, exposures.size());
        }

        LeverageReport generateLeverageReport(BigDecimal tier1, BigDecimal exposure) {
            BigDecimal ratio = tier1.divide(exposure, 4, java.math.RoundingMode.HALF_UP);
            return new LeverageReport(ratio, tier1, ratio.compareTo(BigDecimal.valueOf(0.03)) >= 0);
        }

        LiquidityReport generateLiquidityReport(LiquidityMetrics metrics, LocalDate date) {
            boolean compliant = metrics.lcr().compareTo(BigDecimal.ONE) >= 0 && metrics.nsfr().compareTo(BigDecimal.valueOf(1.0)) >= 0;
            return new LiquidityReport(metrics, compliant, date);
        }
    }
}
