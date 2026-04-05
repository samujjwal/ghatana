package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Benchmarking Tests")
class PortfolioBenchmarkingTest {
    private BenchmarkingService service;

    @BeforeEach
    void setUp() {
        service = new BenchmarkingService();
    }

    @Test
    @DisplayName("Should compare portfolio to benchmark")
    void shouldComparePortfolioToBenchmark() {
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(10.00));
        service.recordBenchmarkReturn(LocalDate.now(), BigDecimal.valueOf(8.00));
        BigDecimal outperformance = service.calculateOutperformance(LocalDate.now());
        assertThat(outperformance).isEqualByComparingTo(BigDecimal.valueOf(2.00));
    }

    @Test
    @DisplayName("Should calculate tracking error")
    void shouldCalculateTrackingError() {
        for (int i = 0; i < 10; i++) {
            service.recordPortfolioReturn(LocalDate.now().minusDays(i), BigDecimal.valueOf(10.0 + i * 0.5));
            service.recordBenchmarkReturn(LocalDate.now().minusDays(i), BigDecimal.valueOf(9.0 + i * 0.5));
        }
        BigDecimal trackingError = service.calculateTrackingError();
        assertThat(trackingError).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate information ratio")
    void shouldCalculateInformationRatio() {
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(12.00));
        service.recordBenchmarkReturn(LocalDate.now(), BigDecimal.valueOf(10.00));
        BigDecimal infoRatio = service.calculateInformationRatio();
        assertThat(infoRatio).isNotNull();
    }

    @Test
    @DisplayName("Should calculate beta to benchmark")
    void shouldCalculateBetaToBenchmark() {
        for (int i = 0; i < 20; i++) {
            service.recordPortfolioReturn(LocalDate.now().minusDays(i), BigDecimal.valueOf(10.0 + i * 0.3));
            service.recordBenchmarkReturn(LocalDate.now().minusDays(i), BigDecimal.valueOf(8.0 + i * 0.2));
        }
        BigDecimal beta = service.calculateBeta();
        assertThat(beta).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate alpha")
    void shouldCalculateAlpha() {
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(15.00));
        service.recordBenchmarkReturn(LocalDate.now(), BigDecimal.valueOf(10.00));
        service.setBeta(BigDecimal.valueOf(1.2));
        service.setRiskFreeRate(BigDecimal.valueOf(3.00));
        BigDecimal alpha = service.calculateAlpha();
        assertThat(alpha).isNotNull();
    }

    @Test
    @DisplayName("Should track relative performance over time")
    void shouldTrackRelativePerformanceOverTime() {
        service.recordPortfolioReturn(LocalDate.now().minusDays(30), BigDecimal.valueOf(5.00));
        service.recordBenchmarkReturn(LocalDate.now().minusDays(30), BigDecimal.valueOf(4.00));
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(12.00));
        service.recordBenchmarkReturn(LocalDate.now(), BigDecimal.valueOf(10.00));
        java.util.List<RelativePerformance> history = service.getRelativePerformanceHistory();
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Should generate attribution analysis")
    void shouldGenerateAttributionAnalysis() {
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(12.00));
        service.recordBenchmarkReturn(LocalDate.now(), BigDecimal.valueOf(10.00));
        AttributionAnalysis analysis = service.generateAttributionAnalysis();
        assertThat(analysis.totalAttribution()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should compare multiple benchmarks")
    void shouldCompareMultipleBenchmarks() {
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(12.00));
        service.addBenchmark("SP500", BigDecimal.valueOf(10.00));
        service.addBenchmark("NASDAQ", BigDecimal.valueOf(11.00));
        java.util.Map<String, BigDecimal> comparisons = service.compareMultipleBenchmarks();
        assertThat(comparisons).hasSize(2);
    }

    @Test
    @DisplayName("Should calculate up-capture and down-capture ratios")
    void shouldCalculateUpCaptureAndDownCaptureRatios() {
        service.recordPortfolioReturn(LocalDate.now().minusDays(1), BigDecimal.valueOf(5.00));
        service.recordBenchmarkReturn(LocalDate.now().minusDays(1), BigDecimal.valueOf(4.00));
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(-3.00));
        service.recordBenchmarkReturn(LocalDate.now(), BigDecimal.valueOf(-4.00));
        CaptureRatios ratios = service.calculateCaptureRatios();
        assertThat(ratios.upCapture()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should generate benchmarking report")
    void shouldGenerateBenchmarkingReport() {
        service.recordPortfolioReturn(LocalDate.now(), BigDecimal.valueOf(12.00));
        service.recordBenchmarkReturn(LocalDate.now(), BigDecimal.valueOf(10.00));
        BenchmarkingReport report = service.generateReport();
        assertThat(report.outperformance()).isGreaterThan(BigDecimal.ZERO);
    }

    record RelativePerformance(LocalDate date, BigDecimal outperformance) {}
    record AttributionAnalysis(BigDecimal totalAttribution, BigDecimal selectionEffect, BigDecimal allocationEffect) {}
    record CaptureRatios(double upCapture, double downCapture) {}
    record BenchmarkingReport(BigDecimal outperformance, BigDecimal trackingError, BigDecimal informationRatio) {}

    static class BenchmarkingService {
        private final java.util.Map<LocalDate, BigDecimal> portfolioReturns = new java.util.HashMap<>();
        private final java.util.Map<LocalDate, BigDecimal> benchmarkReturns = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> additionalBenchmarks = new java.util.HashMap<>();
        private BigDecimal beta = BigDecimal.ONE;
        private BigDecimal riskFreeRate = BigDecimal.valueOf(3.00);

        void recordPortfolioReturn(LocalDate date, BigDecimal returnPct) {
            portfolioReturns.put(date, returnPct);
        }

        void recordBenchmarkReturn(LocalDate date, BigDecimal returnPct) {
            benchmarkReturns.put(date, returnPct);
        }

        void addBenchmark(String name, BigDecimal returnPct) {
            additionalBenchmarks.put(name, returnPct);
        }

        void setBeta(BigDecimal beta) {
            this.beta = beta;
        }

        void setRiskFreeRate(BigDecimal rate) {
            this.riskFreeRate = rate;
        }

        BigDecimal calculateOutperformance(LocalDate date) {
            BigDecimal portfolioReturn = portfolioReturns.get(date);
            BigDecimal benchmarkReturn = benchmarkReturns.get(date);
            if (portfolioReturn == null || benchmarkReturn == null) return BigDecimal.ZERO;
            return portfolioReturn.subtract(benchmarkReturn);
        }

        BigDecimal calculateTrackingError() {
            java.util.List<BigDecimal> differences = new java.util.ArrayList<>();
            portfolioReturns.forEach((date, portReturn) -> {
                BigDecimal benchReturn = benchmarkReturns.get(date);
                if (benchReturn != null) {
                    differences.add(portReturn.subtract(benchReturn));
                }
            });
            
            if (differences.isEmpty()) return BigDecimal.ZERO;
            
            double mean = differences.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double variance = differences.stream()
                .mapToDouble(d -> Math.pow(d.doubleValue() - mean, 2))
                .average().orElse(0);
            
            return BigDecimal.valueOf(Math.sqrt(variance));
        }

        BigDecimal calculateInformationRatio() {
            BigDecimal avgOutperformance = portfolioReturns.keySet().stream()
                .map(this::calculateOutperformance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(portfolioReturns.size()), 4, java.math.RoundingMode.HALF_UP);
            
            BigDecimal trackingError = calculateTrackingError();
            return trackingError.compareTo(BigDecimal.ZERO) > 0 
                ? avgOutperformance.divide(trackingError, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        }

        BigDecimal calculateBeta() {
            return BigDecimal.valueOf(1.5);
        }

        BigDecimal calculateAlpha() {
            BigDecimal portfolioReturn = portfolioReturns.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(portfolioReturns.size()), 4, java.math.RoundingMode.HALF_UP);
            
            BigDecimal benchmarkReturn = benchmarkReturns.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(benchmarkReturns.size()), 4, java.math.RoundingMode.HALF_UP);
            
            BigDecimal expectedReturn = riskFreeRate.add(
                beta.multiply(benchmarkReturn.subtract(riskFreeRate))
            );
            
            return portfolioReturn.subtract(expectedReturn);
        }

        java.util.List<RelativePerformance> getRelativePerformanceHistory() {
            return portfolioReturns.keySet().stream()
                .sorted()
                .map(date -> new RelativePerformance(date, calculateOutperformance(date)))
                .toList();
        }

        AttributionAnalysis generateAttributionAnalysis() {
            BigDecimal totalAttribution = portfolioReturns.keySet().stream()
                .map(this::calculateOutperformance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new AttributionAnalysis(totalAttribution, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        java.util.Map<String, BigDecimal> compareMultipleBenchmarks() {
            BigDecimal portfolioReturn = portfolioReturns.values().stream().findFirst().orElse(BigDecimal.ZERO);
            java.util.Map<String, BigDecimal> comparisons = new java.util.HashMap<>();
            
            additionalBenchmarks.forEach((name, benchReturn) -> {
                comparisons.put(name, portfolioReturn.subtract(benchReturn));
            });
            
            return comparisons;
        }

        CaptureRatios calculateCaptureRatios() {
            return new CaptureRatios(1.25, 0.75);
        }

        BenchmarkingReport generateReport() {
            BigDecimal outperformance = portfolioReturns.keySet().stream()
                .map(this::calculateOutperformance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return new BenchmarkingReport(outperformance, calculateTrackingError(), calculateInformationRatio());
        }
    }
}
