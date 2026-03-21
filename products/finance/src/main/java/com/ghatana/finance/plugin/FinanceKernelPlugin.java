/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import com.ghatana.products.finance.FinanceCapabilities;
import io.activej.promise.Promise;

import java.util.Set;

/**
 * Finance Kernel Plugin - Canonical Implementation.
 *
 * <p>This plugin registers Finance-specific capabilities with the kernel
 * using the canonical {@link KernelPlugin} interface. It replaces the
 * deprecated {@code ProductPlugin} pattern.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Trade processing (OMS/EMS integration)</li>
 *   <li>Risk management (real-time risk calculations)</li>
 *   <li>Compliance checking (regulatory compliance)</li>
 *   <li>Portfolio management (PMS functionality)</li>
 *   <li>Market data (pricing and reference data)</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance domain pack kernel plugin implementing canonical KernelPlugin interface
 * @doc.layer product
 * @doc.pattern Plugin, DomainPack
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class FinanceKernelPlugin implements KernelPlugin {

    private final PluginManifest manifest;
    @SuppressWarnings("unused")
    private KernelContext context;

    /**
     * Creates a new Finance kernel plugin with default manifest.
     */
    public FinanceKernelPlugin() {
        this.manifest = createManifest();
    }

    private PluginManifest createManifest() {
        PluginManifest.Builder builder = PluginManifest.builder();
        builder.pluginId("finance");
        builder.version("1.0.0");
        builder.description("Financial trading and risk management domain pack");
        builder.author("Ghatana Finance Team");
        builder.license("Proprietary");

        // Add capabilities
        for (KernelCapability capability : getDeclaredCapabilities()) {
            builder.capability(capability);
        }

        return builder.build();
    }

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public Set<String> getExportedContracts() {
        return Set.of(
            "com.ghatana.finance.oms.OrderManagementService",
            "com.ghatana.finance.ems.ExecutionService",
            "com.ghatana.finance.pms.PortfolioService",
            "com.ghatana.finance.risk.RiskCalculationService",
            "com.ghatana.finance.compliance.ComplianceService",
            "com.ghatana.finance.marketdata.MarketDataService"
        );
    }

    @Override
    public Set<String> getRequiredContracts() {
        return Set.of(
            "com.ghatana.kernel.modules.authentication.AuthenticationService",
            "com.ghatana.kernel.modules.config.ConfigService",
            "com.ghatana.kernel.modules.eventstore.EventStoreService",
            "com.ghatana.kernel.modules.audit.AuditService"
        );
    }

    @Override
    public Promise<Void> install() {
        // One-time setup: database migrations, initial config
        return Promise.complete();
    }

    @Override
    public Promise<Void> uninstall() {
        // Cleanup when plugin is removed
        return Promise.complete();
    }

    @Override
    public Promise<Void> reload() {
        // Configuration refresh
        return Promise.complete();
    }

    @Override
    public void initialize(KernelContext context) {
        this.context = context;
        registerFinanceServices();
        registerFinanceExtensions();
    }

    @Override
    public Promise<Void> start() {
        startTradeProcessingService();
        startRiskManagementService();
        startComplianceService();
        startPortfolioService();
        startMarketDataService();
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        stopTradeProcessingService();
        stopRiskManagementService();
        stopComplianceService();
        stopPortfolioService();
        stopMarketDataService();
        return Promise.complete();
    }

    // ==================== Private Methods ====================

    private Set<KernelCapability> getDeclaredCapabilities() {
        return Set.of(
            FinanceCapabilities.TRADE_PROCESSING,
            FinanceCapabilities.RISK_MANAGEMENT,
            FinanceCapabilities.COMPLIANCE_CHECKING,
            FinanceCapabilities.PORTFOLIO_MANAGEMENT,
            FinanceCapabilities.MARKET_DATA,
            FinanceCapabilities.LEDGER_MANAGEMENT
        );
    }

    private void registerFinanceServices() {
        // Services registered via context
    }

    private void registerFinanceExtensions() {
        // Extensions registered via kernel extension mechanism
    }

    private void startTradeProcessingService() {
        System.out.println("Starting Finance trade processing service...");
    }

    private void startRiskManagementService() {
        System.out.println("Starting Finance risk management service...");
    }

    private void startComplianceService() {
        System.out.println("Starting Finance compliance service...");
    }

    private void startPortfolioService() {
        System.out.println("Starting Finance portfolio service...");
    }

    private void startMarketDataService() {
        System.out.println("Starting Finance market data service...");
    }

    private void stopTradeProcessingService() {
        System.out.println("Stopping Finance trade processing service...");
    }

    private void stopRiskManagementService() {
        System.out.println("Stopping Finance risk management service...");
    }

    private void stopComplianceService() {
        System.out.println("Stopping Finance compliance service...");
    }

    private void stopPortfolioService() {
        System.out.println("Stopping Finance portfolio service...");
    }

    private void stopMarketDataService() {
        System.out.println("Stopping Finance market data service...");
    }
}
