package com.ghatana.aiplatform.featurestore;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for {@link FeatureDriftDetector}.
 *
 * @doc.type test
 * @doc.purpose Validates PSI/KS drift detection, event emission, and edge cases
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("FeatureDriftDetector [GH-90000]")
class FeatureDriftDetectorTest {

    private final RecordingEventBus eventBus = new RecordingEventBus(); // GH-90000
    private FeatureDriftDetector detector;

    @BeforeEach
    void setUp() { // GH-90000
        detector = new FeatureDriftDetector(MetricsCollector.create(), eventBus); // GH-90000
    }

    @Nested
    @DisplayName("PSI computation [GH-90000]")
    class PsiTests {

        @Test
        @DisplayName("identical distributions yield PSI ≈ 0 [GH-90000]")
        void identicalDistributions() { // GH-90000
            double[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            FeatureDriftDetector.DriftResult result = detector.check("feat-same", data, data.clone()); // GH-90000

            assertThat(result.psi()).isCloseTo(0.0, within(0.01)); // GH-90000
            assertThat(result.status()).isEqualTo(FeatureDriftDetector.DriftStatus.STABLE); // GH-90000
        }

        @Test
        @DisplayName("similar distributions produce low PSI (STABLE) [GH-90000]")
        void similarDistributionsStable() { // GH-90000
            Random rng = new Random(42); // GH-90000
            double[] ref = rng.doubles(1000, 0, 100).toArray(); // GH-90000
            double[] cur = new Random(43).doubles(1000, 0, 100).toArray(); // GH-90000

            FeatureDriftDetector.DriftResult result = detector.check("feat-similar", ref, cur); // GH-90000

            assertThat(result.psi()).isLessThan(FeatureDriftDetector.PSI_DRIFT_THRESHOLD); // GH-90000
            assertThat(result.status()).isIn( // GH-90000
                    FeatureDriftDetector.DriftStatus.STABLE,
                    FeatureDriftDetector.DriftStatus.WARNING
            );
        }

        @Test
        @DisplayName("shifted distribution produces high PSI (DRIFT_DETECTED) [GH-90000]")
        void shiftedDistributionDrift() { // GH-90000
            Random rng = new Random(42); // GH-90000
            double[] ref = rng.doubles(1000, 0, 50).toArray(); // GH-90000
            double[] cur = new Random(43).doubles(1000, 50, 100).toArray(); // GH-90000

            FeatureDriftDetector.DriftResult result = detector.check("feat-shifted", ref, cur); // GH-90000

            assertThat(result.psi()).isGreaterThan(FeatureDriftDetector.PSI_DRIFT_THRESHOLD); // GH-90000
            assertThat(result.status()).isEqualTo(FeatureDriftDetector.DriftStatus.DRIFT_DETECTED); // GH-90000
        }

        @Test
        @DisplayName("constant distributions yield PSI = 0 [GH-90000]")
        void constantDistributions() { // GH-90000
            double[] ref = {5, 5, 5, 5, 5};
            double[] cur = {5, 5, 5, 5, 5};

            FeatureDriftDetector.DriftResult result = detector.check("feat-const", ref, cur); // GH-90000

            assertThat(result.psi()).isCloseTo(0.0, within(0.001)); // GH-90000
        }
    }

    @Nested
    @DisplayName("KS statistic [GH-90000]")
    class KsTests {

