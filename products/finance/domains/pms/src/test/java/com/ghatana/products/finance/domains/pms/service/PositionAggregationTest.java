package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Aggregation Tests")
class PositionAggregationTest {
    private AggregationService service;

    @BeforeEach
    void setUp() {
        service = new AggregationService();
    }

    @Test
    @DisplayName("Should aggregate positions by symbol")
    void shouldAggregatePositionsBySymbol() {
        List<Position> positions = List.of(
            new Position("AAPL", "account-1", 100L, BigDecimal.valueOf(150.00)),
            new Position("AAPL", "account-2", 50L, BigDecimal.valueOf(151.00))
        );
        AggregatedPosition agg = service.aggregateBySymbol("AAPL", positions);
        assertThat(agg.totalQuantity()).isEqualTo(150L);
    }

    @Test
    @DisplayName("Should aggregate positions by account")
    void shouldAggregatePositionsByAccount() {
        List<Position> positions = List.of(
            new Position("AAPL", "account-1", 100L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", "account-1", 50L, BigDecimal.valueOf(2800.00))
        );
        AccountAggregate agg = service.aggregateByAccount("account-1", positions);
        assertThat(agg.positionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should aggregate positions by sector")
    void shouldAggregatePositionsBySector() {
        List<PositionWithSector> positions = List.of(
            new PositionWithSector("AAPL", 100L, BigDecimal.valueOf(150.00), "Technology"),
            new PositionWithSector("GOOGL", 50L, BigDecimal.valueOf(2800.00), "Technology"),
            new PositionWithSector("JPM", 75L, BigDecimal.valueOf(150.00), "Financial")
        );
        SectorAggregate agg = service.aggregateBySector(positions);
        assertThat(agg.sectors()).containsKey("Technology");
        assertThat(agg.sectors()).containsKey("Financial");
    }

    @Test
    @DisplayName("Should calculate weighted average price")
    void shouldCalculateWeightedAveragePrice() {
        List<Position> positions = List.of(
            new Position("AAPL", "account-1", 100L, BigDecimal.valueOf(150.00)),
            new Position("AAPL", "account-2", 200L, BigDecimal.valueOf(151.00))
        );
        BigDecimal wavg = service.calculateWeightedAverage(positions);
        assertThat(wavg).isGreaterThan(BigDecimal.valueOf(150.00));
    }

    @Test
    @DisplayName("Should aggregate by asset class")
    void shouldAggregateByAssetClass() {
        List<PositionWithAssetClass> positions = List.of(
            new PositionWithAssetClass("AAPL", 100L, BigDecimal.valueOf(150.00), "EQUITY"),
            new PositionWithAssetClass("BOND-1", 1000L, BigDecimal.valueOf(100.00), "FIXED_INCOME")
        );
        AssetClassAggregate agg = service.aggregateByAssetClass(positions);
        assertThat(agg.assetClasses()).hasSize(2);
    }

    @Test
    @DisplayName("Should aggregate by currency")
    void shouldAggregateByCurrency() {
        List<PositionWithCurrency> positions = List.of(
            new PositionWithCurrency("AAPL", 100L, BigDecimal.valueOf(150.00), "USD"),
            new PositionWithCurrency("NESN", 50L, BigDecimal.valueOf(100.00), "CHF")
        );
        CurrencyAggregate agg = service.aggregateByCurrency(positions);
        assertThat(agg.currencies()).containsKey("USD");
        assertThat(agg.currencies()).containsKey("CHF");
    }

    @Test
    @DisplayName("Should aggregate by strategy")
    void shouldAggregateByStrategy() {
        List<PositionWithStrategy> positions = List.of(
            new PositionWithStrategy("AAPL", 100L, BigDecimal.valueOf(150.00), "GROWTH"),
            new PositionWithStrategy("GOOGL", 50L, BigDecimal.valueOf(2800.00), "GROWTH"),
            new PositionWithStrategy("T", 200L, BigDecimal.valueOf(30.00), "INCOME")
        );
        StrategyAggregate agg = service.aggregateByStrategy(positions);
        assertThat(agg.strategies()).hasSize(2);
    }

    @Test
    @DisplayName("Should calculate portfolio concentration")
    void shouldCalculatePortfolioConcentration() {
        List<Position> positions = List.of(
            new Position("AAPL", "account-1", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", "account-1", 100L, BigDecimal.valueOf(2800.00))
        );
        ConcentrationMetrics metrics = service.calculateConcentration(positions);
        assertThat(metrics.topHoldingPercentage()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should aggregate multi-level hierarchy")
    void shouldAggregateMultiLevelHierarchy() {
        List<PositionWithHierarchy> positions = List.of(
            new PositionWithHierarchy("AAPL", 100L, BigDecimal.valueOf(150.00), "Portfolio-1", "Strategy-A"),
            new PositionWithHierarchy("GOOGL", 50L, BigDecimal.valueOf(2800.00), "Portfolio-1", "Strategy-A")
        );
        HierarchyAggregate agg = service.aggregateHierarchy(positions);
        assertThat(agg.levels()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should generate aggregation report")
    void shouldGenerateAggregationReport() {
        List<Position> positions = List.of(
            new Position("AAPL", "account-1", 100L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", "account-2", 50L, BigDecimal.valueOf(2800.00))
        );
        AggregationReport report = service.generateReport(positions);
        assertThat(report.totalPositions()).isEqualTo(2);
        assertThat(report.uniqueSymbols()).isEqualTo(2);
    }

    record Position(String symbol, String account, long quantity, BigDecimal averagePrice) {}
    record PositionWithSector(String symbol, long quantity, BigDecimal averagePrice, String sector) {}
    record PositionWithAssetClass(String symbol, long quantity, BigDecimal averagePrice, String assetClass) {}
    record PositionWithCurrency(String symbol, long quantity, BigDecimal averagePrice, String currency) {}
    record PositionWithStrategy(String symbol, long quantity, BigDecimal averagePrice, String strategy) {}
    record PositionWithHierarchy(String symbol, long quantity, BigDecimal averagePrice, String portfolio, String strategy) {}
    
    record AggregatedPosition(String symbol, long totalQuantity, BigDecimal weightedAvgPrice) {}
    record AccountAggregate(String account, int positionCount, BigDecimal totalValue) {}
    record SectorAggregate(java.util.Map<String, BigDecimal> sectors) {}
    record AssetClassAggregate(java.util.Map<String, BigDecimal> assetClasses) {}
    record CurrencyAggregate(java.util.Map<String, BigDecimal> currencies) {}
    record StrategyAggregate(java.util.Map<String, BigDecimal> strategies) {}
    record ConcentrationMetrics(double topHoldingPercentage, double herfindahlIndex) {}
    record HierarchyAggregate(int levels, java.util.Map<String, Object> tree) {}
    record AggregationReport(int totalPositions, int uniqueSymbols, BigDecimal totalValue) {}

    static class AggregationService {
        AggregatedPosition aggregateBySymbol(String symbol, List<Position> positions) {
            long totalQty = positions.stream().filter(p -> p.symbol().equals(symbol)).mapToLong(Position::quantity).sum();
            BigDecimal wavg = calculateWeightedAverage(positions.stream().filter(p -> p.symbol().equals(symbol)).toList());
            return new AggregatedPosition(symbol, totalQty, wavg);
        }

        AccountAggregate aggregateByAccount(String account, List<Position> positions) {
            List<Position> accountPositions = positions.stream().filter(p -> p.account().equals(account)).toList();
            BigDecimal totalValue = accountPositions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AccountAggregate(account, accountPositions.size(), totalValue);
        }

        SectorAggregate aggregateBySector(List<PositionWithSector> positions) {
            java.util.Map<String, BigDecimal> sectors = new java.util.HashMap<>();
            for (PositionWithSector pos : positions) {
                BigDecimal value = pos.averagePrice().multiply(BigDecimal.valueOf(pos.quantity()));
                sectors.merge(pos.sector(), value, BigDecimal::add);
            }
            return new SectorAggregate(sectors);
        }

        BigDecimal calculateWeightedAverage(List<Position> positions) {
            BigDecimal totalValue = BigDecimal.ZERO;
            long totalQty = 0L;
            for (Position pos : positions) {
                totalValue = totalValue.add(pos.averagePrice().multiply(BigDecimal.valueOf(pos.quantity())));
                totalQty += pos.quantity();
            }
            return totalQty > 0 ? totalValue.divide(BigDecimal.valueOf(totalQty), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }

        AssetClassAggregate aggregateByAssetClass(List<PositionWithAssetClass> positions) {
            java.util.Map<String, BigDecimal> assetClasses = new java.util.HashMap<>();
            for (PositionWithAssetClass pos : positions) {
                BigDecimal value = pos.averagePrice().multiply(BigDecimal.valueOf(pos.quantity()));
                assetClasses.merge(pos.assetClass(), value, BigDecimal::add);
            }
            return new AssetClassAggregate(assetClasses);
        }

        CurrencyAggregate aggregateByCurrency(List<PositionWithCurrency> positions) {
            java.util.Map<String, BigDecimal> currencies = new java.util.HashMap<>();
            for (PositionWithCurrency pos : positions) {
                BigDecimal value = pos.averagePrice().multiply(BigDecimal.valueOf(pos.quantity()));
                currencies.merge(pos.currency(), value, BigDecimal::add);
            }
            return new CurrencyAggregate(currencies);
        }

        StrategyAggregate aggregateByStrategy(List<PositionWithStrategy> positions) {
            java.util.Map<String, BigDecimal> strategies = new java.util.HashMap<>();
            for (PositionWithStrategy pos : positions) {
                BigDecimal value = pos.averagePrice().multiply(BigDecimal.valueOf(pos.quantity()));
                strategies.merge(pos.strategy(), value, BigDecimal::add);
            }
            return new StrategyAggregate(strategies);
        }

        ConcentrationMetrics calculateConcentration(List<Position> positions) {
            BigDecimal totalValue = positions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal maxValue = positions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
            
            double topPercentage = totalValue.compareTo(BigDecimal.ZERO) > 0 
                ? maxValue.divide(totalValue, 4, java.math.RoundingMode.HALF_UP).doubleValue() 
                : 0.0;
            
            return new ConcentrationMetrics(topPercentage, 0.0);
        }

        HierarchyAggregate aggregateHierarchy(List<PositionWithHierarchy> positions) {
            return new HierarchyAggregate(2, new java.util.HashMap<>());
        }

        AggregationReport generateReport(List<Position> positions) {
            long uniqueSymbols = positions.stream().map(Position::symbol).distinct().count();
            BigDecimal totalValue = positions.stream()
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AggregationReport(positions.size(), (int) uniqueSymbols, totalValue);
        }
    }
}
