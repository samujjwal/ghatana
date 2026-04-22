package com.ghatana.platform.observability.metrics;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Histogram accuracy tests — validates bucket distribution, percentile calculations,
 * aggregation correctness, and behavior with boundary values.
 *
 * @doc.type class
 * @doc.purpose Tests for histogram metric accuracy and percentile correctness
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Histogram Accuracy Tests [GH-90000]")
class HistogramAccuracyTest extends EventloopTestBase {

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000
    }

    // ── Bucket distribution ───────────────────────────────────────────────────

    @Nested
    @DisplayName("bucket distribution [GH-90000]")
    class BucketDistribution {

        @Test
        @DisplayName("single recorded value increments count to 1 [GH-90000]")
        void singleRecordedValue_incrementsCountTo1() { // GH-90000
            DistributionSummary summary = DistributionSummary
                    .builder("test.latency [GH-90000]")
                    .register(meterRegistry); // GH-90000

            summary.record(100.0); // GH-90000

            assertThat(summary.count()).isEqualTo(1); // GH-90000
            assertThat(summary.totalAmount()).isCloseTo(100.0, within(0.001)); // GH-90000
        }

        @Test
        @DisplayName("multiple values produce correct count and sum [GH-90000]")
        void multipleValues_produceCorrectCountAndSum() { // GH-90000
            DistributionSummary summary = DistributionSummary
                    .builder("test.latency.multi [GH-90000]")
                    .register(meterRegistry); // GH-90000

            List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0); // GH-90000
            values.forEach(summary::record); // GH-90000

            assertThat(summary.count()).isEqualTo(5); // GH-90000
            assertThat(summary.totalAmount()).isCloseTo(150.0, within(0.001)); // GH-90000
        }

        @Test
        @DisplayName("average (mean) computed correctly from recorded values [GH-90000]")
        void average_computedCorrectly() { // GH-90000
            DistributionSummary summary = DistributionSummary
                    .builder("test.latency.mean [GH-90000]")
                    .register(meterRegistry); // GH-90000

            summary.record(10.0); // GH-90000
            summary.record(20.0); // GH-90000
            summary.record(30.0); // GH-90000

            double expectedMean = (10.0 + 20.0 + 30.0) / 3; // GH-90000
            assertThat(summary.mean()).isCloseTo(expectedMean, within(0.01)); // GH-90000
        }
    }

    // ── Percentile calculation (manual) ────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("percentile calculation [GH-90000]")
    class PercentileCalculation {

        @Test
        @DisplayName("P50 (median) is correctly computed for odd-count dataset [GH-90000]")
        void p50_correctForOddCountDataset() { // GH-90000
            double[] data = {10, 20, 30, 40, 50};
            Arrays.sort(data); // GH-90000
            double p50 = percentile(data, 50); // GH-90000

            assertThat(p50).isCloseTo(30.0, within(0.001)); // GH-90000
        }

        @Test
        @DisplayName("P95 is correctly computed for 100-element dataset [GH-90000]")
        void p95_correctFor100ElementDataset() { // GH-90000
            double[] data = new double[100];
            for (int i = 0; i < 100; i++) { // GH-90000
                data[i] = i + 1;
            }
            Arrays.sort(data); // GH-90000
            double p95 = percentile(data, 95); // GH-90000

            assertThat(p95).isCloseTo(95.5, within(1.0)); // GH-90000
        }

        @Test
        @DisplayName("P99 is correctly computed for 100-element dataset [GH-90000]")
        void p99_correctFor100ElementDataset() { // GH-90000
            double[] data = new double[100];
            for (int i = 0; i < 100; i++) { // GH-90000
                data[i] = i + 1;
            }
            Arrays.sort(data); // GH-90000
            double p99 = percentile(data, 99); // GH-90000

            assertThat(p99).isGreaterThan(95.0); // GH-90000
        }

        @Test
        @DisplayName("P100 equals maximum value [GH-90000]")
        void p100_equalsMaximumValue() { // GH-90000
            double[] data = {5, 15, 25, 50, 100, 200};
            Arrays.sort(data); // GH-90000
            double p100 = percentile(data, 100); // GH-90000

            assertThat(p100).isCloseTo(200.0, within(0.001)); // GH-90000
        }

        private double percentile(double[] sortedData, int percentile) { // GH-90000
            if (sortedData.length == 0) return 0; // GH-90000
            double index = (percentile / 100.0) * (sortedData.length - 1); // GH-90000
            int lower = (int) Math.floor(index); // GH-90000
            int upper = (int) Math.ceil(index); // GH-90000
            if (lower == upper) return sortedData[lower]; // GH-90000
            double fraction = index - lower;
            return sortedData[lower] * (1 - fraction) + sortedData[upper] * fraction; // GH-90000
        }
    }

    // ── Histogram aggregation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("histogram aggregation [GH-90000]")
    class HistogramAggregation {

        @Test
        @DisplayName("two histograms can be merged by summing counts and totals [GH-90000]")
        void twoHistograms_canBeMergedBySummingCountsAndTotals() { // GH-90000
            DistributionSummary h1 = DistributionSummary.builder("hist.a [GH-90000]").register(meterRegistry);
            DistributionSummary h2 = DistributionSummary.builder("hist.b [GH-90000]").register(meterRegistry);

            h1.record(10.0); // GH-90000
            h1.record(20.0); // GH-90000
            h2.record(30.0); // GH-90000
            h2.record(40.0); // GH-90000

            long mergedCount = h1.count() + h2.count(); // GH-90000
            double mergedTotal = h1.totalAmount() + h2.totalAmount(); // GH-90000

            assertThat(mergedCount).isEqualTo(4); // GH-90000
            assertThat(mergedTotal).isCloseTo(100.0, within(0.001)); // GH-90000
        }
    }

    // ── Boundary values ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("boundary values [GH-90000]")
    class BoundaryValues {

        @Test
        @DisplayName("recording zero is accepted and reflected in count [GH-90000]")
        void recordingZero_isAccepted() { // GH-90000
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.zero [GH-90000]")
                    .register(meterRegistry); // GH-90000

            summary.record(0.0); // GH-90000

            assertThat(summary.count()).isEqualTo(1); // GH-90000
            assertThat(summary.totalAmount()).isCloseTo(0.0, within(0.0001)); // GH-90000
        }

        @Test
        @DisplayName("recording very large value does not overflow [GH-90000]")
        void recordingVeryLargeValue_doesNotOverflow() { // GH-90000
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.large [GH-90000]")
                    .register(meterRegistry); // GH-90000

            double largeValue = Double.MAX_VALUE / 2;
            summary.record(largeValue); // GH-90000

            assertThat(summary.count()).isEqualTo(1); // GH-90000
            assertThat(summary.totalAmount()).isFinite(); // GH-90000
        }

        @Test
        @DisplayName("empty histogram has count 0 and total 0 [GH-90000]")
        void emptyHistogram_hasCountZeroAndTotalZero() { // GH-90000
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.empty [GH-90000]")
                    .register(meterRegistry); // GH-90000

            assertThat(summary.count()).isEqualTo(0); // GH-90000
            assertThat(summary.totalAmount()).isCloseTo(0.0, within(0.0001)); // GH-90000
        }

        @Test
        @DisplayName("single-element histogram mean equals that element [GH-90000]")
        void singleElementHistogram_meanEqualsThatElement() { // GH-90000
            DistributionSummary summary = DistributionSummary
                    .builder("test.boundary.single [GH-90000]")
                    .register(meterRegistry); // GH-90000

            summary.record(42.0); // GH-90000

            assertThat(summary.mean()).isCloseTo(42.0, within(0.001)); // GH-90000
        }
    }
}
