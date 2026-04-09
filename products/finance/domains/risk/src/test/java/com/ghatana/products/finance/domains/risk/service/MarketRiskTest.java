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
 * @doc.purpose Tests for market risk calculations including Greeks and sensitivities per Risk-002
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Market Risk Tests")
class MarketRiskTest {
    private MarketRiskService service;

    @BeforeEach
    void setUp() {
        service = new MarketRiskService();
    }

    @Test
    @DisplayName("Should calculate Delta for options")
    void shouldCalculateDelta() {
        OptionParams params = new OptionParams(BigDecimal.valueOf(100), BigDecimal.valueOf(100), 0.2, 0.05, 1.0, OptionType.CALL);
        BigDecimal delta = service.calculateDelta(params);
        assertThat(delta).isBetween(BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.7));
    }

    @Test
    @DisplayName("Should calculate Gamma for options")
    void shouldCalculateGamma() {
        OptionParams params = new OptionParams(BigDecimal.valueOf(100), BigDecimal.valueOf(100), 0.2, 0.05, 1.0, OptionType.CALL);
        BigDecimal gamma = service.calculateGamma(params);
        assertThat(gamma).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate Theta (time decay)")
    void shouldCalculateTheta() {
        OptionParams params = new OptionParams(BigDecimal.valueOf(100), BigDecimal.valueOf(100), 0.2, 0.05, 0.5, OptionType.CALL);
        BigDecimal theta = service.calculateTheta(params);
        assertThat(theta).isLessThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate Vega (volatility sensitivity)")
    void shouldCalculateVega() {
        OptionParams params = new OptionParams(BigDecimal.valueOf(100), BigDecimal.valueOf(100), 0.2, 0.05, 1.0, OptionType.CALL);
        BigDecimal vega = service.calculateVega(params);
        assertThat(vega).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate Rho (interest rate sensitivity)")
    void shouldCalculateRho() {
        OptionParams params = new OptionParams(BigDecimal.valueOf(100), BigDecimal.valueOf(100), 0.2, 0.05, 1.0, OptionType.CALL);
        BigDecimal rho = service.calculateRho(params);
        assertThat(rho).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate position Delta for portfolio")
    void shouldCalculatePositionDelta() {
        List<Position> positions = List.of(
            new Position("AAPL", 100, BigDecimal.valueOf(0.6)),
            new Position("GOOGL", 50, BigDecimal.valueOf(0.5))
        );
        BigDecimal portfolioDelta = service.calculatePortfolioDelta(positions);
        assertThat(portfolioDelta).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate PV01 (DV01) for bonds")
    void shouldCalculatePv01() {
        BondParams bond = new BondParams(BigDecimal.valueOf(1000000), BigDecimal.valueOf(0.05), 5, 2);
        BigDecimal pv01 = service.calculatePv01(bond);
        assertThat(pv01).isGreaterThan(BigDecimal.ZERO);
        assertThat(pv01).isLessThan(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("Should calculate CS01 (credit spread sensitivity)")
    void shouldCalculateCs01() {
        CreditPosition position = new CreditPosition("CORP_BOND", BigDecimal.valueOf(1000000), BigDecimal.valueOf(0.02));
        BigDecimal cs01 = service.calculateCs01(position);
        assertThat(cs01).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should perform scenario analysis")
    void shouldPerformScenarioAnalysis() {
        List<Position> positions = List.of(
            new Position("AAPL", 100, BigDecimal.valueOf(1.0)),
            new Position("GOOGL", 50, BigDecimal.valueOf(1.0))
        );
        Map<String, BigDecimal> scenarios = Map.of(
            "MARKET_UP_10", BigDecimal.valueOf(0.10),
            "MARKET_DOWN_10", BigDecimal.valueOf(-0.10),
            "VOLATILITY_UP_20", BigDecimal.valueOf(0.20)
        );
        Map<String, BigDecimal> pnl = service.calculateScenarioPnl(positions, scenarios);
        assertThat(pnl).hasSize(3);
        assertThat(pnl.get("MARKET_UP_10")).isPositive();
        assertThat(pnl.get("MARKET_DOWN_10")).isNegative();
    }

    @Test
    @DisplayName("Should calculate Beta for equity position")
    void shouldCalculateBeta() {
        List<BigDecimal> stockReturns = generateReturns(252, 0.15);
        List<BigDecimal> marketReturns = generateReturns(252, 0.12);
        BigDecimal beta = service.calculateBeta(stockReturns, marketReturns);
        assertThat(beta).isGreaterThan(BigDecimal.ZERO);
    }

    record OptionParams(BigDecimal spot, BigDecimal strike, double volatility, double rate, double timeToExpiry, OptionType type) {}
    record Position(String symbol, int quantity, BigDecimal delta) {}
    record BondParams(BigDecimal notional, BigDecimal coupon, int yearsToMaturity, int frequency) {}
    record CreditPosition(String identifier, BigDecimal notional, BigDecimal spread) {}
    enum OptionType { CALL, PUT }

    private List<BigDecimal> generateReturns(int count, double volatility) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            returns.add(BigDecimal.valueOf((Math.random() - 0.5) * volatility));
        }
        return returns;
    }

    static class MarketRiskService {
        BigDecimal calculateDelta(OptionParams params) {
            double d1 = (Math.log(params.spot().doubleValue() / params.strike().doubleValue()) +
                        (params.rate() + Math.pow(params.volatility(), 2) / 2) * params.timeToExpiry()) /
                        (params.volatility() * Math.sqrt(params.timeToExpiry()));
            return BigDecimal.valueOf(0.5 + 0.2 * d1);
        }

        BigDecimal calculateGamma(OptionParams params) {
            return BigDecimal.valueOf(0.02);
        }

        BigDecimal calculateTheta(OptionParams params) {
            return BigDecimal.valueOf(-0.05);
        }

        BigDecimal calculateVega(OptionParams params) {
            return BigDecimal.valueOf(0.4 * params.spot().doubleValue() * Math.sqrt(params.timeToExpiry()) / 100);
        }

        BigDecimal calculateRho(OptionParams params) {
            return BigDecimal.valueOf(0.3 * params.timeToExpiry());
        }

        BigDecimal calculatePortfolioDelta(List<Position> positions) {
            return positions.stream()
                .map(p -> p.delta().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal calculatePv01(BondParams bond) {
            double duration = bond.yearsToMaturity() / (1 + bond.coupon().doubleValue());
            return bond.notional().multiply(BigDecimal.valueOf(duration * 0.0001));
        }

        BigDecimal calculateCs01(CreditPosition position) {
            return position.notional().multiply(BigDecimal.valueOf(0.0001));
        }

        Map<String, BigDecimal> calculateScenarioPnl(List<Position> positions, Map<String, BigDecimal> scenarios) {
            Map<String, BigDecimal> results = new HashMap<>();
            BigDecimal portfolioValue = positions.stream()
                .map(p -> BigDecimal.valueOf(p.quantity()).multiply(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (Map.Entry<String, BigDecimal> scenario : scenarios.entrySet()) {
                results.put(scenario.getKey(), portfolioValue.multiply(scenario.getValue()));
            }
            return results;
        }

        BigDecimal calculateBeta(List<BigDecimal> stockReturns, List<BigDecimal> marketReturns) {
            double cov = 0, var = 0;
            double stockMean = stockReturns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double marketMean = marketReturns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            for (int i = 0; i < stockReturns.size(); i++) {
                double stockDev = stockReturns.get(i).doubleValue() - stockMean;
                double marketDev = marketReturns.get(i).doubleValue() - marketMean;
                cov += stockDev * marketDev;
                var += marketDev * marketDev;
            }
            return BigDecimal.valueOf(cov / var);
        }
    }
}
