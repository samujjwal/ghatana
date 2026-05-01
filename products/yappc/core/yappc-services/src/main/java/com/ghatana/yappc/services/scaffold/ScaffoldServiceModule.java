/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Scaffold Service Module
 */
package com.ghatana.yappc.services.scaffold;

import com.ghatana.platform.observability.MetricsProvider;
import com.ghatana.yappc.core.orchestrator.PolyglotBuildOrchestrator;
import com.ghatana.yappc.core.services.ProjectAnalysisService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * ActiveJ DI module for YAPPC Scaffold services.
 *
 * <p>Provides bindings for code generation, project scaffolding,
 * and build orchestration:
 * <ul>
 *   <li>{@link ProjectAnalysisService} — Project structure detection
 *       (languages, frameworks, build tools)</li>
 *   <li>{@link PolyglotBuildOrchestrator} — Multi-language build
 *       file generation (Gradle, Maven, Cargo, Go, npm)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DI module for scaffold/code-generation services
 * @doc.layer product
 * @doc.pattern Module
 */
public class ScaffoldServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(ScaffoldServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Scaffold Service DI bindings");
    }

    /**
     * Provides ProjectAnalysisService for analyzing existing project structure.
     *
     * <p>Detects languages, frameworks, build tools, and CI/CD patterns
     * for intelligent scaffold generation.</p>
     */
    @Provides
    PrometheusMeterRegistry prometheusMeterRegistry() {
        return MetricsProvider.getRegistry();
    }

    @Provides
    ProjectAnalysisService projectAnalysisService() {
        logger.info("Creating ProjectAnalysisService");
        return new ProjectAnalysisService();
    }

    /**
     * Provides PolyglotBuildOrchestrator for multi-language build generation.
     *
     * <p>Generates build files for Java (Gradle/Maven), Rust (Cargo),
     * Go (go.mod), Node.js (package.json), and more.</p>
     */
    @Provides
    PolyglotBuildOrchestrator polyglotBuildOrchestrator() {
        logger.info("Creating PolyglotBuildOrchestrator");
        return new PolyglotBuildOrchestrator();
    }

    /** Provides DataSource for scaffold service persistence. */
    @Provides
    DataSource dataSource() {
        String url = System.getenv().getOrDefault("YAPPC_SCAFFOLD_DB_URL", "jdbc:postgresql://localhost:5432/yappc_scaffold");
        String user = System.getenv().getOrDefault("YAPPC_SCAFFOLD_DB_USER", "yappc");
        String pass = System.getenv().getOrDefault("YAPPC_SCAFFOLD_DB_PASS", "yappc");
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(10);
        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    /** Provides the durable {@link com.ghatana.platform.security.rbac.PolicyRepository} for RBAC policy persistence. */
    @Provides
    com.ghatana.platform.security.rbac.PolicyRepository policyRepository(DataSource dataSource) {
        logger.info("Creating JdbcPolicyRepository — RBAC policies persisted to PostgreSQL");
        return new com.ghatana.platform.security.rbac.JdbcPolicyRepository(dataSource);
    }
}
