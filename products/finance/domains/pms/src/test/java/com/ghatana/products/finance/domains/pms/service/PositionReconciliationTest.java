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
 * @doc.purpose Tests for position reconciliation per D03-002
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Position Reconciliation Tests")
class PositionReconciliationTest {

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService();
    }

    @Test
    @DisplayName("Should reconcile internal vs broker positions")
    void shouldReconcileInternalVsBrokerPositions() {
        Position internalPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position brokerPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));

        ReconciliationResult result = reconciliationService.reconcile(internalPosition, brokerPosition);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.quantityDifference()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should detect quantity mismatch")
    void shouldDetectQuantityMismatch() {
        Position internalPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position brokerPosition = new Position("AAPL", 95L, BigDecimal.valueOf(150.00));

        ReconciliationResult result = reconciliationService.reconcile(internalPosition, brokerPosition);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.quantityDifference()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Should detect price mismatch")
    void shouldDetectPriceMismatch() {
        Position internalPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position brokerPosition = new Position("AAPL", 100L, BigDecimal.valueOf(151.00));

        ReconciliationResult result = reconciliationService.reconcile(internalPosition, brokerPosition);

        assertThat(result.priceDifference()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should generate reconciliation breaks")
    void shouldGenerateReconciliationBreaks() {
        List<Position> internalPositions = List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00))
        );

        List<Position> brokerPositions = List.of(
            new Position("AAPL", 95L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00))
        );

        List<ReconciliationBreak> breaks = reconciliationService.findBreaks(internalPositions, brokerPositions);

        assertThat(breaks).hasSize(1);
        assertThat(breaks.get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("Should handle missing positions")
    void shouldHandleMissingPositions() {
        List<Position> internalPositions = List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00)),
            new Position("MSFT", 75L, BigDecimal.valueOf(300.00))
        );

        List<Position> brokerPositions = List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00))
        );

        List<ReconciliationBreak> breaks = reconciliationService.findBreaks(internalPositions, brokerPositions);

        assertThat(breaks).hasSize(1);
        assertThat(breaks.get(0).breakType()).isEqualTo("MISSING_BROKER_POSITION");
    }

    @Test
    @DisplayName("Should handle extra broker positions")
    void shouldHandleExtraBrokerPositions() {
        List<Position> internalPositions = List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00))
        );

        List<Position> brokerPositions = List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00)),
            new Position("TSLA", 50L, BigDecimal.valueOf(700.00))
        );

        List<ReconciliationBreak> breaks = reconciliationService.findBreaks(internalPositions, brokerPositions);

        assertThat(breaks).hasSize(1);
        assertThat(breaks.get(0).breakType()).isEqualTo("EXTRA_BROKER_POSITION");
    }

    @Test
    @DisplayName("Should calculate reconciliation tolerance")
    void shouldCalculateReconciliationTolerance() {
        Position internalPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position brokerPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.01));

        boolean withinTolerance = reconciliationService.isWithinTolerance(
            internalPosition,
            brokerPosition,
            BigDecimal.valueOf(0.05)
        );

        assertThat(withinTolerance).isTrue();
    }

    @Test
    @DisplayName("Should track reconciliation history")
    void shouldTrackReconciliationHistory() {
        Position internalPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position brokerPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));

        reconciliationService.reconcile(internalPosition, brokerPosition);

        List<ReconciliationHistory> history = reconciliationService.getHistory("AAPL");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("Should auto-resolve minor breaks")
    void shouldAutoResolveMinorBreaks() {
        Position internalPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position brokerPosition = new Position("AAPL", 100L, BigDecimal.valueOf(150.005));

        ReconciliationResult result = reconciliationService.reconcileWithAutoResolve(
            internalPosition,
            brokerPosition,
            BigDecimal.valueOf(0.01)
        );

        assertThat(result.isMatched()).isTrue();
        assertThat(result.autoResolved()).isTrue();
    }

    @Test
    @DisplayName("Should generate reconciliation report")
    void shouldGenerateReconciliationReport() {
        List<Position> internalPositions = List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00))
        );

        List<Position> brokerPositions = List.of(
            new Position("AAPL", 95L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00))
        );

        ReconciliationReport report = reconciliationService.generateReport(internalPositions, brokerPositions);

        assertThat(report.totalPositions()).isEqualTo(2);
        assertThat(report.matchedPositions()).isEqualTo(1);
        assertThat(report.breaks()).isEqualTo(1);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    
    record ReconciliationResult(
        boolean isMatched,
        long quantityDifference,
        BigDecimal priceDifference,
        boolean autoResolved
    ) {
        ReconciliationResult(boolean isMatched, long quantityDifference, BigDecimal priceDifference) {
            this(isMatched, quantityDifference, priceDifference, false);
        }
    }

    record ReconciliationBreak(String symbol, String breakType, long quantityDiff, BigDecimal priceDiff) {}
    record ReconciliationHistory(String symbol, boolean matched, Instant reconciledAt) {}
    record ReconciliationReport(int totalPositions, int matchedPositions, int breaks) {}

    static class ReconciliationService {
        private final List<ReconciliationHistory> history = new java.util.ArrayList<>();

        ReconciliationResult reconcile(Position internal, Position broker) {
            long qtyDiff = internal.quantity() - broker.quantity();
            BigDecimal priceDiff = internal.averagePrice().subtract(broker.averagePrice()).abs();
            boolean matched = qtyDiff == 0 && priceDiff.compareTo(BigDecimal.ZERO) == 0;

            history.add(new ReconciliationHistory(internal.symbol(), matched, Instant.now()));

            return new ReconciliationResult(matched, qtyDiff, priceDiff);
        }

        List<ReconciliationBreak> findBreaks(List<Position> internal, List<Position> broker) {
            List<ReconciliationBreak> breaks = new java.util.ArrayList<>();

            for (Position internalPos : internal) {
                Position brokerPos = broker.stream()
                    .filter(p -> p.symbol().equals(internalPos.symbol()))
                    .findFirst()
                    .orElse(null);

                if (brokerPos == null) {
                    breaks.add(new ReconciliationBreak(
                        internalPos.symbol(),
                        "MISSING_BROKER_POSITION",
                        internalPos.quantity(),
                        BigDecimal.ZERO
                    ));
                } else {
                    ReconciliationResult result = reconcile(internalPos, brokerPos);
                    if (!result.isMatched()) {
                        breaks.add(new ReconciliationBreak(
                            internalPos.symbol(),
                            "QUANTITY_MISMATCH",
                            result.quantityDifference(),
                            result.priceDifference()
                        ));
                    }
                }
            }

            for (Position brokerPos : broker) {
                boolean exists = internal.stream()
                    .anyMatch(p -> p.symbol().equals(brokerPos.symbol()));
                if (!exists) {
                    breaks.add(new ReconciliationBreak(
                        brokerPos.symbol(),
                        "EXTRA_BROKER_POSITION",
                        brokerPos.quantity(),
                        BigDecimal.ZERO
                    ));
                }
            }

            return breaks;
        }

        boolean isWithinTolerance(Position internal, Position broker, BigDecimal tolerance) {
            BigDecimal priceDiff = internal.averagePrice().subtract(broker.averagePrice()).abs();
            return priceDiff.compareTo(tolerance) <= 0;
        }

        List<ReconciliationHistory> getHistory(String symbol) {
            return history.stream()
                .filter(h -> h.symbol().equals(symbol))
                .toList();
        }

        ReconciliationResult reconcileWithAutoResolve(Position internal, Position broker, BigDecimal tolerance) {
            ReconciliationResult result = reconcile(internal, broker);
            boolean withinTolerance = isWithinTolerance(internal, broker, tolerance);
            
            if (!result.isMatched() && withinTolerance) {
                return new ReconciliationResult(true, 0L, BigDecimal.ZERO, true);
            }
            
            return result;
        }

        ReconciliationReport generateReport(List<Position> internal, List<Position> broker) {
            List<ReconciliationBreak> breaks = findBreaks(internal, broker);
            int totalPositions = internal.size();
            int breakCount = breaks.size();
            int matched = totalPositions - breakCount;

            return new ReconciliationReport(totalPositions, matched, breakCount);
        }
    }
}
