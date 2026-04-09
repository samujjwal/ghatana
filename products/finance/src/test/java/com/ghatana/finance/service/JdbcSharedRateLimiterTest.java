package com.ghatana.finance.service;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies shared JDBC-backed Finance transaction rate limiting across store instances
 * @doc.layer product
 * @doc.pattern Test
 */
class JdbcSharedRateLimiterTest {

    @Test
    void enforcesLimitAcrossLimiterInstances() {
        PostgreSQLContainer<?> postgres = FinanceAiPersistenceTestSupport.startPostgres();
        DataSource dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-rate-limit-test");
        try {
            MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
            JdbcSharedRateLimiter firstLimiter = new JdbcSharedRateLimiter(dataSource, 1, Duration.ofMinutes(1), clock);
            JdbcSharedRateLimiter secondLimiter = new JdbcSharedRateLimiter(dataSource, 1, Duration.ofMinutes(1), clock);

            assertTrue(firstLimiter.tryAcquire("tenant-1").allowed());
            com.ghatana.platform.security.ratelimit.RateLimiter.AcquireResult secondResult = secondLimiter.tryAcquire("tenant-1");

            assertFalse(secondResult.allowed());
            assertEquals(0, secondResult.remainingTokens());
            assertTrue(secondResult.retryAfterSeconds() >= 1L);
        } finally {
            FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
            postgres.stop();
        }
    }

    @Test
    void resetsAllowanceInNextWindow() {
        PostgreSQLContainer<?> postgres = FinanceAiPersistenceTestSupport.startPostgres();
        DataSource dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-rate-limit-reset");
        try {
            MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
            JdbcSharedRateLimiter limiter = new JdbcSharedRateLimiter(dataSource, 1, Duration.ofMinutes(1), clock);

            assertTrue(limiter.tryAcquire("tenant-1").allowed());
            assertFalse(limiter.tryAcquire("tenant-1").allowed());

            clock.advance(Duration.ofMinutes(1));

            assertTrue(limiter.tryAcquire("tenant-1").allowed());
            assertEquals(2L, limiter.getStats().getTotalAllowed());
            assertEquals(1L, limiter.getStats().getTotalRejected());
        } finally {
            FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
            postgres.stop();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
