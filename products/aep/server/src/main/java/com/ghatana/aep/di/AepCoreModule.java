/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.identity.IdentityResolutionService;
import com.ghatana.aep.compliance.ComplianceService;
import com.ghatana.aep.compliance.InMemoryRetentionPolicyEnforcer;
import com.ghatana.aep.compliance.RetentionPolicyEnforcer;
import com.ghatana.aep.forecasting.ForecastingEngine;
import com.ghatana.aep.forecasting.LinearTrendForecastingEngine;
import com.ghatana.data.governance.ConsentManager;
import com.ghatana.data.governance.DataAccessBroker;
import com.ghatana.data.governance.DefaultDataAccessBroker;
import com.ghatana.data.governance.DefaultPurposeLimitationEnforcer;
import com.ghatana.data.governance.InMemoryConsentManager;
import com.ghatana.data.governance.PurposeLimitationEnforcer;
import com.ghatana.identity.DefaultIdentityService;
import com.ghatana.identity.IdentityService;
import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.incident.GracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryGracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryKillSwitchService;
import com.ghatana.platform.incident.KillSwitchService;
import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.security.analytics.DefaultEgressMonitor;
import com.ghatana.platform.security.analytics.EgressMonitor;
import com.ghatana.platform.security.analytics.PromptInjectionDetector;
import com.ghatana.platform.security.analytics.RegexPromptInjectionDetector;
import com.ghatana.platform.toolruntime.NoopToolSandbox;
import com.ghatana.platform.toolruntime.ToolSandbox;
import com.ghatana.platform.toolruntime.approval.ApprovalGateway;
import com.ghatana.platform.toolruntime.approval.InMemoryApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.ChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.change.InMemoryChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.recertification.InMemoryRecertificationPipeline;
import com.ghatana.platform.toolruntime.recertification.RecertificationPipeline;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

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
 * <p>All bindings use their in-memory defaults. Production deployments should
 * override these bindings with infrastructure-backed implementations
 * (e.g., Redis-backed, OPA-connected, Vault-integrated).
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
    ConsentManager consentManager() {
        return new InMemoryConsentManager();
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
    PolicyAsCodeEngine policyAsCodeEngine() {
        return new InMemoryPolicyEngine();
    }

    // ---- Incident Response --------------------------------------------------

    @Provides
    KillSwitchService killSwitchService() {
        return new InMemoryKillSwitchService();
    }

    @Provides
    GracefulDegradationManager gracefulDegradationManager() {
        return new InMemoryGracefulDegradationManager();
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
    ToolSandbox toolSandbox() {
        return new NoopToolSandbox();
    }

    // ---- Compliance ---------------------------------------------------------

    @Provides
    RetentionPolicyEnforcer retentionPolicyEnforcer() {
        return new InMemoryRetentionPolicyEnforcer();
    }

    @Provides
    ComplianceService complianceService(
            DataAccessBroker dataAccessBroker,
            RetentionPolicyEnforcer enforcer) {
        return new ComplianceService(dataAccessBroker, enforcer);
    }

    // ---- Change Approval ----------------------------------------------------

    @Provides
    ChangeApprovalWorkflow changeApprovalWorkflow() {
        return new InMemoryChangeApprovalWorkflow();
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
