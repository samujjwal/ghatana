package com.ghatana.yappc.services.infrastructure;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityServiceAdapter;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Infrastructure service facade for the YAPPC product.
 *
 * <p>Provides centralized access to persistence, data integration, and external
 * API clients. Delegates to {@code infrastructure:datacloud} adapters and
 * platform database/event-cloud abstractions.</p>
 *
 * <p>All operations return ActiveJ {@link Promise} per Golden Rule #3
 * (ActiveJ Concurrency). IO-bound operations use
 * {@code Promise.ofBlocking(executor, ...)}.</p>
 *
 * @doc.type class
 * @doc.purpose Unified infrastructure service facade
 * @doc.layer product
 * @doc.pattern Facade
 */
public class InfrastructureServiceFacade {

    private static final Logger logger = LoggerFactory.getLogger(InfrastructureServiceFacade.class);

    /** Dedicated executor for blocking JDBC calls — never runs on the ActiveJ event loop. */
    private static final Executor DB_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "infra-db");
        t.setDaemon(true);
        return t;
    });

    private final SecurityServiceAdapter securityAdapter;

    /**
     * Optional DataSource for real connectivity checks.
     * When {@code null} the DB health check returns {@code false} instead of
     * a misleading {@code true}.
     */
    @Nullable
    private final DataSource dataSource;

    /**
     * Creates an InfrastructureServiceFacade with a security adapter but no DB DataSource.
     * {@link #isDatabaseReachable()} will return {@code false} when no DataSource is configured.
     *
     * @param securityAdapter security scanning adapter
     */
    public InfrastructureServiceFacade(@NotNull SecurityServiceAdapter securityAdapter) {
        this(securityAdapter, null);
    }

    /**
     * Creates an InfrastructureServiceFacade with all adapters.
     *
     * @param securityAdapter security scanning adapter
     * @param dataSource       optional JDBC DataSource for health checks; may be {@code null}
     */
    public InfrastructureServiceFacade(
            @NotNull SecurityServiceAdapter securityAdapter,
            @Nullable DataSource dataSource) {
        this.securityAdapter = securityAdapter;
        this.dataSource = dataSource;
        logger.info("InfrastructureServiceFacade initialized (dataSource={})",
                dataSource != null ? "configured" : "not configured");
    }

    /**
     * Performs a health check on all infrastructure services.
     *
     * @return a Promise resolving to "OK" if all services are healthy
     */
    @NotNull
    public Promise<String> healthCheck() {
        return isDatabaseReachable().map(db -> db ? "OK" : "DEGRADED");
    }

    /**
     * Tests database connectivity by executing {@code SELECT 1} over the configured
     * {@link DataSource}. Uses {@code Promise.ofBlocking} so the ActiveJ event loop
     * is never blocked.
     *
     * <p>Returns {@code false} (rather than always {@code true}) when no DataSource is
     * configured, making misconfiguration visible instead of silently claiming "healthy".</p>
     *
     * @return a Promise resolving to {@code true} iff a live DB connection is available
     */
    @NotNull
    public Promise<Boolean> isDatabaseReachable() {
        if (dataSource == null) {
            logger.warn("isDatabaseReachable: no DataSource configured — reporting unreachable");
            return Promise.of(false);
        }
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return true;
            } catch (Exception e) {
                logger.warn("Database health check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Scans a project for security vulnerabilities.
     *
     * @param projectPath the path to scan
     * @return a Promise resolving to the vulnerability report
     */
    @NotNull
    public Promise<Map<String, Object>> scanProject(@NotNull Path projectPath) {
        return securityAdapter.scanProject(projectPath);
    }
}

