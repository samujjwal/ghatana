/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure Service Module
 */
package com.ghatana.yappc.services.infrastructure;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityServiceAdapter;
import com.ghatana.yappc.infrastructure.security.OsvScannerAdapter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ActiveJ DI module for YAPPC Infrastructure services.
 *
 * <p>Provides bindings for persistence, data integration, and
 * external service adapters:
 * <ul>
 *   <li>{@link SecurityServiceAdapter} — Security scanning and SBOM</li>
 *   <li>{@link InfrastructureServiceFacade} — Unified infrastructure facade</li>
 * </ul>
 *
 * <p>All IO-bound operations use {@code Promise.ofBlocking(executor, ...)}
 * per Golden Rule #3 (never block the event loop).</p>
 *
 * <p>A {@link DataSource} is provisioned when the {@code YAPPC_DB_URL} environment
 * variable is set. When absent the facade correctly reports the database as unreachable
 * instead of silently returning {@code true}.</p>
 *
 * @doc.type class
 * @doc.purpose DI module for infrastructure/persistence services
 * @doc.layer product
 * @doc.pattern Module
 */
public class InfrastructureServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(InfrastructureServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Infrastructure Service DI bindings");
    }

    /**
     * Provides the unified infrastructure facade, wiring in a real DataSource when
     * {@code YAPPC_DB_URL} is present in the environment.
     */
    @Provides
    InfrastructureServiceFacade infrastructureServiceFacade(
            SecurityServiceAdapter securityAdapter) {
        DataSource ds = buildDataSourceFromEnv();
        if (ds != null) {
            logger.info("Creating InfrastructureServiceFacade with JDBC DataSource");
        } else {
            logger.warn("YAPPC_DB_URL not set — InfrastructureServiceFacade running without DataSource");
        }
        return new InfrastructureServiceFacade(securityAdapter, ds);
    }

    /**
     * Provides SecurityServiceAdapter with composite scanner (SAST + dependency scanning).
     */
    @Provides
    SecurityServiceAdapter securityServiceAdapter() {
        logger.info("Creating SecurityServiceAdapter with CompositeSecurityScanner");
        Executor executor = Executors.newCachedThreadPool();

        com.ghatana.yappc.infrastructure.datacloud.adapter.StaticAnalysisScanner staticAnalysisScanner =
            new com.ghatana.yappc.infrastructure.datacloud.adapter.StaticAnalysisScanner(executor);

        OsvScannerAdapter osvScanner = new OsvScannerAdapter(executor);

        com.ghatana.yappc.infrastructure.security.CompositeSecurityScanner compositeScanner =
            new com.ghatana.yappc.infrastructure.security.CompositeSecurityScanner(
                List.of(staticAnalysisScanner, osvScanner)
            );

        return new SecurityServiceAdapter(compositeScanner);
    }

    /**
     * Builds a HikariCP {@link DataSource} from environment variables.
     *
     * <p>Required: {@code YAPPC_DB_URL}<br>
     * Optional: {@code YAPPC_DB_USER}, {@code YAPPC_DB_PASSWORD}, {@code YAPPC_DB_POOL_SIZE}
     *
     * @return a configured DataSource, or {@code null} if {@code YAPPC_DB_URL} is not set
     */
    @Nullable
    private static DataSource buildDataSourceFromEnv() {
        String jdbcUrl = System.getenv("YAPPC_DB_URL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(System.getenv().getOrDefault("YAPPC_DB_USER", ""));
        cfg.setPassword(System.getenv().getOrDefault("YAPPC_DB_PASSWORD", ""));
        cfg.setPoolName("yappc-infra");
        cfg.setMinimumIdle(1);
        int maxPool = Integer.parseInt(System.getenv().getOrDefault("YAPPC_DB_POOL_SIZE", "5"));
        cfg.setMaximumPoolSize(maxPool);
        cfg.setConnectionTimeout(10_000);
        cfg.setIdleTimeout(300_000);
        return new HikariDataSource(cfg);
    }
}

