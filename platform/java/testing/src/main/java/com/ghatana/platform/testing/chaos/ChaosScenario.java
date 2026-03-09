package com.ghatana.platform.testing.chaos;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Fluent builder for constructing chaos test scenarios.
 *
 * <p>Example usage:
 * <pre>{@code
 * ChaosScenario.builder()
 *     .withChaosType(ChaosType.NETWORK)
 *     .withFailureProbability(0.3)
 *     .withDuration(Duration.ofSeconds(10))
 *     .withConcurrency(5)
 *     .execute(() -> myService.call())
 *     .assertAllSucceeded();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fluent API for building complex chaos test scenarios
 * @doc.layer core
 * @doc.pattern Builder
 */
public class ChaosScenario {

    private final ChaosType chaosType;
    private final double failureProbability;
    private final Duration duration;
    private final int concurrency;
    private final int iterations;

    private ChaosScenario(Builder builder) {
        this.chaosType = builder.chaosType;
        this.failureProbability = builder.failureProbability;
        this.duration = builder.duration;
        this.concurrency = builder.concurrency;
        this.iterations = builder.iterations;
    }

    /**
     * Creates a new builder for ChaosScenario.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes the given operation under chaos conditions.
     *
     * @param operation the operation to execute
     * @param <T>       the return type
     * @return the execution result
     */
    public <T> ChaosExecutionResult<T> execute(Callable<T> operation) {
        ChaosContext context = new ChaosContext(chaosType, failureProbability, duration.toMillis());
        ChaosInjector.activate(context);

        List<ExecutionOutcome<T>> outcomes = new ArrayList<>();

        try {
            if (concurrency > 1) {
                outcomes = executeConcurrently(operation);
            } else {
                outcomes = executeSequentially(operation);
            }
        } finally {
            ChaosInjector.deactivate();
        }

        return new ChaosExecutionResult<>(outcomes, context);
    }

    /**
     * Executes the given runnable under chaos conditions.
     *
     * @param operation the operation to execute
     * @return the execution result
     */
    public ChaosExecutionResult<Void> execute(Runnable operation) {
        return execute(() -> {
            operation.run();
            return null;
        });
    }

    private <T> List<ExecutionOutcome<T>> executeSequentially(Callable<T> operation) {
        List<ExecutionOutcome<T>> outcomes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            outcomes.add(executeOnce(operation));
        }
        return outcomes;
    }

    private <T> List<ExecutionOutcome<T>> executeConcurrently(Callable<T> operation) {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<ExecutionOutcome<T>>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < iterations; i++) {
                futures.add(executor.submit(() -> executeOnce(operation)));
            }

            List<ExecutionOutcome<T>> outcomes = new ArrayList<>();
            for (Future<ExecutionOutcome<T>> future : futures) {
                try {
                    outcomes.add(future.get(duration.toMillis() * 2, TimeUnit.MILLISECONDS));
                } catch (Exception e) {
                    outcomes.add(new ExecutionOutcome<>(null, e, 0));
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> ExecutionOutcome<T> executeOnce(Callable<T> operation) {
        long startTime = System.nanoTime();
        try {
            T result = operation.call();
            long durationNs = System.nanoTime() - startTime;
            return new ExecutionOutcome<>(result, null, durationNs);
        } catch (Exception e) {
            long durationNs = System.nanoTime() - startTime;
            return new ExecutionOutcome<>(null, e, durationNs);
        }
    }

    /**
     * Builder for ChaosScenario.
     */
    public static class Builder {
        private ChaosType chaosType = ChaosType.RANDOM;
        private double failureProbability = 0.5;
        private Duration duration = Duration.ofSeconds(5);
        private int concurrency = 1;
        private int iterations = 10;

        /**
         * Sets the type of chaos to inject.
         */
        public Builder withChaosType(ChaosType chaosType) {
            this.chaosType = chaosType;
            return this;
        }

        /**
         * Sets the probability of failure (0.0 to 1.0).
         */
        public Builder withFailureProbability(double probability) {
            this.failureProbability = probability;
            return this;
        }

        /**
         * Sets the maximum duration for chaos injection.
         */
        public Builder withDuration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Sets the number of concurrent executions.
         */
        public Builder withConcurrency(int concurrency) {
            this.concurrency = Math.max(1, concurrency);
            return this;
        }

        /**
         * Sets the number of iterations to execute.
         */
        public Builder withIterations(int iterations) {
            this.iterations = Math.max(1, iterations);
            return this;
        }

        /**
         * Builds the ChaosScenario.
         */
        public ChaosScenario build() {
            return new ChaosScenario(this);
        }

        /**
         * Builds and executes the scenario with the given operation.
         */
        public <T> ChaosExecutionResult<T> execute(Callable<T> operation) {
            return build().execute(operation);
        }

        /**
         * Builds and executes the scenario with the given runnable.
         */
        public ChaosExecutionResult<Void> execute(Runnable operation) {
            return build().execute(operation);
        }
    }

    /**
     * Represents the outcome of a single execution.
     */
    public static class ExecutionOutcome<T> {
        private final T result;
        private final Throwable error;
        private final long durationNanos;

        public ExecutionOutcome(T result, Throwable error, long durationNanos) {
            this.result = result;
            this.error = error;
            this.durationNanos = durationNanos;
        }

        public T getResult() {
            return result;
        }

        public Throwable getError() {
            return error;
        }

        public long getDurationNanos() {
            return durationNanos;
        }

        public boolean isSuccess() {
            return error == null;
        }

        public boolean isFailure() {
            return error != null;
        }

        public Duration getDuration() {
            return Duration.ofNanos(durationNanos);
        }
    }

    /**
     * Aggregated result of chaos test execution.
     */
    public static class ChaosExecutionResult<T> {
        private final List<ExecutionOutcome<T>> outcomes;
        private final ChaosContext context;

        public ChaosExecutionResult(List<ExecutionOutcome<T>> outcomes, ChaosContext context) {
            this.outcomes = outcomes;
            this.context = context;
        }

        public List<ExecutionOutcome<T>> getOutcomes() {
            return outcomes;
        }

        public ChaosContext getContext() {
            return context;
        }

        public long getSuccessCount() {
            return outcomes.stream().filter(ExecutionOutcome::isSuccess).count();
        }

        public long getFailureCount() {
            return outcomes.stream().filter(ExecutionOutcome::isFailure).count();
        }

        public double getSuccessRate() {
            if (outcomes.isEmpty()) return 0.0;
            return (double) getSuccessCount() / outcomes.size();
        }

        public Duration getAverageDuration() {
            if (outcomes.isEmpty()) return Duration.ZERO;
            long totalNanos = outcomes.stream()
                    .mapToLong(ExecutionOutcome::getDurationNanos)
                    .sum();
            return Duration.ofNanos(totalNanos / outcomes.size());
        }

        /**
         * Asserts that all executions succeeded.
         */
        public ChaosExecutionResult<T> assertAllSucceeded() {
            long failures = getFailureCount();
            if (failures > 0) {
                throw new AssertionError("Expected all executions to succeed, but " + failures + " failed");
            }
            return this;
        }

        /**
         * Asserts that at least the given percentage of executions succeeded.
         */
        public ChaosExecutionResult<T> assertSuccessRate(double minRate) {
            double rate = getSuccessRate();
            if (rate < minRate) {
                throw new AssertionError(
                        String.format("Expected success rate >= %.2f, but was %.2f", minRate, rate));
            }
            return this;
        }

        /**
         * Asserts that the system handled failures gracefully.
         */
        public ChaosExecutionResult<T> assertGracefulDegradation() {
            // At least some operations should succeed even under chaos
            if (getSuccessCount() == 0 && !outcomes.isEmpty()) {
                throw new AssertionError("System did not degrade gracefully - all operations failed");
            }
            return this;
        }

        /**
         * Asserts that failures were handled with expected exception types.
         */
        public ChaosExecutionResult<T> assertFailuresAreExpected(Class<? extends Throwable>... expectedTypes) {
            for (ExecutionOutcome<T> outcome : outcomes) {
                if (outcome.isFailure()) {
                    boolean matched = false;
                    for (Class<? extends Throwable> expectedType : expectedTypes) {
                        if (expectedType.isInstance(outcome.getError())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        throw new AssertionError(
                                "Unexpected exception type: " + outcome.getError().getClass().getName(),
                                outcome.getError());
                    }
                }
            }
            return this;
        }
    }
}
