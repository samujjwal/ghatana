/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.datacloud.infrastructure.state.redis.RedisStateAdapter;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for DistributedRateLimiter
 * @doc.layer product
 * @doc.pattern UnitTest
 *
 * <p>Tests use mocked RedisStateAdapter. For integration testing with Garnet
 * (MIT-licensed Redis alternative), configure RedisStateAdapter with Garnet
 * connection parameters.
 */
@DisplayName("DistributedRateLimiter Tests")
class DistributedRateLimiterTest {

    private RedisStateAdapter mockRedisAdapter;
    private DistributedRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        mockRedisAdapter = mock(RedisStateAdapter.class);
        rateLimiter = new DistributedRateLimiter(mockRedisAdapter);
    }

    @Test
    @DisplayName("Should allow request when under rate limit")
    void shouldAllowRequestWhenUnderRateLimit() {
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(1L));
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true));
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(true));

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000);

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult();
        assertThat(rateLimitResult.isAllowed()).isTrue();
        assertThat(rateLimitResult.getReason()).isNull();
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should deny request when over rate limit")
    void shouldDenyRequestWhenOverRateLimit() {
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(1001L));
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true));

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000);

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult();
        assertThat(rateLimitResult.isAllowed()).isFalse();
        assertThat(rateLimitResult.getReason()).contains("Rate limit exceeded");
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(1001);
        assertThat(rateLimitResult.getLimit()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should allow events when under rate limit")
    void shouldAllowEventsWhenUnderRateLimit() {
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(50L));
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true));

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkEventRateLimit("tenant-1", 10, 100);

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult();
        assertThat(rateLimitResult.isAllowed()).isTrue();
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should deny events when over rate limit")
    void shouldDenyEventsWhenOverRateLimit() {
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(101L));
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true));

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkEventRateLimit("tenant-1", 10, 100);

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult();
        assertThat(rateLimitResult.isAllowed()).isFalse();
        assertThat(rateLimitResult.getReason()).contains("Event rate limit exceeded");
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(101);
    }

    @Test
    @DisplayName("Should fallback to local rate limiting when Redis fails")
    void shouldFallbackToLocalRateLimitingWhenRedisFails() {
        when(mockRedisAdapter.increment(anyString(), anyLong()))
            .thenReturn(Promise.ofException(new RuntimeException("Redis connection failed")));
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(false));

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000);

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult();
        assertThat(rateLimitResult.isAllowed()).isTrue();
        assertThat(rateLimitResult.getReason()).contains("local fallback");
    }

    @Test
    @DisplayName("Should reset rate limit for tenant")
    void shouldResetRateLimitForTenant() {
        when(mockRedisAdapter.scanKeys(anyString())).thenReturn(Promise.of(java.util.Map.of()));
        when(mockRedisAdapter.deleteAll(any())).thenReturn(Promise.of(null));

        Promise<Void> result = rateLimiter.resetRateLimit("tenant-1");
        result.getResult();

        verify(mockRedisAdapter).scanKeys("ratelimit:request:tenant-1:*");
        verify(mockRedisAdapter).scanKeys("ratelimit:event:tenant-1:*");
    }

    @Test
    @DisplayName("Should get rate limit usage for tenant")
    void shouldGetRateLimitUsageForTenant() {
        when(mockRedisAdapter.scanKeys(anyString()))
            .thenReturn(Promise.of(java.util.Map.of("key1", "100", "key2", "50")));

        Promise<DistributedRateLimiter.RateLimitUsage> result = 
            rateLimiter.getRateLimitUsage("tenant-1");

        DistributedRateLimiter.RateLimitUsage usage = result.getResult();
        assertThat(usage.getTotalRequests()).isEqualTo(150);
    }

    @Test
    @DisplayName("Should check health and reset fallback when Redis is healthy")
    void shouldCheckHealthAndResetFallbackWhenRedisIsHealthy() {
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(true));

        Promise<Void> result = rateLimiter.checkHealth();
        result.getResult();

        // Force fallback state first
        rateLimiter.checkRequestRateLimit("tenant-1", 1000)
            .then(r -> {
                // Check health again
                rateLimiter.checkHealth().getResult();
                return Promise.of(null);
            }).getResult();

        verify(mockRedisAdapter).isHealthy();
    }

    @Test
    @DisplayName("Should calculate usage percentage correctly")
    void shouldCalculateUsagePercentageCorrectly() {
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(500L));
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true));

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000);

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult();
        assertThat(rateLimitResult.getUsagePercentage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Should handle null Redis adapter gracefully")
    void shouldHandleNullRedisAdapterGracefully() {
        DistributedRateLimiter nullAdapterLimiter = new DistributedRateLimiter(null);

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            nullAdapterLimiter.checkRequestRateLimit("tenant-1", 1000);

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult();
        assertThat(rateLimitResult.isAllowed()).isTrue();
        assertThat(rateLimitResult.getReason()).contains("local fallback");
    }
}
