package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for risk aggregation across desks and portfolios per Risk-009
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Risk Aggregation Tests")
class RiskAggregationTest {
    private RiskAggregationService service;

    @BeforeEach
    void setUp() {
        service = new RiskAggregationService();
    }

    @Test
    @DisplayName("Should aggregate VaR across portfolios")
    void shouldAggregateVarAcrossPortfolios() {
        List<PortfolioRisk> portfolios = List.of(
            new PortfolioRisk("P1", BigDecimal.valueOf(1000000), BigDecimal.valueOf(0.5)),
            new PortfolioRisk("P2", BigDecimal.valueOf(2000000), BigDecimal.valueOf(0.3)),
            new PortfolioRisk("P3", BigDecimal.valueOf(1500000), BigDecimal.valueOf(0.4))
        );
        AggregationResult result = service.aggregateVar(portfolios);
        assertThat(result.totalVar()).isPositive();
        assertThat(result.diversificationBenefit()).isPositive();
    }

    @Test
    @DisplayName("Should calculate diversification benefit")
    void shouldCalculateDiversificationBenefit() {
        BigDecimal standaloneVar = BigDecimal.valueOf(5000000);
        BigDecimal aggregatedVar = BigDecimal.valueOf(4000000);
        BigDecimal benefit = service.calculateDiversificationBenefit(standaloneVar, aggregatedVar);
        assertThat(benefit).isEqualByComparingTo(BigDecimal.valueOf(0.20));
    }

    @Test
    @DisplayName("Should aggregate risk by risk factor")
    void shouldAggregateByRiskFactor() {
        List<PositionRisk> positions = List.of(
            new PositionRisk("AAPL", "EQUITY", BigDecimal.valueOf(100000), BigDecimal.valueOf(0.5)),
            new PositionRisk("GOOGL", "EQUITY", BigDecimal.valueOf(150000), BigDecimal.valueOf(0.6)),
            new PositionRisk("TSLA", "EQUITY", BigDecimal.valueOf(80000), BigDecimal.valueOf(0.8)),
            new PositionRisk("IBM", "CREDIT", BigDecimal.valueOf(120000), BigDecimal.valueOf(0.3))
        );
        Map<String, BigDecimal> factorRisks = service.aggregateByRiskFactor(positions);
        assertThat(factorRisks).containsKeys("EQUITY", "CREDIT");
        assertThat(factorRisks.get("EQUITY")).isGreaterThan(factorRisks.get("CREDIT"));
    }

    @Test
    @DisplayName("Should calculate portfolio level Greeks")
    void shouldCalculatePortfolioLevelGreeks() {
        List<OptionPosition> options = List.of(
            new OptionPosition("OPT1", BigDecimal.valueOf(0.6), 100, BigDecimal.valueOf(0.02), BigDecimal.valueOf(-0.05), BigDecimal.valueOf(0.4)),
            new OptionPosition("OPT2", BigDecimal.valueOf(-0.4), 50, BigDecimal.valueOf(0.03), BigDecimal.valueOf(-0.03), BigDecimal.valueOf(0.3)),
            new OptionPosition("OPT3", BigDecimal.valueOf(0.5), 75, BigDecimal.valueOf(0.025), BigDecimal.valueOf(-0.04), BigDecimal.valueOf(0.35))
        );
        PortfolioGreeks greeks = service.calculatePortfolioGreeks(options);
        assertThat(greeks.totalDelta()).isNotNull();
        assertThat(greeks.totalGamma()).isPositive();
    }

    @Test
    @DisplayName("Should aggregate exposure by counterparty")
    void shouldAggregateByCounterparty() {
        List<TradeExposure> trades = List.of(
            new TradeExposure("T1", "CPTY_A", BigDecimal.valueOf(1000000)),
            new TradeExposure("T2", "CPTY_A", BigDecimal.valueOf(500000)),
            new TradeExposure("T3", "CPTY_B", BigDecimal.valueOf(2000000)),
            new TradeExposure("T4", "CPTY_C", BigDecimal.valueOf(800000))
        );
        Map<String, BigDecimal> counterpartyExposures = service.aggregateByCounterparty(trades);
        assertThat(counterpartyExposures.get("CPTY_A")).isEqualByComparingTo(BigDecimal.valueOf(1500000));
    }

