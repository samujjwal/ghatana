package com.ghatana.platform.testing.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the chaos testing framework.
 *
 * @doc.type class
 * @doc.purpose Validates chaos testing framework functionality
 * @doc.layer core
 * @doc.pattern UnitTest
 */
@DisplayName("Chaos Testing Framework Tests [GH-90000]")
class ChaosFrameworkTest {

    @Nested
    @DisplayName("ChaosType Tests [GH-90000]")
    class ChaosTypeTests {

        @Test
        @DisplayName("should have all expected chaos types [GH-90000]")
        void shouldHaveAllExpectedTypes() { // GH-90000
            assertThat(ChaosType.values()).hasSize(10); // GH-90000
        }

        @Test
        @DisplayName("NETWORK should be network-related [GH-90000]")
        void networkShouldBeNetworkRelated() { // GH-90000
            assertThat(ChaosType.NETWORK.isNetworkRelated()).isTrue(); // GH-90000
            assertThat(ChaosType.NETWORK.isDataRelated()).isFalse(); // GH-90000
            assertThat(ChaosType.NETWORK.isResourceRelated()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DATA_CORRUPTION should be data-related [GH-90000]")
        void dataCorruptionShouldBeDataRelated() { // GH-90000
            assertThat(ChaosType.DATA_CORRUPTION.isDataRelated()).isTrue(); // GH-90000
            assertThat(ChaosType.DATA_CORRUPTION.isNetworkRelated()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("RESOURCE_EXHAUSTION should be resource-related [GH-90000]")
        void resourceExhaustionShouldBeResourceRelated() { // GH-90000
            assertThat(ChaosType.RESOURCE_EXHAUSTION.isResourceRelated()).isTrue(); // GH-90000
            assertThat(ChaosType.RESOURCE_EXHAUSTION.isNetworkRelated()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("all types should have descriptions [GH-90000]")
        void allTypesShouldHaveDescriptions() { // GH-90000
            for (ChaosType type : ChaosType.values()) { // GH-90000
                assertThat(type.getDescription()).isNotBlank(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("ChaosContext Tests [GH-90000]")
    class ChaosContextTests {

        @Test
        @DisplayName("should initialize with correct values [GH-90000]")
        void shouldInitializeWithCorrectValues() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 0.5, 5000); // GH-90000

            assertThat(context.getChaosType()).isEqualTo(ChaosType.NETWORK); // GH-90000
            assertThat(context.getFailureProbability()).isEqualTo(0.5); // GH-90000
            assertThat(context.getMaxDurationMs()).isEqualTo(5000); // GH-90000
            assertThat(context.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should clamp probability to valid range [GH-90000]")
        void shouldClampProbability() { // GH-90000
            ChaosContext tooHigh = new ChaosContext(ChaosType.RANDOM, 1.5, 1000); // GH-90000
            ChaosContext tooLow = new ChaosContext(ChaosType.RANDOM, -0.5, 1000); // GH-90000

            assertThat(tooHigh.getFailureProbability()).isEqualTo(1.0); // GH-90000
            assertThat(tooLow.getFailureProbability()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("should track injection and failure counts [GH-90000]")
        void shouldTrackCounts() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 1.0, 5000); // GH-90000

            context.recordInjection(); // GH-90000
            context.recordInjection(); // GH-90000
            context.recordFailure(); // GH-90000

            assertThat(context.getInjectionCount()).isEqualTo(2); // GH-90000
            assertThat(context.getFailureCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("shouldInjectFailure respects probability 0 [GH-90000]")
        void shouldNotInjectWhenProbabilityZero() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 0.0, 5000); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                assertThat(context.shouldInjectFailure()).isFalse(); // GH-90000
            }
        }

        @Test
        @DisplayName("shouldInjectFailure respects probability 1 [GH-90000]")
        void shouldAlwaysInjectWhenProbabilityOne() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 1.0, 5000); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                assertThat(context.shouldInjectFailure()).isTrue(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("ChaosInjector Tests [GH-90000]")
    class ChaosInjectorTests {

        @Test
        @DisplayName("should be inactive by default [GH-90000]")
        void shouldBeInactiveByDefault() { // GH-90000
            assertThat(ChaosInjector.isActive()).isFalse(); // GH-90000
            assertThat(ChaosInjector.getContext()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should activate and deactivate [GH-90000]")
        void shouldActivateAndDeactivate() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 0.5, 5000); // GH-90000

            ChaosInjector.activate(context); // GH-90000
            assertThat(ChaosInjector.isActive()).isTrue(); // GH-90000
            assertThat(ChaosInjector.getContext()).isSameAs(context); // GH-90000

            ChaosInjector.deactivate(); // GH-90000
            assertThat(ChaosInjector.isActive()).isFalse(); // GH-90000
            assertThat(ChaosInjector.getContext()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("maybeCorruptData returns original when inactive [GH-90000]")
        void maybeCorruptDataReturnsOriginalWhenInactive() { // GH-90000
            String original = "test data";
            String result = ChaosInjector.maybeCorruptData(original); // GH-90000
            assertThat(result).isEqualTo(original); // GH-90000
        }

        @Test
        @DisplayName("maybeCorruptData can corrupt data when active [GH-90000]")
        void maybeCorruptDataCanCorruptWhenActive() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.DATA_CORRUPTION, 1.0, 5000); // GH-90000
            ChaosInjector.activate(context); // GH-90000

            try {
                String result = ChaosInjector.maybeCorruptData("test [GH-90000]");
                assertThat(result).isNull(); // Corruption returns null // GH-90000
            } finally {
                ChaosInjector.deactivate(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("ChaosScenario Tests [GH-90000]")
    class ChaosScenarioTests {

        @Test
        @DisplayName("should execute operations successfully without chaos [GH-90000]")
        void shouldExecuteWithoutChaos() { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<Integer> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.RANDOM) // GH-90000
                    .withFailureProbability(0.0) // No chaos // GH-90000
                    .withIterations(10) // GH-90000
                    .execute(counter::incrementAndGet); // GH-90000

            assertThat(result.getSuccessCount()).isEqualTo(10); // GH-90000
            assertThat(result.getFailureCount()).isZero(); // GH-90000
            assertThat(result.getSuccessRate()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("should track failures when operation throws [GH-90000]")
        void shouldTrackFailures() { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withIterations(10) // GH-90000
                    .execute(() -> { // GH-90000
                        if (counter.incrementAndGet() % 2 == 0) { // GH-90000
                            throw new RuntimeException("Simulated failure [GH-90000]");
                        }
                    });

            assertThat(result.getSuccessCount()).isEqualTo(5); // GH-90000
            assertThat(result.getFailureCount()).isEqualTo(5); // GH-90000
            assertThat(result.getSuccessRate()).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("assertAllSucceeded throws when failures exist [GH-90000]")
        void assertAllSucceededThrowsOnFailures() { // GH-90000
            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withIterations(1) // GH-90000
                    .execute(() -> { // GH-90000
                        throw new RuntimeException("fail [GH-90000]");
                    });

            assertThatThrownBy(result::assertAllSucceeded) // GH-90000
                    .isInstanceOf(AssertionError.class) // GH-90000
                    .hasMessageContaining("1 failed [GH-90000]");
        }

        @Test
        @DisplayName("assertSuccessRate validates minimum rate [GH-90000]")
        void assertSuccessRateValidatesMinRate() { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withIterations(10) // GH-90000
                    .execute(() -> { // GH-90000
                        if (counter.incrementAndGet() <= 8) { // GH-90000
                            // 8 successes
                        } else {
                            throw new RuntimeException("fail [GH-90000]");
                        }
                    });

            // 80% success rate
            result.assertSuccessRate(0.8); // GH-90000

            assertThatThrownBy(() -> result.assertSuccessRate(0.9)) // GH-90000
                    .isInstanceOf(AssertionError.class); // GH-90000
        }

        @Test
        @DisplayName("should calculate average duration [GH-90000]")
        void shouldCalculateAverageDuration() { // GH-90000
            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withIterations(5) // GH-90000
                    .execute(() -> { // GH-90000
                        try {
                            Thread.sleep(10); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                    });

            Duration avgDuration = result.getAverageDuration(); // GH-90000
            assertThat(avgDuration.toMillis()).isGreaterThanOrEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("should support concurrent execution [GH-90000]")
        void shouldSupportConcurrentExecution() { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<Integer> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withConcurrency(4) // GH-90000
                    .withIterations(20) // GH-90000
                    .withDuration(Duration.ofSeconds(5)) // GH-90000
                    .execute(counter::incrementAndGet); // GH-90000

            assertThat(result.getSuccessCount()).isEqualTo(20); // GH-90000
            assertThat(counter.get()).isEqualTo(20); // GH-90000
        }
    }
}
