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
@DisplayName("FeatureDriftDetector")
class FeatureDriftDetectorTest {

    private final RecordingEventBus eventBus = new RecordingEventBus(); 
    private FeatureDriftDetector detector;

    @BeforeEach
    void setUp() { 
        detector = new FeatureDriftDetector(MetricsCollector.create(), eventBus); 
    }

    @Nested
    @DisplayName("PSI computation")
    class PsiTests {

        @Test
        @DisplayName("identical distributions yield PSI ≈ 0")
        void identicalDistributions() { 
            double[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            FeatureDriftDetector.DriftResult result = detector.check("feat-same", data, data.clone()); 

            assertThat(result.psi()).isCloseTo(0.0, within(0.01)); 
            assertThat(result.status()).isEqualTo(FeatureDriftDetector.DriftStatus.STABLE); 
        }

        @Test
        @DisplayName("similar distributions produce low PSI (STABLE)")
        void similarDistributionsStable() { 
            Random rng = new Random(42); 
            double[] ref = rng.doubles(1000, 0, 100).toArray(); 
            double[] cur = new Random(43).doubles(1000, 0, 100).toArray(); 

            FeatureDriftDetector.DriftResult result = detector.check("feat-similar", ref, cur); 

            assertThat(result.psi()).isLessThan(FeatureDriftDetector.PSI_DRIFT_THRESHOLD); 
            assertThat(result.status()).isIn( 
                    FeatureDriftDetector.DriftStatus.STABLE,
                    FeatureDriftDetector.DriftStatus.WARNING
            );
        }

        @Test
        @DisplayName("shifted distribution produces high PSI (DRIFT_DETECTED)")
        void shiftedDistributionDrift() { 
            Random rng = new Random(42); 
            double[] ref = rng.doubles(1000, 0, 50).toArray(); 
            double[] cur = new Random(43).doubles(1000, 50, 100).toArray(); 

            FeatureDriftDetector.DriftResult result = detector.check("feat-shifted", ref, cur); 

            assertThat(result.psi()).isGreaterThan(FeatureDriftDetector.PSI_DRIFT_THRESHOLD); 
            assertThat(result.status()).isEqualTo(FeatureDriftDetector.DriftStatus.DRIFT_DETECTED); 
        }

        @Test
        @DisplayName("constant distributions yield PSI = 0")
        void constantDistributions() { 
            double[] ref = {5, 5, 5, 5, 5};
            double[] cur = {5, 5, 5, 5, 5};

            FeatureDriftDetector.DriftResult result = detector.check("feat-const", ref, cur); 

            assertThat(result.psi()).isCloseTo(0.0, within(0.001)); 
        }
    }

    @Nested
    @DisplayName("KS statistic")
    class KsTests {

        @Test
        @DisplayName("identical samples yield KS ≈ 0")
        void identicalSamplesKsZero() { 
            double[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

            double ks = detector.computeKS(data, data.clone()); 

            assertThat(ks).isCloseTo(0.0, within(0.01)); 
        }

        @Test
        @DisplayName("completely separated samples yield KS = 1.0")
        void separatedSamplesKsOne() { 
            double[] ref = {1, 2, 3, 4, 5};
            double[] cur = {10, 11, 12, 13, 14};

            double ks = detector.computeKS(ref, cur); 

            assertThat(ks).isCloseTo(1.0, within(0.01)); 
        }

        @Test
        @DisplayName("partially overlapping samples produce intermediate KS")
        void partialOverlap() { 
            double[] ref = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            double[] cur = {5, 6, 7, 8, 9, 10, 11, 12, 13, 14};

            double ks = detector.computeKS(ref, cur); 

            assertThat(ks).isBetween(0.2, 0.8); 
        }
    }

    @Nested
    @DisplayName("Event emission")
    class EventTests {

        @Test
        @DisplayName("drift_detected event emitted when PSI exceeds threshold")
        void emitsEventOnDrift() { 
            Random rng = new Random(42); 
            double[] ref = rng.doubles(1000, 0, 50).toArray(); 
            double[] cur = new Random(43).doubles(1000, 50, 100).toArray(); 

            detector.check("feat-drift-event", ref, cur); 

            assertThat(eventBus.events).hasSize(1); 
            FeatureDriftDetector.FeatureDriftEvent event =
                    (FeatureDriftDetector.FeatureDriftEvent) eventBus.events.get(0); 
            assertThat(event.featureName()).isEqualTo("feat-drift-event");
            assertThat(event.eventType()).isEqualTo("feature.drift_detected");
            assertThat(event.psi()).isGreaterThan(FeatureDriftDetector.PSI_DRIFT_THRESHOLD); 
        }

        @Test
        @DisplayName("no event emitted for stable distributions")
        void noEventWhenStable() { 
            double[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

            detector.check("feat-stable", data, data.clone()); 

            assertThat(eventBus.events).isEmpty(); 
        }

        @Test
        @DisplayName("no event emitted for warning-level drift")
        void noEventOnWarning() { 
            // Construct a mildly shifted distribution that produces 0.1 <= PSI < 0.2
            Random rng = new Random(42); 
            double[] ref = rng.doubles(1000, 0, 100).toArray(); 
            // Shift by a small amount
            double[] cur = new double[1000];
            Random rng2 = new Random(43); 
            for (int i = 0; i < 1000; i++) { 
                cur[i] = rng2.nextDouble() * 100 + 10; 
            }

            detector.check("feat-warn", ref, cur); 

            // Event is only emitted for DRIFT_DETECTED, not WARNING
            for (Object e : eventBus.events) { 
                FeatureDriftDetector.FeatureDriftEvent evt = (FeatureDriftDetector.FeatureDriftEvent) e; 
                assertThat(evt.status()).isNotEqualTo(FeatureDriftDetector.DriftStatus.WARNING); 
            }
        }
    }

    @Nested
    @DisplayName("Result metadata")
    class ResultTests {

        @Test
        @DisplayName("result includes feature name and sample counts")
        void resultMetadata() { 
            double[] ref = {1, 2, 3, 4, 5};
            double[] cur = {6, 7, 8, 9, 10, 11, 12};

            FeatureDriftDetector.DriftResult result = detector.check("feat-meta", ref, cur); 

            assertThat(result.featureName()).isEqualTo("feat-meta");
            assertThat(result.referenceCount()).isEqualTo(5); 
            assertThat(result.currentCount()).isEqualTo(7); 
            assertThat(result.checkedAt()).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("Input validation")
    class ValidationTests {

        @Test
        @DisplayName("null reference values throws")
        void nullReferenceThrows() { 
            assertThatThrownBy(() -> detector.check("f", null, new double[]{1})) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("empty reference values throws")
        void emptyReferenceThrows() { 
            assertThatThrownBy(() -> detector.check("f", new double[]{}, new double[]{1})) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("null current values throws")
        void nullCurrentThrows() { 
            assertThatThrownBy(() -> detector.check("f", new double[]{1}, null)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("bucket count < 2 throws")
        void invalidBucketCount() { 
            assertThatThrownBy(() -> new FeatureDriftDetector(MetricsCollector.create(), eventBus, 1)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    // --- Test doubles ---

    static class RecordingEventBus implements EventBusPort {
        final List<Object> events = new ArrayList<>(); 

        @Override
        public void publish(Object event) { 
            events.add(event); 
        }
    }
}
