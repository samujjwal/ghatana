package com.ghatana.aep.connector.util;

import com.ghatana.aep.connector.config.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RetryExecutor")
class RetryExecutorTest {

    private static final RetryConfig FAST_RETRY = RetryConfig.builder()
        .maxAttempts(3)
        .initialDelay(Duration.ZERO)
        .maxDelay(Duration.ZERO)
        .backoffMultiplier(2.0)
        .build();

    @Test
    @DisplayName("retries retryable false results until success")
    void retriesRetryableResultsUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryExecutor.execute(
            FAST_RETRY,
            LoggerFactory.getLogger(RetryExecutorTest.class),
            "soft-failure",
            () -> attempts.incrementAndGet() >= 3,
            Boolean.TRUE::equals
        );

        assertThat(result).isTrue();
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("rethrows the terminal exception after max attempts")
    void rethrowsTerminalExceptionAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> RetryExecutor.execute(
            FAST_RETRY,
            LoggerFactory.getLogger(RetryExecutorTest.class),
            "hard-failure",
            () -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("boom");
            },
            ignored -> true
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("boom");

        assertThat(attempts.get()).isEqualTo(3);
    }
}