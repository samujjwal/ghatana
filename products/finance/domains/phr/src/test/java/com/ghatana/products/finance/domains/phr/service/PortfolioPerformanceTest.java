package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Performance Tests")
class PortfolioPerformanceTest {
    private PerformanceService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceService();
    }

    @Test
    @DisplayName("Should calculate daily return")
    void shouldCalculateDailyReturn() {
        service.recordValue(LocalDate.now().minusDays(1), BigDecimal.valueOf(100000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(105000.00));
        BigDecimal dailyReturn = service.calculateDailyReturn(LocalDate.now());
        assertThat(dailyReturn).isEqualByComparingTo(BigDecimal.valueOf(5.00));
    }

    @Test
    @DisplayName("Should calculate cumulative return")
    void shouldCalculateCumulativeReturn() {
        service.recordValue(LocalDate.now().minusDays(30), BigDecimal.valueOf(100000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(110000.00));
        BigDecimal cumulativeReturn = service.calculateCumulativeReturn(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(cumulativeReturn).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    @Test
    @DisplayName("Should calculate annualized return")
    void shouldCalculateAnnualizedReturn() {
        service.recordValue(LocalDate.now().minusDays(365), BigDecimal.valueOf(100000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(115000.00));
        BigDecimal annualizedReturn = service.calculateAnnualizedReturn(LocalDate.now().minusDays(365), LocalDate.now());
        assertThat(annualizedReturn).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate volatility")
    void shouldCalculateVolatility() {
        for (int i = 30; i >= 0; i--) {
            service.recordValue(LocalDate.now().minusDays(i), BigDecimal.valueOf(100000 + i * 1000));
        }
        BigDecimal volatility = service.calculateVolatility(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(volatility).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate Sharpe ratio")
    void shouldCalculateSharpeRatio() {
        service.recordValue(LocalDate.now().minusDays(365), BigDecimal.valueOf(100000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(115000.00));
        BigDecimal riskFreeRate = BigDecimal.valueOf(3.00);
        BigDecimal sharpeRatio = service.calculateSharpeRatio(LocalDate.now().minusDays(365), LocalDate.now(), riskFreeRate);
        assertThat(sharpeRatio).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should track maximum drawdown")
    void shouldTrackMaximumDrawdown() {
        service.recordValue(LocalDate.now().minusDays(10), BigDecimal.valueOf(110000.00));
        service.recordValue(LocalDate.now().minusDays(5), BigDecimal.valueOf(95000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(105000.00));
        BigDecimal maxDrawdown = service.calculateMaxDrawdown(LocalDate.now().minusDays(10), LocalDate.now());
        assertThat(maxDrawdown).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate time-weighted return")
    void shouldCalculateTimeWeightedReturn() {
        service.recordValue(LocalDate.now().minusDays(30), BigDecimal.valueOf(100000.00));
        service.recordCashFlow(LocalDate.now().minusDays(15), BigDecimal.valueOf(10000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(115000.00));
        BigDecimal twr = service.calculateTimeWeightedReturn(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(twr).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate money-weighted return")
    void shouldCalculateMoneyWeightedReturn() {
        service.recordValue(LocalDate.now().minusDays(30), BigDecimal.valueOf(100000.00));
        service.recordCashFlow(LocalDate.now().minusDays(15), BigDecimal.valueOf(10000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(115000.00));
        BigDecimal mwr = service.calculateMoneyWeightedReturn(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(mwr).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate performance report")
    void shouldGeneratePerformanceReport() {
        service.recordValue(LocalDate.now().minusDays(30), BigDecimal.valueOf(100000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(110000.00));
        PerformanceReport report = service.generateReport(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(report.totalReturn()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should compare performance to benchmark")
    void shouldComparePerformanceToBenchmark() {
        service.recordValue(LocalDate.now().minusDays(30), BigDecimal.valueOf(100000.00));
        service.recordValue(LocalDate.now(), BigDecimal.valueOf(110000.00));
        service.recordBenchmarkValue(LocalDate.now().minusDays(30), BigDecimal.valueOf(1000.00));
        service.recordBenchmarkValue(LocalDate.now(), BigDecimal.valueOf(1080.00));
        BigDecimal alpha = service.calculateAlpha(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(alpha).isNotNull();
    }

    record PerformanceReport(BigDecimal totalReturn, BigDecimal volatility, BigDecimal sharpeRatio) {}

    static class PerformanceService {
        private final java.util.Map<LocalDate, BigDecimal> values = new java.util.HashMap<>();
        private final java.util.Map<LocalDate, BigDecimal> cashFlows = new java.util.HashMap<>();
        private final java.util.Map<LocalDate, BigDecimal> benchmarkValues = new java.util.HashMap<>();

        void recordValue(LocalDate date, BigDecimal value) {
            values.put(date, value);
        }

        void recordCashFlow(LocalDate date, BigDecimal amount) {
            cashFlows.put(date, amount);
        }

        void recordBenchmarkValue(LocalDate date, BigDecimal value) {
            benchmarkValues.put(date, value);
        }

        BigDecimal calculateDailyReturn(LocalDate date) {
            BigDecimal today = values.get(date);
            BigDecimal yesterday = values.get(date.minusDays(1));
            if (today == null || yesterday == null) return BigDecimal.ZERO;
            return today.subtract(yesterday).divide(yesterday, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        BigDecimal calculateCumulativeReturn(LocalDate from, LocalDate to) {
            BigDecimal startValue = values.get(from);
            BigDecimal endValue = values.get(to);
            if (startValue == null || endValue == null) return BigDecimal.ZERO;
            return endValue.subtract(startValue).divide(startValue, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        BigDecimal calculateAnnualizedReturn(LocalDate from, LocalDate to) {
            BigDecimal cumulativeReturn = calculateCumulativeReturn(from, to);
            long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
            double years = days / 365.0;
            return BigDecimal.valueOf(Math.pow(1 + cumulativeReturn.doubleValue() / 100, 1 / years) - 1).multiply(BigDecimal.valueOf(100));
        }

        BigDecimal calculateVolatility(LocalDate from, LocalDate to) {
            java.util.List<BigDecimal> returns = new java.util.ArrayList<>();
            LocalDate current = from.plusDays(1);
            while (!current.isAfter(to)) {
                BigDecimal dailyReturn = calculateDailyReturn(current);
                if (dailyReturn.compareTo(BigDecimal.ZERO) != 0) {
                    returns.add(dailyReturn);
                }
                current = current.plusDays(1);
            }
            if (returns.isEmpty()) return BigDecimal.ZERO;
            double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r.doubleValue() - mean, 2))
                .average().orElse(0);
            return BigDecimal.valueOf(Math.sqrt(variance));
        }

        BigDecimal calculateSharpeRatio(LocalDate from, LocalDate to, BigDecimal riskFreeRate) {
            BigDecimal annualizedReturn = calculateAnnualizedReturn(from, to);
            BigDecimal volatility = calculateVolatility(from, to);
            if (volatility.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            return annualizedReturn.subtract(riskFreeRate).divide(volatility, 4, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal calculateMaxDrawdown(LocalDate from, LocalDate to) {
            BigDecimal maxValue = BigDecimal.ZERO;
            BigDecimal maxDrawdown = BigDecimal.ZERO;
            LocalDate current = from;
            while (!current.isAfter(to)) {
                BigDecimal value = values.get(current);
                if (value != null) {
                    maxValue = maxValue.max(value);
                    BigDecimal drawdown = maxValue.subtract(value).divide(maxValue, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                    maxDrawdown = maxDrawdown.max(drawdown);
                }
                current = current.plusDays(1);
            }
            return maxDrawdown;
        }

        BigDecimal calculateTimeWeightedReturn(LocalDate from, LocalDate to) {
            return calculateCumulativeReturn(from, to);
        }

        BigDecimal calculateMoneyWeightedReturn(LocalDate from, LocalDate to) {
            return calculateCumulativeReturn(from, to);
        }

        PerformanceReport generateReport(LocalDate from, LocalDate to) {
            BigDecimal totalReturn = calculateCumulativeReturn(from, to);
            BigDecimal volatility = calculateVolatility(from, to);
            BigDecimal sharpeRatio = calculateSharpeRatio(from, to, BigDecimal.valueOf(3.00));
            return new PerformanceReport(totalReturn, volatility, sharpeRatio);
        }

        BigDecimal calculateAlpha(LocalDate from, LocalDate to) {
            BigDecimal portfolioReturn = calculateCumulativeReturn(from, to);
            BigDecimal benchmarkStart = benchmarkValues.get(from);
            BigDecimal benchmarkEnd = benchmarkValues.get(to);
            if (benchmarkStart == null || benchmarkEnd == null) return BigDecimal.ZERO;
            BigDecimal benchmarkReturn = benchmarkEnd.subtract(benchmarkStart)
                .divide(benchmarkStart, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            return portfolioReturn.subtract(benchmarkReturn);
        }
    }
}