        @Test
        @DisplayName("identical samples yield KS ≈ 0 [GH-90000]")
        void identicalSamplesKsZero() { // GH-90000
            double[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

            double ks = detector.computeKS(data, data.clone()); // GH-90000

            assertThat(ks).isCloseTo(0.0, within(0.01)); // GH-90000
        }

        @Test
        @DisplayName("completely separated samples yield KS = 1.0 [GH-90000]")
        void separatedSamplesKsOne() { // GH-90000
            double[] ref = {1, 2, 3, 4, 5};
            double[] cur = {10, 11, 12, 13, 14};

            double ks = detector.computeKS(ref, cur); // GH-90000

            assertThat(ks).isCloseTo(1.0, within(0.01)); // GH-90000
        }

        @Test
        @DisplayName("partially overlapping samples produce intermediate KS [GH-90000]")
        void partialOverlap() { // GH-90000
            double[] ref = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            double[] cur = {5, 6, 7, 8, 9, 10, 11, 12, 13, 14};

            double ks = detector.computeKS(ref, cur); // GH-90000

            assertThat(ks).isBetween(0.2, 0.8); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event emission [GH-90000]")
    class EventTests {

        @Test
        @DisplayName("drift_detected event emitted when PSI exceeds threshold [GH-90000]")
        void emitsEventOnDrift() { // GH-90000
            Random rng = new Random(42); // GH-90000
            double[] ref = rng.doubles(1000, 0, 50).toArray(); // GH-90000
            double[] cur = new Random(43).doubles(1000, 50, 100).toArray(); // GH-90000

            detector.check("feat-drift-event", ref, cur); // GH-90000

            assertThat(eventBus.events).hasSize(1); // GH-90000
            FeatureDriftDetector.FeatureDriftEvent event =
                    (FeatureDriftDetector.FeatureDriftEvent) eventBus.events.get(0); // GH-90000
            assertThat(event.featureName()).isEqualTo("feat-drift-event [GH-90000]");
            assertThat(event.eventType()).isEqualTo("feature.drift_detected [GH-90000]");
            assertThat(event.psi()).isGreaterThan(FeatureDriftDetector.PSI_DRIFT_THRESHOLD); // GH-90000
        }

        @Test
        @DisplayName("no event emitted for stable distributions [GH-90000]")
        void noEventWhenStable() { // GH-90000
            double[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

            detector.check("feat-stable", data, data.clone()); // GH-90000

            assertThat(eventBus.events).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("no event emitted for warning-level drift [GH-90000]")
        void noEventOnWarning() { // GH-90000
            // Construct a mildly shifted distribution that produces 0.1 <= PSI < 0.2
            Random rng = new Random(42); // GH-90000
            double[] ref = rng.doubles(1000, 0, 100).toArray(); // GH-90000
            // Shift by a small amount
            double[] cur = new double[1000];
            Random rng2 = new Random(43); // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                cur[i] = rng2.nextDouble() * 100 + 10; // GH-90000
            }

            detector.check("feat-warn", ref, cur); // GH-90000

            // Event is only emitted for DRIFT_DETECTED, not WARNING
            for (Object e : eventBus.events) { // GH-90000
                FeatureDriftDetector.FeatureDriftEvent evt = (FeatureDriftDetector.FeatureDriftEvent) e; // GH-90000
                assertThat(evt.status()).isNotEqualTo(FeatureDriftDetector.DriftStatus.WARNING); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Result metadata [GH-90000]")
    class ResultTests {

        @Test
        @DisplayName("result includes feature name and sample counts [GH-90000]")
        void resultMetadata() { // GH-90000
            double[] ref = {1, 2, 3, 4, 5};
            double[] cur = {6, 7, 8, 9, 10, 11, 12};

            FeatureDriftDetector.DriftResult result = detector.check("feat-meta", ref, cur); // GH-90000

            assertThat(result.featureName()).isEqualTo("feat-meta [GH-90000]");
            assertThat(result.referenceCount()).isEqualTo(5); // GH-90000
            assertThat(result.currentCount()).isEqualTo(7); // GH-90000
            assertThat(result.checkedAt()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Input validation [GH-90000]")
    class ValidationTests {

        @Test
        @DisplayName("null reference values throws [GH-90000]")
        void nullReferenceThrows() { // GH-90000
            assertThatThrownBy(() -> detector.check("f", null, new double[]{1})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("empty reference values throws [GH-90000]")
        void emptyReferenceThrows() { // GH-90000
            assertThatThrownBy(() -> detector.check("f", new double[]{}, new double[]{1})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("null current values throws [GH-90000]")
        void nullCurrentThrows() { // GH-90000
            assertThatThrownBy(() -> detector.check("f", new double[]{1}, null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("bucket count < 2 throws [GH-90000]")
        void invalidBucketCount() { // GH-90000
            assertThatThrownBy(() -> new FeatureDriftDetector(MetricsCollector.create(), eventBus, 1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // --- Test doubles ---

    static class RecordingEventBus implements EventBusPort {
        final List<Object> events = new ArrayList<>(); // GH-90000

        @Override
        public void publish(Object event) { // GH-90000
            events.add(event); // GH-90000
        }
    }
}
