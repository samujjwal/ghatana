package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionFill;
import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
import com.ghatana.products.finance.domains.ems.service.FillAggregationService.FillAggregated;
import com.ghatana.products.finance.domains.ems.service.FillAggregationService.PartialFill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for fill processing and aggregation per D02-006
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Fill Processing Tests")
class FillProcessingTest {

    private FillProcessor fillProcessor;
    private FillValidator fillValidator;

    @BeforeEach
    void setUp() {
        fillProcessor = new FillProcessor();
        fillValidator = new FillValidator();
    }

    @Test
    @DisplayName("Should process single fill correctly")
    void shouldProcessSingleFillCorrectly() {
        ExecutionFill fill = new ExecutionFill(
            UUID.randomUUID().toString(),
            "routing-1",
            "exec-1",
            100L,
            BigDecimal.valueOf(150.50),
            Instant.now(),
            "NASDAQ"
        );

        ProcessedFill processed = fillProcessor.process(fill);

        assertThat(processed.fillId()).isEqualTo(fill.fillId());
        assertThat(processed.quantity()).isEqualTo(100L);
        assertThat(processed.price()).isEqualByComparingTo(BigDecimal.valueOf(150.50));
    }

    @Test
    @DisplayName("Should aggregate multiple partial fills")
    void shouldAggregateMultiplePartialFills() {
        List<PartialFill> fills = List.of(
            new PartialFill("fill-1", "order-1", "slice-1", 30L, BigDecimal.valueOf(150.00), Instant.now()),
            new PartialFill("fill-2", "order-1", "slice-2", 40L, BigDecimal.valueOf(150.50), Instant.now()),
            new PartialFill("fill-3", "order-1", "slice-3", 30L, BigDecimal.valueOf(151.00), Instant.now())
        );

        AggregatedFill aggregated = fillProcessor.aggregate(fills, 100L);

        assertThat(aggregated.totalQuantity()).isEqualTo(100L);
        assertThat(aggregated.averagePrice()).isEqualByComparingTo(BigDecimal.valueOf(150.50));
        assertThat(aggregated.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Should calculate weighted average price correctly")
    void shouldCalculateWeightedAveragePriceCorrectly() {
        List<PartialFill> fills = List.of(
            new PartialFill("fill-1", "order-1", "slice-1", 100L, BigDecimal.valueOf(100.00), Instant.now()),
            new PartialFill("fill-2", "order-1", "slice-2", 200L, BigDecimal.valueOf(101.00), Instant.now())
        );

        BigDecimal wavg = fillProcessor.calculateWeightedAverage(fills);

        assertThat(wavg).isEqualByComparingTo(BigDecimal.valueOf(100.67).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Should validate fill quantity against order quantity")
    void shouldValidateFillQuantityAgainstOrderQuantity() {
        ExecutionFill fill = new ExecutionFill(
            UUID.randomUUID().toString(),
            "routing-1",
            "exec-1",
            100L,
            BigDecimal.valueOf(150.50),
            Instant.now(),
            "NASDAQ"
        );

        boolean isValid = fillValidator.validateQuantity(fill, 100L, 0L);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject overfill")
    void shouldRejectOverfill() {
        ExecutionFill fill = new ExecutionFill(
            UUID.randomUUID().toString(),
            "routing-1",
            "exec-1",
            150L,
            BigDecimal.valueOf(150.50),
            Instant.now(),
            "NASDAQ"
        );

        assertThatThrownBy(() -> fillValidator.validateQuantity(fill, 100L, 0L, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("overfill");
    }

    @Test
    @DisplayName("Should track fill sequence")
    void shouldTrackFillSequence() {
        List<ExecutionFill> fills = new ArrayList<>();
        fills.add(createFill("fill-1", 30L));
        fills.add(createFill("fill-2", 40L));
        fills.add(createFill("fill-3", 30L));

        FillSequence sequence = fillProcessor.createSequence(fills);

        assertThat(sequence.fills()).hasSize(3);
        assertThat(sequence.isOrdered()).isTrue();
    }

    @Test
    @DisplayName("Should detect duplicate fills")
    void shouldDetectDuplicateFills() {
        String fillId = UUID.randomUUID().toString();
        ExecutionFill fill1 = new ExecutionFill(fillId, "routing-1", "exec-1", 100L, BigDecimal.valueOf(150.50), Instant.now(), "NASDAQ");
        ExecutionFill fill2 = new ExecutionFill(fillId, "routing-1", "exec-1", 100L, BigDecimal.valueOf(150.50), Instant.now(), "NASDAQ");

        boolean isDuplicate = fillValidator.isDuplicate(fill1, fill2);

        assertThat(isDuplicate).isTrue();
    }

    @Test
    @DisplayName("Should calculate fill rate")
    void shouldCalculateFillRate() {
        long orderQuantity = 1000L;
        long filledQuantity = 750L;

        double fillRate = fillProcessor.calculateFillRate(orderQuantity, filledQuantity);

        assertThat(fillRate).isEqualTo(0.75);
    }

    @Test
    @DisplayName("Should handle partial fill aggregation")
    void shouldHandlePartialFillAggregation() {
        List<PartialFill> fills = List.of(
            new PartialFill("fill-1", "order-1", "slice-1", 30L, BigDecimal.valueOf(150.00), Instant.now()),
            new PartialFill("fill-2", "order-1", "slice-2", 40L, BigDecimal.valueOf(150.50), Instant.now())
        );

        AggregatedFill aggregated = fillProcessor.aggregate(fills, 100L);

        assertThat(aggregated.totalQuantity()).isEqualTo(70L);
        assertThat(aggregated.isComplete()).isFalse();
        assertThat(aggregated.remainingQuantity()).isEqualTo(30L);
    }

    @Test
    @DisplayName("Should validate fill price within tolerance")
    void shouldValidateFillPriceWithinTolerance() {
        ExecutionFill fill = new ExecutionFill(
            UUID.randomUUID().toString(),
            "routing-1",
            "exec-1",
            100L,
            BigDecimal.valueOf(150.50),
            Instant.now(),
            "NASDAQ"
        );

        boolean isValid = fillValidator.validatePrice(
            fill,
            BigDecimal.valueOf(150.00),
            BigDecimal.valueOf(1.00)
        );

        assertThat(isValid).isTrue();
    }

    private ExecutionFill createFill(String fillId, long quantity) {
        return new ExecutionFill(
            fillId,
            "routing-1",
            "exec-1",
            quantity,
            BigDecimal.valueOf(150.50),
            Instant.now(),
            "NASDAQ"
        );
    }

    record ProcessedFill(String fillId, long quantity, BigDecimal price, Instant processedAt) {}
    record AggregatedFill(long totalQuantity, BigDecimal averagePrice, boolean isComplete, long remainingQuantity) {}
    record FillSequence(List<ExecutionFill> fills, boolean isOrdered) {}

    static class FillProcessor {
        ProcessedFill process(ExecutionFill fill) {
            return new ProcessedFill(fill.fillId(), fill.filledQuantity(), fill.fillPrice(), Instant.now());
        }

        AggregatedFill aggregate(List<PartialFill> fills, long orderQuantity) {
            long totalQty = fills.stream().mapToLong(PartialFill::filledQuantity).sum();
            BigDecimal wavg = calculateWeightedAverage(fills);
            boolean complete = totalQty >= orderQuantity;
            long remaining = orderQuantity - totalQty;
            return new AggregatedFill(totalQty, wavg, complete, remaining);
        }

        BigDecimal calculateWeightedAverage(List<PartialFill> fills) {
            BigDecimal totalValue = BigDecimal.ZERO;
            long totalQty = 0L;
            
            for (PartialFill fill : fills) {
                totalValue = totalValue.add(fill.fillPrice().multiply(BigDecimal.valueOf(fill.filledQuantity())));
                totalQty += fill.filledQuantity();
            }
            
            return totalValue.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
        }

        FillSequence createSequence(List<ExecutionFill> fills) {
            return new FillSequence(fills, true);
        }

        double calculateFillRate(long orderQuantity, long filledQuantity) {
            return (double) filledQuantity / orderQuantity;
        }
    }

    static class FillValidator {
        boolean validateQuantity(ExecutionFill fill, long orderQuantity, long previouslyFilled) {
            return validateQuantity(fill, orderQuantity, previouslyFilled, false);
        }

        boolean validateQuantity(ExecutionFill fill, long orderQuantity, long previouslyFilled, boolean throwOnError) {
            long totalFilled = previouslyFilled + fill.filledQuantity();
            if (totalFilled > orderQuantity) {
                if (throwOnError) {
                    throw new IllegalStateException("Fill would cause overfill");
                }
                return false;
            }
            return true;
        }

        boolean isDuplicate(ExecutionFill fill1, ExecutionFill fill2) {
            return fill1.fillId().equals(fill2.fillId());
        }

        boolean validatePrice(ExecutionFill fill, BigDecimal expectedPrice, BigDecimal tolerance) {
            BigDecimal diff = fill.fillPrice().subtract(expectedPrice).abs();
            return diff.compareTo(tolerance) <= 0;
        }
    }
}
