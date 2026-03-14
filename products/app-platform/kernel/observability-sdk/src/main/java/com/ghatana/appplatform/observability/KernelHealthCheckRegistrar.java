package com.ghatana.appplatform.observability;

import com.ghatana.platform.observability.health.HealthCheck;
import com.ghatana.platform.observability.health.HealthCheckRegistry;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * Registers the standard kernel health checks (IAM DB and ledger DB) into a
 * {@link HealthCheckRegistry}.
 *
 * <p>Each database is checked by executing a lightweight {@code SELECT 1} over the
 * given {@link DataSource}. The check is non-critical (readiness-only) — a DB
 * outage should stop traffic routing but must not trigger a pod restart.
 *
 * <p>Usage at module startup:
 * <pre>{@code
 * KernelHealthCheckRegistrar.registerKernelChecks(
 *     HealthCheckRegistry.getInstance(),
 *     iamDataSource,
 *     ledgerDataSource,
 *     blockingExecutor
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Registers IAM and ledger DB health checks in the platform HealthCheckRegistry
 * @doc.layer product
 * @doc.pattern Registrar, Factory
 */
public final class KernelHealthCheckRegistrar {

    private KernelHealthCheckRegistrar() {}

    /**
     * Registers IAM and ledger database health checks.
     *
     * @param registry         platform health-check registry (must be initialised)
     * @param iamDataSource    DataSource for the IAM database
     * @param ledgerDataSource DataSource for the ledger database
     * @param executor         blocking executor for JDBC calls (ActiveJ offload thread pool)
     */
    public static void registerKernelChecks(
            HealthCheckRegistry registry,
            DataSource iamDataSource,
            DataSource ledgerDataSource,
            Executor executor) {

        registry.register(new DataSourceHealthCheck("iam-db", iamDataSource, executor));
        registry.register(new DataSourceHealthCheck("ledger-db", ledgerDataSource, executor));
    }

    // -------------------------------------------------------------------------
    // Inner implementation — not part of the public API
    // -------------------------------------------------------------------------

    /**
     * Health check that validates database connectivity with a {@code SELECT 1}.
     *
     * @doc.type class
     * @doc.purpose Lightweight DB connectivity probe for any JDBC DataSource
     * @doc.layer product
     * @doc.pattern Strategy (implements HealthCheck)
     */
    private static final class DataSourceHealthCheck implements HealthCheck {

        private static final String PING_SQL = "SELECT 1";

        private final String name;
        private final DataSource dataSource;
        private final Executor executor;

        DataSourceHealthCheck(String name, DataSource dataSource, Executor executor) {
            this.name       = name;
            this.dataSource = dataSource;
            this.executor   = executor;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isCritical() {
            // DB checks are readiness-only — failure removes from LB, not pod restart
            return false;
        }

        @Override
        public Duration getTimeout() {
            return Duration.ofSeconds(3);
        }

        @Override
        public Promise<HealthCheckResult> check() {
            return Promise.ofBlocking(executor, () -> {
                Instant start = Instant.now();
                try (Connection conn = dataSource.getConnection();
                     var stmt = conn.prepareStatement(PING_SQL)) {
                    stmt.executeQuery();
                    Duration elapsed = Duration.between(start, Instant.now());
                    return HealthCheckResult.healthy(
                            name + " is reachable",
                            java.util.Map.of("latencyMs", elapsed.toMillis()),
                            elapsed);
                } catch (Exception ex) {
                    return HealthCheckResult.unhealthy(name + " unreachable: " + ex.getMessage(), ex);
                }
            });
        }
    }
}
