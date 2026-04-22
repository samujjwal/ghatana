package com.ghatana.platform.messaging.util;

import com.ghatana.platform.messaging.config.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RetryExecutor [GH-90000]")
class RetryExecutorTest {

    private static final RetryConfig FAST_RETRY = RetryConfig.builder() // GH-90000
        .maxAttempts(3) // GH-90000
        .initialDelay(Duration.ZERO) // GH-90000
        .maxDelay(Duration.ZERO) // GH-90000
        .backoffMultiplier(2.0) // GH-90000
        .build(); // GH-90000

    @Test
    @DisplayName("retries retryable false results until success [GH-90000]")
    void retriesRetryableResultsUntilSuccess() throws Exception { // GH-90000
        AtomicInteger attempts = new AtomicInteger(); // GH-90000

        boolean result = RetryExecutor.execute( // GH-90000
            FAST_RETRY,
            LoggerFactory.getLogger(RetryExecutorTest.class), // GH-90000
            "soft-failure",
            () -> attempts.incrementAndGet() >= 3, // GH-90000
            Boolean.TRUE::equals
        );

        assertThat(result).isTrue(); // GH-90000
        assertThat(attempts.get()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("rethrows the terminal exception after max attempts [GH-90000]")
    void rethrowsTerminalExceptionAfterMaxAttempts() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(); // GH-90000

        assertThatThrownBy(() -> RetryExecutor.execute( // GH-90000
            FAST_RETRY,
            LoggerFactory.getLogger(RetryExecutorTest.class), // GH-90000
            "hard-failure",
            () -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                throw new IllegalStateException("boom [GH-90000]");
            },
            ignored -> true
        )).isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("boom [GH-90000]");

        assertThat(attempts.get()).isEqualTo(3); // GH-90000
    }
}
