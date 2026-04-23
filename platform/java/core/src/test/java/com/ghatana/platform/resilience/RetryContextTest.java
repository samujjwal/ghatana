package com.ghatana.platform.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for RetryContext value-object construction and accessors
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RetryContext — value-object construction and retry metadata")
class RetryContextTest {

    @Test
    @DisplayName("first() creates initial context with attempt 1 and no error")
    void firstCreatesInitialContext() { // GH-90000
        RetryContext ctx = RetryContext.first(4); // GH-90000

        assertThat(ctx.getAttemptNumber()).isEqualTo(1); // GH-90000
        assertThat(ctx.getMaxAttempts()).isEqualTo(4); // GH-90000
        assertThat(ctx.isRetry()).isFalse(); // GH-90000
        assertThat(ctx.getLastError()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("first() with maxAttempts=1 is the last and only attempt")
    void firstWithOneMaxAttemptIsLastAttempt() { // GH-90000
        RetryContext ctx = RetryContext.first(1); // GH-90000

        assertThat(ctx.isLastAttempt()).isTrue(); // GH-90000
        assertThat(ctx.attemptsRemaining()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("retry() creates context with isRetry=true and lastError set")
    void retryCreatesContextWithError() { // GH-90000
        Throwable error = new RuntimeException("network failure");
        RetryContext ctx = RetryContext.retry(2, 4, error); // GH-90000

        assertThat(ctx.getAttemptNumber()).isEqualTo(2); // GH-90000
        assertThat(ctx.getMaxAttempts()).isEqualTo(4); // GH-90000
        assertThat(ctx.isRetry()).isTrue(); // GH-90000
        assertThat(ctx.getLastError()).isSameAs(error); // GH-90000
    }

    @Test
    @DisplayName("attemptsRemaining returns correct remaining count")
    void attemptsRemainingIsCorrect() { // GH-90000
        RetryContext ctx = RetryContext.retry(3, 5, new RuntimeException()); // GH-90000

        assertThat(ctx.attemptsRemaining()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("isLastAttempt returns true when attemptNumber == maxAttempts")
    void isLastAttemptWhenAttemptEqualsMax() { // GH-90000
        RetryContext ctx = RetryContext.retry(4, 4, new RuntimeException()); // GH-90000

        assertThat(ctx.isLastAttempt()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isLastAttempt returns false before last attempt")
    void isNotLastAttemptBeforeMax() { // GH-90000
        RetryContext ctx = RetryContext.retry(3, 4, new RuntimeException()); // GH-90000

        assertThat(ctx.isLastAttempt()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("first() throws for attemptNumber validation (via constructor)")
    void firstInvalidMaxAttempts() { // GH-90000
        assertThatThrownBy(() -> RetryContext.first(0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("maxAttempts");
    }

    @Test
    @DisplayName("retry() throws when lastError is null")
    void retryThrowsForNullLastError() { // GH-90000
        assertThatThrownBy(() -> RetryContext.retry(2, 4, null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("lastError");
    }

    @Test
    @DisplayName("retry() throws for invalid attemptNumber")
    void retryThrowsForAttemptNumberLessThanOne() { // GH-90000
        assertThatThrownBy(() -> RetryContext.retry(0, 4, new RuntimeException())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("attemptNumber");
    }

    @Test
    @DisplayName("toString includes attempt and max attempt info")
    void toStringContainsAttemptInfo() { // GH-90000
        RetryContext ctx = RetryContext.retry(2, 5, new RuntimeException("err"));
        String str = ctx.toString(); // GH-90000

        assertThat(str).contains("2");
        assertThat(str).contains("5");
        assertThat(str).contains("isRetry=true");
    }
}
