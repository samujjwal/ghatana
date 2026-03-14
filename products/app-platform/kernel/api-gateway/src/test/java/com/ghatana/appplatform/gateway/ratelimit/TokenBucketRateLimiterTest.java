package com.ghatana.appplatform.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenBucketRateLimiter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for token bucket rate limiter facade
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBucketRateLimiter — Unit Tests")
class TokenBucketRateLimiterTest {

    @Mock
    private RateLimitStore store;

    private TokenBucketRateLimiter limiter;

    private static final long CAPACITY = 100L;
    private static final double REFILL_RATE = 10.0;

    @BeforeEach
    void setUp() {
        limiter = new TokenBucketRateLimiter(store, CAPACITY, REFILL_RATE);
    }

    @Test
    @DisplayName("tryConsume_allowed — delegates to store and returns allowed result")
    void tryConsumeAllowed() {
        RateLimitStore.ConsumeResult expected = new RateLimitStore.ConsumeResult(true, 99L, 0L);
        when(store.tryConsume("tenant:t1", CAPACITY, REFILL_RATE, 1)).thenReturn(expected);

        RateLimitStore.ConsumeResult result = limiter.tryConsume("tenant:t1");

        assertThat(result.allowed()).isTrue();
        assertThat(result.tokensLeft()).isEqualTo(99L);
        assertThat(result.retryAfterMs()).isZero();
        verify(store).tryConsume("tenant:t1", CAPACITY, REFILL_RATE, 1);
    }

    @Test
    @DisplayName("tryConsume_denied — returns denied result with retry hint")
    void tryConsumeDenied() {
        RateLimitStore.ConsumeResult denied = new RateLimitStore.ConsumeResult(false, 0L, 500L);
        when(store.tryConsume("tenant:t2", CAPACITY, REFILL_RATE, 1)).thenReturn(denied);

        RateLimitStore.ConsumeResult result = limiter.tryConsume("tenant:t2");

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterMs()).isEqualTo(500L);
    }

    @Test
    @DisplayName("tryConsume_customParams — passes custom capacity and refill rate to store")
    void tryConsumeCustomParams() {
        RateLimitStore.ConsumeResult expected = new RateLimitStore.ConsumeResult(true, 48L, 0L);
        when(store.tryConsume("tenant:t3", 50L, 5.0, 2L)).thenReturn(expected);

        RateLimitStore.ConsumeResult result = limiter.tryConsume("tenant:t3", 50L, 5.0, 2L);

        assertThat(result.allowed()).isTrue();
        verify(store).tryConsume("tenant:t3", 50L, 5.0, 2L);
    }

    @Test
    @DisplayName("isAllowed_true — returns true when store allows")
    void isAllowedTrue() {
        when(store.tryConsume(eq("tenant:t4"), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(true, 100L, 0L));

        assertThat(limiter.isAllowed("tenant:t4")).isTrue();
    }

    @Test
    @DisplayName("isAllowed_false — returns false when store denies")
    void isAllowedFalse() {
        when(store.tryConsume(eq("tenant:t5"), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(false, 0L, 100L));

        assertThat(limiter.isAllowed("tenant:t5")).isFalse();
    }

    @Test
    @DisplayName("consumeResult_record — stores fields immutably")
    void consumeResultRecord() {
        RateLimitStore.ConsumeResult result = new RateLimitStore.ConsumeResult(true, 42L, 0L);

        assertThat(result.allowed()).isTrue();
        assertThat(result.tokensLeft()).isEqualTo(42L);
        assertThat(result.retryAfterMs()).isZero();
    }
}
