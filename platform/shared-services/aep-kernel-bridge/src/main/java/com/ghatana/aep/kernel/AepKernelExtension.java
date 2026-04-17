package com.ghatana.aep.kernel;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.adapter.aep.AepKernelAdapterImpl;
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
 * KernelExtension that registers a live AEP adapter into the kernel context.
 *
 * <h2>Architectural role</h2>
 * <p>The kernel defines the <em>port</em> ({@link AepKernelAdapter} interface) but knows
 * nothing about the concrete AEP product. This extension runs <em>on the AEP side of
 * responsibility</em>: it accepts an {@link AepKernelAdapterImpl.AepClient} implementation
 * at construction time, wraps it in {@link AepKernelAdapterImpl}, and calls
 * {@code context.registerService(AepKernelAdapter.class, impl)} during
 * {@link #onInitialize}.</p>
 *
 * <h2>Dependency direction</h2>
 * <pre>
 *   kernel-core  &lt;─── aep-kernel-bridge  &lt;─── aep product (provides AepClient impl)
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AepKernelAdapterImpl.AepClient myClient = new ProdAepClient(...);
 * AepKernelExtension extension = new AepKernelExtension(myClient);
 * kernelModule.registerExtension(extension);
 * }</pre>
 *
 * <p>After module initialisation, kernel consumers can retrieve the adapter:</p>
 * <pre>{@code
 * AepKernelAdapter adapter = context.getDependency(AepKernelAdapter.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge extension that registers the AEP adapter into the kernel context
 * @doc.layer adapter
 * @doc.pattern Extension, Bridge
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class AepKernelExtension extends AbstractKernelExtension {

    private static final Logger LOG = LoggerFactory.getLogger(AepKernelExtension.class);

    private static final String EXTENSION_ID = "aep-kernel-bridge";
    private static final String EXTENSION_NAME = "AEP Kernel Bridge";
    private static final String EXTENSION_VERSION = "1.0.0";

    private final AepKernelAdapterImpl.AepClient client;

    /**
     * Creates the bridge extension backed by the provided AEP client.
     *
     * @param client the AEP client implementation — must not be {@code null}
     */
    public AepKernelExtension(AepKernelAdapterImpl.AepClient client) {
        this.client = Objects.requireNonNull(client, "AEP client must not be null");
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
            .withDescription("Registers a live AepKernelAdapter into the kernel context. " +
                             "AEP provides this extension; the kernel receives event/agent capabilities.")
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            AepBridgeCapabilities.AEP_EVENT_STREAMING,
            AepBridgeCapabilities.AEP_AGENT_RUNTIME,
            AepBridgeCapabilities.AEP_PIPELINE_ORCHESTRATION
        );
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return hostModule != null;
    }

    // ==================== Lifecycle hooks ====================

    @Override
    protected void onInitialize(KernelContext context) {
        LOG.info("[AepKernelExtension] Initializing: registering AepKernelAdapter into context");

        AepKernelAdapterImpl adapter = new AepKernelAdapterImpl(client);
        context.registerService(AepKernelAdapter.class, adapter);

        LOG.info("[AepKernelExtension] AepKernelAdapter registered successfully");
    }

    @Override
    protected void onStart(KernelContext context) {
        LOG.info("[AepKernelExtension] Started — AEP event/agent capabilities active");
    }

    @Override
    protected void onStop(KernelContext context) {
        LOG.info("[AepKernelExtension] Stopping — AEP capabilities removed from context");
    }
}
