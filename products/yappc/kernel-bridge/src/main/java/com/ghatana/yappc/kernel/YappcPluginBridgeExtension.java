package com.ghatana.yappc.kernel;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.AbstractKernelExtension;
import com.ghatana.kernel.module.KernelModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * KernelExtension that exposes narrow YAPPC evidence and intent ports into the kernel context.
 *
 * <h2>Architectural role</h2>
 * <p>YAPPC keeps its product plugin registry and compiler/scanner internals inside the
 * product boundary. This bridge registers stable service ports so Kernel consumers can
 * request ProductUnitIntent candidates and artifact-intelligence evidence without receiving
 * the broad plugin registry.</p>
 *
 * <h2>Dependency direction</h2>
 * <pre>
 *   kernel-core  &lt;─── yappc-kernel-bridge  &lt;─── yappc product providers
 * </pre>
 *
 * <h2>Usage in the YAPPC launcher / service entry-point</h2>
 * <pre>{@code
 * YappcPluginBridgeExtension extension = new YappcPluginBridgeExtension(
 *     intentProvider, semanticEvidenceProvider, graphSummaryProvider,
 *     residualIslandReportProvider, riskHotspotReportProvider);
 * kernelModule.registerExtension(extension);
 * }</pre>
 *
 * <p>After module initialisation, kernel consumers can retrieve the narrow ports:</p>
 * <pre>{@code
 * YappcProductUnitIntentProvider intents = context.getDependency(YappcProductUnitIntentProvider.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge extension that exposes YAPPC evidence ports via the kernel context
 * @doc.layer adapter
 * @doc.pattern Extension, Bridge
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class YappcPluginBridgeExtension extends AbstractKernelExtension {

    private static final Logger LOG = LoggerFactory.getLogger(YappcPluginBridgeExtension.class);

    private static final String EXTENSION_ID = "yappc-plugin-bridge";
    private static final String EXTENSION_NAME = "YAPPC Plugin Bridge";
    private static final String EXTENSION_VERSION = "1.0.0";

    private final YappcProductUnitIntentProvider productUnitIntentProvider;
    private final YappcSemanticArtifactEvidenceProvider semanticArtifactEvidenceProvider;
    private final YappcArtifactGraphSummaryProvider artifactGraphSummaryProvider;
    private final YappcResidualIslandReportProvider residualIslandReportProvider;
    private final YappcRiskHotspotReportProvider riskHotspotReportProvider;

    /**
     * Creates the bridge extension backed by narrow YAPPC provider ports.
     */
    public YappcPluginBridgeExtension(
            YappcProductUnitIntentProvider productUnitIntentProvider,
            YappcSemanticArtifactEvidenceProvider semanticArtifactEvidenceProvider,
            YappcArtifactGraphSummaryProvider artifactGraphSummaryProvider,
            YappcResidualIslandReportProvider residualIslandReportProvider,
            YappcRiskHotspotReportProvider riskHotspotReportProvider) {
        this.productUnitIntentProvider =
            Objects.requireNonNull(productUnitIntentProvider, "productUnitIntentProvider must not be null");
        this.semanticArtifactEvidenceProvider =
            Objects.requireNonNull(semanticArtifactEvidenceProvider, "semanticArtifactEvidenceProvider must not be null");
        this.artifactGraphSummaryProvider =
            Objects.requireNonNull(artifactGraphSummaryProvider, "artifactGraphSummaryProvider must not be null");
        this.residualIslandReportProvider =
            Objects.requireNonNull(residualIslandReportProvider, "residualIslandReportProvider must not be null");
        this.riskHotspotReportProvider =
            Objects.requireNonNull(riskHotspotReportProvider, "riskHotspotReportProvider must not be null");
    }

    // ==================== KernelExtension identity ====================

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getVersion() {
        return EXTENSION_VERSION;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId(EXTENSION_ID)
            .withName(EXTENSION_NAME)
            .withVersion(EXTENSION_VERSION)
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .withDescription("Registers narrow YAPPC ProductUnitIntent and artifact-intelligence provider ports. " +
                             "YAPPC keeps plugin registry internals inside the product boundary.")
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            YappcBridgeCapabilities.YAPPC_PRODUCT_UNIT_INTENTS,
            YappcBridgeCapabilities.YAPPC_ARTIFACT_INTELLIGENCE
        );
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return hostModule != null;
    }

    // ==================== Lifecycle hooks ====================

    @Override
    protected void onInitialize(KernelContext context) {
        LOG.info("[YappcPluginBridgeExtension] Initializing: registering narrow YAPPC provider ports");

        context.registerService(YappcProductUnitIntentProvider.class, productUnitIntentProvider);
        context.registerService(YappcSemanticArtifactEvidenceProvider.class, semanticArtifactEvidenceProvider);
        context.registerService(YappcArtifactGraphSummaryProvider.class, artifactGraphSummaryProvider);
        context.registerService(YappcResidualIslandReportProvider.class, residualIslandReportProvider);
        context.registerService(YappcRiskHotspotReportProvider.class, riskHotspotReportProvider);

        LOG.info("[YappcPluginBridgeExtension] YAPPC provider ports registered successfully");
    }

    @Override
    protected void onStart(KernelContext context) {
        LOG.info("[YappcPluginBridgeExtension] Started — YAPPC evidence bridge active");
    }

    @Override
    protected void onStop(KernelContext context) {
        LOG.info("[YappcPluginBridgeExtension] Stopping — YAPPC plugin bridge deactivated");
    }
}
