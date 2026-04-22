package com.ghatana.finance.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.AbstractKernelExtension;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import com.ghatana.plugin.risk.impl.StandardRiskManagementPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Finance Risk Extension - Migrated to use RiskManagementPlugin.
 *
 * <p>This class now wraps the shared {@link RiskManagementPlugin} instead of implementing
 * all risk logic directly. The actual risk functionality is delegated to
 * {@link StandardRiskManagementPlugin}.</p>
 *
 * <p><strong>Migration Note:</strong> This class is maintained for backward compatibility
 * during the transition to plugin-based architecture. New code should use
 * {@link RiskManagementPlugin} directly.</p>
 *
 * @doc.type class
 * @doc.purpose Finance risk extension using shared plugin
 * @doc.layer product
 * @doc.pattern Extension (Adapter to Plugin)
 * @deprecated Use {@link RiskManagementPlugin} directly via plugin system
 * @since 1.0.0
 */
@Deprecated(since = "1.0.0")
public class FinanceRiskExtension extends AbstractKernelExtension {

    private static final Logger LOG = LoggerFactory.getLogger(FinanceRiskExtension.class);

    private final RiskManagementPlugin riskPlugin;
    private KernelContext context;

    public FinanceRiskExtension() {
        // Use the shared plugin implementation
        this.riskPlugin = new StandardRiskManagementPlugin();
    }

    @Override
    public String getExtensionId() {
        return "finance-risk";
    }

    @Override
    public String getName() {
        return "Finance Risk Extension";
    }

    @Override
    public String getVersion() {
        return "2.0.0-plugin";
    }

    protected void onInitialize(KernelContext context) {
        this.context = context;
        LOG.info("Initializing FinanceRiskExtension with plugin");

        riskPlugin.initialize(new PluginContextAdapter(context));
    }

    protected void onStart(KernelContext context) {
        LOG.info("Starting FinanceRiskExtension");
        riskPlugin.start();
    }

    protected void onStop(KernelContext context) {
        LOG.info("Stopping FinanceRiskExtension");
        riskPlugin.stop();
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return true;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId("finance-risk")
            .withName("Finance Risk Extension")
            .withVersion("2.0.0-plugin")
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of();
    }

    /**
     * Calculates trading risk for a position.
     *
     * @param positionId the position identifier
     * @param factors the risk factors
     * @return Promise containing risk score
     */
    public Promise<com.ghatana.plugin.risk.RiskManagementPlugin.RiskScore> calculateTradingRisk(
            String positionId, Map<String, Object> factors) {
        return riskPlugin.calculateRisk(positionId, RiskManagementPlugin.RiskType.MARKET, factors);
    }

    /**
     * Sets trading risk limits.
     *
     * @param positionId the position identifier
     * @param maxNotional the maximum notional exposure
     * @param maxVaR the maximum VaR
     * @return Promise completing when limits are set
     */
    public Promise<Void> setTradingLimits(String positionId, BigDecimal maxNotional, BigDecimal maxVaR) {
        RiskManagementPlugin.RiskLimits limits = new RiskManagementPlugin.RiskLimits(
            maxNotional,
            maxNotional.multiply(BigDecimal.valueOf(10)), // Portfolio = 10x position
            maxVaR,
            BigDecimal.valueOf(0.25), // 25% max concentration
            maxNotional.multiply(BigDecimal.valueOf(0.5)) // Max loss = 50% of position
        );
        return riskPlugin.setRiskLimits(positionId, limits);
    }

    /**
     * Gets the underlying risk plugin.
     *
     * @return the risk management plugin
     */
    public RiskManagementPlugin getRiskPlugin() {
        return riskPlugin;
    }

    @Override
    public String toString() {
        return "FinanceRiskExtension{plugin=" + riskPlugin.getClass().getSimpleName() + "}";
    }
}
