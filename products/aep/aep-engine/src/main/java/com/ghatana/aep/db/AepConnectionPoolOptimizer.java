/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database connection pool optimizer for AEP (AEP-004.4).
 *
 * <p>Monitors pool utilisation and provides real-time statistics that help
 * operators tune pool parameters.  Targets pool efficiency &gt;90%, defined as:
 * {@code (successful borrows) / (total borrow attempts) ≥ 0.90}.
 *
 * <p>This class wraps any {@link DataSource} and collects metrics on every
 * {@link #getConnection()} call.  It does not replace the underlying pool
 * implementation — use it as a measuring layer on top of HikariCP, c3p0, etc.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * DataSource optimizedDs = AepConnectionPoolOptimizer.builder(hikariDs)
 *     .slowConnectionThreshold(Duration.ofMillis(50))
 *     .build()
 *     .asDataSource();
 *
 * // Inspect pool health
 * AepConnectionPoolOptimizer.PoolStats stats = optimizer.stats();
 * assertThat(stats.efficiency()).isGreaterThanOrEqualTo(0.90);
 * }</pre>
 *
 * @doc.type    class
 * @doc.purpose Connection pool monitoring targeting &gt;90% efficiency
 * @doc.layer   product
 * @doc.pattern Decorator, Proxy
 */
public final class AepConnectionPoolOptimizer {

    private static final Logger LOG = LoggerFactory.getLogger(AepConnectionPoolOptimizer.class);

    private final DataSource delegate;
    private final long slowThresholdNs;

    private final AtomicLong totalBorrows     = new AtomicLong(0);
    private final AtomicLong successfulBorrows = new AtomicLong(0);
    private final AtomicLong failedBorrows     = new AtomicLong(0);
    private final AtomicLong slowBorrows       = new AtomicLong(0);
    private final AtomicLong totalBorrowNs     = new AtomicLong(0);

    private AepConnectionPoolOptimizer(Builder builder) {
        this.delegate        = builder.delegate;
        this.slowThresholdNs = builder.slowConnectionThreshold.toNanos();
    }

    // ── DataSource façade ─────────────────────────────────────────────────────

    /**
     * Returns a monitored {@link DataSource} that delegates to the wrapped pool.
     *
     * @return instrumented DataSource
     */
    public DataSource asDataSource() {
        return new InstrumentedDataSource(this);
    }

    /**
     * Borrows a connection from the underlying pool, recording timing metrics.
     *
     * @return connection from the pool
     * @throws SQLException if the pool cannot supply a connection
     */
    public Connection getConnection() throws SQLException {
        totalBorrows.incrementAndGet();
        long start = System.nanoTime();
        try {
            Connection conn = delegate.getConnection();
            long elapsed = System.nanoTime() - start;
            successfulBorrows.incrementAndGet();
            totalBorrowNs.addAndGet(elapsed);
            if (elapsed > slowThresholdNs) {
                slowBorrows.incrementAndGet();
                LOG.warn("Slow connection borrow: {}ms (threshold {}ms)",
                        elapsed / 1_000_000, slowThresholdNs / 1_000_000);
            }
            return conn;
        } catch (SQLException ex) {
            failedBorrows.incrementAndGet();
            LOG.error("Connection borrow failed: {}", ex.getMessage());
            throw ex;
        }
    }

    // ── Statistics ─────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of current pool statistics.
     *
     * @return pool statistics
     */
    public PoolStats stats() {
        long total   = totalBorrows.get();
        long success = successfulBorrows.get();
        long failed  = failedBorrows.get();
        long slow    = slowBorrows.get();
        long ns      = totalBorrowNs.get();
        double efficiency = total == 0 ? 1.0 : (double) success / total;
        double avgMs      = success == 0 ? 0.0 : (double) ns / success / 1_000_000;
        return new PoolStats(total, success, failed, slow, efficiency, avgMs);
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Pool performance statistics snapshot.
     *
     * @param totalBorrows     total connection borrow attempts
     * @param successfulBorrows connections successfully borrowed
     * @param failedBorrows    borrow attempts that threw {@link SQLException}
     * @param slowBorrows      borrows that exceeded the slow threshold
     * @param efficiency       successful borrows / total borrows [0, 1]
     * @param avgBorrowMs      average successful borrow time in milliseconds
     */
    public record PoolStats(
            long totalBorrows,
            long successfulBorrows,
            long failedBorrows,
            long slowBorrows,
            double efficiency,
            double avgBorrowMs
    ) {
        /**
         * Returns {@code true} when pool efficiency meets the AEP-004.4 target of &gt;90%.
         *
         * @return {@code true} if efficiency &ge; 0.90
         */
        public boolean meetsTarget() {
            return efficiency >= 0.90;
        }
    }

    private static final class InstrumentedDataSource implements DataSource {
        private final AepConnectionPoolOptimizer optimizer;

        InstrumentedDataSource(AepConnectionPoolOptimizer optimizer) {
            this.optimizer = optimizer;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return optimizer.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return optimizer.delegate.getConnection(username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return optimizer.delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            optimizer.delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            optimizer.delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return optimizer.delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(AepConnectionPoolOptimizer.class.getName());
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return optimizer.delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return optimizer.delegate.isWrapperFor(iface);
        }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Returns a new builder for {@link AepConnectionPoolOptimizer}.
     *
     * @param delegate the underlying {@link DataSource} to wrap
     * @return builder instance
     */
    public static Builder builder(DataSource delegate) {
        return new Builder(delegate);
    }

    /**
     * Builder for {@link AepConnectionPoolOptimizer}.
     */
    public static final class Builder {
        private final DataSource delegate;
        private Duration slowConnectionThreshold = Duration.ofMillis(50);

        private Builder(DataSource delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        /**
         * Threshold above which a borrow is logged as slow and counted as a slow borrow.
         *
         * @param threshold positive duration
         * @return this builder
         */
        public Builder slowConnectionThreshold(Duration threshold) {
            Objects.requireNonNull(threshold, "threshold must not be null");
            if (threshold.isNegative() || threshold.isZero()) {
                throw new IllegalArgumentException("threshold must be positive");
            }
            this.slowConnectionThreshold = threshold;
            return this;
        }

        /** Builds and returns the optimizer. */
        public AepConnectionPoolOptimizer build() {
            return new AepConnectionPoolOptimizer(this);
        }
    }
}

