package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl;
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

    /**
     * Creates the bridge extension backed by the provided Data-Cloud client.
     *
     * @param client the Data-Cloud client implementation — must not be {@code null}
     */
    public DataCloudKernelExtension(DataCloudKernelAdapterImpl.DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloud client must not be null");
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

        DataCloudKernelAdapterImpl adapter = new DataCloudKernelAdapterImpl(client);
        context.registerService(DataCloudKernelAdapter.class, adapter);

        LOG.info("[DataCloudKernelExtension] DataCloudKernelAdapter registered successfully");
    }

    @Override
    protected void onStart(KernelContext context) {
        LOG.info("[DataCloudKernelExtension] Started — Data-Cloud storage capabilities active");
    }

    @Override
    protected void onStop(KernelContext context) {
        LOG.info("[DataCloudKernelExtension] Stopping — Data-Cloud storage capabilities removed");
    }
}
