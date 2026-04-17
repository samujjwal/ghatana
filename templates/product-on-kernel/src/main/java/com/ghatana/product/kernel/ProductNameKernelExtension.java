package com.ghatana.${product_package}.kernel;

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
 * Example KernelExtension for ${ProductName}.
 *
 * <p>Extensions live on the <em>product side</em> of responsibility. They accept
 * an external dependency (client/service) at construction time and register it
 * into the kernel context via {@code context.registerService(...)} during
 * {@link #onInitialize}.</p>
 *
 * <h2>When to use a KernelExtension instead of a KernelModule</h2>
 * <ul>
 *   <li>You want to contribute a single capability to a hosting module.</li>
 *   <li>You do not need the full module lifecycle (initialize → start → stop chain).</li>
 *   <li>You are wiring an external product (Data-Cloud, AEP, YAPPC) into the kernel.</li>
 * </ul>
 *
 * <h2>Extension registration pattern</h2>
 * <pre>{@code
 * // In a product launcher or bootstrap class:
 * MyProductKernelExtension extension = new MyProductKernelExtension(myExternalClient);
 * hostKernelModule.registerExtension(extension);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Template KernelExtension showing the extension-registration pattern
 * @doc.layer product
 * @doc.pattern Extension, Bridge
 */
public class ProductNameKernelExtension extends AbstractKernelExtension {

    private static final Logger LOG = LoggerFactory.getLogger(ProductNameKernelExtension.class);

    // TODO: replace with your product's adapter/client type
    private final DataCloudKernelAdapterImpl.DataCloudClient client;

    /**
     * Creates the extension with its external dependency.
     *
     * @param client the external client — must not be {@code null}
     */
    public ProductNameKernelExtension(DataCloudKernelAdapterImpl.DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String getExtensionId() {
        return "${product-name}-extension";  // TODO: choose a stable kebab-case ID
    }

    @Override
    public String getName() {
        return "${ProductName} Extension";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId(getExtensionId())
            .withName(getName())
            .withVersion(getVersion())
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        // TODO: declare the capabilities this extension contributes to the hosting module
        return Set.of();
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return hostModule != null;
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onInitialize(KernelContext context) {
        LOG.info("[{}] Registering adapter into kernel context", getExtensionId());

        // TODO: create the adapter/service wrapping your client and register it
        // Example (Data-Cloud):
        //   DataCloudKernelAdapter adapter = new DataCloudKernelAdapterImpl(client);
        //   context.registerService(DataCloudKernelAdapter.class, adapter);
    }

    @Override
    protected void onStart(KernelContext context) {
        LOG.info("[{}] Started", getExtensionId());
    }

    @Override
    protected void onStop(KernelContext context) {
        LOG.info("[{}] Stopped", getExtensionId());
    }
}
