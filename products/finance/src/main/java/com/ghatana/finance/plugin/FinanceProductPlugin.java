package com.ghatana.finance.plugin;

import com.ghatana.kernel.capability.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.plugin.ProductPlugin;
import com.ghatana.kernel.plugin.PluginContext;
import com.ghatana.kernel.plugin.KernelExtension;

import java.util.Set;

/**
 * Finance product plugin implementation.
 * 
 * This plugin registers Finance-specific capabilities with the kernel
 * without creating tight coupling between Finance and the kernel.
 */
public class FinanceProductPlugin implements ProductPlugin {
    private PluginContext context;
    
    @Override
    public String getProductId() {
        return "finance";
    }

    @Override
    public String getProductVersion() {
        return "1.0.0";
    }

    @Override
    public String getProductDescription() {
        return "Financial trading and risk management system";
    }

    @Override
    public Set<KernelCapability> getDeclaredCapabilities() {
        return Set.of(
            // Finance-specific capabilities
            new KernelCapability(
                "trade.processing", 
                "Trade Processing", 
                "High-frequency trade processing",
                KernelCapability.CapabilityType.EVENT_PROCESSING,
                java.util.Map.of(
                    "throughput", "100k_tps",
                    "latency", "<1ms",
                    "persistence", "event_sourcing",
                    "required_services", "trade_engine,order_book,risk_checker"
                )
            ),
            
            new KernelCapability(
                "risk.management", 
                "Risk Management", 
                "Real-time risk assessment",
                KernelCapability.CapabilityType.AI_ML,
                java.util.Map.of(
                    "risk_types", "market,credit,operational",
                    "real_time", "true",
                    "models", "var,stress_testing,monte_carlo",
                    "required_services", "risk_engine,model_service,analytics_service"
                )
            ),
            
            new KernelCapability(
                "compliance.checking", 
                "Compliance Checking", 
                "Financial compliance monitoring",
                KernelCapability.CapabilityType.COMPLIANCE,
                java.util.Map.of(
                    "regulations", "sox,pci_dss,miFID_II",
                    "real_time", "true",
                    "reporting", "automated",
                    "required_services", "compliance_engine,reporting_service,audit_service"
                )
            ),
            
            new KernelCapability(
                "portfolio.management", 
                "Portfolio Management", 
                "Investment portfolio management",
                KernelCapability.CapabilityType.DATA_MANAGEMENT,
                java.util.Map.of(
                    "portfolio_types", "equity,fixed_income,derivatives",
                    "analytics", "performance,attribution,risk",
                    "required_services", "portfolio_service,analytics_service"
                )
            ),
            
            new KernelCapability(
                "market.data", 
                "Market Data", 
                "Real-time market data processing",
                KernelCapability.CapabilityType.DATA_MANAGEMENT,
                java.util.Map.of(
                    "data_sources", "bloomberg,refinitiv,exchange_feeds",
                    "data_types", "quotes,trades,order_book,fundamentals",
                    "required_services", "market_data_service,feed_processor"
                )
            )
        );
    }

    @Override
    public Set<KernelDependency> getRequiredDependencies() {
        return Set.of(
            // Core kernel capabilities
            new KernelDependency("event.processing", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("data.storage", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("ai.ml.framework", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("observability.framework", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            
            // External services
            new KernelDependency("market-data-feed", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, false),
            new KernelDependency("risk-models", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, false)
        );
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        registerFinanceServices();
        registerFinanceExtensions();
    }

    @Override
    public void start() {
        startTradeProcessingService();
        startRiskManagementService();
        startComplianceService();
        startPortfolioService();
        startMarketDataService();
    }

    @Override
    public void stop() {
        stopTradeProcessingService();
        stopRiskManagementService();
        stopComplianceService();
        stopPortfolioService();
        stopMarketDataService();
    }

    @Override
    public void shutdown() {
        cleanupFinanceResources();
    }

    @Override
    public Set<KernelExtension> getExtensions() {
        return Set.of(
            new TradingExtension(),
            new RiskAssessmentExtension(),
            new ComplianceReportingExtension(),
            new PortfolioAnalyticsExtension()
        );
    }

    private void registerFinanceServices() {
        context.registerService("trade.service", new TradeProcessingService(context));
        context.registerService("risk.service", new RiskManagementService(context));
        context.registerService("compliance.service", new ComplianceService(context));
        context.registerService("portfolio.service", new PortfolioService(context));
        context.registerService("market.data.service", new MarketDataService(context));
    }

    private void registerFinanceExtensions() {
        context.registerExtension(new TradingExtension());
        context.registerExtension(new RiskAssessmentExtension());
        context.registerExtension(new ComplianceReportingExtension());
        context.registerExtension(new PortfolioAnalyticsExtension());
    }

    // Service lifecycle methods
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

    private void cleanupFinanceResources() {
        System.out.println("Cleaning up Finance resources...");
    }

    // Inner classes for services and extensions
    private static class TradeProcessingService {
        private final PluginContext context;
        
        public TradeProcessingService(PluginContext context) {
            this.context = context;
        }
    }

    private static class RiskManagementService {
        private final PluginContext context;
        
        public RiskManagementService(PluginContext context) {
            this.context = context;
        }
    }

    private static class ComplianceService {
        private final PluginContext context;
        
        public ComplianceService(PluginContext context) {
            this.context = context;
        }
    }

    private static class PortfolioService {
        private final PluginContext context;
        
        public PortfolioService(PluginContext context) {
            this.context = context;
        }
    }

    private static class MarketDataService {
        private final PluginContext context;
        
        public MarketDataService(PluginContext context) {
            this.context = context;
        }
    }

    private static class TradingExtension implements KernelExtension {
        @Override
        public String getExtensionId() { return "trading.engine"; }
        
        @Override
        public String getVersion() { return "1.0.0"; }
        
        @Override
        public String getDescription() { return "Trading engine extension"; }
        
        @Override
        public String getTargetCapabilityId() { return "trade.processing"; }
        
        @Override
        public void initialize(PluginContext context) {}
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void shutdown() {}
    }

    private static class RiskAssessmentExtension implements KernelExtension {
        @Override
        public String getExtensionId() { return "risk.assessment"; }
        
        @Override
        public String getVersion() { return "1.0.0"; }
        
        @Override
        public String getDescription() { return "Risk assessment extension"; }
        
        @Override
        public String getTargetCapabilityId() { return "risk.management"; }
        
        @Override
        public void initialize(PluginContext context) {}
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void shutdown() {}
    }

    private static class ComplianceReportingExtension implements KernelExtension {
        @Override
        public String getExtensionId() { return "compliance.reporting"; }
        
        @Override
        public String getVersion() { return "1.0.0"; }
        
        @Override
        public String getDescription() { return "Compliance reporting extension"; }
        
        @Override
        public String getTargetCapabilityId() { return "compliance.checking"; }
        
        @Override
        public void initialize(PluginContext context) {}
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void shutdown() {}
    }

    private static class PortfolioAnalyticsExtension implements KernelExtension {
        @Override
        public String getExtensionId() { return "portfolio.analytics"; }
        
        @Override
        public String getVersion() { return "1.0.0"; }
        
        @Override
        public String getDescription() { return "Portfolio analytics extension"; }
        
        @Override
        public String getTargetCapabilityId() { return "portfolio.management"; }
        
        @Override
        public void initialize(PluginContext context) {}
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void shutdown() {}
    }
}
