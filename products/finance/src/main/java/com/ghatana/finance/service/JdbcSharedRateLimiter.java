package com.ghatana.finance.service;

import com.ghatana.platform.security.ratelimit.RateLimiter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

/**
 * @doc.type class
 * @doc.purpose Provides shared JDBC-backed rate limiting so Finance transaction throttling is enforced across instances
 * @doc.layer product
 * @doc.pattern Component
 */
public final class JdbcSharedRateLimiter implements RateLimiter {

    private static final String UPSERT_SQL = """
        INSERT INTO finance_transaction_rate_limit_window (
            rate_limit_key,
            window_start_epoch_seconds,
            request_count,
            updated_at
        ) VALUES (?, ?, 1, ?)
        ON CONFLICT (rate_limit_key, window_start_epoch_seconds)
        DO UPDATE SET
            request_count = finance_transaction_rate_limit_window.request_count + 1,
            updated_at = EXCLUDED.updated_at
        RETURNING request_count
        """;
    private static final String MISSING_TABLE_SQL_STATE = "42P01";

    private final DataSource dataSource;
    private final int maxRequestsPerWindow;
    private final Duration windowDuration;
    private final Clock clock;
    private final AtomicLong allowedCount = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();

    public JdbcSharedRateLimiter(
            DataSource dataSource,
            int maxRequestsPerWindow,
            Duration windowDuration,
            Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        if (maxRequestsPerWindow <= 0) {
            throw new IllegalArgumentException("maxRequestsPerWindow must be positive");
        }
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowDuration = Objects.requireNonNull(windowDuration, "windowDuration cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        FinanceTransactionPersistenceSupport.migrate(dataSource);
    }

    @Override
    public AcquireResult tryAcquire(String key) {
        Objects.requireNonNull(key, "key must not be null");

        Instant now = Instant.now(clock);
        long windowSeconds = windowDuration.toSeconds();
        long nowEpochSeconds = now.getEpochSecond();
        long windowStart = (nowEpochSeconds / windowSeconds) * windowSeconds;
        long resetAt = windowStart + windowSeconds;

        try {
            int requestCount = withInitializedSchema(() -> incrementAndGetCount(key, windowStart, now));
            if (requestCount <= maxRequestsPerWindow) {
                allowedCount.incrementAndGet();
                return new AcquireResult(true, Math.max(0, maxRequestsPerWindow - requestCount), 0L, resetAt);
            }

            rejectedCount.incrementAndGet();
            long retryAfterSeconds = Math.max(1L, resetAt - nowEpochSeconds);
            return new AcquireResult(false, 0, retryAfterSeconds, resetAt);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to enforce shared finance transaction rate limit", exception);
        }
    }

    @Override
    public Stats getStats() {
        return new Stats() {
            @Override
            public long getTotalAllowed() {
                return allowedCount.get();
            }

            @Override
            public long getTotalRejected() {
                return rejectedCount.get();
            }
        };
    }

    private int incrementAndGetCount(String key, long windowStart, Instant now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, key);
            statement.setLong(2, windowStart);
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Shared finance transaction rate limiter did not return a counter");
                }
                int requestCount = resultSet.getInt("request_count");
                FinanceTransactionPersistenceSupport.commitIfNeeded(connection);
                return requestCount;
            }
        }
    }

    private <T> T withInitializedSchema(SqlOperation<T> operation) throws SQLException {
        try {
            return operation.run();
        } catch (SQLException exception) {
            if (!MISSING_TABLE_SQL_STATE.equals(exception.getSQLState())) {
                throw exception;
            }
            FinanceTransactionPersistenceSupport.migrate(dataSource);
            return operation.run();
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }
}
