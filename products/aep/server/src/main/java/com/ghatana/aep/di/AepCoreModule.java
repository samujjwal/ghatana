/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.identity.IdentityResolutionService;
import com.ghatana.aep.compliance.ComplianceService;
import com.ghatana.aep.compliance.InMemoryRetentionPolicyEnforcer;
import com.ghatana.aep.compliance.PostgresRetentionPolicyEnforcer;
import com.ghatana.aep.compliance.RetentionPolicyEnforcer;
import com.ghatana.aep.forecasting.ForecastingEngine;
import com.ghatana.aep.forecasting.LinearTrendForecastingEngine;
import com.ghatana.data.governance.ConsentManager;
import com.ghatana.data.governance.DataAccessBroker;
import com.ghatana.data.governance.DefaultDataAccessBroker;
import com.ghatana.data.governance.DefaultPurposeLimitationEnforcer;
import com.ghatana.data.governance.InMemoryConsentManager;
import com.ghatana.data.governance.PostgresConsentManager;
import com.ghatana.data.governance.PurposeLimitationEnforcer;
import com.ghatana.identity.DefaultIdentityService;
import com.ghatana.identity.IdentityService;
import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.incident.GracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryGracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryKillSwitchService;
import com.ghatana.platform.incident.PostgresKillSwitchService;
import com.ghatana.platform.incident.RedisGracefulDegradationManager;
import com.ghatana.platform.incident.KillSwitchService;
import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PostgresPolicyEngine;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.security.analytics.DefaultEgressMonitor;
import com.ghatana.platform.security.analytics.EgressMonitor;
import com.ghatana.platform.security.analytics.PromptInjectionDetector;
import com.ghatana.platform.security.analytics.RegexPromptInjectionDetector;
import com.ghatana.platform.toolruntime.NoopToolSandbox;
import com.ghatana.platform.toolruntime.PolicyBasedToolSandbox;
import com.ghatana.platform.toolruntime.ToolSandbox;
import com.ghatana.platform.toolruntime.approval.ApprovalGateway;
import com.ghatana.platform.toolruntime.approval.InMemoryApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.ChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.InMemoryChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.PostgresChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.recertification.InMemoryRecertificationPipeline;
import com.ghatana.platform.toolruntime.recertification.RecertificationPipeline;
import com.ghatana.core.database.config.JpaConfig;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ActiveJ DI module providing core AEP infrastructure bindings.
 *
 * <p>Wires all governance platform modules into the AEP DI graph:
 * <ul>
 *   <li>{@link IdentityService} / {@link IdentityResolutionService}</li>
 *   <li>{@link ConsentManager} / {@link DataAccessBroker}</li>
 *   <li>{@link EgressMonitor} / {@link PromptInjectionDetector}</li>
 *   <li>{@link PolicyAsCodeEngine}</li>
 *   <li>{@link KillSwitchService} / {@link GracefulDegradationManager}</li>
 *   <li>{@link ApprovalGateway} / {@link ToolSandbox}</li>
 *   <li>{@link RetentionPolicyEnforcer} / {@link ComplianceService}</li>
 *   <li>{@link ForecastingEngine}</li>
 * </ul>
 *
 * <p>In production all major bindings are backed by PostgreSQL (via HikariCP)
 * and Redis (via Jedis). The data source and connection pool are resolved from
 * environment variables:
 * <ul>
 *   <li>{@code AEP_DB_URL} — JDBC URL (e.g. {@code jdbc:postgresql://host:5432/aep})</li>
 *   <li>{@code AEP_DB_USER} — database username</li>
 *   <li>{@code AEP_DB_PASSWORD} — database password</li>
 *   <li>{@code AEP_REDIS_HOST} — Redis host (default: localhost)</li>
 *   <li>{@code AEP_REDIS_PORT} — Redis port (default: 6379)</li>
 * </ul>
 * Falls back to in-memory stubs when {@code AEP_DB_URL} is absent so that
 * development without a local database remains possible.
 *
 * @doc.type class
 * @doc.purpose Core AEP DI wiring — binds all governance platform services
 * @doc.layer product
 * @doc.pattern Module
 */
public class AepCoreModule extends AbstractModule {

    @Override
    protected void configure() {
        // No class-level bindings needed; everything is provided via @Provides.
    }

    // ---- Infrastructure -----------------------------------------------------

    @Provides
    DataSource dataSource() {
        String dbUrl      = System.getenv("AEP_DB_URL");
        String dbUser     = System.getenv("AEP_DB_USER");
        String dbPassword = System.getenv("AEP_DB_PASSWORD");

        if (dbUrl == null || dbUrl.isBlank()) {
            // Fallback: use a minimal stub DataSource so in-memory impls still compile;
            // real JDBC ops will fail fast if accidentally used without AEP_DB_URL set.
            return null;
        }
        return JpaConfig.builder()
            .jdbcUrl(dbUrl)
            .username(dbUser != null ? dbUser : "aep")
            .password(dbPassword != null ? dbPassword : "")
            .poolSize(10)
            .build()
            .createDataSource();
    }

    @Provides
    Executor blockingExecutor() {
        return Executors.newFixedThreadPool(8,
            r -> { Thread t = new Thread(r, "aep-jdbc"); t.setDaemon(true); return t; });
    }

    @Provides
    JedisPool jedisPool() {
        String redisHost = System.getenv("AEP_REDIS_HOST");
        String redisPort = System.getenv("AEP_REDIS_PORT");
        String host = (redisHost != null && !redisHost.isBlank()) ? redisHost : "localhost";
        int    port = (redisPort != null && !redisPort.isBlank()) ? Integer.parseInt(redisPort) : 6379;
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(5);
        poolConfig.setTestOnBorrow(true);
        return new JedisPool(poolConfig, host, port);
    }

    // ---- Identity -----------------------------------------------------------

    @Provides
    IdentityService identityService() {
        return new DefaultIdentityService(new InMemoryIdentityResolver());
    }

    @Provides
    IdentityResolutionService identityResolutionService(IdentityService identityService) {
        return new IdentityResolutionService(identityService);
    }

    // ---- Data Governance ----------------------------------------------------

    @Provides
    ConsentManager consentManager(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryConsentManager();
        }
        return new PostgresConsentManager(dataSource, executor);
    }

    @Provides
    PurposeLimitationEnforcer purposeLimitationEnforcer() {
        return new DefaultPurposeLimitationEnforcer();
    }

    @Provides
    DataAccessBroker dataAccessBroker(
            ConsentManager consentManager,
            PurposeLimitationEnforcer purposeEnforcer) {
        return new DefaultDataAccessBroker(consentManager, purposeEnforcer);
    }

    // ---- Security Analytics -------------------------------------------------

    @Provides
    EgressMonitor egressMonitor() {
        return new DefaultEgressMonitor();
    }

    @Provides
    PromptInjectionDetector promptInjectionDetector() {
        return new RegexPromptInjectionDetector();
    }

    // ---- Policy-as-Code -----------------------------------------------------

    @Provides
    PolicyAsCodeEngine policyAsCodeEngine(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryPolicyEngine();
        }
        return new PostgresPolicyEngine(dataSource, executor);
    }

    // ---- Incident Response --------------------------------------------------

    @Provides
    KillSwitchService killSwitchService(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryKillSwitchService();
        }
        return new PostgresKillSwitchService(dataSource, executor);
    }

    @Provides
    GracefulDegradationManager gracefulDegradationManager(
            JedisPool jedisPool, DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryGracefulDegradationManager();
        }
        return new RedisGracefulDegradationManager(jedisPool, dataSource, executor);
    }

    // ---- Tool Runtime -------------------------------------------------------

    @Provides
    ApprovalGateway approvalGateway() {
        InMemoryApprovalWorkflow wf = new InMemoryApprovalWorkflow();
        // Register high-risk action types that require human-in-the-loop approval
        wf.requireApproval("DELETE_AGENT");
        wf.requireApproval("DISABLE_PIPELINE");
        wf.requireApproval("POLICY_OVERRIDE");
        wf.requireApproval("BULK_DELETE");
        return wf;
    }

    @Provides
    ToolSandbox toolSandbox(PolicyAsCodeEngine policyEngine) {
        return new PolicyBasedToolSandbox(policyEngine, new NoopToolSandbox());
    }

    // ---- Compliance ---------------------------------------------------------

    @Provides
    RetentionPolicyEnforcer retentionPolicyEnforcer(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryRetentionPolicyEnforcer();
        }
        return new PostgresRetentionPolicyEnforcer(dataSource, executor);
    }

    @Provides
    ComplianceService complianceService(
            DataAccessBroker dataAccessBroker,
            RetentionPolicyEnforcer enforcer) {
        return new ComplianceService(dataAccessBroker, enforcer);
    }

    // ---- Change Approval ----------------------------------------------------

    @Provides
    ChangeApprovalWorkflow changeApprovalWorkflow(DataSource dataSource, Executor executor) {
        if (dataSource == null) {
            return new InMemoryChangeApprovalWorkflow();
        }
        return new PostgresChangeApprovalWorkflow(dataSource, executor);
    }

    // ---- Recertification ----------------------------------------------------

    @Provides
    RecertificationPipeline recertificationPipeline() {
        return new InMemoryRecertificationPipeline();
    }

    // ---- Forecasting --------------------------------------------------------

    @Provides
    ForecastingEngine forecastingEngine() {
        return new LinearTrendForecastingEngine();
    }
}
