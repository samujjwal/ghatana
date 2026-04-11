package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for position risk management per D03-003
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Position Risk Tests")
class PositionRiskTest {

    private RiskCalculator riskCalculator;

    @BeforeEach
    void setUp() {
        riskCalculator = new RiskCalculator();
    }

    @Test
    @DisplayName("Should calculate position exposure")
    void shouldCalculatePositionExposure() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        BigDecimal currentPrice = BigDecimal.valueOf(155.00);

        BigDecimal exposure = riskCalculator.calculateExposure(position, currentPrice);

        assertThat(exposure).isEqualByComparingTo(BigDecimal.valueOf(155000.00));
    }

    @Test
    @DisplayName("Should calculate Value at Risk (VaR)")
    void shouldCalculateValueAtRisk() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        BigDecimal volatility = BigDecimal.valueOf(0.25);

        BigDecimal var = riskCalculator.calculateVaR(position, volatility, 0.95);

        assertThat(var).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should detect concentration risk")
    void shouldDetectConcentrationRisk() {
        List<Position> portfolio = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00)),
            new Position("MSFT", 200L, BigDecimal.valueOf(300.00))
        );

        ConcentrationRisk risk = riskCalculator.calculateConcentrationRisk(portfolio);

        assertThat(risk.maxConcentration()).isGreaterThan(0.0);
        assertThat(risk.isExcessive()).isTrue();
    }

    @Test
    @DisplayName("Should calculate portfolio beta")
    void shouldCalculatePortfolioBeta() {
        List<PositionWithBeta> positions = List.of(
            new PositionWithBeta("AAPL", 1000L, BigDecimal.valueOf(150.00), 1.2),
            new PositionWithBeta("GOOGL", 100L, BigDecimal.valueOf(2800.00), 1.1),
            new PositionWithBeta("MSFT", 200L, BigDecimal.valueOf(300.00), 0.9)
        );

        double portfolioBeta = riskCalculator.calculatePortfolioBeta(positions);

        assertThat(portfolioBeta).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should enforce position limits")
    void shouldEnforcePositionLimits() {
        Position position = new Position("AAPL", 10000L, BigDecimal.valueOf(150.00));
        PositionLimit limit = new PositionLimit("AAPL", 5000L, BigDecimal.valueOf(1000000.00));

        boolean withinLimit = riskCalculator.isWithinLimit(position, limit);

        assertThat(withinLimit).isFalse();
    }

    @Test
    @DisplayName("Should calculate margin requirement")
    void shouldCalculateMarginRequirement() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        BigDecimal marginRate = BigDecimal.valueOf(0.50);

        BigDecimal margin = riskCalculator.calculateMarginRequirement(position, marginRate);

        assertThat(margin).isEqualByComparingTo(BigDecimal.valueOf(75000.00));
    }

    @Test
    @DisplayName("Should detect stop loss breach")
    void shouldDetectStopLossBreach() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        BigDecimal stopLoss = BigDecimal.valueOf(145.00);
        BigDecimal currentPrice = BigDecimal.valueOf(144.00);

        boolean breached = riskCalculator.isStopLossBreached(position, stopLoss, currentPrice);

        assertThat(breached).isTrue();
    }

    @Test
    @DisplayName("Should calculate portfolio volatility")
    void shouldCalculatePortfolioVolatility() {
        List<PositionWithVolatility> positions = List.of(
            new PositionWithVolatility("AAPL", 1000L, BigDecimal.valueOf(150.00), 0.25),
            new PositionWithVolatility("GOOGL", 100L, BigDecimal.valueOf(2800.00), 0.30)
        );

        double portfolioVol = riskCalculator.calculatePortfolioVolatility(positions);

        assertThat(portfolioVol).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should calculate Sharpe ratio")
    void shouldCalculateSharpeRatio() {
        BigDecimal portfolioReturn = BigDecimal.valueOf(0.15);
        BigDecimal riskFreeRate = BigDecimal.valueOf(0.03);
        BigDecimal volatility = BigDecimal.valueOf(0.20);

        double sharpeRatio = riskCalculator.calculateSharpeRatio(portfolioReturn, riskFreeRate, volatility);

        assertThat(sharpeRatio).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should generate risk report")
    void shouldGenerateRiskReport() {
        List<Position> portfolio = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00))
        );

        RiskReport report = riskCalculator.generateRiskReport(portfolio);

        assertThat(report.totalExposure()).isGreaterThan(BigDecimal.ZERO);
        assertThat(report.positionCount()).isEqualTo(2);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record PositionWithBeta(String symbol, long quantity, BigDecimal averagePrice, double beta) {}
    record PositionWithVolatility(String symbol, long quantity, BigDecimal averagePrice, double volatility) {}
    record PositionLimit(String symbol, long maxQuantity, BigDecimal maxValue) {}
    record ConcentrationRisk(double maxConcentration, boolean isExcessive) {}
    record RiskReport(BigDecimal totalExposure, int positionCount, BigDecimal var) {}

    static class RiskCalculator {
        BigDecimal calculateExposure(Position position, BigDecimal currentPrice) {
            return currentPrice.multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
        }

        BigDecimal calculateVaR(Position position, BigDecimal volatility, double confidenceLevel) {
            BigDecimal exposure = position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
            double zScore = confidenceLevel == 0.95 ? 1.645 : 2.326;
            return exposure.multiply(volatility).multiply(BigDecimal.valueOf(zScore));
        }

        ConcentrationRisk calculateConcentrationRisk(List<Position> portfolio) {
            BigDecimal totalValue = portfolio.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            double maxConcentration = portfolio.stream()
                .mapToDouble(p -> {
                    BigDecimal posValue = p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity())));
                    return posValue.divide(totalValue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
                })
                .max()
                .orElse(0.0);

            return new ConcentrationRisk(maxConcentration, maxConcentration > 0.30);
        }

        double calculatePortfolioBeta(List<PositionWithBeta> positions) {
            BigDecimal totalValue = positions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return positions.stream()
                .mapToDouble(p -> {
                    BigDecimal weight = p.averagePrice().multiply(BigDecimal.valueOf(p.quantity()))
                        .divide(totalValue, 4, java.math.RoundingMode.HALF_UP);
                    return weight.doubleValue() * p.beta();
                })
                .sum();
        }

        boolean isWithinLimit(Position position, PositionLimit limit) {
            if (Math.abs(position.quantity()) > limit.maxQuantity()) {
                return false;
            }
            BigDecimal value = position.averagePrice().multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
            return value.compareTo(limit.maxValue()) <= 0;
        }

        BigDecimal calculateMarginRequirement(Position position, BigDecimal marginRate) {
            BigDecimal exposure = position.averagePrice().multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
            return exposure.multiply(marginRate);
        }

        boolean isStopLossBreached(Position position, BigDecimal stopLoss, BigDecimal currentPrice) {
            if (position.quantity() > 0) {
                return currentPrice.compareTo(stopLoss) < 0;
            } else {
                return currentPrice.compareTo(stopLoss) > 0;
            }
        }

        double calculatePortfolioVolatility(List<PositionWithVolatility> positions) {
            BigDecimal totalValue = positions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return positions.stream()
                .mapToDouble(p -> {
                    BigDecimal weight = p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity())))
                        .divide(totalValue, 4, java.math.RoundingMode.HALF_UP);
                    return Math.pow(weight.doubleValue() * p.volatility(), 2);
                })
                .sum();
        }

        double calculateSharpeRatio(BigDecimal portfolioReturn, BigDecimal riskFreeRate, BigDecimal volatility) {
            return portfolioReturn.subtract(riskFreeRate)
                .divide(volatility, 4, java.math.RoundingMode.HALF_UP)
                .doubleValue();
        }

        RiskReport generateRiskReport(List<Position> portfolio) {
            BigDecimal totalExposure = portfolio.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(Math.abs(p.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal var = portfolio.stream()
                .map(p -> calculateVaR(p, BigDecimal.valueOf(0.25), 0.95))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new RiskReport(totalExposure, portfolio.size(), var);
        }
    }
}
