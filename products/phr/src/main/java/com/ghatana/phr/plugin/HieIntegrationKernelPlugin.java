package com.ghatana.phr.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.phr.hie.HieIntegrationContract;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Kernel plugin wrapper for configured HIE integrations.
 *
 * @doc.type class
 * @doc.purpose Exposes health information exchange operations through a kernel plugin contract
 * @doc.layer product
 * @doc.pattern Plugin, Adapter
 */
public final class HieIntegrationKernelPlugin implements KernelPlugin, HieIntegrationContract {

    private final HieIntegrationContract delegate;
    private final PluginManifest manifest;

    public HieIntegrationKernelPlugin(HieIntegrationContract delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.manifest = PluginManifest.builder()
            .pluginId("phr-hie-integration")
            .version("1.0.0")
            .description("PHR health information exchange integration contract adapter")
            .author("Ghatana PHR Team")
            .license("Proprietary")
            .capability(new KernelCapability(
                "phr.hie.integration",
                "PHR HIE Integration",
                "Plugin-backed export, import, sync, and status contract for HIE integrations",
                KernelCapability.CapabilityType.INTEGRATION,
                Map.of(
                    "contract", HieIntegrationContract.class.getName(),
                    "delegateContractId", delegate.contractId()
                )
            ))
            .tag("healthcare")
            .tag("hie")
            .tag("integration")
            .build();
    }

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public Set<String> getExportedContracts() {
        return Set.of(HieIntegrationContract.class.getName());
    }

    @Override
    public Set<String> getRequiredContracts() {
        return Set.of();
    }

    @Override
    public String getModuleId() {
        return "phr-hie-integration";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return manifest.getCapabilities();
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return manifest.getDependencies();
    }

    @Override
    public Promise<Void> install() {
        return Promise.complete();
    }

    @Override
    public Promise<Void> uninstall() {
        return Promise.complete();
    }

    @Override
    public void initialize(KernelContext context) {
        context.registerService(HieIntegrationContract.class, this);
    }

    @Override
    public Promise<Void> start() {
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return HealthStatus.healthy("HIE integration contract plugin ready");
    }

    @Override
    public String contractId() {
        return delegate.contractId();
    }

    @Override
    public Set<Operation> supportedOperations() {
        return delegate.supportedOperations();
    }

    @Override
    public Promise<HieIntegrationResult> submit(HieIntegrationRequest request) {
        return delegate.submit(request);
    }

    @Override
    public Promise<HieIntegrationStatus> getStatus(String requestId, String correlationId) {
        return delegate.getStatus(requestId, correlationId);
    }
}
