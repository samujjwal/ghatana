package com.ghatana.agent.learning.retention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for decay functions: {@link ExponentialDecay}, {@link PowerLawDecay}, {@link StepDecay}.
 */
@DisplayName("Decay Functions")
class DecayFunctionTest {

    @Nested
    @DisplayName("ExponentialDecay")
    class ExponentialDecayTest {

        @Test
        void shouldReturnOneAtAgeZero() {
            ExponentialDecay decay = new ExponentialDecay(168.0);
            assertThat(decay.compute(0.0)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void shouldReturnHalfAtHalfLife() {
            double halfLifeHours = 168.0; // 7 days
            ExponentialDecay decay = new ExponentialDecay(halfLifeHours);
            assertThat(decay.compute(halfLifeHours)).isCloseTo(0.5, within(1e-9));
        }

        @Test
        void shouldDecreaseMonotonically() {
            ExponentialDecay decay = ExponentialDecay.sevenDay();
            double prev = decay.compute(0);
            for (double t = 1; t <= 500; t += 10) {
                double current = decay.compute(t);
                assertThat(current).isLessThan(prev);
                prev = current;
            }
        }

        @Test
        void shouldApproachZeroForLargeAge() {
            ExponentialDecay decay = new ExponentialDecay(24.0);
            assertThat(decay.compute(10000)).isCloseTo(0.0, within(1e-6));
        }

        @Test
        void shouldRejectNonPositiveHalfLife() {
            assertThatThrownBy(() -> new ExponentialDecay(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExponentialDecay(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldCreateSevenDayPreset() {
            ExponentialDecay decay = ExponentialDecay.sevenDay();
            // Half-life = 7 * 24 = 168 hours
            assertThat(decay.compute(168.0)).isCloseTo(0.5, within(1e-9));
        }
    }

    @Nested
    @DisplayName("PowerLawDecay")
    class PowerLawDecayTest {

        @Test
        void shouldReturnOneAtAgeZero() {
            PowerLawDecay decay = new PowerLawDecay(168.0, 1.5);
            assertThat(decay.compute(0.0)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void shouldDecreaseMonotonically() {
            PowerLawDecay decay = PowerLawDecay.defaultDecay();
            double prev = decay.compute(0);
            for (double t = 1; t <= 1000; t += 10) {
                double current = decay.compute(t);
                assertThat(current).isLessThan(prev);
                prev = current;
            }
        }

        @Test
        void shouldDecaySlowerThanExponentialForLargeAge() {
            ExponentialDecay exponential = new ExponentialDecay(168.0);
            PowerLawDecay powerLaw = new PowerLawDecay(168.0, 1.5);

            // At large age, power-law should still retain more value
            double largeAge = 5000.0; // ~208 days
            assertThat(powerLaw.compute(largeAge)).isGreaterThan(exponential.compute(largeAge));
        }

        @Test
        void shouldRejectNonPositiveParameters() {
            assertThatThrownBy(() -> new PowerLawDecay(0, 1.5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PowerLawDecay(168.0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("StepDecay")
    class StepDecayTest {

        @Test
        void shouldReturnCorrectTierValues() {
            StepDecay decay = StepDecay.defaultTiered();
            // 0-24h: 1.0
            assertThat(decay.compute(0.0)).isCloseTo(1.0, within(1e-9));
            assertThat(decay.compute(12.0)).isCloseTo(1.0, within(1e-9));
            // 24-168h: 0.7
            assertThat(decay.compute(24.0)).isCloseTo(0.7, within(1e-9));
            assertThat(decay.compute(100.0)).isCloseTo(0.7, within(1e-9));
            // 168-720h: 0.3
            assertThat(decay.compute(168.0)).isCloseTo(0.3, within(1e-9));
            assertThat(decay.compute(500.0)).isCloseTo(0.3, within(1e-9));
            // 720h+: 0.1
            assertThat(decay.compute(720.0)).isCloseTo(0.1, within(1e-9));
            assertThat(decay.compute(10000.0)).isCloseTo(0.1, within(1e-9));
        }

        @Test
        void shouldRejectMismatchedArrayLengths() {
            assertThatThrownBy(() -> new StepDecay(
                    new double[]{24.0, 168.0},
                    new double[]{1.0, 0.5} // needs 3 values for 2 thresholds
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldWorkWithSingleTier() {
            StepDecay decay = new StepDecay(
                    new double[]{100.0},
                    new double[]{1.0, 0.0}
            );
            assertThat(decay.compute(50.0)).isCloseTo(1.0, within(1e-9));
            assertThat(decay.compute(100.0)).isCloseTo(0.0, within(1e-9));
        }
    }
}
