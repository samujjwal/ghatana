/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * (MIT-licensed Redis alternative), configure RedisStateAdapter with Garnet // GH-90000
 * connection parameters.
 */
@DisplayName("DistributedRateLimiter Tests [GH-90000]")
class DistributedRateLimiterTest {

    private RedisStateAdapter mockRedisAdapter;
    private DistributedRateLimiter rateLimiter;

    @BeforeEach
    void setUp() { // GH-90000
        mockRedisAdapter = mock(RedisStateAdapter.class); // GH-90000
        rateLimiter = new DistributedRateLimiter(mockRedisAdapter); // GH-90000
    }

    @Test
    @DisplayName("Should allow request when under rate limit [GH-90000]")
    void shouldAllowRequestWhenUnderRateLimit() { // GH-90000
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(1L)); // GH-90000
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true)); // GH-90000
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(true)); // GH-90000

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000); // GH-90000

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult(); // GH-90000
        assertThat(rateLimitResult.isAllowed()).isTrue(); // GH-90000
        assertThat(rateLimitResult.getReason()).isNull(); // GH-90000
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should deny request when over rate limit [GH-90000]")
    void shouldDenyRequestWhenOverRateLimit() { // GH-90000
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(1001L)); // GH-90000
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true)); // GH-90000
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(true)); // GH-90000

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000); // GH-90000

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult(); // GH-90000
        assertThat(rateLimitResult.isAllowed()).isFalse(); // GH-90000
        assertThat(rateLimitResult.getReason()).contains("Rate limit exceeded [GH-90000]");
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(1001); // GH-90000
        assertThat(rateLimitResult.getLimit()).isEqualTo(1000); // GH-90000
    }

    @Test
    @DisplayName("Should allow events when under rate limit [GH-90000]")
    void shouldAllowEventsWhenUnderRateLimit() { // GH-90000
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(50L)); // GH-90000
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true)); // GH-90000
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(true)); // GH-90000

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkEventRateLimit("tenant-1", 10, 100); // GH-90000

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult(); // GH-90000
        assertThat(rateLimitResult.isAllowed()).isTrue(); // GH-90000
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(50); // GH-90000
    }

    @Test
    @DisplayName("Should deny events when over rate limit [GH-90000]")
    void shouldDenyEventsWhenOverRateLimit() { // GH-90000
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(101L)); // GH-90000
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true)); // GH-90000
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(true)); // GH-90000

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkEventRateLimit("tenant-1", 10, 100); // GH-90000

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult(); // GH-90000
        assertThat(rateLimitResult.isAllowed()).isFalse(); // GH-90000
        assertThat(rateLimitResult.getReason()).contains("Event rate limit exceeded [GH-90000]");
        assertThat(rateLimitResult.getCurrentUsage()).isEqualTo(101); // GH-90000
    }

    @Test
    @DisplayName("Should fallback to local rate limiting when Redis fails [GH-90000]")
    @org.junit.jupiter.api.Disabled("TODO: Fix ActiveJ Promise event loop context issue [GH-90000]")
    void shouldFallbackToLocalRateLimitingWhenRedisFails() { // GH-90000
        when(mockRedisAdapter.increment(anyString(), anyLong())) // GH-90000
            .thenReturn(Promise.ofException(new RuntimeException("Redis connection failed [GH-90000]")));
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(false)); // GH-90000
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true)); // GH-90000

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000); // GH-90000

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult(); // GH-90000
        assertThat(rateLimitResult).isNotNull(); // GH-90000
        assertThat(rateLimitResult.isAllowed()).isTrue(); // GH-90000
        assertThat(rateLimitResult.getReason()).contains("local fallback [GH-90000]");
    }

    @Test
    @DisplayName("Should reset rate limit for tenant [GH-90000]")
    void shouldResetRateLimitForTenant() { // GH-90000
        when(mockRedisAdapter.scanKeys(anyString())).thenReturn(Promise.of(java.util.Map.of())); // GH-90000
        when(mockRedisAdapter.deleteAll(any())).thenReturn(Promise.of(null)); // GH-90000

        Promise<Void> result = rateLimiter.resetRateLimit("tenant-1 [GH-90000]");
        result.getResult(); // GH-90000

        verify(mockRedisAdapter).scanKeys("ratelimit:request:tenant-1:* [GH-90000]");
        verify(mockRedisAdapter).scanKeys("ratelimit:event:tenant-1:* [GH-90000]");
    }

    @Test
    @DisplayName("Should get rate limit usage for tenant [GH-90000]")
    void shouldGetRateLimitUsageForTenant() { // GH-90000
        when(mockRedisAdapter.scanKeys(anyString())) // GH-90000
            .thenReturn(Promise.of(java.util.Map.of("key1", "100", "key2", "50"))); // GH-90000

        Promise<DistributedRateLimiter.RateLimitUsage> result = 
            rateLimiter.getRateLimitUsage("tenant-1 [GH-90000]");

        DistributedRateLimiter.RateLimitUsage usage = result.getResult(); // GH-90000
        assertThat(usage.getTotalRequests()).isEqualTo(150); // GH-90000
    }

    @Test
    @DisplayName("Should check health and reset fallback when Redis is healthy [GH-90000]")
    @org.junit.jupiter.api.Disabled("TODO: Fix ActiveJ Promise event loop context issue [GH-90000]")
    void shouldCheckHealthAndResetFallbackWhenRedisIsHealthy() { // GH-90000
        when(mockRedisAdapter.isHealthy()).thenReturn(Promise.of(true)); // GH-90000
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(1L)); // GH-90000
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true)); // GH-90000

        Promise<Void> result = rateLimiter.checkHealth(); // GH-90000
        result.getResult(); // GH-90000

        // Force fallback state first
        rateLimiter.checkRequestRateLimit("tenant-1", 1000) // GH-90000
            .then(r -> { // GH-90000
                // Check health again
                rateLimiter.checkHealth().getResult(); // GH-90000
                return Promise.of(null); // GH-90000
            }).getResult(); // GH-90000

        verify(mockRedisAdapter).isHealthy(); // GH-90000
    }

    @Test
    @DisplayName("Should calculate usage percentage correctly [GH-90000]")
    void shouldCalculateUsagePercentageCorrectly() { // GH-90000
        when(mockRedisAdapter.increment(anyString(), anyLong())).thenReturn(Promise.of(500L)); // GH-90000
        when(mockRedisAdapter.expire(anyString(), anyLong())).thenReturn(Promise.of(true)); // GH-90000

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            rateLimiter.checkRequestRateLimit("tenant-1", 1000); // GH-90000

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult(); // GH-90000
        assertThat(rateLimitResult.getUsagePercentage()).isEqualTo(50.0); // GH-90000
    }

    @Test
    @DisplayName("Should handle null Redis adapter gracefully [GH-90000]")
    @org.junit.jupiter.api.Disabled("TODO: Fix ActiveJ Promise event loop context issue [GH-90000]")
    void shouldHandleNullRedisAdapterGracefully() { // GH-90000
        DistributedRateLimiter nullAdapterLimiter = new DistributedRateLimiter(null); // GH-90000

        Promise<DistributedRateLimiter.RateLimitResult> result = 
            nullAdapterLimiter.checkRequestRateLimit("tenant-1", 1000); // GH-90000

        DistributedRateLimiter.RateLimitResult rateLimitResult = result.getResult(); // GH-90000
        assertThat(rateLimitResult).isNotNull(); // GH-90000
        assertThat(rateLimitResult.isAllowed()).isTrue(); // GH-90000
        assertThat(rateLimitResult.getReason()).contains("local fallback [GH-90000]");
    }
}
