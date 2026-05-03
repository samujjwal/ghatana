/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.identity.IdentityResolutionService;
import com.ghatana.aep.identity.JdbcAgentIdentityResolver;
import com.ghatana.aep.server.governance.MfaStepUpGate;
import com.ghatana.aep.server.governance.StepUpAuthenticationGate;
import com.ghatana.identity.IdentityService;
import com.ghatana.identity.spi.IdentityResolver;
import com.ghatana.platform.incident.GracefulDegradationManager;
import com.ghatana.platform.incident.KillSwitchService;
import com.ghatana.platform.incident.PostgresKillSwitchService;
import com.ghatana.platform.incident.RedisGracefulDegradationManager;
import com.ghatana.platform.pac.CircuitBreakingPolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PostgresPolicyEngine;
import com.ghatana.platform.toolruntime.change.ChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.PostgresChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.recertification.InMemoryRecertificationPipeline;
import com.ghatana.platform.toolruntime.recertification.RecertificationPipeline;
import io.activej.eventloop.Eventloop;
import redis.clients.jedis.JedisPool;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Production-only AEP DI module.
 *
 * <p>Extends {@link AepCoreModule} but validates mandatory production
 * configuration eagerly so the service fails fast before binding unsafe
 * in-memory fallbacks.
 *
 * @doc.type class
 * @doc.purpose Production AEP DI profile with mandatory DB and JWT configuration
 * @doc.layer product
 * @doc.pattern Module
 */
public class AepProductionModule extends AepCoreModule {

    private final Map<String, String> environment;
    private DataSource cachedDataSource;

    public AepProductionModule() {
        this(System.getenv());
    }

    public AepProductionModule(Map<String, String> environment) {
        validateRequiredConfiguration(environment);
        this.environment = Map.copyOf(environment);
    }

    @Override
    DataSource dataSource() {
        if (cachedDataSource != null) {
            return cachedDataSource;
        }
        DataSource dataSource = super.dataSource(environment);
        if (dataSource == null) {
            throw new IllegalStateException(
                "AEP_DB_URL must be configured when AEP_PROFILE=production");
        }
        cachedDataSource = dataSource;
        return cachedDataSource;
    }

    @Override
    IdentityService identityService() {
        String jwtSecret = environment.get("AEP_JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "AEP_JWT_SECRET must be configured when AEP_PROFILE=production");
        }

        return IdentityResolutionService.identityServiceWithResolvers(identityResolvers(dataSource()));
    }

    List<IdentityResolver> identityResolvers(DataSource dataSource) {
        return identityResolvers(environment, dataSource);
    }

    @Override
    protected IdentityResolver baseIdentityResolver(DataSource dataSource) {
        return new JdbcAgentIdentityResolver(dataSource);
    }

    // ---- T-21: Governance fail-closed overrides for production ------------------

    /**
     * In production, {@link KillSwitchService} must be backed by Postgres.
     * An in-memory kill-switch does not survive a restart — rejecting it prevents
     * silent kill-switch loss under process failure.
     */
    @io.activej.inject.annotation.Provides
    @Override
    KillSwitchService killSwitchService(DataSource dataSource, Executor executor) {
        requireDurableStore("KillSwitchService", dataSource);
        return new PostgresKillSwitchService(dataSource, executor);
    }

    /**
     * In production, {@link GracefulDegradationManager} must be backed by Redis + Postgres.
     * An in-memory manager loses degradation state on restart, risking uncontrolled traffic
     * resumption after a controlled degradation window.
     */
    @io.activej.inject.annotation.Provides
    @Override
    GracefulDegradationManager gracefulDegradationManager(
            JedisPool jedisPool, DataSource dataSource, Executor executor) {
        requireDurableStore("GracefulDegradationManager", dataSource);
        return new RedisGracefulDegradationManager(jedisPool, dataSource, executor);
    }

    /**
     * In production, {@link PolicyAsCodeEngine} must be backed by Postgres.
     * Policy evaluation cannot silently degrade to the in-memory allow-all stub.
     */
    @io.activej.inject.annotation.Provides
    @Override
    PolicyAsCodeEngine policyAsCodeEngine(DataSource dataSource, Executor executor, Eventloop eventloop) {
        requireDurableStore("PolicyAsCodeEngine", dataSource);
        return new CircuitBreakingPolicyAsCodeEngine(
            new PostgresPolicyEngine(dataSource, executor), eventloop);
    }

    /**
     * In production, {@link ChangeApprovalWorkflow} must be backed by Postgres.
     * An in-memory workflow loses all pending approvals on restart.
     */
    @io.activej.inject.annotation.Provides
    @Override
    ChangeApprovalWorkflow changeApprovalWorkflow(DataSource dataSource, Executor executor) {
        requireDurableStore("ChangeApprovalWorkflow", dataSource);
        return new PostgresChangeApprovalWorkflow(dataSource, executor);
    }

    /**
     * In production, log a startup warning for the recertification pipeline that still uses
     * the in-memory implementation (no durable Postgres variant available yet) and fail if
     * {@code AEP_ALLOW_INMEM_RECERTIFICATION} is not explicitly set.
     */
    @io.activej.inject.annotation.Provides
    @Override
    RecertificationPipeline recertificationPipeline() {
        String override = environment.get("AEP_ALLOW_INMEM_RECERTIFICATION");
        if (!"true".equalsIgnoreCase(override)) {
            throw new IllegalStateException(
                "RecertificationPipeline has no durable backing store. "
                    + "Set AEP_ALLOW_INMEM_RECERTIFICATION=true to acknowledge this limitation, "
                    + "or provide a durable PostgresRecertificationPipeline implementation.");
        }
        return new InMemoryRecertificationPipeline();
    }

    /**
     * In production, {@link StepUpAuthenticationGate} must be backed by the real
     * {@link MfaStepUpGate}. The no-op implementation allows all verifications
     * unconditionally and must never be used in production.
     *
     * <p>Fails at startup if {@code AEP_MFA_SECRET} is not configured, preventing
     * silent MFA bypass in production environments.
     */
    @io.activej.inject.annotation.Provides
    StepUpAuthenticationGate stepUpAuthenticationGate() {
        String mfaSecret = environment.get("AEP_MFA_SECRET");
        if (mfaSecret == null || mfaSecret.isBlank()) {
            throw new IllegalStateException(
                "AEP_MFA_SECRET must be configured when AEP_PROFILE=production. "
                    + "The kill-switch MFA gate requires a real TOTP secret. "
                    + "Configure AEP_MFA_SECRET or do not set AEP_PROFILE=production.");
        }
        return new MfaStepUpGate();
    }

    // ---- Helpers ------------------------------------------------------------

    static void validateRequiredConfiguration(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        if (!AepRuntimeProfile.isProduction(environment)) {
            return;
        }

        requireValue(environment, "AEP_DB_URL");
        requireValue(environment, "AEP_JWT_SECRET");
    }

    private static void requireValue(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                key + " must be configured when AEP_PROFILE=production");
        }
    }

    private static void requireDurableStore(String serviceName, DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalStateException(
                serviceName + " requires a durable DataSource in production. "
                    + "Configure AEP_DB_URL or set AEP_PROFILE != production.");
        }
    }

}