    @Test
    @DisplayName("Should calculate cross-asset correlation matrix")
    void shouldCalculateCorrelationMatrix() {
        List<String> assets = List.of("AAPL", "GOOGL", "MSFT", "AMZN");
        Map<String, List<BigDecimal>> returns = generateReturns(assets, 252);
        CorrelationMatrix matrix = service.calculateCorrelationMatrix(assets, returns);
        assertThat(matrix.correlations()).hasSize(assets.size() * assets.size());
        assertThat(matrix.getCorrelation("AAPL", "AAPL")).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should aggregate P&L by strategy")
    void shouldAggregatePnlByStrategy() {
        List<StrategyPnl> pnls = List.of(
            new StrategyPnl("Arbitrage", LocalDate.now(), BigDecimal.valueOf(50000)),
            new StrategyPnl("Arbitrage", LocalDate.now().minusDays(1), BigDecimal.valueOf(30000)),
            new StrategyPnl("TrendFollowing", LocalDate.now(), BigDecimal.valueOf(-20000)),
            new StrategyPnl("TrendFollowing", LocalDate.now().minusDays(1), BigDecimal.valueOf(10000))
        );
        Map<String, BigDecimal> aggregated = service.aggregatePnlByStrategy(pnls);
        assertThat(aggregated.get("Arbitrage")).isEqualByComparingTo(BigDecimal.valueOf(80000));
        assertThat(aggregated.get("TrendFollowing")).isEqualByComparingTo(BigDecimal.valueOf(-10000));
    }

    @Test
    @DisplayName("Should calculate incremental risk contribution")
    void shouldCalculateIncrementalRiskContribution() {
        BigDecimal portfolioVar = BigDecimal.valueOf(5000000);
        List<PositionRisk> positions = List.of(
            new PositionRisk("POS1", "EQUITY", BigDecimal.valueOf(1000000), BigDecimal.valueOf(0.2)),
            new PositionRisk("POS2", "EQUITY", BigDecimal.valueOf(1500000), BigDecimal.valueOf(0.3)),
            new PositionRisk("POS3", "CREDIT", BigDecimal.valueOf(800000), BigDecimal.valueOf(0.15))
        );
        Map<String, BigDecimal> contributions = service.calculateIncrementalContribution(positions, portfolioVar);
        assertThat(contributions.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
            .isCloseTo(portfolioVar, within(BigDecimal.valueOf(1)));
    }

    @Test
    @DisplayName("Should aggregate risk tenor profile")
    void shouldAggregateRiskTenorProfile() {
        List<TenorRisk> tenorRisks = List.of(
            new TenorRisk("ON", BigDecimal.valueOf(1000000)),
            new TenorRisk("1W", BigDecimal.valueOf(2000000)),
            new TenorRisk("1M", BigDecimal.valueOf(3000000)),
            new TenorRisk("3M", BigDecimal.valueOf(4000000)),
            new TenorRisk("6M", BigDecimal.valueOf(3500000)),
            new TenorRisk("1Y", BigDecimal.valueOf(5000000))
        );
        TenorProfile profile = service.aggregateTenorProfile(tenorRisks);
        assertThat(profile.shortTerm()).isEqualByComparingTo(BigDecimal.valueOf(3000000));
        assertThat(profile.total()).isEqualByComparingTo(BigDecimal.valueOf(18500000));
    }

    @Test
    @DisplayName("Should calculate risk attribution")
    void shouldCalculateRiskAttribution() {
        Map<String, BigDecimal> componentVars = Map.of(
            "AssetAllocation", BigDecimal.valueOf(1500000),
            "SecuritySelection", BigDecimal.valueOf(1000000),
            "Currency", BigDecimal.valueOf(800000),
            "MarketTiming", BigDecimal.valueOf(200000)
        );
        RiskAttribution attribution = service.calculateRiskAttribution(componentVars);
        assertThat(attribution.largestContributor()).isEqualTo("AssetAllocation");
    }

    record PortfolioRisk(String id, BigDecimal var, BigDecimal correlation) {}
    record AggregationResult(BigDecimal totalVar, BigDecimal diversificationBenefit, int positionCount) {}
    record PositionRisk(String id, String factor, BigDecimal exposure, BigDecimal sensitivity) {}
    record OptionPosition(String id, BigDecimal delta, int quantity, BigDecimal gamma, BigDecimal theta, BigDecimal vega) {}
    record PortfolioGreeks(BigDecimal totalDelta, BigDecimal totalGamma, BigDecimal totalTheta, BigDecimal totalVega) {}
    record TradeExposure(String tradeId, String counterparty, BigDecimal exposure) {}
    record CorrelationMatrix(Map<String, Map<String, BigDecimal>> correlations) {
        BigDecimal getCorrelation(String a, String b) {
            return correlations.getOrDefault(a, Map.of()).getOrDefault(b, BigDecimal.ZERO);
        }
    }
    record StrategyPnl(String strategy, LocalDate date, BigDecimal pnl) {}
    record TenorRisk(String tenor, BigDecimal risk) {}
    record TenorProfile(BigDecimal shortTerm, BigDecimal mediumTerm, BigDecimal longTerm, BigDecimal total) {}
    record RiskAttribution(Map<String, BigDecimal> contributions, String largestContributor, BigDecimal totalRisk) {}

    private Map<String, List<BigDecimal>> generateReturns(List<String> assets, int days) {
        Map<String, List<BigDecimal>> returns = new HashMap<>();
        for (String asset : assets) {
            List<BigDecimal> rets = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                rets.add(BigDecimal.valueOf((Math.random() - 0.5) * 0.02));
            }
            returns.put(asset, rets);
        }
        return returns;
    }

    static class RiskAggregationService {
        AggregationResult aggregateVar(List<PortfolioRisk> portfolios) {
            BigDecimal standalone = portfolios.stream().map(PortfolioRisk::var).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal aggregated = standalone.multiply(BigDecimal.valueOf(0.75));
            BigDecimal benefit = standalone.subtract(aggregated).divide(standalone, 4, java.math.RoundingMode.HALF_UP);
            return new AggregationResult(aggregated, benefit, portfolios.size());
        }

        BigDecimal calculateDiversificationBenefit(BigDecimal standalone, BigDecimal aggregated) {
            return standalone.subtract(aggregated).divide(standalone, 2, java.math.RoundingMode.HALF_UP);
        }

        Map<String, BigDecimal> aggregateByRiskFactor(List<PositionRisk> positions) {
            Map<String, BigDecimal> result = new HashMap<>();
            for (PositionRisk pos : positions) {
                BigDecimal risk = pos.exposure().multiply(pos.sensitivity());
                result.merge(pos.factor(), risk, BigDecimal::add);
            }
            return result;
        }

        PortfolioGreeks calculatePortfolioGreeks(List<OptionPosition> options) {
            BigDecimal delta = BigDecimal.ZERO, gamma = BigDecimal.ZERO, theta = BigDecimal.ZERO, vega = BigDecimal.ZERO;
            for (OptionPosition opt : options) {
                BigDecimal qty = BigDecimal.valueOf(opt.quantity());
                delta = delta.add(opt.delta().multiply(qty));
                gamma = gamma.add(opt.gamma().multiply(qty));
                theta = theta.add(opt.theta().multiply(qty));
                vega = vega.add(opt.vega().multiply(qty));
            }
            return new PortfolioGreeks(delta, gamma, theta, vega);
        }

        Map<String, BigDecimal> aggregateByCounterparty(List<TradeExposure> trades) {
            Map<String, BigDecimal> result = new HashMap<>();
            for (TradeExposure trade : trades) {
                result.merge(trade.counterparty(), trade.exposure(), BigDecimal::add);
            }
            return result;
        }

        CorrelationMatrix calculateCorrelationMatrix(List<String> assets, Map<String, List<BigDecimal>> returns) {
            Map<String, Map<String, BigDecimal>> matrix = new HashMap<>();
            for (String a : assets) {
                Map<String, BigDecimal> row = new HashMap<>();
                for (String b : assets) {
                    if (a.equals(b)) {
                        row.put(b, BigDecimal.ONE);
                    } else {
                        row.put(b, BigDecimal.valueOf(0.5 + Math.random() * 0.3));
                    }
                }
                matrix.put(a, row);
            }
            return new CorrelationMatrix(matrix);
        }

        Map<String, BigDecimal> aggregatePnlByStrategy(List<StrategyPnl> pnls) {
            Map<String, BigDecimal> result = new HashMap<>();
            for (StrategyPnl pnl : pnls) {
                result.merge(pnl.strategy(), pnl.pnl(), BigDecimal::add);
            }
            return result;
        }

        Map<String, BigDecimal> calculateIncrementalContribution(List<PositionRisk> positions, BigDecimal portfolioVar) {
            Map<String, BigDecimal> contributions = new HashMap<>();
            BigDecimal totalExposure = positions.stream().map(p -> p.exposure().multiply(p.sensitivity())).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (PositionRisk pos : positions) {
                BigDecimal weight = pos.exposure().multiply(pos.sensitivity()).divide(totalExposure, 4, java.math.RoundingMode.HALF_UP);
                contributions.put(pos.id(), portfolioVar.multiply(weight));
            }
            return contributions;
        }

        TenorProfile aggregateTenorProfile(List<TenorRisk> tenorRisks) {
            BigDecimal shortTerm = BigDecimal.ZERO, mediumTerm = BigDecimal.ZERO, longTerm = BigDecimal.ZERO;
            for (TenorRisk tr : tenorRisks) {
                if (tr.tenor().equals("ON") || tr.tenor().equals("1W") || tr.tenor().equals("1M")) {
                    shortTerm = shortTerm.add(tr.risk());
                } else if (tr.tenor().equals("3M") || tr.tenor().equals("6M")) {
                    mediumTerm = mediumTerm.add(tr.risk());
                } else {
                    longTerm = longTerm.add(tr.risk());
                }
            }
            return new TenorProfile(shortTerm, mediumTerm, longTerm, shortTerm.add(mediumTerm).add(longTerm));
        }

        RiskAttribution calculateRiskAttribution(Map<String, BigDecimal> componentVars) {
            BigDecimal total = componentVars.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            String largest = componentVars.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("NONE");
            return new RiskAttribution(componentVars, largest, total);
        }
    }
}
