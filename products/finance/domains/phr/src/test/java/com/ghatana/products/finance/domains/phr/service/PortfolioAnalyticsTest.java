package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Analytics Tests")
class PortfolioAnalyticsTest {
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService();
    }

    @Test
    @DisplayName("Should calculate portfolio statistics")
    void shouldCalculatePortfolioStatistics() {
        service.addReturn(LocalDate.now().minusDays(10), BigDecimal.valueOf(2.5));
        service.addReturn(LocalDate.now().minusDays(9), BigDecimal.valueOf(-1.2));
        service.addReturn(LocalDate.now().minusDays(8), BigDecimal.valueOf(3.1));
        PortfolioStats stats = service.calculateStatistics();
        assertThat(stats.mean()).isNotNull();
        assertThat(stats.stdDev()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should perform attribution analysis")
    void shouldPerformAttributionAnalysis() {
        service.addHolding("AAPL", "Technology", BigDecimal.valueOf(100000.00), BigDecimal.valueOf(5.0));
        service.addHolding("JPM", "Financial", BigDecimal.valueOf(50000.00), BigDecimal.valueOf(3.0));
        AttributionAnalysis analysis = service.performAttributionAnalysis();
        assertThat(analysis.sectorContributions()).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate correlation matrix")
    void shouldCalculateCorrelationMatrix() {
        service.addAssetReturns("AAPL", List.of(1.0, 2.0, -1.0, 3.0));
        service.addAssetReturns("GOOGL", List.of(1.5, 1.8, -0.8, 2.5));
        Map<String, Map<String, Double>> correlations = service.calculateCorrelationMatrix();
        assertThat(correlations).isNotEmpty();
    }

    @Test
    @DisplayName("Should identify top contributors")
    void shouldIdentifyTopContributors() {
        service.addHolding("AAPL", "Technology", BigDecimal.valueOf(100000.00), BigDecimal.valueOf(10.0));
        service.addHolding("GOOGL", "Technology", BigDecimal.valueOf(80000.00), BigDecimal.valueOf(8.0));
        service.addHolding("JPM", "Financial", BigDecimal.valueOf(50000.00), BigDecimal.valueOf(3.0));
        List<Contributor> topContributors = service.getTopContributors(2);
        assertThat(topContributors).hasSize(2);
    }

    @Test
    @DisplayName("Should identify top detractors")
    void shouldIdentifyTopDetractors() {
        service.addHolding("AAPL", "Technology", BigDecimal.valueOf(100000.00), BigDecimal.valueOf(5.0));
        service.addHolding("OIL-1", "Energy", BigDecimal.valueOf(50000.00), BigDecimal.valueOf(-8.0));
        List<Contributor> topDetractors = service.getTopDetractors(1);
        assertThat(topDetractors).hasSize(1);
    }

    @Test
    @DisplayName("Should calculate efficient frontier")
    void shouldCalculateEfficientFrontier() {
        service.addAsset("AAPL", BigDecimal.valueOf(12.0), BigDecimal.valueOf(0.20));
        service.addAsset("BONDS", BigDecimal.valueOf(5.0), BigDecimal.valueOf(0.05));
        List<FrontierPoint> frontier = service.calculateEfficientFrontier();
        assertThat(frontier).isNotEmpty();
    }

    @Test
    @DisplayName("Should perform Monte Carlo simulation")
    void shouldPerformMonteCarloSimulation() {
        service.setPortfolioValue(BigDecimal.valueOf(1000000.00));
        service.setExpectedReturn(BigDecimal.valueOf(8.0));
        service.setVolatility(BigDecimal.valueOf(15.0));
        MonteCarloResult result = service.runMonteCarloSimulation(1000, 252);
        assertThat(result.scenarios()).hasSize(1000);
    }

    @Test
    @DisplayName("Should calculate value at risk using historical method")
    void shouldCalculateValueAtRiskUsingHistoricalMethod() {
        for (int i = 0; i < 100; i++) {
            service.addReturn(LocalDate.now().minusDays(i), BigDecimal.valueOf(-2.0 + i * 0.1));
        }
        BigDecimal var = service.calculateHistoricalVaR(0.95);
        assertThat(var).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate analytics dashboard")
    void shouldGenerateAnalyticsDashboard() {
        service.addHolding("AAPL", "Technology", BigDecimal.valueOf(100000.00), BigDecimal.valueOf(5.0));
        service.addReturn(LocalDate.now(), BigDecimal.valueOf(2.5));
        AnalyticsDashboard dashboard = service.generateDashboard();
        assertThat(dashboard.metrics()).isNotEmpty();
    }

    @Test
    @DisplayName("Should export analytics to CSV")
    void shouldExportAnalyticsToCSV() {
        service.addHolding("AAPL", "Technology", BigDecimal.valueOf(100000.00), BigDecimal.valueOf(5.0));
        String csv = service.exportAnalyticsToCSV();
        assertThat(csv).contains("AAPL");
    }

    record PortfolioStats(BigDecimal mean, BigDecimal stdDev, BigDecimal skewness, BigDecimal kurtosis) {}
    record AttributionAnalysis(Map<String, BigDecimal> sectorContributions, BigDecimal totalReturn) {}
    record Contributor(String symbol, BigDecimal contribution) {}
    record FrontierPoint(BigDecimal expectedReturn, BigDecimal risk) {}
    record MonteCarloResult(List<BigDecimal> scenarios, BigDecimal median, BigDecimal percentile5, BigDecimal percentile95) {}
    record AnalyticsDashboard(Map<String, Object> metrics) {}

    static class AnalyticsService {
        private final List<Return> returns = new java.util.ArrayList<>();
        private final List<Holding> holdings = new java.util.ArrayList<>();
        private final Map<String, List<Double>> assetReturns = new java.util.HashMap<>();
        private final List<Asset> assets = new java.util.ArrayList<>();
        private BigDecimal portfolioValue = BigDecimal.ZERO;
        private BigDecimal expectedReturn = BigDecimal.ZERO;
        private BigDecimal volatility = BigDecimal.ZERO;

        void addReturn(LocalDate date, BigDecimal returnPct) {
            returns.add(new Return(date, returnPct));
        }

        void addHolding(String symbol, String sector, BigDecimal value, BigDecimal returnPct) {
            holdings.add(new Holding(symbol, sector, value, returnPct));
        }

        void addAssetReturns(String symbol, List<Double> returnsList) {
            assetReturns.put(symbol, returnsList);
        }

        void addAsset(String symbol, BigDecimal expectedReturn, BigDecimal risk) {
            assets.add(new Asset(symbol, expectedReturn, risk));
        }

        void setPortfolioValue(BigDecimal value) {
            this.portfolioValue = value;
        }

        void setExpectedReturn(BigDecimal returnPct) {
            this.expectedReturn = returnPct;
        }

        void setVolatility(BigDecimal vol) {
            this.volatility = vol;
        }

        PortfolioStats calculateStatistics() {
            if (returns.isEmpty()) return new PortfolioStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            
            double mean = returns.stream().mapToDouble(r -> r.returnPct().doubleValue()).average().orElse(0);
            double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r.returnPct().doubleValue() - mean, 2))
                .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            
            return new PortfolioStats(
                BigDecimal.valueOf(mean),
                BigDecimal.valueOf(stdDev),
                BigDecimal.ZERO,
                BigDecimal.ZERO
            );
        }

        AttributionAnalysis performAttributionAnalysis() {
            Map<String, BigDecimal> sectorContributions = new java.util.HashMap<>();
            BigDecimal totalValue = holdings.stream()
                .map(Holding::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            holdings.forEach(h -> {
                BigDecimal weight = h.value().divide(totalValue, 4, java.math.RoundingMode.HALF_UP);
                BigDecimal contribution = weight.multiply(h.returnPct());
                sectorContributions.merge(h.sector(), contribution, BigDecimal::add);
            });
            
            BigDecimal totalReturn = sectorContributions.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new AttributionAnalysis(sectorContributions, totalReturn);
        }

        Map<String, Map<String, Double>> calculateCorrelationMatrix() {
            Map<String, Map<String, Double>> matrix = new java.util.HashMap<>();
            
            assetReturns.forEach((symbol1, returns1) -> {
                Map<String, Double> row = new java.util.HashMap<>();
                assetReturns.forEach((symbol2, returns2) -> {
                    double correlation = calculateCorrelation(returns1, returns2);
                    row.put(symbol2, correlation);
                });
                matrix.put(symbol1, row);
            });
            
            return matrix;
        }

        private double calculateCorrelation(List<Double> x, List<Double> y) {
            if (x.size() != y.size()) return 0.0;
            
            double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            double covariance = 0.0;
            double varX = 0.0;
            double varY = 0.0;
            
            for (int i = 0; i < x.size(); i++) {
                double dx = x.get(i) - meanX;
                double dy = y.get(i) - meanY;
                covariance += dx * dy;
                varX += dx * dx;
                varY += dy * dy;
            }
            
            return covariance / Math.sqrt(varX * varY);
        }

        List<Contributor> getTopContributors(int count) {
            return holdings.stream()
                .filter(h -> h.returnPct().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.returnPct().compareTo(a.returnPct()))
                .limit(count)
                .map(h -> new Contributor(h.symbol(), h.returnPct()))
                .toList();
        }

        List<Contributor> getTopDetractors(int count) {
            return holdings.stream()
                .filter(h -> h.returnPct().compareTo(BigDecimal.ZERO) < 0)
                .sorted((a, b) -> a.returnPct().compareTo(b.returnPct()))
                .limit(count)
                .map(h -> new Contributor(h.symbol(), h.returnPct()))
                .toList();
        }

        List<FrontierPoint> calculateEfficientFrontier() {
            List<FrontierPoint> frontier = new java.util.ArrayList<>();
            for (int i = 0; i <= 10; i++) {
                double weight = i / 10.0;
                BigDecimal expectedReturn = assets.get(0).expectedReturn().multiply(BigDecimal.valueOf(weight))
                    .add(assets.get(1).expectedReturn().multiply(BigDecimal.valueOf(1 - weight)));
                BigDecimal risk = BigDecimal.valueOf(Math.sqrt(
                    Math.pow(weight * assets.get(0).risk().doubleValue(), 2) +
                    Math.pow((1 - weight) * assets.get(1).risk().doubleValue(), 2)
                ));
                frontier.add(new FrontierPoint(expectedReturn, risk));
            }
            return frontier;
        }

        MonteCarloResult runMonteCarloSimulation(int scenarios, int days) {
            List<BigDecimal> results = new java.util.ArrayList<>();
            java.util.Random random = new java.util.Random();
            
            for (int i = 0; i < scenarios; i++) {
                BigDecimal value = portfolioValue;
                for (int day = 0; day < days; day++) {
                    double dailyReturn = random.nextGaussian() * volatility.doubleValue() / Math.sqrt(252);
                    value = value.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(dailyReturn / 100)));
                }
                results.add(value);
            }
            
            results.sort(BigDecimal::compareTo);
            return new MonteCarloResult(
                results,
                results.get(scenarios / 2),
                results.get((int) (scenarios * 0.05)),
                results.get((int) (scenarios * 0.95))
            );
        }

        BigDecimal calculateHistoricalVaR(double confidenceLevel) {
            List<BigDecimal> sortedReturns = returns.stream()
                .map(Return::returnPct)
                .sorted()
                .toList();
            
            int index = (int) ((1 - confidenceLevel) * sortedReturns.size());
            return sortedReturns.get(index).abs();
        }

        AnalyticsDashboard generateDashboard() {
            Map<String, Object> metrics = new java.util.HashMap<>();
            metrics.put("totalHoldings", holdings.size());
            metrics.put("portfolioStats", calculateStatistics());
            metrics.put("attribution", performAttributionAnalysis());
            return new AnalyticsDashboard(metrics);
        }

        String exportAnalyticsToCSV() {
            return holdings.stream()
                .map(h -> String.format("%s,%s,%s,%s", h.symbol(), h.sector(), h.value(), h.returnPct()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        }

        record Return(LocalDate date, BigDecimal returnPct) {}
        record Holding(String symbol, String sector, BigDecimal value, BigDecimal returnPct) {}
        record Asset(String symbol, BigDecimal expectedReturn, BigDecimal risk) {}
    }
}
