package com.ghatana.finance.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.plugin.compliance.CompliancePlugin;
import com.ghatana.plugin.compliance.impl.StandardCompliancePlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finance Compliance Extension - Migrated to use CompliancePlugin.
 *
 * <p>This class now wraps the shared {@link CompliancePlugin} instead of implementing
 * all compliance logic directly. The actual compliance functionality is delegated to
 * {@link StandardCompliancePlugin}.</p>
 *
 * <p><strong>Migration Note:</strong> This class is maintained for backward compatibility
 * during the transition to plugin-based architecture. New code should use
 * {@link CompliancePlugin} directly.</p>
 *
 * @doc.type class
 * @doc.purpose Finance compliance extension using shared plugin
 * @doc.layer product
 * @doc.pattern Extension (Adapter to Plugin)
 * @deprecated Use {@link CompliancePlugin} directly via plugin system
 * @since 1.0.0
 */
@Deprecated(since = "1.0.0")
public class FinanceComplianceExtension implements KernelExtension {

    private static final Logger LOG = LoggerFactory.getLogger(FinanceComplianceExtension.class);

    private final CompliancePlugin compliancePlugin;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile KernelContext context;

    public FinanceComplianceExtension() {
        // Use the shared plugin implementation
        this.compliancePlugin = new StandardCompliancePlugin();
    }

    @Override
    public String getExtensionId() {
        return "finance-compliance";
    }

    @Override
    public String getName() {
        return "Finance Compliance Extension";
    }

    @Override
    public String getVersion() {
        return "2.0.0-plugin";
    }

    @Override
    public void onModuleInitialized(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        this.context = context;
        LOG.info("Initializing FinanceComplianceExtension with plugin");
        // Initialize the plugin with a compatible context
        compliancePlugin.initialize(new PluginContextAdapter(context));
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        started.set(true);
        LOG.info("Starting FinanceComplianceExtension");
        compliancePlugin.start();
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        started.set(false);
        LOG.info("Stopping FinanceComplianceExtension");
        compliancePlugin.stop();
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return true;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId("finance-compliance")
            .withName("Finance Compliance Extension")
            .withVersion("2.0.0-plugin")
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of();
    }

    /**
     * Evaluates compliance for a financial entity.
     *
     * @param ruleSetId the rule set (e.g., "SOX", "PCI-DSS")
     * @param entityId the entity to evaluate
     * @param data the entity data
     * @return Promise containing compliance result
     */
    public Promise<com.ghatana.plugin.compliance.CompliancePlugin.ComplianceResult> evaluateCompliance(
            String ruleSetId, String entityId, Map<String, Object> data) {

        com.ghatana.plugin.compliance.CompliancePlugin.ComplianceContext ctx =
            new com.ghatana.plugin.compliance.CompliancePlugin.ComplianceContext(
                entityId,
                "financial_entity",
                data,
                "system", // Principal ID not available directly from KernelContext
                java.time.Instant.now()
            );

        return compliancePlugin.evaluate(ruleSetId, ctx);
    }

    /**
     * Gets the underlying compliance plugin.
     *
     * @return the compliance plugin
     */
    public CompliancePlugin getCompliancePlugin() {
        return compliancePlugin;
    }

    @Override
    public String toString() {
        return "FinanceComplianceExtension{plugin=" + compliancePlugin.getClass().getSimpleName() + "}";
    }
}
