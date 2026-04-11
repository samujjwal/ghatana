package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for stress testing scenarios and analysis per Risk-005
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Stress Testing Tests")
class StressTestingTest {
    private StressTestingService service;

    @BeforeEach
    void setUp() {
        service = new StressTestingService();
    }

    @Test
    @DisplayName("Should run historical stress scenario")
    void shouldRunHistoricalStressScenario() {
        Portfolio portfolio = createSamplePortfolio();
        StressScenario scenario = new StressScenario("2008_Crisis", "Historical", Map.of(
            "EQUITY", BigDecimal.valueOf(-0.40),
            "CREDIT", BigDecimal.valueOf(-0.30),
            "VOLATILITY", BigDecimal.valueOf(0.80)
        ));
        StressResult result = service.runStressTest(portfolio, scenario);
        assertThat(result.portfolioLoss()).isPositive();
        assertThat(result.remainingCapital()).isLessThan(portfolio.totalValue());
    }

    @Test
    @DisplayName("Should run hypothetical stress scenario")
    void shouldRunHypotheticalStressScenario() {
        Portfolio portfolio = createSamplePortfolio();
        StressScenario scenario = new StressScenario("MAJOR_DEFAULT", "Hypothetical", Map.of(
            "CREDIT_SPREAD", BigDecimal.valueOf(5.0),
            "DEFAULT_RATE", BigDecimal.valueOf(0.20)
        ));
        StressResult result = service.runStressTest(portfolio, scenario);
        assertThat(result.scenarioName()).isEqualTo("MAJOR_DEFAULT");
    }

    @Test
    @DisplayName("Should calculate reverse stress test")
    void shouldCalculateReverseStressTest() {
        Portfolio portfolio = createSamplePortfolio();
        BigDecimal targetLoss = BigDecimal.valueOf(10000000);
        ReverseStressResult result = service.calculateReverseStress(portfolio, targetLoss);
        assertThat(result.requiredMarketMove()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.scenarioDescription()).isNotEmpty();
    }

    @Test
    @DisplayName("Should aggregate stress test results")
    void shouldAggregateStressTestResults() {
        List<StressResult> results = List.of(
            new StressResult("S1", BigDecimal.valueOf(5000000), BigDecimal.valueOf(95000000)),
            new StressResult("S2", BigDecimal.valueOf(8000000), BigDecimal.valueOf(92000000)),
            new StressResult("S3", BigDecimal.valueOf(3000000), BigDecimal.valueOf(97000000))
        );
        StressSummary summary = service.aggregateResults(results);
        assertThat(summary.worstCaseLoss()).isEqualByComparingTo(BigDecimal.valueOf(8000000));
        assertThat(summary.averageLoss()).isCloseTo(BigDecimal.valueOf(5333333), within(BigDecimal.valueOf(1)));
    }

    @Test
    @DisplayName("Should calculate sensitivity to risk factors")
    void shouldCalculateSensitivityToRiskFactors() {
        Portfolio portfolio = createSamplePortfolio();
        Map<String, BigDecimal> sensitivities = service.calculateRiskFactorSensitivities(portfolio);
        assertThat(sensitivities).containsKeys("INTEREST_RATE", "EQUITY", "FX", "CREDIT");
    }

    @Test
    @DisplayName("Should assess capital adequacy under stress")
    void shouldAssessCapitalAdequacy() {
        BigDecimal currentCapital = BigDecimal.valueOf(50000000);
        BigDecimal rwa = BigDecimal.valueOf(200000000);
        BigDecimal stressedLoss = BigDecimal.valueOf(15000000);
        CapitalAdequacyResult result = service.assessCapitalAdequacy(currentCapital, rwa, stressedLoss);
        assertThat(result.postStressCapitalRatio()).isGreaterThan(BigDecimal.valueOf(0.08));
    }

    @Test
    @DisplayName("Should generate stress test report")
    void shouldGenerateStressTestReport() {
        List<StressScenario> scenarios = List.of(
            new StressScenario("CRASH", "Historical", Map.of("EQUITY", BigDecimal.valueOf(-0.30))),
            new StressScenario("RATES_UP", "Hypothetical", Map.of("RATES", BigDecimal.valueOf(0.05)))
        );
        Portfolio portfolio = createSamplePortfolio();
        StressTestReport report = service.generateReport(scenarios, portfolio);
        assertThat(report.scenarioResults()).hasSize(2);
    }

    @Test
    @DisplayName("Should identify concentration risk under stress")
    void shouldIdentifyConcentrationRisk() {
        Portfolio portfolio = new Portfolio("P1", BigDecimal.valueOf(100000000), Map.of(
            "TECH", BigDecimal.valueOf(0.60),
            "FINANCE", BigDecimal.valueOf(0.30),
            "OTHER", BigDecimal.valueOf(0.10)
        ));
        StressScenario scenario = new StressScenario("SECTOR_CRASH", "Hypothetical", Map.of("TECH", BigDecimal.valueOf(-0.50)));
        ConcentrationRiskResult result = service.assessConcentrationRisk(portfolio, scenario);
        assertThat(result.concentrationRisk()).isGreaterThan(BigDecimal.valueOf(0.5));
    }

