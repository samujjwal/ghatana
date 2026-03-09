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
        void shouldHaveAllExpectedTypes() {
            assertThat(ChaosType.values()).hasSize(10);
        }

        @Test
        @DisplayName("NETWORK should be network-related")
        void networkShouldBeNetworkRelated() {
            assertThat(ChaosType.NETWORK.isNetworkRelated()).isTrue();
            assertThat(ChaosType.NETWORK.isDataRelated()).isFalse();
            assertThat(ChaosType.NETWORK.isResourceRelated()).isFalse();
        }

        @Test
        @DisplayName("DATA_CORRUPTION should be data-related")
        void dataCorruptionShouldBeDataRelated() {
            assertThat(ChaosType.DATA_CORRUPTION.isDataRelated()).isTrue();
            assertThat(ChaosType.DATA_CORRUPTION.isNetworkRelated()).isFalse();
        }

        @Test
        @DisplayName("RESOURCE_EXHAUSTION should be resource-related")
        void resourceExhaustionShouldBeResourceRelated() {
            assertThat(ChaosType.RESOURCE_EXHAUSTION.isResourceRelated()).isTrue();
            assertThat(ChaosType.RESOURCE_EXHAUSTION.isNetworkRelated()).isFalse();
        }

        @Test
        @DisplayName("all types should have descriptions")
        void allTypesShouldHaveDescriptions() {
            for (ChaosType type : ChaosType.values()) {
                assertThat(type.getDescription()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("ChaosContext Tests")
    class ChaosContextTests {

        @Test
        @DisplayName("should initialize with correct values")
        void shouldInitializeWithCorrectValues() {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 0.5, 5000);

            assertThat(context.getChaosType()).isEqualTo(ChaosType.NETWORK);
            assertThat(context.getFailureProbability()).isEqualTo(0.5);
            assertThat(context.getMaxDurationMs()).isEqualTo(5000);
            assertThat(context.isActive()).isTrue();
        }

        @Test
        @DisplayName("should clamp probability to valid range")
        void shouldClampProbability() {
            ChaosContext tooHigh = new ChaosContext(ChaosType.RANDOM, 1.5, 1000);
            ChaosContext tooLow = new ChaosContext(ChaosType.RANDOM, -0.5, 1000);

            assertThat(tooHigh.getFailureProbability()).isEqualTo(1.0);
            assertThat(tooLow.getFailureProbability()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should track injection and failure counts")
        void shouldTrackCounts() {
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 1.0, 5000);

            context.recordInjection();
            context.recordInjection();
            context.recordFailure();

            assertThat(context.getInjectionCount()).isEqualTo(2);
            assertThat(context.getFailureCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("shouldInjectFailure respects probability 0")
        void shouldNotInjectWhenProbabilityZero() {
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 0.0, 5000);

            for (int i = 0; i < 100; i++) {
                assertThat(context.shouldInjectFailure()).isFalse();
            }
        }

        @Test
        @DisplayName("shouldInjectFailure respects probability 1")
        void shouldAlwaysInjectWhenProbabilityOne() {
            ChaosContext context = new ChaosContext(ChaosType.RANDOM, 1.0, 5000);

            for (int i = 0; i < 10; i++) {
                assertThat(context.shouldInjectFailure()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("ChaosInjector Tests")
    class ChaosInjectorTests {

        @Test
        @DisplayName("should be inactive by default")
        void shouldBeInactiveByDefault() {
            assertThat(ChaosInjector.isActive()).isFalse();
            assertThat(ChaosInjector.getContext()).isNull();
        }

        @Test
        @DisplayName("should activate and deactivate")
        void shouldActivateAndDeactivate() {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 0.5, 5000);

            ChaosInjector.activate(context);
            assertThat(ChaosInjector.isActive()).isTrue();
            assertThat(ChaosInjector.getContext()).isSameAs(context);

            ChaosInjector.deactivate();
            assertThat(ChaosInjector.isActive()).isFalse();
            assertThat(ChaosInjector.getContext()).isNull();
        }

        @Test
        @DisplayName("maybeCorruptData returns original when inactive")
        void maybeCorruptDataReturnsOriginalWhenInactive() {
            String original = "test data";
            String result = ChaosInjector.maybeCorruptData(original);
            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("maybeCorruptData can corrupt data when active")
        void maybeCorruptDataCanCorruptWhenActive() {
            ChaosContext context = new ChaosContext(ChaosType.DATA_CORRUPTION, 1.0, 5000);
            ChaosInjector.activate(context);

            try {
                String result = ChaosInjector.maybeCorruptData("test");
                assertThat(result).isNull(); // Corruption returns null
            } finally {
                ChaosInjector.deactivate();
            }
        }
    }

    @Nested
    @DisplayName("ChaosScenario Tests")
    class ChaosScenarioTests {

        @Test
        @DisplayName("should execute operations successfully without chaos")
        void shouldExecuteWithoutChaos() {
            AtomicInteger counter = new AtomicInteger(0);

            ChaosScenario.ChaosExecutionResult<Integer> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.RANDOM)
                    .withFailureProbability(0.0) // No chaos
                    .withIterations(10)
                    .execute(counter::incrementAndGet);

            assertThat(result.getSuccessCount()).isEqualTo(10);
            assertThat(result.getFailureCount()).isZero();
            assertThat(result.getSuccessRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should track failures when operation throws")
        void shouldTrackFailures() {
            AtomicInteger counter = new AtomicInteger(0);

            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder()
                    .withFailureProbability(0.0)
                    .withIterations(10)
                    .execute(() -> {
                        if (counter.incrementAndGet() % 2 == 0) {
                            throw new RuntimeException("Simulated failure");
                        }
                    });

            assertThat(result.getSuccessCount()).isEqualTo(5);
            assertThat(result.getFailureCount()).isEqualTo(5);
            assertThat(result.getSuccessRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("assertAllSucceeded throws when failures exist")
        void assertAllSucceededThrowsOnFailures() {
            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder()
                    .withFailureProbability(0.0)
                    .withIterations(1)
                    .execute(() -> {
                        throw new RuntimeException("fail");
                    });

            assertThatThrownBy(result::assertAllSucceeded)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("1 failed");
        }

        @Test
        @DisplayName("assertSuccessRate validates minimum rate")
        void assertSuccessRateValidatesMinRate() {
            AtomicInteger counter = new AtomicInteger(0);

            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder()
                    .withFailureProbability(0.0)
                    .withIterations(10)
                    .execute(() -> {
                        if (counter.incrementAndGet() <= 8) {
                            // 8 successes
                        } else {
                            throw new RuntimeException("fail");
                        }
                    });

            // 80% success rate
            result.assertSuccessRate(0.8);

            assertThatThrownBy(() -> result.assertSuccessRate(0.9))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("should calculate average duration")
        void shouldCalculateAverageDuration() {
            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder()
                    .withFailureProbability(0.0)
                    .withIterations(5)
                    .execute(() -> {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });

            Duration avgDuration = result.getAverageDuration();
            assertThat(avgDuration.toMillis()).isGreaterThanOrEqualTo(10);
        }

        @Test
        @DisplayName("should support concurrent execution")
        void shouldSupportConcurrentExecution() {
            AtomicInteger counter = new AtomicInteger(0);

            ChaosScenario.ChaosExecutionResult<Integer> result = ChaosScenario.builder()
                    .withFailureProbability(0.0)
                    .withConcurrency(4)
                    .withIterations(20)
                    .withDuration(Duration.ofSeconds(5))
                    .execute(counter::incrementAndGet);

            assertThat(result.getSuccessCount()).isEqualTo(20);
            assertThat(counter.get()).isEqualTo(20);
        }
    }
}
