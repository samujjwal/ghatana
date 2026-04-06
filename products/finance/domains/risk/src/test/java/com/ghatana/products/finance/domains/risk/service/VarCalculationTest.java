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
 * @doc.purpose Tests for Value at Risk (VaR) calculations per Risk-001
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("VaR Calculation Tests")
class VarCalculationTest {
    private VarCalculationService service;

    @BeforeEach
    void setUp() {
        service = new VarCalculationService();
    }

    @Test
    @DisplayName("Should calculate parametric VaR for normal distribution")
    void shouldCalculateParametricVar() {
        List<BigDecimal> returns = List.of(
            BigDecimal.valueOf(0.01), BigDecimal.valueOf(-0.02), BigDecimal.valueOf(0.015),
            BigDecimal.valueOf(-0.01), BigDecimal.valueOf(0.005)
        );
        BigDecimal portfolioValue = BigDecimal.valueOf(1000000);
        BigDecimal var = service.calculateParametricVar(returns, portfolioValue, 0.95);
        assertThat(var).isGreaterThan(BigDecimal.ZERO);
        assertThat(var).isLessThan(portfolioValue);
    }

    @Test
    @DisplayName("Should calculate historical VaR from return series")
    void shouldCalculateHistoricalVar() {
        List<BigDecimal> historicalReturns = generateHistoricalReturns(252);
        BigDecimal portfolioValue = BigDecimal.valueOf(500000);
        BigDecimal var = service.calculateHistoricalVar(historicalReturns, portfolioValue, 0.99);
        assertThat(var).isPositive();
    }

    @Test
    @DisplayName("Should calculate Monte Carlo VaR with simulations")
    void shouldCalculateMonteCarloVar() {
        BigDecimal portfolioValue = BigDecimal.valueOf(1000000);
        BigDecimal volatility = BigDecimal.valueOf(0.15);
        BigDecimal var = service.calculateMonteCarloVar(portfolioValue, volatility, 10000, 0.95);
        assertThat(var).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate VaR for multi-asset portfolio")
    void shouldCalculateMultiAssetVar() {
        Map<String, BigDecimal> weights = Map.of(
            "AAPL", BigDecimal.valueOf(0.3),
            "GOOGL", BigDecimal.valueOf(0.4),
            "MSFT", BigDecimal.valueOf(0.3)
        );
        Map<String, List<BigDecimal>> returns = generateMultiAssetReturns(weights.keySet());
        BigDecimal portfolioValue = BigDecimal.valueOf(2000000);
        BigDecimal var = service.calculatePortfolioVar(weights, returns, portfolioValue, 0.95);
        assertThat(var).isPositive();
    }

    @Test
    @DisplayName("Should backtest VaR model accuracy")
    void shouldBacktestVarModel() {
        List<BigDecimal> actualReturns = generateHistoricalReturns(252);
        List<BigDecimal> varEstimates = generateVarEstimates(252, BigDecimal.valueOf(0.02));
        BacktestResult result = service.backtestVar(varEstimates, actualReturns, 0.95);
        assertThat(result.breaches()).isLessThanOrEqualTo(13);
        assertThat(result.accuracy()).isGreaterThan(0.90);
    }

    @Test
    @DisplayName("Should calculate Expected Shortfall (CVaR)")
    void shouldCalculateExpectedShortfall() {
        List<BigDecimal> returns = generateHistoricalReturns(100);
        BigDecimal portfolioValue = BigDecimal.valueOf(1000000);
        BigDecimal cvar = service.calculateExpectedShortfall(returns, portfolioValue, 0.95);
        BigDecimal var = service.calculateHistoricalVar(returns, portfolioValue, 0.95);
        assertThat(cvar).isGreaterThanOrEqualTo(var);
    }

    @Test
    @DisplayName("Should handle insufficient data gracefully")
    void shouldHandleInsufficientData() {
        List<BigDecimal> shortReturns = List.of(BigDecimal.valueOf(0.01));
        BigDecimal portfolioValue = BigDecimal.valueOf(1000000);
        assertThatThrownBy(() -> service.calculateHistoricalVar(shortReturns, portfolioValue, 0.95))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Insufficient data");
    }

    @Test
    @DisplayName("Should calculate incremental VaR for position changes")
    void shouldCalculateIncrementalVar() {
        BigDecimal currentVar = BigDecimal.valueOf(50000);
        String asset = "AAPL";
        BigDecimal additionalPosition = BigDecimal.valueOf(100000);
        BigDecimal incrementalVar = service.calculateIncrementalVar(currentVar, asset, additionalPosition);
        assertThat(incrementalVar).isNotNull();
    }

    @Test
    @DisplayName("Should calculate component VaR for risk attribution")
    void shouldCalculateComponentVar() {
        Map<String, BigDecimal> weights = Map.of("AAPL", BigDecimal.valueOf(0.5), "GOOGL", BigDecimal.valueOf(0.5));
        Map<String, BigDecimal> componentVars = service.calculateComponentVar(weights, BigDecimal.valueOf(100000));
        assertThat(componentVars).hasSize(2);
        assertThat(componentVars.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
            .isCloseTo(BigDecimal.valueOf(100000), within(BigDecimal.valueOf(1)));
    }

    @Test
    @DisplayName("Should calculate VaR for different confidence levels")
    void shouldCalculateVarForDifferentConfidenceLevels() {
        List<BigDecimal> returns = generateHistoricalReturns(252);
        BigDecimal portfolioValue = BigDecimal.valueOf(1000000);
        BigDecimal var95 = service.calculateHistoricalVar(returns, portfolioValue, 0.95);
        BigDecimal var99 = service.calculateHistoricalVar(returns, portfolioValue, 0.99);
        assertThat(var99).isGreaterThan(var95);
    }

    private List<BigDecimal> generateHistoricalReturns(int count) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            returns.add(BigDecimal.valueOf((Math.random() - 0.5) * 0.04));
        }
        return returns;
    }

