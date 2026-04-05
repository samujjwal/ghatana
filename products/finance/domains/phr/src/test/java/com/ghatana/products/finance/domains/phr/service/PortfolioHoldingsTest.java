package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for portfolio holdings record per D05-001
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Portfolio Holdings Record Tests")
class PortfolioHoldingsTest {
    private HoldingsService service;

    @BeforeEach
    void setUp() {
        service = new HoldingsService();
    }

    @Test
    @DisplayName("Should record daily holdings snapshot")
    void shouldRecordDailyHoldingsSnapshot() {
        Holding holding = new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now());
        service.recordHolding(holding);
        assertThat(service.getHoldings(LocalDate.now())).hasSize(1);
    }

    @Test
    @DisplayName("Should track holdings over time")
    void shouldTrackHoldingsOverTime() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now().minusDays(1)));
        service.recordHolding(new Holding("AAPL", 150L, BigDecimal.valueOf(151.00), LocalDate.now()));
        assertThat(service.getHoldingHistory("AAPL")).hasSize(2);
    }

    @Test
    @DisplayName("Should calculate holdings value")
    void shouldCalculateHoldingsValue() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.recordHolding(new Holding("GOOGL", 50L, BigDecimal.valueOf(2800.00), LocalDate.now()));
        BigDecimal totalValue = service.calculateTotalValue(LocalDate.now());
        assertThat(totalValue).isEqualByComparingTo(BigDecimal.valueOf(155000.00));
    }

    @Test
    @DisplayName("Should detect holdings changes")
    void shouldDetectHoldingsChanges() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now().minusDays(1)));
        service.recordHolding(new Holding("AAPL", 150L, BigDecimal.valueOf(151.00), LocalDate.now()));
        HoldingChange change = service.calculateChange("AAPL", LocalDate.now().minusDays(1), LocalDate.now());
        assertThat(change.quantityDelta()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should aggregate holdings by symbol")
    void shouldAggregateHoldingsBySymbol() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.recordHolding(new Holding("GOOGL", 50L, BigDecimal.valueOf(2800.00), LocalDate.now()));
        List<HoldingSummary> summary = service.getHoldingsSummary(LocalDate.now());
        assertThat(summary).hasSize(2);
    }

    @Test
    @DisplayName("Should calculate cost basis from holdings")
    void shouldCalculateCostBasisFromHoldings() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        BigDecimal costBasis = service.calculateCostBasis("AAPL", LocalDate.now());
        assertThat(costBasis).isEqualByComparingTo(BigDecimal.valueOf(15000.00));
    }

    @Test
    @DisplayName("Should track unrealized gains")
    void shouldTrackUnrealizedGains() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.updateMarketPrice("AAPL", BigDecimal.valueOf(155.00));
        BigDecimal unrealizedGain = service.calculateUnrealizedGain("AAPL", LocalDate.now());
        assertThat(unrealizedGain).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should generate holdings report")
    void shouldGenerateHoldingsReport() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        service.recordHolding(new Holding("GOOGL", 50L, BigDecimal.valueOf(2800.00), LocalDate.now()));
        HoldingsReport report = service.generateReport(LocalDate.now());
        assertThat(report.totalHoldings()).isEqualTo(2);
        assertThat(report.totalValue()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should export holdings to CSV")
    void shouldExportHoldingsToCSV() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        String csv = service.exportToCSV(LocalDate.now());
        assertThat(csv).contains("AAPL").contains("100");
    }

    @Test
    @DisplayName("Should validate holdings data integrity")
    void shouldValidateHoldingsDataIntegrity() {
        service.recordHolding(new Holding("AAPL", 100L, BigDecimal.valueOf(150.00), LocalDate.now()));
        boolean valid = service.validateHoldings(LocalDate.now());
        assertThat(valid).isTrue();
    }

    record Holding(String symbol, long quantity, BigDecimal price, LocalDate date) {}
    record HoldingChange(long quantityDelta, BigDecimal priceDelta) {}
    record HoldingSummary(String symbol, long quantity, BigDecimal value) {}
    record HoldingsReport(int totalHoldings, BigDecimal totalValue) {}

    static class HoldingsService {
        private final List<Holding> holdings = new java.util.ArrayList<>();
        private final java.util.Map<String, BigDecimal> marketPrices = new java.util.HashMap<>();

        void recordHolding(Holding holding) {
            holdings.add(holding);
        }

        List<Holding> getHoldings(LocalDate date) {
            return holdings.stream().filter(h -> h.date().equals(date)).toList();
        }

        List<Holding> getHoldingHistory(String symbol) {
            return holdings.stream().filter(h -> h.symbol().equals(symbol)).toList();
        }

        BigDecimal calculateTotalValue(LocalDate date) {
            return getHoldings(date).stream()
                .map(h -> h.price().multiply(BigDecimal.valueOf(h.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        HoldingChange calculateChange(String symbol, LocalDate from, LocalDate to) {
            Holding fromHolding = holdings.stream()
                .filter(h -> h.symbol().equals(symbol) && h.date().equals(from))
                .findFirst().orElse(null);
            Holding toHolding = holdings.stream()
                .filter(h -> h.symbol().equals(symbol) && h.date().equals(to))
                .findFirst().orElse(null);
            
            if (fromHolding == null || toHolding == null) {
                return new HoldingChange(0L, BigDecimal.ZERO);
            }
            
            return new HoldingChange(
                toHolding.quantity() - fromHolding.quantity(),
                toHolding.price().subtract(fromHolding.price())
            );
        }

        List<HoldingSummary> getHoldingsSummary(LocalDate date) {
            return getHoldings(date).stream()
                .map(h -> new HoldingSummary(h.symbol(), h.quantity(), 
                    h.price().multiply(BigDecimal.valueOf(h.quantity()))))
                .toList();
        }

        BigDecimal calculateCostBasis(String symbol, LocalDate date) {
            return getHoldings(date).stream()
                .filter(h -> h.symbol().equals(symbol))
                .map(h -> h.price().multiply(BigDecimal.valueOf(h.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        void updateMarketPrice(String symbol, BigDecimal price) {
            marketPrices.put(symbol, price);
        }

        BigDecimal calculateUnrealizedGain(String symbol, LocalDate date) {
            Holding holding = getHoldings(date).stream()
                .filter(h -> h.symbol().equals(symbol))
                .findFirst().orElse(null);
            
            if (holding == null) return BigDecimal.ZERO;
            
            BigDecimal marketPrice = marketPrices.getOrDefault(symbol, holding.price());
            BigDecimal marketValue = marketPrice.multiply(BigDecimal.valueOf(holding.quantity()));
            BigDecimal costBasis = holding.price().multiply(BigDecimal.valueOf(holding.quantity()));
            return marketValue.subtract(costBasis);
        }

        HoldingsReport generateReport(LocalDate date) {
            List<Holding> dateHoldings = getHoldings(date);
            BigDecimal totalValue = calculateTotalValue(date);
            return new HoldingsReport(dateHoldings.size(), totalValue);
        }

        String exportToCSV(LocalDate date) {
            return getHoldings(date).stream()
                .map(h -> String.format("%s,%d,%s", h.symbol(), h.quantity(), h.price()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        }

        boolean validateHoldings(LocalDate date) {
            return getHoldings(date).stream()
                .allMatch(h -> h.quantity() > 0 && h.price().compareTo(BigDecimal.ZERO) > 0);
        }
    }
}
