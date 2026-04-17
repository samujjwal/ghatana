package com.ghatana.${product_package}.kernel;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.module.AbstractKernelModule;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Set;

/**
 * Kernel module for ${ProductName}.
 *
 * <p>Initializes and starts all ${ProductName} services within the kernel's
 * lifecycle. On initialization this module:</p>
 * <ol>
 *   <li>Receives the {@link KernelContext} containing platform capabilities</li>
 *   <li>Registers its own services back into the context for cross-product use</li>
 *   <li>Optionally registers {@link com.ghatana.kernel.extension.KernelExtension}s
 *       to contribute additional capabilities</li>
 * </ol>
 *
 * <p>Do NOT put business logic here. Wire services and register them.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel module wiring for ${ProductName}
 * @doc.layer product
 * @doc.pattern KernelModule
 */
public class ${ProductName}KernelModule extends AbstractKernelModule {

    @Override
    public String getModuleId() {
        return "${product-name}";
    }

    @Override
    public String getName() {
        return "${ProductName} Kernel Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId("${product-name}")
            .withName("${ProductName}")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .build();
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        // TODO: declare the capabilities this module provides
        return Set.of();
    }

    @Override
    public Set<KernelCapability> getRequiredCapabilities() {
        // TODO: declare the capabilities this module REQUIRES before it can initialize
        // Example: require Data-Cloud storage before starting patient services
        // return Set.of(DataCloudBridgeCapabilities.DATA_CLOUD_STORAGE);
        return Set.of();
    }

    // ==================== AbstractKernelModule hooks ====================

    @Override
    protected void afterInitialized(KernelContext context) {
        // TODO: create your domain services and register them
        // Example:
        //   ${ProductName}DataService dataService = new ${ProductName}DataService(context);
        //   registerService(dataService);
        //   context.registerService(${ProductName}Api.class, dataService);
    }

    @Override
    protected Promise<Void> onStarted(KernelContext context) {
        // TODO: start background tasks after all services are started
        return Promise.complete();
    }
}
