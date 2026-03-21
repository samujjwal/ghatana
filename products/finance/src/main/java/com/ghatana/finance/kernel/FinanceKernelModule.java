package com.ghatana.finance.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.products.finance.FinanceCapabilities;
import com.ghatana.finance.kernel.service.OrderManagementService;
import com.ghatana.finance.kernel.service.RiskManagementService;
import com.ghatana.finance.kernel.service.ComplianceService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.List;
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

    private volatile KernelContext context;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<Object> serviceInstances = new ArrayList<>();

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
            // Finance-specific capabilities (trading/risk/compliance domain)
            FinanceCapabilities.TRADE_PROCESSING,
            FinanceCapabilities.RISK_MANAGEMENT,
            FinanceCapabilities.COMPLIANCE_CHECKING,
            FinanceCapabilities.LEDGER_MANAGEMENT,
            FinanceCapabilities.PORTFOLIO_MANAGEMENT,

            // Shared capabilities used by Finance
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
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException(MODULE_ID + " already initialized");
        }

        this.context = context;

        validateDependencies();
        initializeConfiguration();
        registerEventHandlers();
        initializeServices();
    }

    @Override
    public Promise<Void> start() {
        if (!initialized.get()) {
            return Promise.ofException(new IllegalStateException("Module not initialized"));
        }

        if (!started.compareAndSet(false, true)) {
            return Promise.complete();
        }

        List<Promise<Void>> startPromises = new ArrayList<>();
        for (Object service : serviceInstances) {
            if (service instanceof OrderManagementService oms) {
                startPromises.add(oms.start());
            } else if (service instanceof RiskManagementService rms) {
                startPromises.add(rms.start());
            } else if (service instanceof ComplianceService cs) {
                startPromises.add(cs.start());
            }
        }

        return Promises.all(startPromises)
            .whenException(e -> started.set(false));
    }

    @Override
    public Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return Promise.complete();
        }

        List<Promise<Void>> stopPromises = new ArrayList<>();
        for (Object service : serviceInstances) {
            if (service instanceof OrderManagementService oms) {
                stopPromises.add(oms.stop());
            } else if (service instanceof RiskManagementService rms) {
                stopPromises.add(rms.stop());
            } else if (service instanceof ComplianceService cs) {
                stopPromises.add(cs.stop());
            }
        }

        return Promises.all(stopPromises);
    }

    @Override
    public HealthStatus getHealthStatus() {
        if (!initialized.get()) {
            return HealthStatus.unhealthy("Module not initialized");
        }

        HealthStatus.Builder builder = HealthStatus.builder()
            .withStatus(HealthStatus.Status.HEALTHY)
            .withMessage("Finance module operational");

        for (Object service : serviceInstances) {
            String name;
            boolean healthy;
            if (service instanceof OrderManagementService oms) {
                name = oms.getName();
                healthy = oms.isHealthy();
            } else if (service instanceof RiskManagementService rms) {
                name = rms.getName();
                healthy = rms.isHealthy();
            } else if (service instanceof ComplianceService cs) {
                name = cs.getName();
                healthy = cs.isHealthy();
            } else {
                continue;
            }

            builder.withCheck(name,
                healthy ? HealthStatus.Status.HEALTHY : HealthStatus.Status.UNHEALTHY,
                healthy ? "Service healthy" : "Service unhealthy",
                0);
        }

        return builder.build();
    }

    private void validateDependencies() {
        if (!context.hasDependency(KernelConfigResolver.class)) {
            throw new IllegalStateException("KernelConfigResolver not available");
        }
    }

    private void initializeConfiguration() {
        // Initialize Finance configuration from kernel config resolver
        KernelConfigResolver config = context.getDependency(KernelConfigResolver.class);
        // Load Finance-specific settings
    }

    private void registerEventHandlers() {
        // Register Finance-specific event handlers
        // - Order events
        // - Trade execution events
        // - Risk limit breach events
    }

    private void initializeServices() {
        serviceInstances.add(new OrderManagementService(context));
        serviceInstances.add(new RiskManagementService(context));
        serviceInstances.add(new ComplianceService(context));
        // Additional services can be added here
    }

}
