package com.ghatana.datacloud.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates a sequence of {@link ZeroDowntimeMigrationStrategy} steps after Flyway DDL
 * migrations have run.
 *
 * <h2>Design Intent</h2>
 * <p>Flyway handles DDL (schema) migrations — {@code CREATE TABLE}, {@code ADD COLUMN},
 * {@code DROP CONSTRAINT}, etc. {@code DataMigrationService} handles DML (data) migrations that
 * must run with careful batching to avoid long-held locks or replication lag spikes:
 * backfills, column-rename copy steps, and concurrent index builds.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Register strategies once at startup, after Flyway.migrate()
 * DataMigrationService svc = new DataMigrationService(dataSource);
 * svc.register(new ColumnRenameMigration("entities", "data_legacy", "data", 500))
 *    .register(new BackfillEntitiesDisplayName());
 *
 * List&lt;MigrationReport&gt; reports = svc.runAll();
 * reports.stream().filter(r -&gt; !r.success())
 *        .forEach(r -&gt; log.error("Migration failed: {}", r));
 * </pre>
 *
 * <h2>Failure Handling</h2>
 * <p>A strategy failure is recorded in the returned reports but does <em>not</em> abort
 * subsequent strategies. This allows partial progress to be observed and retried on the
 * next startup. All strategies must be idempotent so that re-running them is safe.
 *
 * @doc.type class
 * @doc.purpose Startup orchestrator for zero-downtime DML migration strategies
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DataMigrationService {

    private static final Logger LOG = LoggerFactory.getLogger(DataMigrationService.class);

    private final DataSource dataSource;
    private final List<ZeroDowntimeMigrationStrategy> strategies = new ArrayList<>();

    /**
     * @param dataSource live DataSource, typically the same pool used by the application
     */
    public DataMigrationService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /**
     * Register a strategy to run during {@link #runAll()}.
     * Strategies are executed in the order they are registered.
     *
     * @param strategy non-null strategy to append
     * @return {@code this} for fluent chaining
     */
    public DataMigrationService register(ZeroDowntimeMigrationStrategy strategy) {
        strategies.add(Objects.requireNonNull(strategy, "strategy must not be null"));
        LOG.debug("[DataMigrationService] Registered strategy: {}", strategy.name());
        return this;
    }

    /**
     * Execute all registered strategies in registration order.
     *
     * <p>Each strategy is executed independently. A failure in one does not prevent subsequent
     * strategies from running. Inspect {@link MigrationReport#success()} on each report to
     * determine whether action is required.
     *
     * @return unmodifiable list of {@link MigrationReport}, one per strategy, in order
     */
    public List<MigrationReport> runAll() {
        if (strategies.isEmpty()) {
            LOG.info("[DataMigrationService] No strategies registered — nothing to run.");
            return List.of();
        }

        LOG.info("[DataMigrationService] Starting {} zero-downtime migration strategy/strategies", strategies.size());
        List<MigrationReport> reports = new ArrayList<>(strategies.size());

        for (ZeroDowntimeMigrationStrategy strategy : strategies) {
            LOG.info("[DataMigrationService] Executing: {}", strategy.name());
            MigrationReport report;
            try {
                report = strategy.execute(dataSource);
            } catch (SQLException e) {
                report = MigrationReport.failure(
                        strategy.name(), "unknown", "EXECUTE", 0L,
                        "Unhandled SQL exception: " + e.getMessage());
                LOG.error("[DataMigrationService] Strategy '{}' threw unhandled exception: {}",
                        strategy.name(), e.getMessage());
            }
            reports.add(report);
            if (report.success()) {
                LOG.info("[DataMigrationService] '{}' succeeded — {} rows in {}ms",
                        report.strategyName(), report.rowsAffected(), report.durationMs());
            } else {
                LOG.error("[DataMigrationService] '{}' FAILED — {}",
                        report.strategyName(), report.errorMessage());
            }
        }

        long succeeded = reports.stream().filter(MigrationReport::success).count();
        LOG.info("[DataMigrationService] Done. {}/{} strategies succeeded.", succeeded, reports.size());

        return Collections.unmodifiableList(reports);
    }

    /** Number of strategies currently registered. */
    public int strategyCount() {
        return strategies.size();
    }
}
