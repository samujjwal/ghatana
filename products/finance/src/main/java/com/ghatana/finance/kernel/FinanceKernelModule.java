package com.ghatana.finance.kernel;

import com.ghatana.finance.kernel.service.ComplianceService;
import com.ghatana.finance.kernel.service.OrderManagementService;
import com.ghatana.finance.kernel.service.RiskManagementService;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.contracts.ModuleContract;
import com.ghatana.kernel.contracts.SchemaRegistration;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.AbstractKernelModule;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.products.finance.FinanceCapabilities;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level kernel module for Finance product (trading, risk, compliance).
 *
 * <p>Composes 8 sub-modules: OMS, EMS, portfolio, market data, pricing,
 * risk management, compliance engine, surveillance.</p>
 *
 * <p>This module implements the Finance-specific kernel integration following
 * financial regulations (SOX, PCI-DSS, MiFID II) with real-time trading capabilities.</p>
 *
 * @doc.type class
 * @doc.purpose Finance product kernel module — trading/risk/compliance composition root
 * @doc.layer product
 * @doc.pattern Service, Facade
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class FinanceKernelModule extends AbstractKernelModule {

    private static final String MODULE_ID = "finance-core";
    private static final String VERSION = "1.0.0";

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            FinanceCapabilities.TRADE_PROCESSING,
            FinanceCapabilities.RISK_MANAGEMENT,
            FinanceCapabilities.COMPLIANCE_CHECKING,
            FinanceCapabilities.LEDGER_MANAGEMENT,
            FinanceCapabilities.PORTFOLIO_MANAGEMENT,
            KernelCapability.Core.USER_AUTHENTICATION,
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.API_FRAMEWORK,
            KernelCapability.Core.WORKFLOW_ENGINE,
            KernelCapability.Core.OBSERVABILITY_FRAMEWORK
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            new KernelDependency("kernel-core", "1.0.0", KernelDependency.DependencyType.MODULE, false),
            new KernelDependency("event-processing", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("ai-ml-framework", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("market-data-feed", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, false),
            new KernelDependency("risk-models", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, false)
        );
    }

    @Override
    protected void validateDependencies(KernelContext context) {
        if (!context.hasDependency(KernelConfigResolver.class)) {
            throw new IllegalStateException("KernelConfigResolver not available");
        }
    }

    @Override
    protected void initializeConfiguration(KernelContext context) {
        context.getDependency(KernelConfigResolver.class);
    }

    @Override
    protected void registerEventHandlers(KernelContext context) {
        // Register finance event handlers when concrete event contracts are available.
    }

    @Override
    protected void registerServices(List<KernelLifecycleAware> services, KernelContext context) {
        services.add((KernelLifecycleAware) new OrderManagementService(context));
        services.add((KernelLifecycleAware) new RiskManagementService(context));
        services.add((KernelLifecycleAware) new ComplianceService(context));
    }

    @Override
    protected void registerModuleContract(KernelContext context) {
        if (!context.hasDependency(ContractRegistry.class)) {
            return;
        }

        ContractRegistry registry = context.getDependency(ContractRegistry.class);
        registry.registerModuleContract(new ModuleContract(
            MODULE_ID, VERSION, getCapabilities(), getDependencies(), Map.of()
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "finance.orders", VERSION, "json",
            Map.of("fields", List.of("orderId", "clientId", "instrumentId", "side", "quantity", "status")),
            Map.of("owner", MODULE_ID)
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "finance.risk.metrics", VERSION, "json",
            Map.of("fields", List.of("instrumentId", "var", "stress", "exposure", "limit")),
            Map.of("owner", MODULE_ID)
        ));
    }

    @Override
    protected String getHealthyMessage() {
        return "Finance module operational";
    }
}
