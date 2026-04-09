package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for position calculation and aggregation per D03-001
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Position Calculation Tests")
class PositionCalculationTest {

    private PositionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PositionCalculator();
    }

    @Test
    @DisplayName("Should calculate position from trades")
    void shouldCalculatePositionFromTrades() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "BUY", 100L, BigDecimal.valueOf(150.00)),
            new Trade("trade-2", "AAPL", "BUY", 50L, BigDecimal.valueOf(151.00)),
            new Trade("trade-3", "AAPL", "SELL", 30L, BigDecimal.valueOf(152.00))
        );

        Position position = calculator.calculatePosition("AAPL", trades);

        assertThat(position.quantity()).isEqualTo(120L);
        assertThat(position.averagePrice()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate weighted average cost")
    void shouldCalculateWeightedAverageCost() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "BUY", 100L, BigDecimal.valueOf(100.00)),
            new Trade("trade-2", "AAPL", "BUY", 200L, BigDecimal.valueOf(101.00))
        );

        BigDecimal avgCost = calculator.calculateWeightedAverage(trades);

        assertThat(avgCost).isEqualByComparingTo(BigDecimal.valueOf(100.67));
    }

    @Test
    @DisplayName("Should handle long position")
    void shouldHandleLongPosition() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "BUY", 100L, BigDecimal.valueOf(150.00))
        );

        Position position = calculator.calculatePosition("AAPL", trades);

        assertThat(position.quantity()).isEqualTo(100L);
        assertThat(position.isLong()).isTrue();
        assertThat(position.isShort()).isFalse();
    }

    @Test
    @DisplayName("Should handle short position")
    void shouldHandleShortPosition() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "SELL", 100L, BigDecimal.valueOf(150.00))
        );

        Position position = calculator.calculatePosition("AAPL", trades);

        assertThat(position.quantity()).isEqualTo(-100L);
        assertThat(position.isLong()).isFalse();
        assertThat(position.isShort()).isTrue();
    }

    @Test
    @DisplayName("Should calculate unrealized P&L")
    void shouldCalculateUnrealizedPnL() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        BigDecimal currentPrice = BigDecimal.valueOf(155.00);

        BigDecimal pnl = calculator.calculateUnrealizedPnL(position, currentPrice);

        assertThat(pnl).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should calculate realized P&L")
    void shouldCalculateRealizedPnL() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "BUY", 100L, BigDecimal.valueOf(150.00)),
            new Trade("trade-2", "AAPL", "SELL", 100L, BigDecimal.valueOf(155.00))
        );

        BigDecimal realizedPnL = calculator.calculateRealizedPnL(trades);

        assertThat(realizedPnL).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Should aggregate positions across accounts")
    void shouldAggregatePositionsAcrossAccounts() {
        List<Position> positions = List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00)),
            new Position("AAPL", 50L, BigDecimal.valueOf(151.00)),
            new Position("AAPL", -30L, BigDecimal.valueOf(152.00))
        );

        Position aggregated = calculator.aggregatePositions("AAPL", positions);

        assertThat(aggregated.quantity()).isEqualTo(120L);
    }

    @Test
    @DisplayName("Should calculate position value")
    void shouldCalculatePositionValue() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        BigDecimal currentPrice = BigDecimal.valueOf(155.00);

        BigDecimal value = calculator.calculatePositionValue(position, currentPrice);

        assertThat(value).isEqualByComparingTo(BigDecimal.valueOf(15500.00));
    }

    @Test
    @DisplayName("Should handle flat position")
    void shouldHandleFlatPosition() {
        List<Trade> trades = List.of(
            new Trade("trade-1", "AAPL", "BUY", 100L, BigDecimal.valueOf(150.00)),
            new Trade("trade-2", "AAPL", "SELL", 100L, BigDecimal.valueOf(155.00))
        );

        Position position = calculator.calculatePosition("AAPL", trades);

        assertThat(position.quantity()).isEqualTo(0L);
        assertThat(position.isFlat()).isTrue();
    }

    @Test
    @DisplayName("Should calculate position cost basis")
    void shouldCalculatePositionCostBasis() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));

        BigDecimal costBasis = calculator.calculateCostBasis(position);

        assertThat(costBasis).isEqualByComparingTo(BigDecimal.valueOf(15000.00));
    }

    record Trade(String tradeId, String symbol, String side, long quantity, BigDecimal price) {}

    record Position(String symbol, long quantity, BigDecimal averagePrice) {
        boolean isLong() { return quantity > 0; }
        boolean isShort() { return quantity < 0; }
        boolean isFlat() { return quantity == 0; }
    }

    static class PositionCalculator {
        Position calculatePosition(String symbol, List<Trade> trades) {
            long netQuantity = 0L;
            BigDecimal totalCost = BigDecimal.ZERO;
            long totalBought = 0L;

            for (Trade trade : trades) {
                if (trade.side().equals("BUY")) {
                    netQuantity += trade.quantity();
                    totalCost = totalCost.add(trade.price().multiply(BigDecimal.valueOf(trade.quantity())));
                    totalBought += trade.quantity();
                } else {
                    netQuantity -= trade.quantity();
                }
            }

            BigDecimal avgPrice = totalBought > 0
                ? totalCost.divide(BigDecimal.valueOf(totalBought), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            return new Position(symbol, netQuantity, avgPrice);
        }

        BigDecimal calculateWeightedAverage(List<Trade> trades) {
            BigDecimal totalValue = BigDecimal.ZERO;
            long totalQty = 0L;

            for (Trade trade : trades) {
                if (trade.side().equals("BUY")) {
                    totalValue = totalValue.add(trade.price().multiply(BigDecimal.valueOf(trade.quantity())));
                    totalQty += trade.quantity();
                }
            }

            return totalQty > 0
                ? totalValue.divide(BigDecimal.valueOf(totalQty), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        }

        BigDecimal calculateUnrealizedPnL(Position position, BigDecimal currentPrice) {
            BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(position.quantity()));
            BigDecimal costBasis = position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
            return currentValue.subtract(costBasis);
        }

        BigDecimal calculateRealizedPnL(List<Trade> trades) {
            BigDecimal pnl = BigDecimal.ZERO;
            BigDecimal avgCost = BigDecimal.ZERO;
            long heldQuantity = 0L;

            for (Trade trade : trades) {
                if (trade.side().equals("BUY")) {
                    BigDecimal newCost = avgCost.multiply(BigDecimal.valueOf(heldQuantity))
                        .add(trade.price().multiply(BigDecimal.valueOf(trade.quantity())));
                    heldQuantity += trade.quantity();
                    avgCost = heldQuantity > 0
                        ? newCost.divide(BigDecimal.valueOf(heldQuantity), 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                } else {
                    pnl = pnl.add(trade.price().subtract(avgCost).multiply(BigDecimal.valueOf(trade.quantity())));
                    heldQuantity -= trade.quantity();
                }
            }

            return pnl;
        }

        Position aggregatePositions(String symbol, List<Position> positions) {
            long totalQty = positions.stream().mapToLong(Position::quantity).sum();
            BigDecimal weightedAvg = positions.stream()
                .filter(p -> p.quantity() > 0)
                .map(p -> p.averagePrice().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            long totalLong = positions.stream().filter(p -> p.quantity() > 0).mapToLong(Position::quantity).sum();
            BigDecimal avgPrice = totalLong > 0
                ? weightedAvg.divide(BigDecimal.valueOf(totalLong), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            return new Position(symbol, totalQty, avgPrice);
        }

        BigDecimal calculatePositionValue(Position position, BigDecimal currentPrice) {
            return currentPrice.multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
        }

        BigDecimal calculateCostBasis(Position position) {
            return position.averagePrice().multiply(BigDecimal.valueOf(Math.abs(position.quantity())));
        }
    }
}
