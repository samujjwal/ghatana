package com.ghatana.products.finance.domains.ems.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for execution algorithms (VWAP, TWAP, POV) per D02-005
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Execution Algorithm Tests")
class ExecutionAlgorithmTest {

    private VwapAlgorithm vwapAlgorithm;
    private TwapAlgorithm twapAlgorithm;
    private PovAlgorithm povAlgorithm;

    @BeforeEach
    void setUp() {
        vwapAlgorithm = new VwapAlgorithm();
        twapAlgorithm = new TwapAlgorithm();
        povAlgorithm = new PovAlgorithm();
    }

    @Test
    @DisplayName("Should calculate VWAP slices correctly")
    void shouldCalculateVwapSlicesCorrectly() {
        List<VolumeProfile> profile = List.of(
            new VolumeProfile(Instant.parse("2024-04-04T09:30:00Z"), 1000L),
            new VolumeProfile(Instant.parse("2024-04-04T10:00:00Z"), 2000L),
            new VolumeProfile(Instant.parse("2024-04-04T10:30:00Z"), 1500L)
        );

        List<AlgorithmSlice> slices = vwapAlgorithm.calculateSlices(5000L, profile);

        assertThat(slices).hasSize(3);
        assertThat(slices.stream().mapToLong(AlgorithmSlice::quantity).sum()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should distribute TWAP slices evenly")
    void shouldDistributeTwapSlicesEvenly() {
        Instant startTime = Instant.parse("2024-04-04T09:30:00Z");
        Instant endTime = Instant.parse("2024-04-04T16:00:00Z");

        List<AlgorithmSlice> slices = twapAlgorithm.calculateSlices(
            10000L,
            startTime,
            endTime,
            10
        );

        assertThat(slices).hasSize(10);
        assertThat(slices).allMatch(slice -> slice.quantity() == 1000L);
    }

    @Test
    @DisplayName("Should calculate POV participation rate")
    void shouldCalculatePovParticipationRate() {
        long marketVolume = 100000L;
        double targetRate = 0.10;

        long orderQuantity = povAlgorithm.calculateQuantity(marketVolume, targetRate);

        assertThat(orderQuantity).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should adjust VWAP slices based on market conditions")
    void shouldAdjustVwapSlicesBasedOnMarketConditions() {
        List<VolumeProfile> profile = List.of(
            new VolumeProfile(Instant.parse("2024-04-04T09:30:00Z"), 500L),
            new VolumeProfile(Instant.parse("2024-04-04T10:00:00Z"), 3000L),
            new VolumeProfile(Instant.parse("2024-04-04T10:30:00Z"), 1500L)
        );

        List<AlgorithmSlice> slices = vwapAlgorithm.calculateSlices(5000L, profile);

        AlgorithmSlice largestSlice = slices.stream()
            .max((a, b) -> Long.compare(a.quantity(), b.quantity()))
            .orElseThrow();

        assertThat(largestSlice.executeAt()).isEqualTo(Instant.parse("2024-04-04T10:00:00Z"));
    }

    @Test
    @DisplayName("Should enforce minimum slice size for TWAP")
    void shouldEnforceMinimumSliceSizeForTwap() {
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(3600);

        List<AlgorithmSlice> slices = twapAlgorithm.calculateSlices(
            500L,
            startTime,
            endTime,
            10,
            100L
        );

        assertThat(slices).allMatch(slice -> slice.quantity() >= 100L);
    }

    @Test
    @DisplayName("Should cap POV participation at maximum rate")
    void shouldCapPovParticipationAtMaximumRate() {
        long marketVolume = 100000L;
        double targetRate = 0.30;
        double maxRate = 0.20;

        long orderQuantity = povAlgorithm.calculateQuantity(marketVolume, targetRate, maxRate);

        assertThat(orderQuantity).isEqualTo(20000L);
    }

    @Test
    @DisplayName("Should handle VWAP with no historical volume data")
    void shouldHandleVwapWithNoHistoricalVolumeData() {
        List<VolumeProfile> emptyProfile = List.of();

        List<AlgorithmSlice> slices = vwapAlgorithm.calculateSlicesWithFallback(
            5000L,
            emptyProfile,
            10
        );

        assertThat(slices).hasSize(10);
        assertThat(slices).allMatch(slice -> slice.quantity() == 500L);
    }

    @Test
    @DisplayName("Should calculate urgency-adjusted TWAP slices")
    void shouldCalculateUrgencyAdjustedTwapSlices() {
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(3600);
        double urgency = 1.5;

        List<AlgorithmSlice> slices = twapAlgorithm.calculateSlicesWithUrgency(
            10000L,
            startTime,
            endTime,
            10,
            urgency
        );

        long firstSliceQty = slices.get(0).quantity();
        long lastSliceQty = slices.get(slices.size() - 1).quantity();

        assertThat(firstSliceQty).isGreaterThan(lastSliceQty);
    }

    @Test
    @DisplayName("Should track POV execution progress")
    void shouldTrackPovExecutionProgress() {
        long totalQuantity = 10000L;
        long executedQuantity = 3000L;
        long currentMarketVolume = 50000L;

        PovProgress progress = povAlgorithm.calculateProgress(
            totalQuantity,
            executedQuantity,
            currentMarketVolume,
            0.10
        );

        assertThat(progress.percentComplete()).isEqualTo(30.0);
        assertThat(progress.onTarget()).isTrue();
    }

    @Test
    @DisplayName("Should validate algorithm parameters")
    void shouldValidateAlgorithmParameters() {
        assertThatThrownBy(() -> twapAlgorithm.calculateSlices(
            -1000L,
            Instant.now(),
            Instant.now().plusSeconds(3600),
            10
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Quantity must be positive");
    }

    record VolumeProfile(Instant time, long volume) {}
    record AlgorithmSlice(Instant executeAt, long quantity, BigDecimal limitPrice) {
        AlgorithmSlice(Instant executeAt, long quantity) {
            this(executeAt, quantity, null);
        }
    }
    record PovProgress(double percentComplete, boolean onTarget, long remainingQuantity) {}

    static class VwapAlgorithm {
        List<AlgorithmSlice> calculateSlices(long totalQuantity, List<VolumeProfile> profile) {
            long totalVolume = profile.stream().mapToLong(VolumeProfile::volume).sum();
            return profile.stream()
                .map(vp -> new AlgorithmSlice(
                    vp.time(),
                    (long) (totalQuantity * ((double) vp.volume() / totalVolume))
                ))
                .toList();
        }

        List<AlgorithmSlice> calculateSlicesWithFallback(long totalQuantity, List<VolumeProfile> profile, int sliceCount) {
            if (profile.isEmpty()) {
                return twapFallback(totalQuantity, sliceCount);
            }
            return calculateSlices(totalQuantity, profile);
        }

        private List<AlgorithmSlice> twapFallback(long totalQuantity, int sliceCount) {
            long sliceSize = totalQuantity / sliceCount;
            Instant now = Instant.now();
            return java.util.stream.IntStream.range(0, sliceCount)
                .mapToObj(i -> new AlgorithmSlice(now.plusSeconds(i * 300L), sliceSize))
                .toList();
        }
    }

    static class TwapAlgorithm {
        List<AlgorithmSlice> calculateSlices(long totalQuantity, Instant startTime, Instant endTime, int sliceCount) {
            return calculateSlices(totalQuantity, startTime, endTime, sliceCount, 0L);
        }

        List<AlgorithmSlice> calculateSlices(long totalQuantity, Instant startTime, Instant endTime,
                                            int sliceCount, long minSliceSize) {
            if (totalQuantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            long sliceSize = Math.max(totalQuantity / sliceCount, minSliceSize);
            long intervalSeconds = (endTime.getEpochSecond() - startTime.getEpochSecond()) / sliceCount;

            return java.util.stream.IntStream.range(0, sliceCount)
                .mapToObj(i -> new AlgorithmSlice(
                    startTime.plusSeconds(i * intervalSeconds),
                    sliceSize
                ))
                .toList();
        }

        List<AlgorithmSlice> calculateSlicesWithUrgency(long totalQuantity, Instant startTime,
                                                        Instant endTime, int sliceCount, double urgency) {
            long intervalSeconds = (endTime.getEpochSecond() - startTime.getEpochSecond()) / sliceCount;
            double totalWeight = java.util.stream.IntStream.range(0, sliceCount)
                .mapToDouble(i -> Math.pow(urgency, sliceCount - i - 1))
                .sum();

            return java.util.stream.IntStream.range(0, sliceCount)
                .mapToObj(i -> {
                    double weight = Math.pow(urgency, sliceCount - i - 1);
                    long qty = (long) (totalQuantity * (weight / totalWeight));
                    return new AlgorithmSlice(startTime.plusSeconds(i * intervalSeconds), qty);
                })
                .toList();
        }
    }

    static class PovAlgorithm {
        long calculateQuantity(long marketVolume, double targetRate) {
            return (long) (marketVolume * targetRate);
        }

        long calculateQuantity(long marketVolume, double targetRate, double maxRate) {
            double effectiveRate = Math.min(targetRate, maxRate);
            return (long) (marketVolume * effectiveRate);
        }

        PovProgress calculateProgress(long totalQuantity, long executedQuantity,
                                     long currentMarketVolume, double targetRate) {
            double percentComplete = (executedQuantity * 100.0) / totalQuantity;
            long targetExecuted = (long) (currentMarketVolume * targetRate);
            boolean onTarget = executedQuantity >= (targetExecuted * 0.9);

            return new PovProgress(percentComplete, onTarget, totalQuantity - executedQuantity);
        }
    }
}
