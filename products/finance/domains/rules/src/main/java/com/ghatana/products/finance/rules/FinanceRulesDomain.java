/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.rules;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.products.finance.rules.service.FinanceRulesService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Finance Rules Domain Module.
 *
 * <p>Finance-specific rules engine domain. Contains business rules for trade validation,
 * compliance checking, risk assessment, and other finance-specific rule processing.
 * This domain uses kernel capabilities for generic rule processing and implements
 * finance-specific business logic.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Trade validation rules</li>
 *   <li>Compliance checking rules</li>
 *   <li>Risk assessment rules</li>
 *   <li>Regulatory reporting rules</li>
 *   <li>Portfolio constraint rules</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance rules domain - finance-specific business rules engine
 * @doc.layer product
 * @doc.pattern Domain
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceRulesDomain implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(FinanceRulesDomain.class);

    private static final KernelCapability FINANCE_TRADE_PROCESSING = new KernelCapability(
        "finance.trade-processing", "Trade Processing",
        "High-frequency trade order processing and execution",
        KernelCapability.CapabilityType.BUSINESS_LOGIC,
        Map.of(
            "domain", "finance",
            "latency", "microsecond",
            "throughput", "100k-tps"
        )
    );

    private static final KernelCapability FINANCE_COMPLIANCE_CHECKING = new KernelCapability(
        "finance.compliance-checking", "Compliance Checking",
        "Financial compliance monitoring and regulatory reporting",
        KernelCapability.CapabilityType.COMPLIANCE,
        Map.of(
            "domain", "finance",
            "regulations", "securities,aml,kyc,mifid",
            "reporting", "automated"
        )
    );

    private static final KernelCapability FINANCE_RISK_MANAGEMENT = new KernelCapability(
        "finance.risk-management", "Risk Management",
        "Real-time risk assessment and position monitoring",
        KernelCapability.CapabilityType.BUSINESS_LOGIC,
        Map.of(
            "domain", "finance",
            "types", "market,credit,operational,liquidity",
            "calculation", "real-time"
        )
    );

    private FinanceRulesService rulesService;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "finance-rules";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            // Finance-specific capabilities
            FINANCE_TRADE_PROCESSING,
            FINANCE_COMPLIANCE_CHECKING,
            FINANCE_RISK_MANAGEMENT,

            // Reused kernel capabilities
            KernelCapability.Core.CONFIG_MANAGEMENT,
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.OBSERVABILITY_FRAMEWORK
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            // Kernel capabilities
            KernelDependency.onCapability("config"),
            KernelDependency.onCapability("event-store"),
            KernelDependency.onCapability("audit"),

            // Platform capabilities
            KernelDependency.onCapability("data.storage"),
            KernelDependency.onCapability("observability.framework")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Finance Rules domain");
        this.context = context;

        // Initialize finance rules service
        this.rulesService = new FinanceRulesService(context);

        // Register service with kernel context
        context.registerService(FinanceRulesService.class, rulesService);

        log.info("Finance Rules domain initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Finance Rules domain");


            // Start rules service
            rulesService.start();

            log.info("Finance Rules domain started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Finance Rules domain");


            // Stop rules service
            if (rulesService != null) {
                rulesService.stop();
            }

            log.info("Finance Rules domain stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean rulesHealthy = rulesService != null && rulesService.isHealthy();

            return rulesHealthy
                ? HealthStatus.healthy("Finance rules service operational")
                : HealthStatus.unhealthy("Finance rules service degraded");
        } catch (Exception e) {
            log.error("Error checking finance rules domain health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }
}
