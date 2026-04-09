package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for position valuation per D03-005
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Position Valuation Tests")
class PositionValuationTest {

    private ValuationService valuationService;

    @BeforeEach
    void setUp() {
        valuationService = new ValuationService();
    }

    @Test
    @DisplayName("Should value position at market price")
    void shouldValuePositionAtMarketPrice() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        MarketPrice marketPrice = new MarketPrice("AAPL", BigDecimal.valueOf(155.00), Instant.now());

        BigDecimal value = valuationService.valueAtMarket(position, marketPrice);

        assertThat(value).isEqualByComparingTo(BigDecimal.valueOf(155000.00));
    }

    @Test
    @DisplayName("Should calculate mark-to-market")
    void shouldCalculateMarkToMarket() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00))
        );

        List<MarketPrice> prices = List.of(
            new MarketPrice("AAPL", BigDecimal.valueOf(155.00), Instant.now()),
            new MarketPrice("GOOGL", BigDecimal.valueOf(2850.00), Instant.now())
        );

        MarkToMarket mtm = valuationService.calculateMarkToMarket(positions, prices);

        assertThat(mtm.totalMarketValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(mtm.totalCostBasis()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle stale prices")
    void shouldHandleStalePrices() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        MarketPrice stalePrice = new MarketPrice("AAPL", BigDecimal.valueOf(155.00), Instant.now().minusSeconds(7200));

        boolean isStale = valuationService.isPriceStale(stalePrice, 3600);

        assertThat(isStale).isTrue();
    }

    @Test
    @DisplayName("Should use last known price for missing quotes")
    void shouldUseLastKnownPriceForMissingQuotes() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));

        BigDecimal value = valuationService.valueWithFallback(position, null, BigDecimal.valueOf(152.00));

        assertThat(value).isEqualByComparingTo(BigDecimal.valueOf(152000.00));
    }

    @Test
    @DisplayName("Should calculate NAV")
    void shouldCalculateNAV() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00))
        );

        List<MarketPrice> prices = List.of(
            new MarketPrice("AAPL", BigDecimal.valueOf(155.00), Instant.now()),
            new MarketPrice("GOOGL", BigDecimal.valueOf(2850.00), Instant.now())
        );

        BigDecimal cash = BigDecimal.valueOf(50000.00);
        int shares = 1000;

        BigDecimal nav = valuationService.calculateNAV(positions, prices, cash, shares);

        assertThat(nav).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should apply valuation adjustments")
    void shouldApplyValuationAdjustments() {
        BigDecimal marketValue = BigDecimal.valueOf(1000000.00);
        BigDecimal illiquidityDiscount = BigDecimal.valueOf(0.05);

        BigDecimal adjustedValue = valuationService.applyAdjustment(marketValue, illiquidityDiscount);

        assertThat(adjustedValue).isEqualByComparingTo(BigDecimal.valueOf(950000.00));
    }

    @Test
    @DisplayName("Should value derivatives")
    void shouldValueDerivatives() {
        DerivativePosition option = new DerivativePosition(
            "AAPL_CALL_150",
            100L,
            BigDecimal.valueOf(5.00),
            "CALL",
            BigDecimal.valueOf(150.00)
        );

        BigDecimal underlyingPrice = BigDecimal.valueOf(155.00);

        BigDecimal value = valuationService.valueDerivative(option, underlyingPrice);

        assertThat(value).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate portfolio value over time")
    void shouldCalculatePortfolioValueOverTime() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00))
        );

        List<PriceHistory> history = List.of(
            new PriceHistory("AAPL", BigDecimal.valueOf(150.00), Instant.now().minusSeconds(86400)),
            new PriceHistory("AAPL", BigDecimal.valueOf(152.00), Instant.now().minusSeconds(43200)),
            new PriceHistory("AAPL", BigDecimal.valueOf(155.00), Instant.now())
        );

        List<BigDecimal> values = valuationService.calculateHistoricalValues(positions, history);

        assertThat(values).hasSize(3);
        assertThat(values.get(2)).isGreaterThan(values.get(0));
    }

    @Test
    @DisplayName("Should handle currency conversion")
    void shouldHandleCurrencyConversion() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        BigDecimal exchangeRate = BigDecimal.valueOf(1.20);

        BigDecimal valueInForeignCurrency = valuationService.convertCurrency(
            position,
            BigDecimal.valueOf(155.00),
            exchangeRate
        );

        assertThat(valueInForeignCurrency).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate valuation report")
    void shouldGenerateValuationReport() {
        List<Position> positions = List.of(
            new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 100L, BigDecimal.valueOf(2800.00))
        );

        List<MarketPrice> prices = List.of(
            new MarketPrice("AAPL", BigDecimal.valueOf(155.00), Instant.now()),
            new MarketPrice("GOOGL", BigDecimal.valueOf(2850.00), Instant.now())
        );

        ValuationReport report = valuationService.generateReport(positions, prices);

        assertThat(report.totalMarketValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(report.positionCount()).isEqualTo(2);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record MarketPrice(String symbol, BigDecimal price, Instant timestamp) {}
    record DerivativePosition(String symbol, long quantity, BigDecimal premium, String type, BigDecimal strikePrice) {}
    record PriceHistory(String symbol, BigDecimal price, Instant timestamp) {}

    record MarkToMarket(BigDecimal totalMarketValue, BigDecimal totalCostBasis, BigDecimal unrealizedPnL) {}
    record ValuationReport(BigDecimal totalMarketValue, int positionCount, Instant valuationTime) {}

    static class ValuationService {
        BigDecimal valueAtMarket(Position position, MarketPrice marketPrice) {
            return marketPrice.price().multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
        }

        MarkToMarket calculateMarkToMarket(List<Position> positions, List<MarketPrice> prices) {
            BigDecimal totalMarketValue = BigDecimal.ZERO;
            BigDecimal totalCostBasis = BigDecimal.ZERO;

            for (Position pos : positions) {
                MarketPrice price = prices.stream()
                    .filter(p -> p.symbol().equals(pos.symbol()))
                    .findFirst()
                    .orElse(null);

                if (price != null) {
                    totalMarketValue = totalMarketValue.add(
                        price.price().multiply(BigDecimal.valueOf(Math.abs(pos.quantity())))
                    );
                }

                totalCostBasis = totalCostBasis.add(
                    pos.averagePrice().multiply(BigDecimal.valueOf(Math.abs(pos.quantity())))
                );
            }

            BigDecimal unrealizedPnL = totalMarketValue.subtract(totalCostBasis);
            return new MarkToMarket(totalMarketValue, totalCostBasis, unrealizedPnL);
        }

        boolean isPriceStale(MarketPrice price, int maxAgeSeconds) {
            long ageSeconds = Instant.now().getEpochSecond() - price.timestamp().getEpochSecond();
            return ageSeconds > maxAgeSeconds;
        }

        BigDecimal valueWithFallback(Position position, MarketPrice currentPrice, BigDecimal fallbackPrice) {
            BigDecimal price = currentPrice != null ? currentPrice.price() : fallbackPrice;
            return price.multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
        }

        BigDecimal calculateNAV(List<Position> positions, List<MarketPrice> prices, BigDecimal cash, int shares) {
            MarkToMarket mtm = calculateMarkToMarket(positions, prices);
            BigDecimal totalAssets = mtm.totalMarketValue().add(cash);
            return totalAssets.divide(BigDecimal.valueOf(shares), 2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal applyAdjustment(BigDecimal marketValue, BigDecimal discountRate) {
            return marketValue.multiply(BigDecimal.ONE.subtract(discountRate));
        }

        BigDecimal valueDerivative(DerivativePosition derivative, BigDecimal underlyingPrice) {
            BigDecimal intrinsicValue = BigDecimal.ZERO;
            if (derivative.type().equals("CALL")) {
                intrinsicValue = underlyingPrice.subtract(derivative.strikePrice()).max(BigDecimal.ZERO);
            } else if (derivative.type().equals("PUT")) {
                intrinsicValue = derivative.strikePrice().subtract(underlyingPrice).max(BigDecimal.ZERO);
            }
            return intrinsicValue.add(derivative.premium()).multiply(BigDecimal.valueOf(derivative.quantity()));
        }

        List<BigDecimal> calculateHistoricalValues(List<Position> positions, List<PriceHistory> history) {
            return history.stream()
                .map(h -> {
                    Position pos = positions.stream()
                        .filter(p -> p.symbol().equals(h.symbol()))
                        .findFirst()
                        .orElse(null);
                    if (pos != null) {
                        return h.price().multiply(BigDecimal.valueOf(Math.abs(pos.quantity())));
                    }
                    return BigDecimal.ZERO;
                })
                .toList();
        }

        BigDecimal convertCurrency(Position position, BigDecimal price, BigDecimal exchangeRate) {
            BigDecimal valueInBaseCurrency = price.multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
            return valueInBaseCurrency.multiply(exchangeRate);
        }

        ValuationReport generateReport(List<Position> positions, List<MarketPrice> prices) {
            MarkToMarket mtm = calculateMarkToMarket(positions, prices);
            return new ValuationReport(mtm.totalMarketValue(), positions.size(), Instant.now());
        }
    }
}