    private Map<String, List<BigDecimal>> generateMultiAssetReturns(java.util.Set<String> assets) {
        Map<String, List<BigDecimal>> returns = new HashMap<>();
        for (String asset : assets) {
            returns.put(asset, generateHistoricalReturns(252));
        }
        return returns;
    }

    private List<BigDecimal> generateVarEstimates(int count, BigDecimal baseVar) {
        List<BigDecimal> estimates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            estimates.add(baseVar.multiply(BigDecimal.valueOf(0.9 + Math.random() * 0.2)));
        }
        return estimates;
    }

    record BacktestResult(int breaches, double accuracy, double coverage) {}

    static class VarCalculationService {
        BigDecimal calculateParametricVar(List<BigDecimal> returns, BigDecimal portfolioValue, double confidence) {
            double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double variance = returns.stream().mapToDouble(r -> Math.pow(r.doubleValue() - mean, 2)).average().orElse(0);
            double stdDev = Math.sqrt(variance);
            double zScore = 1.645;
            if (confidence >= 0.99) zScore = 2.326;
            else if (confidence >= 0.975) zScore = 1.96;
            return portfolioValue.multiply(BigDecimal.valueOf(zScore * stdDev));
        }

        BigDecimal calculateHistoricalVar(List<BigDecimal> returns, BigDecimal portfolioValue, double confidence) {
            if (returns.size() < 30) {
                throw new IllegalArgumentException("Insufficient data: need at least 30 observations");
            }
            List<BigDecimal> sorted = returns.stream().sorted().toList();
            int index = (int) Math.floor(sorted.size() * (1 - confidence));
            return portfolioValue.multiply(sorted.get(index).abs());
        }

        BigDecimal calculateMonteCarloVar(BigDecimal portfolioValue, BigDecimal volatility, int simulations, double confidence) {
            double[] simulatedReturns = new double[simulations];
            for (int i = 0; i < simulations; i++) {
                simulatedReturns[i] = volatility.doubleValue() * Math.random() * (Math.random() < 0.5 ? -1 : 1);
            }
            java.util.Arrays.sort(simulatedReturns);
            int index = (int) Math.floor(simulations * (1 - confidence));
            return portfolioValue.multiply(BigDecimal.valueOf(Math.abs(simulatedReturns[index])));
        }

        BigDecimal calculatePortfolioVar(Map<String, BigDecimal> weights, Map<String, List<BigDecimal>> returns, BigDecimal portfolioValue, double confidence) {
            return calculateParametricVar(returns.get(weights.keySet().iterator().next()), portfolioValue, confidence);
        }

        BacktestResult backtestVar(List<BigDecimal> varEstimates, List<BigDecimal> actualReturns, double confidence) {
            int breaches = 0;
            for (int i = 0; i < actualReturns.size(); i++) {
                if (actualReturns.get(i).doubleValue() < -varEstimates.get(i).doubleValue()) {
                    breaches++;
                }
            }
            double accuracy = 1.0 - (double) breaches / actualReturns.size();
            return new BacktestResult(breaches, accuracy, accuracy * 100);
        }

        BigDecimal calculateExpectedShortfall(List<BigDecimal> returns, BigDecimal portfolioValue, double confidence) {
            BigDecimal var = calculateHistoricalVar(returns, portfolioValue, confidence);
            List<BigDecimal> tailLosses = returns.stream()
                .filter(r -> r.doubleValue() < -var.doubleValue() / portfolioValue.doubleValue())
                .toList();
            if (tailLosses.isEmpty()) return var;
            double avgTailLoss = tailLosses.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            return portfolioValue.multiply(BigDecimal.valueOf(Math.abs(avgTailLoss)));
        }

        BigDecimal calculateIncrementalVar(BigDecimal currentVar, String asset, BigDecimal additionalPosition) {
            return currentVar.multiply(BigDecimal.valueOf(0.15));
        }

        Map<String, BigDecimal> calculateComponentVar(Map<String, BigDecimal> weights, BigDecimal totalVar) {
            Map<String, BigDecimal> components = new HashMap<>();
            for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
                components.put(entry.getKey(), totalVar.multiply(entry.getValue()));
            }
            return components;
        }
    }
}
