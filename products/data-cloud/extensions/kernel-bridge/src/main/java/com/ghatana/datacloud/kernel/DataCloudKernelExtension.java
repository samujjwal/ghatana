package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
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
 * KernelExtension that registers a live Data-Cloud adapter into the kernel context.
 *
 * <h2>Architectural role</h2>
 * <p>The kernel defines the <em>port</em> ({@link DataCloudKernelAdapter} interface) but knows
 * nothing about the concrete Data-Cloud product. This extension runs <em>on the Data-Cloud side
 * of responsibility</em>: it accepts a {@link DataCloudKernelAdapterImpl.DataCloudClient}
 * implementation at construction time, wraps it in {@link DataCloudKernelAdapterImpl}, and
 * calls {@code context.registerService(DataCloudKernelAdapter.class, impl)} during
 * {@link #onInitialize}.</p>
 *
 * <h2>Dependency direction</h2>
 * <pre>
 *   kernel-core  &lt;─── data-cloud-kernel-bridge  &lt;─── data-cloud (provides DataCloudClient impl)
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DataCloudKernelAdapterImpl.DataCloudClient myClient = new ProdDataCloudClient(...);
 * DataCloudKernelExtension extension = new DataCloudKernelExtension(myClient);
 * kernelModule.registerExtension(extension);
 * }</pre>
 *
 * <p>After module initialisation, kernel consumers can call:</p>
 * <pre>{@code
 * DataCloudKernelAdapter adapter = context.getDependency(DataCloudKernelAdapter.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge extension that registers the Data-Cloud adapter into the kernel context
 * @doc.layer adapter
 * @doc.pattern Extension, Bridge
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class DataCloudKernelExtension extends AbstractKernelExtension {

    private static final Logger LOG = LoggerFactory.getLogger(DataCloudKernelExtension.class);

    private static final String EXTENSION_ID = "data-cloud-kernel-bridge";
    private static final String EXTENSION_NAME = "Data-Cloud Kernel Bridge";
    private static final String EXTENSION_VERSION = "1.0.0";

    private final DataCloudKernelAdapterImpl.DataCloudClient client;
    private final BridgeAuthorizationService authorizationService;
    private final BridgeAuditEmitter auditEmitter;
    private final BridgeHealthIndicator healthIndicator;
    private final TenantBridgeContextResolver tenantContextResolver;

    /**
     * Creates the bridge extension backed by the provided Data-Cloud ports.
     *
     * @param client the Data-Cloud client implementation — must not be {@code null}
     */
    public DataCloudKernelExtension(
            DataCloudKernelAdapterImpl.DataCloudClient client,
            BridgeAuthorizationService authorizationService,
            BridgeAuditEmitter auditEmitter,
            BridgeHealthIndicator healthIndicator,
            TenantBridgeContextResolver tenantContextResolver) {
        this.client = Objects.requireNonNull(client, "DataCloud client must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
        this.auditEmitter = Objects.requireNonNull(auditEmitter, "auditEmitter must not be null");
        this.healthIndicator = Objects.requireNonNull(healthIndicator, "healthIndicator must not be null");
        this.tenantContextResolver = Objects.requireNonNull(tenantContextResolver, "tenantContextResolver must not be null");
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
            .withDescription("Registers a live DataCloudKernelAdapter into the kernel context. " +
                             "Data-Cloud provides this extension; the kernel receives storage capability.")
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            DataCloudBridgeCapabilities.DATA_CLOUD_STORAGE,
            DataCloudBridgeCapabilities.DATA_CLOUD_TRANSACTIONS,
            DataCloudBridgeCapabilities.DATA_CLOUD_STREAMING
        );
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        // Compatible with any kernel module — the adapter is generic
        return hostModule != null;
    }

    // ==================== Lifecycle hooks ====================

    @Override
    protected void onInitialize(KernelContext context) {
        LOG.info("[DataCloudKernelExtension] Initializing: registering DataCloudKernelAdapter into context");
        BridgeContext bridgeContext = tenantContextResolver.resolve(context);
        healthIndicator.reportHealthy(EXTENSION_ID + ":initializing:" + bridgeContext.getTenantId());

        DataCloudKernelAdapterImpl adapter = new DataCloudKernelAdapterImpl(
            client,
            authorizationService,
            auditEmitter,
            healthIndicator);
        context.registerService(DataCloudKernelAdapter.class, adapter);
        registerPlatformProviders(context, bridgeContext, adapter);

        LOG.info("[DataCloudKernelExtension] DataCloudKernelAdapter registered successfully");
    }

    @Override
    protected void onStart(KernelContext context) {
        BridgeContext bridgeContext = tenantContextResolver.resolve(context);
        healthIndicator.reportHealthy(EXTENSION_ID + ":started:" + bridgeContext.getTenantId());
        LOG.info("[DataCloudKernelExtension] Started — Data-Cloud storage capabilities active");
    }

    @Override
    protected void onStop(KernelContext context) {
        BridgeContext bridgeContext = tenantContextResolver.resolve(context);
        healthIndicator.reportDegraded(EXTENSION_ID, "stopped for tenant " + bridgeContext.getTenantId());
        LOG.info("[DataCloudKernelExtension] Stopping — Data-Cloud storage capabilities removed");
    }

    private void registerPlatformProviders(
            KernelContext context,
            BridgeContext bridgeContext,
            DataCloudKernelAdapter adapter) {
        context.registerService(DataCloudEventProvider.class, new DataCloudEventProvider(adapter, bridgeContext));
        context.registerService(DataCloudArtifactProvider.class, new DataCloudArtifactProvider(adapter, bridgeContext));
        context.registerService(DataCloudArtifactGraphProvider.class, new DataCloudArtifactGraphProvider(adapter, bridgeContext));
        context.registerService(DataCloudHealthProvider.class, new DataCloudHealthProvider(adapter, bridgeContext));
        context.registerService(DataCloudProvenanceProvider.class, new DataCloudProvenanceProvider(adapter, bridgeContext));
        context.registerService(DataCloudMemoryProvider.class, new DataCloudMemoryProvider(adapter, bridgeContext));
        context.registerService(DataCloudKnowledgeProvider.class, new DataCloudKnowledgeProvider(adapter, bridgeContext));
        context.registerService(DataCloudRuntimeTruthProvider.class, new DataCloudRuntimeTruthProvider(adapter, bridgeContext));
        context.registerService(
            DataCloudPolicyEvidenceProvider.class,
            new DataCloudPolicyEvidenceProvider(adapter, bridgeContext));
        healthIndicator.reportHealthy(EXTENSION_ID + ":providers:" + bridgeContext.getTenantId());
    }

    /**
     * Resolves the platform tenant scope used when registering Data Cloud bridge providers.
     *
     * @doc.type interface
     * @doc.purpose Product-side resolver that converts KernelContext into BridgeContext
     * @doc.layer adapter
     * @doc.pattern Port
     */
    @FunctionalInterface
    public interface TenantBridgeContextResolver {
        BridgeContext resolve(KernelContext context);
    }
}
