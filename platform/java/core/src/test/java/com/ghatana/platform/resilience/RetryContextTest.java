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
    void firstCreatesInitialContext() {
        RetryContext ctx = RetryContext.first(4);

        assertThat(ctx.getAttemptNumber()).isEqualTo(1);
        assertThat(ctx.getMaxAttempts()).isEqualTo(4);
        assertThat(ctx.isRetry()).isFalse();
        assertThat(ctx.getLastError()).isNull();
    }

    @Test
    @DisplayName("first() with maxAttempts=1 is the last and only attempt")
    void firstWithOneMaxAttemptIsLastAttempt() {
        RetryContext ctx = RetryContext.first(1);

        assertThat(ctx.isLastAttempt()).isTrue();
        assertThat(ctx.attemptsRemaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("retry() creates context with isRetry=true and lastError set")
    void retryCreatesContextWithError() {
        Throwable error = new RuntimeException("network failure");
        RetryContext ctx = RetryContext.retry(2, 4, error);

        assertThat(ctx.getAttemptNumber()).isEqualTo(2);
        assertThat(ctx.getMaxAttempts()).isEqualTo(4);
        assertThat(ctx.isRetry()).isTrue();
        assertThat(ctx.getLastError()).isSameAs(error);
    }

    @Test
    @DisplayName("attemptsRemaining returns correct remaining count")
    void attemptsRemainingIsCorrect() {
        RetryContext ctx = RetryContext.retry(3, 5, new RuntimeException());

        assertThat(ctx.attemptsRemaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("isLastAttempt returns true when attemptNumber == maxAttempts")
    void isLastAttemptWhenAttemptEqualsMax() {
        RetryContext ctx = RetryContext.retry(4, 4, new RuntimeException());

        assertThat(ctx.isLastAttempt()).isTrue();
    }

    @Test
    @DisplayName("isLastAttempt returns false before last attempt")
    void isNotLastAttemptBeforeMax() {
        RetryContext ctx = RetryContext.retry(3, 4, new RuntimeException());

        assertThat(ctx.isLastAttempt()).isFalse();
    }

    @Test
    @DisplayName("first() throws for attemptNumber validation (via constructor)")
    void firstInvalidMaxAttempts() {
        assertThatThrownBy(() -> RetryContext.first(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    @Test
    @DisplayName("retry() throws when lastError is null")
    void retryThrowsForNullLastError() {
        assertThatThrownBy(() -> RetryContext.retry(2, 4, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("lastError");
    }

    @Test
    @DisplayName("retry() throws for invalid attemptNumber")
    void retryThrowsForAttemptNumberLessThanOne() {
        assertThatThrownBy(() -> RetryContext.retry(0, 4, new RuntimeException()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptNumber");
    }

    @Test
    @DisplayName("toString includes attempt and max attempt info")
    void toStringContainsAttemptInfo() {
        RetryContext ctx = RetryContext.retry(2, 5, new RuntimeException("err"));
        String str = ctx.toString();

        assertThat(str).contains("2");
        assertThat(str).contains("5");
        assertThat(str).contains("isRetry=true");
    }
}
