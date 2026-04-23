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
@DisplayName("Chaos Testing Framework Tests")
class ChaosFrameworkTest {

    @Nested
    @DisplayName("ChaosType Tests")
    class ChaosTypeTests {

        @Test
        @DisplayName("should have all expected chaos types")
        void shouldHaveAllExpectedTypes() { // GH-90000
            assertThat(ChaosType.values()).hasSize(10); // GH-90000
        }

        @Test
        @DisplayName("NETWORK should be network-related")
        void networkShouldBeNetworkRelated() { // GH-90000
            assertThat(ChaosType.NETWORK.isNetworkRelated()).isTrue(); // GH-90000
            assertThat(ChaosType.NETWORK.isDataRelated()).isFalse(); // GH-90000
            assertThat(ChaosType.NETWORK.isResourceRelated()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DATA_CORRUPTION should be data-related")
        void dataCorruptionShouldBeDataRelated() { // GH-90000
            assertThat(ChaosType.DATA_CORRUPTION.isDataRelated()).isTrue(); // GH-90000
            assertThat(ChaosType.DATA_CORRUPTION.isNetworkRelated()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("RESOURCE_EXHAUSTION should be resource-related")
        void resourceExhaustionShouldBeResourceRelated() { // GH-90000
            assertThat(ChaosType.RESOURCE_EXHAUSTION.isResourceRelated()).isTrue(); // GH-90000
            assertThat(ChaosType.RESOURCE_EXHAUSTION.isNetworkRelated()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("all types should have descriptions")
        void allTypesShouldHaveDescriptions() { // GH-90000
            for (ChaosType type : ChaosType.values()) { // GH-90000
                assertThat(type.getDescription()).isNotBlank(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("ChaosContext Tests")
    class ChaosContextTests {

        @Test
        @DisplayName("should initialize with correct values")
        void shouldInitializeWithCorrectValues() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 0.5, 5000); // GH-90000

            assertThat(context.getChaosType()).isEqualTo(ChaosType.NETWORK); // GH-90000
            assertThat(context.getFailureProbability()).isEqualTo(0.5); // GH-90000
            assertThat(context.getMaxDurationMs()).isEqualTo(5000); // GH-90000
            assertThat(context.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should clamp probability to valid range")
        void shouldClampProbability() { // GH-90000
            ChaosContext tooHigh = new ChaosContext(ChaosType.RANDOM, 1.5, 1000); // GH-90000
            ChaosContext tooLow = new ChaosContext(ChaosType.RANDOM, -0.5, 1000); // GH-90000

            assertThat(tooHigh.getFailureProbability()).isEqualTo(1.0); // GH-90000
            assertThat(tooLow.getFailureProbability()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("should track injection and failure counts")
        void shouldTrackCounts() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 1.0, 5000); // GH-90000

            context.recordInjection(); // GH-90000
            context.recordInjection(); // GH-90000
            context.recordFailure(); // GH-90000

            assertThat(context.getInjectionCount()).isEqualTo(2); // GH-90000
            assertThat(context.getFailureCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("shouldInjectFailure respects probability 0")
        void shouldNotInjectWhenProbabilityZero() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 0.0, 5000); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                assertThat(context.shouldInjectFailure()).isFalse(); // GH-90000
            }
        }

        @Test
        @DisplayName("shouldInjectFailure respects probability 1")
        void shouldAlwaysInjectWhenProbabilityOne() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 1.0, 5000); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                assertThat(context.shouldInjectFailure()).isTrue(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("ChaosInjector Tests")
    class ChaosInjectorTests {

        @Test
        @DisplayName("should be inactive by default")
        void shouldBeInactiveByDefault() { // GH-90000
            assertThat(ChaosInjector.isActive()).isFalse(); // GH-90000
            assertThat(ChaosInjector.getContext()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should activate and deactivate")
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
        @DisplayName("maybeCorruptData returns original when inactive")
        void maybeCorruptDataReturnsOriginalWhenInactive() { // GH-90000
            String original = "test data";
            String result = ChaosInjector.maybeCorruptData(original); // GH-90000
            assertThat(result).isEqualTo(original); // GH-90000
        }

        @Test
        @DisplayName("maybeCorruptData can corrupt data when active")
        void maybeCorruptDataCanCorruptWhenActive() { // GH-90000
            ChaosContext context = new ChaosContext(ChaosType.DATA_CORRUPTION, 1.0, 5000); // GH-90000
            ChaosInjector.activate(context); // GH-90000

            try {
                String result = ChaosInjector.maybeCorruptData("test");
                assertThat(result).isNull(); // Corruption returns null // GH-90000
            } finally {
                ChaosInjector.deactivate(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("ChaosScenario Tests")
    class ChaosScenarioTests {

        @Test
        @DisplayName("should execute operations successfully without chaos")
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
        @DisplayName("should track failures when operation throws")
        void shouldTrackFailures() { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withIterations(10) // GH-90000
                    .execute(() -> { // GH-90000
                        if (counter.incrementAndGet() % 2 == 0) { // GH-90000
                            throw new RuntimeException("Simulated failure");
                        }
                    });

            assertThat(result.getSuccessCount()).isEqualTo(5); // GH-90000
            assertThat(result.getFailureCount()).isEqualTo(5); // GH-90000
            assertThat(result.getSuccessRate()).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("assertAllSucceeded throws when failures exist")
        void assertAllSucceededThrowsOnFailures() { // GH-90000
            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withIterations(1) // GH-90000
                    .execute(() -> { // GH-90000
                        throw new RuntimeException("fail");
                    });

            assertThatThrownBy(result::assertAllSucceeded) // GH-90000
                    .isInstanceOf(AssertionError.class) // GH-90000
                    .hasMessageContaining("1 failed");
        }

        @Test
        @DisplayName("assertSuccessRate validates minimum rate")
        void assertSuccessRateValidatesMinRate() { // GH-90000
            AtomicInteger counter = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withIterations(10) // GH-90000
                    .execute(() -> { // GH-90000
                        if (counter.incrementAndGet() <= 8) { // GH-90000
                            // 8 successes
                        } else {
                            throw new RuntimeException("fail");
                        }
                    });

            // 80% success rate
            result.assertSuccessRate(0.8); // GH-90000

            assertThatThrownBy(() -> result.assertSuccessRate(0.9)) // GH-90000
                    .isInstanceOf(AssertionError.class); // GH-90000
        }

        @Test
        @DisplayName("should calculate average duration")
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
        @DisplayName("should support concurrent execution")
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