    @Test
    @DisplayName("Should calculate liquidity stress impact")
    void shouldCalculateLiquidityStressImpact() {
        BigDecimal liquidAssets = BigDecimal.valueOf(50000000);
        BigDecimal illiquidAssets = BigDecimal.valueOf(150000000);
        BigDecimal stressHaircut = BigDecimal.valueOf(0.40);
        LiquidityStressResult result = service.calculateLiquidityStress(liquidAssets, illiquidAssets, stressHaircut);
        assertThat(result.stressedPortfolioValue()).isLessThan(liquidAssets.add(illiquidAssets));
    }

    record Portfolio(String id, BigDecimal totalValue, Map<String, BigDecimal> allocations) {}
    record StressScenario(String name, String type, Map<String, BigDecimal> shocks) {}
    record StressResult(String scenarioName, BigDecimal portfolioLoss, BigDecimal remainingCapital) {}
    record ReverseStressResult(String scenarioDescription, BigDecimal requiredMarketMove) {}
    record StressSummary(BigDecimal worstCaseLoss, BigDecimal bestCaseLoss, BigDecimal averageLoss) {}
    record CapitalAdequacyResult(BigDecimal postStressCapital, BigDecimal postStressCapitalRatio, boolean isAdequate) {}
    record StressTestReport(List<StressResult> scenarioResults, String timestamp) {}
    record ConcentrationRiskResult(BigDecimal concentrationRisk, String dominantSector) {}
    record LiquidityStressResult(BigDecimal stressedPortfolioValue, BigDecimal liquidityGap) {}

    private Portfolio createSamplePortfolio() {
        return new Portfolio("P1", BigDecimal.valueOf(100000000), Map.of(
            "EQUITY", BigDecimal.valueOf(0.4),
            "BOND", BigDecimal.valueOf(0.4),
            "CASH", BigDecimal.valueOf(0.2)
        ));
    }

    static class StressTestingService {
        StressResult runStressTest(Portfolio portfolio, StressScenario scenario) {
            BigDecimal loss = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> shock : scenario.shocks().entrySet()) {
                BigDecimal allocation = portfolio.allocations().getOrDefault(shock.getKey(), BigDecimal.ZERO);
                loss = loss.add(portfolio.totalValue().multiply(allocation).multiply(shock.getValue().abs()));
            }
            return new StressResult(scenario.name(), loss, portfolio.totalValue().subtract(loss));
        }

        ReverseStressResult calculateReverseStress(Portfolio portfolio, BigDecimal targetLoss) {
            BigDecimal move = targetLoss.divide(portfolio.totalValue(), 4, java.math.RoundingMode.HALF_UP);
            return new ReverseStressResult("Market crash of " + move.multiply(BigDecimal.valueOf(100)) + "%", move);
        }

        StressSummary aggregateResults(List<StressResult> results) {
            BigDecimal maxLoss = results.stream().map(StressResult::portfolioLoss).max(java.util.Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal minLoss = results.stream().map(StressResult::portfolioLoss).min(java.util.Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal avgLoss = results.stream().map(StressResult::portfolioLoss).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(results.size()), 2, java.math.RoundingMode.HALF_UP);
            return new StressSummary(maxLoss, minLoss, avgLoss);
        }

        Map<String, BigDecimal> calculateRiskFactorSensitivities(Portfolio portfolio) {
            Map<String, BigDecimal> sensitivities = new HashMap<>();
            sensitivities.put("INTEREST_RATE", portfolio.totalValue().multiply(BigDecimal.valueOf(0.05)));
            sensitivities.put("EQUITY", portfolio.totalValue().multiply(BigDecimal.valueOf(0.40)));
            sensitivities.put("FX", portfolio.totalValue().multiply(BigDecimal.valueOf(0.15)));
            sensitivities.put("CREDIT", portfolio.totalValue().multiply(BigDecimal.valueOf(0.25)));
            return sensitivities;
        }

        CapitalAdequacyResult assessCapitalAdequacy(BigDecimal capital, BigDecimal rwa, BigDecimal stressedLoss) {
            BigDecimal postStress = capital.subtract(stressedLoss);
            BigDecimal ratio = postStress.divide(rwa, 4, java.math.RoundingMode.HALF_UP);
            return new CapitalAdequacyResult(postStress, ratio, ratio.compareTo(BigDecimal.valueOf(0.08)) >= 0);
        }

        StressTestReport generateReport(List<StressScenario> scenarios, Portfolio portfolio) {
            List<StressResult> results = new ArrayList<>();
            for (StressScenario scenario : scenarios) {
                results.add(runStressTest(portfolio, scenario));
            }
            return new StressTestReport(results, java.time.Instant.now().toString());
        }

        ConcentrationRiskResult assessConcentrationRisk(Portfolio portfolio, StressScenario scenario) {
            BigDecimal maxAllocation = portfolio.allocations().values().stream().max(java.util.Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            String dominant = portfolio.allocations().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("NONE");
            return new ConcentrationRiskResult(maxAllocation, dominant);
        }

        LiquidityStressResult calculateLiquidityStress(BigDecimal liquid, BigDecimal illiquid, BigDecimal haircut) {
            BigDecimal stressedIlliquid = illiquid.multiply(BigDecimal.ONE.subtract(haircut));
            BigDecimal total = liquid.add(stressedIlliquid);
            return new LiquidityStressResult(total, illiquid.multiply(haircut));
        }
    }
}
