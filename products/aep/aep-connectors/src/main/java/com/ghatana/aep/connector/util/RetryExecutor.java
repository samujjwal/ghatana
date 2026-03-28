package com.ghatana.aep.connector.util;

import com.ghatana.aep.connector.config.RetryConfig;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Shared retry executor for connector operations.
 *
 * @doc.type class
 * @doc.purpose Centralize connector retry execution, backoff, and retryable-result handling
 * @doc.layer infrastructure
 * @doc.pattern Utility
 */
public final class RetryExecutor {

    private RetryExecutor() {
    }

    public static <T> T execute(RetryConfig retryConfig,
                                Logger logger,
                                String operationName,
                                ThrowingSupplier<T> operation,
                                Predicate<T> successPredicate) throws Exception {
        Objects.requireNonNull(retryConfig, "retryConfig required");
        Objects.requireNonNull(logger, "logger required");
        Objects.requireNonNull(operationName, "operationName required");
        Objects.requireNonNull(operation, "operation required");
        Objects.requireNonNull(successPredicate, "successPredicate required");

        int attempts = 0;
        long delayMs = retryConfig.initialDelay().toMillis();
        T lastResult = null;

        while (attempts < retryConfig.maxAttempts()) {
            try {
                T result = operation.get();
                if (successPredicate.test(result)) {
                    return result;
                }

                lastResult = result;
                attempts++;
                if (attempts < retryConfig.maxAttempts()) {
                    logger.warn("Attempt {}/{} returned a retryable result for operation '{}'. Retrying in {}ms.",
                        attempts, retryConfig.maxAttempts(), operationName, delayMs);
                    sleep(delayMs);
                    delayMs = nextDelay(retryConfig, delayMs);
                }
            } catch (Exception e) {
                attempts++;
                if (attempts >= retryConfig.maxAttempts()) {
                    throw e;
                }
                logger.warn("Attempt {}/{} failed for operation '{}': {}. Retrying in {}ms.",
                    attempts, retryConfig.maxAttempts(), operationName, e.getMessage(), delayMs);
                sleep(delayMs);
                delayMs = nextDelay(retryConfig, delayMs);
            }
        }

        return lastResult;
    }

    private static long nextDelay(RetryConfig retryConfig, long currentMs) {
        return Math.min((long) (currentMs * retryConfig.backoffMultiplier()), retryConfig.maxDelay().toMillis());
    }

    private static void sleep(long ms) throws InterruptedException {
        if (ms <= 0) {
            return;
        }
        Thread.sleep(ms);
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}