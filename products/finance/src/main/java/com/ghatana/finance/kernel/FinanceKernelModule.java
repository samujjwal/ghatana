package com.ghatana.finance.kernel;

import com.ghatana.finance.kernel.service.ComplianceService;
import com.ghatana.finance.kernel.service.LedgerManagementService;
import com.ghatana.finance.kernel.service.MarketDataService;
import com.ghatana.finance.kernel.service.OrderManagementService;
import com.ghatana.finance.kernel.service.PortfolioManagementService;
import com.ghatana.finance.kernel.service.RiskManagementService;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.contracts.ModuleContract;
import com.ghatana.kernel.contracts.SchemaRegistration;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
public class FinanceKernelModule implements KernelModule {

    private static final String MODULE_ID = "finance-core";
    private static final String VERSION = "1.0.0";

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final List<KernelLifecycleAware> services = new ArrayList<>();
    private volatile KernelContext context;

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
    public void initialize(KernelContext context) {
        if (initialized.getAndSet(true)) {
            throw new IllegalStateException("FinanceKernelModule already initialized");
        }
        
        this.context = context;
        
        // Validate dependencies
        if (!context.hasDependency(KernelConfigResolver.class)) {
            throw new IllegalStateException("KernelConfigResolver not available");
        }
        
        // Initialize configuration
        context.getDependency(KernelConfigResolver.class);
        
        // Register event handlers
        registerEventHandlers(context);
        
        // Register services
        registerServices(context);
        
        // Register module contract
        registerModuleContract(context);
    }

    @Override
    public Promise<Void> start() {
        if (!initialized.get()) {
            return Promise.ofException(new IllegalStateException("Module not initialized"));
        }
        
        started.set(true);
        List<Promise<Void>> startPromises = new ArrayList<>();
        for (KernelLifecycleAware service : services) {
            startPromises.add(service.start());
        }
        return Promises.all(startPromises);
    }

    @Override
    public Promise<Void> stop() {
        started.set(false);
        List<Promise<Void>> stopPromises = new ArrayList<>();
        // Stop in reverse order
        for (int i = services.size() - 1; i >= 0; i--) {
            stopPromises.add(services.get(i).stop());
        }
        return Promises.all(stopPromises);
    }

    @Override
    public HealthStatus getHealthStatus() {
        if (!initialized.get()) {
            return HealthStatus.unhealthy("Module not initialized");
        }
        if (!started.get()) {
            return HealthStatus.unhealthy("Module not started");
        }
        
        HealthStatus.Builder builder = HealthStatus.builder()
            .withStatus(HealthStatus.Status.HEALTHY)
            .withMessage("Finance module operational");
        
        boolean allHealthy = true;
        for (KernelLifecycleAware service : services) {
            boolean serviceHealthy = service.isHealthy();
            builder.withCheck(
                service.getName(),
                serviceHealthy ? HealthStatus.Status.HEALTHY : HealthStatus.Status.UNHEALTHY,
                serviceHealthy ? "Operational" : "Unhealthy",
                0
            );
            allHealthy = allHealthy && serviceHealthy;
        }
        
        if (!allHealthy) {
            builder.withStatus(HealthStatus.Status.UNHEALTHY);
        }
        
        return builder.build();
    }

    public boolean providesCapability(KernelCapability capability) {
        return getCapabilities().contains(capability);
    }

    private void registerEventHandlers(KernelContext context) {
        // Register finance event handlers when concrete event contracts are available.
    }

    private void registerServices(KernelContext context) {
        services.add(new OrderManagementService(context));
        services.add(new RiskManagementService(context));
        services.add(new ComplianceService(context));
        services.add(new PortfolioManagementService(context));
        services.add(new LedgerManagementService(context));
        services.add(new MarketDataService(context));
    }

    private void registerModuleContract(KernelContext context) {
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
}
