/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.products.finance.bff.FinanceBFF;
import com.ghatana.products.finance.domains.corporateactions.CorporateActionsDomainModule;
import com.ghatana.products.finance.domains.ems.EmsDomainModule;
import com.ghatana.products.finance.domains.marketdata.MarketDataDomainModule;
import com.ghatana.products.finance.domains.oms.OmsDomainModule;
import com.ghatana.products.finance.domains.pms.PmsDomainModule;
import com.ghatana.products.finance.domains.posttrade.PostTradeDomainModule;
import com.ghatana.products.finance.domains.pricing.PricingDomainModule;
import com.ghatana.products.finance.domains.reconciliation.ReconciliationDomainModule;
import com.ghatana.products.finance.domains.referencedata.ReferenceDataDomainModule;
import com.ghatana.products.finance.domains.regulatoryreporting.RegulatoryReportingDomainModule;
import com.ghatana.products.finance.domains.risk.RiskDomainModule;
import com.ghatana.products.finance.domains.sanctions.SanctionsDomainModule;
import com.ghatana.products.finance.domains.surveillance.SurveillanceDomainModule;
import com.ghatana.products.finance.rules.FinanceRulesDomain;
import com.ghatana.products.finance.shell.FinanceProductShell;
import io.activej.promise.Promise;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finance Product Module.
 *
 * <p>Main product module for the Finance product. Serves as the entry point for
 * finance-specific business logic and workflows. This module uses kernel capabilities
 * for generic functionality and implements finance-specific business rules.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Order Management System (OMS)</li>
 *   <li>Execution Management System (EMS)</li>
 *   <li>Portfolio Management System (PMS)</li>
 *   <li>Risk Management</li>
 *   <li>Regulatory Compliance</li>
 *   <li>Finance-specific Rules Engine</li>
 *   <li>Corporate Actions Processing</li>
 *   <li>Market Data Management</li>
 *   <li>Post-Trade Processing</li>
 *   <li>Pricing and Valuation</li>
 *   <li>Trade Reconciliation</li>
 *   <li>Reference Data Management</li>
 *   <li>Regulatory Reporting (MiFID II, EMIR, SFTR)</li>
 *   <li>Sanctions Screening</li>
 *   <li>Trade Surveillance</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance product module - main entry point for finance business logic
 * @doc.layer product
 * @doc.pattern Module
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceProductModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(FinanceProductModule.class);

    private FinanceProductShell productShell;
    private FinanceBFF bff;
    private KernelContext context;

    private OmsDomainModule omsModule;
    private EmsDomainModule emsModule;
    private PmsDomainModule pmsModule;
    private RiskDomainModule riskModule;
    private FinanceRulesDomain rulesModule;
    private CorporateActionsDomainModule corporateActionsModule;
    private MarketDataDomainModule marketDataModule;
    private PostTradeDomainModule postTradeModule;
    private PricingDomainModule pricingModule;
    private ReconciliationDomainModule reconciliationModule;
    private ReferenceDataDomainModule referenceDataModule;
    private RegulatoryReportingDomainModule regulatoryReportingModule;
    private SanctionsDomainModule sanctionsModule;
    private SurveillanceDomainModule surveillanceModule;

    @Override
    public String getModuleId() {
        return "finance";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            FinanceCapabilities.TRADE_PROCESSING,
            FinanceCapabilities.PORTFOLIO_MANAGEMENT,
            FinanceCapabilities.RISK_MANAGEMENT,
            FinanceCapabilities.COMPLIANCE_CHECKING,
            FinanceCapabilities.LEDGER_MANAGEMENT,
            KernelCapability.Core.USER_AUTHENTICATION,
            KernelCapability.Core.CONFIG_MANAGEMENT,
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.OBSERVABILITY_FRAMEWORK,
            KernelCapability.Core.RESILIENCE_PATTERNS
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("authentication"),
            KernelDependency.onCapability("config"),
            KernelDependency.onCapability("event-store"),
            KernelDependency.onCapability("audit"),
            KernelDependency.onCapability("resilience"),
            KernelDependency.onCapability("data.storage"),
            KernelDependency.onCapability("observability.framework"),
            KernelDependency.onCapability("security.framework")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Finance product module with all 14 domains");
        this.context = context;

        productShell = new FinanceProductShell(context);
        bff = new FinanceBFF(context);

        omsModule = new OmsDomainModule();
        emsModule = new EmsDomainModule();
        pmsModule = new PmsDomainModule();
        riskModule = new RiskDomainModule();
        rulesModule = new FinanceRulesDomain();
        corporateActionsModule = new CorporateActionsDomainModule();
        marketDataModule = new MarketDataDomainModule();
        postTradeModule = new PostTradeDomainModule();
        pricingModule = new PricingDomainModule();
        reconciliationModule = new ReconciliationDomainModule();
        referenceDataModule = new ReferenceDataDomainModule();
        regulatoryReportingModule = new RegulatoryReportingDomainModule();
        sanctionsModule = new SanctionsDomainModule();
        surveillanceModule = new SurveillanceDomainModule();

        initializeDomainModules();

        context.registerService(FinanceProductShell.class, productShell);
        context.registerService(FinanceBFF.class, bff);

        log.info("Finance product module initialized successfully with 14 domains");
    }

    private void initializeDomainModules() {
        log.debug("Initializing all finance domain modules");

        omsModule.initialize(context);
        emsModule.initialize(context);
        pmsModule.initialize(context);
        riskModule.initialize(context);
        rulesModule.initialize(context);
        corporateActionsModule.initialize(context);
        marketDataModule.initialize(context);
        postTradeModule.initialize(context);
        pricingModule.initialize(context);
        reconciliationModule.initialize(context);
        referenceDataModule.initialize(context);
        regulatoryReportingModule.initialize(context);
        sanctionsModule.initialize(context);
        surveillanceModule.initialize(context);

        log.debug("All finance domain modules initialized");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Finance product module with all domains");

        startDomainModules();
        productShell.start();
        bff.start();

        log.info("Finance product module started successfully");
        return Promise.complete();
    }

    private void startDomainModules() {
        log.debug("Starting all finance domain modules");

        omsModule.start();
        emsModule.start();
        pmsModule.start();
        riskModule.start();
        rulesModule.start();
        corporateActionsModule.start();
        marketDataModule.start();
        postTradeModule.start();
        pricingModule.start();
        reconciliationModule.start();
        referenceDataModule.start();
        regulatoryReportingModule.start();
        sanctionsModule.start();
        surveillanceModule.start();

        log.debug("All finance domain modules started");
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Finance product module");

        if (bff != null) {
            bff.stop();
        }
        if (productShell != null) {
            productShell.stop();
        }

        stopDomainModules();

        log.info("Finance product module stopped successfully");
        return Promise.complete();
    }

    private void stopDomainModules() {
        log.debug("Stopping all finance domain modules");

        if (surveillanceModule != null) surveillanceModule.stop();
        if (sanctionsModule != null) sanctionsModule.stop();
        if (regulatoryReportingModule != null) regulatoryReportingModule.stop();
        if (referenceDataModule != null) referenceDataModule.stop();
        if (reconciliationModule != null) reconciliationModule.stop();
        if (pricingModule != null) pricingModule.stop();
        if (postTradeModule != null) postTradeModule.stop();
        if (marketDataModule != null) marketDataModule.stop();
        if (corporateActionsModule != null) corporateActionsModule.stop();
        if (rulesModule != null) rulesModule.stop();
        if (riskModule != null) riskModule.stop();
        if (pmsModule != null) pmsModule.stop();
        if (emsModule != null) emsModule.stop();
        if (omsModule != null) omsModule.stop();

        log.debug("All finance domain modules stopped");
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean shellHealthy = productShell != null && productShell.isHealthy();
            boolean bffHealthy = bff != null && bff.isHealthy();
            boolean domainsHealthy = checkDomainModulesHealth();
            boolean overallHealthy = shellHealthy && bffHealthy && domainsHealthy;

            return overallHealthy
                ? HealthStatus.healthy("All finance services and 14 domains operational")
                : HealthStatus.unhealthy("Some finance services or domains degraded");
        } catch (Exception e) {
            log.error("Error checking finance module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }

    private boolean checkDomainModulesHealth() {
        boolean allHealthy = true;

        if (omsModule != null) allHealthy &= omsModule.getHealthStatus().isHealthy();
        if (emsModule != null) allHealthy &= emsModule.getHealthStatus().isHealthy();
        if (pmsModule != null) allHealthy &= pmsModule.getHealthStatus().isHealthy();
        if (riskModule != null) allHealthy &= riskModule.getHealthStatus().isHealthy();
        if (rulesModule != null) allHealthy &= rulesModule.getHealthStatus().isHealthy();
        if (corporateActionsModule != null) allHealthy &= corporateActionsModule.getHealthStatus().isHealthy();
        if (marketDataModule != null) allHealthy &= marketDataModule.getHealthStatus().isHealthy();
        if (postTradeModule != null) allHealthy &= postTradeModule.getHealthStatus().isHealthy();
        if (pricingModule != null) allHealthy &= pricingModule.getHealthStatus().isHealthy();
        if (reconciliationModule != null) allHealthy &= reconciliationModule.getHealthStatus().isHealthy();
        if (referenceDataModule != null) allHealthy &= referenceDataModule.getHealthStatus().isHealthy();
        if (regulatoryReportingModule != null) allHealthy &= regulatoryReportingModule.getHealthStatus().isHealthy();
        if (sanctionsModule != null) allHealthy &= sanctionsModule.getHealthStatus().isHealthy();
        if (surveillanceModule != null) allHealthy &= surveillanceModule.getHealthStatus().isHealthy();

        return allHealthy;
    }
}